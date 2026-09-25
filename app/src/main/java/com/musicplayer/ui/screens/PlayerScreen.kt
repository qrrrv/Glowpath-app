package com.musicplayer.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.musicplayer.R
import com.musicplayer.MiniPlayer
import com.musicplayer.data.OrbSettings
import com.musicplayer.data.RepeatMode
import com.musicplayer.data.toTimeString
import com.musicplayer.ui.components.*
import com.musicplayer.ui.components.instrumentIconRes
import com.musicplayer.ui.theme.*
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.palette.graphics.Palette
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.musicplayer.viewmodel.MusicViewModel
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*

// ─────────────────────────────────────────────────────────────────────────────
// Animated orb background — 7 orbs, seamless Lissajous, full OrbSettings support
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnimatedOrbBackground(
    color1: Color,
    color2: Color,
    color3: Color,
    baseColor: Color,
    orbSettings: OrbSettings = OrbSettings(),
    modifier: Modifier = Modifier,
    audioReactiveLevel: Float = -1f
) {
    val speedMs = (32000f / orbSettings.speed).toInt().coerceIn(5000, 120000)
    val cov  = orbSettings.coverage
    val spread = (0.2f + orbSettings.orbSpread.coerceIn(0.1f, 1f) * 0.95f)
    val vbias = orbSettings.verticalBias.coerceIn(-0.5f, 0.5f)
    val inf  = rememberInfiniteTransition(label = "orbAnim")

    val t by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(speedMs, easing = LinearEasing), AnimRepeatMode.Restart),
        label = "orbT"
    )
    val tau = (2.0 * Math.PI * t).toFloat()

    // Color cycle — animate hue continuously if colorCycleSpeed > 0
    val cycleSpeedMs = if (orbSettings.colorCycleSpeed > 0.01f)
        (20000f / orbSettings.colorCycleSpeed.coerceIn(0.1f, 5f)).toInt().coerceIn(2000, 120000)
    else Int.MAX_VALUE
    val colorCycleT by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(cycleSpeedMs, easing = LinearEasing), AnimRepeatMode.Restart),
        label = "colorCycle"
    )
    val cycleDeg = if (orbSettings.colorCycleSpeed > 0.01f) colorCycleT * 360f else 0f

    // Magnetic attraction: orbs drift toward art center (0.5, 0.35)
    val magX = 0.5f; val magY = 0.35f
    val mag = if (orbSettings.magneticToArt) orbSettings.magneticStrength else 0f
    fun mx(x: Float) = x + (magX - x) * mag * 0.4f
    fun my(y: Float) = (y + (magY - y) * mag * 0.4f + vbias).coerceIn(0f, 1f)

    // Rotation: apply global rotation if enabled
    val rotOffset = if (orbSettings.rotationEnabled) tau * 0.08f else 0f

    // flowMode: 0=drift, 1=orbit, 2=chaos, 3=sinus
    fun spreadAmp(amp: Float) = amp * spread
    fun ox(base: Float, amp: Float, freq: Int, phase: Float) = when (orbSettings.flowMode) {
        1 -> mx(0.5f + spreadAmp(amp) * cos(freq * tau + phase + rotOffset))  // orbit around center
        2 -> mx(base + spreadAmp(amp) * sin(freq * tau * 1.7f + phase + rotOffset) * sin(freq * tau * 0.9f + phase)) // chaos
        3 -> mx(base + spreadAmp(amp) * sin(freq * tau + phase) * cos(tau * 0.5f + phase)) // sinus cross
        else -> mx(base + spreadAmp(amp) * sin(freq * tau + phase + rotOffset))
    }
    fun oy(base: Float, amp: Float, freq: Int, phase: Float) = when (orbSettings.flowMode) {
        1 -> my(0.5f + spreadAmp(amp) * sin(freq * tau + phase))
        2 -> my(base + spreadAmp(amp) * cos(freq * tau * 1.3f + phase + rotOffset) * cos(freq * tau * 1.1f + phase))
        3 -> my(base + spreadAmp(amp) * cos(freq * tau + phase) * sin(tau * 0.7f + phase))
        else -> my(base + spreadAmp(amp) * cos(freq * tau + phase))
    }

    // waveMode: orbs move on wave spine
    fun wy(base: Float, amp: Float, freq: Int, phase: Float) =
        if (orbSettings.waveMode) my(0.5f + spreadAmp(0.35f) * sin(tau * 2f + phase)) else oy(base, amp, freq, phase)

    val o1x = ox(0.50f, 0.42f, 1, 0.00f); val o1y = wy(0.22f, 0.25f, 2, 0.50f)
    val o2x = ox(0.72f, 0.24f, 3, 1.00f); val o2y = wy(0.50f, 0.32f, 2, 2.10f)
    val o3x = ox(0.45f, 0.35f, 2, 3.20f); val o3y = wy(0.72f, 0.24f, 3, 0.80f)
    val o4x = ox(0.22f, 0.20f, 1, 2.50f); val o4y = wy(0.55f, 0.38f, 3, 1.60f)
    val o5x = ox(0.55f, 0.30f, 4, 0.70f); val o5y = wy(0.42f, 0.28f, 3, 3.80f)
    val o6x = ox(0.75f, 0.20f, 3, 4.20f); val o6y = wy(0.18f, 0.17f, 5, 1.30f)
    val o7x = ox(0.50f, 0.44f, 5, 2.00f); val o7y = wy(0.50f, 0.42f, 4, 0.20f)
    val o8x = ox(0.18f, 0.16f, 5, 5.30f); val o8y = wy(0.28f, 0.22f, 4, 2.40f)

    // Color: shift + saturation/brightness + color cycle
    val cs = orbSettings.colorShift
    val sat = orbSettings.saturation.coerceIn(0f, 1.5f)
    val bri = orbSettings.brightness.coerceIn(0f, 1.5f)
    fun adjustedColor(base: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.argb(
                (base.alpha * 255).toInt(),
                (base.red * 255).toInt(),
                (base.green * 255).toInt(),
                (base.blue * 255).toInt()
            ), hsv
        )
        if (cs > 0.01f) hsv[0] = (hsv[0] + cs * 360f) % 360f
        hsv[0] = (hsv[0] + cycleDeg) % 360f
        hsv[1] = (hsv[1] * sat).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * bri).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = base.alpha)
    }
    val sc1 = adjustedColor(color1)
    val sc2 = adjustedColor(color2)
    val sc3 = adjustedColor(color3)

    val previewKick by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 720
                0f at 0
                1f at 90 using FastOutSlowInEasing
                0.58f at 180 using LinearOutSlowInEasing
                0.18f at 360 using FastOutLinearInEasing
                0f at 720
            },
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "orbKick"
    )
    val reactiveKick = when {
        audioReactiveLevel >= 0f && orbSettings.bassReactive -> audioReactiveLevel.coerceIn(0f, 1f)
        orbSettings.pulseOnBeat -> previewKick
        audioReactiveLevel < 0f && orbSettings.bassReactive -> previewKick
        else -> 0f
    }
    val primaryPulse = 1f + reactiveKick * orbSettings.beatScale * if (orbSettings.bassReactive) 2.6f else 1.45f
    val secondaryPulse = 1f + reactiveKick * orbSettings.beatScale * if (orbSettings.bassReactive) 1.7f else 0.95f
    val haloPulse = 1f + reactiveKick * orbSettings.beatScale * 0.72f

    data class OrbDef(val cx: Float, val cy: Float, val r: Float, val color: Color, val alpha: Float, val depth: Float = 1f)
    val allOrbs = listOf(
        OrbDef(o1x, o1y, 0.90f * primaryPulse, sc1, 0.78f, 1.0f),
        OrbDef(o2x, o2y, 0.78f * secondaryPulse, sc2, 0.68f, 0.85f),
        OrbDef(o3x, o3y, 0.76f * haloPulse, sc3, 0.60f, 1.1f),
        OrbDef(o4x, o4y, 0.60f * secondaryPulse, sc1, 0.50f, 0.7f),
        OrbDef(o5x, o5y, 0.65f * primaryPulse, sc2, 0.44f, 0.9f),
        OrbDef(o6x, o6y, 0.48f * secondaryPulse, sc3, 0.52f, 1.2f),
        OrbDef(o7x, o7y, 1.05f * haloPulse, sc1, 0.28f, 0.6f),
        OrbDef(o8x, o8y, 0.54f * secondaryPulse, sc2, 0.38f, 1.05f)
    )
    val activeOrbs = allOrbs.take(orbSettings.orbCount.coerceIn(1, 8))
    val glowAlpha = orbSettings.glowIntensity.coerceIn(0f, 1f)
    val blurSoftness = (0.12f + orbSettings.blurRadius.coerceIn(0f, 1f) * 0.45f).coerceIn(0.08f, 0.58f)
    val reactiveGlowBoost = reactiveKick * if (orbSettings.bassReactive) 0.42f else 0.18f

    // Particle positions (seeded from tau)
    val particleCount = orbSettings.particleCount.coerceIn(5, 60)

    Box(modifier = modifier.graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)) {
        Box(Modifier.fillMaxSize().drawBehind {
            val w = size.width; val h = size.height
            drawRect(color = baseColor)

            // Depth factor per orb
            fun depthFactor(depth: Float) = if (orbSettings.depthEffect) depth else 1f

            fun orbCircle(cx: Float, cy: Float, r: Float, c: Color, alpha: Float, depth: Float = 1f) {
                val df = depthFactor(depth)
                val a = (alpha * (0.4f + glowAlpha * 0.6f + reactiveGlowBoost) * df).coerceIn(0f, 1f)
                val covR = w * r * cov * df

                if (orbSettings.chromaEffect) {
                    // RGB channel split — draw R, G, B offset copies
                    val offset = w * 0.012f
                    val rColor = Color(c.red, 0f, 0f, a * 0.6f)
                    val gColor = Color(0f, c.green, 0f, a * 0.6f)
                    val bColor = Color(0f, 0f, c.blue, a * 0.6f)
                    drawCircle(Brush.radialGradient(listOf(rColor, rColor.copy(a*0.15f), Color.Transparent), Offset(w*cx - offset, h*cy), covR), covR, Offset(w*cx - offset, h*cy))
                    drawCircle(Brush.radialGradient(listOf(gColor, gColor.copy(a*0.15f), Color.Transparent), Offset(w*cx, h*cy + offset*0.5f), covR), covR, Offset(w*cx, h*cy + offset*0.5f))
                    drawCircle(Brush.radialGradient(listOf(bColor, bColor.copy(a*0.15f), Color.Transparent), Offset(w*cx + offset, h*cy), covR), covR, Offset(w*cx + offset, h*cy))
                }

                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            c.copy(a),
                            c.copy(a * blurSoftness),
                            Color.Transparent
                        ),
                        Offset(w * cx, h * cy),
                        covR
                    ),
                    covR,
                    Offset(w * cx, h * cy)
                )

                // Border glow — ring around orb center
                if (orbSettings.borderGlow) {
                    val ringR = covR * 0.35f * orbSettings.borderThickness
                    val ringAlpha = (a * 0.7f).coerceIn(0f, 1f)
                    drawCircle(color = c.copy(ringAlpha * 0.5f), radius = ringR, center = Offset(w*cx, h*cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringR * 0.12f))
                }
            }

            // Kaleidoscope: mirror canvas in 4 quadrants
            if (orbSettings.kaleidoscopeMode) {
                with(this) {
                    // Mirror horizontally
                    fun mirrorOrbs(flipH: Boolean, flipV: Boolean) {
                        activeOrbs.forEach { o ->
                            val cx = if (flipH) 1f - o.cx else o.cx
                            val cy = if (flipV) 1f - o.cy else o.cy
                            orbCircle(cx, cy, o.r * 0.65f, o.color, o.alpha * 0.45f, o.depth)
                        }
                    }
                    mirrorOrbs(true, false)
                    mirrorOrbs(false, true)
                    mirrorOrbs(true, true)
                }
            }

            // Trail: draw ghost copies slightly behind in time with lower alpha
            if (orbSettings.trailEffect) {
                val tau2 = tau - 0.04f * orbSettings.trailLength * 3f
                val trailOrbs = listOf(
                    OrbDef(mx(0.50f + 0.42f * sin(1*tau2 + 0.00f + rotOffset)), wy(0.22f, 0.25f, 2, 0.50f + tau2 - tau), 0.85f, sc1, 0.18f),
                    OrbDef(mx(0.72f + 0.24f * sin(3*tau2 + 1.00f + rotOffset)), wy(0.50f, 0.32f, 2, 2.10f + tau2 - tau), 0.74f, sc2, 0.15f),
                    OrbDef(mx(0.45f + 0.35f * sin(2*tau2 + 3.20f + rotOffset)), wy(0.72f, 0.24f, 3, 0.80f + tau2 - tau), 0.70f, sc3, 0.12f),
                )
                trailOrbs.take(orbSettings.orbCount.coerceIn(1, 3)).forEach { o -> orbCircle(o.cx, o.cy, o.r, o.color, o.alpha * orbSettings.trailLength, o.depth) }
            }

            activeOrbs.forEachIndexed { index, orb ->
                val jitterAmount = if (orbSettings.bassReactive) reactiveKick * (0.012f + orbSettings.beatScale * 0.03f) else 0f
                val jitterX = if (jitterAmount > 0f) {
                    (orb.cx + sin(tau * (5f + index) + index * 0.64f) * jitterAmount).coerceIn(0.02f, 0.98f)
                } else orb.cx
                val jitterY = if (jitterAmount > 0f) {
                    (orb.cy + cos(tau * (6f + index) + index * 0.52f) * jitterAmount).coerceIn(0.02f, 0.98f)
                } else orb.cy
                val extraScale = 1f + reactiveKick * if (index % 2 == 0) 0.12f else 0.07f
                val extraAlpha = orb.alpha * (1f + reactiveKick * 0.15f)
                orbCircle(jitterX, jitterY, orb.r * extraScale, orb.color, extraAlpha, orb.depth)
            }

            // Particle emission — small glowing dots near orb centers
            if (orbSettings.particleEmission) {
                val seed = t * 100f
                repeat(particleCount.coerceAtMost(activeOrbs.size * 10)) { i ->
                    val orbRef = activeOrbs[i % activeOrbs.size]
                    val angle = (i * 137.5f + seed * 40f) * Math.PI.toFloat() / 180f
                    val dist = ((i % 7) / 7f) * w * 0.15f * cov
                    val px = (orbRef.cx * w + cos(angle) * dist).coerceIn(0f, w)
                    val py = (orbRef.cy * h + sin(angle) * dist).coerceIn(0f, h)
                    val pa = (0.6f - (i % 7) / 7f * 0.5f).coerceIn(0f, 0.6f)
                    drawCircle(
                        color = orbRef.color.copy(pa * (glowAlpha + reactiveGlowBoost).coerceIn(0f, 1f)),
                        radius = (2.5f + (i % 3) * 1.5f) * (1f + reactiveKick * 0.25f),
                        center = Offset(px, py)
                    )
                }
            }

            // Visualizer bars at the bottom
            if (orbSettings.showVisualizerBars) {
                val barCount = 24
                val barW = w / (barCount * 1.6f)
                val maxH = h * 0.18f
                repeat(barCount) { i ->
                    val phase = i * 0.4f
                    val beatBoost = 1f + reactiveKick * if (orbSettings.bassReactive) 0.95f else 0.45f
                    val barH = maxH * (0.3f + 0.7f * ((sin(tau * 3f + phase) + 1f) / 2f)) * beatBoost
                    val barX = i * (w / barCount) + barW * 0.3f
                    val barColor = if (i % 3 == 0) sc1 else if (i % 3 == 1) sc2 else sc3
                    val a = (0.45f + reactiveKick * 0.35f) * glowAlpha
                    drawRoundRect(
                        color = barColor.copy(a.coerceIn(0f, 1f)),
                        topLeft = Offset(barX, h - barH),
                        size = androidx.compose.ui.geometry.Size(barW, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f)
                    )
                }
            }

            drawRect(Brush.verticalGradient(listOf(Color.Transparent, baseColor.copy(0.55f)), h*0.50f, h))
        })

        // Frosted glass overlay (Compose level)
        if (orbSettings.frostedGlass) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        listOf(baseColor.copy(0.08f), baseColor.copy(0.22f)),
                        radius = Float.POSITIVE_INFINITY
                    )
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onLyricsClick: () -> Unit = {}
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val song            by viewModel.currentSong.collectAsState()
    val isPlaying       by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration        by viewModel.duration.collectAsState()
    val volume          by viewModel.volume.collectAsState()
    val playbackSpeed   by viewModel.playbackSpeed.collectAsState()
    val settings        by viewModel.settings.collectAsState()
    val favourites      by viewModel.favourites.collectAsState()
    val songs           by viewModel.songs.collectAsState()
    val bridgeQueueUris by viewModel.bridgeQueueUris.collectAsState()
    val customArtMap    by viewModel.customArtMap.collectAsState()
    val orbSettings     by viewModel.orbSettings.collectAsState()
    val audioReactiveLevel by viewModel.audioReactiveLevel.collectAsState()

    if (song == null) { LaunchedEffect(Unit) { onBack() }; return }

    val isFavourite = song!!.id in favourites
    val customArtUri: android.net.Uri? = customArtMap[song!!.id]
    // ── Palette extraction — from custom art if set, else album art ───────────
    data class ArtColors(val dominant: Color, val vibrant: Color, val muted: Color)
    var artColors by remember(song!!.id) { mutableStateOf<ArtColors?>(null) }
    val artSourceUri = customArtUri ?: song!!.albumArtUri

    LaunchedEffect(artSourceUri) {
        artColors = null
        artSourceUri?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    bmp?.let { b ->
                        val p = Palette.from(b).maximumColorCount(12).generate()
                        artColors = ArtColors(
                            Color(p.getDominantColor(0xFF888888.toInt())),
                            Color(p.getVibrantColor(p.getMutedColor(0xFF888888.toInt()))),
                            Color(p.getMutedColor(p.getDominantColor(0xFF666666.toInt())))
                        )
                        b.recycle()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val ct = orbSettings.contrast
    val fallback = c.accent.copy(alpha = 0.6f)
    val animColor1 by animateColorAsState(artColors?.dominant?.copy(0.4f + ct * 0.4f) ?: fallback, tween(800), label = "ac1")
    val animColor2 by animateColorAsState(artColors?.vibrant?.copy(0.3f + ct * 0.35f) ?: c.accentVar.copy(0.5f), tween(900, 100), label = "ac2")
    val animColor3 by animateColorAsState(artColors?.muted?.copy(0.25f + ct * 0.3f) ?: c.accentMuted.copy(0.4f), tween(1000, 200), label = "ac3")

    // ── Page-flip animation ───────────────────────────────────────────────────
    var flipDirection by remember { mutableIntStateOf(0) }
    val flipRotY = remember { Animatable(0f) }
    val artSlideX = remember { Animatable(0f) }
    var prevSongId by remember { mutableLongStateOf(song!!.id) }
    LaunchedEffect(song!!.id) {
        if (prevSongId != song!!.id && flipDirection != 0) {
            val enterFrom = if (flipDirection > 0) -1f else 1f
            // Slide in from side
            artSlideX.snapTo(enterFrom)
            launch {
                artSlideX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
            }
            flipRotY.snapTo(if (flipDirection > 0) -90f else 90f)
            flipRotY.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            flipDirection = 0
        }
        prevSongId = song!!.id
    }

    // ── Swipe-down to close ───────────────────────────────────────────────────
    var swipeOffsetY by remember { mutableFloatStateOf(0f) }
    var isSwipeDragging by remember { mutableStateOf(false) }
    var isSwipeClosing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val swipeDismissDistance = with(density) { 420.dp.toPx() }  // longer travel for full morph into mini bar
    val swipeCloseThreshold = with(density) { 140.dp.toPx() }
    val animatedOffset by animateFloatAsState(
        targetValue = swipeOffsetY,
        animationSpec = if (isSwipeDragging) {
            snap()
        } else {
            spring(
                dampingRatio = 0.86f,
                stiffness = 380f
            )
        },
        label = "sw"
    )
    val swipeProgress = (animatedOffset / swipeDismissDistance).coerceIn(0f, 1f)
    // Stronger shrink + softer alpha so the panel morphs into the mini-player bar (YouTube Music style)
    val swipeScale    = lerpFloat(1f, 0.42f, FastOutSlowInEasing.transform(swipeProgress))
    val swipeAlpha    = (1f - (swipeProgress * 0.92f)).coerceIn(0f, 1f)
    val swipeCorner   = lerpFloat(0f, 28f,   FastOutSlowInEasing.transform(swipeProgress))

    var showTrackSettings by remember { mutableStateOf(false) }

    // ── Pulse animation ───────────────────────────────────────────────────────
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseAnim.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse), "p")
    val artScale by animateFloatAsState(if (isPlaying) pulse else 0.88f, spring(stiffness = Spring.StiffnessMediumLow), label = "as")

    val scope = rememberCoroutineScope()
    val openProgress = remember { Animatable(0f) }
    val screenEntryProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        openProgress.snapTo(0f)
        openProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 430,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
            )
        )
    }
    LaunchedEffect(Unit) {
        screenEntryProgress.snapTo(0f)
        screenEntryProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 430,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
            )
        )
    }

    val activeQueueCount = remember(song!!.id, songs, bridgeQueueUris, settings.sortOrder) {
        viewModel.buildActiveQueueSnapshot().size
    }
    // Continuous morph: show MiniPlayer under during both open and collapse (YT Music style)
    val collapseBackdropReveal = when {
        isSwipeClosing || swipeProgress > 0.005f -> {
            // Collapse / swipe-down: progressively reveal the mini-player under the shrinking full player
            (swipeProgress * 1.25f).coerceIn(0f, 1f)
        }
        else -> {
            // Open: start with mini visible, fade it out as the full player expands over it
            (1f - screenEntryProgress.value).coerceIn(0f, 1f)
        }
    }
    val entryProgress = FastOutSlowInEasing.transform(screenEntryProgress.value.coerceIn(0f, 1f))
    val playerBackgroundAlpha = ((1f - swipeProgress.coerceIn(0f, 1f)) * entryProgress).coerceIn(0f, 1f)
    val entryOffsetY = with(density) { lerpFloat(96.dp.toPx(), 0f, entryProgress) }
    val entryScale = lerpFloat(0.88f, 1f, entryProgress)
    val entryAlpha = ((entryProgress - 0.02f) / 0.98f).coerceIn(0f, 1f)
    val entryCorner = lerpFloat(28f, 0f, entryProgress)  // matches MiniPlayer RoundedCornerShape(28.dp)

    suspend fun animateCloseAndExit(startOffset: Float = swipeOffsetY) {
        if (isSwipeClosing) return
        isSwipeClosing = true
        isSwipeDragging = false
        swipeOffsetY = startOffset.coerceIn(0f, swipeDismissDistance)
        // Drive fully to the collapsed (mini-player) state so the morph finishes cleanly
        swipeOffsetY = swipeDismissDistance * 1.05f
        withTimeoutOrNull(520L) {
            snapshotFlow { animatedOffset }
                .first { it >= swipeDismissDistance * 0.97f }
        }
        // Tiny settle so the underlaid MiniPlayer is fully opaque before we pop the route
        kotlinx.coroutines.delay(32)
        onBack()
    }

    Box(Modifier.fillMaxSize()) {
        val hazeState = remember { HazeState() }

        if (orbSettings.showInPlayer) {
            AnimatedOrbBackground(
                color1 = animColor1,
                color2 = animColor2,
                color3 = animColor3,
                baseColor = c.bgDeep,
                orbSettings = orbSettings,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = playerBackgroundAlpha },
                audioReactiveLevel = audioReactiveLevel
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.bgDeep.copy(alpha = playerBackgroundAlpha))
            )
        }

        PlayerCollapseBackdrop(
            title = song!!.title,
            artist = if (song!!.artist != "<unknown>") song!!.artist else "Неизвестный",
            albumArtUri = customArtUri ?: song!!.albumArtUri,
            isPlaying = isPlaying,
            progress = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f,
            revealProgress = collapseBackdropReveal,
            queueCount = activeQueueCount,
            onPlayPause = { viewModel.togglePlayPause() },
            onPrevious = { viewModel.playPrevious() },
            onNext = { viewModel.playNext() },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier.fillMaxSize()
                .haze(hazeState)
                .graphicsLayer {
                    translationY = animatedOffset.coerceAtLeast(0f) + entryOffsetY
                    scaleX = swipeScale * entryScale
                    scaleY = swipeScale * entryScale
                    alpha = swipeAlpha * entryAlpha
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    clip = swipeProgress > 0f || entryProgress < 0.999f
                    shape = RoundedCornerShape(max(swipeCorner, entryCorner).dp)
                }
                .pointerInput(showTrackSettings, isSwipeClosing) {
                    if (!showTrackSettings && !isSwipeClosing) detectVerticalDragGestures(
                        onDragStart = { isSwipeDragging = true },
                        onDragEnd    = {
                            isSwipeDragging = false
                            if (swipeOffsetY >= swipeCloseThreshold) {
                                scope.launch { animateCloseAndExit(swipeOffsetY.coerceAtLeast(swipeCloseThreshold)) }
                            } else {
                                swipeOffsetY = 0f
                            }
                        },
                        onDragCancel = {
                            isSwipeDragging = false
                            swipeOffsetY = 0f
                        },
                        onVerticalDrag = { ch, dy ->
                            ch.consume()
                            val dragMultiplier = if (dy >= 0f) 0.92f else 1.08f
                            swipeOffsetY = (swipeOffsetY + dy * dragMultiplier)
                                .coerceIn(0f, swipeDismissDistance)
                        }
                    )
                }
        ) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.textDisabled.copy(0.35f + (swipeOffsetY / 180f).coerceIn(0f, 0.65f))))

            val doFlipNext: () -> Unit = { flipDirection = 1; scope.launch { flipRotY.animateTo(90f, tween(200, easing = FastOutSlowInEasing)); viewModel.playNext() } }
            val doFlipPrev: () -> Unit = { flipDirection = -1; scope.launch { flipRotY.animateTo(-90f, tween(200, easing = FastOutSlowInEasing)); viewModel.playPrevious() } }
            val requestClose: () -> Unit = { scope.launch { animateCloseAndExit() } }

            if (isLandscape) {
                LandscapePlayerContent(song!!, isPlaying, currentPosition, duration, settings, isFavourite, artScale, flipRotY.value, artSlideX.value, viewModel, customArtUri, requestClose, onLyricsClick, { showTrackSettings = true }, doFlipNext, doFlipPrev, swipeProgress, openProgress.value)
            } else {
                PortraitPlayerContent(song!!, isPlaying, currentPosition, duration, settings, isFavourite, artScale, flipRotY.value, artSlideX.value, viewModel, customArtUri, requestClose, onLyricsClick, { showTrackSettings = true }, doFlipNext, doFlipPrev, swipeProgress, openProgress.value)
            }
        }

        TrackSettingsOverlay(
            visible = showTrackSettings, volume = volume, playbackSpeed = playbackSpeed, song = song,
            viewModel = viewModel,
            hazeState = hazeState,
            onVolumeChange = { viewModel.setVolume(it) }, onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onSetRingtone = { song?.let { setAsRingtone(context, it.uri) }; showTrackSettings = false },
            onDismiss = { showTrackSettings = false }
        )
    }
}

@Composable
private fun PlayerCollapseBackdrop(
    title: String,
    artist: String,
    albumArtUri: Uri?,
    isPlaying: Boolean,
    progress: Float,
    revealProgress: Float,
    queueCount: Int,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (revealProgress <= 0f) return

    val density = LocalDensity.current
    val revealAlpha = FastOutSlowInEasing.transform(revealProgress).coerceIn(0f, 1f)
    val contentLift = with(density) { lerpFloat(28.dp.toPx(), 0f, revealAlpha) }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = revealAlpha }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, bottom = 6.dp, top = 14.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            MiniPlayer(
                title = title,
                artist = artist,
                albumArtUri = albumArtUri,
                isPlaying = isPlaying,
                progress = progress,
                queueCount = queueCount,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onMoreClick = {},
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Stay fully solid once mostly revealed so handoff to real MiniPlayer is invisible
                        alpha = 0.55f + revealAlpha * 0.45f
                        translationY = contentLift
                    }
            )
        }
    }
}

@Composable
private fun PortraitPlayerContent(
    song: com.musicplayer.data.Song, isPlaying: Boolean, currentPosition: Long, duration: Long,
    settings: com.musicplayer.data.PlayerSettings, isFavourite: Boolean, artScale: Float, flipRotY: Float, artSlideX: Float = 0f,
    viewModel: MusicViewModel, customArtUri: android.net.Uri?,
    onBack: () -> Unit, onLyricsClick: () -> Unit, onSettingsClick: () -> Unit,
    onSwipeNext: () -> Unit, onSwipePrev: () -> Unit, swipeProgress: Float = 0f, openProgress: Float = 1f
) {
    val density = LocalDensity.current
    // Fade full-player content early during collapse so it doesn't look squashed while morphing to mini
    val contentReveal = (((openProgress - 0.34f) / 0.66f).coerceIn(0f, 1f) * (1f - swipeProgress * 1.1f)).coerceIn(0f, 1f)
    val contentOffset = with(density) { lerpFloat(34.dp.toPx(), 0f, contentReveal) }
    val topReveal = (((openProgress - 0.18f) / 0.82f).coerceIn(0f, 1f) * (1f - swipeProgress * 1.15f)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer {
            alpha = topReveal
            translationY = lerpFloat((-18).dp.toPx(), 0f, topReveal)
        }) {
            TopBar(onBack, onSettingsClick, swipeProgress)
        }
        Spacer(Modifier.height(20.dp))
        AlbumArtSection(song, isPlaying, artScale, flipRotY, artSlideX, customArtUri, onSwipeNext, onSwipePrev, { viewModel.setCustomArt(song.id, null) }, settings.albumArtAnim, settings.animParams, openProgress)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.graphicsLayer {
            alpha = contentReveal
            translationY = contentOffset
        }) {
            SongMetaSection(song, isFavourite, onLyricsClick, viewModel) { viewModel.toggleFavourite(song.id) }
            Spacer(Modifier.height(24.dp))
            ProgressSection(currentPosition, duration, isPlaying, viewModel)
            Spacer(Modifier.height(16.dp))
            ControlsSection(settings, isPlaying, viewModel)
            Spacer(Modifier.height(8.dp))
            BottomToggleRow(settings, isFavourite, viewModel, song.id)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LandscapePlayerContent(
    song: com.musicplayer.data.Song, isPlaying: Boolean, currentPosition: Long, duration: Long,
    settings: com.musicplayer.data.PlayerSettings, isFavourite: Boolean, artScale: Float, flipRotY: Float, artSlideX: Float = 0f,
    viewModel: MusicViewModel, customArtUri: android.net.Uri?,
    onBack: () -> Unit, onLyricsClick: () -> Unit, onSettingsClick: () -> Unit,
    onSwipeNext: () -> Unit, onSwipePrev: () -> Unit, swipeProgress: Float = 0f, openProgress: Float = 1f
) {
    val density = LocalDensity.current
    val sideReveal = (((openProgress - 0.28f) / 0.72f).coerceIn(0f, 1f) * (1f - swipeProgress * 1.1f)).coerceIn(0f, 1f)
    val sideOffset = with(density) { lerpFloat(26.dp.toPx(), 0f, sideReveal) }
    Row(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.graphicsLayer {
                alpha = sideReveal
                translationY = lerpFloat((-14).dp.toPx(), 0f, sideReveal)
            }) {
                TopBar(onBack, onSettingsClick, swipeProgress)
            }
            Spacer(Modifier.height(8.dp))
            AlbumArtSection(song, isPlaying, artScale, flipRotY, artSlideX, customArtUri, onSwipeNext, onSwipePrev, { viewModel.setCustomArt(song.id, null) }, settings.albumArtAnim, settings.animParams, openProgress)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).fillMaxHeight().graphicsLayer {
            alpha = sideReveal
            translationY = sideOffset
        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            SongMetaSection(song, isFavourite, onLyricsClick, viewModel) { viewModel.toggleFavourite(song.id) }
            ProgressSection(currentPosition, duration, isPlaying, viewModel)
            ControlsSection(settings, isPlaying, viewModel)
            BottomToggleRow(settings, isFavourite, viewModel, song.id)
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, onSettingsClick: () -> Unit, swipeProgress: Float = 0f) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedBackButton(
            onBack = onBack,
            containerColor = c.surfaceContainerLow,
            iconColor = c.onSurface,
            swipeProgress = swipeProgress
        )
        Spacer(Modifier.weight(1f))
        Text("СЕЙЧАС ИГРАЕТ", color = c.textDisabled, fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.1f.sp)
        Spacer(Modifier.weight(1f))
        FilledIconButton(
            onSettingsClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = c.surfaceContainerLow,
                contentColor = c.onSurface
            )
        ) {
            Icon(Icons.Rounded.Tune, "Настройки трека")
        }
    }
}

// ── Animated back button with Lottie 180° rotation ───────────────────────────
// Behaviour:
//   • Tap  → onBack() fires IMMEDIATELY, animation plays while screen closes
//   • Swipe → swipeProgress (0..1) drives animation frame in real-time
@Composable
private fun AnimatedBackButton(
    onBack: () -> Unit,
    containerColor: Color,
    iconColor: Color,
    swipeProgress: Float = 0f
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.arrow_down_anim))

    // Internal tap animation (independent of swipe)
    var tapAnimating by remember { mutableStateOf(false) }
    val tapProgress by animateLottieCompositionAsState(
        composition   = composition,
        isPlaying     = tapAnimating,
        iterations    = 1,
        speed         = 1.4f,
        restartOnPlay = true
    )
    // Reset flag when tap animation completes
    LaunchedEffect(tapProgress) {
        if (tapAnimating && tapProgress >= 0.99f) tapAnimating = false
    }

    // Final rendered progress:
    //   During swipe → driven by finger (0..1)
    //   During tap   → driven by internal animation
    //   Otherwise    → 0 (rest position)
    val displayProgress = when {
        swipeProgress > 0f -> swipeProgress.coerceIn(0f, 1f)
        tapAnimating       -> tapProgress
        else               -> 0f
    }

    // Dynamic color tint to match current theme
    val colorArgb = android.graphics.Color.argb(
        (iconColor.alpha * 255).toInt(),
        (iconColor.red   * 255).toInt(),
        (iconColor.green * 255).toInt(),
        (iconColor.blue  * 255).toInt()
    )
    val dynamicProps = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value    = android.graphics.PorterDuffColorFilter(colorArgb, android.graphics.PorterDuff.Mode.SRC_ATOP),
            keyPath  = arrayOf("**")
        )
    )

    FilledIconButton(
        onClick = {
            // Close screen immediately, animation plays in parallel while screen animates out
            tapAnimating = true
            onBack()
        },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor   = iconColor
        )
    ) {
        if (composition != null) {
            LottieAnimation(
                composition       = composition,
                progress          = { displayProgress },
                modifier          = Modifier.size(26.dp),
                dynamicProperties = dynamicProps
            )
        } else {
            Icon(Icons.Rounded.KeyboardArrowDown, "Назад", Modifier.size(26.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumArtSection(
    song: com.musicplayer.data.Song, isPlaying: Boolean, artScale: Float, flipRotY: Float, artSlideX: Float = 0f,
    customArtUri: android.net.Uri?,
    onSwipeNext: () -> Unit, onSwipePrev: () -> Unit,
    onResetArt: () -> Unit,
    albumArtAnim: Int = 0,
    animParams: com.musicplayer.data.AnimParams = com.musicplayer.data.AnimParams(),
    openProgress: Float = 1f
) {
    val c = MaterialTheme.colorScheme
    val appStyle = com.musicplayer.ui.theme.LocalAppStyle.current
    val artCorner = androidx.compose.ui.unit.Dp(appStyle.cardCornerRadius.coerceIn(8f, 64f))
    val artShape  = RoundedCornerShape(artCorner)
    var swipeDragX by remember { mutableFloatStateOf(0f) }
    val densityState = LocalDensity.current
    val openEased = FastOutSlowInEasing.transform(openProgress.coerceIn(0f, 1f))
    val enterShiftX = with(densityState) { lerpFloat((-118).dp.toPx(), 0f, openEased) }
    val enterShiftY = with(densityState) { lerpFloat(220.dp.toPx(), 0f, openEased) }
    val enterScale = lerpFloat(0.34f, 1f, openEased)
    val enterAlpha = ((openProgress - 0.02f) / 0.98f).coerceIn(0f, 1f)

    // ── Album Art extra animations ────────────────────────────────────────────
    val artEasing = when (animParams.albumArtEasing) {
        1 -> FastOutSlowInEasing
        2 -> FastOutLinearInEasing
        3 -> LinearEasing
        4 -> CubicBezierEasing(0.2f, 1.25f, 0.38f, 1f)
        5 -> CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
        else -> FastOutSlowInEasing
    }
    fun artMs(base: Int) = (base / animParams.albumArtSpeed.coerceIn(0.2f, 4f)).toInt().coerceAtLeast(60)
    val infiniteArt = rememberInfiniteTransition(label = "art_inf")
    // Vinyl rotation (anim 5)
    val vinylRotation by infiniteArt.animateFloat(0f, 360f,
        infiniteRepeatable(tween(artMs(3000), easing = LinearEasing)), label = "vinyl")
    // Pulse / breathe (anim 2 & 3)
    val artPulse by infiniteArt.animateFloat(0.97f, 1.04f,
        infiniteRepeatable(tween(artMs(1000), easing = artEasing), AnimRepeatMode.Reverse), label = "pulse")
    // Glitch offset (anim 6)
    val glitchX by infiniteArt.animateFloat(-4f, 4f,
        infiniteRepeatable(tween(artMs(120), easing = LinearEasing), AnimRepeatMode.Reverse), label = "glitch")
    // Swing (anim 7)
    val swingRot by infiniteArt.animateFloat(-6f, 6f,
        infiniteRepeatable(tween(artMs(1200), easing = artEasing), AnimRepeatMode.Reverse), label = "swing")
    // Orbit / wave (anim 8 & 9)
    val orbitRotation by infiniteArt.animateFloat(0f, 360f,
        infiniteRepeatable(tween(artMs(2200), easing = LinearEasing)), label = "orbit")
    val waveShift by infiniteArt.animateFloat(-6f, 6f,
        infiniteRepeatable(tween(artMs(1200), easing = artEasing), AnimRepeatMode.Reverse), label = "waveShift")
    val waveStretch by infiniteArt.animateFloat(0.97f, 1.04f,
        infiniteRepeatable(tween(artMs(900), easing = artEasing), AnimRepeatMode.Reverse), label = "waveStretch")

    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(280.dp * artScale)
                .graphicsLayer {
                    rotationY = flipRotY + swipeDragX * 0.08f
                    translationX = enterShiftX + artSlideX * size.width * 0.6f + when (albumArtAnim) {
                        4 -> if (isPlaying) artSlideX * 12f else 0f  // Параллакс
                        6 -> glitchX                                  // Glitch
                        else -> 0f
                    }
                    translationY = enterShiftY + when (albumArtAnim) {
                        9 -> if (isPlaying) waveShift else 0f
                        else -> 0f
                    }
                    alpha = enterAlpha
                    scaleX = enterScale
                    scaleY = enterScale
                    cameraDistance = 12f * density
                    when (albumArtAnim) {
                        1 -> rotationZ = if (isPlaying) vinylRotation else 0f  // Вращение
                        5 -> rotationZ = vinylRotation                          // Vinyl
                        7 -> rotationZ = swingRot                               // Качели
                        2 -> {
                            scaleX = enterScale * if (isPlaying) artPulse else 1f
                            scaleY = enterScale * if (isPlaying) artPulse else 1f
                        }
                        3 -> {
                            scaleX = enterScale * (artPulse * 0.97f)
                            scaleY = enterScale * (artPulse * 0.97f)
                        }
                        8 -> rotationZ = if (isPlaying) orbitRotation * 0.12f else 0f
                        9 -> {
                            scaleX = enterScale * if (isPlaying) waveStretch else 1f
                            scaleY = enterScale * if (isPlaying) (2f - waveStretch) else 1f
                        }
                        else -> {}
                    }
                }
                .pointerInput(Unit) {
                    val pointerScope = this
                    while (true) {
                        var dragX = 0f
                        coroutineScope {
                            pointerScope.awaitPointerEventScope {
                                awaitFirstDown()
                                try {
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull() ?: break
                                        val dx = ch.position.x - ch.previousPosition.x
                                        dragX += dx
                                        swipeDragX = dragX.coerceIn(-160f, 160f)
                                        ch.consume()
                                        if (!ch.pressed) {
                                            if (dragX < -80f) onSwipeNext()
                                            else if (dragX > 80f) onSwipePrev()
                                            swipeDragX = 0f
                                            break
                                        }
                                    }
                                } finally { swipeDragX = 0f }
                            }
                        }
                    }
                }
                .clip(artShape)
                .background(c.bgCard)
                .border(1.5.dp, c.accentMuted.copy(0.3f), artShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                customArtUri != null -> AsyncImage(customArtUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                song.albumArtUri != null -> AsyncImage(song.albumArtUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else -> Icon(painterResource(instrumentIconRes(song.id)), null, tint = c.accentMuted, modifier = Modifier.size(80.dp))
            }

            if (albumArtAnim == 8) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxSize(0.86f)
                            .border(1.dp, c.accent.copy(alpha = 0.24f), CircleShape)
                    )
                    Box(
                        Modifier
                            .fillMaxSize(0.92f)
                            .graphicsLayer { rotationZ = if (isPlaying) orbitRotation else 0f }
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-4).dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(c.accent.copy(alpha = 0.85f))
                                .border(1.dp, c.bgDeep.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }

            if (albumArtAnim == 9) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    listOf(18f, 32f, 24f, 36f).forEachIndexed { i, base ->
                        val barWave = 0.7f + kotlin.math.abs(sin((orbitRotation / 180f * Math.PI + i).toFloat())) * 0.5f
                        Box(
                            Modifier
                                .width(7.dp)
                                .height((base * if (isPlaying) barWave else 0.7f).dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(c.textPrimary.copy(alpha = 0.84f), c.accent.copy(alpha = 0.52f))
                                    )
                                )
                        )
                    }
                }
            }

            if (swipeDragX.absoluteValue > 20f) {
                val alpha = ((swipeDragX.absoluteValue - 20f) / 80f).coerceIn(0f, 0.7f)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    if (swipeDragX > 0) listOf(c.accent.copy(alpha), Color.Transparent)
                    else listOf(Color.Transparent, c.accent.copy(alpha))
                )))
            }
        }

        // Show small "reset" button if custom art is set
        if (customArtUri != null) {
            Box(
                Modifier.padding(8.dp).size(28.dp).clip(CircleShape)
                    .background(c.bgCard.copy(0.92f)).clickable(onClick = onResetArt),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SongMetaSection(song: com.musicplayer.data.Song, isFavourite: Boolean, onLyricsClick: () -> Unit, viewModel: MusicViewModel, onFavToggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val settings by viewModel.settings.collectAsState()
    val customTitleMap  by viewModel.customTitleMap.collectAsState()
    val customArtistMap by viewModel.customArtistMap.collectAsState()
    val displayTitle  = customTitleMap[song.id]  ?: song.title
    val displayArtist = customArtistMap[song.id] ?: song.artist
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = song.id,
            transitionSpec = {
                (fadeIn(tween(350)) + slideInHorizontally { it / 5 })
                    .togetherWith(fadeOut(tween(200)) + slideOutHorizontally { -it / 5 })
                    .using(SizeTransform(clip = false))
            },
            label = "songMeta",
            modifier = Modifier.weight(1f)
        ) { _ ->
            Column {
                val shadowModifier = if (settings.textShadowEnabled) {
                    Modifier
                } else Modifier
                AutoScrollingText(
                    text = if (settings.uppercaseTitles) displayTitle.uppercase() else displayTitle,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = settings.playerTitleSize.sp,
                        fontWeight = if (settings.boldTitles) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (settings.textShadowEnabled)
                            c.textPrimary.copy(alpha = 1f)
                        else c.textPrimary,
                        fontFamily = LocalAppFontFamily.current,
                        letterSpacing = settings.letterSpacingEm.em,
                        lineHeight = (settings.playerTitleSize * settings.lineHeightScale * 1.4f).sp,
                        shadow = if (settings.textShadowEnabled) androidx.compose.ui.graphics.Shadow(
                            color = c.accent.copy(alpha = settings.textShadowIntensity * 0.8f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                            blurRadius = 12f * settings.textShadowIntensity
                        ) else null
                    ),
                    textAlign = TextAlign.Start, gradientEdgeColor = c.bgDeep, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (displayArtist != "<unknown>" && displayArtist.isNotBlank()) displayArtist else "Неизвестный",
                    color = when(settings.artistNameStyle) {
                        2 -> c.textDisabled
                        else -> c.textSecondary
                    },
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = settings.playerArtistSize.sp,
                    fontStyle = if (settings.artistNameStyle == 1) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    letterSpacing = settings.letterSpacingEm.em,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (settings.textShadowEnabled) androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = c.accent.copy(alpha = settings.textShadowIntensity * 0.5f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                            blurRadius = 8f * settings.textShadowIntensity
                        )
                    ) else androidx.compose.ui.text.TextStyle.Default
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        FilledIconButton(
            onLyricsClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = c.surfaceContainerLow,
                contentColor = c.onSurface
            )
        ) {
            Icon(Icons.Rounded.Lyrics, null, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ProgressSection(currentPosition: Long, duration: Long, isPlaying: Boolean, viewModel: MusicViewModel) {
    val c = MaterialTheme.colorScheme
    val appStyle = com.musicplayer.ui.theme.LocalAppStyle.current
    val settings by viewModel.settings.collectAsState()
    val progress = if (duration > 0L) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        if (settings.useWavySeekBar) {
            WavyMusicSlider(
                value = progress,
                onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxWidth(),
                trackHeight = appStyle.sliderTrackHeight.dp.coerceIn(3.dp, 16.dp)
            )
        } else {
            Slider(
                value = progress,
                onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = c.primary,
                    activeTrackColor = c.primary,
                    inactiveTrackColor = c.surfaceContainerHighest
                )
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(currentPosition.toTimeString(), color = c.textSecondary, fontFamily = LocalAppFontFamily.current, fontSize = settings.playerTimeSize.sp)
            Text(duration.toTimeString(), color = c.textSecondary, fontFamily = LocalAppFontFamily.current, fontSize = settings.playerTimeSize.sp)
        }
    }
}

@Composable
private fun ControlsSection(settings: com.musicplayer.data.PlayerSettings, isPlaying: Boolean, viewModel: MusicViewModel) {
    val appStyle = com.musicplayer.ui.theme.LocalAppStyle.current
    // Применяем скругление кнопки из настроек темы
    val btnCorner = appStyle.buttonCornerRadius.dp.coerceIn(4.dp, 60.dp)
    AnimatedPlaybackControls(
        isPlayingProvider = { isPlaying }, onPrevious = { viewModel.playPrevious() }, onPlayPause = { viewModel.togglePlayPause() }, onNext = { viewModel.playNext() },
        modifier = Modifier.fillMaxWidth(), height = 80.dp, pressAnimationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        colorPlayPause = MaterialTheme.colorScheme.primary,
        tintPlayPauseIcon = MaterialTheme.colorScheme.onPrimary,
        colorOtherButtons = MaterialTheme.colorScheme.secondaryContainer,
        tintOtherIcons = MaterialTheme.colorScheme.onSecondaryContainer,
        playPauseCornerPlaying = btnCorner, playPauseCornerPaused = (btnCorner * 0.5f).coerceIn(4.dp, 30.dp)
    )
}

@Composable
private fun BottomToggleRow(settings: com.musicplayer.data.PlayerSettings, isFavourite: Boolean, viewModel: MusicViewModel, songId: Long) {
    val c = MaterialTheme.colorScheme
    val repeatIcon = when(settings.repeatMode) { RepeatMode.NONE -> Icons.Rounded.Repeat; RepeatMode.ALL -> Icons.Rounded.Repeat; RepeatMode.ONE -> Icons.Rounded.RepeatOne }
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.fillMaxSize().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ToggleSegmentButton(settings.shuffleEnabled, Icons.Rounded.Shuffle, "Перемешать", { viewModel.toggleShuffle() }, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f).fillMaxHeight())
            ToggleSegmentButton(settings.repeatMode != RepeatMode.NONE, repeatIcon, "Повтор", { viewModel.toggleRepeat() }, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f).fillMaxHeight())
            ToggleSegmentButton(isFavourite, if(isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Избранное", { viewModel.toggleFavourite(songId) }, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track Settings — Haze Glass Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSettingsOverlay(
    visible: Boolean, volume: Float, playbackSpeed: Float, song: com.musicplayer.data.Song?,
    viewModel: MusicViewModel,
    hazeState: HazeState,
    onVolumeChange: (Float) -> Unit, onSpeedChange: (Float) -> Unit, onSetRingtone: () -> Unit, onDismiss: () -> Unit
) {
    val c    = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    var showEqualizerSheet  by remember { mutableStateOf(false) }
    var showEditSection     by remember { mutableStateOf(false) }
    var showLyricsFontScale by remember { mutableStateOf(false) }

    val settings        by viewModel.settings.collectAsState()
    val customArtMap    by viewModel.customArtMap.collectAsState()
    val customTitleMap  by viewModel.customTitleMap.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = c.bgSurface,
            contentColor = c.textPrimary,
            tonalElevation = 0.dp,
            dragHandle = {
                Box(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.textDisabled.copy(0.45f)))
                }
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            val art = song?.let { customArtMap[it.id] ?: it.albumArtUri }
            val displayTitle = song?.let { customTitleMap[it.id] ?: it.title }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
            ) {
                // ── Header ────────────────────────────────────────────────
                item(key = "header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (song != null) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.bgElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                if (art != null) AsyncImage(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
                                else Icon(Icons.Rounded.MusicNote, null, tint = c.accentMuted, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Настройки трека", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (song != null && displayTitle != null) {
                                Text(displayTitle, color = c.textSecondary, fontFamily = font, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        FilledIconButton(
                            onDismiss,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.bgElevated, contentColor = c.textSecondary),
                            modifier = Modifier.size(36.dp)
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(22.dp)) }
                    }
                }

                // ── Volume ────────────────────────────────────────────────
                item(key = "volume") {
                    SheetSection {
                        SheetSectionHeader(Icons.Rounded.VolumeUp, "Громкость", "${(volume * 100).roundToInt()}%", c, font)
                        SheetSlider(volume, 0f, 1f, onVolumeChange, Icons.Rounded.VolumeDown, Icons.Rounded.VolumeUp, c)
                    }
                }

                // ── Speed ─────────────────────────────────────────────────
                item(key = "speed") {
                    SheetSection {
                        SheetSectionHeader(Icons.Rounded.Speed, "Скорость", "${"%.2f".format(playbackSpeed).trimEnd('0').trimEnd('.')}×", c, font)
                        SheetSlider(playbackSpeed, 0.25f, 2.0f, onSpeedChange, Icons.Rounded.SlowMotionVideo, Icons.Rounded.Speed, c)
                    }
                }

                // ── Lyrics Font Scale ─────────────────────────────────────
                item(key = "lyrics_scale") {
                    SheetSection {
                        SheetExpandRow(
                            icon = Icons.Rounded.FormatSize, title = "Масштаб текста",
                            subtitle = "${(settings.lyricsFontScale * 100).roundToInt()}% — размер букв текста песни",
                            expanded = showLyricsFontScale, onToggle = { showLyricsFontScale = !showLyricsFontScale },
                            c = c, font = font
                        )
                        AnimatedVisibility(showLyricsFontScale, enter = expandVertically(tween(220)), exit = shrinkVertically(tween(180))) {
                            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                LyricsFontScalePanel(currentScale = settings.lyricsFontScale, onScaleChange = { viewModel.updateSettings(settings.copy(lyricsFontScale = it)) })
                                InnerCard(c) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        InnerCardHeader(Icons.Rounded.BlurOn, "Тени текста песни", c, font)
                                        LyricsFadeStylePicker(currentStyle = settings.lyricsFadeStyle, onStyleChange = { viewModel.updateSettings(settings.copy(lyricsFadeStyle = it)) })
                                    }
                                }
                                InnerCard(c) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        InnerCardHeader(Icons.Rounded.AlignHorizontalLeft, "Выравнивание текста", c, font)
                                        LyricsAlignmentPicker(currentAlignment = settings.lyricsAlignment, onAlignmentChange = { viewModel.updateSettings(settings.copy(lyricsAlignment = it)) })
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                        .background(c.bgElevated)
                                        .clickable { viewModel.updateSettings(settings.copy(lyricsCurlAnim = !settings.lyricsCurlAnim)) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, null, tint = c.accentVar, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Анимация закручивания", color = c.textPrimary, fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Строки заворачиваются при входе снизу", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                                    }
                                    Switch(settings.lyricsCurlAnim, { viewModel.updateSettings(settings.copy(lyricsCurlAnim = it)) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = c.bgDeep, checkedTrackColor = c.accentVar, uncheckedThumbColor = c.textDisabled, uncheckedTrackColor = c.bgElevated))
                                }
                            }
                        }
                    }
                }

                // ── Настроить трек ────────────────────────────────────────
                item(key = "edit_track") {
                    SheetSection {
                        SheetExpandRow(
                            icon = Icons.Rounded.Edit, title = "Настроить трек",
                            subtitle = "Обложка, название, исполнитель",
                            expanded = showEditSection, onToggle = { showEditSection = !showEditSection },
                            c = c, font = font
                        )
                        AnimatedVisibility(showEditSection, enter = expandVertically(tween(220)), exit = shrinkVertically(tween(180))) {
                            if (song != null) Box(Modifier.padding(top = 12.dp)) { TrackEditPanel(song = song, viewModel = viewModel) }
                        }
                    }
                }

                // ── Actions ───────────────────────────────────────────────
                item(key = "actions") {
                    SheetSection {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            if (song != null) {
                                var showInfo by remember { mutableStateOf(false) }
                                SheetActionRow(Icons.Rounded.Info, "Информация о треке", "Метаданные, формат, путь к файлу", c, font) { showInfo = true }
                                SheetDivider(c)
                                if (showInfo) TrackInfoDialog(song = song, c = c, font = font) { showInfo = false }
                            }
                            SheetActionRow(Icons.Rounded.Equalizer, "Эквалайзер", "Настройка частот звука", c, font) { showEqualizerSheet = true }
                            SheetDivider(c)
                            SheetActionRow(Icons.Rounded.GraphicEq, "Нормализация громкости", "Выровнять уровень звука", c, font, badge = true) {}
                            SheetDivider(c)
                            SheetActionRow(Icons.Rounded.Tune, "Буст баса", "Усилить низкие частоты", c, font, badge = true) {}
                            SheetDivider(c)
                            SheetActionRow(Icons.Rounded.AddAlert, "Поставить на звонок", "Мелодия звонка", c, font) { onSetRingtone() }
                        }
                    }
                }
            }
        }
    }

    if (showEqualizerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEqualizerSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EqualizerSheetContent(viewModel = viewModel, onDismiss = { showEqualizerSheet = false })
        }
    }
}

// ── Sheet helpers ──────────────────────────────────────────────────────────────

@Composable
private fun SheetSection(content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgElevated)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        content = content
    )
}

@Composable
private fun SheetSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, value: String,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(17.dp))
        }
        Text(title, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = c.accent, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun SheetSlider(
    value: Float, min: Float, max: Float, onChange: (Float) -> Unit,
    startIcon: androidx.compose.ui.graphics.vector.ImageVector,
    endIcon: androidx.compose.ui.graphics.vector.ImageVector,
    c: ColorScheme
) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(startIcon, null, tint = c.textDisabled, modifier = Modifier.size(18.dp))
        Slider(value, onChange, valueRange = min..max, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.bgCard.copy(0.8f)))
        Icon(endIcon, null, tint = c.textDisabled, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SheetExpandRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, subtitle: String,
    expanded: Boolean, onToggle: () -> Unit,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
        }
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(c.bgCard.copy(0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null, tint = c.textDisabled, modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SheetDivider(c: ColorScheme) {
    HorizontalDivider(Modifier.padding(vertical = 2.dp), color = c.divider.copy(0.25f), thickness = 0.5.dp)
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, subtitle: String,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?,
    badge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (badge) Surface(shape = RoundedCornerShape(6.dp), color = c.accent.copy(0.16f)) {
                    Text("Скоро", color = c.accent, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(subtitle, color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.textDisabled.copy(0.5f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun InnerCard(c: ColorScheme, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
private fun InnerCardHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String,
    c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(15.dp))
        Text(title, color = c.textPrimary, fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Keep old GlassSection aliases for any remaining references (removed above, but keep stubs just in case)
@Composable private fun GlassSection(content: @Composable ColumnScope.() -> Unit) = SheetSection(content)
@Composable private fun GlassSectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?) = SheetSectionHeader(icon, title, value, c, font)
@Composable private fun GlassSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit, startIcon: androidx.compose.ui.graphics.vector.ImageVector, endIcon: androidx.compose.ui.graphics.vector.ImageVector, c: ColorScheme) = SheetSlider(value, min, max, onChange, startIcon, endIcon, c)
@Composable private fun GlassExpandRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit, c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?) = SheetExpandRow(icon, title, subtitle, expanded, onToggle, c, font)
@Composable private fun GlassDivider(c: ColorScheme) = SheetDivider(c)
@Composable private fun GlassActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?, badge: Boolean = false, onClick: () -> Unit) = SheetActionRow(icon, title, subtitle, c, font, badge, onClick)

@Composable
private fun TrackInfoDialog(song: com.musicplayer.data.Song, c: ColorScheme, font: androidx.compose.ui.text.font.FontFamily?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Информация", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold) },
        text = {
            val dm = song.duration / 60000; val ds = (song.duration % 60000) / 1000
            val ext = song.uri.lastPathSegment?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.length in 2..5 } ?: "AUDIO"
            val path = song.uri.path ?: song.uri.toString()
            val kb = try { java.io.File(song.uri.path ?: "").length() / 1024 } catch (_: Exception) { 0L }
            @Composable fun R(l: String, v: String) { Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(l, color = c.textDisabled, fontFamily = font, fontSize = 12.sp, modifier = Modifier.width(96.dp)); Text(v, color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), softWrap = true) } }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                R("Название", song.title.ifBlank { "?" }); R("Исполнитель", song.artist.ifBlank { "?" }); R("Альбом", song.album.ifBlank { "?" })
                R("Длительность", "%02d:%02d".format(dm, ds)); R("Формат", ext)
                if (kb > 0L) R("Размер", if (kb > 1024) "${"%.1f".format(kb / 1024f)} МБ" else "$kb КБ")
                R("Путь", path); R("ID", song.id.toString())
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Закрыть", color = c.accent, fontFamily = font, fontWeight = FontWeight.Bold) } }
    )
}

// ── Track Edit Panel ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackEditPanel(song: com.musicplayer.data.Song, viewModel: MusicViewModel) {
    val c       = MaterialTheme.colorScheme
    val font    = LocalAppFontFamily.current
    val context = LocalContext.current

    val customTitleMap  by viewModel.customTitleMap.collectAsState()
    val customArtistMap by viewModel.customArtistMap.collectAsState()
    val customArtMap    by viewModel.customArtMap.collectAsState()

    var titleText  by remember(song.id) { mutableStateOf(customTitleMap[song.id] ?: song.title) }
    var artistText by remember(song.id) { mutableStateOf(customArtistMap[song.id] ?: song.artist.takeIf { it != "<unknown>" } ?: "") }
    val currentArt = customArtMap[song.id] ?: song.albumArtUri

    val artPickerForCurrent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.setCustomArt(song.id, it) } }
    val artPickerForAll     = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.setCustomArtForAll(it) } }

    var saved by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
        // ── Обложка ───────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bgElevated).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(c.bgDeep)
                    .border(1.dp, c.accentMuted.copy(0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    currentArt != null -> AsyncImage(currentArt, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
                    else -> Icon(Icons.Rounded.MusicNote, null, tint = c.accentMuted, modifier = Modifier.size(28.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { artPickerForCurrent.launch("image/*") },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.accent.copy(0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Image, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Выбрать обложку", fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { artPickerForAll.launch("image/*") },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.accentVar.copy(0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accentVar),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.SelectAll, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Применить ко всем", fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { viewModel.resetAllCustomArt() },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.accentMuted.copy(0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accentMuted),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.HideImage, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сбросить все обложки", fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Название ──────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Название", color = c.accent, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.5.sp)
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it; saved = false },
                placeholder = { Text("Название трека", color = c.textDisabled, fontFamily = font) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.accentMuted.copy(0.4f),
                    focusedTextColor = c.textPrimary,
                    unfocusedTextColor = c.textPrimary,
                    cursorColor = c.accent,
                    focusedContainerColor = c.bgElevated,
                    unfocusedContainerColor = c.bgElevated
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = font, fontSize = 15.sp),
                leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = c.accent, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Исполнитель ───────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Исполнитель", color = c.accent, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.5.sp)
            OutlinedTextField(
                value = artistText,
                onValueChange = { artistText = it; saved = false },
                placeholder = { Text("Исполнитель", color = c.textDisabled, fontFamily = font) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.accentMuted.copy(0.4f),
                    focusedTextColor = c.textPrimary,
                    unfocusedTextColor = c.textPrimary,
                    cursorColor = c.accent,
                    focusedContainerColor = c.bgElevated,
                    unfocusedContainerColor = c.bgElevated
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = font, fontSize = 15.sp),
                leadingIcon = { Icon(Icons.Rounded.Person, null, tint = c.accent, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Кнопка сохранить ──────────────────────────────────────────────
        Button(
            onClick = {
                viewModel.setCustomTitle(song.id, titleText.trim().takeIf { it.isNotBlank() })
                viewModel.setCustomArtist(song.id, artistText.trim().takeIf { it.isNotBlank() })
                saved = true
                android.widget.Toast.makeText(context, "Сохранено ✓", android.widget.Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (saved) c.accentMuted else c.accent, contentColor = c.bgDeep),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(if (saved) Icons.Rounded.CheckCircle else Icons.Rounded.Save, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (saved) "Сохранено" else "Сохранить", fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ── Lyrics Font Scale Panel ────────────────────────────────────────────────────
@Composable
fun LyricsFontScalePanel(
    currentScale: Float,
    onScaleChange: (Float) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    // Sample lyrics text for preview
    val sampleLines = listOf(
        "Это пример текста песни",
        "The quick brown fox",
        "Просто красивые слова",
        "Just example lyrics here"
    )
    val sampleText = sampleLines.joinToString("\n")
    val baseFontSize = 16f
    val previewFontSize = (baseFontSize * currentScale).sp

    Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgElevated)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Preview box
        Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.bgDeep)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Show exactly how lyrics will look
                sampleLines.forEachIndexed { idx, line ->
                    Text(
                        text = line,
                        color = if (idx == 1) c.accent else c.textPrimary.copy(alpha = 0.7f),
                        fontFamily = font,
                        fontSize = previewFontSize,
                        fontWeight = if (idx == 1) FontWeight.Bold else FontWeight.Normal,
                        lineHeight = (previewFontSize.value * 1.4f).sp
                    )
                }
            }
        }

        // Scale value badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FormatSize, null, tint = c.accent, modifier = androidx.compose.ui.Modifier.size(18.dp))
            Spacer(androidx.compose.ui.Modifier.width(8.dp))
            Text("Масштаб", color = c.textPrimary, fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = androidx.compose.ui.Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(8.dp), color = c.accent.copy(0.15f)) {
                Text(
                    "${(currentScale * 100).roundToInt()}%",
                    color = c.accent, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = androidx.compose.ui.Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Slider
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.ZoomOut, null, tint = c.textDisabled, modifier = androidx.compose.ui.Modifier.size(18.dp))
            Slider(
                value = currentScale,
                onValueChange = onScaleChange,
                valueRange = 0.6f..2.0f,
                steps = 27,
                modifier = androidx.compose.ui.Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = c.accent,
                    activeTrackColor = c.accent,
                    inactiveTrackColor = c.bgCard
                )
            )
            Icon(Icons.Rounded.ZoomIn, null, tint = c.textDisabled, modifier = androidx.compose.ui.Modifier.size(22.dp))
        }

        // Quick presets
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) {
            listOf(0.75f to "S", 1.0f to "M", 1.25f to "L", 1.5f to "XL", 1.75f to "XXL").forEach { (scale, label) ->
                val sel = kotlin.math.abs(currentScale - scale) < 0.05f
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) c.accent else c.bgCard)
                        .clickable { onScaleChange(scale) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

// ── Lyrics Fade Style Picker ───────────────────────────────────────────────────
@Composable
fun LyricsFadeStylePicker(
    currentStyle: Int,
    onStyleChange: (Int) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    val styles = listOf(
        Triple(0, Icons.Rounded.Circle, "Без теней"),
        Triple(1, Icons.Rounded.BlurOn,    "Мягкий"),
        Triple(2, Icons.Rounded.Layers,      "Сильный"),
    )

    Row(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        styles.forEach { (id, icon, label) ->
            val sel = currentStyle == id
            Box(
                modifier = androidx.compose.ui.Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) c.accent else c.bgElevated)
                    .clickable { onStyleChange(id) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(icon, null, tint = if (sel) c.bgDeep else c.textSecondary, modifier = androidx.compose.ui.Modifier.size(20.dp))
                    Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}


// ── Lyrics Alignment Picker ───────────────────────────────────────────────────
@Composable
fun LyricsAlignmentPicker(
    currentAlignment: Int,
    onAlignmentChange: (Int) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    val options = listOf(
        Triple(0, Icons.Rounded.AlignHorizontalLeft,   "Слева"),
        Triple(1, Icons.Rounded.AlignHorizontalCenter, "По центру"),
        Triple(2, Icons.Rounded.AlignHorizontalRight,  "Справа"),
    )

    // Preview text
    val previewAlign = when (currentAlignment) {
        1 -> androidx.compose.ui.text.style.TextAlign.Center
        2 -> androidx.compose.ui.text.style.TextAlign.End
        else -> androidx.compose.ui.text.style.TextAlign.Start
    }

    // Mini preview
    Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.bgDeep)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Это пример текста песни", "Как будут выглядеть строки", "При выбранном выравнивании").forEachIndexed { idx, line ->
                Text(
                    line,
                    color = if (idx == 1) c.accent else c.textPrimary.copy(0.6f),
                    fontFamily = font,
                    fontSize = 13.sp,
                    fontWeight = if (idx == 1) FontWeight.Bold else FontWeight.Normal,
                    textAlign = previewAlign,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
            }
        }
    }

    Spacer(androidx.compose.ui.Modifier.height(8.dp))

    Row(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (id, icon, label) ->
            val sel = currentAlignment == id
            Box(
                modifier = androidx.compose.ui.Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) c.accent else c.bgCard)
                    .clickable { onAlignmentChange(id) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(icon, null, tint = if (sel) c.bgDeep else c.textSecondary, modifier = androidx.compose.ui.Modifier.size(20.dp))
                    Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

private fun setAsRingtone(context: Context, uri: Uri) {
    try {
        if (!Settings.System.canWrite(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply { data = Uri.parse("package:${context.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            Toast.makeText(context, "Разрешите изменение системных настроек и повторите", Toast.LENGTH_LONG).show()
            return
        }
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
        Toast.makeText(context, "Мелодия звонка установлена ✓", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
