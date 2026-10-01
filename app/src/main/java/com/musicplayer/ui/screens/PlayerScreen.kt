package com.musicplayer.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.musicplayer.MiniPlayer
import com.musicplayer.data.RepeatMode
import com.musicplayer.data.SongColorCache
import com.musicplayer.data.toTimeString
import com.musicplayer.ui.components.*
import com.musicplayer.ui.components.instrumentIconRes
import com.musicplayer.ui.theme.*
import androidx.compose.ui.util.lerp as lerpFloat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.musicplayer.viewmodel.MusicViewModel
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos

import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*

@Composable
fun PlayerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onLyricsClick: () -> Unit = {}
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val song            by viewModel.currentSong.collectAsState()
    val isPlaying       by viewModel.isPlaying.collectAsState()
    val duration        by viewModel.duration.collectAsState()
    // Позиция тикает 5 раз в секунду. Читаем её ТОЛЬКО в ProgressSection (и в backdrop при сворачивании),
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
    // ── Swipe-down to close ───────────────────────────────────────────────────
    var swipeOffsetY by remember { mutableFloatStateOf(0f) }
    var isSwipeDragging by remember { mutableStateOf(false) }
    var isSwipeClosing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val swipeDismissDistance = with(density) { 420.dp.toPx() }  // longer travel for full morph into mini bar
    val swipeCloseThreshold = with(density) { 140.dp.toPx() }
    val animatedOffset by animateFloatAsState(
        targetValue = swipeOffsetY,
        animationSpec = if (isSwipeDragging) {
            snap()
        } else {
            spring(
                dampingRatio = 0.86f,
                stiffness = 380f
            )
        },
        label = "sw"
    )
    val swipeProgress = (animatedOffset / swipeDismissDistance).coerceIn(0f, 1f)
    // Stronger shrink + softer alpha so the panel morphs into the mini-player bar (YouTube Music style)
    val swipeScale    = lerpFloat(1f, 0.42f, FastOutSlowInEasing.transform(swipeProgress))
    val swipeAlpha    = (1f - (swipeProgress * 0.92f)).coerceIn(0f, 1f)
    val swipeCorner   = lerpFloat(0f, 28f,   FastOutSlowInEasing.transform(swipeProgress))

    var showTrackSettings by remember { mutableStateOf(false) }

    // Keep the full-screen artwork stable. The previous pulse animation resized the
    // image on every frame and made the cover visibly jump while playback progressed.
    val artScale = 1f

    val scope = rememberCoroutineScope()
    val openProgress = remember { Animatable(0f) }
    val screenEntryProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        openProgress.snapTo(0f)
        openProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = 300f
            )
        )
    }
    LaunchedEffect(Unit) {
        screenEntryProgress.snapTo(0f)
        screenEntryProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = 300f
            )
        )
    }

    val activeQueueCount = remember(song!!.id, songs, bridgeQueueUris, settings.sortOrder, settings.shuffleEnabled, queueRevision) {
        viewModel.buildActiveQueueSnapshot().size
    }
    // Continuous morph: show MiniPlayer under during both open and collapse (YT Music style)
    val collapseBackdropReveal = when {
        isSwipeClosing || swipeProgress > 0.005f -> {
            // Collapse / swipe-down: progressively reveal the mini-player under the shrinking full player
            (swipeProgress * 1.25f).coerceIn(0f, 1f)
        }
        else -> {
            // Open: start with mini visible, fade it out as the full player expands over it
            (1f - screenEntryProgress.value).coerceIn(0f, 1f)
        }
    }
    val entryProgress = FastOutSlowInEasing.transform(screenEntryProgress.value.coerceIn(0f, 1f))
    val playerBackgroundAlpha = ((1f - swipeProgress.coerceIn(0f, 1f)) * entryProgress).coerceIn(0f, 1f)
    val entryOffsetY = with(density) { lerpFloat(96.dp.toPx(), 0f, entryProgress) }
    val entryScale = lerpFloat(0.88f, 1f, entryProgress)
    val entryAlpha = ((entryProgress - 0.02f) / 0.98f).coerceIn(0f, 1f)
    val entryCorner = lerpFloat(28f, 0f, entryProgress)  // matches MiniPlayer RoundedCornerShape(28.dp)

    suspend fun animateCloseAndExit(startOffset: Float = swipeOffsetY) {
        if (isSwipeClosing) return
        isSwipeClosing = true
        isSwipeDragging = false
        swipeOffsetY = startOffset.coerceIn(0f, swipeDismissDistance)
        // Drive fully to the collapsed (mini-player) state so the morph finishes cleanly
        swipeOffsetY = swipeDismissDistance * 1.05f
        withTimeoutOrNull(520L) {
            snapshotFlow { animatedOffset }
                .first { it >= swipeDismissDistance * 0.97f }
        }
        // Tiny settle so the underlaid MiniPlayer is fully opaque before we pop the route
        kotlinx.coroutines.delay(32)
        onBack()
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    c.surfaceColorAtElevation(NavigationBarDefaults.Elevation)
                        .copy(alpha = playerBackgroundAlpha)
                )
        )

        AnimatedContent(
            targetState = artworkGradientColors,
            transitionSpec = {
                fadeIn(tween(1000)).togetherWith(fadeOut(tween(1000)))
            },
            label = "playerArtworkGradient"
        ) { colors ->
            if (colors.size >= 2) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(colors),
                            alpha = 0.4f * playerBackgroundAlpha
                        )
                )
            }
        }

        PlayerCollapseBackdrop(
            title = song!!.title,
            artist = if (song!!.artist != "<unknown>") song!!.artist else "Неизвестный",
            albumArtUri = artworkUri,
            isPlaying = isPlaying,
            viewModel = viewModel,
            duration = duration,
            revealProgress = collapseBackdropReveal,
            queueCount = activeQueueCount,
            onPlayPause = { viewModel.togglePlayPause() },
            onPrevious = { viewModel.playPrevious() },
            onNext = { viewModel.playNext() },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationY = animatedOffset.coerceAtLeast(0f) + entryOffsetY
                    scaleX = swipeScale * entryScale
                    scaleY = swipeScale * entryScale
                    alpha = swipeAlpha * entryAlpha
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    clip = swipeProgress > 0f || entryProgress < 0.999f
                    shape = RoundedCornerShape(max(swipeCorner, entryCorner).dp)
                }
                .pointerInput(showTrackSettings, isSwipeClosing) {
                    if (!showTrackSettings && !isSwipeClosing) detectVerticalDragGestures(
                        onDragStart = { isSwipeDragging = true },
                        onDragEnd    = {
                            isSwipeDragging = false
                            if (swipeOffsetY >= swipeCloseThreshold) {
                                scope.launch { animateCloseAndExit(swipeOffsetY.coerceAtLeast(swipeCloseThreshold)) }
                            } else {
                                swipeOffsetY = 0f
                            }
                        },
                        onDragCancel = {
                            isSwipeDragging = false
                            swipeOffsetY = 0f
                        },
                        onVerticalDrag = { ch, dy ->
                            ch.consume()
                            val dragMultiplier = if (dy >= 0f) 0.92f else 1.08f
                            swipeOffsetY = (swipeOffsetY + dy * dragMultiplier)
                                .coerceIn(0f, swipeDismissDistance)
                        }
                    )
                }
        ) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.textDisabled.copy(0.35f + (swipeOffsetY / 180f).coerceIn(0f, 0.65f))))

            val doFlipNext: () -> Unit = { viewModel.playNext() }
            val doFlipPrev: () -> Unit = { viewModel.playPrevious() }
            val requestClose: () -> Unit = { scope.launch { animateCloseAndExit() } }

            if (isLandscape) {
                LandscapePlayerContent(song!!, isPlaying, duration, settings, isFavourite, artScale, viewModel, customArtUri, previousCover, nextCover, requestClose, onLyricsClick, { showTrackSettings = true }, doFlipNext, doFlipPrev, swipeProgress, openProgress.value)
            } else {
                PlayerQueueHost(
                    viewModel = viewModel,
                    song = song!!,
                    isPlaying = isPlaying,
                    gradientColors = artworkGradientColors
                ) { queueSlots ->
                    PortraitPlayerContent(song!!, isPlaying, duration, settings, isFavourite, artScale, viewModel, customArtUri, previousCover, nextCover, requestClose, onLyricsClick, { showTrackSettings = true }, doFlipNext, doFlipPrev, swipeProgress, openProgress.value, queueSlots)
                }
            }
        }

        TrackSettingsOverlay(
            visible = showTrackSettings, volume = volume, playbackSpeed = playbackSpeed, song = song,
            viewModel = viewModel,
            onVolumeChange = { viewModel.setVolume(it) }, onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onSetRingtone = { song?.let { setAsRingtone(context, it.uri) }; showTrackSettings = false },
            onDismiss = { showTrackSettings = false }
        )
    }
}

@Composable
private fun PlayerCollapseBackdrop(
    title: String,
    artist: String,
    albumArtUri: Uri?,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    duration: Long,
    revealProgress: Float,
    queueCount: Int,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (revealProgress <= 0f) return

    // Позицию собираем только пока backdrop реально виден (идёт сворачивание плеера)
    val position by viewModel.currentPosition.collectAsState()
    val progress = if (duration > 0L) position.toFloat() / duration.toFloat() else 0f

    val density = LocalDensity.current
    val revealAlpha = FastOutSlowInEasing.transform(revealProgress).coerceIn(0f, 1f)
    val contentLift = with(density) { lerpFloat(28.dp.toPx(), 0f, revealAlpha) }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = revealAlpha }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            MiniPlayer(
                title = title,
                artist = artist,
                albumArtUri = albumArtUri,
                isPlaying = isPlaying,
                progress = progress,
                queueCount = queueCount,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onMoreClick = {},
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Stay fully solid once mostly revealed so handoff to real MiniPlayer is invisible
                        alpha = 0.55f + revealAlpha * 0.45f
                        translationY = contentLift
                    }
            )
        }
    }
}

@Composable
private fun PortraitPlayerContent(
    song: com.musicplayer.data.Song, isPlaying: Boolean, duration: Long,
    settings: com.musicplayer.data.PlayerSettings, isFavourite: Boolean, artScale: Float,
    viewModel: MusicViewModel, customArtUri: android.net.Uri?, previousCover: CoverItem?, nextCover: CoverItem?,
    onBack: () -> Unit, onLyricsClick: () -> Unit, onSettingsClick: () -> Unit,
    onSwipeNext: () -> Unit, onSwipePrev: () -> Unit, swipeProgress: Float = 0f, openProgress: Float = 1f,
    queueSlots: PlayerQueueSlots
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
    val contentReveal = (((openProgress - 0.34f) / 0.66f).coerceIn(0f, 1f) * (1f - swipeProgress * 1.1f)).coerceIn(0f, 1f)
    val contentOffset = with(density) { lerpFloat(34.dp.toPx(), 0f, contentReveal) }
    val topReveal = (((openProgress - 0.18f) / 0.82f).coerceIn(0f, 1f) * (1f - swipeProgress * 1.15f)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer {
            alpha = topReveal
            translationY = lerpFloat((-18).dp.toPx(), 0f, topReveal)
        }) {
            TopBar(onBack, settings.lyricsOnCover, toggleLyrics, onSettingsClick, swipeProgress)
        }
        Spacer(Modifier.height(20.dp))
        // Обложка получает всё свободное место (weight) и стоит в нём по центру, а блок
        // управления прижат вниз — поэтому «Дальше» всегда лежит прямо над шторкой очереди.
        // Размер обложки = всё свободное место (до 360dp). Нажатие на «Текст» плавно
        // заменяет обложку на текст песни в этом же месте (как в Booming).
        CoverWithLyrics(
            viewModel = viewModel,
            song = song,
            lyricsProgress = lyricsProgress,
            onExpand = onLyricsClick,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { coverScale ->
            AlbumArtSection(song, isPlaying, artScale * coverScale, customArtUri, previousCover, nextCover, onSwipeNext, onSwipePrev, { viewModel.setCustomArt(song.id, null) }, settings.albumArtAnim, settings.animParams, openProgress)
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.graphicsLayer {
            alpha = contentReveal
            translationY = contentOffset
        }) {
            SongMetaSection(song, viewModel)
            Spacer(Modifier.height(24.dp))
            ProgressSection(duration, isPlaying, viewModel)
            Spacer(Modifier.height(24.dp))
            ControlsSection(settings, isPlaying, viewModel)
            Spacer(Modifier.height(14.dp))
            BottomToggleRow(settings, isFavourite, viewModel, song.id)
            Spacer(Modifier.height(8.dp))
            queueSlots.nextUp()
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LandscapePlayerContent(
    song: com.musicplayer.data.Song, isPlaying: Boolean, duration: Long,
    settings: com.musicplayer.data.PlayerSettings, isFavourite: Boolean, artScale: Float,
    viewModel: MusicViewModel, customArtUri: android.net.Uri?, previousCover: CoverItem?, nextCover: CoverItem?,
    onBack: () -> Unit, onLyricsClick: () -> Unit, onSettingsClick: () -> Unit,
    onSwipeNext: () -> Unit, onSwipePrev: () -> Unit, swipeProgress: Float = 0f, openProgress: Float = 1f
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
    val sideReveal = (((openProgress - 0.28f) / 0.72f).coerceIn(0f, 1f) * (1f - swipeProgress * 1.1f)).coerceIn(0f, 1f)
    val sideOffset = with(density) { lerpFloat(26.dp.toPx(), 0f, sideReveal) }
    Row(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.graphicsLayer {
                alpha = sideReveal
                translationY = lerpFloat((-14).dp.toPx(), 0f, sideReveal)
            }) {
                TopBar(onBack, settings.lyricsOnCover, toggleLyrics, onSettingsClick, swipeProgress)
            }
            Spacer(Modifier.height(8.dp))
            CoverWithLyrics(
                viewModel = viewModel,
                song = song,
                lyricsProgress = lyricsProgress,
                onExpand = onLyricsClick,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { coverScale ->
                AlbumArtSection(song, isPlaying, artScale * coverScale, customArtUri, previousCover, nextCover, onSwipeNext, onSwipePrev, { viewModel.setCustomArt(song.id, null) }, settings.albumArtAnim, settings.animParams, openProgress)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).fillMaxHeight().graphicsLayer {
            alpha = sideReveal
            translationY = sideOffset
        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            SongMetaSection(song, viewModel)
            ProgressSection(duration, isPlaying, viewModel)
            ControlsSection(settings, isPlaying, viewModel)
            BottomToggleRow(settings, isFavourite, viewModel, song.id)
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, lyricsVisible: Boolean, onLyricsClick: () -> Unit, onSettingsClick: () -> Unit, swipeProgress: Float = 0f) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Text("СЕЙЧАС ИГРАЕТ", color = c.textDisabled, fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.1f.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        AnimatedBackButton(
            onBack = onBack,
            containerColor = c.surfaceContainerLow,
            iconColor = c.onSurface,
            swipeProgress = swipeProgress
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LyricsToggleButton(
                selected = lyricsVisible,
                onClick = onLyricsClick,
                containerColor = c.surfaceContainerLow,
                contentColor = c.onSurface,
                selectedContainerColor = c.primary,
                selectedContentColor = c.onPrimary
            )
            FilledIconButton(
                onSettingsClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = c.surfaceContainerLow,
                    contentColor = c.onSurface
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
    openProgress: Float = 1f
) {
    val c = MaterialTheme.colorScheme
    val appStyle = com.musicplayer.ui.theme.LocalAppStyle.current
    val artCorner = androidx.compose.ui.unit.Dp(appStyle.cardCornerRadius.coerceIn(8f, 64f))
    val artShape = RoundedCornerShape(artCorner)

    Box(contentAlignment = Alignment.BottomEnd) {
        CoverSwipeCarousel(
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
private fun ProgressSection(duration: Long, isPlaying: Boolean, viewModel: MusicViewModel) {
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
                modifier = Modifier.fillMaxWidth(),
                trackHeight = appStyle.sliderTrackHeight.dp.coerceIn(3.dp, 16.dp),
                onValueChangeFinished = { commitSeek(sliderProgress) }
            )
        } else {
            Slider(
                value = sliderProgress,
                onValueChange = { sliderProgress = it; isScrubbing = true },
                modifier = Modifier.fillMaxWidth(),
                onValueChangeFinished = { commitSeek(sliderProgress) },
                colors = SliderDefaults.colors(
                    thumbColor = c.primary,
                    activeTrackColor = c.primary,
                    inactiveTrackColor = c.surfaceContainerHighest
                )
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(displayedPosition.toTimeString(), color = c.textSecondary, fontFamily = LocalAppFontFamily.current, fontSize = settings.playerTimeSize.sp)
            Text(duration.toTimeString(), color = c.textSecondary, fontFamily = LocalAppFontFamily.current, fontSize = settings.playerTimeSize.sp)
        }
    }
}

@Composable
private fun ControlsSection(settings: com.musicplayer.data.PlayerSettings, isPlaying: Boolean, viewModel: MusicViewModel) {
    val appStyle = com.musicplayer.ui.theme.LocalAppStyle.current
    // Применяем скругление кнопки из настроек темы
    val btnCorner = appStyle.buttonCornerRadius.dp.coerceIn(4.dp, 60.dp)
    AnimatedPlaybackControls(
        isPlayingProvider = { isPlaying }, onPrevious = { viewModel.playPrevious() }, onPlayPause = { viewModel.togglePlayPause() }, onNext = { viewModel.playNext() },
        modifier = Modifier.fillMaxWidth(), height = 80.dp, pressAnimationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        colorPlayPause = MaterialTheme.colorScheme.primary,
        tintPlayPauseIcon = MaterialTheme.colorScheme.onPrimary,
        colorOtherButtons = MaterialTheme.colorScheme.secondaryContainer,
        tintOtherIcons = MaterialTheme.colorScheme.onSecondaryContainer,
        playPauseCornerPlaying = btnCorner, playPauseCornerPaused = (btnCorner * 0.5f).coerceIn(4.dp, 30.dp)
    )
}

@Composable
private fun BottomToggleRow(settings: com.musicplayer.data.PlayerSettings, isFavourite: Boolean, viewModel: MusicViewModel, songId: Long) {
    val c = MaterialTheme.colorScheme
    val repeatIcon = when(settings.repeatMode) { RepeatMode.NONE -> Icons.Rounded.Repeat; RepeatMode.ALL -> Icons.Rounded.Repeat; RepeatMode.ONE -> Icons.Rounded.RepeatOne }
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.fillMaxSize().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ToggleSegmentButton(settings.shuffleEnabled, Icons.Rounded.Shuffle, "Перемешать", { viewModel.toggleShuffle() }, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f).fillMaxHeight(), iconAnim = ToggleIconAnim.Shuffle)
            ToggleSegmentButton(settings.repeatMode != RepeatMode.NONE, repeatIcon, "Повтор", { viewModel.toggleRepeat() }, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f).fillMaxHeight(), iconAnim = ToggleIconAnim.Repeat)
            ToggleSegmentButton(isFavourite, if(isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Избранное", { viewModel.toggleFavourite(songId) }, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f).fillMaxHeight(), iconAnim = ToggleIconAnim.Favorite)
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
    onVolumeChange: (Float) -> Unit, onSpeedChange: (Float) -> Unit, onSetRingtone: () -> Unit, onDismiss: () -> Unit
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
