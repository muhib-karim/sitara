package app.sitara.music

/** A detail page on the browse back stack: a catalogue page by its id, or one of the listener's own collections. */
sealed interface DetailRoute {
    data class Artist(val id: String) : DetailRoute

    data class Album(val id: String) : DetailRoute

    data class Playlist(val id: String) : DetailRoute

    data object LikedSongs : DetailRoute

    /** A playlist the listener made, by its id in the on-device library. */
    data class UserPlaylist(val id: Long) : DetailRoute

    /** A mood or genre page of the catalogue: the [params] that ask for it, and its [title] for the header. */
    data class Mood(val params: String, val title: String) : DetailRoute
}

/** "kind:id", the form a route is saved in across process death. */
fun DetailRoute.encode(): String = when (this) {
    is DetailRoute.Artist -> "artist:$id"
    is DetailRoute.Album -> "album:$id"
    is DetailRoute.Playlist -> "playlist:$id"
    DetailRoute.LikedSongs -> "liked:songs"
    is DetailRoute.UserPlaylist -> "mine:$id"
    // The params never hold a "|", the title may.
    is DetailRoute.Mood -> "mood:$params|$title"
}

fun decodeDetailRoute(saved: String): DetailRoute? {
    val id = saved.substringAfter(':', "").ifEmpty { return null }
    return when (saved.substringBefore(':')) {
        "artist" -> DetailRoute.Artist(id)
        "album" -> DetailRoute.Album(id)
        "playlist" -> DetailRoute.Playlist(id)
        "liked" -> DetailRoute.LikedSongs
        "mine" -> id.toLongOrNull()?.let(DetailRoute::UserPlaylist)
        "mood" -> DetailRoute.Mood(id.substringBefore('|').ifEmpty { return null }, id.substringAfter('|', "").ifEmpty { return null })
        else -> null
    }
}
