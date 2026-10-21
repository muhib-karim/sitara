package app.sitara.music.core.designsystem

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sitara.music.core.model.Artwork
import coil3.compose.AsyncImage

/** The stage's layers over the blurred cover; [ArtworkPalette.stageScrim] works out the scrim from the same numbers. */
object ArtworkScrim {
    /** The cover's own colour washed over its blur, so a busy cover reads as one colour field. */
    const val Wash = 0.35f

    /** The accent glow over the scrim, at its centre. */
    const val Glow = 0.22f

    /** The lightest the scrim gets, over the darkest covers. */
    const val Floor = 0.30f

    /** The scrim at the bottom edge, behind the transport and the dock. */
    const val Bottom = 0.85f

    const val InactiveLyric = 0.68f
}

/**
 * The listening stage behind [artwork]: the cover blurred across the whole screen (on Android 12+ with RenderEffect;
 * below that, its tiny copy stretched), washed in its colour, under a scrim as light as the text allows and a glow in the
 * accent. The cover crossfades between tracks (cuts under reduced motion); the blur radius never animates.
 */
@Composable
fun ArtworkBackdrop(
    artwork: Artwork,
    modifier: Modifier = Modifier,
    look: CoverLook? = rememberCoverLook(artwork),
    theme: ArtworkTheme = rememberArtworkTheme(artwork, look = look),
) {
    val reduced = LocalReducedMotion.current
    Box(modifier.background(SitaraColors.Stage)) {
        if (!LocalArtworkWash.current) return@Box
        val wash = animateArtworkColor(theme.backdropTop, "backdrop wash")
        val glow = animateArtworkColor(theme.accent, "backdrop glow")
        val scrimTarget = remember(look, theme) { ArtworkPalette.stageScrim(look?.colors?.brightest ?: theme.backdropTop, theme, SitaraColors.StageSecondary.toArgb()) }
        val scrim = animateFloatAsState(scrimTarget, SitaraMotion.orReduced(reduced, SitaraMotion.fade(SitaraMotion.FADE_LONG_MS)), label = "backdrop scrim").value
        Box(Modifier.fillMaxSize().background(wash))
        BlurredCover(artwork, look, reduced)
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val shade = Brush.verticalGradient(0f to Color.Black.copy(alpha = scrim), 1f to Color.Black.copy(alpha = maxOf(scrim, ArtworkScrim.Bottom)))
                val light = Brush.radialGradient(
                    listOf(glow.copy(alpha = ArtworkScrim.Glow), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.3f),
                    radius = size.maxDimension * 0.6f,
                )
                onDrawBehind {
                    drawRect(wash.copy(alpha = ArtworkScrim.Wash))
                    drawRect(shade)
                    drawRect(light)
                }
            },
        )
    }
}

@Composable
private fun BlurredCover(artwork: Artwork, look: CoverLook?, reduced: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // No RenderEffect: bilinear filtering stretches the cover's 12 px copy into the same soft field.
        look?.let { Image(it.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low) }
        return
    }
    val context = LocalContext.current
    Crossfade(artwork, animationSpec = SitaraMotion.orReduced(reduced, SitaraMotion.fade(SitaraMotion.FADE_LONG_MS)), label = "artwork wash") { cover ->
        // The palette's small read of the cover: already in the cache, and a 72 dp blur leaves no detail to fetch more for.
        val request = remember(cover) { paletteRequest(context, cover) } ?: return@Crossfade
        AsyncImage(
            request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }.blur(72.dp, BlurredEdgeTreatment.Unbounded),
        )
    }
}
