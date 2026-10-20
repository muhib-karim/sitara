package app.sitara.music.source.youtube

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private val THUMBNAIL_SIZE = Regex("=w\\d+-h\\d+")
private val AVATAR_SIZE = Regex("=s\\d+(?=-|$)")

// Gradient pairs for the generated cover shown until a thumbnail can be drawn; picked by video id so a song keeps its colours.
private val COVER_COLORS = listOf(
    listOf(0xFFF2A93B, 0xFF7A3E12),
    listOf(0xFF4F6DF5, 0xFF1B1F4B),
    listOf(0xFFE5677F, 0xFF4A1530),
    listOf(0xFF2BB39A, 0xFF0E3B3A),
    listOf(0xFF9A7BFF, 0xFF2B1D5C),
    listOf(0xFFF07B4F, 0xFF3D1A12),
).map { pair -> pair.map { it.toInt() } }

/**
 * A YouTube Music song search item as a [Track] whose id is the video id, or null when the item has none.
 * The extractor's song items carry no album, so [Track.album] stays empty.
 */
internal fun StreamInfoItem.toTrack(): Track? {
    val videoId = videoIdFromLink(url) ?: return null
    return youTubeTrack(videoId, name, uploaderName, uploaderUrl, duration, thumbnails)
}

/** The video behind a pasted or shared link as a [Track]. */
internal fun StreamInfo.toTrack(): Track = youTubeTrack(id, name, uploaderName, uploaderUrl, duration, thumbnails)

private fun youTubeTrack(videoId: String, title: String, uploader: String?, uploaderUrl: String?, seconds: Long, thumbnails: List<Image>) = Track(
    id = videoId,
    title = title,
    artist = artistName(uploader),
    album = "",
    durationMs = seconds.coerceAtLeast(0) * 1_000,
    artwork = artwork(videoId, thumbnails),
    artistId = channelIdFromUrl(uploaderUrl),
)

// Auto-generated artist channels are named "<artist> - Topic".
internal fun artistName(uploader: String?): String = uploader.orEmpty().removeSuffix(" - Topic")

/** Generated colours picked by [id], so an item keeps them, plus the largest image upgraded to a 544 px cover. */
internal fun artwork(id: String, images: List<Image>) = Artwork(COVER_COLORS[Math.floorMod(id.hashCode(), COVER_COLORS.size)], images.largest()?.coverSize())

internal fun List<Image>.largest(): String? = maxByOrNull { it.height.toLong() * it.width }?.url

private fun String.coverSize() = replace(THUMBNAIL_SIZE, "=w544-h544").replace(AVATAR_SIZE, "=s544")
