package app.sitara.music.core.data.library

import android.content.Context
import app.sitara.music.core.data.imports.ImportCatalogue
import app.sitara.music.core.data.imports.PlaylistImporter
import app.sitara.music.core.data.local.localIndex
import app.sitara.music.core.data.scrobble.ScrobbleOutbox

/** The listener's library, songs on the phone, the saved play queue, cached lyrics, per-song volume trims, playlist imports and plays waiting to be scrobbled on this device; Room stays an implementation detail of this module. */
class LibraryStore(context: Context) {
    private val database = SitaraDatabase.open(context)
    val likedSongs: LikedSongsRepository = RoomLikedSongs(database.likedSongs())
    val history: PlayHistoryRepository = RoomPlayHistory(database.playHistory())
    val saved: SavedCollectionsRepository = RoomSavedCollections(database.saved())
    val playlists: PlaylistRepository = RoomPlaylists(database.playlists())
    val queue: PlaybackQueueRepository = RoomPlaybackQueue(database.playbackQueue())
    val lyrics: LyricsCacheRepository = RoomLyricsCache(database.lyricsCache())
    val songGain: SongGainRepository = RoomSongGain(database.songGain())
    val scrobbles = ScrobbleOutbox(database.scrobbles())
    val local = context.localIndex(database.local())

    /** Imports into [playlists] that look songs up in [catalogue]. */
    fun importer(catalogue: ImportCatalogue) = PlaylistImporter(database.imports(), catalogue)
}
