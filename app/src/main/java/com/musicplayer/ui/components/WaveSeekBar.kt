package com.musicplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicplayer.ui.theme.AccentMuted
import com.musicplayer.ui.theme.AccentPeach
import com.musicplayer.ui.theme.BrownElevated
import kotlin.math.sin

@Composable
fun WaveSeekBar(
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = BrownElevated,
    progressColor: Color = AccentPeach,
    thumbColor: Color = AccentPeach,
    waveAmplitudeTarget: Dp = 6.dp,
    waveLength: Dp = 28.dp,
    strokeWidth: Dp = 4.dp,
    thumbRadius: Dp = 8.dp
) {
    val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    // Animate wave offset for movement
    val wavePhase = rememberInfiniteTransition(label = "wave")
    val waveOffset by wavePhase.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    // Animate amplitude: 0 when paused, waveAmplitudeTarget when playing
    val amplitudeAnimated by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "amplitude"
    )

    Canvas(
        modifier = modifier
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    val seekTo = ((offset.x / size.width) * duration).toLong().coerceIn(0L, duration)
                    onSeek(seekTo)
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val seekTo = ((change.position.x / size.width) * duration).toLong().coerceIn(0L, duration)
                    onSeek(seekTo)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val progressX = w * progress
        val ampPx = waveAmplitudeTarget.toPx() * amplitudeAnimated
        val waveLenPx = waveLength.toPx()
        val sw = strokeWidth.toPx()
        val tr = thumbRadius.toPx()

        // Draw background track
        drawLine(
            color = trackColor,
            start = Offset(0f, cy),
            end = Offset(w, cy),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )

        // Draw wavy progress (played portion)
        if (progressX > sw) {
            val wavePath = Path()
            var x = 0f
            var first = true
            while (x <= progressX) {
                val angle = (x / waveLenPx) * 2f * Math.PI.toFloat() + waveOffset
                val y = cy + sin(angle) * ampPx
                if (first) {
                    wavePath.moveTo(x, y)
                    first = false
                } else {
                    wavePath.lineTo(x, y)
                }
                x += 1f
            }
            drawPath(
                path = wavePath,
                color = progressColor,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }

        // Draw thumb circle
        drawCircle(
            color = thumbColor,
            radius = tr,
            center = Offset(progressX, cy)
        )
    }
}

@Composable
fun VolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val progressColor = AccentPeach
    val trackColor = BrownElevated

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onVolumeChange((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onVolumeChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val cy = h / 2f
            val trackH = 20.dp.toPx()
            val progressX = w * volume

            // Background pill
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, cy - trackH / 2),
                size = androidx.compose.ui.geometry.Size(w, trackH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2)
            )

            // Progress pill
            if (progressX > 0f) {
                drawRoundRect(
                    color = progressColor,
                    topLeft = Offset(0f, cy - trackH / 2),
                    size = androidx.compose.ui.geometry.Size(progressX, trackH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2)
                )
            }

            // Divider line at thumb
            drawLine(
                color = progressColor.copy(alpha = 0.9f),
                start = Offset(progressX, cy - trackH / 2 + 4.dp.toPx()),
                end = Offset(progressX, cy + trackH / 2 - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Small dot as thumb indicator
            drawCircle(
                color = trackColor,
                radius = 4.dp.toPx(),
                center = Offset(progressX + 10.dp.toPx(), cy)
            )
        }
    }
}
