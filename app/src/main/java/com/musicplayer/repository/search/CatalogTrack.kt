package com.musicplayer.repository.search

/** Where a catalog candidate came from. */
internal enum class CatalogSource {
    /** Free-text Deezer search: already ordered by Deezer relevance. */
    DEEZER_SEARCH,

    /** Top tracks of an artist resolved from the query. */
    DEEZER_ARTIST,

    /** iTunes search result (metadata only, no popularity rank). */
    ITUNES_SEARCH,

    /** Top tracks of an artist resolved through the iTunes catalog. */
    ITUNES_ARTIST;

    val isArtistSeed: Boolean
        get() = this == DEEZER_ARTIST || this == ITUNES_ARTIST

    /** Results of a free-text search, ordered by the provider itself. */
    val isTextSearch: Boolean
        get() = this == DEEZER_SEARCH || this == ITUNES_SEARCH
}

/**
 * A single track known to the catalog. Metadata only: a playable file is resolved
 * later against Hitmos.
 */
internal data class CatalogTrack(
    val artist: String,
    val title: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val coverUrl: String = "",
    /** Deezer popularity rank, 0 when unknown. */
    val popularity: Long = 0L,
    /** Deezer fan count of the artist, 0 when unknown. */
    val artistFans: Long = 0L,
    /** Position inside the provider response; keeps provider ordering for ties. */
    val position: Int = 0,
    val source: CatalogSource = CatalogSource.DEEZER_SEARCH
)
