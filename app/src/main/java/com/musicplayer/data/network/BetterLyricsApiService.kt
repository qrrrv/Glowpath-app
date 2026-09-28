package com.musicplayer.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * BetterLyrics client used by BoomingMusic as a public, keyless lyrics source.
 * It returns Apple-style TTML with line and word timestamps.
 */
object BetterLyricsApiService {
    private const val TAG = "BetterLyricsApiService"
    private const val ENDPOINT = "https://lyrics-api.boidu.dev/getLyrics"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000

    suspend fun getTtmlLyrics(
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int
    ): String? = withContext(Dispatchers.IO) {
        val query = listOf(
            "s" to title,
            "a" to artist,
            "d" to durationSeconds.toString(),
            "al" to album
        ).joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

        val connection = try {
            (URL("$ENDPOINT?$query").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Glowpath/1.0")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not create request: ${e.message}")
            return@withContext null
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "BetterLyrics returned ${connection.responseCode}")
                return@withContext null
            }
            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            JSONObject(body).optString("ttml").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "BetterLyrics request failed: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }
}
