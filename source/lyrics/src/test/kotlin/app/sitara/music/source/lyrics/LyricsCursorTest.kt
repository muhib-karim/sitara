package app.sitara.music.source.lyrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LyricsCursorTest {
    private val lyrics = LrcParser.parse(
        "[00:01.00]<00:01.00>one <00:02.00>two\n[00:03.00]three\n[00:06.00]four",
        trackDurationMs = 8_000,
    )

    @Test
    fun `no active line before the first timestamp`() {
        assertEquals(-1, LyricsCursor.activeLineIndex(lyrics.lines, 500))
    }

    @Test
    fun `finds the active line by binary search`() {
        assertEquals(0, LyricsCursor.activeLineIndex(lyrics.lines, 1_000))
        assertEquals(0, LyricsCursor.activeLineIndex(lyrics.lines, 2_999))
        assertEquals(1, LyricsCursor.activeLineIndex(lyrics.lines, 3_000))
        assertEquals(2, LyricsCursor.activeLineIndex(lyrics.lines, 7_999))
    }

    @Test
    fun `reports word progress inside a line`() {
        val line = lyrics.lines[0]
        assertEquals(0f, LyricsCursor.wordProgress(line.words[0], 1_000))
        assertEquals(0.5f, LyricsCursor.wordProgress(line.words[0], 1_500))
        assertEquals(1f, LyricsCursor.wordProgress(line.words[0], 2_500))
        assertEquals(1, LyricsCursor.activeWordIndex(line, 2_100))
    }
}
