package com.musicplayer.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // milliseconds
    val uri: Uri,
    val albumArtUri: Uri?,
    val folderPath: String? = null,  // parent directory path for Folders screen
    val streamHeaders: Map<String, String> = emptyMap(),
    val isHidden: Boolean = false,  // для исключения из библиотеки без удаления
    val customTitle: String? = null,  // локально измененное название
    val customArtist: String? = null,  // локально измененный исполнитель
    val customAlbumArtUri: Uri? = null  // локально измененная обложка
) {
    // Возвращает отображаемое название (приоритет custom)
    fun displayTitle() = customTitle ?: title

    // Возвращает отображаемого исполнителя (приоритет custom)
    fun displayArtist() = customArtist ?: artist

    // Возвращает отображаемую обложку (приоритет custom)
    fun displayAlbumArt() = customAlbumArtUri ?: albumArtUri
    fun formattedDuration(): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

fun Long.toTimeString(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
