package com.musicplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.data.ArtistPageState
import com.musicplayer.repository.ArtistRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArtistViewModel : ViewModel() {

    private val _state = MutableStateFlow<ArtistPageState>(ArtistPageState.Idle)
    val state: StateFlow<ArtistPageState> = _state.asStateFlow()

    private var job: Job? = null
    private var currentName: String = ""

    fun open(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        if (clean == currentName && _state.value is ArtistPageState.Success) return
        currentName = clean
        load(clean, force = false)
    }

    fun retry() {
        if (currentName.isNotBlank()) load(currentName, force = true)
    }

    private fun load(name: String, force: Boolean) {
        job?.cancel()
        _state.value = ArtistPageState.Loading(name)
        job = viewModelScope.launch {
            val page = runCatching { ArtistRepository.load(name, force) }.getOrNull()
            coroutineContext.ensureActive()
            _state.value = if (page == null) {
                ArtistPageState.Empty(name)
            } else {
                ArtistPageState.Success(page)
            }
        }
    }
}
