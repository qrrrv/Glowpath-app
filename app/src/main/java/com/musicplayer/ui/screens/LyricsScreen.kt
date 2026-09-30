package com.musicplayer.ui.screens

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size as CoilSize
import com.musicplayer.ui.components.FadingEdges
import com.musicplayer.ui.components.LyricsLine
import com.musicplayer.ui.components.LyricsView
import com.musicplayer.ui.components.OptimizedAlbumArt
import com.musicplayer.ui.components.buildLyricsLines
import com.musicplayer.ui.components.findLyricsLineIndex
import com.musicplayer.R
import com.musicplayer.data.lyrics.LyricsState
import com.musicplayer.data.lyrics.SyncedLine
import com.musicplayer.ui.components.PlayingEqIcon
import com.musicplayer.ui.theme.*
import kotlinx.coroutines.isActive
import com.musicplayer.viewmodel.MusicViewModel
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.airbnb.lottie.compose.*

@Composable
private fun rememberSmoothPlaybackPosition(
    songId: Long,
    playbackPosition: Long,
    playbackSpeed: Float,
    isPlaying: Boolean
): Long {
    var position by remember(songId) { mutableLongStateOf(playbackPosition) }

    // The player position flow is intentionally coarse. Interpolate between its
    // updates on frame boundaries so the lyric transition happens at the exact
    // timestamp instead of waiting for the next StateFlow emission.
    LaunchedEffect(songId, playbackPosition, playbackSpeed, isPlaying) {
        val baseRealtime = SystemClock.elapsedRealtime()
        if (!isPlaying) {
            position = playbackPosition
            return@LaunchedEffect
        }

        while (isActive) {
            withFrameNanos {
                val elapsed = SystemClock.elapsedRealtime() - baseRealtime
                position = playbackPosition + (elapsed * playbackSpeed).toLong()
            }
        }
    }

    return position
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val c       = MaterialTheme.colorScheme
    val haptic  = LocalHapticFeedback.current
    val density = LocalDensity.current

    val song            by viewModel.currentSong.collectAsState()
    val isPlaying       by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val playbackSpeed   by viewModel.playbackSpeed.collectAsState()
    val lyricsState     by viewModel.lyricsState.collectAsState()
    val customArtMap    by viewModel.customArtMap.collectAsState()
    val customTitleMap  by viewModel.customTitleMap.collectAsState()
    val customArtistMap by viewModel.customArtistMap.collectAsState()
    val settings        by viewModel.settings.collectAsState()
    val lyricsFontFamily = LocalAppFontFamily.current
    val lyricsFontScale = settings.lyricsFontScale
    val lyricsAlignment = settings.lyricsAlignment   // 0=left, 1=center, 2=right
    val lyricsCurlAnim  = settings.lyricsCurlAnim

    if (song == null) { LaunchedEffect(Unit) { onBack() }; return }

    // Автоматически загружаем текст если ещё не загружен (может быть Idle при первом открытии)
    LaunchedEffect(song?.id) {
        val state = viewModel.lyricsState.value
        if (state is com.musicplayer.data.lyrics.LyricsState.Idle ||
            state is com.musicplayer.data.lyrics.LyricsState.NotFound) {
            song?.let { viewModel.loadLyricsForSong(it) }
        }
    }

    val customArtUri = customArtMap[song!!.id]

    // ── Immersive mode ────────────────────────────────────────────────────────
    var immersiveMode   by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun resetImmersive() { lastInteraction = System.currentTimeMillis(); immersiveMode = false }

    val hasLyrics = lyricsState is LyricsState.Found

    LaunchedEffect(isPlaying, lastInteraction, hasLyrics) {
        if (isPlaying && hasLyrics) {
            kotlinx.coroutines.delay(5_000L)
            if (System.currentTimeMillis() - lastInteraction >= 4_900L) immersiveMode = true
        } else {
            immersiveMode = false
        }
    }

    // ── Vinyl rotation ────────────────────────────────────────────────────────
    val vinylRotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                vinylRotation.animateTo(vinylRotation.value + 360f, tween(5_000, easing = LinearEasing))
            }
        } else { vinylRotation.stop() }
    }

    // ── Swipe skip ────────────────────────────────────────────────────────────
    var dragOffsetX   by remember { mutableFloatStateOf(0f) }
    var isSwipeActive by remember { mutableStateOf(false) }
    val swipeThresholdPx = with(density) { 100.dp.toPx() }
    val swipeProgress    = remember { Animatable(0f) }
    val scope            = rememberCoroutineScope()

    // ── Song change slide animation ──────────────────────────────────────────
    var slideDirection by remember { mutableIntStateOf(0) } // -1=left, 1=right
    val slideOffsetX = remember { Animatable(0f) }
    var prevSongIdLyrics by remember { mutableLongStateOf(song!!.id) }
    LaunchedEffect(song!!.id) {
        if (prevSongIdLyrics != song!!.id) {
            val enterFrom = if (slideDirection >= 0) -1f else 1f
            slideOffsetX.snapTo(enterFrom)
            slideOffsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
            slideDirection = 0
        }
        prevSongIdLyrics = song!!.id
    }

    // ── Karaoke current line ──────────────────────────────────────────────────
    val syncedLines: List<SyncedLine>? = (lyricsState as? LyricsState.Found)?.lyrics?.synced
    val plainLines:  List<String>?     = (lyricsState as? LyricsState.Found)?.lyrics?.plain
    val smoothPosition = rememberSmoothPlaybackPosition(
        songId = song!!.id,
        playbackPosition = currentPosition,
        playbackSpeed = playbackSpeed,
        isPlaying = isPlaying
    )

    // Booming-style model: line/word end times, intro + gap "bubbles".
    val lyricLines: List<LyricsLine>? = remember(syncedLines, song!!.duration) {
        syncedLines?.let { buildLyricsLines(it, song!!.duration) }
    }
    val currentLineIndex = findLyricsLineIndex(lyricLines, smoothPosition)

    val context = LocalContext.current
    val isPowerSaveMode = remember {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    }

    // Used only by the plain (unsynced) view. The synced view scrolls itself.
    val listState = rememberLazyListState()

    // ── Root ──────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart  = { isSwipeActive = true; dragOffsetX = 0f; resetImmersive() },
                    onDragEnd    = {
                        if (abs(dragOffsetX) > swipeThresholdPx) {
                            if (dragOffsetX > 0) {
                                slideDirection = 1
                                viewModel.playPrevious()
                            } else {
                                slideDirection = -1
                                viewModel.playNext()
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        scope.launch { swipeProgress.animateTo(0f, tween(200)) }
                        isSwipeActive = false; dragOffsetX = 0f
                    },
                    onDragCancel = {
                        isSwipeActive = false; dragOffsetX = 0f
                        scope.launch { swipeProgress.animateTo(0f, tween(200)) }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume(); resetImmersive()
                        dragOffsetX += amount
                        scope.launch { swipeProgress.snapTo((abs(dragOffsetX) / swipeThresholdPx).coerceIn(0f, 1f)) }
                    }
                )
            }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { resetImmersive() }
    ) {
        Box(Modifier.fillMaxSize().background(c.bgCard))

        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            // ── Top bar ───────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = !immersiveMode,
                enter   = fadeIn() + slideInVertically(),
                exit    = fadeOut() + slideOutVertically()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = onBack,
                        colors  = IconButtonDefaults.filledIconButtonColors(containerColor = c.bgElevated.copy(0.85f), contentColor = c.textPrimary)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Назад", Modifier.size(26.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("ТЕКСТ ПЕСНИ", color = c.textDisabled, fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.1f.sp)
                    Spacer(Modifier.weight(1f))
                    FilledIconButton(
                        onClick = { viewModel.refreshLyrics() },
                        colors  = IconButtonDefaults.filledIconButtonColors(containerColor = c.bgElevated.copy(0.85f), contentColor = c.textPrimary)
                    ) {
                        Icon(Icons.Rounded.Refresh, "Обновить")
                    }
                }
            }

            // ── Track header (vinyl) ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .background(c.bgElevated.copy(0.75f), CircleShape).padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(66.dp).clip(CircleShape).background(c.bgCard), contentAlignment = Alignment.Center) {
                    val displayUri = customArtUri ?: song!!.albumArtUri
                    if (displayUri != null) {
                        OptimizedAlbumArt(uri = displayUri, title = song!!.title,
                            modifier = Modifier.fillMaxSize().clip(CircleShape).graphicsLayer { rotationZ = vinylRotation.value % 360f },
                            targetSize = CoilSize(198, 198))
                    } else {
                        Icon(painterResource(R.drawable.ic_music_placeholder), null, tint = c.accentMuted,
                            modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = vinylRotation.value % 360f })
                    }
                    Box(Modifier.size(12.dp).clip(CircleShape).background(c.bgElevated))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(customTitleMap[song!!.id] ?: song!!.title, color = c.textPrimary, fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(run { val a = customArtistMap[song!!.id] ?: song!!.artist; if (a != "<unknown>" && a.isNotBlank()) a else "Неизвестный" }, color = c.textSecondary, fontFamily = LocalAppFontFamily.current, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlayingEqIcon(isPlaying = isPlaying, color = c.accent, Modifier.size(18.dp))
            }

            // ── Lyrics area ───────────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val state = lyricsState) {
                    is LyricsState.Loading -> LoadingView(c.accent)
                    is LyricsState.NotFound -> EmptyView("Текст не найден", "Нажмите ↺ чтобы попробовать снова", Icons.Rounded.SearchOff, c) { viewModel.refreshLyrics() }
                    is LyricsState.Error   -> EmptyView("Ошибка поиска", state.message, Icons.Rounded.ErrorOutline, c) { viewModel.refreshLyrics() }
                    is LyricsState.Found   -> {
                        if (!lyricLines.isNullOrEmpty()) {
                            key(song!!.id) {
                                SyncedLyricsContent(
                                    lines = lyricLines,
                                    currentIndex = currentLineIndex,
                                    positionMs = smoothPosition,
                                    accent = c.accent,
                                    textColor = c.textPrimary,
                                    fontFamily = lyricsFontFamily,
                                    fontScale = lyricsFontScale,
                                    fadeStyle = settings.lyricsFadeStyle,
                                    alignment = lyricsAlignment,
                                    isPowerSaveMode = isPowerSaveMode,
                                    onSeekTo = { ms -> viewModel.seekTo(ms); resetImmersive() }
                                )
                            }
                        } else if (plainLines != null && plainLines.isNotEmpty()) {
                            PlainLyricsView(
                                lines = plainLines, immersiveMode = immersiveMode, listState = listState,
                                textPrimary = c.textPrimary, bgCard = Color.Transparent,
                                fontFamily = lyricsFontFamily,
                                userFontScale = lyricsFontScale,
                                fadeStyle = settings.lyricsFadeStyle,
                                alignment = lyricsAlignment
                            )
                        } else {
                            EmptyView("Текст не найден", "Нажмите ↺ чтобы попробовать снова", Icons.Rounded.SearchOff, c) { viewModel.refreshLyrics() }
                        }
                    }
                    else -> LoadingView(c.accent)
                }

            }

            // ── Bottom controls ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = !immersiveMode,
                enter   = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit    = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column(
                    Modifier.fillMaxWidth().background(c.bgCard.copy(0.8f)).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {

                        val prevInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val isPrevPressed by prevInteraction.collectIsPressedAsState()
                        val prevScale by animateFloatAsState(if (isPrevPressed) 0.82f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "prevScale")
                        val prevOffX by animateFloatAsState(if (isPrevPressed) -6f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "prevOX")
                        FilledIconButton(
                            onClick = { slideDirection = 1; viewModel.playPrevious() },
                            modifier = Modifier.size(56.dp).graphicsLayer { scaleX = prevScale; scaleY = prevScale; translationX = prevOffX },
                            colors = IconButtonDefaults.filledIconButtonColors(c.bgElevated, c.textPrimary),
                            interactionSource = prevInteraction
                        ) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(26.dp)) }

                        val ppInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val isPPPressed by ppInteraction.collectIsPressedAsState()
                        val ppScale by animateFloatAsState(if (isPPPressed) 0.88f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "ppScale")
                        val cornerRadius by animateDpAsState(if (isPlaying) 18.dp else 50.dp, spring(stiffness = Spring.StiffnessLow), "ppShape")
                        Box(
                            Modifier.weight(1f).height(56.dp)
                                .graphicsLayer { scaleX = ppScale; scaleY = ppScale }
                                .clip(RoundedCornerShape(cornerRadius))
                                .background(c.accent)
                                .clickable(interactionSource = ppInteraction, indication = null) { viewModel.togglePlayPause() },
                            Alignment.Center
                        ) {
                            AnimatedContent(isPlaying, transitionSpec = { fadeIn(tween(150)).togetherWith(fadeOut(tween(150))) }, label = "pp") { playing ->
                                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = c.bgDeep, modifier = Modifier.size(28.dp))
                            }
                        }

                        val nextInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val isNextPressed by nextInteraction.collectIsPressedAsState()
                        val nextScale by animateFloatAsState(if (isNextPressed) 0.82f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "nextScale")
                        val nextOffX by animateFloatAsState(if (isNextPressed) 6f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "nextOX")
                        FilledIconButton(
                            onClick = { slideDirection = -1; viewModel.playNext() },
                            modifier = Modifier.size(56.dp).graphicsLayer { scaleX = nextScale; scaleY = nextScale; translationX = nextOffX },
                            colors = IconButtonDefaults.filledIconButtonColors(c.bgElevated, c.textPrimary),
                            interactionSource = nextInteraction
                        ) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(26.dp)) }
                    }
                    Text("Свайп влево/вправо для смены трека", color = c.textDisabled, fontFamily = LocalAppFontFamily.current, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Immersive show controls button
        AnimatedVisibility(
            visible  = immersiveMode,
            enter    = fadeIn() + slideInVertically { it / 2 },
            exit     = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).navigationBarsPadding()
        ) {
            FilledIconButton(onClick = { resetImmersive() }, Modifier.size(48.dp), colors = IconButtonDefaults.filledIconButtonColors(c.bgElevated, c.accent)) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Показать управление")
            }
        }

        // Swipe overlay
        if (isSwipeActive || swipeProgress.value > 0.01f) {
            val isNext = dragOffsetX < 0
            Box(
                modifier = Modifier.align(if (isNext) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(100.dp).padding(6.dp)
                    .graphicsLayer {
                        translationX = (if (isNext) size.width else -size.width) * (1f - swipeProgress.value)
                        val s = 0.8f + swipeProgress.value * 0.2f; scaleX = s; scaleY = s
                    }
                    .background(c.accent, RoundedCornerShape(
                        topStart = if (isNext) 360.dp else 8.dp, bottomStart = if (isNext) 360.dp else 8.dp,
                        topEnd = if (isNext) 8.dp else 360.dp, bottomEnd = if (isNext) 8.dp else 360.dp
                    )),
                contentAlignment = Alignment.Center
            ) { Icon(if (isNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious, null, tint = c.bgDeep, modifier = Modifier.size(48.dp)) }
        }
    }
}

// ── Booming-style synchronized lyrics view ───────────────────────────────────
@Composable
private fun SyncedLyricsContent(
    lines: List<LyricsLine>,
    currentIndex: Int,
    positionMs: Long,
    accent: Color,
    textColor: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    fadeStyle: Int,
    alignment: Int,
    isPowerSaveMode: Boolean,
    onSeekTo: (Long) -> Unit
) {
    val textAlign = when (alignment) {
        1 -> TextAlign.Center
        2 -> TextAlign.End
        else -> TextAlign.Start
    }
    val fadingEdges = when (fadeStyle) {
        0 -> FadingEdges.None
        2 -> FadingEdges(top = 72.dp, bottom = 64.dp)
        else -> FadingEdges(top = 56.dp, bottom = 32.dp)
    }

    Box(Modifier.fillMaxSize()) {
        LyricsView(
            lines = lines,
            currentLineIndex = currentIndex,
            positionMs = positionMs,
            textAlign = textAlign,
            fontScale = fontScale,
            fontFamily = fontFamily,
            contentColor = textColor,
            contentPadding = PaddingValues(vertical = 96.dp, horizontal = 16.dp),
            fadingEdges = fadingEdges,
            isPowerSaveMode = isPowerSaveMode,
            onSeekTo = onSeekTo
        )

        Surface(
            Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 8.dp),
            shape = RoundedCornerShape(50),
            color = accent.copy(alpha = 0.2f)
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(12.dp))
                Text(
                    "SYNC",
                    color = accent,
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ── Plain lyrics view ─────────────────────────────────────────────────────────
@Composable
private fun PlainLyricsView(
    lines: List<String>,
    immersiveMode: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    textPrimary: Color,
    bgCard: Color,
    fontFamily: FontFamily,
    userFontScale: Float = 1.0f,
    fadeStyle: Int = 1,
    alignment: Int = 0
) {
    val textAlign = when (alignment) { 1 -> TextAlign.Center; 2 -> TextAlign.End; else -> TextAlign.Start }
    val fontScale by animateFloatAsState(
        (if (immersiveMode) 1.35f else 1f) * userFontScale,
        animationSpec = spring<Float>(stiffness = Spring.StiffnessLow), label = "fontScale"
    )
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(lines, key = { i, _ -> i }) { _, line ->
                if (line.isBlank()) Spacer(Modifier.height(8.dp))
                else Text(line, color = textPrimary.copy(alpha = 0.85f), fontFamily = fontFamily, fontSize = (16f * fontScale).sp, lineHeight = (24f * fontScale).sp, textAlign = textAlign, modifier = Modifier.fillMaxWidth())
            }
        }
        val fadeColor2 = MaterialTheme.colorScheme.bgDeep
        if (fadeStyle > 0) {
            val alpha = if (fadeStyle == 2) 1f else 0.7f
            Box(Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, fadeColor2.copy(alpha)))))
        }
    }
}

// ── Loading / empty / error ───────────────────────────────────────────────────
@Composable
private fun LoadingView(color: Color) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_lyrics))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = LottieConstants.IterateForever,
        isPlaying   = true,
        speed       = 1f
    )
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress    = { progress },
            modifier    = Modifier.size(140.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ищем текст песни...",
            color      = c.textSecondary,
            fontFamily = font,
            fontSize   = 14.sp
        )
    }
}

@Composable
private fun EmptyView(
    message: String, subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    c: ColorScheme,
    onRefresh: (() -> Unit)?
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = c.textDisabled, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = c.textDisabled, fontFamily = LocalAppFontFamily.current, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (subtext.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(subtext, color = c.textDisabled.copy(0.6f), fontFamily = LocalAppFontFamily.current, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        }
        if (onRefresh != null) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onRefresh, colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.accent.copy(0.15f), contentColor = c.accent)) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Попробовать снова", fontFamily = LocalAppFontFamily.current)
            }
        }
    }
}
