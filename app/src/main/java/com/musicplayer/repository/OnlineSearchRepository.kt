package com.musicplayer.repository

import android.util.Log
import com.musicplayer.data.OnlineSearchPayload
import com.musicplayer.data.OnlineSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Searches the canonical music catalog in Deezer and resolves playable files in Hitmos.
 *
 * Deezer supplies the trusted artist/title/cover/duration metadata. Hitmos is used only
 * as a file source because Deezer does not expose a direct MP3 URL. A track is returned
 * only when Hitmos contains an exact artist + title match with a direct MP3 URL.
 */
object OnlineSearchRepository {

    private const val TAG = "OnlineSearchRepository"

    suspend fun search(query: String): OnlineSearchPayload = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext OnlineSearchPayload(emptyList(), emptyList())

        val catalog = runCatching { DeezerApi.search(cleanQuery) }
            .onFailure { Log.w(TAG, "Deezer search failed", it) }
            .getOrDefault(emptyList())

        if (catalog.isEmpty()) {
            return@withContext OnlineSearchPayload(emptyList(), emptyList())
        }

        // Resolve all ten catalog results concurrently. Each resolver still performs its
        // network work on Dispatchers.IO, so Compose's main thread is never blocked.
        val songs = coroutineScope {
            catalog.map { track ->
                async {
                    runCatching {
                        HitmosRepository.findExactTrack(
                            artist = track.artist,
                            title = track.title
                        )?.let { hitmosSong ->
                            hitmosSong.copy(
                                // Keep canonical Deezer spelling and metadata in the UI.
                                title = track.title,
                                artist = track.artist,
                                duration = formatDuration(track.durationSeconds),
                                coverUrl = track.coverUrl.ifBlank { hitmosSong.coverUrl }
                            )
                        }
                    }.onFailure {
                        Log.d(TAG, "Hitmos resolution failed for ${track.artist} - ${track.title}", it)
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }

        OnlineSearchPayload(
            songs = songs.distinctBy { it.downloadUrl },
            // Deezer album URLs are not Hitmos page URLs. Do not expose broken album
            // cards; the existing OnlineAlbumSummary model remains compatible for the
            // Hitmos album-detail flow used elsewhere in the app.
            albums = emptyList()
        )
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return ""
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
}

private data class DeezerTrack(
    val artist: String,
    val title: String,
    val durationSeconds: Int,
    val coverUrl: String
)

private object DeezerApi {
    private const val ENDPOINT = "https://api.deezer.com/search"
    private const val LIMIT = 10

    fun search(query: String): List<DeezerTrack> {
        val url = "$ENDPOINT?q=${encode(query)}&limit=$LIMIT"
        val root = JSONObject(
            Jsoup.connect(url)
                .ignoreContentType(true)
                .followRedirects(true)
                .userAgent("MusicPlayer/1.0 (Android)")
                .header("Accept", "application/json")
                .timeout(8_000)
                .execute()
                .body()
        )
        if (root.optInt("error", 0) != 0) return emptyList()

        val data = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                val artist = item.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
                if (title.isBlank() || artist.isBlank()) continue

                val album = item.optJSONObject("album")
                add(
                    DeezerTrack(
                        artist = artist,
                        title = title,
                        durationSeconds = item.optInt("duration", 0),
                        coverUrl = album?.optString("cover_medium")?.trim().orEmpty()
                    )
                )
            }
        }.distinctBy { normalize("${it.artist} ${it.title}") }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun normalize(value: String): String = value
        .lowercase()
        .replace('ё', 'е')
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
