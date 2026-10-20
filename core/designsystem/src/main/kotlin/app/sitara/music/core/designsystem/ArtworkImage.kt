package app.sitara.music.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sitara.music.core.model.Artwork
import coil3.compose.AsyncImage
import kotlin.math.abs

/**
 * Draws [artwork]'s real cover when it has one. Underneath, and alone while the cover loads or when it
 * cannot (offline, removed, no image at all), a cover generated from the artwork's colours.
 */
@Composable
fun ArtworkImage(
    artwork: Artwork,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    val request = remember(artwork, reduced) { coverRequest(context, artwork, crossfade = !reduced) }
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(cornerRadius))) {
        GeneratedCover(artwork, Modifier.matchParentSize())
        if (request != null) {
            AsyncImage(request, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

/**
 * A diagonal gradient, a soft glow and a few concentric "groove" rings whose position is derived
 * from the colours, so each cover is distinct but deterministic (stable screenshot tests).
 */
@Composable
private fun GeneratedCover(artwork: Artwork, modifier: Modifier) {
    val colors = remember(artwork) { artwork.colors.map { Color(it) }.let { if (it.size == 1) it + it else it } }
    val seed = remember(artwork) { abs(artwork.colors.fold(17) { acc, c -> acc * 31 + c }) }
    Canvas(modifier) {
        drawRect(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height)))
        val cx = size.width * (0.25f + (seed % 50) / 100f)
        val cy = size.height * (0.25f + (seed / 50 % 50) / 100f)
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = Offset(cx, cy), radius = size.minDimension * 0.6f),
            radius = size.minDimension * 0.6f,
            center = Offset(cx, cy),
        )
        val rings = 3 + seed % 3
        for (i in 1..rings) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.minDimension * (0.12f + i * 0.09f),
                center = Offset(size.width - cx * 0.6f, size.height - cy * 0.5f),
                style = Stroke(width = size.minDimension * 0.008f),
            )
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f))))
    }
}
