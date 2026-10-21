package app.sitara.music.core.designsystem

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import app.sitara.music.core.model.Artwork
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.map.Mapper
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.toBitmap
import coil3.transform.Transformation
import kotlinx.coroutines.withContext

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

private const val PALETTE_PX = 128

// Where blur is unavailable the backdrop stretches a copy this small, and bilinear scaling does the blurring.
private const val THUMBNAIL_PX = 12

// About a 72 dp blur's reach across a phone: no blurred pixel comes out brighter than the brightest cell.
private const val BRIGHTNESS_GRID = 8

/** What one read of a cover gives the stage: its colours, and a tiny copy to stretch where blur is unavailable. */
class CoverLook(val colors: CoverColors, val thumbnail: ImageBitmap)

internal fun coverColorsOf(bitmap: Bitmap): CoverColors {
    val grid = Bitmap.createScaledBitmap(bitmap, BRIGHTNESS_GRID, BRIGHTNESS_GRID, true)
    val cells = IntArray(BRIGHTNESS_GRID * BRIGHTNESS_GRID).also { grid.getPixels(it, 0, BRIGHTNESS_GRID, 0, 0, BRIGHTNESS_GRID, BRIGHTNESS_GRID) }
    val brightest = cells.maxBy { ArtworkPalette.relativeLuminance(it) } or OPAQUE
    val palette = Palette.from(bitmap).generate()
    // The palette filters out near-white and near-black, so an all-white or all-black cover has no swatches at all.
    val dominant = palette.dominantSwatch ?: return CoverColors(listOf(brightest), brightest)
    val accent = palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.lightVibrantSwatch ?: palette.mutedSwatch ?: dominant
    return CoverColors(listOf(dominant.rgb, accent.rgb), brightest)
}

private const val OPAQUE = 0xFF000000.toInt()

/** The small, software-decoded read of [artwork]'s cover the palette is taken from; null when it has none. */
internal fun paletteRequest(context: Context, artwork: Artwork): ImageRequest? =
    coverRequest(context, artwork, crossfade = false)?.newBuilder()?.size(PALETTE_PX)?.allowHardware(false)?.build()

private val coverLooks = LruCache<Artwork, CoverLook>(32)

/** [artwork]'s look, or null when there is no cover or it cannot load. */
private suspend fun loadCoverLook(context: Context, artwork: Artwork): CoverLook? {
    coverLooks[artwork]?.let { return it }
    val request = paletteRequest(context, artwork) ?: return null
    val loader = SingletonImageLoader.get(context)
    val result = loader.execute(request) as? SuccessResult ?: return null
    // Where the loader decodes: off the main thread in the app, inline in screenshot tests.
    return withContext(loader.defaults.decoderCoroutineContext) {
        val bitmap = result.image.toBitmap()
        CoverLook(coverColorsOf(bitmap), Bitmap.createScaledBitmap(bitmap, THUMBNAIL_PX, THUMBNAIL_PX, true).asImageBitmap())
    }.also { coverLooks.put(artwork, it) }
}

/**
 * [artwork]'s look once its cover has loaded, null when it cannot (offline, no cover). Looks already read are there on the
 * first frame; otherwise the previous track's look holds until the next is known, so the stage changes colour once per track.
 */
@Composable
fun rememberCoverLook(artwork: Artwork): CoverLook? {
    val context = LocalContext.current
    return produceState(coverLooks[artwork], artwork) { value = loadCoverLook(context, artwork) }.value
}

/** [artwork]'s colours for a [dark] or light surface, from its cover once loaded, from the artwork's own colours before that. */
@Composable
fun rememberArtworkTheme(artwork: Artwork, dark: Boolean = true, look: CoverLook? = rememberCoverLook(artwork)): ArtworkTheme =
    remember(artwork, look, dark) { ArtworkPalette.coverTheme(artwork, look?.colors, dark) }

/** Moves to [argb] with a long fade between tracks, or at once under reduced motion. */
@Composable
fun animateArtworkColor(argb: Int, label: String): Color {
    val spec = SitaraMotion.orReduced(LocalReducedMotion.current, SitaraMotion.fade<Color>(SitaraMotion.FADE_LONG_MS))
    return animateColorAsState(Color(argb), spec, label = label).value
}
