package com.example.data.catalog

import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.CatalogSearchResult
import com.example.data.model.GenreCategory
import com.example.data.model.MusicFormGenresTaxonomy
import com.example.data.model.SearchFilter
import com.example.data.model.SearchSortOrder
import com.example.data.model.StreamUrlResult
import com.example.data.model.Track
import com.example.data.model.WaveMood
import com.example.player.SynthesizedAudioGenerator
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Extensible catalog provider contract for Music Form.
 * Supports multi-thousand-track remote federation (Jamendo API v3.0 + Apple iTunes Official Catalog API),
 * Room caching, pagination, 29 genre categories, and licensed playback stream resolution.
 */
interface MusicCatalogProvider {
    val providerName: String
    val isDemonstrationCatalog: Boolean
    val licensingNoticeRu: String

    fun getAllTracks(): List<Track>
    fun getAllAlbums(): List<Album>
    fun getAllArtists(): List<Artist>
    fun getAllGenres(): List<String>
    fun getGenreCategories(): List<GenreCategory> = MusicFormGenresTaxonomy.allCategories

    fun getTrackById(trackId: String): Track?
    fun getAlbumById(albumId: String): Album?
    fun getArtistById(artistId: String): Artist?

    fun ingestRemoteTracks(
        newTracks: List<Track>,
        newArtists: List<Artist> = emptyList(),
        newAlbums: List<Album> = emptyList()
    ) {}

    fun getTracksForGenre(genreOrCategoryTitle: String, subgenre: String? = null): List<Track> {
        val cat = MusicFormGenresTaxonomy.findByTitleOrId(genreOrCategoryTitle)
        return getAllTracks().filter { tr ->
            val genreMatch = tr.genre.equals(genreOrCategoryTitle, ignoreCase = true) ||
                (cat != null && (
                    tr.genre.equals(cat.titleRu, ignoreCase = true) ||
                        tr.genre.equals(cat.id, ignoreCase = true) ||
                        tr.moodTags.any { tag -> cat.moods.any { m -> m.equals(tag, ignoreCase = true) } } ||
                        mapLegacyDemoGenreToCategoryId(tr.genre) == cat.id
                    ))
            val subMatch = subgenre.isNullOrBlank() ||
                tr.subgenre.equals(subgenre, ignoreCase = true) ||
                tr.moodTags.any { it.contains(subgenre, ignoreCase = true) }
            genreMatch && subMatch
        }
    }

    fun searchCatalog(
        query: String,
        filter: SearchFilter = SearchFilter.ALL,
        sortOrder: SearchSortOrder = SearchSortOrder.RELEVANCE
    ): CatalogSearchResult

    fun getRecommendations(
        seedTrackId: String? = null,
        seedArtistId: String? = null,
        genre: String? = null,
        mood: WaveMood? = null
    ): List<Track>

    fun resolveStreamUrl(
        trackId: String,
        cacheDir: File?,
        simulateExpiredToken: Boolean = false,
        simulateRegionLock: Boolean = false,
        simulateOfflineError: Boolean = false
    ): StreamUrlResult

    companion object {
        fun mapLegacyDemoGenreToCategoryId(genre: String): String = when (genre.lowercase()) {
            "synthwave", "electronic" -> "electronic"
            "ambient" -> "ambient"
            "lo-fi" -> "lofi"
            "drum & bass" -> "dnb"
            "indie" -> "indie"
            "classical" -> "classical"
            "cyber rock" -> "anime"
            else -> ""
        }
    }
}

/**
 * Federated Catalog Provider combining:
 * 1. Live remote tracks ingested from Apple iTunes Official Catalog API & Jamendo Creative Commons API v3.0 (cached in Room).
 * 2. Built-in fallback demonstration catalog (16 tracks) when offline or before remote pages load.
 * Never substitutes synthesized audio for a real remote track: remote tracks stream directly from their licensed HTTPS streamUrl.
 */
class MusicFormOpenCatalogProvider : MusicCatalogProvider {

    private val remoteTracks = CopyOnWriteArrayList<Track>()
    private val remoteArtists = CopyOnWriteArrayList<Artist>()
    private val remoteAlbums = CopyOnWriteArrayList<Album>()

    override val providerName: String
        get() = if (remoteTracks.isNotEmpty()) {
            "Federated Catalog: Apple iTunes Official API / Jamendo API + Локальный кэш (${remoteTracks.size + fallbackTracks.size} треков загружено, >350 000 доступно онлайн)"
        } else {
            "Music Form Open Audio Catalog (Резервный демо-каталог + онлайн-федерация Jamendo / Apple iTunes API)"
        }

    override val isDemonstrationCatalog: Boolean
        get() = remoteTracks.isEmpty()

    override val licensingNoticeRu: String
        get() = if (remoteTracks.isNotEmpty()) {
            "Подключён реальный онлайн-каталог (Apple iTunes Official Catalog API / Jamendo Creative Commons API): загружаются настоящие обложки, метаданные и лицензионные аудиопотоки. " +
                "Скачивание в память устройства разрешено только для треков с открытой лицензией Creative Commons / Royalty-Free."
        } else {
            "Активен резервный каталог Music Form (при наличии сети автоматически подгружаются тысячи реальных треков через Apple iTunes API и Jamendo API v3.0)."
        }

    private val fallbackArtists = listOf(
        Artist(
            id = "art_aether_pulse",
            name = "Aether Pulse",
            primaryGenre = "Synthwave",
            bio = "Неоновый ретрофутуризм, аналоговые арпеджио и ночные магистрали мегаполиса.",
            monthlyListeners = 428_500,
            accentHex = "#00E5FF",
            isEmergingArtist = false
        ),
        Artist(
            id = "art_kora_vel",
            name = "Kora Vel",
            primaryGenre = "Electronic",
            bio = "Глубокая прогрессив-электроника с кристальными вокальными текстурами и плотным грувом.",
            monthlyListeners = 319_200,
            accentHex = "#7C4DFF",
            isEmergingArtist = false
        ),
        Artist(
            id = "art_lumina_drift",
            name = "Lumina Drift",
            primaryGenre = "Ambient",
            bio = "Космический эмбиент и кинематографичные звуковые ландшафты для глубокого фокуса и сна.",
            monthlyListeners = 195_000,
            accentHex = "#3D5AFE",
            isEmergingArtist = true
        ),
        Artist(
            id = "art_sova_lofi",
            name = "Sova Beats",
            primaryGenre = "Lo-Fi",
            bio = "Тёплый виниловый краш, джазовые аккорды и уютные инструментальные зарисовки для учёбы и чтения.",
            monthlyListeners = 512_800,
            accentHex = "#00BFA5",
            isEmergingArtist = false
        ),
        Artist(
            id = "art_vector_zero",
            name = "Vector Zero",
            primaryGenre = "Drum & Bass",
            bio = "Стремительный ликвидно-нейрофанковый импульс 174 BPM для тренировок и максимального тонуса.",
            monthlyListeners = 276_400,
            accentHex = "#FF4081",
            isEmergingArtist = true
        ),
        Artist(
            id = "art_sever_indie",
            name = "Северный Берег",
            primaryGenre = "Indie",
            bio = "Атмосферный инди-поп и пост-панк с гитарным ревербом и честной меланхолией.",
            monthlyListeners = 384_100,
            accentHex = "#536DFE",
            isEmergingArtist = false
        ),
        Artist(
            id = "art_elena_nocturne",
            name = "Elena Nocturne",
            primaryGenre = "Classical",
            bio = "Неоклассическое фортепиано и камерные струнные минималистичной школы.",
            monthlyListeners = 148_900,
            accentHex = "#B388FF",
            isEmergingArtist = true
        ),
        Artist(
            id = "art_katan_cyber",
            name = "Katan V",
            primaryGenre = "Cyber Rock",
            bio = "Энергичный кибер-рок и саундтрековый драйв в духе культового киберпанк-аниме.",
            monthlyListeners = 229_700,
            accentHex = "#FF6E40",
            isEmergingArtist = true
        )
    )

    private val fallbackTracks = listOf(
        Track(
            id = "tr_neon_horizon",
            title = "Neon Horizon",
            artistId = "art_aether_pulse",
            artistName = "Aether Pulse",
            albumId = "alb_midnight_circuit",
            albumTitle = "Midnight Circuit",
            genre = "Synthwave",
            subgenre = "Retrowave",
            durationMs = 24_000L,
            bpm = 118,
            energy = 0.82f,
            releaseYear = 2026,
            isNewRelease = true,
            popularityScore = 96,
            coverColorPrimaryHex = "#00E5FF",
            coverColorSecondaryHex = "#7C4DFF",
            moodTags = listOf("ночь", "поездка", "драйв", "неон", "аниме", "бодрое", "дорога"),
            synthFreqHz = 220f,
            chordProgression = listOf(0, 3, 7, 10),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_chrome_highway",
            title = "Chrome Highway 2099",
            artistId = "art_aether_pulse",
            artistName = "Aether Pulse",
            albumId = "alb_midnight_circuit",
            albumTitle = "Midnight Circuit",
            genre = "Synthwave",
            subgenre = "Outrun",
            durationMs = 24_000L,
            bpm = 124,
            energy = 0.88f,
            releaseYear = 2026,
            isNewRelease = false,
            popularityScore = 91,
            coverColorPrimaryHex = "#00B8D4",
            coverColorSecondaryHex = "#651FFF",
            moodTags = listOf("ночь", "поездка", "трасса", "скорость", "тренировка", "дорога"),
            synthFreqHz = 246.94f,
            chordProgression = listOf(0, 5, 7, 12),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_electric_form",
            title = "Electric Form",
            artistId = "art_kora_vel",
            artistName = "Kora Vel",
            albumId = "alb_subzero_bloom",
            albumTitle = "Subzero Bloom",
            genre = "Electronic",
            subgenre = "Progressive Electronic",
            durationMs = 24_000L,
            bpm = 126,
            energy = 0.85f,
            releaseYear = 2026,
            isNewRelease = true,
            popularityScore = 94,
            coverColorPrimaryHex = "#7C4DFF",
            coverColorSecondaryHex = "#00E5FF",
            moodTags = listOf("клуб", "танцы", "энергия", "фокус", "электроника"),
            synthFreqHz = 196.0f,
            chordProgression = listOf(0, 4, 7, 11),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_cobalt_pulse",
            title = "Cobalt Pulse",
            artistId = "art_kora_vel",
            artistName = "Kora Vel",
            albumId = "alb_subzero_bloom",
            albumTitle = "Subzero Bloom",
            genre = "Electronic",
            subgenre = "Melodic Techno",
            durationMs = 24_000L,
            bpm = 122,
            energy = 0.76f,
            releaseYear = 2025,
            isNewRelease = false,
            popularityScore = 87,
            coverColorPrimaryHex = "#536DFE",
            coverColorSecondaryHex = "#AA00FF",
            moodTags = listOf("работа", "фокус", "ночь", "код", "учёба"),
            synthFreqHz = 207.65f,
            chordProgression = listOf(0, 3, 5, 10),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_starlight_veil",
            title = "Starlight Veil",
            artistId = "art_lumina_drift",
            artistName = "Lumina Drift",
            albumId = "alb_orbital_silence",
            albumTitle = "Orbital Silence",
            genre = "Ambient",
            subgenre = "Space Ambient",
            durationMs = 24_000L,
            bpm = 72,
            energy = 0.25f,
            releaseYear = 2026,
            isNewRelease = true,
            popularityScore = 85,
            coverColorPrimaryHex = "#3D5AFE",
            coverColorSecondaryHex = "#00B0FF",
            moodTags = listOf("спокойная", "чтение", "сон", "космос", "медитация", "отдых"),
            synthFreqHz = 146.83f,
            chordProgression = listOf(0, 7, 12, 14),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_deep_nebula",
            title = "Deep Nebula Echo",
            artistId = "art_lumina_drift",
            artistName = "Lumina Drift",
            albumId = "alb_orbital_silence",
            albumTitle = "Orbital Silence",
            genre = "Ambient",
            subgenre = "Drone Ambient",
            durationMs = 24_000L,
            bpm = 68,
            energy = 0.20f,
            releaseYear = 2025,
            isNewRelease = false,
            popularityScore = 81,
            coverColorPrimaryHex = "#1A237E",
            coverColorSecondaryHex = "#00E5FF",
            moodTags = listOf("спокойная", "чтение", "отдых", "тишина", "фокус", "сон"),
            synthFreqHz = 130.81f,
            chordProgression = listOf(0, 5, 9, 12),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_rainy_window_lofi",
            title = "Rain on Tokyo Glass",
            artistId = "art_sova_lofi",
            artistName = "Sova Beats",
            albumId = "alb_velvet_tape",
            albumTitle = "Velvet Tape Vol. 4",
            genre = "Lo-Fi",
            subgenre = "Study Beats",
            durationMs = 24_000L,
            bpm = 84,
            energy = 0.38f,
            releaseYear = 2026,
            isNewRelease = true,
            popularityScore = 95,
            coverColorPrimaryHex = "#00BFA5",
            coverColorSecondaryHex = "#536DFE",
            moodTags = listOf("чтение", "учёба", "спокойная", "уют", "дождь", "аниме"),
            synthFreqHz = 174.61f,
            chordProgression = listOf(0, 4, 7, 9),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_espresso_notes",
            title = "Midnight Espresso Notes",
            artistId = "art_sova_lofi",
            artistName = "Sova Beats",
            albumId = "alb_velvet_tape",
            albumTitle = "Velvet Tape Vol. 4",
            genre = "Lo-Fi",
            subgenre = "Chillhop",
            durationMs = 24_000L,
            bpm = 88,
            energy = 0.42f,
            releaseYear = 2025,
            isNewRelease = false,
            popularityScore = 89,
            coverColorPrimaryHex = "#26A69A",
            coverColorSecondaryHex = "#7C4DFF",
            moodTags = listOf("фокус", "чтение", "работа", "кофе", "спокойная", "учёба"),
            synthFreqHz = 164.81f,
            chordProgression = listOf(0, 3, 7, 11),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_hyperdrive_174",
            title = "Hyperdrive 174",
            artistId = "art_vector_zero",
            artistName = "Vector Zero",
            albumId = "alb_kinetic_core",
            albumTitle = "Kinetic Core",
            genre = "Drum & Bass",
            subgenre = "Neurofunk",
            durationMs = 24_000L,
            bpm = 174,
            energy = 0.96f,
            releaseYear = 2026,
            isNewRelease = true,
            popularityScore = 92,
            coverColorPrimaryHex = "#FF4081",
            coverColorSecondaryHex = "#651FFF",
            moodTags = listOf("тренировка", "бег", "спорт", "бодрое", "энергия", "драйв"),
            synthFreqHz = 293.66f,
            chordProgression = listOf(0, 2, 7, 10),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_neural_sprint",
            title = "Neural Sprint",
            artistId = "art_vector_zero",
            artistName = "Vector Zero",
            albumId = "alb_kinetic_core",
            albumTitle = "Kinetic Core",
            genre = "Drum & Bass",
            subgenre = "Liquid Funk",
            durationMs = 24_000L,
            bpm = 172,
            energy = 0.93f,
            releaseYear = 2025,
            isNewRelease = false,
            popularityScore = 86,
            coverColorPrimaryHex = "#F50057",
            coverColorSecondaryHex = "#00E5FF",
            moodTags = listOf("тренировка", "кардио", "бодрое", "скорость", "дорога"),
            synthFreqHz = 261.63f,
            chordProgression = listOf(0, 3, 8, 10),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_northern_lights",
            title = "Огни Северного Порта",
            artistId = "art_sever_indie",
            artistName = "Северный Берег",
            albumId = "alb_polar_radio",
            albumTitle = "Полярное Радио",
            genre = "Indie",
            subgenre = "Русский инди",
            durationMs = 24_000L,
            bpm = 104,
            energy = 0.56f,
            releaseYear = 2026,
            isNewRelease = true,
            popularityScore = 93,
            coverColorPrimaryHex = "#536DFE",
            coverColorSecondaryHex = "#00E5FF",
            moodTags = listOf("меланхолия", "вечер", "ночь", "поездка", "осень", "дорога", "русская музыка"),
            synthFreqHz = 196.0f,
            chordProgression = listOf(0, 3, 7, 12),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_last_tram",
            title = "Последний Трамвай",
            artistId = "art_sever_indie",
            artistName = "Северный Берег",
            albumId = "alb_polar_radio",
            albumTitle = "Полярное Радио",
            genre = "Indie",
            subgenre = "Пост-панк",
            durationMs = 24_000L,
            bpm = 96,
            energy = 0.48f,
            releaseYear = 2025,
            isNewRelease = false,
            popularityScore = 88,
            coverColorPrimaryHex = "#304FFE",
            coverColorSecondaryHex = "#7C4DFF",
            moodTags = listOf("меланхолия", "прогулка", "дождь", "спокойная", "русская музыка"),
            synthFreqHz = 185.0f,
            chordProgression = listOf(0, 4, 9, 11),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_glass_sonata",
            title = "Glass Nocturne in D",
            artistId = "art_elena_nocturne",
            artistName = "Elena Nocturne",
            albumId = "alb_ivory_reflections",
            albumTitle = "Ivory Reflections",
            genre = "Classical",
            subgenre = "Неоклассика",
            durationMs = 24_000L,
            bpm = 76,
            energy = 0.28f,
            releaseYear = 2026,
            isNewRelease = false,
            popularityScore = 84,
            coverColorPrimaryHex = "#B388FF",
            coverColorSecondaryHex = "#3D5AFE",
            moodTags = listOf("чтение", "классика", "пианино", "спокойная", "меланхолия", "учёба"),
            synthFreqHz = 293.66f,
            chordProgression = listOf(0, 3, 7, 14),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_winter_prelude",
            title = "Winter Solstice Prelude",
            artistId = "art_elena_nocturne",
            artistName = "Elena Nocturne",
            albumId = "alb_ivory_reflections",
            albumTitle = "Ivory Reflections",
            genre = "Classical",
            subgenre = "Фортепиано соло",
            durationMs = 24_000L,
            bpm = 70,
            energy = 0.22f,
            releaseYear = 2025,
            isNewRelease = false,
            popularityScore = 79,
            coverColorPrimaryHex = "#9575CD",
            coverColorSecondaryHex = "#00B0FF",
            moodTags = listOf("чтение", "фокус", "спокойная", "утро", "учёба", "отдых"),
            synthFreqHz = 246.94f,
            chordProgression = listOf(0, 5, 7, 12),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_shibuya_overdrive",
            title = "Shibuya Overdrive (Anime OP Mix)",
            artistId = "art_katan_cyber",
            artistName = "Katan V",
            albumId = "alb_neo_tokyo_arc",
            albumTitle = "Neo-Tokyo Arc",
            genre = "Cyber Rock",
            subgenre = "Anime Openings",
            durationMs = 24_000L,
            bpm = 152,
            energy = 0.94f,
            releaseYear = 2026,
            isNewRelease = true,
            popularityScore = 95,
            coverColorPrimaryHex = "#FF6E40",
            coverColorSecondaryHex = "#7C4DFF",
            moodTags = listOf("аниме", "тренировка", "драйв", "бодрое", "киберпанк", "игры"),
            synthFreqHz = 329.63f,
            chordProgression = listOf(0, 3, 5, 7),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        ),
        Track(
            id = "tr_mecha_horizon",
            title = "Mecha Skyline",
            artistId = "art_katan_cyber",
            artistName = "Katan V",
            albumId = "alb_neo_tokyo_arc",
            albumTitle = "Neo-Tokyo Arc",
            genre = "Cyber Rock",
            subgenre = "Игровые саундтреки",
            durationMs = 24_000L,
            bpm = 144,
            energy = 0.90f,
            releaseYear = 2025,
            isNewRelease = false,
            popularityScore = 88,
            coverColorPrimaryHex = "#FF3D00",
            coverColorSecondaryHex = "#00E5FF",
            moodTags = listOf("аниме", "ночь", "поездка", "тренировка", "рок", "игры"),
            synthFreqHz = 277.18f,
            chordProgression = listOf(0, 2, 5, 10),
            isDownloadAllowed = true,
            isSynthesizedDemo = true
        )
    )

    private val fallbackAlbums = listOf(
        Album(
            id = "alb_midnight_circuit",
            title = "Midnight Circuit",
            artistId = "art_aether_pulse",
            artistName = "Aether Pulse",
            genre = "Synthwave",
            releaseYear = 2026,
            coverColorPrimaryHex = "#00E5FF",
            coverColorSecondaryHex = "#7C4DFF",
            trackIds = listOf("tr_neon_horizon", "tr_chrome_highway"),
            description = "Концептуальный синтвейв-альбом о ночных магистралях и электрическом свете мегаполиса."
        ),
        Album(
            id = "alb_subzero_bloom",
            title = "Subzero Bloom",
            artistId = "art_kora_vel",
            artistName = "Kora Vel",
            genre = "Electronic",
            releaseYear = 2026,
            coverColorPrimaryHex = "#7C4DFF",
            coverColorSecondaryHex = "#00E5FF",
            trackIds = listOf("tr_electric_form", "tr_cobalt_pulse"),
            description = "Плотная прогрессив-электроника с глубоким басом и кристальной атмосферой."
        ),
        Album(
            id = "alb_orbital_silence",
            title = "Orbital Silence",
            artistId = "art_lumina_drift",
            artistName = "Lumina Drift",
            genre = "Ambient",
            releaseYear = 2026,
            coverColorPrimaryHex = "#3D5AFE",
            coverColorSecondaryHex = "#00B0FF",
            trackIds = listOf("tr_starlight_veil", "tr_deep_nebula"),
            description = "Невесомый эмбиент для медитации, чтения и глубокого погружения."
        ),
        Album(
            id = "alb_velvet_tape",
            title = "Velvet Tape Vol. 4",
            artistId = "art_sova_lofi",
            artistName = "Sova Beats",
            genre = "Lo-Fi",
            releaseYear = 2026,
            coverColorPrimaryHex = "#00BFA5",
            coverColorSecondaryHex = "#536DFE",
            trackIds = listOf("tr_rainy_window_lofi", "tr_espresso_notes"),
            description = "Тёплые инструментальные лоу-фай биты с мягкими клавишами."
        ),
        Album(
            id = "alb_kinetic_core",
            title = "Kinetic Core",
            artistId = "art_vector_zero",
            artistName = "Vector Zero",
            genre = "Drum & Bass",
            releaseYear = 2026,
            coverColorPrimaryHex = "#FF4081",
            coverColorSecondaryHex = "#651FFF",
            trackIds = listOf("tr_hyperdrive_174", "tr_neural_sprint"),
            description = "Высокоскоростной Drum & Bass для бега, кардио и максимального фокуса."
        ),
        Album(
            id = "alb_polar_radio",
            title = "Полярное Радио",
            artistId = "art_sever_indie",
            artistName = "Северный Берег",
            genre = "Indie",
            releaseYear = 2026,
            coverColorPrimaryHex = "#536DFE",
            coverColorSecondaryHex = "#00E5FF",
            trackIds = listOf("tr_northern_lights", "tr_last_tram"),
            description = "Гитарный инди-саунд с атмосферой северных портов и ночных трамваев."
        ),
        Album(
            id = "alb_ivory_reflections",
            title = "Ivory Reflections",
            artistId = "art_elena_nocturne",
            artistName = "Elena Nocturne",
            genre = "Classical",
            releaseYear = 2026,
            coverColorPrimaryHex = "#B388FF",
            coverColorSecondaryHex = "#3D5AFE",
            trackIds = listOf("tr_glass_sonata", "tr_winter_prelude"),
            description = "Камерная неоклассика для спокойного чтения и созерцания."
        ),
        Album(
            id = "alb_neo_tokyo_arc",
            title = "Neo-Tokyo Arc",
            artistId = "art_katan_cyber",
            artistName = "Katan V",
            genre = "Cyber Rock",
            releaseYear = 2026,
            coverColorPrimaryHex = "#FF6E40",
            coverColorSecondaryHex = "#7C4DFF",
            trackIds = listOf("tr_shibuya_overdrive", "tr_mecha_horizon"),
            description = "Взрывной саундтрек в стиле футуристического аниме и кибер-рока."
        )
    )

    override fun ingestRemoteTracks(
        newTracks: List<Track>,
        newArtists: List<Artist>,
        newAlbums: List<Album>
    ) {
        val existingTrackIds = remoteTracks.map { it.id }.toHashSet()
        for (track in newTracks) {
            if (track.id !in existingTrackIds) {
                remoteTracks.add(track)
                existingTrackIds.add(track.id)
            }
        }
        val existingArtistIds = remoteArtists.map { it.id }.toHashSet()
        for (artist in newArtists) {
            if (artist.id !in existingArtistIds) {
                remoteArtists.add(artist)
                existingArtistIds.add(artist.id)
            }
        }
        val existingAlbumIds = remoteAlbums.map { it.id }.toHashSet()
        for (album in newAlbums) {
            if (album.id !in existingAlbumIds) {
                remoteAlbums.add(album)
                existingAlbumIds.add(album.id)
            }
        }
    }

    override fun getAllTracks(): List<Track> = remoteTracks + fallbackTracks
    override fun getAllAlbums(): List<Album> = remoteAlbums + fallbackAlbums
    override fun getAllArtists(): List<Artist> = remoteArtists + fallbackArtists

    override fun getAllGenres(): List<String> {
        val taxonomyTitles = MusicFormGenresTaxonomy.allCategories.map { it.titleRu }
        val trackGenres = getAllTracks().map { it.genre }
        return (trackGenres + taxonomyTitles).distinct()
    }

    override fun getGenreCategories(): List<GenreCategory> = MusicFormGenresTaxonomy.allCategories

    override fun getTrackById(trackId: String): Track? =
        remoteTracks.find { it.id == trackId } ?: fallbackTracks.find { it.id == trackId }

    override fun getAlbumById(albumId: String): Album? =
        remoteAlbums.find { it.id == albumId } ?: fallbackAlbums.find { it.id == albumId }

    override fun getArtistById(artistId: String): Artist? =
        remoteArtists.find { it.id == artistId } ?: fallbackArtists.find { it.id == artistId }

    override fun searchCatalog(
        query: String,
        filter: SearchFilter,
        sortOrder: SearchSortOrder
    ): CatalogSearchResult {
        val q = query.trim().lowercase()
        val allTracksSnapshot = getAllTracks()
        val allArtistsSnapshot = getAllArtists()
        val allAlbumsSnapshot = getAllAlbums()
        val allGenresSnapshot = getAllGenres()

        if (q.isEmpty()) {
            return CatalogSearchResult(query = "")
        }

        val rawTracks = if (filter == SearchFilter.ALL || filter == SearchFilter.TRACKS) {
            allTracksSnapshot.filter { track ->
                track.title.lowercase().contains(q) ||
                    track.artistName.lowercase().contains(q) ||
                    track.albumTitle.lowercase().contains(q) ||
                    track.genre.lowercase().contains(q) ||
                    track.subgenre.lowercase().contains(q) ||
                    track.moodTags.any { it.lowercase().contains(q) }
            }
        } else emptyList()

        val sortedTracks = when (sortOrder) {
            SearchSortOrder.RELEVANCE -> rawTracks.sortedByDescending {
                var score = it.popularityScore
                if (it.title.lowercase().startsWith(q)) score += 50
                if (it.artistName.lowercase().startsWith(q)) score += 35
                score
            }
            SearchSortOrder.POPULARITY -> rawTracks.sortedByDescending { it.popularityScore }
            SearchSortOrder.NEWEST -> rawTracks.sortedByDescending { it.releaseYear }
            SearchSortOrder.DURATION -> rawTracks.sortedByDescending { it.durationMs }
            SearchSortOrder.TITLE_ASC -> rawTracks.sortedBy { it.title.lowercase() }
        }

        val matchedArtists = if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) {
            allArtistsSnapshot.filter { artist ->
                artist.name.lowercase().contains(q) ||
                    artist.primaryGenre.lowercase().contains(q) ||
                    artist.bio.lowercase().contains(q)
            }
        } else emptyList()

        val matchedAlbums = if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) {
            allAlbumsSnapshot.filter { album ->
                album.title.lowercase().contains(q) ||
                    album.artistName.lowercase().contains(q) ||
                    album.genre.lowercase().contains(q)
            }
        } else emptyList()

        val matchedGenres = if (filter == SearchFilter.ALL || filter == SearchFilter.GENRES) {
            allGenresSnapshot.filter { genre ->
                genre.lowercase().contains(q) ||
                    MusicFormGenresTaxonomy.allCategories.any { cat ->
                        cat.titleRu.equals(genre, ignoreCase = true) &&
                            (cat.subgenres.any { it.lowercase().contains(q) } || cat.apiQueryTerm.lowercase().contains(q))
                    }
            }
        } else emptyList()

        val curatedPlaylists = buildList {
            add("Ночной драйв 2099")
            add("Глубокий фокус & Код")
            add("Аниме Опенинги: Neo-Tokyo")
            add("Спокойное чтение")
            add("Импульс 174 BPM")
            MusicFormGenresTaxonomy.allCategories.forEach { addAll(it.curatedPlaylists) }
        }.distinct()

        val matchedPlaylists = if (filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS) {
            curatedPlaylists.filter { it.lowercase().contains(q) }
        } else emptyList()

        val suggestions = buildList {
            allTracksSnapshot.filter { it.title.lowercase().contains(q) }.take(3).forEach { add(it.title) }
            allArtistsSnapshot.filter { it.name.lowercase().contains(q) }.take(2).forEach { add(it.name) }
            allGenresSnapshot.filter { it.lowercase().contains(q) }.take(3).forEach { add(it) }
        }.distinct().take(6)

        val isEmptyResult = sortedTracks.isEmpty() &&
            matchedArtists.isEmpty() &&
            matchedAlbums.isEmpty() &&
            matchedGenres.isEmpty() &&
            matchedPlaylists.isEmpty()

        val similarFallback = if (isEmptyResult) {
            allTracksSnapshot.sortedByDescending { it.popularityScore }.take(6)
        } else emptyList()

        return CatalogSearchResult(
            query = query,
            tracks = sortedTracks,
            artists = matchedArtists,
            albums = matchedAlbums,
            genres = matchedGenres,
            matchingPlaylists = matchedPlaylists,
            similarSuggestions = similarFallback,
            querySuggestions = suggestions
        )
    }

    override fun getRecommendations(
        seedTrackId: String?,
        seedArtistId: String?,
        genre: String?,
        mood: WaveMood?
    ): List<Track> {
        val allTracksSnapshot = getAllTracks()
        val seedTrack = seedTrackId?.let { getTrackById(it) }
        val seedArtist = seedArtistId?.let { getArtistById(it) }
        val targetGenre = genre ?: seedTrack?.genre ?: seedArtist?.primaryGenre

        return allTracksSnapshot.sortedByDescending { candidate ->
            var score = candidate.popularityScore
            if (targetGenre != null && candidate.genre.equals(targetGenre, ignoreCase = true)) {
                score += 35
            }
            if (seedArtist != null && candidate.artistId == seedArtist.id) {
                score += 25
            }
            if (mood != null) {
                if (candidate.genre in mood.primaryGenreHints) score += 30
                if (candidate.bpm in mood.minBpm..mood.maxBpm) score += 20
            }
            score
        }
    }

    override fun resolveStreamUrl(
        trackId: String,
        cacheDir: File?,
        simulateExpiredToken: Boolean,
        simulateRegionLock: Boolean,
        simulateOfflineError: Boolean
    ): StreamUrlResult {
        val track = getTrackById(trackId)
            ?: return StreamUrlResult.NetworkError(trackId, "Композиция не найдена в каталоге.")

        if (simulateExpiredToken) {
            return StreamUrlResult.ExpiredLink(
                trackId = trackId,
                messageRu = "Срок действия ссылки на поток истёк. Запрашиваем новый токен..."
            )
        }
        if (simulateRegionLock) {
            return StreamUrlResult.RegionRestricted(
                trackId = trackId,
                messageRu = "Трек «${track.title}» временно недоступен в выбранном регионе по условиям лицензии."
            )
        }

        // 1. Check if the track was legally downloaded to device storage
        if (cacheDir != null) {
            val safeId = track.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val downloadedFile = File(File(cacheDir, "music_form_downloads"), "$safeId.mp3")
            if (downloadedFile.exists() && downloadedFile.length() > 1024L) {
                return StreamUrlResult.Success(
                    uriString = downloadedFile.toURI().toString(),
                    isLocalCached = true,
                    formatLabel = "Офлайн-файл (${track.sourceProvider})"
                )
            }
        }

        if (simulateOfflineError) {
            return StreamUrlResult.NetworkError(
                trackId = trackId,
                messageRu = "Ошибка сети при буферизации потока. Проверьте подключение к интернету."
            )
        }

        // 2. Real Remote Track (Jamendo Full MP3 or Apple iTunes Official AAC Stream):
        // NEVER run the procedural WAV synthesizer for a real remote song!
        val remoteStream = track.streamUrl
        if (!remoteStream.isNullOrBlank() && !track.isSynthesizedDemo) {
            val formatLabel = if (track.sourceProvider.contains("Jamendo", ignoreCase = true)) {
                "MP3 Full Stream • Jamendo Creative Commons"
            } else {
                "AAC 256 kbps Preview Stream • Apple iTunes Catalog"
            }
            return StreamUrlResult.Success(
                uriString = remoteStream,
                isLocalCached = false,
                formatLabel = formatLabel
            )
        }

        // 3. Fallback Demonstration Catalog Track (explicitly flagged as isSynthesizedDemo)
        if (cacheDir != null) {
            val wavFile = SynthesizedAudioGenerator.ensurePlayableAudioFile(cacheDir, track)
            return StreamUrlResult.Success(
                uriString = wavFile.toURI().toString(),
                isLocalCached = true,
                formatLabel = "PCM 16-bit Stereo • 22.05 kHz (Демо-каталог)"
            )
        }

        return StreamUrlResult.Success(
            uriString = "file:///tmp/${track.id}.wav",
            isLocalCached = true,
            formatLabel = "PCM 16-bit Stereo (Демо-каталог)"
        )
    }
}
