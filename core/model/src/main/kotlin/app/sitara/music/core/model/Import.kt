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

/** The songs of a list another service shows publicly; [truncated] when it shows only part of a longer list. */
data class SourceSongList(val name: String, val songs: List<SourceSong>, val truncated: Boolean = false)

/** A Spotify playlist or album by its id. */
data class SpotifyLink(val kind: Kind, val id: String) {
    enum class Kind { PLAYLIST, ALBUM }
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
    /** Where the songs came from: a Spotify list shows its first 100 songs at most. */
    val fromSpotify: Boolean = false,
    /** The source showed only part of the list. */
    val truncated: Boolean = false,
) {
    val total: Int get() = pending + matched + uncertain + notFound + skipped

    /** A Spotify list that showed only its first songs, so the playlist has more. */
    val mayHaveMore: Boolean get() = fromSpotify && truncated
}

/** The most songs Spotify shows of a list without an account. */
const val SPOTIFY_PUBLIC_LIMIT = 100

/** Why an import could not start or go on, worded for the listener. */
enum class ImportProblem {
    LINK_NOT_RECOGNISED,
    PLAYLIST_UNAVAILABLE,
    OFFLINE,
    NOTHING_TO_IMPORT,
    FILE_UNREADABLE,
    FILE_TOO_LARGE,

    /** A Spotify page Sitara can no longer read (Spotify changed it). */
    SPOTIFY_UNREADABLE,

    /** A Spotify link to one song, not a playlist or album. */
    SPOTIFY_SONG_LINK,
}

/** A finished import: the playlist it made or updated with [added] songs. */
data class ImportResult(val playlistId: Long, val name: String, val added: Int, val updated: Boolean)
