package com.musicplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.ui.theme.*
import com.musicplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.musicplayer.data.AnimParams
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationSettingsScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val c       = MaterialTheme.colorScheme
    val font    = LocalAppFontFamily.current
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, null)
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Анимации", fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "Переходы, отклики и motion-поведение в стиле Material 3",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = font,
                            fontSize = 11.sp
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── 1. Открытие плеера ────────────────────────────────────────────
            AnimCard("Открытие плеера", Icons.Rounded.OpenInFull, c, font) {
                Text("Нажатие на трек", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OpenModeCard(Modifier.weight(1f), "Плеер сразу", settings.playerOpenMode == 0, c, font,
                        { viewModel.updateSettings(settings.copy(playerOpenMode = 0)) }) { t -> OpenImmediatePreview(c, t) }
                    OpenModeCard(Modifier.weight(1f), "Мини-плеер", settings.playerOpenMode == 1, c, font,
                        { viewModel.updateSettings(settings.copy(playerOpenMode = 1)) }) { t -> MiniPlayerPreview(c, t) }
                }
                HorizontalDivider(color = c.divider.copy(0.4f))
                Text("Анимация открытия плеера", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                Text("Нажмите на карточку, чтобы проиграть пример", color = c.textDisabled, fontFamily = font, fontSize = 10.sp)
                val playerEnterOptions = listOf(
                    Triple("Снизу (iOS)", Icons.Rounded.KeyboardArrowUp, 0),
                    Triple("Масштаб", Icons.Rounded.ZoomOutMap, 1),
                    Triple("Fade", Icons.Rounded.Flare, 2),
                    Triple("Слайд", Icons.Rounded.ChevronRight, 3),
                    Triple("Переворот", Icons.Rounded.Flip, 4),
                    Triple("Пружина", Icons.Rounded.ExpandCircleDown, 5),
                    Triple("Резина", Icons.Rounded.Compress, 6),
                    Triple("Взрыв", Icons.Rounded.Brightness7, 7),
                    Triple("Шторка", Icons.Rounded.ViewAgenda, 8),
                    Triple("Карусель", Icons.Rounded.RotateRight, 9),
                    Triple("Растворение", Icons.Rounded.BlurOn, 10),
                    Triple("Куб", Icons.Rounded.ViewInAr, 11),
                    Triple("Диагональ", Icons.Rounded.NorthEast, 12),
                    Triple("Подъём", Icons.Rounded.VerticalAlignTop, 13),
                    Triple("Фокус", Icons.Rounded.CenterFocusStrong, 14),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    playerEnterOptions.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (lbl, ico, idx) ->
                                EnterAnimCard(Modifier.weight(1f), lbl, ico, idx, settings.playerEnterAnim == idx, c, font, settings.animParams) {
                                    viewModel.updateSettings(settings.copy(playerEnterAnim = idx))
                                }
                            }
                            if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                AnimParamsPanel(
                    title = "Параметры открытия плеера",
                    config = AnimParamConfig(
                        speed = settings.animParams.playerEnterSpeed,
                        easingIdx = settings.animParams.playerEnterEasing,
                        dampingIdx = settings.animParams.playerEnterDamping,
                        stiffnessIdx = settings.animParams.playerEnterStiffness
                    ),
                    c = c, font = font
                ) { cfg ->
                    viewModel.updateSettings(settings.copy(animParams = settings.animParams.copy(
                        playerEnterSpeed = cfg.speed,
                        playerEnterEasing = cfg.easingIdx,
                        playerEnterDamping = cfg.dampingIdx,
                        playerEnterStiffness = cfg.stiffnessIdx
                    )))
                }
            }

            // ── 2. Переходы между экранами ────────────────────────────────────
            AnimCard("Переходы экранов", Icons.Rounded.SwapHoriz, c, font) {
                Text("Анимация при переключении разделов", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                Text("Карточки ниже показывают мини-пример перехода", color = c.textDisabled, fontFamily = font, fontSize = 10.sp)
                val transOptions = listOf(
                    Triple("Material Slide", Icons.Rounded.ChevronRight, 0),
                    Triple("iOS Push", Icons.Rounded.ArrowForwardIos, 1),
                    Triple("Fade Cross", Icons.Rounded.Flare, 2),
                    Triple("Zoom In/Out", Icons.Rounded.ZoomIn, 3),
                    Triple("Вертикаль", Icons.Rounded.SwapVert, 4),
                    Triple("Флип H", Icons.Rounded.Flip, 5),
                    Triple("Флип V", Icons.Rounded.FlipCameraAndroid, 6),
                    Triple("Пружина", Icons.Rounded.FitnessCenter, 7),
                    Triple("Параллакс", Icons.Rounded.Layers, 8),
                    Triple("Аккордеон", Icons.Rounded.ViewWeek, 9),
                    Triple("Глубина", Icons.Rounded.FilterVintage, 10),
                    Triple("Рябь", Icons.Rounded.Waves, 11),
                    Triple("Куб 3D", Icons.Rounded.ViewInAr, 12),
                    Triple("Шторка", Icons.Rounded.ViewAgenda, 13),
                    Triple("Взрыв", Icons.Rounded.Brightness7, 14),
                    Triple("Диагональ", Icons.Rounded.NorthEast, 15),
                    Triple("Стек", Icons.Rounded.Layers, 16),
                    Triple("Подъём", Icons.Rounded.VerticalAlignTop, 17),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    transOptions.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (lbl, ico, idx) ->
                                ScreenTransitionCard(Modifier.weight(1f), lbl, ico, idx, settings.screenTransitionAnim == idx, c, font, settings.animParams) {
                                    viewModel.updateSettings(settings.copy(screenTransitionAnim = idx))
                                }
                            }
                            if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                AnimParamsPanel(
                    title = "Параметры переходов экранов",
                    config = AnimParamConfig(
                        speed = settings.animParams.screenTransSpeed,
                        easingIdx = settings.animParams.screenTransEasing,
                        dampingIdx = settings.animParams.screenTransDamping,
                        stiffnessIdx = settings.animParams.screenTransStiffness
                    ),
                    c = c, font = font
                ) { cfg ->
                    viewModel.updateSettings(settings.copy(animParams = settings.animParams.copy(
                        screenTransSpeed = cfg.speed,
                        screenTransEasing = cfg.easingIdx,
                        screenTransDamping = cfg.dampingIdx,
                        screenTransStiffness = cfg.stiffnessIdx
                    )))
                }
            }

            // ── 3. Обложка альбома ────────────────────────────────────────────
            AnimCard("Обложка альбома", Icons.Rounded.Album, c, font) {
                Text("Анимация обложки в плеере", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                val artOptions = listOf(
                    Triple("Нет", Icons.Rounded.Block, 0),
                    Triple("Вращение", Icons.Rounded.RotateRight, 1),
                    Triple("Пульс", Icons.Rounded.FavoriteBorder, 2),
                    Triple("Дыхание", Icons.Rounded.AirplanemodeActive, 3),
                    Triple("Параллакс", Icons.Rounded.Layers, 4),
                    Triple("Vinyl", Icons.Rounded.Album, 5),
                    Triple("Glitch", Icons.Rounded.BrokenImage, 6),
                    Triple("Качели", Icons.Rounded.Cached, 7),
                    Triple("Орбита", Icons.Rounded.AutoAwesome, 8),
                    Triple("Волна", Icons.Rounded.GraphicEq, 9),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    artOptions.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (lbl, ico, idx) ->
                                AlbumArtAnimCard(Modifier.weight(1f), lbl, ico, idx, settings.albumArtAnim == idx, c, font, settings.animParams) {
                                    viewModel.updateSettings(settings.copy(albumArtAnim = idx))
                                }
                            }
                            if (row.size < 4) repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                AnimParamsPanel(
                    title = "Параметры анимации обложки",
                    config = AnimParamConfig(
                        speed = settings.animParams.albumArtSpeed,
                        easingIdx = settings.animParams.albumArtEasing,
                        dampingIdx = settings.animParams.albumArtDamping
                    ),
                    showSpring = true, c = c, font = font
                ) { cfg ->
                    viewModel.updateSettings(settings.copy(animParams = settings.animParams.copy(
                        albumArtSpeed = cfg.speed,
                        albumArtEasing = cfg.easingIdx,
                        albumArtDamping = cfg.dampingIdx
                    )))
                }
            }

            // ── 4. Список треков ─────────────────────────────────────────────
            AnimCard("Список треков", Icons.Rounded.List, c, font) {
                AnimToggleRow("Автопрокрутка к треку", "Прокручивать список к текущему треку",
                    settings.listScrollAnim, c, font) { viewModel.updateSettings(settings.copy(listScrollAnim = it)) }
                HorizontalDivider(color = c.divider.copy(0.4f))
                AnimToggleRow("Анимация нажатия строки", "Эффект сжатия при нажатии на трек",
                    settings.rowPressAnim, c, font) { viewModel.updateSettings(settings.copy(rowPressAnim = it)) }
                HorizontalDivider(color = c.divider.copy(0.4f))
                RowPressPreview(c, font, settings.rowPressAnim)
                HorizontalDivider(color = c.divider.copy(0.4f))
                Text("Появление элементов списка", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                val listEntryOptions = listOf(
                    Triple("Нет", Icons.Rounded.Block, 0),
                    Triple("Слайд", Icons.Rounded.ChevronRight, 1),
                    Triple("Fade", Icons.Rounded.Flare, 2),
                    Triple("Пружина", Icons.Rounded.FitnessCenter, 3),
                    Triple("Каскад", Icons.Rounded.FormatListNumbered, 4),
                    Triple("Откат", Icons.Rounded.Undo, 5),
                    Triple("Подъём", Icons.Rounded.VerticalAlignTop, 6),
                    Triple("Глубина", Icons.Rounded.CenterFocusStrong, 7),
                    Triple("Liquid", Icons.Rounded.Opacity, 8),
                    Triple("Mist", Icons.Rounded.BlurOn, 9),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listEntryOptions.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (lbl, ico, idx) ->
                                ListEntryAnimCard(Modifier.weight(1f), lbl, ico, idx, settings.listItemEntryAnim == idx, c, font, settings.animParams) {
                                    viewModel.updateSettings(settings.copy(listItemEntryAnim = idx))
                                }
                            }
                            if (row.size < 4) repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                AnimParamsPanel(
                    title = "Параметры появления элементов",
                    config = AnimParamConfig(
                        speed = settings.animParams.listItemSpeed,
                        easingIdx = settings.animParams.listItemEasing,
                        dampingIdx = settings.animParams.listItemDamping,
                        extra1 = settings.animParams.listItemDelay,
                        extra1Label = "Каскадная задержка"
                    ),
                    showSpring = true, extra1Visible = true, c = c, font = font
                ) { cfg ->
                    viewModel.updateSettings(settings.copy(animParams = settings.animParams.copy(
                        listItemSpeed = cfg.speed,
                        listItemEasing = cfg.easingIdx,
                        listItemDamping = cfg.dampingIdx,
                        listItemDelay = cfg.extra1
                    )))
                }
                HorizontalDivider(color = c.divider.copy(0.4f))
                Text("Удаление треков", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                Text("Нажмите на карточку, чтобы заново проиграть эффект удаления", color = c.textDisabled, fontFamily = font, fontSize = 10.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeleteAnimStyleCard(Modifier.weight(1f), "Растворение", Icons.Rounded.Opacity, 0, settings.trackDeleteAnimStyle == 0, c, font, settings.animParams) {
                        viewModel.updateSettings(settings.copy(trackDeleteAnimStyle = 0))
                    }
                    DeleteAnimStyleCard(Modifier.weight(1f), "Пыль", Icons.Rounded.BlurOn, 1, settings.trackDeleteAnimStyle == 1, c, font, settings.animParams) {
                        viewModel.updateSettings(settings.copy(trackDeleteAnimStyle = 1))
                    }
                    DeleteAnimStyleCard(Modifier.weight(1f), "Snap", Icons.Rounded.FlashOn, 2, settings.trackDeleteAnimStyle == 2, c, font, settings.animParams) {
                        viewModel.updateSettings(settings.copy(trackDeleteAnimStyle = 2))
                    }
                }
                AnimParamsPanel(
                    title = "Параметры удаления",
                    config = AnimParamConfig(
                        speed = settings.animParams.deleteAnimSpeed,
                        easingIdx = 0,
                        dampingIdx = 0,
                        extra1 = settings.animParams.deleteScatter,
                        extra1Label = "Разлёт"
                    ),
                    extra1Visible = true, c = c, font = font
                ) { cfg ->
                    viewModel.updateSettings(settings.copy(animParams = settings.animParams.copy(
                        deleteAnimSpeed = cfg.speed,
                        deleteScatter = cfg.extra1
                    )))
                }
                AnimSliderRow("Плотность частиц", "${(settings.animParams.deleteParticleDensity * 100).toInt()}%", settings.animParams.deleteParticleDensity, 0.6f..1.6f, c, font) {
                    viewModel.updateSettings(settings.copy(animParams = settings.animParams.copy(deleteParticleDensity = it)))
                }
            }

            // ── 5. Мини-плеер ────────────────────────────────────────────────
            AnimCard("Мини-плеер", Icons.Rounded.QueueMusic, c, font) {
                Text("Анимация появления мини-плеера", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                val miniOptions = listOf(
                    Triple("Снизу", Icons.Rounded.KeyboardArrowUp, 0),
                    Triple("Пружина", Icons.Rounded.FitnessCenter, 1),
                    Triple("Fade", Icons.Rounded.Flare, 2),
                    Triple("Рост", Icons.Rounded.ZoomIn, 3),
                    Triple("Вращение", Icons.Rounded.RotateRight, 4),
                    Triple("Шторка", Icons.Rounded.ViewAgenda, 5),
                    Triple("Диагональ", Icons.Rounded.NorthEast, 6),
                    Triple("Glass Rise", Icons.Rounded.Opacity, 7),
                    Triple("Telegram Float", Icons.Rounded.Waves, 8),
                    Triple("Mist Flow", Icons.Rounded.BlurOn, 9),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    miniOptions.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (lbl, ico, idx) ->
                                MiniPlayerAnimCard(Modifier.weight(1f), lbl, ico, idx, settings.miniPlayerEntryAnim == idx, c, font, settings.animParams) {
                                    viewModel.updateSettings(settings.copy(miniPlayerEntryAnim = idx))
                                }
                            }
                            if (row.size < 4) repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                AnimParamsPanel(
                    title = "Параметры мини-плеера",
                    config = AnimParamConfig(
                        speed = settings.animParams.miniPlayerSpeed,
                        easingIdx = settings.animParams.miniPlayerEasing,
                        dampingIdx = settings.animParams.miniPlayerDamping,
                        extra1 = settings.animParams.miniPlayerFloatiness,
                        extra1Label = "Плавучесть"
                    ),
                    showSpring = true, extra1Visible = true, c = c, font = font
                ) { cfg ->
                    viewModel.updateSettings(settings.copy(animParams = settings.animParams.copy(
                        miniPlayerSpeed = cfg.speed,
                        miniPlayerEasing = cfg.easingIdx,
                        miniPlayerDamping = cfg.dampingIdx,
                        miniPlayerFloatiness = cfg.extra1
                    )))
                }
            }

            // ── 6. Переключение вкладок ───────────────────────────────────────
            AnimCard("Переключение вкладок", Icons.Rounded.Tab, c, font) {
                Text("Анимация при смене вкладки навигации", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                val tabOptions = listOf(
                    Triple("Слайд", Icons.Rounded.ChevronRight, 0),
                    Triple("Fade", Icons.Rounded.Flare, 1),
                    Triple("Масштаб", Icons.Rounded.ZoomIn, 2),
                    Triple("Переворот", Icons.Rounded.Flip, 3),
                    Triple("Морф", Icons.Rounded.Transform, 4),
                    Triple("Параллакс", Icons.Rounded.Layers, 5),
                    Triple("Шторка", Icons.Rounded.ViewAgenda, 6),
                    Triple("Глубина", Icons.Rounded.FilterVintage, 7),
                    Triple("Liquid Glass", Icons.Rounded.Opacity, 8),
                    Triple("Telegram Flow", Icons.Rounded.Waves, 9),
                    Triple("Elastic", Icons.Rounded.Compress, 10),
                    Triple("Mist", Icons.Rounded.BlurOn, 11),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabOptions.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (lbl, ico, idx) ->
                                TabAnimCard(Modifier.weight(1f), lbl, ico, idx, settings.tabSwitchAnim == idx, c, font, settings.animParams) {
                                    viewModel.updateSettings(settings.copy(tabSwitchAnim = idx))
                                }
                            }
                            if (row.size < 4) repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                AnimParamsPanel(
                    title = "Параметры переключения вкладок",
                    config = AnimParamConfig(
                        speed = settings.animParams.tabSwitchSpeed,
                        easingIdx = settings.animParams.tabSwitchEasing,
                        dampingIdx = settings.animParams.tabSwitchDamping,
                        stiffnessIdx = settings.animParams.tabSwitchStiffness,
                        extra1 = settings.animParams.tabFollowThrough,
                        extra1Label = "Следование"
                    ),
                    extra1Visible = true, c = c, font = font
                ) { cfg ->
                    viewModel.updateSettings(settings.copy(animParams = settings.animParams.copy(
                        tabSwitchSpeed = cfg.speed,
                        tabSwitchEasing = cfg.easingIdx,
                        tabSwitchDamping = cfg.dampingIdx,
                        tabSwitchStiffness = cfg.stiffnessIdx,
                        tabFollowThrough = cfg.extra1
                    )))
                }
                HorizontalDivider(color = c.divider.copy(0.4f))
                Text("Кроссфейд между библиотекой и поиском", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                TabCrossfadeSection(
                    enabled = settings.tabCrossfadeEnabled,
                    durationMs = settings.tabCrossfadeDurationMs,
                    onEnabledChange = { viewModel.updateSettings(settings.copy(tabCrossfadeEnabled = it)) },
                    onDurationChange = { viewModel.updateSettings(settings.copy(tabCrossfadeDurationMs = it)) },
                    c = c, font = font
                )
            }

            // ── 7. Эффект кнопок ─────────────────────────────────────────────
            AnimCard("Эффект кнопок", Icons.Rounded.TouchApp, c, font) {
                Text("Визуальный отклик при нажатии на кнопки", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                val btnOptions = listOf(
                    Triple("Стандарт", Icons.Rounded.TouchApp, 0),
                    Triple("Расширение", Icons.Rounded.OpenWith, 1),
                    Triple("Свечение", Icons.Rounded.LightMode, 2),
                    Triple("Отдача", Icons.Rounded.OfflineBolt, 3),
                    Triple("Всплеск", Icons.Rounded.BlurOn, 4),
                    Triple("Tilt", Icons.Rounded.Transform, 5),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    btnOptions.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (lbl, ico, idx) ->
                                ButtonPressCard(Modifier.weight(1f), lbl, ico, idx, settings.buttonPressStyle == idx, c, font, settings.animParams) {
                                    viewModel.updateSettings(settings.copy(buttonPressStyle = idx))
                                }
                            }
                            if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                AnimParamsPanel(
                    title = "Параметры эффекта кнопок",
                    config = AnimParamConfig(
                        speed = settings.animParams.buttonSpeed,
                        easingIdx = 0,
                        dampingIdx = 0,
                        extra1 = settings.animParams.buttonStrength,
                        extra1Label = "Сила эффекта"
                    ),
                    showSpring = false, extra1Visible = true, c = c, font = font
                ) { cfg ->
                    viewModel.updateSettings(settings.copy(animParams = settings.animParams.copy(
                        buttonSpeed = cfg.speed,
                        buttonStrength = cfg.extra1
                    )))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Карточки выбора режима открытия ──────────────────────────────────────────

@Composable
private fun OpenModeCard(modifier: Modifier, label: String, isSelected: Boolean, c: ColorScheme,
    font: FontFamily?, onSelect: () -> Unit, preview: @Composable (Boolean) -> Unit) {
    var trigger by remember { mutableStateOf(true) }
    var replayKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { trigger = false; delay(60); trigger = true }
    LaunchedEffect(replayKey) { if (replayKey == 0) return@LaunchedEffect; trigger = false; delay(60); trigger = true }
    val borderColor by animateColorAsState(if (isSelected) c.accent else c.divider.copy(0.4f), tween(200), "omBorder")
    val bgColor by animateColorAsState(if (isSelected) c.accent.copy(0.1f) else Color.Transparent, tween(200), "omBg")
    Surface(
        modifier = modifier.clickable { onSelect(); replayKey++ },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .width(70.dp)
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(5) {
                        Row(
                            Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier.height(3.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(2.dp))
                                    .background(c.textDisabled.copy(0.32f))
                            )
                        }
                    }
                }
                preview(trigger)
            }
            Text(label, color = if (isSelected) c.accent else c.textSecondary, fontFamily = font,
                fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
            if (isSelected) Icon(Icons.Rounded.CheckCircle, null, tint = c.accent, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun OpenImmediatePreview(c: ColorScheme, trigger: Boolean) {
    val offsetY by animateFloatAsState(if (trigger) 0f else 1f,
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium), label = "oiY")
    Box(
        Modifier.fillMaxSize().graphicsLayer { translationY = size.height * offsetY }
            .background(Brush.verticalGradient(listOf(c.accent.copy(0.18f), MaterialTheme.colorScheme.surfaceContainerHighest)))
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(c.bgElevated))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.height(3.dp).width(40.dp).clip(RoundedCornerShape(2.dp)).background(c.textPrimary.copy(0.7f)))
            Spacer(Modifier.height(2.dp))
            Box(Modifier.height(2.dp).width(24.dp).clip(RoundedCornerShape(2.dp)).background(c.textSecondary.copy(0.5f)))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(c.bgElevated))
                Box(Modifier.size(20.dp).clip(CircleShape).background(c.accent))
                Box(Modifier.size(14.dp).clip(CircleShape).background(c.bgElevated))
            }
        }
    }
}

@Composable
private fun MiniPlayerPreview(c: ColorScheme, trigger: Boolean) {
    val offsetY by animateFloatAsState(if (trigger) 0f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "mpY")
    Box(Modifier.fillMaxWidth().height(22.dp)
        .graphicsLayer { translationY = size.height * offsetY }
        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        .background(Brush.horizontalGradient(listOf(c.accent.copy(0.18f), MaterialTheme.colorScheme.surfaceContainerHigh)))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(c.bgElevated))
            Box(Modifier.height(2.dp).width(22.dp).clip(RoundedCornerShape(1.dp)).background(c.textPrimary.copy(0.7f)))
            Icon(Icons.Rounded.PlayArrow, null, tint = c.accent, modifier = Modifier.size(12.dp))
        }
    }
}

// ── Базовый компонент карточки анимации ──────────────────────────────────────

@Composable
private fun AnimSelectCard(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    c: ColorScheme,
    font: FontFamily?,
    onSelect: () -> Unit,
    previewContent: @Composable BoxScope.(trigger: Boolean, isSelected: Boolean) -> Unit
) {
    var trigger by remember { mutableStateOf(true) }
    var replayKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(isSelected) {
        if (!isSelected) {
            trigger = true
            return@LaunchedEffect
        }
        while (true) {
            trigger = false
            delay(50)
            trigger = true
            delay(1450)
        }
    }

    LaunchedEffect(replayKey) {
        if (replayKey == 0) return@LaunchedEffect
        trigger = false
        delay(60)
        trigger = true
    }

    val borderColor by animateColorAsState(if (isSelected) c.accent else c.divider.copy(0.35f), tween(180), "asCard")
    val bgColor by animateColorAsState(if (isSelected) c.accent.copy(0.12f) else Color.Transparent, tween(180), "asBg")
    Surface(
        modifier = modifier.clickable {
            onSelect()
            replayKey++
        },
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                previewContent(trigger, isSelected)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (isSelected) c.accent else c.textDisabled, modifier = Modifier.size(11.dp))
                Text(label, color = if (isSelected) c.accent else c.textSecondary, fontFamily = font,
                    fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlayerPanelContent(c: ColorScheme) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(c.accent.copy(0.18f), MaterialTheme.colorScheme.surfaceContainerHigh))
        )
    ) {
        Column(Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).background(c.bgElevated))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.height(2.dp).width(32.dp).clip(RoundedCornerShape(1.dp)).background(c.textPrimary.copy(0.7f)))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.bgElevated))
                Box(Modifier.size(14.dp).clip(CircleShape).background(c.accent.copy(0.8f)))
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.bgElevated))
            }
        }
    }
}

private fun previewTweenMs(baseMs: Int, speed: Float): Int =
    (baseMs / speed.coerceIn(0.2f, 4f)).toInt().coerceAtLeast(50)

private fun previewEasing(idx: Int): Easing = when (idx) {
    1 -> FastOutSlowInEasing
    2 -> FastOutLinearInEasing
    3 -> LinearEasing
    4 -> CubicBezierEasing(0.2f, 1.25f, 0.38f, 1f)
    5 -> CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    else -> FastOutSlowInEasing
}

private fun previewDamping(idx: Int): Float = when (idx) {
    1 -> Spring.DampingRatioLowBouncy
    2 -> Spring.DampingRatioHighBouncy
    3 -> Spring.DampingRatioNoBouncy
    else -> Spring.DampingRatioMediumBouncy
}

private fun previewStiffness(idx: Int): Float = when (idx) {
    1 -> Spring.StiffnessMediumLow
    2 -> Spring.StiffnessHigh
    3 -> Spring.StiffnessLow
    else -> Spring.StiffnessMedium
}

private fun previewSpring(dampingIdx: Int, stiffnessIdx: Int): SpringSpec<Float> =
    spring(
        dampingRatio = previewDamping(dampingIdx),
        stiffness = previewStiffness(stiffnessIdx)
    )

// ── 1. Анимации открытия плеера (15 вариантов) ───────────────────────────────

@Composable
private fun EnterAnimCard(modifier: Modifier, label: String, icon: ImageVector, animIndex: Int,
    isSelected: Boolean, c: ColorScheme, font: FontFamily?, animParams: AnimParams = AnimParams(), onSelect: () -> Unit) {
    AnimSelectCard(modifier, label, icon, isSelected, c, font, onSelect) { trigger, _ ->
        PlayerPanelAnimExtended(animIndex, trigger, c, animParams)
    }
}

@Composable
private fun PlayerPanelAnimExtended(animIndex: Int, trigger: Boolean, c: ColorScheme, animParams: AnimParams) {
    val easing = previewEasing(animParams.playerEnterEasing)
    val springSpec = previewSpring(animParams.playerEnterDamping, animParams.playerEnterStiffness)
    val dur = previewTweenMs(340, animParams.playerEnterSpeed)
    val shortDur = previewTweenMs(220, animParams.playerEnterSpeed)
    val longDur = previewTweenMs(400, animParams.playerEnterSpeed)
    when (animIndex) {
        0 -> {
            val offsetY by animateFloatAsState(if (trigger) 0f else 1f, springSpec, label = "p0")
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = size.height * offsetY }) { PlayerPanelContent(c) }
        }
        1 -> {
            val scale by animateFloatAsState(if (trigger) 1f else 0.3f, springSpec, label = "p1s")
            val alpha by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "p1a")
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }) { PlayerPanelContent(c) }
        }
        2 -> {
            val alpha by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "p2a")
            Box(Modifier.fillMaxSize().alpha(alpha)) { PlayerPanelContent(c) }
        }
        3 -> {
            val offsetX by animateFloatAsState(if (trigger) 0f else -1f, springSpec, label = "p3x")
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * offsetX }) { PlayerPanelContent(c) }
        }
        4 -> {
            val rot by animateFloatAsState(if (trigger) 0f else -90f, tween(dur, easing = easing), label = "p4r")
            val alpha by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "p4a")
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = rot; this.alpha = alpha }) { PlayerPanelContent(c) }
        }
        5 -> {
            val offsetY by animateFloatAsState(if (trigger) 0f else 1.5f, springSpec, label = "p5")
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = size.height * offsetY }) { PlayerPanelContent(c) }
        }
        6 -> {
            val scaleY by animateFloatAsState(if (trigger) 1f else 0.1f, springSpec, label = "p6sy")
            val scaleX by animateFloatAsState(if (trigger) 1f else 1.4f, springSpec, label = "p6sx")
            Box(Modifier.fillMaxSize().graphicsLayer { this.scaleY = scaleY; this.scaleX = scaleX }) { PlayerPanelContent(c) }
        }
        7 -> {
            val scale by animateFloatAsState(if (trigger) 1f else 2f, springSpec, label = "p7sc")
            val alpha by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "p7a")
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }) { PlayerPanelContent(c) }
        }
        8 -> {
            val h by animateFloatAsState(if (trigger) 1f else 0f, tween(longDur, easing = easing), label = "p8h")
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(h.coerceAtLeast(0.01f)).clip(RoundedCornerShape(9.dp))) {
                    PlayerPanelContent(c)
                }
            }
        }
        9 -> {
            val offsetX by animateFloatAsState(if (trigger) 0f else 1.2f, springSpec, label = "p9x")
            val rot by animateFloatAsState(if (trigger) 0f else 30f, springSpec, label = "p9r")
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * offsetX; rotationZ = rot }) { PlayerPanelContent(c) }
        }
        10 -> {
            val scale by animateFloatAsState(if (trigger) 1f else 1.1f, tween(longDur, easing = easing), label = "p10sc")
            val alpha by animateFloatAsState(if (trigger) 1f else 0f, tween(longDur, easing = easing), label = "p10a")
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }) { PlayerPanelContent(c) }
        }
        11 -> {
            val rot by animateFloatAsState(if (trigger) 0f else -90f, tween(longDur, easing = easing), label = "p11r")
            val alpha by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "p11a")
            Box(Modifier.fillMaxSize().graphicsLayer { rotationX = rot; this.alpha = alpha }) { PlayerPanelContent(c) }
        }
        12 -> {
            val offsetX by animateFloatAsState(if (trigger) 0f else 0.24f, tween(dur, easing = easing), label = "p12x")
            val offsetY by animateFloatAsState(if (trigger) 0f else 0.28f, tween(dur, easing = easing), label = "p12y")
            val alpha by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "p12a")
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = size.width * offsetX
                translationY = size.height * offsetY
                this.alpha = alpha
            }) { PlayerPanelContent(c) }
        }
        13 -> {
            val offsetY by animateFloatAsState(if (trigger) 0f else 0.35f, tween(dur, easing = easing), label = "p13y")
            val scale by animateFloatAsState(if (trigger) 1f else 0.88f, tween(dur, easing = easing), label = "p13s")
            val alpha by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "p13a")
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationY = size.height * offsetY
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
                this.alpha = alpha
            }) { PlayerPanelContent(c) }
        }
        14 -> {
            val scale by animateFloatAsState(if (trigger) 1f else 0.72f, tween(longDur, easing = easing), label = "p14s")
            val alpha by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "p14a")
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.35f)
                this.alpha = alpha
            }) { PlayerPanelContent(c) }
        }
        else -> PlayerPanelContent(c)
    }
}

// ── 2. Переходы экранов (18 вариантов) ───────────────────────────────────────

@Composable
private fun ScreenTransitionCard(modifier: Modifier, label: String, icon: ImageVector, idx: Int,
    isSelected: Boolean, c: ColorScheme, font: FontFamily?, animParams: AnimParams = AnimParams(), onSelect: () -> Unit) {
    AnimSelectCard(modifier, label, icon, isSelected, c, font, onSelect) { trigger, _ ->
        ScreenTransitionPreview(idx, trigger, c, animParams)
    }
}

@Composable
private fun BoxScope.ScreenTransitionPreview(idx: Int, trigger: Boolean, c: ColorScheme, animParams: AnimParams) {
    val easing = previewEasing(animParams.screenTransEasing)
    val springSpec = previewSpring(animParams.screenTransDamping, animParams.screenTransStiffness)
    val dur = previewTweenMs(340, animParams.screenTransSpeed)
    val shortDur = previewTweenMs(220, animParams.screenTransSpeed)
    Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)).background(c.bgElevated)) }
    }
    val newScreenContent: @Composable BoxScope.() -> Unit = {
        repeat(2) { i ->
            Box(Modifier.padding(top = (10 + i * 15).dp, start = 5.dp, end = 5.dp).fillMaxWidth().height(8.dp)
                .clip(RoundedCornerShape(3.dp)).background(c.accent.copy(0.55f)))
        }
    }
    when (idx) {
        0 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 0.3f, tween(dur, easing = easing), label = "st0x")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "st0a")
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * ox; this.alpha = a }
                .background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        1 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 1f, tween(dur, easing = easing), label = "st1x")
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * ox }
                .background(c.bgCard.copy(0.97f)), content = newScreenContent)
        }
        2 -> {
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "st2a")
            Box(Modifier.fillMaxSize().alpha(a).background(Brush.verticalGradient(listOf(c.accent.copy(0.8f), c.bgCard))),
                content = newScreenContent)
        }
        3 -> {
            val sc by animateFloatAsState(if (trigger) 1f else 0.5f, springSpec, label = "st3sc")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "st3a")
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = a }
                .background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        4 -> {
            val oy by animateFloatAsState(if (trigger) 0f else 1f, tween(dur, easing = easing), label = "st4y")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "st4a")
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = size.height * oy; this.alpha = a }
                .background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        5 -> {
            val rot by animateFloatAsState(if (trigger) 0f else 90f, tween(dur, easing = easing), label = "st5r")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "st5a")
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = rot; this.alpha = a }
                .background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        6 -> {
            val rot by animateFloatAsState(if (trigger) 0f else -90f, tween(dur, easing = easing), label = "st6r")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "st6a")
            Box(Modifier.fillMaxSize().graphicsLayer { rotationX = rot; this.alpha = a }
                .background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        7 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 0.4f, springSpec, label = "st7x")
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * ox }
                .background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        8 -> {
            val ox1 by animateFloatAsState(if (trigger) 0f else 0.5f, tween(dur, easing = easing), label = "st8x1")
            val ox2 by animateFloatAsState(if (trigger) 0f else 0.15f, tween(dur, easing = easing), label = "st8x2")
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * ox2 }.background(c.bgCard.copy(0.88f)))
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * ox1 }, content = newScreenContent)
        }
        9 -> {
            val sx by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "st9sx")
            Box(Modifier.fillMaxSize().graphicsLayer {
                    scaleX = sx; transformOrigin = TransformOrigin(0f, 0.5f)
                }.background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        10 -> {
            val sc by animateFloatAsState(if (trigger) 1f else 1.3f, tween(dur, easing = easing), label = "st10sc")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "st10a")
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = a }
                .background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        11 -> {
            val sc by animateFloatAsState(if (trigger) 1f else 0.85f, springSpec, label = "st11sc")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "st11a")
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = a }
                .background(Brush.radialGradient(listOf(c.accent.copy(0.6f), c.bgCard))), content = newScreenContent)
        }
        12 -> {
            val rot by animateFloatAsState(if (trigger) 0f else -90f, tween(dur, easing = easing), label = "st12r")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "st12a")
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = rot; cameraDistance = 12f; this.alpha = a }
                .background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        13 -> {
            val h by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "st13h")
            Box(Modifier.fillMaxWidth().fillMaxHeight(h.coerceAtLeast(0.01f)).align(Alignment.TopCenter)
                .background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        14 -> {
            val sc by animateFloatAsState(if (trigger) 1f else 2.5f, springSpec, label = "st14sc")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "st14a")
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = a }
                .background(Brush.radialGradient(listOf(c.accent.copy(0.9f), c.bgCard))), content = newScreenContent)
        }
        15 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 0.2f, tween(dur, easing = easing), label = "st15x")
            val oy by animateFloatAsState(if (trigger) 0f else 0.12f, tween(dur, easing = easing), label = "st15y")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "st15a")
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = size.width * ox
                translationY = size.height * oy
                this.alpha = a
            }.background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        16 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 0.14f, tween(dur, easing = easing), label = "st16x")
            val sc by animateFloatAsState(if (trigger) 1f else 0.94f, tween(dur, easing = easing), label = "st16s")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "st16a")
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = size.width * ox
                scaleX = sc
                scaleY = sc
                this.alpha = a
            }.background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
        17 -> {
            val oy by animateFloatAsState(if (trigger) 0f else 0.24f, tween(dur, easing = easing), label = "st17y")
            val sc by animateFloatAsState(if (trigger) 1f else 0.98f, tween(dur, easing = easing), label = "st17s")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(shortDur, easing = easing), label = "st17a")
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationY = size.height * oy
                scaleX = sc
                scaleY = sc
                transformOrigin = TransformOrigin(0.5f, 1f)
                this.alpha = a
            }.background(c.bgCard.copy(0.95f)), content = newScreenContent)
        }
    }
}

// ── 3. Анимация обложки (8 вариантов) ────────────────────────────────────────

@Composable
private fun AlbumArtAnimCard(modifier: Modifier, label: String, icon: ImageVector, idx: Int,
    isSelected: Boolean, c: ColorScheme, font: FontFamily?, animParams: AnimParams = AnimParams(), onSelect: () -> Unit) {
    AnimSelectCard(modifier, label, icon, isSelected, c, font, onSelect) { trigger, selected ->
        AlbumArtAnimPreview(idx, trigger, selected, c, animParams)
    }
}

@Composable
private fun StaticAlbumArtPreview(idx: Int, c: ColorScheme) {
    when (idx) {
        0 -> Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(c.accent.copy(0.7f), c.accentMuted)))
        )
        1 -> Box(Modifier.size(40.dp).clip(CircleShape)
            .background(Brush.sweepGradient(listOf(c.accent.copy(0.8f), c.accentMuted, c.accent.copy(0.8f))))) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.bgDeep).align(Alignment.Center))
        }
        2 -> Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(c.accent.copy(0.8f), c.accentMuted)))
        )
        3 -> Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .background(Brush.radialGradient(listOf(c.accent, c.accentMuted)))
        )
        4 -> Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(c.accent.copy(0.7f), c.bgCard, c.accentMuted)))
        )
        5 -> Box(Modifier.size(44.dp).clip(CircleShape).background(c.bgElevated)) {
            Box(Modifier.size(40.dp).clip(CircleShape).align(Alignment.Center)
                .background(Brush.sweepGradient(listOf(c.accent.copy(0.6f), c.bgCard, c.accent.copy(0.4f), c.bgCard, c.accent.copy(0.6f)))))
            Box(Modifier.size(12.dp).clip(CircleShape).align(Alignment.Center).background(c.bgDeep))
            Box(Modifier.size(5.dp).clip(CircleShape).align(Alignment.Center).background(c.textDisabled))
        }
        6 -> Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(c.accent.copy(0.8f), c.accentMuted)))) {
            Box(Modifier.fillMaxWidth().height(8.dp).offset(y = 12.dp).background(Color(0x8000FFFF)))
            Box(Modifier.fillMaxWidth().height(5.dp).offset(y = 24.dp).background(Color(0x80FF00FF)))
        }
        7 -> Box(Modifier.size(40.dp).graphicsLayer {
            rotationZ = 6f
            transformOrigin = TransformOrigin(0.5f, 0f)
        }.clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(c.accent.copy(0.8f), c.accentMuted))))
        8 -> Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.radialGradient(listOf(c.accent.copy(0.75f), c.accentMuted))))
            Box(Modifier.size(44.dp).border(BorderStroke(1.dp, c.accent.copy(0.45f)), CircleShape))
            Box(Modifier.align(Alignment.TopCenter).offset(y = (-2).dp).size(8.dp).clip(CircleShape).background(c.accentVar))
        }
        9 -> Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(c.bgCard, c.accent.copy(0.7f))))) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(12.dp, 18.dp, 14.dp).forEach { h ->
                    Box(Modifier.width(5.dp).height(h).clip(RoundedCornerShape(999.dp)).background(c.textPrimary.copy(alpha = 0.72f)))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.AlbumArtAnimPreview(idx: Int, trigger: Boolean, animateMotion: Boolean, c: ColorScheme, animParams: AnimParams) {
    if (!animateMotion) {
        StaticAlbumArtPreview(idx, c)
        return
    }
    val easing = previewEasing(animParams.albumArtEasing)
    val longLoop = previewTweenMs(2200, animParams.albumArtSpeed)
    val mediumLoop = previewTweenMs(1400, animParams.albumArtSpeed)
    val fastLoop = previewTweenMs(700, animParams.albumArtSpeed)
    when (idx) {
        0 -> Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(c.accent.copy(0.7f), c.accentMuted))))
        1 -> {
            val inf = rememberInfiniteTransition(label = "art1")
            val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(previewTweenMs(3000, animParams.albumArtSpeed), easing = LinearEasing)), label = "artRot1")
            Box(Modifier.size(40.dp).graphicsLayer { rotationZ = if (trigger) rot else 0f }
                .clip(CircleShape).background(Brush.sweepGradient(listOf(c.accent.copy(0.8f), c.accentMuted, c.accent.copy(0.8f))))) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.bgDeep).align(Alignment.Center))
            }
        }
        2 -> {
            val inf = rememberInfiniteTransition(label = "art2")
            val sc by inf.animateFloat(1f, 1.12f, infiniteRepeatable(tween(fastLoop, easing = easing), RepeatMode.Reverse), label = "artPulse")
            Box(Modifier.size(40.dp).graphicsLayer { scaleX = if (trigger) sc else 1f; scaleY = if (trigger) sc else 1f }
                .clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(c.accent.copy(0.8f), c.accentMuted))))
        }
        3 -> {
            val inf = rememberInfiniteTransition(label = "art3")
            val sc by inf.animateFloat(0.92f, 1.05f, infiniteRepeatable(tween(mediumLoop, easing = easing), RepeatMode.Reverse), label = "artBreath")
            val a by inf.animateFloat(0.7f, 1f, infiniteRepeatable(tween(mediumLoop, easing = easing), RepeatMode.Reverse), label = "artBAlpha")
            Box(Modifier.size(40.dp).graphicsLayer { scaleX = if (trigger) sc else 1f; scaleY = if (trigger) sc else 1f; this.alpha = if (trigger) a else 1f }
                .clip(RoundedCornerShape(8.dp)).background(Brush.radialGradient(listOf(c.accent, c.accentMuted))))
        }
        4 -> {
            val inf = rememberInfiniteTransition(label = "art4")
            val tx by inf.animateFloat(-4f, 4f, infiniteRepeatable(tween(previewTweenMs(2000, animParams.albumArtSpeed), easing = easing), RepeatMode.Reverse), label = "artParX")
            val ty by inf.animateFloat(-3f, 3f, infiniteRepeatable(tween(previewTweenMs(1700, animParams.albumArtSpeed), easing = easing), RepeatMode.Reverse), label = "artParY")
            Box(Modifier.size(40.dp).graphicsLayer { translationX = if (trigger) tx else 0f; translationY = if (trigger) ty else 0f }
                .clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(c.accent.copy(0.7f), c.bgCard, c.accentMuted))))
        }
        5 -> {
            val inf = rememberInfiniteTransition(label = "art5")
            val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(longLoop, easing = LinearEasing)), label = "artVinyl")
            Box(Modifier.size(44.dp).graphicsLayer { rotationZ = if (trigger) rot else 0f }.clip(CircleShape).background(c.bgElevated)) {
                Box(Modifier.size(40.dp).clip(CircleShape).align(Alignment.Center)
                    .background(Brush.sweepGradient(listOf(c.accent.copy(0.6f), c.bgCard, c.accent.copy(0.4f), c.bgCard, c.accent.copy(0.6f)))))
                Box(Modifier.size(12.dp).clip(CircleShape).align(Alignment.Center).background(c.bgDeep))
                Box(Modifier.size(5.dp).clip(CircleShape).align(Alignment.Center).background(c.textDisabled))
            }
        }
        6 -> {
            val inf = rememberInfiniteTransition(label = "art6")
            val tx by inf.animateFloat(0f, 5f, infiniteRepeatable(tween(previewTweenMs(120, animParams.albumArtSpeed), easing = LinearEasing), RepeatMode.Reverse), label = "artGlitchX")
            val a by inf.animateFloat(0.6f, 1f, infiniteRepeatable(tween(previewTweenMs(180, animParams.albumArtSpeed)), RepeatMode.Reverse), label = "artGlitchA")
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(c.accent.copy(0.8f), c.accentMuted)))) {
                Box(Modifier.fillMaxWidth().height(8.dp).offset(x = if (trigger) tx.dp else 0.dp, y = 12.dp).background(Color(0x8000FFFF)))
                Box(Modifier.fillMaxWidth().height(5.dp).offset(x = if (trigger) (-tx).dp else 0.dp, y = 24.dp)
                    .alpha(if (trigger) a else 1f).background(Color(0x80FF00FF)))
            }
        }
        7 -> {
            val inf = rememberInfiniteTransition(label = "art7")
            val rot by inf.animateFloat(-8f, 8f, infiniteRepeatable(tween(previewTweenMs(1200, animParams.albumArtSpeed), easing = easing), RepeatMode.Reverse), label = "artSwing")
            Box(Modifier.size(40.dp).graphicsLayer {
                    rotationZ = if (trigger) rot else 0f
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }.clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(c.accent.copy(0.8f), c.accentMuted))))
        }
        8 -> {
            val inf = rememberInfiniteTransition(label = "art8")
            val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(previewTweenMs(2200, animParams.albumArtSpeed), easing = LinearEasing)), label = "artOrbit")
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.radialGradient(listOf(c.accent.copy(0.75f), c.accentMuted))))
                Box(Modifier.size(44.dp).border(BorderStroke(1.dp, c.accent.copy(0.45f)), CircleShape))
                Box(
                    Modifier
                        .size(44.dp)
                        .graphicsLayer { rotationZ = if (trigger) rot else 0f }
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-2).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(c.accentVar)
                    )
                }
            }
        }
        9 -> {
            val inf = rememberInfiniteTransition(label = "art9")
            val bob by inf.animateFloat(-3f, 3f, infiniteRepeatable(tween(previewTweenMs(1200, animParams.albumArtSpeed), easing = easing), RepeatMode.Reverse), label = "artWaveBob")
            val stretch by inf.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(previewTweenMs(900, animParams.albumArtSpeed), easing = easing), RepeatMode.Reverse), label = "artWaveStretch")
            Box(
                Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        translationY = if (trigger) bob else 0f
                        scaleX = if (trigger) stretch else 1f
                        scaleY = if (trigger) (2f - stretch) else 1f
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(c.bgCard, c.accent.copy(0.7f))))
            ) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(12.dp, 18.dp, 14.dp).forEach { h ->
                        Box(Modifier.width(5.dp).height(h).clip(RoundedCornerShape(999.dp)).background(c.textPrimary.copy(alpha = 0.72f)))
                    }
                }
            }
        }
    }
}

// ── 4. Анимация элементов списка (6 вариантов) ───────────────────────────────

@Composable
private fun ListEntryAnimCard(modifier: Modifier, label: String, icon: ImageVector, idx: Int,
    isSelected: Boolean, c: ColorScheme, font: FontFamily?, animParams: AnimParams = AnimParams(), onSelect: () -> Unit) {
    AnimSelectCard(modifier, label, icon, isSelected, c, font, onSelect) { trigger, _ ->
        ListEntryAnimPreview(idx, trigger, c, animParams)
    }
}

@Composable
private fun ListEntryAnimPreview(idx: Int, trigger: Boolean, c: ColorScheme, animParams: AnimParams) {
    val dur = previewTweenMs(280, animParams.listItemSpeed)
    val springSpec = previewSpring(animParams.listItemDamping, 0)
    Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(3) { i ->
            val dms = ((i * 70) * animParams.listItemDelay.coerceIn(0.2f, 2f)).toInt()
            when (idx) {
                0 -> ListItemMini(c, alpha = 1f, offsetX = 0f)
                1 -> {
                    val ox by animateFloatAsState(if (trigger) 0f else -60f, tween(dur + dms, easing = FastOutSlowInEasing), label = "li1x$i")
                    val a by animateFloatAsState(if (trigger) 1f else 0f, tween((dur * 0.8f).toInt() + dms), label = "li1a$i")
                    ListItemMini(c, alpha = a, offsetX = ox)
                }
                2 -> {
                    val a by animateFloatAsState(if (trigger) 1f else 0f, tween(dur + dms), label = "li2a$i")
                    ListItemMini(c, alpha = a, offsetX = 0f)
                }
                3 -> {
                    val ox by animateFloatAsState(if (trigger) 0f else -50f, springSpec, label = "li3x$i")
                    val a by animateFloatAsState(if (trigger) 1f else 0f, tween((dur * 0.8f).toInt() + dms), label = "li3a$i")
                    ListItemMini(c, alpha = a, offsetX = ox)
                }
                4 -> {
                    val sc by animateFloatAsState(if (trigger) 1f else 0.7f, springSpec, label = "li4sc$i")
                    val a by animateFloatAsState(if (trigger) 1f else 0f, tween((dur * 0.9f).toInt() + dms), label = "li4a$i")
                    ListItemMini(c, alpha = a, offsetX = 0f, scale = sc)
                }
                5 -> {
                    val ox by animateFloatAsState(if (trigger) 0f else 40f, springSpec, label = "li5x$i")
                    val a by animateFloatAsState(if (trigger) 1f else 0f, tween((dur * 0.85f).toInt() + dms), label = "li5a$i")
                    ListItemMini(c, alpha = a, offsetX = ox)
                }
                6 -> {
                    val oy by animateFloatAsState(if (trigger) 0f else 18f, tween(dur + dms, easing = FastOutSlowInEasing), label = "li6y$i")
                    val a by animateFloatAsState(if (trigger) 1f else 0f, tween((dur * 0.85f).toInt() + dms), label = "li6a$i")
                    ListItemMini(c, alpha = a, offsetX = 0f, offsetY = oy)
                }
                7 -> {
                    val oy by animateFloatAsState(if (trigger) 0f else 10f, springSpec, label = "li7y$i")
                    val sc by animateFloatAsState(if (trigger) 1f else 0.88f, tween(dur + dms, easing = FastOutSlowInEasing), label = "li7s$i")
                    val a by animateFloatAsState(if (trigger) 1f else 0f, tween((dur * 0.9f).toInt() + dms), label = "li7a$i")
                    ListItemMini(c, alpha = a, offsetX = 0f, offsetY = oy, scale = sc)
                }
                8 -> {
                    val ox by animateFloatAsState(if (trigger) 0f else -32f, tween(dur + dms, easing = FastOutSlowInEasing), label = "li8x$i")
                    val sc by animateFloatAsState(if (trigger) 1f else 0.95f, tween(dur + dms, easing = FastOutSlowInEasing), label = "li8s$i")
                    val a by animateFloatAsState(if (trigger) 1f else 0f, tween((dur * 0.9f).toInt() + dms), label = "li8a$i")
                    ListItemMini(c, alpha = a, offsetX = ox, scale = sc)
                }
                9 -> {
                    val sc by animateFloatAsState(if (trigger) 1f else 1.06f, tween(dur + dms, easing = FastOutSlowInEasing), label = "li9s$i")
                    val a by animateFloatAsState(if (trigger) 1f else 0f, tween((dur * 0.95f).toInt() + dms), label = "li9a$i")
                    ListItemMini(c, alpha = a, offsetX = 0f, scale = sc)
                }
            }
        }
    }
}

@Composable
private fun ListItemMini(c: ColorScheme, alpha: Float, offsetX: Float, offsetY: Float = 0f, scale: Float = 1f) {
    Row(Modifier.fillMaxWidth().height(13.dp)
        .graphicsLayer { this.alpha = alpha; translationX = offsetX; translationY = offsetY; scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(4.dp)).background(c.bgElevated),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(c.bgCard))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.height(2.dp).fillMaxWidth(0.55f).clip(RoundedCornerShape(1.dp)).background(c.textPrimary.copy(0.5f)))
    }
}

@Composable
private fun DeleteAnimStyleCard(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    idx: Int,
    isSelected: Boolean,
    c: ColorScheme,
    font: FontFamily?,
    animParams: AnimParams = AnimParams(),
    onSelect: () -> Unit
) {
    AnimSelectCard(modifier, label, icon, isSelected, c, font, onSelect) { trigger, _ ->
        DeleteAnimPreview(idx, trigger, c, animParams)
    }
}

@Composable
private fun DeleteAnimPreview(idx: Int, trigger: Boolean, c: ColorScheme, animParams: AnimParams) {
    val speed = animParams.deleteAnimSpeed.coerceIn(0.45f, 2.2f)
    val scatter = animParams.deleteScatter.coerceIn(0.65f, 1.8f)
    val density = animParams.deleteParticleDensity.coerceIn(0.6f, 1.6f)
    val duration = ((when (idx) {
        0 -> 360f
        2 -> 440f
        else -> 520f
    }) / speed).toInt().coerceIn(180, 700)

    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger, idx, duration) {
        if (trigger) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(duration, easing = LinearOutSlowInEasing))
        } else {
            progress.snapTo(0f)
        }
    }

    val rowAlpha by animateFloatAsState(
        targetValue = if (trigger) 0f else 1f,
        animationSpec = tween((duration * 0.35f).toInt().coerceAtLeast(120), easing = FastOutLinearInEasing),
        label = "deletePreviewAlpha_$idx"
    )
    val rowScale by animateFloatAsState(
        targetValue = if (trigger) when (idx) {
            0 -> 0.98f
            2 -> 0.90f
            else -> 0.94f
        } else 1f,
        animationSpec = tween((duration * 0.40f).toInt().coerceAtLeast(150), easing = FastOutLinearInEasing),
        label = "deletePreviewScale_$idx"
    )

    Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp)) {
        ListItemMini(c, alpha = rowAlpha, offsetX = 0f, scale = rowScale)
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val p = progress.value
            if (p <= 0f) return@Canvas
            val particleCount = (14 * density).roundToInt().coerceIn(8, 24)
            repeat(particleCount) { i ->
                val fraction = i / particleCount.toFloat()
                val delay = when (idx) {
                    0 -> fraction * 0.22f
                    2 -> fraction * 0.42f
                    else -> fraction * 0.30f
                }
                if (p <= delay) return@repeat
                val localT = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
                val startX = size.width * (0.16f + fraction * 0.64f)
                val startY = size.height * 0.34f
                val travelX = when (idx) {
                    0 -> 14f
                    2 -> 34f
                    else -> 26f
                } * scatter
                val travelY = when (idx) {
                    0 -> 5f
                    2 -> 18f
                    else -> 12f
                } * scatter
                val px = startX + travelX * localT + i * 0.8f
                val py = startY + sin((fraction * 6.28f) + localT * 4f) * travelY + localT * localT * if (idx == 0) 8f else 14f
                val alpha = (1f - localT * if (idx == 0) 1.5f else 1.15f).coerceIn(0f, 1f)
                val radius = (1.8f + (1f - localT) * if (idx == 2) 1.8f else 1.2f).coerceAtLeast(0.8f)
                if (alpha > 0.04f) {
                    drawCircle(
                        color = if (idx == 2) c.accent.copy(alpha = alpha) else c.textPrimary.copy(alpha = alpha),
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(px, py)
                    )
                }
            }
        }
    }
}

// ── 5. Анимация мини-плеера (10 вариантов) ───────────────────────────────────

@Composable
private fun MiniPlayerAnimCard(modifier: Modifier, label: String, icon: ImageVector, idx: Int,
    isSelected: Boolean, c: ColorScheme, font: FontFamily?, animParams: AnimParams = AnimParams(), onSelect: () -> Unit) {
    AnimSelectCard(modifier, label, icon, isSelected, c, font, onSelect) { trigger, _ ->
        MiniPlayerAnimPreview(idx, trigger, c, animParams)
    }
}

@Composable
private fun BoxScope.MiniPlayerAnimPreview(idx: Int, trigger: Boolean, c: ColorScheme, animParams: AnimParams) {
    val dur = previewTweenMs(300, animParams.miniPlayerSpeed)
    val easing = previewEasing(animParams.miniPlayerEasing)
    val springSpec = previewSpring(animParams.miniPlayerDamping, 0)
    val floatiness = animParams.miniPlayerFloatiness.coerceIn(0.5f, 1.8f)
    Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { Box(Modifier.fillMaxWidth().height(11.dp).clip(RoundedCornerShape(3.dp)).background(c.bgElevated)) }
    }
    val barMod: Modifier = when (idx) {
        0 -> {
            val oy by animateFloatAsState(if (trigger) 0f else 1f, tween(dur, easing = easing), label = "mp0y")
            Modifier.graphicsLayer { translationY = size.height * oy }
        }
        1 -> {
            val oy by animateFloatAsState(if (trigger) 0f else 1.5f, springSpec, label = "mp1y")
            Modifier.graphicsLayer { translationY = size.height * oy }
        }
        2 -> {
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "mp2a")
            Modifier.alpha(a)
        }
        3 -> {
            val sc by animateFloatAsState(if (trigger) 1f else 0.5f, springSpec, label = "mp3sc")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(200, animParams.miniPlayerSpeed), easing = easing), label = "mp3a")
            Modifier.graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = a; transformOrigin = TransformOrigin(0.5f, 1f) }
        }
        4 -> {
            val rot by animateFloatAsState(if (trigger) 0f else -20f, springSpec, label = "mp4r")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(250, animParams.miniPlayerSpeed), easing = easing), label = "mp4a")
            Modifier.graphicsLayer { rotationX = rot; this.alpha = a }
        }
        5 -> {
            val h by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "mp5h")
            Modifier.graphicsLayer {
                scaleY = h.coerceAtLeast(0.01f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
        }
        6 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 0.35f, tween(dur, easing = easing), label = "mp6x")
            val oy by animateFloatAsState(if (trigger) 0f else 0.65f, tween(dur, easing = easing), label = "mp6y")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(220, animParams.miniPlayerSpeed), easing = easing), label = "mp6a")
            Modifier.graphicsLayer { translationX = size.width * ox; translationY = size.height * oy; this.alpha = a }
        }
        7 -> {
            val oy by animateFloatAsState(if (trigger) 0f else 0.48f * floatiness, tween(dur, easing = easing), label = "mp7y")
            val sc by animateFloatAsState(if (trigger) 1f else 0.92f, springSpec, label = "mp7s")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(240, animParams.miniPlayerSpeed), easing = easing), label = "mp7a")
            Modifier.graphicsLayer { translationY = size.height * oy; scaleX = sc; scaleY = sc; this.alpha = a }
        }
        8 -> {
            val oy by animateFloatAsState(if (trigger) 0f else 0.62f * floatiness, springSpec, label = "mp8y")
            val ox by animateFloatAsState(if (trigger) 0f else 0.10f * floatiness, tween(dur, easing = easing), label = "mp8x")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(220, animParams.miniPlayerSpeed), easing = easing), label = "mp8a")
            Modifier.graphicsLayer { translationY = size.height * oy; translationX = size.width * ox; this.alpha = a }
        }
        9 -> {
            val sc by animateFloatAsState(if (trigger) 1f else 1.08f, tween(dur, easing = easing), label = "mp9s")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(280, animParams.miniPlayerSpeed), easing = easing), label = "mp9a")
            Modifier.graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = a }
        }
        else -> Modifier
    }
    Row(
        modifier = barMod.fillMaxWidth().height(18.dp).align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(Brush.horizontalGradient(listOf(c.accent.copy(0.9f), c.accentMuted.copy(0.8f)))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c.bgElevated))
        Box(Modifier.height(2.dp).width(20.dp).clip(RoundedCornerShape(1.dp)).background(c.textPrimary.copy(0.7f)))
        Icon(Icons.Rounded.PlayArrow, null, tint = c.bgDeep, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
    }
}

// ── 6. Анимация переключения вкладок (12 вариантов) ──────────────────────────

@Composable
private fun TabAnimCard(modifier: Modifier, label: String, icon: ImageVector, idx: Int,
    isSelected: Boolean, c: ColorScheme, font: FontFamily?, animParams: AnimParams = AnimParams(), onSelect: () -> Unit) {
    AnimSelectCard(modifier, label, icon, isSelected, c, font, onSelect) { trigger, _ ->
        TabAnimPreview(idx, trigger, c, animParams)
    }
}

@Composable
private fun BoxScope.TabAnimPreview(idx: Int, trigger: Boolean, c: ColorScheme, animParams: AnimParams) {
    val dur = previewTweenMs(280, animParams.tabSwitchSpeed)
    val easing = previewEasing(animParams.tabSwitchEasing)
    val springSpec = previewSpring(animParams.tabSwitchDamping, animParams.tabSwitchStiffness)
    val follow = animParams.tabFollowThrough.coerceIn(0.6f, 1.8f)
    Row(Modifier.fillMaxWidth().height(16.dp).align(Alignment.BottomCenter)
        .background(c.bgCard), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(Modifier.size(8.dp).clip(CircleShape)
                .background(if (i == (if (trigger) 1 else 0)) c.accent else c.textDisabled.copy(0.4f)))
        }
    }
    val contentMod: Modifier = when (idx) {
        0 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 0.5f, tween(dur, easing = easing), label = "tab0x")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(200, animParams.tabSwitchSpeed), easing = easing), label = "tab0a")
            Modifier.graphicsLayer { translationX = size.width * ox; this.alpha = a }
        }
        1 -> {
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(300, animParams.tabSwitchSpeed), easing = easing), label = "tab1a")
            Modifier.alpha(a)
        }
        2 -> {
            val sc by animateFloatAsState(if (trigger) 1f else 0.85f, springSpec, label = "tab2sc")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(250, animParams.tabSwitchSpeed), easing = easing), label = "tab2a")
            Modifier.graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = a }
        }
        3 -> {
            val rot by animateFloatAsState(if (trigger) 0f else 90f, tween(dur, easing = easing), label = "tab3r")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(200, animParams.tabSwitchSpeed), easing = easing), label = "tab3a")
            Modifier.graphicsLayer { rotationY = rot; this.alpha = a }
        }
        4 -> {
            val sc by animateFloatAsState(if (trigger) 1f else 0.7f, springSpec, label = "tab4sc")
            val ox by animateFloatAsState(if (trigger) 0f else 0.3f, tween(previewTweenMs(320, animParams.tabSwitchSpeed), easing = easing), label = "tab4x")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "tab4a")
            Modifier.graphicsLayer { scaleX = sc; scaleY = sc; translationX = size.width * ox; this.alpha = a }
        }
        5 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 0.45f, tween(dur, easing = easing), label = "tab5x")
            val backOx by animateFloatAsState(if (trigger) -0.08f else -0.18f, tween(dur, easing = easing), label = "tab5bx")
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * backOx }.padding(6.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(3.dp)).background(c.bgElevated))
                    Box(Modifier.fillMaxWidth(0.74f).height(7.dp).clip(RoundedCornerShape(3.dp)).background(c.bgSurface))
                }
            }
            Modifier.graphicsLayer { translationX = size.width * ox }
        }
        6 -> {
            val h by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "tab6h")
            Modifier.graphicsLayer {
                scaleY = h.coerceAtLeast(0.01f)
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
        7 -> {
            val sc by animateFloatAsState(if (trigger) 1f else 1.18f, tween(dur, easing = easing), label = "tab7sc")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(dur, easing = easing), label = "tab7a")
            Modifier.graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = a }
        }
        8 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 0.24f * follow, tween(dur, easing = easing), label = "tab8x")
            val sc by animateFloatAsState(if (trigger) 1f else 0.96f, tween(dur, easing = easing), label = "tab8s")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(240, animParams.tabSwitchSpeed), easing = easing), label = "tab8a")
            Modifier.graphicsLayer { translationX = size.width * ox; scaleX = sc; scaleY = sc; this.alpha = a }
        }
        9 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 0.32f * follow, tween(dur, easing = easing), label = "tab9x")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(200, animParams.tabSwitchSpeed), easing = easing), label = "tab9a")
            Modifier.graphicsLayer { translationX = size.width * ox; this.alpha = a }
        }
        10 -> {
            val ox by animateFloatAsState(if (trigger) 0f else 0.36f * follow, springSpec, label = "tab10x")
            val sc by animateFloatAsState(if (trigger) 1f else 0.92f, springSpec, label = "tab10s")
            Modifier.graphicsLayer { translationX = size.width * ox; scaleX = sc; scaleY = sc }
        }
        11 -> {
            val sc by animateFloatAsState(if (trigger) 1f else 1.06f, tween(dur, easing = easing), label = "tab11s")
            val a by animateFloatAsState(if (trigger) 1f else 0f, tween(previewTweenMs(260, animParams.tabSwitchSpeed), easing = easing), label = "tab11a")
            Modifier.graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = a }
        }
        else -> Modifier
    }
    Box(modifier = contentMod.fillMaxWidth().padding(bottom = 18.dp).padding(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(3.dp)).background(c.accent.copy(0.6f)))
            Box(Modifier.fillMaxWidth(0.7f).height(7.dp).clip(RoundedCornerShape(3.dp)).background(c.bgElevated))
        }
    }
}

// ── 7. Эффект кнопок (4 варианта) ────────────────────────────────────────────

@Composable
private fun ButtonPressCard(modifier: Modifier, label: String, icon: ImageVector, idx: Int,
    isSelected: Boolean, c: ColorScheme, font: FontFamily?, animParams: AnimParams = AnimParams(), onSelect: () -> Unit) {
    AnimSelectCard(modifier, label, icon, isSelected, c, font, onSelect) { trigger, _ ->
        ButtonPressPreview(idx, trigger, c, animParams)
    }
}

@Composable
private fun BoxScope.ButtonPressPreview(idx: Int, trigger: Boolean, c: ColorScheme, animParams: AnimParams) {
    val springSpec = previewSpring(1, 0)
    val dur = previewTweenMs(220, animParams.buttonSpeed)
    val strength = animParams.buttonStrength.coerceIn(0.4f, 2f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (idx) {
            0 -> {
                val sc by animateFloatAsState(if (trigger) 1f - 0.12f * strength else 1f, springSpec, label = "bp0sc")
                Box(Modifier.size(36.dp).graphicsLayer { scaleX = sc; scaleY = sc }
                    .clip(RoundedCornerShape(10.dp)).background(c.accent.copy(0.8f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = c.bgDeep, modifier = Modifier.size(18.dp))
                }
            }
            1 -> {
                val sc by animateFloatAsState(if (trigger) 1f + 0.15f * strength else 1f, springSpec, label = "bp1sc")
                val a by animateFloatAsState(if (trigger) 1f else 0.6f, tween(dur), label = "bp1a")
                Box(Modifier.size(36.dp).graphicsLayer { scaleX = sc; scaleY = sc; this.alpha = a }
                    .clip(CircleShape).background(c.accent.copy(0.8f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = c.bgDeep, modifier = Modifier.size(18.dp))
                }
            }
            2 -> {
                val glow by animateFloatAsState(if (trigger) 0.35f + 0.15f * strength else 0f, tween(previewTweenMs(350, animParams.buttonSpeed)), label = "bp2g")
                Box(Modifier.size(48.dp).graphicsLayer { this.alpha = glow }.clip(CircleShape).background(c.accent.copy(0.3f)))
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(0.85f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = c.bgDeep, modifier = Modifier.size(18.dp))
                }
            }
            3 -> {
                val sc by animateFloatAsState(if (trigger) 1f else 1f - 0.18f * strength.coerceAtMost(1.2f), springSpec, label = "bp3sc")
                val rot by animateFloatAsState(if (trigger) 0f else -12f * strength.coerceAtMost(1.4f), springSpec, label = "bp3r")
                Box(Modifier.size(36.dp).graphicsLayer { scaleX = sc; scaleY = sc; rotationZ = rot }
                    .clip(RoundedCornerShape(10.dp)).background(c.accent.copy(0.8f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = c.bgDeep, modifier = Modifier.size(18.dp))
                }
            }
            4 -> {
                val ringScale by animateFloatAsState(if (trigger) 1.22f + 0.12f * strength else 0.8f, tween(dur), label = "bp4ring")
                val ringAlpha by animateFloatAsState(if (trigger) 0f else 0.34f + 0.08f * strength, tween(dur), label = "bp4alpha")
                Box(Modifier.size(48.dp).graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                    .clip(CircleShape).background(c.accent.copy(0.28f)))
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(0.86f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = c.bgDeep, modifier = Modifier.size(18.dp))
                }
            }
            5 -> {
                val sc by animateFloatAsState(if (trigger) 1f else 1f - 0.05f * strength, springSpec, label = "bp5sc")
                val rot by animateFloatAsState(if (trigger) 0f else 10f * strength.coerceAtMost(1.3f), springSpec, label = "bp5rot")
                val oy by animateFloatAsState(if (trigger) 0f else -4f * strength.coerceAtMost(1.2f), springSpec, label = "bp5y")
                Box(Modifier.size(36.dp).graphicsLayer { scaleX = sc; scaleY = sc; rotationZ = rot; translationY = oy }
                    .clip(RoundedCornerShape(10.dp)).background(c.accent.copy(0.84f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = c.bgDeep, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Превью нажатия строки ────────────────────────────────────────────────────

@Composable
private fun RowPressPreview(c: ColorScheme, font: FontFamily?, enabled: Boolean) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "rpScale")
    LaunchedEffect(pressed) { if (pressed) { delay(160); pressed = false } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Предпросмотр", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bgElevated)
                .clickable { pressed = true }
                .graphicsLayer { scaleX = scale; scaleY = scale }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(c.bgCard))
            Column(Modifier.weight(1f)) {
                Box(Modifier.height(3.dp).fillMaxWidth(0.65f).clip(RoundedCornerShape(2.dp)).background(c.textPrimary.copy(0.7f)))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.height(2.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(2.dp)).background(c.textSecondary.copy(0.5f)))
            }
            Text("3:45", color = c.textDisabled, fontFamily = font, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.accent.copy(0.08f)).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, null, tint = c.accent.copy(0.7f), modifier = Modifier.size(14.dp))
            Text(if (enabled) "Нажмите на трек выше, чтобы увидеть анимацию" else "Анимация отключена",
                color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
        }
    }
}

// ── Общие вспомогательные ─────────────────────────────────────────────────────

// ══════════════════════════════════════════════════════════════════════════════
// Кроссфейд вкладок — секция настройки
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TabCrossfadeSection(
    enabled: Boolean,
    durationMs: Int,
    onEnabledChange: (Boolean) -> Unit,
    onDurationChange: (Int) -> Unit,
    c: ColorScheme,
    font: FontFamily?
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Toggle
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Кроссфейд вкладок", color = c.textPrimary, fontFamily = font,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Плавное перетекание «Библиотека» ↔ «Поиск»",
                        color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

        // Visual preview
        val crossfadePreview = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(enabled) {
            if (enabled) {
                kotlinx.coroutines.delay(200)
                crossfadePreview.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
                kotlinx.coroutines.delay(300)
                crossfadePreview.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing))
            }
        }
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
            // "Библиотека" panel
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(c.accent.copy(0.16f), MaterialTheme.colorScheme.surfaceContainerHigh)))) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.LibraryMusic, null, tint = c.accent, modifier = Modifier.size(16.dp))
                    Text("Библиотека", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            // "Поиск" panel fades in on top
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(c.accentVar.copy(0.16f * crossfadePreview.value), MaterialTheme.colorScheme.surfaceContainerHigh.copy(crossfadePreview.value))))
                .graphicsLayer { alpha = crossfadePreview.value }) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Search, null, tint = c.accentVar, modifier = Modifier.size(16.dp))
                    Text("Поиск", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            if (!enabled) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(0.72f)),
                    contentAlignment = Alignment.Center) {
                    Text("Выключено — смена мгновенная", color = c.textDisabled, fontFamily = font, fontSize = 10.sp)
                }
            }
            }

        // Duration slider
            AnimatedVisibility(visible = enabled,
                enter = expandVertically() + fadeIn(),
                exit  = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Длительность", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                    Text("${durationMs} мс", color = c.accent, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                Slider(
                    value = durationMs.toFloat(),
                    onValueChange = { onDurationChange(it.toInt()) },
                    valueRange = 100f..2000f,
                    steps = 18,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent,
                        inactiveTrackColor = c.bgElevated)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Быстро (100мс)", color = c.textDisabled, fontFamily = font, fontSize = 9.sp)
                    Text("Медленно (2с)", color = c.textDisabled, fontFamily = font, fontSize = 9.sp)
                }
            }
        }
    }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Панель настройки параметров анимации
// ══════════════════════════════════════════════════════════════════════════════

data class AnimParamConfig(
    val speed: Float,
    val easingIdx: Int,
    val dampingIdx: Int,
    val stiffnessIdx: Int = 0,
    val extra1: Float = 1.0f,  // delay multiplier / strength / etc.
    val extra1Label: String = ""
)

@Composable
private fun AnimParamsPanel(
    title: String,
    config: AnimParamConfig,
    showSpring: Boolean = true,
    extra1Visible: Boolean = false,
    c: ColorScheme,
    font: FontFamily?,
    onChange: (AnimParamConfig) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val easingLabels  = listOf("EaseInOut", "EaseOut", "EaseIn", "Линейный", "Bounce", "Overshoot")
    val dampingLabels = listOf("Средний", "Пружинистый", "Упругий", "Без отскока")
    val stiffLabels   = listOf("Средняя", "Мягкая", "Жёсткая", "Очень мягкая")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Gear button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val btnBg by animateColorAsState(
                if (expanded) c.accent.copy(0.18f) else c.bgElevated.copy(0.6f), tween(180), "pBg")
            val btnTint by animateColorAsState(
                if (expanded) c.accent else c.textDisabled, tween(180), "pTint")
            Surface(
                modifier = Modifier.clickable { expanded = !expanded },
                shape = RoundedCornerShape(20.dp),
                color = btnBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.Tune,
                    null, tint = btnTint, modifier = Modifier.size(13.dp))
                Text(if (expanded) "Скрыть" else "Настройки",
                    color = btnTint, fontFamily = font, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + fadeIn(tween(160)),
            exit  = shrinkVertically(tween(200)) + fadeOut(tween(150))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(title, color = c.accent, fontFamily = font,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)

                // Speed
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Скорость", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                        Text(when {
                            config.speed < 0.6f -> "Очень быстро"
                            config.speed < 0.85f -> "Быстро"
                            config.speed < 1.15f -> "Нормально"
                            config.speed < 1.7f -> "Медленно"
                            else -> "Очень медленно"
                        }, color = c.accent, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                    Slider(value = config.speed, onValueChange = { onChange(config.copy(speed = it)) },
                        valueRange = 0.3f..3.0f,
                        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.bgElevated))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Быстро", color = c.textDisabled, fontFamily = font, fontSize = 9.sp)
                        Text("Медленно", color = c.textDisabled, fontFamily = font, fontSize = 9.sp)
                    }
                }

                // Easing
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Кривая", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                    easingLabels.chunked(3).forEachIndexed { rowIndex, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEachIndexed { offset, lbl ->
                                val i = rowIndex * 3 + offset
                                val sel = config.easingIdx == i
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) c.accent.copy(0.2f) else c.bgElevated)
                                        .border(BorderStroke(if (sel) 1.5.dp else 0.5.dp,
                                            if (sel) c.accent else c.divider.copy(0.3f)), RoundedCornerShape(8.dp))
                                        .clickable { onChange(config.copy(easingIdx = i)) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lbl, color = if (sel) c.accent else c.textSecondary,
                                        fontFamily = font, fontSize = 8.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }

                if (showSpring) {
                    // Damping
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Упругость (пружина)", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dampingLabels.forEachIndexed { i, lbl ->
                                val sel = config.dampingIdx == i
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) c.accent.copy(0.2f) else c.bgElevated)
                                        .border(BorderStroke(if (sel) 1.5.dp else 0.5.dp,
                                            if (sel) c.accent else c.divider.copy(0.3f)), RoundedCornerShape(8.dp))
                                        .clickable { onChange(config.copy(dampingIdx = i)) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lbl, color = if (sel) c.accent else c.textSecondary,
                                        fontFamily = font, fontSize = 8.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }

                    // Stiffness
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Жёсткость пружины", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            stiffLabels.forEachIndexed { i, lbl ->
                                val sel = config.stiffnessIdx == i
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) c.accent.copy(0.2f) else c.bgElevated)
                                        .border(BorderStroke(if (sel) 1.5.dp else 0.5.dp,
                                            if (sel) c.accent else c.divider.copy(0.3f)), RoundedCornerShape(8.dp))
                                        .clickable { onChange(config.copy(stiffnessIdx = i)) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lbl, color = if (sel) c.accent else c.textSecondary,
                                        fontFamily = font, fontSize = 8.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }

                // Extra param (delay / strength)
                if (extra1Visible && config.extra1Label.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(config.extra1Label, color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                            Text("${(config.extra1 * 100).toInt()}%",
                                color = c.accent, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                        Slider(value = config.extra1, onValueChange = { onChange(config.copy(extra1 = it)) },
                            valueRange = 0f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.bgElevated))
                    }
                }

                // Reset button
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onChange(AnimParamConfig(1f, 0, 0, 0, 1f, config.extra1Label)) }) {
                            Icon(Icons.Rounded.Refresh, null, tint = c.textDisabled, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Сбросить", color = c.textDisabled, fontFamily = font, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    c: ColorScheme,
    font: FontFamily?,
    onValueChange: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                Text(valueLabel, color = c.accent, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun AnimCard(title: String, icon: ImageVector, c: ColorScheme, font: FontFamily?,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Text(title, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            content()
        }
    }
}

@Composable
private fun AnimToggleRow(label: String, subtitle: String, checked: Boolean, c: ColorScheme,
    font: FontFamily?, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
