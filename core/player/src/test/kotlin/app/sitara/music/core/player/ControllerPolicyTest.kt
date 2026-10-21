package app.sitara.music.core.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerPolicyTest {
    private val ownUid = 10_123

    private fun caller(
        pkg: String,
        uid: Int = 10_999,
        trusted: Boolean = false,
        notification: Boolean = false,
        car: Boolean = false,
    ) = ControllerIdentity(packageName = pkg, uid = uid, isTrusted = trusted, isMediaNotification = notification, isCar = car)

    @Test
    fun `the app itself gets full access`() {
        assertEquals(ControllerAccess.FULL, ControllerPolicy.access(caller("app.sitara.music", uid = ownUid), ownUid))
    }

    @Test
    fun `system surfaces get playback controls only`() {
        assertEquals(ControllerAccess.PLAYBACK_ONLY, ControllerPolicy.access(caller("app.sitara.music", uid = ownUid, notification = true), ownUid))
        assertEquals(ControllerAccess.PLAYBACK_ONLY, ControllerPolicy.access(caller("com.android.bluetooth", trusted = true), ownUid))
        assertEquals(ControllerAccess.PLAYBACK_ONLY, ControllerPolicy.access(caller("com.android.bluetooth", uid = 1_002), ownUid))
    }

    @Test
    fun `the car may browse the library as well as control playback`() {
        assertEquals(ControllerAccess.BROWSE, ControllerPolicy.access(caller("com.google.android.projection.gearhead", car = true), ownUid))
        assertEquals(ControllerAccess.BROWSE, ControllerPolicy.access(caller("com.android.car.media", uid = 1_000, trusted = true, car = true), ownUid))
    }

    @Test
    fun `unknown apps are rejected`() {
        assertEquals(ControllerAccess.REJECT, ControllerPolicy.access(caller("com.example.other"), ownUid))
    }

    @Test
    fun `claiming the app's package name from another uid is not enough`() {
        assertEquals(ControllerAccess.REJECT, ControllerPolicy.access(caller("app.sitara.music", uid = 10_500), ownUid))
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
