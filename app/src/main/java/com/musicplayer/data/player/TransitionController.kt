package com.musicplayer.data.player

import com.musicplayer.data.Curve
import com.musicplayer.data.Song
import com.musicplayer.data.TransitionMode
import com.musicplayer.data.TransitionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Оркестрирует переходы между треками, наблюдая за позицией воспроизведения.
 *
 * Адаптировано из TransitionController (PixelPlay / ExoPlayer) под MediaPlayer.
 * Вместо Player.Listener — polling через coroutine (подходит для MediaPlayer).
 *
 * Логика:
 *  1. Когда запускается новый трек — вызвать [scheduleTransition].
 *  2. Контроллер ждёт момента (duration - transitionDuration), затем
 *     вызывает [DualPlayerEngine.performTransition].
 *  3. При смене/перемотке/отмене — [cancelPendingTransition].
 */
class TransitionController(private val engine: DualPlayerEngine) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var schedulerJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Планирует переход для текущего трека.
     *
     * @param currentSong    Текущий трек (нужен URI для предзагрузки следующего).
     * @param nextSong       Следующий трек (null — переход не нужен).
     * @param getPositionMs  Лямбда, возвращающая текущую позицию (мс).
     * @param getDurationMs  Лямбда, возвращающая длину трека (мс).
     * @param settings       Настройки кроссфейда из [TransitionSettings].
     * @param onTransitionComplete Вызывается, когда кроссфейд завершён.
     */
    fun scheduleTransition(
        currentSong: Song,
        nextSong: Song?,
        getPositionMs: () -> Long,
        getDurationMs: () -> Long,
        settings: TransitionSettings,
        onTransitionComplete: () -> Unit,
        onSwapReady: ((Song) -> Unit)? = null
    ) {
        // Отменяем предыдущее ожидание
        cancelPendingTransition()

        // Если переход выключен или следующего трека нет — ничего не делаем
        if (settings.mode == TransitionMode.NONE || nextSong == null) {
            engine.cancelNext()
            return
        }

        schedulerJob = scope.launch {
            // Ждём пока MoviDuration станет известна
            var duration = getDurationMs()
            var waitCycles = 0
            while ((duration <= 0L) && isActive && waitCycles < 60) {
                delay(200)
                duration = getDurationMs()
                waitCycles++
            }
            if (!isActive || duration <= 0L) return@launch

            val minFade    = 500L
            val guardMs    = 150L
            val fadeDur    = settings.durationMs.toLong().coerceAtLeast(minFade)

            if (duration < fadeDur + guardMs) {
                // Трек слишком короткий для кроссфейда
                engine.cancelNext()
                return@launch
            }

            val transitionPoint = duration - fadeDur

            // Предзагружаем следующий трек заранее (за 5 с до перехода)
            val prebufferPoint = (transitionPoint - 5_000L).coerceAtLeast(0L)

            var prebuffered = false

            // Адаптивный цикл ожидания (из TransitionController оригинала)
            while (isActive) {
                val pos = getPositionMs()

                if (!prebuffered && pos >= prebufferPoint) {
                    engine.prepareNext(nextSong.uri)
                    prebuffered = true
                }

                if (pos >= transitionPoint) {
                    // Момент запуска кроссфейда
                    if (!engine.isTransitionRunning()) {
                        engine.performTransition(settings, onTransitionComplete) {
                            onSwapReady?.invoke(nextSong)
                        }
                    }
                    break
                }

                // Адаптивный сон — плотнее когда близко к точке перехода
                val remaining = transitionPoint - pos
                delay(
                    when {
                        remaining > 5_000L -> 1_000L
                        remaining > 1_000L -> 250L
                        else               -> 50L
                    }
                )
            }
        }
    }

    /**
     * Отменяет запланированный переход (при перемотке, смене трека, паузе).
     */
    fun cancelPendingTransition() {
        schedulerJob?.cancel()
        schedulerJob = null
        engine.cancelNext()
    }

    /** Освобождает ресурсы контроллера. */
    fun release() {
        cancelPendingTransition()
        scope.cancel()
    }
}
