package app.sitara.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.sitara.music.core.model.Artwork
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Serves the sample catalogue's `sample:cover/` images without a network: flat colour fields and an orbit ring
 * drawn from the artwork's colours, so a loaded cover is told apart from the generated gradient placeholder.
 * Any other URL fails, as an offline or removed cover does. Work runs inline, which keeps screenshots deterministic.
 */
internal fun sampleImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(
            Interceptor { chain ->
                val artwork = chain.request.data as Artwork
                if (artwork.imageUrl.orEmpty().startsWith("sample:cover/")) {
                    // A memory-cache hit is never crossfaded, and a fade timed by the wall clock would make screenshots flaky.
                    SuccessResult(drawSampleCover(artwork.colors).asImage(), chain.request, DataSource.MEMORY_CACHE)
                } else {
                    ErrorResult(null, chain.request, IOException("No sample cover for ${artwork.imageUrl}"))
                }
            },
        )
    }
    .coroutineContext(EmptyCoroutineContext)
    .build()

private fun drawSampleCover(colors: List<Int>): Bitmap {
    val side = 512f
    val bitmap = Bitmap.createBitmap(side.toInt(), side.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(colors.first())
    paint.color = colors.getOrElse(1) { colors.first() }
    canvas.drawCircle(side * 0.62f, side * 0.40f, side * 0.28f, paint)
    paint.color = colors.getOrElse(2) { 0xFF0B0B14.toInt() }
    canvas.drawRect(0f, side * 0.74f, side, side, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = side * 0.018f
    paint.color = Color.argb(150, 255, 255, 255)
    canvas.drawCircle(side * 0.62f, side * 0.40f, side * 0.40f, paint)
    return bitmap
}
