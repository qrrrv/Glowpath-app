package com.musicplayer.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlin.math.*

/**
 * Красивый слайдер с анимированной волной — заменяет WaveSeekBar.
 * Волна пульсирует во время воспроизведения, затихает на паузе.
 * Thumb плавно трансформируется в вертикальную черту при касании.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun WavyMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    trackHeight: Dp = 6.dp,
    thumbRadius: Dp = 8.dp,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    waveAmplitudeWhenPlaying: Dp = 3.dp,
    waveLength: Dp = 80.dp,
    waveAnimationDuration: Int = 2000,
    isPlaying: Boolean = true,
    thumbLineHeightWhenInteracting: Dp = 24.dp,
    isWaveEligible: Boolean = true
) {
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )

    val shouldShowWave = isWaveEligible && isPlaying && !isInteracting

    val animatedWaveAmplitude by animateDpAsState(
        targetValue = if (shouldShowWave) waveAmplitudeWhenPlaying else 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "WaveAmplitudeAnim"
    )

    val phaseShiftAnim = remember { Animatable(0f) }
    val phaseShift = phaseShiftAnim.value

    LaunchedEffect(shouldShowWave, waveAnimationDuration) {
        if (shouldShowWave && waveAnimationDuration > 0) {
            val fullRotation = (2 * PI).toFloat()
            while (shouldShowWave) {
                val start = (phaseShiftAnim.value % fullRotation).let { if (it < 0f) it + fullRotation else it }
                phaseShiftAnim.snapTo(start)
                phaseShiftAnim.animateTo(
                    targetValue = start + fullRotation,
                    animationSpec = tween(durationMillis = waveAnimationDuration, easing = LinearEasing)
                )
            }
        }
    }

    val trackHeightPx = with(LocalDensity.current) { trackHeight.toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
    val waveAmplitudePx = with(LocalDensity.current) { animatedWaveAmplitude.toPx() }
    val waveLengthPx = with(LocalDensity.current) { waveLength.toPx() }
    val waveFrequency = if (waveLengthPx > 0f) ((2 * PI) / waveLengthPx).toFloat() else 0f
    val thumbLineHeightPx = with(LocalDensity.current) { thumbLineHeightWhenInteracting.toPx() }
    val thumbGapPx = with(LocalDensity.current) { 4.dp.toPx() }

    val wavePath = remember { Path() }

    val sliderVisualHeight = remember(trackHeight, thumbRadius, thumbLineHeightWhenInteracting) {
        max(trackHeight * 2, max(thumbRadius * 2, thumbLineHeightWhenInteracting) + 8.dp)
    }

    val hapticFeedback = LocalHapticFeedback.current

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val lastHapticStep = remember { mutableIntStateOf(-1) }

        Slider(
            value = value,
            onValueChange = { newValue ->
                val currentStep = (newValue * 100 / (valueRange.endInclusive - valueRange.start)).toInt()
                if (currentStep != lastHapticStep.intValue) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticStep.intValue = currentStep
                }
                onValueChange(newValue)
            },
            modifier = Modifier.fillMaxWidth().height(sliderVisualHeight),
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
                .drawWithCache {
                    val canvasWidth = size.width
                    val localCenterY = size.height / 2f
                    val localTrackStart = thumbRadiusPx
                    val localTrackEnd = canvasWidth - thumbRadiusPx
                    val localTrackWidth = (localTrackEnd - localTrackStart).coerceAtLeast(0f)

                    val normalizedValue = if (valueRange.endInclusive == valueRange.start) 0f
                    else ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

                    onDrawWithContent {
                        val currentProgressPxEnd = localTrackStart + localTrackWidth * normalizedValue

                        // Неактивная часть трека
                        if (currentProgressPxEnd < localTrackEnd) {
                            drawLine(
                                color = inactiveTrackColor,
                                start = Offset(currentProgressPxEnd, localCenterY),
                                end = Offset(localTrackEnd, localCenterY),
                                strokeWidth = trackHeightPx,
                                cap = StrokeCap.Round
                            )
                        }

                        // Активная часть (волна или линия)
                        if (normalizedValue > 0f) {
                            val activeEnd = currentProgressPxEnd - (thumbGapPx * thumbInteractionFraction)

                            if (waveAmplitudePx > 0.01f && waveFrequency > 0f) {
                                wavePath.reset()
                                val waveStart = localTrackStart
                                val waveEnd = activeEnd.coerceAtLeast(waveStart)
                                if (waveEnd > waveStart) {
                                    val periodPx = ((2 * PI) / waveFrequency).toFloat()
                                    val waveStep = (periodPx / 20f).coerceAtLeast(1.2f).coerceAtMost(trackHeightPx)

                                    fun yAt(x: Float): Float = (localCenterY + waveAmplitudePx * sin(waveFrequency * x + phaseShift))
                                        .coerceIn(localCenterY - waveAmplitudePx - trackHeightPx / 2f,
                                            localCenterY + waveAmplitudePx + trackHeightPx / 2f)

                                    var prevX = waveStart
                                    var prevY = yAt(prevX)
                                    wavePath.moveTo(prevX, prevY)

                                    var x = prevX + waveStep
                                    while (x < waveEnd) {
                                        val y = yAt(x)
                                        val midX = (prevX + x) * 0.5f
                                        val midY = (prevY + y) * 0.5f
                                        wavePath.quadraticBezierTo(prevX, prevY, midX, midY)
                                        prevX = x; prevY = y; x += waveStep
                                    }
                                    wavePath.quadraticBezierTo(prevX, prevY, waveEnd, yAt(waveEnd))

                                    drawPath(path = wavePath, color = activeTrackColor,
                                        style = Stroke(width = trackHeightPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            } else {
                                if (activeEnd > localTrackStart) {
                                    drawLine(color = activeTrackColor,
                                        start = Offset(localTrackStart, localCenterY),
                                        end = Offset(activeEnd, localCenterY),
                                        strokeWidth = trackHeightPx, cap = StrokeCap.Round)
                                }
                            }
                        }

                        // Thumb
                        val thumbX = localTrackStart + localTrackWidth * normalizedValue
                        val thumbW = lerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, thumbInteractionFraction)
                        val thumbH = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
                        drawRoundRect(
                            color = thumbColor,
                            topLeft = Offset(thumbX - thumbW / 2f, localCenterY - thumbH / 2f),
                            size = Size(thumbW, thumbH),
                            cornerRadius = CornerRadius(thumbW / 2f)
                        )
                    }
                }
        )
    }
}
