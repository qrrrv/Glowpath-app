package com.musicplayer.data.whisper

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import com.musicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

private const val WAV_RATE = 16_000

class PreparedAudio(val file: File, val fileName: String, val mime: String)

/**
 * Готовит аудио для отправки: копирует трек во временный файл и, если формат не подходит
 * или файл больше лимита API (~25 МБ), перекодирует в WAV 16 кГц моно (Whisper всё равно
 * работает именно с таким звуком).
 */
object WhisperAudioPreparer {

    const val MAX_UPLOAD_BYTES = 24L * 1024L * 1024L

    private val SUPPORTED = mapOf(
        "mp3" to "audio/mpeg",
        "mpga" to "audio/mpeg",
        "mpeg" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "mp4" to "audio/mp4",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "ogg" to "audio/ogg",
        "webm" to "audio/webm"
    )

    private fun workDir(context: Context): File =
        File(context.cacheDir, "whisper").also { it.mkdirs() }

    fun cleanup(context: Context) {
        workDir(context).listFiles()?.forEach { it.delete() }
    }

    suspend fun prepare(
        context: Context,
        song: Song,
        onStatus: (String) -> Unit
    ): PreparedAudio = withContext(Dispatchers.IO) {
        val dir = workDir(context)
        dir.listFiles()?.forEach { it.delete() }

        onStatus("Чтение аудиофайла…")
        val ext = detectExtension(context, song.uri)
        val src = File(dir, "source.${ext ?: "bin"}")
        copySource(context, song, src)

        val mime = ext?.let { SUPPORTED[it] }
        if (mime != null && src.length() in 1L..MAX_UPLOAD_BYTES) {
            return@withContext PreparedAudio(src, "song.$ext", mime)
        }

        onStatus("Конвертация аудио (16 кГц, моно)…")
        val wav = File(dir, "song.wav")
        decodeToWav(src, wav)
        src.delete()
        if (wav.length() > MAX_UPLOAD_BYTES) {
            wav.delete()
            throw IOException("Трек слишком длинный для загрузки (лимит ≈ 24 МБ, это примерно 12 минут звука)")
        }
        PreparedAudio(wav, "song.wav", "audio/wav")
    }

    // ── Источник ──────────────────────────────────────────────────────────────

    private fun detectExtension(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
            }
        }
        if (name == null) name = uri.lastPathSegment

        val fromName = name
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.length in 2..5 }
        if (fromName != null) return fromName

        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return when (mime) {
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/x-m4a", "audio/m4a" -> "m4a"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> null
        }
    }

    private suspend fun copySource(context: Context, song: Song, dst: File) {
        val uri = song.uri
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            val conn = URL(uri.toString()).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                song.streamHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("Не удалось скачать трек (HTTP $code)")
                conn.inputStream.use { input ->
                    FileOutputStream(dst).use { out -> copyStream(input, out) }
                }
            } finally {
                conn.disconnect()
            }
        } else {
            val input: InputStream = if (scheme == null || scheme == "file") {
                val path = uri.path ?: throw IOException("Не удалось определить путь к файлу")
                FileInputStream(File(path))
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Не удалось открыть аудиофайл")
            }
            input.use { stream ->
                FileOutputStream(dst).use { out -> copyStream(stream, out) }
            }
        }
    }

    private suspend fun copyStream(input: InputStream, out: OutputStream) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
    }

    // ── Декодирование в WAV 16 кГц моно ───────────────────────────────────────

    private fun MediaFormat.intOr(key: String, def: Int): Int =
        if (containsKey(key)) getInteger(key) else def

    private suspend fun decodeToWav(src: File, dst: File) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var pcmBytes = 0L
        try {
            extractor.setDataSource(src.absolutePath)

            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    trackIndex = i
                    trackFormat = f
                    break
                }
            }
            val fmt = trackFormat ?: throw IOException("В файле не найдена аудиодорожка")
            extractor.selectTrack(trackIndex)

            val mime = fmt.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("Неизвестный формат аудио")
            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(fmt, null, null, 0)
            dec.start()

            var sampleRate = fmt.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var channels = fmt.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2)

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idle = 0

            BufferedOutputStream(FileOutputStream(dst), 64 * 1024).use { out ->
                out.write(ByteArray(44)) // место под WAV-заголовок
                val mixer = PcmDownmixer(out)

                while (!outputDone) {
                    currentCoroutineContext().ensureActive()

                    if (!inputDone) {
                        val inIdx = dec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = dec.getInputBuffer(inIdx)
                            val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                            if (size < 0) {
                                dec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                dec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = dec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx >= 0 -> {
                            idle = 0
                            val outBuf = dec.getOutputBuffer(outIdx)
                            if (outBuf != null && info.size > 0) {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                val shorts: ShortBuffer = outBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                                mixer.feed(shorts, channels, sampleRate)
                            }
                            dec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = dec.outputFormat
                            sampleRate = of.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                            channels = of.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        }
                        else -> {
                            if (inputDone) idle++
                            if (idle > 500) outputDone = true // декодер завис — выходим с тем, что есть
                        }
                    }
                }
                mixer.flush()
                pcmBytes = mixer.bytesWritten
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }

        if (pcmBytes <= 0L) throw IOException("Не удалось декодировать аудио")
        writeWavHeader(dst, pcmBytes)
    }

    private fun writeWavHeader(file: File, dataLen: Long) {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray(Charsets.US_ASCII))
        h.putInt((36L + dataLen).toInt())
        h.put("WAVE".toByteArray(Charsets.US_ASCII))
        h.put("fmt ".toByteArray(Charsets.US_ASCII))
        h.putInt(16)
        h.putShort(1.toShort())                 // PCM
        h.putShort(1.toShort())                 // моно
        h.putInt(WAV_RATE)
        h.putInt(WAV_RATE * 2)                  // байт в секунду
        h.putShort(2.toShort())                 // блок
        h.putShort(16.toShort())                // бит на сэмпл
        h.put("data".toByteArray(Charsets.US_ASCII))
        h.putInt(dataLen.toInt())
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(h.array())
        }
    }
}

/** Сводит каналы в моно и усредняющим «ящиком» понижает частоту до 16 кГц. */
private class PcmDownmixer(private val out: OutputStream) {

    var bytesWritten = 0L
        private set

    private var phase = 0.0
    private var acc = 0.0
    private var cnt = 0
    private val buf = ByteArray(8192)
    private var bufLen = 0

    fun feed(samples: ShortBuffer, channels: Int, sampleRate: Int) {
        val ch = channels.coerceAtLeast(1)
        val step = sampleRate.coerceAtLeast(1).toDouble() / WAV_RATE
        while (samples.remaining() >= ch) {
            var sum = 0
            for (i in 0 until ch) sum += samples.get().toInt()
            acc += sum.toDouble() / ch
            cnt++
            phase += 1.0
            if (phase >= step) {
                val v = (acc / cnt).toInt().coerceIn(-32768, 32767)
                acc = 0.0
                cnt = 0
                while (phase >= step) {
                    phase -= step
                    put(v)
                }
            }
        }
    }

    private fun put(v: Int) {
        buf[bufLen++] = (v and 0xFF).toByte()
        buf[bufLen++] = ((v shr 8) and 0xFF).toByte()
        if (bufLen >= buf.size) flush()
    }

    fun flush() {
        if (bufLen > 0) {
            out.write(buf, 0, bufLen)
            bytesWritten += bufLen
            bufLen = 0
        }
    }
}
