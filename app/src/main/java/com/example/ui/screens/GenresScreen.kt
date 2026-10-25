package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.GenreCategory
import com.example.data.model.Track
import com.example.player.PlayerUiState
import com.example.ui.components.ExpressiveAlbumCover
import com.example.ui.components.TrackRowItem
import com.example.ui.components.parseHexColor
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.viewmodel.MusicFormUiState

@Composable
fun GenresScreen(
    uiState: MusicFormUiState,
    playerState: PlayerUiState,
    onSelectGenreCategory: (GenreCategory?) -> Unit,
    onSelectSubgenre: (String?) -> Unit,
    onSelectGenreMood: (String?) -> Unit,
    onLoadMoreGenreTracks: () -> Unit,
    onLaunchGenreWave: (String) -> Unit,
    onPlayTrack: (Track, List<Track>?, String?) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onSelectAlbum: (Album) -> Unit,
    onToggleSaveAlbum: (Album) -> Unit,
    onSelectArtistWave: (Artist) -> Unit,
    onToggleFavoriteArtist: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCategory = uiState.selectedGenreCategory

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("genres_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (selectedCategory == null) {
            // OVERVIEW OF ALL 29 GENRES
            item {
                Column {
                    Text(
                        text = "Музыкальные жанры (${uiState.genreCategories.size})",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Полный каталог из 29 жанров, поджанров, настроений и живых онлайн-подборок",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Render 2-column rows for the 29 genres
            val chunkedGenres = uiState.genreCategories.chunked(2)
            items(chunkedGenres, key = { pair -> "genre_row_${pair.first().id}" }) { rowGenres ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowGenres.forEach { category ->
                        val representativeCover = uiState.allTracks
                            .firstOrNull {
                                it.genre.equals(category.titleRu, ignoreCase = true) &&
                                    !it.coverUrl.isNullOrBlank()
                            }?.coverUrl
                        GenreGridCard(
                            category = category,
                            coverUrl = representativeCover,
                            onClick = { onSelectGenreCategory(category) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowGenres.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            // DETAILED GENRE EXPLORER (Tracks, Subgenres, Moods, Albums, Artists, Curated Collections, Pagination)
            val c1 = parseHexColor(selectedCategory.primaryColorHex, ElectricCyan)
            val c2 = parseHexColor(selectedCategory.secondaryColorHex, NeonViolet)

            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(c1.copy(alpha = 0.85f), c2.copy(alpha = 0.85f), ObsidianBlack)))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onSelectGenreCategory(null) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("back_to_all_genres_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Все жанры",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Все 29 жанров", color = Color.White)
                            }

                            Button(
                                onClick = { onLaunchGenreWave(selectedCategory.titleRu) },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianBlack
                                ),
                                modifier = Modifier.testTag("launch_selected_genre_wave_button")
                            ) {
                                Icon(imageVector = Icons.Filled.Radio, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Волна жанра", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = selectedCategory.titleRu,
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White
                        )
                        Text(
                            text = "${selectedCategory.subtitleRu} • Доступно треков: ${uiState.selectedGenreTracks.size} (онлайн-каталог: ${selectedCategory.catalogTrackEstimate}+)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFE2E8F8)
                        )
                    }
                }
            }

            // Subgenres Filter Bar
            if (selectedCategory.subgenres.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "Поджанры «${selectedCategory.titleRu}»",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = uiState.selectedSubgenre == null,
                                    onClick = { onSelectSubgenre(null) },
                                    label = { Text("Все поджанры") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                        selectedLabelColor = ElectricCyan
                                    )
                                )
                            }
                            items(selectedCategory.subgenres) { sub ->
                                val selected = uiState.selectedSubgenre == sub
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectSubgenre(if (selected) null else sub) },
                                    label = { Text(sub) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                        selectedLabelColor = ElectricCyan
                                    ),
                                    modifier = Modifier.testTag("subgenre_chip_$sub")
                                )
                            }
                        }
                    }
                }
            }

            // Moods within Genre
            if (selectedCategory.moods.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "Настроения внутри жанра",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(selectedCategory.moods) { moodTag ->
                                val selected = uiState.selectedGenreMood == moodTag
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectGenreMood(if (selected) null else moodTag) },
                                    label = { Text("✦ $moodTag") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonViolet.copy(alpha = 0.25f),
                                        selectedLabelColor = ElectricCyan
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Curated Playlists for this Genre
            if (selectedCategory.curatedPlaylists.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "Тематические подборки жанра",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(selectedCategory.curatedPlaylists) { playlistName ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .width(210.dp)
                                        .clickable {
                                            val queue = uiState.selectedGenreTracks
                                            queue.firstOrNull()?.let { first ->
                                                onPlayTrack(first, queue, "Подборка «$playlistName» (${selectedCategory.titleRu})")
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ExpressiveAlbumCover(
                                            primaryHex = selectedCategory.primaryColorHex,
                                            secondaryHex = selectedCategory.secondaryColorHex,
                                            title = playlistName,
                                            size = 48.dp,
                                            cornerRadius = 12.dp,
                                            coverUrl = uiState.selectedGenreTracks.firstOrNull()?.coverUrl
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlistName,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Слушать подборку",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Artists in this Genre
            val genreArtists = uiState.artists.filter {
                it.primaryGenre.equals(selectedCategory.titleRu, ignoreCase = true) ||
                    uiState.selectedGenreTracks.any { tr -> tr.artistId == it.id || tr.artistName.equals(it.name, ignoreCase = true) }
            }.distinctBy { it.name }.take(12)

            if (genreArtists.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "Исполнители жанра (${genreArtists.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(genreArtists, key = { "gen_art_${it.id}" }) { artist ->
                                val isFav = artist.id in uiState.favoriteArtistIds || artist.name in uiState.favoriteArtistNames
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .width(156.dp)
                                        .clickable { onSelectArtistWave(artist) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        ExpressiveAlbumCover(
                                            primaryHex = artist.accentHex,
                                            secondaryHex = selectedCategory.secondaryColorHex,
                                            title = artist.name,
                                            size = 64.dp,
                                            cornerRadius = 32.dp,
                                            coverUrl = artist.avatarUrl
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        IconButton(
                                            onClick = { onToggleFavoriteArtist(artist) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                                contentDescription = "В любимые исполнители",
                                                tint = if (isFav) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Albums in this Genre
            val genreAlbums = uiState.albums.filter {
                it.genre.equals(selectedCategory.titleRu, ignoreCase = true) ||
                    uiState.selectedGenreTracks.any { tr -> tr.albumId == it.id }
            }.distinctBy { it.title }.take(12)

            if (genreAlbums.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "Альбомы и синглы жанра (${genreAlbums.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(genreAlbums, key = { "gen_alb_${it.id}" }) { album ->
                                val isSaved = album.id in uiState.savedAlbumIds
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .width(156.dp)
                                        .clickable { onSelectAlbum(album) }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        ExpressiveAlbumCover(
                                            primaryHex = album.coverColorPrimaryHex,
                                            secondaryHex = album.coverColorSecondaryHex,
                                            title = album.title,
                                            size = 136.dp,
                                            cornerRadius = 12.dp,
                                            coverUrl = album.coverUrl
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = album.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = album.artistName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { onToggleSaveAlbum(album) },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                                    contentDescription = "Сохранить альбом",
                                                    tint = if (isSaved) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tracks Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Треки жанра (${uiState.selectedGenreTracks.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (uiState.isCatalogLoadingMore) {
                        CircularProgressIndicator(
                            color = ElectricCyan,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            items(uiState.selectedGenreTracks, key = { "gen_tr_${it.id}" }) { track ->
                TrackRowItem(
                    track = track,
                    isCurrentTrack = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.isPlaying,
                    isLiked = track.id in uiState.likedTrackIds,
                    isDownloaded = track.id in uiState.downloadedTrackIds,
                    reasonBadge = if (track.subgenre.isNotBlank()) "Поджанр: ${track.subgenre}" else null,
                    onPlayClick = {
                        onPlayTrack(
                            track,
                            uiState.selectedGenreTracks,
                            "Жанр «${selectedCategory.titleRu}»"
                        )
                    },
                    onLikeClick = { onToggleLike(track) },
                    onAddToPlaylistClick = { onAddTrackToPlaylist(track) },
                    onDownloadClick = if (track.isDownloadAllowed) {
                        { onDownloadTrack(track) }
                    } else null
                )
            }

            // Pagination button
            item {
                Button(
                    onClick = onLoadMoreGenreTracks,
                    enabled = !uiState.isCatalogLoadingMore,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan.copy(alpha = 0.18f),
                        contentColor = ElectricCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("load_more_genre_tracks_button")
                ) {
                    Icon(imageVector = Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isCatalogLoadingMore) {
                            "Загрузка треков из онлайн-каталога..."
                        } else {
                            "Загрузить ещё треки жанра «${selectedCategory.titleRu}»"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
private fun GenreGridCard(
    category: GenreCategory,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c1 = parseHexColor(category.primaryColorHex, ElectricCyan)
    val c2 = parseHexColor(category.secondaryColorHex, NeonViolet)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = modifier
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(c1.copy(alpha = 0.85f), c2.copy(alpha = 0.85f))))
            .clickable(onClick = onClick)
            .testTag("genre_tile_${category.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            if (!coverUrl.isNullOrBlank()) {
                ExpressiveAlbumCover(
                    primaryHex = category.primaryColorHex,
                    secondaryHex = category.secondaryColorHex,
                    title = category.titleRu,
                    size = 54.dp,
                    cornerRadius = 12.dp,
                    coverUrl = coverUrl,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = category.titleRu,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.subtitleRu,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.38f)
                ) {
                    Text(
                        text = "${category.catalogTrackEstimate} треков",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
