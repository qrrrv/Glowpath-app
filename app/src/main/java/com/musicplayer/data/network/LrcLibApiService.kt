package com.musicplayer.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * LRCLIB API client.
 * Uses plain HttpURLConnection — no Retrofit/OkHttp dependency.
 * Base URL: https://lrclib.net
 */
object LrcLibApiService {

    private const val BASE_URL = "https://lrclib.net"
    private const val TIMEOUT_MS = 10_000

    /**
     * Exact match lookup by track metadata.
     * Endpoint: GET /api/get?track_name=…&artist_name=…&album_name=…&duration=…
     */
    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String,
        duration: Int
    ): LrcLibResponse? = withContext(Dispatchers.IO) {
        val params = buildParams(
            "track_name"  to trackName,
            "artist_name" to artistName,
            "album_name"  to albumName,
            "duration"    to duration.toString()
        )
        val json = get("$BASE_URL/api/get?$params") ?: return@withContext null
        parseSingle(json)
    }

    /**
     * Flexible search endpoint.
     * Endpoint: GET /api/search?q=…&track_name=…&artist_name=…&album_name=…
     */
    suspend fun searchLyrics(
        query: String? = null,
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null
    ): List<LrcLibResponse> = withContext(Dispatchers.IO) {
        val pairs = mutableListOf<Pair<String, String>>()
        query?.let      { pairs += "q" to it }
        trackName?.let  { pairs += "track_name" to it }
        artistName?.let { pairs += "artist_name" to it }
        albumName?.let  { pairs += "album_name" to it }

        val params = buildParams(*pairs.toTypedArray())
        val json   = get("$BASE_URL/api/search?$params") ?: return@withContext emptyList()
        parseArray(json)
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private fun get(urlStr: String): String? {
        return try {
            val url  = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout    = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MusicPlayer/1.0")
            }
            val code = conn.responseCode
            if (code != 200) return null

            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildParams(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

    // ── JSON parsers ─────────────────────────────────────────────────────────

    private fun parseSingle(json: String): LrcLibResponse? = try {
        parseObject(JSONObject(json))
    } catch (e: Exception) { null }

    private fun parseArray(json: String): List<LrcLibResponse> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            try { parseObject(arr.getJSONObject(i)) } catch (e: Exception) { null }
        }
    } catch (e: Exception) { emptyList() }

    private fun parseObject(obj: JSONObject): LrcLibResponse? = try {
        LrcLibResponse(
            id           = obj.optInt("id", -1),
            name         = obj.optString("name", ""),
            artistName   = obj.optString("artistName", ""),
            albumName    = obj.optString("albumName", ""),
            duration     = obj.optDouble("duration", 0.0),
            plainLyrics  = obj.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" },
            syncedLyrics = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        )
    } catch (e: Exception) { null }
}
