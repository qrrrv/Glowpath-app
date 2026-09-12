package com.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay

/**
 * A single toggle segment button — used inside BottomToggleRow.
 * Adapated from PixelPlay's ToggleSegmentButton.
 */
@Composable
fun ToggleSegmentButton(
    active: Boolean,
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    activeColor: Color,
    activeContentColor: Color,
    inactiveColor: Color,
    inactiveContentColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 60.dp
) {
    val bgColor by animateColorAsState(
        targetValue   = if (active) activeColor else inactiveColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "toggleBg"
    )
    val iconColor by animateColorAsState(
        targetValue   = if (active) activeContentColor else inactiveContentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "toggleIcon"
    )

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label         = "toggleScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .clickable {
                pressed = true
                onClick()
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = iconColor,
            modifier           = Modifier.size(22.dp)
        )
    }

    // Release press
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}
