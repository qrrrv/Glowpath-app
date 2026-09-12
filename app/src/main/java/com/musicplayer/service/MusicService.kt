package com.musicplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.musicplayer.MainActivity
import com.musicplayer.R

class MusicService : Service() {

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.musicplayer.ACTION_PLAY_PAUSE"
        const val ACTION_PREV       = "com.musicplayer.ACTION_PREV"
        const val ACTION_NEXT       = "com.musicplayer.ACTION_NEXT"
        const val ACTION_STOP       = "com.musicplayer.ACTION_STOP"
        const val CHANNEL_ID        = "music_player_channel"
        const val NOTIFICATION_ID   = 1
    }

    private val binder = MusicBinder()
    private lateinit var mediaSession: MediaSessionCompat

    // Callbacks injected by ViewModel
    var onPlayPause: (() -> Unit)? = null
    var onPrev:      (() -> Unit)? = null
    var onNext:      (() -> Unit)? = null
    var onStop:      (() -> Unit)? = null
    var onSeekTo:    ((Long) -> Unit)? = null

    // Настройки наушников (управляются через ViewModel)
    var pauseOnHeadphoneDisconnect: Boolean = true
    var resumeOnHeadphoneConnect: Boolean = false

    private var lastTitle: String = ""
    private var lastArtist: String = ""
    private var lastAlbumArtUri: Uri? = null
    private var lastAlbumArtBitmap: Bitmap? = null
    private var lastIsPlaying: Boolean = false
    private var lastDurationMs: Long = 0L
    private var lastPositionMs: Long = 0L
    private var lastPlaybackSpeed: Float = 1f
    private var isForegroundStarted: Boolean = false

    // Обработчик отключения наушников
    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Наушники отключены
                if (pauseOnHeadphoneDisconnect && lastIsPlaying) {
                    onPlayPause?.invoke()
                }
            }
        }
    }

    // Обработчик подключения наушников
    private val headsetPlugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                when (state) {
                    0 -> { // Наушники отключены
                        if (pauseOnHeadphoneDisconnect && lastIsPlaying) {
                            onPlayPause?.invoke()
                        }
                    }
                    1 -> { // Наушники подключены
                        if (resumeOnHeadphoneConnect && !lastIsPlaying) {
                            onPlayPause?.invoke()
                        }
                    }
                }
            }
        }
    }

    private val mediaButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> onPlayPause?.invoke()
                ACTION_PREV       -> onPrev?.invoke()
                ACTION_NEXT       -> onNext?.invoke()
                ACTION_STOP       -> onStop?.invoke() ?: stopForegroundPlayback()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // MediaSession с callback-ами — именно это позволяет шторке управлять плеером
        mediaSession = MediaSessionCompat(this, "MusicPlayer").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
            )
            setSessionActivity(makeActivityIntent())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()         { onPlayPause?.invoke() }
                override fun onPause()        { onPlayPause?.invoke() }
                override fun onSkipToNext()   { onNext?.invoke() }
                override fun onSkipToPrevious() { onPrev?.invoke() }
                override fun onSeekTo(pos: Long) { onSeekTo?.invoke(pos) }
                override fun onStop()         { onStop?.invoke() ?: stopForegroundPlayback() }
            })
            isActive = true
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_PREV)
            addAction(ACTION_NEXT)
            addAction(ACTION_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaButtonReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(mediaButtonReceiver, filter)
        }

        // Регистрируем обработчики наушников
        val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyAudioReceiver, noisyFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(noisyAudioReceiver, noisyFilter)
        }

        val headsetFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(headsetPlugReceiver, headsetFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(headsetPlugReceiver, headsetFilter)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        try { unregisterReceiver(mediaButtonReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(noisyAudioReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(headsetPlugReceiver) } catch (_: Exception) {}
    }

    // ── Pending intents ───────────────────────────────────────────────────────

    private fun makePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun makeActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── Public API called by ViewModel ────────────────────────────────────────

    fun updateNotification(
        title: String,
        artist: String,
        albumArtUri: Uri?,
        isPlaying: Boolean,
        durationMs: Long = 0L,
        positionMs: Long = 0L,
        playbackSpeed: Float = 1f
    ) {
        lastTitle = title
        lastArtist = artist
        lastIsPlaying = isPlaying
        lastDurationMs = durationMs.coerceAtLeast(0L)
        lastPositionMs = positionMs.coerceAtLeast(0L)
        lastPlaybackSpeed = playbackSpeed

        if (lastAlbumArtUri != albumArtUri) {
            lastAlbumArtUri = albumArtUri
            lastAlbumArtBitmap = loadNotificationBitmap(albumArtUri)
        }

        updateSessionMetadata()
        updatePlaybackState(
            positionMs = lastPositionMs,
            durationMs = lastDurationMs,
            isPlaying = lastIsPlaying,
            playbackSpeed = lastPlaybackSpeed
        )
        pushNotification()
    }

    fun updatePlaybackState(
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        playbackSpeed: Float = 1f
    ) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        lastDurationMs = durationMs.coerceAtLeast(0L)
        lastIsPlaying = isPlaying
        lastPlaybackSpeed = playbackSpeed

        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SEEK_TO

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    lastPositionMs,
                    if (isPlaying) playbackSpeed else 0f,
                    SystemClock.elapsedRealtime()
                )
                .build()
        )

        if (lastTitle.isNotBlank()) {
            pushNotification()
        }
    }

    private fun updateSessionMetadata() {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, lastTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, lastArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, lastDurationMs)

        lastAlbumArtBitmap?.let { art ->
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
        }

        mediaSession.setMetadata(metadata.build())
    }

    private fun pushNotification() {
        val notification = buildNotification(lastTitle, lastArtist, lastIsPlaying)
        val nm = getSystemService(NotificationManager::class.java)
        if (lastIsPlaying) {
            if (!isForegroundStarted) {
                startForeground(NOTIFICATION_ID, notification)
                isForegroundStarted = true
            } else {
                nm.notify(NOTIFICATION_ID, notification)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isForegroundStarted) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForegroundStarted = false
            }
            nm.notify(NOTIFICATION_ID, notification)
        } else {
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    fun startForegroundPlayback(title: String, artist: String) {
        lastTitle = title
        lastArtist = artist
        lastIsPlaying = true
        isForegroundStarted = true
        startForeground(NOTIFICATION_ID, buildNotification(title, artist, true))
    }

    fun stopForegroundPlayback() {
        isForegroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ── Notification builder ──────────────────────────────────────────────────

    private fun buildNotification(
        title: String,
        artist: String,
        isPlaying: Boolean
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(artist)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setLargeIcon(lastAlbumArtBitmap)
        .setContentIntent(makeActivityIntent())
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setOngoing(isPlaying)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setProgress(
            lastDurationMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            lastPositionMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            false
        )
        .addAction(
            android.R.drawable.ic_media_previous,
            "Предыдущая",
            makePendingIntent(ACTION_PREV, 1)
        )
        .addAction(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Пауза" else "Играть",
            makePendingIntent(ACTION_PLAY_PAUSE, 2)
        )
        .addAction(
            android.R.drawable.ic_media_next,
            "Следующая",
            makePendingIntent(ACTION_NEXT, 3)
        )
        .setStyle(
            MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        .build()

    private fun loadNotificationBitmap(uri: Uri?): Bitmap? {
        if (uri == null) return null
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Воспроизведение музыки",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Управление воспроизведением"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
