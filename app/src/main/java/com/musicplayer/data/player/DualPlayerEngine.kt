package com.musicplayer.data.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import com.musicplayer.data.TransitionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Управляет двумя MediaPlayer'ами (A и B) для плавных кроссфейд-переходов.
 *
 * Адаптировано из DualPlayerEngine (PixelPlay / ExoPlayer) под MediaPlayer.
 * — Player A — мастер (играет сейчас, показывается в UI).
 * — Player B — вспомогательный (предзагружает следующий трек).
 * После перехода A принимает роль B, старый A освобождается.
 *
 * Концепция переноса из оригинала:
 *  • Ранний своп плееров — UI обновляется как только начинается кроссфейд.
 *  • Цикл fade с шагом 16 мс (~60 fps) и кривыми из [CurveUtils.envelope].
 *  • Пересоздание «старого» плеера после каждого перехода.
 */
class DualPlayerEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionJob: Job? = null
    private var transitionRunning = false

    // Player A — активный мастер, Player B — предзагруженный следующий
    private var playerA: MediaPlayer? = null
    private var playerB: MediaPlayer? = null

    // Пользовательская громкость (0f..1f), применяется поверх fade-значений
    private var userVolume: Float = 1f

    /**
     * Вызывается когда плееры поменялись — ViewModel должен обновить UI
     * и навесить onCompletionListener на новый мастер.
     */
    var onPlayerSwapped: ((masterPlayer: MediaPlayer) -> Unit)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Устанавливает первый мастер-плеер (уже подготовленный и играющий).
     * Вызывается из ViewModel при первом старте трека.
     */
    fun setMasterPlayer(player: MediaPlayer) {
        releaseQuiet(playerA)
        releaseQuiet(playerB)
        playerB = null
        playerA = player
        playerA?.setVolume(userVolume, userVolume)
    }

    /** Обновляет пользовательскую громкость без прерывания fade. */
    fun setUserVolume(volume: Float) {
        userVolume = volume
        if (!transitionRunning) {
            playerA?.setVolume(volume, volume)
        }
    }

    fun isTransitionRunning(): Boolean = transitionRunning

    // ── Pre-buffering ─────────────────────────────────────────────────────────

    /**
     * Подготавливает Player B со следующим треком (тихо, не стартует).
     */
    fun prepareNext(uri: Uri, startPositionMs: Long = 0L) {
        try {
            releaseQuiet(playerB)
            playerB = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                setVolume(0f, 0f)
                if (startPositionMs > 0L) seekTo(startPositionMs.toInt())
                // Намеренно не стартуем — движок запустит в нужный момент
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playerB = null
        }
    }

    /**
     * Отменяет предзагруженный Player B.
     */
    fun cancelNext() {
        transitionJob?.cancel()
        transitionRunning = false
        releaseQuiet(playerB)
        playerB = null
        // Используем try-catch: playerA мог быть уже освобождён извне (ViewModel)
        try {
            playerA?.setVolume(userVolume, userVolume)
        } catch (_: Exception) { /* плеер уже освобождён — игнорируем */ }
    }

    /**
     * Уведомляет движок что мастер-плеер был освобождён извне (ViewModel).
     * Вызывать ПОСЛЕ того как ViewModel уже выпустил плеер через release().
     * Это предотвращает IllegalStateException в cancelNext() при последующих вызовах.
     */
    fun notifyMasterReleased() {
        transitionJob?.cancel()
        transitionRunning = false
        // НЕ вызываем releaseQuiet — ViewModel уже сделал release()
        playerA = null
        releaseQuiet(playerB)
        playerB = null
    }

    // ── Transition ────────────────────────────────────────────────────────────

    /**
     * Запускает кроссфейд-переход на основе [settings].
     * [onTransitionComplete] — вызывается после смены треков (обновить UI/счётчики).
     */
    fun performTransition(settings: TransitionSettings, onTransitionComplete: () -> Unit, onSwapReady: (() -> Unit)? = null) {
        val next = playerB
        if (next == null) {
            onTransitionComplete()
            return
        }

        transitionJob?.cancel()
        transitionRunning = true

        transitionJob = scope.launch {
            try {
                performOverlapTransition(settings, next, onTransitionComplete, onSwapReady)
            } catch (e: Exception) {
                e.printStackTrace()
                playerA?.setVolume(userVolume, userVolume)
                onTransitionComplete()
            } finally {
                transitionRunning = false
            }
        }
    }

    /**
     * Основная логика перекрытия двух треков с fade-кривыми.
     *
     * Переносит паттерн «ранний своп» из DualPlayerEngine (PixelPlay):
     * плееры меняются ещё ДО окончания фейда, чтобы UI сразу показал
     * следующий трек.
     */
    private suspend fun performOverlapTransition(
        settings: TransitionSettings,
        nextPlayer: MediaPlayer,
        onComplete: () -> Unit,
        onSwapReady: (() -> Unit)? = null
    ) {
        val outgoing = playerA ?: return

        // 1. Стартуем входящий трек с нулевой громкостью
        nextPlayer.setVolume(0f, 0f)
        nextPlayer.start()

        // Небольшой прогрев — гарантируем что аудио пошло
        delay(75)

        // 2. Ранний своп: меняем ссылки ДО цикла fade
        //    UI сразу видит следующий трек
        playerA = nextPlayer
        playerB = outgoing

        // 3. Уведомляем ViewModel — тот обновит метаданные, статистику, уведомление
        onPlayerSwapped?.invoke(nextPlayer)
        // Уведомляем об обмене треков — UI обновляет название/обложку немедленно
        onSwapReady?.invoke()

        // 4. Цикл fade (~16 мс шаг ≈ 60 fps)
        val duration = settings.durationMs.toLong().coerceAtLeast(300L)
        val stepMs = 16L
        var elapsed = 0L

        while (elapsed <= duration) {
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

            val volIn  = envelope(progress, settings.curveIn)  * userVolume
            val volOut = (1f - envelope(progress, settings.curveOut)) * userVolume

            try { playerA?.setVolume(volIn, volIn) } catch (_: Exception) { break }
            try { playerB?.setVolume(volOut.coerceIn(0f, userVolume),
                               volOut.coerceIn(0f, userVolume)) } catch (_: Exception) {}

            delay(stepMs)
            elapsed += stepMs
        }

        // 5. Финализация: старый плеер останавливаем и пересоздаём
        outgoing.stop()
        outgoing.release()
        playerB = null

        // Восстанавливаем полную пользовательскую громкость на новом мастере
        playerA?.setVolume(userVolume, userVolume)

        onComplete()
    }

    // ── Release ───────────────────────────────────────────────────────────────

    /** Полностью освобождает все ресурсы движка. */
    fun release() {
        transitionJob?.cancel()
        scope.cancel()
        releaseQuiet(playerA)
        releaseQuiet(playerB)
        playerA = null
        playerB = null
    }

    private fun releaseQuiet(player: MediaPlayer?) {
        try {
            if (player?.isPlaying == true) player.stop()
            player?.release()
        } catch (_: Exception) { /* игнорируем */ }
    }
}
