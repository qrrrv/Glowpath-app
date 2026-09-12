package com.musicplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import com.musicplayer.ui.components.OptimizedAlbumArt
import com.musicplayer.R
import com.musicplayer.data.OrbSettings
import com.musicplayer.data.lyrics.LyricsState
import com.musicplayer.data.lyrics.SyncedLine
import com.musicplayer.ui.components.PlayingEqIcon
import com.musicplayer.ui.theme.*
import androidx.compose.animation.animateColorAsState
import androidx.palette.graphics.Palette
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.musicplayer.viewmodel.MusicViewModel
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.airbnb.lottie.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val c       = MaterialTheme.colorScheme
    val haptic  = LocalHapticFeedback.current
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val song            by viewModel.currentSong.collectAsState()
    val isPlaying       by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val lyricsState     by viewModel.lyricsState.collectAsState()
    val customArtMap    by viewModel.customArtMap.collectAsState()
    val customTitleMap  by viewModel.customTitleMap.collectAsState()
    val customArtistMap by viewModel.customArtistMap.collectAsState()
    val orbSettings     by viewModel.orbSettings.collectAsState()
    val audioReactiveLevel by viewModel.audioReactiveLevel.collectAsState()
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

    // ── Palette for orb colors ────────────────────────────────────────────────
    data class ArtColors(val dominant: Color, val vibrant: Color, val muted: Color)
    var artColors by remember(song!!.id) { mutableStateOf<ArtColors?>(null) }
    val artSourceUri = customArtUri ?: song!!.albumArtUri
    LaunchedEffect(artSourceUri) {
        artColors = null
        artSourceUri?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    bmp?.let { b ->
                        val p = Palette.from(b).maximumColorCount(12).generate()
                        artColors = ArtColors(
                            Color(p.getDominantColor(0xFF555555.toInt())),
                            Color(p.getVibrantColor(p.getMutedColor(0xFF666666.toInt()))),
                            Color(p.getMutedColor(p.getDominantColor(0xFF444444.toInt())))
                        )
                        b.recycle()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val ct = orbSettings.contrast
    val animOrbColor1 by animateColorAsState(artColors?.dominant?.copy(0.3f + ct * 0.35f) ?: c.accent.copy(0.4f), tween(800), label = "lc1")
    val animOrbColor2 by animateColorAsState(artColors?.vibrant?.copy(0.25f + ct * 0.3f) ?: c.accentVar.copy(0.35f), tween(900, 100), label = "lc2")
    val animOrbColor3 by animateColorAsState(artColors?.muted?.copy(0.2f + ct * 0.25f) ?: c.accentMuted.copy(0.3f), tween(1000, 200), label = "lc3")

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

    val currentLineIndex by remember(syncedLines, currentPosition) {
        derivedStateOf {
            if (syncedLines.isNullOrEmpty()) -1
            else {
                val posMs = currentPosition.toInt()
                val idx   = syncedLines.indexOfLast { it.time <= posMs }
                if (idx < 0 && syncedLines.isNotEmpty()) 0 else idx
            }
        }
    }

    val listState = rememberLazyListState()
    // Плавный автоскролл к текущей строке
    // Используем флаг: если пользователь сам листает — не прерываем его
    val isUserScrolling = listState.isScrollInProgress
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 2 && !isUserScrolling) {
            // Маленькая задержка оставляет время для рывка строки до автоскролла.
            kotlinx.coroutines.delay(36)
            listState.animateScrollToItem(
                index = (currentLineIndex - 2).coerceAtLeast(0),
                scrollOffset = 0
            )
        }
    }

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
        // ── Orb background (if enabled) ───────────────────────────────────────
        if (orbSettings.showInLyrics) {
            AnimatedOrbBackground(
                color1 = animOrbColor1, color2 = animOrbColor2, color3 = animOrbColor3,
                baseColor = c.bgDeep.copy(alpha = 0.92f),
                orbSettings = orbSettings.copy(speed = orbSettings.speed * 0.6f), // slower in lyrics
                modifier = Modifier.fillMaxSize(),
                audioReactiveLevel = audioReactiveLevel
            )
            // Extra dark scrim so text remains readable
            Box(Modifier.fillMaxSize().background(c.bgCard.copy(alpha = 0.55f)))
        } else {
            Box(Modifier.fillMaxSize().background(c.bgCard))
        }

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
                        if (syncedLines != null && syncedLines.isNotEmpty()) {
                            KaraokeLyricsView(
                                lines = syncedLines, currentIndex = currentLineIndex,
                                immersiveMode = immersiveMode, listState = listState,
                                accent = c.accent, textPrimary = c.textPrimary, bgCard = Color.Transparent,
                                fontFamily = lyricsFontFamily,
                                userFontScale = lyricsFontScale,
                                fadeStyle = settings.lyricsFadeStyle,
                                alignment = lyricsAlignment,
                                curlAnim = lyricsCurlAnim
                            )
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

// ── Apple Music-style Karaoke view ────────────────────────────────────────────
@Composable
private fun KaraokeLyricsView(
    lines: List<SyncedLine>,
    currentIndex: Int,
    immersiveMode: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    accent: Color,
    textPrimary: Color,
    bgCard: Color,
    fontFamily: FontFamily,
    userFontScale: Float = 1.0f,
    fadeStyle: Int = 1,
    alignment: Int = 0,
    curlAnim: Boolean = true
) {
    val textAlign = when (alignment) { 1 -> TextAlign.Center; 2 -> TextAlign.End; else -> TextAlign.Start }
    val fontScale by animateFloatAsState(
        (if (immersiveMode) 1.25f else 1f) * userFontScale,
        animationSpec = spring<Float>(stiffness = Spring.StiffnessLow), label = "fontScale"
    )
    val animatedFocusIndex by animateFloatAsState(
        targetValue = currentIndex.coerceAtLeast(0).toFloat(),
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lyricsFocus"
    )
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(lines, key = { i, _ -> i }) { index, line ->
                if (line.line.isBlank()) {
                    Spacer(Modifier.height(16.dp))
                } else {
                    KaraokeLine(
                        text = line.line,
                        discretePosition = index - currentIndex,
                        relativePosition = index - animatedFocusIndex,
                        fontScale = fontScale,
                        accent = accent,
                        textPrimary = textPrimary,
                        fontFamily = fontFamily,
                        textAlign = textAlign,
                        curlAnim = curlAnim
                    )
                }
            }
        }
        // Top & bottom fade gradients — controlled by fadeStyle (0=none, 1=soft, 2=strong)
        val fadeColor = MaterialTheme.colorScheme.bgDeep
        if (fadeStyle > 0) {
            val topAlpha = if (fadeStyle == 2) 1f else 0.85f
            val botAlpha = if (fadeStyle == 2) 1f else 0.9f
            val topH = if (fadeStyle == 2) 110 else 80
            val botH = if (fadeStyle == 2) 260 else 220
            Box(Modifier.fillMaxWidth().height(topH.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(
                    0f to fadeColor.copy(alpha = topAlpha),
                    0.6f to fadeColor.copy(alpha = topAlpha * 0.25f),
                    1f to Color.Transparent
                )))
            // Нижний градиент плавно выходит снизу — текст «вырастает» из панели управления
            Box(Modifier.fillMaxWidth().height(botH.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.25f to fadeColor.copy(alpha = botAlpha * 0.18f),
                    0.55f to fadeColor.copy(alpha = botAlpha * 0.58f),
                    0.8f to fadeColor.copy(alpha = botAlpha * 0.88f),
                    1f to fadeColor.copy(alpha = botAlpha)
                )))
        }

        // SYNC badge
        Surface(Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 8.dp), shape = RoundedCornerShape(50), color = accent.copy(0.2f)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(12.dp))
                Text("SYNC", color = accent, fontFamily = LocalAppFontFamily.current, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

/**
 * Apple Music-style karaoke line — плавные пружинные переходы между строками,
 * размытие дальних строк и лёгкий 3D-curl при входе снизу.
 */
@Composable
private fun KaraokeLine(
    text: String,
    discretePosition: Int,
    relativePosition: Float,
    fontScale: Float,
    accent: Color,
    textPrimary: Color,
    fontFamily: FontFamily,
    textAlign: TextAlign = TextAlign.Start,
    curlAnim: Boolean = true,
    animStyle: Int = 1  // unused, kept for compat
) {
    val absPosition = abs(relativePosition)
    val focusBlend = (1f - absPosition.coerceAtMost(1.25f) / 1.25f).coerceIn(0f, 1f)
    val pullKick = remember { Animatable(0f) }
    var previousDiscretePosition by remember { mutableIntStateOf(discretePosition) }
    LaunchedEffect(discretePosition) {
        if (previousDiscretePosition == discretePosition) return@LaunchedEffect
        val movementDirection = if (discretePosition < previousDiscretePosition) -1f else 1f
        val distanceWeight = when (abs(discretePosition)) {
            0 -> 1f
            1 -> 0.78f
            2 -> 0.52f
            3 -> 0.34f
            else -> 0.2f
        }
        pullKick.snapTo(1f * movementDirection * distanceWeight)
        pullKick.animateTo(
            0f,
            animationSpec = keyframes {
                durationMillis = 440
                (-0.42f * movementDirection * distanceWeight) at 150 using FastOutSlowInEasing
                (0.14f * movementDirection * distanceWeight) at 285 using LinearOutSlowInEasing
            }
        )
        previousDiscretePosition = discretePosition
    }
    val alpha = lyricsAlphaAt(relativePosition)
    val fontSize = lyricsFontSizeAt(relativePosition) * fontScale
    val blurRadius = lyricsBlurAt(relativePosition)
    val kickOffset = pullKick.value * when (abs(discretePosition)) {
        0 -> 18f
        1 -> 12f
        2 -> 7f
        else -> 4f
    }
    val lineTranslationY = lyricsTranslationAt(relativePosition) + kickOffset
    val lineRotationX = if (curlAnim) lyricsCurlAt(relativePosition) else 0f
    val lineColor = lerp(textPrimary, accent, focusBlend)
    val isCurrent = absPosition < 0.55f
    val isNearNext = relativePosition in 0.55f..1.45f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val motionBoost = (abs(pullKick.value) * 0.08f).coerceAtMost(0.08f)
    val lineScale = 1f + when {
        isCurrent -> motionBoost
        abs(discretePosition) <= 1 -> motionBoost * 0.65f
        else -> motionBoost * 0.35f
    }

    val bleedPad = if (blurRadius > 0.5f) (blurRadius * 2.5f).coerceAtMost(20f).dp else 0.dp
    val blurMod  = if (blurRadius > 0.5f) Modifier.blur(blurRadius.dp) else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isCurrent) 8.dp else 2.dp)
            .graphicsLayer {
                rotationX = lineRotationX
                translationY = with(density) { lineTranslationY.dp.toPx() }
                scaleX = lineScale
                scaleY = lineScale
                cameraDistance = 10f * density.density
            }
    ) {
        Box(modifier = Modifier.fillMaxWidth().then(blurMod)) {
            Text(
                text       = text,
                color      = lineColor.copy(alpha = (alpha + motionBoost * 0.7f).coerceAtMost(1f)),
                fontFamily = fontFamily,
                fontWeight = if (isCurrent) FontWeight.ExtraBold
                             else if (isNearNext) FontWeight.SemiBold
                             else FontWeight.Normal,
                fontSize   = fontSize.sp,
                lineHeight = (fontSize * 1.35f).sp,
                textAlign  = textAlign,
                modifier   = Modifier.fillMaxWidth().padding(vertical = bleedPad)
            )
        }
    }
}

private fun lyricsRangeProgress(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun lyricsLerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun lyricsAlphaAt(position: Float): Float = when {
    position <= -2f -> 0.2f
    position < -1f -> lyricsLerp(0.2f, 0.45f, lyricsRangeProgress(position, -2f, -1f))
    position < 0f -> lyricsLerp(0.45f, 1f, lyricsRangeProgress(position, -1f, 0f))
    position < 1f -> lyricsLerp(1f, 0.8f, lyricsRangeProgress(position, 0f, 1f))
    position < 2f -> lyricsLerp(0.8f, 0.54f, lyricsRangeProgress(position, 1f, 2f))
    position < 3f -> lyricsLerp(0.54f, 0.34f, lyricsRangeProgress(position, 2f, 3f))
    else -> lyricsLerp(0.34f, 0.24f, lyricsRangeProgress(position, 3f, 4f))
}

private fun lyricsFontSizeAt(position: Float): Float = when {
    position <= -2f -> 16f
    position < -1f -> lyricsLerp(16f, 18f, lyricsRangeProgress(position, -2f, -1f))
    position < 0f -> lyricsLerp(18f, 30f, lyricsRangeProgress(position, -1f, 0f))
    position < 1f -> lyricsLerp(30f, 22f, lyricsRangeProgress(position, 0f, 1f))
    position < 2f -> lyricsLerp(22f, 19f, lyricsRangeProgress(position, 1f, 2f))
    position < 3f -> lyricsLerp(19f, 17.5f, lyricsRangeProgress(position, 2f, 3f))
    else -> lyricsLerp(17.5f, 16.5f, lyricsRangeProgress(position, 3f, 4f))
}

private fun lyricsBlurAt(position: Float): Float = when {
    position <= 0f -> 0f
    position < 1f -> lyricsLerp(0f, 1.5f, lyricsRangeProgress(position, 0f, 1f))
    position < 2f -> lyricsLerp(1.5f, 4.5f, lyricsRangeProgress(position, 1f, 2f))
    position < 3f -> lyricsLerp(4.5f, 7.5f, lyricsRangeProgress(position, 2f, 3f))
    else -> lyricsLerp(7.5f, 10.5f, lyricsRangeProgress(position, 3f, 4f))
}

private fun lyricsTranslationAt(position: Float): Float = when {
    position <= -2f -> -6f
    position < -1f -> lyricsLerp(-6f, -2.5f, lyricsRangeProgress(position, -2f, -1f))
    position < 0f -> lyricsLerp(-2.5f, 0f, lyricsRangeProgress(position, -1f, 0f))
    position < 1f -> lyricsLerp(0f, 4f, lyricsRangeProgress(position, 0f, 1f))
    position < 2f -> lyricsLerp(4f, 7.5f, lyricsRangeProgress(position, 1f, 2f))
    position < 3f -> lyricsLerp(7.5f, 9.5f, lyricsRangeProgress(position, 2f, 3f))
    else -> lyricsLerp(9.5f, 11f, lyricsRangeProgress(position, 3f, 4f))
}

private fun lyricsCurlAt(position: Float): Float = when {
    position <= 0f -> 0f
    position < 1f -> lyricsLerp(0f, -3.5f, lyricsRangeProgress(position, 0f, 1f))
    position < 2f -> lyricsLerp(-3.5f, -7f, lyricsRangeProgress(position, 1f, 2f))
    position < 3f -> lyricsLerp(-7f, -10f, lyricsRangeProgress(position, 2f, 3f))
    else -> lyricsLerp(-10f, -12f, lyricsRangeProgress(position, 3f, 4f))
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
