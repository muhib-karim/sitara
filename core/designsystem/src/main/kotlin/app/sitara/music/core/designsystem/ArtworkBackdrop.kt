package app.sitara.music.core.designsystem

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.sitara.music.core.model.Artwork

object ArtworkScrim {
    const val Top = 0.72f
    const val Middle = 0.78f
    const val Bottom = 0.90f
    const val InactiveLyric = 0.68f
}

/** Static artwork layers crossfade only when the track changes; blur radius never animates. */
@Composable
fun ArtworkBackdrop(artwork: Artwork, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val wash = LocalArtworkWash.current
    Box(modifier.background(SitaraColors.Stage)) {
        if (wash) {
            Crossfade(
                targetState = artwork,
                animationSpec = SitaraMotion.orReduced(reduced, SitaraMotion.fade(SitaraMotion.FADE_LONG_MS)),
                label = "artwork wash",
            ) { cover ->
                val palette = remember(cover) { ArtworkPalette.themeFrom(cover.colors, true) }
                val blurred = !reduced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                Box(Modifier.fillMaxSize().background(Color(palette.backdropTop))) {
                    if (blurred) {
                        ArtworkImage(
                            cover,
                            Modifier.fillMaxWidth().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }
                                .blur(72.dp, BlurredEdgeTreatment.Unbounded),
                            cornerRadius = 0.dp,
                        )
                    }
                    Box(
                        Modifier.fillMaxSize().drawWithCache {
                            val scrim = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = if (blurred) ArtworkScrim.Top else 0.40f),
                                0.45f to Color.Black.copy(alpha = if (blurred) ArtworkScrim.Middle else 0.55f),
                                1f to Color.Black.copy(alpha = ArtworkScrim.Bottom),
                            )
                            onDrawBehind { drawRect(scrim) }
                        },
                    )
                }
            }
        }
    }
}
