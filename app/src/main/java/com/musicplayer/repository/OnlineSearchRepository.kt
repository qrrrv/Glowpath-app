package com.musicplayer.repository

import android.util.Log
import com.musicplayer.data.OnlineSearchPayload
import com.musicplayer.data.OnlineSong
import com.musicplayer.repository.search.CatalogTrack
import com.musicplayer.repository.search.SearchCatalog
import com.musicplayer.repository.search.SearchRanker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Online search: Deezer catalog for metadata and relevance, Hitmos only as the file
 * source (Deezer exposes no MP3 URL).
 *
 * The catalog layer returns up to 24 candidates ordered by relevance instead of the
 * first ten raw Deezer hits. Results are resolved against Hitmos in small batches and
 * published as soon as a batch is ready, so the screen fills progressively.
 */
object OnlineSearchRepository {

    private const val TAG = "OnlineSearchRepository"
    private const val MAX_CANDIDATES = 24
    private const val MAX_RESULTS = 20
    private const val CHUNK_SIZE = 6

    suspend fun search(
        query: String,
        onPartial: (List<OnlineSong>) -> Unit = {}
    ): OnlineSearchPayload = coroutineScope {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@coroutineScope OnlineSearchPayload(emptyList(), emptyList())
        }

        val candidates = withContext(Dispatchers.IO) {
            runCatching { SearchCatalog.resolve(cleanQuery) }
                .onFailure { Log.w(TAG, "Catalog search failed", it) }
                .getOrDefault(emptyList())
        }
        if (candidates.isEmpty()) {
            return@coroutineScope OnlineSearchPayload(emptyList(), emptyList())
        }

        val ordered = SearchRanker.rank(cleanQuery, candidates)
            .map { it.track }
            .take(MAX_CANDIDATES)

        val resolved = LinkedHashMap<String, OnlineSong>()
        var index = 0
        while (index < ordered.size && resolved.size < MAX_RESULTS) {
            if (!isActive) break
            val end = minOf(index + CHUNK_SIZE, ordered.size)
            val chunk = ordered.subList(index, end)
            index = end

            val batch = coroutineScope {
                chunk.map { track ->
                    async(Dispatchers.IO) { resolveTrack(track) }
                }.awaitAll()
            }

            var added = false
            for (song in batch) {
                if (song == null) continue
                if (resolved.putIfAbsent(song.downloadUrl, song) == null) added = true
            }
            if (added) onPartial(resolved.values.toList())
        }

        OnlineSearchPayload(songs = resolved.values.toList(), albums = emptyList())
    }

    private suspend fun resolveTrack(track: CatalogTrack): OnlineSong? {
        val match = try {
            HitmosRepository.findBestMatch(track.artist, track.title, track.durationSeconds)
        } catch (e: Exception) {
            Log.d(TAG, "Hitmos resolution failed for " + track.artist + " - " + track.title, e)
            return null
        } ?: return null

        return match.copy(
            title = track.title,
            artist = track.artist,
            duration = formatDuration(track.durationSeconds).ifBlank { match.duration },
            coverUrl = track.coverUrl.ifBlank { match.coverUrl }
        )
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return ""
        return (seconds / 60).toString() + ":" + (seconds % 60).toString().padStart(2, '0')
    }
}
