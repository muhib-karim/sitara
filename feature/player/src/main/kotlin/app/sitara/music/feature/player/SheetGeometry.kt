package app.sitara.music.feature.player

/**
 * Where the player sheet sits for a given expansion progress (0 = docked mini player, 1 = full screen).
 * Progress is clamped, so a spring that overshoots can never produce a negative corner radius
 * or push the sheet past the top of the screen.
 */
data class SheetGeometry(
    val y: Float,
    val height: Float,
    val side: Float,
    val cornerRadiusDp: Float,
) {
    companion object {
        const val COLLAPSED_RADIUS_DP = 22f

        fun at(progress: Float, collapsedTop: Float, fullHeight: Float, miniHeight: Float, collapsedSide: Float): SheetGeometry {
            val p = progress.coerceIn(0f, 1f)
            return SheetGeometry(
                y = lerp(collapsedTop, 0f, p),
                height = lerp(miniHeight, fullHeight, p),
                side = lerp(collapsedSide, 0f, p),
                cornerRadiusDp = lerp(COLLAPSED_RADIUS_DP, 0f, p),
            )
        }

        private fun lerp(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction
    }
}
