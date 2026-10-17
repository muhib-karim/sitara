package app.sitara.music.core.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Frosted glass for floating chrome. Real backdrop blur (Haze) on API 31+,
 * a denser tinted scrim below that, so text stays legible everywhere.
 */
@Composable
fun Modifier.glass(state: HazeState?, tint: Color, blurEnabled: Boolean = true): Modifier =
    if (state != null && blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.hazeEffect(state) {
            blurRadius = 28.dp
            tints = listOf(HazeTint(tint.copy(alpha = 0.55f)))
            noiseFactor = 0.08f
        }
    } else {
        this.background(tint.copy(alpha = 0.92f))
    }
