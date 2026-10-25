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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.model.WaveMood
import com.example.player.PlayerUiState
import com.example.ui.components.ExpressiveAlbumCover
import com.example.ui.components.GlowingSoundwaveVisualizer
import com.example.ui.components.MusicFormLogo
import com.example.ui.components.TrackRowItem
import com.example.ui.components.parseHexColor
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.viewmodel.MusicFormUiState

@Composable
fun HomeScreen(
    uiState: MusicFormUiState,
    playerState: PlayerUiState,
    licensingNoticeRu: String,
    onLaunchMyWave: (WaveMood?) -> Unit,
    onLaunchGenreWave: (String) -> Unit,
    onLaunchArtistWave: (Artist) -> Unit,
    onPlayTrack: (Track, List<Track>?, String?) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit = {},
    onSelectAlbum: (Album) -> Unit,
    onOpenGenresCatalog: (GenreCategory?) -> Unit = {},
    onRefreshOnlineCatalog: () -> Unit = {},
    onNavigateToAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // 1. Greeting Header + Catalog Status
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        MusicFormLogo(size = 44.dp, isAnimated = playerState.isPlaying)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Привет, ${uiState.userDisplayName}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Твоя музыка. Твоя волна. Твой мир",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ElectricCyan
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElectricCyan.copy(alpha = 0.16f),
                            modifier = Modifier
                                .clickable(onClick = onRefreshOnlineCatalog)
                                .testTag("home_refresh_catalog_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = "Пополнить онлайн-каталог",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${uiState.allTracks.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ElectricCyan
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NeonViolet.copy(alpha = 0.20f),
                            modifier = Modifier.clickable(onClick = onNavigateToAi)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "Music Form AI",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AI",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Hero Block: «Моя волна»
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("hero_my_wave_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF101935),
                                    Color(0xFF1E1342),
                                    Color(0xFF0B253A)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Radio,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ПЕРСОНАЛЬНОЕ РАДИО",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Моя волна",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "Бесконечный поток под ваш вкус и настроение",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFCFD8EE)
                                )
                            }

                            Button(
                                onClick = { onLaunchMyWave(uiState.waveSettings.mood) },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianBlack
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                                modifier = Modifier.testTag("home_launch_wave_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Запустить Мою волну"
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Поток",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        GlowingSoundwaveVisualizer(
                            isPlaying = playerState.isPlaying && playerState.isWaveModeActive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(WaveMood.entries) { mood ->
                                val selected = uiState.waveSettings.mood == mood
                                FilterChip(
                                    selected = selected,
                                    onClick = { onLaunchMyWave(mood) },
                                    label = { Text(mood.titleRu) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCyan.copy(alpha = 0.24f),
                                        selectedLabelColor = ElectricCyan
                                    ),
                                    modifier = Modifier.testTag("home_mood_chip_${mood.name}")
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. «Продолжить прослушивание»
        playerState.currentTrack?.let { current ->
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader(
                        title = "Продолжить прослушивание",
                        subtitle = "Восстановлено с последней позиции"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(current, playerState.queue, playerState.waveReasonBanner) }
                            .testTag("continue_listening_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ExpressiveAlbumCover(
                                    primaryHex = current.coverColorPrimaryHex,
                                    secondaryHex = current.coverColorSecondaryHex,
                                    title = current.title,
                                    size = 54.dp,
                                    coverUrl = current.coverUrl
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = current.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${current.artistName} • ${current.albumTitle}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = ElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val progress = if (playerState.durationMs > 0) {
                                (playerState.currentPositionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = ElectricCyan,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }
        }

        // 4. «Для тебя» (Персональные рекомендации)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = "Для тебя",
                    subtitle = "Персональные рекомендации на основе ваших лайков, жанров и истории"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.waveRecommendations.take(5).forEach { rec ->
                        TrackRowItem(
                            track = rec.track,
                            isCurrentTrack = playerState.currentTrack?.id == rec.track.id,
                            isPlaying = playerState.isPlaying,
                            isLiked = rec.track.id in uiState.likedTrackIds,
                            isDownloaded = rec.track.id in uiState.downloadedTrackIds,
                            reasonBadge = "${rec.matchScorePercent}% совпадение • ${rec.reasonRu}",
                            onPlayClick = {
                                onPlayTrack(
                                    rec.track,
                                    uiState.waveRecommendations.map { it.track },
                                    rec.reasonRu
                                )
                            },
                            onLikeClick = { onToggleLike(rec.track) },
                            onAddToPlaylistClick = { onAddTrackToPlaylist(rec.track) },
                            onDownloadClick = if (rec.track.isDownloadAllowed) {
                                { onDownloadTrack(rec.track) }
                            } else null
                        )
                    }
                }
            }
        }

        // 5. «Недавно прослушанное»
        if (uiState.historyItems.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(
                        title = "Недавно прослушанное",
                        subtitle = "Ваша живая история воспроизведения",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val recentTracks = uiState.historyItems.mapNotNull { h ->
                            uiState.allTracks.find { it.id == h.trackId }
                        }.take(10)
                        items(recentTracks, key = { "recent_${it.id}" }) { track ->
                            CompactTrackCard(
                                track = track,
                                onClick = { onPlayTrack(track, recentTracks, null) }
                            )
                        }
                    }
                }
            }
        }

        // 6. «Популярное сейчас»
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = "Популярное сейчас",
                    subtitle = "Лидеры прослушиваний каталога Music Form"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.popularTracks.take(5).forEach { track ->
                        TrackRowItem(
                            track = track,
                            isCurrentTrack = playerState.currentTrack?.id == track.id,
                            isPlaying = playerState.isPlaying,
                            isLiked = track.id in uiState.likedTrackIds,
                            isDownloaded = track.id in uiState.downloadedTrackIds,
                            onPlayClick = { onPlayTrack(track, uiState.popularTracks, null) },
                            onLikeClick = { onToggleLike(track) },
                            onAddToPlaylistClick = { onAddTrackToPlaylist(track) },
                            onDownloadClick = if (track.isDownloadAllowed) {
                                { onDownloadTrack(track) }
                            } else null
                        )
                    }
                }
            }
        }

        // 7. «Новые релизы»
        item {
            Column {
                SectionHeader(
                    title = "Новые релизы",
                    subtitle = "Свежие премьеры, альбомы и синглы",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.newReleases.take(16), key = { "new_${it.id}" }) { track ->
                        CompactTrackCard(
                            track = track,
                            badge = "NEW",
                            onClick = { onPlayTrack(track, uiState.newReleases, "Новый релиз") }
                        )
                    }
                }
            }
        }

        // 8. «Популярные исполнители»
        item {
            Column {
                SectionHeader(
                    title = "Популярные исполнители",
                    subtitle = "Запустите персональное радио по артисту",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.artists.take(18), key = { it.id }) { artist ->
                        ArtistCircleCard(
                            artist = artist,
                            onClick = { onLaunchArtistWave(artist) }
                        )
                    }
                }
            }
        }

        // 9. «Альбомы и синглы»
        item {
            Column {
                SectionHeader(
                    title = "Альбомы и синглы",
                    subtitle = "Нажмите на релиз для просмотра треклиста",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.albums.take(18), key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = { onSelectAlbum(album) })
                    }
                }
            }
        }

        // 10. «Музыка по настроению» + 29 Жанров
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Музыка по настроению и 29 жанров",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Перейдите в раздел «Жанры» или запустите поток одним касанием",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { onOpenGenresCatalog(null) },
                        modifier = Modifier.testTag("home_open_all_genres_button")
                    ) {
                        Text("Все 29 жанров →", color = ElectricCyan, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.genreCategories, key = { "home_g_${it.id}" }) { cat ->
                        val c1 = parseHexColor(cat.primaryColorHex, ElectricCyan)
                        val c2 = parseHexColor(cat.secondaryColorHex, NeonViolet)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(listOf(c1.copy(alpha = 0.8f), c2.copy(alpha = 0.8f))))
                                .clickable { onOpenGenresCatalog(cat) }
                                .testTag("genre_card_${cat.titleRu}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(172.dp)
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = cat.titleRu,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${cat.catalogTrackEstimate} треков",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 11. «Для тренировок»
        item {
            ThematicCarouselSection(
                title = "Для тренировок",
                subtitle = "Высокий BPM, электроника, рок, фонк и хип-хоп для спорта",
                tracks = uiState.workoutTracks,
                onPlayTrack = { tr -> onPlayTrack(tr, uiState.workoutTracks, "Подборка «Для тренировок»") }
            )
        }

        // 12. «Для учёбы»
        item {
            ThematicCarouselSection(
                title = "Для учёбы",
                subtitle = "Лоу-фай, инструментальная классика и эмбиент для концентрации",
                tracks = uiState.studyTracks,
                onPlayTrack = { tr -> onPlayTrack(tr, uiState.studyTracks, "Подборка «Для учёбы»") }
            )
        }

        // 13. «Для дороги»
        item {
            ThematicCarouselSection(
                title = "Для дороги",
                subtitle = "Синтвейв, инди, рок и зарубежные хиты для ночных трасс",
                tracks = uiState.roadTracks,
                onPlayTrack = { tr -> onPlayTrack(tr, uiState.roadTracks, "Подборка «Для дороги»") }
            )
        }

        // 14. «Для отдыха и сна»
        item {
            ThematicCarouselSection(
                title = "Для отдыха и сна",
                subtitle = "Чилл, медитативная музыка, акустика и мягкий эмбиент",
                tracks = uiState.sleepRelaxTracks,
                onPlayTrack = { tr -> onPlayTrack(tr, uiState.sleepRelaxTracks, "Подборка «Для отдыха и сна»") }
            )
        }

        // 15. «Аниме и игровые саундтреки»
        item {
            ThematicCarouselSection(
                title = "Аниме и игровые саундтреки",
                subtitle = "Эпические OST, J-pop, киберпанк-темы и симфонические партитуры",
                tracks = uiState.animeGamingTracks,
                onPlayTrack = { tr -> onPlayTrack(tr, uiState.animeGamingTracks, "Подборка «Аниме и игровые саундтреки»") }
            )
        }

        // 16. Catalog Provider & Licensing Transparency Banner
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = licensingNoticeRu,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ThematicCarouselSection(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    onPlayTrack: (Track) -> Unit
) {
    if (tracks.isEmpty()) return
    Column {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks.take(14), key = { "${title}_${it.id}" }) { track ->
                CompactTrackCard(
                    track = track,
                    badge = track.genre,
                    onClick = { onPlayTrack(track) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactTrackCard(
    track: Track,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box {
                ExpressiveAlbumCover(
                    primaryHex = track.coverColorPrimaryHex,
                    secondaryHex = track.coverColorSecondaryHex,
                    title = track.title,
                    size = 132.dp,
                    cornerRadius = 14.dp,
                    coverUrl = track.coverUrl
                )
                if (badge != null) {
                    Surface(
                        color = ElectricCyan.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = badge.take(14),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ObsidianBlack,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(164.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("album_card_${album.id}"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ExpressiveAlbumCover(
                primaryHex = album.coverColorPrimaryHex,
                secondaryHex = album.coverColorSecondaryHex,
                title = album.title,
                size = 140.dp,
                cornerRadius = 16.dp,
                coverUrl = album.coverUrl
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.artistName} • ${album.releaseYear}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistCircleCard(
    artist: Artist,
    onClick: () -> Unit
) {
    val accent = parseHexColor(artist.accentHex, ElectricCyan)
    Surface(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("artist_card_${artist.id}"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExpressiveAlbumCover(
                primaryHex = artist.accentHex,
                secondaryHex = "#7C4DFF",
                title = artist.name,
                size = 72.dp,
                cornerRadius = 36.dp,
                coverUrl = artist.avatarUrl
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist.primaryGenre,
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
