package app.sitara.music.core.designsystem

import androidx.compose.ui.unit.IntRect
import app.sitara.music.core.model.Artwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverUrlsTest {
    private val albumCover = "https://lh3.googleusercontent.com/cover=w544-h544-l90-rj"
    private val avatar = "https://yt3.ggpht.com/avatar=s544-c-k-c0x00ffffff-no-rj"
    private val videoThumbnail = "https://i.ytimg.com/vi/x7kGuhlXmFI/hqdefault.jpg?sqp=a"

    @Test
    fun `a list row asks for the smallest bucket that covers it`() {
        // 48 dp at xxhdpi is 144 px.
        assertEquals("https://lh3.googleusercontent.com/cover=w240-h240-l90-rj", sizedCoverUrl(albumCover, 144))
    }

    @Test
    fun `an exact bucket is not rounded up`() {
        assertEquals("https://lh3.googleusercontent.com/cover=w120-h120-l90-rj", sizedCoverUrl(albumCover, 120))
    }

    @Test
    fun `now playing is capped at the largest bucket`() {
        assertEquals("https://lh3.googleusercontent.com/cover=w1080-h1080-l90-rj", sizedCoverUrl(albumCover, 4000))
    }

    @Test
    fun `an unknown slot size asks for the default cover size`() {
        assertEquals(albumCover, sizedCoverUrl(albumCover, 0))
    }

    @Test
    fun `artist avatars resize through their single side parameter`() {
        assertEquals("https://yt3.ggpht.com/avatar=s240-c-k-c0x00ffffff-no-rj", sizedCoverUrl(avatar, 200))
    }

    @Test
    fun `video thumbnails and other hosts are fetched as they are`() {
        assertEquals(videoThumbnail, sizedCoverUrl(videoThumbnail, 144))
        assertEquals("https://example.org/a=w100-h100", sizedCoverUrl("https://example.org/a=w100-h100", 144))
    }

    @Test
    fun `only video thumbnails are cropped`() {
        assertTrue(needsSquareCrop(videoThumbnail))
        assertTrue(needsSquareCrop("https://i9.ytimg.com/vi_webp/x/maxresdefault.webp"))
        assertFalse(needsSquareCrop(albumCover))
        assertFalse(needsSquareCrop(avatar))
    }

    @Test
    fun `a 16 by 9 thumbnail keeps its full height, centred`() {
        assertEquals(IntRect(280, 0, 1000, 720), videoThumbnailSquare(1280, 720))
        assertEquals(IntRect(70, 0, 250, 180), videoThumbnailSquare(320, 180))
    }

    @Test
    fun `a 4 by 3 thumbnail drops its letterbox bars`() {
        // hqdefault: the 16:9 picture is 480x270 between 45 px bars.
        assertEquals(IntRect(105, 45, 375, 315), videoThumbnailSquare(480, 360))
    }

    @Test
    fun `artwork without a usable url has no cover to load`() {
        assertNull(coverUrlOf(Artwork(listOf(0xFF6C5CE7.toInt()))))
        assertNull(coverUrlOf(Artwork(listOf(0xFF6C5CE7.toInt()), imageUrl = " ")))
        assertEquals(albumCover, coverUrlOf(Artwork(listOf(0xFF6C5CE7.toInt()), imageUrl = albumCover)))
    }
}
