package com.musicplayer.data.whisper

import android.content.Context
import com.musicplayer.data.Song
import com.musicplayer.data.lyrics.SyncedLine
import com.musicplayer.data.lyrics.SyncedWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

class WhisperException(message: String) : Exception(message)

/**
 * @param lines строки с таймингами слов в формате, который понимает плеер
 * @param wordLevel false, если сервер не отдал тайминги слов и они распределены приблизительно
 */
data class WhisperResult(val lines: List<SyncedLine>, val wordLevel: Boolean)

/**
 * Распознавание речи через OpenAI-совместимый Whisper API (Groq, OpenAI или свой сервер
 * с faster-whisper). Результат сразу собирается в строки вида
 * [мм:сс.хх]<мм:сс.хх>слово <мм:сс.хх>слово ...
 * (сама запись в этот текст делает LyricsUtils.syncedToLrcString при сохранении).
 */
object WhisperTranscriber {

    private const val LINE_GAP_MS = 900          // пауза, после которой начинается новая строка
    private const val MAX_WORDS_PER_LINE = 10
    private const val SEGMENT_TOLERANCE_MS = 80

    private val JUNK = Regex(
        "(субтитры|редактор субтитров|корректор|продолжение следует|thanks for watching|subtitles by|amara\\.org|dimatorzok)",
        RegexOption.IGNORE_CASE
    )

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    /** Обрывает текущую загрузку/ожидание ответа (из любого потока). */
    fun abort() {
        runCatching { activeConnection?.disconnect() }
    }

    suspend fun transcribe(
        context: Context,
        song: Song,
        cfg: WhisperConfig,
        onStatus: (String) -> Unit
    ): WhisperResult {
        val app = context.applicationContext
        if (cfg.provider != WhisperProvider.CUSTOM && cfg.apiKey.isBlank()) {
            throw WhisperException("Укажи API-ключ")
        }
        if (cfg.baseUrl.isBlank()) throw WhisperException("Укажи адрес сервера")
        if (cfg.model.isBlank()) throw WhisperException("Укажи модель")

        try {
            val audio = try {
                WhisperAudioPreparer.prepare(app, song, onStatus)
            } catch (e: IOException) {
                throw WhisperException(e.message ?: "Не удалось подготовить аудио")
            }

            onStatus("Отправка и распознавание…")
            val body = withContext(Dispatchers.IO) { upload(cfg, audio) }
            currentCoroutineContext().ensureActive()

            onStatus("Разбор результата…")
            return withContext(Dispatchers.Default) { parse(body) }
        } finally {
            WhisperAudioPreparer.cleanup(app)
            activeConnection = null
        }
    }

    // ── Отправка ──────────────────────────────────────────────────────────────

    private fun upload(cfg: WhisperConfig, audio: PreparedAudio): String {
        val boundary = "----GlowPath${System.currentTimeMillis()}"
        val url = URL(cfg.baseUrl.trim().trimEnd('/') + "/audio/transcriptions")
        val conn = url.openConnection() as HttpURLConnection
        activeConnection = conn
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.doInput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 10 * 60_000
            conn.setChunkedStreamingMode(64 * 1024)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            if (cfg.apiKey.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey.trim()}")
            }

            DataOutputStream(BufferedOutputStream(conn.outputStream, 64 * 1024)).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n")
                }
                field("model", cfg.model.trim())
                field("response_format", "verbose_json")
                field("temperature", "0")
                field("timestamp_granularities[]", "word")
                field("timestamp_granularities[]", "segment")
                if (cfg.language.isNotBlank()) field("language", cfg.language.trim().lowercase())

                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${audio.fileName}\"\r\n")
                out.writeBytes("Content-Type: ${audio.mime}\r\n\r\n")
                FileInputStream(audio.file).use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw WhisperException(httpError(code, text))
            return text
        } catch (e: WhisperException) {
            throw e
        } catch (e: IOException) {
            throw WhisperException("Сетевая ошибка: ${e.message ?: "нет соединения"}")
        } finally {
            runCatching { conn.disconnect() }
            if (activeConnection === conn) activeConnection = null
        }
    }

    private fun httpError(code: Int, body: String): String {
        val detail = runCatching {
            val o = JSONObject(body)
            when (val err = o.opt("error")) {
                is JSONObject -> err.optString("message")
                is String -> err
                else -> o.optString("message").ifBlank { o.optString("detail") }
            }
        }.getOrNull().orEmpty().ifBlank { body.take(200) }

        val prefix = when (code) {
            401, 403 -> "Неверный API-ключ или нет доступа"
            413 -> "Файл слишком большой для сервера"
            429 -> "Превышен лимит запросов, попробуй чуть позже"
            else -> "Ошибка сервера ($code)"
        }
        return if (detail.isBlank()) prefix else "$prefix: $detail"
    }

    // ── Разбор ответа ─────────────────────────────────────────────────────────

    private class RawWord(val text: String, val startMs: Int, val endMs: Int)

    private fun ms(seconds: Double): Int = (seconds * 1000.0).roundToInt().coerceAtLeast(0)

    private fun readWords(arr: JSONArray?, into: MutableList<RawWord>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = o.optString("word").ifBlank { o.optString("text") }.trim()
            if (text.isEmpty()) continue
            val start = ms(o.optDouble("start", 0.0))
            val end = max(ms(o.optDouble("end", 0.0)), start)
            into.add(RawWord(text, start, end))
        }
    }

    private fun parse(json: String): WhisperResult {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw WhisperException("Сервер вернул ответ не в формате JSON")
        }

        val segments = root.optJSONArray("segments")
        val words = ArrayList<RawWord>()
        var wordLevel = true

        // 1) слова на верхнем уровне (OpenAI, Groq)
        readWords(root.optJSONArray("words"), words)
        // 2) слова внутри сегментов (часть самописных серверов)
        if (words.isEmpty() && segments != null) {
            for (i in 0 until segments.length()) {
                readWords(segments.optJSONObject(i)?.optJSONArray("words"), words)
            }
        }

        // Границы сегментов — здесь лучше всего начинать новые строки.
        val segStarts = ArrayList<Int>()
        if (segments != null) {
            for (i in 0 until segments.length()) {
                val s = segments.optJSONObject(i) ?: continue
                if (s.optString("text").isBlank()) continue
                segStarts.add(ms(s.optDouble("start", 0.0)))
            }
        }

        // 3) сервер не дал слова — размазываем слова сегмента по его длительности
        if (words.isEmpty() && segments != null) {
            wordLevel = false
            for (i in 0 until segments.length()) {
                val s = segments.optJSONObject(i) ?: continue
                val tokens = s.optString("text").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (tokens.isEmpty()) continue
                val start = ms(s.optDouble("start", 0.0))
                val end = max(ms(s.optDouble("end", 0.0)), start + 300)
                val total = tokens.sumOf { it.length.coerceAtLeast(1) }
                var acc = 0
                for (t in tokens) {
                    val ws = start + ((end - start).toLong() * acc / total).toInt()
                    acc += t.length.coerceAtLeast(1)
                    val we = start + ((end - start).toLong() * acc / total).toInt()
                    words.add(RawWord(t, ws, we))
                }
            }
        }

        if (words.isEmpty()) {
            if (root.optString("text").isNotBlank()) {
                throw WhisperException("Сервер не вернул тайминги. Нужен ответ verbose_json с timestamp_granularities")
            }
            throw WhisperException("Речь не распознана (инструментал или слишком тихий вокал)")
        }

        // Время слов должно только расти.
        val fixed = ArrayList<RawWord>(words.size)
        var prevStart = 0
        for (w in words) {
            val t = max(w.startMs, prevStart)
            fixed.add(RawWord(w.text, t, max(w.endMs, t)))
            prevStart = t
        }

        // Группировка в строки.
        val breaks = segStarts.drop(1)
        var bp = 0
        val lines = ArrayList<SyncedLine>()
        var cur = ArrayList<RawWord>()

        fun flush() {
            if (cur.isEmpty()) return
            val last = cur.lastIndex
            val synced = cur.mapIndexed { idx, w ->
                SyncedWord(w.startMs, if (idx < last) w.text + " " else w.text)
            }
            lines.add(SyncedLine(synced.first().time, synced.joinToString("") { it.word }, synced))
            cur = ArrayList<RawWord>()
        }

        var prevEnd = 0
        for (w in fixed) {
            var segmentBreak = false
            while (bp < breaks.size && w.startMs >= breaks[bp] - SEGMENT_TOLERANCE_MS) {
                segmentBreak = true
                bp++
            }
            if (cur.isNotEmpty()) {
                val gap = w.startMs - prevEnd
                if (segmentBreak || gap >= LINE_GAP_MS || cur.size >= MAX_WORDS_PER_LINE) flush()
            }
            cur.add(w)
            prevEnd = w.endMs
        }
        flush()

        // Типичные «галлюцинации» Whisper на тишине/музыке.
        val cleaned = lines.filterNot { it.line.split(" ").size <= 8 && JUNK.containsMatchIn(it.line) }
        if (cleaned.isEmpty()) {
            throw WhisperException("Речь не распознана (инструментал или слишком тихий вокал)")
        }
        return WhisperResult(cleaned, wordLevel)
    }
}
