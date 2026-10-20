package app.sitara.music.core.player

import android.app.Application
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The real service, ExoPlayer and media session on Robolectric; streams never resolve, so nothing plays or touches the network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PlaybackServiceTest.TestApp::class)
class PlaybackServiceTest {
    class TestApp :
        Application(),
        PlaybackDependencies {
        var saved: SavedQueue? = null
        val saves = mutableListOf<SavedQueue>()

        override val streamResolver = object : StreamResolver {
            override val id = "offline"

            override suspend fun resolve(track: Track): ResolvedStream = throw IOException("No streams in tests")
        }

        override fun onTrackStarted(track: Track) = Unit

        override suspend fun savedQueue() = saved

        override fun saveQueue(queue: SavedQueue) {
            saves += queue
        }
    }

    private val songs = (1..3).map { Track("id$it", "Song $it", "Artist $it", "Album", 200_000, Artwork(listOf(0), "https://i.example.org/$it.jpg")) }

    @Test
    fun `a new service puts the saved queue back paused, ready for the notification, and saves it when it ends`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        app.saved = SavedQueue(songs, currentIndex = 1, positionMs = 30_000, repeatMode = RepeatMode.ALL)

        val service = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()

        val player = service.get().sessions.single().player
        assertEquals(3, player.mediaItemCount)
        assertEquals(1, player.currentMediaItemIndex)
        assertFalse(player.playWhenReady)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
        assertEquals("Song 2", player.getMediaItemAt(1).mediaMetadata.title.toString())
        assertEquals("https://i.example.org/2.jpg", player.getMediaItemAt(1).mediaMetadata.artworkUri.toString())
        assertEquals(C.USAGE_MEDIA, player.audioAttributes.usage)

        service.destroy()
        assertEquals(app.saved, app.saves.last())
    }
}
