package com.musicplayer.bridge

import android.content.Context
import org.json.JSONObject

object ExteraGramBridgeStateStore {
    private const val PREFS_NAME = "music_player_bridge"
    private const val KEY_SNAPSHOT = "snapshot"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSnapshot(context: Context): ExteraGramBridgeSnapshot {
        val raw = prefs(context).getString(KEY_SNAPSHOT, null) ?: return ExteraGramBridgeSnapshot()
        return runCatching {
            val json = JSONObject(raw)
            ExteraGramBridgeSnapshot(
                title = json.optString("title"),
                artist = json.optString("artist"),
                album = json.optString("album"),
                isPlaying = json.optBoolean("isPlaying", false),
                hasSong = json.optBoolean("hasSong", false),
                durationMs = json.optLong("durationMs", 0L),
                positionMs = json.optLong("positionMs", 0L),
                currentUri = json.optString("currentUri"),
                librarySize = json.optInt("librarySize", 0),
                lastCommandLabel = json.optString("lastCommandLabel"),
                lastQuery = json.optString("lastQuery"),
                lastImportUri = json.optString("lastImportUri"),
                bridgeQueueSize = json.optInt("bridgeQueueSize", 0),
                lastBatchSummary = json.optString("lastBatchSummary"),
                updatedAt = json.optLong("updatedAt", 0L),
            )
        }.getOrElse { ExteraGramBridgeSnapshot() }
    }

    fun saveSnapshot(context: Context, snapshot: ExteraGramBridgeSnapshot) {
        val json = JSONObject().apply {
            put("title", snapshot.title)
            put("artist", snapshot.artist)
            put("album", snapshot.album)
            put("isPlaying", snapshot.isPlaying)
            put("hasSong", snapshot.hasSong)
            put("durationMs", snapshot.durationMs)
            put("positionMs", snapshot.positionMs)
            put("currentUri", snapshot.currentUri)
            put("librarySize", snapshot.librarySize)
            put("lastCommandLabel", snapshot.lastCommandLabel)
            put("lastQuery", snapshot.lastQuery)
            put("lastImportUri", snapshot.lastImportUri)
            put("bridgeQueueSize", snapshot.bridgeQueueSize)
            put("lastBatchSummary", snapshot.lastBatchSummary)
            put("updatedAt", snapshot.updatedAt)
        }
        prefs(context).edit().putString(KEY_SNAPSHOT, json.toString()).apply()
    }

    fun syncPlayback(
        context: Context,
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        hasSong: Boolean,
        durationMs: Long,
        positionMs: Long,
        currentUri: String,
        librarySize: Int,
        bridgeQueueSize: Int = 0,
        lastBatchSummary: String = "",
    ) {
        val previous = loadSnapshot(context)
        saveSnapshot(
            context,
            previous.copy(
                title = title,
                artist = artist,
                album = album,
                isPlaying = isPlaying,
                hasSong = hasSong,
                durationMs = durationMs,
                positionMs = positionMs,
                currentUri = currentUri,
                librarySize = librarySize,
                bridgeQueueSize = bridgeQueueSize,
                lastBatchSummary = if (lastBatchSummary.isBlank()) previous.lastBatchSummary else lastBatchSummary,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun rememberCommand(context: Context, command: ExteraGramBridgeCommand) {
        val previous = loadSnapshot(context)
        saveSnapshot(
            context,
            previous.copy(
                lastCommandLabel = bridgeCommandLabel(command),
                lastQuery = command.query.ifBlank { previous.lastQuery },
                lastImportUri = command.uri.ifBlank { previous.lastImportUri },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
