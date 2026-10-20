package app.sitara.music.core.designsystem

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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

/** The cover's palette swatches, most common first; null when there is no cover or it cannot load. */
private suspend fun coverSwatches(context: Context, artwork: Artwork): List<Int>? {
    val request = coverRequest(context, artwork, crossfade = false)?.newBuilder()?.size(PALETTE_PX)?.allowHardware(false)?.build() ?: return null
    val loader = SingletonImageLoader.get(context)
    val result = loader.execute(request) as? SuccessResult ?: return null
    // Where the loader decodes: off the main thread in the app, inline in screenshot tests.
    return withContext(loader.defaults.decoderCoroutineContext) {
        Palette.from(result.image.toBitmap()).generate().swatches.sortedByDescending { it.population }.map { it.rgb }
    }
}

// Themes already worked out, so opening the player or revisiting a page starts in the right colours instead of fading in.
private val stageThemes = LruCache<Artwork, ArtworkTheme>(32)

/**
 * The listening stage's colours for [artwork]: from the cover's palette once it has loaded, from the artwork's own
 * colours before that or when it cannot load. The previous track's colours hold until the next are known, so the
 * stage changes colour once per track.
 */
@Composable
fun rememberArtworkTheme(artwork: Artwork): ArtworkTheme {
    val context = LocalContext.current
    val initial = remember { stageThemes[artwork] ?: ArtworkPalette.stageTheme(artwork, null) }
    return produceState(initial, artwork) {
        value = stageThemes[artwork] ?: coverSwatches(context, artwork).let { swatches ->
            // Only a loaded cover is final; after a failure (offline) the next visit tries again.
            ArtworkPalette.stageTheme(artwork, swatches).also { if (swatches != null) stageThemes.put(artwork, it) }
        }
    }.value
}

/** Moves to [argb] with a long fade between tracks, or at once under reduced motion. */
@Composable
fun animateArtworkColor(argb: Int, label: String): Color {
    val spec = SitaraMotion.orReduced(LocalReducedMotion.current, SitaraMotion.fade<Color>(SitaraMotion.FADE_LONG_MS))
    return animateColorAsState(Color(argb), spec, label = label).value
}
