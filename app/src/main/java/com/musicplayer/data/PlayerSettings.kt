package com.musicplayer.data

// ── Top Bar / Header customization ───────────────────────────────────────────
data class TopBarSettings(
    // ── Пресеты стиля ─────────────────────────────────────────────────────────
    val stylePreset: Int       = 6,   // 0=Стекло, 1=Минимал, 2=Жирный, 3=Неон, 4=Ретро, 5=Кастом

    // ── Иконка ────────────────────────────────────────────────────────────────
    val showIcon: Boolean      = true,
    val iconStyle: Int         = 1,   // 0=Нота, 1=Эквалайзер, 2=Волна, 3=Диск, 4=Нет
    val iconAnimation: Int     = 4,   // 0=Пульс, 1=Вращение, 2=Прыжок, 3=Без анимации
    val iconSize: Float        = 30f, // dp
    val iconGlowRadius: Float  = 0.82f, // 0..1 сила свечения вокруг иконки
    val iconRingVisible: Boolean = true,

    // ── Заголовок ────────────────────────────────────────────────────────────
    val titleText: String      = "Bloomee Music",  // кастомный текст заголовка
    val titleSize: Float       = 23f,       // sp
    val titleWeight: Int       = 0,         // 0=Bold, 1=ExtraBold, 2=Medium
    val titleLetterSpacing: Float = -0.3f,
    val titleGlowStrength: Float = 0.14f,
    val showSongCount: Boolean = true,
    val songCountPrefix: String= "",        // кастомный префикс перед числом
    val subtitleSize: Float    = 11f,

    // ── Фон шапки ─────────────────────────────────────────────────────────────
    val bgStyle: Int           = 5,   // 0=Градиент, 1=Сплошной, 2=Прозрачный, 3=Матовое стекло
    val bgOpacity: Float       = 0.94f,

    // ── Декоративные орбы ────────────────────────────────────────────────────
    val orbsVisible: Boolean   = true,
    val orbCount: Int          = 3,   // 1..5
    val orbOpacity: Float      = 0.28f,
    val orbSpeed: Float        = 1.1f,
    val orbSize: Float         = 0.78f,  // относительный размер (0.2..1.5)
    val orbColorMode: Int      = 1,   // 0=Accent, 1=Accent+Var, 2=Радуга, 3=Белый

    // ── Частицы ───────────────────────────────────────────────────────────────
    val sparklesVisible: Boolean = true,
    val sparkleCount: Int      = 10,
    val sparkleSpeed: Float    = 1.15f,

    // ── Разделитель ───────────────────────────────────────────────────────────
    val dividerStyle: Int      = 3,   // 0=Нет, 1=Линия, 2=Градиент, 3=Свечение
    val dividerOpacity: Float  = 0.72f,

    // ── Высота и отступы ─────────────────────────────────────────────────────
    val headerHeightExtra: Float = 0f,  // доп. высота в dp (-8..+32)

    // ── Кнопки действий ──────────────────────────────────────────────────────
    val buttonStyle: Int       = 5,   // 0=Стекло, 1=Заливка, 2=Контур, 3=Без фона
    val buttonCorner: Float    = 18f, // dp
    val accentButtons: Boolean = true, // подсвечивать кнопки accent-цветом
    val buttonSize: Float      = 44f,  // dp — размер кнопок
    val buttonBorderVisible: Boolean = true, // рамка вокруг кнопок
    val buttonGap: Float       = 4f,   // расстояние между action-кнопками

    // ── Заголовок — дополнительно ────────────────────────────────────────
    val titleItalic: Boolean   = false, // курсив заголовка
    val titleColorMode: Int    = 2,    // 0=Primary, 1=Accent, 2=White, 3=Secondary

    // ── Иконка — дополнительно ───────────────────────────────────────────
    val iconBgVisible: Boolean = true,  // показывать фон иконки
    val iconTintMode: Int      = 0,    // 0=Accent, 1=White, 2=Primary

    // ── Разделитель — дополнительно ──────────────────────────────────────
    val dividerThickness: Float = 1.5f,   // толщина разделителя dp

    // ── Liquid glass-тонкая настройка ────────────────────────────────────
    val glassTintMode: Int     = 0,      // 0=Accent, 1=Ice, 2=Neutral, 3=Warm
    val glassDepth: Float      = 0.72f,  // плотность/матовость стекла
    val edgeShine: Float       = 0.58f,  // верхняя световая кромка
    val titleCapsuleVisible: Boolean = false,
    val titleCapsuleOpacity: Float = 0.20f,
    val subtitleStyle: Int     = 0       // 0=plain, 1=glass pill, 2=accent pill
)


data class OrbSettings(
    // ── Базовые ──────────────────────────────────────────────────────────────
    val speed: Float           = 1.0f,
    val contrast: Float        = 0.7f,
    val coverage: Float        = 0.9f,
    val bassReactive: Boolean  = true,
    val showInLyrics: Boolean  = true,
    val showInPlayer: Boolean  = true,      // показывать орбы в плеере

    // ── Количество и форма ────────────────────────────────────────────────────
    val orbCount: Int          = 3,         // 1..8
    val orbShape: Int          = 0,         // 0=круг, 1=капля, 2=звезда, 3=кристалл
    val orbSpread: Float       = 0.6f,      // как далеко разбросаны (0..1)
    val verticalBias: Float    = 0f,        // смещение по вертикали (-1..+1)

    // ── Визуал ────────────────────────────────────────────────────────────────
    val glowIntensity: Float   = 0.6f,
    val blurRadius: Float      = 0.7f,
    val saturation: Float      = 0.8f,      // насыщенность цветов (0..1)
    val brightness: Float      = 0.75f,     // яркость орбов (0..1)
    val borderGlow: Boolean    = false,     // светящийся контур орба
    val borderThickness: Float = 0.3f,      // толщина контура (0..1)
    val frostedGlass: Boolean  = false,     // эффект матового стекла (overlay)
    val noiseAmount: Float     = 0.1f,      // зернистость (0..1)

    // ── Цвет ─────────────────────────────────────────────────────────────────
    val colorShift: Float      = 0f,        // смещение оттенка 0°..360°
    val colorPullStrength: Float = 0.5f,    // тяга к цвету обложки
    val colorCycleSpeed: Float = 0f,        // скорость смены цвета (0=выкл)
    val chromaEffect: Boolean  = false,     // RGB-хрома разлёт

    // ── Движение ─────────────────────────────────────────────────────────────
    val rotationEnabled: Boolean = true,
    val magneticToArt: Boolean  = false,
    val magneticStrength: Float = 0.3f,
    val pulseOnBeat: Boolean    = true,
    val beatScale: Float        = 0.18f,    // сила масштаба на бит (0..0.5)
    val waveMode: Boolean       = false,    // орбы движутся волнами
    val flowMode: Int           = 0,        // 0=дрейф, 1=орбита, 2=хаос, 3=синус

    // ── Эффекты ───────────────────────────────────────────────────────────────
    val trailEffect: Boolean    = false,
    val trailLength: Float      = 0.4f,     // длина следов (0..1)
    val particleEmission: Boolean = false,
    val particleCount: Int      = 20,       // количество частиц
    val kaleidoscopeMode: Boolean = false,
    val depthEffect: Boolean    = false,    // эффект глубины (ближний/дальний план)
    val showVisualizerBars: Boolean = false
)

// ── Custom theme color palette ────────────────────────────────────────────────
data class CustomThemeColors(
    // Background levels
    val bgDeep: Long       = 0xFF1C1008,
    val bgSurface: Long    = 0xFF2A1A0A,
    val bgCard: Long       = 0xFF321E0D,
    val bgElevated: Long   = 0xFF3D2612,
    // Accent colors
    val accent: Long       = 0xFFE8B88A,
    val accentVar: Long    = 0xFFD4956A,
    val accentMuted: Long  = 0xFF8B6040,
    // Text colors
    val textPrimary: Long  = 0xFFF0DCC0,
    val textSecondary: Long= 0xFFB8956A,
    val textDisabled: Long = 0xFF6B4E30,
    // UI extras
    val divider: Long      = 0xFF3D2612,
    // Element style (0=default, 1=rounded, 2=sharp)
    val buttonCornerRadius: Float = 50f,
    val cardCornerRadius: Float   = 16f,
    val sliderTrackHeight: Float  = 4f,
    val surfaceAlpha: Float       = 1f,
    val surfaceBorderAlpha: Float = 0.18f,
    val surfaceTintStrength: Float = 1f,
    val surfaceToneMix: Float     = 1f,
    val containerVibrance: Float  = 1f,
    val useGradientBg: Boolean    = true,
    val isLightTheme: Boolean     = false
)

data class PlayerSettings(
    val shuffleEnabled: Boolean   = false,
    val repeatMode: RepeatMode    = RepeatMode.NONE,
    val crossfadeDuration: Int    = 0,
    val bassBoostEnabled: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val showAlbumArt: Boolean     = true,
    val theme: AppTheme           = AppTheme.BLOOMEE,
    val interfaceStyle: InterfaceStyle = InterfaceStyle.MATERIAL3,
    val useDynamicColors: Boolean = false,
    val sortOrder: SortOrder      = SortOrder.TITLE,
    val customFontUri: String     = "",
    val selectedFontId: String    = "manrope",
    val lyricsFontScale: Float    = 1.0f,
    val lyricsFadeStyle: Int      = 1,
    val lyricsAlignment: Int      = 0,   // 0=left, 1=center, 2=right
    val lyricsCurlAnim: Boolean   = true,
    val useWavySeekBar: Boolean   = true,
    val showRandomOnlineAlbumsShelf: Boolean = false,

    // ── Типографика ───────────────────────────────────────────────────────────
    val playerTitleSize: Float    = 22f,     // размер заголовка трека в плеере (sp)
    val playerArtistSize: Float   = 15f,     // размер исполнителя в плеере (sp)
    val trackListTitleSize: Float = 15f,     // размер заголовка в списке треков (sp)
    val trackListArtistSize: Float= 13f,     // размер исполнителя в списке (sp)
    val letterSpacingEm: Float    = 0f,      // межбуквенный интервал (em)
    val lineHeightScale: Float    = 1.0f,    // масштаб межстрочного интервала (0.8..1.6)
    val boldTitles: Boolean       = false,   // жирные заголовки в списке
    val uppercaseTitles: Boolean  = false,   // прописные буквы в заголовках
    val textShadowEnabled: Boolean= false,   // тень/свечение под текстом
    val textShadowIntensity: Float= 0.5f,    // интенсивность тени (0..1)
    val trackItemDensity: Int     = 1,       // 0=компактный, 1=обычный, 2=просторный
    val trackItemCornerRadius: Float = 12f,  // скругление карточки трека (dp)
    val showTrackNumber: Boolean  = false,   // показывать порядковый номер
    val showDurationInList: Boolean = true,  // показывать длительность в списке
    val showBitrateInList: Boolean = false,  // показывать битрейт в списке
    val glowOnNowPlaying: Boolean = true,    // подсветка активного трека
    val artistNameStyle: Int      = 0,       // 0=обычный, 1=курсив, 2=заглушён
    val playerTimeSize: Float     = 12f,     // размер таймкода в плеере (sp)
    val showAlbumInList: Boolean  = false,   // показывать альбом в списке треков
    val trackArtSize: Float       = 52f,     // размер обложки в списке (dp, 36..72)
    val trackMetaOpacity: Float   = 0.78f,   // прозрачность вторичного текста
    val artistLetterSpacingEm: Float = 0f,   // межбуквенный у артиста/метаданных
    val trackTextAlign: Int       = 0,       // 0=left, 1=center, 2=right
    val trackItemPaddingScale: Float = 1.0f, // масштаб внутренних отступов строки
    val nowPlayingGlowStrength: Float = 0.78f, // сила подсветки текущего трека
    val trackMetaCapsule: Boolean = false,   // артист и альбом в стеклянной капсуле
    val trackTitleWeightMode: Int = 2,       // 0=Regular, 1=Medium, 2=SemiBold, 3=Bold, 4=Black
    val trackMetaWeightMode: Int = 1,        // 0=Regular, 1=Medium, 2=SemiBold, 3=Bold
    val trackTitleItalic: Boolean = false,   // курсив заголовка трека
    val trackTitleOpacity: Float  = 1.0f,    // прозрачность заголовка
    val trackMetaUppercase: Boolean = false, // метаданные прописными
    val trackMetaSpacingScale: Float = 1.0f, // расстояние между заголовком и метаданными
    val trackTitleTwoLines: Boolean = false, // длинные названия можно в 2 строки
    val durationBadgeStyle: Int   = 0,       // 0=plain, 1=capsule, 2=accent
    val trackTitleAccentBlend: Float = 0.0f, // подмешивание акцентного цвета к заголовку
    val trackTitleDecorStyle: Int = 0,       // 0=none, 1=underline, 2=side bar, 3=glass chip
    val trackMetaSeparatorStyle: Int = 0,    // 0=dot, 1=slash, 2=wave, 3=spark

    // ── Анимации ──────────────────────────────────────────────────────────────
    val playerOpenMode: Int        = 0,   // 0=Сразу открыть плеер, 1=Мини-плеер (tap → открыть)
    val playerEnterAnim: Int       = 0,   // 0=Снизу(iOS), 1=Масштаб, 2=Fade, 3=Слайд, 4=Переворот, 5=Пружина, 6=Резина, 7=Взрыв, 8=Шторка, 9=Карусель, 10=Растворение, 11=Куб, 12=Диагональ, 13=Подъём, 14=Фокус
    val listScrollAnim: Boolean    = true, // плавный скролл к треку
    val rowPressAnim: Boolean      = true, // анимация нажатия строки

    // ── Расширенные анимации (новые) ──────────────────────────────────────────
    val screenTransitionAnim: Int  = 0,   // 0-17 переходы экранов
    val albumArtAnim: Int          = 0,   // 0=Нет, 1=Вращение, 2=Пульс, 3=Дыхание, 4=Параллакс, 5=Vinyl, 6=Glitch
    val listItemEntryAnim: Int     = 0,   // 0=Нет, 1=Слайд, 2=Fade, 3=Пружина, 4=Каскад, 5=Откат
    val miniPlayerEntryAnim: Int   = 0,   // 0=Снизу, 1=Пружина, 2=Fade, 3=Рост, 4=Вращение
    val buttonPressStyle: Int      = 0,   // 0=Стандарт, 1=Расширенный, 2=Свечение, 3=Отдача
    val tabSwitchAnim: Int         = 0,   // 0=Слайд, 1=Fade, 2=Масштаб, 3=Переворот, 4=Морф
    val trackDeleteAnimStyle: Int  = 1,   // 0=Растворение, 1=Пыль, 2=Snap

    // ── Кроссфейд вкладок (библиотека ↔ поиск) ───────────────────────────────
    val tabCrossfadeEnabled: Boolean = false,
    val tabCrossfadeDurationMs: Int  = 400, // 100..2000

    // ── Параметры анимаций (тонкая настройка) ─────────────────────────────────
    val animParams: AnimParams       = AnimParams(),

    // ── Новые настройки (версия 1) ─────────────────────────────────────────────
    val uiScale: Float = 1.0f,  // глобальный масштаб интерфейса (0.7..1.5)
    val miniPlayerHeight: Float = 72f,  // высота мини-плеера в dp (56..120)
    val pauseOnHeadphoneDisconnect: Boolean = true,  // пауза при отключении наушников
    val resumeOnHeadphoneConnect: Boolean = false,  // продолжить при подключении
    val showHiddenTracks: Boolean = false,  // показывать скрытые треки
    val geniusApiEnabled: Boolean = true  // использовать Genius как fallback для текстов
)

/**
 * Тонкие параметры для каждой группы анимаций.
 * speedFactor: множитель длительности (0.3=быстро, 1.0=нормально, 3.0=медленно)
 * easingIdx:   0=EaseInOut, 1=EaseOut, 2=EaseIn, 3=Linear, 4=Bounce, 5=Overshoot
 * dampingIdx:  0=Medium, 1=Low(bounce), 2=High(snappy), 3=NoBounce
 * stiffnessIdx:0=Medium, 1=MediumLow, 2=High, 3=Low
 */
data class AnimParams(
    // Открытие плеера
    val playerEnterSpeed: Float  = 1.0f,
    val playerEnterEasing: Int   = 0,
    val playerEnterDamping: Int  = 0,
    val playerEnterStiffness: Int= 0,

    // Переходы экранов
    val screenTransSpeed: Float  = 1.0f,
    val screenTransEasing: Int   = 0,
    val screenTransDamping: Int  = 0,
    val screenTransStiffness: Int= 0,

    // Переключение вкладок
    val tabSwitchSpeed: Float    = 1.0f,
    val tabSwitchEasing: Int     = 0,
    val tabSwitchDamping: Int    = 0,
    val tabSwitchStiffness: Int  = 0,

    // Обложка альбома
    val albumArtSpeed: Float     = 1.0f,
    val albumArtEasing: Int      = 0,
    val albumArtDamping: Int     = 0,

    // Список треков
    val listItemSpeed: Float     = 1.0f,
    val listItemEasing: Int      = 0,
    val listItemDamping: Int     = 0,
    val listItemDelay: Float     = 1.0f, // каскадная задержка (0=без, 2=двойная)

    // Мини-плеер
    val miniPlayerSpeed: Float   = 1.0f,
    val miniPlayerEasing: Int    = 0,
    val miniPlayerDamping: Int   = 0,
    val miniPlayerFloatiness: Float = 1.0f,

    // Кнопки
    val buttonSpeed: Float       = 1.0f,
    val buttonStrength: Float    = 1.0f,  // сила эффекта (0.5..2.0)

    // Жидкие таб-переходы
    val tabFollowThrough: Float  = 1.0f,

    // Удаление треков
    val deleteAnimSpeed: Float   = 1.0f,
    val deleteScatter: Float     = 1.0f,
    val deleteParticleDensity: Float = 1.0f
)

enum class RepeatMode { NONE, ONE, ALL }
enum class AppTheme   {
    BLOOMEE,
    MATERIAL_YOU,
    DARK_BROWN, DARK_BLACK, LIGHT, PURPLE, PINK, OCEAN, FOREST, SUNSET, MIDNIGHT,
    // New themes
    NEON, ROSE_GOLD, ARCTIC, AMBER, EMERALD, AMOLED,
    LAVENDER, RUBY, STEEL, MATCHA, DESERT, COBALT, CHERRY, MOCHA, AURORA,
    COSMOS, SUNRISE, GRAPHITE, SAKURA, LAGOON, VOLCANO,
    IVORY, CITRUS, FROST, EMBER, TURQUOISE, PLUM, PEARL, OLIVE,
    SAPPHIRE, MINT, BRONZE, WINE,
    // Custom user theme
    CUSTOM
}
enum class InterfaceStyle { CLASSIC, MATERIAL3, LIQUID_GLASS }
enum class SortOrder  { TITLE, ARTIST, ALBUM, DURATION, DATE_ADDED }
