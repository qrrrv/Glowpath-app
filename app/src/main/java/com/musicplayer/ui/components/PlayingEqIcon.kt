package com.musicplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated equalizer icon — 3 bars that bounce when isPlaying=true,
 * and freeze at a low level when paused. Adapted from PixelPlay's PlayingEqIcon.
 */
@Composable
fun PlayingEqIcon(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 3,
    barWidthDp: Dp = 3.dp,
    gapDp: Dp = 2.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eqAnim")

    // Each bar gets a different phase / duration so they don't move in sync
    val durations = remember { listOf(420, 340, 490) }
    val minHeights = remember { listOf(0.15f, 0.25f, 0.10f) }
    val maxHeights = remember { listOf(1.0f, 0.75f, 0.90f) }

    val heights = (0 until barCount).map { i ->
        val raw by infiniteTransition.animateFloat(
            initialValue = minHeights[i % minHeights.size],
            targetValue  = maxHeights[i % maxHeights.size],
            animationSpec = infiniteRepeatable(
                animation  = tween(durations[i % durations.size], easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        )
        val target by animateFloatAsState(
            targetValue   = if (isPlaying) raw else minHeights[i % minHeights.size],
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label         = "barTarget$i"
        )
        target
    }

    Row(
        modifier        = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(gapDp)
    ) {
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(barWidthDp)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(color)
            )
        }
    }
}
