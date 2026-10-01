package com.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 (Expressive) icon toggle button for "lyrics".
 *
 *  - off: tonal circle + outlined speech bubble with text lines;
 *  - on : the container morphs circle -> rounded square and takes [selectedContainerColor], the bubble
 *         fills, the lines "type" in, the note pops; while lyrics are visible the highlighted line keeps
 *         walking down the text (like karaoke) and the note gently sways;
 *  - press: squash + squarer shape, springy release.
 */
@Composable
fun LyricsToggleButton(
    selected: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val description = if (selected) "Скрыть текст песни" else "Показать текст песни"

    val container by animateColorAsState(
        targetValue = if (selected) selectedContainerColor else containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lyricsBtnBg"
    )
    val content by animateColorAsState(
        targetValue = if (selected) selectedContentColor else contentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lyricsBtnFg"
    )

    // Shape morph: circle -> squircle when selected, even squarer while pressed.
    val corner by animateDpAsState(
        targetValue = when {
            pressed  -> 10.dp
            selected -> 14.dp
            else     -> 20.dp
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "lyricsBtnCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lyricsBtnScale"
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Switch
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        LyricsAnimatedIcon(
            selected = selected,
            tint = content,
            cutColor = container,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Hand-drawn lyrics icon on a 24-unit grid: speech bubble + three text lines + music note.
 *
 * @param cutColor colour behind the icon; when the bubble is filled the text lines are drawn in it.
 */
@Composable
fun LyricsAnimatedIcon(
    selected: Boolean,
    tint: Color,
    cutColor: Color,
    modifier: Modifier = Modifier
) {
    // 0 = outlined bubble, 1 = filled bubble
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "lyricsIconFill"
    )

    // "Typing" of the three text lines, replayed whenever the icon becomes selected.
    val typing = remember { List(3) { Animatable(1f) } }
    // Note: scale pop + tilt, replayed on every toggle.
    val notePop = remember { Animatable(1f) }
    val noteTilt = remember { Animatable(0f) }
    var firstRun by remember { mutableStateOf(true) }

    LaunchedEffect(selected) {
        if (firstRun) {
            firstRun = false
            return@LaunchedEffect
        }
        launch {
            notePop.snapTo(0.35f)
            notePop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
        launch {
            noteTilt.snapTo(if (selected) -28f else 28f)
            noteTilt.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f))
        }
        if (selected) {
            typing.forEach { it.snapTo(0f) }
            typing.forEachIndexed { i, anim ->
                launch {
                    delay(i * 70L)
                    anim.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                }
            }
        } else {
            typing.forEach { anim -> launch { anim.animateTo(1f, tween(120)) } }
        }
    }

    // While selected: highlight walks through the lines (0..3) and the note sways.
    val infinite = rememberInfiniteTransition(label = "lyricsIconIdle")
    val idle = if (selected) {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
            label = "lyricsIconIdlePhase"
        )
    } else null

    Canvas(modifier) {
        val u = size.minDimension / 24f
        val idlePhase = idle?.value ?: 0f
        val f = fill

        scale(u, pivot = Offset.Zero) {
            val stroke = 1.7f

            // ── speech bubble (single outline incl. the tail) ───────────────
            val l = 2.5f; val t = 5f; val r = 16.8f; val b = 18f; val rad = 3.5f
            val bubble = Path().apply {
                moveTo(l + rad, t)
                lineTo(r - rad, t)
                arcTo(Rect(r - 2 * rad, t, r, t + 2 * rad), 270f, 90f, false)
                lineTo(r, b - rad)
                arcTo(Rect(r - 2 * rad, b - 2 * rad, r, b), 0f, 90f, false)
                lineTo(10f, b)
                lineTo(6f, 21.8f)
                lineTo(6f, b)
                lineTo(l + rad, b)
                arcTo(Rect(l, b - 2 * rad, l + 2 * rad, b), 90f, 90f, false)
                lineTo(l, t + rad)
                arcTo(Rect(l, t, l + 2 * rad, t + 2 * rad), 180f, 90f, false)
                close()
            }
            if (f > 0.01f) drawPath(bubble, tint.copy(alpha = f))
            drawPath(bubble, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // ── text lines ──────────────────────────────────────────────────
            val lineColor = lerp(tint, cutColor, f)
            val ys = floatArrayOf(9.2f, 12f, 14.8f)
            val lengths = floatArrayOf(8.2f, 5.4f, 6.6f)
            for (i in 0..2) {
                val len = lengths[i] * typing[i].value
                if (len < 0.05f) continue
                // karaoke-like emphasis: the line near the moving playhead is brighter
                var d = abs(idlePhase - (i + 0.5f))
                d = min(d, 3f - d)
                val emphasis = (1f - d).coerceIn(0f, 1f)
                val idleAlpha = 0.4f + 0.6f * emphasis
                val alpha = 1f - f * (1f - idleAlpha)
                drawLine(
                    color = lineColor.copy(alpha = alpha),
                    start = Offset(5.2f, ys[i]),
                    end = Offset(5.2f + len, ys[i]),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }

            // ── music note ──────────────────────────────────────────────────
            val head = Offset(19.9f, 8.6f)
            val sway = sin(idlePhase / 3f * 2f * PI.toFloat()) * 7f * f
            rotate(noteTilt.value + sway, pivot = head) {
                scale(notePop.value, pivot = head) {
                    drawCircle(color = tint, radius = 2.1f, center = head)
                    drawLine(
                        color = tint,
                        start = Offset(21.9f, head.y),
                        end = Offset(21.9f, 2.6f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    val flag = Path().apply {
                        moveTo(21.9f, 2.6f)
                        quadraticBezierTo(21.9f, 4.7f, 23.3f, 5.6f)
                    }
                    drawPath(flag, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
