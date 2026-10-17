package app.sitara.music.core.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

val LocalGlassHaze = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun Modifier.glass(state: HazeState?, tint: Color, blurEnabled: Boolean = true): Modifier =
    if (state != null && blurEnabled && !LocalReducedMotion.current && LocalArtworkWash.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hazeEffect(state) {
            blurRadius = 24.dp
            tints = listOf(HazeTint(tint.copy(alpha = 0.88f)))
            noiseFactor = 0f
        }
    } else {
        background(tint.copy(alpha = 0.92f))
    }

/** One clipped glass body owns both the navigation and playback dock. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    haze: HazeState? = LocalGlassHaze.current,
    shape: Shape = RoundedCornerShape(SitaraRadius.Dock),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable BoxScope.() -> Unit,
) {
    val edge = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Box(
        modifier.clip(shape).glass(haze, tint).border(0.5.dp, edge, shape).drawWithCache {
            onDrawBehind {
                drawLine(Color.White.copy(alpha = 0.08f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
            }
        },
        content = content,
    )
}

@Composable
fun GlassIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Box(modifier.size(SitaraSpacing.TouchTarget).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.size(36.dp), shape = CircleShape) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp).align(Alignment.Center))
        }
    }
}

@Composable
fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val fill = if (primary) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (primary) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.defaultMinSize(minHeight = SitaraSpacing.TouchTarget).clip(CircleShape).background(fill)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, style = MaterialTheme.typography.labelLarge)
    }
}

/** A narrow blur band lets scrolling content dissolve under fixed chrome. */
@Composable
fun ScrollEdge(haze: HazeState, modifier: Modifier = Modifier, top: Boolean = true) {
    val fill = MaterialTheme.colorScheme.background
    Box(
        modifier.glass(haze, fill).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val mask = Brush.verticalGradient(if (top) listOf(Color.Black, Color.Transparent) else listOf(Color.Transparent, Color.Black))
                onDrawWithContent {
                    drawContent()
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            },
    )
}
