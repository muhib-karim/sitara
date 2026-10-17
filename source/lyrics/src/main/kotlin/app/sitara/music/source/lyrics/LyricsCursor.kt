package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.LyricsLine
import app.sitara.music.core.model.LyricsWord

/**
 * Character offsets into [LyricsLine.text] for the karaoke fill: `[0, sungEnd)` is fully sung,
 * `[activeStart, activeEnd)` is the word being sung, [activeProgress] of the way through.
 */
data class KaraokeSweep(
    val sungEnd: Int,
    val activeStart: Int,
    val activeEnd: Int,
    val activeProgress: Float,
)

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

    /** Like the list overload, but unsynced lyrics have no timing, so no line is ever active. */
    fun activeLineIndex(lyrics: Lyrics, positionMs: Long): Int = if (lyrics.synced) activeLineIndex(lyrics.lines, positionMs) else -1

    /** Where the karaoke fill is inside a word-synced line; word texts concatenate to the line text. */
    fun sweep(line: LyricsLine, positionMs: Long): KaraokeSweep {
        var offset = 0
        for (word in line.words) {
            val end = offset + word.text.length
            if (positionMs < word.endMs) {
                val progress = if (positionMs < word.startMs) 0f else wordProgress(word, positionMs)
                return KaraokeSweep(sungEnd = offset, activeStart = offset, activeEnd = if (progress > 0f) end else offset, activeProgress = progress)
            }
            offset = end
        }
        return KaraokeSweep(sungEnd = offset, activeStart = offset, activeEnd = offset, activeProgress = 0f)
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
