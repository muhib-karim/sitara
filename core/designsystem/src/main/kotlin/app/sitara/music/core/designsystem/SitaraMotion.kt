package app.sitara.music.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/** A firm settle, quiet fades, and a single reduced-motion policy. */
object SitaraMotion {
    fun <T> smooth(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 380f)

    const val FADE_MEDIUM_MS = 250
    const val FADE_LONG_MS = 400
    const val LYRIC_FOCUS_MS = 220

    fun <T> fade(durationMs: Int = FADE_MEDIUM_MS): FiniteAnimationSpec<T> = tween(durationMs, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))

    fun <T> orReduced(reduced: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (reduced) snap() else spec
}

val LocalReducedMotion = staticCompositionLocalOf { false }
