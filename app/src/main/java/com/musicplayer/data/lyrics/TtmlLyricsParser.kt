package com.musicplayer.data.lyrics

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale

/** Parses Apple-style TTML returned by BetterLyrics into the app's lyric model. */
object TtmlLyricsParser {
    fun parse(ttml: String?): Lyrics? {
        if (ttml.isNullOrBlank()) return null

        return runCatching {
            val parser = Xml.newPullParser().apply {
                setInput(StringReader(ttml))
            }
            val lines = mutableListOf<SyncedLine>()
            var currentLine: LineBuilder? = null
            var currentWordStart: Int? = null
            var currentWordEnd: Int? = null
            val currentWordText = StringBuilder()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.substringAfterLast(':').lowercase(Locale.US)) {
                        "p" -> {
                            if (currentLine == null) {
                                currentLine = LineBuilder(
                                    start = parseTime(parser.getAttributeValue(null, "begin")),
                                    end = parseTime(parser.getAttributeValue(null, "end"))
                                        ?: parseDurationEnd(
                                            parser.getAttributeValue(null, "begin"),
                                            parser.getAttributeValue(null, "dur")
                                        )
                                )
                            }
                        }
                        "span" -> {
                            if (currentLine != null) {
                                currentWordStart = parseTime(parser.getAttributeValue(null, "begin"))
                                currentWordEnd = parseTime(parser.getAttributeValue(null, "end"))
                                    ?: parseDurationEnd(
                                        parser.getAttributeValue(null, "begin"),
                                        parser.getAttributeValue(null, "dur")
                                    )
                                currentWordText.clear()
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (currentLine != null) {
                            val text = parser.text ?: ""
                            currentLine.text.append(text)
                            if (currentWordStart != null) currentWordText.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name.substringAfterLast(':').lowercase(Locale.US)) {
                        "span" -> {
                            val start = currentWordStart
                            if (currentLine != null && start != null) {
                                val word = currentWordText.toString().trim()
                                if (word.isNotEmpty()) {
                                    currentLine.words += SyncedWord(
                                        time = start,
                                        word = word
                                    )
                                }
                            }
                            currentWordStart = null
                            currentWordEnd = null
                            currentWordText.clear()
                        }
                        "p" -> {
                            currentLine?.let { builder ->
                                val start = builder.start
                                if (start != null) {
                                    val text = normalizeText(builder.text.toString())
                                    if (text.isNotEmpty()) {
                                        lines += SyncedLine(
                                            time = start,
                                            line = text,
                                            words = builder.words.takeIf { it.isNotEmpty() }
                                        )
                                    }
                                }
                            }
                            currentLine = null
                            currentWordStart = null
                            currentWordEnd = null
                            currentWordText.clear()
                        }
                    }
                }
                event = parser.next()
            }

            val sorted = lines
                .sortedBy { it.time }
                .distinctBy { "${it.time}:${it.line}" }
            sorted.takeIf { it.isNotEmpty() }?.let {
                Lyrics(synced = it, plain = it.map(SyncedLine::line), areFromRemote = true)
            }
        }.getOrNull()
    }

    private data class LineBuilder(
        val start: Int?,
        val end: Int?,
        val text: StringBuilder = StringBuilder(),
        val words: MutableList<SyncedWord> = mutableListOf()
    )

    private fun normalizeText(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun parseDurationEnd(begin: String?, duration: String?): Int? {
        val start = parseTime(begin) ?: return null
        val length = parseTime(duration) ?: return null
        return start + length
    }

    private fun parseTime(value: String?): Int? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            when {
                raw.endsWith("ms", ignoreCase = true) -> raw.dropLast(2).toDouble().toInt()
                raw.contains(":") -> {
                    val parts = raw.split(':')
                    val seconds = parts.last().toDouble()
                    val minutes = parts.getOrNull(parts.lastIndex - 1)?.toDouble() ?: 0.0
                    val hours = parts.getOrNull(parts.lastIndex - 2)?.toDouble() ?: 0.0
                    ((hours * 3_600_000.0) + (minutes * 60_000.0) + (seconds * 1_000.0)).toInt()
                }
                else -> (raw.toDouble() * 1_000.0).toInt()
            }
        }.getOrNull()
    }
}
