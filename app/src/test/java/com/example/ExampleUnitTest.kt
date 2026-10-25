package com.example

import com.example.data.ai.MusicFormAiService
import com.example.data.catalog.MusicFormOpenCatalogProvider
import com.example.data.local.ListeningHistoryEntity
import com.example.data.model.MusicFormGenresTaxonomy
import com.example.data.model.RepeatModeState
import com.example.data.model.SearchFilter
import com.example.data.model.SearchSortOrder
import com.example.data.model.StreamUrlResult
import com.example.data.model.WaveMood
import com.example.data.model.WaveSettings
import com.example.data.remote.ItunesTrackDto
import com.example.data.remote.JamendoTrackDto
import com.example.data.remote.RemoteMusicCatalogClient
import com.example.domain.recommendation.MyWaveRecommendationEngine
import com.example.player.MusicPlayerController
import com.example.player.SynthesizedAudioGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExampleUnitTest {

    private val catalog = MusicFormOpenCatalogProvider()
    private val recommendationEngine = MyWaveRecommendationEngine(catalog)
    private val aiService = MusicFormAiService(catalog, enableNetworkCalls = false)
    private val remoteClient = RemoteMusicCatalogClient(jamendoClientIdOverride = "")

    @Test
    fun `taxonomy contains all 29 requested musical genres with subgenres moods and curated collections`() {
        val categories = MusicFormGenresTaxonomy.allCategories
        assertEquals(29, categories.size)

        val expectedGenresRu = listOf(
            "Поп",
            "Рок и альтернативный рок",
            "Хип-хоп и рэп",
            "R&B и соул",
            "Электронная музыка",
            "EDM и техно",
            "Драм-н-бейс",
            "Джаз",
            "Блюз",
            "Классическая музыка",
            "Лоу-фай",
            "Инди",
            "Метал",
            "Панк",
            "Фанк и диско",
            "Регги",
            "Кантри",
            "Фолк",
            "K-pop",
            "J-pop",
            "Аниме-саундтреки",
            "Саундтреки из фильмов и игр",
            "Эмбиент",
            "Чилл и релакс",
            "Медитативная музыка",
            "Акустическая музыка",
            "Ретро и музыка прошлых десятилетий",
            "Русская музыка",
            "Зарубежные хиты"
        )
        val actualTitles = categories.map { it.titleRu }
        expectedGenresRu.forEach { expected ->
            assertTrue("Missing genre: $expected", expected in actualTitles)
        }
        categories.forEach { cat ->
            assertTrue(cat.subgenres.isNotEmpty())
            assertTrue(cat.moods.isNotEmpty())
            assertTrue(cat.curatedPlaylists.isNotEmpty())
            assertTrue(cat.catalogTrackEstimate > 0)
        }
    }

    @Test
    fun `remote catalog client maps iTunes and Jamendo DTOs into real streamable tracks without synthesis`() {
        val itunesDto = ItunesTrackDto(
            trackId = 77889900L,
            trackName = "Midnight Tokyo City",
            artistName = "Hiroshi Synth",
            artistId = 5544L,
            collectionName = "Neo Shibuya Nights",
            collectionId = 6677L,
            previewUrl = "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview123/v4/sample.m4a",
            artworkUrl100 = "https://is1-ssl.mzstatic.com/image/thumb/Music126/100x100bb.jpg",
            trackTimeMillis = 214_000L,
            primaryGenreName = "J-Pop",
            releaseDate = "2025-11-10T08:00:00Z"
        )

        val mappedItunes = remoteClient.mapItunesDtoToTrack(
            dto = itunesDto,
            canonicalGenreRu = "J-pop",
            subgenreRu = "Shibuya-kei",
            primaryHex = "#536DFE",
            secondaryHex = "#FF4081"
        )
        assertNotNull(mappedItunes)
        assertEquals("itunes_77889900", mappedItunes!!.id)
        assertEquals("Midnight Tokyo City", mappedItunes.title)
        assertEquals("J-pop", mappedItunes.genre)
        assertEquals("Shibuya-kei", mappedItunes.subgenre)
        assertFalse(mappedItunes.isSynthesizedDemo)
        assertFalse(mappedItunes.isDownloadAllowed)
        assertTrue(mappedItunes.coverUrl!!.contains("600x600bb"))
        assertEquals(
            "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview123/v4/sample.m4a",
            mappedItunes.streamUrl
        )

        val jamendoDto = JamendoTrackDto(
            id = "991122",
            name = "Cyberpunk Odyssey",
            duration = 198,
            artistId = "4411",
            artistName = "Nova Pulse",
            albumName = "Future Grid",
            albumId = "8811",
            releaseDate = "2026-01-15",
            albumImage = "https://usercontent.jamendo.com/cover.jpg",
            audio = "https://prod-1.storage.jamendo.com/track/991122/mp32",
            audiodownload = "https://prod-1.storage.jamendo.com/download/991122",
            audioDownloadAllowed = true,
            licenseCcUrl = "https://creativecommons.org/licenses/by-nc-sa/4.0/"
        )

        val mappedJamendo = remoteClient.mapJamendoDtoToTrack(
            dto = jamendoDto,
            fallbackGenreRu = "Электронная музыка",
            subgenreRu = "Synthwave"
        )
        assertNotNull(mappedJamendo)
        assertEquals("jam_991122", mappedJamendo!!.id)
        assertFalse(mappedJamendo.isSynthesizedDemo)
        assertTrue(mappedJamendo.isDownloadAllowed)
        assertEquals("https://prod-1.storage.jamendo.com/track/991122/mp32", mappedJamendo.streamUrl)

        // Ingest into catalog and verify resolveStreamUrl returns HTTPS directly without WAV synthesis
        catalog.ingestRemoteTracks(listOf(mappedItunes, mappedJamendo))
        val tempDir = Files.createTempDirectory("remote_stream_test").toFile()
        try {
            val streamResult = catalog.resolveStreamUrl(mappedItunes.id, tempDir)
            assertTrue(streamResult is StreamUrlResult.Success)
            val resolvedSuccess = streamResult as StreamUrlResult.Success
            assertEquals(mappedItunes.streamUrl, resolvedSuccess.uriString)
            // Ensure no WAV file was synthesized for the remote track
            val possibleWav = File(File(tempDir, "music_form_streams"), "${mappedItunes.id}.wav")
            assertFalse(possibleWav.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `player controller supports play pause seek volume repeat and shuffle`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = MusicPlayerController(
            appContext = null,
            catalogProvider = catalog,
            scope = scope
        )

        val initialTrack = controller.state.value.currentTrack
        assertNotNull(initialTrack)

        controller.play()
        assertTrue(controller.state.value.isPlaying)

        controller.seekTo(12_500L)
        assertEquals(12_500L, controller.state.value.currentPositionMs)

        controller.pause()
        assertFalse(controller.state.value.isPlaying)

        // Repeat modes cycle: OFF -> ALL -> ONE -> OFF
        assertEquals(RepeatModeState.OFF, controller.state.value.repeatMode)
        controller.cycleRepeatMode()
        assertEquals(RepeatModeState.ALL, controller.state.value.repeatMode)
        controller.cycleRepeatMode()
        assertEquals(RepeatModeState.ONE, controller.state.value.repeatMode)
        controller.cycleRepeatMode()
        assertEquals(RepeatModeState.OFF, controller.state.value.repeatMode)

        // Shuffle toggle
        assertFalse(controller.state.value.isShuffleEnabled)
        controller.toggleShuffle()
        assertTrue(controller.state.value.isShuffleEnabled)

        // Volume clamping
        controller.setVolume(0.65f)
        assertEquals(0.65f, controller.state.value.volume, 0.001f)
    }

    @Test
    fun `player queue supports add remove reorder and track transitions`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = MusicPlayerController(
            appContext = null,
            catalogProvider = catalog,
            scope = scope
        )

        val allTracks = catalog.getAllTracks()
        val extraTrack = allTracks.last()

        controller.addToQueue(extraTrack)
        assertTrue(controller.state.value.queue.any { it.id == extraTrack.id })

        val beforeOrder = controller.state.value.queue.map { it.id }
        controller.moveTrackInQueue(0, 2)
        val afterOrder = controller.state.value.queue.map { it.id }
        assertEquals(beforeOrder[0], afterOrder[2])

        val currentIdxBeforeNext = controller.state.value.currentQueueIndex
        controller.skipToNext()
        delay(50)
        assertTrue(controller.state.value.currentQueueIndex != currentIdxBeforeNext)

        controller.removeFromQueue(extraTrack.id)
        assertFalse(controller.state.value.queue.any { it.id == extraTrack.id })
    }

    @Test
    fun `player state restoration restores track position queue repeat and shuffle`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = MusicPlayerController(
            appContext = null,
            catalogProvider = catalog,
            scope = scope
        )

        val targetTrack = catalog.getTrackById("tr_shibuya_overdrive")!!
        val customQueue = listOf("tr_neon_horizon", "tr_shibuya_overdrive", "tr_hyperdrive_174")

        controller.restoreState(
            trackId = targetTrack.id,
            positionMs = 9_400L,
            queueIds = customQueue,
            shuffle = true,
            repeatMode = RepeatModeState.ONE,
            volume = 0.72f
        )

        val restored = controller.state.value
        assertEquals("tr_shibuya_overdrive", restored.currentTrack?.id)
        assertEquals(9_400L, restored.currentPositionMs)
        assertEquals(1, restored.currentQueueIndex)
        assertTrue(restored.isShuffleEnabled)
        assertEquals(RepeatModeState.ONE, restored.repeatMode)
        assertEquals(0.72f, restored.volume, 0.001f)
    }

    @Test
    fun `my wave recommendation engine respects likes dislikes skips mood excluded artists and excluded genres`() {
        val history = listOf(
            ListeningHistoryEntity(
                trackId = "tr_neon_horizon",
                trackTitle = "Neon Horizon",
                artistName = "Aether Pulse",
                genre = "Synthwave",
                liked = true,
                disliked = false,
                playCount = 5,
                skipCount = 0,
                totalListenMs = 115_000L,
                lastPlayedEpochMs = System.currentTimeMillis()
            ),
            ListeningHistoryEntity(
                trackId = "tr_hyperdrive_174",
                trackTitle = "Hyperdrive 174",
                artistName = "Vector Zero",
                genre = "Drum & Bass",
                liked = false,
                disliked = true,
                playCount = 0,
                skipCount = 3,
                totalListenMs = 2_000L,
                lastPlayedEpochMs = System.currentTimeMillis()
            )
        )

        val settings = WaveSettings(
            seedTrackId = "tr_neon_horizon",
            mood = WaveMood.NIGHT_DRIVE,
            diversityPercent = 35,
            excludedArtists = setOf("Katan V"),
            excludedGenres = setOf("Lo-Fi", "Лоу-фай")
        )

        val recommendations = recommendationEngine.generateWaveQueue(
            settings = settings,
            history = history,
            preferredGenres = listOf("Synthwave"),
            favoriteArtistNames = setOf("Aether Pulse")
        )

        assertTrue(recommendations.isNotEmpty())
        // Disliked track must be excluded
        assertFalse(recommendations.any { it.track.id == "tr_hyperdrive_174" })
        // Excluded artist must be filtered out
        assertFalse(recommendations.any { it.track.artistName.equals("Katan V", ignoreCase = true) })
        // Excluded genre must be filtered out
        assertFalse(recommendations.any { it.track.genre.equals("Lo-Fi", ignoreCase = true) })
        // Top recommendation should have high match score and a clear Russian explanation
        val top = recommendations.first()
        assertTrue(top.matchScorePercent >= 80)
        assertTrue(top.reasonRu.isNotBlank())
    }

    @Test
    fun `catalog search supports filters sort orders query suggestions and similar suggestions on empty match`() {
        val allResult = catalog.searchCatalog("Synthwave", SearchFilter.ALL, SearchSortOrder.POPULARITY)
        assertTrue(allResult.tracks.isNotEmpty())
        assertTrue(allResult.artists.isNotEmpty())
        assertTrue(allResult.albums.isNotEmpty())
        assertTrue(allResult.querySuggestions.isNotEmpty())

        // Verify popularity sorting is descending
        val popScores = allResult.tracks.map { it.popularityScore }
        assertEquals(popScores.sortedDescending(), popScores)

        // Verify duration sorting
        val durationResult = catalog.searchCatalog("a", SearchFilter.TRACKS, SearchSortOrder.DURATION)
        val durations = durationResult.tracks.map { it.durationMs }
        assertEquals(durations.sortedDescending(), durations)

        val artistOnly = catalog.searchCatalog("Kora Vel", SearchFilter.ARTISTS)
        assertEquals(1, artistOnly.artists.size)
        assertTrue(artistOnly.tracks.isEmpty())

        val playlistSearch = catalog.searchCatalog("Ночной драйв", SearchFilter.PLAYLISTS)
        assertTrue(playlistSearch.matchingPlaylists.isNotEmpty())

        // Empty query result provides similar suggestions
        val emptyMatch = catalog.searchCatalog("nonexistent_xyz_query_999", SearchFilter.ALL)
        assertTrue(emptyMatch.tracks.isEmpty())
        assertTrue(emptyMatch.similarSuggestions.isNotEmpty())
    }

    @Test
    fun `catalog stream resolver handles valid wav synthesis expired token region lock and offline error`() {
        val tempDir: File = Files.createTempDirectory("music_form_test").toFile()
        try {
            val validStream = catalog.resolveStreamUrl("tr_neon_horizon", tempDir)
            assertTrue(validStream is StreamUrlResult.Success)
            val wavFile = File(File(tempDir, "music_form_streams"), "tr_neon_horizon.wav")
            assertTrue(wavFile.exists())
            assertTrue(wavFile.length() > 1000L)

            val expired = catalog.resolveStreamUrl(
                trackId = "tr_neon_horizon",
                cacheDir = tempDir,
                simulateExpiredToken = true
            )
            assertTrue(expired is StreamUrlResult.ExpiredLink)

            val regionLocked = catalog.resolveStreamUrl(
                trackId = "tr_neon_horizon",
                cacheDir = tempDir,
                simulateRegionLock = true
            )
            assertTrue(regionLocked is StreamUrlResult.RegionRestricted)

            val networkError = catalog.resolveStreamUrl(
                trackId = "tr_neon_horizon",
                cacheDir = tempDir,
                simulateOfflineError = true
            )
            assertTrue(networkError is StreamUrlResult.NetworkError)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `synthesized audio generator creates valid RIFF WAVE header and supports cache cleanup`() {
        val tempDir: File = Files.createTempDirectory("wav_header_test").toFile()
        try {
            val track = catalog.getAllTracks().first { it.isSynthesizedDemo }
            val wav = SynthesizedAudioGenerator.ensurePlayableAudioFile(tempDir, track)
            assertTrue(SynthesizedAudioGenerator.validateWavFile(wav))

            val header = wav.inputStream().use { stream ->
                val bytes = ByteArray(12)
                stream.read(bytes)
                String(bytes, Charsets.US_ASCII)
            }
            assertTrue(header.startsWith("RIFF"))
            assertTrue(header.endsWith("WAVE"))

            // Corrupted header must fail validation
            val corruptedFile = File(tempDir, "corrupted.wav")
            corruptedFile.writeBytes(ByteArray(64) { 0 })
            assertFalse(SynthesizedAudioGenerator.validateWavFile(corruptedFile))

            val deletedCount = SynthesizedAudioGenerator.clearCachedStreams(tempDir)
            assertEquals(1, deletedCount)
            assertFalse(wav.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `player controller fires early skip callback when skipping before 10 seconds and releases cleanly`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = MusicPlayerController(
            appContext = null,
            catalogProvider = catalog,
            scope = scope
        )

        val events = mutableListOf<Triple<String, Long, Boolean>>()
        controller.setOnTrackCompletedOrSkippedListener { track, listenedMs, wasSkippedEarly ->
            events.add(Triple(track.id, listenedMs, wasSkippedEarly))
        }

        val initialId = controller.state.value.currentTrack!!.id
        controller.seekTo(4_200L) // < 10_000L => early skip
        controller.skipToNext()

        assertEquals(1, events.size)
        assertEquals(initialId, events.first().first)
        assertEquals(4_200L, events.first().second)
        assertTrue(events.first().third)

        controller.release()
        assertFalse(controller.state.value.isPlaying)
    }

    @Test
    fun `my wave recommendation engine shifts novel genre bonuses with diversity slider`() {
        val history = listOf(
            ListeningHistoryEntity(
                trackId = "tr_neon_horizon",
                trackTitle = "Neon Horizon",
                artistName = "Aether Pulse",
                genre = "Synthwave",
                liked = true,
                disliked = false,
                playCount = 6,
                skipCount = 0,
                totalListenMs = 140_000L,
                lastPlayedEpochMs = System.currentTimeMillis()
            )
        )

        val lowDiversityQueue = recommendationEngine.generateWaveQueue(
            settings = WaveSettings(mood = WaveMood.ENERGETIC, diversityPercent = 10),
            history = history,
            preferredGenres = listOf("Synthwave")
        )
        val highDiversityQueue = recommendationEngine.generateWaveQueue(
            settings = WaveSettings(mood = WaveMood.ENERGETIC, diversityPercent = 95),
            history = history,
            preferredGenres = listOf("Synthwave")
        )

        val unexploredLow = lowDiversityQueue.first { it.track.genre != "Synthwave" }
        val unexploredHigh = highDiversityQueue.first { it.track.id == unexploredLow.track.id }
        assertTrue(unexploredHigh.matchScorePercent >= unexploredLow.matchScorePercent)
    }

    @Test
    fun `music form ai grounds recommendations in real catalog and reports honest empty matches`() = runBlocking {
        val catalogIds = catalog.getAllTracks().map { it.id }.toSet()

        val nightDriveResponse = aiService.curateMusicByPrompt("Подбери музыку для ночной поездки")
        assertFalse(nightDriveResponse.isHonestEmptyMatch)
        assertTrue(nightDriveResponse.recommendedTracks.isNotEmpty())
        assertTrue(nightDriveResponse.recommendedTracks.all { it.track.id in catalogIds })
        assertTrue(nightDriveResponse.sourceChannelRu.isNotBlank())

        val rawAiJson = """
            ```json
            {
              "summaryRu": "Тестовая подборка синтвейва",
              "targetGenres": ["Synthwave"],
              "moodTags": ["ночь", "неон"],
              "minBpm": 95,
              "maxBpm": 135,
              "discoverNewArtists": false
            }
            ```
        """.trimIndent()
        val parsedCriteria = aiService.parseCriteriaJson(rawAiJson)
        assertNotNull(parsedCriteria)
        assertEquals(listOf("Synthwave"), parsedCriteria?.targetGenres)
        assertEquals(95, parsedCriteria?.minBpm)

        val missingGenreResponse = aiService.curateMusicByPrompt("Включи оперу XVIII века")
        assertTrue(missingGenreResponse.isHonestEmptyMatch)
        assertTrue(missingGenreResponse.recommendedTracks.isEmpty())
        assertNull(missingGenreResponse.suggestedMood)
    }
}
