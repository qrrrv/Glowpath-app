package com.musicplayer.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.size.Size as CoilSize
import com.musicplayer.ui.theme.accentMuted
import com.musicplayer.ui.theme.bgCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/** Что нужно знать карусели про один трек: его id и обложку. */
data class CoverItem(val songId: Long, val artUri: Uri?)

/**
 * Карусель обложек со свайпом влево/вправо.
 *
 * Как это устроено (и почему не дёргается):
 *  1. Одно-единственное значение [rawOffset] (в px) двигает ВСЕ три обложки (пред / текущая / след).
 *     Оно читается только внутри graphicsLayer {} → во время свайпа нет ни одной рекомпозиции.
 *  2. Наклон, масштаб, прозрачность и поворот считаются как чистая функция позиции обложки
 *     ("pos" = слот + смещение / ширина страницы). Поэтому в момент смены трека, когда соседняя
 *     обложка становится текущей, её вид совпадает до пикселя — никакого скачка.
 *  3. Смещение "привязано" к id трека ([offsetOwner]). Как только id трека сменился, смещение
 *     считается нулём в ТОМ ЖЕ кадре, где появились новые слоты (без LaunchedEffect и задержки).
 *  4. Жест: настоящий touch slop (тап не двигает обложку), velocity, fling, rubber-band у края,
 *     вертикальный свайп закрытия плеера продолжает работать.
 *  5. Отпускание: пружина с начальной скоростью пальца — обложка продолжает движение, а не
 *     "перескакивает" в новое состояние.
 */
@Composable
fun CoverSwipeCarousel(
    current: CoverItem,
    previous: CoverItem?,
    next: CoverItem?,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    modifier: Modifier = Modifier,
    coverSize: Dp = 280.dp,
    gap: Dp = 24.dp,
    shape: Shape,
    /** Макс. наклон в плоскости экрана (rotationZ), градусы, на расстоянии одной страницы. */
    tiltZDegrees: Float = 9f,
    /** Макс. 3D-поворот вокруг вертикальной оси (rotationY), градусы. */
    tiltYDegrees: Float = 26f,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val widthPx = with(density) { coverSize.toPx() }
    val pagePx = with(density) { (coverSize + gap).toPx() }
    val commitDistancePx = widthPx * 0.30f
    val rubberRangePx = widthPx * 0.16f
    val maxFlingPx = with(density) { 6000.dp.toPx() }
    val cameraDistance = 14f * density.density

    // Актуальные значения для корутин/жестов — без перезапуска pointerInput.
    val currentIdState by rememberUpdatedState(current.songId)
    val hasPreviousState by rememberUpdatedState(previous != null)
    val hasNextState by rememberUpdatedState(next != null)
    val onNextState by rememberUpdatedState(onSwipeNext)
    val onPreviousState by rememberUpdatedState(onSwipePrevious)

    // Смещение (px). Читается ТОЛЬКО в graphicsLayer.
    var rawOffset by remember { mutableFloatStateOf(0f) }
    // Для какого трека это смещение. Если id сменился — смещение считается нулём.
    var offsetOwner by remember { mutableLongStateOf(current.songId) }
    var animJob by remember { mutableStateOf<Job?>(null) }

    // Небольшой "толчок" при посадке новой обложки на место (вращение с пружиной).
    val landingKick = remember { Animatable(0f) }
    var pendingKick by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(current.songId) {
        val kick = pendingKick
        pendingKick = 0f
        if (kick != 0f) {
            landingKick.snapTo(kick)
            landingKick.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 230f))
        }
    }

    val currentId = current.songId
    val offsetProvider: () -> Float = { if (offsetOwner == currentId) rawOffset else 0f }

    // ── логика отпускания ────────────────────────────────────────────────────────────────────
    fun settleBack(fromOffset: Float, velocity: Float, ownerId: Long) {
        offsetOwner = ownerId
        rawOffset = fromOffset
        animJob?.cancel()
        animJob = scope.launch {
            animate(
                initialValue = fromOffset,
                targetValue = 0f,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f)
            ) { value, _ -> rawOffset = value }
        }
    }

    fun commit(forward: Boolean, fromOffset: Float, velocity: Float, ownerId: Long) {
        val target = if (forward) -pagePx else pagePx
        offsetOwner = ownerId
        rawOffset = fromOffset
        pendingKick = (if (forward) -1f else 1f) * (2f + min(abs(velocity) / 3000f, 1f) * 4f)
        animJob?.cancel()
        animJob = scope.launch {
            var swapped = false
            val snapEpsilon = 2f
            animate(
                initialValue = fromOffset,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 360f)
            ) { value, _ ->
                rawOffset = value
                // Меняем трек в момент, когда соседняя обложка почти села по центру.
                if (!swapped && abs(value) >= pagePx - snapEpsilon) {
                    swapped = true
                    if (forward) onNextState() else onPreviousState()
                }
            }
            if (!swapped) {
                swapped = true
                if (forward) onNextState() else onPreviousState()
            }
            // Если трек по какой-то причине не сменился — красиво вернуть обложку обратно.
            val changed = withTimeoutOrNull(600) {
                snapshotFlow { currentIdState }.first { it != ownerId }
            } != null
            if (!changed) {
                pendingKick = 0f
                animate(
                    initialValue = rawOffset,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f)
                ) { value, _ -> rawOffset = value }
            } else {
                rawOffset = 0f
                offsetOwner = currentIdState
            }
        }
    }

    fun release(velocity: Float, canPrevious: Boolean, canNext: Boolean) {
        val ownerId = currentIdState
        val startOffset = if (offsetOwner == ownerId) rawOffset else 0f
        val v = velocity.coerceIn(-maxFlingPx, maxFlingPx)
        // "Куда бы обложка доехала по инерции" — так короткий быстрый флик тоже листает.
        val projected = startOffset + v * 0.12f
        when {
            canNext && projected < -commitDistancePx -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                commit(forward = true, fromOffset = startOffset, velocity = v, ownerId = ownerId)
            }
            canPrevious && projected > commitDistancePx -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                commit(forward = false, fromOffset = startOffset, velocity = v, ownerId = ownerId)
            }
            startOffset != 0f -> settleBack(startOffset, v, ownerId)
        }
    }

    // ── слоты ────────────────────────────────────────────────────────────────────────────────
    val slots = buildList {
        if (previous != null) add(previous to -1)
        add(current to 0)
        if (next != null) add(next to 1)
    }
    val hasDuplicateIds = slots.map { it.first.songId }.distinct().size != slots.size

    Box(
        modifier = modifier
            .size(coverSize)
            .pointerInput(pagePx, commitDistancePx, rubberRangePx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // Схватили обложку в полёте — останавливаем анимацию и продолжаем с этого места.
                    animJob?.cancel()
                    animJob = null
                    val ownerId = currentIdState
                    if (offsetOwner != ownerId) {
                        rawOffset = 0f
                        offsetOwner = ownerId
                    }
                    // Возможности фиксируем на время жеста: границы не "прыгают" посреди свайпа.
                    val canPrevious = hasPreviousState
                    val canNext = hasNextState
                    val minX = if (canNext) -pagePx else 0f
                    val maxX = if (canPrevious) pagePx else 0f

                    fun rubber(over: Float) = rubberRangePx * (1f - exp(-over / rubberRangePx))
                    fun unrubber(v: Float) =
                        -rubberRangePx * ln(1f - (v / rubberRangePx).coerceIn(0f, 0.999f))

                    fun toOffset(finger: Float): Float = when {
                        finger < minX -> minX - rubber(minX - finger)
                        finger > maxX -> maxX + rubber(finger - maxX)
                        else -> finger
                    }
                    fun toFinger(offset: Float): Float = when {
                        offset < minX -> minX - unrubber(minX - offset)
                        offset > maxX -> maxX + unrubber(offset - maxX)
                        else -> offset
                    }

                    var finger = toFinger(rawOffset)
                    var armed = abs(rawOffset) > commitDistancePx &&
                        ((rawOffset < 0f && canNext) || (rawOffset > 0f && canPrevious))
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    fun applyFinger() {
                        rawOffset = toOffset(finger)
                        val nowArmed = abs(rawOffset) > commitDistancePx &&
                            ((rawOffset < 0f && canNext) || (rawOffset > 0f && canPrevious))
                        if (nowArmed != armed) {
                            armed = nowArmed
                            haptics.performHapticFeedback(
                                if (nowArmed) HapticFeedbackType.GestureThresholdActivate
                                else HapticFeedbackType.TextHandleMove
                            )
                        }
                    }

                    val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        change.consume()
                        finger += overSlop
                        tracker.addPosition(change.uptimeMillis, change.position)
                        applyFinger()
                    }

                    var velocity = 0f
                    if (slopChange != null) {
                        val finished = horizontalDrag(slopChange.id) { change ->
                            finger += change.positionChange().x
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            applyFinger()
                        }
                        if (finished) velocity = tracker.calculateVelocity().x
                    }
                    release(velocity, canPrevious, canNext)
                }
            }
    ) {
        slots.forEach { (item, slot) ->
            key(if (hasDuplicateIds) "${item.songId}#$slot" else item.songId.toString()) {
                CoverCard(
                    item = item,
                    slot = slot,
                    offsetPx = offsetProvider,
                    kickDegrees = { if (slot == 0) landingKick.value else 0f },
                    pagePx = pagePx,
                    cameraDistance = cameraDistance,
                    tiltZDegrees = tiltZDegrees,
                    tiltYDegrees = tiltYDegrees,
                    shape = shape,
                )
            }
        }
    }
}

@Composable
private fun CoverCard(
    item: CoverItem,
    slot: Int,
    offsetPx: () -> Float,
    kickDegrees: () -> Float,
    pagePx: Float,
    cameraDistance: Float,
    tiltZDegrees: Float,
    tiltYDegrees: Float,
    shape: Shape,
) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // pos: 0 = по центру, ±1 = ровно одна страница в сторону.
                val pos = slot + offsetPx() / pagePx
                val distance = abs(pos)
                val limited = pos.coerceIn(-1.25f, 1.25f)

                translationX = pos * pagePx
                // Наклон: верх обложки "заваливается" по ходу движения (точка вращения — низ).
                transformOrigin = TransformOrigin(0.5f, 1f)
                rotationZ = limited * tiltZDegrees + kickDegrees()
                // 3D: обложка разворачивается как в Cover Flow.
                rotationY = limited * tiltYDegrees
                this.cameraDistance = cameraDistance

                val scale = 1f - 0.10f * min(distance, 1f)
                scaleX = scale
                scaleY = scale

                // Соседи невидимы в покое и плавно проявляются по мере приближения к центру.
                val t = ((distance - 0.30f) / 0.70f).coerceIn(0f, 1f)
                alpha = 1f - t * t * (3f - 2f * t)

                this.shape = shape
                clip = true
            }
            .background(c.bgCard)
            .border(1.5.dp, c.accentMuted.copy(alpha = 0.3f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (item.artUri != null) {
            OptimizedAlbumArt(
                uri = item.artUri,
                title = "",
                modifier = Modifier.fillMaxSize(),
                targetSize = CoilSize(640, 640)
            )
        } else {
            Icon(
                painterResource(instrumentIconRes(item.songId)),
                contentDescription = null,
                tint = c.accentMuted,
                modifier = Modifier.size(80.dp)
            )
        }
    }
}
