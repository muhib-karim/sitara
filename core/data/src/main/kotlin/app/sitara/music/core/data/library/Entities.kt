package app.sitara.music.core.data.library

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track

/**
 * A song as stored next to whatever refers to it. Each table keeps its own copy, so a row never
 * points at a song another table has dropped.
 */
data class TrackColumns(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "artwork_colors") val artworkColors: String,
    @ColumnInfo(name = "artwork_url") val artworkUrl: String?,
    val explicit: Boolean,
    @ColumnInfo(name = "artist_id") val artistId: String?,
)

@Entity(tableName = "liked_songs", primaryKeys = ["track_id"])
data class LikedSongEntity(
    @Embedded(prefix = "track_") val track: TrackColumns,
    @ColumnInfo(name = "liked_at") val likedAt: Long,
)

/** A play that counted, keyed by its song and start; [listenedMs] is null for plays kept before 0.9.5, which kept no time heard. */
@Entity(tableName = "play_history", indices = [Index("track_id"), Index("played_at")])
data class PlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded(prefix = "track_") val track: TrackColumns,
    @ColumnInfo(name = "played_at") val playedAt: Long,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long?,
)

@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val year: Int?,
    @ColumnInfo(name = "artwork_colors") val artworkColors: String,
    @ColumnInfo(name = "artwork_url") val artworkUrl: String?,
    @ColumnInfo(name = "artist_id") val artistId: String?,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
)

@Entity(tableName = "saved_artists")
data class SavedArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "artwork_colors") val artworkColors: String,
    @ColumnInfo(name = "artwork_url") val artworkUrl: String?,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** One song in a playlist; [position] runs 0 until the playlist's size with no gaps. */
@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlist_id", "track_id"],
    foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlist_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlist_id", "position")],
)
data class PlaylistEntryEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    val position: Int,
    @Embedded(prefix = "track_") val track: TrackColumns,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** A library row for a playlist: its name and size. */
data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "song_count") val songCount: Int,
)

/** One song's cover in a playlist, in playlist order. */
data class PlaylistCoverRow(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    val colors: String,
    val url: String?,
)

/** A song's plays in a stats range, with the details of its latest play. */
data class SongTotalRow(
    @Embedded(prefix = "track_") val track: TrackColumns,
    val plays: Int,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
)

/** A play's start and time heard. */
data class ListenTimeRow(
    @ColumnInfo(name = "played_at") val playedAt: Long,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,
)

/** A song's plays in the kept history. */
data class PlayCountRow(
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "artist_id") val artistId: String?,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
    val plays: Int,
)

// Packed ARGB ints as decimal text: "-6587393,42".
internal fun encodeColors(colors: List<Int>): String = colors.joinToString(",")

internal fun decodeColors(text: String): List<Int> = text.split(',').map(String::toInt)

internal fun Track.toColumns() = TrackColumns(id, title, artist, album, durationMs, encodeColors(artwork.colors), artwork.imageUrl, explicit, artistId)

internal fun TrackColumns.toTrack() = Track(id, title, artist, album, durationMs, Artwork(decodeColors(artworkColors), artworkUrl), explicit, artistId)

internal fun SavedAlbumEntity.toAlbum() = Album(id, title, artist, year, Artwork(decodeColors(artworkColors), artworkUrl), emptyList(), artistId)

internal fun SavedArtistEntity.toArtist() = Artist(id, name, Artwork(decodeColors(artworkColors), artworkUrl))

/** One slot of the saved play queue, by play order; [originalPosition] is its unshuffled place, null when not shuffled. */
@Entity(tableName = "queue_entries")
data class QueueEntryEntity(
    @PrimaryKey val position: Int,
    @ColumnInfo(name = "original_position") val originalPosition: Int?,
    @Embedded(prefix = "track_") val track: TrackColumns,
)

/** The single row saying which queue entry is current and where in it playback was. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "current_index") val currentIndex: Int,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "repeat_mode") val repeatMode: String,
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: String,
    val synced: String?,
    val plain: String?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/** The listener's volume trim for one song, in whole decibels; a song without a row plays at 0 dB. */
@Entity(tableName = "song_gain")
data class SongGainEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "trim_db") val trimDb: Int,
)
