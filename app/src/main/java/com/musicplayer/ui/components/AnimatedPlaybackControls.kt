package com.musicplayer.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.musicplayer.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

/**
 * Анимированные кнопки управления плеером.
 *
 * При нажатии на кнопку она расширяется, а остальные — сжимаются.
 * Кнопка Play/Pause меняет форму: круг при паузе, скруглённый прямоугольник при воспроизведении.
 * Иконка Play/Pause меняется плавным Crossfade.
 *
 * @param isPlayingProvider лямбда, возвращающая текущий статус воспроизведения
 * @param pressAnimationSpec анимация изменения веса (weight) при нажатии
 * @param releaseDelay задержка до "отпускания" кнопки (мс)
 * @param playPauseCornerPlaying радиус углов кнопки Play/Pause во время воспроизведения
 * @param playPauseCornerPaused радиус углов кнопки Play/Pause на паузе
 */
@Composable
fun AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = MaterialTheme.colorScheme.secondaryContainer,
    colorPlayPause: Color = MaterialTheme.colorScheme.primary,
    tintPlayPauseIcon: Color = MaterialTheme.colorScheme.onPrimary,
    tintOtherIcons: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    colorPreviousButton: Color = colorOtherButtons,
    colorNextButton: Color = colorOtherButtons,
    tintPreviousIcon: Color = tintOtherIcons,
    tintNextIcon: Color = tintOtherIcons,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp,
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<PlaybackButtonType?>(null) }
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)
    val latestLastClicked by rememberUpdatedState(newValue = lastClicked)
    val isPlayPauseLocked =
        lastClicked == PlaybackButtonType.NEXT || lastClicked == PlaybackButtonType.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(lastClicked) {
        if (lastClicked != null) {
            delay(releaseDelay)
            lastClicked = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }
        val shouldDelay = latestLastClicked != PlaybackButtonType.PLAY_PAUSE
        if (shouldDelay) delay(releaseDelay)
        if (!latestIsPlayingProvider()) {
            pendingPlayPauseState = false
        }
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fun weightFor(button: PlaybackButtonType): Float = when (lastClicked) {
                button -> expansionWeight
                null -> baseWeight
                else -> compressionWeight
            }

            // --- Кнопка ПРЕДЫДУЩИЙ ТРЕК ---
            val prevWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PREVIOUS),
                animationSpec = pressAnimationSpec,
                label = "prevWeight"
            )
            val prevComposition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(R.raw.ic_skip_prev_anim)
            )
            var prevIsPlaying by remember { mutableStateOf(false) }
            val prevProgress by animateLottieCompositionAsState(
                composition = prevComposition,
                isPlaying = prevIsPlaying,
                iterations = 1,
                speed = 1.2f,
                restartOnPlay = true
            )
            LaunchedEffect(prevProgress) {
                if (prevIsPlaying && prevProgress == 1f) prevIsPlaying = false
            }
            val prevCoroutine = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorPreviousButton)
                    .clickable {
                        lastClicked = PlaybackButtonType.PREVIOUS
                        prevCoroutine.launch { prevIsPlaying = true }
                        onPrevious()
                    },
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(
                    composition = prevComposition,
                    progress = { prevProgress },
                    modifier = Modifier.size(iconSize)
                )
            }

            // --- Кнопка PLAY/PAUSE ---
            val playWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec,
                label = "playWeight"
            )
            val playCorner by animateDpAsState(
                targetValue = if (!playPauseVisualState) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "playCorner"
            )
            val playIconBounce = remember { Animatable(1f) }
            val playCoroutine = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .weight(playWeight)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(playCorner))
                    .background(colorPlayPause)
                    .clickable {
                        lastClicked = PlaybackButtonType.PLAY_PAUSE
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        playCoroutine.launch {
                            playIconBounce.snapTo(0.6f)
                            playIconBounce.animateTo(1.2f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                            playIconBounce.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                        }
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = playPauseVisualState,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "playPauseCrossfade"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Пауза" else "Воспроизведение",
                        tint = tintPlayPauseIcon,
                        modifier = Modifier
                            .size(playPauseIconSize)
                            .graphicsLayer {
                                scaleX = playIconBounce.value
                                scaleY = playIconBounce.value
                            }
                    )
                }
            }

            // --- Кнопка СЛЕДУЮЩИЙ ТРЕК ---
            val nextWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.NEXT),
                animationSpec = pressAnimationSpec,
                label = "nextWeight"
            )
            val nextComposition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(R.raw.ic_skip_next_anim)
            )
            var nextIsPlaying by remember { mutableStateOf(false) }
            val nextProgress by animateLottieCompositionAsState(
                composition = nextComposition,
                isPlaying = nextIsPlaying,
                iterations = 1,
                speed = 1.2f,
                restartOnPlay = true
            )
            LaunchedEffect(nextProgress) {
                if (nextIsPlaying && nextProgress == 1f) nextIsPlaying = false
            }
            val nextCoroutine = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorNextButton)
                    .clickable {
                        lastClicked = PlaybackButtonType.NEXT
                        nextCoroutine.launch { nextIsPlaying = true }
                        onNext()
                    },
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(
                    composition = nextComposition,
                    progress = { nextProgress },
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
