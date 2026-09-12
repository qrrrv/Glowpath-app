package com.musicplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp

/**
 * Expressive animated top bar with a title that scales and slides as the user scrolls.
 *
 * collapseFraction: 0f = fully expanded (large title), 1f = fully collapsed (small title)
 *
 * Adapted from PixelPlay's ExpressiveTopBarContent.
 */
@Composable
fun ExpressiveTopBarContent(
    title: String,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    collapsedTitleStartPadding: Dp = 64.dp,
    expandedTitleStartPadding: Dp = 16.dp,
    containerHeightRange: Pair<Dp, Dp> = 88.dp to 56.dp,
    collapsedTitleVerticalBias: Float = -1f,
    maxLines: Int = 2,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingContent: (@Composable () -> Unit)? = null
) {
    val clamped       = collapseFraction.coerceIn(0f, 1f)
    val titleScale    = lerp(1.2f, 0.8f, clamped)
    val paddingStart  = lerp(expandedTitleStartPadding, collapsedTitleStartPadding, clamped)
    val vBias         = lerp(1f, collapsedTitleVerticalBias, clamped)
    val alignment     = BiasAlignment(horizontalBias = -1f, verticalBias = vBias)
    val containerH    = lerp(containerHeightRange.first, containerHeightRange.second, clamped)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(alignment)
                .height(containerH)
                .fillMaxWidth()
                .padding(start = paddingStart, end = 24.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = contentColor,
                    maxLines   = maxLines,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.headlineMedium.fontSize * 1.1f,
                    modifier   = Modifier.graphicsLayer {
                        scaleX          = titleScale
                        scaleY          = titleScale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text     = subtitle,
                        style    = MaterialTheme.typography.labelLarge,
                        color    = contentColor.copy(alpha = 0.65f),
                        modifier = Modifier.alpha(1f - clamped)
                    )
                }
                if (supportingContent != null) {
                    Box(modifier = Modifier.alpha(1f - clamped)) {
                        supportingContent()
                    }
                }
            }
        }
    }
}
