package app.sitara.music.source.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeLinksTest {
    @Test
    fun `reads the acceptance track from all three link shapes`() {
        assertEquals("x7kGuhlXmFI", videoIdFromLink("https://music.youtube.com/watch?v=x7kGuhlXmFI"))
        assertEquals("x7kGuhlXmFI", videoIdFromLink("https://www.youtube.com/watch?v=x7kGuhlXmFI"))
        assertEquals("x7kGuhlXmFI", videoIdFromLink("https://youtu.be/x7kGuhlXmFI"))
    }

    @Test
    fun `ignores share and playlist parameters`() {
        assertEquals("x7kGuhlXmFI", videoIdFromLink("https://music.youtube.com/watch?v=x7kGuhlXmFI&si=AbC123xyz"))
        assertEquals("x7kGuhlXmFI", videoIdFromLink("https://www.youtube.com/watch?list=RDAMVM1&v=x7kGuhlXmFI&t=42"))
        assertEquals("x7kGuhlXmFI", videoIdFromLink("https://youtu.be/x7kGuhlXmFI?si=AbC123xyz&t=10"))
    }

    @Test
    fun `accepts mobile hosts, plain http, missing scheme and surrounding share text`() {
        assertEquals("x7kGuhlXmFI", videoIdFromLink("https://m.youtube.com/watch?v=x7kGuhlXmFI"))
        assertEquals("x7kGuhlXmFI", videoIdFromLink("http://youtube.com/watch?v=x7kGuhlXmFI"))
        assertEquals("x7kGuhlXmFI", videoIdFromLink("  music.youtube.com/watch?v=x7kGuhlXmFI  "))
        assertEquals("x7kGuhlXmFI", videoIdFromLink("Niye Jabe Ki by Xefer\nhttps://youtu.be/x7kGuhlXmFI"))
    }

    @Test
    fun `rejects other hosts, look-alike hosts and non-web schemes`() {
        assertNull(videoIdFromLink("https://example.com/watch?v=x7kGuhlXmFI"))
        assertNull(videoIdFromLink("https://youtube.com.example.com/watch?v=x7kGuhlXmFI"))
        assertNull(videoIdFromLink("https://notyoutu.be/x7kGuhlXmFI"))
        assertNull(videoIdFromLink("https://youtube.com@example.com/watch?v=x7kGuhlXmFI"))
        assertNull(videoIdFromLink("javascript://youtube.com/watch?v=x7kGuhlXmFI"))
    }

    @Test
    fun `rejects pages that are not a single video and malformed ids`() {
        assertNull(videoIdFromLink("https://music.youtube.com/playlist?list=PL123"))
        assertNull(videoIdFromLink("https://music.youtube.com/browse/MPREb_abc"))
        assertNull(videoIdFromLink("https://www.youtube.com/watch?ov=x7kGuhlXmFI"))
        assertNull(videoIdFromLink("https://www.youtube.com/watch?v=short"))
        assertNull(videoIdFromLink("https://youtu.be/"))
    }

    @Test
    fun `ordinary search text is not a link`() {
        assertNull(videoIdFromLink("Niye Jabe Ki Xefer"))
        assertNull(videoIdFromLink(""))
    }

    @Test
    fun `video ids are exactly eleven url-safe characters`() {
        assertTrue(isVideoId("x7kGuhlXmFI"))
        assertTrue(isVideoId("a-b_C1d2E3f"))
        assertFalse(isVideoId("tr-01"))
        assertFalse(isVideoId("x7kGuhlXmFI&list=x"))
    }

    @Test
    fun `reads a playlist id from playlist pages on every host and from shared watch links`() {
        assertEquals("PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG", playlistIdFromLink("https://www.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"))
        assertEquals("RDCLAK5uy_kmPRjHDECIcuVwnKsx2Ng7fyNgFKWNJFs", playlistIdFromLink("https://music.youtube.com/playlist?list=RDCLAK5uy_kmPRjHDECIcuVwnKsx2Ng7fyNgFKWNJFs&si=AbC"))
        assertEquals("PL123abc", playlistIdFromLink("m.youtube.com/playlist?feature=share&list=PL123abc"))
        assertEquals("PL123abc", playlistIdFromLink("https://youtu.be/x7kGuhlXmFI?list=PL123abc&si=AbC"))
        assertEquals("PL123abc", playlistIdFromLink("https://www.youtube.com/watch?v=x7kGuhlXmFI&list=PL123abc&index=2"))
        assertEquals("PL123abc", playlistIdFromLink("Check out this playlist\nhttps://music.youtube.com/playlist?list=PL123abc"))
    }

    @Test
    fun `a link without a usable playlist id is not a playlist link`() {
        assertNull(playlistIdFromLink("https://music.youtube.com/watch?v=x7kGuhlXmFI"))
        assertNull(playlistIdFromLink("https://www.youtube.com/playlist?list="))
        assertNull(playlistIdFromLink("https://www.youtube.com/playlist?list=PL1%22%3E"))
        assertNull(playlistIdFromLink("https://example.com/playlist?list=PL123abc"))
        assertNull(playlistIdFromLink("https://youtube.com.example.com/playlist?list=PL123abc"))
        assertNull(playlistIdFromLink("https://music.youtube.com/browse/VLPL123abc"))
        assertNull(playlistIdFromLink("my favourite songs"))
    }
}
