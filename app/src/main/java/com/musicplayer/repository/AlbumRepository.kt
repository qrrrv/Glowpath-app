package com.musicplayer.repository

import android.util.Log
import com.musicplayer.data.AlbumDetail
import com.musicplayer.data.AlbumTrack
import com.musicplayer.repository.search.SearchHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Reads a Deezer album with its track list for the artist page. */
object AlbumRepository {

    private const val TAG = "AlbumRepository"
    private const val DEEZER = "https://api.deezer.com"

    suspend fun load(albumId: Long): AlbumDetail? = withContext(Dispatchers.IO) {
        if (albumId <= 0L) return@withContext null
        runCatching { fetch(albumId) }
            .onFailure { Log.w(TAG, "Album lookup failed for " + albumId, it) }
            .getOrNull()
    }

    private fun fetch(albumId: Long): AlbumDetail? {
        val root = SearchHttp.jsonObject(DEEZER + "/album/" + albumId) ?: return null
        val title = root.optString("title").trim()
        if (title.isBlank()) return null
        val artist = root.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
        val release = root.optString("release_date").trim()
        val cover = root.optString("cover_xl").ifBlank {
            root.optString("cover_big").ifBlank { root.optString("cover_medium") }
        }

        val tracksJson = root.optJSONObject("tracks")?.optJSONArray("data")
        val tracks = ArrayList<AlbumTrack>(tracksJson?.length() ?: 0)
        if (tracksJson != null) {
            for (index in 0 until tracksJson.length()) {
                val item = tracksJson.optJSONObject(index) ?: continue
                val trackTitle = item.optString("title").trim()
                if (trackTitle.isBlank()) continue
                tracks += AlbumTrack(
                    id = item.optLong("id", 0L),
                    title = trackTitle,
                    artist = item.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
                        .ifBlank { artist },
                    durationSeconds = item.optInt("duration", 0),
                    position = index
                )
            }
        }

        return AlbumDetail(
            id = albumId,
            title = title,
            artist = artist,
            coverUrl = cover,
            year = if (release.length >= 4) release.substring(0, 4) else "",
            kind = recordType(root.optString("record_type")),
            tracks = tracks
        )
    }

    private fun recordType(value: String): String = when (value.lowercase()) {
        "album" -> "Альбом"
        "single" -> "Сингл"
        "ep" -> "EP"
        "compile" -> "Сборник"
        else -> ""
    }
}
