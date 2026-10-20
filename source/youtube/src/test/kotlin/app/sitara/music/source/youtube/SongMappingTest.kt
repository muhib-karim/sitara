package app.sitara.music.source.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Items are built by hand the way the extractor's YouTube Music song search fills them. */
class SongMappingTest {
    private fun song(
        url: String = "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
        title: String = "Never Gonna Give You Up",
        artist: String? = "Rick Astley",
        seconds: Long = 214,
        thumbnails: List<Image> = listOf(
            Image("https://lh3.googleusercontent.com/cover=w60-h60-l90-rj", 60, 60, ResolutionLevel.LOW),
            Image("https://lh3.googleusercontent.com/cover=w120-h120-l90-rj", 120, 120, ResolutionLevel.LOW),
        ),
    ) = StreamInfoItem(0, url, title, StreamType.VIDEO_STREAM).apply {
        uploaderName = artist
        duration = seconds
        setThumbnails(thumbnails)
    }

    @Test
    fun `maps id, title, artist and duration`() {
        val track = song().toTrack()!!

        assertEquals("dQw4w9WgXcQ", track.id)
        assertEquals("Never Gonna Give You Up", track.title)
        assertEquals("Rick Astley", track.artist)
        assertEquals("", track.album)
        assertEquals(214_000L, track.durationMs)
    }

    @Test
    fun `takes the largest thumbnail and upgrades it to a large cover`() {
        assertEquals("https://lh3.googleusercontent.com/cover=w544-h544-l90-rj", song().toTrack()!!.artwork.imageUrl)
    }

    @Test
    fun `keeps a thumbnail url that has no size parameters`() {
        val thumbnails = listOf(
            Image("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", 180, 320, ResolutionLevel.LOW),
            Image("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", 360, 480, ResolutionLevel.MEDIUM),
        )

        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", song(thumbnails = thumbnails).toTrack()!!.artwork.imageUrl)
    }

    @Test
    fun `no thumbnail still gives generated cover colours`() {
        val track = song(thumbnails = emptyList()).toTrack()!!

        assertNull(track.artwork.imageUrl)
        assertTrue(track.artwork.colors.size >= 2)
    }

    @Test
    fun `cover colours are stable for a video id`() {
        assertEquals(song().toTrack()!!.artwork.colors, song(title = "Other title").toTrack()!!.artwork.colors)
    }

    @Test
    fun `unknown duration and artist become zero and empty`() {
        val track = song(artist = null, seconds = -1).toTrack()!!

        assertEquals(0L, track.durationMs)
        assertEquals("", track.artist)
    }

    @Test
    fun `reads the video id wherever v sits in the query`() {
        assertEquals("a-b_C1d2E3f", song(url = "https://www.youtube.com/watch?list=RD1&v=a-b_C1d2E3f&t=4").toTrack()!!.id)
    }

    @Test
    fun `an item without a video id is dropped`() {
        assertNull(song(url = "https://music.youtube.com/browse/MPREb_abc").toTrack())
        assertNull(song(url = "https://music.youtube.com/watch?ov=dQw4w9WgXcQ").toTrack())
    }
}
