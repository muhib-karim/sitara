package app.sitara.music.core.model

/** Something that can be shared as a link and a QR code: what it is called and where it opens. */
data class ShareTarget(val title: String, val subtitle: String, val link: String, val artwork: Artwork)

private const val MUSIC = "https://music.youtube.com"

/** A song's YouTube Music link; null for a song on the phone, which has none. */
fun Track.shareTarget(): ShareTarget? = if (isLocal) null else ShareTarget(title, artist, "$MUSIC/watch?v=$id", artwork)

/** An album's link: YouTube Music albums are playlists. Null for a group of songs on the phone. */
fun Album.shareTarget(): ShareTarget? = if (tracks.isNotEmpty() && tracks.all { it.isLocal }) null else playlistTarget(id, title, artist, artwork)

fun Playlist.shareTarget(): ShareTarget = playlistTarget(id, title, curator, artwork)

fun Artist.shareTarget(): ShareTarget = ShareTarget(name, "Artist", "$MUSIC/channel/$id", artwork)

// YouTube Music's browse ids put "VL" before a playlist id.
private fun playlistTarget(id: String, title: String, subtitle: String, artwork: Artwork) =
    ShareTarget(title, subtitle, "$MUSIC/playlist?list=${id.removePrefix("VL")}", artwork)
