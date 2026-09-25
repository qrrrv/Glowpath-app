package com.musicplayer.repository

import android.util.Log
import com.musicplayer.data.OnlineAlbumSummary
import com.musicplayer.data.OnlineSearchPayload
import com.musicplayer.data.OnlineSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import kotlin.math.max

/**
 * Resolves the query against real music catalogs (Deezer + iTunes),
 * then ranks Hitmos results so the typed track is first — not random "NN" songs.
 */
object OnlineSearchRepository {

    suspend fun search(query: String): OnlineSearchPayload = coroutineScope {
        val clean = query.trim()
        if (clean.isBlank()) return@coroutineScope OnlineSearchPayload(emptyList(), emptyList())

        val catalogDeferred = async { MusicCatalogApi.resolve(clean) }
        val primaryDeferred = async { HitmosRepository.search(clean) }

        val catalog = catalogDeferred.await()
        val primary = primaryDeferred.await()

        val canonical = catalog.firstOrNull()
            ?.takeIf { SearchRanker.coverage(clean, "${it.artist} ${it.title}") >= 0.45f }
        val extraQuery = canonical
            ?.let { "${it.artist} ${it.title}".trim() }
            ?.takeIf { SearchRanker.normalize(it) != SearchRanker.normalize(clean) }

        val extra = if (extraQuery != null) {
            runCatching { HitmosRepository.search(extraQuery) }.getOrNull()
        } else {
            null
        }

        val songs = SearchRanker.rankSongs(
            query = clean,
            songs = primary.songs + extra?.songs.orEmpty(),
            catalog = catalog
        )
        val albums = SearchRanker.rankAlbums(
            query = clean,
            albums = primary.albums + extra?.albums.orEmpty(),
            catalog = catalog
        )
        OnlineSearchPayload(songs = songs, albums = albums)
    }
}

data class CatalogTrack(
    val artist: String,
    val title: String,
    val album: String = "",
    val coverUrl: String = ""
)

private object MusicCatalogApi {
    private const val TAG = "MusicCatalog"

    suspend fun resolve(query: String): List<CatalogTrack> = withContext(Dispatchers.IO) {
        val deezer = runCatching { searchDeezer(query) }.onFailure {
            Log.w(TAG, "Deezer failed", it)
        }.getOrDefault(emptyList())
        val itunes = runCatching { searchItunes(query) }.onFailure {
            Log.w(TAG, "iTunes failed", it)
        }.getOrDefault(emptyList())
        mergeCatalog(deezer + itunes)
    }

    private fun searchDeezer(query: String): List<CatalogTrack> {
        val url = "https://api.deezer.com/search?q=${enc(query)}&limit=8"
        val root = JSONObject(httpGet(url))
        val data = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val title = item.optString("title").ifBlank { item.optString("title_short") }
                val artist = item.optJSONObject("artist")?.optString("name").orEmpty()
                if (title.isBlank() || artist.isBlank()) continue
                val album = item.optJSONObject("album")
                add(
                    CatalogTrack(
                        artist = artist,
                        title = title,
                        album = album?.optString("title").orEmpty(),
                        coverUrl = album?.optString("cover_medium")
                            ?.ifBlank { album.optString("cover") }
                            .orEmpty()
                    )
                )
            }
        }
    }

    private fun searchItunes(query: String): List<CatalogTrack> {
        val cyrillic = query.any { it in '\u0400'..'\u04FF' }
        val countries = if (cyrillic) listOf("ru") else listOf("ru", "us")
        return countries.flatMap { country ->
            val url =
                "https://itunes.apple.com/search?term=${enc(query)}&entity=song&limit=6&country=$country"
            val root = JSONObject(httpGet(url))
            val results = root.optJSONArray("results") ?: JSONArray()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val title = item.optString("trackName")
                    val artist = item.optString("artistName")
                    if (title.isBlank() || artist.isBlank()) continue
                    add(
                        CatalogTrack(
                            artist = artist,
                            title = title,
                            album = item.optString("collectionName"),
                            coverUrl = item.optString("artworkUrl100")
                                .replace("100x100bb", "300x300bb")
                        )
                    )
                }
            }
        }
    }

    private fun mergeCatalog(tracks: List<CatalogTrack>): List<CatalogTrack> {
        val seen = linkedSetOf<String>()
        return tracks.filter { track ->
            val key = SearchRanker.normalize("${track.artist} ${track.title}")
            key.isNotBlank() && seen.add(key)
        }.take(8)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun httpGet(url: String): String {
        return Jsoup.connect(url)
            .ignoreContentType(true)
            .followRedirects(true)
            .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/json")
            .timeout(8_000)
            .execute()
            .body()
    }
}

internal object SearchRanker {
    private val junkTitle = Regex(
        """(?i)(karaoke|караоке|minusovk|минусовк|\bminus\b|\bминус\b|instrumental|инструментал|nightcore|slowed|reverb|sped up|speed up|8d\b|tiktok|ai cover|cover by|на пианино|\bpiano\b|\bmidi\b)"""
    )
    private val remixWords = Regex("""(?i)(remix|ремикс|mashup|bootleg|club mix)""")
    private val junkArtist = setOf(
        "nn", "нн", "n.n", "n.n.", "unknown", "unknown artist",
        "неизвестен", "неизвестный", "various", "various artists", "ost", "soundtrack"
    )

    fun rankSongs(
        query: String,
        songs: List<OnlineSong>,
        catalog: List<CatalogTrack>
    ): List<OnlineSong> {
        val seen = linkedSetOf<String>()
        val scored = songs
            .filter { it.downloadUrl.isNotBlank() && seen.add(it.downloadUrl) }
            .map { song ->
                val enriched = enrichCover(song, catalog)
                scoredSong(query, enriched, catalog)
            }
            .sortedByDescending { it.second }

        val strong = scored.filter { it.second >= 0.42f }
        val picked = if (strong.size >= 3) strong else scored.filter { it.second >= 0.18f }
        return (picked.ifEmpty { scored }).map { it.first }
    }

    fun rankAlbums(
        query: String,
        albums: List<OnlineAlbumSummary>,
        catalog: List<CatalogTrack>
    ): List<OnlineAlbumSummary> {
        val seen = linkedSetOf<String>()
        return albums
            .filter { it.albumUrl.isNotBlank() && seen.add(it.albumUrl) }
            .map { album -> album to scoreText(query, "${album.artist} ${album.title}", catalog) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun coverage(query: String, haystack: String): Float {
        val q = tokens(query)
        val h = tokens(haystack)
        if (q.isEmpty()) return 0f
        val hits = q.count { qt -> h.any { it == qt || it.startsWith(qt) || qt.startsWith(it) && it.length >= 3 } }
        return hits.toFloat() / q.size
    }

    fun normalize(raw: String): String {
        return raw.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun scoredSong(
        query: String,
        song: OnlineSong,
        catalog: List<CatalogTrack>
    ): Pair<OnlineSong, Float> {
        val hay = "${song.artist} ${song.title}"
        var score = scoreText(query, hay, catalog)

        val artistNorm = normalize(song.artist)
        if (artistNorm in junkArtist || artistNorm.matches(Regex("""\d{2,}"""))) {
            score -= 0.45f
        }
        val titleNorm = normalize(song.title)
        val queryNorm = normalize(query)
        if (junkTitle.containsMatchIn(song.title) && !junkTitle.containsMatchIn(query)) {
            score -= 0.35f
        }
        if (remixWords.containsMatchIn(song.title) && !remixWords.containsMatchIn(query)) {
            score -= 0.12f
        }
        if (titleNorm.length <= 1) score -= 0.4f
        if (coverage(query, song.title) >= 0.99f && coverage(query, song.artist) >= 0.5f) {
            score += 0.25f
        }
        if (queryNorm.isNotBlank() && (titleNorm == queryNorm || hay.let { normalize(it) } == queryNorm)) {
            score += 0.2f
        }
        return song to score
    }

    private fun scoreText(query: String, haystack: String, catalog: List<CatalogTrack>): Float {
        var score = coverage(query, haystack)
        if (tokens(query).isNotEmpty() && tokens(haystack).containsAll(tokens(query))) {
            score += 0.25f
        }
        catalog.forEachIndexed { index, track ->
            val canonical = "${track.artist} ${track.title}"
            val sim = max(
                coverage(canonical, haystack),
                coverage(haystack, canonical)
            )
            if (sim >= 0.55f) {
                score += (0.55f - index * 0.04f) * sim
            }
        }
        return score
    }

    private fun enrichCover(song: OnlineSong, catalog: List<CatalogTrack>): OnlineSong {
        if (song.coverUrl.isNotBlank() || catalog.isEmpty()) return song
        val match = catalog.firstOrNull { track ->
            max(
                coverage("${track.artist} ${track.title}", "${song.artist} ${song.title}"),
                coverage("${song.artist} ${song.title}", "${track.artist} ${track.title}")
            ) >= 0.7f
        } ?: return song
        return song.copy(coverUrl = match.coverUrl)
    }

    private fun tokens(raw: String): List<String> {
        return normalize(raw)
            .split(' ')
            .filter { it.length >= 2 || it.any { ch -> ch in '\u0400'..'\u04FF' } }
    }
}
