package app.sitara.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import app.sitara.music.core.model.Artwork
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Serves the sample catalogue's `sample:cover/` images without a network, drawn from the artwork's colours (debug and
 * tests only, never in a release build). A loaded cover is a landscape, told apart from the generated gradient placeholder.
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

/**
 * A procedural landscape in the artwork's colours: a sky graded from the first colour into the second, a sun with a halo,
 * two ranges of hills and an orbit ring. Real covers carry gradients, highlights and a dark edge, and so does this, so the
 * blurred backdrop, the palette and the glass have something real-looking to work on with no third-party image.
 */
private fun drawSampleCover(colors: List<Int>): Bitmap {
    val side = 512f
    val bitmap = Bitmap.createBitmap(side.toInt(), side.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val sky = colors.first()
    val glow = colors.getOrElse(1) { sky }
    val ground = colors.getOrElse(2) { 0xFF0B0B14.toInt() }
    paint.shader = LinearGradient(0f, 0f, 0f, side * 0.72f, sky, glow, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, side, side, paint)
    paint.shader = RadialGradient(side * 0.62f, side * 0.44f, side * 0.42f, Color.argb(170, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
    canvas.drawCircle(side * 0.62f, side * 0.44f, side * 0.42f, paint)
    paint.shader = null
    paint.color = Color.argb(235, 255, 244, 214)
    canvas.drawCircle(side * 0.62f, side * 0.44f, side * 0.15f, paint)
    paint.color = blend(ground, glow, 0.35f)
    canvas.drawPath(hills(side, crest = 0.62f, phase = 0.3f), paint)
    paint.color = ground
    canvas.drawPath(hills(side, crest = 0.74f, phase = 1.7f), paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = side * 0.012f
    paint.color = Color.argb(120, 255, 255, 255)
    canvas.drawCircle(side * 0.62f, side * 0.44f, side * 0.30f, paint)
    return bitmap
}

/** A range of rounded hills whose crest line sits at [crest] of the height. */
private fun hills(side: Float, crest: Float, phase: Float) = Path().apply {
    moveTo(0f, side)
    var x = 0f
    while (x <= side) {
        lineTo(x, side * (crest + 0.05f * sin(x / side * 2f * PI.toFloat() * 1.5f + phase)))
        x += side / 64f
    }
    lineTo(side, side)
    close()
}

private fun blend(from: Int, to: Int, amount: Float) = Color.rgb(
    (Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
    (Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
    (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt(),
)
