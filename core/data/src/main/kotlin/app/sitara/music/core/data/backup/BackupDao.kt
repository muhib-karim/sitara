package app.sitara.music.core.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.sitara.music.core.data.library.LikedSongEntity
import app.sitara.music.core.data.library.LyricsChoiceEntity
import app.sitara.music.core.data.library.PlayEntity
import app.sitara.music.core.data.library.PlaylistEntity
import app.sitara.music.core.data.library.PlaylistEntryEntity
import app.sitara.music.core.data.library.SavedAlbumEntity
import app.sitara.music.core.data.library.SavedArtistEntity
import app.sitara.music.core.data.library.SongGainEntity

/** Everything a backup holds, as stored: playlists with their songs in order, plays in the order they were kept. */
data class BackupSnapshot(
    val liked: List<LikedSongEntity>,
    val albums: List<SavedAlbumEntity>,
    val artists: List<SavedArtistEntity>,
    val playlists: List<Pair<PlaylistEntity, List<PlaylistEntryEntity>>>,
    val history: List<PlayEntity>,
    val gains: List<SongGainEntity>,
    val lyricsChoices: List<LyricsChoiceEntity>,
)

@Dao
abstract class BackupDao {
    @Query("SELECT * FROM liked_songs ORDER BY liked_at DESC")
    abstract suspend fun liked(): List<LikedSongEntity>

    @Query("SELECT * FROM saved_albums ORDER BY saved_at DESC")
    abstract suspend fun albums(): List<SavedAlbumEntity>

    @Query("SELECT * FROM saved_artists ORDER BY saved_at DESC")
    abstract suspend fun artists(): List<SavedArtistEntity>

    @Query("SELECT * FROM playlists ORDER BY id")
    abstract suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_entries ORDER BY playlist_id, position")
    abstract suspend fun playlistEntries(): List<PlaylistEntryEntity>

    @Query("SELECT * FROM play_history ORDER BY id")
    abstract suspend fun history(): List<PlayEntity>

    @Query("SELECT * FROM song_gain ORDER BY track_id")
    abstract suspend fun gains(): List<SongGainEntity>

    @Query("SELECT * FROM lyrics_choice ORDER BY track_id")
    abstract suspend fun lyricsChoices(): List<LyricsChoiceEntity>

    /** One consistent read of everything a backup holds. */
    @Transaction
    open suspend fun snapshot(): BackupSnapshot {
        val entries = playlistEntries().groupBy { it.playlistId }
        return BackupSnapshot(liked(), albums(), artists(), playlists().map { it to entries[it.id].orEmpty() }, history(), gains(), lyricsChoices())
    }

    @Query("DELETE FROM liked_songs")
    abstract suspend fun clearLiked()

    @Query("DELETE FROM saved_albums")
    abstract suspend fun clearAlbums()

    @Query("DELETE FROM saved_artists")
    abstract suspend fun clearArtists()

    @Query("DELETE FROM playlist_entries")
    abstract suspend fun clearPlaylistEntries()

    @Query("DELETE FROM playlists")
    abstract suspend fun clearPlaylists()

    @Query("DELETE FROM play_history")
    abstract suspend fun clearHistory()

    @Query("DELETE FROM song_gain")
    abstract suspend fun clearGains()

    @Query("DELETE FROM lyrics_choice")
    abstract suspend fun clearLyricsChoices()

    @Insert
    abstract suspend fun insertLiked(rows: List<LikedSongEntity>)

    @Insert
    abstract suspend fun insertAlbums(rows: List<SavedAlbumEntity>)

    @Insert
    abstract suspend fun insertArtists(rows: List<SavedArtistEntity>)

    @Insert
    abstract suspend fun insertPlaylist(row: PlaylistEntity): Long

    @Insert
    abstract suspend fun insertPlaylistEntries(rows: List<PlaylistEntryEntity>)

    @Insert
    abstract suspend fun insertHistory(rows: List<PlayEntity>)

    @Insert
    abstract suspend fun insertGains(rows: List<SongGainEntity>)

    @Insert
    abstract suspend fun insertLyricsChoices(rows: List<LyricsChoiceEntity>)

    /** Replaces the library with [snapshot] in one transaction: all of it, or, if anything fails, none of it. */
    @Transaction
    open suspend fun replaceAll(snapshot: BackupSnapshot) {
        clearLiked()
        clearAlbums()
        clearArtists()
        clearPlaylistEntries()
        clearPlaylists()
        clearHistory()
        clearGains()
        clearLyricsChoices()
        insertLiked(snapshot.liked)
        insertAlbums(snapshot.albums)
        insertArtists(snapshot.artists)
        // Playlists get new ids in the backup's order, so newest first still holds.
        snapshot.playlists.forEach { (playlist, entries) ->
            val id = insertPlaylist(playlist.copy(id = 0))
            insertPlaylistEntries(entries.mapIndexed { position, entry -> entry.copy(playlistId = id, position = position) })
        }
        insertHistory(snapshot.history.map { it.copy(id = 0) })
        insertGains(snapshot.gains)
        insertLyricsChoices(snapshot.lyricsChoices)
    }
}
