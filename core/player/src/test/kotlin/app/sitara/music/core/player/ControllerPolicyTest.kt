package app.sitara.music.core.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerPolicyTest {
    private val own = "app.sitara.music"

    private fun caller(
        pkg: String,
        trusted: Boolean = false,
        notification: Boolean = false,
        car: Boolean = false,
    ) = ControllerIdentity(packageName = pkg, isTrusted = trusted, isMediaNotification = notification, isCar = car)

    @Test
    fun `the app itself gets full access`() {
        assertEquals(ControllerAccess.FULL, ControllerPolicy.access(caller(own), own))
    }

    @Test
    fun `system surfaces get playback controls only`() {
        assertEquals(ControllerAccess.PLAYBACK_ONLY, ControllerPolicy.access(caller(own, notification = true), own))
        assertEquals(ControllerAccess.PLAYBACK_ONLY, ControllerPolicy.access(caller("com.google.android.projection.gearhead", car = true), own))
        assertEquals(ControllerAccess.PLAYBACK_ONLY, ControllerPolicy.access(caller("com.android.bluetooth", trusted = true), own))
    }

    @Test
    fun `unknown apps are rejected`() {
        assertEquals(ControllerAccess.REJECT, ControllerPolicy.access(caller("com.example.other"), own))
    }

    @Test
    fun `playback commands cannot replace the queue or change volume`() {
        val allowed = ControllerPolicy.PLAYBACK_COMMANDS
        assertTrue(Player.COMMAND_PLAY_PAUSE in allowed)
        assertTrue(Player.COMMAND_SEEK_TO_NEXT in allowed)
        assertTrue(Player.COMMAND_SEEK_TO_PREVIOUS in allowed)
        assertTrue(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM in allowed)
        assertFalse(Player.COMMAND_SET_MEDIA_ITEM in allowed)
        assertFalse(Player.COMMAND_CHANGE_MEDIA_ITEMS in allowed)
        assertFalse(Player.COMMAND_SET_VOLUME in allowed)
        assertFalse(Player.COMMAND_SET_SHUFFLE_MODE in allowed)
    }
}
