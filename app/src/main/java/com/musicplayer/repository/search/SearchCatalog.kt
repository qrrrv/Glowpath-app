package com.musicplayer.repository.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.max

/**
 * Builds the candidate list for a query.
 *
 * Text candidates come from two catalogs at once: Deezer (popularity rank included)
 * and iTunes (the only one that still knows Russian artists by their Cyrillic name).
 * On top of that the query is resolved as an artist name; when a popular artist is
 * found its top tracks are seeded, which is what makes "kizaru" return kizaru songs
 * instead of tracks that merely mention him.
 *
 * Artist resolution is guarded by popularity: a tiny band whose name equals a famous
 * song title (for example "Birds Of A Feather", 171 Deezer fans) is never promoted
 * over the real song.
 */
internal object SearchCatalog {

    private const val TAG = "SearchCatalog"
    private const val TEXT_LIMIT = 30
    private const val SEED_TRACKS = 20
    private const val MIN_SEED_FANS = 10_000L
    private const val MIN_NAME_COVERAGE = 0.6
    private const val FAN_LOOKUPS = 16

    private data class Seed(
        val name: String,
        val fans: Long,
        val tracks: List<CatalogTrack>
    )

    private data class ArtistCandidate(
        val name: String,
        val relevance: Double,
        val position: Int
    )

    suspend fun resolve(query: String): List<CatalogTrack> = coroutineScope {
        val deezerText = async(Dispatchers.IO) {
            runCatching { DeezerCatalog.searchTracks(query, TEXT_LIMIT) }
                .onFailure { Log.w(TAG, "Deezer text search failed", it) }
                .getOrDefault(emptyList())
        }
        val itunesText = async(Dispatchers.IO) {
            runCatching { ItunesCatalog.searchSongs(query, TEXT_LIMIT) }
                .onFailure { Log.w(TAG, "iTunes text search failed", it) }
                .getOrDefault(emptyList())
        }
        val seedDeferred = async(Dispatchers.IO) {
            runCatching { resolveSeed(query) }
                .onFailure { Log.w(TAG, "Artist resolution failed", it) }
                .getOrNull()
        }

        val seed = seedDeferred.await()
        val text = deezerText.await() + itunesText.await()

        val tracks = ArrayList<CatalogTrack>(text.size + SEED_TRACKS)
        if (seed != null) tracks += seed.tracks
        for (track in text) {
            tracks += if (seed != null && sameArtist(track.artist, seed.name)) {
                track.copy(artistFans = seed.fans)
            } else {
                track
            }
        }

        enrichWithFanCounts(query, tracks)
    }

    /**
     * Deezer artist lookup gives a real authority signal to iTunes rows, which have no
     * popularity of their own. Without it a cover by a bedroom producer ranks exactly
     * like the original.
     */
    private suspend fun enrichWithFanCounts(
        query: String,
        tracks: List<CatalogTrack>
    ): List<CatalogTrack> {
        // Only artists whose rows actually match the query are worth a lookup; without
        // this ordering the budget was spent on the first ten search hits, which are
        // exactly the covers that need no authority signal.
        val candidates = LinkedHashMap<String, ArtistCandidate>()
        for (track in tracks) {
            if (track.artistFans > 0L) continue
            val folded = SearchText.fold(track.artist)
            if (folded.isEmpty()) continue
            val relevance = SearchRanker.relevance(query, track)
            val current = candidates[folded]
            if (current == null || relevance > current.relevance) {
                candidates[folded] = ArtistCandidate(track.artist, relevance, track.position)
            }
        }
        if (candidates.isEmpty()) return tracks

        val lookups = LinkedHashMap<String, String>()
        candidates.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, ArtistCandidate>> { it.value.relevance }
                    .thenBy { it.value.position }
            )
            .take(FAN_LOOKUPS)
            .forEach { lookups[it.key] = it.value.name }
        if (lookups.isEmpty()) return tracks

        val fans = coroutineScope {
            lookups.entries.map { entry ->
                async(Dispatchers.IO) {
                    entry.key to (runCatching { DeezerCatalog.artistFans(entry.value) }.getOrNull() ?: 0L)
                }
            }.awaitAll()
        }.toMap()

        return tracks.map { track ->
            if (track.artistFans > 0L) {
                track
            } else {
                val value = fans[SearchText.fold(track.artist)] ?: 0L
                if (value > 0L) track.copy(artistFans = value) else track
            }
        }
    }

    private fun resolveSeed(query: String): Seed? = deezerSeed(query) ?: itunesSeed(query)

    private fun deezerSeed(query: String): Seed? {
        val queryTokens = SearchText.keyTokens(query)
        if (queryTokens.isEmpty()) return null
        val artist = DeezerCatalog.searchArtists(query, 5).firstOrNull { candidate ->
            candidate.fans >= MIN_SEED_FANS &&
                nameCoverage(candidate.name, queryTokens) >= MIN_NAME_COVERAGE
        } ?: return null

        var tracks = DeezerCatalog.artistTop(artist.id, SEED_TRACKS, artist.fans)
        if (tracks.size < 3) {
            // Deezer top lists are geo filtered; fill the gap from the iTunes catalog.
            tracks = ItunesCatalog.searchSongs(
                artist.name,
                SEED_TRACKS,
                attribute = "artistTerm",
                source = CatalogSource.ITUNES_ARTIST
            )
                .filter { sameArtist(it.artist, artist.name) }
                .map { it.copy(artistFans = artist.fans) }
        }
        return if (tracks.isEmpty()) null else Seed(artist.name, artist.fans, tracks)
    }

    private fun itunesSeed(query: String): Seed? {
        val queryTokens = SearchText.keyTokens(query)
        if (queryTokens.isEmpty()) return null
        for (artist in ItunesCatalog.searchArtists(query, 4)) {
            if (nameCoverage(artist.name, queryTokens) < MIN_NAME_COVERAGE) continue
            val fans = DeezerCatalog.artistFans(artist.name) ?: continue
            if (fans < MIN_SEED_FANS) continue
            val tracks = ItunesCatalog.searchSongs(
                artist.name,
                SEED_TRACKS,
                attribute = "artistTerm",
                source = CatalogSource.ITUNES_ARTIST
            )
                .filter { sameArtist(it.artist, artist.name) }
                .map { it.copy(artistFans = fans) }
            if (tracks.isNotEmpty()) return Seed(artist.name, fans, tracks)
        }
        return null
    }

    private fun nameCoverage(name: String, queryTokens: List<String>): Double {
        val nameTokens = SearchText.keyTokens(name)
        if (nameTokens.isEmpty()) return 0.0
        return max(
            SearchText.coverage(nameTokens, queryTokens),
            SearchText.coverage(queryTokens, nameTokens)
        )
    }

    private fun sameArtist(first: String, second: String): Boolean {
        if (first.isBlank() || second.isBlank()) return false
        if (SearchText.fold(first) == SearchText.fold(second)) return true
        val firstTokens = SearchText.keyTokens(first)
        val secondTokens = SearchText.keyTokens(second)
        return SearchText.coverage(firstTokens, secondTokens) >= 0.8 ||
            SearchText.coverage(secondTokens, firstTokens) >= 0.8
    }
}
