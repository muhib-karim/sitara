package app.sitara.music.core.model

// A song on the phone keeps its content URI in its id, so a like, a playlist entry, a play or a backup plays it without the index.
private const val LOCAL_ID_PREFIX = "local:"
private const val CONTENT_SCHEME = "content://"

/** The id of the song at [uri] on this phone (a MediaStore or Storage Access Framework content URI). */
fun localTrackId(uri: String): String = LOCAL_ID_PREFIX + uri

/** The content URI the song with this id plays from when it is a file on the phone, otherwise null. */
fun localUriOf(trackId: String): String? = trackId.takeIf { it.startsWith(LOCAL_ID_PREFIX + CONTENT_SCHEME) }?.removePrefix(LOCAL_ID_PREFIX)

/** The content URI this song plays from when it is a file on the phone, otherwise null. */
val Track.localUri: String? get() = localUriOf(id)

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

/** Songs on the phone gathered by album, artist or folder; [key] tells the group apart from others of its kind. */
data class LocalGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val artwork: Artwork,
    val songs: List<Track>,
    /** When its newest song was added. */
    val addedAt: Long,
)

/** Albums by name whatever the case, songs in track order (unnumbered ones after, by title), named by their artist or "Various artists". */
fun List<LocalSong>.albums(): List<LocalGroup> = grouped({ it.track.album.lowercase() }, { it.track.album }) { songs ->
    songs.map { it.track.artist.lowercase() }.distinct().singleOrNull()?.let { songs.first().track.artist } ?: "Various artists"
}

/** Artists by name whatever the case, with how many songs each has. */
fun List<LocalSong>.artists(): List<LocalGroup> = grouped({ it.track.artist.lowercase() }, { it.track.artist }) { songCount(it.size) }

/** The folders the files sit in ("Music/Orbit"), named by the last one; files at the top of the storage are in "Phone storage". */
fun List<LocalSong>.folders(): List<LocalGroup> =
    grouped({ it.path.substringBeforeLast('/', "").ifEmpty { TOP_FOLDER } }, { it.path.substringBeforeLast('/', "").substringAfterLast('/').ifEmpty { TOP_FOLDER } }) {
        songCount(it.size)
    }

private const val TOP_FOLDER = "Phone storage"

private fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"

// Groups keep the title their first song gives them and come newest first.
private fun List<LocalSong>.grouped(key: (LocalSong) -> String, title: (LocalSong) -> String, subtitle: (List<LocalSong>) -> String): List<LocalGroup> =
    groupBy(key).map { (key, members) ->
        val songs = members.sortedWith(compareBy<LocalSong>({ it.trackNumber ?: Int.MAX_VALUE }, { it.track.title.lowercase() }))
        LocalGroup(
            key, title(members.first()), subtitle(songs),
            songs.firstOrNull { it.track.artwork.imageUrl != null }?.track?.artwork ?: songs.first().track.artwork,
            songs.map { it.track }, members.maxOf { it.addedAt },
        )
    }.sortedByDescending { it.addedAt }
