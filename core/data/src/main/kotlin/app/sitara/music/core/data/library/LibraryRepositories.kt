package app.sitara.music.core.data.library

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.Listen
import app.sitara.music.core.model.ListeningStats
import app.sitara.music.core.model.PlayCount
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.UserPlaylist
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** Songs the listener liked, newest first. */
interface LikedSongsRepository {
    val tracks: Flow<List<Track>>
    val ids: Flow<Set<String>>

    suspend fun setLiked(track: Track, liked: Boolean)
}

/** Plays that counted (heard for 30 s, or half the song), every one kept on this device. */
interface PlayHistoryRepository {
    /** Each song once, most recently played first. */
    fun recent(limit: Int): Flow<List<Track>>

    /** Each kept song's plays and last play, in no particular order. */
    val counts: Flow<List<PlayCount>>

    /** What was played from [sinceMs] on, following new plays; [zone] places plays in the hours of the day. */
    fun stats(sinceMs: Long, zone: ZoneId): Flow<ListeningStats>

    /** Keeps [listen], or updates its time heard when the same play (song and start) is kept already. */
    suspend fun record(listen: Listen)

    /** Forgets every play: Recently played, play counts and stats start over. */
    suspend fun clear()
}

/** Albums and artists the listener saved, newest first. Albums are stored without their songs. */
interface SavedCollectionsRepository {
    val albums: Flow<List<Album>>
    val albumIds: Flow<Set<String>>
    val artists: Flow<List<Artist>>
    val artistIds: Flow<Set<String>>

    suspend fun setSaved(album: Album, saved: Boolean)

    suspend fun setSaved(artist: Artist, saved: Boolean)
}

/** The listener's own playlists. A song appears at most once in a playlist. */
interface PlaylistRepository {
    /** Most recently changed first. */
    val all: Flow<List<PlaylistSummary>>

    /** Emits null once the playlist is deleted. */
    fun playlist(id: Long): Flow<UserPlaylist?>

    /** Returns the new playlist's id; [name] is trimmed and must not be blank. */
    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    suspend fun delete(id: Long)

    /** Adds [track] at the end; false when it is already in the playlist. */
    suspend fun add(id: Long, track: Track): Boolean

    /** Moves the entry at [from] to [to]; out-of-range positions change nothing. */
    suspend fun move(id: Long, from: Int, to: Int)

    suspend fun remove(id: Long, trackId: String)
}

/** The play queue kept for after a restart; there is at most one. */
interface PlaybackQueueRepository {
    suspend fun load(): SavedQueue?

    /** Replaces the saved queue; null forgets it. */
    suspend fun save(queue: SavedQueue?)
}

/** Lyrics lookups by song id, so a song is not looked up again. */
interface LyricsCacheRepository {
    suspend fun get(trackId: String): CachedLyrics?

    suspend fun put(trackId: String, lyrics: CachedLyrics)
}

/** Per-song volume trims, applied before the equaliser. */
interface SongGainRepository {
    /** The song's trim in dB, 0 when none is set; follows changes. */
    fun trimDb(trackId: String): Flow<Int>

    /** Keeps [db] for the song, clamped to ±6 dB; 0 forgets it. */
    suspend fun setTrimDb(trackId: String, db: Int)
}
