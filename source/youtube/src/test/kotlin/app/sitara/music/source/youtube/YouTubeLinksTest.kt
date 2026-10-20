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
}
