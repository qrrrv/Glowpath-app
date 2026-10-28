package com.musicplayer.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.Lyrics as OutlinedLyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.size.Size as CoilSize
import com.musicplayer.R
import com.musicplayer.data.RepeatMode
import com.musicplayer.data.SongColorCache
import com.musicplayer.data.toTimeString
import com.musicplayer.ui.components.*
import com.musicplayer.ui.components.instrumentIconRes
import com.musicplayer.ui.theme.*
import androidx.compose.ui.util.lerp as lerpFloat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.musicplayer.viewmodel.MusicViewModel
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos

import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*

@Composable
fun PlayerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    miniPlayerBounds: Rect? = null,
    miniArtworkBounds: Rect? = null,
    onLyricsClick: () -> Unit = {},
    onEditLyricsClick: () -> Unit = {}
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val song            by viewModel.currentSong.collectAsState()
    val isPlaying       by viewModel.isPlaying.collectAsState()
    val duration        by viewModel.duration.collectAsState()
    // Позиция тикает 5 раз в секунду. Читаем её ТОЛЬКО в ProgressSection,
    // а здесь — лишь булев «порог» (>3с), который меняется раз за трек. Иначе весь плеер
    // (обложки, градиенты, очередь) пересобирался бы на каждый тик.
    val positionPastRestart by remember(viewModel) {
        viewModel.currentPosition.map { it > 3000L }.distinctUntilChanged()
    }.collectAsState(initial = viewModel.currentPosition.value > 3000L)
    val volume          by viewModel.volume.collectAsState()
    val playbackSpeed   by viewModel.playbackSpeed.collectAsState()
    val settings        by viewModel.settings.collectAsState()
    val favourites      by viewModel.favourites.collectAsState()
    val songs           by viewModel.songs.collectAsState()
    val bridgeQueueUris by viewModel.bridgeQueueUris.collectAsState()
    val queueRevision   by viewModel.queueRevision.collectAsState()
    val customArtMap    by viewModel.customArtMap.collectAsState()

    if (song == null) { LaunchedEffect(Unit) { onBack() }; return }

    val isFavourite = song!!.id in favourites
    val customArtUri: android.net.Uri? = customArtMap[song!!.id]
    val artworkUri = customArtUri ?: song!!.albumArtUri
    // Соседние треки считаем не на каждый тик позиции (сортировка всей библиотеки дважды в секунду
    // забивала главный поток и давала микро-подвисания при свайпе), а только когда что-то изменилось.
    val previousSong = remember(song!!.id, positionPastRestart, songs, settings, bridgeQueueUris, queueRevision) {
        viewModel.playerNeighbour(forward = false)
    }
    val nextSong = remember(song!!.id, positionPastRestart, songs, settings, bridgeQueueUris, queueRevision) {
        viewModel.playerNeighbour(forward = true)
    }
    val previousCover = previousSong?.let { CoverItem(it.id, customArtMap[it.id] ?: it.albumArtUri) }
    val nextCover = nextSong?.let { CoverItem(it.id, customArtMap[it.id] ?: it.albumArtUri) }

    // Match OuterTune's player background: keep the previous gradient visible
    // while the next artwork is decoded, then crossfade the complete gradient.
    var artworkGradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(artworkUri) {
        artworkUri?.let { uri ->
            artworkGradientColors = SongColorCache.getGradientColors(context, uri)
        }
    }
    val density = LocalDensity.current
    val artworkCornerPx = with(density) { LocalAppStyle.current.cardCornerRadius.coerceIn(8f, 64f).dp.toPx() }
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { PlayerCollapseMotion(scope) }
    var showTrackSettings by remember { mutableStateOf(false) }
    var playerBounds by remember { mutableStateOf<Rect?>(null) }
    var artworkBounds by remember { mutableStateOf<Rect?>(null) }
    var lyricsBounds by remember { mutableStateOf<Rect?>(null) }
    var queueTop by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var queueExpanded by remember { mutableStateOf(false) }
    val artworkLayer = rememberGraphicsLayer()
    val expandedFraction = motion.fraction
    val latestOnBack by rememberUpdatedState(onBack)
    val fallbackHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val parentOrigin = playerBounds?.topLeft ?: Offset.Zero
    val dismissDistance = ((miniPlayerBounds?.top?.minus(parentOrigin.y))
        ?: (playerBounds?.height ?: fallbackHeight) * 0.8f).coerceAtLeast(1f)

    // Кнопки плеера перекрашиваются под цвета обложки вместе с фоном.
    // settings.coloredPlayerButtons выключает это — кнопки остаются в цветах темы.
    val songButtons = if (settings.coloredPlayerButtons) {
        rememberSongButtonColors(artworkGradientColors)
    } else null

    LaunchedEffect(motion) { motion.settle(close = false) }

    fun settlePlayer(close: Boolean, velocity: Float = 0f) {
        motion.settle(close, velocity, dismissDistance) { latestOnBack() }
    }

    BackHandler(enabled = !showTrackSettings) { settlePlayer(close = true) }

    // Capture the existing artwork display list: no second image request, bitmap copy,
    // or disappearing/recreated cover at either end of the transition.
    val artworkModifier = Modifier
        .onGloballyPositioned { artworkBounds = it.boundsInRoot() }
        .drawWithContent {
            artworkLayer.record { this@drawWithContent.drawContent() }
            if (motion.fraction >= 1f || settings.lyricsOnCover || miniArtworkBounds == null) {
                drawLayer(artworkLayer)
            }
        }
    val lyricsModifier = Modifier.onGloballyPositioned { lyricsBounds = it.boundsInRoot() }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { playerBounds = it.boundsInRoot() }
            .playerCollapseGestures(
                enabled = !showTrackSettings,
                canStart = { position ->
                    val rootPosition = position + parentOrigin
                    !queueExpanded && rootPosition.y < queueTop &&
                        !(settings.lyricsOnCover && lyricsBounds?.contains(rootPosition) == true)
                },
                canDragUp = { motion.fraction < 1f },
                onStart = { motion.grab() },
                onDrag = { motion.dragBy(it, dismissDistance) },
                onEnd = { velocity ->
                    settlePlayer(
                        shouldCollapsePlayer(motion.fraction, velocity, dismissDistance, density.density),
                        velocity,
                    )
                },
                onCancel = { settlePlayer(close = false) },
            )
    ) {
        // Only the background changes aspect ratio; artwork and controls never stretch.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val progress = expandedFraction
                val target = miniPlayerBounds?.translate(-parentOrigin)
                    ?: Rect(12.dp.toPx(), size.height - 150.dp.toPx(),
                        size.width - 12.dp.toPx(), size.height - 86.dp.toPx())
                val sx = lerpFloat(target.width, size.width, progress) / size.width.coerceAtLeast(1f)
                val sy = lerpFloat(target.height, size.height, progress) / size.height.coerceAtLeast(1f)
                val corner = 28.dp.toPx() * (1f - progress)
                translationX = target.left * (1f - progress)
                translationY = target.top * (1f - progress)
                scaleX = sx
                scaleY = sy
                transformOrigin = TransformOrigin(0f, 0f)
                shape = GenericShape { outlineSize, _ ->
                    addRoundRect(RoundRect(0f, 0f, outlineSize.width, outlineSize.height,
                        CornerRadius(corner / sx, corner / sy)))
                }
                clip = true
                alpha = (progress / 0.18f).coerceIn(0f, 1f)
                shadowElevation = 10.dp.toPx() * (1f - progress)
            }
        ) {
            Box(Modifier.fillMaxSize()
                .background(c.surfaceColorAtElevation(NavigationBarDefaults.Elevation)))
            AnimatedContent(
                targetState = artworkGradientColors,
                transitionSpec = { fadeIn(tween(1000)).togetherWith(fadeOut(tween(1000))) },
                label = "playerArtworkGradient"
            ) { colors ->
                if (colors.size >= 2) {
                    Box(Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(colors), alpha = 0.4f))
                }
            }
        }

        // Keep layout coordinates stationary. Move the drawing, so measurements and
        // gesture distances cannot feed the animated transform back into themselves.
        Box(Modifier.fillMaxSize()
            .graphicsLayer { alpha = ((expandedFraction - 0.12f) / 0.88f).coerceIn(0.001f, 1f) }
            .drawWithContent {
                translate(top = (1f - expandedFraction) * dismissDistance) {
                    this@drawWithContent.drawContent()
                }
            }
        ) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                    .size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp))
                    .background(c.textDisabled.copy(alpha = 0.35f))
            )
            val requestClose: () -> Unit = { settlePlayer(close = true) }
            val doFlipNext: () -> Unit = { viewModel.playNext() }
            val doFlipPrev: () -> Unit = { viewModel.playPrevious() }
            val swipeProgress = 1f - expandedFraction

            if (isLandscape) {
                LandscapePlayerContent(
                    song!!, isPlaying, duration, settings, isFavourite, 1f,
                    viewModel, customArtUri, previousCover, nextCover, requestClose,
                    onLyricsClick, { showTrackSettings = true }, doFlipNext, doFlipPrev,
                    swipeProgress, expandedFraction, artworkModifier, lyricsModifier,
                    songButtons
                )
            } else {
                PlayerQueueHost(
                    viewModel = viewModel,
                    song = song!!,
                    isPlaying = isPlaying,
                    gradientColors = artworkGradientColors,
                    onQueueTopChanged = { queueTop = it },
                    onQueueExpandedChanged = { queueExpanded = it },
                ) { queueSlots ->
                    PortraitPlayerContent(
                        song!!, isPlaying, duration, settings, isFavourite, 1f,
                        viewModel, customArtUri, previousCover, nextCover, requestClose,
                        onLyricsClick, { showTrackSettings = true }, doFlipNext, doFlipPrev,
                        swipeProgress, expandedFraction, queueSlots, artworkModifier, lyricsModifier,
                        songButtons
                    )
                }
            }
        }

        // Shared cover moves above the outgoing controls/queue, into the measured
        // mini-player artwork. Vertical travel is linear while dragging; the lateral
        // curve and rounded corners make the final docking softer.
        if (!settings.lyricsOnCover) {
            Canvas(Modifier.fillMaxSize().graphicsLayer {
                alpha = (expandedFraction / 0.06f).coerceIn(0f, 1f)
            }) {
                val source = artworkBounds?.translate(-parentOrigin)
                val target = miniArtworkBounds?.translate(-parentOrigin)
                if (expandedFraction < 1f && source != null && target != null &&
                    source.width > 0f && artworkLayer.size.width > 0
                ) {
                    val collapse = 1f - expandedFraction
                    val curved = collapse * collapse * (3f - 2f * collapse)
                    val width = lerpFloat(source.width, target.width, collapse)
                    val left = lerpFloat(source.center.x, target.center.x, curved) - width / 2f
                    val top = lerpFloat(source.top, target.top, collapse)
                    val corner = lerpFloat(
                        minOf(artworkCornerPx * width / source.width, width / 2f),
                        14.dp.toPx(), collapse
                    )
                    val outline = Path().apply {
                        addRoundRect(RoundRect(left, top, left + width, top + width,
                            CornerRadius(corner)))
                    }
                    clipPath(outline) {
                        translate(left, top) {
                            scale(width / source.width, width / source.width, pivot = Offset.Zero) {
                                drawLayer(artworkLayer)
                            }
                        }
                    }
                }
            }
        }

        TrackSettingsOverlay(
            visible = showTrackSettings, volume = volume, playbackSpeed = playbackSpeed, song = song,
            viewModel = viewModel,
            onVolumeChange = { viewModel.setVolume(it) }, onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onSetRingtone = { song?.let { setAsRingtone(context, it.uri) }; showTrackSettings = false },
            onDismiss = { showTrackSettings = false },
            onEditLyrics = { showTrackSettings = false; onEditLyricsClick() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Кнопки в цвет трека: палитра из обложки для всех кнопок большого плеера.
// Чистая визуалка: ничего не знает о ViewModel и только производит цвета.
// ─────────────────────────────────────────────────────────────────────────────

/** Набор цветов кнопок плеера, выведенных из градиента текущей обложки. */
/** Боковое поле единой колонки плеера: от него отсчитываются обложка, ползунок, кнопки и «Дальше». */
private val PlayerSideMargin = 28.dp

/** Максимальная ширина колонки = макс. размер обложки (360dp) + поля — на планшетах колонка не расползается шире обложки. */
private val PlayerColumnMaxWidth = 360.dp + PlayerSideMargin * 2

/**
 * Расширяет элемент на [amount] за левый и правый край, не меняя занимаемую в раскладке ширину.
 * Нужен волнистому слайдеру: его линия начинается с отступа ~6dp от края (радиус ползунка),
 * из-за чего волна выглядела уже обложки и кнопок.
 */
private fun Modifier.bleedHorizontally(amount: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, constraints ->
        val extra = (amount * 2).roundToPx()
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        val placeable = measurable.measure(
            constraints.copy(minWidth = (constraints.minWidth + extra).coerceAtMost(maxW), maxWidth = maxW)
        )
        layout((placeable.width - extra).coerceAtLeast(0), placeable.height) {
            placeable.place(-extra / 2, 0)
        }
    }

private data class SongButtonColors(
    val playContainer: Color,
    val onPlayContainer: Color,
    val otherContainer: Color,
    val onOtherContainer: Color,
    val pillBackground: Color,
    val toggleActive: Color,
    val onToggleActive: Color,
    val toggleInactive: Color,
    val onToggleInactive: Color,
    val topContainer: Color,
    val onTopContainer: Color,
    val topSelectedContainer: Color,
    val onTopSelectedContainer: Color,
    val sliderActive: Color,
    val sliderInactive: Color,
    val sliderThumb: Color,
)

/** Цвет иконки, контрастный подложке: тёмный на светлой и белый на тёмной. */
private fun contrastIconOn(container: Color): Color =
    if (container.luminance() > 0.30f) Color(0xFF121212) else Color.White

/**
 * Выводит цвета кнопок из градиента обложки (SongColorCache: [0]=светлый, [last]=тёмный).
 * Акцент — светлый цвет песни; подложки — его затемнённые варианты, чтобы кнопки
 * читались на фоне, который сам и окрашен этим градиентом.
 */
private fun deriveSongButtonColors(gradient: List<Color>): SongButtonColors? {
    if (gradient.isEmpty()) return null
    val bright = gradient.first()
    val dark = gradient.last()

    val deepBase = lerpColor(dark, Color.Black, 0.30f)
    val play = bright
    val other = lerpColor(bright, dark, 0.45f)
    val pill = lerpColor(deepBase, bright, 0.12f)
    val toggleInactive = lerpColor(pill, Color.White, 0.10f)
    // Светлая обложка: притемняем верхние пилюли, иначе сольются со светлым фоном
    val top = if (bright.luminance() > 0.5f) lerpColor(bright, Color.Black, 0.24f)
              else lerpColor(pill, Color.White, 0.08f)

    return SongButtonColors(
        playContainer = play,
        onPlayContainer = contrastIconOn(play),
        otherContainer = other,
        onOtherContainer = contrastIconOn(other),
        pillBackground = pill,
        toggleActive = bright,
        onToggleActive = contrastIconOn(bright),
        toggleInactive = toggleInactive,
        onToggleInactive = Color.White.copy(alpha = 0.86f),
        topContainer = top,
        onTopContainer = contrastIconOn(top),
        topSelectedContainer = bright,
        onTopSelectedContainer = contrastIconOn(bright),
        sliderActive = bright,
        sliderInactive = lerpColor(pill, Color.White, 0.16f),
        sliderThumb = bright,
    )
}

/**
 * Плавно анимирует смену цветов при переключении трека — в такт кроссфейду
 * фонового градиента (1000 мс).
 */
@Composable
private fun rememberSongButtonColors(gradient: List<Color>): SongButtonColors? {
    val target = remember(gradient) { deriveSongButtonColors(gradient) } ?: return null
    val spec = tween<Color>(1000)
    val playContainer by animateColorAsState(target.playContainer, spec, label = "sbPlayContainer")
    val onPlayContainer by animateColorAsState(target.onPlayContainer, spec, label = "sbOnPlayContainer")
    val otherContainer by animateColorAsState(target.otherContainer, spec, label = "sbOtherContainer")
    val onOtherContainer by animateColorAsState(target.onOtherContainer, spec, label = "sbOnOtherContainer")
    val pillBackground by animateColorAsState(target.pillBackground, spec, label = "sbPillBackground")
    val toggleActive by animateColorAsState(target.toggleActive, spec, label = "sbToggleActive")
    val onToggleActive by animateColorAsState(target.onToggleActive, spec, label = "sbOnToggleActive")
    val toggleInactive by animateColorAsState(target.toggleInactive, spec, label = "sbToggleInactive")
    val onToggleInactive by animateColorAsState(target.onToggleInactive, spec, label = "sbOnToggleInactive")
    val topContainer by animateColorAsState(target.topContainer, spec, label = "sbTopContainer")
    val onTopContainer by animateColorAsState(target.onTopContainer, spec, label = "sbOnTopContainer")
    val topSelectedContainer by animateColorAsState(target.topSelectedContainer, spec, label = "sbTopSelected")
    val onTopSelectedContainer by animateColorAsState(target.onTopSelectedContainer, spec, label = "sbOnTopSelected")
    val sliderActive by animateColorAsState(target.sliderActive, spec, label = "sbSliderActive")
    val sliderInactive by animateColorAsState(target.sliderInactive, spec, label = "sbSliderInactive")
    val sliderThumb by animateColorAsState(target.sliderThumb, spec, label = "sbSliderThumb")
    return SongButtonColors(
        playContainer = playContainer,
        onPlayContainer = onPlayContainer,
        otherContainer = otherContainer,
        onOtherContainer = onOtherContainer,
        pillBackground = pillBackground,
        toggleActive = toggleActive,
        onToggleActive = onToggleActive,
        toggleInactive = toggleInactive,
        onToggleInactive = onToggleInactive,
        topContainer = topContainer,
        onTopContainer = onTopContainer,
        topSelectedContainer = topSelectedContainer,
        onTopSelectedContainer = onTopSelectedContainer,
        sliderActive = sliderActive,
        sliderInactive = sliderInactive,
        sliderThumb = sliderThumb,
    )
}

@Composable
private fun PortraitPlayerContent(
    song: com.musicplayer.data.Song, isPlaying: Boolean, duration: Long,
    settings: com.musicplayer.data.PlayerSettings, isFavourite: Boolean, artScale: Float,
    viewModel: MusicViewModel, customArtUri: android.net.Uri?, previousCover: CoverItem?, nextCover: CoverItem?,
    onBack: () -> Unit, onLyricsClick: () -> Unit, onSettingsClick: () -> Unit,
    onSwipeNext: () -> Unit, onSwipePrev: () -> Unit, swipeProgress: Float = 0f, openProgress: Float = 1f,
    queueSlots: PlayerQueueSlots,
    artworkModifier: Modifier = Modifier,
    lyricsModifier: Modifier = Modifier,
    songButtons: SongButtonColors? = null,
) {
    // Booming: кнопка «Текст» плавно меняет обложку на текст песни (alpha 1→0 / 0→1, 350 мс).
    val lyricsProgress by animateFloatAsState(
        targetValue = if (settings.lyricsOnCover) 1f else 0f,
        animationSpec = tween(BOOMING_ANIM_TIME, easing = BoomingAnimEasing),
        label = "coverLyricsProgress"
    )
    val toggleLyrics = {
        // Как в Booming: пока идёт анимация, повторное нажатие игнорируется.
        if (lyricsProgress <= 0f || lyricsProgress >= 1f) {
            viewModel.updateSettings(settings.copy(lyricsOnCover = !settings.lyricsOnCover))
        }
    }
    val density = LocalDensity.current
    val contentReveal = ((openProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val contentOffset = with(density) { lerpFloat(34.dp.toPx(), 0f, contentReveal) }
    val topReveal = ((openProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
    // Единая колонка: обложка, ползунок, кнопки управления и «Дальше» имеют одни и те же
    // левый/правый края (поле PlayerSideMargin). Чтобы обложка (квадрат) дотянулась до ширины
    // колонки, вертикальные отступы ниже слегка сжаты — освободившееся место отдано обложке.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.fillMaxHeight().widthIn(max = PlayerColumnMaxWidth).fillMaxWidth().padding(horizontal = PlayerSideMargin),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.graphicsLayer {
            alpha = topReveal
            translationY = lerpFloat((-18).dp.toPx(), 0f, topReveal)
        }) {
            TopBar(onBack, onSettingsClick, swipeProgress, songButtons)
        }
        Spacer(Modifier.height(8.dp))
        // Обложка получает всё свободное место (weight) и стоит в нём по центру, а блок
        // управления прижат вниз — поэтому «Дальше» всегда лежит прямо над шторкой очереди.
        // Размер обложки = всё свободное место (до 360dp). Нажатие на «Текст» плавно
        // заменяет обложку на текст песни в этом же месте (как в Booming).
        CoverWithLyrics(
            viewModel = viewModel,
            song = song,
            lyricsProgress = lyricsProgress,
            onExpand = onLyricsClick,
            modifier = Modifier.weight(1f).fillMaxWidth().then(lyricsModifier)
        ) { coverScale ->
            AlbumArtSection(song, isPlaying, artScale * coverScale, customArtUri, previousCover, nextCover, onSwipeNext, onSwipePrev, { viewModel.setCustomArt(song.id, null) }, settings.albumArtAnim, settings.animParams, openProgress, artworkModifier)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.graphicsLayer {
            alpha = contentReveal
            translationY = contentOffset
        }) {
            SongMetaSection(song, viewModel)
            Spacer(Modifier.height(16.dp))
            ProgressSection(duration, isPlaying, viewModel, songButtons)
            Spacer(Modifier.height(16.dp))
            ControlsSection(settings, isPlaying, viewModel, songButtons)
            Spacer(Modifier.height(8.dp))
            BottomToggleRow(settings, isFavourite, viewModel, song.id, songButtons, onLyricsToggle = toggleLyrics)
            Spacer(Modifier.height(8.dp))
            queueSlots.nextUp()
            Spacer(Modifier.height(6.dp))
        }
    }
    }
}

@Composable
private fun LandscapePlayerContent(
    song: com.musicplayer.data.Song, isPlaying: Boolean, duration: Long,
    settings: com.musicplayer.data.PlayerSettings, isFavourite: Boolean, artScale: Float,
    viewModel: MusicViewModel, customArtUri: android.net.Uri?, previousCover: CoverItem?, nextCover: CoverItem?,
    onBack: () -> Unit, onLyricsClick: () -> Unit, onSettingsClick: () -> Unit,
    onSwipeNext: () -> Unit, onSwipePrev: () -> Unit, swipeProgress: Float = 0f, openProgress: Float = 1f,
    artworkModifier: Modifier = Modifier,
    lyricsModifier: Modifier = Modifier,
    songButtons: SongButtonColors? = null,
) {
    // Booming: кнопка «Текст» плавно меняет обложку на текст песни (alpha 1→0 / 0→1, 350 мс).
    val lyricsProgress by animateFloatAsState(
        targetValue = if (settings.lyricsOnCover) 1f else 0f,
        animationSpec = tween(BOOMING_ANIM_TIME, easing = BoomingAnimEasing),
        label = "coverLyricsProgress"
    )
    val toggleLyrics = {
        // Как в Booming: пока идёт анимация, повторное нажатие игнорируется.
        if (lyricsProgress <= 0f || lyricsProgress >= 1f) {
            viewModel.updateSettings(settings.copy(lyricsOnCover = !settings.lyricsOnCover))
        }
    }
    val density = LocalDensity.current
    val sideReveal = ((openProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val sideOffset = with(density) { lerpFloat(26.dp.toPx(), 0f, sideReveal) }
    Row(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.graphicsLayer {
                alpha = sideReveal
                translationY = lerpFloat((-14).dp.toPx(), 0f, sideReveal)
            }) {
                TopBar(onBack, onSettingsClick, swipeProgress, songButtons)
            }
            Spacer(Modifier.height(8.dp))
            CoverWithLyrics(
                viewModel = viewModel,
                song = song,
                lyricsProgress = lyricsProgress,
                onExpand = onLyricsClick,
                modifier = Modifier.weight(1f).fillMaxWidth().then(lyricsModifier)
            ) { coverScale ->
                AlbumArtSection(song, isPlaying, artScale * coverScale, customArtUri, previousCover, nextCover, onSwipeNext, onSwipePrev, { viewModel.setCustomArt(song.id, null) }, settings.albumArtAnim, settings.animParams, openProgress, artworkModifier)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).fillMaxHeight().graphicsLayer {
            alpha = sideReveal
            translationY = sideOffset
        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            SongMetaSection(song, viewModel)
            ProgressSection(duration, isPlaying, viewModel, songButtons)
            ControlsSection(settings, isPlaying, viewModel, songButtons)
            BottomToggleRow(settings, isFavourite, viewModel, song.id, songButtons, onLyricsToggle = toggleLyrics)
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, onSettingsClick: () -> Unit, swipeProgress: Float = 0f, songButtons: SongButtonColors? = null) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        // Кнопка текстов переехала в нижний ряд (к Перемешать/Повтор/Лайк), поэтому
        // «СЕЙЧАС ИГРАЕТ» никто не зажимает, а шапка симметрична: слева Свернуть,
        // справа Настройки. Обе кнопки — FilledIconButton (одинаковая зона касания
        // 48dp с кругом 40dp по центру), значит они всегда на одной горизонтальной оси.
        Text("СЕЙЧАС ИГРАЕТ", color = c.textDisabled, fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.1f.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // Круглые кнопки 40dp лежат в зоне касания 48dp, поэтому визуально они на 4dp внутри
        // колонки. Сдвигаем на эти 4dp наружу, чтобы край кнопки совпадал с краем обложки.
        Box(Modifier.offset(x = (-4).dp)) {
            AnimatedBackButton(
                onBack = onBack,
                containerColor = songButtons?.topContainer ?: c.surfaceContainerLow,
                iconColor = songButtons?.onTopContainer ?: c.onSurface,
                swipeProgress = swipeProgress
            )
        }
        Box(Modifier.offset(x = 4.dp)) {
            FilledIconButton(
                onSettingsClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = songButtons?.topContainer ?: c.surfaceContainerLow,
                    contentColor = songButtons?.onTopContainer ?: c.onSurface
                )
            ) {
                Icon(Icons.Rounded.Tune, "Настройки трека")
            }
        }
        }
    }
}

// ── Animated back button with Lottie 180° rotation ───────────────────────────
// Behaviour:
//   • Tap  → onBack() fires IMMEDIATELY, animation plays while screen closes
//   • Swipe → swipeProgress (0..1) drives animation frame in real-time
@Composable
private fun AnimatedBackButton(
    onBack: () -> Unit,
    containerColor: Color,
    iconColor: Color,
    swipeProgress: Float = 0f
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.arrow_down_anim))

    // Internal tap animation (independent of swipe)
    var tapAnimating by remember { mutableStateOf(false) }
    val tapProgress by animateLottieCompositionAsState(
        composition   = composition,
        isPlaying     = tapAnimating,
        iterations    = 1,
        speed         = 1.4f,
        restartOnPlay = true
    )
    // Reset flag when tap animation completes
    LaunchedEffect(tapProgress) {
        if (tapAnimating && tapProgress >= 0.99f) tapAnimating = false
    }

    // Final rendered progress:
    //   During swipe → driven by finger (0..1)
    //   During tap   → driven by internal animation
    //   Otherwise    → 0 (rest position)
    val displayProgress = when {
        swipeProgress > 0f -> swipeProgress.coerceIn(0f, 1f)
        tapAnimating       -> tapProgress
        else               -> 0f
    }

    // Dynamic color tint to match current theme
    val colorArgb = android.graphics.Color.argb(
        (iconColor.alpha * 255).toInt(),
        (iconColor.red   * 255).toInt(),
        (iconColor.green * 255).toInt(),
        (iconColor.blue  * 255).toInt()
    )
    val dynamicProps = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value    = android.graphics.PorterDuffColorFilter(colorArgb, android.graphics.PorterDuff.Mode.SRC_ATOP),
            keyPath  = arrayOf("**")
        )
    )

    FilledIconButton(
        onClick = {
            // Close screen immediately, animation plays in parallel while screen animates out
            tapAnimating = true
            onBack()
        },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor   = iconColor
        )
    ) {
        if (composition != null) {
            LottieAnimation(
                composition       = composition,
                progress          = { displayProgress },
                modifier          = Modifier.size(26.dp),
                dynamicProperties = dynamicProps
            )
        } else {
            Icon(Icons.Rounded.KeyboardArrowDown, "Назад", Modifier.size(26.dp))
        }
    }
}

@Composable
private fun AlbumArtSection(
    song: com.musicplayer.data.Song, isPlaying: Boolean, artScale: Float,
    customArtUri: android.net.Uri?, previousCover: CoverItem?, nextCover: CoverItem?,
    onSwipeNext: () -> Unit, onSwipePrev: () -> Unit, onResetArt: () -> Unit,
    albumArtAnim: Int = 0, animParams: com.musicplayer.data.AnimParams = com.musicplayer.data.AnimParams(),
    openProgress: Float = 1f,
    artworkModifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val appStyle = com.musicplayer.ui.theme.LocalAppStyle.current
    val artCorner = androidx.compose.ui.unit.Dp(appStyle.cardCornerRadius.coerceIn(8f, 64f))
    val artShape = RoundedCornerShape(artCorner)

    Box(contentAlignment = Alignment.BottomEnd) {
        CoverSwipeCarousel(
            modifier = artworkModifier,
            current = CoverItem(song.id, customArtUri ?: song.albumArtUri),
            previous = previousCover,
            next = nextCover,
            onSwipeNext = onSwipeNext,
            onSwipePrevious = onSwipePrev,
            coverSize = 280.dp * artScale,
            shape = artShape,
        )
        if (customArtUri != null) {
            Box(Modifier.padding(8.dp).size(28.dp).clip(CircleShape).background(c.bgCard.copy(0.92f)).clickable(onClick = onResetArt), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SongMetaSection(song: com.musicplayer.data.Song, viewModel: MusicViewModel) {
    val c = MaterialTheme.colorScheme
    val settings by viewModel.settings.collectAsState()
    val customTitleMap  by viewModel.customTitleMap.collectAsState()
    val customArtistMap by viewModel.customArtistMap.collectAsState()
    val displayTitle  = customTitleMap[song.id]  ?: song.title
    val displayArtist = customArtistMap[song.id] ?: song.artist
    AnimatedContent(
            targetState = song.id,
            transitionSpec = {
                (fadeIn(tween(350)) + slideInHorizontally { it / 5 })
                    .togetherWith(fadeOut(tween(200)) + slideOutHorizontally { -it / 5 })
                    .using(SizeTransform(clip = false))
            },
            label = "songMeta",
            modifier = Modifier.fillMaxWidth()
        ) { _ ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val shadowModifier = if (settings.textShadowEnabled) {
                    Modifier
                } else Modifier
                AutoScrollingText(
                    text = if (settings.uppercaseTitles) displayTitle.uppercase() else displayTitle,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = settings.playerTitleSize.sp,
                        fontWeight = if (settings.boldTitles) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (settings.textShadowEnabled)
                            c.textPrimary.copy(alpha = 1f)
                        else c.textPrimary,
                        fontFamily = LocalAppFontFamily.current,
                        letterSpacing = settings.letterSpacingEm.em,
                        lineHeight = (settings.playerTitleSize * settings.lineHeightScale * 1.4f).sp,
                        shadow = if (settings.textShadowEnabled) androidx.compose.ui.graphics.Shadow(
                            color = c.accent.copy(alpha = settings.textShadowIntensity * 0.8f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                            blurRadius = 12f * settings.textShadowIntensity
                        ) else null
                    ),
                    textAlign = TextAlign.Center, gradientEdgeColor = c.bgDeep, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (displayArtist != "<unknown>" && displayArtist.isNotBlank()) displayArtist else "Неизвестный",
                    color = when(settings.artistNameStyle) {
                        2 -> c.textDisabled
                        else -> c.textSecondary
                    },
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = settings.playerArtistSize.sp,
                    fontStyle = if (settings.artistNameStyle == 1) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    letterSpacing = settings.letterSpacingEm.em,
                    textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    style = if (settings.textShadowEnabled) androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = c.accent.copy(alpha = settings.textShadowIntensity * 0.5f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                            blurRadius = 8f * settings.textShadowIntensity
                        )
                    ) else androidx.compose.ui.text.TextStyle.Default
                )
            }
        }
}

@Composable
private fun ProgressSection(duration: Long, isPlaying: Boolean, viewModel: MusicViewModel, songButtons: SongButtonColors? = null) {
    val c = MaterialTheme.colorScheme
    // Единственное место плеера, которое подписано на тик позиции
    val currentPosition by viewModel.currentPosition.collectAsState()
    val appStyle = com.musicplayer.ui.theme.LocalAppStyle.current
    val settings by viewModel.settings.collectAsState()
    val progress = if (duration > 0L) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    // Keep dragging local to Compose. Calling seekTo() for every pointer event
    // makes MediaPlayer, transition scheduling and bridge/notification updates
    // compete with the slider animation. BoomingMusic commits the seek on release.
    var isScrubbing by remember { mutableStateOf(false) }
    var sliderProgress by remember(duration) { mutableFloatStateOf(progress) }
    LaunchedEffect(progress, isScrubbing) {
        if (!isScrubbing) sliderProgress = progress
    }
    val commitSeek: (Float) -> Unit = { target ->
        val safeTarget = target.coerceIn(0f, 1f)
        sliderProgress = safeTarget
        viewModel.seekTo((safeTarget * duration).toLong())
        isScrubbing = false
    }
    val displayedPosition = if (isScrubbing) (sliderProgress * duration).toLong() else currentPosition
    Column(Modifier.fillMaxWidth()) {
        if (settings.useWavySeekBar) {
            WavyMusicSlider(
                value = sliderProgress,
                onValueChange = { sliderProgress = it; isScrubbing = true },
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxWidth().bleedHorizontally(6.dp),
                trackHeight = appStyle.sliderTrackHeight.dp.coerceIn(3.dp, 16.dp),
                onValueChangeFinished = { commitSeek(sliderProgress) },
                activeTrackColor = songButtons?.sliderActive ?: c.primary,
                inactiveTrackColor = songButtons?.sliderInactive ?: c.surfaceVariant,
                thumbColor = songButtons?.sliderThumb ?: c.primary
            )
        } else {
            Slider(
                value = sliderProgress,
                onValueChange = { sliderProgress = it; isScrubbing = true },
                modifier = Modifier.fillMaxWidth(),
                onValueChangeFinished = { commitSeek(sliderProgress) },
                colors = SliderDefaults.colors(
                    thumbColor = songButtons?.sliderThumb ?: c.primary,
                    activeTrackColor = songButtons?.sliderActive ?: c.primary,
                    inactiveTrackColor = songButtons?.sliderInactive ?: c.surfaceContainerHighest
                )
            )
        }
        // Таймкоды вплотную к волне: цифры стоят ~5dp под нижним краем волны.
        // 1) строка текста с lineHeight = размеру шрифта и trim=Both — внутри строки нет
        //    «воздуха» над цифрами, значит расстояние до волны предсказуемо;
        // 2) layout{} поднимает ряд на 8dp в пустую нижнюю часть слайдера (у него высота 32dp,
        //    а волна занимает только ~10dp вокруг центра) и на столько же уменьшает занимаемую
        //    высоту, чтобы отступ до кнопок управления не вырос.
        val timeStyle = androidx.compose.ui.text.TextStyle(
            lineHeight = settings.playerTimeSize.sp,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
            )
        )
        Row(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val pull = 8.dp.roundToPx()
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, (placeable.height - pull).coerceAtLeast(0)) {
                        placeable.place(0, -pull)
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(displayedPosition.toTimeString(), color = c.textSecondary, fontFamily = LocalAppFontFamily.current, fontSize = settings.playerTimeSize.sp, style = timeStyle)
            Text(duration.toTimeString(), color = c.textSecondary, fontFamily = LocalAppFontFamily.current, fontSize = settings.playerTimeSize.sp, style = timeStyle)
        }
    }
}

@Composable
private fun ControlsSection(settings: com.musicplayer.data.PlayerSettings, isPlaying: Boolean, viewModel: MusicViewModel, songButtons: SongButtonColors? = null) {
    val appStyle = com.musicplayer.ui.theme.LocalAppStyle.current
    // Применяем скругление кнопки из настроек темы
    val btnCorner = appStyle.buttonCornerRadius.dp.coerceIn(4.dp, 60.dp)
    // Центральная кнопка: при воспроизведении — мягкий squircle (≈24dp при радиусе темы 20),
    // на паузе — ещё более скруглённая. Раньше было вдвое меньше (10dp) и углы выглядели острыми.
    val playingCorner = (btnCorner * 1.2f).coerceIn(22.dp, 30.dp)
    val pausedCorner = maxOf(btnCorner, playingCorner * 1.5f).coerceAtMost(60.dp)
    AnimatedPlaybackControls(
        isPlayingProvider = { isPlaying }, onPrevious = { viewModel.playPrevious() }, onPlayPause = { viewModel.togglePlayPause() }, onNext = { viewModel.playNext() },
        modifier = Modifier.fillMaxWidth(), height = 80.dp, pressAnimationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        colorPlayPause = songButtons?.playContainer ?: MaterialTheme.colorScheme.primary,
        tintPlayPauseIcon = songButtons?.onPlayContainer ?: MaterialTheme.colorScheme.onPrimary,
        colorOtherButtons = songButtons?.otherContainer ?: MaterialTheme.colorScheme.secondaryContainer,
        tintOtherIcons = songButtons?.onOtherContainer ?: MaterialTheme.colorScheme.onSecondaryContainer,
        playPauseCornerPlaying = playingCorner, playPauseCornerPaused = pausedCorner
    )
}

@Composable
private fun BottomToggleRow(settings: com.musicplayer.data.PlayerSettings, isFavourite: Boolean, viewModel: MusicViewModel, songId: Long, songButtons: SongButtonColors? = null, onLyricsToggle: () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    val repeatIcon = when(settings.repeatMode) { RepeatMode.NONE -> Icons.Rounded.Repeat; RepeatMode.ALL -> Icons.Rounded.Repeat; RepeatMode.ONE -> Icons.Rounded.RepeatOne }
    // Выключенное состояние — чистые иконки без подложки (цвет как у вторичного текста плеера),
    // цветная «таблетка» появляется только когда режим активен.
    val inactiveIcon = c.textSecondary
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp) // без общей подложки высота 64dp не нужна — место отдано обложке
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ToggleSegmentButton(
                settings.shuffleEnabled, Icons.Rounded.Shuffle, "Перемешать", { viewModel.toggleShuffle() },
                songButtons?.toggleActive ?: c.primary, songButtons?.onToggleActive ?: c.onPrimary,
                inactiveIcon,
                Modifier.weight(1f).fillMaxHeight(), iconAnim = ToggleIconAnim.Shuffle
            )
            ToggleSegmentButton(
                settings.repeatMode != RepeatMode.NONE, repeatIcon, "Повтор", { viewModel.toggleRepeat() },
                songButtons?.toggleActive ?: c.secondary, songButtons?.onToggleActive ?: c.onSecondary,
                inactiveIcon,
                Modifier.weight(1f).fillMaxHeight(), iconAnim = ToggleIconAnim.Repeat
            )
            // Текст песни: четвёртая иконка ряда. Раньше жила в шапке, где из-за неё
            // «СЕЙЧАС ИГРАЕТ» зажималась, а справа было две кнопки против одной слева.
            // Иконка — собственная анимация LyricsAnimatedIcon (пузырёк + нота),
            // поэтому в ToggleIconAnim добавлен режим Lyrics.
            ToggleSegmentButton(
                settings.lyricsOnCover, OutlinedLyrics, "Текст песни", onLyricsToggle,
                songButtons?.toggleActive ?: c.primary, songButtons?.onToggleActive ?: c.onPrimary,
                inactiveIcon,
                Modifier.weight(1f).fillMaxHeight(), iconAnim = ToggleIconAnim.Lyrics
            )
            ToggleSegmentButton(
                isFavourite, if(isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Избранное", { viewModel.toggleFavourite(songId) },
                songButtons?.toggleActive ?: c.tertiary, songButtons?.onToggleActive ?: c.onTertiary,
                inactiveIcon,
                Modifier.weight(1f).fillMaxHeight(), iconAnim = ToggleIconAnim.Favorite
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track Settings — Material 3 bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSettingsOverlay(
    visible: Boolean, volume: Float, playbackSpeed: Float, song: com.musicplayer.data.Song?,
    viewModel: MusicViewModel,
    onVolumeChange: (Float) -> Unit, onSpeedChange: (Float) -> Unit, onSetRingtone: () -> Unit, onDismiss: () -> Unit,
    onEditLyrics: () -> Unit = {}
) {
    val c    = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    var showEqualizerSheet  by remember { mutableStateOf(false) }
    var showEditSection     by remember { mutableStateOf(false) }
    var showLyricsFontScale by remember { mutableStateOf(false) }
    var showInfo            by remember { mutableStateOf(false) }

    val settings        by viewModel.settings.collectAsState()
    val customArtMap    by viewModel.customArtMap.collectAsState()
    val customTitleMap  by viewModel.customTitleMap.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = c.bgSurface,
            contentColor = c.textPrimary,
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = c.textDisabled.copy(0.45f)) },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            val art = song?.let { customArtMap[it.id] ?: it.albumArtUri }
            val displayTitle = song?.let { customTitleMap[it.id] ?: it.title }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Header ────────────────────────────────────────────────
                item(key = "header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (song != null) {
                            OptimizedAlbumArt(
                                uri = art,
                                title = displayTitle ?: song.title,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Настройки трека", color = c.textPrimary, fontFamily = font,
                                fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp
                            )
                            if (song != null && displayTitle != null) {
                                Text(
                                    displayTitle, color = c.textSecondary, fontFamily = font,
                                    fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = c.bgElevated, contentColor = c.textSecondary
                            )
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, "Закрыть") }
                    }
                }

                // ── Volume ────────────────────────────────────────────────
                item(key = "volume") {
                    SheetSection {
                        SheetSectionHeader(Icons.Rounded.VolumeUp, "Громкость", "${(volume * 100).roundToInt()}%", c, font)
                        SheetSlider(volume, 0f, 1f, onVolumeChange, Icons.Rounded.VolumeDown, Icons.Rounded.VolumeUp, c)
                    }
                }

                // ── Speed ─────────────────────────────────────────────────
                item(key = "speed") {
                    SheetSection {
                        SheetSectionHeader(Icons.Rounded.Speed, "Скорость", formatSpeed(playbackSpeed), c, font)
                        SheetSlider(playbackSpeed, 0.25f, 2.0f, onSpeedChange, Icons.Rounded.SlowMotionVideo, Icons.Rounded.Speed, c)
                        PresetRow(
                            options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { it to formatSpeed(it) },
                            isSelected = { kotlin.math.abs(playbackSpeed - it) < 0.01f },
                            onSelect = onSpeedChange,
                            c = c, font = font
                        )
                    }
                }

                // ── Lyrics Font Scale ─────────────────────────────────────
                item(key = "lyrics_scale") {
                    SheetSection {
                        SheetExpandRow(
                            icon = Icons.Rounded.FormatSize, title = "Вид текста песни",
                            subtitle = "Размер ${(settings.lyricsFontScale * 100).roundToInt()}%, затемнение краёв и выравнивание",
                            expanded = showLyricsFontScale, onToggle = { showLyricsFontScale = !showLyricsFontScale },
                            c = c, font = font
                        )
                        AnimatedVisibility(showLyricsFontScale, enter = expandVertically(tween(220)) + fadeIn(tween(220)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                LyricsFontScalePanel(currentScale = settings.lyricsFontScale, onScaleChange = { viewModel.updateSettings(settings.copy(lyricsFontScale = it)) })
                                InnerCard(c) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        InnerCardHeader(Icons.Rounded.BlurOn, "Тени текста песни", c, font)
                                        LyricsFadeStylePicker(currentStyle = settings.lyricsFadeStyle, onStyleChange = { viewModel.updateSettings(settings.copy(lyricsFadeStyle = it)) })
                                    }
                                }
                                InnerCard(c) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        InnerCardHeader(Icons.Rounded.AlignHorizontalLeft, "Выравнивание текста", c, font)
                                        LyricsAlignmentPicker(currentAlignment = settings.lyricsAlignment, onAlignmentChange = { viewModel.updateSettings(settings.copy(lyricsAlignment = it)) })
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Настроить трек ────────────────────────────────────────
                item(key = "edit_track") {
                    SheetSection {
                        SheetExpandRow(
                            icon = Icons.Rounded.Edit, title = "Изменить трек",
                            subtitle = "Своя обложка, название и исполнитель",
                            expanded = showEditSection, onToggle = { showEditSection = !showEditSection },
                            c = c, font = font
                        )
                        AnimatedVisibility(showEditSection, enter = expandVertically(tween(220)) + fadeIn(tween(220)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                            if (song != null) Box(Modifier.padding(top = 16.dp)) { TrackEditPanel(song = song, viewModel = viewModel) }
                        }
                    }
                }

                // ── Actions (grouped list) ────────────────────────────────
                item(key = "actions") {
                    val actions = buildList {
                        if (song != null) {
                            add(SheetAction(Icons.Rounded.Info, "Информация о треке", "Формат, битрейт, размер и расположение файла") { showInfo = true })
                        }
                        if (song != null) {
                            add(SheetAction(Icons.Rounded.Lyrics, "Редактор текста", "Тайминги строк и слов вручную") { onEditLyrics() })
                        }
                        add(SheetAction(Icons.Rounded.Equalizer, "Эквалайзер", "Пресеты, полосы частот и 3D-звук") { showEqualizerSheet = true })
                        add(SheetAction(Icons.Rounded.GraphicEq, "Нормализация громкости", "Одинаковая громкость у разных треков", soon = true) {})
                        add(SheetAction(Icons.Rounded.Tune, "Буст баса", "Усиление низких частот", soon = true) {})
                        add(SheetAction(Icons.Rounded.AddAlert, "Поставить на звонок", "Сделать этот трек мелодией входящего вызова") { onSetRingtone() })
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        actions.forEachIndexed { index, action ->
                            SheetActionRow(action, groupShape(index, actions.size), c, font)
                        }
                    }
                }
            }

            if (showInfo && song != null) TrackInfoDialog(song = song, onDismiss = { showInfo = false })
        }
    }

    if (showEqualizerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEqualizerSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EqualizerSheetContent(viewModel = viewModel, onDismiss = { showEqualizerSheet = false })
        }
    }
}

// ── Sheet helpers ──────────────────────────────────────────────────────────────

/** "1×", "0,75×", "1,25×" - uses the device locale, no dangling separator. */
private fun formatSpeed(v: Float): String {
    val s = String.format(java.util.Locale.getDefault(), "%.2f", v)
    val sep = if (s.contains(',')) ',' else '.'
    val trimmed = if (s.contains(sep)) s.trimEnd('0').trimEnd(sep) else s
    return "$trimmed×"
}

private class SheetAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val soon: Boolean = false,
    val onClick: () -> Unit
)

/** M3 Expressive grouped list: big outer corners, small inner corners. */
private fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    return when {
        count == 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

@Composable
private fun SheetSection(content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = c.bgElevated
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), content = content)
    }
}

@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, c: ColorScheme) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.accent.copy(0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SheetSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, value: String,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBadge(icon, c)
        Text(
            title, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f)
        )
        Surface(shape = RoundedCornerShape(50), color = c.accent.copy(0.16f)) {
            Text(
                value, color = c.accent, fontFamily = font, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, maxLines = 1, softWrap = false,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SheetSlider(
    value: Float, min: Float, max: Float, onChange: (Float) -> Unit,
    startIcon: androidx.compose.ui.graphics.vector.ImageVector,
    endIcon: androidx.compose.ui.graphics.vector.ImageVector,
    c: ColorScheme
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(startIcon, null, tint = c.textDisabled, modifier = Modifier.size(20.dp))
        Slider(
            value, onChange, valueRange = min..max,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.bgCard.copy(0.8f)
            )
        )
        Icon(endIcon, null, tint = c.textDisabled, modifier = Modifier.size(20.dp))
    }
}

/** Row of small selectable "chips" with equal width (speed / text-size presets). */
@Composable
private fun PresetRow(
    options: List<Pair<Float, String>>,
    isSelected: (Float) -> Boolean,
    onSelect: (Float) -> Unit,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?
) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            PresetChip(label, isSelected(value), Modifier.weight(1f), c, font) { onSelect(value) }
        }
    }
}

@Composable
private fun PresetChip(
    label: String, selected: Boolean, modifier: Modifier,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?,
    onClick: () -> Unit
) {
    val container by animateColorAsState(if (selected) c.accent else Color.Transparent, label = "chipBg")
    val content by animateColorAsState(if (selected) c.bgDeep else c.textSecondary, label = "chipFg")
    val border by animateColorAsState(if (selected) c.accent else c.textDisabled.copy(0.35f), label = "chipBorder")
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = content, fontFamily = font, fontSize = 12.sp, maxLines = 1, softWrap = false,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SheetExpandRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, subtitle: String,
    expanded: Boolean, onToggle: () -> Unit,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "expandChevron"
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconBadge(icon, c)
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
            Text(subtitle, color = c.textSecondary, fontFamily = font, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(c.bgSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.ExpandMore, null, tint = c.textSecondary,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    action: SheetAction,
    shape: androidx.compose.ui.graphics.Shape,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?
) {
    Surface(
        onClick = action.onClick,
        enabled = !action.soon,
        shape = shape,
        color = c.bgElevated,
        contentColor = c.textPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .graphicsLayer { alpha = if (action.soon) 0.6f else 1f }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconBadge(action.icon, c)
            // The text column takes only what's left after the trailing badge/chevron, so the badge can never be squeezed.
            Column(Modifier.weight(1f)) {
                Text(
                    action.title, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    action.subtitle, color = c.textSecondary, fontFamily = font,
                    fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (action.soon) {
                Surface(shape = RoundedCornerShape(50), color = c.accent.copy(0.16f)) {
                    Text(
                        "Скоро", color = c.accent, fontFamily = font, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else {
                Icon(Icons.Rounded.ChevronRight, null, tint = c.textDisabled, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun InnerCard(c: ColorScheme, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = c.bgSurface
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun InnerCardHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
        Text(title, color = c.textPrimary, fontFamily = font, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Track Edit Panel ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackEditPanel(song: com.musicplayer.data.Song, viewModel: MusicViewModel) {
    val c       = MaterialTheme.colorScheme
    val font    = LocalAppFontFamily.current

    val customTitleMap  by viewModel.customTitleMap.collectAsState()
    val customArtistMap by viewModel.customArtistMap.collectAsState()
    val customArtMap    by viewModel.customArtMap.collectAsState()

    var titleText  by remember(song.id) { mutableStateOf(customTitleMap[song.id] ?: song.title) }
    var artistText by remember(song.id) { mutableStateOf(customArtistMap[song.id] ?: song.artist.takeIf { it != "<unknown>" } ?: "") }
    val customArt  = customArtMap[song.id]
    val currentArt = customArt ?: song.albumArtUri

    val artPickerForCurrent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.setCustomArt(song.id, it) } }
    val artPickerForAll     = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.setCustomArtForAll(it) } }

    var saved by remember(song.id) { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent,
        unfocusedBorderColor = c.textDisabled.copy(0.45f),
        focusedLabelColor = c.accent,
        unfocusedLabelColor = c.textSecondary,
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        cursorColor = c.accent,
        focusedLeadingIconColor = c.accent,
        unfocusedLeadingIconColor = c.textSecondary,
        focusedTrailingIconColor = c.textSecondary,
        unfocusedTrailingIconColor = c.textSecondary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent
    )
    val fieldTextStyle = TextStyle(fontFamily = font, fontSize = 16.sp)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── Обложка ───────────────────────────────────────────────────────
        InnerCard(c) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OptimizedAlbumArt(
                        uri = currentArt,
                        title = song.title,
                        modifier = Modifier.size(104.dp).clip(RoundedCornerShape(24.dp)),
                        targetSize = CoilSize(320, 320)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { artPickerForCurrent.launch("image/*") },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.accent.copy(0.18f), contentColor = c.accent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Image, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Выбрать обложку", fontFamily = font, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                        if (customArt != null) {
                            TextButton(
                                onClick = { viewModel.setCustomArt(song.id, null) },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = c.textSecondary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Вернуть исходную", fontFamily = font, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                HorizontalDivider(color = c.textDisabled.copy(0.2f))

                Text(
                    "Для всех треков", color = c.textSecondary, fontFamily = font,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp
                )
                OutlinedButton(
                    onClick = { artPickerForAll.launch("image/*") },
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.textDisabled.copy(0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.SelectAll, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Применить ко всем", fontFamily = font, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = { viewModel.resetAllCustomArt() },
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.textDisabled.copy(0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.HideImage, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Сбросить все обложки", fontFamily = font, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
        }

        // ── Название ──────────────────────────────────────────────────────
        OutlinedTextField(
            value = titleText,
            onValueChange = { titleText = it; saved = false },
            label = { Text("Название", fontFamily = font) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            textStyle = fieldTextStyle,
            leadingIcon = { Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(22.dp)) },
            trailingIcon = if (titleText.isNotEmpty()) {
                {
                    IconButton(onClick = { titleText = ""; saved = false }) {
                        Icon(Icons.Rounded.Close, "Очистить", modifier = Modifier.size(20.dp))
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )

        // ── Исполнитель ───────────────────────────────────────────────────
        OutlinedTextField(
            value = artistText,
            onValueChange = { artistText = it; saved = false },
            label = { Text("Исполнитель", fontFamily = font) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            textStyle = fieldTextStyle,
            leadingIcon = { Icon(Icons.Rounded.Person, null, modifier = Modifier.size(22.dp)) },
            trailingIcon = if (artistText.isNotEmpty()) {
                {
                    IconButton(onClick = { artistText = ""; saved = false }) {
                        Icon(Icons.Rounded.Close, "Очистить", modifier = Modifier.size(20.dp))
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )

        // ── Кнопка сохранить ──────────────────────────────────────────────
        val saveContainer by animateColorAsState(if (saved) c.accentMuted else c.accent, label = "saveBg")
        Button(
            onClick = {
                viewModel.setCustomTitle(song.id, titleText.trim().takeIf { it.isNotBlank() })
                viewModel.setCustomArtist(song.id, artistText.trim().takeIf { it.isNotBlank() })
                saved = true
            },
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = saveContainer, contentColor = c.bgDeep),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            AnimatedContent(targetState = saved, label = "saveLabel") { isSaved ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (isSaved) Icons.Rounded.CheckCircle else Icons.Rounded.Save, null, modifier = Modifier.size(20.dp))
                    Text(if (isSaved) "Сохранено" else "Сохранить", fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        Text(
            "Изменения видны только в Glowpath. Теги самого файла не меняются.",
            color = c.textSecondary, fontFamily = font, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// ── Lyrics Font Scale Panel ────────────────────────────────────────────────────
@Composable
fun LyricsFontScalePanel(
    currentScale: Float,
    onScaleChange: (Float) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    val sampleLines = listOf(
        "Это пример текста песни",
        "The quick brown fox",
        "Просто красивые слова",
        "Just example lyrics here"
    )
    val previewFontSize = (18f * currentScale).sp

    InnerCard(c) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Preview: shows how the lyrics will look
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.bgDeep)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sampleLines.forEachIndexed { idx, line ->
                        Text(
                            text = line,
                            color = if (idx == 1) c.textPrimary else c.textPrimary.copy(alpha = 0.4f),
                            fontFamily = font,
                            fontSize = previewFontSize,
                            lineHeight = (previewFontSize.value * 1.4f).sp
                        )
                    }
                }
            }

            // Scale value
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.FormatSize, null, tint = c.accent, modifier = Modifier.size(20.dp))
                Text(
                    "Масштаб", color = c.textPrimary, fontFamily = font, fontSize = 14.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(50), color = c.accent.copy(0.16f)) {
                    Text(
                        "${(currentScale * 100).roundToInt()}%",
                        color = c.accent, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Slider (snaps to 5% steps, no tick marks)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.ZoomOut, null, tint = c.textDisabled, modifier = Modifier.size(20.dp))
                Slider(
                    value = currentScale,
                    onValueChange = { onScaleChange((it * 20f).roundToInt() / 20f) },
                    valueRange = 0.6f..2.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = c.accent,
                        activeTrackColor = c.accent,
                        inactiveTrackColor = c.bgCard
                    )
                )
                Icon(Icons.Rounded.ZoomIn, null, tint = c.textDisabled, modifier = Modifier.size(20.dp))
            }

            // Quick presets
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(0.75f to "S", 1.0f to "M", 1.25f to "L", 1.5f to "XL", 1.75f to "XXL").forEach { (scale, label) ->
                    PresetChip(
                        label = label,
                        selected = kotlin.math.abs(currentScale - scale) < 0.05f,
                        modifier = Modifier.weight(1f),
                        c = c, font = font
                    ) { onScaleChange(scale) }
                }
            }
        }
    }
}

/** Square-ish selectable tile with an icon and a label (used by the lyrics pickers). */
@Composable
private fun OptionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?,
    onClick: () -> Unit
) {
    val container by animateColorAsState(if (selected) c.accent else c.bgElevated, label = "tileBg")
    val content by animateColorAsState(if (selected) c.bgDeep else c.textSecondary, label = "tileFg")
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(22.dp))
        Text(
            label, color = content, fontFamily = font, fontSize = 12.sp, maxLines = 1, softWrap = false,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ── Lyrics Fade Style Picker ───────────────────────────────────────────────────
@Composable
fun LyricsFadeStylePicker(
    currentStyle: Int,
    onStyleChange: (Int) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    val styles = listOf(
        Triple(0, Icons.Rounded.Circle, "Без теней"),
        Triple(1, Icons.Rounded.BlurOn, "Мягкий"),
        Triple(2, Icons.Rounded.Layers, "Сильный"),
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEach { (id, icon, label) ->
            OptionTile(icon, label, currentStyle == id, Modifier.weight(1f), c, font) { onStyleChange(id) }
        }
    }
}

// ── Lyrics Alignment Picker ───────────────────────────────────────────────────
@Composable
fun LyricsAlignmentPicker(
    currentAlignment: Int,
    onAlignmentChange: (Int) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    val options = listOf(
        Triple(0, Icons.Rounded.AlignHorizontalLeft, "Слева"),
        Triple(1, Icons.Rounded.AlignHorizontalCenter, "По центру"),
        Triple(2, Icons.Rounded.AlignHorizontalRight, "Справа"),
    )

    val previewAlign = when (currentAlignment) {
        1 -> TextAlign.Center
        2 -> TextAlign.End
        else -> TextAlign.Start
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.bgDeep)
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Это пример текста песни", "Как будут выглядеть строки", "При выбранном выравнивании").forEachIndexed { idx, line ->
                    Text(
                        line,
                        color = if (idx == 1) c.textPrimary else c.textPrimary.copy(0.4f),
                        fontFamily = font,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = previewAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (id, icon, label) ->
                OptionTile(icon, label, currentAlignment == id, Modifier.weight(1f), c, font) { onAlignmentChange(id) }
            }
        }
    }
}

private fun setAsRingtone(context: Context, uri: Uri) {
    try {
        if (!Settings.System.canWrite(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply { data = Uri.parse("package:${context.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            Toast.makeText(context, "Разрешите изменение системных настроек и повторите", Toast.LENGTH_LONG).show()
            return
        }
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
        Toast.makeText(context, "Мелодия звонка установлена ✓", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
