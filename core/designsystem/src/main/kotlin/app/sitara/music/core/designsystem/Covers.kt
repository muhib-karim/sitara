package app.sitara.music.core.designsystem

import android.content.Context
import android.graphics.Bitmap
import app.sitara.music.core.model.Artwork
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.transform.Transformation

/** The app's image loader: Coil's memory and disk caches, with each cover asked for at the size of its slot. */
fun sitaraImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context).components { add(CoverMapper) }.build()

// Runs once the slot is measured, so a 48 dp row and the Now Playing sleeve fetch different sizes of one cover.
private val CoverMapper = Mapper<Artwork, String> { artwork, options ->
    coverUrlOf(artwork)?.let { sizedCoverUrl(it, maxOf(options.size.width.pxOrElse { 0 }, options.size.height.pxOrElse { 0 })) }
}

private object VideoThumbnailCrop : Transformation() {
    override val cacheKey = "app.sitara.videoThumbnailSquare"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val square = videoThumbnailSquare(input.width, input.height)
        return Bitmap.createBitmap(input, square.left, square.top, square.width, square.height)
    }
}

/** A request for [artwork]'s cover, square-cropped from a video thumbnail when needed; null when there is none to load. */
internal fun coverRequest(context: Context, artwork: Artwork, crossfade: Boolean): ImageRequest? {
    val url = coverUrlOf(artwork) ?: return null
    return ImageRequest.Builder(context)
        .data(artwork)
        .crossfade(crossfade)
        .apply { if (needsSquareCrop(url)) transformations(VideoThumbnailCrop) }
        .build()
}
