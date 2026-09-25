package com.musicplayer.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.R

/**
 * Первый запуск: живая стена обложек, как WelcomeStep в AniSync.
 * Показывается один раз, пока PreferencesManager.hasSeenWelcome() == false.
 */
private val WelcomeBg = Color(0xFFF6F4F8)
private val WelcomeOnSurface = Color(0xFF1C1B1F)
private val WelcomeOnSurfaceVariant = Color(0xFF5A5860)
private val WelcomePrimary = Color(0xFF2C4466)
private val WelcomeOnPrimary = Color.White

private val WelcomeCoverRes = listOf(
    R.drawable.welcome_cover_01,
    R.drawable.welcome_cover_02,
    R.drawable.welcome_cover_03,
    R.drawable.welcome_cover_04,
    R.drawable.welcome_cover_05,
    R.drawable.welcome_cover_06,
    R.drawable.welcome_cover_07,
    R.drawable.welcome_cover_08,
    R.drawable.welcome_cover_09,
    R.drawable.welcome_cover_10,
    R.drawable.welcome_cover_11,
    R.drawable.welcome_cover_12,
    R.drawable.welcome_cover_13,
    R.drawable.welcome_cover_14,
    R.drawable.welcome_cover_15,
    R.drawable.welcome_cover_16,
    R.drawable.welcome_cover_17,
    R.drawable.welcome_cover_18,
)

@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val covers = WelcomeCoverRes
    if (isWideWelcome()) {
        WideWelcome(covers, onContinue, modifier)
    } else {
        CompactWelcome(covers, onContinue, modifier)
    }
}

@Composable
private fun CompactWelcome(
    covers: List<Int>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WelcomeBg)
    ) {
        PosterMarquee(
            covers = covers,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f)
                .align(Alignment.TopCenter)
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to WelcomeBg.copy(alpha = 0.86f),
                        0.16f to WelcomeBg.copy(alpha = 0.30f),
                        0.44f to WelcomeBg.copy(alpha = 0.42f),
                        0.66f to WelcomeBg,
                        1f to WelcomeBg
                    )
                )
        )

        BrandLockup(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 24.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .welcomeColumn()
                .padding(horizontal = welcomeMargin())
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WelcomeCopy(
                textAlign = TextAlign.Center,
                onContinue = onContinue
            )
        }
    }
}

@Composable
private fun WideWelcome(
    covers: List<Int>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(WelcomeBg)
    ) {
        Box(
            modifier = Modifier
                .weight(0.52f)
                .fillMaxHeight()
        ) {
            PosterMarquee(
                covers = covers,
                columnCount = 4,
                scale = 1.35f,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to WelcomeBg.copy(alpha = 0.55f),
                            0.35f to WelcomeBg.copy(alpha = 0.20f),
                            0.85f to WelcomeBg.copy(alpha = 0.75f),
                            1f to WelcomeBg
                        )
                    )
            )

            BrandLockup(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 40.dp, top = 32.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight()
                .padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .welcomeColumn(440.dp)
            ) {
                WelcomeCopy(
                    textAlign = TextAlign.Start,
                    onContinue = onContinue
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.WelcomeCopy(
    textAlign: TextAlign,
    onContinue: () -> Unit
) {
    val centred = textAlign == TextAlign.Center
    val alignment = if (centred) Alignment.CenterHorizontally else Alignment.Start

    Text(
        text = stringResource(R.string.welcome_headline),
        fontSize = 32.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.32).sp,
        textAlign = textAlign,
        color = WelcomeOnSurface,
        modifier = Modifier.align(alignment)
    )

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = stringResource(R.string.welcome_body),
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp,
        textAlign = textAlign,
        color = WelcomeOnSurfaceVariant,
        modifier = Modifier
            .align(alignment)
            .padding(horizontal = if (centred) 12.dp else 0.dp)
    )

    Spacer(modifier = Modifier.height(36.dp))

    Button(
        onClick = onContinue,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WelcomePrimary,
            contentColor = WelcomeOnPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.welcome_cta),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.align(alignment)
    ) {
        Text(
            text = stringResource(R.string.welcome_prompt),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = WelcomeOnSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.welcome_action),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            color = WelcomePrimary,
            modifier = Modifier.clickable(onClick = onContinue)
        )
    }
}

@Composable
private fun BrandLockup(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.welcome_app_icon),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            color = Color.White,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.40f),
                    blurRadius = 10f
                )
            )
        )
    }
}

@Composable
private fun PosterMarquee(
    covers: List<Int>,
    modifier: Modifier = Modifier,
    columnCount: Int = 3,
    scale: Float = 1.9f
) {
    if (covers.isEmpty()) {
        Box(modifier = modifier.background(WelcomeBg))
        return
    }

    val columns = remember(covers, columnCount) {
        val needed = columnCount * 3
        val padded = if (covers.size >= needed) covers else List(needed) { covers[it % covers.size] }
        List(columnCount) { column -> padded.filterIndexed { index, _ -> index % columnCount == column } }
    }

    val transition = rememberInfiniteTransition(label = "Marquee")

    Box(modifier = modifier.clipToBounds()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = -14f
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                columns.forEachIndexed { index, columnCovers ->
                    val duration = 46_000 + index * 9_000
                    val offset by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(duration, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "MarqueeColumn$index"
                    )
                    MarqueeColumn(
                        covers = columnCovers,
                        offset = if (index % 2 == 0) offset else 1f - offset,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarqueeColumn(
    covers: List<Int>,
    offset: Float,
    modifier: Modifier = Modifier
) {
    val doubled = remember(covers) { covers + covers }

    Column(
        modifier = modifier
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .graphicsLayer { translationY = -offset * size.height / 2f },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        doubled.forEach { resId ->
            Image(
                painter = painterResource(resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE7E0EA))
            )
        }
    }
}

@Composable
private fun isWideWelcome(): Boolean =
    LocalConfiguration.current.screenWidthDp >= 840

@Composable
private fun welcomeMargin(): Dp =
    if (LocalConfiguration.current.screenWidthDp < 600) 20.dp else 32.dp

@Composable
private fun Modifier.welcomeColumn(max: Dp = 520.dp): Modifier {
    if (LocalConfiguration.current.screenWidthDp < 600) return this
    return this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)
}
