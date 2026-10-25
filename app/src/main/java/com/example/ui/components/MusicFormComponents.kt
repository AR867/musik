package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.model.Track
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import kotlin.math.PI
import kotlin.math.sin

fun parseHexColor(hex: String, fallback: Color = ElectricCyan): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        val value = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000L or value)
        } else {
            Color(value)
        }
    } catch (_: Exception) {
        fallback
    }
}

fun formatDurationShort(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}

/**
 * Music Form App Icon / Brand Logo with reactive glowing frame when audio is playing.
 */
@Composable
fun MusicFormLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    isAnimated: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_phase"
    )
    val glowAlpha = if (isAnimated) (0.55f + 0.35f * sin(phase).toFloat()).coerceIn(0.25f, 0.95f) else 0.55f

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color(0xFF010101))
            .border(
                width = if (isAnimated) 1.5.dp else 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        ElectricCyan.copy(alpha = glowAlpha),
                        NeonViolet.copy(alpha = glowAlpha)
                    )
                ),
                shape = RoundedCornerShape(size * 0.28f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.music_form_app_icon),
            contentDescription = "Иконка Music Form",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Glowing reactive soundwave visualizer for «Моя волна» and Full Player.
 */
@Composable
fun GlowingSoundwaveVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    primaryColor: Color = ElectricCyan,
    secondaryColor: Color = NeonViolet,
    barCount: Int = 28
) {
    val transition = rememberInfiniteTransition(label = "soundwave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val spacing = w / (barCount * 1.8f)
        val barWidth = spacing * 0.85f
        val brush = Brush.verticalGradient(
            colors = listOf(primaryColor, ElectricBlue, secondaryColor)
        )

        for (i in 0 until barCount) {
            val normalizedIdx = i.toFloat() / barCount.toFloat()
            val envelope = sin(normalizedIdx * PI).toFloat()
            val dynamicWave = if (isPlaying) {
                0.35f + 0.65f * ((sin(phase + i * 0.45f) + 1f) * 0.5f) * envelope
            } else {
                0.18f + 0.15f * envelope
            }
            val barH = (h * dynamicWave).coerceAtLeast(h * 0.12f)
            val x = (w / barCount) * i + (w / barCount - barWidth) * 0.5f
            val y = (h - barH) * 0.5f

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
        }
    }
}

/**
 * Expressive album cover artwork supporting real remote coverUrl (via Coil AsyncImage)
 * with vinyl/soundwave geometry and glowing gradient fallback.
 */
@Composable
fun ExpressiveAlbumCover(
    primaryHex: String,
    secondaryHex: String,
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 14.dp,
    coverUrl: String? = null
) {
    val c1 = parseHexColor(primaryHex, ElectricCyan)
    val c2 = parseHexColor(secondaryHex, NeonViolet)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(listOf(c1, c2))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            drawCircle(
                color = Color.Black.copy(alpha = 0.24f),
                radius = w * 0.38f,
                center = Offset(w * 0.72f, h * 0.28f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = w * 0.24f,
                center = Offset(w * 0.30f, h * 0.72f),
                style = Stroke(width = w * 0.04f)
            )
        }
        Text(
            text = title.take(2).uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Обложка $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Reusable Track Row item with real cover art, Play/Pause indicator, Like toggle,
 * optional legal Download button, Add to Playlist button, and optional recommendation reason badge.
 */
@Composable
fun TrackRowItem(
    track: Track,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    reasonBadge: String? = null,
    isDownloaded: Boolean = false,
    onPlayClick: () -> Unit,
    onLikeClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onDownloadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isCurrentTrack) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlayClick)
            .testTag("track_item_${track.id}"),
        color = bgColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ExpressiveAlbumCover(
                        primaryHex = track.coverColorPrimaryHex,
                        secondaryHex = track.coverColorSecondaryHex,
                        title = track.title,
                        size = 50.dp,
                        cornerRadius = 12.dp,
                        coverUrl = track.coverUrl
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCurrentTrack && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isCurrentTrack && isPlaying) "Пауза" else "Воспроизвести ${track.title}",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCurrentTrack) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Сейчас играет",
                                tint = ElectricCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrentTrack) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${track.artistName} • ${track.genre} • ${formatDurationShort(track.durationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val badgeText = if (track.isSynthesizedDemo) {
                            "Демо-каталог"
                        } else {
                            track.sourceProvider
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (track.isSynthesizedDemo) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                ElectricCyan
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isDownloaded) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = "Скачано офлайн",
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (onDownloadClick != null && track.isDownloadAllowed) {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.testTag("download_button_${track.id}")
                    ) {
                        Icon(
                            imageVector = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                            contentDescription = if (isDownloaded) "Скачано" else "Скачать по лицензии",
                            tint = if (isDownloaded) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.testTag("like_button_${track.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isLiked) "Убрать лайк" else "Нравится",
                        tint = if (isLiked) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onAddToPlaylistClick,
                    modifier = Modifier.testTag("add_playlist_button_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Добавить в плейлист",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!reasonBadge.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElectricCyan.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✦ $reasonBadge",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
