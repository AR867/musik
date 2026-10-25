package com.example.data.remote

import com.example.BuildConfig
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.GenreCategory
import com.example.data.model.MusicFormGenresTaxonomy
import com.example.data.model.PaginatedCatalogPage
import com.example.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// --- JAMENDO API v3.0 DTOs ---

@Serializable
data class JamendoTracksResponse(
    val headers: JamendoHeaders? = null,
    val results: List<JamendoTrackDto> = emptyList()
)

@Serializable
data class JamendoHeaders(
    val status: String = "",
    val code: Int = 0,
    @SerialName("error_message") val errorMessage: String = "",
    @SerialName("results_count") val resultsCount: Int = 0,
    @SerialName("results_fullcount") val resultsFullCount: Int = 0
)

@Serializable
data class JamendoTrackDto(
    val id: String = "",
    val name: String = "",
    val duration: Int = 0, // seconds
    @SerialName("artist_id") val artistId: String = "",
    @SerialName("artist_name") val artistName: String = "",
    @SerialName("album_id") val albumId: String = "",
    @SerialName("album_name") val albumName: String = "",
    @SerialName("releasedate") val releaseDate: String = "",
    @SerialName("album_image") val albumImage: String = "",
    val image: String = "",
    val audio: String = "",
    val audiodownload: String = "",
    @SerialName("audiodownload_allowed") val audioDownloadAllowed: Boolean = false,
    @SerialName("license_ccurl") val licenseCcUrl: String = "",
    val musicinfo: JamendoMusicInfoDto? = null
)

@Serializable
data class JamendoMusicInfoDto(
    val tags: JamendoTagsDto? = null
)

@Serializable
data class JamendoTagsDto(
    val genres: List<String> = emptyList(),
    val instruments: List<String> = emptyList(),
    val vartags: List<String> = emptyList()
)

interface JamendoApiService {
    @GET("v3.0/tracks/")
    suspend fun getTracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("include") include: String = "musicinfo",
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("order") order: String = "popularity_total",
        @Query("fuzzytags") fuzzyTags: String? = null,
        @Query("namesearch") nameSearch: String? = null,
        @Query("search") search: String? = null
    ): JamendoTracksResponse
}

// --- APPLE ITUNES PUBLIC CATALOG API DTOs ---

@Serializable
data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesTrackDto> = emptyList()
)

@Serializable
data class ItunesTrackDto(
    val trackId: Long = 0L,
    val artistId: Long = 0L,
    val collectionId: Long = 0L,
    val trackName: String = "",
    val artistName: String = "",
    val collectionName: String = "",
    val previewUrl: String = "",
    val artworkUrl100: String = "",
    val trackTimeMillis: Long = 0L,
    val releaseDate: String = "",
    val primaryGenreName: String = "",
    val country: String = ""
)

interface ItunesSearchApiService {
    @GET("search")
    suspend fun searchTracks(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("country") country: String = "US"
    ): ItunesSearchResponse
}

/**
 * Official Remote Music Catalog Client supporting:
 * 1. Jamendo Music API v3.0 (600,000+ Creative Commons full MP3 tracks when JAMENDO_CLIENT_ID is configured in Secrets).
 * 2. Apple iTunes Official Public Catalog API (millions of real songs, artists, albums, 600x600 covers, and AAC preview streams out-of-the-box).
 */
class RemoteMusicCatalogClient(
    private val jamendoClientIdOverride: String? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val jamendoApi: JamendoApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.jamendo.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(JamendoApiService::class.java)
    }

    private val itunesApi: ItunesSearchApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ItunesSearchApiService::class.java)
    }

    fun isJamendoConfigured(): Boolean {
        val cid = (jamendoClientIdOverride ?: BuildConfig.JAMENDO_CLIENT_ID).trim()
        return cid.isNotBlank() && cid != "MY_JAMENDO_CLIENT_ID"
    }

    fun activeRemoteSourceName(): String {
        return if (isJamendoConfigured()) {
            "Jamendo Music API v3.0 (Creative Commons Full MP3) + Apple iTunes Catalog API"
        } else {
            "Apple iTunes Official Catalog API (реальные метаданные, обложки и AAC-потоки)"
        }
    }

    suspend fun fetchTracksByGenre(
        genreCategory: GenreCategory,
        subgenreFilter: String? = null,
        offset: Int = 0,
        limit: Int = 25
    ): PaginatedCatalogPage = withContext(Dispatchers.IO) {
        val searchTerm = if (!subgenreFilter.isNullOrBlank()) {
            "$subgenreFilter ${genreCategory.apiQueryTerm}"
        } else {
            genreCategory.apiQueryTerm
        }

        // 1. Try Jamendo API v3.0 first if JAMENDO_CLIENT_ID is configured
        if (isJamendoConfigured()) {
            val clientId = (jamendoClientIdOverride ?: BuildConfig.JAMENDO_CLIENT_ID).trim()
            try {
                val res = jamendoApi.getTracks(
                    clientId = clientId,
                    limit = limit,
                    offset = offset,
                    fuzzyTags = genreCategory.jamendoTag,
                    search = subgenreFilter
                )
                if (res.results.isNotEmpty()) {
                    val mappedTracks = res.results.mapNotNull { dto ->
                        mapJamendoDtoToTrack(dto, genreCategory.titleRu, subgenreFilter ?: "")
                    }
                    if (mappedTracks.isNotEmpty()) {
                        return@withContext buildPageFromTracks(
                            tracks = mappedTracks,
                            offset = offset,
                            limit = limit,
                            totalEstimate = res.headers?.resultsFullCount?.takeIf { it > 0 }
                                ?: genreCategory.catalogTrackEstimate,
                            sourceProvider = "Jamendo Creative Commons API"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to Apple iTunes Catalog API
            }
        }

        // 2. Official Apple iTunes Public Catalog API
        val countryCode = if (genreCategory.id == "russian") "RU" else "US"
        try {
            val response = itunesApi.searchTracks(
                term = searchTerm,
                limit = limit,
                offset = offset,
                country = countryCode
            )
            val mappedTracks = response.results.mapNotNull { dto ->
                mapItunesDtoToTrack(
                    dto = dto,
                    canonicalGenreRu = genreCategory.titleRu,
                    subgenreRu = subgenreFilter ?: genreCategory.subgenres.firstOrNull().orEmpty(),
                    primaryHex = genreCategory.primaryColorHex,
                    secondaryHex = genreCategory.secondaryColorHex
                )
            }
            buildPageFromTracks(
                tracks = mappedTracks,
                offset = offset,
                limit = limit,
                totalEstimate = genreCategory.catalogTrackEstimate,
                sourceProvider = "Apple iTunes Official Catalog API"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PaginatedCatalogPage(
                tracks = emptyList(),
                pageOffset = offset,
                pageSize = limit,
                hasMore = false,
                totalAvailableEstimate = genreCategory.catalogTrackEstimate,
                sourceProvider = activeRemoteSourceName(),
                errorRu = "Ошибка загрузки каталога (${e.localizedMessage ?: "нет соединения с сервером"})."
            )
        }
    }

    suspend fun searchRemoteCatalog(
        query: String,
        offset: Int = 0,
        limit: Int = 30
    ): PaginatedCatalogPage = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext PaginatedCatalogPage()
        }

        val combinedTracks = mutableListOf<Track>()
        var sourceLabel = "Apple iTunes Official Catalog API"

        if (isJamendoConfigured()) {
            val clientId = (jamendoClientIdOverride ?: BuildConfig.JAMENDO_CLIENT_ID).trim()
            try {
                val jamendoRes = jamendoApi.getTracks(
                    clientId = clientId,
                    limit = limit / 2,
                    offset = offset,
                    search = cleanQuery
                )
                val jamendoTracks = jamendoRes.results.mapNotNull { dto ->
                    mapJamendoDtoToTrack(dto, null, "")
                }
                if (jamendoTracks.isNotEmpty()) {
                    combinedTracks.addAll(jamendoTracks)
                    sourceLabel = "Jamendo API + Apple iTunes Catalog API"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Continue with iTunes API
            }
        }

        try {
            val hasCyrillic = cleanQuery.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
            val itunesRes = itunesApi.searchTracks(
                term = cleanQuery,
                limit = limit,
                offset = offset,
                country = if (hasCyrillic) "RU" else "US"
            )
            val itunesTracks = itunesRes.results.mapNotNull { dto ->
                val matchedCat = MusicFormGenresTaxonomy.findByTitleOrId(dto.primaryGenreName)
                mapItunesDtoToTrack(
                    dto = dto,
                    canonicalGenreRu = matchedCat?.titleRu ?: mapEnglishGenreToRussian(dto.primaryGenreName),
                    subgenreRu = dto.primaryGenreName,
                    primaryHex = matchedCat?.primaryColorHex ?: "#00E5FF",
                    secondaryHex = matchedCat?.secondaryColorHex ?: "#7C4DFF"
                )
            }
            combinedTracks.addAll(itunesTracks)

            buildPageFromTracks(
                tracks = combinedTracks.distinctBy { it.id },
                offset = offset,
                limit = limit,
                totalEstimate = if (combinedTracks.size >= limit / 2) 5000 else combinedTracks.size,
                sourceProvider = sourceLabel
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (combinedTracks.isNotEmpty()) {
                buildPageFromTracks(
                    tracks = combinedTracks,
                    offset = offset,
                    limit = limit,
                    totalEstimate = combinedTracks.size,
                    sourceProvider = sourceLabel
                )
            } else {
                PaginatedCatalogPage(
                    tracks = emptyList(),
                    pageOffset = offset,
                    pageSize = limit,
                    hasMore = false,
                    sourceProvider = sourceLabel,
                    errorRu = "Не удалось выполнить онлайн-поиск: ${e.localizedMessage ?: "проверьте подключение к сети"}"
                )
            }
        }
    }

    /**
     * Downloads a track to local storage ONLY if its license permits downloading (`track.isDownloadAllowed == true`).
     */
    suspend fun downloadLicensedTrackToStorage(
        track: Track,
        targetDir: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!track.isDownloadAllowed) {
                throw IllegalStateException(
                    "Лицензия поставщика (${track.sourceProvider}) разрешает только потоковое прослушивание этого трека без сохранения файла."
                )
            }
            val downloadUrl = track.downloadUrl ?: track.streamUrl
                ?: throw IllegalStateException("Ссылка для скачивания недоступна.")

            val downloadsFolder = File(targetDir, "music_form_downloads")
            if (!downloadsFolder.exists()) {
                downloadsFolder.mkdirs()
            }
            val safeId = track.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val destFile = File(downloadsFolder, "$safeId.mp3")
            if (destFile.exists() && destFile.length() > 1024L) {
                return@runCatching destFile
            }

            val req = Request.Builder().url(downloadUrl).build()
            okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Ошибка сервера при скачивании: HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Пустой ответ сервера")
                val tmpFile = File(downloadsFolder, "$safeId.tmp")
                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists()) destFile.delete()
                tmpFile.renameTo(destFile)
                destFile
            }
        }
    }

    internal fun mapJamendoDtoToTrack(
        dto: JamendoTrackDto,
        fallbackGenreRu: String?,
        subgenreRu: String
    ): Track? {
        if (dto.id.isBlank() || dto.name.isBlank() || dto.audio.isBlank()) return null
        val rawGenre = dto.musicinfo?.tags?.genres?.firstOrNull()
        val genreRu = fallbackGenreRu ?: mapEnglishGenreToRussian(rawGenre ?: "Electronic")
        val cat = MusicFormGenresTaxonomy.findByTitleOrId(genreRu)
        val year = dto.releaseDate.take(4).toIntOrNull() ?: 2025
        val hash = abs(dto.id.hashCode())
        val bpm = 85 + (hash % 85)
        val energy = ((bpm - 60).toFloat() / 120f).coerceIn(0.25f, 0.95f)
        val highResCover = dto.albumImage.ifBlank { dto.image }.takeIf { it.isNotBlank() }
        val moodList = (dto.musicinfo?.tags?.vartags.orEmpty() + listOfNotNull(rawGenre, genreRu))
            .map { it.lowercase() }
            .distinct()
            .take(6)

        return Track(
            id = "jam_${dto.id}",
            title = dto.name.trim(),
            artistId = "jam_art_${dto.artistId.ifBlank { dto.artistName.hashCode().toString() }}",
            artistName = dto.artistName.ifBlank { "Jamendo Artist" }.trim(),
            albumId = "jam_alb_${dto.albumId.ifBlank { dto.id }}",
            albumTitle = dto.albumName.ifBlank { "${dto.name} — Single" }.trim(),
            genre = genreRu,
            subgenre = subgenreRu.ifBlank { rawGenre ?: genreRu },
            durationMs = (dto.duration.coerceAtLeast(15) * 1000L),
            bpm = bpm,
            energy = energy,
            releaseYear = year,
            isNewRelease = year >= 2024,
            popularityScore = 80 + (hash % 19),
            coverColorPrimaryHex = cat?.primaryColorHex ?: "#00E5FF",
            coverColorSecondaryHex = cat?.secondaryColorHex ?: "#7C4DFF",
            moodTags = moodList,
            licenseInfo = if (dto.licenseCcUrl.isNotBlank()) {
                "Jamendo Creative Commons (${dto.licenseCcUrl})"
            } else {
                "Jamendo Creative Commons License (Full Stream)"
            },
            coverUrl = highResCover,
            streamUrl = dto.audio,
            downloadUrl = dto.audiodownload.takeIf { dto.audioDownloadAllowed && it.isNotBlank() },
            isDownloadAllowed = dto.audioDownloadAllowed && dto.audiodownload.isNotBlank(),
            isSynthesizedDemo = false,
            sourceProvider = "Jamendo Creative Commons API"
        )
    }

    internal fun mapItunesDtoToTrack(
        dto: ItunesTrackDto,
        canonicalGenreRu: String,
        subgenreRu: String,
        primaryHex: String,
        secondaryHex: String
    ): Track? {
        if (dto.trackId == 0L || dto.trackName.isBlank() || dto.previewUrl.isBlank()) return null
        val highResCover = dto.artworkUrl100
            .replace("100x100bb", "600x600bb")
            .takeIf { it.isNotBlank() }
        val year = dto.releaseDate.take(4).toIntOrNull() ?: 2025
        val hash = abs(dto.trackId.hashCode())
        val bpm = estimateBpmForGenre(canonicalGenreRu, hash)
        val energy = estimateEnergyForGenre(canonicalGenreRu, bpm)
        val cat = MusicFormGenresTaxonomy.findByTitleOrId(canonicalGenreRu)
        val tags = buildList {
            add(canonicalGenreRu.lowercase())
            if (subgenreRu.isNotBlank()) add(subgenreRu.lowercase())
            if (dto.primaryGenreName.isNotBlank()) add(dto.primaryGenreName.lowercase())
            cat?.moods?.forEach { add(it.lowercase()) }
        }.distinct()

        return Track(
            id = "itunes_${dto.trackId}",
            title = dto.trackName.trim(),
            artistId = "itunes_art_${if (dto.artistId != 0L) dto.artistId else abs(dto.artistName.hashCode()).toLong()}",
            artistName = dto.artistName.ifBlank { "Unknown Artist" }.trim(),
            albumId = "itunes_alb_${if (dto.collectionId != 0L) dto.collectionId else dto.trackId}",
            albumTitle = dto.collectionName.ifBlank { "${dto.trackName} — Single" }.trim(),
            genre = canonicalGenreRu,
            subgenre = subgenreRu.ifBlank { dto.primaryGenreName },
            durationMs = dto.trackTimeMillis.takeIf { it > 0L } ?: 30_000L,
            bpm = bpm,
            energy = energy,
            releaseYear = year,
            isNewRelease = year >= 2024,
            popularityScore = 78 + (hash % 21),
            coverColorPrimaryHex = primaryHex,
            coverColorSecondaryHex = secondaryHex,
            moodTags = tags,
            licenseInfo = "Apple iTunes Official Catalog API (Лицензионный AAC-поток, без сохранения файла)",
            coverUrl = highResCover,
            streamUrl = dto.previewUrl,
            downloadUrl = null,
            isDownloadAllowed = false,
            isSynthesizedDemo = false,
            sourceProvider = "Apple iTunes Official Catalog API"
        )
    }

    private fun buildPageFromTracks(
        tracks: List<Track>,
        offset: Int,
        limit: Int,
        totalEstimate: Int,
        sourceProvider: String
    ): PaginatedCatalogPage {
        val artists = tracks.groupBy { it.artistId }.map { (artistId, artistTracks) ->
            val first = artistTracks.first()
            Artist(
                id = artistId,
                name = first.artistName,
                primaryGenre = first.genre,
                bio = "Исполнитель жанра «${first.genre}» • Доступно треков в выборке: ${artistTracks.size}",
                monthlyListeners = 50_000 + (abs(artistId.hashCode()) % 900_000),
                accentHex = first.coverColorPrimaryHex,
                isEmergingArtist = (abs(artistId.hashCode()) % 3 == 0),
                avatarUrl = first.coverUrl,
                sourceProvider = sourceProvider
            )
        }
        val albums = tracks.groupBy { it.albumId }.map { (albumId, albumTracks) ->
            val first = albumTracks.first()
            Album(
                id = albumId,
                title = first.albumTitle,
                artistId = first.artistId,
                artistName = first.artistName,
                genre = first.genre,
                releaseYear = first.releaseYear,
                coverColorPrimaryHex = first.coverColorPrimaryHex,
                coverColorSecondaryHex = first.coverColorSecondaryHex,
                trackIds = albumTracks.map { it.id },
                description = "${first.genre} • ${first.artistName} (${first.releaseYear})",
                coverUrl = first.coverUrl,
                isSingle = albumTracks.size == 1 || first.albumTitle.contains("Single", ignoreCase = true),
                sourceProvider = sourceProvider
            )
        }
        return PaginatedCatalogPage(
            tracks = tracks,
            artists = artists,
            albums = albums,
            pageOffset = offset,
            pageSize = limit,
            hasMore = tracks.size >= (limit / 2).coerceAtLeast(5),
            totalAvailableEstimate = totalEstimate,
            sourceProvider = sourceProvider,
            errorRu = null
        )
    }

    internal fun mapEnglishGenreToRussian(rawGenre: String): String {
        val g = rawGenre.lowercase()
        return when {
            g.contains("k-pop") || g.contains("kpop") || g.contains("korean") -> "K-pop"
            g.contains("j-pop") || g.contains("jpop") -> "J-pop"
            g.contains("anime") -> "Аниме-саундтреки"
            g.contains("soundtrack") || g.contains("score") -> "Саундтреки из фильмов и игр"
            g.contains("drum") || g.contains("dnb") || g.contains("jungle") -> "Драм-н-бейс"
            g.contains("techno") || g.contains("house") || g.contains("dance") || g.contains("edm") || g.contains("trance") -> "EDM и техно"
            g.contains("electronic") || g.contains("synth") || g.contains("electronica") -> "Электронная музыка"
            g.contains("lo-fi") || g.contains("lofi") || g.contains("chillhop") -> "Лоу-фай"
            g.contains("ambient") -> "Эмбиент"
            g.contains("meditation") || g.contains("new age") -> "Медитативная музыка"
            g.contains("chill") || g.contains("lounge") || g.contains("downtempo") -> "Чилл и релакс"
            g.contains("hip-hop") || g.contains("hip hop") || g.contains("rap") -> "Хип-хоп и рэп"
            g.contains("r&b") || g.contains("soul") -> "R&B и соул"
            g.contains("metal") -> "Метал"
            g.contains("punk") -> "Панк"
            g.contains("indie") -> "Инди"
            g.contains("rock") || g.contains("alternative") -> "Рок и альтернативный рок"
            g.contains("jazz") -> "Джаз"
            g.contains("blues") -> "Блюз"
            g.contains("classical") || g.contains("orchestra") || g.contains("piano") -> "Классическая музыка"
            g.contains("funk") || g.contains("disco") -> "Фанк и диско"
            g.contains("reggae") || g.contains("dub") || g.contains("ska") -> "Регги"
            g.contains("country") || g.contains("americana") -> "Кантри"
            g.contains("folk") -> "Фолк"
            g.contains("acoustic") || g.contains("singer") -> "Акустическая музыка"
            g.contains("retro") || g.contains("80s") || g.contains("90s") || g.contains("oldies") -> "Ретро и музыка прошлых десятилетий"
            g.contains("russian") || g.contains("рус") -> "Русская музыка"
            else -> "Поп"
        }
    }

    private fun estimateBpmForGenre(genreRu: String, hash: Int): Int {
        return when (genreRu) {
            "Драм-н-бейс" -> 168 + (hash % 10)
            "EDM и техно" -> 124 + (hash % 16)
            "Электронная музыка" -> 115 + (hash % 20)
            "Лоу-фай" -> 76 + (hash % 18)
            "Эмбиент", "Медитативная музыка" -> 62 + (hash % 16)
            "Классическая музыка", "Чилл и релакс" -> 68 + (hash % 24)
            "Метал", "Панк" -> 138 + (hash % 32)
            "Хип-хоп и рэп", "R&B и соул" -> 86 + (hash % 28)
            else -> 98 + (hash % 34)
        }
    }

    private fun estimateEnergyForGenre(genreRu: String, bpm: Int): Float {
        return when (genreRu) {
            "Драм-н-бейс", "Метал", "Панк", "EDM и техно" -> 0.88f
            "Эмбиент", "Медитативная музыка", "Классическая музыка", "Чилл и релакс" -> 0.28f
            "Лоу-фай", "Акустическая музыка", "Джаз" -> 0.42f
            else -> ((bpm - 55).toFloat() / 125f).coerceIn(0.35f, 0.92f)
        }
    }
}
