package app.sitara.music.core.data.library

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.UserPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomLikedSongs(private val dao: LikedSongsDao, private val clock: () -> Long = System::currentTimeMillis) : LikedSongsRepository {
    override val tracks: Flow<List<Track>> = dao.all().map { rows -> rows.map { it.track.toTrack() } }
    override val ids: Flow<Set<String>> = dao.ids().map { it.toSet() }

    override suspend fun setLiked(track: Track, liked: Boolean) {
        if (liked) dao.insert(LikedSongEntity(track.toColumns(), clock())) else dao.delete(track.id)
    }
}

class RoomPlayHistory(
    private val dao: PlayHistoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val limit: Int = DEFAULT_LIMIT,
) : PlayHistoryRepository {
    override fun recent(limit: Int): Flow<List<Track>> = dao.recent(limit).map { rows -> rows.map { it.track.toTrack() } }

    override suspend fun record(track: Track) = dao.record(PlayEntity(track = track.toColumns(), playedAt = clock()), keep = limit)

    companion object {
        const val DEFAULT_LIMIT = 500
    }
}

class RoomSavedCollections(private val dao: SavedDao, private val clock: () -> Long = System::currentTimeMillis) : SavedCollectionsRepository {
    override val albums: Flow<List<Album>> = dao.albums().map { rows -> rows.map { it.toAlbum() } }
    override val albumIds: Flow<Set<String>> = dao.albumIds().map { it.toSet() }
    override val artists: Flow<List<Artist>> = dao.artists().map { rows -> rows.map { it.toArtist() } }
    override val artistIds: Flow<Set<String>> = dao.artistIds().map { it.toSet() }

    override suspend fun setSaved(album: Album, saved: Boolean) {
        if (!saved) return dao.deleteAlbum(album.id)
        val art = album.artwork
        dao.insert(SavedAlbumEntity(album.id, album.title, album.artist, album.year, encodeColors(art.colors), art.imageUrl, album.artistId, clock()))
    }

    override suspend fun setSaved(artist: Artist, saved: Boolean) {
        if (!saved) return dao.deleteArtist(artist.id)
        dao.insert(SavedArtistEntity(artist.id, artist.name, encodeColors(artist.artwork.colors), artist.artwork.imageUrl, clock()))
    }
}

class RoomPlaylists(private val dao: PlaylistDao, private val clock: () -> Long = System::currentTimeMillis) : PlaylistRepository {
    override val all: Flow<List<PlaylistSummary>> = dao.summaries().map { rows ->
        rows.map { PlaylistSummary(it.id, it.name, it.songCount, it.coverColors?.let { colors -> Artwork(decodeColors(colors), it.coverUrl) }) }
    }

    override fun playlist(id: Long): Flow<UserPlaylist?> = combine(dao.playlist(id), dao.entries(id)) { playlist, entries ->
        playlist?.let { UserPlaylist(it.id, it.name, entries.map { entry -> entry.track.toTrack() }) }
    }

    override suspend fun create(name: String): Long {
        val at = clock()
        return dao.insert(PlaylistEntity(name = validName(name), createdAt = at, updatedAt = at))
    }

    override suspend fun rename(id: Long, name: String) = dao.rename(id, validName(name), clock())

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun add(id: Long, track: Track): Boolean = dao.add(id, track.toColumns(), clock())

    override suspend fun move(id: Long, from: Int, to: Int) = dao.move(id, from, to, clock())

    override suspend fun remove(id: Long, trackId: String) = dao.remove(id, trackId, clock())

    private fun validName(name: String): String = name.trim().also { require(it.isNotEmpty()) { "A playlist needs a name" } }
}
