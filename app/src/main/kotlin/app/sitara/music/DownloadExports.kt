package app.sitara.music

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import app.sitara.music.core.data.export.AudioContainer
import app.sitara.music.core.data.export.SongTags
import app.sitara.music.core.data.export.UnsupportedAudioException
import app.sitara.music.core.data.export.exportFileName
import app.sitara.music.core.data.export.isOpusWebm
import app.sitara.music.core.data.export.lrcFile
import app.sitara.music.core.data.export.sidecarName
import app.sitara.music.core.data.export.sniffContainer
import app.sitara.music.core.data.export.tagMp4
import app.sitara.music.core.data.export.webmOpusToOgg
import app.sitara.music.core.data.library.LyricsCacheRepository
import app.sitara.music.core.model.DownloadExport
import app.sitara.music.core.model.ExportProblem
import app.sitara.music.core.model.Track
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A folder songs are written into. A name already there is overwritten, so exporting again does not make copies. */
interface ExportFolder {
    fun names(): Set<String>

    /** Writes the file [name] through [content], replacing it if it is there. */
    fun write(name: String, content: (OutputStream) -> Unit)
}

private val webmOpusToOgg: (ByteArray, OutputStream, SongTags) -> Unit = { bytes, out, tags -> webmOpusToOgg(bytes, out, tags) }
private val mp4Tagger: (ByteArray, OutputStream, SongTags) -> Unit = { bytes, out, tags -> tagMp4(bytes, tags, out) }

/**
 * Turns downloaded songs into files a music app or a computer can read: WebM Opus becomes Ogg Opus, MP4 is tagged in
 * place, both with the title, artist, album, cover and plain lyrics; synced lyrics go into an .lrc file beside the song.
 */
class DownloadExporter(
    private val downloaded: suspend () -> List<Track>,
    private val readWhole: suspend (String) -> ByteArray?,
    private val lyrics: LyricsCacheRepository,
    private val lyricSources: List<String>,
    private val cover: suspend (Track) -> ByteArray?,
) {
    suspend fun export(folder: ExportFolder, progress: (done: Int, total: Int) -> Unit): DownloadExport {
        val songs = downloaded()
        if (songs.isEmpty()) return DownloadExport.Failed(ExportProblem.NOTHING_DOWNLOADED)
        var exported = 0
        var withLyrics = 0
        var untagged = 0
        var skipped = 0
        withContext(Dispatchers.IO) { folder.names() }
        // Names taken in this export, without their extension and in any case: two songs of one name both keep their
        // file and their .lrc, also on card file systems that ignore case; a name from an earlier export is replaced.
        val taken = mutableSetOf<String>()
        songs.forEachIndexed { index, track ->
            progress(index, songs.size)
            // A song too big for memory is skipped like one not fully downloaded, not the end of the export.
            val written = try {
                exportOne(track, folder, taken)
            } catch (e: OutOfMemoryError) {
                null
            }
            if (written == null) {
                skipped++
            } else {
                exported++
                if (written.lyrics) withLyrics++
                if (!written.tagged) untagged++
            }
        }
        progress(songs.size, songs.size)
        return DownloadExport.Done(exported, withLyrics, untagged, skipped)
    }

    private class Written(val lyrics: Boolean, val tagged: Boolean)

    private suspend fun exportOne(track: Track, folder: ExportFolder, taken: MutableSet<String>): Written? {
        val bytes = readWhole(track.id) ?: return null
        val (extension, tagger) = when (sniffContainer(bytes.copyOf(16))) {
            AudioContainer.WEBM -> if (withContext(Dispatchers.Default) { isOpusWebm(bytes) }) "opus" to webmOpusToOgg else "webm" to null
            AudioContainer.MP4 -> "m4a" to mp4Tagger
            AudioContainer.UNKNOWN -> return null
        }
        val (synced, plain) = lyricsOf(track.id)
        val tags = SongTags(track.title, track.artist, track.album, track.durationMs, cover(track), "image/jpeg", plain)
        val name = generateSequence(1) { it + 1 }
            .map { n -> exportFileName(track.artist, if (n == 1) track.title else "${track.title} ($n)", extension) }
            .first { it.substringBeforeLast('.').lowercase() !in taken }
        taken += name.substringBeforeLast('.').lowercase()
        val tagged = withContext(Dispatchers.IO) {
            val wroteTags = tagger != null && try {
                folder.write(name) { out -> tagger(bytes, out, tags) }
                true
            } catch (e: UnsupportedAudioException) {
                false
            } catch (e: IndexOutOfBoundsException) {
                // Data the parsers did not expect: the file is copied as it is (a second write replaces any part written).
                false
            } catch (e: IllegalArgumentException) {
                false
            }
            if (!wroteTags) folder.write(name) { it.write(bytes) }
            wroteTags
        }
        if (synced != null) withContext(Dispatchers.IO) { folder.write(sidecarName(name, "lrc")) { it.write(lrcFile(tags, synced).toByteArray()) } }
        return Written(lyrics = synced != null, tagged = tagged)
    }

    /** Synced lyrics for an .lrc file and plain text for the tag: the listener's chosen source first, then any that has them. */
    private suspend fun lyricsOf(trackId: String): Pair<String?, String?> {
        val chosen = lyrics.choice(trackId)
        val rows = (listOfNotNull(chosen) + lyricSources).distinct().mapNotNull { lyrics.get(trackId, it) }
        val synced = rows.firstNotNullOfOrNull { it.synced?.takeIf(String::isNotBlank) }
        val plain = rows.firstNotNullOfOrNull { it.plain?.takeIf(String::isNotBlank) }
        return synced to plain
    }
}

/** Settings → Downloads → Export and Library's Downloads: one export at a time, its progress and outcome. */
class DownloadExports(
    private val scope: CoroutineScope,
    private val exporter: DownloadExporter,
    private val folderOf: (String) -> ExportFolder,
) {
    private val status = MutableStateFlow<DownloadExport>(DownloadExport.Idle)
    val state: StateFlow<DownloadExport> = status

    fun export(treeUri: String) {
        if (status.value is DownloadExport.Working) return
        status.value = DownloadExport.Working(0, 0)
        scope.launch {
            status.value = try {
                exporter.export(folderOf(treeUri)) { done, total -> status.value = DownloadExport.Working(done, total) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A folder that cannot be listed or written (a revoked grant, a full drive, a provider error).
                DownloadExport.Failed(ExportProblem.FOLDER_UNWRITABLE)
            }
        }
    }
}

/**
 * A folder picked through the system folder picker. Files are created as `application/octet-stream`, because a
 * provider adds its own extension to a name whose extension does not match the type it is given (".opus.ogg").
 */
class DocumentTreeFolder(private val resolver: ContentResolver, tree: Uri) : ExportFolder {
    private val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    private val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    private val known = mutableMapOf<String, Uri>()

    override fun names(): Set<String> {
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) known[cursor.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0))
        } ?: throw IOException("Folder cannot be listed")
        return known.keys
    }

    override fun write(name: String, content: (OutputStream) -> Unit) {
        val uri = known[name] ?: DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)?.also { known[name] = it }
            ?: throw IOException("Cannot create $name")
        // "wt" truncates, so a shorter file over an older export keeps no stale tail.
        resolver.openOutputStream(uri, "wt")?.use { raw ->
            val buffered = raw.buffered()
            content(buffered)
            buffered.flush()
        } ?: throw IOException("Cannot write $name")
    }
}
