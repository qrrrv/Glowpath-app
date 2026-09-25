package com.musicplayer.data.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.musicplayer.data.TransitionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Два MediaPlayer'а (A — мастер, B — следующий) для кроссфейда.
 *
 * Важно:
 *  • Длительность фейда считается по wall-clock, а не по сумме delay(16),
 *    иначе реальное перекрытие всегда короче настройки.
 *  • Перед стартом B снимаем onCompletion с A — иначе конец текущего трека
 *    вызывает playNext() и убивает переход.
 *  • Оба плеера получают USAGE_MEDIA, чтобы система не глушила один из них.
 */
class DualPlayerEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionJob: Job? = null
    private var transitionRunning = false

    private var playerA: MediaPlayer? = null
    private var playerB: MediaPlayer? = null

    private var userVolumeL: Float = 1f
    private var userVolumeR: Float = 1f
    private var playbackSpeed: Float = 1f
    private var nextPrepared: Boolean = false

    var onPlayerSwapped: ((masterPlayer: MediaPlayer) -> Unit)? = null

    fun setMasterPlayer(player: MediaPlayer) {
        releaseQuiet(playerA)
        releaseQuiet(playerB)
        playerB = null
        nextPrepared = false
        playerA = player
        applyVolume(player, 1f)
    }

    fun setUserVolume(volume: Float) {
        setUserVolume(volume, volume)
    }

    fun setUserVolume(left: Float, right: Float) {
        userVolumeL = left.coerceIn(0f, 1f)
        userVolumeR = right.coerceIn(0f, 1f)
        if (!transitionRunning) {
            playerA?.let { applyVolume(it, 1f) }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.25f, 3f)
        applySpeed(playerA)
        applySpeed(playerB)
    }

    fun isTransitionRunning(): Boolean = transitionRunning

    fun isNextPrepared(): Boolean = nextPrepared && playerB != null

    fun pause() {
        try { playerA?.pause() } catch (_: Exception) {}
        try { playerB?.pause() } catch (_: Exception) {}
    }

    fun resume() {
        try { playerA?.start() } catch (_: Exception) {}
        if (transitionRunning) {
            try { playerB?.start() } catch (_: Exception) {}
        }
    }

    fun prepareNext(uri: Uri, startPositionMs: Long = 0L) {
        try {
            releaseQuiet(playerB)
            nextPrepared = false
            val player = MediaPlayer()
            configurePlayer(player)
            player.setVolume(0f, 0f)
            player.setOnPreparedListener {
                try {
                    if (startPositionMs > 0L) it.seekTo(startPositionMs.toInt())
                    applySpeed(it)
                    it.setVolume(0f, 0f)
                    nextPrepared = playerB === it
                } catch (_: Exception) {
                    nextPrepared = false
                }
            }
            player.setOnErrorListener { mp, _, _ ->
                if (playerB === mp) {
                    nextPrepared = false
                    playerB = null
                }
                releaseQuiet(mp)
                true
            }
            player.setDataSource(context, uri)
            playerB = player
            player.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            nextPrepared = false
            playerB = null
        }
    }

    suspend fun waitUntilNextPrepared(timeoutMs: Long = 2_000L): Boolean {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (isNextPrepared()) return true
            delay(40)
        }
        return isNextPrepared()
    }

    fun cancelNext() {
        transitionJob?.cancel()
        transitionRunning = false
        nextPrepared = false
        releaseQuiet(playerB)
        playerB = null
        playerA?.let { applyVolume(it, 1f) }
    }

    fun notifyMasterReleased() {
        transitionJob?.cancel()
        transitionRunning = false
        nextPrepared = false
        playerA = null
        releaseQuiet(playerB)
        playerB = null
    }

    fun performTransition(
        settings: TransitionSettings,
        onTransitionComplete: () -> Unit,
        onSwapReady: (() -> Unit)? = null
    ) {
        val next = playerB
        if (next == null || !nextPrepared) {
            return
        }

        transitionJob?.cancel()
        transitionRunning = true

        transitionJob = scope.launch {
            try {
                performOverlapTransition(settings, next, onTransitionComplete, onSwapReady)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                playerA?.let { applyVolume(it, 1f) }
            } finally {
                transitionRunning = false
            }
        }
    }

    private suspend fun performOverlapTransition(
        settings: TransitionSettings,
        nextPlayer: MediaPlayer,
        onComplete: () -> Unit,
        onSwapReady: (() -> Unit)?
    ) {
        val outgoing = playerA ?: return

        // Снимаем completion у исходящего — иначе конец трека вызовет playNext()
        // и пересоздаст плеер посреди кроссфейда.
        try { outgoing.setOnCompletionListener(null) } catch (_: Exception) {}
        try { nextPlayer.setOnCompletionListener(null) } catch (_: Exception) {}

        applySpeed(nextPlayer)
        nextPlayer.setVolume(0f, 0f)
        nextPlayer.start()

        val fadeMs = settings.durationMs.toLong().coerceAtLeast(0L)

        if (fadeMs <= 0L) {
            // Gapless cut: следующий уже играет, старый глушим сразу.
            playerA = nextPlayer
            playerB = outgoing
            applyVolume(nextPlayer, 1f)
            onPlayerSwapped?.invoke(nextPlayer)
            onSwapReady?.invoke()
            releaseQuiet(outgoing)
            playerB = null
            nextPrepared = false
            onComplete()
            return
        }

        delay(16)

        playerA = nextPlayer
        playerB = outgoing
        onPlayerSwapped?.invoke(nextPlayer)
        onSwapReady?.invoke()

        val startAt = SystemClock.uptimeMillis()
        val stepMs = 16L

        while (true) {
            val elapsed = SystemClock.uptimeMillis() - startAt
            val progress = (elapsed.toFloat() / fadeMs).coerceIn(0f, 1f)

            val volIn = fadeIn(progress, settings.curveIn)
            val volOut = fadeOut(progress, settings.curveOut)

            try {
                applyVolume(playerA, volIn)
            } catch (_: Exception) {
                break
            }
            try {
                applyVolume(playerB, volOut)
            } catch (_: Exception) {
            }

            if (elapsed >= fadeMs) break
            delay(stepMs)
        }

        applyVolume(playerA, 1f)
        releaseQuiet(outgoing)
        playerB = null
        nextPrepared = false
        onComplete()
    }

    fun release() {
        transitionJob?.cancel()
        scope.cancel()
        releaseQuiet(playerA)
        releaseQuiet(playerB)
        playerA = null
        playerB = null
        nextPrepared = false
    }

    private fun configurePlayer(player: MediaPlayer) {
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
        } catch (_: Exception) {}
    }

    private fun applyVolume(player: MediaPlayer?, envelope: Float) {
        if (player == null) return
        val left = (userVolumeL * envelope).coerceIn(0f, 1f)
        val right = (userVolumeR * envelope).coerceIn(0f, 1f)
        player.setVolume(left, right)
    }

    private fun applySpeed(player: MediaPlayer?) {
        if (player == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val playing = try { player.isPlaying } catch (_: Exception) { false }
            player.playbackParams = PlaybackParams().apply { speed = playbackSpeed }
            // На части прошивок setPlaybackParams ставит плеер на паузу.
            if (playing && !player.isPlaying) {
                player.start()
            }
        } catch (_: Exception) {}
    }

    private fun releaseQuiet(player: MediaPlayer?) {
        if (player == null) return
        try { player.setOnCompletionListener(null) } catch (_: Exception) {}
        try { player.setOnPreparedListener(null) } catch (_: Exception) {}
        try { player.setOnErrorListener(null) } catch (_: Exception) {}
        try { if (player.isPlaying) player.stop() } catch (_: Exception) {}
        try { player.release() } catch (_: Exception) {}
    }
}
