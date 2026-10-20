package app.sitara.music.core.data.library

import android.content.Context

/** The listener's library, the saved play queue and cached lyrics on this device; Room stays an implementation detail of this module. */
class LibraryStore(context: Context) {
    private val database = SitaraDatabase.open(context)
    val likedSongs: LikedSongsRepository = RoomLikedSongs(database.likedSongs())
    val history: PlayHistoryRepository = RoomPlayHistory(database.playHistory())
    val saved: SavedCollectionsRepository = RoomSavedCollections(database.saved())
    val playlists: PlaylistRepository = RoomPlaylists(database.playlists())
    val queue: PlaybackQueueRepository = RoomPlaybackQueue(database.playbackQueue())
    val lyrics: LyricsCacheRepository = RoomLyricsCache(database.lyricsCache())
}
