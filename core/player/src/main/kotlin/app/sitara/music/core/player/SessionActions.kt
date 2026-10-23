package app.sitara.music.core.player

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import app.sitara.music.core.model.RepeatMode

/** The media notification's and lock screen's buttons beside play and skip: like, shuffle and repeat, as custom session commands. */
@OptIn(UnstableApi::class)
internal object SessionActions {
    const val LIKE = "app.sitara.music.action.LIKE"
    const val SHUFFLE = "app.sitara.music.action.SHUFFLE"
    const val REPEAT = "app.sitara.music.action.REPEAT"

    /** What system surfaces may send besides transport controls. */
    val commands: SessionCommands = SessionCommands.Builder().apply { listOf(LIKE, SHUFFLE, REPEAT).forEach { add(SessionCommand(it, Bundle.EMPTY)) } }.build()

    /** The buttons for what plays now, named in [context]'s language; each icon shows the state and each name says what a press does. */
    fun buttons(context: Context, liked: Boolean, shuffled: Boolean, repeat: RepeatMode): List<CommandButton> = listOf(
        button(LIKE, if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED, context.getString(if (liked) R.string.player_service_unlike else R.string.player_service_like)),
        button(SHUFFLE, if (shuffled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF, context.getString(if (shuffled) R.string.player_service_shuffle_off else R.string.player_service_shuffle)),
        when (repeat) {
            RepeatMode.OFF -> button(REPEAT, CommandButton.ICON_REPEAT_OFF, context.getString(R.string.player_service_repeat_all))
            RepeatMode.ALL -> button(REPEAT, CommandButton.ICON_REPEAT_ALL, context.getString(R.string.player_service_repeat_one))
            RepeatMode.ONE -> button(REPEAT, CommandButton.ICON_REPEAT_ONE, context.getString(R.string.player_service_repeat_off))
        },
    )

    private fun button(action: String, icon: Int, name: String): CommandButton =
        CommandButton.Builder(icon).setDisplayName(name).setSessionCommand(SessionCommand(action, Bundle.EMPTY)).build()
}
