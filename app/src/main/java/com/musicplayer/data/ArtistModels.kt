package com.musicplayer.data

/** Everything the artist page needs, assembled from the Deezer and iTunes catalogs. */
data class ArtistProfile(
    val id: Long,
    val name: String,
    val imageUrl: String = "",
    val fans: Long = 0L,
    val albumCount: Int = 0,
    val link: String = ""
)

data class RelatedArtist(
    val id: Long,
    val name: String,
    val imageUrl: String = ""
)

data class ArtistAlbum(
    val id: Long,
    val title: String,
    val coverUrl: String = "",
    val year: String = "",
    val kind: String = "",
    val link: String = ""
)

data class ArtistVideo(
    val id: Long,
    val title: String,
    val artist: String = "",
    val thumbnailUrl: String = "",
    val externalUrl: String = ""
)

data class ArtistPage(
    val profile: ArtistProfile,
    val related: List<RelatedArtist> = emptyList(),
    val albums: List<ArtistAlbum> = emptyList(),
    val videos: List<ArtistVideo> = emptyList()
)

sealed class ArtistPageState {
    object Idle : ArtistPageState()
    data class Loading(val name: String) : ArtistPageState()
    data class Success(val page: ArtistPage) : ArtistPageState()
    data class Empty(val name: String) : ArtistPageState()
}
