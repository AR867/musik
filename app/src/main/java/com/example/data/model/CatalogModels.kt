package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class WaveMood(
    val titleRu: String,
    val subtitleRu: String,
    val primaryGenreHints: List<String>,
    val minBpm: Int,
    val maxBpm: Int
) {
    ENERGETIC(
        "Бодрое",
        "Максимум драйва и ритма",
        listOf("Поп", "Электронная музыка", "EDM и техно", "Драм-н-бейс", "Synthwave", "Electronic", "Drum & Bass", "Cyber Rock"),
        120,
        180
    ),
    CALM(
        "Спокойное",
        "Мягкое звучание для отдыха",
        listOf("Эмбиент", "Чилл и релакс", "Лоу-фай", "Классическая музыка", "Акустическая музыка", "Ambient", "Lo-Fi", "Classical", "Acoustic"),
        60,
        105
    ),
    FOCUS(
        "Фокус",
        "Глубокая концентрация без отвлечений",
        listOf("Лоу-фай", "Эмбиент", "Электронная музыка", "Классическая музыка", "Медитативная музыка", "Lo-Fi", "Ambient", "Electronic", "Classical"),
        70,
        125
    ),
    MELANCHOLY(
        "Меланхолия",
        "Атмосферная лирика и глубина",
        listOf("Инди", "Акустическая музыка", "Классическая музыка", "Блюз", "Фолк", "Indie", "Acoustic", "Classical", "Ambient"),
        65,
        110
    ),
    NIGHT_DRIVE(
        "Ночной драйв",
        "Неоновые трассы и синтвейв",
        listOf("Электронная музыка", "EDM и техно", "Ретро и музыка прошлых десятилетий", "Synthwave", "Electronic", "Cyber Rock"),
        100,
        145
    ),
    WORKOUT(
        "Тренировка",
        "Высокий темп и мощный бас",
        listOf("Драм-н-бейс", "EDM и техно", "Хип-хоп и рэп", "Метал", "Рок и альтернативный рок", "Drum & Bass", "Cyber Rock", "Electronic", "Synthwave"),
        125,
        180
    )
}

@Serializable
enum class SubscriptionTier(val titleRu: String, val maxBitrateKbps: Int, val badgeText: String) {
    FREE("Music Form Free", 192, "FREE"),
    PREMIUM("Music Form Premium (Архитектура)", 320, "HI-FI READY")
}

@Serializable
enum class AppThemeMode(val titleRu: String) {
    DARK("Тёмная (Премиум)"),
    LIGHT("Светлая"),
    SYSTEM("Системная")
}

@Serializable
enum class SearchFilter(val titleRu: String) {
    ALL("Все"),
    TRACKS("Треки"),
    ARTISTS("Исполнители"),
    ALBUMS("Альбомы"),
    GENRES("Жанры"),
    PLAYLISTS("Плейлисты")
}

@Serializable
enum class SearchSortOrder(val titleRu: String) {
    RELEVANCE("По релевантности"),
    POPULARITY("По популярности"),
    NEWEST("По новизне"),
    DURATION("По длительности"),
    TITLE_ASC("По алфавиту")
}

@Serializable
enum class RepeatModeState {
    OFF,
    ALL,
    ONE
}

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val albumId: String,
    val albumTitle: String,
    val genre: String,
    val durationMs: Long,
    val bpm: Int,
    val energy: Float, // 0.0f..1.0f
    val releaseYear: Int,
    val isNewRelease: Boolean = false,
    val popularityScore: Int = 80, // 0..100
    val coverColorPrimaryHex: String = "#00E5FF",
    val coverColorSecondaryHex: String = "#7C4DFF",
    val moodTags: List<String> = emptyList(),
    val synthFreqHz: Float = 220f,
    val chordProgression: List<Int> = listOf(0, 3, 7, 10),
    val licenseInfo: String = "Music Form Open Audio Engine (Royalty-Free / Procedural Synthesis)",
    // Extended fields for real multi-thousand-track catalog & licensed streaming
    val coverUrl: String? = null,
    val streamUrl: String? = null,
    val downloadUrl: String? = null,
    val isDownloadAllowed: Boolean = false,
    val isSynthesizedDemo: Boolean = false,
    val sourceProvider: String = "Music Form Open Audio",
    val subgenre: String = ""
)

@Serializable
data class Album(
    val id: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val genre: String,
    val releaseYear: Int,
    val coverColorPrimaryHex: String,
    val coverColorSecondaryHex: String,
    val trackIds: List<String>,
    val description: String,
    val coverUrl: String? = null,
    val isSingle: Boolean = false,
    val sourceProvider: String = "Music Form Open Audio"
)

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val primaryGenre: String,
    val bio: String,
    val monthlyListeners: Int,
    val accentHex: String,
    val isEmergingArtist: Boolean = false,
    val avatarUrl: String? = null,
    val sourceProvider: String = "Music Form Open Audio"
)

@Serializable
data class GenreCategory(
    val id: String,
    val titleRu: String,
    val subtitleRu: String,
    val primaryColorHex: String,
    val secondaryColorHex: String,
    val apiQueryTerm: String,
    val jamendoTag: String,
    val subgenres: List<String>,
    val moods: List<String>,
    val curatedPlaylists: List<String>,
    val catalogTrackEstimate: Int = 2500
)

@Serializable
data class PaginatedCatalogPage(
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val pageOffset: Int = 0,
    val pageSize: Int = 25,
    val hasMore: Boolean = false,
    val totalAvailableEstimate: Int = 0,
    val sourceProvider: String = "",
    val errorRu: String? = null
)

@Serializable
data class RecommendationItem(
    val track: Track,
    val matchScorePercent: Int,
    val reasonRu: String,
    val isNewArtistDiscovery: Boolean = false
)

@Serializable
data class WaveSettings(
    val seedTrackId: String? = null,
    val seedArtistId: String? = null,
    val seedGenre: String? = null,
    val mood: WaveMood? = WaveMood.NIGHT_DRIVE,
    val diversityPercent: Int = 50, // 0 = familiar favorites, 100 = maximum discovery
    val excludedArtists: Set<String> = emptySet(),
    val excludedGenres: Set<String> = emptySet(),
    val naturalLanguagePrompt: String? = null
)

sealed class StreamUrlResult {
    data class Success(val uriString: String, val isLocalCached: Boolean, val formatLabel: String) : StreamUrlResult()
    data class ExpiredLink(val trackId: String, val messageRu: String) : StreamUrlResult()
    data class RegionRestricted(val trackId: String, val messageRu: String) : StreamUrlResult()
    data class NetworkError(val trackId: String, val messageRu: String) : StreamUrlResult()
}

@Serializable
data class CatalogSearchResult(
    val query: String,
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val genres: List<String> = emptyList(),
    val matchingPlaylists: List<String> = emptyList(),
    val similarSuggestions: List<Track> = emptyList(),
    val querySuggestions: List<String> = emptyList()
)

/**
 * Official 29-genre taxonomy for Music Form with subgenres, moods, curated collections,
 * and search/tag mappings for real remote music APIs.
 */
object MusicFormGenresTaxonomy {

    val allCategories: List<GenreCategory> = listOf(
        GenreCategory(
            id = "pop",
            titleRu = "Поп",
            subtitleRu = "Современные мировые чарты, синт-поп и танцевальные хиты",
            primaryColorHex = "#FF4081",
            secondaryColorHex = "#7C4DFF",
            apiQueryTerm = "pop hits modern",
            jamendoTag = "pop",
            subgenres = listOf("Дэнс-поп", "Синт-поп", "Дрим-поп", "Электро-поп", "Инди-поп"),
            moods = listOf("Бодрое", "Вечеринка", "Лето", "Хорошее настроение"),
            curatedPlaylists = listOf("Главные поп-премьеры", "Неоновый поп-импульс", "Поп-драйв 24/7"),
            catalogTrackEstimate = 18_400
        ),
        GenreCategory(
            id = "rock",
            titleRu = "Рок и альтернативный рок",
            subtitleRu = "Гитарный драйв, пост-панк, гранж и современная альтернатива",
            primaryColorHex = "#FF3D00",
            secondaryColorHex = "#3D5AFE",
            apiQueryTerm = "alternative rock indie rock",
            jamendoTag = "rock",
            subgenres = listOf("Альтернативный рок", "Пост-рок", "Гаражный рок", "Хард-рок", "Шугейз"),
            moods = listOf("Драйв", "Энергия", "Концерт", "Дорога"),
            curatedPlaylists = listOf("Альтернативная сцена", "Гитарный перегруз", "Легенды и новая кровь"),
            catalogTrackEstimate = 16_200
        ),
        GenreCategory(
            id = "hiphop",
            titleRu = "Хип-хоп и рэп",
            subtitleRu = "Плотные биты, бум-бэп, трэп и речитатив новой школы",
            primaryColorHex = "#FFAB00",
            secondaryColorHex = "#651FFF",
            apiQueryTerm = "hip hop rap beats",
            jamendoTag = "hiphop",
            subgenres = listOf("Трэп", "Бум-бэп", "Мелодик-рэп", "Инструментальный хип-хоп", "Клауд-рэп"),
            moods = listOf("Тренировка", "Улица", "Ритм", "Уверенность"),
            curatedPlaylists = listOf("Биты мегаполиса", "Новая школа 808", "Ночной флоу"),
            catalogTrackEstimate = 14_900
        ),
        GenreCategory(
            id = "rnb",
            titleRu = "R&B и соул",
            subtitleRu = "Бархатный вокал, нео-соул и чувственный ночной грув",
            primaryColorHex = "#AA00FF",
            secondaryColorHex = "#00E5FF",
            apiQueryTerm = "r&b neo soul",
            jamendoTag = "rnb",
            subgenres = listOf("Нео-соул", "Contemporary R&B", "Альтернативный R&B", "Соул-баллады"),
            moods = listOf("Вечер", "Романтика", "Уют", "Спокойное"),
            curatedPlaylists = listOf("Бархатный вечер", "Нео-соул вибрации", "Ночной R&B"),
            catalogTrackEstimate = 9_800
        ),
        GenreCategory(
            id = "electronic",
            titleRu = "Электронная музыка",
            subtitleRu = "Синтезаторы, глубокий бас, синтвейв и футуристичная электроника",
            primaryColorHex = "#00E5FF",
            secondaryColorHex = "#7C4DFF",
            apiQueryTerm = "electronic synthwave",
            jamendoTag = "electronic",
            subgenres = listOf("Synthwave", "IDM", "Downtempo", "Progressive Electronic", "Future Garage"),
            moods = listOf("Ночной драйв", "Фокус", "Киберпанк", "Энергия"),
            curatedPlaylists = listOf("Электрическая форма", "Неоновый горизонт", "Код и синтезаторы"),
            catalogTrackEstimate = 24_500
        ),
        GenreCategory(
            id = "edm",
            titleRu = "EDM и техно",
            subtitleRu = "Клубный пульс, мелодик-техно, хаус и фестивальная энергия",
            primaryColorHex = "#651FFF",
            secondaryColorHex = "#00E676",
            apiQueryTerm = "edm techno house",
            jamendoTag = "techno",
            subgenres = listOf("Мелодик-техно", "Deep House", "Tech House", "Trance", "Big Room"),
            moods = listOf("Клуб", "Танцы", "Тренировка", "Бодрое"),
            curatedPlaylists = listOf("Техно-пульс 130 BPM", "Ночной танцпол", "Фестивальный прайм"),
            catalogTrackEstimate = 19_300
        ),
        GenreCategory(
            id = "dnb",
            titleRu = "Драм-н-бейс",
            subtitleRu = "Стремительные ломаные ритмы 174 BPM, ликвид-фанк и нейрофанк",
            primaryColorHex = "#F50057",
            secondaryColorHex = "#00E5FF",
            apiQueryTerm = "drum and bass liquid dnb",
            jamendoTag = "drumandbass",
            subgenres = listOf("Liquid Funk", "Neurofunk", "Atmospheric DnB", "Jump Up", "Jungle"),
            moods = listOf("Тренировка", "Бег", "Скорость", "Максимум драйва"),
            curatedPlaylists = listOf("Импульс 174 BPM", "Ликвид-горизонт", "Кардио и нейрофанк"),
            catalogTrackEstimate = 8_600
        ),
        GenreCategory(
            id = "jazz",
            titleRu = "Джаз",
            subtitleRu = "Импровизация саксофона, фортепианное трио и современный ню-джаз",
            primaryColorHex = "#FFB300",
            secondaryColorHex = "#283593",
            apiQueryTerm = "jazz smooth jazz trio",
            jamendoTag = "jazz",
            subgenres = listOf("Кул-джаз", "Ню-джаз", "Смут-джаз", "Бибоп", "Джаз-фьюжн"),
            moods = listOf("Вечер", "Кофе", "Спокойное", "Интеллектуальное"),
            curatedPlaylists = listOf("Полночный джаз-клуб", "Винил и саксофон", "Утренний эспрессо"),
            catalogTrackEstimate = 11_400
        ),
        GenreCategory(
            id = "blues",
            titleRu = "Блюз",
            subtitleRu = "Душевная гитара, чикагский блюз и винтажный грув",
            primaryColorHex = "#3949AB",
            secondaryColorHex = "#FF6D00",
            apiQueryTerm = "blues guitar electric blues",
            jamendoTag = "blues",
            subgenres = listOf("Блюз-рок", "Дельта-блюз", "Чикагский блюз", "Соул-блюз"),
            moods = listOf("Дорога", "Меланхолия", "Вечер", "Винтаж"),
            curatedPlaylists = listOf("Гитарные истории", "Ночное шоссе блюза", "Ламповый звук"),
            catalogTrackEstimate = 7_200
        ),
        GenreCategory(
            id = "classical",
            titleRu = "Классическая музыка",
            subtitleRu = "Симфонические шедевры, камерные струнные и современная неоклассика",
            primaryColorHex = "#B388FF",
            secondaryColorHex = "#1A237E",
            apiQueryTerm = "classical piano neoclassical orchestra",
            jamendoTag = "classical",
            subgenres = listOf("Неоклассика", "Фортепиано соло", "Симфонический оркестр", "Камерная музыка", "Барокко"),
            moods = listOf("Учёба", "Чтение", "Фокус", "Вдохновение"),
            curatedPlaylists = listOf("Неоклассика для фокуса", "Фортепиано при свечах", "Золотой фонд симфоний"),
            catalogTrackEstimate = 15_600
        ),
        GenreCategory(
            id = "lofi",
            titleRu = "Лоу-фай",
            subtitleRu = "Уютные инструментальные биты с виниловым треском для учёбы и работы",
            primaryColorHex = "#00BFA5",
            secondaryColorHex = "#536DFE",
            apiQueryTerm = "lofi chillhop study beats",
            jamendoTag = "lofi",
            subgenres = listOf("Chillhop", "Lo-Fi Jazz", "Study Beats", "Sleep Lo-Fi", "Vaporhop"),
            moods = listOf("Учёба", "Фокус", "Чтение", "Дождь за окном"),
            curatedPlaylists = listOf("Биты для учёбы и кода", "Дождь в Токио", "Ночная библиотека"),
            catalogTrackEstimate = 12_900
        ),
        GenreCategory(
            id = "indie",
            titleRu = "Инди",
            subtitleRu = "Независимая сцена, искренняя лирика, реверб и тёплый гитарный звук",
            primaryColorHex = "#536DFE",
            secondaryColorHex = "#00E5FF",
            apiQueryTerm = "indie pop indie folk",
            jamendoTag = "indie",
            subgenres = listOf("Инди-рок", "Инди-поп", "Пост-панк", "Bedroom Pop", "Дрим-поп"),
            moods = listOf("Прогулка", "Меланхолия", "Вечер", "Дорога"),
            curatedPlaylists = listOf("Огни северного порта", "Новые инди-голоса", "Последний трамвай"),
            catalogTrackEstimate = 14_100
        ),
        GenreCategory(
            id = "metal",
            titleRu = "Метал",
            subtitleRu = "Мощные риффы, симфо-метал, металкор и прогрессивная тяжесть",
            primaryColorHex = "#D50000",
            secondaryColorHex = "#212121",
            apiQueryTerm = "metal heavy metal metalcore",
            jamendoTag = "metal",
            subgenres = listOf("Металкор", "Прогрессив-метал", "Симфо-метал", "Хэви-метал", "Индастриал-метал"),
            moods = listOf("Тренировка", "Энергия", "Экстрим", "Драйв"),
            curatedPlaylists = listOf("Стальной напор", "Тренировка на пределе", "Прогрессивные риффы"),
            catalogTrackEstimate = 9_400
        ),
        GenreCategory(
            id = "punk",
            titleRu = "Панк",
            subtitleRu = "Быстрый темп, поп-панк, скейт-панк и бунтарская энергия",
            primaryColorHex = "#FF1744",
            secondaryColorHex = "#FFEA00",
            apiQueryTerm = "punk rock pop punk",
            jamendoTag = "punk",
            subgenres = listOf("Поп-панк", "Панк-рок", "Пост-панк", "Скейт-панк", "Хардкор-панк"),
            moods = listOf("Бодрое", "Драйв", "Скорость", "Спорт"),
            curatedPlaylists = listOf("Скейт-парк 2000-х", "Три аккорда и скорость", "Панк-перезагрузка"),
            catalogTrackEstimate = 6_800
        ),
        GenreCategory(
            id = "funk",
            titleRu = "Фанк и диско",
            subtitleRu = "Танцевальный бас-грув, духовые секции, ню-диско и буги",
            primaryColorHex = "#FF9100",
            secondaryColorHex = "#AA00FF",
            apiQueryTerm = "funk disco nu disco",
            jamendoTag = "funk",
            subgenres = listOf("Ню-диско", "Классический фанк", "Фьюче-фанк", "Диско-хаус", "Джаз-фанк"),
            moods = listOf("Танцы", "Вечеринка", "Бодрое", "Улыбка"),
            curatedPlaylists = listOf("Зеркальный шар и грув", "Фанк-лаборатория", "Ню-диско коктейль"),
            catalogTrackEstimate = 8_300
        ),
        GenreCategory(
            id = "reggae",
            titleRu = "Регги",
            subtitleRu = "Солнечный ритм, даб, рутс-регги и карибский грув",
            primaryColorHex = "#00C853",
            secondaryColorHex = "#FFD600",
            apiQueryTerm = "reggae dub roots",
            jamendoTag = "reggae",
            subgenres = listOf("Рутс-регги", "Даб", "Дэнсхолл", "Ска", "Регги-фьюжн"),
            moods = listOf("Лето", "Отдых", "Солнце", "Чилл"),
            curatedPlaylists = listOf("Солнечный берег", "Даб-терраса", "Тёплый бриз"),
            catalogTrackEstimate = 6_100
        ),
        GenreCategory(
            id = "country",
            titleRu = "Кантри",
            subtitleRu = "Акустические истории, слайд-гитара, американа и дорожная романтика",
            primaryColorHex = "#FF6D00",
            secondaryColorHex = "#5D4037",
            apiQueryTerm = "country americana acoustic",
            jamendoTag = "country",
            subgenres = listOf("Современное кантри", "Американа", "Кантри-рок", "Блюграсс", "Кантри-поп"),
            moods = listOf("Дорога", "Путешествие", "Уют", "Вечер"),
            curatedPlaylists = listOf("Бесконечное шоссе", "Акустика прерий", "Кантри и костёр"),
            catalogTrackEstimate = 7_500
        ),
        GenreCategory(
            id = "folk",
            titleRu = "Фолк",
            subtitleRu = "Живые акустические инструменты, этника, инди-фолк и баллады",
            primaryColorHex = "#2E7D32",
            secondaryColorHex = "#00BFA5",
            apiQueryTerm = "indie folk acoustic folk",
            jamendoTag = "folk",
            subgenres = listOf("Инди-фолк", "Нео-фолк", "Фолк-рок", "Этно-фолк", "Бардовская акустика"),
            moods = listOf("Природа", "Спокойное", "Поход", "Меланхолия"),
            curatedPlaylists = listOf("Лесная тропа", "Сказки северного ветра", "Тёплый инди-фолк"),
            catalogTrackEstimate = 8_900
        ),
        GenreCategory(
            id = "kpop",
            titleRu = "K-pop",
            subtitleRu = "Южнокорейские бэнгеры, яркая хореография и глобальные суперхиты",
            primaryColorHex = "#FF4081",
            secondaryColorHex = "#00E5FF",
            apiQueryTerm = "k-pop korean pop",
            jamendoTag = "Asian",
            subgenres = listOf("K-Pop Idol Hits", "K-Hip-Hop", "K-R&B", "K-Indie", "Korean OST"),
            moods = listOf("Бодрое", "Танцы", "Тренировка", "Энергия"),
            curatedPlaylists = listOf("Сеул Неон Топ-50", "K-Pop Дэнс-драйв", "Корейские дорамы и чилл"),
            catalogTrackEstimate = 9_600
        ),
        GenreCategory(
            id = "jpop",
            titleRu = "J-pop",
            subtitleRu = "Японская поп-сцена, сити-поп, вокалоид и токийский электро-рок",
            primaryColorHex = "#E040FB",
            secondaryColorHex = "#3D5AFE",
            apiQueryTerm = "j-pop japanese city pop",
            jamendoTag = "jpop",
            subgenres = listOf("City Pop", "J-Rock", "Shibuya-kei", "Электронный J-Pop", "Каваий-бейс"),
            moods = listOf("Ночной Токио", "Бодрое", "Аниме", "Поездка"),
            curatedPlaylists = listOf("Огни Сибуи", "Токийский City Pop 80s", "J-Pop Суперчарт"),
            catalogTrackEstimate = 8_700
        ),
        GenreCategory(
            id = "anime",
            titleRu = "Аниме-саундтреки",
            subtitleRu = "Взрывные опенинги, эмоциональные эндинги и симфонические темы аниме",
            primaryColorHex = "#FF6E40",
            secondaryColorHex = "#7C4DFF",
            apiQueryTerm = "anime soundtrack opening ost",
            jamendoTag = "anime",
            subgenres = listOf("Anime Openings", "Anime Endings", "Cyberpunk Anime OST", "Anime Piano", "Anime Lo-Fi"),
            moods = listOf("Аниме", "Драйв", "Тренировка", "Вдохновение"),
            curatedPlaylists = listOf("Аниме Опенинги: Neo-Tokyo", "Эпичные битвы OST", "Лирика аниме на пианино"),
            catalogTrackEstimate = 7_400
        ),
        GenreCategory(
            id = "soundtrack",
            titleRu = "Саундтреки из фильмов и игр",
            subtitleRu = "Эпическая оркестровая музыка, киберпанк-скоры и культовые темы видеоигр",
            primaryColorHex = "#3D5AFE",
            secondaryColorHex = "#FF3D00",
            apiQueryTerm = "film score video game soundtrack epic",
            jamendoTag = "soundtrack",
            subgenres = listOf("Игровые саундтреки", "Киномузыка", "Эпический трейлер", "Synth Sci-Fi Score", "Фэнтези-оркестр"),
            moods = listOf("Фокус", "Кино", "Гейминг", "Эпик"),
            curatedPlaylists = listOf("Миры видеоигр", "Кинематографический масштаб", "Киберпанк и Sci-Fi OST"),
            catalogTrackEstimate = 13_200
        ),
        GenreCategory(
            id = "ambient",
            titleRu = "Эмбиент",
            subtitleRu = "Бескрайние звуковые текстуры, космос и глубокое погружение без слов",
            primaryColorHex = "#1E88E5",
            secondaryColorHex = "#00E5FF",
            apiQueryTerm = "ambient space drone atmospheric",
            jamendoTag = "ambient",
            subgenres = listOf("Space Ambient", "Dark Ambient", "Drone", "Organic Ambient", "Кинематографический эмбиент"),
            moods = listOf("Сон", "Учёба", "Космос", "Тишина"),
            curatedPlaylists = listOf("Орбитальная тишина", "Глубокий космос", "Фокус без границ"),
            catalogTrackEstimate = 14_700
        ),
        GenreCategory(
            id = "chill",
            titleRu = "Чилл и релакс",
            subtitleRu = "Мягкий даунтемпо, чиллаут и лаунж для восстановления сил",
            primaryColorHex = "#00ACC1",
            secondaryColorHex = "#7C4DFF",
            apiQueryTerm = "chillout lounge relax downtempo",
            jamendoTag = "chillout",
            subgenres = listOf("Chillout", "Lounge", "Chillstep", "Balearic Beat", "Soft Electronic"),
            moods = listOf("Отдых", "Вечер", "Закат", "Спокойное"),
            curatedPlaylists = listOf("Вечерний лаунж", "Мягкая перезагрузка", "Закат у океана"),
            catalogTrackEstimate = 16_800
        ),
        GenreCategory(
            id = "meditation",
            titleRu = "Медитативная музыка",
            subtitleRu = "Поющие чаши, звуки природы, бинауральные волны и гармония дыхания",
            primaryColorHex = "#26A69A",
            secondaryColorHex = "#80CBC4",
            apiQueryTerm = "meditation zen healing sleep",
            jamendoTag = "meditation",
            subgenres = listOf("Дзен и йога", "Тибетские чаши", "Звуки природы", "Глубокий сон 432 Гц", "Майндфулнесс"),
            moods = listOf("Медитация", "Сон", "Йога", "Покой"),
            curatedPlaylists = listOf("Глубокое дыхание", "Звуковое исцеление", "Сон без тревог"),
            catalogTrackEstimate = 9_100
        ),
        GenreCategory(
            id = "acoustic",
            titleRu = "Акустическая музыка",
            subtitleRu = "Тёплая гитара фингерстайл, камерный вокал и живое звучание",
            primaryColorHex = "#FFB74D",
            secondaryColorHex = "#536DFE",
            apiQueryTerm = "acoustic guitar unplugged",
            jamendoTag = "acoustic",
            subgenres = listOf("Fingerstyle Guitar", "Unplugged", "Акустический поп", "Инструментальная гитара"),
            moods = listOf("Утро", "Уют", "Кофе", "Спокойное"),
            curatedPlaylists = listOf("Акустическое утро", "Шелковые струны", "Вечер у камина"),
            catalogTrackEstimate = 11_900
        ),
        GenreCategory(
            id = "retro",
            titleRu = "Ретро и музыка прошлых десятилетий",
            subtitleRu = "Золотая классика 70-х, 80-х, 90-х и 2000-х годов в новом качестве",
            primaryColorHex = "#EC407A",
            secondaryColorHex = "#FFAB00",
            apiQueryTerm = "80s 90s retro synth classic hits",
            jamendoTag = "80s",
            subgenres = listOf("Хиты 80-х", "Евродэнс 90-х", "Нулевые 2000s", "Классик-рок 70-х", "Retrowave"),
            moods = listOf("Ностальгия", "Вечеринка", "Дорога", "Бодрое"),
            curatedPlaylists = listOf("Кассета 1988", "Танцпол девяностых", "Легенды винила"),
            catalogTrackEstimate = 13_500
        ),
        GenreCategory(
            id = "russian",
            titleRu = "Русская музыка",
            subtitleRu = "Русский инди, поп-хиты, рок-легенды и новая отечественная волна",
            primaryColorHex = "#2979FF",
            secondaryColorHex = "#00E5FF",
            apiQueryTerm = "русская музыка инди рок поп",
            jamendoTag = "russian",
            subgenres = listOf("Русский инди", "Русский рок", "Русский поп", "Пост-панк СНГ", "Русская электроника"),
            moods = listOf("Родное", "Поездка", "Вечер", "Душевное"),
            curatedPlaylists = listOf("Полярное радио: Русская волна", "Новые имена сцены", "Огни ночных проспектов"),
            catalogTrackEstimate = 10_500
        ),
        GenreCategory(
            id = "global_hits",
            titleRu = "Зарубежные хиты",
            subtitleRu = "Мировые чарты, платиновые синглы и главные тренды планеты",
            primaryColorHex = "#00E5FF",
            secondaryColorHex = "#651FFF",
            apiQueryTerm = "global top hits chart",
            jamendoTag = "pop",
            subgenres = listOf("Global Top 100", "Euro Hits", "US Billboard Vibes", "Latin Crossover", "UK Garage & Pop"),
            moods = listOf("Бодрое", "Чарты", "В дорогу", "Тренды"),
            curatedPlaylists = listOf("Мировой прайм-тайм", "Главные хиты планеты", "Мегаполис FM"),
            catalogTrackEstimate = 22_000
        )
    )

    fun findByTitleOrId(queryOrTitle: String): GenreCategory? {
        val q = queryOrTitle.trim().lowercase()
        return allCategories.find {
            it.id.lowercase() == q ||
                it.titleRu.lowercase() == q ||
                it.subgenres.any { sub -> sub.lowercase() == q }
        } ?: allCategories.find {
            it.titleRu.lowercase().contains(q) ||
                it.apiQueryTerm.lowercase().contains(q) ||
                it.jamendoTag.lowercase() == q
        }
    }
}
