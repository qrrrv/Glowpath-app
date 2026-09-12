package com.musicplayer.data.lyrics

import android.content.Context
import android.util.Log
import com.musicplayer.data.Song
import com.musicplayer.data.network.GeniusApiService
import com.musicplayer.data.network.LrcLibApiService
import com.musicplayer.data.network.LrcLibResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
     * Order: memory cache → disk JSON cache → local .lrc file → LRCLIB API → Genius API.
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

            // 4. LRCLIB API
            fetchFromApi(song)?.let { remote ->
                cacheInMemory(song.id, remote)
                saveToDiskCache(song.id, remote)
                return@withContext remote
            }

            // 5. Genius API (fallback, если включено)
            if (useGenius) {
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

        // Simplified artist/title for songs with feat. tags
        val simpleArtist = cleanArtist.split(" feat.", " ft.", " featuring").first().trim()
        val simpleTitle  = cleanTitle.split(" feat.", " ft.", " featuring").first().trim()

        // Run strategies in parallel and take first non-empty result
        val strategies: List<Pair<String, suspend () -> List<LrcLibResponse>>> = buildList {
            add("track+artist"   to { LrcLibApiService.searchLyrics(trackName = cleanTitle,  artistName = cleanArtist) })
            add("combined_query" to { LrcLibApiService.searchLyrics(query = "$cleanArtist $cleanTitle") })
            if (simpleArtist != cleanArtist || simpleTitle != cleanTitle) {
                add("simplified" to { LrcLibApiService.searchLyrics(trackName = simpleTitle, artistName = simpleArtist) })
            }
        }

        val results = runStrategiesParallel(strategies)
        if (results.isEmpty()) {
            Log.d(TAG, "No results from LRCLIB for: ${song.artist} - ${song.title}")
            return@withContext null
        }

        val songDurationSec = song.duration / 1000.0

        // Best match: exact artist+title+duration, then synced, then any
        val best = results.firstOrNull { r ->
            val artistMatch = r.artistName.lowercase().contains(cleanArtist.lowercase()) ||
                    cleanArtist.lowercase().contains(r.artistName.lowercase())
            val titleMatch  = r.name.lowercase().contains(cleanTitle.lowercase()) ||
                    cleanTitle.lowercase().contains(r.name.lowercase())
            artistMatch && titleMatch && abs(r.duration - songDurationSec) <= 15 && r.hasLyrics()
        } ?: results.firstOrNull { abs(it.duration - songDurationSec) <= 15 && it.syncedLyrics != null }
          ?: results.firstOrNull { abs(it.duration - songDurationSec) <= 15 && it.hasLyrics() }

        if (best == null) {
            Log.d(TAG, "No suitable match in LRCLIB results for: ${song.title}")
            return@withContext null
        }

        val raw = best.syncedLyrics ?: best.plainLyrics ?: return@withContext null
        val parsed = LyricsUtils.parseLyrics(raw).copy(areFromRemote = true)

        if (!parsed.isValid()) return@withContext null

        Log.d(TAG, "LRCLIB hit for '${song.title}' — synced=${best.syncedLyrics != null}")
        parsed
    }

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

    /**
     * Run all search strategies in parallel, return results from the first non-empty response.
     */
    private suspend fun runStrategiesParallel(
        strategies: List<Pair<String, suspend () -> List<LrcLibResponse>>>
    ): List<LrcLibResponse> = coroutineScope {
        if (strategies.isEmpty()) return@coroutineScope emptyList()
        val channel = Channel<List<LrcLibResponse>>(capacity = strategies.size)
        val jobs = strategies.map { (name, request) ->
            launch {
                val results = runCatching { request() }.getOrNull() ?: emptyList()
                channel.trySend(results)
            }
        }
        var found = emptyList<LrcLibResponse>()
        repeat(strategies.size) {
            val batch = channel.receive()
            if (batch.isNotEmpty() && found.isEmpty()) {
                found = batch.distinctBy { it.id }
                jobs.forEach { it.cancel() }
            }
        }
        channel.close()
        found
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
