package app.sitara.music.core.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * The player as the session shows it to cars, the lock screen and Bluetooth: when a song fails, its error says so in
 * words ([songFailed], asked for at each failure so it follows the listener's language) instead of the player's own
 * "Source error". Every command stays, so play still tries again and skip skips.
 */
@OptIn(UnstableApi::class)
internal class WordedErrors(player: Player, private val songFailed: () -> String = { SONG_FAILED }) : ForwardingSimpleBasePlayer(player) {
    // One worded copy per failure, so listeners hear of each failure once.
    private var worded: Pair<PlaybackException, PlaybackException>? = null

    override fun getState(): State {
        val state = super.getState()
        val error = state.playerError ?: return state
        val shown = worded?.takeIf { it.first === error }?.second ?: PlaybackException(songFailed(), error, error.errorCode).also { worded = error to it }
        return state.buildUpon().setPlayerError(shown).build()
    }

    private companion object {
        // English, for a player built without resources; the service words it from player_service_song_failed.
        const val SONG_FAILED = "Couldn’t play this song. Try again, skip it, or play a download."
    }
}
