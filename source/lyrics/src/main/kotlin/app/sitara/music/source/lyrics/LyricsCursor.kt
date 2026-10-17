package app.sitara.music.source.lyrics

import app.sitara.music.core.model.LyricsLine
import app.sitara.music.core.model.LyricsWord

/** Position lookups used by the lyrics screen on every frame, so they stay O(log n). */
object LyricsCursor {
    /** Index of the last line that has started at [positionMs], or -1 before the first line. */
    fun activeLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].startMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    fun activeWordIndex(line: LyricsLine, positionMs: Long): Int {
        var found = -1
        for ((i, word) in line.words.withIndex()) {
            if (word.startMs <= positionMs) found = i else break
        }
        return found
    }

    /** 0..1 fill of a word for the karaoke sweep. */
    fun wordProgress(word: LyricsWord, positionMs: Long): Float {
        val span = (word.endMs - word.startMs).coerceAtLeast(1)
        return ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
    }
}
