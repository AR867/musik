package com.example.domain.recommendation

import com.example.data.catalog.MusicCatalogProvider
import com.example.data.local.ListeningHistoryEntity
import com.example.data.model.RecommendationItem
import com.example.data.model.WaveSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Core recommendation algorithm for «Моя волна» (My Wave) and personal recommendations.
 * Evaluates real telemetry: likes, dislikes, skips, listening duration, repeat listens,
 * preferred genres, favorite artists, frequently listened albums, excluded artists,
 * excluded genres, mood constraints, and user-adjustable diversity (0..100%).
 */
class MyWaveRecommendationEngine(
    private val catalogProvider: MusicCatalogProvider
) {

    fun generateWaveQueue(
        settings: WaveSettings,
        history: List<ListeningHistoryEntity>,
        preferredGenres: List<String> = emptyList(),
        favoriteArtistNames: Set<String> = emptySet(),
        savedAlbumIds: Set<String> = emptySet(),
        limit: Int = 18
    ): List<RecommendationItem> {
        val historyByTrack = history.associateBy { it.trackId }
        val likedTracks = history.filter { it.liked && !it.disliked }
        val dislikedTrackIds = history.filter { it.disliked }.map { it.trackId }.toSet()

        // Calculate genre affinity weights from actual listening history
        val genreAffinity = mutableMapOf<String, Double>()
        preferredGenres.forEach { g -> genreAffinity[g] = (genreAffinity[g] ?: 0.0) + 18.0 }
        history.forEach { h ->
            var delta = 0.0
            if (h.liked) delta += 30.0
            if (h.disliked) delta -= 55.0
            delta += (h.playCount * 9.0).coerceAtMost(45.0)
            delta += ((h.totalListenMs / 10_000.0) * 4.0).coerceAtMost(25.0)
            delta -= (h.skipCount * 14.0).coerceAtMost(42.0)
            genreAffinity[h.genre] = (genreAffinity[h.genre] ?: 0.0) + delta
        }

        // Calculate artist affinity weights
        val artistAffinity = mutableMapOf<String, Double>()
        favoriteArtistNames.forEach { name ->
            artistAffinity[name] = (artistAffinity[name] ?: 0.0) + 28.0
        }
        history.forEach { h ->
            var delta = 0.0
            if (h.liked) delta += 25.0
            if (h.disliked) delta -= 60.0
            delta += (h.playCount * 7.0).coerceAtMost(35.0)
            delta -= (h.skipCount * 12.0).coerceAtMost(36.0)
            artistAffinity[h.artistName] = (artistAffinity[h.artistName] ?: 0.0) + delta
        }

        // Calculate album affinity weights from listening history & saved albums
        val albumAffinity = mutableMapOf<String, Double>()
        savedAlbumIds.forEach { albumId ->
            albumAffinity[albumId] = (albumAffinity[albumId] ?: 0.0) + 22.0
        }
        history.forEach { h ->
            val trackObj = catalogProvider.getTrackById(h.trackId)
            if (trackObj != null && (h.playCount > 0 || h.liked)) {
                val boost = (if (h.liked) 14.0 else 0.0) + (h.playCount * 5.0).coerceAtMost(20.0)
                albumAffinity[trackObj.albumId] = (albumAffinity[trackObj.albumId] ?: 0.0) + boost
            }
        }

        val seedTrack = settings.seedTrackId?.let { catalogProvider.getTrackById(it) }
        val seedArtist = settings.seedArtistId?.let { catalogProvider.getArtistById(it) }
        val knownArtists = (
            history.filter { it.playCount > 0 || it.liked }.map { it.artistName } +
                favoriteArtistNames
            ).toSet()
        val allArtistsById = catalogProvider.getAllArtists().associateBy { it.id }

        val excludedArtistsNormalized = settings.excludedArtists.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val excludedGenresNormalized = settings.excludedGenres.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

        val candidates = catalogProvider.getAllTracks().filter { track ->
            track.id !in dislikedTrackIds &&
                track.artistName.lowercase() !in excludedArtistsNormalized &&
                track.artistId.lowercase() !in excludedArtistsNormalized &&
                track.genre.lowercase() !in excludedGenresNormalized &&
                (track.subgenre.isBlank() || track.subgenre.lowercase() !in excludedGenresNormalized)
        }

        val scored = candidates.map { track ->
            var score = 50.0
            val reasons = mutableListOf<String>()

            // 1. Seed Track similarity
            if (seedTrack != null) {
                if (track.id == seedTrack.id) {
                    score += 40.0
                    reasons.add("Стартовый трек вашей волны «${seedTrack.title}»")
                } else {
                    if (track.genre.equals(seedTrack.genre, ignoreCase = true)) {
                        score += 28.0
                        reasons.add("Похоже на «${seedTrack.title}» по жанру ${track.genre}")
                    }
                    val bpmDiff = abs(track.bpm - seedTrack.bpm)
                    if (bpmDiff <= 18) {
                        score += 14.0
                        reasons.add("Близкий темп (${track.bpm} BPM)")
                    }
                }
            }

            // 2. Seed Artist filter
            if (seedArtist != null) {
                if (track.artistId == seedArtist.id || track.artistName.equals(seedArtist.name, ignoreCase = true)) {
                    score += 38.0
                    reasons.add("Волна по исполнителю ${seedArtist.name}")
                } else if (track.genre.equals(seedArtist.primaryGenre, ignoreCase = true)) {
                    score += 20.0
                    reasons.add("В стиле исполнителя ${seedArtist.name}")
                }
            }

            // 3. Seed Genre filter
            if (!settings.seedGenre.isNullOrBlank()) {
                if (track.genre.equals(settings.seedGenre, ignoreCase = true) ||
                    track.subgenre.equals(settings.seedGenre, ignoreCase = true)
                ) {
                    score += 36.0
                    reasons.add("Жанр волны: ${track.genre}")
                } else {
                    score -= 12.0
                }
            }

            // 4. Mood alignment
            settings.mood?.let { mood ->
                if (track.genre in mood.primaryGenreHints ||
                    mood.primaryGenreHints.any { hint -> track.genre.contains(hint, ignoreCase = true) }
                ) {
                    score += 22.0
                    reasons.add("Настроение «${mood.titleRu}»")
                }
                if (track.bpm in mood.minBpm..mood.maxBpm) {
                    score += 12.0
                } else {
                    score -= 8.0
                }
            }

            // 5. Historical interaction with this specific track
            val trackHistory = historyByTrack[track.id]
            if (trackHistory != null) {
                if (trackHistory.liked) {
                    score += 24.0
                    reasons.add("В ваших любимых треках")
                }
                if (trackHistory.playCount >= 2 && trackHistory.skipCount == 0) {
                    score += 14.0
                    reasons.add("Частое повторное прослушивание (${trackHistory.playCount}×)")
                }
                if (trackHistory.skipCount > trackHistory.playCount) {
                    score -= 26.0
                }
            }

            // 6. Genre, Artist & Album affinity
            val gScore = genreAffinity[track.genre] ?: 0.0
            if (gScore > 15.0 && reasons.none { it.contains(track.genre) }) {
                score += (gScore * 0.35).coerceAtMost(22.0)
                reasons.add("Любимый вами жанр ${track.genre}")
            } else if (gScore < -15.0) {
                score += (gScore * 0.4).coerceAtLeast(-25.0)
            }

            val aScore = artistAffinity[track.artistName] ?: 0.0
            if (aScore > 15.0 && reasons.none { it.contains(track.artistName) }) {
                score += (aScore * 0.35).coerceAtMost(20.0)
                if (track.artistName in favoriteArtistNames) {
                    reasons.add("Ваш любимый исполнитель ${track.artistName}")
                } else {
                    reasons.add("Вы часто слушаете ${track.artistName}")
                }
            }

            val albScore = albumAffinity[track.albumId] ?: 0.0
            if (albScore >= 18.0 && reasons.size < 2) {
                score += (albScore * 0.3).coerceAtMost(14.0)
                reasons.add("Из часто прослушиваемого альбома «${track.albumTitle}»")
            }

            // 7. Diversity & New Artist Discovery (0 = familiar, 100 = discovery)
            val isNewArtistForUser = track.artistName !in knownArtists
            val artistMeta = allArtistsById[track.artistId]
            val discoveryFactor = (settings.diversityPercent - 50) / 50.0 // -1.0 .. +1.0

            if (isNewArtistForUser) {
                score += discoveryFactor * 24.0
                if (settings.diversityPercent >= 60) {
                    reasons.add("Открытие нового исполнителя (разнообразие ${settings.diversityPercent}%)")
                }
            } else {
                score -= discoveryFactor * 14.0
            }

            if (artistMeta?.isEmergingArtist == true && settings.diversityPercent >= 50) {
                score += 8.0
            }

            // 8. Natural language prompt keyword boost
            if (!settings.naturalLanguagePrompt.isNullOrBlank()) {
                val words = settings.naturalLanguagePrompt.lowercase().split(" ", ",", ".")
                    .filter { it.length >= 3 }
                val matchedTag = (track.moodTags + listOf(track.genre, track.subgenre, track.artistName)).firstOrNull { tag ->
                    tag.isNotBlank() && words.any { w -> tag.lowercase().contains(w) || w.contains(tag.lowercase()) }
                }
                if (matchedTag != null) {
                    score += 28.0
                    reasons.add("Соответствует запросу «$matchedTag»")
                }
            }

            // Prefer real remote tracks slightly over synthesized demo tracks when online
            if (!track.isSynthesizedDemo) {
                score += 4.0
            }

            val normalizedMatch = score.roundToInt().coerceIn(52, 99)
            val primaryReason = reasons.distinct().take(2).joinToString(" • ")
                .ifBlank {
                    if (likedTracks.isNotEmpty()) {
                        "Подобрано на основе ваших лайков и жанра ${track.genre}"
                    } else {
                        "Рекомендация Music Form • ${track.genre} (${track.bpm} BPM)"
                    }
                }

            RecommendationItem(
                track = track,
                matchScorePercent = normalizedMatch,
                reasonRu = primaryReason,
                isNewArtistDiscovery = isNewArtistForUser
            )
        }

        return scored
            .sortedByDescending { it.matchScorePercent }
            .take(limit)
    }
}
