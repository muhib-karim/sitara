package app.sitara.music.feature.player

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView

@Suppress("DEPRECATION")
internal fun systemBarFlags(flags: Int, darkIcons: Boolean): Int {
    val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    return if (darkIcons) flags or mask else flags and mask.inv()
}

/** The listening stage is dark even when the surrounding app uses a light theme. */
@Suppress("DEPRECATION")
@Composable
internal fun PlayerSystemBars(expanded: Boolean) {
    val view = LocalView.current
    val darkIcons = !expanded && MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(view, darkIcons) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val decor = activity?.window?.decorView
        val original = decor?.systemUiVisibility ?: 0
        if (!view.isInEditMode && decor != null) decor.systemUiVisibility = systemBarFlags(original, darkIcons)
        onDispose {
            if (!view.isInEditMode && decor != null) {
                val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                decor.systemUiVisibility = (decor.systemUiVisibility and mask.inv()) or (original and mask)
            }
        }
    }
}
