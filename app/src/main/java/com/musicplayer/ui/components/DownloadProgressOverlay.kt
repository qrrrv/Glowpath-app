package com.musicplayer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.musicplayer.data.DownloadState
import com.musicplayer.data.OnlineSong
import kotlin.math.PI
import kotlin.math.sin

/**
 * Красивая кнопка-индикатор загрузки для каждого трека.
 * Анимированно переходит: Idle → Pending (Lottie) → Progress → Done.
 */
@Composable
fun DownloadButton(
    song: OnlineSong,
    dlState: DownloadState,
    isAdded: Boolean,
    accentColor: Color,
    onDownload: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(44.dp)
    ) {
        when (dlState) {
            DownloadState.Idle, is DownloadState.Err -> {
                IdleDownloadButton(accentColor = accentColor, onClick = onDownload)
            }
            DownloadState.Pending -> {
                CircularWaveDownloadButton(progress = null, accentColor = accentColor)
            }
            is DownloadState.Progress -> {
                CircularWaveDownloadButton(progress = dlState.percent / 100f, accentColor = accentColor)
            }
            is DownloadState.Done -> {
                if (isAdded) DoneAddedButton(accentColor = accentColor)
                else DoneAddButton(accentColor = accentColor, onClick = onAdd)
            }
        }
    }
}

// ── Idle: пульсирующая кнопка ─────────────────────────────────────────────────

@Composable
private fun IdleDownloadButton(accentColor: Color, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "dlPulse")
    val glowAlpha by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = 0.3f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "glow"
    )
    val arrowOff by inf.animateFloat(
        initialValue  = -2f,
        targetValue   = 2f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "arrow"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.1f + glowAlpha * 0.3f))
            .drawBehind {
                drawCircle(
                    color  = accentColor.copy(alpha = glowAlpha * 0.5f),
                    radius = size.minDimension / 2f + 7f,
                    style  = Stroke(width = 1.5f)
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = "Скачать",
            tint     = accentColor,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { translationY = arrowOff }
        )
    }
}

@Composable
private fun CircularWaveDownloadButton(progress: Float?, accentColor: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progress ?: 0.24f).coerceIn(0f, 1f),
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "downloadProgress"
    )
    val transition = rememberInfiniteTransition(label = "downloadWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing)
        ),
        label = "downloadPhase"
    )
    val indeterminateOffset by transition.animateFloat(
        initialValue = -90f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "downloadIndeterminateOffset"
    )
    val wavePath = remember { Path() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(40.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.095f
            val radius = size.minDimension / 2f - strokeWidth * 1.2f
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val sweepAngle = if (progress == null) 110f else (360f * animatedProgress).coerceAtLeast(6f)
            val startAngle = if (progress == null) indeterminateOffset else -90f

            drawCircle(
                color = accentColor.copy(alpha = 0.18f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            wavePath.reset()
            val steps = (sweepAngle / 5f).toInt().coerceAtLeast(18)
            for (i in 0..steps) {
                val fraction = i / steps.toFloat()
                val angleDeg = startAngle + sweepAngle * fraction
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val radialWave = strokeWidth * 0.78f * sin(phase + fraction * (2f * PI).toFloat() * 2.2f)
                val currentRadius = radius + radialWave
                val x = center.x + currentRadius * kotlin.math.cos(angleRad).toFloat()
                val y = center.y + currentRadius * kotlin.math.sin(angleRad).toFloat()
                if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            drawPath(
                path = wavePath,
                color = accentColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            )
        }

        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ── Done: добавить в библиотеку ───────────────────────────────────────────────

@Composable
private fun DoneAddButton(accentColor: Color, onClick: () -> Unit) {
    val inf   = rememberInfiniteTransition(label = "addPulse")
    val glow  by inf.animateFloat(
        initialValue  = 0.1f,
        targetValue   = 0.3f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "addGlow"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.15f + glow))
            .drawBehind {
                drawCircle(
                    color  = accentColor.copy(alpha = glow * 0.6f),
                    radius = size.minDimension / 2f + 6f,
                    style  = Stroke(width = 1.5f)
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = "Добавить в библиотеку",
            tint     = accentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DoneAddedButton(accentColor: Color) {
    val inf   = rememberInfiniteTransition(label = "donePulse")
    val scale by inf.animateFloat(
        initialValue  = 0.95f,
        targetValue   = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "doneScale"
    )
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = "Добавлено",
        tint     = accentColor,
        modifier = Modifier.size(26.dp).scale(scale)
    )
}

// ── Mini equalizer bars ───────────────────────────────────────────────────────

@Composable
private fun MiniEqualizerBars(
    accentColor: Color,
    barCount: Int = 3
) {
    val inf = rememberInfiniteTransition(label = "eqBars")
    val heights = (0 until barCount).map { i ->
        inf.animateFloat(
            initialValue  = 0.25f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(250 + i * 100, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        )
    }

    Row(
        modifier              = Modifier.size(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        heights.forEach { heightFraction ->
            val h by heightFraction
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
        }
    }
}

@Composable
fun WavyDownloadProgressIndicator(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progress ?: 0.34f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "downloadWaveProgress"
    )
    val wavePath = remember { Path() }
    val transition = rememberInfiniteTransition(label = "downloadWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing)
        ),
        label = "downloadWavePhase"
    )
    val indeterminateShift by transition.animateFloat(
        initialValue = -0.22f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing)
        ),
        label = "downloadWaveShift"
    )
    val amplitudeScale by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "downloadWaveAmp"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val strokeWidth = size.height * 0.34f
        val centerY = size.height / 2f
        val startX = strokeWidth / 2f
        val endX = size.width - strokeWidth / 2f
        val totalWidth = (endX - startX).coerceAtLeast(0f)
        val wavelength = (size.width / 5.4f).coerceAtLeast(26f)
        val amplitude = strokeWidth * 0.95f * amplitudeScale

        drawLine(
            color = trackColor,
            start = androidx.compose.ui.geometry.Offset(startX, centerY),
            end = androidx.compose.ui.geometry.Offset(endX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        val indeterminateWidth = totalWidth * 0.32f
        val activeStart = if (progress == null) {
            (startX + totalWidth * indeterminateShift).coerceIn(startX, endX)
        } else {
            startX
        }
        val activeEnd = if (progress == null) {
            (activeStart + indeterminateWidth).coerceIn(startX, endX)
        } else {
            (startX + totalWidth * animatedProgress).coerceIn(startX, endX)
        }

        if (activeEnd - activeStart <= 1f) return@Canvas

        val step = (wavelength / 18f).coerceAtLeast(1.6f)
        fun yAt(x: Float): Float = centerY + amplitude * sin(((x - activeStart) / wavelength) * (2f * PI).toFloat() + phase)

        wavePath.reset()
        var prevX = activeStart
        var prevY = yAt(prevX)
        wavePath.moveTo(prevX, prevY)

        var x = activeStart + step
        while (x < activeEnd) {
            val y = yAt(x)
            val midX = (prevX + x) * 0.5f
            val midY = (prevY + y) * 0.5f
            wavePath.quadraticBezierTo(prevX, prevY, midX, midY)
            prevX = x
            prevY = y
            x += step
        }
        wavePath.quadraticBezierTo(prevX, prevY, activeEnd, yAt(activeEnd))

        drawPath(
            path = wavePath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        if (progress != null) {
            drawCircle(
                color = color,
                radius = strokeWidth * 0.66f,
                center = androidx.compose.ui.geometry.Offset(activeEnd, centerY)
            )
        }
    }
}
