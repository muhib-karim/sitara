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

private fun videoIdFromUrl(token: String): String? {
    val uri = runCatching { URI(if ("://" in token) token else "https://$token") }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    val host = uri.host?.lowercase() ?: return null
    val id = when {
        host in SHORT_HOSTS -> uri.path.orEmpty().removePrefix("/")
        host in WATCH_HOSTS && uri.path == "/watch" -> uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.substringAfter('=')
        else -> null
    }
    return id?.takeIf(::isVideoId)
}
