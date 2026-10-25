package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.catalog.MusicFormOpenCatalogProvider
import com.example.data.local.CachedTrackEntity
import com.example.data.local.DownloadedTrackEntity
import com.example.data.local.FavoriteArtistEntity
import com.example.data.local.ListeningHistoryEntity
import com.example.data.local.LocalPlaylistEntity
import com.example.data.local.MusicFormDao
import com.example.data.local.MusicFormDatabase
import com.example.data.local.SavedAlbumEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.model.AppThemeMode
import com.example.data.model.RepeatModeState
import com.example.data.model.Track
import com.example.player.SynthesizedAudioGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: MusicFormDatabase
    private lateinit var dao: MusicFormDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MusicFormDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.musicFormDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `verify app name and branding resources`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Music Form", appName)
        val slogan = context.getString(R.string.app_slogan)
        assertEquals("Твоя музыка. Твоя волна. Твой мир", slogan)
    }

    @Test
    fun `room caches remote catalog tracks favorite artists saved albums and licensed downloads`() = runBlocking {
        val remoteTrack = Track(
            id = "itunes_100200300",
            title = "Tokyo Drift Cyber",
            artistId = "art_tokyo",
            artistName = "Kenji Wave",
            albumId = "alb_tokyo",
            albumTitle = "Shibuya 2099",
            genre = "J-pop",
            subgenre = "City Pop",
            durationMs = 205_000L,
            bpm = 128,
            energy = 0.85f,
            isNewRelease = true,
            popularityScore = 94,
            releaseYear = 2026,
            moodTags = listOf("j-pop", "аниме", "драйв"),
            coverColorPrimaryHex = "#FF4081",
            coverColorSecondaryHex = "#536DFE",
            coverUrl = "https://example.com/cover600x600.jpg",
            streamUrl = "https://example.com/audio.m4a",
            downloadUrl = null,
            isDownloadAllowed = false,
            isSynthesizedDemo = false,
            sourceProvider = "Apple iTunes Public Catalog",
            licenseInfo = "Apple iTunes Official Preview Stream"
        )

        dao.upsertCachedTracks(listOf(CachedTrackEntity.fromDomainTrack(remoteTrack)))
        assertEquals(1, dao.getAllCachedTracksOnce().size)

        val cachedTracks = dao.observeCachedTracks().first()
        assertEquals(1, cachedTracks.size)
        val restored = cachedTracks.first().toDomainTrack()
        assertEquals("itunes_100200300", restored.id)
        assertEquals("J-pop", restored.genre)
        assertEquals("https://example.com/audio.m4a", restored.streamUrl)
        assertFalse(restored.isSynthesizedDemo)

        // Favorite Artist subscription
        dao.upsertFavoriteArtist(
            FavoriteArtistEntity(
                artistId = "art_tokyo",
                name = "Kenji Wave",
                primaryGenre = "J-pop",
                bio = "Electronic J-pop producer",
                monthlyListeners = 240_000,
                accentHex = "#FF4081",
                avatarUrl = "https://example.com/avatar.jpg",
                sourceProvider = "Apple iTunes Public Catalog",
                addedAtEpochMs = 1000L
            )
        )
        val favArtists = dao.observeFavoriteArtists().first()
        assertEquals(1, favArtists.size)
        assertEquals("Kenji Wave", favArtists.first().name)

        // Saved Album
        dao.upsertSavedAlbum(
            SavedAlbumEntity(
                albumId = "alb_tokyo",
                title = "Shibuya 2099",
                artistId = "art_tokyo",
                artistName = "Kenji Wave",
                genre = "J-pop",
                releaseYear = 2026,
                coverColorPrimaryHex = "#FF4081",
                coverColorSecondaryHex = "#536DFE",
                trackIdsCsv = "itunes_100200300",
                description = "Tokyo Electronic Album",
                coverUrl = "https://example.com/cover600x600.jpg",
                sourceProvider = "Apple iTunes Public Catalog",
                savedAtEpochMs = 2000L
            )
        )
        val savedAlbums = dao.observeSavedAlbums().first()
        assertEquals(1, savedAlbums.size)
        assertEquals("Shibuya 2099", savedAlbums.first().title)

        // Licensed Track Download record
        dao.upsertDownloadedTrack(
            DownloadedTrackEntity(
                trackId = "jamendo_555",
                title = "Open Sky",
                artistName = "Aurora CC",
                albumTitle = "Northern Lights",
                genre = "Эмбиент",
                durationMs = 195_000L,
                localFilePath = "/data/user/0/com.aistudio.musicform.qxkplm/files/licensed_downloads/jamendo_555.mp3",
                licenseInfo = "Jamendo Creative Commons",
                coverUrl = "https://example.com/jamendo.jpg",
                sourceProvider = "Jamendo Creative Commons",
                downloadedAtEpochMs = 3000L
            )
        )
        val downloads = dao.observeDownloadedTracks().first()
        assertEquals(1, downloads.size)
        assertEquals("jamendo_555", downloads.first().trackId)
    }

    @Test
    fun `room persists liked tracks listening history and supports clearing`() = runBlocking {
        val item1 = ListeningHistoryEntity(
            trackId = "tr_neon_horizon",
            trackTitle = "Neon Horizon",
            artistName = "Aether Pulse",
            genre = "Synthwave",
            liked = true,
            disliked = false,
            playCount = 3,
            skipCount = 0,
            totalListenMs = 72_000L,
            lastPlayedEpochMs = 1000L
        )
        val item2 = ListeningHistoryEntity(
            trackId = "tr_electric_form",
            trackTitle = "Electric Form",
            artistName = "Kora Vel",
            genre = "Electronic",
            liked = false,
            disliked = false,
            playCount = 1,
            skipCount = 1,
            totalListenMs = 8_000L,
            lastPlayedEpochMs = 2000L
        )

        dao.upsertHistory(item1)
        dao.upsertHistory(item2)

        val history = dao.observeHistory().first()
        assertEquals(2, history.size)
        assertEquals("tr_electric_form", history.first().trackId)

        val liked = dao.observeLikedTracks().first()
        assertEquals(1, liked.size)
        assertEquals("tr_neon_horizon", liked.first().trackId)

        dao.clearAllHistory()
        assertTrue(dao.observeHistory().first().isEmpty())
    }

    @Test
    fun `room supports custom playlist creation track reordering and deletion`() = runBlocking {
        val playlist = LocalPlaylistEntity(
            playlistId = "pl_cyber_night",
            title = "Ночной Киберпанк",
            description = "Треки для поездки по ночному городу",
            trackIdsCsv = "tr_neon_horizon,tr_chrome_highway,tr_shibuya_overdrive",
            coverTintHex = "#00E5FF",
            updatedAtEpochMs = System.currentTimeMillis()
        )
        dao.upsertLocalPlaylist(playlist)

        val savedPlaylists = dao.observeLocalPlaylists().first()
        assertEquals(1, savedPlaylists.size)
        assertEquals(
            listOf("tr_neon_horizon", "tr_chrome_highway", "tr_shibuya_overdrive"),
            savedPlaylists.first().trackIdsList()
        )

        val reordered = playlist.copy(
            trackIdsCsv = "tr_shibuya_overdrive,tr_neon_horizon,tr_chrome_highway",
            updatedAtEpochMs = System.currentTimeMillis() + 100
        )
        dao.upsertLocalPlaylist(reordered)

        val updated = dao.observeLocalPlaylists().first().first()
        assertEquals("tr_shibuya_overdrive", updated.trackIdsList().first())

        dao.deleteLocalPlaylist("pl_cyber_night")
        assertTrue(dao.observeLocalPlaylists().first().isEmpty())
    }

    @Test
    fun `room persists and clears search history queries`() = runBlocking {
        dao.insertSearchQuery(SearchHistoryEntity("Synthwave", 100L))
        dao.insertSearchQuery(SearchHistoryEntity("Aether Pulse", 200L))

        val queries = dao.observeSearchHistory().first()
        assertEquals(2, queries.size)
        assertEquals("Aether Pulse", queries.first().query)

        dao.deleteSearchQuery("Synthwave")
        val remaining = dao.observeSearchHistory().first()
        assertEquals(1, remaining.size)
        assertFalse(remaining.any { it.query == "Synthwave" })

        dao.clearSearchHistory()
        assertNotNull(dao.observeSearchHistory().first())
        assertTrue(dao.observeSearchHistory().first().isEmpty())
    }

    @Test
    fun `datastore persists theme wave settings excluded genres playback queue and clears all on account deletion`() = runBlocking {
        val prefs = UserPreferencesDataStore(context)
        prefs.setThemeMode(AppThemeMode.LIGHT)
        prefs.saveWaveSettings(
            diversity = 65,
            excludedArtists = setOf("Katan V"),
            excludedGenres = setOf("Метал", "Панк")
        )
        prefs.savePlayerState(
            trackId = "tr_shibuya_overdrive",
            positionMs = 14_200L,
            queueIds = listOf("tr_neon_horizon", "tr_shibuya_overdrive"),
            shuffle = true,
            repeatMode = RepeatModeState.ALL,
            volume = 0.8f
        )

        val saved = prefs.appStateFlow.first()
        assertEquals(AppThemeMode.LIGHT, saved.themeMode)
        assertEquals(65, saved.waveDiversity)
        assertTrue(saved.excludedArtistsCsv.contains("Katan V"))
        assertTrue(saved.excludedGenresCsv.contains("Метал"))
        assertEquals("tr_shibuya_overdrive", saved.lastTrackId)
        assertEquals(14_200L, saved.lastPositionMs)
        assertEquals("tr_neon_horizon,tr_shibuya_overdrive", saved.lastQueueCsv)
        assertTrue(saved.isShuffleEnabled)
        assertEquals(RepeatModeState.ALL, saved.repeatMode)

        val catalog = MusicFormOpenCatalogProvider()
        val tempCacheDir = Files.createTempDirectory("vm_delete_test").toFile()
        try {
            val wavFile = SynthesizedAudioGenerator.ensurePlayableAudioFile(
                tempCacheDir,
                catalog.getAllTracks().first { it.isSynthesizedDemo }
            )
            assertTrue(wavFile.exists())

            dao.clearAllHistory()
            dao.clearAllLocalPlaylists()
            dao.clearSearchHistory()
            dao.clearFavoriteArtists()
            dao.clearSavedAlbums()
            dao.clearDownloadedTracks()
            prefs.clearAll()
            SynthesizedAudioGenerator.clearCachedStreams(tempCacheDir)

            val afterClear = prefs.appStateFlow.first()
            assertEquals(AppThemeMode.DARK, afterClear.themeMode)
            assertEquals(50, afterClear.waveDiversity)
            assertTrue(afterClear.lastQueueCsv.isEmpty())
            assertTrue(afterClear.excludedGenresCsv.isEmpty())
            assertTrue(dao.observeLocalPlaylists().first().isEmpty())
            assertFalse(wavFile.exists())
        } finally {
            tempCacheDir.deleteRecursively()
        }
    }
}
