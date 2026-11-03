package com.musicplayer.repository.search

import com.musicplayer.data.OnlineSong
import kotlin.math.abs
import kotlin.math.max

/**
 * Picks the Hitmos file that belongs to a catalog track.
 *
 * Exact string equality (the previous behaviour) rejected almost every versioned
 * release: "Numb - Remastered 2011" against "Numb", "Мокрые кроссы" against
 * "МОКРЫЕ КРОССЫ" or a collaboration credited as "Kizaru, ALBLAK 52". The matcher
 * compares token sets instead, so those tracks still resolve while a cover by an
 * unrelated artist stays rejected.
 */
internal object TrackMatcher {

    private const val MIN_MATCH_SCORE = 0.68
    private const val TITLE_WEIGHT = 0.62
    private const val ARTIST_WEIGHT = 0.38
    private const val DURATION_BONUS = 0.06
    private const val DURATION_MISS = 0.2
    private const val VERSION_PENALTY = 0.08

    private val VERSION_WORDS = setOf(
        "live", "remix", "ремикс", "slowed", "sped", "reverb", "karaoke",
        "караоке", "instrumental", "инструментал", "cover", "кавер"
    )

    fun bestMatch(
        songs: List<OnlineSong>,
        artist: String,
        title: String,
        durationSeconds: Int = 0
    ): OnlineSong? {
        if (songs.isEmpty()) return null
        var best: OnlineSong? = null
        var bestScore = 0.0
        for (song in songs) {
            val score = score(song, artist, title, durationSeconds)
            if (score > bestScore) {
                bestScore = score
                best = song
            }
        }
        return if (bestScore >= MIN_MATCH_SCORE) best else null
    }

    fun score(
        song: OnlineSong,
        artist: String,
        title: String,
        durationSeconds: Int = 0
    ): Double {
        val expectedTitle = SearchText.keyTokens(title)
        val candidateTitle = SearchText.keyTokens(song.title)
        if (expectedTitle.isEmpty() || candidateTitle.isEmpty()) return 0.0

        val titleScore = SearchText.dice(expectedTitle, candidateTitle)
        val artistScore = artistScore(artist, song.artist)
        var score = TITLE_WEIGHT * titleScore + ARTIST_WEIGHT * artistScore

        if (durationSeconds > 0) {
            val candidateDuration = parseDurationSeconds(song.duration)
            if (candidateDuration > 0) {
                val diff = abs(candidateDuration - durationSeconds)
                score += when {
                    diff <= 4 -> DURATION_BONUS
                    diff <= 12 -> 0.0
                    else -> -DURATION_MISS
                }
            }
        }

        val asked = " " + SearchText.fold(title) + " "
        val candidate = " " + SearchText.fold(song.title) + " "
        for (word in VERSION_WORDS) {
            val needle = " " + SearchText.fold(word) + " "
            if (candidate.contains(needle) && !asked.contains(needle)) score -= VERSION_PENALTY
        }
        return score
    }

    private fun artistScore(expected: String, candidate: String): Double {
        val expectedTokens = SearchText.keyTokens(expected)
        if (expectedTokens.isEmpty()) return 0.0
        val parts = SearchText.artistParts(candidate)
        if (parts.isEmpty()) return 0.0
        var best = 0.0
        for (part in parts) {
            val partTokens = part.split(' ').filter { it.isNotEmpty() }
            val coverage = SearchText.coverage(expectedTokens, partTokens)
            if (coverage > best) best = coverage
        }
        val allTokens = SearchText.keyTokens(candidate)
        return max(best, SearchText.coverage(expectedTokens, allTokens) * 0.9)
    }

    private fun parseDurationSeconds(value: String): Int {
        if (value.isBlank()) return 0
        val parts = value.trim().split(':')
        return try {
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> 0
            }
        } catch (e: NumberFormatException) {
            0
        }
    }
}
