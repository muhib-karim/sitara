package app.sitara.music.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPolicyTest {
    @Test
    fun `the player loads only queue tracks and https`() {
        assertTrue(StreamPolicy.mayLoad("sitara"))
        assertTrue(StreamPolicy.mayLoad("https"))
        assertFalse(StreamPolicy.mayLoad("file"))
        assertFalse(StreamPolicy.mayLoad("content"))
        assertFalse(StreamPolicy.mayLoad("http"))
        assertFalse(StreamPolicy.mayLoad(null))
    }

    @Test
    fun `resolved streams must be https`() {
        assertTrue(StreamPolicy.mayStream("https://example.org/a.mp3"))
        assertFalse(StreamPolicy.mayStream("http://example.org/a.mp3"))
        assertFalse(StreamPolicy.mayStream("file:///sdcard/a.mp3"))
    }
}
