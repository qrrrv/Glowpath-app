package com.musicplayer.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.data.DownloadState
import com.musicplayer.data.OnlineAlbumSheetState
import com.musicplayer.data.OnlineAlbumSummary
import com.musicplayer.data.OnlineSong
import com.musicplayer.data.OnlineSearchState
import com.musicplayer.data.Song
import com.musicplayer.repository.HitmosRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

class OnlineSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)

    private val _searchState = MutableStateFlow<OnlineSearchState>(OnlineSearchState.Idle)
    val searchState: StateFlow<OnlineSearchState> = _searchState.asStateFlow()

    private val _albumSheetState = MutableStateFlow<OnlineAlbumSheetState>(OnlineAlbumSheetState.Hidden)
    val albumSheetState: StateFlow<OnlineAlbumSheetState> = _albumSheetState.asStateFlow()

    private val _bridgeQuery = MutableStateFlow("")
    val bridgeQuery: StateFlow<String> = _bridgeQuery.asStateFlow()

    // downloadId -> OnlineSong
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // Finished downloads: url -> local Song (ready to add to library)
    private val _readySongs = MutableStateFlow<Map<String, Song>>(emptyMap())
    val readySongs: StateFlow<Map<String, Song>> = _readySongs.asStateFlow()

    private val _searchHistory = MutableStateFlow(loadSearchHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private var searchJob: Job? = null
    private var albumJob: Job? = null
    private val pendingDownloads = mutableMapOf<Long, OnlineSong>() // dmId -> song

    // Broadcast receiver for DownloadManager completion
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val song = pendingDownloads[id] ?: return
            pendingDownloads.remove(id)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(id)
            val cursor = dm.query(query)
            if (cursor.moveToFirst()) {
                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val uriCol    = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val status = cursor.getInt(statusCol)
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val localUri = cursor.getString(uriCol)
                    // ── Heavy IO work moved off main thread ────────────────────
                    viewModelScope.launch(Dispatchers.IO) {
                        val file = File(Uri.parse(localUri).path ?: "")
                        setDownloadState(song.downloadUrl, DownloadState.Done(file.absolutePath))
                        val localSong = buildLocalSong(context, file, song)
                        if (localSong != null) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                _readySongs.value = _readySongs.value + (song.downloadUrl to localSong)
                            }
                        }
                    }
                } else {
                    setDownloadState(song.downloadUrl, DownloadState.Err("Ошибка загрузки"))
                }
            }
            cursor.close()
        }
    }

    init {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(downloadReceiver, filter)
        }
        // Poll progress for active downloads
        viewModelScope.launch {
            while (true) {
                delay(800)
                if (pendingDownloads.isNotEmpty()) pollDownloadProgress()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        albumJob?.cancel()
        runCatching { getApplication<Application>().unregisterReceiver(downloadReceiver) }
    }

    fun search(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            _searchState.value = OnlineSearchState.Idle
            return
        }
        rememberSearchQuery(cleanQuery)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchState.value = OnlineSearchState.Loading
            _albumSheetState.value = OnlineAlbumSheetState.Hidden
            try {
                val payload = HitmosRepository.search(cleanQuery)
                _searchState.value = if (payload.songs.isEmpty() && payload.albums.isEmpty()) {
                    OnlineSearchState.Empty(cleanQuery)
                } else {
                    OnlineSearchState.Success(
                        songs = payload.songs,
                        albums = payload.albums
                    )
                }
            } catch (e: Exception) {
                _searchState.value = OnlineSearchState.Error(mapErrorMessage(e))
            }
        }
    }

    fun openFromBridge(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        _bridgeQuery.value = clean
        search(clean)
    }

    fun openAlbum(album: OnlineAlbumSummary) {
        albumJob?.cancel()
        _albumSheetState.value = OnlineAlbumSheetState.Loading(album)
        albumJob = viewModelScope.launch {
            try {
                val detail = HitmosRepository.fetchAlbumDetail(album.albumUrl)
                _albumSheetState.value = OnlineAlbumSheetState.Success(album, detail)
            } catch (e: Exception) {
                _albumSheetState.value = OnlineAlbumSheetState.Error(
                    album = album,
                    message = mapErrorMessage(e)
                )
            }
        }
    }

    fun retryAlbum() {
        val album = when (val state = _albumSheetState.value) {
            is OnlineAlbumSheetState.Loading -> state.album
            is OnlineAlbumSheetState.Success -> state.album
            is OnlineAlbumSheetState.Error -> state.album
            OnlineAlbumSheetState.Hidden -> null
        } ?: return
        openAlbum(album)
    }

    fun closeAlbum() {
        albumJob?.cancel()
        _albumSheetState.value = OnlineAlbumSheetState.Hidden
    }

    fun startDownload(song: OnlineSong) {
        val ctx = getApplication<Application>()
        if (_downloadStates.value[song.downloadUrl] is DownloadState.Pending ||
            _downloadStates.value[song.downloadUrl] is DownloadState.Progress
        ) return

        setDownloadState(song.downloadUrl, DownloadState.Pending)

        val safeFilename = buildFilename(song)
        val referer = runCatching {
            val parsed = Uri.parse(song.downloadUrl)
            "${parsed.scheme ?: "https"}://${parsed.host ?: "eu.hitmoz.com"}/"
        }.getOrDefault("https://eu.hitmoz.com/")
        val request = DownloadManager.Request(Uri.parse(song.downloadUrl)).apply {
            setTitle("${song.artist} – ${song.title}".trim(' ', '–'))
            setDescription("Загрузка музыки...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "MusicPlayer/$safeFilename")
            addRequestHeader("User-Agent", "Mozilla/5.0 (Android 14) AppleWebKit/537.36")
            addRequestHeader("Referer", referer)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val dmId = dm.enqueue(request)
        pendingDownloads[dmId] = song
    }

    private fun pollDownloadProgress() {
        val ctx = getApplication<Application>()
        val dm  = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val ids = pendingDownloads.keys.toLongArray()
        if (ids.isEmpty()) return

        val query  = DownloadManager.Query().setFilterById(*ids)
        val cursor = dm.query(query)
        while (cursor.moveToNext()) {
            val idCol        = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol     = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val statusCol    = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

            val dmId      = cursor.getLong(idCol)
            val song      = pendingDownloads[dmId] ?: continue
            val status    = cursor.getInt(statusCol)
            val downloaded = cursor.getLong(downloadedCol)
            val total      = cursor.getLong(totalCol)

            when (status) {
                DownloadManager.STATUS_RUNNING -> {
                    val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    setDownloadState(song.downloadUrl, DownloadState.Progress(pct))
                }
                DownloadManager.STATUS_FAILED -> {
                    pendingDownloads.remove(dmId)
                    setDownloadState(song.downloadUrl, DownloadState.Err("Ошибка загрузки"))
                }
            }
        }
        cursor.close()
    }

    private fun setDownloadState(url: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value + (url to state)
    }

    fun clearError(url: String) {
        val updated = _downloadStates.value.toMutableMap()
        updated.remove(url)
        _downloadStates.value = updated
    }

    fun removeHistoryQuery(query: String) {
        val updated = _searchHistory.value.filterNot { it == query }
        _searchHistory.value = updated
        saveSearchHistory(updated)
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        saveSearchHistory(emptyList())
    }

    private fun buildFilename(song: OnlineSong): String {
        val raw = if (song.artist.isNotBlank()) "${song.artist} - ${song.title}" else song.title
        return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100) + ".mp3"
    }

    private fun rememberSearchQuery(query: String) {
        val updated = buildList {
            add(query)
            _searchHistory.value.forEach { if (!it.equals(query, ignoreCase = true)) add(it) }
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
        _searchHistory.value = updated
        saveSearchHistory(updated)
    }

    private fun saveSearchHistory(items: List<String>) {
        val raw = JSONArray().apply { items.forEach { put(it) } }.toString()
        prefs.edit().putString("online_search_history", raw).apply()
    }

    private fun loadSearchHistory(): List<String> {
        val raw = prefs.getString("online_search_history", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).trim().takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildLocalSong(ctx: Context, file: File, online: OnlineSong): Song? {
        if (!file.exists()) return null
        // ── Strip embedded ID3 album art from downloaded file ─────────────────
        stripId3AlbumArt(file)
        val uri = Uri.fromFile(file)
        return Song(
            id          = file.absolutePath.hashCode().toLong() and 0xFFFFFFFFL,
            title       = online.title.ifBlank { file.nameWithoutExtension },
            artist      = online.artist,
            album       = "Загруженное",
            duration    = parseDurationMs(online.duration),
            uri         = uri,
            albumArtUri = null,
            folderPath  = file.parent
        )
    }

    /**
     * Removes the APIC (attached picture) frame from an ID3v2 tag in an MP3 file.
     * This prevents MediaStore from indexing the muzmo.ru watermark art.
     */
    private fun stripId3AlbumArt(file: File) {
        try {
            val bytes = file.readBytes()
            if (bytes.size < 10) return
            // Check for ID3v2 header: "ID3"
            if (bytes[0] != 0x49.toByte() || bytes[1] != 0x44.toByte() || bytes[2] != 0x33.toByte()) return

            val id3Size = ((bytes[6].toInt() and 0x7F) shl 21) or
                          ((bytes[7].toInt() and 0x7F) shl 14) or
                          ((bytes[8].toInt() and 0x7F) shl 7)  or
                           (bytes[9].toInt() and 0x7F)
            val tagEnd = 10 + id3Size
            if (tagEnd > bytes.size) return

            val result = mutableListOf<Byte>()
            // Copy ID3 header (first 10 bytes) — we'll fix size later
            result.addAll(bytes.slice(0..9))

            var pos = 10
            // Optional extended header (ID3v2.3/2.4)
            val extendedHeader = bytes[5].toInt() and 0x40 != 0
            if (extendedHeader && pos + 4 <= tagEnd) {
                val extSize = ((bytes[pos].toInt() and 0xFF) shl 24) or
                              ((bytes[pos+1].toInt() and 0xFF) shl 16) or
                              ((bytes[pos+2].toInt() and 0xFF) shl 8) or
                               (bytes[pos+3].toInt() and 0xFF)
                result.addAll(bytes.slice(pos until minOf(pos + extSize, tagEnd)))
                pos += extSize
            }

            var foundApic = false
            while (pos + 10 <= tagEnd) {
                val frameId = String(bytes, pos, 4, Charsets.ISO_8859_1)
                if (frameId == "\u0000\u0000\u0000\u0000") break  // padding
                val frameSize = ((bytes[pos+4].toInt() and 0xFF) shl 24) or
                                ((bytes[pos+5].toInt() and 0xFF) shl 16) or
                                ((bytes[pos+6].toInt() and 0xFF) shl 8) or
                                 (bytes[pos+7].toInt() and 0xFF)
                val frameTotal = 10 + frameSize
                if (frameId == "APIC") {
                    foundApic = true  // skip this frame — don't copy
                } else {
                    result.addAll(bytes.slice(pos until minOf(pos + frameTotal, tagEnd)))
                }
                pos += frameTotal
            }

            if (!foundApic) return  // Nothing to strip, don't rewrite

            // Fill rest with padding to keep the same tag size
            while (result.size < tagEnd) result.add(0x00)
            // Copy audio data after ID3 tag
            if (tagEnd < bytes.size) result.addAll(bytes.slice(tagEnd until bytes.size))

            file.writeBytes(result.toByteArray())
        } catch (_: Exception) { /* If anything fails, just skip — don't crash */ }
    }

    private fun parseDurationMs(dur: String): Long {
        return try {
            val parts = dur.trim().split(":")
            when (parts.size) {
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                else -> 0L
            }
        } catch (_: Exception) { 0L }
    }

    private fun mapErrorMessage(error: Exception): String {
        return when {
            error.message?.contains("timeout", true) == true -> "Время ожидания истекло. Проверьте соединение."
            error.message?.contains("Unable to resolve", true) == true -> "Нет подключения к интернету."
            error.message?.contains("403", true) == true -> "Сервис временно ограничил доступ. Попробуйте позже."
            else -> "Ошибка: ${error.message}"
        }
    }
}
