package com.musicplayer.data.lyrics

import java.util.regex.Pattern

/**
 * Utility for parsing LRC-format lyrics.
 * Adapted from PixelPlay's LyricsUtils.kt — pure parsing logic only (no Compose).
 */
object LyricsUtils {

    private val LRC_LINE_REGEX   = Pattern.compile("^\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)$")
    private val LRC_WORD_REGEX   = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)")
    private val LRC_WORD_TAG_REGEX       = Regex("<\\d{2}:\\d{2}\\.\\d{2,3}>")
    private val LRC_WORD_SPLIT_REGEX     = Regex("(?=<\\d{2}:\\d{2}\\.\\d{2,3}>)")
    private val LRC_TIMESTAMP_TAG_REGEX  = Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?]")

    /**
     * Parse a string that contains LRC-format or plain-text lyrics.
     * Returns a [Lyrics] object with `synced` or `plain` populated.
     */
    fun parseLyrics(lyricsText: String?): Lyrics {
        if (lyricsText.isNullOrEmpty()) return Lyrics(plain = emptyList(), synced = emptyList())

        val syncedLines = mutableListOf<SyncedLine>()
        val plainLines  = mutableListOf<String>()
        var isSynced    = false

        lyricsText.lines().forEach { rawLine ->
            val line = sanitizeLrcLine(rawLine)
            if (line.isEmpty()) return@forEach

            val lineMatcher = LRC_LINE_REGEX.matcher(line)
            if (lineMatcher.matches()) {
                isSynced = true
                val minutes  = lineMatcher.group(1)?.toLong() ?: 0L
                val seconds  = lineMatcher.group(2)?.toLong() ?: 0L
                val fraction = lineMatcher.group(3)?.toLong() ?: 0L
                val textWithTags = stripFormatChars(lineMatcher.group(4)?.trim() ?: "")
                val text = stripLrcTimestamps(textWithTags)

                val millis = if ((lineMatcher.group(3)?.length ?: 0) == 2) fraction * 10 else fraction
                val lineTs = minutes * 60 * 1000 + seconds * 1000 + millis

                // Word-by-word parsing (for karaoke apps that provide <mm:ss.xx>word)
                if (text.contains(LRC_WORD_TAG_REGEX)) {
                    val words  = mutableListOf<SyncedWord>()
                    val parts  = text.split(LRC_WORD_SPLIT_REGEX)
                    for (part in parts) {
                        if (part.isEmpty()) continue
                        val wm = LRC_WORD_REGEX.matcher(part)
                        if (wm.find()) {
                            val wMin = wm.group(1)?.toLong() ?: 0L
                            val wSec = wm.group(2)?.toLong() ?: 0L
                            val wFrac = wm.group(3)?.toLong() ?: 0L
                            val wText = stripFormatChars(wm.group(4) ?: "")
                            val wMillis = if ((wm.group(3)?.length ?: 0) == 2) wFrac * 10 else wFrac
                            val wTs = wMin * 60 * 1000 + wSec * 1000 + wMillis
                            words.add(SyncedWord(wTs.toInt(), wText))
                        } else {
                            val lastTime = words.lastOrNull()?.time ?: lineTs.toInt()
                            words.add(SyncedWord(lastTime, part))
                        }
                    }
                    if (words.isNotEmpty()) {
                        syncedLines.add(SyncedLine(lineTs.toInt(), words.joinToString("") { it.word }, words))
                    } else {
                        syncedLines.add(SyncedLine(lineTs.toInt(), text))
                    }
                } else {
                    syncedLines.add(SyncedLine(lineTs.toInt(), text))
                }
            } else {
                val stripped = stripLrcTimestamps(stripFormatChars(line))
                if (isSynced && syncedLines.isNotEmpty()) {
                    // Append continuation line to previous synced entry
                    val last = syncedLines.removeAt(syncedLines.lastIndex)
                    val merged = if (last.line.isEmpty()) stripped else last.line + "\n" + stripped
                    syncedLines.add(
                        if (last.words?.isNotEmpty() == true)
                            SyncedLine(last.time, merged, last.words)
                        else
                            SyncedLine(last.time, merged)
                    )
                } else {
                    plainLines.add(stripped)
                }
            }
        }

        return if (isSynced && syncedLines.isNotEmpty()) {
            val sorted = syncedLines.sortedBy { it.time }
            Lyrics(synced = sorted, plain = sorted.map { it.line })
        } else {
            Lyrics(plain = plainLines)
        }
    }

    internal fun stripLrcTimestamps(value: String): String {
        if (value.isEmpty()) return value
        return LRC_TIMESTAMP_TAG_REGEX.replace(value, "").trimStart()
    }

    /** Convert synced lyrics back to LRC string (used for JSON cache). */
    fun syncedToLrcString(syncedLines: List<SyncedLine>): String {
        return syncedLines.sortedBy { it.time }.joinToString("\n") { line ->
            val mm = line.time / 60000
            val ss = (line.time % 60000) / 1000
            val hh = (line.time % 1000) / 10
            "[%02d:%02d.%02d]%s".format(mm, ss, hh, line.line)
        }
    }
}

// ── Private helpers ──────────────────────────────────────────────────────────

private fun sanitizeLrcLine(rawLine: String): String {
    if (rawLine.isEmpty()) return rawLine
    val cleaned = rawLine
        .trimEnd('\r', '\n')
        .filterNot { c -> Character.getType(c).toByte() == Character.FORMAT ||
                (Character.isISOControl(c) && c != '\t') }
        .trimEnd('\uFEFF')
        .trimStart { it.isWhitespace() }
    val firstBracket = cleaned.indexOf('[')
    return if (firstBracket > 0) cleaned.substring(firstBracket) else cleaned
}

private fun stripFormatChars(value: String): String {
    val cleaned = value.filterNot { c ->
        Character.getType(c).toByte() == Character.FORMAT ||
                (Character.isISOControl(c) && c != '\t')
    }
    return when (cleaned) { "\"", "'" -> "" else -> cleaned }
}
