package com.example.data.ai

import com.example.BuildConfig
import com.example.data.catalog.MusicCatalogProvider
import com.example.data.model.RecommendationItem
import com.example.data.model.Track
import com.example.data.model.WaveMood
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class AiCurationCriteria(
    val summaryRu: String = "",
    val targetGenres: List<String> = emptyList(),
    val moodTags: List<String> = emptyList(),
    val minBpm: Int = 60,
    val maxBpm: Int = 180,
    val discoverNewArtists: Boolean = false
)

data class AiAssistantResponse(
    val userPrompt: String,
    val assistantExplanationRu: String,
    val criteriaSummaryRu: String,
    val recommendedTracks: List<RecommendationItem>,
    val suggestedMood: WaveMood?,
    val isHonestEmptyMatch: Boolean = false,
    val sourceChannelRu: String = "Firebase AI Logic (gemini-3.5-flash)"
)

@Serializable
private data class GeminiRestRequest(
    val contents: List<GeminiContent>
)

@Serializable
private data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
private data class GeminiPart(
    val text: String
)

@Serializable
private data class GeminiRestResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null
)

private interface GeminiRestApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRestRequest
    ): GeminiRestResponse
}

/**
 * Music Form AI Curator powered by Firebase AI Logic (protected by Firebase App Check)
 * with Gemini REST API fallback and strict grounding against real tracks in MusicCatalogProvider.
 * Never hallucinates non-existent tracks; every recommended track is verified in the catalog,
 * and the active resolution channel (Firebase AI, REST, or local NLU analyzer) is reported transparently.
 */
class MusicFormAiService(
    private val catalogProvider: MusicCatalogProvider,
    private val enableNetworkCalls: Boolean = true
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val restService: GeminiRestApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiRestApi::class.java)
    }

    suspend fun curateMusicByPrompt(
        userPrompt: String,
        knownArtistNames: Set<String> = emptySet(),
        currentTrack: Track? = null
    ): AiAssistantResponse = withContext(Dispatchers.IO) {
        val trimmed = userPrompt.trim()
        if (trimmed.isEmpty()) {
            return@withContext AiAssistantResponse(
                userPrompt = "",
                assistantExplanationRu = "Введите музыкальный запрос, например: «Подбери музыку для ночной поездки» или «Создай плейлист для тренировки».",
                criteriaSummaryRu = "Ожидание запроса",
                recommendedTracks = emptyList(),
                suggestedMood = null,
                isHonestEmptyMatch = true,
                sourceChannelRu = "Проверка ввода"
            )
        }

        val availableGenres = catalogProvider.getAllGenres()
        val catalogSnapshot = catalogProvider.getAllTracks().joinToString("\n") {
            "- id=${it.id}, title=${it.title}, artist=${it.artistName}, genre=${it.genre}, bpm=${it.bpm}, tags=${it.moodTags.joinToString(",")}"
        }

        val systemPrompt = buildString {
            appendLine("Ты — музыкальный ИИ-куратор сервиса Music Form.")
            appendLine("Доступные жанры в реальном каталоге: ${availableGenres.joinToString(", ")}.")
            if (currentTrack != null) {
                appendLine("Сейчас играет трек: ${currentTrack.title} — ${currentTrack.artistName} (${currentTrack.genre}, ${currentTrack.bpm} BPM).")
            }
            appendLine("Каталог реальных треков:\n$catalogSnapshot")
            appendLine("Проанализируй запрос пользователя: \"$trimmed\"")
            appendLine("Верни ТОЛЬКО JSON-объект без markdown-блоков со полями:")
            appendLine("""{"summaryRu": "краткое объяснение выбора на русском (1-2 предложения)", "targetGenres": ["жанры из списка доступных"], "moodTags": ["ключевые теги"], "minBpm": 60, "maxBpm": 180, "discoverNewArtists": false}""")
            appendLine("Если пользователь просит жанр, которого вообще нет в каталоге (например, оперу XVIII века или тяжёлый кантри-блюграсс), укажи пустой список targetGenres: [] и честно напиши об этом в summaryRu.")
        }

        val (aiCriteria, channelLabel) = callGeminiForCriteria(systemPrompt, trimmed, currentTrack)
        matchCatalogWithCriteria(trimmed, aiCriteria, knownArtistNames, currentTrack, channelLabel)
    }

    private suspend fun callGeminiForCriteria(
        prompt: String,
        rawUserQuery: String,
        currentTrack: Track?
    ): Pair<AiCurationCriteria, String> {
        if (!enableNetworkCalls) {
            return analyzeQueryLocally(rawUserQuery, currentTrack) to "Семантический анализатор каталога (офлайн/резерв)"
        }
        // 1. Primary secure layer: Firebase AI Logic (protected via Firebase App Check, no client key exposure)
        try {
            val responseText = withTimeout(15_000L) {
                val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
                    .generativeModel("gemini-3.5-flash")
                generativeModel.generateContent(prompt).text
            }
            if (!responseText.isNullOrBlank()) {
                parseCriteriaJson(responseText)?.let {
                    return it to "Firebase AI Logic (gemini-3.5-flash)"
                }
            }
        } catch (e: TimeoutCancellationException) {
            // Timeout on Firebase AI Logic; fall through to REST or local NLU
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Fall through to REST or local semantic analyzer
        }

        // 2. Fallback: Gemini REST API if key is configured in Secrets / BuildConfig
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val text = withTimeout(15_000L) {
                    val res = restService.generateContent(
                        apiKey = apiKey,
                        request = GeminiRestRequest(
                            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
                        )
                    )
                    res.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }
                if (!text.isNullOrBlank()) {
                    parseCriteriaJson(text)?.let {
                        return it to "Gemini REST API (gemini-3.5-flash)"
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // Fall through to deterministic NLU analyzer
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Fall through to deterministic NLU analyzer
            }
        }

        // 3. Deterministic local semantic analyzer (ensures 100% reliability offline or without quota)
        return analyzeQueryLocally(rawUserQuery, currentTrack) to "Семантический анализатор каталога (офлайн/резерв)"
    }

    internal fun parseCriteriaJson(rawText: String): AiCurationCriteria? {
        val start = rawText.indexOf('{')
        val end = rawText.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        val jsonSlice = rawText.substring(start, end + 1)
        return runCatching { json.decodeFromString<AiCurationCriteria>(jsonSlice) }.getOrNull()
    }

    internal fun analyzeQueryLocally(query: String, currentTrack: Track?): AiCurationCriteria {
        val q = query.lowercase()
        return when {
            q.contains("опер") || q.contains("шансон") || q.contains("кантри") || q.contains("регги") -> {
                AiCurationCriteria(
                    summaryRu = "По вашему запросу «$query» в текущем лицензионном каталоге Music Form нет точных совпадений по этому узкому жанру.",
                    targetGenres = emptyList(),
                    moodTags = emptyList(),
                    minBpm = 0,
                    maxBpm = 0
                )
            }
            q.contains("ночн") || q.contains("поездк") || q.contains("трасс") || q.contains("рул") || q.contains("неон") -> {
                AiCurationCriteria(
                    summaryRu = "Для ночной поездки идеально подходят атмосферный Synthwave, мелодичный Indie и прогрессивная электроника с ритмом 100–130 BPM.",
                    targetGenres = listOf("Synthwave", "Electronic", "Indie"),
                    moodTags = listOf("ночь", "поездка", "трасса", "неон"),
                    minBpm = 98,
                    maxBpm = 145
                )
            }
            q.contains("трениров") || q.contains("спорт") || q.contains("бег") || q.contains("зал") || q.contains("кач") -> {
                AiCurationCriteria(
                    summaryRu = "Для интенсивной тренировки отобраны высокоэнергичные треки Drum & Bass, Cyber Rock и динамичный Synthwave с темпом 124–174 BPM.",
                    targetGenres = listOf("Drum & Bass", "Cyber Rock", "Synthwave", "Electronic"),
                    moodTags = listOf("тренировка", "спорт", "бег", "драйв", "энергия"),
                    minBpm = 122,
                    maxBpm = 180
                )
            }
            q.contains("чтен") || q.contains("спокойн") || q.contains("сон") || q.contains("отдых") || q.contains("тиш") -> {
                AiCurationCriteria(
                    summaryRu = "Для спокойного чтения и отдыха выбраны инструментальные композиции в жанрах Ambient, Classical и мягкий Lo-Fi без резких перепадов темпа.",
                    targetGenres = listOf("Ambient", "Classical", "Lo-Fi"),
                    moodTags = listOf("чтение", "спокойная", "фокус", "отдых"),
                    minBpm = 60,
                    maxBpm = 95
                )
            }
            q.contains("аниме") || q.contains("кибер") || q.contains("токио") || q.contains("опенинг") -> {
                AiCurationCriteria(
                    summaryRu = "Подборка в эстетике современного аниме и неонового Токио: взрывной Cyber Rock, японский Lo-Fi и футуристичный Synthwave.",
                    targetGenres = listOf("Cyber Rock", "Synthwave", "Lo-Fi"),
                    moodTags = listOf("аниме", "киберпанк", "драйв", "ночь"),
                    minBpm = 80,
                    maxBpm = 165
                )
            }
            q.contains("похож") && currentTrack != null -> {
                AiCurationCriteria(
                    summaryRu = "Подобраны треки, близкие к «${currentTrack.title}» (${currentTrack.artistName}) по жанру ${currentTrack.genre} и темпу около ${currentTrack.bpm} BPM.",
                    targetGenres = listOf(currentTrack.genre),
                    moodTags = currentTrack.moodTags,
                    minBpm = (currentTrack.bpm - 22).coerceAtLeast(60),
                    maxBpm = (currentTrack.bpm + 22).coerceAtMost(180)
                )
            }
            q.contains("нов") && (q.contains("исполнит") || q.contains("артист") || q.contains("откр")) -> {
                AiCurationCriteria(
                    summaryRu = "Специальная подборка для открытия новых и набирающих популярность исполнителей каталога Music Form.",
                    targetGenres = catalogProvider.getAllGenres(),
                    moodTags = emptyList(),
                    minBpm = 60,
                    maxBpm = 180,
                    discoverNewArtists = true
                )
            }
            else -> {
                AiCurationCriteria(
                    summaryRu = "По вашему запросу «$query» сформирована сбалансированная подборка из реального каталога Music Form.",
                    targetGenres = listOf("Electronic", "Synthwave", "Lo-Fi", "Indie"),
                    moodTags = listOf("фокус", "ночь", "энергия"),
                    minBpm = 70,
                    maxBpm = 160
                )
            }
        }
    }

    private fun matchCatalogWithCriteria(
        userPrompt: String,
        criteria: AiCurationCriteria,
        knownArtistNames: Set<String>,
        currentTrack: Track?,
        channelLabel: String
    ): AiAssistantResponse {
        if (criteria.targetGenres.isEmpty() && criteria.maxBpm == 0) {
            return AiAssistantResponse(
                userPrompt = userPrompt,
                assistantExplanationRu = criteria.summaryRu,
                criteriaSummaryRu = "Совпадений в каталоге: 0",
                recommendedTracks = emptyList(),
                suggestedMood = null,
                isHonestEmptyMatch = true,
                sourceChannelRu = channelLabel
            )
        }

        val allTracks = catalogProvider.getAllTracks()
        val artistsById = catalogProvider.getAllArtists().associateBy { it.id }

        val matched = allTracks.mapNotNull { track ->
            var score = 45
            val reasons = mutableListOf<String>()

            if (criteria.targetGenres.any { it.equals(track.genre, ignoreCase = true) }) {
                score += 30
                reasons.add("Жанр ${track.genre}")
            }
            val matchedTags = track.moodTags.filter { tag ->
                criteria.moodTags.any { cTag ->
                    tag.contains(cTag, ignoreCase = true) || cTag.contains(tag, ignoreCase = true)
                }
            }
            if (matchedTags.isNotEmpty()) {
                score += 20
                reasons.add("Атмосфера: ${matchedTags.take(2).joinToString(", ")}")
            }
            if (track.bpm in criteria.minBpm..criteria.maxBpm) {
                score += 12
                reasons.add("Темп ${track.bpm} BPM")
            }
            val isEmerging = artistsById[track.artistId]?.isEmergingArtist == true
            val isUnknownToUser = track.artistName !in knownArtistNames
            if (criteria.discoverNewArtists) {
                if (isUnknownToUser || isEmerging) {
                    score += 28
                    reasons.add("Новый для вас исполнитель")
                } else {
                    score -= 15
                }
            }
            if (currentTrack != null && track.id == currentTrack.id && userPrompt.lowercase().contains("похож")) {
                return@mapNotNull null
            }

            if (score < 60) return@mapNotNull null

            RecommendationItem(
                track = track,
                matchScorePercent = score.coerceIn(65, 99),
                reasonRu = reasons.joinToString(" • ").ifBlank { "Подобрано Music Form AI" },
                isNewArtistDiscovery = isUnknownToUser || isEmerging
            )
        }.sortedByDescending { it.matchScorePercent }

        if (matched.isEmpty()) {
            return AiAssistantResponse(
                userPrompt = userPrompt,
                assistantExplanationRu = "К сожалению, в подключённом каталоге сейчас нет композиций, точно соответствующих всем параметрам запроса «$userPrompt». Попробуйте уточнить жанр или настроение.",
                criteriaSummaryRu = "Точных совпадений не найдено",
                recommendedTracks = emptyList(),
                suggestedMood = null,
                isHonestEmptyMatch = true,
                sourceChannelRu = channelLabel
            )
        }

        val suggestedMood = when {
            criteria.targetGenres.contains("Drum & Bass") -> WaveMood.WORKOUT
            criteria.targetGenres.contains("Ambient") || criteria.targetGenres.contains("Classical") -> WaveMood.CALM
            criteria.targetGenres.contains("Synthwave") -> WaveMood.NIGHT_DRIVE
            criteria.targetGenres.contains("Lo-Fi") -> WaveMood.FOCUS
            else -> WaveMood.ENERGETIC
        }

        val criteriaBadge = buildString {
            if (criteria.targetGenres.isNotEmpty()) {
                append("Жанры: ${criteria.targetGenres.joinToString(", ")} • ")
            }
            append("Темп: ${criteria.minBpm}–${criteria.maxBpm} BPM")
            if (criteria.discoverNewArtists) {
                append(" • Фокус на новых артистах")
            }
        }

        return AiAssistantResponse(
            userPrompt = userPrompt,
            assistantExplanationRu = criteria.summaryRu,
            criteriaSummaryRu = criteriaBadge,
            recommendedTracks = matched.take(8),
            suggestedMood = suggestedMood,
            isHonestEmptyMatch = false,
            sourceChannelRu = channelLabel
        )
    }
}
