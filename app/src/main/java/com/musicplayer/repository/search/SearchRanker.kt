package com.musicplayer.repository.search

import kotlin.math.log10
import kotlin.math.max

internal data class RankedTrack(
    val track: CatalogTrack,
    val score: Double
)

/**
 * Orders catalog candidates the way a person expects: the song or artist that was
 * typed first, covers and karaoke afterwards.
 *
 * The score mixes four independent signals:
 *  * how much of the query the artist name explains (gated by artist popularity, so
 *    a tiny band called exactly like a famous song cannot jump to the top);
 *  * how much of the query the artist + title explain;
 *  * how much of the query the title alone explains;
 *  * catalog popularity.
 *
 * Karaoke covers, tributes, live/remix edits and similar filler are penalised unless
 * the query itself asks for them.
 */
internal object SearchRanker {

    private const val W_ARTIST = 2.0
    private const val W_TEXT = 1.1
    private const val W_TITLE = 0.35
    private const val W_POPULARITY = 0.45
    private const val W_FANS = 1.2
    private const val W_POSITION = 0.2

    private const val SEED_ARTIST_GATE = 0.85
    private const val SPAM_PENALTY = 0.55
    private const val VERSION_PENALTY = 0.15
    private const val MAX_PENALTY = 1.1

    private val SPAM_WORDS = setOf(
        "karaoke", "караоке", "cover", "кавер", "tribute", "instrumental",
        "инструментал", "lullaby", "ringtone", "nightcore", "8bit", "minus",
        "минус", "backing track", "made famous", "in the style of"
    )

    private val VERSION_WORDS = setOf(
        "live", "концерт", "remix", "ремикс", "acoustic", "акустика", "slowed",
        "sped", "reverb", "demo", "remastered", "remaster", "version", "edit"
    )

    fun rank(query: String, tracks: List<CatalogTrack>): List<RankedTrack> {
        if (tracks.isEmpty()) return emptyList()
        val best = LinkedHashMap<String, RankedTrack>()
        for (track in tracks) {
            if (track.title.isBlank()) continue
            val key = dedupeKey(track)
            val candidate = RankedTrack(track, score(query, track))
            val existing = best[key]
            if (existing == null || candidate.score > existing.score) {
                best[key] = candidate
            }
        }
        return best.values.sortedWith(
            compareByDescending<RankedTrack> { it.score }.thenBy { it.track.position }
        )
    }

    /** Text-only relevance: how much of the query the artist and the title explain. */
    fun relevance(query: String, track: CatalogTrack): Double {
        val queryTokens = SearchText.keyTokens(query)
        if (queryTokens.isEmpty()) return 0.0
        val artistTokens = SearchText.keyTokens(track.artist)
        val titleTokens = SearchText.keyTokens(track.title)
        if (artistTokens.isEmpty() && titleTokens.isEmpty()) return 0.0

        val artistCoverage = max(
            SearchText.coverage(artistTokens, queryTokens),
            SearchText.coverage(queryTokens, artistTokens)
        )
        val artistGate = if (track.source.isArtistSeed) {
            max(fanGate(track.artistFans), SEED_ARTIST_GATE)
        } else {
            fanGate(track.artistFans)
        }
        val textCoverage = SearchText.coverage(queryTokens, artistTokens + titleTokens)
        val titleCoverage = SearchText.coverage(queryTokens, titleTokens)

        return W_ARTIST * artistCoverage * artistGate +
            W_TEXT * textCoverage +
            W_TITLE * titleCoverage
    }

    fun score(query: String, track: CatalogTrack): Double {
        var value = relevance(query, track)
        if (value <= 0.0) return 0.0

        value += W_POPULARITY * normalize(track.popularity, 6.0)
        value += W_FANS * fanAuthority(track.artistFans)

        // Both catalogs already order their own results by relevance and listens; the
        // decaying position prior keeps that signal without letting it dominate.
        if (track.source.isTextSearch) {
            value += W_POSITION * positionPrior(track.position)
        }

        value -= penalties(query, track)
        return value
    }

    private fun positionPrior(position: Int): Double {
        if (position < 0) return 0.0
        return 1.0 / (1.0 + 0.12 * position)
    }

    /** Popularity gate: unknown artists do not steal the top of the list. */
    private fun fanGate(fans: Long): Double {
        if (fans <= 0L) return 0.0
        return ((log10(1.0 + fans) - 2.0) / 3.0).coerceIn(0.0, 1.0)
    }

    /**
     * Artist authority on a log scale: 100 fans -> 0, 10k -> 0.38, 1M -> 0.8,
     * 10M -> 1.0. This is what keeps a cover with a few thousand listeners below the
     * original by a globally known artist.
     */
    private fun fanAuthority(fans: Long): Double {
        if (fans <= 0L) return 0.0
        return ((log10(1.0 + fans) - 2.0) / 5.0).coerceIn(0.0, 1.0)
    }

    private fun normalize(value: Long, scale: Double): Double {
        if (value <= 0L) return 0.0
        return (log10(1.0 + value) / scale).coerceIn(0.0, 1.0)
    }

    private fun penalties(query: String, track: CatalogTrack): Double {
        val haystack = " " + SearchText.fold(track.artist + " " + track.title) + " "
        val asked = " " + SearchText.fold(query) + " "
        var total = 0.0
        for (word in SPAM_WORDS) {
            val needle = " " + SearchText.fold(word) + " "
            if (haystack.contains(needle) && !asked.contains(needle)) total += SPAM_PENALTY
        }
        for (word in VERSION_WORDS) {
            val needle = " " + SearchText.fold(word) + " "
            if (haystack.contains(needle) && !asked.contains(needle)) total += VERSION_PENALTY
        }
        return total.coerceAtMost(MAX_PENALTY)
    }

    private fun dedupeKey(track: CatalogTrack): String {
        val artist = SearchText.fold(track.artist)
        val title = SearchText.fold(SearchText.matchable(track.title))
        return artist + "|" + title
    }
}
