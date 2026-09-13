package com.musicplayer.server

import android.content.Context
import android.util.Log
import com.musicplayer.bridge.ExteraGramBridgeContract
import com.musicplayer.bridge.ExteraGramBridgeCommand
import com.musicplayer.bridge.ExteraGramBridgeRuntime
import com.musicplayer.bridge.ExteraGramBridgeStateStore
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Локальный HTTP-сервер для отдачи текущего трека (Hikka / внешние клиенты).
 * Порт: 8765
 *
 * GET  /now      → JSON трека (расширенный)
 * GET  /status   → алиас /now
 * GET  /cover    → обложка
 * GET  /info     → информация о сервере
 * POST /control/play_pause
 * POST /control/next
 * POST /control/prev
 * POST /control/stop
 */
class NowPlayingHttpServer(
    private val context: Context,
    port: Int = DEFAULT_PORT,
) : NanoHTTPD(port) {

    companion object {
        const val DEFAULT_PORT = 8765
        private const val TAG = "NowPlayingHttpServer"
        const val API_VERSION = "1.1"

        @Volatile
        private var instance: NowPlayingHttpServer? = null

        fun start(context: Context, port: Int = DEFAULT_PORT): NowPlayingHttpServer {
            synchronized(this) {
                instance?.stop()
                val server = NowPlayingHttpServer(context.applicationContext, port)
                try {
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    Log.i(TAG, "NowPlaying HTTP server started on port $port")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start HTTP server on port $port", e)
                }
                instance = server
                return server
            }
        }

        fun stop() {
            synchronized(this) {
                instance?.stop()
                instance = null
            }
        }

        fun isRunning(): Boolean = instance?.isAlive == true
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri?.trimEnd('/') ?: "/"
        val method = session.method

        // CORS preflight
        if (method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                addHeader("Access-Control-Allow-Headers", "Content-Type")
            }
        }

        return when {
            uri == "" || uri == "/" -> serveHelp()
            uri == "/now" || uri == "/status" -> serveNow()
            uri == "/cover" || uri == "/cover/current" -> serveCover()
            uri == "/info" -> serveInfo()

            // Управление
            method == Method.POST && uri == "/control/play_pause" -> handleControl(ExteraGramBridgeContract.TYPE_PLAY_PAUSE)
            method == Method.POST && uri == "/control/next" -> handleControl(ExteraGramBridgeContract.TYPE_NEXT)
            method == Method.POST && uri == "/control/prev" -> handleControl(ExteraGramBridgeContract.TYPE_PREV)
            method == Method.POST && uri == "/control/stop" -> handleControl(ExteraGramBridgeContract.TYPE_STOP)

            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not found. See GET / for available endpoints"
            ).withCors()
        }
    }

    private fun serveHelp(): Response {
        val text = """
            GlowPath Now Playing API v$API_VERSION
            ------------------------------------
            GET  /now              - current track as JSON (extended)
            GET  /status           - alias of /now
            GET  /cover            - current cover image (JPEG)
            GET  /info             - server info

            POST /control/play_pause
            POST /control/next
            POST /control/prev
            POST /control/stop
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, text).withCors()
    }

    private fun serveInfo(): Response {
        return try {
            val json = JSONObject().apply {
                put("api_version", API_VERSION)
                put("server", "GlowPath NowPlayingHttpServer")
                put("port", listeningPort)
                put("running", isAlive)
                put("timestamp", System.currentTimeMillis())
            }
            jsonResponse(json)
        } catch (e: Exception) {
            Log.e(TAG, "serveInfo error", e)
            errorResponse("Error: ${e.message}")
        }
    }

    private fun serveNow(): Response {
        return try {
            val snapshot = ExteraGramBridgeStateStore.loadSnapshot(context)
            val now = System.currentTimeMillis()
            val effectivePos = snapshot.effectivePositionMs(now)
            val duration = snapshot.durationMs.coerceAtLeast(0L)
            val progress = if (duration > 0) {
                (effectivePos.toDouble() / duration).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val remaining = (duration - effectivePos).coerceAtLeast(0L)

            val json = JSONObject().apply {
                put("title", snapshot.title)
                put("artist", snapshot.artist)
                put("album", snapshot.album)
                put("is_playing", snapshot.isPlaying)
                put("has_song", snapshot.hasSong)

                // Позиция
                put("duration_ms", duration)
                put("position_ms", effectivePos)                    // эффективная
                put("position_raw_ms", snapshot.positionMs)         // сырая из снапшота
                put("remaining_ms", remaining)
                put("progress", progress)                           // 0.0 .. 1.0

                // Красивые строки для карточек
                put("position_str", formatMs(effectivePos))
                put("duration_str", formatMs(duration))
                put("remaining_str", formatMs(remaining))

                put("current_uri", snapshot.currentUri)
                put("library_size", snapshot.librarySize)
                put("bridge_queue_size", snapshot.bridgeQueueSize)
                put("last_command", snapshot.lastCommandLabel)
                put("last_query", snapshot.lastQuery)
                put("last_import_uri", snapshot.lastImportUri)
                put("last_batch_summary", snapshot.lastBatchSummary)

                put("updated_at", snapshot.updatedAt)
                put("server_time", now)
                put("cover_url", "http://127.0.0.1:$listeningPort/cover")
            }
            jsonResponse(json)
        } catch (e: Exception) {
            Log.e(TAG, "serveNow error", e)
            errorResponse("Error: ${e.message}")
        }
    }

    private fun serveCover(): Response {
        return try {
            val coverUri = android.net.Uri.parse("content://com.musicplayer.bridge/cover/current")
            val input = context.contentResolver.openInputStream(coverUri)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No cover").withCors()

            val bytes = input.use { it.readBytes() }
            if (bytes.isEmpty()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Empty cover").withCors()
            }

            newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                ByteArrayInputStream(bytes),
                bytes.size.toLong()
            ).apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Cache-Control", "no-cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "serveCover error", e)
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Cover not available").withCors()
        }
    }

    private fun handleControl(type: String): Response {
        return try {
            val command = ExteraGramBridgeCommand(
                type = type,
                source = "http_api"
            )
            ExteraGramBridgeRuntime.dispatch(context, command)

            val json = JSONObject().apply {
                put("ok", true)
                put("command", type)
                put("message", "Command dispatched")
            }
            jsonResponse(json)
        } catch (e: Exception) {
            Log.e(TAG, "handleControl error ($type)", e)
            errorResponse("Failed to dispatch command: ${e.message}")
        }
    }

    // ----------------- helpers -----------------

    private fun formatMs(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private fun jsonResponse(json: JSONObject): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            json.toString()
        ).withCors()
    }

    private fun errorResponse(message: String): Response {
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            MIME_PLAINTEXT,
            message
        ).withCors()
    }

    private fun Response.withCors(): Response {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        addHeader("Access-Control-Allow-Headers", "Content-Type")
        return this
    }
}
