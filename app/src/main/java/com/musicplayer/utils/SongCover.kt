package com.musicplayer.utils

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.Buffer
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-song cover URI.
 *
 * It is the usual MediaStore album-art URI plus `?song=<id>`; MediaStore ignores the query,
 * so everything that used to open the URI keeps working. Our Coil fetcher uses the song id
 * to find the cover when MediaStore has none (see [SongCoverResolver]).
 */
object SongCoverUri {
    private const val PARAM_SONG = "song"
    private const val ALBUM_ART_BASE = "content://media/external/audio/albumart/"

    fun isMediaStoreAlbumArt(uri: Uri): Boolean =
        uri.scheme == "content" && uri.authority == "media" &&
                uri.path?.contains("/audio/albumart") == true

    fun isSongCover(uri: Uri): Boolean =
        isMediaStoreAlbumArt(uri) && uri.getQueryParameter(PARAM_SONG) != null

    fun songId(uri: Uri): Long? = uri.getQueryParameter(PARAM_SONG)?.toLongOrNull()

    /** The same URI without our query - what MediaStore understands. */
    fun mediaStoreUri(uri: Uri): Uri = uri.buildUpon().clearQuery().build()

    /**
     * Adds the song id to a MediaStore album-art URI (or creates one for MediaStore songs
     * that have no album at all). Custom / remote art URIs are returned untouched.
     */
    fun forSong(songId: Long, songUri: Uri?, albumArtUri: Uri?): Uri? {
        if (albumArtUri != null && !isMediaStoreAlbumArt(albumArtUri)) return albumArtUri
        if (albumArtUri == null && songUri?.authority != "media") return null
        if (albumArtUri != null && albumArtUri.getQueryParameter(PARAM_SONG) != null) return albumArtUri
        if (songId <= 0L) return albumArtUri
        val base = albumArtUri ?: Uri.parse(ALBUM_ART_BASE + "0")
        return base.buildUpon().appendQueryParameter(PARAM_SONG, songId.toString()).build()
    }
}

/**
 * Finds a cover for a song, like BoomingMusic does:
 *  1. MediaStore album art
 *  2. picture embedded in the audio file
 *  3. cover already downloaded earlier
 *  4. Deezer search (artist + title) -> downloaded once and cached
 */
object SongCoverResolver {

    /** Set to false to never contact the internet for covers. */
    @Volatile
    var onlineEnabled: Boolean = true

    private const val TAG = "SongCover"
    private const val LOCAL_MISS_TTL_MS = 5L * 60_000L
    private const val ONLINE_MISS_TTL_MS = 3L * 24 * 60 * 60_000L

    private val localMiss = ConcurrentHashMap<Long, Long>()
    private val songLocks = ConcurrentHashMap<Long, Mutex>()
    private val onlineGate = Semaphore(3) // be gentle with the Deezer API

    private class SongInfo(val title: String?, val artist: String?)

    /** Everything, including the network. Suspend - safe to call from Coil. */
    suspend fun load(context: Context, coverUri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val songId = SongCoverUri.songId(coverUri) ?: return@withContext null
        loadLocal(app, coverUri, songId)?.let { return@withContext it }
        if (!onlineEnabled) return@withContext null
        loadOnline(app, songId)
    }

    /** No network, blocking - for the notification / lock screen. */
    fun loadLocalBlocking(context: Context, coverUri: Uri): ByteArray? {
        val songId = SongCoverUri.songId(coverUri) ?: return null
        return loadLocal(context.applicationContext, coverUri, songId)
    }

    // ── local ────────────────────────────────────────────────────────────────

    private fun loadLocal(ctx: Context, coverUri: Uri, songId: Long): ByteArray? {
        // 1) MediaStore album art
        readBytes { ctx.contentResolver.openInputStream(SongCoverUri.mediaStoreUri(coverUri)) }
            ?.let { return it }

        // 2) embedded picture (skipped for a few minutes after a miss, so lists don't re-probe files)
        val missAt = localMiss[songId]
        val recentlyMissed = missAt != null && System.currentTimeMillis() - missAt < LOCAL_MISS_TTL_MS
        if (!recentlyMissed) {
            val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
            val embedded: ByteArray? = MediaMetadataRetrieverPool.withRetriever { r ->
                r.setDataSource(ctx, songUri)
                r.embeddedPicture
            }
            if (embedded != null && embedded.size > 64) return embedded
            localMiss[songId] = System.currentTimeMillis()
        }

        // 3) cover downloaded earlier
        val cached = File(onlineDir(ctx), "$songId.jpg")
        if (cached.isFile) {
            try { return cached.readBytes() } catch (_: Exception) { }
        }
        return null
    }

    private inline fun readBytes(open: () -> java.io.InputStream?): ByteArray? =
        try {
            open()?.use { it.readBytes() }?.takeIf { it.size > 64 }
        } catch (_: Exception) {
            null
        }

    // ── online (Deezer) ──────────────────────────────────────────────────────

    private suspend fun loadOnline(ctx: Context, songId: Long): ByteArray? {
        val dir = onlineDir(ctx)
        val image = File(dir, "$songId.jpg")
        val none = File(dir, "$songId.none")

        if (image.isFile) return runCatching { image.readBytes() }.getOrNull()
        if (none.isFile && System.currentTimeMillis() - none.lastModified() < ONLINE_MISS_TTL_MS) return null

        val lock = songLocks.getOrPut(songId) { Mutex() }
        return lock.withLock {
            if (image.isFile) return@withLock runCatching { image.readBytes() }.getOrNull()

            val info = queryInfo(ctx, songId) ?: return@withLock null
            val query = buildQuery(info)
            if (query == null) {
                markNone(none)
                return@withLock null
            }

            onlineGate.withPermit {
                currentCoroutineContext().ensureActive()
                try {
                    val bytes = fetchFromDeezer(query.first, query.second)
                    if (bytes == null) {
                        markNone(none)
                        null
                    } else {
                        runCatching { image.writeBytes(bytes) }
                        none.delete()
                        bytes
                    }
                } catch (e: IOException) {
                    // offline / timeout / rate limit: don't remember it as "no cover"
                    Log.w(TAG, "Online cover lookup failed for song $songId: ${e.message}")
                    null
                } catch (e: org.json.JSONException) {
                    Log.w(TAG, "Bad Deezer response for song $songId", e)
                    null
                }
            }
        }
    }

    private fun markNone(file: File) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(ByteArray(0))
            file.setLastModified(System.currentTimeMillis())
        }
    }

    private fun onlineDir(ctx: Context): File =
        File(ctx.cacheDir, "online_covers").also { it.mkdirs() }

    private fun queryInfo(ctx: Context, songId: Long): SongInfo? {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        val projection = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        return try {
            ctx.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) SongInfo(c.getString(0), c.getString(1)) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── query building ───────────────────────────────────────────────────────

    private val NOISE = Regex(
        """\s*[(\[{][^)\]}]*(official|video|audio|lyric|lyrics|clip|клип|\bmv\b|\bhd\b|\bhq\b|4k|remaster|visualizer|текст|премьера)[^)\]}]*[)\]}]""",
        RegexOption.IGNORE_CASE
    )
    private val EXTENSION = Regex("""\.(mp3|m4a|flac|wav|ogg|opus|aac)$""", RegexOption.IGNORE_CASE)
    private val ARTIST_SPLIT = Regex("""\s*(?:,|&|;|/|feat\.?|ft\.?)\s*""", RegexOption.IGNORE_CASE)

    private fun isUnknown(artist: String): Boolean =
        artist.isBlank() ||
                artist.contains("неизвест", ignoreCase = true) ||
                artist.contains("unknown", ignoreCase = true) ||
                artist == "<unknown>"

    private fun cleanTitle(raw: String): String =
        raw.replace(EXTENSION, "").replace(NOISE, "").replace('"', ' ').trim()

    private fun buildQuery(info: SongInfo): Pair<String, String>? {
        var artist = info.artist.orEmpty().replace('"', ' ').trim()
        var title = cleanTitle(info.title.orEmpty())
        if (title.isBlank()) return null

        val dash = title.indexOf(" - ")
        if (isUnknown(artist)) {
            // "Artist - Title" is the only hint we have
            if (dash <= 0) return null
            artist = title.substring(0, dash).trim()
            title = title.substring(dash + 3).trim()
        } else if (dash > 0 && norm(title.substring(0, dash)) == norm(artist)) {
            title = title.substring(dash + 3).trim()
        }
        if (artist.isBlank() || title.isBlank()) return null
        return artist to title
    }

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private fun loose(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        return minOf(a.length, b.length) >= 3 && (a.contains(b) || b.contains(a))
    }

    private fun artistMatches(requested: String, found: String): Boolean {
        val f = norm(found)
        if (f.isEmpty()) return false
        if (loose(norm(requested), f)) return true
        return requested.split(ARTIST_SPLIT).map { norm(it) }.any { loose(it, f) }
    }

    // ── Deezer ───────────────────────────────────────────────────────────────

    /** @return image bytes, or null if Deezer has nothing suitable. Throws [IOException] on network problems. */
    private fun fetchFromDeezer(artist: String, title: String): ByteArray? {
        val queries = listOf("artist:\"$artist\" track:\"$title\"", "$artist $title")
        for (q in queries) {
            val body = httpGet("https://api.deezer.com/search?limit=5&q=" + URLEncoder.encode(q, "UTF-8"))
            val json = JSONObject(String(body, Charsets.UTF_8))
            if (json.has("error")) throw IOException("Deezer error: ${json.opt("error")}")
            val data = json.optJSONArray("data") ?: continue

            var best: JSONObject? = null
            var bestScore = 0
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val foundArtist = item.optJSONObject("artist")?.optString("name").orEmpty()
                if (!artistMatches(artist, foundArtist)) continue
                val score = if (loose(norm(title), norm(item.optString("title")))) 2 else 1
                if (score > bestScore) {
                    best = item
                    bestScore = score
                }
            }

            val album = best?.optJSONObject("album") ?: continue
            val url = listOf("cover_xl", "cover_big", "cover_medium", "cover")
                .map { album.optString(it) }
                .firstOrNull { it.isNotBlank() && !it.contains("/images/artist//") }
                ?: continue

            val bytes = httpGet(url)
            if (isImage(bytes)) return bytes
        }
        return null
    }

    private fun isImage(bytes: ByteArray): Boolean {
        if (bytes.size < 1024) return false
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return opts.outWidth > 0 && opts.outHeight > 0
    }

    @Throws(IOException::class)
    private fun httpGet(url: String, maxBytes: Int = 4 * 1024 * 1024): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "Glowpath/1.0")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IOException("response too large")
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }
}

/** Coil fetcher for [SongCoverUri] URIs. Returning null lets Coil fall back to its default content:// fetcher. */
class SongCoverFetcher(
    private val data: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = SongCoverResolver.load(options.context, data) ?: return null
        return SourceResult(
            source = ImageSource(Buffer().apply { write(bytes) }, options.context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (SongCoverUri.isSongCover(data)) SongCoverFetcher(data, options) else null
    }
}
