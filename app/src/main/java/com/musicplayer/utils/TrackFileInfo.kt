package com.musicplayer.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import com.musicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What we REALLY know about a track's file. Every field is null when it could not be read -
 * the UI simply hides such rows (nothing is guessed or invented).
 */
data class TrackFileInfo(
    val isOnline: Boolean = false,
    // file
    val fileName: String? = null,
    val folder: String? = null,
    val extension: String? = null,
    val sizeBytes: Long? = null,
    val addedAtMs: Long? = null,
    // audio stream
    val codec: String? = null,
    val lossless: Boolean? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitsPerSample: Int? = null,
    val channels: Int? = null,
    val durationMs: Long? = null,
    // tags stored inside the file
    val tagTitle: String? = null,
    val tagArtist: String? = null,
    val tagAlbum: String? = null,
    val tagAlbumArtist: String? = null,
    val tagGenre: String? = null,
    val tagYear: String? = null,
    val tagTrack: String? = null,
    val hasEmbeddedCover: Boolean? = null
)

object TrackFileInfoLoader {

    private class Tags(
        val title: String?, val artist: String?, val album: String?, val albumArtist: String?,
        val genre: String?, val year: String?, val track: String?,
        val bitrateBps: Int?, val sampleRate: Int?, val bits: Int?,
        val durationMs: Long?, val hasCover: Boolean
    )

    suspend fun load(context: Context, song: Song): TrackFileInfo = withContext(Dispatchers.IO) {
        val uri = song.uri
        val scheme = uri.scheme

        if (scheme == "http" || scheme == "https") {
            return@withContext TrackFileInfo(isOnline = true, durationMs = song.duration.takeIf { it > 0 })
        }

        var fileName: String? = null
        var path: String? = null
        var size: Long? = null
        var addedMs: Long? = null

        // 1) MediaStore row (content://media/...)
        if (scheme == "content") {
            try {
                val projection = arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_ADDED,
                    @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA
                )
                context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        fileName = c.getString(0)
                        size = c.getLong(1).takeIf { it > 0 }
                        addedMs = c.getLong(2).takeIf { it > 0 }?.times(1000)
                        path = c.getString(3)
                    }
                }
            } catch (_: Exception) { }
        }

        // 2) plain file (downloaded inside the app)
        if (scheme == "file" || scheme == null) {
            val f = uri.path?.let { File(it) }
            if (f != null && f.isFile) {
                fileName = f.name
                path = f.absolutePath
                size = f.length().takeIf { it > 0 }
                addedMs = f.lastModified().takeIf { it > 0 }
            }
        }

        val folder = path?.let { File(it).parent } ?: song.folderPath
        val ext = (fileName ?: path)
            ?.substringAfterLast('.', "")
            ?.uppercase()
            ?.takeIf { it.length in 2..5 }

        // 3) audio stream parameters straight from the container
        var codecMime: String? = null
        var sampleRate: Int? = null
        var channels: Int? = null
        var streamBitrate: Int? = null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    codecMime = mime
                    sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
                    channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                    streamBitrate = format.intOrNull(MediaFormat.KEY_BIT_RATE)
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { extractor.release() }
        }

        // 4) tags
        val tags: Tags? = MediaMetadataRetrieverPool.withRetriever { r ->
            r.setDataSource(context, uri)
            fun meta(key: Int): String? = r.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            Tags(
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE),
                year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR)?.takeIf { it != "0" },
                track = meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                bitrateBps = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                sampleRate = if (Build.VERSION.SDK_INT >= 31)
                    meta(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() else null,
                bits = if (Build.VERSION.SDK_INT >= 31)
                    meta(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() else null,
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                hasCover = r.embeddedPicture != null
            )
        }

        val codec = codecName(codecMime)
        val durationMs = (tags?.durationMs ?: song.duration).takeIf { it > 0 }

        // average bitrate: reported one, or file size / duration as a last resort
        val bitrateKbps = (tags?.bitrateBps ?: streamBitrate)?.takeIf { it > 0 }?.let { (it + 500) / 1000 }
            ?: if (size != null && durationMs != null && durationMs > 0)
                ((size!! * 8L * 1000L) / durationMs / 1000L).toInt().takeIf { it > 0 } else null

        TrackFileInfo(
            fileName = fileName,
            folder = folder,
            extension = ext,
            sizeBytes = size,
            addedAtMs = addedMs,
            codec = codec,
            lossless = codec?.let { it == "FLAC" || it == "ALAC" || it.startsWith("PCM") },
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRate ?: tags?.sampleRate,
            bitsPerSample = tags?.bits,
            channels = channels,
            durationMs = durationMs,
            tagTitle = tags?.title,
            tagArtist = tags?.artist,
            tagAlbum = tags?.album,
            tagAlbumArtist = tags?.albumArtist,
            tagGenre = tags?.genre,
            tagYear = tags?.year,
            tagTrack = tags?.track,
            hasEmbeddedCover = tags?.hasCover
        )
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun codecName(mime: String?): String? = when {
        mime == null -> null
        mime.contains("mpeg", ignoreCase = true) && !mime.contains("mp4") -> "MP3"
        mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
        mime.contains("flac", ignoreCase = true) -> "FLAC"
        mime.contains("vorbis", ignoreCase = true) -> "Vorbis"
        mime.contains("opus", ignoreCase = true) -> "Opus"
        mime.contains("alac", ignoreCase = true) -> "ALAC"
        mime.contains("raw", ignoreCase = true) || mime.contains("wav", ignoreCase = true) -> "PCM"
        mime.contains("amr", ignoreCase = true) -> "AMR"
        else -> mime.substringAfter('/').uppercase()
    }
}
