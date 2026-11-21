package com.musicplayer.repository

import android.util.Log
import com.musicplayer.data.ArtistAlbum
import com.musicplayer.data.ArtistPage
import com.musicplayer.data.ArtistProfile
import com.musicplayer.data.ArtistVideo
import com.musicplayer.data.RelatedArtist
import com.musicplayer.repository.search.SearchHttp
import com.musicplayer.repository.search.SearchText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Artist pages: related artists and releases come from Deezer, music videos from the
 * iTunes catalog (the same two sources online search already uses).
 *
 * Results are cached for ten minutes so switching between artists does not repeat the
 * same four network calls.
 */
object ArtistRepository {

    private const val TAG = "ArtistRepository"
    private const val DEEZER = "https://api.deezer.com"
    private const val ITUNES = "https://itunes.apple.com"
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val CACHE_LIMIT = 24

    private data class CacheEntry(val createdAt: Long, val page: ArtistPage)

    private val cache = LinkedHashMap<String, CacheEntry>()
    private val cacheLock = Any()

    suspend fun load(name: String, force: Boolean = false): ArtistPage? = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext null
        val key = SearchText.fold(clean).ifEmpty { clean.lowercase() }

        synchronized(cacheLock) {
            val cached = cache[key]
            if (!force && cached != null && System.currentTimeMillis() - cached.createdAt < CACHE_TTL_MS) {
                return@withContext cached.page
            }
        }

        val page = runCatching { fetch(clean) }
            .onFailure { Log.w(TAG, "Artist lookup failed for " + clean, it) }
            .getOrNull()

        if (page != null) {
            synchronized(cacheLock) {
                cache[key] = CacheEntry(System.currentTimeMillis(), page)
                while (cache.size > CACHE_LIMIT) {
                    val oldest = cache.keys.firstOrNull() ?: break
                    cache.remove(oldest)
                }
            }
        }
        page
    }

    private fun fetch(name: String): ArtistPage? {
        val search = SearchHttp.jsonObject(DEEZER + "/search/artist?q=" + encode(name) + "&limit=1")
            ?: return null
        val first = search.optJSONArray("data")?.optJSONObject(0) ?: return null
        val artistId = first.optLong("id", 0L)
        if (artistId <= 0L) return null

        val details = SearchHttp.jsonObject(DEEZER + "/artist/" + artistId) ?: first
        val profile = ArtistProfile(
            id = artistId,
            name = details.optString("name").ifBlank { first.optString("name") }.trim(),
            imageUrl = details.optString("picture_xl").ifBlank { details.optString("picture_big") },
            fans = details.optLong("nb_fan", 0L),
            albumCount = details.optInt("nb_album", 0),
            link = details.optString("link")
        )
        if (profile.name.isBlank()) return null

        val related = parseRelated(SearchHttp.jsonObject(DEEZER + "/artist/" + artistId + "/related?limit=12"))
        val albums = parseAlbums(SearchHttp.jsonObject(DEEZER + "/artist/" + artistId + "/albums?limit=12"))
        val videos = fetchVideos(profile.name)

        return ArtistPage(profile = profile, related = related, albums = albums, videos = videos)
    }

    private fun parseRelated(root: JSONObject?): List<RelatedArtist> {
        val data = root?.optJSONArray("data") ?: return emptyList()
        val result = ArrayList<RelatedArtist>(data.length())
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val artistName = item.optString("name").trim()
            if (artistName.isBlank()) continue
            result += RelatedArtist(
                id = item.optLong("id", 0L),
                name = artistName,
                imageUrl = item.optString("picture_medium").ifBlank { item.optString("picture_big") }
            )
        }
        return result.distinctBy { SearchText.fold(it.name) }.take(10)
    }

    private fun parseAlbums(root: JSONObject?): List<ArtistAlbum> {
        val data = root?.optJSONArray("data") ?: return emptyList()
        val result = ArrayList<ArtistAlbum>(data.length())
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            if (title.isBlank()) continue
            val release = item.optString("release_date").trim()
            result += ArtistAlbum(
                id = item.optLong("id", 0L),
                title = title,
                coverUrl = item.optString("cover_medium").ifBlank { item.optString("cover_big") },
                year = if (release.length >= 4) release.substring(0, 4) else "",
                kind = recordType(item.optString("record_type")),
                link = item.optString("link")
            )
        }
        return result.distinctBy { it.id }.take(12)
    }

    private fun fetchVideos(artist: String): List<ArtistVideo> {
        val url = ITUNES + "/search?term=" + encode(artist) + "&entity=musicVideo&limit=12&country=us"
        val root = SearchHttp.jsonObject(url) ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        val expected = SearchText.fold(artist)
        val result = ArrayList<ArtistVideo>(results.length())
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val credited = item.optString("artistName").trim()
            if (!sameArtist(expected, credited)) continue
            val preview = item.optString("previewUrl").trim()
            val storePage = item.optString("trackViewUrl").trim()
            val target = preview.ifBlank { storePage }
            if (target.isBlank()) continue
            result += ArtistVideo(
                id = item.optLong("trackId", 0L),
                title = item.optString("trackName").trim(),
                artist = credited,
                thumbnailUrl = item.optString("artworkUrl100").replace("100x100", "600x600"),
                externalUrl = target
            )
        }
        return result.distinctBy { SearchText.fold(it.title) }.take(10)
    }

    private fun sameArtist(expected: String, credited: String): Boolean {
        val folded = SearchText.fold(credited)
        if (expected.isEmpty() || folded.isEmpty()) return false
        if (folded == expected) return true
        return folded.contains(expected) || expected.contains(folded)
    }

    private fun recordType(value: String): String = when (value.lowercase()) {
        "album" -> "Альбом"
        "single" -> "Сингл"
        "ep" -> "EP"
        "compile" -> "Сборник"
        else -> ""
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
