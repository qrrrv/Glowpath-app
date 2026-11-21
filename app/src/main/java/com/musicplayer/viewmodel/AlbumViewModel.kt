package com.musicplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.data.AlbumPageState
import com.musicplayer.data.AlbumTrack
import com.musicplayer.data.OnlineSong
import com.musicplayer.repository.AlbumRepository
import com.musicplayer.repository.HitmosRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlbumViewModel : ViewModel() {

    private val _state = MutableStateFlow<AlbumPageState>(AlbumPageState.Idle)
    val state: StateFlow<AlbumPageState> = _state.asStateFlow()

    /** Track id currently being resolved against Hitmos. */
    private val _resolvingTrack = MutableStateFlow<Long?>(null)
    val resolvingTrack: StateFlow<Long?> = _resolvingTrack.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var job: Job? = null
    private var currentAlbumId: Long = 0L

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

    fun play(track: AlbumTrack, onReady: (OnlineSong) -> Unit) {
        if (_resolvingTrack.value != null) return
        _resolvingTrack.value = track.id
        viewModelScope.launch {
            val song = runCatching {
                HitmosRepository.findBestMatch(track.artist, track.title, track.durationSeconds)
            }.getOrNull()
            coroutineContext.ensureActive()
            _resolvingTrack.value = null
            if (song == null) {
                _message.value = "Не удалось найти файл для «" + track.title + "»"
            } else {
                onReady(song)
            }
        }
    }

    private fun load(albumId: Long) {
        job?.cancel()
        _state.value = AlbumPageState.Loading(albumId)
        job = viewModelScope.launch {
            val album = runCatching { AlbumRepository.load(albumId) }.getOrNull()
            coroutineContext.ensureActive()
            _state.value = if (album == null) {
                AlbumPageState.Empty(albumId)
            } else {
                AlbumPageState.Success(album)
            }
        }
    }
}
