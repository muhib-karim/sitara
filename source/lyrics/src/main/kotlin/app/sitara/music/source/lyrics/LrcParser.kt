package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.LyricsLine
import app.sitara.music.core.model.LyricsWord

/**
 * Parses LRC and enhanced LRC (word timings in `<mm:ss.xx>` tags).
 *
 * Line end times are the next line's start; the last line ends at [trackDurationMs]
 * when known, otherwise a few seconds after it starts.
 */
object LrcParser {
    private val lineTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val metaTag = Regex("""^\[[a-zA-Z]+:.*]$""")
    private const val LAST_LINE_FALLBACK_MS = 4_000L

    fun parse(text: String, trackDurationMs: Long? = null): Lyrics {
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val raw = mutableListOf<Pair<Long, String>>()
        val plain = mutableListOf<String>()

        for (rawLine in text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
            val stamps = mutableListOf<Long>()
            var rest = rawLine
            while (true) {
                val m = lineTag.find(rest) ?: break
                if (m.range.first != 0) break
                stamps += toMs(m.groupValues) - offset
                rest = rest.substring(m.range.last + 1)
            }
            when {
                stamps.isNotEmpty() -> stamps.forEach { raw += it.coerceAtLeast(0) to rest.trim() }
                metaTag.matches(rawLine) -> Unit
                else -> plain += rawLine
            }
        }

        if (raw.isEmpty()) {
            return Lyrics(plain.map { LyricsLine(0, 0, it) }, synced = false)
        }

        val sorted = raw.sortedBy { it.first }
        val lines = sorted.mapIndexed { i, (start, body) ->
            val end = sorted.getOrNull(i + 1)?.first ?: (trackDurationMs ?: (start + LAST_LINE_FALLBACK_MS))
            buildLine(start, end.coerceAtLeast(start), body, offset)
        }
        return Lyrics(lines, synced = true)
    }

    /**
     * Splits a line at its word tags without touching the text between them, so syllables
     * (`<t>Hel<t>lo`) stay joined and separators survive. Text before the first tag is timed
     * from the line start. A tag with no text after it only marks where the previous word ends.
     */
    private fun buildLine(start: Long, end: Long, body: String, offset: Long): LyricsLine {
        val marks = wordTag.findAll(body).toList()
        if (marks.isEmpty()) return LyricsLine(start, end, body)

        val spans = mutableListOf<Pair<String, Long>>()
        val lead = body.substring(0, marks.first().range.first)
        if (lead.isNotEmpty()) spans += lead to start
        val tagTimes = marks.map { (toMs(it.groupValues) - offset).coerceAtLeast(0) }
        val ends = mutableListOf<Long>()
        if (lead.isNotEmpty()) ends += tagTimes.first()
        marks.forEachIndexed { i, m ->
            val textEnd = marks.getOrNull(i + 1)?.range?.first ?: body.length
            val text = body.substring(m.range.last + 1, textEnd)
            if (text.isNotEmpty()) {
                spans += text to tagTimes[i]
                ends += tagTimes.getOrNull(i + 1) ?: end
            }
        }
        val words = spans.mapIndexed { i, (text, wStart) -> LyricsWord(text, wStart, ends[i].coerceAtLeast(wStart)) }
        return LyricsLine(start, end, words.joinToString("") { it.text }, words)
    }

    private fun toMs(groups: List<String>): Long {
        val minutes = groups[1].toLong()
        val seconds = groups[2].toLong()
        val fraction = groups[3]
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return minutes * 60_000 + seconds * 1_000 + millis
    }
}
