package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalPlaylistEntity
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.Track
import com.example.player.PlayerUiState
import com.example.ui.components.ExpressiveAlbumCover
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.viewmodel.MusicFormUiState

private enum class LibraryTab(val titleRu: String) {
    LIKED("Любимые треки"),
    ARTISTS("Любимые исполнители"),
    ALBUMS("Сохранённые альбомы"),
    PLAYLISTS("Плейлисты"),
    DOWNLOADS("Скачанные треки"),
    HISTORY("История"),
    RECENT_ADDED("Недавно добавленные")
}

@Composable
fun LibraryScreen(
    uiState: MusicFormUiState,
    playerState: PlayerUiState,
    onCreatePlaylist: (String, String) -> Unit,
    onRenamePlaylist: (String, String, String) -> Unit = { _, _, _ -> },
    onDeletePlaylist: (String) -> Unit,
    onRemoveTrackFromPlaylist: (String, String) -> Unit,
    onReorderPlaylistTrack: (String, Int, Int) -> Unit,
    onClearListeningHistory: () -> Unit = {},
    onPlayTrack: (Track, List<Track>?) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit = {},
    onDeleteDownloadedTrack: (String) -> Unit = {},
    onToggleFavoriteArtist: (Artist) -> Unit = {},
    onSelectArtistWave: (Artist) -> Unit = {},
    onToggleSaveAlbum: (Album) -> Unit = {},
    onSelectAlbum: (Album) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(LibraryTab.LIKED) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }

    var editingPlaylist by remember { mutableStateOf<LocalPlaylistEntity?>(null) }
    var editPlaylistTitle by remember { mutableStateOf("") }
    var editPlaylistDesc by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Создать плейлист") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPlaylistTitle,
                        onValueChange = { newPlaylistTitle = it },
                        label = { Text("Название плейлиста") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_playlist_title_input")
                    )
                    OutlinedTextField(
                        value = newPlaylistDesc,
                        onValueChange = { newPlaylistDesc = it },
                        label = { Text("Описание") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_playlist_desc_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(newPlaylistTitle, newPlaylistDesc)
                        newPlaylistTitle = ""
                        newPlaylistDesc = ""
                        showCreateDialog = false
                    },
                    modifier = Modifier.testTag("confirm_create_playlist_button")
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    editingPlaylist?.let { target ->
        AlertDialog(
            onDismissRequest = { editingPlaylist = null },
            title = { Text("Переименовать плейлист") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editPlaylistTitle,
                        onValueChange = { editPlaylistTitle = it },
                        label = { Text("Название плейлиста") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_playlist_title_input")
                    )
                    OutlinedTextField(
                        value = editPlaylistDesc,
                        onValueChange = { editPlaylistDesc = it },
                        label = { Text("Описание") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_playlist_desc_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenamePlaylist(target.playlistId, editPlaylistTitle, editPlaylistDesc)
                        editingPlaylist = null
                    },
                    modifier = Modifier.testTag("confirm_edit_playlist_button")
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPlaylist = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Cloud/Offline Status
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Моя медиатека",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isOfflineModeSimulated) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = if (uiState.isOfflineModeSimulated) MaterialTheme.colorScheme.error else ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.cloudSyncStatusRu,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianBlack
                    ),
                    modifier = Modifier.testTag("open_create_playlist_button")
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Плейлист", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LibraryTab.entries) { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = { Text(tab.titleRu) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("library_tab_${tab.name}")
                    )
                }
            }
        }

        when (activeTab) {
            LibraryTab.LIKED -> {
                val likedTracks = uiState.allTracks.filter { it.id in uiState.likedTrackIds }
                if (likedTracks.isEmpty()) {
                    item {
                        EmptyLibraryNotice(
                            title = "Пока нет любимых треков",
                            subtitle = "Отмечайте понравившиеся треки сердечком в плеере или «Моей волне» — они автоматически сохранятся здесь и в облаке."
                        )
                    }
                } else {
                    items(likedTracks, key = { "lib_liked_${it.id}" }) { track ->
                        TrackRowItem(
                            track = track,
                            isCurrentTrack = playerState.currentTrack?.id == track.id,
                            isPlaying = playerState.isPlaying,
                            isLiked = true,
                            isDownloaded = track.id in uiState.downloadedTrackIds,
                            onPlayClick = { onPlayTrack(track, likedTracks) },
                            onLikeClick = { onToggleLike(track) },
                            onAddToPlaylistClick = { onAddTrackToPlaylist(track) },
                            onDownloadClick = if (track.isDownloadAllowed) {
                                { onDownloadTrack(track) }
                            } else null
                        )
                    }
                }
            }

            LibraryTab.ARTISTS -> {
                val favoriteArtists = uiState.artists.filter {
                    it.id in uiState.favoriteArtistIds || it.name in uiState.favoriteArtistNames
                }
                item {
                    Text(
                        text = "Подписки на исполнителей (${favoriteArtists.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (favoriteArtists.isEmpty()) {
                    item {
                        EmptyLibraryNotice(
                            title = "Вы пока не подписаны на исполнителей",
                            subtitle = "Ниже представлены популярные артисты каталога — нажмите сердечко, чтобы добавить их в подписки и усилить рекомендации «Моей волны»."
                        )
                    }
                } else {
                    items(favoriteArtists, key = { "fav_art_${it.id}" }) { artist ->
                        ArtistSubscriptionRow(
                            artist = artist,
                            isSubscribed = true,
                            onToggleSubscribe = { onToggleFavoriteArtist(artist) },
                            onLaunchWave = { onSelectArtistWave(artist) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Рекомендуемые исполнители для подписки",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                val otherArtists = uiState.artists.filterNot {
                    it.id in uiState.favoriteArtistIds || it.name in uiState.favoriteArtistNames
                }.take(10)
                items(otherArtists, key = { "rec_art_${it.id}" }) { artist ->
                    ArtistSubscriptionRow(
                        artist = artist,
                        isSubscribed = false,
                        onToggleSubscribe = { onToggleFavoriteArtist(artist) },
                        onLaunchWave = { onSelectArtistWave(artist) }
                    )
                }
            }

            LibraryTab.ALBUMS -> {
                val savedAlbums = uiState.albums.filter { it.id in uiState.savedAlbumIds }
                item {
                    Text(
                        text = "Сохранённые альбомы (${savedAlbums.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (savedAlbums.isEmpty()) {
                    item {
                        EmptyLibraryNotice(
                            title = "Нет сохранённых альбомов",
                            subtitle = "Сохраняйте любимые альбомы и синглы для быстрого доступа. Ниже доступны популярные релизы каталога."
                        )
                    }
                } else {
                    items(savedAlbums, key = { "saved_alb_${it.id}" }) { album ->
                        SavedAlbumRow(
                            album = album,
                            isSaved = true,
                            onToggleSave = { onToggleSaveAlbum(album) },
                            onOpenAlbum = { onSelectAlbum(album) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Популярные альбомы каталога",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                val otherAlbums = uiState.albums.filterNot { it.id in uiState.savedAlbumIds }.take(10)
                items(otherAlbums, key = { "rec_alb_${it.id}" }) { album ->
                    SavedAlbumRow(
                        album = album,
                        isSaved = false,
                        onToggleSave = { onToggleSaveAlbum(album) },
                        onOpenAlbum = { onSelectAlbum(album) }
                    )
                }
            }

            LibraryTab.PLAYLISTS -> {
                if (uiState.playlists.isEmpty()) {
                    item {
                        EmptyLibraryNotice(
                            title = "У вас пока нет плейлистов",
                            subtitle = "Нажмите кнопку «+ Плейлист» выше или попросите ИИ-куратора создать подборку."
                        )
                    }
                } else {
                    items(uiState.playlists, key = { it.playlistId }) { playlist ->
                        PlaylistExpandableCard(
                            playlist = playlist,
                            allTracks = uiState.allTracks,
                            onPlayPlaylist = { tracks ->
                                tracks.firstOrNull()?.let { first -> onPlayTrack(first, tracks) }
                            },
                            onEditPlaylist = {
                                editPlaylistTitle = playlist.title
                                editPlaylistDesc = playlist.description
                                editingPlaylist = playlist
                            },
                            onDeletePlaylist = { onDeletePlaylist(playlist.playlistId) },
                            onRemoveTrack = { trackId ->
                                onRemoveTrackFromPlaylist(playlist.playlistId, trackId)
                            },
                            onMoveTrack = { fromIdx, toIdx ->
                                onReorderPlaylistTrack(playlist.playlistId, fromIdx, toIdx)
                            }
                        )
                    }
                }
            }

            LibraryTab.DOWNLOADS -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ElectricCyan.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Соблюдение лицензий: скачивание и офлайн-кэширование разрешено только для треков с открытой лицензией (Creative Commons / Jamendo / Music Form Studio). Официальные превью-потоки iTunes защищены правообладателями и работают только в режиме потокового стриминга.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                val downloadedTracks = uiState.allTracks.filter { it.id in uiState.downloadedTrackIds }
                if (downloadedTracks.isEmpty()) {
                    item {
                        EmptyLibraryNotice(
                            title = "Нет скачанных офлайн-треков",
                            subtitle = "Ниже показаны треки каталога, лицензия которых разрешает легальное офлайн-скачивание."
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "Доступно офлайн (${downloadedTracks.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(downloadedTracks, key = { "dl_tr_${it.id}" }) { track ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TrackRowItem(
                                track = track,
                                isCurrentTrack = playerState.currentTrack?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                isLiked = track.id in uiState.likedTrackIds,
                                isDownloaded = true,
                                reasonBadge = "Лицензия: ${track.licenseInfo}",
                                onPlayClick = { onPlayTrack(track, downloadedTracks) },
                                onLikeClick = { onToggleLike(track) },
                                onAddToPlaylistClick = { onAddTrackToPlaylist(track) },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeleteDownloadedTrack(track.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Удалить офлайн-файл",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                val downloadableCandidates = uiState.allTracks
                    .filter { it.isDownloadAllowed && it.id !in uiState.downloadedTrackIds }
                    .take(12)
                if (downloadableCandidates.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Доступно для легального скачивания по лицензии",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(downloadableCandidates, key = { "cand_dl_${it.id}" }) { track ->
                        TrackRowItem(
                            track = track,
                            isCurrentTrack = playerState.currentTrack?.id == track.id,
                            isPlaying = playerState.isPlaying,
                            isLiked = track.id in uiState.likedTrackIds,
                            isDownloaded = false,
                            reasonBadge = "Разрешено лицензией: ${track.licenseInfo}",
                            onPlayClick = { onPlayTrack(track, downloadableCandidates) },
                            onLikeClick = { onToggleLike(track) },
                            onAddToPlaylistClick = { onAddTrackToPlaylist(track) },
                            onDownloadClick = { onDownloadTrack(track) }
                        )
                    }
                }
            }

            LibraryTab.HISTORY -> {
                val historyTracks = uiState.historyItems.mapNotNull { h ->
                    uiState.allTracks.find { it.id == h.trackId }?.let { track -> track to h }
                }
                if (historyTracks.isEmpty()) {
                    item {
                        EmptyLibraryNotice(
                            title = "История прослушивания пуста",
                            subtitle = "Запустите «Мою волну» или выберите любой трек на главной странице."
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Записей в истории: ${historyTracks.size}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedButton(
                                onClick = onClearListeningHistory,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("clear_listening_history_button")
                            ) {
                                Text("Очистить историю")
                            }
                        }
                    }
                    items(historyTracks, key = { "hist_row_${it.first.id}" }) { (track, hist) ->
                        TrackRowItem(
                            track = track,
                            isCurrentTrack = playerState.currentTrack?.id == track.id,
                            isPlaying = playerState.isPlaying,
                            isLiked = hist.liked,
                            isDownloaded = track.id in uiState.downloadedTrackIds,
                            reasonBadge = "Прослушиваний: ${hist.playCount} • Пропусков: ${hist.skipCount} • Время: ${hist.totalListenMs / 1000} сек",
                            onPlayClick = { onPlayTrack(track, historyTracks.map { it.first }) },
                            onLikeClick = { onToggleLike(track) },
                            onAddToPlaylistClick = { onAddTrackToPlaylist(track) },
                            onDownloadClick = if (track.isDownloadAllowed) {
                                { onDownloadTrack(track) }
                            } else null
                        )
                    }
                }
            }

            LibraryTab.RECENT_ADDED -> {
                val recentAdded = uiState.newReleases.ifEmpty { uiState.allTracks.take(12) }
                items(recentAdded, key = { "rec_add_${it.id}" }) { track ->
                    TrackRowItem(
                        track = track,
                        isCurrentTrack = playerState.currentTrack?.id == track.id,
                        isPlaying = playerState.isPlaying,
                        isLiked = track.id in uiState.likedTrackIds,
                        isDownloaded = track.id in uiState.downloadedTrackIds,
                        reasonBadge = "Недавно добавлено в каталог (${track.releaseYear})",
                        onPlayClick = { onPlayTrack(track, recentAdded) },
                        onLikeClick = { onToggleLike(track) },
                        onAddToPlaylistClick = { onAddTrackToPlaylist(track) },
                        onDownloadClick = if (track.isDownloadAllowed) {
                            { onDownloadTrack(track) }
                        } else null
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
private fun ArtistSubscriptionRow(
    artist: Artist,
    isSubscribed: Boolean,
    onToggleSubscribe: () -> Unit,
    onLaunchWave: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLaunchWave)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveAlbumCover(
                primaryHex = artist.accentHex,
                secondaryHex = "#7C4DFF",
                title = artist.name,
                size = 50.dp,
                cornerRadius = 25.dp,
                coverUrl = artist.avatarUrl
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = artist.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${artist.primaryGenre} • Нажмите для запуска волны",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onLaunchWave) {
                Icon(
                    imageVector = Icons.Filled.Radio,
                    contentDescription = "Запустить волну артиста",
                    tint = ElectricCyan
                )
            }
            OutlinedButton(
                onClick = onToggleSubscribe,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("toggle_subscribe_artist_${artist.id}")
            ) {
                Text(if (isSubscribed) "В подписках ✓" else "Подписаться")
            }
        }
    }
}

@Composable
private fun SavedAlbumRow(
    album: Album,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onOpenAlbum: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAlbum)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveAlbumCover(
                primaryHex = album.coverColorPrimaryHex,
                secondaryHex = album.coverColorSecondaryHex,
                title = album.title,
                size = 52.dp,
                cornerRadius = 12.dp,
                coverUrl = album.coverUrl
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = album.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${album.artistName} • ${album.genre} (${album.releaseYear})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onToggleSave,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("toggle_save_album_${album.id}")
            ) {
                Text(if (isSaved) "Сохранено ✓" else "Сохранить")
            }
        }
    }
}

@Composable
private fun PlaylistExpandableCard(
    playlist: LocalPlaylistEntity,
    allTracks: List<Track>,
    onPlayPlaylist: (List<Track>) -> Unit,
    onEditPlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onRemoveTrack: (String) -> Unit,
    onMoveTrack: (Int, Int) -> Unit
) {
    val playlistTracks = playlist.trackIdsList().mapNotNull { id -> allTracks.find { it.id == id } }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("playlist_card_${playlist.playlistId}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveAlbumCover(
                    primaryHex = playlist.coverTintHex,
                    secondaryHex = "#7C4DFF",
                    title = playlist.title,
                    size = 56.dp,
                    coverUrl = playlistTracks.firstOrNull()?.coverUrl
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${playlistTracks.size} треков • ${playlist.description.ifBlank { "Пользовательский плейлист" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (playlistTracks.isNotEmpty()) {
                    IconButton(
                        onClick = { onPlayPlaylist(playlistTracks) },
                        modifier = Modifier.testTag("play_playlist_${playlist.playlistId}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Воспроизвести плейлист",
                            tint = ElectricCyan
                        )
                    }
                }
                IconButton(
                    onClick = onEditPlaylist,
                    modifier = Modifier.testTag("edit_playlist_${playlist.playlistId}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Переименовать плейлист",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDeletePlaylist,
                    modifier = Modifier.testTag("delete_playlist_${playlist.playlistId}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Удалить плейлист",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (playlistTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                playlistTracks.forEachIndexed { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. ${track.title} — ${track.artistName}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (index > 0) {
                            IconButton(
                                onClick = { onMoveTrack(index, index - 1) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowUpward,
                                    contentDescription = "Переместить выше",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (index < playlistTracks.lastIndex) {
                            IconButton(
                                onClick = { onMoveTrack(index, index + 1) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDownward,
                                    contentDescription = "Переместить ниже",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onRemoveTrack(track.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Удалить из плейлиста",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryNotice(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
