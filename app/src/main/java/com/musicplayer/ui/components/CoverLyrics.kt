// Adapted from BoomingMusic (GPL-3.0): CoverPagerFragment (cover <-> lyrics cross-fade)
// and CoverLyricsScreen (lyrics drawn in place of the album cover).
package com.musicplayer.ui.components

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.musicplayer.R
import com.musicplayer.data.Song
import com.musicplayer.data.lyrics.LyricsState
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos

/** BoomingMusic: BOOMING_ANIM_TIME. */
const val BOOMING_ANIM_TIME = 350

/**
 * Booming animates with a plain ObjectAnimator => AccelerateDecelerateInterpolator.
 * Same curve here, so the cross-fade feels identical.
 */
val BoomingAnimEasing = Easing { fraction ->
    ((cos((fraction + 1.0) * PI) / 2.0) + 0.5).toFloat()
}

private const val COVER_BASE_DP = 280f

/**
 * Cover area of the player. Shows the album cover (provided via [cover]) and, when
 * [lyricsProgress] > 0, cross-fades it with the lyrics drawn in the very same place:
 * cover alpha 1 -> 0, lyrics alpha 0 -> 1 (both driven by the same value, like Booming's AnimatorSet).
 *
 * @param lyricsProgress 0 = cover only, 1 = lyrics only. Animate it from outside with
 *   tween([BOOMING_ANIM_TIME], [BoomingAnimEasing]).
 * @param cover receives a scale relative to 280dp (the default cover size in this app).
 */
@Composable
fun CoverWithLyrics(
    viewModel: MusicViewModel,
    song: Song,
    lyricsProgress: Float,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable (coverScale: Float) -> Unit
) {
    // Booming keeps the screen on while lyrics are visible on the cover and music is playing.
    val view = LocalView.current
    val isPlaying by viewModel.isPlaying.collectAsState()
    val keepScreenOn = lyricsProgress > 0f && isPlaying
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Bigger cover: as large as the free area allows (width/height), up to 360dp.
        val coverSize = minOf(maxWidth, maxHeight - 8.dp).coerceIn(140.dp, 360.dp)
        val coverScale = coverSize / COVER_BASE_DP.dp

        Box(Modifier.graphicsLayer { alpha = 1f - lyricsProgress }) {
            Box(Modifier.wrapContentSize(Alignment.Center, unbounded = true)) {
                cover(coverScale)
            }
        }

        // Composed only while (partly) visible; the overlay swallows touches so the hidden
        // cover (swipe to change track) can't be triggered through the lyrics.
        if (lyricsProgress > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = lyricsProgress }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {}
                    )
            ) {
                CoverLyricsContent(
                    viewModel = viewModel,
                    song = song,
                    onExpand = onExpand
                )
            }
        }
    }
}

@Composable
private fun CoverLyricsContent(
    viewModel: MusicViewModel,
    song: Song,
    onExpand: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val fontFamily = LocalAppFontFamily.current

    val lyricsState by viewModel.lyricsState.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val isPowerSaveMode = remember {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    }

    // Lyrics are normally loaded when a song starts; this covers app start (Idle).
    LaunchedEffect(song.id) {
        if (viewModel.lyricsState.value is LyricsState.Idle) {
            viewModel.loadLyricsForSong(song)
        }
    }

    val contentColor = c.onSurface
    val fadingEdges = if (settings.lyricsFadeStyle == 0) FadingEdges.None
    else FadingEdges(top = 72.dp, bottom = 64.dp)
    val contentPadding = PaddingValues(vertical = 72.dp, horizontal = 12.dp)

    Box(Modifier.fillMaxSize()) {
        // New song => fresh list state (scroll position of the previous song is dropped).
        key(song.id) {
        when (val state = lyricsState) {
            is LyricsState.Found -> {
                val syncedLines = state.lyrics.synced
                val plainLines = state.lyrics.plain

                val lyricLines = remember(syncedLines, song.duration) {
                    syncedLines?.takeIf { it.isNotEmpty() }
                        ?.let { buildLyricsLines(it, song.duration) }
                }

                if (!lyricLines.isNullOrEmpty()) {
                    val position = rememberLyricsPlaybackPosition(
                        songId = song.id,
                        playbackPosition = currentPosition,
                        playbackSpeed = playbackSpeed,
                        isPlaying = isPlaying
                    )
                    LyricsView(
                        lines = lyricLines,
                        currentLineIndex = findLyricsLineIndex(lyricLines, position),
                        positionMs = position,
                        textAlign = when (settings.lyricsAlignment) {
                            1 -> TextAlign.Center
                            2 -> TextAlign.End
                            else -> TextAlign.Start
                        },
                        fontScale = settings.lyricsFontScale,
                        fontSizeSp = PLAYER_SYNCED_FONT_SIZE_SP,
                        fontFamily = fontFamily,
                        contentColor = contentColor,
                        contentPadding = contentPadding,
                        fadingEdges = fadingEdges,
                        isPowerSaveMode = isPowerSaveMode,
                        onSeekTo = { viewModel.seekTo(it) }
                    )
                } else if (!plainLines.isNullOrEmpty()) {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(song.id) { scrollState.scrollTo(0) }

                    val style = remember(fontFamily, settings.lyricsFontScale) {
                        TextStyle(
                            fontFamily = fontFamily,
                            fontSize = (PLAYER_PLAIN_FONT_SIZE_SP * settings.lyricsFontScale).sp,
                            lineHeight = 1.4.em
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .fadingEdges(fadingEdges)
                            .verticalScroll(scrollState)
                            .padding(contentPadding)
                    ) {
                        Text(
                            text = plainLines.joinToString("\n"),
                            color = contentColor,
                            textAlign = TextAlign.Center,
                            style = style,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    NoLyricsText(contentColor, fontFamily)
                }
            }

            is LyricsState.NotFound, is LyricsState.Error -> NoLyricsText(contentColor, fontFamily)

            else -> { // Idle / Loading
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_lyrics))
                val progress by animateLottieCompositionAsState(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    isPlaying = true
                )
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.align(Alignment.Center).size(120.dp)
                )
            }
        }
        }

        FilledIconButton(
            onClick = onExpand,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = c.onSurface,
                contentColor = c.surface
            )
        ) {
            Icon(Icons.Rounded.OpenInFull, contentDescription = "Развернуть текст")
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.NoLyricsText(
    color: androidx.compose.ui.graphics.Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily
) {
    Text(
        text = "Текст не найден",
        color = color,
        fontFamily = fontFamily,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .align(Alignment.Center)
    )
}

/** BoomingMusic defaults for the "player" lyrics mode. */
private const val PLAYER_SYNCED_FONT_SIZE_SP = 20f
private const val PLAYER_PLAIN_FONT_SIZE_SP = 16f

/**
 * Interpolates between the (coarse) player position updates on frame boundaries so the
 * line/word highlight changes at the exact timestamp. Same idea as in LyricsScreen.
 */
@Composable
private fun rememberLyricsPlaybackPosition(
    songId: Long,
    playbackPosition: Long,
    playbackSpeed: Float,
    isPlaying: Boolean
): Long {
    var position by remember(songId) { mutableLongStateOf(playbackPosition) }

    LaunchedEffect(songId, playbackPosition, playbackSpeed, isPlaying) {
        val baseRealtime = SystemClock.elapsedRealtime()
        if (!isPlaying) {
            position = playbackPosition
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos {
                val elapsed = SystemClock.elapsedRealtime() - baseRealtime
                position = playbackPosition + (elapsed * playbackSpeed).toLong()
            }
        }
    }
    return position
}
