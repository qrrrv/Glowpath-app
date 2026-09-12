package com.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.bridge.ExteraGramBridgeBatchItem
import com.musicplayer.bridge.ExteraGramBridgeContract
import com.musicplayer.bridge.ExteraGramBridgeStateStore
import com.musicplayer.data.PlayerSettings
import com.musicplayer.data.PreferencesManager
import com.musicplayer.data.RepeatMode
import com.musicplayer.data.Song
import com.musicplayer.data.TransitionMode
import com.musicplayer.data.TransitionSettings
import com.musicplayer.data.UserAlbum
import com.musicplayer.data.lyrics.LyricsRepository
import com.musicplayer.data.lyrics.LyricsState
import com.musicplayer.data.player.DualPlayerEngine
import com.musicplayer.data.player.TransitionController
import com.musicplayer.data.stats.PlaybackStatsRepository
import com.musicplayer.repository.MusicRepository
import com.musicplayer.service.MusicService
import com.musicplayer.utils.SongsFileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository   = MusicRepository(application)
    private val prefs        = PreferencesManager(application)
    val statsRepository      = PlaybackStatsRepository(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    // Debounced save job — batches rapid song-list changes into a single disk write
    private var saveSongsJob: Job? = null
    private fun debouncedSaveSongs(songs: List<Song>) {
        saveSongsJob?.cancel()
        saveSongsJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300L)  // wait 300ms for burst to settle
            // Save to fast file storage (primary) + legacy SharedPrefs (backup compatibility)
            SongsFileStorage.save(getApplication(), songs)
            prefs.saveSongs(songs)
        }
    }

    private var saveSettingsJob: Job? = null
    private fun debouncedSaveSettings(settings: PlayerSettings) {
        saveSettingsJob?.cancel()
        saveSettingsJob = viewModelScope.launch(Dispatchers.IO) {
            delay(220L)
            prefs.saveSettings(settings)
        }
    }

    // ── Splash / startup state ────────────────────────────────────────────────
    /** true когда первичная загрузка завершена и можно показывать UI */
    private val _isAppReady = MutableStateFlow(false)
    val isAppReady: StateFlow<Boolean> = _isAppReady.asStateFlow()

    /** Сколько песен уже загружено (для прогресс-бара сплеша) */
    private val _splashLoadedCount = MutableStateFlow(0)
    val splashLoadedCount: StateFlow<Int> = _splashLoadedCount.asStateFlow()

    private val _splashTotalCount = MutableStateFlow(0)
    val splashTotalCount: StateFlow<Int> = _splashTotalCount.asStateFlow()

    // ── Background lyrics prefetch ────────────────────────────────────────────
    /** ID песен для которых тексты уже скачаны в фоне */
    private val _prefetchedLyricIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(prefs.loadVolume())
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(prefs.loadSpeed())
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // ── Lyrics (auto-search via LRCLIB) ──────────────────────────────────────
    private val lyricsRepository = LyricsRepository(application)

    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    private var lyricsSearchJob: Job? = null

    private val _favourites = MutableStateFlow(prefs.loadFavourites())
    val favourites: StateFlow<Set<Long>> = _favourites.asStateFlow()

    private val _userAlbums = MutableStateFlow(prefs.loadUserAlbums())
    val userAlbums: StateFlow<List<UserAlbum>> = _userAlbums.asStateFlow()

    private val _settings = MutableStateFlow(prefs.loadSettings())
    val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    // ── Custom artwork per song ───────────────────────────────────────────────
    private val _customArtMap = MutableStateFlow(prefs.loadAllCustomArts())
    val customArtMap: StateFlow<Map<Long, android.net.Uri>> = _customArtMap.asStateFlow()

    // ── Centralised palette colour cache ──────────────────────────────────────
    // Pre-loaded from artwork URIs so SongRow never does disk I/O on-screen.
    private val _songColors = MutableStateFlow<Map<Long, androidx.compose.ui.graphics.Color>>(emptyMap())
    val songColors: StateFlow<Map<Long, androidx.compose.ui.graphics.Color>> = _songColors.asStateFlow()

    /** Load palette colours for all songs that have artwork. Idempotent — skips already-cached IDs.
     *  Processes songs in batches to avoid N recompositions for large libraries. */
    fun warmPaletteCache(songs: List<com.musicplayer.data.Song>) {
        val ctx = getApplication<android.app.Application>()
        // Filter to only uncached songs with art
        val toLoad = songs.filter { song ->
            val uri = _customArtMap.value[song.id] ?: song.albumArtUri
            uri != null && !_songColors.value.containsKey(song.id)
        }
        if (toLoad.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Process in batches of 8; pause between batches so main thread stays fluid
            toLoad.chunked(8).forEach { batch ->
                val batchColors = mutableMapOf<Long, androidx.compose.ui.graphics.Color>()
                batch.forEach { song ->
                    val uri = _customArtMap.value[song.id] ?: song.albumArtUri ?: return@forEach
                    if (_songColors.value.containsKey(song.id)) return@forEach
                    val color = com.musicplayer.data.SongColorCache.getColor(ctx, uri) ?: return@forEach
                    batchColors[song.id] = color
                }
                if (batchColors.isNotEmpty()) {
                    _songColors.value = _songColors.value + batchColors  // single emit per batch
                }
                // Yield between batches to prevent IO starvation on large libraries
                kotlinx.coroutines.delay(40L)
            }
        }
    }

    fun setCustomArt(songId: Long, uri: android.net.Uri?) {
        val updated = _customArtMap.value.toMutableMap()
        if (uri == null) {
            updated.remove(songId)
            // Invalidate cached colour so it gets re-derived from album art
            _songColors.value = _songColors.value - songId
        } else {
            try {
                getApplication<Application>().contentResolver
                    .takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            updated[songId] = uri
            com.musicplayer.data.SongColorCache.invalidate(uri)
            _songColors.value = _songColors.value - songId
            // Re-load colour for new artwork
            val song = _songs.value.firstOrNull { it.id == songId }
            if (song != null) warmPaletteCache(listOf(song))
        }
        _customArtMap.value = updated
        prefs.saveCustomArt(songId, uri?.toString())
    }

    fun getCustomArt(songId: Long): android.net.Uri? = _customArtMap.value[songId]

    fun setCustomArtForAll(uri: android.net.Uri) {
        try {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        val updated = _customArtMap.value.toMutableMap()
        _songs.value.forEach { song ->
            updated[song.id] = uri
            prefs.saveCustomArt(song.id, uri.toString())
        }
        _customArtMap.value = updated
    }

    fun resetAllCustomArt() {
        _customArtMap.value.keys.forEach { songId -> prefs.saveCustomArt(songId, null) }
        _customArtMap.value = emptyMap()
    }

    // ── Custom Title / Artist overrides ──────────────────────────────────────
    private val _customTitleMap  = MutableStateFlow(prefs.loadAllCustomTitles())
    val customTitleMap: StateFlow<Map<Long, String>> = _customTitleMap.asStateFlow()

    private val _customArtistMap = MutableStateFlow(prefs.loadAllCustomArtists())
    val customArtistMap: StateFlow<Map<Long, String>> = _customArtistMap.asStateFlow()

    fun setCustomTitle(songId: Long, title: String?) {
        val updated = _customTitleMap.value.toMutableMap()
        if (title.isNullOrBlank()) updated.remove(songId) else updated[songId] = title
        _customTitleMap.value = updated
        prefs.saveCustomTitle(songId, title)
    }

    fun setCustomArtist(songId: Long, artist: String?) {
        val updated = _customArtistMap.value.toMutableMap()
        if (artist.isNullOrBlank()) updated.remove(songId) else updated[songId] = artist
        _customArtistMap.value = updated
        prefs.saveCustomArtist(songId, artist)
    }

    // ── Orb settings ─────────────────────────────────────────────────────────
    private val _orbSettings = MutableStateFlow(prefs.loadOrbSettings())
    val orbSettings: StateFlow<com.musicplayer.data.OrbSettings> = _orbSettings.asStateFlow()

    fun updateOrbSettings(orb: com.musicplayer.data.OrbSettings) {
        _orbSettings.value = orb
        prefs.saveOrbSettings(orb)
    }

    // ── Top Bar Settings ──────────────────────────────────────────────────────
    private val _topBarSettings = MutableStateFlow(prefs.loadTopBarSettings())
    val topBarSettings: StateFlow<com.musicplayer.data.TopBarSettings> = _topBarSettings.asStateFlow()

    fun updateTopBarSettings(t: com.musicplayer.data.TopBarSettings) {
        _topBarSettings.value = t
        prefs.saveTopBarSettings(t)
    }

    // ── Custom theme colors ───────────────────────────────────────────────────
    private val _customThemeColors = MutableStateFlow(prefs.loadCustomTheme())
    val customThemeColors: StateFlow<com.musicplayer.data.CustomThemeColors> = _customThemeColors.asStateFlow()

    fun updateCustomTheme(colors: com.musicplayer.data.CustomThemeColors) {
        _customThemeColors.value = colors
        prefs.saveCustomTheme(colors)
    }

    // ── EQ state (saved / restored) ───────────────────────────────────────────
    private val _eqGains = MutableStateFlow(prefs.loadEqGains())
    val eqGains: StateFlow<List<Float>> = _eqGains.asStateFlow()

    private val _eqEnabled = MutableStateFlow(prefs.loadEqEnabled())
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _eqPresetName = MutableStateFlow(prefs.loadEqPresetName())
    val eqPresetName: StateFlow<String> = _eqPresetName.asStateFlow()

    private val _audioOutput = MutableStateFlow(prefs.loadAudioOutput())  // "STEREO", "EARPIECE", "SPEAKER"
    val audioOutput: StateFlow<String> = _audioOutput.asStateFlow()

    private val _spatialAudio = MutableStateFlow(prefs.loadSpatialAudio())
    val spatialAudio: StateFlow<Boolean> = _spatialAudio.asStateFlow()

    // ── Pre-Amp gain (-12..+12 dB, applied as volume multiplier) ─────────────
    private val _preampGain = MutableStateFlow(prefs.loadPreAmpGain())
    val preampGain: StateFlow<Float> = _preampGain.asStateFlow()

    // ── L/R Balance (-1 = full left, 0 = center, +1 = full right) ────────────
    private val _audioBalance = MutableStateFlow(prefs.loadAudioBalance())
    val audioBalance: StateFlow<Float> = _audioBalance.asStateFlow()

    /** Equalizer Android effect (null if device doesn't support it) */
    private var androidEqualizer: android.media.audiofx.Equalizer? = null
    private var androidVirtualizer: android.media.audiofx.Virtualizer? = null
    private var audioVisualizer: Visualizer? = null
    private val _audioReactiveLevel = MutableStateFlow(0f)
    val audioReactiveLevel: StateFlow<Float> = _audioReactiveLevel.asStateFlow()

    fun updateEqGains(gains: List<Float>) {
        _eqGains.value = gains
        prefs.saveEqGains(gains)
        applyEqToPlayer()
    }

    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        prefs.saveEqEnabled(enabled)
        androidEqualizer?.enabled = enabled
    }

    fun setEqPresetName(name: String) {
        _eqPresetName.value = name
        prefs.saveEqPresetName(name)
    }

    fun setAudioOutput(output: String) {
        _audioOutput.value = output
        prefs.saveAudioOutput(output)
    }

    fun setSpatialAudio(enabled: Boolean) {
        _spatialAudio.value = enabled
        prefs.saveSpatialAudio(enabled)
        androidVirtualizer?.enabled = enabled
    }

    fun setPreAmpGain(gain: Float) {
        _preampGain.value = gain.coerceIn(-12f, 12f)
        prefs.savePreAmpGain(_preampGain.value)
        applyVolumeWithBalance()
    }

    fun setAudioBalance(balance: Float) {
        _audioBalance.value = balance.coerceIn(-1f, 1f)
        prefs.saveAudioBalance(_audioBalance.value)
        applyVolumeWithBalance()
    }

    /** Apply current volume + preamp + balance to the player */
    private fun applyVolumeWithBalance() {
        val vol = _volume.value
        val preampFactor = Math.pow(10.0, _preampGain.value / 20.0).toFloat().coerceIn(0f, 4f)
        val effective = (vol * preampFactor).coerceIn(0f, 1f)
        val balance = _audioBalance.value
        val leftVol  = if (balance > 0) effective * (1f - balance) else effective
        val rightVol = if (balance < 0) effective * (1f + balance) else effective
        mediaPlayer?.setVolume(leftVol, rightVol)
        dualEngine.setUserVolume(effective)
    }

    private fun applyEqToPlayer() {
        val eq = androidEqualizer ?: return
        val gains = _eqGains.value
        val numBands = eq.numberOfBands.toInt()
        gains.take(numBands).forEachIndexed { i, gain ->
            try {
                // Android EQ uses millibels (100 * dB)
                eq.setBandLevel(i.toShort(), (gain * 100).toInt().toShort())
            } catch (_: Exception) {}
        }
    }

    private fun attachEqualizerToSession(sessionId: Int) {
        // Release old instances
        androidEqualizer?.release()
        androidVirtualizer?.release()
        audioVisualizer?.release()
        androidEqualizer = null
        androidVirtualizer = null
        audioVisualizer = null
        _audioReactiveLevel.value = 0f

        try {
            androidEqualizer = android.media.audiofx.Equalizer(0, sessionId).apply {
                enabled = _eqEnabled.value
            }
            applyEqToPlayer()
        } catch (_: Exception) {}

        try {
            androidVirtualizer = android.media.audiofx.Virtualizer(0, sessionId).apply {
                enabled = _spatialAudio.value
            }
        } catch (_: Exception) {}

        try {
            val visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null || fft.size < 8) {
                                _audioReactiveLevel.value = _audioReactiveLevel.value * 0.82f
                                return
                            }
                            val binCount = fft.size / 2
                            val resolution = (samplingRate / 2f) / binCount.coerceAtLeast(1)
                            var bass = 0f
                            var bins = 0
                            for (bin in 1 until binCount.coerceAtMost(28)) {
                                if (bin * resolution > 190f) break
                                val real = fft[bin * 2].toInt()
                                val imag = fft[bin * 2 + 1].toInt()
                                bass += sqrt((real * real + imag * imag).toFloat()) / 128f
                                bins++
                            }
                            val raw = if (bins > 0) bass / bins else 0f
                            val boosted = ((raw - 0.18f).coerceAtLeast(0f) * 1.9f).coerceIn(0f, 1f)
                            val current = _audioReactiveLevel.value
                            _audioReactiveLevel.value = if (boosted > current) {
                                boosted
                            } else {
                                current * 0.78f + boosted * 0.22f
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                enabled = true
            }
            audioVisualizer = visualizer
        } catch (_: Exception) {
            _audioReactiveLevel.value = 0f
        }
    }

    private val _bridgeQueueUris = MutableStateFlow<List<String>>(emptyList())
    val bridgeQueueUris: StateFlow<List<String>> = _bridgeQueueUris.asStateFlow()
    private var bridgeLastBatchSummary: String = ""

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var musicService: MusicService? = null

    // ── Transition Engine ────────────────────────────────────────────────────
    // Движок кроссфейда (два MediaPlayer) + контроллер расписания переходов.
    // Адаптировано из DualPlayerEngine / TransitionController (PixelPlay).
    private val dualEngine         = DualPlayerEngine(application)
    private val transitionCtrl     = TransitionController(dualEngine)

    private val _transitionSettings = MutableStateFlow(prefs.loadTransitionSettings())
    val transitionSettings: StateFlow<TransitionSettings> = _transitionSettings.asStateFlow()

    // ── Playback stats tracking ───────────────────────────────────────────────
    private var trackingStartMs: Long = 0L
    private var trackingSongId: Long  = -1L

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = (service as MusicService.MusicBinder).getService().also { svc ->
                svc.onPlayPause = { togglePlayPause() }
                svc.onPrev      = { playPrevious() }
                svc.onNext      = { playNext() }
                svc.onStop      = { stopPlaybackFromBridge(clearSong = true) }
                svc.onSeekTo    = { seekTo(it) }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    init {
        // Launch service immediately so it's ready
        val intent = Intent(application, MusicService::class.java)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        application.startService(intent)

        // Load songs in background — use fast file storage when available,
        // fall back to legacy SharedPreferences on first run (migration)
        viewModelScope.launch(Dispatchers.IO) {
            val saved: List<Song> = run {
                // Fast path: file-based JSON (10–20× faster than SharedPrefs for big libraries)
                val fromFile = SongsFileStorage.load(application)
                if (fromFile != null) {
                    fromFile
                } else {
                    // First run or old install — migrate from SharedPreferences
                    val legacy = prefs.loadSongs()
                    if (legacy.isNotEmpty()) {
                        // Persist to fast storage so next launch is instant
                        SongsFileStorage.save(application, legacy)
                    }
                    legacy
                }
            }

            _splashTotalCount.value = saved.size.coerceAtLeast(1)

            if (saved.isNotEmpty()) {
                // Load all songs at once — no per-chunk delay needed, IO thread is non-blocking
                _songs.value = saved
                _splashLoadedCount.value = saved.size
            }

            if (_songs.value.isNotEmpty()) {
                normalizeUserAlbums(_songs.value)
            }

            _splashLoadedCount.value = _splashTotalCount.value
            syncBridgeState()
            // Brief intentional pause so splash looks polished, not instant
            kotlinx.coroutines.delay(200L)
            _isAppReady.value = true

            // Defer heavy work: let user interact with the app first
            kotlinx.coroutines.delay(3000L)
            prefetchLyricsForLibrary()
        }
    }

    // ── Song management ──────────────────────────────────────────────────────

    fun addSongFromUri(uri: Uri, autoPlay: Boolean = false, onComplete: ((Song?) -> Unit)? = null) {
        viewModelScope.launch {
            val song = kotlinx.coroutines.withContext(Dispatchers.IO) {
                repository.getSongFromUri(getApplication(), uri)
            }

            var targetSong: Song? = null
            if (song != null) {
                val resolvedSong = _songs.value.firstOrNull { existing ->
                    existing.uri == uri || (song.id != 0L && existing.id == song.id)
                } ?: song
                targetSong = resolvedSong

                if (_songs.value.none { existing -> existing.uri == resolvedSong.uri || existing.id == resolvedSong.id }) {
                    val newList = _songs.value + resolvedSong
                    _songs.value = newList
                    debouncedSaveSongs(newList)
                    normalizeUserAlbums(newList)
                    syncBridgeState()
                }

                if (autoPlay) {
                    playSong(resolvedSong)
                }
            }

            onComplete?.invoke(targetSong)
        }
    }

    fun scanAndAddAllMusic(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val found    = repository.scanAllMusic(30_000L)
            val existingUris = _songs.value.map { it.uri }.toSet()
            // Also track file names of existing file:// URIs to block MediaStore duplicates
            val existingFileNames = _songs.value
                .mapNotNull { song ->
                    song.uri.scheme?.let { scheme ->
                        if (scheme == "file") song.uri.lastPathSegment else null
                    }
                }.toSet()
            val newSongs = found.filter { s ->
                s.uri !in existingUris &&
                s.uri.lastPathSegment !in existingFileNames
            }.map { s ->
                // Strip albumArtUri for songs from the MusicPlayer download folder
                if (s.folderPath?.contains("MusicPlayer") == true) s.copy(albumArtUri = null)
                else s
            }
            val newList  = (_songs.value + newSongs).sortedBy { it.title }
            _songs.value = newList
            debouncedSaveSongs(newList)
            normalizeUserAlbums(newList)
            syncBridgeState()
            onComplete?.invoke()
        }
    }

    /**
     * Adds a song downloaded from online search to the local library.
     * Avoids duplicates by checking the URI.
     */
    fun addDownloadedSong(song: Song) {
        if (_songs.value.any { it.uri == song.uri }) return
        val newList = (_songs.value + song).sortedBy { it.title }
        _songs.value = newList
        debouncedSaveSongs(newList)
        normalizeUserAlbums(newList)
        syncBridgeState()
        // Warm palette cache for the new song
        warmPaletteCache(listOf(song))
    }

    fun removeSong(song: Song) {
        val newList = _songs.value.toMutableList()
        val idx     = newList.indexOf(song)
        newList.remove(song)
        _songs.value = newList
        debouncedSaveSongs(newList)
        normalizeUserAlbums(newList)

        if (_currentSong.value?.id == song.id) {
            stopPlayback()
            _currentSong.value  = null
            _currentIndex.value = -1
        } else if (_currentIndex.value > idx) {
            _currentIndex.value = _currentIndex.value - 1
        }
        syncBridgeState()
    }

    fun searchLibrary(query: String, limit: Int = 8): List<Song> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()

        return _songs.value.mapNotNull { song ->
            val title = song.title.lowercase()
            val artist = song.artist.lowercase()
            val album = song.album.lowercase()
            val combined = "$title $artist $album"
            val score = when {
                title == needle -> 320
                "$artist - $title" == needle -> 300
                title.startsWith(needle) -> 260
                artist.startsWith(needle) -> 220
                title.contains(needle) -> 180
                artist.contains(needle) -> 160
                album.contains(needle) -> 120
                combined.contains(needle) -> 90
                else -> 0
            }
            if (score > 0) song to score else null
        }
            .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.lowercase() })
            .take(limit)
            .map { it.first }
    }

    fun playByQuery(query: String): Song? {
        val match = searchLibrary(query, limit = 1).firstOrNull() ?: return null
        playSong(match)
        return match
    }

    private fun setBridgeQueueFromSongs(songs: List<Song>) {
        _bridgeQueueUris.value = songs
            .distinctBy { it.uri.toString() }
            .map { it.uri.toString() }
    }

    private fun resolveBridgeQueueSongs(): List<Song> {
        val uris = _bridgeQueueUris.value
        if (uris.isEmpty()) return emptyList()
        return uris.mapNotNull { uri ->
            _songs.value.firstOrNull { it.uri.toString() == uri }
        }
    }

    private fun bridgeQueueRelative(step: Int): Song? {
        val current = _currentSong.value ?: return null
        val queue = resolveBridgeQueueSongs()
        if (queue.isEmpty()) return null
        val currentIdx = queue.indexOfFirst { it.uri == current.uri }
        if (currentIdx < 0) return null
        val targetIdx = currentIdx + step
        if (targetIdx !in queue.indices) return null
        return queue[targetIdx]
    }

    private fun clearBridgeQueue() {
        _bridgeQueueUris.value = emptyList()
    }

    fun buildActiveQueueSnapshot(): List<Song> {
        return resolveBridgeQueueSongs().takeIf { it.isNotEmpty() } ?: getSortedSongs()
    }

    fun playFromActiveQueue(song: Song) {
        val preserveBridgeQueue = resolveBridgeQueueSongs().isNotEmpty()
        playSong(song, preserveBridgeQueue = preserveBridgeQueue)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val queue = songs.distinctBy { it.uri.toString() }
        if (queue.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, queue.lastIndex)
        setBridgeQueueFromSongs(queue)
        playSong(queue[safeIndex], preserveBridgeQueue = true)
    }

    fun playQueueFrom(song: Song, songs: List<Song>) {
        val queue = songs.distinctBy { it.uri.toString() }
        if (queue.isEmpty()) return
        val startIndex = queue.indexOfFirst { it.uri == song.uri }
            .takeIf { it >= 0 }
            ?: 0
        playQueue(queue, startIndex)
    }

    fun playAlbum(albumSongs: List<Song>, startSong: Song? = null) {
        if (startSong == null) {
            playQueue(albumSongs, 0)
            return
        }
        playQueueFrom(startSong, albumSongs)
    }

    fun resolveSongsByIds(songIds: Collection<Long>): List<Song> {
        if (songIds.isEmpty()) return emptyList()
        val songMap = _songs.value.associateBy { it.id }
        return songIds.mapNotNull(songMap::get)
    }

    fun resolveUserAlbumSongs(albumId: String): List<Song> {
        val album = _userAlbums.value.firstOrNull { it.id == albumId } ?: return emptyList()
        return resolveSongsByIds(album.songIds)
    }

    fun createUserAlbum(
        name: String,
        songIds: Collection<Long> = emptyList(),
        coverSongId: Long? = null,
        customCoverUri: String? = null,
        motionCoverUri: String? = null,
        description: String = "",
        isPinned: Boolean = false
    ): UserAlbum? {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return null
        val validSongIds = normalizeSongIds(songIds)
        val resolvedCoverSongId = resolveAlbumCover(validSongIds, coverSongId)
        val normalizedCustomCoverUri = normalizeOptionalUri(customCoverUri)
        val normalizedMotionCoverUri: String? = null
        persistReadableUri(normalizedCustomCoverUri)
        persistReadableUri(normalizedMotionCoverUri)
        val now = System.currentTimeMillis()
        val album = UserAlbum(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            songIds = validSongIds,
            coverSongId = resolvedCoverSongId,
            customCoverUri = normalizedCustomCoverUri,
            motionCoverUri = normalizedMotionCoverUri,
            description = description.trim(),
            isPinned = isPinned,
            updatedAt = now,
            createdAt = now
        )
        val updated = (_userAlbums.value + album).sortedBy { it.name.lowercase() }
        persistUserAlbums(updated)
        return album
    }

    fun renameUserAlbum(albumId: String, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        val now = System.currentTimeMillis()
        val updated = _userAlbums.value.map { album ->
            if (album.id == albumId) album.copy(name = trimmedName, updatedAt = now) else album
        }
        persistUserAlbums(updated)
    }

    fun deleteUserAlbum(albumId: String) {
        persistUserAlbums(_userAlbums.value.filterNot { it.id == albumId })
    }

    fun replaceUserAlbumSongs(albumId: String, songIds: Collection<Long>, coverSongId: Long? = null) {
        val normalizedSongIds = normalizeSongIds(songIds)
        val now = System.currentTimeMillis()
        val updated = _userAlbums.value.map { album ->
            if (album.id != albumId) return@map album
            album.copy(
                songIds = normalizedSongIds,
                coverSongId = resolveAlbumCover(normalizedSongIds, coverSongId ?: album.coverSongId),
                updatedAt = now
            )
        }
        persistUserAlbums(updated)
    }

    fun addSongsToUserAlbum(albumId: String, songIds: Collection<Long>) {
        if (songIds.isEmpty()) return
        val now = System.currentTimeMillis()
        val updated = _userAlbums.value.map { album ->
            if (album.id != albumId) return@map album
            val mergedSongIds = normalizeSongIds(album.songIds + songIds)
            album.copy(
                songIds = mergedSongIds,
                coverSongId = resolveAlbumCover(mergedSongIds, album.coverSongId),
                updatedAt = now
            )
        }
        persistUserAlbums(updated)
    }

    fun removeSongsFromUserAlbum(albumId: String, songIds: Collection<Long>) {
        if (songIds.isEmpty()) return
        val removedIds = songIds.toSet()
        val now = System.currentTimeMillis()
        val updated = _userAlbums.value.map { album ->
            if (album.id != albumId) return@map album
            val remainingSongIds = album.songIds.filterNot { it in removedIds }
            album.copy(
                songIds = remainingSongIds,
                coverSongId = resolveAlbumCover(remainingSongIds, album.coverSongId),
                updatedAt = now
            )
        }
        persistUserAlbums(updated)
    }

    fun setUserAlbumCover(albumId: String, songId: Long?) {
        val now = System.currentTimeMillis()
        val updated = _userAlbums.value.map { album ->
            if (album.id != albumId) return@map album
            album.copy(
                coverSongId = resolveAlbumCover(album.songIds, songId),
                updatedAt = now
            )
        }
        persistUserAlbums(updated)
    }

    fun updateUserAlbumDetails(
        albumId: String,
        name: String,
        description: String,
        songIds: Collection<Long>,
        coverSongId: Long? = null,
        customCoverUri: String? = null,
        motionCoverUri: String? = null,
        isPinned: Boolean = false
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        val normalizedSongIds = normalizeSongIds(songIds)
        val normalizedCustomCoverUri = normalizeOptionalUri(customCoverUri)
        val normalizedMotionCoverUri: String? = null
        persistReadableUri(normalizedCustomCoverUri)
        persistReadableUri(normalizedMotionCoverUri)
        val now = System.currentTimeMillis()
        val updated = _userAlbums.value.map { album ->
            if (album.id != albumId) return@map album
            album.copy(
                name = trimmedName,
                description = description.trim(),
                songIds = normalizedSongIds,
                coverSongId = resolveAlbumCover(normalizedSongIds, coverSongId ?: album.coverSongId),
                customCoverUri = normalizedCustomCoverUri,
                motionCoverUri = normalizedMotionCoverUri,
                isPinned = isPinned,
                updatedAt = now
            )
        }
        persistUserAlbums(updated)
    }

    private fun rememberBridgeBatchSummary(summary: String) {
        bridgeLastBatchSummary = summary
    }

    fun importBridgeBatch(
        items: List<ExteraGramBridgeBatchItem>,
        mode: String = ExteraGramBridgeContract.TYPE_IMPORT_BATCH,
        onComplete: ((List<Song>) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            if (items.isEmpty()) {
                onComplete?.invoke(emptyList())
                return@launch
            }

            var library = _songs.value
            var changed = false
            val resolved = mutableListOf<Song>()

            items.forEach { item ->
                var targetSong: Song? = null
                val parsedUri = item.uri.takeIf { it.isNotBlank() }?.let { raw ->
                    runCatching { Uri.parse(raw) }.getOrNull()
                }

                if (parsedUri != null) {
                    val importedSong = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        repository.getSongFromUri(getApplication(), parsedUri)
                    }
                    if (importedSong != null) {
                        val existing = library.firstOrNull { existingSong ->
                            existingSong.uri == parsedUri || (importedSong.id != 0L && existingSong.id == importedSong.id)
                        }
                        targetSong = existing ?: importedSong
                        if (existing == null) {
                            library = library + importedSong
                            changed = true
                        }
                    }
                }

                if (targetSong == null) {
                    val query = listOf(item.artist, item.title, item.query)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .trim()
                    if (query.isNotBlank()) {
                        targetSong = searchLibrary(query, limit = 1).firstOrNull()
                    }
                }

                val resolvedSong = targetSong
                if (resolvedSong != null && resolved.none { it.uri == resolvedSong.uri }) {
                    resolved += resolvedSong
                }
            }

            if (changed) {
                _songs.value = library
                debouncedSaveSongs(library)
            }

            val unique = resolved.distinctBy { it.uri.toString() }
            if (unique.isNotEmpty()) {
                rememberBridgeBatchSummary("Telegram batch: ${unique.size} трек(ов)")
                when (mode) {
                    ExteraGramBridgeContract.TYPE_IMPORT_BATCH -> {
                        setBridgeQueueFromSongs(unique)
                    }

                    ExteraGramBridgeContract.TYPE_IMPORT_BATCH_AND_PLAY -> {
                        setBridgeQueueFromSongs(unique)
                        playSong(unique.first(), preserveBridgeQueue = true)
                    }

                    ExteraGramBridgeContract.TYPE_QUEUE_BATCH_APPEND -> {
                        val merged = (resolveBridgeQueueSongs() + unique)
                            .distinctBy { it.uri.toString() }
                        setBridgeQueueFromSongs(merged)
                        if (_currentSong.value == null) {
                            playSong(merged.first(), preserveBridgeQueue = true)
                        }
                    }
                }
            }

            syncBridgeState()
            onComplete?.invoke(unique)
        }
    }

    fun stopPlaybackFromBridge(clearSong: Boolean = true) {
        stopPlayback()
        if (clearSong) {
            _currentSong.value = null
            _currentIndex.value = -1
            _duration.value = 0L
            clearBridgeQueue()
        }
        syncBridgeState()
    }

    // ── Playback ─────────────────────────────────────────────────────────────

    /** Returns the song list sorted according to current settings (mirrors HomeScreen logic). */
    private fun getSortedSongs(): List<Song> {
        val raw = _songs.value
        return when (_settings.value.sortOrder) {
            com.musicplayer.data.SortOrder.TITLE      -> raw.sortedBy { it.title.lowercase() }
            com.musicplayer.data.SortOrder.ARTIST     -> raw.sortedBy { it.artist.lowercase() }
            com.musicplayer.data.SortOrder.ALBUM      -> raw.sortedBy { it.album.lowercase() }
            com.musicplayer.data.SortOrder.DURATION   -> raw.sortedBy { it.duration }
            com.musicplayer.data.SortOrder.DATE_ADDED -> raw.sortedByDescending { it.id }
        }
    }

    fun playSong(song: Song, preserveBridgeQueue: Boolean = false) {
        if (!preserveBridgeQueue) {
            clearBridgeQueue()
        }
        val sorted = getSortedSongs()
        _currentIndex.value = sorted.indexOf(song).takeIf { it >= 0 } ?: _songs.value.indexOf(song)
        startPlayback(song)
    }

    private fun notificationArtUri(song: Song): Uri? =
        _customArtMap.value[song.id] ?: song.albumArtUri

    private fun pushNotificationState(forceMetadata: Boolean) {
        val song = _currentSong.value ?: return
        val player = mediaPlayer
        val positionMs = player?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: _currentPosition.value
        val durationMs = player?.duration?.toLong()?.coerceAtLeast(0L) ?: _duration.value
        val isPlaying = _isPlaying.value
        val playbackSpeed = if (isPlaying) _playbackSpeed.value else 0f

        if (forceMetadata) {
            musicService?.updateNotification(
                title = song.title,
                artist = song.artist,
                albumArtUri = notificationArtUri(song),
                isPlaying = isPlaying,
                durationMs = durationMs,
                positionMs = positionMs,
                playbackSpeed = playbackSpeed
            )
        } else {
            musicService?.updatePlaybackState(
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed
            )
        }
    }

    private fun startPlayback(song: Song) {
        flushStatsIfNeeded()  // record previous song before switching
        // Сначала останавливаем текущий плеер (это вызовет notifyMasterReleased в dualEngine),
        // а ПОТОМ отменяем переход — иначе cancelNext() падал на уже освобождённом плеере
        stopPlayback()
        transitionCtrl.cancelPendingTransition()
        try {
            val player = MediaPlayer().apply {
                setDataSource(getApplication(), song.uri)
                prepare()
                start()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = PlaybackParams().apply { speed = _playbackSpeed.value }
                }
                setOnCompletionListener { onSongComplete() }
            }
            mediaPlayer        = player
            applyVolumeWithBalance()   // apply volume + preamp + L/R balance to the new player
            _currentSong.value = song
            _isPlaying.value   = true
            _duration.value    = player.duration.toLong()
            trackingStartMs    = System.currentTimeMillis()
            trackingSongId     = song.id
            startProgressTracking()
            loadLyricsForSong(song)
            pushNotificationState(forceMetadata = true)
            syncBridgeState()

            // Передаём плеер в движок и планируем переход
            dualEngine.setMasterPlayer(player)
            dualEngine.onPlayerSwapped = { newMaster ->
                // Вызывается когда кроссфейд поменял плееры местами —
                // обновляем ссылку на активный MediaPlayer
                mediaPlayer = newMaster
                // Re-attach EQ effects to new session
                try { attachEqualizerToSession(newMaster.audioSessionId) } catch (_: Exception) {}
            }
            // Attach equalizer effects to the new MediaPlayer's audio session
            try { attachEqualizerToSession(player.audioSessionId) } catch (_: Exception) {}
            scheduleNextTransition(song)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Планирует кроссфейд к следующему треку (если включено). */
    private fun scheduleNextTransition(currentSong: Song) {
        val settings = _transitionSettings.value
        if (settings.mode == TransitionMode.NONE) return

        val list = getSortedSongs()
        val idx  = list.indexOf(currentSong).takeIf { it >= 0 } ?: _currentIndex.value
        val nextSong = bridgeQueueRelative(1) ?: when (_settings.value.repeatMode) {
            RepeatMode.ONE  -> currentSong
            RepeatMode.ALL  -> list.getOrNull((idx + 1) % list.size)
            RepeatMode.NONE -> list.getOrNull(idx + 1)
        } ?: return

        transitionCtrl.scheduleTransition(
            currentSong         = currentSong,
            nextSong            = nextSong,
            getPositionMs       = { mediaPlayer?.currentPosition?.toLong() ?: 0L },
            getDurationMs       = { mediaPlayer?.duration?.toLong() ?: 0L },
            settings            = settings,
            onTransitionComplete = {
                // Кроссфейд завершён — финализируем (метаданные уже обновлены при onPlayerSwapped)
                flushStatsIfNeeded()
                _duration.value = mediaPlayer?.duration?.toLong() ?: 0L
                // Планируем следующий переход
                scheduleNextTransition(nextSong)
            },
            onSwapReady = { swappedToSong ->
                // Вызывается в начале кроссфейда — обновляем UI немедленно
                // чтобы название/обложка соответствовали уже играющему треку
                _currentSong.value  = swappedToSong
                _currentIndex.value = list.indexOf(swappedToSong)
                trackingStartMs     = System.currentTimeMillis()
                trackingSongId      = swappedToSong.id
                _currentPosition.value = 0L
                loadLyricsForSong(swappedToSong)
                pushNotificationState(forceMetadata = true)
                syncBridgeState()
            }
        )
    }

    /** Обновляет настройки кроссфейда (вызывается из EditTransitionScreen). */
    fun updateTransitionSettings(settings: TransitionSettings) {
        _transitionSettings.value = settings
        prefs.saveTransitionSettings(settings)
        // Переплановываем для текущего трека
        _currentSong.value?.let { scheduleNextTransition(it) }
    }

    /** Records elapsed time for the current song to the stats repository. */
    private fun flushStatsIfNeeded() {
        val songId = trackingSongId
        val start  = trackingStartMs
        if (songId < 0 || start <= 0L) return
        val elapsed = System.currentTimeMillis() - start
        val song    = _currentSong.value
        val maxDur  = song?.duration ?: Long.MAX_VALUE
        // Only record if listened at least 10 seconds
        val toRecord = elapsed.coerceAtMost(maxDur)
        if (toRecord >= 10_000L) {
            viewModelScope.launch(Dispatchers.IO) {
                statsRepository.recordPlayback(songId, toRecord)
            }
        }
        trackingStartMs = 0L
        trackingSongId  = -1L
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        val song   = _currentSong.value ?: return
        if (_isPlaying.value) {
            flushStatsIfNeeded()
            player.pause()
            _isPlaying.value = false
            stopProgressTracking()
            pushNotificationState(forceMetadata = true)
        } else {
            player.start()
            trackingStartMs = System.currentTimeMillis()
            trackingSongId  = song.id
            _isPlaying.value = true
            startProgressTracking()
            pushNotificationState(forceMetadata = true)
        }
        syncBridgeState()
    }

    fun seekTo(position: Long) {
        // Перемотка прерывает запланированный переход
        transitionCtrl.cancelPendingTransition()
        mediaPlayer?.seekTo(position.toInt())
        _currentPosition.value = position
        // Перепланируем переход с новой позиции
        _currentSong.value?.let { scheduleNextTransition(it) }
        pushNotificationState(forceMetadata = false)
        syncBridgeState(position)
    }

    fun setVolume(volume: Float) {
        _volume.value = volume
        prefs.saveVolume(volume)
        applyVolumeWithBalance()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        prefs.saveSpeed(speed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { player ->
                try {
                    player.playbackParams = PlaybackParams().apply { this.speed = speed }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        pushNotificationState(forceMetadata = false)
    }

    // ── Lyrics ────────────────────────────────────────────────────────────────

    /**
     * Auto-search lyrics for a song. Called when song starts or user opens LyricsScreen.
     * Searches in order: memory cache → disk cache → local .lrc → LRCLIB API
     */
    fun loadLyricsForSong(song: Song) {
        lyricsSearchJob?.cancel()
        // Показываем Loading только если нет кэша (чтобы не мелькало)
        _lyricsState.value = LyricsState.Loading
        lyricsSearchJob = viewModelScope.launch {
            try {
                // getLyrics уже проверяет memory cache → disk cache → API
                // Если текст уже скачан в фоне — вернётся мгновенно
                val result = lyricsRepository.getLyrics(song)
                _lyricsState.value = if (result != null && result.isValid()) {
                    LyricsState.Found(result)
                } else {
                    LyricsState.NotFound
                }
            } catch (e: Exception) {
                _lyricsState.value = LyricsState.Error(e.message ?: "Ошибка поиска")
            }
        }
    }

    /**
     * Фоновая загрузка текстов для всей библиотеки.
     * Работает тихо, не влияет на текущее воспроизведение.
     * Запускается один раз при старте, пропускает уже скачанные.
     */
    fun prefetchLyricsForLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = _songs.value
            if (songs.isEmpty()) return@launch

            // Проверяем какие уже есть на диске
            val toFetch = songs.filter { song ->
                val cacheFile = java.io.File(
                    getApplication<android.app.Application>().filesDir,
                    "lyrics/${song.id}.json"
                )
                !cacheFile.exists()
            }

            if (toFetch.isEmpty()) return@launch

            // Грузим по одной с задержкой чтобы не нагружать сеть
            toFetch.forEach { song ->
                try {
                    val result = lyricsRepository.getLyrics(song)
                    if (result != null && result.isValid()) {
                        _prefetchedLyricIds.value = _prefetchedLyricIds.value + song.id
                    }
                } catch (_: Exception) {}
                // Задержка между запросами — уважаем rate limit API
                kotlinx.coroutines.delay(600L)
            }
        }
    }

    /**
     * Force refresh lyrics from API (ignore all caches).
     */
    fun refreshLyrics() {
        val song = _currentSong.value ?: return
        lyricsSearchJob?.cancel()
        _lyricsState.value = LyricsState.Loading
        lyricsSearchJob = viewModelScope.launch {
            try {
                lyricsRepository.clearCache(song.id)
                val result = lyricsRepository.getLyrics(song, forceRefresh = true)
                _lyricsState.value = if (result != null && result.isValid()) {
                    LyricsState.Found(result)
                } else {
                    LyricsState.NotFound
                }
            } catch (e: Exception) {
                _lyricsState.value = LyricsState.Error(e.message ?: "Ошибка поиска")
            }
        }
    }

    // ── Favourites ────────────────────────────────────────────────────────────

    fun toggleFavourite(songId: Long) {
        val current = _favourites.value.toMutableSet()
        if (songId in current) current.remove(songId) else current.add(songId)
        _favourites.value = current
        prefs.saveFavourites(current)
    }

    fun isFavourite(songId: Long) = songId in _favourites.value

    private fun normalizeSongIds(songIds: Collection<Long>): List<Long> {
        val validSongIds = _songs.value.asSequence().map { it.id }.toHashSet()
        return songIds
            .distinct()
            .filter { it in validSongIds }
    }

    private fun resolveAlbumCover(songIds: List<Long>, preferredCoverSongId: Long?): Long? {
        return preferredCoverSongId
            ?.takeIf { it in songIds }
            ?: songIds.firstOrNull()
    }

    private fun normalizeOptionalUri(uriString: String?): String? =
        uriString?.trim()?.takeIf { it.isNotBlank() }

    private fun persistReadableUri(uriString: String?) {
        val uri = uriString?.let(android.net.Uri::parse) ?: return
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }

    private fun normalizeUserAlbums(library: List<Song>) {
        val validSongIds = library.asSequence().map { it.id }.toHashSet()
        val normalized = _userAlbums.value.map { album ->
            val normalizedSongIds = album.songIds
                .distinct()
                .filter { it in validSongIds }
            album.copy(
                songIds = normalizedSongIds,
                coverSongId = album.coverSongId?.takeIf { it in normalizedSongIds } ?: normalizedSongIds.firstOrNull(),
                motionCoverUri = null
            )
        }
        if (normalized != _userAlbums.value) {
            persistUserAlbums(normalized)
        }
    }

    private fun persistUserAlbums(albums: List<UserAlbum>) {
        val normalized = albums
            .distinctBy { it.id }
            .map { album ->
                val normalizedSongIds = normalizeSongIds(album.songIds)
                val normalizedCustomCoverUri = normalizeOptionalUri(album.customCoverUri)
                val normalizedMotionCoverUri: String? = null
                persistReadableUri(normalizedCustomCoverUri)
                persistReadableUri(normalizedMotionCoverUri)
                album.copy(
                    name = album.name.trim().ifBlank { "Мой плейлист" },
                    description = album.description.trim(),
                    songIds = normalizedSongIds,
                    coverSongId = resolveAlbumCover(normalizedSongIds, album.coverSongId),
                    customCoverUri = normalizedCustomCoverUri,
                    motionCoverUri = normalizedMotionCoverUri,
                    updatedAt = album.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            }
            .sortedWith(
                compareByDescending<UserAlbum> { it.isPinned }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.createdAt }
            )
        _userAlbums.value = normalized
        prefs.saveUserAlbums(normalized)
    }

    fun playNext() {
        bridgeQueueRelative(1)?.let { nextSong ->
            val sorted = getSortedSongs()
            _currentIndex.value = sorted.indexOf(nextSong).takeIf { it >= 0 } ?: _songs.value.indexOf(nextSong)
            playSong(nextSong, preserveBridgeQueue = true)
            return
        }

        val visibleSongs = getVisibleSongs()
        val sorted = if (_settings.value.shuffleEnabled) {
            visibleSongs  // shuffle будет применен через getNextShuffleIndex
        } else {
            when (_settings.value.sortOrder) {
                com.musicplayer.data.SortOrder.TITLE      -> visibleSongs.sortedBy { it.displayTitle().lowercase() }
                com.musicplayer.data.SortOrder.ARTIST     -> visibleSongs.sortedBy { it.displayArtist().lowercase() }
                com.musicplayer.data.SortOrder.ALBUM      -> visibleSongs.sortedBy { it.album.lowercase() }
                com.musicplayer.data.SortOrder.DURATION   -> visibleSongs.sortedBy { it.duration }
                com.musicplayer.data.SortOrder.DATE_ADDED -> visibleSongs.sortedByDescending { it.id }
            }
        }

        if (sorted.isEmpty()) return

        val nextIdx = if (_settings.value.shuffleEnabled) {
            getNextShuffleIndex(_currentIndex.value, sorted.size)
        } else {
            (_currentIndex.value + 1) % sorted.size
        }

        _currentIndex.value = nextIdx
        startPlayback(sorted[nextIdx])
    }

    fun playPrevious() {
        if (_currentPosition.value > 3000L) { seekTo(0L); return }
        bridgeQueueRelative(-1)?.let { prevSong ->
            val sorted = getSortedSongs()
            _currentIndex.value = sorted.indexOf(prevSong).takeIf { it >= 0 } ?: _songs.value.indexOf(prevSong)
            playSong(prevSong, preserveBridgeQueue = true)
            return
        }

        val visibleSongs = getVisibleSongs()
        val sorted = when (_settings.value.sortOrder) {
            com.musicplayer.data.SortOrder.TITLE      -> visibleSongs.sortedBy { it.displayTitle().lowercase() }
            com.musicplayer.data.SortOrder.ARTIST     -> visibleSongs.sortedBy { it.displayArtist().lowercase() }
            com.musicplayer.data.SortOrder.ALBUM      -> visibleSongs.sortedBy { it.album.lowercase() }
            com.musicplayer.data.SortOrder.DURATION   -> visibleSongs.sortedBy { it.duration }
            com.musicplayer.data.SortOrder.DATE_ADDED -> visibleSongs.sortedByDescending { it.id }
        }

        if (sorted.isEmpty()) return
        val prevIdx = if (_currentIndex.value <= 0) sorted.size - 1 else _currentIndex.value - 1
        _currentIndex.value = prevIdx
        startPlayback(sorted[prevIdx])
    }

    fun toggleShuffle() {
        val updated = _settings.value.copy(shuffleEnabled = !_settings.value.shuffleEnabled)
        _settings.value = updated
        prefs.saveSettings(updated)
        // Сбросить состояние shuffle при переключении
        if (updated.shuffleEnabled) {
            resetShuffleState()
        }
    }

    fun toggleRepeat() {
        val next = when (_settings.value.repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL  -> RepeatMode.ONE
            RepeatMode.ONE  -> RepeatMode.NONE
        }
        val updated = _settings.value.copy(repeatMode = next)
        _settings.value = updated
        prefs.saveSettings(updated)
        mediaPlayer?.isLooping = (next == RepeatMode.ONE)
    }

    fun updateSettings(settings: PlayerSettings) {
        _settings.value = settings
        debouncedSaveSettings(settings)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun onSongComplete() {
        flushStatsIfNeeded()
        when (_settings.value.repeatMode) {
            RepeatMode.ONE  -> { mediaPlayer?.seekTo(0); mediaPlayer?.start(); startProgressTracking() }
            RepeatMode.ALL  -> playNext()
            RepeatMode.NONE -> {
                if (bridgeQueueRelative(1) != null || _currentIndex.value < _songs.value.size - 1) {
                    playNext()
                } else {
                    _isPlaying.value       = false
                    _currentPosition.value = 0
                    stopProgressTracking()
                    pushNotificationState(forceMetadata = true)
                }
            }
        }
        syncBridgeState()
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = viewModelScope.launch {
            var lastBridgeSyncMs = 0L
            while (true) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        val current = it.currentPosition.toLong()
                        _currentPosition.value = current
                        val now = System.currentTimeMillis()
                        if (now - lastBridgeSyncMs >= 1000L) {
                            pushNotificationState(forceMetadata = false)
                            syncBridgeState(current)
                            lastBridgeSyncMs = now
                        }
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracking() { progressJob?.cancel(); progressJob = null }

    private fun stopPlayback() {
        flushStatsIfNeeded()
        stopProgressTracking()           // ← сначала останавливаем корутину,
        val player = mediaPlayer         //   чтобы она не обратилась к уже
        mediaPlayer = null               //   освобождённому MediaPlayer
        _isPlaying.value = false
        _currentPosition.value = 0
        // Уведомляем DualEngine ПЕРЕД release(), пока объект ещё технически жив,
        // но ПОСЛЕ того как обнулили mediaPlayer — чтобы cancelNext() не пытался
        // вызывать setVolume() на уже мёртвом плеере в следующем startPlayback()
        dualEngine.notifyMasterReleased()
        player?.let {
            // Обнуляем слушатель ДО release() — предотвращает ложное срабатывание
            // onCompletion после освобождения плеера (был краш при удалении трека)
            try { it.setOnCompletionListener(null) } catch (_: Exception) {}
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        try { audioVisualizer?.release() } catch (_: Exception) {}
        audioVisualizer = null
        _audioReactiveLevel.value = 0f
        _duration.value = 0L
        syncBridgeState()
    }

    private fun syncBridgeState(positionOverride: Long? = null) {
        val song = _currentSong.value
        ExteraGramBridgeStateStore.syncPlayback(
            context = getApplication(),
            title = song?.title.orEmpty(),
            artist = song?.artist.orEmpty(),
            album = song?.album.orEmpty(),
            isPlaying = _isPlaying.value,
            hasSong = song != null,
            durationMs = _duration.value,
            positionMs = positionOverride ?: _currentPosition.value,
            currentUri = song?.uri?.toString().orEmpty(),
            librarySize = _songs.value.size,
            bridgeQueueSize = resolveBridgeQueueSongs().size,
            lastBatchSummary = bridgeLastBatchSummary,
        )
    }

    // ── Sleep Timer ───────────────────────────────────────────────────────────
    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun startSleepTimer(
        hours: Int,
        minutes: Int,
        fadeOut: Boolean,
        stopAfterTrack: Boolean,
        action: SleepTimerAction
    ) {
        sleepTimerJob?.cancel()
        val totalSeconds = hours * 3600 + minutes * 60
        if (totalSeconds <= 0 && !stopAfterTrack) return

        _sleepTimerState.value = SleepTimerState(
            isRunning = true,
            remainingSeconds = totalSeconds,
            fadeOut = fadeOut,
            stopAfterTrack = stopAfterTrack,
            action = action
        )

        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                // Fade out: gradually lower volume in the last 30 seconds
                if (fadeOut && remaining in 1..30) {
                    val volumeFraction = remaining / 30f
                    runCatching { mediaPlayer?.setVolume(volumeFraction, volumeFraction) }
                }
                _sleepTimerState.value = _sleepTimerState.value.copy(remainingSeconds = remaining)
            }

            if (stopAfterTrack) {
                // Wait until the current track ends (poll isPlaying)
                while (_isPlaying.value) {
                    delay(500L)
                }
            }

            // Execute the action
            when (action) {
                SleepTimerAction.PAUSE -> {
                    if (_isPlaying.value) togglePlayPause()
                }
                SleepTimerAction.STOP -> {
                    stopPlayback()
                }
            }
            // Restore volume
            if (fadeOut) {
                runCatching { mediaPlayer?.setVolume(1f, 1f) }
            }
            _sleepTimerState.value = SleepTimerState()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        // Restore volume in case fade-out was active
        runCatching { mediaPlayer?.setVolume(1f, 1f) }
        _sleepTimerState.value = SleepTimerState()
    }

    // ── Управление скрытыми треками ───────────────────────────────────────────

    /** Скрыть трек из библиотеки (без удаления файла) */
    fun hideSong(song: Song) {
        val updated = _songs.value.map {
            if (it.id == song.id) it.copy(isHidden = true) else it
        }
        _songs.value = updated
        debouncedSaveSongs(updated)
    }

    /** Показать скрытый трек */
    fun unhideSong(song: Song) {
        val updated = _songs.value.map {
            if (it.id == song.id) it.copy(isHidden = false) else it
        }
        _songs.value = updated
        debouncedSaveSongs(updated)
    }

    /** Получить список скрытых треков */
    fun getHiddenSongs(): List<Song> = _songs.value.filter { it.isHidden }

    /** Получить видимые треки (с учетом настроек showHiddenTracks) */
    fun getVisibleSongs(): List<Song> {
        val allSongs = _songs.value
        return if (_settings.value.showHiddenTracks) {
            allSongs
        } else {
            allSongs.filter { !it.isHidden }
        }
    }

    // ── Редактирование метаданных ─────────────────────────────────────────────

    /** Обновить локальные метаданные трека */
    fun updateSongMetadata(
        song: Song,
        newTitle: String? = null,
        newArtist: String? = null,
        newAlbumArtUri: Uri? = null
    ) {
        val updated = _songs.value.map {
            if (it.id == song.id) {
                it.copy(
                    customTitle = newTitle?.ifBlank { null },
                    customArtist = newArtist?.ifBlank { null },
                    customAlbumArtUri = newAlbumArtUri
                )
            } else {
                it
            }
        }
        _songs.value = updated
        debouncedSaveSongs(updated)

        // Обновить UI если это текущий трек
        if (_currentSong.value?.id == song.id) {
            _currentSong.value = updated.find { it.id == song.id }
            pushNotificationState(forceMetadata = true)
        }
    }

    /** Сбросить кастомные метаданные трека */
    fun resetSongMetadata(song: Song) {
        updateSongMetadata(song, newTitle = "", newArtist = "", newAlbumArtUri = null)
    }

    // ── Улучшенный shuffle ────────────────────────────────────────────────────

    private var shuffleHistory = mutableListOf<Int>()  // история воспроизведенных индексов
    private var shuffleRemaining = mutableListOf<Int>()  // оставшиеся треки для shuffle

    /** Генерирует следующий случайный индекс с учетом истории */
    private fun getNextShuffleIndex(currentIndex: Int, listSize: Int): Int {
        if (listSize <= 1) return 0

        // Если список закончился, перезапускаем с новой перемешкой
        if (shuffleRemaining.isEmpty()) {
            shuffleRemaining = (0 until listSize).toMutableList()
            // Убираем текущий трек из нового цикла
            shuffleRemaining.remove(currentIndex)
            shuffleRemaining.shuffle()
        }

        return shuffleRemaining.removeAt(0)
    }

    /** Сброс состояния shuffle (вызывается при изменении библиотеки) */
    fun resetShuffleState() {
        shuffleHistory.clear()
        shuffleRemaining.clear()
    }

    override fun onCleared() {
        saveSettingsJob?.cancel()
        sleepTimerJob?.cancel()
        prefs.saveSettings(_settings.value)
        super.onCleared()
        transitionCtrl.release()
        dualEngine.release()
        stopPlayback()
        try { androidEqualizer?.release() } catch (_: Exception) {}
        try { androidVirtualizer?.release() } catch (_: Exception) {}
        try { audioVisualizer?.release() } catch (_: Exception) {}
        try { getApplication<Application>().unbindService(serviceConnection) } catch (e: Exception) {}
    }
}
