package app.sitara.music.source.lyrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LrcParserTest {
    @Test
    fun `parses plain synced lines and sorts them`() {
        val lyrics = LrcParser.parse(
            """
            [ar:Someone]
            [00:12.50]Second line
            [00:01.00]First line
            """.trimIndent(),
            trackDurationMs = 20_000,
        )
        assertTrue(lyrics.synced)
        assertEquals(listOf("First line", "Second line"), lyrics.lines.map { it.text })
        assertEquals(1_000, lyrics.lines[0].startMs)
        assertEquals(12_500, lyrics.lines[0].endMs)
        assertEquals(20_000, lyrics.lines[1].endMs)
    }

    @Test
    fun `repeats a line that carries several timestamps`() {
        val lyrics = LrcParser.parse("[00:01.00][00:05.000]Chorus")
        assertEquals(listOf(1_000L, 5_000L), lyrics.lines.map { it.startMs })
    }

    @Test
    fun `applies the offset tag`() {
        val lyrics = LrcParser.parse("[offset:+500]\n[00:02.00]Late")
        assertEquals(1_500, lyrics.lines.single().startMs)
    }

    @Test
    fun `parses enhanced word timings`() {
        val lyrics = LrcParser.parse("[00:10.00]<00:10.00>Shine <00:10.40>on <00:10.90>me\n[00:12.00]Next")
        val line = lyrics.lines.first()
        assertEquals("Shine on me", line.text)
        assertEquals(listOf("Shine", "on", "me"), line.words.map { it.text })
        assertEquals(listOf(10_000L, 10_400L, 10_900L), line.words.map { it.startMs })
        assertEquals(10_400, line.words[0].endMs)
        assertEquals(12_000, line.words[2].endMs)
    }

    @Test
    fun `falls back to unsynced text without timestamps`() {
        val lyrics = LrcParser.parse("just\nwords")
        assertFalse(lyrics.synced)
        assertEquals(listOf("just", "words"), lyrics.lines.map { it.text })
    }

    @Test
    fun `ignores malformed tags`() {
        val lyrics = LrcParser.parse("[xx:yy]bad\n[00:03.00]good")
        assertEquals(listOf("good"), lyrics.lines.map { it.text })
    }
}
