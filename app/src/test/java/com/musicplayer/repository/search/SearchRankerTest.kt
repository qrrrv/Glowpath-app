package com.musicplayer.repository.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankerTest {

    private fun track(
        artist: String,
        title: String,
        popularity: Long = 0L,
        fans: Long = 0L,
        source: CatalogSource = CatalogSource.DEEZER_SEARCH,
        position: Int = 0
    ) = CatalogTrack(
        artist = artist,
        title = title,
        album = "",
        durationSeconds = 200,
        coverUrl = "",
        popularity = popularity,
        artistFans = fans,
        position = position,
        source = source
    )

    @Test
    fun originalBeatsCoversForATitleQuery() {
        val ranked = SearchRanker.rank(
            "birds of a feather",
            listOf(
                track("VLX", "BIRDS OF A FEATHER", popularity = 453_171, position = 1),
                track("Billie Eilish", "BIRDS OF A FEATHER", popularity = 963_029, position = 0),
                track("Mocky", "Birds Of A Feather", popularity = 120_000, position = 2)
            )
        )

        assertEquals("Billie Eilish", ranked.first().track.artist)
        assertTrue(ranked.size >= 3)
    }

    @Test
    fun obscureBandNamedLikeAFamousTitleDoesNotWin() {
        val ranked = SearchRanker.rank(
            "birds of a feather",
            listOf(
                track("Birds Of A Feather", "Echoes", popularity = 0, fans = 171, position = 0),
                track("Billie Eilish", "BIRDS OF A FEATHER", popularity = 963_029, position = 1)
            )
        )

        assertEquals("Billie Eilish", ranked.first().track.artist)
    }

    @Test
    fun russianCoversDoNotBeatTheOriginal() {
        val ranked = SearchRanker.rank(
            "мокрые кроссы",
            listOf(
                track("KSYNY", "МОКРЫЕ КРОССЫ", popularity = 355_000, position = 0),
                track("Тима Белорусских", "Мокрые кроссы", popularity = 385_166, position = 1),
                track("SokolBeats", "Мокрые кроссы", popularity = 40_000, position = 2)
            )
        )

        assertEquals("Тима Белорусских", ranked.first().track.artist)
    }

    @Test
    fun artistSeedBeatsUnrelatedPopularTracks() {
        val ranked = SearchRanker.rank(
            "kizaru",
            listOf(
                track("Big Baby Tape", "99 Problems", popularity = 313_759, position = 0),
                track(
                    "Kizaru",
                    "Дежавю",
                    popularity = 277_559,
                    fans = 123_541,
                    source = CatalogSource.DEEZER_ARTIST,
                    position = 0
                ),
                // SearchCatalog copies the resolved artist fan count onto matching
                // free-text rows, so the exact track shares the artist authority.
                track("Kizaru", "Fake ID", popularity = 333_117, fans = 123_541, position = 3)
            )
        )

        assertEquals("Kizaru", ranked.first().track.artist)
        assertEquals("Fake ID", ranked.first().track.title)
    }

    @Test
    fun karaokeIsPushedDown() {
        val ranked = SearchRanker.rank(
            "numb linkin park",
            listOf(
                track("Karaoke Hits", "Numb (Karaoke Version)", popularity = 300_000, position = 0),
                track("Linkin Park", "Numb", popularity = 974_827, position = 1)
            )
        )

        assertEquals("Linkin Park", ranked.first().track.artist)
    }

    @Test
    fun cyrillicAndLatinSpellingsMatchTheSameArtist() {
        val ranked = SearchRanker.rank(
            "мияги",
            listOf(
                track(
                    "Miyagi & Эндшпиль",
                    "I Got Love",
                    fans = 351_027,
                    source = CatalogSource.ITUNES_ARTIST
                )
            )
        )

        assertTrue(ranked.first().score > 2.0)
    }

    @Test
    fun duplicatesAreCollapsed() {
        val ranked = SearchRanker.rank(
            "numb",
            listOf(
                track("Linkin Park", "Numb", popularity = 974_827, position = 0),
                track("Linkin Park", "Numb", popularity = 500_000, position = 5)
            )
        )

        assertEquals(1, ranked.size)
        assertEquals(974_827L, ranked.first().track.popularity)
    }
}
