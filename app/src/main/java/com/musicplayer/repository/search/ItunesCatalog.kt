package com.musicplayer.repository.search

import java.net.URLEncoder

/**
 * iTunes Search API. It works both as a second text catalog and as the source for
 * artists that Deezer does not index under their Cyrillic spelling.
 *
 * The API exposes no popularity rank, so its candidates are ranked with Deezer fan
 * counts collected for the artists they mention.
 */
internal object ItunesCatalog {

    private const val BASE = "https://itunes.apple.com"
    // The Russian storefront is missing a large part of the catalog (Deezer and Apple
    // both pulled out of Russia in 2022), so metadata is read from the US catalog.
    // Only titles, artists, duration and artwork are used from here: the audio itself
    // still comes from Hitmos.
    private const val COUNTRY = "us"

    data class Artist(
        val id: Long,
        val name: String
    )

    fun searchSongs(
        term: String,
        limit: Int,
        attribute: String? = null,
        source: CatalogSource = CatalogSource.ITUNES_SEARCH
    ): List<CatalogTrack> {
        var url = BASE + "/search?term=" + encode(term) + "&entity=song&limit=" + limit + "&country=" + COUNTRY
        if (!attribute.isNullOrBlank()) url += "&attribute=" + attribute
        val root = SearchHttp.jsonObject(url) ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        val tracks = ArrayList<CatalogTrack>(results.length())
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val title = item.optString("trackName").trim()
            val artist = item.optString("artistName").trim()
            if (title.isBlank() || artist.isBlank()) continue
            tracks += CatalogTrack(
                artist = artist,
                title = title,
                album = item.optString("collectionName").trim(),
                durationSeconds = item.optLong("trackTimeMillis", 0L).toInt() / 1000,
                coverUrl = artwork(item.optString("artworkUrl100")),
                popularity = 0L,
                artistFans = 0L,
                position = index,
                source = source
            )
        }
        return tracks
    }

    fun searchArtists(term: String, limit: Int): List<Artist> {
        val url = BASE + "/search?term=" + encode(term) + "&entity=musicArtist&limit=" + limit + "&country=" + COUNTRY
        val root = SearchHttp.jsonObject(url) ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        val artists = ArrayList<Artist>(results.length())
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val name = item.optString("artistName").trim()
            if (name.isBlank()) continue
            artists += Artist(id = item.optLong("artistId", 0L), name = name)
        }
        return artists
    }

    private fun artwork(url: String): String {
        if (url.isBlank()) return ""
        return url.replace("100x100", "600x600")
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
