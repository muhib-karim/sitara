package app.sitara.music.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ShareLinksTest {
    private val art = Artwork(listOf(0))

    @Test
    fun `songs, albums, playlists and artists share their YouTube Music links`() {
        assertEquals("https://music.youtube.com/watch?v=x7kGuhlXmFI", Track("x7kGuhlXmFI", "Niye Jabe Ki", "Xefer", "", 200_000, art).shareTarget()?.link)
        assertEquals("https://music.youtube.com/playlist?list=OLAK5uy_abc", Album("OLAK5uy_abc", "Album", "Artist", 2024, art, emptyList()).shareTarget()?.link)
        assertEquals("https://music.youtube.com/playlist?list=PL123", Playlist("VLPL123", "Road trip", "", art, emptyList(), curator = "Sam").shareTarget().link)
        assertEquals("Sam", Playlist("PL123", "Road trip", "", art, emptyList(), curator = "Sam").shareTarget().subtitle)
        assertEquals("https://music.youtube.com/channel/UC1", Artist("UC1", "Orchid Lane", art).shareTarget().link)
    }

    @Test
    fun `songs and albums on the phone have no link to share`() {
        val local = Track(localTrackId("content://media/external/audio/media/7"), "Field recording", "Unknown artist", "", 1_000, art)
        assertNull(local.shareTarget())
        assertNull(Album("local-album", "Phone album", "Me", null, art, listOf(local)).shareTarget())
    }
}
