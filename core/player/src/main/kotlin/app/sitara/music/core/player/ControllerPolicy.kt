package app.sitara.music.core.player

import androidx.media3.common.Player

/** What the session knows about a connecting controller, free of Media3 types so it can be unit tested. */
data class ControllerIdentity(
    val packageName: String,
    val uid: Int,
    val isTrusted: Boolean,
    val isMediaNotification: Boolean,
    val isCar: Boolean,
)

enum class ControllerAccess { FULL, PLAYBACK_ONLY, REJECT }

/**
 * Who may control playback. The service is exported so system surfaces can bind,
 * which also lets any installed app try; Media3 identifies callers but does not
 * authorise them, so this policy does.
 */
object ControllerPolicy {
    /** Mirrors android.os.Process.FIRST_APPLICATION_UID; uids below it belong to the platform. */
    private const val FIRST_APPLICATION_UID = 10_000

    /** Transport controls only: nothing that replaces the queue, changes volume or reorders it. */
    val PLAYBACK_COMMANDS: Set<Int> = setOf(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_PREPARE,
        Player.COMMAND_STOP,
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_BACK,
        Player.COMMAND_SEEK_FORWARD,
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_GET_METADATA,
        Player.COMMAND_GET_AUDIO_ATTRIBUTES,
        Player.COMMAND_RELEASE,
    )

    /**
     * The app itself is recognised by the binder-verified [ControllerIdentity.uid], never by a
     * package-name string. System surfaces get transport controls; everyone else is turned away.
     */
    fun access(caller: ControllerIdentity, ownUid: Int): ControllerAccess = when {
        caller.isMediaNotification || caller.isCar -> ControllerAccess.PLAYBACK_ONLY
        caller.uid == ownUid -> ControllerAccess.FULL
        // Headset buttons and Bluetooth arrive from system uids that may not report as trusted.
        caller.isTrusted || caller.uid in 0 until FIRST_APPLICATION_UID -> ControllerAccess.PLAYBACK_ONLY
        else -> ControllerAccess.REJECT
    }
}
