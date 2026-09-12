package com.musicplayer.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Shimmer — мигающий градиент для состояния загрузки.
 * Использует цвета MaterialTheme для поддержки тёмной и светлой темы.
 * Пример: ShimmerBox(modifier = Modifier.fillMaxWidth().height(48.dp))
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    // MaterialTheme цвета для корректной работы в тёмной/светлой теме
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val shimmerColors = listOf(
        baseColor,
        highlightColor,
        baseColor,
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, delayMillis = 200)
        ),
        label = "shimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Box(modifier = modifier.background(brush = brush))
}

/**
 * Shimmer-строка — удобная обёртка для строк плейлиста при загрузке.
 */
@Composable
fun ShimmerSongRow(modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier = modifier.clip(RoundedCornerShape(8.dp))
    )
}
