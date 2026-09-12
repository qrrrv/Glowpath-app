package com.musicplayer.bridge

import android.os.Build
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object ExteraGramBridgeContract {
    const val AUTHORITY = "com.musicplayer.bridge"
    const val ACTION_BRIDGE_COMMAND = "com.musicplayer.bridge.ACTION_COMMAND"
    const val EXTRA_COMMAND_TYPE = "bridge_command_type"
    const val EXTRA_QUERY = "bridge_query"
    const val EXTRA_URI = "bridge_uri"
    const val EXTRA_SOURCE = "bridge_source"
    const val EXTRA_AUTO_PLAY = "bridge_auto_play"
    const val EXTRA_PAYLOAD = "bridge_payload"

    const val TYPE_OPEN_HUB = "open_hub"
    const val TYPE_OPEN_PLAYER = "open_player"
    const val TYPE_PLAY_PAUSE = "play_pause"
    const val TYPE_NEXT = "next"
    const val TYPE_PREV = "prev"
    const val TYPE_STOP = "stop"
    const val TYPE_SEARCH_ONLINE = "search_online"
    const val TYPE_PLAY_LIBRARY_QUERY = "play_library_query"
    const val TYPE_OPEN_LYRICS_QUERY = "open_lyrics_query"
    const val TYPE_IMPORT_URI = "import_uri"
    const val TYPE_IMPORT_AND_PLAY_URI = "import_and_play_uri"
    const val TYPE_IMPORT_BATCH = "import_batch"
    const val TYPE_IMPORT_BATCH_AND_PLAY = "import_batch_and_play"
    const val TYPE_QUEUE_BATCH_APPEND = "queue_batch_append"

    val CONTROL_COMMANDS = setOf(
        TYPE_PLAY_PAUSE,
        TYPE_NEXT,
        TYPE_PREV,
        TYPE_STOP,
    )

    fun statusUri(): Uri = Uri.parse("content://$AUTHORITY/status")

    fun coverUri(): Uri = Uri.parse("content://$AUTHORITY/cover/current")

    fun searchUri(query: String): Uri =
        Uri.parse("content://$AUTHORITY/search").buildUpon()
            .appendQueryParameter("q", query)
            .build()
}

data class ExteraGramBridgeCommand(
    val type: String,
    val query: String = "",
    val uri: String = "",
    val source: String = "exteragram",
    val autoPlay: Boolean = false,
    val payload: String = "",
)

data class ExteraGramBridgeBatchItem(
    val uri: String = "",
    val query: String = "",
    val title: String = "",
    val artist: String = "",
)

data class ExteraGramBridgeSnapshot(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val hasSong: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val currentUri: String = "",
    val librarySize: Int = 0,
    val lastCommandLabel: String = "",
    val lastQuery: String = "",
    val lastImportUri: String = "",
    val bridgeQueueSize: Int = 0,
    val lastBatchSummary: String = "",
    val updatedAt: Long = 0L,
) {
    fun effectivePositionMs(nowMs: Long): Long {
        if (!isPlaying || updatedAt <= 0L) return positionMs
        return (positionMs + (nowMs - updatedAt)).coerceAtLeast(0L)
    }
}

fun ExteraGramBridgeCommand.toJson(): String = JSONObject().apply {
    put("type", type)
    put("query", query)
    put("uri", uri)
    put("source", source)
    put("autoPlay", autoPlay)
    put("payload", payload)
}.toString()

fun parseBridgeCommand(raw: String?): ExteraGramBridgeCommand? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val json = JSONObject(raw)
        ExteraGramBridgeCommand(
            type = json.optString("type"),
            query = json.optString("query"),
            uri = json.optString("uri"),
            source = json.optString("source", "exteragram"),
            autoPlay = json.optBoolean("autoPlay", false),
            payload = json.optString("payload"),
        )
    }.getOrNull()?.takeIf { it.type.isNotBlank() }
}

fun Intent.toExteraGramBridgeCommand(): ExteraGramBridgeCommand? {
    getStringExtra(ExteraGramBridgeContract.EXTRA_COMMAND_TYPE)?.let { type ->
        return ExteraGramBridgeCommand(
            type = type,
            query = getStringExtra(ExteraGramBridgeContract.EXTRA_QUERY).orEmpty(),
            uri = getStringExtra(ExteraGramBridgeContract.EXTRA_URI).orEmpty(),
            source = getStringExtra(ExteraGramBridgeContract.EXTRA_SOURCE).orEmpty().ifBlank { "exteragram" },
            autoPlay = getBooleanExtra(ExteraGramBridgeContract.EXTRA_AUTO_PLAY, false),
            payload = getStringExtra(ExteraGramBridgeContract.EXTRA_PAYLOAD).orEmpty(),
        )
    }

    if (action == Intent.ACTION_VIEW) {
        val dataUri = data
        if (dataUri?.scheme == "musicplayer" && dataUri.host == "bridge") {
            val first = dataUri.pathSegments.firstOrNull().orEmpty()
            return when (first) {
                "hub" -> ExteraGramBridgeCommand(ExteraGramBridgeContract.TYPE_OPEN_HUB)
                "player" -> ExteraGramBridgeCommand(ExteraGramBridgeContract.TYPE_OPEN_PLAYER)
                "search" -> ExteraGramBridgeCommand(
                    type = ExteraGramBridgeContract.TYPE_SEARCH_ONLINE,
                    query = dataUri.getQueryParameter("q").orEmpty().ifBlank {
                        dataUri.getQueryParameter("query").orEmpty()
                    },
                )
                "play" -> ExteraGramBridgeCommand(
                    type = ExteraGramBridgeContract.TYPE_PLAY_LIBRARY_QUERY,
                    query = dataUri.getQueryParameter("q").orEmpty().ifBlank {
                        dataUri.getQueryParameter("query").orEmpty()
                    },
                )
                "import" -> ExteraGramBridgeCommand(
                    type = if (dataUri.getBooleanQueryParameter("autoplay", false)) {
                        ExteraGramBridgeContract.TYPE_IMPORT_AND_PLAY_URI
                    } else {
                        ExteraGramBridgeContract.TYPE_IMPORT_URI
                    },
                    uri = dataUri.getQueryParameter("uri").orEmpty(),
                    autoPlay = dataUri.getBooleanQueryParameter("autoplay", false),
                )
                else -> null
            }
        }

        if (type?.startsWith("audio/") == true || dataUri?.scheme in setOf("content", "file")) {
            return ExteraGramBridgeCommand(
                type = if (getBooleanExtra(ExteraGramBridgeContract.EXTRA_AUTO_PLAY, false)) {
                    ExteraGramBridgeContract.TYPE_IMPORT_AND_PLAY_URI
                } else {
                    ExteraGramBridgeContract.TYPE_IMPORT_URI
                },
                uri = dataUri?.toString().orEmpty(),
                autoPlay = getBooleanExtra(ExteraGramBridgeContract.EXTRA_AUTO_PLAY, false),
            )
        }
    }

    if (action == Intent.ACTION_SEND) {
        val sharedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (sharedUri != null) {
            return ExteraGramBridgeCommand(
                type = if (getBooleanExtra(ExteraGramBridgeContract.EXTRA_AUTO_PLAY, false)) {
                    ExteraGramBridgeContract.TYPE_IMPORT_AND_PLAY_URI
                } else {
                    ExteraGramBridgeContract.TYPE_IMPORT_URI
                },
                uri = sharedUri.toString(),
                autoPlay = getBooleanExtra(ExteraGramBridgeContract.EXTRA_AUTO_PLAY, false),
            )
        }

        val text = getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        if (text.isNotBlank()) {
            return ExteraGramBridgeCommand(
                type = ExteraGramBridgeContract.TYPE_SEARCH_ONLINE,
                query = text,
            )
        }
    }

    return null
}

fun Intent.putExteraGramBridgeCommand(command: ExteraGramBridgeCommand): Intent {
    putExtra(ExteraGramBridgeContract.EXTRA_COMMAND_TYPE, command.type)
    putExtra(ExteraGramBridgeContract.EXTRA_QUERY, command.query)
    putExtra(ExteraGramBridgeContract.EXTRA_URI, command.uri)
    putExtra(ExteraGramBridgeContract.EXTRA_SOURCE, command.source)
    putExtra(ExteraGramBridgeContract.EXTRA_AUTO_PLAY, command.autoPlay)
    putExtra(ExteraGramBridgeContract.EXTRA_PAYLOAD, command.payload)
    return this
}

fun serializeBridgeBatchItems(items: List<ExteraGramBridgeBatchItem>): String =
    JSONArray().apply {
        items.forEach { item ->
            put(
                JSONObject().apply {
                    put("uri", item.uri)
                    put("query", item.query)
                    put("title", item.title)
                    put("artist", item.artist)
                }
            )
        }
    }.toString()

fun parseBridgeBatchItems(raw: String?): List<ExteraGramBridgeBatchItem> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            ExteraGramBridgeBatchItem(
                uri = obj.optString("uri"),
                query = obj.optString("query"),
                title = obj.optString("title"),
                artist = obj.optString("artist"),
            )
        }
    }.getOrElse { emptyList() }
}

fun bridgeCommandLabel(command: ExteraGramBridgeCommand): String {
    return when (command.type) {
        ExteraGramBridgeContract.TYPE_OPEN_HUB -> "Открыт ExteraGram Hub"
        ExteraGramBridgeContract.TYPE_OPEN_PLAYER -> "Открыт экран плеера"
        ExteraGramBridgeContract.TYPE_PLAY_PAUSE -> "Play / Pause"
        ExteraGramBridgeContract.TYPE_NEXT -> "Следующий трек"
        ExteraGramBridgeContract.TYPE_PREV -> "Предыдущий трек"
        ExteraGramBridgeContract.TYPE_STOP -> "Остановка"
        ExteraGramBridgeContract.TYPE_SEARCH_ONLINE -> "Онлайн поиск: ${command.query}"
        ExteraGramBridgeContract.TYPE_PLAY_LIBRARY_QUERY -> "Играть из библиотеки: ${command.query}"
        ExteraGramBridgeContract.TYPE_OPEN_LYRICS_QUERY -> "Открыть lyrics для: ${command.query}"
        ExteraGramBridgeContract.TYPE_IMPORT_URI -> "Импорт аудио из ExteraGram"
        ExteraGramBridgeContract.TYPE_IMPORT_AND_PLAY_URI -> "Импорт и запуск аудио"
        ExteraGramBridgeContract.TYPE_IMPORT_BATCH -> "Импорт Telegram batch"
        ExteraGramBridgeContract.TYPE_IMPORT_BATCH_AND_PLAY -> "Импорт Telegram batch и запуск"
        ExteraGramBridgeContract.TYPE_QUEUE_BATCH_APPEND -> "Добавление Telegram batch в очередь"
        else -> command.type
    }
}
