package app.sitara.music.core.data.library

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.Listen
import app.sitara.music.core.model.ListeningStats
import app.sitara.music.core.model.LyricsSong
import app.sitara.music.core.model.LyricsTranslation
import app.sitara.music.core.model.PlayCount
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.SongGain
import app.sitara.music.core.model.SongTotal
import app.sitara.music.core.model.TimedListen
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.UserPlaylist
import java.time.ZoneId
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

class RoomPlayHistory(private val dao: PlayHistoryDao) : PlayHistoryRepository {
    override fun recent(limit: Int): Flow<List<Track>> = dao.recent(limit).map { rows -> rows.map { it.track.toTrack() } }

    override val counts: Flow<List<PlayCount>> = dao.counts().map { rows -> rows.map { PlayCount(it.trackId, it.artistId, it.lastPlayedAt, it.plays) } }

    override fun stats(sinceMs: Long, zone: ZoneId): Flow<ListeningStats> = combine(dao.songTotals(sinceMs), dao.listenTimes(sinceMs)) { songs, times ->
        ListeningStats.of(songs.map { SongTotal(it.track.toTrack(), it.plays, it.listenedMs, it.lastPlayedAt) }, times.map { TimedListen(it.playedAt, it.listenedMs) }, zone)
    }

    override suspend fun record(listen: Listen) = dao.record(PlayEntity(track = listen.track.toColumns(), playedAt = listen.startedAtMs, listenedMs = listen.listenedMs))

    override suspend fun clear() = dao.clear()
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

// A playlist with this many different covers shows them as a 2 x 2 collage.
private const val COLLAGE_COVERS = 4

class RoomPlaylists(private val dao: PlaylistDao, private val clock: () -> Long = System::currentTimeMillis) : PlaylistRepository {
    override val all: Flow<List<PlaylistSummary>> = combine(dao.summaries(), dao.covers()) { rows, covers ->
        val byPlaylist = covers.groupBy({ it.playlistId }) { Artwork(decodeColors(it.colors), it.url) }
        rows.map { PlaylistSummary(it.id, it.name, it.songCount, byPlaylist[it.id].orEmpty().distinct().take(COLLAGE_COVERS)) }
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

    override suspend fun inAnyPlaylist(trackId: String) = dao.inAnyPlaylist(trackId)

    private fun validName(name: String): String = name.trim().also { require(it.isNotEmpty()) { "A playlist needs a name" } }
}

class RoomPlaybackQueue(private val dao: PlaybackQueueDao) : PlaybackQueueRepository {
    // The entries last written; most saves only move the position, which then rewrites one row instead of the queue.
    private var written: List<QueueEntryEntity>? = null

    override suspend fun load(): SavedQueue? {
        val state = dao.state() ?: return null
        val entries = dao.entries()
        val unshuffled = entries.takeIf { rows -> rows.all { it.originalPosition != null } && rows.isNotEmpty() }
            ?.sortedBy { it.originalPosition }?.map { it.position }
        return SavedQueue(entries.map { it.track.toTrack() }, state.currentIndex, state.positionMs, RepeatMode.valueOf(state.repeatMode), unshuffled)
    }

    override suspend fun save(queue: SavedQueue?) {
        val original = queue?.unshuffledOrder?.withIndex()?.associate { (unshuffledAt, playAt) -> playAt to unshuffledAt }
        val entries = queue?.tracks?.mapIndexed { i, track -> QueueEntryEntity(i, original?.get(i), track.toColumns()) }.orEmpty()
        val state = queue?.let { QueueStateEntity(currentIndex = it.currentIndex, positionMs = it.positionMs, repeatMode = it.repeatMode.name) }
        if (state != null && entries == written) {
            dao.upsertState(state)
        } else {
            dao.replace(entries, state)
            written = entries.takeIf { state != null }
        }
    }
}

class RoomLyricsCache(private val dao: LyricsCacheDao) : LyricsCacheRepository {
    override suspend fun get(trackId: String, provider: String): CachedLyrics? = dao.get(trackId, provider)?.let {
        val matched = if (it.title != null && it.artist != null) LyricsSong(it.title, it.artist, it.durationMs ?: 0) else null
        CachedLyrics(it.synced, it.plain, it.fetchedAt, matched, it.licensor)
    }

    override suspend fun put(trackId: String, provider: String, lyrics: CachedLyrics) = dao.put(
        LyricsCacheEntity(
            trackId, provider, lyrics.synced, lyrics.plain, lyrics.fetchedAtMs,
            lyrics.matched?.title, lyrics.matched?.artist, lyrics.matched?.durationMs, lyrics.licensor,
        ),
    )

    override suspend fun forgetMisses(trackId: String) = dao.forgetMisses(trackId)

    override suspend fun choice(trackId: String): String? = dao.choice(trackId)

    override suspend fun choose(trackId: String, provider: String) = dao.choose(LyricsChoiceEntity(trackId, provider))

    // A translated line never holds a newline (the translator flattens them), so newlines can join the lines.
    override suspend fun translation(trackId: String, provider: String, language: String): LyricsTranslation? =
        dao.translation(trackId, provider, language)?.let { LyricsTranslation(it.lines.split("\n"), it.fromLanguage, it.language) }

    override suspend fun putTranslation(trackId: String, provider: String, translation: LyricsTranslation) =
        dao.putTranslation(LyricsTranslationEntity(trackId, provider, translation.toLanguage, translation.fromLanguage, translation.lines.joinToString("\n")))
}

class RoomSongGain(private val dao: SongGainDao) : SongGainRepository {
    override fun trimDb(trackId: String): Flow<Int> = dao.trimDb(trackId).map { it ?: 0 }

    override suspend fun setTrimDb(trackId: String, db: Int) {
        val trim = db.coerceIn(-SongGain.MAX_TRIM_DB, SongGain.MAX_TRIM_DB)
        if (trim == 0) dao.clear(trackId) else dao.put(SongGainEntity(trackId, trim))
    }
}
