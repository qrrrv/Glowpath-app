package com.musicplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.data.Song
import com.musicplayer.data.stats.PlaybackStatsRepository
import com.musicplayer.data.stats.StatsTimeRange
import com.musicplayer.data.stats.PlaybackStatsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class StatsUiState {
    object Loading : StatsUiState()
    object Empty   : StatsUiState()
    data class Ready(val summary: PlaybackStatsSummary) : StatsUiState()
}

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    val repository = PlaybackStatsRepository(application)

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val _selectedRange = MutableStateFlow(StatsTimeRange.WEEK)
    val selectedRange: StateFlow<StatsTimeRange> = _selectedRange.asStateFlow()

    fun load(songs: List<Song>, range: StatsTimeRange = _selectedRange.value) {
        _selectedRange.value = range
        _uiState.value = StatsUiState.Loading
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                repository.loadSummary(range, songs)
            }
            _uiState.value = if (summary.totalPlayCount == 0 && !repository.hasAnyData()) {
                StatsUiState.Empty
            } else {
                StatsUiState.Ready(summary)
            }
        }
    }

    fun clearStats(songs: List<Song>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
        _uiState.value = StatsUiState.Empty
    }
}