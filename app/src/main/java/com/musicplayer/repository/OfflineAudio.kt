package com.musicplayer.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.musicplayer.data.AlbumTrack
import com.musicplayer.data.OnlineSong
import com.musicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves album tracks on Hitmos and keeps the audio inside the app.
 *
 * Playback and downloads both go through a local file: streaming a Hitmos URL with
 * MediaPlayer needs headers, blocks on prepare and quietly fails otherwise. A file in
 * the app storage plays instantly and survives restarts through the saved library.
 */
object OfflineAudio {

    private const val TAG = "OfflineAudio"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    suspend fun resolve(track: AlbumTrack, timeoutMs: Long = 22_000L): OnlineSong? =
        withTimeoutOrNull(timeoutMs) {
            runCatching {
                HitmosRepository.findBestMatch(track.artist, track.title, track.durationSeconds)
            }.getOrNull()
        }

    /** Cached copy for instant playback; safe to delete at any time. */
    fun cacheFile(context: Context, track: AlbumTrack): File =
        File(File(context.cacheDir, "stream"), fileName(track, "cached"))

    /** Permanent copy in the app storage, added to the music library. */
    fun libraryFile(context: Context, track: AlbumTrack): File =
        File(File(context.filesDir, "offline"), fileName(track, "offline"))

    suspend fun download(
        song: OnlineSong,
        target: File,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (target.exists() && target.length() > 64 * 1024L) {
            onProgress(100)
            return@withContext true
        }
        var connection: HttpURLConnection? = null
        try {
            target.parentFile?.mkdirs()
            val partial = File(target.absolutePath + ".part")
            connection = (URL(song.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", refererFor(song.downloadUrl))
                setRequestProperty("Accept", "*/*")
            }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Download rejected with HTTP " + code)
                return@withContext false
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0L) {
                            val percent = ((written * 100L) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }
            if (partial.length() > 64 * 1024L) {
                if (target.exists()) target.delete()
                partial.renameTo(target)
            } else {
                partial.delete()
                return@withContext false
            }
            onProgress(100)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Download failed for " + song.downloadUrl, e)
            runCatching { target.delete() }
            false
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    fun buildSong(file: File, track: AlbumTrack, coverUrl: String, albumTitle: String): Song =
        Song(
            id = file.absolutePath.hashCode().toLong() and 0xFFFFFFFFL,
            title = track.title.ifBlank { file.nameWithoutExtension },
            artist = track.artist,
            album = albumTitle.ifBlank { "Загруженное" },
            duration = track.durationSeconds * 1000L,
            uri = Uri.fromFile(file),
            albumArtUri = coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse),
            folderPath = file.parent
        )

    private fun fileName(track: AlbumTrack, prefix: String): String {
        val base = (track.artist + " - " + track.title)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(80)
            .ifBlank { prefix + "_" + track.id }
        return base + ".mp3"
    }

    private fun refererFor(url: String): String = runCatching {
        val parsed = Uri.parse(url)
        (parsed.scheme ?: "https") + "://" + (parsed.host ?: "hitmos.me") + "/"
    }.getOrDefault("https://hitmos.me/")
}
