package com.musicplayer.repository.search

import com.musicplayer.data.OnlineSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMatcherTest {

    private fun song(artist: String, title: String, duration: String = "") =
        OnlineSong(
            title = title,
            artist = artist,
            duration = duration,
            downloadUrl = "https://example.com/" + title.hashCode() + ".mp3"
        )

    @Test
    fun exactReleaseIsSelected() {
        val songs = listOf(
            song("Linkin Park", "Numb", "3:07"),
            song("Linkin Park", "Numb / Encore", "3:25"),
            song("Lil Bo Weep", "Numb (Linkin Park Cover)", "3:40")
        )

        val match = TrackMatcher.bestMatch(songs, "Linkin Park", "Numb", 187)

        assertEquals("Numb", match?.title)
        assertEquals("Linkin Park", match?.artist)
    }

    @Test
    fun remasteredSuffixStillMatches() {
        val songs = listOf(song("Linkin Park", "Numb - Remastered 2011", "3:08"))

        val match = TrackMatcher.bestMatch(songs, "Linkin Park", "Numb", 188)

        assertEquals("Numb - Remastered 2011", match?.title)
    }

    @Test
    fun collaborationCreditStillMatchesTheArtist() {
        val songs = listOf(song("kizaru, ALBLAK 52", "WHOOP WHOOP", "1:53"))

        val match = TrackMatcher.bestMatch(songs, "Kizaru", "WHOOP WHOOP", 113)

        assertEquals("WHOOP WHOOP", match?.title)
    }

    @Test
    fun cyrillicAndLatinArtistsAreTheSameArtist() {
        val songs = listOf(song("MiyaGi & Эндшпиль", "I Got Love", "4:37"))

        val match = TrackMatcher.bestMatch(songs, "Miyagi & Эндшпиль", "I Got Love", 277)

        assertEquals("I Got Love", match?.title)
    }

    @Test
    fun coverByAnotherArtistIsRejected() {
        val songs = listOf(
            song("Karaoke Hits", "Numb", "3:05"),
            song("Unknown Band", "Numb", "3:10")
        )

        assertNull(TrackMatcher.bestMatch(songs, "Linkin Park", "Numb", 187))
    }

    @Test
    fun durationDecidesBetweenTwoCopies() {
        val songs = listOf(
            song("Linkin Park", "Numb", "5:02"),
            song("Linkin Park", "Numb", "3:07")
        )

        val match = TrackMatcher.bestMatch(songs, "Linkin Park", "Numb", 187)

        assertEquals("3:07", match?.duration)
    }
}
