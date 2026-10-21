package app.sitara.music.core.data.local

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.LocalFolder
import app.sitara.music.core.model.LocalSong
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.localTrackId
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** An audio file a reader found. [tags] come with the listing when the reader has them (MediaStore); otherwise they are read for new and changed files only. */
data class AudioFile(
    val uri: String,
    /** Folders and file name, for display, duplicates and a title when the file has none. */
    val path: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val tags: AudioTags? = null,
)

/** Tags as a file holds them; any may be missing or blank. */
data class AudioTags(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val trackNumber: Int?,
)

/** One place songs come from: the phone's music, or one picked folder. */
interface AudioReader {
    /** Every audio file it can see; throws [SecurityException] once Sitara may no longer read it. */
    fun files(): List<AudioFile>

    fun tags(file: AudioFile): AudioTags

    /** The file's embedded cover as encoded image bytes, or null. */
    fun art(file: AudioFile): ByteArray?
}

/**
 * Songs on the phone: the phone's music (MediaStore) and folders the listener picked (Storage Access Framework). A rescan reads tags
 * only of new and changed files and drops files that are gone; a place Sitara may no longer read keeps its songs, and a folder is
 * marked for relinking. The same song found twice is listed once.
 */
class LocalIndex(
    private val dao: LocalDao,
    /** The phone's music, or null without the permission to read it. */
    private val phoneMusic: () -> AudioReader?,
    /** A picked folder by its tree URI, or null when its read permission is no longer kept. */
    private val folderReader: (String) -> AudioReader?,
    /** Keeps an album's cover under [key] and returns the URI it loads from, or null when it cannot be kept. */
    private val saveArt: (key: String, bytes: ByteArray) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scanning = Mutex()

    /** Every song once, newest first. */
    val songs: Flow<List<LocalSong>> = dao.songs().map { rows -> rows.withoutDuplicates().map { it.toLocalSong() } }

    /** Picked folders, oldest first. */
    val folders: Flow<List<LocalFolder>> = dao.folders().map { rows -> rows.map { LocalFolder(it.uri, it.name, it.songCount, it.linked) } }

    /** Reads the phone's music and every picked folder again. */
    suspend fun rescan() = scanning.withLock {
        scan(null, phoneMusic())
        dao.folderList().forEach { folder ->
            dao.setLinked(folder.uri, scan(folder.uri, folderReader(folder.uri)))
        }
    }

    /** Adds a folder the listener picked, or relinks one Sitara lost access to, and reads it. */
    suspend fun addFolder(uri: String, name: String) = scanning.withLock {
        dao.upsertFolder(LocalFolderEntity(uri, name, linked = true, addedAt = dao.folder(uri)?.addedAt ?: clock()))
        dao.setLinked(uri, scan(uri, folderReader(uri)))
    }

    /** Forgets a folder and its songs; likes and playlists keep theirs. */
    suspend fun removeFolder(uri: String) = scanning.withLock { dao.removeFolder(uri) }

    // Returns whether the place could be read; one that cannot keeps its songs.
    private suspend fun scan(folder: String?, reader: AudioReader?): Boolean {
        if (reader == null) return false
        return try {
            // Readers block on the content resolver and the tag reader.
            withContext(Dispatchers.IO) { index(folder, reader) }
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private suspend fun index(folder: String?, reader: AudioReader) {
        val files = reader.files()
        val known = dao.songsIn(folder).associateBy { it.uri }
        val covers = known.values.mapNotNull { song -> song.artUri?.let { coverKey(song.album, song.artist) to it } }.toMap(HashMap())
        val changed = files.filter { file -> known[file.uri].let { it == null || it.sizeBytes != file.sizeBytes || it.modifiedMs != file.modifiedMs } }
        val found = changed.map { file ->
            val tags = file.tags ?: reader.tags(file)
            val title = tags.title.tag() ?: file.path.substringAfterLast('/').substringBeforeLast('.')
            val artist = tags.artist.tag() ?: UNKNOWN_ARTIST
            val album = tags.album.tag() ?: UNKNOWN_ALBUM
            val key = coverKey(album, artist)
            val art = covers[key] ?: reader.art(file)?.let { saveArt(key, it) }?.also { covers[key] = it }
            LocalSongEntity(
                file.uri, folder, file.path, title, artist, album, tags.durationMs, tags.trackNumber, art, file.sizeBytes, file.modifiedMs,
                addedAt = known[file.uri]?.addedAt ?: clock(),
            )
        }
        val present = files.mapTo(HashSet()) { it.uri }
        dao.applyScan(found, known.keys.filter { it !in present })
    }

    companion object {
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
    }
}

// Blank and "<unknown>" (what older MediaStores store for a missing artist) count as missing.
private fun String?.tag(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }

private fun coverKey(album: String, artist: String) = "${album.lowercase()}\u0000${artist.lowercase()}".hashCode().toUInt().toString(16)

// Same title, artist and album, at most this far apart in length: the same song found twice (a picked folder inside the phone's music).
private const val SAME_LENGTH_MS = 2_000

/** Each song once: the phone's copy over a folder's, then the shorter path. */
internal fun List<LocalSongEntity>.withoutDuplicates(): List<LocalSongEntity> {
    val kept = HashMap<Triple<String, String, String>, MutableList<LocalSongEntity>>()
    val keep = HashSet<String>()
    sortedWith(compareBy<LocalSongEntity>({ it.folder != null }, { it.path.length }, { it.path })).forEach { song ->
        val twins = kept.getOrPut(Triple(song.title.lowercase(), song.artist.lowercase(), song.album.lowercase())) { mutableListOf() }
        if (twins.none { abs(it.durationMs - song.durationMs) <= SAME_LENGTH_MS }) {
            twins += song
            keep += song.uri
        }
    }
    return filter { it.uri in keep }
}

// Two of the theme's deep tones per cover, picked by album, for songs without art of their own.
private val CoverTones = listOf(
    listOf(0xFF3A2A55.toInt(), 0xFFE0A030.toInt()),
    listOf(0xFF1E3A4C.toInt(), 0xFF7FB8C9.toInt()),
    listOf(0xFF4A2320.toInt(), 0xFFD9774B.toInt()),
    listOf(0xFF22382B.toInt(), 0xFF8CC08E.toInt()),
    listOf(0xFF2E2A4A.toInt(), 0xFF9A7BFF.toInt()),
    listOf(0xFF40301A.toInt(), 0xFFF2C14E.toInt()),
)

internal fun LocalSongEntity.toLocalSong() = LocalSong(
    Track(localTrackId(uri), title, artist, album, durationMs, Artwork(CoverTones[Math.floorMod(album.lowercase().hashCode(), CoverTones.size)], artUri)),
    folder, path, trackNumber, addedAt,
)
