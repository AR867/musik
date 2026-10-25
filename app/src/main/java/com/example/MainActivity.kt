package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.MusicFormAiService
import com.example.data.catalog.MusicFormOpenCatalogProvider
import com.example.data.local.MusicFormDatabase
import com.example.data.local.UserPreferencesDataStore
import com.example.data.model.Album
import com.example.data.model.Track
import com.example.data.remote.RemoteMusicCatalogClient
import com.example.data.repository.MusicFormFirestoreRepository
import com.example.player.MusicPlayerController
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FullPlayerOverlay
import com.example.ui.screens.GenresScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MiniPlayerBar
import com.example.ui.screens.MyWaveScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.signOutFromMusicForm
import com.example.ui.theme.MusicFormTheme
import com.example.ui.viewmodel.MusicFormViewModel
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class MusicFormDestination(
    val route: String,
    val labelRu: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Главная", Icons.Filled.Home, Icons.Outlined.Home),
    WAVE("wave", "Моя волна", Icons.Filled.Radio, Icons.Outlined.Radio),
    GENRES("genres", "Жанры", Icons.Filled.Category, Icons.Outlined.Category),
    SEARCH("search", "Поиск", Icons.Filled.Search, Icons.Outlined.Search),
    LIBRARY("library", "Медиатека", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    AI("ai", "AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    PROFILE("profile", "Профиль", Icons.Filled.Person, Icons.Outlined.Person)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mandatory App Check initialization before Compose UI or ViewModels
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        MusicFormTheme {
            AuthScreen(
                onAuthSuccess = { /* authStateFlow updates automatically */ }
            )
        }
    } else {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val vm: MusicFormViewModel = viewModel(
            key = user.uid,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(databaseId)
                    val firestoreRepo = MusicFormFirestoreRepository(firestore)
                    val roomDb = MusicFormDatabase.getInstance(app)
                    val prefs = UserPreferencesDataStore(app)
                    val catalog = MusicFormOpenCatalogProvider()
                    val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                    val playerController = MusicPlayerController(
                        appContext = app,
                        catalogProvider = catalog,
                        scope = playerScope
                    )
                    val aiService = MusicFormAiService(catalog)
                    val remoteCatalogClient = RemoteMusicCatalogClient()
                    MusicFormViewModel(
                        firestoreRepository = firestoreRepo,
                        dao = roomDb.musicFormDao(),
                        prefs = prefs,
                        catalogProvider = catalog,
                        playerController = playerController,
                        aiService = aiService,
                        currentUserId = user.uid,
                        initialDisplayName = user.displayName ?: "Слушатель Music Form",
                        initialEmail = user.email ?: "",
                        remoteCatalogClient = remoteCatalogClient,
                        filesDir = app.filesDir
                    )
                }
            }
        )

        MusicFormAppShell(
            viewModel = vm,
            onSignOut = {
                signOutFromMusicForm(
                    context = context,
                    onSignOutComplete = {},
                    scope = scope
                )
            }
        )
    }
}

@Composable
fun MusicFormAppShell(
    viewModel: MusicFormViewModel,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    var currentDestination by rememberSaveable { mutableStateOf(MusicFormDestination.HOME) }
    var isFullPlayerVisible by rememberSaveable { mutableStateOf(false) }
    var selectedAlbumForDialog by remember { mutableStateOf<Album?>(null) }
    var trackForPlaylistPicker by remember { mutableStateOf<Track?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusSnackbarRu) {
        val msg = uiState.statusSnackbarRu
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // BackHandlers for overlays, genre sub-views, and secondary tabs
    BackHandler(enabled = isFullPlayerVisible) {
        isFullPlayerVisible = false
    }
    BackHandler(
        enabled = !isFullPlayerVisible &&
            currentDestination == MusicFormDestination.GENRES &&
            uiState.selectedGenreCategory != null
    ) {
        viewModel.selectGenreCategory(null)
    }
    BackHandler(
        enabled = !isFullPlayerVisible &&
            currentDestination != MusicFormDestination.HOME &&
            !(currentDestination == MusicFormDestination.GENRES && uiState.selectedGenreCategory != null)
    ) {
        currentDestination = MusicFormDestination.HOME
    }

    // Album Detail Modal
    selectedAlbumForDialog?.let { album ->
        val albumTracks = uiState.allTracks.filter { it.albumId == album.id }
        val isAlbumSaved = album.id in uiState.savedAlbumIds
        AlertDialog(
            onDismissRequest = { selectedAlbumForDialog = null },
            title = { Text("${album.title} • ${album.artistName}") },
            text = {
                Column {
                    Text(
                        text = album.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    albumTracks.forEachIndexed { idx, tr ->
                        Text(
                            text = "${idx + 1}. ${tr.title} (${tr.bpm} BPM)",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = { viewModel.toggleSaveAlbum(album) }
                    ) {
                        Text(if (isAlbumSaved) "В медиатеке ✓" else "Сохранить альбом")
                    }
                    Button(
                        onClick = {
                            albumTracks.firstOrNull()?.let { first ->
                                viewModel.playTrack(first, albumTracks, isWaveMode = false)
                            }
                            selectedAlbumForDialog = null
                        },
                        modifier = Modifier.testTag("play_album_dialog_button")
                    ) {
                        Text("Слушать альбом")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAlbumForDialog = null }) {
                    Text("Закрыть")
                }
            }
        )
    }

    // Add Track to Playlist Picker Modal
    trackForPlaylistPicker?.let { targetTrack ->
        AlertDialog(
            onDismissRequest = { trackForPlaylistPicker = null },
            title = { Text("Добавить «${targetTrack.title}» в плейлист") },
            text = {
                Column {
                    if (uiState.playlists.isEmpty()) {
                        Text("У вас пока нет плейлистов. Создайте новый одним нажатием ниже.")
                    } else {
                        uiState.playlists.forEach { pl ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    viewModel.addTrackToPlaylist(pl.playlistId, targetTrack)
                                    trackForPlaylistPicker = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("pick_playlist_${pl.playlistId}")
                            ) {
                                Text(
                                    text = pl.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPlaylist(
                            title = "Избранное: ${targetTrack.genre}",
                            description = "Создано из трека ${targetTrack.title}",
                            initialTrackIds = listOf(targetTrack.id)
                        )
                        trackForPlaylistPicker = null
                    }
                ) {
                    Text("В новый плейлист")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackForPlaylistPicker = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    MusicFormTheme(themeMode = uiState.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 640.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!isWideScreen) {
                        Column {
                            MiniPlayerBar(
                                playerState = playerState,
                                isLiked = playerState.currentTrack?.id in uiState.likedTrackIds,
                                onExpandPlayer = { isFullPlayerVisible = true },
                                onTogglePlayPause = { viewModel.onTogglePlayPause() },
                                onSkipNext = { viewModel.onSkipNext() },
                                onToggleLike = { viewModel.toggleLike(it) }
                            )
                            NavigationBar(
                                modifier = Modifier.testTag("bottom_nav_bar")
                            ) {
                                MusicFormDestination.entries.forEach { dest ->
                                    val selected = currentDestination == dest
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentDestination = dest },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.labelRu
                                            )
                                        },
                                        label = { Text(dest.labelRu, maxLines = 1) },
                                        modifier = Modifier.testTag("nav_tab_${dest.route}")
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                val appContext = LocalContext.current.applicationContext
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isWideScreen) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .testTag("side_nav_rail")
                        ) {
                            MusicFormDestination.entries.forEach { dest ->
                                val selected = currentDestination == dest
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.labelRu
                                        )
                                    },
                                    label = { Text(dest.labelRu) },
                                    modifier = Modifier.testTag("nav_tab_${dest.route}")
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentDestination) {
                            MusicFormDestination.HOME -> HomeScreen(
                                uiState = uiState,
                                playerState = playerState,
                                licensingNoticeRu = viewModel.catalogProvider.licensingNoticeRu,
                                onLaunchMyWave = { mood ->
                                    viewModel.launchMyWave(mood = mood)
                                    currentDestination = MusicFormDestination.WAVE
                                },
                                onLaunchGenreWave = { genre ->
                                    viewModel.launchMyWave(seedGenre = genre)
                                    currentDestination = MusicFormDestination.WAVE
                                },
                                onLaunchArtistWave = { artist ->
                                    viewModel.launchMyWave(seedArtistId = artist.id)
                                    currentDestination = MusicFormDestination.WAVE
                                },
                                onPlayTrack = { track, queue, reason ->
                                    viewModel.playTrack(track, queue, isWaveMode = reason != null, waveReason = reason)
                                },
                                onToggleLike = { viewModel.toggleLike(it) },
                                onAddTrackToPlaylist = { trackForPlaylistPicker = it },
                                onDownloadTrack = { viewModel.downloadLicensedTrack(it) },
                                onSelectAlbum = { selectedAlbumForDialog = it },
                                onOpenGenresCatalog = { cat ->
                                    viewModel.selectGenreCategory(cat)
                                    currentDestination = MusicFormDestination.GENRES
                                },
                                onRefreshOnlineCatalog = { viewModel.refreshOnlineCatalogManual() },
                                onNavigateToAi = { currentDestination = MusicFormDestination.AI }
                            )

                            MusicFormDestination.WAVE -> MyWaveScreen(
                                uiState = uiState,
                                playerState = playerState,
                                onLaunchWave = { seedTr, seedArt, seedGen, mood ->
                                    viewModel.launchMyWave(seedTr, seedArt, seedGen, mood)
                                },
                                onUpdateDiversity = { viewModel.updateWaveDiversity(it) },
                                onToggleExcludeArtist = { viewModel.toggleExcludeArtist(it) },
                                onToggleExcludeGenre = { viewModel.toggleExcludeGenre(it) },
                                onClearWaveSeeds = { viewModel.clearWaveSeeds() },
                                onResetRecommendationHistory = { viewModel.resetRecommendationHistory() },
                                onTogglePlayPause = { viewModel.onTogglePlayPause() },
                                onSkipNext = { viewModel.onSkipNext() },
                                onToggleLike = { viewModel.toggleLike(it) },
                                onDislikeTrack = { viewModel.dislikeAndSkipTrack(it) },
                                onPlayRecommendation = { tr, reason ->
                                    viewModel.playTrack(
                                        tr,
                                        uiState.waveRecommendations.map { it.track },
                                        isWaveMode = true,
                                        waveReason = reason
                                    )
                                },
                                onAddTrackToPlaylist = { trackForPlaylistPicker = it },
                                onDownloadTrack = { viewModel.downloadLicensedTrack(it) }
                            )

                            MusicFormDestination.GENRES -> GenresScreen(
                                uiState = uiState,
                                playerState = playerState,
                                onSelectGenreCategory = { viewModel.selectGenreCategory(it) },
                                onSelectSubgenre = { viewModel.selectSubgenre(it) },
                                onSelectGenreMood = { viewModel.selectGenreMood(it) },
                                onLoadMoreGenreTracks = { viewModel.loadMoreGenreTracks() },
                                onLaunchGenreWave = { genre ->
                                    viewModel.launchMyWave(seedGenre = genre)
                                    currentDestination = MusicFormDestination.WAVE
                                },
                                onPlayTrack = { tr, q, reason ->
                                    viewModel.playTrack(tr, q, isWaveMode = reason != null, waveReason = reason)
                                },
                                onToggleLike = { viewModel.toggleLike(it) },
                                onAddTrackToPlaylist = { trackForPlaylistPicker = it },
                                onDownloadTrack = { viewModel.downloadLicensedTrack(it) },
                                onSelectAlbum = { selectedAlbumForDialog = it },
                                onToggleSaveAlbum = { viewModel.toggleSaveAlbum(it) },
                                onSelectArtistWave = { artist ->
                                    viewModel.launchMyWave(seedArtistId = artist.id)
                                    currentDestination = MusicFormDestination.WAVE
                                },
                                onToggleFavoriteArtist = { viewModel.toggleFavoriteArtist(it) }
                            )

                            MusicFormDestination.SEARCH -> SearchScreen(
                                uiState = uiState,
                                playerState = playerState,
                                onQueryChange = { q, f, sort -> viewModel.updateSearchQuery(q, f, sort) },
                                onLoadMoreRemoteSearch = { viewModel.loadMoreRemoteSearch() },
                                onCommitSearch = { viewModel.commitSearchToHistory(it) },
                                onRemoveHistoryItem = { viewModel.removeSearchHistoryItem(it) },
                                onClearHistory = { viewModel.clearSearchHistory() },
                                onPlayTrack = { tr, q -> viewModel.playTrack(tr, q) },
                                onToggleLike = { viewModel.toggleLike(it) },
                                onAddTrackToPlaylist = { trackForPlaylistPicker = it },
                                onDownloadTrack = { viewModel.downloadLicensedTrack(it) },
                                onSelectArtistWave = { artist ->
                                    viewModel.launchMyWave(seedArtistId = artist.id)
                                    currentDestination = MusicFormDestination.WAVE
                                },
                                onSelectAlbum = { selectedAlbumForDialog = it },
                                onSelectGenreWave = { genre ->
                                    viewModel.launchMyWave(seedGenre = genre)
                                    currentDestination = MusicFormDestination.WAVE
                                }
                            )

                            MusicFormDestination.LIBRARY -> LibraryScreen(
                                uiState = uiState,
                                playerState = playerState,
                                onCreatePlaylist = { t, d -> viewModel.createPlaylist(t, d) },
                                onRenamePlaylist = { id, t, d -> viewModel.renamePlaylist(id, t, d) },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                onRemoveTrackFromPlaylist = { plId, trId ->
                                    viewModel.removeTrackFromPlaylist(plId, trId)
                                },
                                onReorderPlaylistTrack = { plId, from, to ->
                                    viewModel.reorderPlaylistTrack(plId, from, to)
                                },
                                onClearListeningHistory = { viewModel.clearListeningHistory() },
                                onPlayTrack = { tr, q -> viewModel.playTrack(tr, q) },
                                onToggleLike = { viewModel.toggleLike(it) },
                                onAddTrackToPlaylist = { trackForPlaylistPicker = it },
                                onDownloadTrack = { viewModel.downloadLicensedTrack(it) },
                                onDeleteDownloadedTrack = { viewModel.deleteDownloadedTrack(it) },
                                onToggleFavoriteArtist = { viewModel.toggleFavoriteArtist(it) },
                                onSelectArtistWave = { artist ->
                                    viewModel.launchMyWave(seedArtistId = artist.id)
                                    currentDestination = MusicFormDestination.WAVE
                                },
                                onToggleSaveAlbum = { viewModel.toggleSaveAlbum(it) },
                                onSelectAlbum = { selectedAlbumForDialog = it }
                            )

                            MusicFormDestination.AI -> AiAssistantScreen(
                                uiState = uiState,
                                playerState = playerState,
                                onPromptChange = { viewModel.updateAiPromptInput(it) },
                                onSubmitPrompt = { viewModel.submitAiPrompt(it) },
                                onSaveAiPlaylist = { viewModel.saveAiResponseAsPlaylist(it) },
                                onPlayTrack = { tr, q, reason ->
                                    viewModel.playTrack(tr, q, isWaveMode = true, waveReason = reason)
                                },
                                onToggleLike = { viewModel.toggleLike(it) },
                                onAddTrackToPlaylist = { trackForPlaylistPicker = it }
                            )

                            MusicFormDestination.PROFILE -> ProfileScreen(
                                uiState = uiState,
                                onSetThemeMode = { viewModel.setThemeMode(it) },
                                onToggleOfflineSimulation = { viewModel.toggleSimulateOfflineMode() },
                                onTriggerCloudSync = { viewModel.triggerManualCloudSync() },
                                onExportData = { viewModel.exportUserDataAsJson() },
                                onDismissExportPreview = { viewModel.dismissExportPreview() },
                                onDeleteAccountAndData = {
                                    viewModel.deleteAllUserData(
                                        cacheDir = appContext.cacheDir,
                                        onComplete = onSignOut
                                    )
                                },
                                onSignOut = onSignOut
                            )
                        }

                        if (isWideScreen) {
                            MiniPlayerBar(
                                playerState = playerState,
                                isLiked = playerState.currentTrack?.id in uiState.likedTrackIds,
                                onExpandPlayer = { isFullPlayerVisible = true },
                                onTogglePlayPause = { viewModel.onTogglePlayPause() },
                                onSkipNext = { viewModel.onSkipNext() },
                                onToggleLike = { viewModel.toggleLike(it) },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }

                // Full Player Modal Overlay
                AnimatedVisibility(
                    visible = isFullPlayerVisible,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    FullPlayerOverlay(
                        playerState = playerState,
                        likedTrackIds = uiState.likedTrackIds,
                        onCollapse = { isFullPlayerVisible = false },
                        onTogglePlayPause = { viewModel.onTogglePlayPause() },
                        onSeekTo = {
                            viewModel.playerController.seekTo(it)
                            viewModel.persistPlayerSnapshot()
                        },
                        onSkipPrevious = { viewModel.onSkipPrevious() },
                        onSkipNext = { viewModel.onSkipNext() },
                        onToggleShuffle = {
                            viewModel.playerController.toggleShuffle()
                            viewModel.persistPlayerSnapshot()
                        },
                        onCycleRepeat = {
                            viewModel.playerController.cycleRepeatMode()
                            viewModel.persistPlayerSnapshot()
                        },
                        onVolumeChange = {
                            viewModel.playerController.setVolume(it)
                            viewModel.persistPlayerSnapshot()
                        },
                        onToggleLike = { viewModel.toggleLike(it) },
                        onSelectQueueTrack = { viewModel.playTrack(it, playerState.queue) },
                        onMoveQueueTrack = { from, to ->
                            viewModel.playerController.moveTrackInQueue(from, to)
                            viewModel.persistPlayerSnapshot()
                        },
                        onRemoveQueueTrack = {
                            viewModel.playerController.removeFromQueue(it)
                            viewModel.persistPlayerSnapshot()
                        }
                    )
                }
            }
        }
    }
}
