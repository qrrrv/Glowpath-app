// Adapted from BoomingMusic (GPL-3.0): LyricsView / LyricsLineView / BubblesLine.
package com.musicplayer.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.musicplayer.data.lyrics.SyncedLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  Settings (defaults taken from BoomingMusic, tweak here)
// ─────────────────────────────────────────────────────────────────────────────

object LyricsViewDefaults {
    /** Font size of synced lyrics in sp (Booming "full screen" default = 24). */
    const val FONT_SIZE_SP = 24f
    /** Booming "lyrics_line_spacing" default = 40. */
    const val LINE_SPACING_PERCENT = 40
    const val BOLD = false
    /** Word-by-word highlighting when the lyrics have word timings. */
    const val WORD_SYNC = true
    /** Gradient wipe inside a line (karaoke style) instead of per-word alpha. */
    const val KARAOKE_STYLE = true
    const val BLUR_EFFECT = true
    const val SHADOW_EFFECT = true
    /** Only for lines WITHOUT word timings: vertical gradient sweep. */
    const val PROGRESSIVE_COLORING = false
    /** false = active line goes to the top (like Booming default), true = to the center. */
    const val CENTER_CURRENT_LINE = false
    /** Empty LRC lines shorter than this are not shown as "bubbles". */
    const val MIN_GAP_BUBBLES_MS = 3000L
    /** If the first line starts later than this, intro bubbles are added. */
    const val MIN_INTRO_OFFSET_MS = 3500L
}

// ─────────────────────────────────────────────────────────────────────────────
//  UI model (Booming's SyncedLyrics.Line / Word)
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class LyricsWord(
    val content: String,
    val start: Long,
    val end: Long
) {
    val duration: Long get() = end - start
}

@Immutable
data class LyricsLine(
    val start: Long,
    val end: Long,
    val text: String,
    val words: List<LyricsWord>
) {
    val isEmpty: Boolean = text.isBlank()

    /** Earliest moment something of this line is animated. */
    val activeStart: Long = words.firstOrNull()?.let { min(it.start, start) } ?: start

    /** Latest moment something of this line is animated. */
    val activeEnd: Long = words.lastOrNull()?.let { max(it.end, end) } ?: end
}

/**
 * Converts the app's [SyncedLine] list into Booming-like lines:
 * - every line gets an end time (start of the next line);
 * - every word gets an end time (start of the next word);
 * - spacing between words is normalized (needed for word wrapping);
 * - long empty lines / intro become "bubbles" lines.
 */
fun buildLyricsLines(source: List<SyncedLine>, durationMs: Long): List<LyricsLine> {
    if (source.isEmpty()) return emptyList()
    val sorted = source.sortedBy { it.time }
    val n = sorted.size

    val lastStart = sorted.last().time.toLong()
    val lastEnd = if (durationMs > lastStart) durationMs else lastStart + 4000L

    val ends = LongArray(n)
    var currentEnd = lastEnd
    for (i in n - 1 downTo 0) {
        if (i < n - 1 && sorted[i + 1].time > sorted[i].time) {
            currentEnd = sorted[i + 1].time.toLong()
        }
        ends[i] = currentEnd
    }

    val result = ArrayList<LyricsLine>(n + 1)
    for (i in 0 until n) {
        val src = sorted[i]
        val start = src.time.toLong()
        val end = max(ends[i], start + 1L)
        val text = src.line.trim()

        if (text.isBlank()) {
            if (end - start >= LyricsViewDefaults.MIN_GAP_BUBBLES_MS) {
                result.add(LyricsLine(start, end, "", emptyList()))
            }
            continue
        }
        result.add(LyricsLine(start, end, text, buildWords(src, end)))
    }

    val first = result.firstOrNull()
    if (first != null && !first.isEmpty && first.start > LyricsViewDefaults.MIN_INTRO_OFFSET_MS) {
        result.add(0, LyricsLine(0L, first.start, "", emptyList()))
    }
    return result
}

private fun buildWords(src: SyncedLine, lineEnd: Long): List<LyricsWord> {
    val raw = src.words
    if (raw.isNullOrEmpty()) return emptyList()

    val times = ArrayList<Long>(raw.size)
    val texts = ArrayList<String>(raw.size)
    raw.forEach {
        if (it.word.isNotEmpty()) {
            times.add(it.time.toLong())
            texts.add(it.word)
        }
    }
    if (texts.isEmpty()) return emptyList()

    // Move leading whitespace of a word to the end of the previous one.
    for (i in 1 until texts.size) {
        val t = texts[i]
        val trimmed = t.trimStart()
        if (trimmed.length < t.length) {
            val prev = texts[i - 1]
            if (prev.isNotEmpty() && !prev.last().isWhitespace()) texts[i - 1] = "$prev "
            texts[i] = trimmed
        }
    }
    texts[0] = texts[0].trimStart()

    // Old cached lyrics had trimmed words without any spaces: treat every token as a word.
    val hasSpacing = texts.any { tok -> tok.any { it.isWhitespace() } }
    if (!hasSpacing && texts.size > 1) {
        for (i in 0 until texts.lastIndex) texts[i] = texts[i] + " "
    }

    val kept = ArrayList<Pair<Long, String>>(texts.size)
    for (i in texts.indices) {
        if (texts[i].isNotBlank()) kept.add(times[i] to texts[i])
    }
    if (kept.isEmpty()) return emptyList()

    val out = ArrayList<LyricsWord>(kept.size)
    for (k in kept.indices) {
        val start = kept[k].first
        val end = if (k < kept.lastIndex) {
            max(kept[k + 1].first, start)
        } else {
            max(min(lineEnd, start + 1200L), start + 1L)
        }
        out.add(LyricsWord(content = kept[k].second, start = start, end = end))
    }
    return out
}

fun findLyricsLineIndex(lines: List<LyricsLine>?, positionMs: Long): Int {
    if (lines.isNullOrEmpty() || positionMs < 0) return -1
    for (i in lines.lastIndex downTo 0) {
        if (positionMs >= lines[i].start) return i
    }
    return -1
}

internal fun String.isRtlText(): Boolean {
    for (ch in this) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true

            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
            else -> Unit
        }
    }
    return false
}

// ─────────────────────────────────────────────────────────────────────────────
//  LyricsView
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LyricsView(
    lines: List<LyricsLine>,
    currentLineIndex: Int,
    positionMs: Long,
    textAlign: TextAlign,
    fontScale: Float,
    fontFamily: FontFamily,
    contentColor: Color,
    contentPadding: PaddingValues,
    fadingEdges: FadingEdges,
    isPowerSaveMode: Boolean,
    modifier: Modifier = Modifier,
    /** 24 = full-screen lyrics, 20 = lyrics shown on the player cover (Booming defaults). */
    fontSizeSp: Float = LyricsViewDefaults.FONT_SIZE_SP,
    onSeekTo: (Long) -> Unit
) {
    val density = LocalDensity.current

    val textStyle = remember(fontFamily, fontScale, fontSizeSp) {
        TextStyle(
            fontFamily = fontFamily,
            fontSize = (fontSizeSp * fontScale).sp,
            fontWeight = if (LyricsViewDefaults.BOLD) FontWeight.Bold else FontWeight.Normal,
            lineHeight = (1f + (LyricsViewDefaults.LINE_SPACING_PERCENT / 100f)).em
        )
    }
    val lineSpacing = (((LyricsViewDefaults.LINE_SPACING_PERCENT / 2) + 8).coerceIn(8, 48)).dp

    val listState = rememberLazyListState()
    val isInDragGesture by listState.interactionSource.collectIsDraggedAsState()

    val enableBlur = LyricsViewDefaults.BLUR_EFFECT && !isPowerSaveMode && !isInDragGesture

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex < 0) return@LaunchedEffect
        if (isInDragGesture || listState.isScrollInProgress) return@LaunchedEffect

        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        if (viewportHeight <= 0) return@LaunchedEffect
        val bottomPadding = with(density) { contentPadding.calculateBottomPadding().toPx() }
        val activeItem = layoutInfo.visibleItemsInfo.find { it.index == currentLineIndex }

        if (activeItem != null) {
            val targetOffset = if (LyricsViewDefaults.CENTER_CURRENT_LINE) {
                (viewportHeight / 2f) - (activeItem.size / 2f) - bottomPadding
            } else {
                0f
            }
            val gap = (lines.getOrNull(currentLineIndex + 1)?.start ?: 0L) -
                    (lines.getOrNull(currentLineIndex)?.start ?: 0L)
            listState.animateScrollBy(
                value = activeItem.offset - targetOffset,
                animationSpec = tween(
                    durationMillis = (gap / 2).coerceIn(100L, 1000L).toInt(),
                    easing = FastOutSlowInEasing
                )
            )
        } else {
            val fontSizePx = with(density) { textStyle.fontSize.toPx() * 2 }
            val targetOffset = if (LyricsViewDefaults.CENTER_CURRENT_LINE) {
                (viewportHeight / 2f) - fontSizePx - bottomPadding
            } else {
                0f
            }
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = -targetOffset.toInt()
            )
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(lineSpacing),
        modifier = modifier
            .fadingEdges(fadingEdges)
            .fillMaxSize()
    ) {
        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
            LyricsLineView(
                index = index,
                selectedIndex = currentLineIndex,
                selectedLine = index == currentLineIndex,
                textAlign = textAlign,
                enableSyllable = LyricsViewDefaults.WORD_SYNC && !isPowerSaveMode,
                enableKaraokeStyle = LyricsViewDefaults.KARAOKE_STYLE,
                progressiveColoring = LyricsViewDefaults.PROGRESSIVE_COLORING && !isPowerSaveMode,
                enableBlurEffect = enableBlur,
                enableShadowEffect = LyricsViewDefaults.SHADOW_EFFECT && !isPowerSaveMode,
                contentColor = contentColor,
                // Clamped: lines far from the playhead get a constant value, so Compose
                // skips their recomposition on every frame.
                progressMillis = positionMs.coerceIn(line.activeStart - 1L, line.activeEnd + 1L),
                line = line,
                textStyle = textStyle,
                modifier = Modifier.animateItem(placementSpec = tween(durationMillis = 500)),
                onClick = { onSeekTo(line.start) }
            )
        }
    }
}

@Composable
private fun LyricsLineView(
    index: Int,
    selectedIndex: Int,
    selectedLine: Boolean,
    textAlign: TextAlign,
    enableSyllable: Boolean,
    enableKaraokeStyle: Boolean,
    progressiveColoring: Boolean,
    enableBlurEffect: Boolean,
    enableShadowEffect: Boolean,
    contentColor: Color,
    progressMillis: Long,
    line: LyricsLine,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selectedLine) 1.1f else 1f,
        animationSpec = tween(durationMillis = 700),
        label = "current-line-scale-animation"
    )

    val isRtl = line.text.isRtlText()

    val transformOrigin = when (textAlign) {
        TextAlign.End -> if (isRtl) TransformOrigin(0f, 1f) else TransformOrigin(1f, 1f)
        TextAlign.Start -> if (isRtl) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)
        else -> TransformOrigin.Center
    }

    val paddingValues = when (textAlign) {
        TextAlign.End -> PaddingValues(start = 32.dp, end = 8.dp)
        TextAlign.Start -> PaddingValues(start = 8.dp, end = 32.dp)
        else -> PaddingValues(horizontal = 32.dp)
    }

    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = onClick
                )
                .padding(paddingValues)
        ) {
            if (line.isEmpty) {
                BubblesLine(
                    selectedLine = selectedLine,
                    color = contentColor,
                    fontSize = textStyle.fontSize,
                    progressMillis = progressMillis,
                    startMillis = line.start,
                    endMillis = line.end,
                    modifier = Modifier.align(
                        when (textAlign) {
                            TextAlign.End -> Alignment.CenterEnd
                            TextAlign.Center -> Alignment.Center
                            else -> Alignment.CenterStart
                        }
                    )
                )
            } else {
                Column(
                    horizontalAlignment = when (textAlign) {
                        TextAlign.End -> Alignment.End
                        TextAlign.Center -> Alignment.CenterHorizontally
                        else -> Alignment.Start
                    },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            this.transformOrigin = transformOrigin
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    LyricsLineContentView(
                        index = index,
                        selectedIndex = selectedIndex,
                        line = line,
                        enableSyllable = enableSyllable,
                        enableKaraokeStyle = enableKaraokeStyle,
                        progressiveColoring = progressiveColoring,
                        enableBlurEffect = enableBlurEffect,
                        enableShadowEffect = enableShadowEffect,
                        selectedLine = selectedLine,
                        contentColor = contentColor,
                        progressMillis = progressMillis,
                        style = textStyle,
                        align = textAlign
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsLineContentView(
    index: Int,
    selectedIndex: Int,
    line: LyricsLine,
    enableSyllable: Boolean,
    enableKaraokeStyle: Boolean,
    progressiveColoring: Boolean,
    enableBlurEffect: Boolean,
    enableShadowEffect: Boolean,
    selectedLine: Boolean,
    contentColor: Color,
    progressMillis: Long,
    style: TextStyle,
    align: TextAlign,
    modifier: Modifier = Modifier
) {
    val startMillis = line.start
    val endMillis = line.end

    val progressFraction = when {
        progressMillis < startMillis -> 0f
        progressMillis > endMillis -> 1f
        else -> ((progressMillis - startMillis).toFloat() / (endMillis - startMillis).toFloat()).coerceIn(0f, 1f)
    }

    val effectDuration = ((endMillis - startMillis) / 2).coerceAtMost(500).toInt()
    val blurRadius by animateFloatAsState(
        targetValue = if (index == selectedIndex) 0f else
            (abs(index - selectedIndex).toFloat() + 1.5f).coerceIn(0f, 10f),
        animationSpec = tween(effectDuration),
        label = "line-blur-radius"
    )

    val blurEffect = remember(enableBlurEffect, blurRadius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && enableBlurEffect && blurRadius > 0f) {
            BlurEffect(
                radiusX = blurRadius,
                radiusY = blurRadius,
                edgeTreatment = TileMode.Clamp
            )
        } else null
    }

    if (enableSyllable && line.words.isNotEmpty()) {
        WordSyncedText(
            karaokeStyle = enableKaraokeStyle,
            selectedLine = selectedLine,
            shadowEffect = enableShadowEffect,
            progress = progressMillis,
            syllables = line.words,
            contentColor = contentColor,
            style = style,
            align = align,
            modifier = modifier.graphicsLayer { renderEffect = blurEffect }
        )
    } else if (line.text.isNotBlank()) {
        LineSyncedView(
            selectedLine = selectedLine,
            progressiveColoring = progressiveColoring,
            shadowEffect = enableShadowEffect,
            effectDuration = effectDuration,
            progressFraction = progressFraction,
            content = line.text,
            color = contentColor,
            style = style,
            align = align,
            modifier = modifier.graphicsLayer { renderEffect = blurEffect }
        )
    }
}

@Composable
private fun LineSyncedView(
    selectedLine: Boolean,
    progressiveColoring: Boolean,
    shadowEffect: Boolean,
    effectDuration: Int,
    progressFraction: Float,
    content: String,
    color: Color,
    style: TextStyle,
    align: TextAlign,
    modifier: Modifier = Modifier
) {
    var textHeight by remember { mutableFloatStateOf(0f) }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (selectedLine) 1f else .4f,
        animationSpec = tween(400),
        label = "current-line-alpha-animation"
    )

    val animatedOrigin by animateFloatAsState(
        targetValue = if (selectedLine) progressFraction * textHeight else 0f,
        label = "line-gradient-origin"
    )

    val shadowRadius by animateFloatAsState(
        targetValue = if (selectedLine) 10f * progressFraction else 0f,
        animationSpec = tween(effectDuration),
        label = "line-shadow-radius"
    )

    val shadow = if (shadowEffect && selectedLine) {
        Shadow(
            color = color.copy(alpha = .5f),
            blurRadius = shadowRadius
        )
    } else {
        Shadow.None
    }

    val textStyle by remember(color, selectedLine, progressiveColoring, animatedOrigin) {
        derivedStateOf {
            if (progressiveColoring) {
                style.copy(
                    brush = Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = .4f)),
                        startY = animatedOrigin - 10f,
                        endY = animatedOrigin + 10f
                    )
                )
            } else {
                style.copy(color = color.copy(alpha = animatedAlpha))
            }
        }
    }

    Text(
        text = content,
        style = textStyle.copy(shadow = shadow),
        textAlign = align,
        modifier = modifier
            .onGloballyPositioned {
                textHeight = it.size.height.toFloat()
            }
    )
}

@Composable
private fun WordSyncedText(
    karaokeStyle: Boolean,
    selectedLine: Boolean,
    shadowEffect: Boolean,
    progress: Long,
    syllables: List<LyricsWord>,
    contentColor: Color,
    style: TextStyle,
    align: TextAlign,
    modifier: Modifier = Modifier
) {
    KaraokeLineView(
        selectedLine = selectedLine,
        shadowEffect = shadowEffect,
        currentMillis = progress,
        syllables = syllables,
        contentColor = contentColor,
        style = style,
        align = align,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bubbles (instrumental gaps). From Lotus music player via BoomingMusic.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BubblesLine(
    selectedLine: Boolean,
    color: Color,
    fontSize: TextUnit,
    progressMillis: Long,
    startMillis: Long,
    endMillis: Long,
    modifier: Modifier = Modifier
) {
    var bubblesContainerHeight by remember {
        mutableFloatStateOf(0f)
    }

    val progressFraction by remember(progressMillis) {
        derivedStateOf {
            ((progressMillis.toFloat() - startMillis) / (endMillis - startMillis))
                .coerceIn(0f, 1f)
        }
    }

    val density = LocalDensity.current
    val height = with(density) { (fontSize / 1.35).toDp() }

    val infiniteTransition = rememberInfiniteTransition(
        label = "bubbles-transition"
    )

    val firstBubbleProgress by remember(progressFraction) {
        derivedStateOf {
            (progressFraction / .33f).coerceIn(0f, 1f)
        }
    }

    val firstBubbleTranslationX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "first-bubble-translation-x"
    )

    val secondBubbleProgress by remember(progressFraction) {
        derivedStateOf {
            ((progressFraction - .33f) / .33f).coerceIn(0f, 1f)
        }
    }

    val secondBubbleTranslationX by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                offsetMillis = 500,
                offsetType = StartOffsetType.FastForward
            )
        ),
        label = "second-bubble-translation-x"
    )

    val thirdBubbleProgress by remember(progressFraction) {
        derivedStateOf {
            ((progressFraction - .33f * 2) / .33f).coerceIn(0f, 1f)
        }
    }

    val thirdBubbleTranslationX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                offsetMillis = 1000,
                offsetType = StartOffsetType.FastForward
            )
        ),
        label = "third-bubble-translation-x"
    )

    val scale by animateFloatAsState(
        targetValue = if (progressFraction < .97f) 1f else 1.2f,
        label = "bubbles-scale-before-next-line"
    )

    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .height(height)
            .onGloballyPositioned {
                bubblesContainerHeight = it.size.height.toFloat()
            },
    ) {
        AnimatedVisibility(
            visible = selectedLine,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier
                .fillMaxHeight()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Bubble(
                    bubbleHeight = height,
                    containerHeight = bubblesContainerHeight,
                    animationProgress = firstBubbleProgress,
                    translationX = firstBubbleTranslationX,
                    translationOffset = secondBubbleTranslationX,
                    color = color
                )

                Bubble(
                    bubbleHeight = height,
                    containerHeight = bubblesContainerHeight,
                    animationProgress = secondBubbleProgress,
                    translationX = secondBubbleTranslationX,
                    translationOffset = thirdBubbleTranslationX,
                    color = color
                )

                Bubble(
                    bubbleHeight = height,
                    containerHeight = bubblesContainerHeight,
                    animationProgress = thirdBubbleProgress,
                    translationX = thirdBubbleTranslationX,
                    translationOffset = firstBubbleTranslationX,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun Bubble(
    bubbleHeight: Dp,
    containerHeight: Float,
    animationProgress: Float,
    translationX: Float,
    translationOffset: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(bubbleHeight * .7f)
            .graphicsLayer {
                this.translationY =
                    -containerHeight / 6 *
                            (sin(20 * (animationProgress - .25f) / PI.toFloat()) / 2 + .5f) +
                            translationX * translationOffset / 2

                this.translationX = translationX
                val scale = .5f + animationProgress / 2
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                drawCircle(
                    radius = size.width,
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        .5f to Color.Transparent,
                        .5f to color.copy(alpha = (animationProgress / 2 - .25f).coerceIn(0f, 1f)),
                        .6f to color.copy(alpha = (animationProgress / 3 - .25f).coerceIn(0f, 1f)),
                        .8f to Color.Transparent,
                        radius = size.width
                    )
                )

                drawCircle(
                    color = color.copy(
                        alpha = (.25f + animationProgress).coerceIn(0f, 1f)
                    )
                )
            }
    )
}
