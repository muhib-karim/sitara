package app.sitara.music.core.model

/** A song as a playlist file or another service names it; [link] is a web address the source gave for it, if any. */
data class SourceSong(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long? = null,
    val link: String? = null,
) {
    /** "Artist – Title" for display, or the link when the source named nothing. */
    val label: String get() = listOf(artist, title).filter(String::isNotBlank).joinToString(" – ").ifEmpty { link.orEmpty() }
}

/** Where a song of an import stands: still to look up, found, found but worth a look, not found, or left out by the listener. */
enum class MatchState { PENDING, MATCHED, UNCERTAIN, NOT_FOUND, SKIPPED }

/** A song the importer was not sure about: what the source said, the listener's decision so far, and the best candidates first. */
data class ImportReviewRow(
    val position: Int,
    val source: SourceSong,
    val state: MatchState,
    val match: Track?,
    val candidates: List<Track>,
)

/** An import into the library as far as it has got, by songs in each [MatchState]. */
data class ImportJob(
    val id: Long,
    val name: String,
    val pending: Int,
    val matched: Int,
    val uncertain: Int,
    val notFound: Int,
    val skipped: Int,
    /** True when finishing replaces the songs of the playlist an earlier import of the same source made. */
    val updatesPlaylist: Boolean,
    val review: List<ImportReviewRow>,
) {
    val total: Int get() = pending + matched + uncertain + notFound + skipped
}

/** Why an import could not start or go on, worded for the listener. */
enum class ImportProblem { LINK_NOT_RECOGNISED, PLAYLIST_UNAVAILABLE, OFFLINE, NOTHING_TO_IMPORT, FILE_UNREADABLE, FILE_TOO_LARGE }

/** A finished import: the playlist it made or updated with [added] songs. */
data class ImportResult(val playlistId: Long, val name: String, val added: Int, val updated: Boolean)
