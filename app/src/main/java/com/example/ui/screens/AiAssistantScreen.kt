package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiAssistantResponse
import com.example.data.model.Track
import com.example.player.PlayerUiState
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.viewmodel.MusicFormUiState

private val AI_EXAMPLE_PROMPTS = listOf(
    "Подбери музыку для ночной поездки",
    "Хочу что-нибудь похожее на этот трек",
    "Создай плейлист для тренировки",
    "Найди спокойную музыку для чтения",
    "Открой для меня новых исполнителей",
    "Сделай подборку в стиле моего любимого аниме"
)

@Composable
fun AiAssistantScreen(
    uiState: MusicFormUiState,
    playerState: PlayerUiState,
    onPromptChange: (String) -> Unit,
    onSubmitPrompt: (String?) -> Unit,
    onSaveAiPlaylist: (AiAssistantResponse) -> Unit,
    onPlayTrack: (Track, List<Track>?, String?) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_assistant_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF141B36), Color(0xFF231344))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MUSIC FORM AI • GEMINI КУРАТОР",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Интеллектуальный подбор музыки",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            text = "ИИ анализирует ваш запрос, преобразует настроение в параметры поиска и находит реальные треки из доступного каталога с объяснением выбора.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCFD8EE)
                        )
                    }
                }
            }
        }

        // Preset Prompts
        item {
            Text(
                text = "Популярные запросы:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AI_EXAMPLE_PROMPTS) { example ->
                    AssistChip(
                        onClick = { onSubmitPrompt(example) },
                        label = { Text(example) },
                        modifier = Modifier.testTag("ai_preset_${example.hashCode()}")
                    )
                }
            }
        }

        // Prompt Input
        item {
            OutlinedTextField(
                value = uiState.aiPromptInput,
                onValueChange = onPromptChange,
                placeholder = { Text("Например: Подбери музыку для ночной поездки...") },
                trailingIcon = {
                    IconButton(
                        onClick = { onSubmitPrompt(null) },
                        enabled = !uiState.isAiLoading && uiState.aiPromptInput.isNotBlank(),
                        modifier = Modifier.testTag("ai_submit_button")
                    ) {
                        if (uiState.isAiLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = ElectricCyan
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить запрос ИИ",
                                tint = ElectricCyan
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_prompt_input")
            )
        }

        // AI Response Results
        uiState.lastAiResponse?.let { response ->
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_response_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ответ Music Form AI",
                            style = MaterialTheme.typography.titleLarge,
                            color = ElectricCyan
                        )
                        Text(
                            text = "Канал: ${response.sourceChannelRu}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("ai_source_channel_label")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = response.assistantExplanationRu,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = NeonViolet.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = response.criteriaSummaryRu,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        if (!response.isHonestEmptyMatch && response.recommendedTracks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val list = response.recommendedTracks.map { it.track }
                                        val first = list.first()
                                        onPlayTrack(first, list, response.assistantExplanationRu)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = ObsidianBlack
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("ai_play_all_button")
                                ) {
                                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Слушать подборку", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { onSaveAiPlaylist(response) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("ai_save_playlist_button")
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("В плейлист")
                                }
                            }
                        }
                    }
                }
            }

            items(response.recommendedTracks, key = { "ai_rec_${it.track.id}" }) { rec ->
                TrackRowItem(
                    track = rec.track,
                    isCurrentTrack = playerState.currentTrack?.id == rec.track.id,
                    isPlaying = playerState.isPlaying,
                    isLiked = rec.track.id in uiState.likedTrackIds,
                    reasonBadge = "${rec.matchScorePercent}% • ${rec.reasonRu}",
                    onPlayClick = {
                        onPlayTrack(
                            rec.track,
                            response.recommendedTracks.map { it.track },
                            rec.reasonRu
                        )
                    },
                    onLikeClick = { onToggleLike(rec.track) },
                    onAddToPlaylistClick = { onAddTrackToPlaylist(rec.track) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}
