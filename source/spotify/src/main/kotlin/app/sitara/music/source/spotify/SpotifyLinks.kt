package app.sitara.music.source.spotify

import app.sitara.music.core.model.SpotifyLink
import java.net.URI

/**
 * The Spotify playlist or album [text] links to: `open.spotify.com/playlist/<id>` (also with a language path such as
 * `/intl-de/` and with share parameters), `spotify:playlist:<id>`, and the same for albums. Null for anything else,
 * including a link to one song (see [isSpotifySongLink]).
 */
fun spotifyLinkFrom(text: String): SpotifyLink? = text.trim().split(Regex("\\s+")).firstNotNullOfOrNull(::fromToken)

/** Whether [text] holds a Spotify link to one song (which an import cannot take as a list). */
fun isSpotifySongLink(text: String): Boolean = text.trim().split(Regex("\\s+")).any { token ->
    token.startsWith("spotify:track:") || pathOf(token)?.let { kindAndId(it)?.first == "track" } == true
}

private fun fromToken(token: String): SpotifyLink? {
    if (token.startsWith("spotify:")) {
        val parts = token.split(':')
        return if (parts.size == 3) link(parts[1], parts[2]) else null
    }
    val (kind, id) = pathOf(token)?.let(::kindAndId) ?: return null
    return link(kind, id)
}

private fun pathOf(token: String): List<String>? {
    val uri = runCatching { URI(if (token.startsWith("http")) token else "https://$token") }.getOrNull() ?: return null
    if (uri.host != "open.spotify.com" && uri.host != "play.spotify.com") return null
    return uri.path.orEmpty().split('/').filter(String::isNotEmpty)
}

// "/intl-xx/" and "/embed/" come before the kind.
private fun kindAndId(path: List<String>): Pair<String, String>? {
    val rest = path.dropWhile { it.startsWith("intl-") || it == "embed" }
    return if (rest.size >= 2) rest[0] to rest[1] else null
}

private fun link(kind: String, id: String): SpotifyLink? {
    if (!ID.matches(id)) return null
    return when (kind) {
        "playlist" -> SpotifyLink(SpotifyLink.Kind.PLAYLIST, id)
        "album" -> SpotifyLink(SpotifyLink.Kind.ALBUM, id)
        else -> null
    }
}

private val ID = Regex("[A-Za-z0-9]{22}")
