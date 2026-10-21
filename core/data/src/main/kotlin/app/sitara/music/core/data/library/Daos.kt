package app.sitara.music.core.data.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedSongsDao {
    @Query("SELECT * FROM liked_songs ORDER BY liked_at DESC")
    fun all(): Flow<List<LikedSongEntity>>

    @Query("SELECT track_id FROM liked_songs")
    fun ids(): Flow<List<String>>

    /** Liking a liked song keeps its first like time. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE track_id = :trackId")
    suspend fun delete(trackId: String)
}

@Dao
abstract class PlayHistoryDao {
    /** Each song once, by its latest play; ids grow with every play, so the newest play has the largest id. */
    @Query("SELECT * FROM play_history WHERE id IN (SELECT MAX(id) FROM play_history GROUP BY track_id) ORDER BY id DESC LIMIT :limit")
    abstract fun recent(limit: Int): Flow<List<PlayEntity>>

    @Insert
    abstract suspend fun insert(play: PlayEntity)

    @Query("SELECT track_id, track_artist_id AS artist_id, MAX(played_at) AS last_played_at, COUNT(*) AS plays FROM play_history GROUP BY track_id")
    abstract fun counts(): Flow<List<PlayCountRow>>

    // Plays kept before 0.9.5 have no time heard and are left out of stats. The bare track columns come from the play
    // with MAX(played_at), so each song shows its latest details.
    @Query(
        """
        SELECT track_id, track_title, track_artist, track_album, track_duration_ms, track_artwork_colors, track_artwork_url, track_explicit,
            track_artist_id, COUNT(*) AS plays, SUM(listened_ms) AS listened_ms, MAX(played_at) AS last_played_at
        FROM play_history WHERE listened_ms IS NOT NULL AND played_at >= :since GROUP BY track_id
        """,
    )
    abstract fun songTotals(since: Long): Flow<List<SongTotalRow>>

    @Query("SELECT played_at, listened_ms FROM play_history WHERE listened_ms IS NOT NULL AND played_at >= :since")
    abstract fun listenTimes(since: Long): Flow<List<ListenTimeRow>>

    @Query("DELETE FROM play_history")
    abstract suspend fun clear()

    /** Returns how many plays it changed: 0 or 1. */
    @Query("UPDATE play_history SET listened_ms = :listenedMs WHERE track_id = :trackId AND played_at = :playedAt")
    abstract suspend fun updateListened(trackId: String, playedAt: Long, listenedMs: Long?): Int

    /** Keeps a play, or updates the time heard of the same play (song and start) when it is kept already. */
    @Transaction
    open suspend fun record(play: PlayEntity) {
        if (updateListened(play.track.id, play.playedAt, play.listenedMs) == 0) insert(play)
    }
}

@Dao
interface SavedDao {
    @Query("SELECT * FROM saved_albums ORDER BY saved_at DESC")
    fun albums(): Flow<List<SavedAlbumEntity>>

    @Query("SELECT id FROM saved_albums")
    fun albumIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(album: SavedAlbumEntity)

    @Query("DELETE FROM saved_albums WHERE id = :id")
    suspend fun deleteAlbum(id: String)

    @Query("SELECT * FROM saved_artists ORDER BY saved_at DESC")
    fun artists(): Flow<List<SavedArtistEntity>>

    @Query("SELECT id FROM saved_artists")
    fun artistIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artist: SavedArtistEntity)

    @Query("DELETE FROM saved_artists WHERE id = :id")
    suspend fun deleteArtist(id: String)
}

@Dao
abstract class PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name,
            (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlist_id = p.id) AS song_count
        FROM playlists p ORDER BY p.updated_at DESC, p.id DESC
        """,
    )
    abstract fun summaries(): Flow<List<PlaylistSummaryRow>>

    // shortcut: reads every entry's cover for the library list; take the first few per playlist in SQL if playlists grow to thousands of songs.
    @Query("SELECT playlist_id, track_artwork_colors AS colors, track_artwork_url AS url FROM playlist_entries ORDER BY playlist_id, position")
    abstract fun covers(): Flow<List<PlaylistCoverRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_entries WHERE playlist_id = :id ORDER BY position")
    abstract fun entries(id: Long): Flow<List<PlaylistEntryEntity>>

    @Insert
    abstract suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updated_at = :at WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String, at: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE playlists SET updated_at = :at WHERE id = :id")
    abstract suspend fun touch(id: Long, at: Long)

    @Query("SELECT track_id FROM playlist_entries WHERE playlist_id = :id ORDER BY position")
    abstract suspend fun trackIds(id: Long): List<String>

    /** Returns the new row id, or -1 when the song is already in the playlist. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(entry: PlaylistEntryEntity): Long

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :id AND track_id = :trackId")
    abstract suspend fun deleteEntry(id: Long, trackId: String)

    @Query("UPDATE playlist_entries SET position = :position WHERE playlist_id = :id AND track_id = :trackId")
    abstract suspend fun setPosition(id: Long, trackId: String, position: Int)

    @Transaction
    open suspend fun add(id: Long, track: TrackColumns, at: Long): Boolean {
        val added = insert(PlaylistEntryEntity(id, trackIds(id).size, track, at)) != -1L
        if (added) touch(id, at)
        return added
    }

    @Transaction
    open suspend fun move(id: Long, from: Int, to: Int, at: Long) {
        val order = trackIds(id).toMutableList()
        if (from !in order.indices || to !in order.indices || from == to) return
        order.add(to, order.removeAt(from))
        renumber(id, order)
        touch(id, at)
    }

    @Transaction
    open suspend fun remove(id: Long, trackId: String, at: Long) {
        deleteEntry(id, trackId)
        renumber(id, trackIds(id))
        touch(id, at)
    }

    private suspend fun renumber(id: Long, order: List<String>) = order.forEachIndexed { position, trackId -> setPosition(id, trackId, position) }
}

@Dao
abstract class PlaybackQueueDao {
    @Query("SELECT * FROM queue_entries ORDER BY position")
    abstract suspend fun entries(): List<QueueEntryEntity>

    @Query("SELECT * FROM queue_state WHERE id = 0")
    abstract suspend fun state(): QueueStateEntity?

    @Insert
    abstract suspend fun insertEntries(entries: List<QueueEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertState(state: QueueStateEntity)

    @Query("DELETE FROM queue_entries")
    abstract suspend fun clearEntries()

    @Query("DELETE FROM queue_state")
    abstract suspend fun clearState()

    /** Replaces the saved queue in one transaction, so a crash mid-save never leaves half a queue. */
    @Transaction
    open suspend fun replace(entries: List<QueueEntryEntity>, state: QueueStateEntity?) {
        clearEntries()
        clearState()
        if (state == null) return
        insertEntries(entries)
        upsertState(state)
    }
}

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE track_id = :trackId")
    suspend fun get(trackId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: LyricsCacheEntity)
}

@Dao
interface SongGainDao {
    @Query("SELECT trim_db FROM song_gain WHERE track_id = :trackId")
    fun trimDb(trackId: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: SongGainEntity)

    @Query("DELETE FROM song_gain WHERE track_id = :trackId")
    suspend fun clear(trackId: String)
}
