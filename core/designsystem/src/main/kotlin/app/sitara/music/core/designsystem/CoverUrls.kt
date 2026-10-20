package app.sitara.music.core.designsystem

import androidx.compose.ui.unit.IntRect
import app.sitara.music.core.model.Artwork

// Few sizes, so a cover drawn in a row and in a tile shares cache entries with its neighbours.
private val COVER_BUCKETS = intArrayOf(120, 240, 544, 1080)
private const val DEFAULT_COVER_PX = 544
private val RESIZABLE_HOST = Regex("^https://[^/]+\\.(googleusercontent|ggpht)\\.com/")
private val VIDEO_THUMBNAIL = Regex("^https://i\\d*\\.ytimg\\.com/vi(_webp)?/")
private val THUMBNAIL_SIZE = Regex("=w\\d+-h\\d+")
private val AVATAR_SIZE = Regex("=s\\d+(?=-|$)")

/** The cover to load for [artwork], or null when it has none and the generated gradient stands in. */
fun coverUrlOf(artwork: Artwork): String? = artwork.imageUrl?.takeIf { it.isNotBlank() }

/** Asks YouTube's image servers for the smallest bucket covering [px] (0 when unknown); other URLs are fetched as they are. */
fun sizedCoverUrl(url: String, px: Int): String {
    if (!RESIZABLE_HOST.containsMatchIn(url)) return url
    val side = if (px <= 0) DEFAULT_COVER_PX else COVER_BUCKETS.firstOrNull { it >= px } ?: COVER_BUCKETS.last()
    return url.replace(THUMBNAIL_SIZE, "=w$side-h$side").replace(AVATAR_SIZE, "=s$side")
}

/** YouTube video thumbnails are frames of a 16:9 video, not square cover art. */
fun needsSquareCrop(url: String): Boolean = VIDEO_THUMBNAIL.containsMatchIn(url)

/**
 * The centred square of a video thumbnail's picture. The picture is 16:9; the 4:3 sizes (hqdefault, sddefault) carry
 * it between letterbox bars, so the side is the picture's height, never the bars'.
 */
fun videoThumbnailSquare(width: Int, height: Int): IntRect {
    val side = minOf(height, width * 9 / 16).coerceAtLeast(1)
    val left = (width - side) / 2
    val top = (height - side) / 2
    return IntRect(left, top, left + side, top + side)
}
