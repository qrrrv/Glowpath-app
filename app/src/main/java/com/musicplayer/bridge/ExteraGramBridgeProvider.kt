package com.musicplayer.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.musicplayer.data.PreferencesManager
import com.musicplayer.data.Song
import com.musicplayer.utils.SongsFileStorage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

class ExteraGramBridgeProvider : ContentProvider() {

    companion object {
        private const val STATUS = 1
        private const val SEARCH = 2
        private const val COVER_CURRENT = 3

        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(ExteraGramBridgeContract.AUTHORITY, "status", STATUS)
            addURI(ExteraGramBridgeContract.AUTHORITY, "search", SEARCH)
            addURI(ExteraGramBridgeContract.AUTHORITY, "cover/current", COVER_CURRENT)
        }

        private val statusColumns = arrayOf(
            "title",
            "artist",
            "album",
            "is_playing",
            "has_song",
            "duration_ms",
            "position_ms",
            "effective_position_ms",
            "current_uri",
            "library_size",
            "last_command_label",
            "last_query",
            "last_import_uri",
            "bridge_queue_size",
            "last_batch_summary",
            "cover_uri",
            "updated_at",
        )

        private val searchColumns = arrayOf(
            "song_id",
            "title",
            "artist",
            "album",
            "duration_ms",
            "uri",
            "score",
        )

    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        return when (matcher.match(uri)) {
            STATUS -> {
                val snapshot = ExteraGramBridgeStateStore.loadSnapshot(context)
                val coverUri = resolveCurrentArtworkUri(context, snapshot)
                MatrixCursor(statusColumns).apply {
                    addRow(
                        arrayOf(
                            snapshot.title,
                            snapshot.artist,
                            snapshot.album,
                            if (snapshot.isPlaying) 1 else 0,
                            if (snapshot.hasSong) 1 else 0,
                            snapshot.durationMs,
                            snapshot.positionMs,
                            snapshot.effectivePositionMs(System.currentTimeMillis()),
                            snapshot.currentUri,
                            snapshot.librarySize,
                            snapshot.lastCommandLabel,
                            snapshot.lastQuery,
                            snapshot.lastImportUri,
                            snapshot.bridgeQueueSize,
                            snapshot.lastBatchSummary,
                            if (coverUri != null) ExteraGramBridgeContract.coverUri().toString() else "",
                            snapshot.updatedAt,
                        )
                    )
                }
            }

            SEARCH -> {
                val query = uri.getQueryParameter("q").orEmpty().trim()
                MatrixCursor(searchColumns).apply {
                    if (query.isNotBlank()) {
                        findSongs(context, query).forEach { (song, score) ->
                            addRow(
                                arrayOf(
                                    song.id,
                                    song.title,
                                    song.artist,
                                    song.album,
                                    song.duration,
                                    song.uri.toString(),
                                    score,
                                )
                            )
                        }
                    }
                }
            }

            else -> null
        }
    }

    override fun getType(uri: Uri): String? {
        return when (matcher.match(uri)) {
            STATUS -> "vnd.android.cursor.item/vnd.${ExteraGramBridgeContract.AUTHORITY}.status"
            SEARCH -> "vnd.android.cursor.dir/vnd.${ExteraGramBridgeContract.AUTHORITY}.search"
            COVER_CURRENT -> "image/*"
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        if (matcher.match(uri) != COVER_CURRENT) return null

        val snapshot = ExteraGramBridgeStateStore.loadSnapshot(context)
        val artworkUri = resolveCurrentArtworkUri(context, snapshot)
            ?: throw FileNotFoundException("No current artwork available")
        val artworkFile = exposeArtworkFile(context, artworkUri)
            ?: throw FileNotFoundException("Failed to expose current artwork")

        return ParcelFileDescriptor.open(artworkFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun findSongs(context: android.content.Context, query: String): List<Pair<Song, Int>> {
        val allSongs = runBlocking {
            SongsFileStorage.load(context)
        } ?: PreferencesManager(context).loadSongs()

        if (allSongs.isEmpty()) return emptyList()
        val needle = query.lowercase()
        return allSongs.mapNotNull { song ->
            val title = song.title.lowercase()
            val artist = song.artist.lowercase()
            val album = song.album.lowercase()
            val full = "$title $artist $album"
            val score = when {
                title == needle -> 320
                "$artist - $title" == needle -> 300
                title.startsWith(needle) -> 260
                artist.startsWith(needle) -> 220
                title.contains(needle) -> 180
                artist.contains(needle) -> 160
                album.contains(needle) -> 120
                full.contains(needle) -> 90
                else -> 0
            }
            if (score > 0) song to score else null
        }
            .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.lowercase() })
            .take(12)
    }

    private fun resolveCurrentSong(context: android.content.Context, snapshot: ExteraGramBridgeSnapshot): Song? {
        val allSongs = runBlocking { SongsFileStorage.load(context) } ?: PreferencesManager(context).loadSongs()
        if (allSongs.isEmpty()) return null
        return allSongs.firstOrNull { it.uri.toString() == snapshot.currentUri }
            ?: allSongs.firstOrNull {
                it.title.equals(snapshot.title, ignoreCase = true) &&
                    it.artist.equals(snapshot.artist, ignoreCase = true)
            }
    }

    private fun resolveCurrentArtworkUri(context: android.content.Context, snapshot: ExteraGramBridgeSnapshot): Uri? {
        val song = resolveCurrentSong(context, snapshot) ?: return null
        return PreferencesManager(context).loadCustomArt(song.id) ?: song.albumArtUri
    }

    private fun exposeArtworkFile(context: android.content.Context, artworkUri: Uri): File? {
        return runCatching {
            val outDir = File(context.cacheDir, "bridge_cover").apply { mkdirs() }
            val outFile = File(outDir, "current_cover.bin")
            context.contentResolver.openInputStream(artworkUri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            outFile
        }.getOrNull()
    }
}
