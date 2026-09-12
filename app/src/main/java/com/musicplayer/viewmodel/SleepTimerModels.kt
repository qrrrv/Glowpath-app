package com.musicplayer.viewmodel

enum class SleepTimerAction {
    PAUSE,
    STOP
}

data class SleepTimerState(
    val isRunning: Boolean = false,
    val remainingSeconds: Int = 0,
    val fadeOut: Boolean = false,
    val stopAfterTrack: Boolean = false,
    val action: SleepTimerAction = SleepTimerAction.PAUSE
)
