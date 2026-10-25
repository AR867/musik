package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiAssistantResponse
import com.example.data.ai.MusicFormAiService
import com.example.data.catalog.MusicCatalogProvider
import com.example.data.local.CachedTrackEntity
import com.example.data.local.DownloadedTrackEntity
import com.example.data.local.FavoriteArtistEntity
import com.example.data.local.ListeningHistoryEntity
import com.example.data.local.LocalPlaylistEntity
import com.example.data.local.MusicFormDao
import com.example.data.local.SavedAlbumEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.model.Album
import com.example.data.model.AppThemeMode
import com.example.data.model.Artist
import com.example.data.model.CatalogSearchResult
import com.example.data.model.GenreCategory
import com.example.data.model.MusicFormGenresTaxonomy
import com.example.data.model.RecommendationItem
import com.example.data.model.SearchFilter
import com.example.data.model.SearchSortOrder
import com.example.data.model.SubscriptionTier
import com.example.data.model.Track
import com.example.data.model.UserProfile
import com.example.data.model.WaveMood
import com.example.data.model.WaveSettings
import com.example.data.remote.RemoteMusicCatalogClient
import com.example.data.repository.MusicFormFirestoreRepository
import com.example.domain.recommendation.MyWaveRecommendationEngine
import com.example.player.MusicPlayerController
import com.example.player.PlayerUiState
import com.example.player.SynthesizedAudioGenerator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class MusicFormUiState(
    val currentUserId: String = "",
    val userDisplayName: String = "Слушатель Music Form",
    val userEmail: String = "",
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
    val isOfflineModeSimulated: Boolean = false,
    val cloudSyncStatusRu: String = "Облако синхронизировано (Firestore)",
    // Catalog
    val allTracks: List<Track> = emptyList(),
    val popularTracks: List<Track> = emptyList(),
    val newReleases: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<String> = emptyList(),
    val genreCategories: List<GenreCategory> = MusicFormGenresTaxonomy.allCategories,
    // Thematic Home Carousels
    val workoutTracks: List<Track> = emptyList(),
    val studyTracks: List<Track> = emptyList(),
    val roadTracks: List<Track> = emptyList(),
    val sleepRelaxTracks: List<Track> = emptyList(),
    val animeGamingTracks: List<Track> = emptyList(),
    // Genres Explorer State
    val selectedGenreCategory: GenreCategory? = null,
    val selectedSubgenre: String? = null,
    val selectedGenreMood: String? = null,
    val selectedGenreTracks: List<Track> = emptyList(),
    val genrePaginationOffset: Int = 0,
    val isCatalogLoadingMore: Boolean = false,
    // Library & Telemetry
    val historyItems: List<ListeningHistoryEntity> = emptyList(),
    val likedTrackIds: Set<String> = emptySet(),
    val dislikedTrackIds: Set<String> = emptySet(),
    val playlists: List<LocalPlaylistEntity> = emptyList(),
    val favoriteArtistIds: Set<String> = emptySet(),
    val favoriteArtistNames: Set<String> = emptySet(),
    val savedAlbumIds: Set<String> = emptySet(),
    val downloadedTrackIds: Set<String> = emptySet(),
    // My Wave
    val waveSettings: WaveSettings = WaveSettings(),
    val waveRecommendations: List<RecommendationItem> = emptyList(),
    // Search
    val searchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val searchSortOrder: SearchSortOrder = SearchSortOrder.RELEVANCE,
    val searchResult: CatalogSearchResult = CatalogSearchResult(""),
    val searchHistory: List<SearchHistoryEntity> = emptyList(),
    val isRemoteSearchLoading: Boolean = false,
    val remoteSearchOffset: Int = 0,
    // AI Assistant
    val aiPromptInput: String = "",
    val isAiLoading: Boolean = false,
    val lastAiResponse: AiAssistantResponse? = null,
    // Modals / Export
    val exportedJsonPreview: String? = null,
    val statusSnackbarRu: String? = null
)

class MusicFormViewModel(
    private val firestoreRepository: MusicFormFirestoreRepository?,
    private val dao: MusicFormDao,
    private val prefs: UserPreferencesDataStore,
    val catalogProvider: MusicCatalogProvider,
    val playerController: MusicPlayerController,
    private val aiService: MusicFormAiService,
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String,
    private val remoteCatalogClient: RemoteMusicCatalogClient = RemoteMusicCatalogClient(),
    private val filesDir: File? = null,
    private val enableInitialNetworkBootstrap: Boolean = true
) : ViewModel() {

    private val recommendationEngine = MyWaveRecommendationEngine(catalogProvider)
    private var searchDebounceJob: Job? = null

    private val _uiState = MutableStateFlow(
        buildInitialUiState(
            currentUserId = currentUserId,
            displayName = initialDisplayName,
            email = initialEmail,
            catalogProvider = catalogProvider
        )
    )
    val uiState: StateFlow<MusicFormUiState> = _uiState.asStateFlow()

    val playerState: StateFlow<PlayerUiState> = playerController.state

    val cloudProfileState: StateFlow<UserProfile?> = (
        firestoreRepository?.observeUserProfile(currentUserId)
            ?.catch { error ->
                Log.w("MusicFormVM", "Profile stream error", error)
                emit(null)
            }
            ?: MutableStateFlow(null)
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    init {
        playerController.setOnTrackCompletedOrSkippedListener { track, listenedMs, wasEarlySkip ->
            recordTrackPlayEvent(track, listenedMs, wasEarlySkip)
            persistPlayerSnapshot()
        }
        observeLocalAndCloudState()
        seedInitialPlaylistsIfNeeded()
        if (enableInitialNetworkBootstrap) {
            bootstrapMultiGenreOnlineCatalog()
        }
    }

    override fun onCleared() {
        playerController.release()
        super.onCleared()
    }

    private fun refreshCatalogSnapshotInState() {
        val all = catalogProvider.getAllTracks()
        val cats = catalogProvider.getGenreCategories()
        val currentSel = _uiState.value.selectedGenreCategory?.let { sel ->
            cats.find { it.id == sel.id } ?: sel
        }
        val filteredGenreTracks = if (currentSel != null) {
            filterTracksForGenreSelection(
                category = currentSel,
                subgenre = _uiState.value.selectedSubgenre,
                mood = _uiState.value.selectedGenreMood
            )
        } else {
            emptyList()
        }

        val recs = recommendationEngine.generateWaveQueue(
            settings = _uiState.value.waveSettings,
            history = _uiState.value.historyItems,
            favoriteArtistNames = _uiState.value.favoriteArtistNames,
            savedAlbumIds = _uiState.value.savedAlbumIds
        )

        _uiState.update { state ->
            state.copy(
                allTracks = all,
                popularTracks = all.sortedByDescending { it.popularityScore }.take(30),
                newReleases = all.filter { it.isNewRelease || it.releaseYear >= 2025 }
                    .sortedByDescending { it.releaseYear }
                    .take(30),
                albums = catalogProvider.getAllAlbums(),
                artists = catalogProvider.getAllArtists(),
                genres = catalogProvider.getAllGenres(),
                genreCategories = cats,
                workoutTracks = computeThematicTracks(all, "workout"),
                studyTracks = computeThematicTracks(all, "study"),
                roadTracks = computeThematicTracks(all, "road"),
                sleepRelaxTracks = computeThematicTracks(all, "sleep"),
                animeGamingTracks = computeThematicTracks(all, "anime"),
                selectedGenreCategory = currentSel,
                selectedGenreTracks = filteredGenreTracks,
                waveRecommendations = recs,
                searchResult = if (state.searchQuery.isNotBlank()) {
                    catalogProvider.searchCatalog(
                        query = state.searchQuery,
                        filter = state.searchFilter,
                        sortOrder = state.searchSortOrder
                    )
                } else {
                    state.searchResult
                }
            )
        }
    }

    private fun computeThematicTracks(all: List<Track>, theme: String): List<Track> {
        return when (theme) {
            "workout" -> all.filter { tr ->
                tr.bpm >= 122 ||
                    tr.genre in setOf("EDM и техно", "Драм-н-бейс", "Хип-хоп и рэп", "Рок и альтернативный рок", "Метал", "Электронная музыка") ||
                    tr.moodTags.any { it.contains("спорт", true) || it.contains("трениров", true) || it.contains("энерг", true) }
            }.sortedByDescending { it.popularityScore }.take(18)

            "study" -> all.filter { tr ->
                tr.genre in setOf("Лоу-фай", "Классическая музыка", "Эмбиент", "Джаз", "Акустическая музыка") ||
                    tr.moodTags.any { it.contains("учеб", true) || it.contains("фокус", true) || it.contains("концентрац", true) }
            }.sortedByDescending { it.popularityScore }.take(18)

            "road" -> all.filter { tr ->
                tr.genre in setOf("Электронная музыка", "Рок и альтернативный рок", "Инди", "Поп", "Ретро и музыка прошлых десятилетий", "Зарубежные хиты") ||
                    tr.moodTags.any { it.contains("дорог", true) || it.contains("драйв", true) || it.contains("ночь", true) }
            }.sortedByDescending { it.popularityScore }.take(18)

            "sleep" -> all.filter { tr ->
                tr.bpm <= 102 ||
                    tr.genre in setOf("Чилл и релакс", "Медитативная музыка", "Эмбиент", "Акустическая музыка", "Лоу-фай") ||
                    tr.moodTags.any { it.contains("сон", true) || it.contains("релакс", true) || it.contains("спокой", true) }
            }.sortedByDescending { it.popularityScore }.take(18)

            "anime" -> all.filter { tr ->
                tr.genre in setOf("Аниме-саундтреки", "Саундтреки из фильмов и игр", "J-pop", "K-pop") ||
                    tr.moodTags.any { it.contains("аниме", true) || it.contains("игр", true) || it.contains("саундтрек", true) || it.contains("кибер", true) }
            }.sortedByDescending { it.popularityScore }.take(18)

            else -> all.take(12)
        }
    }

    private fun observeLocalAndCloudState() {
        // 1. Observe DataStore preferences & restore player state once
        viewModelScope.launch {
            val initialPrefs = prefs.appStateFlow.first()
            val excludedArtistsSet = initialPrefs.excludedArtistsCsv
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            val excludedGenresSet = initialPrefs.excludedGenresCsv
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()

            _uiState.update { state ->
                state.copy(
                    themeMode = initialPrefs.themeMode,
                    subscriptionTier = initialPrefs.subscriptionTier,
                    isOfflineModeSimulated = initialPrefs.simulateOfflineMode,
                    waveSettings = state.waveSettings.copy(
                        diversityPercent = initialPrefs.waveDiversity,
                        excludedArtists = excludedArtistsSet,
                        excludedGenres = excludedGenresSet
                    )
                )
            }

            val savedQueueIds = initialPrefs.lastQueueCsv.split(",").filter { it.isNotBlank() }
            playerController.restoreState(
                trackId = initialPrefs.lastTrackId,
                positionMs = initialPrefs.lastPositionMs,
                queueIds = savedQueueIds,
                shuffle = initialPrefs.isShuffleEnabled,
                repeatMode = initialPrefs.repeatMode,
                volume = initialPrefs.volume
            )

            prefs.appStateFlow.collectLatest { p ->
                _uiState.update {
                    it.copy(
                        themeMode = p.themeMode,
                        isOfflineModeSimulated = p.simulateOfflineMode
                    )
                }
            }
        }

        // 2. Observe cached remote tracks in Room so thousands of discovered tracks persist across restarts
        viewModelScope.launch {
            dao.observeCachedTracks().collectLatest { cachedEntities ->
                if (cachedEntities.isNotEmpty()) {
                    val tracks = cachedEntities.map { it.toDomainTrack() }
                    catalogProvider.ingestRemoteTracks(tracks)
                    refreshCatalogSnapshotInState()
                }
            }
        }

        // 3. Observe Room listening history & recompute My Wave recommendations
        viewModelScope.launch {
            dao.observeHistory().collectLatest { historyList ->
                val likedIds = historyList.filter { it.liked && !it.disliked }.map { it.trackId }.toSet()
                val dislikedIds = historyList.filter { it.disliked }.map { it.trackId }.toSet()
                val waveRecs = recommendationEngine.generateWaveQueue(
                    settings = _uiState.value.waveSettings,
                    history = historyList,
                    favoriteArtistNames = _uiState.value.favoriteArtistNames,
                    savedAlbumIds = _uiState.value.savedAlbumIds
                )
                _uiState.update {
                    it.copy(
                        historyItems = historyList,
                        likedTrackIds = likedIds,
                        dislikedTrackIds = dislikedIds,
                        waveRecommendations = waveRecs
                    )
                }
            }
        }

        // 4. Observe Room local playlists
        viewModelScope.launch {
            dao.observeLocalPlaylists().collectLatest { localList ->
                _uiState.update { it.copy(playlists = localList) }
            }
        }

        // 5. Observe Room search history
        viewModelScope.launch {
            dao.observeSearchHistory().collectLatest { searchHist ->
                _uiState.update { it.copy(searchHistory = searchHist) }
            }
        }

        // 6. Observe Favorite Artists, Saved Albums & Downloaded Tracks
        viewModelScope.launch {
            dao.observeFavoriteArtists().collectLatest { favArtists ->
                _uiState.update {
                    it.copy(
                        favoriteArtistIds = favArtists.map { a -> a.artistId }.toSet(),
                        favoriteArtistNames = favArtists.map { a -> a.name }.toSet()
                    )
                }
            }
        }

        viewModelScope.launch {
            dao.observeSavedAlbums().collectLatest { savedAlbums ->
                _uiState.update {
                    it.copy(savedAlbumIds = savedAlbums.map { a -> a.albumId }.toSet())
                }
            }
        }

        viewModelScope.launch {
            dao.observeDownloadedTracks().collectLatest { dlTracks ->
                _uiState.update {
                    it.copy(downloadedTrackIds = dlTracks.map { d -> d.trackId }.toSet())
                }
            }
        }

        // 7. Sync with Firestore when authenticated
        if (firestoreRepository != null && currentUserId.isNotBlank()) {
            viewModelScope.launch {
                firestoreRepository.getOrCreateProfile(
                    displayName = initialDisplayName,
                    email = initialEmail
                ).onFailure {
                    _uiState.update { s -> s.copy(cloudSyncStatusRu = "Локальный режим (Room кэш активен)") }
                }
            }

            viewModelScope.launch {
                firestoreRepository.observePlaylists(currentUserId)
                    .catch { e ->
                        Log.w("MusicFormVM", "Cloud playlists listener note: ${e.message}")
                    }
                    .collectLatest { remotePlaylists ->
                        remotePlaylists.forEach { remote ->
                            if (remote.id.isNotBlank()) {
                                dao.upsertLocalPlaylist(
                                    LocalPlaylistEntity(
                                        playlistId = remote.id,
                                        title = remote.title,
                                        description = remote.description,
                                        trackIdsCsv = remote.trackIds.joinToString(","),
                                        coverTintHex = remote.coverTintHex,
                                        updatedAtEpochMs = remote.updatedAt?.toDate()?.time
                                            ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
            }

            viewModelScope.launch {
                firestoreRepository.observeInteractions(currentUserId)
                    .catch { e ->
                        Log.w("MusicFormVM", "Cloud interactions listener note: ${e.message}")
                    }
                    .collectLatest { remoteInteractions ->
                        remoteInteractions.forEach { r ->
                            val existing = dao.getHistoryForTrack(r.trackId)
                            val mergedPlayCount = maxOf(existing?.playCount ?: 0, r.playCount)
                            val mergedListenMs = maxOf(existing?.totalListenMs ?: 0L, r.totalListenMs)
                            dao.upsertHistory(
                                ListeningHistoryEntity(
                                    trackId = r.trackId,
                                    trackTitle = r.trackTitle,
                                    artistName = r.artistName,
                                    genre = r.genre,
                                    liked = r.liked,
                                    disliked = r.disliked,
                                    playCount = mergedPlayCount,
                                    skipCount = maxOf(existing?.skipCount ?: 0, r.skipCount),
                                    totalListenMs = mergedListenMs,
                                    lastPlayedEpochMs = r.updatedAt?.toDate()?.time
                                        ?: existing?.lastPlayedEpochMs
                                        ?: System.currentTimeMillis()
                                )
                            )
                        }
                    }
            }
        }
    }

    /**
     * Bootstraps real tracks across diverse genres from the legal online catalog APIs
     * (Jamendo CC + Apple iTunes Public Catalog API) and caches them in Room.
     */
    private fun bootstrapMultiGenreOnlineCatalog() {
        viewModelScope.launch {
            if (_uiState.value.isOfflineModeSimulated) return@launch
            val existingCachedCount = dao.getAllCachedTracksOnce().size
            if (existingCachedCount >= 120) {
                return@launch
            }
            val bootstrapGenreIds = listOf(
                "pop", "rock_alt", "hiphop_rap", "electronic", "lofi",
                "kpop", "anime_ost", "movie_game_ost", "russian_music", "chill_relax"
            )
            for (genreId in bootstrapGenreIds) {
                val category = MusicFormGenresTaxonomy.findByTitleOrId(genreId) ?: continue
                runCatching {
                    val page = remoteCatalogClient.fetchTracksByGenre(category, offset = 0, limit = 15)
                    if (page.tracks.isNotEmpty()) {
                        catalogProvider.ingestRemoteTracks(page.tracks)
                        dao.upsertCachedTracks(
                            page.tracks.map { CachedTrackEntity.fromDomainTrack(it) }
                        )
                    }
                }
            }
            refreshCatalogSnapshotInState()
        }
    }

    fun refreshOnlineCatalogManual() {
        if (_uiState.value.isOfflineModeSimulated) {
            showSnackbar("Отключите офлайн-режим для загрузки новых треков из онлайн-каталога")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCatalogLoadingMore = true) }
            var loadedCount = 0
            val randomGenres = MusicFormGenresTaxonomy.allCategories.shuffled().take(5)
            for (cat in randomGenres) {
                val page = remoteCatalogClient.fetchTracksByGenre(
                    genreCategory = cat,
                    offset = (0..60 step 20).toList().random(),
                    limit = 20
                )
                if (page.tracks.isNotEmpty()) {
                    loadedCount += page.tracks.size
                    catalogProvider.ingestRemoteTracks(page.tracks)
                    dao.upsertCachedTracks(page.tracks.map { CachedTrackEntity.fromDomainTrack(it) })
                }
            }
            _uiState.update { it.copy(isCatalogLoadingMore = false) }
            refreshCatalogSnapshotInState()
            if (loadedCount > 0) {
                showSnackbar("Загружено ещё $loadedCount реальных треков в каталог (всего: ${catalogProvider.getAllTracks().size})")
            } else {
                showSnackbar("Каталог актуален (${catalogProvider.getAllTracks().size} треков)")
            }
        }
    }

    // --- 29 GENRES EXPLORER & PAGINATION ---

    fun selectGenreCategory(category: GenreCategory?) {
        if (category == null) {
            _uiState.update {
                it.copy(
                    selectedGenreCategory = null,
                    selectedSubgenre = null,
                    selectedGenreMood = null,
                    selectedGenreTracks = emptyList(),
                    genrePaginationOffset = 0
                )
            }
            return
        }

        val initialLocal = filterTracksForGenreSelection(category, subgenre = null, mood = null)
        _uiState.update {
            it.copy(
                selectedGenreCategory = category,
                selectedSubgenre = null,
                selectedGenreMood = null,
                selectedGenreTracks = initialLocal,
                genrePaginationOffset = 0
            )
        }

        if (!_uiState.value.isOfflineModeSimulated) {
            viewModelScope.launch {
                _uiState.update { it.copy(isCatalogLoadingMore = true) }
                val page = remoteCatalogClient.fetchTracksByGenre(
                    genreCategory = category,
                    offset = 0,
                    limit = 25
                )
                if (page.tracks.isNotEmpty()) {
                    catalogProvider.ingestRemoteTracks(page.tracks)
                    dao.upsertCachedTracks(page.tracks.map { CachedTrackEntity.fromDomainTrack(it) })
                }
                _uiState.update {
                    it.copy(
                        isCatalogLoadingMore = false,
                        genrePaginationOffset = page.pageOffset + page.tracks.size
                    )
                }
                refreshCatalogSnapshotInState()
            }
        }
    }

    fun selectSubgenre(subgenre: String?) {
        val category = _uiState.value.selectedGenreCategory ?: return
        val filtered = filterTracksForGenreSelection(category, subgenre, _uiState.value.selectedGenreMood)
        _uiState.update {
            it.copy(
                selectedSubgenre = subgenre,
                selectedGenreTracks = filtered
            )
        }
        if (subgenre != null && !_uiState.value.isOfflineModeSimulated) {
            viewModelScope.launch {
                _uiState.update { it.copy(isCatalogLoadingMore = true) }
                val page = remoteCatalogClient.fetchTracksByGenre(
                    genreCategory = category,
                    subgenreFilter = subgenre,
                    offset = 0,
                    limit = 20
                )
                if (page.tracks.isNotEmpty()) {
                    catalogProvider.ingestRemoteTracks(page.tracks)
                    dao.upsertCachedTracks(page.tracks.map { CachedTrackEntity.fromDomainTrack(it) })
                }
                _uiState.update { it.copy(isCatalogLoadingMore = false) }
                refreshCatalogSnapshotInState()
            }
        }
    }

    fun selectGenreMood(mood: String?) {
        val category = _uiState.value.selectedGenreCategory ?: return
        val filtered = filterTracksForGenreSelection(category, _uiState.value.selectedSubgenre, mood)
        _uiState.update {
            it.copy(
                selectedGenreMood = mood,
                selectedGenreTracks = filtered
            )
        }
    }

    fun loadMoreGenreTracks() {
        val category = _uiState.value.selectedGenreCategory ?: return
        if (_uiState.value.isOfflineModeSimulated) {
            showSnackbar("В офлайн-режиме отображаются сохранённые в кэше треки")
            return
        }
        viewModelScope.launch {
            val offset = _uiState.value.genrePaginationOffset
            _uiState.update { it.copy(isCatalogLoadingMore = true) }
            val page = remoteCatalogClient.fetchTracksByGenre(
                genreCategory = category,
                subgenreFilter = _uiState.value.selectedSubgenre,
                offset = offset,
                limit = 25
            )
            if (page.tracks.isNotEmpty()) {
                catalogProvider.ingestRemoteTracks(page.tracks)
                dao.upsertCachedTracks(page.tracks.map { CachedTrackEntity.fromDomainTrack(it) })
            }
            _uiState.update {
                it.copy(
                    isCatalogLoadingMore = false,
                    genrePaginationOffset = page.pageOffset + page.tracks.size
                )
            }
            refreshCatalogSnapshotInState()
            showSnackbar("Загружена страница треков жанра «${category.titleRu}»")
        }
    }

    private fun filterTracksForGenreSelection(
        category: GenreCategory,
        subgenre: String?,
        mood: String?
    ): List<Track> {
        val base = catalogProvider.getTracksForGenre(category.titleRu)
        return base.filter { track ->
            val matchesSub = subgenre.isNullOrBlank() ||
                track.subgenre.equals(subgenre, ignoreCase = true) ||
                track.moodTags.any { it.contains(subgenre, ignoreCase = true) } ||
                track.title.contains(subgenre, ignoreCase = true)
            val matchesMood = mood.isNullOrBlank() ||
                track.moodTags.any { it.contains(mood, ignoreCase = true) } ||
                category.moods.contains(mood)
            matchesSub && matchesMood
        }
    }

    private fun seedInitialPlaylistsIfNeeded() {
        viewModelScope.launch {
            val currentPlaylists = dao.getAllPlaylistsOnce()
            if (currentPlaylists.isEmpty()) {
                val defaultPlaylists = listOf(
                    LocalPlaylistEntity(
                        playlistId = "pl_night_drive_default",
                        title = "Ночной драйв 2099",
                        description = "Неоновый синтвейв, электроника и кибер-рок для ночных дорог",
                        trackIdsCsv = "tr_neon_horizon,tr_chrome_highway,tr_electric_form,tr_mecha_horizon",
                        coverTintHex = "#00E5FF",
                        updatedAtEpochMs = System.currentTimeMillis()
                    ),
                    LocalPlaylistEntity(
                        playlistId = "pl_deep_focus_default",
                        title = "Глубокий фокус & Чтение",
                        description = "Lo-Fi, эмбиент и неоклассика для продуктивной работы",
                        trackIdsCsv = "tr_rainy_window_lofi,tr_starlight_veil,tr_glass_sonata,tr_espresso_notes",
                        coverTintHex = "#7C4DFF",
                        updatedAtEpochMs = System.currentTimeMillis() - 1000L
                    )
                )
                defaultPlaylists.forEach { dao.upsertLocalPlaylist(it) }
            }
        }
    }

    // --- PLAYBACK & TELEMETRY ---

    fun playTrack(
        track: Track,
        customQueue: List<Track>? = null,
        isWaveMode: Boolean = false,
        waveReason: String? = null
    ) {
        playerController.playTrack(
            track = track,
            newQueue = customQueue,
            isWaveMode = isWaveMode,
            waveReason = waveReason,
            simulateOfflineError = _uiState.value.isOfflineModeSimulated
        )
        recordTrackPlayEvent(track, listenedMs = 15_000L, wasEarlySkip = false)
        persistPlayerSnapshot()
    }

    fun onTogglePlayPause() {
        playerController.togglePlayPause()
        persistPlayerSnapshot()
    }

    fun onSkipNext() {
        playerController.skipToNext()
        persistPlayerSnapshot()
    }

    fun onSkipPrevious() {
        playerController.skipToPrevious()
        persistPlayerSnapshot()
    }

    fun recordTrackPlayEvent(track: Track, listenedMs: Long, wasEarlySkip: Boolean) {
        viewModelScope.launch {
            val existing = dao.getHistoryForTrack(track.id)
            val updated = ListeningHistoryEntity(
                trackId = track.id,
                trackTitle = track.title,
                artistName = track.artistName,
                genre = track.genre,
                liked = existing?.liked ?: false,
                disliked = existing?.disliked ?: false,
                playCount = (existing?.playCount ?: 0) + if (wasEarlySkip) 0 else 1,
                skipCount = (existing?.skipCount ?: 0) + if (wasEarlySkip) 1 else 0,
                totalListenMs = (existing?.totalListenMs ?: 0L) + listenedMs.coerceAtLeast(1000L),
                lastPlayedEpochMs = System.currentTimeMillis()
            )
            dao.upsertHistory(updated)
            syncInteractionToCloud(updated)
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            val existing = dao.getHistoryForTrack(track.id)
            val newLiked = !(existing?.liked ?: false)
            val updated = ListeningHistoryEntity(
                trackId = track.id,
                trackTitle = track.title,
                artistName = track.artistName,
                genre = track.genre,
                liked = newLiked,
                disliked = if (newLiked) false else (existing?.disliked ?: false),
                playCount = existing?.playCount ?: 1,
                skipCount = existing?.skipCount ?: 0,
                totalListenMs = existing?.totalListenMs ?: 12_000L,
                lastPlayedEpochMs = System.currentTimeMillis()
            )
            dao.upsertHistory(updated)
            val synced = syncInteractionToCloud(updated)
            showSnackbar(
                if (newLiked) {
                    if (synced) "Добавлено в «Любимые треки» и облако: ${track.title}"
                    else "Добавлено в «Любимые треки» (локально в Room): ${track.title}"
                } else {
                    "Удалено из «Любимых треков»: ${track.title}"
                }
            )
        }
    }

    fun dislikeAndSkipTrack(track: Track) {
        viewModelScope.launch {
            val existing = dao.getHistoryForTrack(track.id)
            val updated = ListeningHistoryEntity(
                trackId = track.id,
                trackTitle = track.title,
                artistName = track.artistName,
                genre = track.genre,
                liked = false,
                disliked = true,
                playCount = existing?.playCount ?: 0,
                skipCount = (existing?.skipCount ?: 0) + 1,
                totalListenMs = existing?.totalListenMs ?: 0L,
                lastPlayedEpochMs = System.currentTimeMillis()
            )
            dao.upsertHistory(updated)
            syncInteractionToCloud(updated)
            if (playerState.value.currentTrack?.id == track.id) {
                playerController.skipToNext(autoTriggered = false)
                persistPlayerSnapshot()
            }
            showSnackbar("Трек «${track.title}» скрыт из рекомендаций «Моей волны»")
        }
    }

    fun clearListeningHistory() {
        viewModelScope.launch {
            dao.clearAllHistory()
            showSnackbar("Локальная история прослушивания очищена")
        }
    }

    private suspend fun syncInteractionToCloud(entity: ListeningHistoryEntity): Boolean {
        val repo = firestoreRepository ?: return false
        if (_uiState.value.isOfflineModeSimulated || currentUserId.isBlank()) return false
        val result = repo.upsertInteraction(
            trackId = entity.trackId,
            trackTitle = entity.trackTitle,
            artistName = entity.artistName,
            genre = entity.genre,
            liked = entity.liked,
            disliked = entity.disliked,
            playCount = entity.playCount,
            skipCount = entity.skipCount,
            totalListenMs = entity.totalListenMs
        )
        return result.isSuccess
    }

    fun persistPlayerSnapshot() {
        viewModelScope.launch {
            val ps = playerController.state.value
            val currentId = ps.currentTrack?.id ?: return@launch
            prefs.savePlayerState(
                trackId = currentId,
                positionMs = ps.currentPositionMs,
                queueIds = ps.queue.map { it.id },
                shuffle = ps.isShuffleEnabled,
                repeatMode = ps.repeatMode,
                volume = ps.volume
            )
        }
    }

    // --- USER LIBRARY: FAVORITE ARTISTS, SAVED ALBUMS, LICENSED DOWNLOADS ---

    fun toggleFavoriteArtist(artist: Artist) {
        viewModelScope.launch {
            val isFav = artist.id in _uiState.value.favoriteArtistIds ||
                artist.name in _uiState.value.favoriteArtistNames
            if (isFav) {
                dao.deleteFavoriteArtist(artist.id)
                showSnackbar("Отписка от исполнителя: ${artist.name}")
            } else {
                dao.upsertFavoriteArtist(
                    FavoriteArtistEntity(
                        artistId = artist.id,
                        name = artist.name,
                        primaryGenre = artist.primaryGenre,
                        bio = artist.bio,
                        monthlyListeners = artist.monthlyListeners,
                        accentHex = artist.accentHex,
                        avatarUrl = artist.avatarUrl,
                        sourceProvider = artist.sourceProvider,
                        addedAtEpochMs = System.currentTimeMillis()
                    )
                )
                showSnackbar("Вы подписались на исполнителя: ${artist.name}")
            }
        }
    }

    fun toggleSaveAlbum(album: Album) {
        viewModelScope.launch {
            val isSaved = album.id in _uiState.value.savedAlbumIds
            if (isSaved) {
                dao.deleteSavedAlbum(album.id)
                showSnackbar("Альбом удалён из медиатеки: ${album.title}")
            } else {
                dao.upsertSavedAlbum(
                    SavedAlbumEntity(
                        albumId = album.id,
                        title = album.title,
                        artistId = album.artistId,
                        artistName = album.artistName,
                        genre = album.genre,
                        releaseYear = album.releaseYear,
                        coverColorPrimaryHex = album.coverColorPrimaryHex,
                        coverColorSecondaryHex = album.coverColorSecondaryHex,
                        trackIdsCsv = album.trackIds.joinToString(","),
                        description = album.description,
                        coverUrl = album.coverUrl,
                        sourceProvider = album.sourceProvider,
                        savedAtEpochMs = System.currentTimeMillis()
                    )
                )
                showSnackbar("Альбом сохранён в медиатеку: ${album.title}")
            }
        }
    }

    fun downloadLicensedTrack(track: Track) {
        if (!track.isDownloadAllowed) {
            showSnackbar("Лицензия ${track.licenseInfo} не разрешает офлайн-скачивание этого трека")
            return
        }
        viewModelScope.launch {
            val targetDir = File(filesDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp"), "licensed_downloads")
            val result = if (!track.downloadUrl.isNullOrBlank()) {
                remoteCatalogClient.downloadLicensedTrackToStorage(track, targetDir)
            } else {
                // Built-in Music Form Studio Original CC BY 4.0 track
                val wavFile = SynthesizedAudioGenerator.ensurePlayableAudioFile(targetDir, track)
                Result.success(wavFile)
            }

            result.onSuccess { file ->
                dao.upsertDownloadedTrack(
                    DownloadedTrackEntity(
                        trackId = track.id,
                        title = track.title,
                        artistName = track.artistName,
                        albumTitle = track.albumTitle,
                        genre = track.genre,
                        durationMs = track.durationMs,
                        localFilePath = file.absolutePath,
                        licenseInfo = track.licenseInfo,
                        coverUrl = track.coverUrl,
                        sourceProvider = track.sourceProvider,
                        downloadedAtEpochMs = System.currentTimeMillis()
                    )
                )
                showSnackbar("Трек «${track.title}» скачан для офлайн-прослушивания (${track.licenseInfo})")
            }.onFailure { err ->
                showSnackbar("Ошибка скачивания: ${err.message}")
            }
        }
    }

    fun deleteDownloadedTrack(trackId: String) {
        viewModelScope.launch {
            dao.deleteDownloadedTrack(trackId)
            showSnackbar("Офлайн-копия трека удалена")
        }
    }

    // --- MY WAVE («МОЯ ВОЛНА») ---

    fun launchMyWave(
        seedTrackId: String? = _uiState.value.waveSettings.seedTrackId,
        seedArtistId: String? = _uiState.value.waveSettings.seedArtistId,
        seedGenre: String? = _uiState.value.waveSettings.seedGenre,
        mood: WaveMood? = _uiState.value.waveSettings.mood,
        naturalPrompt: String? = _uiState.value.waveSettings.naturalLanguagePrompt
    ) {
        val updatedSettings = _uiState.value.waveSettings.copy(
            seedTrackId = seedTrackId,
            seedArtistId = seedArtistId,
            seedGenre = seedGenre,
            mood = mood,
            naturalLanguagePrompt = naturalPrompt
        )
        val recommendations = recommendationEngine.generateWaveQueue(
            settings = updatedSettings,
            history = _uiState.value.historyItems,
            favoriteArtistNames = _uiState.value.favoriteArtistNames,
            savedAlbumIds = _uiState.value.savedAlbumIds
        )
        _uiState.update {
            it.copy(
                waveSettings = updatedSettings,
                waveRecommendations = recommendations
            )
        }
        val firstRec = recommendations.firstOrNull() ?: return
        playTrack(
            track = firstRec.track,
            customQueue = recommendations.map { it.track },
            isWaveMode = true,
            waveReason = firstRec.reasonRu
        )
    }

    fun updateWaveDiversity(diversityPercent: Int) {
        val clamped = diversityPercent.coerceIn(0, 100)
        val updatedSettings = _uiState.value.waveSettings.copy(diversityPercent = clamped)
        val recs = recommendationEngine.generateWaveQueue(
            settings = updatedSettings,
            history = _uiState.value.historyItems,
            favoriteArtistNames = _uiState.value.favoriteArtistNames,
            savedAlbumIds = _uiState.value.savedAlbumIds
        )
        _uiState.update {
            it.copy(waveSettings = updatedSettings, waveRecommendations = recs)
        }
        viewModelScope.launch {
            prefs.saveWaveSettings(clamped, updatedSettings.excludedArtists, updatedSettings.excludedGenres)
            if (!_uiState.value.isOfflineModeSimulated) {
                firestoreRepository?.updateProfilePreferences(waveDiversity = clamped)
            }
        }
    }

    fun toggleExcludeArtist(artistName: String) {
        val currentExcluded = _uiState.value.waveSettings.excludedArtists
        val updatedExcluded = if (artistName in currentExcluded) {
            currentExcluded - artistName
        } else {
            currentExcluded + artistName
        }
        val updatedSettings = _uiState.value.waveSettings.copy(excludedArtists = updatedExcluded)
        val recs = recommendationEngine.generateWaveQueue(
            settings = updatedSettings,
            history = _uiState.value.historyItems,
            favoriteArtistNames = _uiState.value.favoriteArtistNames,
            savedAlbumIds = _uiState.value.savedAlbumIds
        )
        _uiState.update {
            it.copy(waveSettings = updatedSettings, waveRecommendations = recs)
        }
        viewModelScope.launch {
            prefs.saveWaveSettings(updatedSettings.diversityPercent, updatedExcluded, updatedSettings.excludedGenres)
            if (!_uiState.value.isOfflineModeSimulated) {
                firestoreRepository?.updateProfilePreferences(excludedArtists = updatedExcluded.toList())
            }
        }
    }

    fun toggleExcludeGenre(genre: String) {
        val currentExcluded = _uiState.value.waveSettings.excludedGenres
        val updatedExcluded = if (genre in currentExcluded) {
            currentExcluded - genre
        } else {
            currentExcluded + genre
        }
        val updatedSettings = _uiState.value.waveSettings.copy(excludedGenres = updatedExcluded)
        val recs = recommendationEngine.generateWaveQueue(
            settings = updatedSettings,
            history = _uiState.value.historyItems,
            favoriteArtistNames = _uiState.value.favoriteArtistNames,
            savedAlbumIds = _uiState.value.savedAlbumIds
        )
        _uiState.update {
            it.copy(waveSettings = updatedSettings, waveRecommendations = recs)
        }
        viewModelScope.launch {
            prefs.saveWaveSettings(updatedSettings.diversityPercent, updatedSettings.excludedArtists, updatedExcluded)
        }
    }

    fun clearWaveSeeds() {
        val updatedSettings = _uiState.value.waveSettings.copy(
            seedTrackId = null,
            seedArtistId = null,
            seedGenre = null,
            naturalLanguagePrompt = null
        )
        val recs = recommendationEngine.generateWaveQueue(
            settings = updatedSettings,
            history = _uiState.value.historyItems,
            favoriteArtistNames = _uiState.value.favoriteArtistNames,
            savedAlbumIds = _uiState.value.savedAlbumIds
        )
        _uiState.update {
            it.copy(waveSettings = updatedSettings, waveRecommendations = recs)
        }
    }

    fun resetRecommendationHistory() {
        viewModelScope.launch {
            dao.clearAllHistory()
            val resetSettings = WaveSettings(diversityPercent = 50)
            prefs.saveWaveSettings(50, emptySet(), emptySet())
            val recs = recommendationEngine.generateWaveQueue(resetSettings, emptyList())
            _uiState.update {
                it.copy(
                    waveSettings = resetSettings,
                    waveRecommendations = recs
                )
            }
            showSnackbar("История рекомендаций и список исключений «Моей волны» сброшены")
        }
    }

    // --- SEARCH (INSTANT LOCAL + DEBOUNCED REMOTE API + PAGINATION + SORTING) ---

    fun updateSearchQuery(
        query: String,
        filter: SearchFilter = _uiState.value.searchFilter,
        sortOrder: SearchSortOrder = _uiState.value.searchSortOrder
    ) {
        val localResult = catalogProvider.searchCatalog(query, filter, sortOrder)
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchFilter = filter,
                searchSortOrder = sortOrder,
                searchResult = localResult,
                remoteSearchOffset = 0
            )
        }

        searchDebounceJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length >= 2 && !_uiState.value.isOfflineModeSimulated && enableInitialNetworkBootstrap) {
            searchDebounceJob = viewModelScope.launch {
                delay(350L)
                fetchRemoteSearchPage(trimmed, offset = 0)
            }
        }
    }

    fun loadMoreRemoteSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.length < 2) return
        if (_uiState.value.isOfflineModeSimulated) {
            showSnackbar("В офлайн-режиме поиск работает по локальному кэшу")
            return
        }
        viewModelScope.launch {
            fetchRemoteSearchPage(query, offset = _uiState.value.remoteSearchOffset)
        }
    }

    private suspend fun fetchRemoteSearchPage(query: String, offset: Int) {
        _uiState.update { it.copy(isRemoteSearchLoading = true) }
        val page = remoteCatalogClient.searchRemoteCatalog(query = query, offset = offset, limit = 25)
        if (page.tracks.isNotEmpty()) {
            catalogProvider.ingestRemoteTracks(page.tracks)
            dao.upsertCachedTracks(page.tracks.map { CachedTrackEntity.fromDomainTrack(it) })
        }
        val updatedResult = catalogProvider.searchCatalog(
            query = _uiState.value.searchQuery,
            filter = _uiState.value.searchFilter,
            sortOrder = _uiState.value.searchSortOrder
        )
        _uiState.update {
            it.copy(
                isRemoteSearchLoading = false,
                remoteSearchOffset = page.pageOffset + page.tracks.size,
                searchResult = updatedResult
            )
        }
        refreshCatalogSnapshotInState()
    }

    fun commitSearchToHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        viewModelScope.launch {
            dao.insertSearchQuery(
                SearchHistoryEntity(
                    query = trimmed,
                    searchedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeSearchHistoryItem(query: String) {
        viewModelScope.launch { dao.deleteSearchQuery(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { dao.clearSearchHistory() }
    }

    // --- PLAYLIST CRUD & REORDERING ---

    fun createPlaylist(title: String, description: String, initialTrackIds: List<String> = emptyList()) {
        val cleanTitle = title.trim().ifBlank { "Мой плейлист" }
        val cleanDesc = description.trim()
        val colors = listOf("#00E5FF", "#7C4DFF", "#3D5AFE", "#00BFA5", "#FF4081")
        val tint = colors[cleanTitle.length % colors.size]

        viewModelScope.launch {
            val remoteResult = if (!_uiState.value.isOfflineModeSimulated) {
                firestoreRepository?.createPlaylist(cleanTitle, cleanDesc, initialTrackIds, tint)
            } else null

            val playlistId = remoteResult?.getOrNull()
                ?: "pl_${UUID.randomUUID().toString().replace("-", "").take(12)}"

            dao.upsertLocalPlaylist(
                LocalPlaylistEntity(
                    playlistId = playlistId,
                    title = cleanTitle,
                    description = cleanDesc,
                    trackIdsCsv = initialTrackIds.joinToString(","),
                    coverTintHex = tint,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            val cloudSynced = remoteResult?.isSuccess == true
            showSnackbar(
                if (cloudSynced) "Плейлист «$cleanTitle» создан и синхронизирован с облаком"
                else "Плейлист «$cleanTitle» сохранён локально в Room"
            )
        }
    }

    fun renamePlaylist(playlistId: String, newTitle: String, newDescription: String) {
        val cleanTitle = newTitle.trim().ifBlank { "Мой плейлист" }
        val cleanDesc = newDescription.trim()
        viewModelScope.launch {
            val playlist = _uiState.value.playlists.find { it.playlistId == playlistId } ?: return@launch
            val updatedEntity = playlist.copy(
                title = cleanTitle,
                description = cleanDesc,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            dao.upsertLocalPlaylist(updatedEntity)
            if (!_uiState.value.isOfflineModeSimulated) {
                firestoreRepository?.upsertPlaylist(
                    playlistId = playlistId,
                    title = cleanTitle,
                    description = cleanDesc,
                    trackIds = updatedEntity.trackIdsList(),
                    coverTintHex = updatedEntity.coverTintHex
                )
            }
            showSnackbar("Плейлист обновлён: «$cleanTitle»")
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            dao.deleteLocalPlaylist(playlistId)
            if (!_uiState.value.isOfflineModeSimulated) {
                firestoreRepository?.deletePlaylist(playlistId)
            }
            showSnackbar("Плейлист удалён")
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            val playlist = _uiState.value.playlists.find { it.playlistId == playlistId } ?: return@launch
            val currentIds = playlist.trackIdsList()
            if (track.id in currentIds) {
                showSnackbar("Трек уже есть в плейлисте «${playlist.title}»")
                return@launch
            }
            val updatedIds = currentIds + track.id
            updatePlaylistTracksInternal(playlist, updatedIds)
            showSnackbar("«${track.title}» добавлен в «${playlist.title}»")
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            val playlist = _uiState.value.playlists.find { it.playlistId == playlistId } ?: return@launch
            val updatedIds = playlist.trackIdsList().filterNot { it == trackId }
            updatePlaylistTracksInternal(playlist, updatedIds)
        }
    }

    fun reorderPlaylistTrack(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val playlist = _uiState.value.playlists.find { it.playlistId == playlistId } ?: return@launch
            val list = playlist.trackIdsList().toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return@launch
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            updatePlaylistTracksInternal(playlist, list)
        }
    }

    private suspend fun updatePlaylistTracksInternal(playlist: LocalPlaylistEntity, updatedIds: List<String>) {
        val updatedEntity = playlist.copy(
            trackIdsCsv = updatedIds.joinToString(","),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        dao.upsertLocalPlaylist(updatedEntity)
        if (!_uiState.value.isOfflineModeSimulated) {
            firestoreRepository?.upsertPlaylist(
                playlistId = playlist.playlistId,
                title = playlist.title,
                description = playlist.description,
                trackIds = updatedIds,
                coverTintHex = playlist.coverTintHex
            )
        }
    }

    // --- MUSIC FORM AI ---

    fun updateAiPromptInput(input: String) {
        _uiState.update { it.copy(aiPromptInput = input) }
    }

    fun submitAiPrompt(promptOverride: String? = null) {
        val query = (promptOverride ?: _uiState.value.aiPromptInput).trim()
        if (query.isEmpty()) return
        _uiState.update { it.copy(aiPromptInput = query, isAiLoading = true) }

        viewModelScope.launch {
            val knownArtists = _uiState.value.historyItems.map { it.artistName }.toSet()
            val response = aiService.curateMusicByPrompt(
                userPrompt = query,
                knownArtistNames = knownArtists,
                currentTrack = playerState.value.currentTrack
            )
            _uiState.update {
                it.copy(
                    isAiLoading = false,
                    lastAiResponse = response
                )
            }
        }
    }

    fun saveAiResponseAsPlaylist(response: AiAssistantResponse) {
        if (response.recommendedTracks.isEmpty()) return
        val title = "AI: ${response.userPrompt.take(28)}"
        val trackIds = response.recommendedTracks.map { it.track.id }
        createPlaylist(
            title = title,
            description = response.assistantExplanationRu,
            initialTrackIds = trackIds
        )
    }

    // --- SETTINGS, CLOUD SYNC, EXPORT & ACCOUNT DELETION ---

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
        }
    }

    fun toggleSimulateOfflineMode() {
        val next = !_uiState.value.isOfflineModeSimulated
        viewModelScope.launch {
            prefs.setSimulateOffline(next)
            _uiState.update {
                it.copy(
                    isOfflineModeSimulated = next,
                    cloudSyncStatusRu = if (next) {
                        "Офлайн-режим: работа из локальной базы Room"
                    } else {
                        "Облако синхронизировано (Firestore)"
                    }
                )
            }
        }
    }

    fun triggerManualCloudSync() {
        if (_uiState.value.isOfflineModeSimulated) {
            showSnackbar("Отключите офлайн-режим для синхронизации с облаком")
            return
        }
        val repo = firestoreRepository
        if (repo == null || currentUserId.isBlank()) {
            _uiState.update { it.copy(cloudSyncStatusRu = "Облачный репозиторий не инициализирован (локальный режим)") }
            showSnackbar("Облачная синхронизация недоступна без авторизованной сессии Google")
            return
        }
        viewModelScope.launch {
            val localHistory = dao.getAllHistoryOnce()
            val localPlaylists = dao.getAllPlaylistsOnce()
            var failedOps = 0

            for (item in localHistory) {
                if (!syncInteractionToCloud(item)) {
                    failedOps++
                }
            }
            for (pl in localPlaylists) {
                val res = repo.upsertPlaylist(
                    playlistId = pl.playlistId,
                    title = pl.title,
                    description = pl.description,
                    trackIds = pl.trackIdsList(),
                    coverTintHex = pl.coverTintHex
                )
                if (res.isFailure) {
                    failedOps++
                }
            }

            if (failedOps == 0) {
                _uiState.update { it.copy(cloudSyncStatusRu = "Синхронизировано с Cloud Firestore (${localPlaylists.size} плейлистов, ${localHistory.size} записей)") }
                showSnackbar("Медиатека и история успешно синхронизированы с Cloud Firestore")
            } else {
                _uiState.update { it.copy(cloudSyncStatusRu = "Частичная синхронизация (ошибок сети/доступа: $failedOps)") }
                showSnackbar("Часть записей ($failedOps) сохранена только локально в Room из-за ошибки сети или правил доступа")
            }
        }
    }

    fun exportUserDataAsJson() {
        viewModelScope.launch {
            val s = _uiState.value
            val root = JSONObject().apply {
                put("service", "Music Form")
                put("userId", s.currentUserId)
                put("displayName", s.userDisplayName)
                put("email", s.userEmail)
                put("subscriptionTier", s.subscriptionTier.name)
                put("waveDiversityPercent", s.waveSettings.diversityPercent)
                put("excludedArtists", JSONArray(s.waveSettings.excludedArtists.toList()))
                put("excludedGenres", JSONArray(s.waveSettings.excludedGenres.toList()))
                put(
                    "playlists",
                    JSONArray(
                        s.playlists.map { pl ->
                            JSONObject().apply {
                                put("id", pl.playlistId)
                                put("title", pl.title)
                                put("tracks", JSONArray(pl.trackIdsList()))
                            }
                        }
                    )
                )
                put(
                    "listeningHistory",
                    JSONArray(
                        s.historyItems.map { h ->
                            JSONObject().apply {
                                put("trackId", h.trackId)
                                put("title", h.trackTitle)
                                put("artist", h.artistName)
                                put("liked", h.liked)
                                put("playCount", h.playCount)
                                put("totalListenMs", h.totalListenMs)
                            }
                        }
                    )
                )
            }
            _uiState.update { it.copy(exportedJsonPreview = root.toString(2)) }
        }
    }

    fun dismissExportPreview() {
        _uiState.update { it.copy(exportedJsonPreview = null) }
    }

    fun deleteAllUserData(cacheDir: File? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            playerController.pause()
            firestoreRepository?.deleteAccountData()
            dao.clearAllHistory()
            dao.clearAllLocalPlaylists()
            dao.clearSearchHistory()
            dao.clearFavoriteArtists()
            dao.clearSavedAlbums()
            dao.clearDownloadedTracks()
            prefs.clearAll()
            SynthesizedAudioGenerator.clearCachedStreams(cacheDir)
            runCatching {
                Firebase.auth.currentUser?.delete()?.await()
            }
            onComplete()
        }
    }

    fun showSnackbar(messageRu: String) {
        _uiState.update { it.copy(statusSnackbarRu = messageRu) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(statusSnackbarRu = null) }
    }

    companion object {
        private fun buildInitialUiState(
            currentUserId: String,
            displayName: String,
            email: String,
            catalogProvider: MusicCatalogProvider
        ): MusicFormUiState {
            val all = catalogProvider.getAllTracks()
            return MusicFormUiState(
                currentUserId = currentUserId,
                userDisplayName = displayName.ifBlank { "Слушатель Music Form" },
                userEmail = email,
                allTracks = all,
                popularTracks = all.sortedByDescending { it.popularityScore },
                newReleases = all.filter { it.isNewRelease },
                albums = catalogProvider.getAllAlbums(),
                artists = catalogProvider.getAllArtists(),
                genres = catalogProvider.getAllGenres(),
                genreCategories = catalogProvider.getGenreCategories(),
                workoutTracks = all.filter { it.bpm >= 122 },
                studyTracks = all.filter { it.bpm <= 105 },
                roadTracks = all.take(8),
                sleepRelaxTracks = all.filter { it.bpm <= 100 },
                animeGamingTracks = all.takeLast(6)
            )
        }
    }
}
