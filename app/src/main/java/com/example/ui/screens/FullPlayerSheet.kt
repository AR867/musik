package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.data.model.RepeatModeState
import com.example.data.model.Track
import com.example.player.PlayerUiState
import com.example.ui.components.ExpressiveAlbumCover
import com.example.ui.components.GlowingSoundwaveVisualizer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBlack

fun formatDurationMmSs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun MiniPlayerBar(
    playerState: PlayerUiState,
    isLiked: Boolean,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleLike: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack ?: return
    val progress = if (playerState.durationMs > 0) {
        (playerState.currentPositionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onExpandPlayer)
            .testTag("mini_player_bar"),
        color = Color(0xFF171D30).copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveAlbumCover(
                    primaryHex = track.coverColorPrimaryHex,
                    secondaryHex = track.coverColorSecondaryHex,
                    title = track.title,
                    size = 44.dp,
                    cornerRadius = 10.dp,
                    coverUrl = track.coverUrl
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playerState.playbackErrorRu ?: "${track.artistName} • ${track.genre}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (playerState.playbackErrorRu != null) {
                            Color(0xFFFF8A80)
                        } else {
                            Color(0xFFA9B4CC)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { onToggleLike(track) },
                    modifier = Modifier.testTag("mini_player_like_button")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Нравится",
                        tint = if (isLiked) Color(0xFFFF4081) else Color.White
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.testTag("mini_player_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Пауза" else "Воспроизвести",
                        tint = ElectricCyan
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.testTag("mini_player_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = Color.White
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = ElectricCyan,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable
fun FullPlayerOverlay(
    playerState: PlayerUiState,
    likedTrackIds: Set<String>,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleLike: (Track) -> Unit,
    onSelectQueueTrack: (Track) -> Unit,
    onMoveQueueTrack: (Int, Int) -> Unit,
    onRemoveQueueTrack: (String) -> Unit
) {
    val track = playerState.currentTrack ?: return
    val isLiked = track.id in likedTrackIds

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF101830),
                        ObsidianBlack,
                        ObsidianBlack
                    )
                )
            )
            .testTag("full_player_overlay")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.testTag("collapse_player_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Свернуть плеер",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (playerState.isWaveModeActive) "МОЯ ВОЛНА" else "СЕЙЧАС ИГРАЕТ",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                        Text(
                            text = playerState.activeStreamFormat,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFA9B4CC)
                        )
                    }
                    IconButton(
                        onClick = { onToggleLike(track) },
                        modifier = Modifier.testTag("full_player_like_button")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Нравится",
                            tint = if (isLiked) Color(0xFFFF4081) else Color.White
                        )
                    }
                }
            }

            // Expressive Cover + Visualizer
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ExpressiveAlbumCover(
                        primaryHex = track.coverColorPrimaryHex,
                        secondaryHex = track.coverColorSecondaryHex,
                        title = track.title,
                        size = 220.dp,
                        cornerRadius = 28.dp,
                        coverUrl = track.coverUrl
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                    Text(
                        text = "${track.artistName} • ${track.albumTitle} (${track.genre})",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFCFD8EE)
                    )
                    if (!playerState.waveReasonBanner.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "✦ ${playerState.waveReasonBanner}",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                    }
                    if (playerState.playbackErrorRu != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = playerState.playbackErrorRu,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFF8A80),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    GlowingSoundwaveVisualizer(
                        isPlaying = playerState.isPlaying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    )
                }
            }

            // Seek Slider & Timestamps
            item {
                Column {
                    Slider(
                        value = playerState.currentPositionMs.toFloat(),
                        onValueChange = { onSeekTo(it.toLong()) },
                        valueRange = 0f..playerState.durationMs.coerceAtLeast(1000L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("player_seek_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMmSs(playerState.currentPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA9B4CC)
                        )
                        Text(
                            text = formatDurationMmSs(playerState.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA9B4CC)
                        )
                    }
                }
            }

            // Transport Controls Row (Shuffle, Previous, Play/Pause, Next, Repeat)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.testTag("player_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Перемешать",
                            tint = if (playerState.isShuffleEnabled) ElectricCyan else Color(0xFFA9B4CC)
                        )
                    }

                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Предыдущий трек",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = ElectricCyan,
                        modifier = Modifier
                            .size(68.dp)
                            .clickable(onClick = onTogglePlayPause)
                            .testTag("full_player_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Пауза" else "Воспроизвести",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Следующий трек",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier.testTag("player_repeat_button")
                    ) {
                        Icon(
                            imageVector = if (playerState.repeatMode == RepeatModeState.ONE) {
                                Icons.Filled.RepeatOne
                            } else {
                                Icons.Filled.Repeat
                            },
                            contentDescription = "Режим повтора",
                            tint = if (playerState.repeatMode != RepeatModeState.OFF) ElectricCyan else Color(0xFFA9B4CC)
                        )
                    }
                }
            }

            // Volume Control
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Громкость",
                        tint = Color(0xFFA9B4CC)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Slider(
                        value = playerState.volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("player_volume_slider")
                    )
                }
            }

            // Playback Queue Header
            item {
                Text(
                    text = "Очередь воспроизведения (${playerState.queue.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            itemsIndexed(playerState.queue, key = { idx, t -> "q_${idx}_${t.id}" }) { index, qTrack ->
                val isCurrent = qTrack.id == track.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCurrent) {
                        ElectricCyan.copy(alpha = 0.16f)
                    } else {
                        Color.White.copy(alpha = 0.06f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectQueueTrack(qTrack) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. ${qTrack.title} — ${qTrack.artistName}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isCurrent) ElectricCyan else Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (index > 0) {
                            IconButton(
                                onClick = { onMoveQueueTrack(index, index - 1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowUpward,
                                    contentDescription = "Выше",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (index < playerState.queue.lastIndex) {
                            IconButton(
                                onClick = { onMoveQueueTrack(index, index + 1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDownward,
                                    contentDescription = "Ниже",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (playerState.queue.size > 1) {
                            IconButton(
                                onClick = { onRemoveQueueTrack(qTrack.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Удалить из очереди",
                                    tint = Color(0xFFA9B4CC),
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
