package app.sitara.music.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// Placeholders are drawn in a screen's own layout while it loads, so the page keeps its shape. They pulse gently
// between full and 45 % opacity; with reduced motion they hold still.

/** One pulse for a group of placeholders. */
@Composable
fun rememberPlaceholderPulse(): State<Float> {
    if (LocalReducedMotion.current) return remember { mutableFloatStateOf(STILL_ALPHA) }
    return rememberInfiniteTransition(label = "placeholder").animateFloat(
        initialValue = STILL_ALPHA,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "placeholder pulse",
    )
}

/** A block standing in for content that is loading. */
@Composable
fun PlaceholderBlock(pulse: State<Float>, modifier: Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    // The alpha is read while drawing, so the pulse redraws without recomposing.
    Box(modifier.graphicsLayer { alpha = pulse.value }.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

private const val STILL_ALPHA = 1f
