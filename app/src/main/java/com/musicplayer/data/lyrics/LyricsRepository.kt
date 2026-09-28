package com.musicplayer.data.lyrics

import android.content.Context
import android.util.Log
import com.musicplayer.data.Song
import com.musicplayer.data.network.BetterLyricsApiService
import com.musicplayer.data.network.GeniusApiService
import com.musicplayer.data.network.LrcLibApiService
import com.musicplayer.data.network.LrcLibResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * Lyrics repository.
 * Adapted from PixelPlay's LyricsRepositoryImpl.kt for our architecture:
 *  – No Hilt (manual instantiation)
 *  – No Room (SharedPreferences + JSON file cache)
 *  – No TagLib (skip embedded lyrics extraction)
 *  – Uses plain HttpURLConnection via LrcLibApiService
 *
 * Source priority (default): API → Local LRC file
 */
class LyricsRepository(private val context: Context) {

    companion object {
        private const val TAG = "LyricsRepository"
        private const val CACHE_DIR = "lyrics"
        private const val CACHE_VERSION = 2
        private const val MAX_CACHE_SIZE = 150
        private const val LRCLIB_MIN_DELAY_MS = 120L
    }

    // In-memory LRU cache (access-order LinkedHashMap)
    private val memCache = object : LinkedHashMap<Long, Lyrics>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Lyrics>?) =
            size > MAX_CACHE_SIZE
    }

    // Rate limiting
    private var lastApiCallMs = 0L

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Main entry: fetch lyrics for a song.
     * Order: memory cache → disk JSON cache → local .lrc file → BetterLyrics TTML
     * → LRCLIB API → Genius API (only when configured with a real token).
     * Returns null if nothing found.
     */
    suspend fun getLyrics(song: Song, forceRefresh: Boolean = false, useGenius: Boolean = true): Lyrics? =
        withContext(Dispatchers.IO) {
            // 1. Memory cache
            if (!forceRefresh) {
                synchronized(memCache) { memCache[song.id] }?.let { return@withContext it }
            }

            // 2. Disk JSON cache (previously fetched from API)
            if (!forceRefresh) {
                loadFromDiskCache(song.id)?.let { cached ->
                    if (cached.isValid()) {
                        cacheInMemory(song.id, cached)
                        return@withContext cached
                    }
                }
            }

            // 3. Local .lrc file next to the audio file
            findLocalLrcFile(song)?.let { local ->
                cacheInMemory(song.id, local)
                return@withContext local
            }

            // 4. BetterLyrics: Apple-style TTML with line and word timestamps.
            fetchFromBetterLyrics(song)?.let { remote ->
                cacheInMemory(song.id, remote)
                saveToDiskCache(song.id, remote)
                return@withContext remote
            }

            // 5. LRCLIB API
            fetchFromApi(song)?.let { remote ->
                cacheInMemory(song.id, remote)
                saveToDiskCache(song.id, remote)
                return@withContext remote
            }

            // 6. Genius API (fallback only when a real token is configured).
            if (useGenius && GeniusApiService.isConfigured) {
                fetchFromGenius(song)?.let { genius ->
                    cacheInMemory(song.id, genius)
                    saveToDiskCache(song.id, genius)
                    return@withContext genius
                }
            }

            null
        }

    /**
     * Clear cached lyrics for a song (force re-fetch next time).
     */
    fun clearCache(songId: Long) {
        synchronized(memCache) { memCache.remove(songId) }
        try {
            File(context.filesDir, "$CACHE_DIR/$songId.json").delete()
        } catch (_: Exception) {}
    }

    // ── LRCLIB API ────────────────────────────────────────────────────────────

    private suspend fun fetchFromApi(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        // Rate limiting
        val now = System.currentTimeMillis()
        val wait = LRCLIB_MIN_DELAY_MS - (now - lastApiCallMs)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastApiCallMs = System.currentTimeMillis()

        val cleanArtist  = song.artist.trim().replace(Regex("\\(.*?\\)"), "").trim()
        val cleanTitle   = song.title.trim().replace(Regex("\\(.*?\\)"), "").trim()

        // Simplified artist/title for songs with feat. tags.
        val simpleArtist = cleanArtist.split(" feat.", " ft.", " featuring").first().trim()
        val simpleTitle  = cleanTitle.split(" feat.", " ft.", " featuring").first().trim()

        // Ask the exact endpoint first, then use several search variants.
        val strategies: List<Pair<String, suspend () -> List<LrcLibResponse>>> = buildList {
            add("track+artist"   to { LrcLibApiService.searchLyrics(trackName = cleanTitle,  artistName = cleanArtist) })
            add("combined_query" to { LrcLibApiService.searchLyrics(query = "$cleanArtist $cleanTitle") })
            if (simpleArtist != cleanArtist || simpleTitle != cleanTitle) {
                add("simplified" to { LrcLibApiService.searchLyrics(trackName = simpleTitle, artistName = simpleArtist) })
            }
        }

        val exact = runCatching {
            LrcLibApiService.getLyrics(
                trackName = cleanTitle,
                artistName = cleanArtist,
                albumName = song.album,
                duration = (song.duration / 1000L).toInt()
            )
        }.getOrNull()
        val results = buildList {
            exact?.let(::add)
            addAll(runStrategiesParallel(strategies))
        }.distinctBy { it.id }
        if (results.isEmpty()) {
            Log.d(TAG, "No results from LRCLIB for: ${song.artist} - ${song.title}")
            return@withContext null
        }

        val songDurationSec = song.duration / 1000.0

        // Rank every result instead of trusting LRCLIB's response order.
        val best = results
            .filter { it.hasLyrics() }
            .maxByOrNull { result ->
                val titleScore = textMatchScore(cleanTitle, result.name)
                val artistScore = textMatchScore(cleanArtist, result.artistName)
                val durationDiff = abs(result.duration - songDurationSec)
                val durationScore = when {
                    durationDiff <= 2.0 -> 1.0
                    durationDiff <= 5.0 -> 0.6
                    durationDiff <= 10.0 -> 0.2
                    else -> -1.0
                }
                titleScore + artistScore + durationScore + if (result.syncedLyrics != null) 0.15 else 0.0
            }

        val bestScore = best?.let { result ->
            textMatchScore(cleanTitle, result.name) + textMatchScore(cleanArtist, result.artistName)
        } ?: 0.0

        if (best == null || bestScore < 1.0) {
            Log.d(TAG, "No suitable match in LRCLIB results for: ${song.title}")
            return@withContext null
        }

        val raw = best.syncedLyrics ?: best.plainLyrics ?: return@withContext null
        val parsed = LyricsUtils.parseLyrics(raw).copy(areFromRemote = true)

        if (!parsed.isValid()) return@withContext null

        Log.d(TAG, "LRCLIB hit for '${song.title}' — synced=${best.syncedLyrics != null}")
        parsed
    }

    // ── BetterLyrics TTML ─────────────────────────────────────────────────────

    private suspend fun fetchFromBetterLyrics(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val cleanArtist = cleanMetadata(song.artist)
        val cleanTitle = cleanMetadata(song.title)
        val ttml = BetterLyricsApiService.getTtmlLyrics(
            title = cleanTitle,
            artist = cleanArtist,
            album = cleanMetadata(song.album),
            durationSeconds = (song.duration / 1000L).toInt()
        ) ?: return@withContext null

        TtmlLyricsParser.parse(ttml)?.takeIf { it.isValid() }?.also {
            Log.d(TAG, "BetterLyrics hit for '${song.title}' — word-synced")
        }
    }

    private fun cleanMetadata(value: String): String = value
        .trim()
        .replace(Regex("\\s*\\((?i:official|video|audio|lyrics|lyric|live|acoustic|remix|feat\\.).*?\\)"), "")
        .replace(Regex("\\s*\\[(?i:official|video|audio|lyrics|lyric|live|acoustic|remix|feat\\.).*?\\]"), "")
        .trim()

    private fun textMatchScore(expected: String, actual: String): Double {
        val left = normalizeForMatch(expected)
        val right = normalizeForMatch(actual)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 1.0
        if (left.contains(right) || right.contains(left)) return 0.82
        val leftWords = left.split(' ').toSet()
        val rightWords = right.split(' ').toSet()
        return (leftWords.intersect(rightWords).size.toDouble() /
            maxOf(leftWords.size, rightWords.size)).coerceIn(0.0, 0.75)
    }

    private fun normalizeForMatch(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    // ── Local LRC file ────────────────────────────────────────────────────────

    private suspend fun findLocalLrcFile(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val songFile  = File(song.uri.path ?: return@withContext null)
            val directory = songFile.parentFile ?: return@withContext null
            if (!directory.exists()) return@withContext null

            val nameNoExt = songFile.nameWithoutExtension
            val candidates = listOf(
                File(directory, "$nameNoExt.lrc"),
                File(directory, "${song.artist.replace(Regex("[^a-zA-Z0-9]"), "_")}_${song.title.replace(Regex("[^a-zA-Z0-9]"), "_")}.lrc")
            )
            for (f in candidates) {
                if (f.exists() && f.canRead()) {
                    val parsed = LyricsUtils.parseLyrics(f.readText())
                    if (parsed.isValid()) {
                        Log.d(TAG, "Found local LRC: ${f.name}")
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning local LRC: ${e.message}")
        }
        null
    }

    // ── Disk JSON cache ───────────────────────────────────────────────────────

    private fun saveToDiskCache(songId: Long, lyrics: Lyrics) {
        try {
            val dir = File(context.filesDir, CACHE_DIR).also { it.mkdirs() }
            val synced = lyrics.synced?.let { LyricsUtils.syncedToLrcString(it) }
            val plain  = lyrics.plain?.joinToString("\n")
            val json   = JSONObject().apply {
                put("version", CACHE_VERSION)
                put("syncedLyrics", synced ?: JSONObject.NULL)
                put("plainLyrics",  plain  ?: JSONObject.NULL)
            }
            File(dir, "$songId.json").writeText(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Error saving disk cache: ${e.message}")
        }
    }

    private fun loadFromDiskCache(songId: Long): Lyrics? {
        return try {
            val file = File(context.filesDir, "$CACHE_DIR/$songId.json")
            if (!file.exists()) return null
            val obj    = JSONObject(file.readText())
            if (obj.optInt("version", 0) != CACHE_VERSION) return null
            val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            val plain  = obj.optString("plainLyrics").takeIf  { it.isNotBlank() && it != "null" }
            val raw    = synced ?: plain ?: return null
            val parsed = LyricsUtils.parseLyrics(raw)
            if (parsed.isValid()) parsed else null
        } catch (e: Exception) {
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cacheInMemory(songId: Long, lyrics: Lyrics) {
        synchronized(memCache) { memCache[songId] = lyrics }
    }

    /** Run all search strategies concurrently and merge every response safely. */
    private suspend fun runStrategiesParallel(
        strategies: List<Pair<String, suspend () -> List<LrcLibResponse>>>
    ): List<LrcLibResponse> = coroutineScope {
        strategies.map { (_, request) ->
            async { runCatching { request() }.getOrNull().orEmpty() }
        }.map { it.await() }
            .flatten()
            .distinctBy { it.id }
    }

    private fun LrcLibResponse.hasLyrics() =
        !plainLyrics.isNullOrBlank() || !syncedLyrics.isNullOrBlank()

    // ── Genius API ────────────────────────────────────────────────────────────

    /**
     * Fetch lyrics from Genius API (fallback когда LRCLIB не находит)
     */
    private suspend fun fetchFromGenius(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Trying Genius API for: ${song.artist} - ${song.title}")

            val songUrl = GeniusApiService.searchSong(song.artist, song.title)
                ?: return@withContext null

            val lyricsText = GeniusApiService.fetchLyrics(songUrl)
                ?: return@withContext null

            // Genius возвращает plain текст (без синхронизации)
            val lines = lyricsText.split("\n").filter { it.isNotBlank() }
            val parsed = Lyrics(
                synced = null,
                plain = lines,
                areFromRemote = true
            )

            Log.d(TAG, "Genius hit for '${song.title}'")
            parsed

        } catch (e: Exception) {
            Log.e(TAG, "Genius API error: ${e.message}")
            null
        }
    }
}
