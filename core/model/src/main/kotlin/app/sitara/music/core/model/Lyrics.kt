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

data class Lyrics(
    val lines: List<LyricsLine>,
    val synced: Boolean,
    val source: String? = null,
) {
    companion object {
        val Empty = Lyrics(emptyList(), synced = false)
    }
}

/** A lyrics lookup as a provider answered it: LRC text, plain text, both, or neither when nothing was found. */
data class CachedLyrics(
    val synced: String?,
    val plain: String?,
    val fetchedAtMs: Long,
)
