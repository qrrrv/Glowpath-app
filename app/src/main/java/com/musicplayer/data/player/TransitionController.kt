package com.musicplayer.data.player

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
 * Планирует кроссфейд: ждёт точку (duration - fadeMs) и запускает DualPlayerEngine.
 *
 * Длительность из настроек применяется как есть. Если до конца трека меньше,
 * чем выбранный фейд — фейд ужимается, а не отменяется. Раньше короткие
 * треки / перемотка к концу полностью глушили переход, из‑за чего слайдер
 * «длительность» выглядел мёртвым.
 */
class TransitionController(private val engine: DualPlayerEngine) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var schedulerJob: Job? = null

    fun scheduleTransition(
        currentSong: Song,
        nextSong: Song?,
        getPositionMs: () -> Long,
        getDurationMs: () -> Long,
        settings: TransitionSettings,
        onTransitionComplete: () -> Unit,
        onSwapReady: ((Song) -> Unit)? = null
    ) {
        cancelScheduler()

        if (settings.mode == TransitionMode.NONE || nextSong == null) {
            if (!engine.isTransitionRunning()) engine.cancelNext()
            return
        }

        if (!engine.isTransitionRunning()) {
            engine.cancelNext()
        }

        schedulerJob = scope.launch {
            var duration = getDurationMs()
            var waitCycles = 0
            while ((duration <= 0L) && isActive && waitCycles < 60) {
                delay(200)
                duration = getDurationMs()
                waitCycles++
            }
            if (!isActive || duration <= 0L) return@launch

            val requestedFade = settings.durationMs.toLong().coerceAtLeast(0L)
            // Оставляем 80 мс запаса до onCompletion исходящего трека.
            val maxFade = (duration - 80L).coerceAtLeast(0L)
            val fadeDur = requestedFade.coerceAtMost(maxFade)
            val appliedSettings = if (fadeDur.toInt() == settings.durationMs) {
                settings
            } else {
                settings.copy(durationMs = fadeDur.toInt())
            }

            val transitionPoint = (duration - fadeDur).coerceAtLeast(0L)
            val prebufferLead = 6_000L.coerceAtLeast(fadeDur + 1_500L)
            val prebufferPoint = (transitionPoint - prebufferLead).coerceAtLeast(0L)

            var prebuffered = false

            while (isActive) {
                val pos = getPositionMs().coerceAtLeast(0L)

                if (!prebuffered && pos >= prebufferPoint) {
                    engine.prepareNext(nextSong.uri)
                    prebuffered = true
                }

                if (pos >= transitionPoint) {
                    if (!engine.isTransitionRunning()) {
                        if (prebuffered) {
                            val ready = engine.waitUntilNextPrepared()
                            if (!ready) {
                                engine.cancelNext()
                                return@launch
                            }
                        } else {
                            engine.prepareNext(nextSong.uri)
                            val ready = engine.waitUntilNextPrepared()
                            if (!ready) {
                                engine.cancelNext()
                                return@launch
                            }
                        }
                        if (!isActive) return@launch
                        engine.performTransition(appliedSettings, onTransitionComplete) {
                            onSwapReady?.invoke(nextSong)
                        }
                    }
                    break
                }

                val remaining = transitionPoint - pos
                delay(
                    when {
                        remaining > 5_000L -> 400L
                        remaining > 1_000L -> 80L
                        else -> 16L
                    }
                )
            }
        }
    }

    fun cancelPendingTransition() {
        cancelScheduler()
        engine.cancelNext()
    }

    private fun cancelScheduler() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    fun release() {
        cancelPendingTransition()
        scope.cancel()
    }
}
