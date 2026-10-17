package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.LyricsLine
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

    @Test
    fun `plain lyrics never have an active line`() {
        val plain = Lyrics(listOf(LyricsLine(0, 0, "just"), LyricsLine(0, 0, "words")), synced = false)
        assertEquals(-1, LyricsCursor.activeLineIndex(plain, 99_000))
        assertEquals(0, LyricsCursor.activeLineIndex(lyrics, 1_000))
    }

    @Test
    fun `karaoke sweep reports character offsets for sung and active spans`() {
        val line = LrcParser.parse("[00:01.00]Oh <00:01.50>Hel<00:02.00>lo <00:02.50>world\n[00:04.00]Next").lines.first()
        assertEquals(KaraokeSweep(sungEnd = 0, activeStart = 0, activeEnd = 0, activeProgress = 0f), LyricsCursor.sweep(line, 500))
        assertEquals(KaraokeSweep(sungEnd = 3, activeStart = 3, activeEnd = 6, activeProgress = 0.5f), LyricsCursor.sweep(line, 1_750))
        val done = LyricsCursor.sweep(line, 9_000)
        assertEquals(line.text.length, done.sungEnd)
        assertEquals(done.activeStart, done.activeEnd)
    }
}
