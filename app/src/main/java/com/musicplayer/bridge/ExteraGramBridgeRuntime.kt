package com.musicplayer.bridge

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ExteraGramBridgeRuntime {
    private val _commands = MutableSharedFlow<ExteraGramBridgeCommand>(extraBufferCapacity = 32)
    val commands = _commands.asSharedFlow()

    fun dispatch(context: Context, command: ExteraGramBridgeCommand) {
        ExteraGramBridgeStateStore.rememberCommand(context, command)
        _commands.tryEmit(command)
    }
}
