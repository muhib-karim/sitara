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
        assertEquals(listOf("Shine ", "on ", "me"), line.words.map { it.text })
        assertEquals(listOf(10_000L, 10_400L, 10_900L), line.words.map { it.startMs })
        assertEquals(10_400, line.words[0].endMs)
        assertEquals(12_000, line.words[2].endMs)
    }

    @Test
    fun `keeps syllables joined and the text before the first word tag`() {
        val lyrics = LrcParser.parse("[00:01.00]Oh <00:01.50>Hel<00:02.00>lo <00:02.50>world\n[00:04.00]Next")
        val line = lyrics.lines.first()
        assertEquals("Oh Hello world", line.text)
        assertEquals(listOf("Oh ", "Hel", "lo ", "world"), line.words.map { it.text })
        assertEquals(listOf(1_000L, 1_500L, 2_000L, 2_500L), line.words.map { it.startMs })
        assertEquals(line.text, line.words.joinToString("") { it.text })
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

    @Test
    fun `parses an LRCLIB file with a negative offset, a repeated chorus and an instrumental break`() {
        val text = checkNotNull(javaClass.getResource("/lrclib-synced.lrc")).readText()

        val lyrics = LrcParser.parse(text, trackDurationMs = 214_000)

        assertTrue(lyrics.synced)
        // offset -250 makes every line 250 ms later; the chorus line plays twice, in time order.
        assertEquals(listOf(12_350L, 17_100L, 21_250L, 24_550L, 80_650L, 85_150L), lyrics.lines.map { it.startMs })
        assertEquals("Carry me home, carry me home", lyrics.lines[1].text)
        assertEquals("Carry me home, carry me home", lyrics.lines[4].text)
        assertEquals("", lyrics.lines[2].text)
        assertEquals(214_000, lyrics.lines.last().endMs)
    }
}
