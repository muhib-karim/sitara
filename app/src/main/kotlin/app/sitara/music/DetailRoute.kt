package app.sitara.music

/** A detail page on the browse back stack: a catalogue page by its id, or one of the listener's own collections. */
sealed interface DetailRoute {
    data class Artist(val id: String) : DetailRoute

    data class Album(val id: String) : DetailRoute

    data class Playlist(val id: String) : DetailRoute

    data object LikedSongs : DetailRoute

    /** A playlist the listener made, by its id in the on-device library. */
    data class UserPlaylist(val id: Long) : DetailRoute
}

/** "kind:id", the form a route is saved in across process death. */
fun DetailRoute.encode(): String = when (this) {
    is DetailRoute.Artist -> "artist:$id"
    is DetailRoute.Album -> "album:$id"
    is DetailRoute.Playlist -> "playlist:$id"
    DetailRoute.LikedSongs -> "liked:songs"
    is DetailRoute.UserPlaylist -> "mine:$id"
}

fun decodeDetailRoute(saved: String): DetailRoute? {
    val id = saved.substringAfter(':', "").ifEmpty { return null }
    return when (saved.substringBefore(':')) {
        "artist" -> DetailRoute.Artist(id)
        "album" -> DetailRoute.Album(id)
        "playlist" -> DetailRoute.Playlist(id)
        "liked" -> DetailRoute.LikedSongs
        "mine" -> id.toLongOrNull()?.let(DetailRoute::UserPlaylist)
        else -> null
    }
}
