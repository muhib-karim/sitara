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

    private fun song(n: Int, title: String, artist: String, album: String, path: String, track: Int? = null, added: Long = n.toLong(), art: String? = null) = LocalSong(
        Track(localTrackId("content://media/external/audio/media/$n"), title, artist, album, 0, Artwork(listOf(n), art)), null, path, track, added,
    )

    @Test
    fun `albums gather their songs in track order, named by their artist or as various artists`() {
        val songs = listOf(
            song(1, "Second Light", "Tara Lane", "Orbit", "Music/Orbit/02.mp3", track = 2, added = 9),
            song(2, "Night Bus", "Kesar", "City", "Music/City/01.mp3", track = 1),
            song(3, "Saffron Morning", "Tara Lane", "orbit", "Music/Orbit/01.mp3", track = 1, art = "file:///c.img"),
            song(4, "Guest Verse", "Mira", "City", "Music/City/02.mp3", track = 2),
        )
        val albums = songs.albums()
        assertEquals(listOf("Orbit", "City"), albums.map { it.title })
        assertEquals(listOf("Saffron Morning", "Second Light"), albums[0].songs.map { it.title })
        assertEquals(listOf("Tara Lane", "Various artists"), albums.map { it.subtitle })
        assertEquals("file:///c.img", albums[0].artwork.imageUrl)
    }

    @Test
    fun `an album's songs without track numbers follow the numbered ones by title`() {
        val songs = listOf(song(1, "B", "A", "X", "p/b", track = null), song(2, "Z", "A", "X", "p/z", track = 1), song(3, "A", "A", "X", "p/a", track = null))
        assertEquals(listOf("Z", "A", "B"), songs.albums().single().songs.map { it.title })
    }

    @Test
    fun `artists gather their songs and count them`() {
        val songs = listOf(song(1, "One", "Kesar", "X", "p/1", added = 9), song(2, "Two", "kesar", "Y", "p/2"), song(3, "Three", "Mira", "X", "p/3"))
        val artists = songs.artists()
        assertEquals(listOf("Kesar", "Mira"), artists.map { it.title })
        assertEquals(listOf("2 songs", "1 song"), artists.map { it.subtitle })
    }

    @Test
    fun `folders are where the files sit, named by their last folder`() {
        val songs = listOf(song(1, "One", "A", "X", "Music/Orbit/01.mp3", added = 9), song(2, "Two", "A", "X", "Music/Orbit/02.mp3"), song(3, "Loose", "A", "X", "voice.mp3"))
        val folders = songs.folders()
        assertEquals(listOf("Orbit", "Phone storage"), folders.map { it.title })
        assertEquals(listOf("Music/Orbit", "Phone storage"), folders.map { it.key })
        assertEquals(2, folders[0].songs.size)
    }

    @Test
    fun `groups come with the newest addition first, as their songs were added`() {
        val songs = listOf(song(1, "Old", "A", "Old album", "a/1", added = 1), song(2, "New", "B", "New album", "b/2", added = 9))
        assertEquals(listOf("New album", "Old album"), songs.albums().map { it.title })
    }
}
