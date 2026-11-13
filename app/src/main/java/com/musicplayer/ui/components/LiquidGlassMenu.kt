package com.musicplayer.ui.components

import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView
import kotlin.math.min
import kotlin.math.roundToInt

// ───────────────────────── state / модель ─────────────────────────

@Stable
class LiquidGlassMenuState {
    /** Открыто ли меню. Можно читать и менять напрямую. */
    var expanded by mutableStateOf(false)

    /** Границы кнопки-источника в координатах корня композиции (px). */
    internal var anchor by mutableStateOf(Rect.Zero)

    fun show() { expanded = true }
    fun dismiss() { expanded = false }
    fun toggle() { expanded = !expanded }
}

@Composable
fun rememberLiquidGlassMenuState() = remember { LiquidGlassMenuState() }

/** Повесь на кнопку «…», из которой должно расти меню. */
fun Modifier.liquidGlassAnchor(state: LiquidGlassMenuState): Modifier =
    onGloballyPositioned { state.anchor = it.boundsInRoot() }

data class GlassMenuItem(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    /** Тонкая линия-разделитель над этим пунктом. */
    val dividerAbove: Boolean = false,
    val onClick: () -> Unit,
)

private fun GlassMenuItem.heightDp(): Int =
    (if (subtitle != null) 60 else 48) + (if (dividerAbove) 9 else 0)

// ───────────────────────── само меню ─────────────────────────

/**
 * Liquid Glass меню, которое вырастает из кнопки с [liquidGlassAnchor].
 * Ставь внутрь Box, который занимает весь экран, ПОСЛЕ основного контента.
 */
@Composable
fun LiquidGlassMenu(
    state: LiquidGlassMenuState,
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    menuWidth: Dp = 220.dp,
    cornerRadius: Dp = 28.dp,
    fontFamily: FontFamily? = null,
) {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    var overLight by remember { mutableStateOf(true) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val itemsState = rememberUpdatedState(items)
    val fontState = rememberUpdatedState(fontFamily)

    // Открытие: пружина с перелётом (капля «выстреливает» и оседает).
    // Закрытие: плавное схлопывание обратно в кнопку.
    LaunchedEffect(state.expanded) {
        if (state.expanded) {
            progress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
        } else {
            progress.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
    }
    BackHandler(enabled = state.expanded) { state.dismiss() }

    // Стекло снимает фон с контента Activity
    val hostView = LocalView.current
    val backdrop: View = remember(hostView) {
        hostView.rootView.findViewById(android.R.id.content) ?: hostView.rootView
    }

    // Внешний Box только измеряет своё положение (касания не перехватывает)
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        val p = progress.value
        val anchor = state.anchor.translate(-origin.x, -origin.y)
        val visible = (state.expanded || p > 0.001f) && state.anchor != Rect.Zero

        if (visible) {
            val menuHeight = (items.sumOf { it.heightDp() } + 16).dp
            val menuWpx = with(density) { menuWidth.toPx() }
            val menuHpx = with(density) { menuHeight.toPx() }
            val btnRadius = min(anchor.width, anchor.height) / 2f
            val menuRadius = with(density) { cornerRadius.toPx() }

            // Геометрия капли: правый верхний угол закреплён на кнопке
            val wPx = (anchor.width + (menuWpx - anchor.width) * p).coerceAtLeast(1f)
            val hPx = (anchor.height + (menuHpx - anchor.height) * p).coerceAtLeast(1f)
            val leftPx = anchor.right - wPx
            val rPx = btnRadius + (menuRadius - btnRadius) * p.coerceIn(0f, 1f)

            // Прозрачная подложка: тап вне меню закрывает его
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { state.dismiss() } }
            )

            AndroidView(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), anchor.top.roundToInt()) }
                    .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() }),
                factory = { ctx ->
                    val glass = LiquidGlassView(ctx)
                    glass.backdropSource = backdrop
                    glass.enableDynamicBackground = true   // ОБЯЗАТЕЛЬНО: стекло двигается
                    glass.material = GlassMaterial.REGULAR
                    glass.bevelWidth = with(density) { 22.dp.toPx() }
                    glass.refractionHeight = with(density) { 14.dp.toPx() }
                    glass.refractionFalloff = 2f
                    glass.dispersionStrength = 0.08f
                    glass.enableSensorHighlight = true
                    glass.enableAdaptiveTint = true
                    glass.setGlassTint(android.graphics.Color.WHITE, 0.18f)
                    glass.enablePressEffect = false
                    glass.collectFrameStats = false
                    glass.glassAppearanceListener = { isOverLight -> overLight = isOverLight }

                    // Контент кладём ВНУТРЬ стекла — так он не попадает в захват фона
                    val content = ComposeView(ctx).apply {
                        setViewCompositionStrategy(
                            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                        )
                        setContent {
                            MenuContent(
                                items = { itemsState.value },
                                progress = { progress.value },
                                overLight = { overLight },
                                fontFamily = { fontState.value },
                                width = menuWidth,
                                height = menuHeight,
                                onItemClick = { item ->
                                    state.dismiss()
                                    item.onClick()
                                },
                            )
                        }
                    }
                    glass.addView(
                        content,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    )
                    glass
                },
                update = { glass -> glass.cornerRadius = rPx },
            )
        }
    }
}

// ───────────────────────── содержимое ─────────────────────────

@Composable
private fun MenuContent(
    items: () -> List<GlassMenuItem>,
    progress: () -> Float,
    overLight: () -> Boolean,
    fontFamily: () -> FontFamily?,
    width: Dp,
    height: Dp,
    onItemClick: (GlassMenuItem) -> Unit,
) {
    val fg = if (overLight()) Color(0xFF111111) else Color.White
    val font = fontFamily()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                // фиксированный размер: текст не переполз, пока капля растёт
                .requiredSize(width, height)
                .graphicsLayer {
                    // как на видео: контент проявляется из размытия
                    val t = ((progress() - 0.35f) / 0.5f).coerceIn(0f, 1f)
                    alpha = t
                    val blur = (1f - t) * 14.dp.toPx()
                    renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
                }
                .padding(vertical = 8.dp)
        ) {
            items().forEach { item ->
                if (item.dividerAbove) {
                    Box(
                        Modifier
                            .padding(vertical = 4.dp, horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(fg.copy(alpha = 0.15f))
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(if (item.subtitle != null) 60.dp else 48.dp)
                        .clickable { onItemClick(item) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.icon != null) {
                        Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                    } else {
                        Spacer(Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(item.title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = font)
                        if (item.subtitle != null) {
                            Text(item.subtitle, color = fg.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = font)
                        }
                    }
                }
            }
        }
    }
}
