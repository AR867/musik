package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ThumbDownOffAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.data.model.WaveMood
import com.example.player.PlayerUiState
import com.example.ui.components.ExpressiveAlbumCover
import com.example.ui.components.GlowingSoundwaveVisualizer
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.viewmodel.MusicFormUiState

@Composable
fun MyWaveScreen(
    uiState: MusicFormUiState,
    playerState: PlayerUiState,
    onLaunchWave: (String?, String?, String?, WaveMood?) -> Unit,
    onUpdateDiversity: (Int) -> Unit,
    onToggleExcludeArtist: (String) -> Unit,
    onToggleExcludeGenre: (String) -> Unit = {},
    onClearWaveSeeds: () -> Unit,
    onResetRecommendationHistory: () -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onDislikeTrack: (Track) -> Unit,
    onPlayRecommendation: (Track, String) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeTrack = playerState.currentTrack ?: uiState.waveRecommendations.firstOrNull()?.track
    val activeReason = playerState.waveReasonBanner
        ?: uiState.waveRecommendations.firstOrNull()?.reasonRu
        ?: "Персональный поток на основе ваших предпочтений"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("my_wave_screen"),
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Glowing My Wave Console Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0E1B38),
                                    Color(0xFF1F1244),
                                    Color(0xFF111626)
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "МОЯ ВОЛНА • MUSIC FORM RADIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (activeTrack != null) {
                            ExpressiveAlbumCover(
                                primaryHex = activeTrack.coverColorPrimaryHex,
                                secondaryHex = activeTrack.coverColorSecondaryHex,
                                title = activeTrack.title,
                                size = 96.dp,
                                cornerRadius = 22.dp,
                                coverUrl = activeTrack.coverUrl
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Text(
                            text = activeTrack?.title ?: "Ваша волна готова",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White
                        )
                        Text(
                            text = activeTrack?.let { "${it.artistName} • ${it.genre} • ${it.bpm} BPM" } ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFCFD8EE)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Reason Explanation Banner
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("wave_reason_banner")
                        ) {
                            Text(
                                text = "✦ Почему играет: $activeReason",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        GlowingSoundwaveVisualizer(
                            isPlaying = playerState.isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Wave Transport Controls: Dislike, Play/Pause, Like, Skip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (activeTrack != null) {
                                IconButton(
                                    onClick = { onDislikeTrack(activeTrack) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("wave_dislike_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ThumbDownOffAlt,
                                        contentDescription = "Не нравится / Пропустить",
                                        tint = Color(0xFFFF8A80)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (!playerState.isWaveModeActive) {
                                        onLaunchWave(
                                            uiState.waveSettings.seedTrackId,
                                            uiState.waveSettings.seedArtistId,
                                            uiState.waveSettings.seedGenre,
                                            uiState.waveSettings.mood
                                        )
                                    } else {
                                        onTogglePlayPause()
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianBlack
                                ),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                                modifier = Modifier.testTag("wave_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (playerState.isPlaying && playerState.isWaveModeActive) {
                                        Icons.Filled.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                    contentDescription = "Воспроизвести или пауза"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (playerState.isPlaying && playerState.isWaveModeActive) "Пауза" else "Слушать волну",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            if (activeTrack != null) {
                                val isLiked = activeTrack.id in uiState.likedTrackIds
                                IconButton(
                                    onClick = { onToggleLike(activeTrack) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("wave_like_button")
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = "Нравится",
                                        tint = if (isLiked) Color(0xFFFF4081) else Color.White
                                    )
                                }

                                IconButton(
                                    onClick = onSkipNext,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("wave_skip_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = "Следующий трек",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Wave Customization: Mood, Genre, Favorite Track & Artist Seeds
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Настройка настроения волны",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (uiState.waveSettings.seedTrackId != null ||
                        uiState.waveSettings.seedArtistId != null ||
                        uiState.waveSettings.seedGenre != null
                    ) {
                        OutlinedButton(
                            onClick = onClearWaveSeeds,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Сбросить фильтры")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(WaveMood.entries) { mood ->
                        val selected = uiState.waveSettings.mood == mood
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onLaunchWave(
                                    uiState.waveSettings.seedTrackId,
                                    uiState.waveSettings.seedArtistId,
                                    uiState.waveSettings.seedGenre,
                                    mood
                                )
                            },
                            label = { Text("${mood.titleRu} • ${mood.subtitleRu}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                selectedLabelColor = ElectricCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "По жанру (все 29 жанров):",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.genres) { genre ->
                        val selected = uiState.waveSettings.seedGenre == genre
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val nextGenre = if (selected) null else genre
                                onLaunchWave(
                                    uiState.waveSettings.seedTrackId,
                                    uiState.waveSettings.seedArtistId,
                                    nextGenre,
                                    uiState.waveSettings.mood
                                )
                            },
                            label = { Text(genre) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "На основе любимого трека:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                val seedCandidates = uiState.allTracks.filter { it.id in uiState.likedTrackIds }
                    .ifEmpty { uiState.allTracks.take(8) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seedCandidates, key = { "seed_${it.id}" }) { track ->
                        val selected = uiState.waveSettings.seedTrackId == track.id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val nextSeed = if (selected) null else track.id
                                onLaunchWave(
                                    nextSeed,
                                    uiState.waveSettings.seedArtistId,
                                    uiState.waveSettings.seedGenre,
                                    uiState.waveSettings.mood
                                )
                            },
                            label = { Text("${track.title} — ${track.artistName}") }
                        )
                    }
                }
            }
        }

        // 3. Diversity Slider, Artist Exclusion, Genre Exclusion & Reset Recommendation History
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Разнообразие рекомендаций: ${uiState.waveSettings.diversityPercent}%",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.waveSettings.diversityPercent >= 60) {
                            "Фокус на открытии новых исполнителей и редких треков"
                        } else if (uiState.waveSettings.diversityPercent <= 35) {
                            "Фокус на проверенных хитах и любимых артистах"
                        } else {
                            "Оптимальный баланс знакомых треков и новых открытий"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = uiState.waveSettings.diversityPercent.toFloat(),
                        onValueChange = { onUpdateDiversity(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("wave_diversity_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = null,
                            tint = NeonViolet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Исключить исполнителей из волны:",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.artists.take(24), key = { "excl_${it.id}" }) { artist ->
                            val isExcluded = artist.name in uiState.waveSettings.excludedArtists
                            FilterChip(
                                selected = isExcluded,
                                onClick = { onToggleExcludeArtist(artist.name) },
                                label = {
                                    Text(if (isExcluded) "✕ ${artist.name}" else artist.name)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.24f),
                                    selectedLabelColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("exclude_artist_${artist.id}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = null,
                            tint = NeonViolet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Исключить жанры из волны:",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.genres, key = { "excl_gen_$it" }) { genre ->
                            val isExcluded = genre in uiState.waveSettings.excludedGenres
                            FilterChip(
                                selected = isExcluded,
                                onClick = { onToggleExcludeGenre(genre) },
                                label = {
                                    Text(if (isExcluded) "✕ $genre" else genre)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.24f),
                                    selectedLabelColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("exclude_genre_$genre")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onResetRecommendationHistory,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_recommendation_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сбросить историю рекомендаций и исключения")
                    }
                }
            }
        }

        // 4. Upcoming Wave Recommendations with Explanations
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Следующие в вашей волне (${uiState.waveRecommendations.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Для каждого трека указана причина подбора алгоритмом",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(uiState.waveRecommendations, key = { "wave_rec_${it.track.id}" }) { rec ->
            TrackRowItem(
                track = rec.track,
                isCurrentTrack = playerState.currentTrack?.id == rec.track.id,
                isPlaying = playerState.isPlaying,
                isLiked = rec.track.id in uiState.likedTrackIds,
                isDownloaded = rec.track.id in uiState.downloadedTrackIds,
                reasonBadge = "${rec.matchScorePercent}% • ${rec.reasonRu}",
                onPlayClick = { onPlayRecommendation(rec.track, rec.reasonRu) },
                onLikeClick = { onToggleLike(rec.track) },
                onAddToPlaylistClick = { onAddTrackToPlaylist(rec.track) },
                onDownloadClick = if (rec.track.isDownloadAllowed) {
                    { onDownloadTrack(rec.track) }
                } else null,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
