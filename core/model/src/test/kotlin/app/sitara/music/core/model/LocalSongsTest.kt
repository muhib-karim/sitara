package app.sitara.music.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalSongsTest {
    private val uri = "content://media/external/audio/media/42"
    private val local = Track(localTrackId(uri), "Song", "Artist", "Album", 180_000, Artwork(listOf(0)))
    private val streamed = Track("x7kGuhlXmFI", "Song", "Artist", "Album", 180_000, Artwork(listOf(0)))

    @Test
    fun `a song on the phone carries the uri it plays from`() {
        assertTrue(local.isLocal)
        assertEquals(uri, local.localUri)
    }

    @Test
    fun `a streamed song has no uri on the phone`() {
        assertFalse(streamed.isLocal)
        assertNull(streamed.localUri)
    }

    @Test
    fun `songs on the phone are never downloaded`() {
        assertEquals(listOf(streamed), listOf(local, streamed).toDownload(emptyMap()))
    }

    @Test
    fun `only content uris make a local id`() {
        assertNull(Track("local:file:///sdcard/a.mp3", "Song", "Artist", "Album", 0, Artwork(listOf(0))).localUri)
    }

    @Test
    fun `search finds songs on the phone by title, artist or album, whatever the case`() {
        fun song(n: Int, title: String, artist: String, album: String) =
            LocalSong(Track(localTrackId("content://media/external/audio/media/$n"), title, artist, album, 0, Artwork(listOf(0))), null, "Music/$title.mp3", null, 0)
        val songs = listOf(song(1, "Saffron Morning", "Tara Lane", "Orbit"), song(2, "Night Bus", "Kesar", "City Lights"), song(3, "Orbit Theme", "Mira", "Singles"))
        assertEquals(listOf(songs[0].track, songs[2].track), songs.matching("orbit"))
        assertEquals(listOf(songs[1].track), songs.matching("  KESAR "))
        assertEquals(emptyList<Track>(), songs.matching(" "))
    }
}
