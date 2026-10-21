package app.sitara.music.source.youtube

import java.net.URI

private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
private val WATCH_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
private val SHORT_HOSTS = setOf("youtu.be", "www.youtu.be")

fun isVideoId(id: String): Boolean = VIDEO_ID.matches(id)

/**
 * The video id of the first YouTube, YouTube Music or youtu.be single-video link in [text], or null.
 * Share text around the link, a missing scheme and extra parameters (`si`, `list`, `t`) are allowed.
 */
fun videoIdFromLink(text: String): String? = text.trim().split(Regex("\\s+")).firstNotNullOfOrNull(::videoIdFromUrl)

/**
 * The playlist id (`list=`) of the first YouTube, YouTube Music or youtu.be link in [text] that names one: a playlist
 * page, or a shared song link played from a playlist. Share text around the link and a missing scheme are allowed.
 */
fun playlistIdFromLink(text: String): String? = text.trim().split(Regex("\\s+")).firstNotNullOfOrNull(::playlistIdFromUrl)

private fun playlistIdFromUrl(token: String): String? {
    val uri = webUri(token) ?: return null
    val host = uri.host.lowercase()
    val playlistPage = host in WATCH_HOSTS && uri.path in setOf("/playlist", "/watch") || host in SHORT_HOSTS
    return if (playlistPage) uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("list=") }?.substringAfter('=')?.takeIf(::isPlaylistId) else null
}

/** [token] as an http(s) URI with a host, reading a missing scheme as https. */
private fun webUri(token: String): URI? {
    val uri = runCatching { URI(if ("://" in token) token else "https://$token") }.getOrNull() ?: return null
    return uri.takeIf { it.scheme?.lowercase() in setOf("http", "https") && it.host != null }
}

private fun videoIdFromUrl(token: String): String? {
    val uri = webUri(token) ?: return null
    val host = uri.host.lowercase()
    val id = when {
        host in SHORT_HOSTS -> uri.path.orEmpty().removePrefix("/")
        host in WATCH_HOSTS && uri.path == "/watch" -> uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.substringAfter('=')
        else -> null
    }
    return id?.takeIf(::isVideoId)
}

private val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")
private val PLAYLIST_ID = Regex("[A-Za-z0-9_-]+")

/** YouTube Music artists are channels; ids are checked before they go into a request URL. */
fun isChannelId(id: String): Boolean = CHANNEL_ID.matches(id)

fun isPlaylistId(id: String): Boolean = PLAYLIST_ID.matches(id)

/** The `UC…` id in an extractor's `/channel/<id>` link, or null for handles, custom URLs and missing links. */
internal fun channelIdFromUrl(url: String?): String? = url?.substringAfter("/channel/", "")?.substringBefore('/')?.substringBefore('?')?.takeIf(::isChannelId)

/** The `list=` id of an extractor's playlist or album link. */
internal fun playlistIdFromUrl(url: String): String? =
    url.substringAfter('?', "").split('&').firstOrNull { it.startsWith("list=") }?.substringAfter('=')?.takeIf(::isPlaylistId)
