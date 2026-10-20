package app.sitara.music.core.player

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.media.audiofx.AudioEffect
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.EqualiserPreset
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The real service, ExoPlayer and media session on Robolectric; streams never resolve, so nothing plays or touches the network. Media item metadata is covered by [Media3PlayerPortTest]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PlaybackServiceTest.TestApp::class)
class PlaybackServiceTest {
    @OptIn(UnstableApi::class)
    class TestApp :
        Application(),
        PlaybackDependencies {
        var saved: SavedQueue? = null
        val saves = mutableListOf<SavedQueue>()

        override val streamResolver = object : StreamResolver {
            override val id = "offline"

            override suspend fun resolve(track: Track): ResolvedStream = throw IOException("No streams in tests")
        }

        override val downloadCache: Cache by lazy { SimpleCache(createTempDirectory().toFile(), NoOpCacheEvictor(), StandaloneDatabaseProvider(this)) }

        override val playbackSettings = MutableStateFlow(SitaraSettings())

        override fun onTrackStarted(track: Track) = Unit

        override suspend fun savedQueue() = saved

        override fun saveQueue(queue: SavedQueue) {
            saves += queue
        }
    }

    private val songs = (1..3).map { Track("id$it", "Song $it", "Artist $it", "Album", 200_000, Artwork(listOf(0), "https://i.example.org/$it.jpg")) }

    @Test
    fun `a new service puts the saved queue back paused and saves it when it ends`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        app.saved = SavedQueue(songs, currentIndex = 1, positionMs = 30_000, repeatMode = RepeatMode.ALL)

        val service = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()

        val engine = checkNotNull(PlaybackEngineRegistry.engine.value)
        val queue = engine.state.value.queue
        assertEquals(songs, queue.items)
        assertEquals(songs[1], queue.current)
        assertEquals(RepeatMode.ALL, queue.repeatMode)
        assertFalse(engine.state.value.isPlaying)
        assertEquals("restored at the saved position", app.saved, engine.savedQueue())

        service.destroy()
        assertEquals(app.saved, app.saves.last())
    }

    @Test
    fun `normalisation, skip silence and crossfade follow the settings while the service runs`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        app.playbackSettings.value = SitaraSettings(crossfadeSeconds = 6, normalisation = false, targetLufs = -16f, skipSilence = true)

        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()
        assertEquals(6_000L, service.crossfader.windowMs)
        assertFalse(service.leveller.enabled)
        assertEquals(-16f, service.leveller.targetLufs, 0f)
        assertTrue("silence skipping is in the audio chain", service.player.skipSilenceEnabled)
        assertTrue("and in the crossfade tail's", service.tail.skipSilenceEnabled)

        app.playbackSettings.value = SitaraSettings()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("crossfade off by default", 0L, service.crossfader.windowMs)
        assertTrue("normalisation on by default", service.leveller.enabled)
        assertFalse("skip silence off by default", service.player.skipSilenceEnabled)
        assertFalse(service.tail.skipSilenceEnabled)

        controller.destroy()
    }

    @Test
    fun `the equaliser in both players follows the settings while the service runs`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        val rock = EqualiserSettings().withPreset(EqualiserPreset.ROCK)
        app.playbackSettings.value = SitaraSettings(equaliser = rock)

        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()
        assertEquals(rock, service.equaliser.settings)
        assertEquals(rock, service.tailEqualiser.settings)

        val custom = rock.withBand(0, -3)
        app.playbackSettings.value = SitaraSettings(equaliser = custom)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(custom, service.equaliser.settings)
        assertEquals(custom, service.tailEqualiser.settings)

        controller.destroy()
    }

    /** Runs the main looper until [done], while ExoPlayer's playback thread works in the background. */
    private fun idleUntil(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("timed out", done())
    }

    private fun installSystemEqualiser(app: Application) {
        val panel = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "com.example.equaliser"; name = "Panel" } }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL), panel)
    }

    @Test
    fun `with the device's equaliser chosen, the built-in one stays flat and both players share its audio session`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        installSystemEqualiser(app)
        val rock = EqualiserSettings(useSystem = true).withPreset(EqualiserPreset.ROCK)
        app.playbackSettings.value = SitaraSettings(equaliser = rock)

        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        val service = controller.get()
        idleUntil { service.player.audioSessionId != 0 && service.tail.audioSessionId == service.player.audioSessionId }
        assertTrue(service.equaliser.settings.isFlat)
        assertTrue(service.tailEqualiser.settings.isFlat)
        assertEquals(service.player.audioSessionId, PlaybackEngineRegistry.audioSessionId)

        app.playbackSettings.value = SitaraSettings(equaliser = rock.copy(useSystem = false))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("switching back hands the sound to the built-in one", rock.copy(useSystem = false), service.equaliser.settings)

        controller.destroy()
        assertEquals(0, PlaybackEngineRegistry.audioSessionId)
    }

    @Test
    fun `without a device equaliser the built-in one keeps the sound and no session is announced`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        val rock = EqualiserSettings(useSystem = true).withPreset(EqualiserPreset.ROCK)
        app.playbackSettings.value = SitaraSettings(equaliser = rock)

        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        val service = controller.get()
        idleUntil { service.player.audioSessionId != 0 }
        assertEquals(rock, service.equaliser.settings)
        assertNotEquals(0, service.player.audioSessionId)

        controller.destroy()
        val announced = shadowOf(app).broadcastIntents.map { it.action }
        assertFalse(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION in announced)
        assertFalse(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION in announced)
    }
}
