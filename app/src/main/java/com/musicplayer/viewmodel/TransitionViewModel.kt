package com.musicplayer.viewmodel

import androidx.lifecycle.ViewModel
import com.musicplayer.data.Curve
import com.musicplayer.data.TransitionMode
import com.musicplayer.data.TransitionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TransitionUiState(
    val globalSettings: TransitionSettings = TransitionSettings(),
    val useGlobalDefaults: Boolean = true,
    val playlistId: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false
)

class TransitionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TransitionUiState())
    val uiState: StateFlow<TransitionUiState> = _uiState.asStateFlow()

    fun updateMode(mode: TransitionMode) {
        _uiState.update { state ->
            state.copy(
                globalSettings = state.globalSettings.copy(mode = mode),
                isSaved = false
            )
        }
    }

    fun updateDuration(durationMs: Int) {
        _uiState.update { state ->
            state.copy(
                globalSettings = state.globalSettings.copy(durationMs = durationMs),
                isSaved = false
            )
        }
    }

    fun updateCurveIn(curve: Curve) {
        _uiState.update { state ->
            state.copy(
                globalSettings = state.globalSettings.copy(curveIn = curve),
                isSaved = false
            )
        }
    }

    fun updateCurveOut(curve: Curve) {
        _uiState.update { state ->
            state.copy(
                globalSettings = state.globalSettings.copy(curveOut = curve),
                isSaved = false
            )
        }
    }

    fun saveSettings(onApply: ((TransitionSettings) -> Unit)? = null) {
        // В будущем: сохранять в DataStore/Room
        _uiState.update { it.copy(isSaved = true) }
        // Применяем настройки к движку кроссфейда через callback из ViewModel
        onApply?.invoke(_uiState.value.globalSettings)
    }

    fun useGlobalDefaults() {
        _uiState.update { it.copy(useGlobalDefaults = true, isSaved = false) }
    }

    fun enablePlaylistOverride() {
        _uiState.update { it.copy(useGlobalDefaults = false, isSaved = false) }
    }
}
