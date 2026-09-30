package com.musicplayer.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adapted from BoomingMusic (GPL-3.0): alpha-mask fading edges (not an overlay),
 * so the list smoothly dissolves into ANY background.
 */
@Immutable
data class FadingEdges(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
) {
    companion object {
        val None = FadingEdges()
    }
}

fun Modifier.fadingEdges(edges: FadingEdges): Modifier =
    if (edges == FadingEdges.None) {
        this
    } else {
        this.composed {
            val density = LocalDensity.current
            val topPx = with(density) { edges.top.toPx() }
            val bottomPx = with(density) { edges.bottom.toPx() }

            graphicsLayer { alpha = 0.99f }
                .drawWithCache {
                    val width = size.width
                    val height = size.height
                    val topBrush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f,
                        endY = topPx
                    )
                    val bottomBrush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = height - bottomPx,
                        endY = height
                    )
                    onDrawWithContent {
                        drawContent()
                        if (topPx > 0f) {
                            drawRect(
                                brush = topBrush,
                                topLeft = Offset(0f, 0f),
                                size = Size(width, topPx),
                                blendMode = BlendMode.DstIn
                            )
                        }
                        if (bottomPx > 0f) {
                            drawRect(
                                brush = bottomBrush,
                                topLeft = Offset(0f, height - bottomPx),
                                size = Size(width, bottomPx),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
                }
        }
    }
