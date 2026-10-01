package com.musicplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Which icon animation a [ToggleSegmentButton] plays when tapped. */
enum class ToggleIconAnim {
    /** Old behaviour: tiny tilt while pressed. */
    Generic,

    /** 3D flip of the crossing arrows (they "swap places"). */
    Shuffle,

    /** Half-turn of the loop arrows; 1-repeat icon swaps in with a springy scale. */
    Repeat,

    /** Squash -> overshoot "pop" of the heart + expanding ring and sparkles. */
    Favorite
}

/**
 * A single toggle segment button — used inside BottomToggleRow.
 * Adapted from PixelPlay's ToggleSegmentButton.
 *
 * Material 3 Expressive motion:
 *  - pressed: the pill morphs to a squarer shape and squashes a little (spring);
 *  - released: it springs back to the full pill;
 *  - the icon plays its own animation ([iconAnim]).
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
    cornerRadius: Dp = 60.dp,
    iconAnim: ToggleIconAnim = ToggleIconAnim.Generic
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

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // ── Container: squash + shape morph (pill <-> squircle) while pressed ─────
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "toggleScale"
    )
    val radius by animateDpAsState(
        targetValue   = if (isPressed) 22.dp else cornerRadius,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label         = "toggleRadius"
    )

    // ── Icon: press / active scale ────────────────────────────────────────────
    val iconScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.82f
            active    -> 1.08f
            else      -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "toggleIconScale"
    )
    val genericRotation by animateFloatAsState(
        targetValue = if (isPressed && iconAnim == ToggleIconAnim.Generic) -8f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "toggleIconRotation"
    )

    // ── Shuffle: every tap flips the icon by 180° around the X axis ───────────
    var flipTarget by remember { mutableFloatStateOf(0f) }
    val flip by animateFloatAsState(
        targetValue   = flipTarget,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 220f),
        label         = "shuffleFlip"
    )

    // ── Repeat: every tap turns the loop by 180° (the icon is point-symmetric) ─
    var spinTarget by remember { mutableFloatStateOf(0f) }
    val spin by animateFloatAsState(
        targetValue   = spinTarget,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 240f),
        label         = "repeatSpin"
    )

    // ── Favourite: heart pop + burst ──────────────────────────────────────────
    val heartScale = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) } // 1 = finished / hidden

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(radius))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                when (iconAnim) {
                    ToggleIconAnim.Shuffle -> {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        flipTarget += 180f
                    }
                    ToggleIconAnim.Repeat -> {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        spinTarget += 180f
                    }
                    ToggleIconAnim.Favorite -> {
                        val willBeActive = !active
                        haptic.performHapticFeedback(
                            if (willBeActive) HapticFeedbackType.LongPress
                            else HapticFeedbackType.TextHandleMove
                        )
                        scope.launch {
                            if (willBeActive) {
                                launch {
                                    burst.snapTo(0f)
                                    burst.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
                                }
                                heartScale.snapTo(0.5f)
                                heartScale.animateTo(
                                    1f,
                                    spring(dampingRatio = 0.3f, stiffness = 380f)
                                )
                            } else {
                                heartScale.snapTo(1f)
                                heartScale.animateTo(0.72f, tween(80))
                                heartScale.animateTo(
                                    1f,
                                    spring(dampingRatio = 0.5f, stiffness = 500f)
                                )
                            }
                        }
                    }
                    ToggleIconAnim.Generic -> {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                onClick()
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        // Sparkles + ring for the heart.
        if (iconAnim == ToggleIconAnim.Favorite && burst.value < 1f) {
            Canvas(Modifier.matchParentSize()) {
                val t = burst.value
                val e = 1f - (1f - t) * (1f - t) // ease-out
                val center = this.center
                val maxR = 24.dp.toPx()
                val minR = 8.dp.toPx()

                drawCircle(
                    color  = activeContentColor.copy(alpha = ((1f - t) * 0.5f).coerceIn(0f, 1f)),
                    radius = minR + (maxR - minR) * e,
                    center = center,
                    style  = Stroke(width = 2.dp.toPx() * (1f - t) + 0.5f)
                )

                for (i in 0 until 8) {
                    val angle = (i * 45f + 22.5f) * (PI.toFloat() / 180f)
                    val big = i % 2 == 0
                    val startDist = (if (big) 12.dp else 10.dp).toPx()
                    val endDist = (if (big) 26.dp else 22.dp).toPx()
                    val dist = startDist + (endDist - startDist) * e
                    val r = (if (big) 3.dp else 2.dp).toPx() * (1f - t)
                    drawCircle(
                        color  = activeContentColor.copy(alpha = (1f - t).coerceIn(0f, 1f)),
                        radius = r,
                        center = Offset(center.x + cos(angle) * dist, center.y + sin(angle) * dist)
                    )
                }
            }
        }

        AnimatedContent(
            targetState = icon,
            modifier = Modifier.graphicsLayer {
                var s = iconScale
                when (iconAnim) {
                    ToggleIconAnim.Shuffle -> {
                        rotationX = flip
                        cameraDistance = 12f * density
                        // little squash in the middle of the flip
                        val frac = (((flip % 180f) + 180f) % 180f) / 180f
                        s *= 1f - 0.18f * sin(frac * PI.toFloat())
                    }
                    ToggleIconAnim.Repeat -> {
                        rotationZ = spin
                    }
                    ToggleIconAnim.Favorite -> {
                        s *= heartScale.value
                    }
                    ToggleIconAnim.Generic -> {
                        rotationZ = genericRotation
                    }
                }
                scaleX = s
                scaleY = s
            },
            transitionSpec = {
                if (iconAnim == ToggleIconAnim.Favorite) {
                    (fadeIn(tween(70)) togetherWith fadeOut(tween(70)))
                        .using(SizeTransform(clip = false))
                } else {
                    (
                        (scaleIn(
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                            initialScale = 0.4f
                        ) + fadeIn(tween(90))) togetherWith
                        (scaleOut(animationSpec = tween(90), targetScale = 0.4f) + fadeOut(tween(90)))
                    ).using(SizeTransform(clip = false))
                }
            },
            label = "toggleIconSwap"
        ) { shownIcon ->
            Icon(
                imageVector        = shownIcon,
                contentDescription = contentDescription,
                tint               = iconColor,
                modifier           = Modifier.size(22.dp)
            )
        }
    }
}
