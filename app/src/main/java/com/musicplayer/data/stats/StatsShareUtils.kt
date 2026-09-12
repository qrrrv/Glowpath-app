package com.musicplayer.data.stats

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Holds the pending shared stats link when app is launched via deep link.
 * Using a simple object instead of nav arguments avoids base64/URL encoding issues.
 */
object SharedStatsHolder {
    var pendingLink: String? = null
}

/**
 * Serializes a PlaybackStatsSummary into a compact JSON → Base64 URL string
 * so it can be embedded directly in a shareable deep link.
 */
object StatsShareUtils {

    const val SCHEME = "musicplayer"
    const val HOST   = "share"
    const val PATH   = "stats"

    fun buildDeepLink(summary: PlaybackStatsSummary): String {
        val json = toJson(summary)
        val encoded = Base64.encodeToString(
            json.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        return "$SCHEME://$HOST/$PATH?d=$encoded"
    }

    fun parseDeepLink(link: String): PlaybackStatsSummary? {
        return try {
            val uri = Uri.parse(link)
            val encoded = uri.getQueryParameter("d") ?: return null
            if (encoded.isBlank()) return null
            val jsonStr = String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            fromJson(JSONObject(jsonStr))
        } catch (_: Exception) { null }
    }

    private fun toJson(s: PlaybackStatsSummary): JSONObject = JSONObject().apply {
        put("range", s.range.name)
        put("totalMs",  s.totalDurationMs)
        put("plays",    s.totalPlayCount)
        put("unique",   s.uniqueSongs)
        put("days",     s.activeDays)
        put("streak",   s.longestStreakDays)
        put("sessions", s.totalSessions)
        put("peakDay",  s.peakDayLabel ?: "")
        put("peakMs",   s.peakDayDurationMs)
        put("dayMs",    s.dayListeningDurationMs)
        put("nightMs",  s.nightListeningDurationMs)

        put("songs", JSONArray().also { arr ->
            s.topSongs.take(5).forEach { song ->
                arr.put(JSONObject().apply {
                    put("t", song.title)
                    put("a", song.artist)
                    put("ms", song.totalDurationMs)
                    put("c", song.playCount)
                })
            }
        })

        put("artists", JSONArray().also { arr ->
            s.topArtists.take(5).forEach { artist ->
                arr.put(JSONObject().apply {
                    put("n", artist.artist)
                    put("ms", artist.totalDurationMs)
                    put("c", artist.playCount)
                    put("u", artist.uniqueSongs)
                })
            }
        })

        put("genres", JSONArray().also { arr ->
            s.topGenres.take(5).forEach { genre ->
                arr.put(JSONObject().apply {
                    put("n", genre.genre)
                    put("ms", genre.totalDurationMs)
                    put("c", genre.playCount)
                })
            }
        })
    }

    private fun fromJson(j: JSONObject): PlaybackStatsSummary {
        val range = try { StatsTimeRange.valueOf(j.optString("range", "WEEK")) }
                    catch (_: Exception) { StatsTimeRange.WEEK }

        val songs = mutableListOf<SongPlaybackSummary>()
        val songsArr = j.optJSONArray("songs")
        if (songsArr != null) {
            for (i in 0 until songsArr.length()) {
                val o = songsArr.getJSONObject(i)
                songs.add(SongPlaybackSummary(
                    songId = i.toLong(),
                    title  = o.optString("t", "?"),
                    artist = o.optString("a", "?"),
                    albumArtUri = null,
                    totalDurationMs = o.optLong("ms", 0),
                    playCount       = o.optInt("c", 0)
                ))
            }
        }

        val artists = mutableListOf<ArtistPlaybackSummary>()
        val artistsArr = j.optJSONArray("artists")
        if (artistsArr != null) {
            for (i in 0 until artistsArr.length()) {
                val o = artistsArr.getJSONObject(i)
                artists.add(ArtistPlaybackSummary(
                    artist          = o.optString("n", "?"),
                    totalDurationMs = o.optLong("ms", 0),
                    playCount       = o.optInt("c", 0),
                    uniqueSongs     = o.optInt("u", 0)
                ))
            }
        }

        val genres = mutableListOf<GenrePlaybackSummary>()
        val genresArr = j.optJSONArray("genres")
        if (genresArr != null) {
            for (i in 0 until genresArr.length()) {
                val o = genresArr.getJSONObject(i)
                genres.add(
                    GenrePlaybackSummary(
                        genre = o.optString("n", "Разное"),
                        totalDurationMs = o.optLong("ms", 0),
                        playCount = o.optInt("c", 0)
                    )
                )
            }
        }

        return PlaybackStatsSummary(
            range                    = range,
            totalDurationMs          = j.optLong("totalMs", 0),
            totalPlayCount           = j.optInt("plays", 0),
            uniqueSongs              = j.optInt("unique", 0),
            averageDailyDurationMs   = 0L,
            topSongs                 = songs,
            topArtists               = artists,
            topAlbums                = emptyList(),
            topGenres                = genres,
            timeline                 = emptyList(),
            activeDays               = j.optInt("days", 0),
            longestStreakDays         = j.optInt("streak", 0),
            totalSessions            = j.optInt("sessions", 0),
            averageSessionDurationMs = 0L,
            longestSessionDurationMs = 0L,
            peakDayLabel             = j.optString("peakDay", "").takeIf { it.isNotBlank() },
            peakDayDurationMs        = j.optLong("peakMs", 0),
            peakWeek                 = null,
            peakMonth                = null,
            dayListeningDurationMs   = j.optLong("dayMs", 0),
            nightListeningDurationMs = j.optLong("nightMs", 0),
            hourlyDistribution       = emptyList()
        )
    }
}
