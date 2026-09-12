package com.musicplayer.data

data class UserAlbum(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val coverSongId: Long? = null,
    val customCoverUri: String? = null,
    val motionCoverUri: String? = null,
    val description: String = "",
    val isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
