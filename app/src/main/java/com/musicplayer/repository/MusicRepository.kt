package com.musicplayer.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.musicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    suspend fun scanAllMusic(minDurationMs: Long = 30_000L): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Неизвестная песня"
                val artist = cursor.getString(artistCol) ?: "Неизвестный исполнитель"
                val album = cursor.getString(albumCol) ?: "Неизвестный альбом"
                val duration = cursor.getLong(durationCol)
                val albumId = cursor.getLong(albumIdCol)
                val filePath = if (dataCol != -1) cursor.getString(dataCol) else null
                val folderPath = filePath?.let { java.io.File(it).parent }

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                )

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = uri,
                        albumArtUri = albumArtUri,
                        folderPath = folderPath
                    )
                )
            }
        }

        return@withContext songs
    }

    fun getSongFromUri(context: Context, uri: Uri): Song? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = try { cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)) } catch (e: Exception) { 0L }
                val title = try { cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) } catch (e: Exception) { uri.lastPathSegment ?: "Песня" }
                val artist = try { cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) } catch (e: Exception) { "Неизвестный" }
                val album = try { cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) } catch (e: Exception) { "" }
                val duration = try { cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)) } catch (e: Exception) { 0L }
                val albumId = try { cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)) } catch (e: Exception) { 0L }

                val albumArtUri = if (albumId > 0) ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                ) else null

                return Song(id, title ?: "Песня", artist ?: "Неизвестный", album ?: "", duration, uri, albumArtUri)
            }
        }

        // Fallback for picked file
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Песня"
        val title = fileName.substringBeforeLast(".")
        return Song(0L, title, "Неизвестный", "", 0L, uri, null)
    }
}
