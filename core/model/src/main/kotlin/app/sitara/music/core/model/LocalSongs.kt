package app.sitara.music.core.model

// A song on the phone keeps its content URI in its id, so a like, a playlist entry, a play or a backup plays it without the index.
private const val LOCAL_ID_PREFIX = "local:"
private const val CONTENT_SCHEME = "content://"

/** The id of the song at [uri] on this phone (a MediaStore or Storage Access Framework content URI). */
fun localTrackId(uri: String): String = LOCAL_ID_PREFIX + uri

/** The content URI this song plays from when it is a file on the phone, otherwise null. */
val Track.localUri: String? get() = id.takeIf { it.startsWith(LOCAL_ID_PREFIX + CONTENT_SCHEME) }?.removePrefix(LOCAL_ID_PREFIX)

/** A file on the phone rather than a song from a catalogue: no download, link, radio or catalogue lyrics. */
val Track.isLocal: Boolean get() = localUri != null

/** A song from the phone's own music or a folder the listener picked; [folder] is that folder's tree URI, null for the phone's music. */
data class LocalSong(
    val track: Track,
    val folder: String?,
    /** Where the file sits, as folders and a file name ("Music/Albums/01 Song.mp3"). */
    val path: String,
    val trackNumber: Int?,
    val addedAt: Long,
)

/** A folder picked through the system's folder picker; [linked] is false once Sitara may no longer read it. */
data class LocalFolder(
    val uri: String,
    val name: String,
    val songCount: Int,
    val linked: Boolean,
)

/** The songs whose title, artist or album holds [query], ignoring case and the spaces around it; nothing for a blank query. */
fun List<LocalSong>.matching(query: String): List<Track> {
    val q = query.trim().ifEmpty { return emptyList() }
    return map { it.track }.filter { song -> listOf(song.title, song.artist, song.album).any { it.contains(q, ignoreCase = true) } }
}
