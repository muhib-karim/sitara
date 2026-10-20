package app.sitara.music

/** A detail page on the browse back stack, addressed by its catalogue id. */
sealed interface DetailRoute {
    val id: String

    data class Artist(override val id: String) : DetailRoute

    data class Album(override val id: String) : DetailRoute

    data class Playlist(override val id: String) : DetailRoute
}

/** "kind:id", the form a route is saved in across process death. */
fun DetailRoute.encode(): String = when (this) {
    is DetailRoute.Artist -> "artist"
    is DetailRoute.Album -> "album"
    is DetailRoute.Playlist -> "playlist"
} + ":" + id

fun decodeDetailRoute(saved: String): DetailRoute? {
    val id = saved.substringAfter(':', "").ifEmpty { return null }
    return when (saved.substringBefore(':')) {
        "artist" -> DetailRoute.Artist(id)
        "album" -> DetailRoute.Album(id)
        "playlist" -> DetailRoute.Playlist(id)
        else -> null
    }
}
