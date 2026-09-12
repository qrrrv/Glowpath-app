package com.musicplayer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Слайдер прогресса с анимированной волной.
 * Волна рисуется через Canvas + sin() — без зависимости от alpha API,
 * работает плавно на любых устройствах.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WavySliderExpressive(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    isPlaying: Boolean = true,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    wavelength: Dp = 48.dp,
    waveSpeed: Dp = 24.dp,
    thumbLineHeightWhenInteracting: Dp = 24.dp
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }
    val wavelengthPx = with(density) { wavelength.toPx() }

    val normalizedValue = if (valueRange.endInclusive == valueRange.start) 0f
    else ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val resolvedIS = interactionSource ?: remember { MutableInteractionSource() }
    val isDragged by resolvedIS.collectIsDraggedAsState()
    val isPressed by resolvedIS.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )

    val animatedAmplitude by animateFloatAsState(
        targetValue = if (enabled && isPlaying && !isInteracting) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "amplitude"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wavePhase")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "wavePhase"
    )

    val containerHeight = maxOf(thumbRadius * 2, thumbLineHeightWhenInteracting, 20.dp)

    Box(
        modifier = modifier.fillMaxWidth().height(containerHeight),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = clampedValue,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(containerHeight),
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = resolvedIS,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent
            )
        )

        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = thumbRadius)) {
            val trackEnd = size.width
            val thumbX = trackEnd * normalizedValue
            val cy = size.height / 2f
            val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            val waveAmplitudePx = strokeWidthPx * 3f * animatedAmplitude

            if (thumbX < trackEnd - strokeWidthPx) {
                val inactivePath = Path()
                var x = thumbX + thumbRadiusPx + 4f
                var firstPoint = true
                while (x <= trackEnd) {
                    val phase = wavePhase + (x / wavelengthPx) * (2f * PI.toFloat())
                    val y = cy + sin(phase) * waveAmplitudePx * 0.5f
                    if (firstPoint) { inactivePath.moveTo(x, y); firstPoint = false }
                    else inactivePath.lineTo(x, y)
                    x += 2f
                }
                drawPath(inactivePath, inactiveTrackColor, style = stroke)
            }

            if (thumbX > strokeWidthPx) {
                val activePath = Path()
                var x = 0f
                var firstPoint = true
                val endX = (thumbX - thumbRadiusPx - 4f).coerceAtLeast(0f)
                while (x <= endX) {
                    val phase = wavePhase + (x / wavelengthPx) * (2f * PI.toFloat())
                    val y = cy + sin(phase) * waveAmplitudePx
                    if (firstPoint) { activePath.moveTo(x, y); firstPoint = false }
                    else activePath.lineTo(x, y)
                    x += 2f
                }
                drawPath(activePath, activeTrackColor, style = stroke)
            }

            fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
            val currentWidth = lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.4f, thumbInteractionFraction)
            val currentHeight = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbX - currentWidth / 2f, cy - currentHeight / 2f),
                size = Size(currentWidth, currentHeight),
                cornerRadius = CornerRadius(currentWidth / 2f)
            )
        }
    }
}
