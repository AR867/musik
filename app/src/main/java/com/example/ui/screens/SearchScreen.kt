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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.SearchFilter
import com.example.data.model.SearchSortOrder
import com.example.data.model.Track
import com.example.player.PlayerUiState
import com.example.ui.components.ExpressiveAlbumCover
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.viewmodel.MusicFormUiState

@Composable
fun SearchScreen(
    uiState: MusicFormUiState,
    playerState: PlayerUiState,
    onQueryChange: (String, SearchFilter, SearchSortOrder) -> Unit,
    onLoadMoreRemoteSearch: () -> Unit = {},
    onCommitSearch: (String) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlayTrack: (Track, List<Track>?) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit = {},
    onSelectArtistWave: (Artist) -> Unit,
    onSelectAlbum: (Album) -> Unit,
    onSelectGenreWave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Поиск по каталогу",
                    style = MaterialTheme.typography.headlineLarge
                )
                if (uiState.isRemoteSearchLoading) {
                    CircularProgressIndicator(
                        color = ElectricCyan,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { onQueryChange(it, uiState.searchFilter, uiState.searchSortOrder) },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("", uiState.searchFilter, uiState.searchSortOrder) },
                            modifier = Modifier.testTag("search_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Очистить запрос"
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onCommitSearch(uiState.searchQuery) }
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input_field")
            )

            // Search-as-you-type autocomplete suggestions
            if (uiState.searchQuery.isNotBlank() && uiState.searchResult.querySuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.searchResult.querySuggestions) { suggestion ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                onQueryChange(suggestion, uiState.searchFilter, uiState.searchSortOrder)
                                onCommitSearch(suggestion)
                            },
                            label = { Text("🔍 $suggestion") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = NeonViolet.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filters
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchFilter.entries) { filter ->
                    val selected = uiState.searchFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { onQueryChange(uiState.searchQuery, filter, uiState.searchSortOrder) },
                        label = { Text(filter.titleRu) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("search_filter_${filter.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sort Order Row (Popularity, Newest, Duration, Relevance, Title)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Сортировка",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchSortOrder.entries) { sortOrder ->
                        val selected = uiState.searchSortOrder == sortOrder
                        FilterChip(
                            selected = selected,
                            onClick = { onQueryChange(uiState.searchQuery, uiState.searchFilter, sortOrder) },
                            label = { Text(sortOrder.titleRu) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonViolet.copy(alpha = 0.25f),
                                selectedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("search_sort_${sortOrder.name}")
                        )
                    }
                }
            }
        }

        // If query is blank: show Search History + Quick Browse Genres + Popular Tracks
        if (uiState.searchQuery.isBlank()) {
            if (uiState.searchHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "История запросов",
                            style = MaterialTheme.typography.titleLarge
                        )
                        TextButton(onClick = onClearHistory) {
                            Text("Очистить")
                        }
                    }
                }
                items(uiState.searchHistory, key = { "hist_${it.query}" }) { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQueryChange(item.query, uiState.searchFilter, uiState.searchSortOrder) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item.query,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRemoveHistoryItem(item.query) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Удалить запрос",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Популярные жанры для быстрого поиска",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.genres.take(15)) { genre ->
                        FilterChip(
                            selected = false,
                            onClick = { onQueryChange(genre, SearchFilter.ALL, uiState.searchSortOrder) },
                            label = { Text(genre) }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Быстрые результаты",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.popularTracks.take(6).forEach { track ->
                        TrackRowItem(
                            track = track,
                            isCurrentTrack = playerState.currentTrack?.id == track.id,
                            isPlaying = playerState.isPlaying,
                            isLiked = track.id in uiState.likedTrackIds,
                            isDownloaded = track.id in uiState.downloadedTrackIds,
                            onPlayClick = { onPlayTrack(track, uiState.popularTracks) },
                            onLikeClick = { onToggleLike(track) },
                            onAddToPlaylistClick = { onAddTrackToPlaylist(track) },
                            onDownloadClick = if (track.isDownloadAllowed) {
                                { onDownloadTrack(track) }
                            } else null
                        )
                    }
                }
            }
        } else {
            val res = uiState.searchResult
            val hasAnyResults = res.tracks.isNotEmpty() ||
                res.artists.isNotEmpty() ||
                res.albums.isNotEmpty() ||
                res.genres.isNotEmpty() ||
                res.matchingPlaylists.isNotEmpty()

            if (!hasAnyResults) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .testTag("search_empty_state")
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Точных совпадений по запросу «${uiState.searchQuery}» пока нет",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Мы подготовили похожую музыку из каталога ниже, или нажмите кнопку загрузки из глобального онлайн-каталога.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onLoadMoreRemoteSearch,
                                enabled = !uiState.isRemoteSearchLoading,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan.copy(alpha = 0.2f),
                                    contentColor = ElectricCyan
                                )
                            ) {
                                Icon(imageVector = Icons.Filled.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Искать «${uiState.searchQuery}» в онлайн-каталоге")
                            }
                        }
                    }
                }

                // Similar Music Suggestions on Empty State
                if (res.similarSuggestions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Похожая музыка, которая может вам понравиться",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(res.similarSuggestions, key = { "sim_${it.id}" }) { track ->
                        TrackRowItem(
                            track = track,
                            isCurrentTrack = playerState.currentTrack?.id == track.id,
                            isPlaying = playerState.isPlaying,
                            isLiked = track.id in uiState.likedTrackIds,
                            isDownloaded = track.id in uiState.downloadedTrackIds,
                            reasonBadge = "Похожая рекомендация • ${track.genre}",
                            onPlayClick = { onPlayTrack(track, res.similarSuggestions) },
                            onLikeClick = { onToggleLike(track) },
                            onAddToPlaylistClick = { onAddTrackToPlaylist(track) },
                            onDownloadClick = if (track.isDownloadAllowed) {
                                { onDownloadTrack(track) }
                            } else null
                        )
                    }
                }
            } else {
                if (res.tracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Треки (${res.tracks.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(res.tracks, key = { "sr_tr_${it.id}" }) { track ->
                        TrackRowItem(
                            track = track,
                            isCurrentTrack = playerState.currentTrack?.id == track.id,
                            isPlaying = playerState.isPlaying,
                            isLiked = track.id in uiState.likedTrackIds,
                            isDownloaded = track.id in uiState.downloadedTrackIds,
                            onPlayClick = {
                                onCommitSearch(uiState.searchQuery)
                                onPlayTrack(track, res.tracks)
                            },
                            onLikeClick = { onToggleLike(track) },
                            onAddToPlaylistClick = { onAddTrackToPlaylist(track) },
                            onDownloadClick = if (track.isDownloadAllowed) {
                                { onDownloadTrack(track) }
                            } else null
                        )
                    }

                    item {
                        Button(
                            onClick = onLoadMoreRemoteSearch,
                            enabled = !uiState.isRemoteSearchLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan.copy(alpha = 0.16f),
                                contentColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_load_more_remote_button")
                        ) {
                            Icon(imageVector = Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isRemoteSearchLoading) {
                                    "Загрузка результатов из онлайн-каталога..."
                                } else {
                                    "Загрузить ещё треки по запросу «${uiState.searchQuery}»"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (res.artists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Исполнители (${res.artists.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(res.artists, key = { "sr_art_${it.id}" }) { artist ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCommitSearch(uiState.searchQuery)
                                    onSelectArtistWave(artist)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExpressiveAlbumCover(
                                    primaryHex = artist.accentHex,
                                    secondaryHex = "#7C4DFF",
                                    title = artist.name,
                                    size = 44.dp,
                                    cornerRadius = 22.dp,
                                    coverUrl = artist.avatarUrl
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(artist.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${artist.primaryGenre} • ${artist.bio}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (res.albums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Альбомы и синглы (${res.albums.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(res.albums, key = { "sr_alb_${it.id}" }) { album ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCommitSearch(uiState.searchQuery)
                                    onSelectAlbum(album)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExpressiveAlbumCover(
                                    primaryHex = album.coverColorPrimaryHex,
                                    secondaryHex = album.coverColorSecondaryHex,
                                    title = album.title,
                                    size = 48.dp,
                                    cornerRadius = 10.dp,
                                    coverUrl = album.coverUrl
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(album.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${album.artistName} • ${album.genre} (${album.releaseYear})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (res.genres.isNotEmpty()) {
                    item {
                        Text(
                            text = "Жанры (${res.genres.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(res.genres, key = { "sr_gen_$it" }) { genre ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectGenreWave(genre) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Radio,
                                    contentDescription = null,
                                    tint = ElectricCyan
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Запустить волну жанра: $genre", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }

                if (res.matchingPlaylists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Подборки и плейлисты (${res.matchingPlaylists.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(res.matchingPlaylists, key = { "sr_pl_$it" }) { plName ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    res.tracks.firstOrNull()?.let { first ->
                                        onPlayTrack(first, res.tracks)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = ElectricCyan
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(plName, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}
