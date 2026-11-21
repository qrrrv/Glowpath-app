package com.musicplayer.data

data class AlbumTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val durationSeconds: Int = 0,
    val position: Int = 0
)

data class AlbumDetail(
    val id: Long,
    val title: String,
    val artist: String,
    val coverUrl: String = "",
    val year: String = "",
    val kind: String = "",
    val tracks: List<AlbumTrack> = emptyList()
)

sealed class AlbumPageState {
    object Idle : AlbumPageState()
    data class Loading(val albumId: Long) : AlbumPageState()
    data class Success(val album: AlbumDetail) : AlbumPageState()
    data class Empty(val albumId: Long) : AlbumPageState()
}
