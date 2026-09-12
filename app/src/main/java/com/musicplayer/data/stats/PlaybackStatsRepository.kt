package com.musicplayer.data.stats

import android.content.Context
import com.musicplayer.data.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ── Data models ───────────────────────────────────────────────────────────────

enum class StatsTimeRange(val displayName: String) {
    DAY("Сегодня"),
    WEEK("Эта неделя"),
    MONTH("Этот месяц"),
    YEAR("Этот год"),
    ALL("Всё время")
}

data class PlaybackEvent(
    val songId: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMs: Long
)

data class SongPlaybackSummary(
    val songId: Long,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val totalDurationMs: Long,
    val playCount: Int
)

data class ArtistPlaybackSummary(
    val artist: String,
    val totalDurationMs: Long,
    val playCount: Int,
    val uniqueSongs: Int
)

data class AlbumPlaybackSummary(
    val album: String,
    val albumArtUri: String?,
    val totalDurationMs: Long,
    val playCount: Int,
    val uniqueSongs: Int
)

data class GenrePlaybackSummary(
    val genre: String,
    val totalDurationMs: Long,
    val playCount: Int
)

data class PeakPeriodSummary(
    val label: String,
    val totalDurationMs: Long
)

data class TimelineEntry(
    val label: String,
    val totalDurationMs: Long,
    val playCount: Int
)

data class PlaybackStatsSummary(
    val range: StatsTimeRange,
    val totalDurationMs: Long,
    val totalPlayCount: Int,
    val uniqueSongs: Int,
    val averageDailyDurationMs: Long,
    val topSongs: List<SongPlaybackSummary>,
    val topArtists: List<ArtistPlaybackSummary>,
    val topAlbums: List<AlbumPlaybackSummary>,
    val topGenres: List<GenrePlaybackSummary>,
    val timeline: List<TimelineEntry>,
    val activeDays: Int,
    val longestStreakDays: Int,
    val totalSessions: Int,
    val averageSessionDurationMs: Long,
    val longestSessionDurationMs: Long,
    val peakDayLabel: String?,
    val peakDayDurationMs: Long,
    val peakWeek: PeakPeriodSummary?,
    val peakMonth: PeakPeriodSummary?,
    val dayListeningDurationMs: Long,
    val nightListeningDurationMs: Long,
    // Hour-of-day distribution: index 0 = 00:00-01:00, ..., 23 = 23:00-24:00
    val hourlyDistribution: List<Long>
)

// ── Repository ────────────────────────────────────────────────────────────────

class PlaybackStatsRepository(context: Context) {

    private val historyFile = File(context.filesDir, "playback_history.json")
    private val lock        = Any()

    private val SESSION_GAP_MS      = TimeUnit.MINUTES.toMillis(30)
    private val MAX_HISTORY_AGE_MS  = TimeUnit.DAYS.toMillis(730) // 2 years
    private val MAX_EVENT_DURATION  = TimeUnit.HOURS.toMillis(8)

    // ── Write ─────────────────────────────────────────────────────────────────

    fun recordPlayback(songId: Long, durationMs: Long) {
        if (songId < 0 || durationMs <= 0) return
        val cappedDuration = durationMs.coerceAtMost(MAX_EVENT_DURATION)
        val end   = System.currentTimeMillis()
        val start = (end - cappedDuration).coerceAtLeast(0L)
        val event = PlaybackEvent(songId, start, end, cappedDuration)

        synchronized(lock) {
            val events = readEventsLocked().toMutableList()
            // Prune old events
            val cutoff = end - MAX_HISTORY_AGE_MS
            events.removeAll { it.endTimestamp < cutoff }
            events += event
            writeEventsLocked(events)
        }
    }

    // ── Read / Summarize ──────────────────────────────────────────────────────

    fun loadSummary(
        range: StatsTimeRange,
        songs: List<Song>,
        nowMillis: Long = System.currentTimeMillis()
    ): PlaybackStatsSummary {
        val zoneId = ZoneId.systemDefault()
        val allEvents = readEvents()
        val (startBound, endBound) = resolveBounds(range, allEvents, nowMillis, zoneId)

        // Filter + clip events to the range
        val filtered = allEvents.mapNotNull { e ->
            val lo = startBound ?: Long.MIN_VALUE
            if (e.endTimestamp < lo || e.startTimestamp > endBound) return@mapNotNull null
            val cs = max(e.startTimestamp, lo)
            val ce = min(e.endTimestamp, endBound)
            val dur = (ce - cs).coerceAtLeast(0L)
            if (dur <= 0) return@mapNotNull null
            e.copy(startTimestamp = cs, endTimestamp = ce, durationMs = dur)
        }

        val songMap = songs.associateBy { it.id }

        // Cap event duration by song actual duration
        val normalized = filtered.map { e ->
            val max = songMap[e.songId]?.duration?.takeIf { it > 0 }
            val dur = if (max != null) min(e.durationMs, max) else e.durationMs
            e.copy(durationMs = dur.coerceAtMost(MAX_EVENT_DURATION))
        }

        // Per-song merged segments
        val byId = normalized.groupBy { it.songId }

        // Total listening time (non-overlapping spans)
        val allSpans = normalized.map { it.startTimestamp..it.endTimestamp }
        val mergedSpans = mergeRanges(allSpans)
        val totalDuration = mergedSpans.sumOf { it.last - it.first }
        val totalPlays    = normalized.size
        val uniqueSongs   = byId.size

        // Day spans for streaks + avg
        val daySlices  = mergedSpans.flatMap { sliceByDay(it.first, it.last, zoneId) }
        val byDay      = daySlices.groupBy { it.first }
        val activeDays = byDay.size

        // Average daily
        val effectiveStart = startBound ?: normalized.minOfOrNull { it.startTimestamp }
        val daySpan = if (effectiveStart != null) {
            val d1 = Instant.ofEpochMilli(effectiveStart).atZone(zoneId).toLocalDate()
            val d2 = Instant.ofEpochMilli(endBound).atZone(zoneId).toLocalDate()
            max(1L, ChronoUnit.DAYS.between(d1, d2) + 1)
        } else 1L
        val avgDaily = totalDuration / daySpan

        // Streak
        val longestStreak = computeStreak(byDay.keys.toList())

        // Sessions
        val sessions           = computeSessions(mergedSpans)
        val totalSessions      = sessions.size
        val totalSessionMs     = sessions.sumOf { it }
        val avgSessionMs       = if (totalSessions > 0) totalSessionMs / totalSessions else 0L
        val longestSessionMs   = sessions.maxOrNull() ?: 0L

        // Peak day of week
        val byDow = daySlices.groupBy { Instant.ofEpochMilli(it.first).atZone(zoneId).dayOfWeek }
        val peakDow = byDow.maxByOrNull { e -> e.value.sumOf { it.second } }
        val peakDayLabel    = peakDow?.key?.getDisplayName(TextStyle.FULL, Locale("ru"))
        val peakDayDuration = peakDow?.value?.sumOf { it.second } ?: 0L

        // Hourly distribution (0-23) + day/night profile
        val hourly = LongArray(24)
        normalized
            .flatMap { e -> sliceByHour(e.startTimestamp, e.endTimestamp, zoneId) }
            .forEach { (hour, dur) -> hourly[hour] = hourly[hour] + dur }
        val dayListeningMs = (6..17).sumOf { hourly[it] }
        val nightListeningMs = hourly.sum() - dayListeningMs

        // Top songs
        val topSongs = byId.mapNotNull { (id, evs) ->
            val s = songMap[id] ?: return@mapNotNull null
            SongPlaybackSummary(
                songId         = id,
                title          = s.title,
                artist         = s.artist,
                albumArtUri    = s.albumArtUri?.toString(),
                totalDurationMs= evs.sumOf { it.durationMs },
                playCount      = evs.size
            )
        }.sortedByDescending { it.totalDurationMs }.take(5)

        // Top artists
        val topArtists = normalized
            .groupBy { songMap[it.songId]?.artist?.takeIf { a -> a.isNotBlank() } ?: "Неизвестный" }
            .map { (artist, evs) ->
                ArtistPlaybackSummary(
                    artist          = artist,
                    totalDurationMs = evs.sumOf { it.durationMs },
                    playCount       = evs.size,
                    uniqueSongs     = evs.map { it.songId }.distinct().size
                )
            }.sortedByDescending { it.totalDurationMs }.take(5)

        // Top albums
        val topAlbums = normalized
            .groupBy { songMap[it.songId]?.album?.takeIf { a -> a.isNotBlank() } ?: "Неизвестный" }
            .map { (album, evs) ->
                val firstSong = evs.mapNotNull { songMap[it.songId] }.firstOrNull()
                AlbumPlaybackSummary(
                    album           = album,
                    albumArtUri     = firstSong?.albumArtUri?.toString(),
                    totalDurationMs = evs.sumOf { it.durationMs },
                    playCount       = evs.size,
                    uniqueSongs     = evs.map { it.songId }.distinct().size
                )
            }.sortedByDescending { it.totalDurationMs }.take(5)

        // Top genres (heuristic from folder / album naming)
        val topGenres = normalized
            .groupBy { inferGenre(songMap[it.songId]) }
            .map { (genre, evs) ->
                GenrePlaybackSummary(
                    genre = genre,
                    totalDurationMs = evs.sumOf { it.durationMs },
                    playCount = evs.size
                )
            }
            .sortedByDescending { it.totalDurationMs }
            .take(5)

        val byWeek = daySlices.groupBy {
            Instant.ofEpochMilli(it.first)
                .atZone(zoneId)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val peakWeek = byWeek.maxByOrNull { entry -> entry.value.sumOf { it.second } }?.let { entry ->
            PeakPeriodSummary(
                label = formatWeekLabel(entry.key),
                totalDurationMs = entry.value.sumOf { it.second }
            )
        }

        val byMonth = daySlices.groupBy {
            YearMonth.from(Instant.ofEpochMilli(it.first).atZone(zoneId))
        }
        val peakMonth = byMonth.maxByOrNull { entry -> entry.value.sumOf { it.second } }?.let { entry ->
            PeakPeriodSummary(
                label = formatMonthLabel(entry.key),
                totalDurationMs = entry.value.sumOf { it.second }
            )
        }

        // Timeline
        val timeline = buildTimeline(range, zoneId, nowMillis, mergedSpans)

        return PlaybackStatsSummary(
            range                  = range,
            totalDurationMs        = totalDuration,
            totalPlayCount         = totalPlays,
            uniqueSongs            = uniqueSongs,
            averageDailyDurationMs = avgDaily,
            topSongs               = topSongs,
            topArtists             = topArtists,
            topAlbums              = topAlbums,
            topGenres              = topGenres,
            timeline               = timeline,
            activeDays             = activeDays,
            longestStreakDays      = longestStreak,
            totalSessions          = totalSessions,
            averageSessionDurationMs = avgSessionMs,
            longestSessionDurationMs = longestSessionMs,
            peakDayLabel           = peakDayLabel,
            peakDayDurationMs      = peakDayDuration,
            peakWeek               = peakWeek,
            peakMonth              = peakMonth,
            dayListeningDurationMs = dayListeningMs,
            nightListeningDurationMs = nightListeningMs,
            hourlyDistribution     = hourly.toList()
        )
    }

    fun hasAnyData(): Boolean = historyFile.exists() && historyFile.length() > 2

    fun clearAll() {
        synchronized(lock) {
            historyFile.writeText("[]")
        }
    }

    // ── Persistence (org.json, no Gson, no Room) ──────────────────────────────

    private fun readEvents(): List<PlaybackEvent> = synchronized(lock) { readEventsLocked() }

    private fun readEventsLocked(): List<PlaybackEvent> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(historyFile.readText())
            (0 until arr.length()).mapNotNull {
                val o = arr.getJSONObject(it)
                PlaybackEvent(
                    songId         = o.getLong("songId"),
                    startTimestamp = o.getLong("startTs"),
                    endTimestamp   = o.getLong("endTs"),
                    durationMs     = o.getLong("dur")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun writeEventsLocked(events: List<PlaybackEvent>) {
        try {
            historyFile.parentFile?.mkdirs()
            val arr = JSONArray()
            events.forEach { e ->
                arr.put(JSONObject().apply {
                    put("songId", e.songId)
                    put("startTs", e.startTimestamp)
                    put("endTs",  e.endTimestamp)
                    put("dur",    e.durationMs)
                })
            }
            historyFile.writeText(arr.toString())
        } catch (e: Exception) { /* silent */ }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveBounds(
        range: StatsTimeRange,
        events: List<PlaybackEvent>,
        nowMs: Long,
        zone: ZoneId
    ): Pair<Long?, Long> {
        val now = Instant.ofEpochMilli(nowMs)
        return when (range) {
            StatsTimeRange.DAY   -> now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() to nowMs
            StatsTimeRange.WEEK  -> now.atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone).toInstant().toEpochMilli() to nowMs
            StatsTimeRange.MONTH -> YearMonth.from(now.atZone(zone)).atDay(1)
                .atStartOfDay(zone).toInstant().toEpochMilli() to nowMs
            StatsTimeRange.YEAR  -> now.atZone(zone).toLocalDate().withDayOfYear(1)
                .atStartOfDay(zone).toInstant().toEpochMilli() to nowMs
            StatsTimeRange.ALL   -> events.minOfOrNull { it.startTimestamp } to nowMs
        }
    }

    /** Merges overlapping/adjacent Long ranges */
    private fun mergeRanges(ranges: List<LongRange>): List<LongRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val result = mutableListOf(sorted[0])
        for (r in sorted.drop(1)) {
            val last = result.last()
            if (r.first <= last.last + 1_000) result[result.lastIndex] = last.first..max(last.last, r.last)
            else result += r
        }
        return result
    }

    /** Slices a [startMs..endMs] into (dayStartMs, durationMs) pairs */
    private fun sliceByDay(startMs: Long, endMs: Long, zone: ZoneId): List<Pair<Long, Long>> {
        val slices = mutableListOf<Pair<Long, Long>>()
        var cur = startMs
        while (cur < endMs) {
            val dayStart = Instant.ofEpochMilli(cur).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val nextDay  = dayStart + TimeUnit.DAYS.toMillis(1)
            val sliceEnd = min(endMs, nextDay)
            slices += dayStart to (sliceEnd - cur)
            cur = sliceEnd
        }
        return slices
    }

    /** Slices a [startMs..endMs] into (hourOfDay, durationMs) pairs */
    private fun sliceByHour(startMs: Long, endMs: Long, zone: ZoneId): List<Pair<Int, Long>> {
        val slices = mutableListOf<Pair<Int, Long>>()
        var cur = startMs
        while (cur < endMs) {
            val currentHour = Instant.ofEpochMilli(cur).atZone(zone)
            val nextHour = currentHour.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val sliceEnd = min(endMs, nextHour)
            slices += currentHour.hour to (sliceEnd - cur)
            cur = sliceEnd
        }
        return slices
    }

    private fun computeStreak(days: List<Long>): Int {
        if (days.isEmpty()) return 0
        val dates = days.map { LocalDate.ofEpochDay(it / TimeUnit.DAYS.toMillis(1)) }.toSortedSet()
        var longest = 0; var current = 0; var last: LocalDate? = null
        for (d in dates) {
            current = if (last == null || d == last!!.plusDays(1)) current + 1 else 1
            if (current > longest) longest = current
            last = d
        }
        return longest
    }

    /** Returns list of session durations in ms */
    private fun computeSessions(spans: List<LongRange>): List<Long> {
        if (spans.isEmpty()) return emptyList()
        val sorted = spans.sortedBy { it.first }
        val sessions = mutableListOf<Long>()
        var sessionStart = sorted[0].first; var sessionEnd = sorted[0].last; var sessionDur = sorted[0].last - sorted[0].first
        for (r in sorted.drop(1)) {
            if (r.first - sessionEnd <= SESSION_GAP_MS) {
                sessionEnd = max(sessionEnd, r.last)
                sessionDur += r.last - r.first
            } else {
                sessions += sessionDur
                sessionStart = r.first; sessionEnd = r.last; sessionDur = r.last - r.first
            }
        }
        sessions += sessionDur
        return sessions
    }

    private fun buildTimeline(
        range: StatsTimeRange,
        zone: ZoneId,
        nowMs: Long,
        spans: List<LongRange>
    ): List<TimelineEntry> {
        val now = Instant.ofEpochMilli(nowMs)
        val buckets: List<Triple<String, Long, Long>> = when (range) {
            StatsTimeRange.DAY -> {
                val dayStart = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                (0 until 6).map { i ->
                    val s = dayStart + i * 4 * 3_600_000L
                    Triple("${i * 4}:00", s, s + 4 * 3_600_000L)
                }
            }
            StatsTimeRange.WEEK -> {
                val monday = now.atZone(zone).toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                (0 until 7).map { i ->
                    val d = monday.plusDays(i.toLong())
                    val s = d.atStartOfDay(zone).toInstant().toEpochMilli()
                    Triple(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")), s, s + TimeUnit.DAYS.toMillis(1))
                }
            }
            StatsTimeRange.MONTH -> {
                val ym = YearMonth.from(now.atZone(zone))
                (1..4).map { w ->
                    val sd = ((w - 1) * 7 + 1).coerceAtMost(ym.lengthOfMonth())
                    val ed = (w * 7).coerceAtMost(ym.lengthOfMonth())
                    val s  = ym.atDay(sd).atStartOfDay(zone).toInstant().toEpochMilli()
                    val e  = ym.atDay(ed).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    Triple("Нед $w", s, e)
                }
            }
            StatsTimeRange.YEAR -> {
                val yr = Year.from(now.atZone(zone))
                val months = listOf("Янв","Фев","Мар","Апр","Май","Июн","Июл","Авг","Сен","Окт","Ноя","Дек")
                (1..12).map { m ->
                    val s = yr.atMonth(m).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val e = yr.atMonth(m).atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    Triple(months[m - 1], s, e)
                }
            }
            StatsTimeRange.ALL -> {
                val minMs = spans.minOfOrNull { it.first } ?: nowMs
                val startYear = Instant.ofEpochMilli(minMs).atZone(zone).year
                val endYear   = now.atZone(zone).year
                (startYear..endYear).map { y ->
                    val s = Year.of(y).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val e = Year.of(y + 1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    Triple(y.toString(), s, e)
                }
            }
        }

        return buckets.map { (label, bucketStart, bucketEnd) ->
            var dur = 0L; var plays = 0
            spans.forEach { span ->
                val overlap = max(0L, min(span.last, bucketEnd) - max(span.first, bucketStart))
                if (overlap > 0) { dur += overlap; plays++ }
            }
            TimelineEntry(label, dur, plays)
        }
    }

    private fun inferGenre(song: Song?): String {
        if (song == null) return "Разное"
        val haystack = listOf(song.folderPath, song.album, song.artist)
            .filterNotNull()
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        val buckets = listOf(
            "Рок" to listOf("rock", "рок", "metal", "метал", "punk", "панк", "grunge"),
            "Поп" to listOf("pop", "поп", "dance", "k-pop", "j-pop"),
            "Хип-хоп" to listOf("hip hop", "hip-hop", "rap", "рэп", "trap", "drill"),
            "Электроника" to listOf("edm", "electro", "house", "techno", "trance", "dnb", "drum and bass", "dubstep"),
            "R&B" to listOf("r&b", "rnb", "soul", "neo soul"),
            "Инди" to listOf("indie", "альтернати", "alternative"),
            "Джаз" to listOf("jazz", "джаз", "blues", "блюз"),
            "Классика" to listOf("classical", "classic", "классик", "оркестр", "piano", "instrumental"),
            "Чилл" to listOf("chill", "lofi", "lo-fi", "ambient", "downtempo", "relax", "спокой"),
            "Фонк" to listOf("phonk")
        )

        buckets.firstOrNull { (_, keys) -> keys.any { haystack.contains(it) } }?.let { return it.first }

        val fallbackFolder = song.folderPath
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.replace('_', ' ')
            ?.trim()
            ?.takeIf { it.length in 3..22 }
            ?.takeIf { candidate ->
                val lowered = candidate.lowercase(Locale.ROOT)
                lowered !in setOf("music", "musicplayer", "download", "downloads", "mp3", "audio", "songs")
            }

        return fallbackFolder ?: "Разное"
    }

    private fun formatWeekLabel(start: LocalDate): String {
        val end = start.plusDays(6)
        val ru = Locale("ru")
        val startMonth = start.month.getDisplayName(TextStyle.SHORT, ru).trimEnd('.')
        val endMonth = end.month.getDisplayName(TextStyle.SHORT, ru).trimEnd('.')
        return if (start.month == end.month) {
            "${start.dayOfMonth}-${end.dayOfMonth} $startMonth"
        } else {
            "${start.dayOfMonth} $startMonth - ${end.dayOfMonth} $endMonth"
        }
    }

    private fun formatMonthLabel(month: YearMonth): String {
        val ru = Locale("ru")
        val monthName = month.month.getDisplayName(TextStyle.FULL, ru)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ru) else it.toString() }
        return "$monthName ${month.year}"
    }
}
