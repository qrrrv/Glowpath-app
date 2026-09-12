package com.musicplayer.server

import android.content.Context
import android.util.Log
import com.musicplayer.bridge.ExteraGramBridgeStateStore
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Локальный HTTP-сервер для отдачи текущего трека (Hikka / внешние клиенты).
 * Порт: 8765
 *
 * GET /now   → JSON трека
 * GET /cover → обложка
 */
class NowPlayingHttpServer(
    private val context: Context,
    port: Int = DEFAULT_PORT,
) : NanoHTTPD(port) {

    companion object {
        const val DEFAULT_PORT = 8765
        private const val TAG = "NowPlayingHttpServer"

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
        return when {
            uri == "" || uri == "/" -> serveHelp()
            uri == "/now" || uri == "/status" -> serveNow()
            uri == "/cover" || uri == "/cover/current" -> serveCover()
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not found. Use /now or /cover"
            )
        }
    }

    private fun serveHelp(): Response {
        val text = """
            GlowPath Now Playing API
            ------------------------
            GET /now     - current track as JSON
            GET /cover   - current cover image
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, text)
    }

    private fun serveNow(): Response {
        return try {
            val snapshot = ExteraGramBridgeStateStore.loadSnapshot(context)
            val json = JSONObject().apply {
                put("title", snapshot.title)
                put("artist", snapshot.artist)
                put("album", snapshot.album)
                put("is_playing", snapshot.isPlaying)
                put("has_song", snapshot.hasSong)
                put("duration_ms", snapshot.durationMs)
                put("position_ms", snapshot.positionMs)
                put("current_uri", snapshot.currentUri)
                put("library_size", snapshot.librarySize)
                put("updated_at", snapshot.updatedAt)
                put("cover_url", "http://127.0.0.1:$listeningPort/cover")
            }
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json; charset=utf-8",
                json.toString()
            ).apply {
                addHeader("Access-Control-Allow-Origin", "*")
            }
        } catch (e: Exception) {
            Log.e(TAG, "serveNow error", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: ${e.message}"
            )
        }
    }

    private fun serveCover(): Response {
        return try {
            val coverUri = android.net.Uri.parse("content://com.musicplayer.bridge/cover/current")
            val input = context.contentResolver.openInputStream(coverUri)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No cover")
            val bytes = input.use { it.readBytes() }
            if (bytes.isEmpty()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Empty cover")
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
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Cover not available")
        }
    }
}
