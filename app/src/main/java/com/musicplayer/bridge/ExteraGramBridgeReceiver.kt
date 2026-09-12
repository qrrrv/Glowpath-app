package com.musicplayer.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musicplayer.MainActivity
import com.musicplayer.service.MusicService

class ExteraGramBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.toExteraGramBridgeCommand() ?: return
        if (command.type in ExteraGramBridgeContract.CONTROL_COMMANDS) {
            val serviceAction = when (command.type) {
                ExteraGramBridgeContract.TYPE_PLAY_PAUSE -> MusicService.ACTION_PLAY_PAUSE
                ExteraGramBridgeContract.TYPE_NEXT -> MusicService.ACTION_NEXT
                ExteraGramBridgeContract.TYPE_PREV -> MusicService.ACTION_PREV
                ExteraGramBridgeContract.TYPE_STOP -> MusicService.ACTION_STOP
                else -> null
            }
            serviceAction?.let { action ->
                context.sendBroadcast(
                    Intent(action).setPackage(context.packageName)
                )
            }
            ExteraGramBridgeRuntime.dispatch(context, command)
            return
        }

        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            .putExteraGramBridgeCommand(command)

        context.startActivity(activityIntent)
    }
}
