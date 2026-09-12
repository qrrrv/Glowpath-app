package com.musicplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.musicplayer.R
import com.musicplayer.ui.theme.LocalAppColors
import com.musicplayer.ui.theme.LocalAppFontFamily
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

@Composable
fun SplashScreen(
    loadedCount: Int,
    totalCount: Int,
    isReady: Boolean
) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current

    val targetProgress = if (totalCount > 0)
        (loadedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    else 0f

    val animProgress by animateFloatAsState(
        targetValue   = targetProgress,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label         = "progress"
    )

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading))
    val lottieProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = LottieConstants.IterateForever,
        isPlaying   = true,
        speed       = 1f
    )

    val inf = rememberInfiniteTransition(label = "splash_inf")

    val orbAngle by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "orbAngle"
    )

    val haloAlpha by inf.animateFloat(
        initialValue  = 0.12f,
        targetValue   = 0.32f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "haloAlpha"
    )

    val glowAlpha by inf.animateFloat(
        initialValue  = 0.25f,
        targetValue   = 0.75f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            drawRect(color = c.bgDeep)

            val orb1x = cx + cos(orbAngle.toDouble()).toFloat() * w * 0.22f
            val orb1y = cy + sin(orbAngle.toDouble()).toFloat() * h * 0.15f
            drawCircle(
                brush = Brush.radialGradient(listOf(c.accent.copy(0.38f), Color.Transparent), center = Offset(orb1x, orb1y), radius = w * 0.52f),
                radius = w * 0.52f, center = Offset(orb1x, orb1y)
            )

            val orb2x = cx - cos(orbAngle.toDouble() * 0.7).toFloat() * w * 0.28f
            val orb2y = cy - sin(orbAngle.toDouble() * 0.7).toFloat() * h * 0.18f
            drawCircle(
                brush = Brush.radialGradient(listOf(c.accentVar.copy(0.28f), Color.Transparent), center = Offset(orb2x, orb2y), radius = w * 0.44f),
                radius = w * 0.44f, center = Offset(orb2x, orb2y)
            )

            val orb3x = cx + cos(orbAngle.toDouble() * 1.3 + PI).toFloat() * w * 0.2f
            val orb3y = cy + h * 0.28f
            drawCircle(
                brush = Brush.radialGradient(listOf(c.accentMuted.copy(0.22f), Color.Transparent), center = Offset(orb3x, orb3y), radius = w * 0.38f),
                radius = w * 0.38f, center = Offset(orb3x, orb3y)
            )

            drawCircle(
                brush = Brush.radialGradient(listOf(c.accent.copy(haloAlpha), Color.Transparent), center = Offset(cx, cy), radius = w * 0.35f),
                radius = w * 0.35f, center = Offset(cx, cy)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 44.dp)
        ) {
            LottieAnimation(
                composition = composition,
                progress    = { lottieProgress },
                modifier    = Modifier.size(120.dp)
            )

            Spacer(Modifier.height(16.dp))

            LoadBar(
                progress  = animProgress,
                glowAlpha = glowAlpha,
                accent    = c.accent,
                accentVar = c.accentVar,
                bg        = c.bgCard.copy(0.5f),
                modifier  = Modifier.fillMaxWidth().height(3.dp)
            )

            Spacer(Modifier.height(14.dp))

            val pct = (animProgress * 100).toInt()
            Text(
                when {
                    totalCount == 0 -> "Подготовка…"
                    pct >= 100      -> "Готово"
                    else            -> "$pct%"
                },
                color         = c.textDisabled,
                fontFamily    = font,
                fontSize      = 12.sp,
                letterSpacing = 1.sp,
                textAlign     = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadBar(
    progress: Float,
    glowAlpha: Float,
    accent: Color,
    accentVar: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w     = size.width
        val h     = size.height
        val r     = h / 2f
        val fillW = (w * progress).coerceAtLeast(0f)

        drawRoundRect(color = bg, size = Size(w, h), cornerRadius = CornerRadius(r, r))

        if (fillW > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.6f), accentVar), startX = 0f, endX = fillW),
                size         = Size(fillW, h),
                cornerRadius = CornerRadius(r, r)
            )
            val tipX  = fillW
            val tipCy = h / 2f
            val glowR = h * 5f
            drawCircle(
                brush = Brush.radialGradient(listOf(accentVar.copy(alpha = glowAlpha * 0.55f), Color.Transparent), center = Offset(tipX, tipCy), radius = glowR),
                radius = glowR, center = Offset(tipX, tipCy)
            )
            drawCircle(color = accentVar, radius = h * 2.4f, center = Offset(tipX, tipCy))
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = h * 1f, center = Offset(tipX, tipCy))
        }
    }
}
