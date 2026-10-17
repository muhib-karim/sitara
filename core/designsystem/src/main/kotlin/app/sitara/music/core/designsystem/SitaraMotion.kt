package app.sitara.music.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.PI
import kotlin.math.pow

/**
 * Every spring and fade in the app lives here, so motion is tuned in one place.
 *
 * Springs are described the way designers think about them: a response time
 * (seconds for one undamped oscillation) and a damping ratio. 1.0 is critically
 * damped: fast to leave, no overshoot on arrival.
 */
object SitaraMotion {
    /** Stiffness for a spring that settles in roughly [responseSeconds]. */
    fun stiffnessFor(responseSeconds: Float): Float = ((2 * PI) / responseSeconds).pow(2).toFloat()

    /** Mini player to full player sheet. Critically damped, ~0.42 s. */
    val SheetResponse = 0.42f
    val sheetStiffness: Float = stiffnessFor(SheetResponse)

    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)

    fun <T> smooth(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = sheetStiffness)

    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 120f)

    /** A spring with a little life in it for the tab-bar selection pill. */
    fun <T> pill(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = 520f)

    const val FADE_SHORT_MS = 120
    const val FADE_MEDIUM_MS = 220
    const val FADE_LONG_MS = 360

    fun <T> fade(durationMs: Int = FADE_MEDIUM_MS): FiniteAnimationSpec<T> = tween(durationMs)

    /** Reduced motion swaps springs for instant changes; fades stay but get shorter. */
    fun <T> orReduced(reduced: Boolean, spec: AnimationSpec<T>): AnimationSpec<T> = if (reduced) snap() else spec

    /** Drag velocity (px/s) above which a release flings the sheet regardless of position. */
    const val FLING_VELOCITY_DP = 900f
}

/** True when the user asked for reduced motion (setting or system "remove animations"). */
val LocalReducedMotion = staticCompositionLocalOf { false }
