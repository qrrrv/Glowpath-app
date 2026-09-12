package com.musicplayer.data.lyrics

/**
 * A single time-stamped word for word-by-word karaoke.
 */
data class SyncedWord(
    val time: Int,  // milliseconds
    val word: String
)

/**
 * A single time-stamped line of synced lyrics.
 */
data class SyncedLine(
    val time: Int,          // milliseconds
    val line: String,
    val words: List<SyncedWord>? = null
)

/**
 * Parsed lyrics object. Either synced (with timestamps) or plain text.
 */
data class Lyrics(
    val synced: List<SyncedLine>? = null,
    val plain: List<String>? = null,
    val areFromRemote: Boolean = false
) {
    fun isValid(): Boolean = !synced.isNullOrEmpty() || !plain.isNullOrEmpty()
}

/**
 * Current lyrics loading/display state for the UI.
 */
sealed class LyricsState {
    object Idle : LyricsState()
    object Loading : LyricsState()
    data class Found(val lyrics: Lyrics) : LyricsState()
    object NotFound : LyricsState()
    data class Error(val message: String) : LyricsState()
}
