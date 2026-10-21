package app.sitara.music

import app.sitara.music.feature.library.LocalKind

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

    /** The listener's stats, from the history kept on the phone. */
    data object Stats : DetailRoute

    /** Import playlist: a link or a file into the library. */
    data object Import : DetailRoute

    /** Settings → Scrobbling: Last.fm and ListenBrainz. */
    data object Scrobbling : DetailRoute

    /** Library → On this phone: songs saved on the phone and in picked folders. */
    data object OnThisPhone : DetailRoute

    /** An album, artist or folder of songs on the phone, by its key among its [kind]. */
    data class LocalGroup(val kind: LocalKind, val key: String) : DetailRoute

    /** Settings → Backup and restore. */
    data object Backup : DetailRoute
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
    DetailRoute.Stats -> "stats:listening"
    DetailRoute.Import -> "import:playlist"
    DetailRoute.Scrobbling -> "scrobbling:services"
    DetailRoute.OnThisPhone -> "local:phone"
    // The kind never holds a "|", the key may.
    is DetailRoute.LocalGroup -> "localgroup:${kind.name}|$key"
    DetailRoute.Backup -> "backup:file"
}

fun decodeDetailRoute(saved: String): DetailRoute? {
    val id = saved.substringAfter(':', "").ifEmpty { return null }
    return when (saved.substringBefore(':')) {
        "artist" -> DetailRoute.Artist(id)
        "album" -> DetailRoute.Album(id)
        "playlist" -> DetailRoute.Playlist(id)
        "liked" -> DetailRoute.LikedSongs
        "stats" -> DetailRoute.Stats
        "import" -> DetailRoute.Import
        "scrobbling" -> DetailRoute.Scrobbling
        "mine" -> id.toLongOrNull()?.let(DetailRoute::UserPlaylist)
        "mood" -> DetailRoute.Mood(id.substringBefore('|').ifEmpty { return null }, id.substringAfter('|', "").ifEmpty { return null })
        "local" -> DetailRoute.OnThisPhone
        "backup" -> DetailRoute.Backup
        "localgroup" -> LocalKind.entries.firstOrNull { it.name == id.substringBefore('|') && it != LocalKind.SONGS }
            ?.let { DetailRoute.LocalGroup(it, id.substringAfter('|', "").ifEmpty { return null }) }
        else -> null
    }
}
