package app.sitara.music

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import app.sitara.music.core.data.imports.ImportCatalogue
import app.sitara.music.core.data.imports.ImportFailedException
import app.sitara.music.core.data.imports.ImportSource
import app.sitara.music.core.data.imports.PlaylistImporter
import app.sitara.music.core.model.ImportProblem
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SourceSongList
import app.sitara.music.core.model.SpotifyLink
import app.sitara.music.core.model.Track
import app.sitara.music.source.spotify.SpotifyEmbedReader
import app.sitara.music.source.spotify.SpotifyException
import app.sitara.music.source.youtube.YouTubeMusicSource
import app.sitara.music.source.youtube.videoIdFromLink
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Imports read YouTube playlists and look songs up in YouTube Music. Any failure of a lookup is worded as not reaching YouTube Music. */
class YouTubeImportCatalogue(private val youtube: YouTubeMusicSource) : ImportCatalogue {
    override suspend fun playlist(id: String): Playlist = try {
        youtube.wholePlaylist(id, PlaylistImporter.MAX_SONGS) ?: throw ImportFailedException(ImportProblem.LINK_NOT_RECOGNISED)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw e
    } catch (e: ImportFailedException) {
        throw e
    } catch (e: Exception) {
        // The extractor answers private, deleted and region-locked playlists with its own exceptions.
        throw ImportFailedException(ImportProblem.PLAYLIST_UNAVAILABLE, e)
    }

    // A song YouTube will not describe (a player request refused) is looked up by its title instead.
    override suspend fun song(link: String): Track? {
        val id = videoIdFromLink(link) ?: return null
        return try {
            youtube.search("https://music.youtube.com/watch?v=$id", SearchFilter.SONGS).tracks.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<Track> = try {
        youtube.search(query, SearchFilter.SONGS).tracks
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException("YouTube Music search failed", e)
    }
}

/** A public Spotify list for an import, with Spotify's answers worded as import problems. */
suspend fun readSpotifyList(reader: SpotifyEmbedReader, link: SpotifyLink): SourceSongList = try {
    reader.read(link)
} catch (e: SpotifyException) {
    throw ImportFailedException(
        if (e.reason == SpotifyException.Reason.NOT_FOUND) ImportProblem.PLAYLIST_UNAVAILABLE else ImportProblem.SPOTIFY_UNREADABLE,
        e,
    )
}

private const val MAX_FILE_BYTES = 4 * 1024 * 1024

/** The picked file's name and text, read off the main thread; at most 4 MB. */
suspend fun readPlaylistFile(resolver: ContentResolver, uri: Uri): ImportSource.PlaylistFile = withContext(Dispatchers.IO) {
    try {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_FILE_BYTES) throw ImportFailedException(ImportProblem.FILE_TOO_LARGE)
            }
            out.toByteArray()
        } ?: throw ImportFailedException(ImportProblem.FILE_UNREADABLE)
        ImportSource.PlaylistFile(name ?: uri.lastPathSegment ?: "Imported playlist", bytes.decodeToString())
    } catch (e: IOException) {
        throw ImportFailedException(ImportProblem.FILE_UNREADABLE, e)
    } catch (e: SecurityException) {
        throw ImportFailedException(ImportProblem.FILE_UNREADABLE, e)
    }
}
