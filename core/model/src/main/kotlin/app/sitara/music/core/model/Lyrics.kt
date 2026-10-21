package app.sitara.music.core.model

/** One timed word (or syllable) inside a synced line. Times are in milliseconds. */
data class LyricsWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class LyricsLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricsWord> = emptyList(),
) {
    val isWordSynced: Boolean get() = words.isNotEmpty()
}

/** The song a lyrics source filed the lyrics under; a length of 0 is unknown. */
data class LyricsSong(
    val title: String,
    val artist: String,
    val durationMs: Long,
)

/** Lyrics as a source answered them: [source] names it, [licensor] who licensed the text to it, [matched] the song it matched. */
data class Lyrics(
    val lines: List<LyricsLine>,
    val synced: Boolean,
    val source: String? = null,
    val licensor: String? = null,
    val matched: LyricsSong? = null,
) {
    companion object {
        val Empty = Lyrics(emptyList(), synced = false)
    }
}

/** A lyrics lookup as a provider answered it: LRC text, plain text, both, or neither when nothing was found; with the song it matched and the text's licensor when known. */
data class CachedLyrics(
    val synced: String?,
    val plain: String?,
    val fetchedAtMs: Long,
    val matched: LyricsSong? = null,
    val licensor: String? = null,
)
