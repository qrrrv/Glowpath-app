package com.musicplayer.ui.components

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Что нужно знать карусели про один трек: его id и обложку. */
data class CoverItem(val songId: Long, val artUri: Uri?)

/**
 * Карусель обложек со свайпом влево/вправо. Повторяет анимацию из LatentJam:
 *
 *  • Обложка идёт за пальцем (92 %) и наклоняется в плоскости экрана: 6° на ширину обложки.
 *    Наклон есть ТОЛЬКО у текущей обложки — соседние едут ровно.
 *  • Соседняя обложка стоит за краем и проявляется (alpha + лёгкий scale), пока текущая уезжает.
 *  • Порог смены трека — 30 % ширины (или быстрый флик). Вибрация как в LatentJam:
 *    один "порог" за жест при пересечении 30 %, лёгкий тик на отпускании со сменой трека.
 *    Если с этой стороны трека нет — резинка (30 %) и вибрация "нельзя" на отпускании.
 *  • После отпускания наклон возвращается в 0 пружиной с отскоком, пока обложка улетает.
 *  • Трек меняется, когда обложка уже улетела. Новая текущая обложка садится ровно туда, где стояла
 *    соседняя (тот же composable, тот же кадр) — никаких скачков, никаких "толчков" при посадке.
 *  • Вертикальный свайп (закрытие плеера) не перехватывается: ось выбирается по первому движению.
 *
 * Во время движения нет ни одной рекомпозиции: смещение и наклон читаются только в graphicsLayer {}.
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
    /** Наклон текущей обложки (градусы) при смещении на её ширину. */
    tiltDegrees: Float = 6f,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val widthPx = with(density) { coverSize.toPx() }
    val pagePx = with(density) { (coverSize + gap).toPx() }
    val rejectPx = with(density) { REJECT_TRAVEL.toPx() }
    val maxFlingPx = with(density) { 6000.dp.toPx() }

    // Актуальные значения для жестов и корутин — без перезапуска pointerInput.
    val currentIdState by rememberUpdatedState(current.songId)
    val hasPreviousState by rememberUpdatedState(previous != null)
    val hasNextState by rememberUpdatedState(next != null)
    val onNextState by rememberUpdatedState(onSwipeNext)
    val onPreviousState by rememberUpdatedState(onSwipePrevious)

    val motion = remember { CoverMotion(current.songId) }
    val currentId = current.songId

    // Соседи первыми, текущая поверх всех.
    val slots = buildList {
        if (previous != null) add(previous to -1)
        if (next != null) add(next to 1)
        add(current to 0)
    }
    val hasDuplicateIds = slots.map { it.first.songId }.distinct().size != slots.size

    Box(
        modifier = modifier
            .size(coverSize)
            .pointerInput(widthPx, pagePx, tiltDegrees) {
                val commitPx = widthPx * COMMIT_FRACTION
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // Схватили обложку в полёте — останавливаем анимации и продолжаем с этого места.
                    motion.travelJob?.cancel()
                    motion.travelJob = null
                    motion.tiltJob?.cancel()
                    motion.tiltJob = null
                    val ownerId = currentIdState
                    if (motion.owner != ownerId) {
                        motion.travel = 0f
                        motion.tilt = 0f
                        motion.owner = ownerId
                    }

                    // Возможности фиксируем на время жеста.
                    val canPrevious = hasPreviousState
                    val canNext = hasNextState

                    fun blocked(x: Float) = (x > 0f && !canPrevious) || (x < 0f && !canNext)
                    fun shown(x: Float) = if (blocked(x)) x * BLOCKED_FOLLOW else x * FOLLOW
                    fun unshown(t: Float) = if (blocked(t)) t / BLOCKED_FOLLOW else t / FOLLOW
                    fun commits(x: Float) = !blocked(x) && abs(x) > commitPx

                    // dx — "виртуальное" смещение пальца; обложка = shown(dx).
                    var dx = unshown(motion.travel)
                    // Вибрация порога — один раз за жест (если схватили уже за порогом — не дёргаем).
                    var thresholdAnnounced = commits(dx)
                    var rejected = false
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    fun applyDrag() {
                        val t = shown(dx)
                        motion.travel = t
                        motion.tilt = t / widthPx * tiltDegrees
                        if (!thresholdAnnounced && commits(dx)) {
                            thresholdAnnounced = true
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                        if (blocked(dx) && abs(dx) > rejectPx) rejected = true
                    }

                    // Ось выбираем по первому движению: горизонталь — наша, вертикаль — отдаём
                    // родителю (закрытие плеера свайпом вниз).
                    val slopChange = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                        val d = change.position - down.position
                        if (abs(d.x) > abs(d.y)) {
                            change.consume()
                            dx += overSlop.x
                            tracker.addPosition(change.uptimeMillis, change.position)
                            applyDrag()
                        }
                    }

                    var velocity = 0f
                    var cancelled = false
                    if (slopChange != null) {
                        val ended = drag(slopChange.id) { change ->
                            dx += change.positionChange().x
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            applyDrag()
                        }
                        if (ended) velocity = tracker.calculateVelocity().x else cancelled = true
                    }

                    // ── отпускание ───────────────────────────────────────────────────────
                    val v = velocity.coerceIn(-maxFlingPx, maxFlingPx)
                    // Куда бы палец доехал по инерции — так короткий быстрый флик тоже листает.
                    val projected = dx + v * FLING_LOOKAHEAD_S
                    val commit = !cancelled && commits(projected)
                    val travelVelocity = v * (if (blocked(dx)) BLOCKED_FOLLOW else FOLLOW)

                    if (commit) {
                        val forward = projected < 0f
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        motion.commitSwipe(
                            scope = scope,
                            forward = forward,
                            velocity = travelVelocity,
                            ownerId = ownerId,
                            pagePx = pagePx,
                            onSkip = { if (forward) onNextState() else onPreviousState() },
                            currentId = { currentIdState },
                        )
                    } else {
                        if (rejected) haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        if (motion.travel != 0f || motion.tilt != 0f) {
                            motion.settleBack(scope, travelVelocity)
                        }
                    }
                }
            }
    ) {
        slots.forEach { (item, slot) ->
            key(if (hasDuplicateIds) "${item.songId}#$slot" else item.songId.toString()) {
                CoverCard(
                    item = item,
                    slot = slot,
                    currentId = currentId,
                    motion = motion,
                    pagePx = pagePx,
                    shape = shape,
                )
            }
        }
    }
}

/**
 * Всё движение карусели. Читается только внутри graphicsLayer {} → рекомпозиций нет.
 * [owner] — id трека, которому принадлежит смещение. Как только id трека сменился, смещение
 * считается нулём в ТОМ ЖЕ кадре, где появились новые слоты: посадка без скачка.
 */
private class CoverMotion(initialOwner: Long) {
    var travel by mutableFloatStateOf(0f)
    var tilt by mutableFloatStateOf(0f)
    var owner by mutableLongStateOf(initialOwner)
    var travelJob: Job? = null
    var tiltJob: Job? = null
}

/** Отпустили без смены трека: обложка и наклон возвращаются пружиной с лёгким отскоком. */
private fun CoverMotion.settleBack(scope: CoroutineScope, velocity: Float) {
    tiltJob?.cancel()
    travelJob?.cancel()
    tiltJob = scope.launch {
        animate(initialValue = tilt, targetValue = 0f, animationSpec = BOUNCE) { value, _ ->
            tilt = value
        }
        tilt = 0f
    }
    travelJob = scope.launch {
        animate(
            initialValue = travel,
            targetValue = 0f,
            initialVelocity = velocity,
            animationSpec = BOUNCE,
        ) { value, _ -> travel = value }
        travel = 0f
    }
}

/**
 * Смена трека: обложка улетает с начальной скоростью пальца, наклон при этом возвращается в 0.
 * Трек меняем, когда обложка уже улетела; после этого ждём, пока новый id доедет до композиции.
 */
private fun CoverMotion.commitSwipe(
    scope: CoroutineScope,
    forward: Boolean,
    velocity: Float,
    ownerId: Long,
    pagePx: Float,
    onSkip: () -> Unit,
    currentId: () -> Long,
) {
    val target = if (forward) -pagePx else pagePx
    tiltJob?.cancel()
    travelJob?.cancel()
    tiltJob = scope.launch {
        animate(initialValue = tilt, targetValue = 0f, animationSpec = BOUNCE) { value, _ ->
            tilt = value
        }
        tilt = 0f
    }
    travelJob = scope.launch {
        animate(
            initialValue = travel,
            targetValue = target,
            initialVelocity = velocity,
            animationSpec = FLY,
        ) { value, _ -> travel = value }
        travel = target

        onSkip()

        val landed = withTimeoutOrNull(LAND_TIMEOUT_MS) {
            snapshotFlow(currentId).first { it != ownerId }
        } != null

        tiltJob?.cancel()
        tilt = 0f
        if (landed) {
            // Новая обложка уже стоит по центру (она была соседней) — просто обнуляем смещение.
            travel = 0f
            owner = currentId()
        } else {
            // Трек не сменился (например, нечего играть) — красиво вернуть обложку обратно.
            animate(initialValue = travel, targetValue = 0f, animationSpec = BOUNCE) { value, _ ->
                travel = value
            }
            travel = 0f
        }
    }
}

@Composable
private fun CoverCard(
    item: CoverItem,
    slot: Int,
    currentId: Long,
    motion: CoverMotion,
    pagePx: Float,
    shape: Shape,
) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val owns = motion.owner == currentId
                val travel = if (owns) motion.travel else 0f

                translationX = slot * pagePx + travel

                if (slot == 0) {
                    // Только текущая обложка наклоняется. К моменту посадки она уже прозрачна,
                    // поэтому "уехавшая" обложка не торчит у края экрана и не исчезает рывком.
                    rotationZ = if (owns) motion.tilt else 0f
                    val away = abs(travel) / pagePx
                    alpha = 1f - smoothstep((away - FADE_OUT_FROM) / (1f - FADE_OUT_FROM))
                } else {
                    // Сосед: стоит за краем и проявляется по мере приближения к центру.
                    val toward = if (slot > 0) -travel else travel
                    val reveal = smoothstep(toward / (size.width * REVEAL_FRACTION))
                    alpha = reveal
                    val s = 0.97f + 0.03f * reveal
                    scaleX = s
                    scaleY = s
                }

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

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

// ── настройки ────────────────────────────────────────────────────────────────────────────────
private val REJECT_TRAVEL: Dp = 40.dp          // сколько тянуть в "закрытую" сторону, чтобы дало вибрацию "нельзя"
private const val FOLLOW = 0.92f               // обложка идёт за пальцем почти 1:1
private const val BLOCKED_FOLLOW = 0.3f        // резинка, если с этой стороны трека нет
private const val COMMIT_FRACTION = 0.3f       // порог смены трека, доля ширины обложки
private const val REVEAL_FRACTION = 0.24f      // на каком пути соседняя обложка становится полностью видимой
private const val FADE_OUT_FROM = 0.5f         // с какой доли страницы уезжающая обложка начинает гаснуть
private const val FLING_LOOKAHEAD_S = 0.12f    // "заглядываем вперёд" по скорости флика
private const val LAND_TIMEOUT_MS = 1500L      // сколько ждать смену трека после свайпа

/** Вылет обложки: без отскока, но с начальной скоростью пальца — нет провала скорости при отпускании. */
private val FLY = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 700f,
    visibilityThreshold = 1f,
)

/** Возврат: лёгкий отскок, как в LatentJam. */
private val BOUNCE = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
