package com.musicplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.data.AlbumPageState
import com.musicplayer.data.AlbumTrack
import com.musicplayer.data.Song
import com.musicplayer.repository.AlbumRepository
import com.musicplayer.repository.OfflineAudio
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class AlbumViewModel(application: Application) : AndroidViewModel(application) {

    sealed class TrackTask {
        abstract val playback: Boolean

        data class Resolving(override val playback: Boolean) : TrackTask()
        data class Downloading(val progress: Int, override val playback: Boolean) : TrackTask()
    }

    private val _state = MutableStateFlow<AlbumPageState>(AlbumPageState.Idle)
    val state: StateFlow<AlbumPageState> = _state.asStateFlow()

    private val _tasks = MutableStateFlow<Map<Long, TrackTask>>(emptyMap())
    val tasks: StateFlow<Map<Long, TrackTask>> = _tasks.asStateFlow()

    private val _savedTracks = MutableStateFlow<Set<Long>>(emptySet())
    val savedTracks: StateFlow<Set<Long>> = _savedTracks.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var job: Job? = null
    private var currentAlbumId: Long = 0L
    private var albumCover: String = ""
    private var albumTitle: String = ""

    fun open(albumId: Long) {
        if (albumId <= 0L) return
        if (albumId == currentAlbumId && _state.value is AlbumPageState.Success) return
        currentAlbumId = albumId
        load(albumId)
    }

    fun retry() {
        if (currentAlbumId > 0L) load(currentAlbumId)
    }

    fun clearMessage() {
        _message.value = null
    }

    /**
     * Resolves the track on Hitmos, stores the audio in the app cache and hands a local
     * file to the player. Streaming the remote URL would block the media player and
     * fail on Hitmos headers, which is what looked like an endless spinner.
     */
    fun play(track: AlbumTrack, onReady: (Song) -> Unit) {
        if (_tasks.value.containsKey(track.id)) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            setTask(track.id, TrackTask.Resolving(playback = true))
            try {
                val file = resolveToFile(track, OfflineAudio.cacheFile(context, track), playback = true)
                if (file != null) {
                    onReady(OfflineAudio.buildSong(file, track, albumCover, albumTitle))
                } else {
                    _message.value = "Не удалось найти файл для «" + track.title + "»"
                }
            } finally {
                clearTask(track.id)
            }
        }
    }

    /** Same audio, but kept in the app storage and added to the library. */
    fun download(track: AlbumTrack, onSaved: (Song) -> Unit) {
        if (_tasks.value.containsKey(track.id)) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            setTask(track.id, TrackTask.Resolving(playback = false))
            try {
                val file = resolveToFile(track, OfflineAudio.libraryFile(context, track), playback = false)
                if (file != null) {
                    _savedTracks.update { it + track.id }
                    onSaved(OfflineAudio.buildSong(file, track, albumCover, albumTitle))
                    _message.value = "«" + track.title + "» сохранён в приложении"
                } else {
                    _message.value = "Не удалось скачать «" + track.title + "»"
                }
            } finally {
                clearTask(track.id)
            }
        }
    }

    private suspend fun resolveToFile(track: AlbumTrack, target: File, playback: Boolean): File? {
        if (target.exists() && target.length() > 64 * 1024L) return target
        val online = OfflineAudio.resolve(track) ?: return null
        val ok = OfflineAudio.download(online, target) { percent ->
            setTask(track.id, TrackTask.Downloading(percent, playback = playback))
        }
        return if (ok) target else null
    }

    private fun setTask(trackId: Long, task: TrackTask) {
        _tasks.update { it + (trackId to task) }
    }

    private fun clearTask(trackId: Long) {
        _tasks.update { it - trackId }
    }

    private fun load(albumId: Long) {
        job?.cancel()
        _state.value = AlbumPageState.Loading(albumId)
        job = viewModelScope.launch {
            val album = runCatching { AlbumRepository.load(albumId) }.getOrNull()
            coroutineContext.ensureActive()
            if (album == null) {
                _state.value = AlbumPageState.Empty(albumId)
            } else {
                albumCover = album.coverUrl
                albumTitle = album.title
                _state.value = AlbumPageState.Success(album)
            }
        }
    }
}
