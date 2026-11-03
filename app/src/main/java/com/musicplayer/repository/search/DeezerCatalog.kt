package com.musicplayer.repository.search

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Deezer catalog: free-text search, artist resolution and artist top tracks.
 * The public API needs no key and returns a popularity rank which is used for
 * ordering.
 */
internal object DeezerCatalog {

    private const val TAG = "DeezerCatalog"
    private const val BASE = "https://api.deezer.com"

    data class Artist(
        val id: Long,
        val name: String,
        val fans: Long
    )

    fun searchTracks(query: String, limit: Int): List<CatalogTrack> {
        val url = BASE + "/search?q=" + encode(query) + "&limit=" + limit
        val root = SearchHttp.jsonObject(url) ?: return emptyList()
        if (root.optInt("error", 0) != 0) {
            Log.d(TAG, "Search error: " + root.optString("error"))
            return emptyList()
        }
        val data = root.optJSONArray("data") ?: return emptyList()
        return parseTracks(data, CatalogSource.DEEZER_SEARCH)
    }

    fun searchArtists(query: String, limit: Int): List<Artist> {
        val url = BASE + "/search/artist?q=" + encode(query) + "&limit=" + limit
        val root = SearchHttp.jsonObject(url) ?: return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()
        val artists = ArrayList<Artist>(data.length())
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isBlank()) continue
            artists += Artist(
                id = item.optLong("id", 0L),
                name = name,
                fans = item.optLong("nb_fan", 0L)
            )
        }
        return artists
    }

    fun artistTop(artistId: Long, limit: Int, artistFans: Long): List<CatalogTrack> {
        if (artistId <= 0L) return emptyList()
        val url = BASE + "/artist/" + artistId + "/top?limit=" + limit
        val root = SearchHttp.jsonObject(url) ?: return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()
        return parseTracks(data, CatalogSource.DEEZER_ARTIST, artistFans)
    }

    /** Looks a name up and returns the fan count of the best matching artist. */
    fun artistFans(name: String): Long? {
        val artists = searchArtists(name, 3)
        if (artists.isEmpty()) return null
        val folded = SearchText.fold(name)
        val exact = artists.firstOrNull { SearchText.fold(it.name) == folded }
        val fans = (exact ?: artists.first()).fans
        return fans.takeIf { it > 0L }
    }

    private fun parseTracks(
        data: JSONArray,
        source: CatalogSource,
        artistFans: Long = 0L
    ): List<CatalogTrack> {
        val tracks = ArrayList<CatalogTrack>(data.length())
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            val artist = item.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
            if (title.isBlank() || artist.isBlank()) continue
            val album = item.optJSONObject("album")
            tracks += CatalogTrack(
                artist = artist,
                title = title,
                album = album?.optString("title")?.trim().orEmpty(),
                durationSeconds = item.optInt("duration", 0),
                coverUrl = album?.optString("cover_medium")?.trim().orEmpty(),
                popularity = item.optLong("rank", 0L),
                artistFans = artistFans,
                position = index,
                source = source
            )
        }
        return tracks
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
