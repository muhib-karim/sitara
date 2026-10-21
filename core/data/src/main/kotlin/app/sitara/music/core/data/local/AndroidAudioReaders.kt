package app.sitara.music.core.data.local

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/** The permission that lets Sitara read the phone's music: READ_MEDIA_AUDIO from Android 13, READ_EXTERNAL_STORAGE before. */
val musicPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

fun Context.hasMusicPermission(): Boolean = checkSelfPermission(musicPermission) == PackageManager.PERMISSION_GRANTED

/** Whether Sitara still holds the read permission it took for a picked folder. */
fun ContentResolver.keepsFolder(tree: String): Boolean = persistedUriPermissions.any { it.uri.toString() == tree && it.isReadPermission }

/** Keeps read access to a folder from the system picker across restarts. */
fun ContentResolver.keepFolder(tree: Uri) = takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)

/** A picked folder's name as its provider shows it, else the last part of its path. */
fun ContentResolver.folderName(tree: Uri): String {
    val id = DocumentsContract.getTreeDocumentId(tree)
    val shown = try {
        query(DocumentsContract.buildDocumentUriUsingTree(tree, id), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: SecurityException) {
        null
    }
    // External storage ids read "primary:Music/Albums".
    return shown?.takeIf { it.isNotBlank() } ?: id.substringAfterLast('/').substringAfter(':').ifEmpty { "Phone storage" }
}

/** The phone's music as MediaStore lists it, tags included. */
internal class MediaStoreAudio(private val context: Context) : AudioReader {
    override fun files(): List<AudioFile> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val columns = arrayOf(
            MediaStore.Audio.Media._ID, path, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK,
        )
        val query = context.contentResolver.query(collection, columns, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null) ?: return emptyList()
        return query.use { c ->
            List(c.count) {
                c.moveToNext()
                val name = c.getString(2).orEmpty()
                // DATA is the whole path before Android 10, RELATIVE_PATH the folders ("Music/Album/") from 10 on.
                val folders = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getString(1).orEmpty() else c.getString(1).orEmpty().substringBeforeLast('/', "") + "/"
                AudioFile(
                    ContentUris.withAppendedId(collection, c.getLong(0)).toString(), folders + name, c.getLong(3), c.getLong(4) * 1_000,
                    // TRACK holds the disc in its thousands: 2003 is disc 2, track 3.
                    AudioTags(c.getString(5), c.getString(6), c.getString(7), c.getLong(8), (c.getInt(9) % 1_000).takeIf { it > 0 }),
                )
            }
        }
    }

    override fun tags(file: AudioFile): AudioTags = readTags(context, file)

    override fun art(file: AudioFile): ByteArray? = readArt(context, file)
}

/** Every audio file under a picked folder, found by walking its documents. */
internal class FolderAudio(private val context: Context, private val tree: Uri) : AudioReader {
    override fun files(): List<AudioFile> {
        val found = mutableListOf<AudioFile>()
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree) to ""))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (pending.isNotEmpty()) {
            val (parent, folders) = pending.removeFirst()
            context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent), columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2).orEmpty()
                    when {
                        mime == DocumentsContract.Document.MIME_TYPE_DIR -> pending += id to "$folders$name/"
                        mime.startsWith("audio/") || mime == "application/ogg" ->
                            found += AudioFile(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), folders + name, c.getLong(3), c.getLong(4))
                    }
                }
            }
        }
        return found
    }

    override fun tags(file: AudioFile): AudioTags = readTags(context, file)

    override fun art(file: AudioFile): ByteArray? = readArt(context, file)
}

// A file the tag reader cannot open gets no tags, so it is still listed under its file name.
private fun readTags(context: Context, file: AudioFile): AudioTags = retrieve(context, file) { r ->
    AudioTags(
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
        // "3/12" or "3".
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull(),
    )
} ?: AudioTags(null, null, null, 0, null)

private fun readArt(context: Context, file: AudioFile): ByteArray? = retrieve(context, file) { it.embeddedPicture }

private fun <T> retrieve(context: Context, file: AudioFile, read: (MediaMetadataRetriever) -> T): T? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(file.uri))
        read(retriever)
    } catch (e: SecurityException) {
        throw e
    } catch (e: RuntimeException) {
        null
    } finally {
        retriever.release()
    }
}

/** The index of the phone's music and picked folders, reading through the content resolver; covers are kept in the app's files. */
internal fun Context.localIndex(dao: LocalDao): LocalIndex {
    val app = applicationContext
    val covers = File(app.filesDir, "local-covers")
    return LocalIndex(
        dao,
        phoneMusic = { if (app.hasMusicPermission()) MediaStoreAudio(app) else null },
        folderReader = { tree -> if (app.contentResolver.keepsFolder(tree)) FolderAudio(app, Uri.parse(tree)) else null },
        // shortcut: covers stay after their songs go (one small file per album); sweep unused ones if folders come and go often.
        saveArt = { key, bytes ->
            try {
                covers.mkdirs()
                Uri.fromFile(File(covers, "$key.img").apply { writeBytes(bytes) }).toString()
            } catch (e: java.io.IOException) {
                null
            }
        },
    )
}
