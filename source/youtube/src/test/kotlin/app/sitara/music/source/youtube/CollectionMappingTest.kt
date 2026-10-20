package app.sitara.music.source.youtube

import app.sitara.music.core.model.SearchFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Playlist pages and music search items, built by hand the way the extractor fills them. */
class CollectionMappingTest {
    private val artistUrl = "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv"
    private val cover = listOf(Image("https://lh3.googleusercontent.com/album=w120-h120-l90-rj", 120, 120, ResolutionLevel.LOW))

    private fun item(videoId: String, seconds: Long = 200, artist: String = "Xefer - Topic") =
        StreamInfoItem(0, "https://www.youtube.com/watch?v=$videoId", "Song $videoId", StreamType.VIDEO_STREAM).apply {
            uploaderName = artist
            uploaderUrl = artistUrl
            duration = seconds
        }

    @Test
    fun `an album page keeps the track order and durations and names the album on every track`() {
        val album = albumPage(
            id = "OLAK5uy_album", title = "Album - Niye Jabe Ki", uploaderName = "Xefer - Topic", uploaderUrl = artistUrl,
            thumbnails = cover, items = listOf(item("x7kGuhlXmFI", 201), item("bbbbbbbbbbb", 185)),
        )

        assertEquals("OLAK5uy_album", album.id)
        assertEquals("Niye Jabe Ki", album.title)
        assertEquals("Xefer", album.artist)
        assertEquals("UCabcdefghijklmnopqrstuv", album.artistId)
        assertNull(album.year)
        assertEquals("https://lh3.googleusercontent.com/album=w544-h544-l90-rj", album.artwork.imageUrl)
        assertEquals(listOf("x7kGuhlXmFI", "bbbbbbbbbbb"), album.tracks.map { it.id })
        assertEquals(listOf(201_000L, 185_000L), album.tracks.map { it.durationMs })
        assertEquals(listOf("Niye Jabe Ki", "Niye Jabe Ki"), album.tracks.map { it.album })
    }

    @Test
    fun `an album without an uploader takes its artist from the tracks`() {
        val album = albumPage("OLAK5uy_album", "Niye Jabe Ki", uploaderName = null, uploaderUrl = null, thumbnails = cover, items = listOf(item("x7kGuhlXmFI")))

        assertEquals("Xefer", album.artist)
        assertEquals("UCabcdefghijklmnopqrstuv", album.artistId)
    }

    @Test
    fun `a playlist page drops repeated and unplayable entries and keeps its curator`() {
        val unplayable = StreamInfoItem(0, "https://www.youtube.com/playlist?list=PL1", "Not a video", StreamType.VIDEO_STREAM)

        val playlist = playlistPage(
            id = "PLmix", title = "Late night", uploaderName = "Sitara fan", thumbnails = cover,
            items = listOf(item("x7kGuhlXmFI"), unplayable, item("x7kGuhlXmFI"), item("bbbbbbbbbbb")),
        )

        assertEquals("PLmix", playlist.id)
        assertEquals("Late night", playlist.title)
        assertEquals("Sitara fan", playlist.curator)
        assertEquals(listOf("x7kGuhlXmFI", "bbbbbbbbbbb"), playlist.tracks.map { it.id })
    }

    @Test
    fun `album search items become album previews linked to their artist`() {
        val album = PlaylistInfoItem(0, "https://music.youtube.com/playlist?list=OLAK5uy_album", "Niye Jabe Ki").apply {
            uploaderName = "Xefer"
            uploaderUrl = artistUrl
            setThumbnails(cover)
        }

        val results = musicResults("xefer", SearchFilter.ALBUMS, listOf(album))

        assertEquals(listOf("OLAK5uy_album"), results.albums.map { it.id })
        assertEquals("UCabcdefghijklmnopqrstuv", results.albums.single().artistId)
        assertTrue(results.tracks.isEmpty() && results.artists.isEmpty() && results.playlists.isEmpty())
    }

    @Test
    fun `artist search items become artists keyed by channel id`() {
        val artist = ChannelInfoItem(0, artistUrl, "Xefer").apply { setThumbnails(cover) }
        val handleOnly = ChannelInfoItem(0, "https://www.youtube.com/@someone", "Someone")

        val results = musicResults("xefer", SearchFilter.ARTISTS, listOf(artist, handleOnly, artist))

        assertEquals(listOf("UCabcdefghijklmnopqrstuv"), results.artists.map { it.id })
        assertEquals("Xefer", results.artists.single().name)
    }

    @Test
    fun `playlist search items become playlist previews with their curator`() {
        val playlist = PlaylistInfoItem(0, "https://music.youtube.com/playlist?list=PLmix", "Late night").apply { uploaderName = "Sitara fan" }

        val results = musicResults("night", SearchFilter.PLAYLISTS, listOf(playlist))

        assertEquals(listOf("PLmix"), results.playlists.map { it.id })
        assertEquals("Sitara fan", results.playlists.single().curator)
        assertTrue(results.playlists.single().tracks.isEmpty())
    }

    @Test
    fun `song searches keep songs only, once each`() {
        val album = PlaylistInfoItem(0, "https://music.youtube.com/playlist?list=OLAK5uy_album", "Niye Jabe Ki")

        val results = musicResults("xefer", SearchFilter.SONGS, listOf(item("x7kGuhlXmFI"), album, item("x7kGuhlXmFI")))

        assertEquals(listOf("x7kGuhlXmFI"), results.tracks.map { it.id })
        assertTrue(results.albums.isEmpty())
    }

    @Test
    fun `every filter maps to a YouTube Music search filter`() {
        assertEquals(listOf("music_songs", "music_albums", "music_artists", "music_playlists"), SearchFilter.entries.map { it.extractorFilter })
    }
}
