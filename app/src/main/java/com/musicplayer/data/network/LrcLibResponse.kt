package com.musicplayer.data.network

/**
 * Represents a response from the LRCLIB API.
 * Using manual JSON parsing (no Gson/Moshi dependency needed).
 */
data class LrcLibResponse(
    val id: Int,
    val name: String,
    val artistName: String,
    val albumName: String,
    val duration: Double,
    val plainLyrics: String?,
    val syncedLyrics: String?
)
