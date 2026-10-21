package app.sitara.music.core.data.backup

import app.sitara.music.core.data.library.LikedSongEntity
import app.sitara.music.core.data.library.LyricsChoiceEntity
import app.sitara.music.core.data.library.PlayEntity
import app.sitara.music.core.data.library.PlaylistEntity
import app.sitara.music.core.data.library.PlaylistEntryEntity
import app.sitara.music.core.data.library.SavedAlbumEntity
import app.sitara.music.core.data.library.SavedArtistEntity
import app.sitara.music.core.data.library.SongGainEntity
import app.sitara.music.core.data.library.TrackColumns
import app.sitara.music.core.model.SongGain
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Why a file could not be restored; the library is untouched in every case. */
enum class BackupProblem { NOT_A_BACKUP, DAMAGED, NEWER_VERSION }

class BackupException(val problem: BackupProblem) : Exception(problem.name)

/** What a backup holds, counted for the listener. */
data class BackupSummary(val likedSongs: Int, val playlists: Int, val plays: Int)

/** A written backup's text and what it holds. */
class BackupFile(val text: String, val summary: BackupSummary)

/** What a restore brought back; the caller applies [settings] with the settings store. */
class RestoredBackup(val settings: Map<String, String>, val summary: BackupSummary)

/**
 * Sitara's backup: likes, saved albums and artists, playlists (in order), every kept play, per-song volume trims and lyric
 * sources, and the app's settings, as one versioned JSON document. Restoring reads and checks the whole file before it
 * writes anything, then replaces the library in one transaction. Songs on the phone are kept by their content URI; picked
 * folders, scrobbling sign-ins and caches are not part of a backup.
 */
class LibraryBackups(private val dao: BackupDao, private val clock: () -> Long = System::currentTimeMillis) {
    /** The library and [settings] (as [app.sitara.music.core.data.SettingsCodec] writes them) as backup text. */
    suspend fun export(settings: Map<String, String>, appVersion: String): BackupFile {
        val snapshot = dao.snapshot()
        val text = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("appVersion", appVersion)
            .put("createdAt", clock())
            .put("liked", snapshot.liked.toJson { JSONObject().put("track", it.track.toJson()).put("likedAt", it.likedAt) })
            .put(
                "savedAlbums",
                snapshot.albums.toJson {
                    JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist).putOpt("year", it.year).put("artworkColors", it.artworkColors.colorsJson())
                        .putOpt("artworkUrl", it.artworkUrl).putOpt("artistId", it.artistId).put("savedAt", it.savedAt)
                },
            )
            .put(
                "savedArtists",
                snapshot.artists.toJson {
                    JSONObject().put("id", it.id).put("name", it.name).put("artworkColors", it.artworkColors.colorsJson()).putOpt("artworkUrl", it.artworkUrl).put("savedAt", it.savedAt)
                },
            )
            .put(
                "playlists",
                snapshot.playlists.toJson { (playlist, entries) ->
                    JSONObject().put("name", playlist.name).put("createdAt", playlist.createdAt).put("updatedAt", playlist.updatedAt)
                        .put("songs", entries.toJson { JSONObject().put("track", it.track.toJson()).put("addedAt", it.addedAt) })
                },
            )
            .put("history", snapshot.history.toJson { JSONObject().put("track", it.track.toJson()).put("playedAt", it.playedAt).put("listenedMs", it.listenedMs ?: JSONObject.NULL) })
            .put("songVolume", snapshot.gains.toJson { JSONObject().put("trackId", it.trackId).put("trimDb", it.trimDb) })
            .put("lyricsSource", snapshot.lyricsChoices.toJson { JSONObject().put("trackId", it.trackId).put("provider", it.provider) })
            .put("settings", JSONObject(settings))
            .toString()
        return BackupFile(text, snapshot.summary())
    }

    /**
     * Replaces the library with the backup in [text] and returns its settings for the caller to apply. Throws
     * [BackupException] without writing anything when the text is not a whole backup this version can read.
     */
    suspend fun restore(text: String): RestoredBackup {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            // A cut-off backup still opens with its format name.
            throw BackupException(if (text.contains("\"$FORMAT\"")) BackupProblem.DAMAGED else BackupProblem.NOT_A_BACKUP)
        }
        if (json.opt("format") != FORMAT) throw BackupException(BackupProblem.NOT_A_BACKUP)
        val version = json.opt("schemaVersion") as? Int ?: damaged()
        if (version > SCHEMA_VERSION) throw BackupException(BackupProblem.NEWER_VERSION)
        if (version < 1) damaged()
        val (snapshot, settings) = try {
            json.toSnapshot() to json.obj("settings").let { s -> s.keys().asSequence().associateWith { s.opt(it) as? String ?: damaged() } }
        } catch (e: JSONException) {
            damaged()
        }
        dao.replaceAll(snapshot)
        return RestoredBackup(settings, snapshot.summary())
    }

    companion object {
        const val FORMAT = "sitara-backup"

        /** Bumped when the document changes shape; a restore migrates older versions and refuses newer ones. */
        const val SCHEMA_VERSION = 1
    }
}

private fun BackupSnapshot.summary() = BackupSummary(liked.size, playlists.size, history.size)

private fun damaged(): Nothing = throw BackupException(BackupProblem.DAMAGED)

private fun <T> List<T>.toJson(item: (T) -> JSONObject) = JSONArray().also { array -> forEach { array.put(item(it)) } }

private fun String.colorsJson() = JSONArray().also { array -> split(',').forEach { array.put(it.toInt()) } }

private fun TrackColumns.toJson() = JSONObject().put("id", id).put("title", title).put("artist", artist).put("album", album).put("durationMs", durationMs)
    .put("artworkColors", artworkColors.colorsJson()).putOpt("artworkUrl", artworkUrl).put("explicit", explicit).putOpt("artistId", artistId)

// Reading: every value is checked for its type, so a damaged or edited file fails here, before anything is written.
private fun JSONObject.obj(key: String): JSONObject = opt(key) as? JSONObject ?: damaged()

private fun JSONObject.list(key: String): List<JSONObject> = (opt(key) as? JSONArray ?: damaged()).let { array -> List(array.length()) { array.opt(it) as? JSONObject ?: damaged() } }

private fun JSONObject.text(key: String): String = opt(key) as? String ?: damaged()

private fun JSONObject.name(key: String): String = text(key).takeIf { it.isNotBlank() } ?: damaged()

private fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else text(key)

private fun JSONObject.long(key: String): Long = (opt(key) as? Number)?.toLong()?.takeIf { it >= 0 } ?: damaged()

private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else long(key)

private fun JSONObject.colors(key: String): String {
    val array = opt(key) as? JSONArray ?: damaged()
    if (array.length() == 0) damaged()
    return List(array.length()) { (array.opt(it) as? Number)?.toInt() ?: damaged() }.joinToString(",")
}

private fun JSONObject.track(): TrackColumns = obj("track").run {
    TrackColumns(
        name("id"), name("title"), text("artist"), text("album"), long("durationMs"), colors("artworkColors"), textOrNull("artworkUrl"),
        opt("explicit") as? Boolean ?: damaged(), textOrNull("artistId"),
    )
}

// The library keeps a song once per list (liked, each playlist) and one trim and source per song; repeats in an edited file are dropped.
private fun JSONObject.toSnapshot() = BackupSnapshot(
    liked = list("liked").map { LikedSongEntity(it.track(), it.long("likedAt")) }.distinctBy { it.track.id },
    albums = list("savedAlbums").map {
        SavedAlbumEntity(it.name("id"), it.name("title"), it.text("artist"), it.longOrNull("year")?.toInt(), it.colors("artworkColors"), it.textOrNull("artworkUrl"), it.textOrNull("artistId"), it.long("savedAt"))
    }.distinctBy { it.id },
    artists = list("savedArtists").map { SavedArtistEntity(it.name("id"), it.name("name"), it.colors("artworkColors"), it.textOrNull("artworkUrl"), it.long("savedAt")) }.distinctBy { it.id },
    playlists = list("playlists").map { playlist ->
        val songs = playlist.list("songs").map { PlaylistEntryEntity(0, 0, it.track(), it.long("addedAt")) }.distinctBy { it.track.id }
        PlaylistEntity(name = playlist.name("name"), createdAt = playlist.long("createdAt"), updatedAt = playlist.long("updatedAt")) to songs
    },
    history = list("history").map { PlayEntity(track = it.track(), playedAt = it.long("playedAt"), listenedMs = it.longOrNull("listenedMs")) },
    gains = list("songVolume").map {
        val db = (it.opt("trimDb") as? Int ?: damaged()).coerceIn(-SongGain.MAX_TRIM_DB, SongGain.MAX_TRIM_DB)
        SongGainEntity(it.name("trackId"), db)
    }.distinctBy { it.trackId },
    lyricsChoices = list("lyricsSource").map { LyricsChoiceEntity(it.name("trackId"), it.name("provider")) }.distinctBy { it.trackId },
)
