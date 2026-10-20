package app.sitara.music.core.player

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

/**
 * Tells the device's equaliser which audio session to shape (the system's open and close audio effect control
 * broadcasts, sent through [announce]): open while the listener has chosen it and something plays, closed when playback
 * stops, they switch back to the built-in one, or the session changes.
 */
internal class SystemEqualiserSession(private val announce: (open: Boolean, sessionId: Int) -> Unit) {
    private var openSession = NO_SESSION

    fun update(wanted: Boolean, sessionId: Int) {
        val next = if (wanted) sessionId else NO_SESSION
        if (next == openSession) return
        if (openSession != NO_SESSION) announce(false, openSession)
        if (next != NO_SESSION) announce(true, next)
        openSession = next
    }

    fun close() = update(wanted = false, sessionId = NO_SESSION)

    companion object {
        // What ExoPlayer reports before it has a session; never a real one.
        const val NO_SESSION = 0
    }
}

/** Whether the device has an equaliser that can take Sitara's audio session (an app answering the effect control panel intent). */
fun Context.hasSystemEqualiser(): Boolean = packageManager.queryIntentActivities(Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL), 0).isNotEmpty()

/** Opens the device's equaliser for [sessionId]; call only when [hasSystemEqualiser]. */
fun systemEqualiserPanel(context: Context, sessionId: Int): Intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
