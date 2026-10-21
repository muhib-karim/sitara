package app.sitara.music.core.player

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.EqualiserPreset
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.Listen
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import java.io.IOException
import java.time.Duration
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

        val trims = MutableStateFlow(mapOf<String, Int>())

        override fun onListened(listen: Listen) = Unit

        override fun songGainDb(trackId: String) = trims.map { it[trackId] ?: 0 }

        val liked = MutableStateFlow(setOf<String>())

        override fun isLiked(trackId: String) = liked.map { trackId in it }

        override fun setLiked(track: Track, liked: Boolean) {
            this.liked.value = if (liked) this.liked.value + track.id else this.liked.value - track.id
        }

        override suspend fun savedQueue() = saved

        override fun saveQueue(queue: SavedQueue) {
            saves += queue
        }

        var likedTracks = emptyList<Track>()

        // The library as the car sees it; the catalogue is out of reach, as on a CI runner.
        override val libraryContent = object : LibraryContent {
            override suspend fun recentlyPlayed() = emptyList<Track>()

            override suspend fun likedSongs() = likedTracks

            override suspend fun downloadedSongs() = emptyList<Track>()

            override suspend fun playlists() = emptyList<PlaylistSummary>()

            override suspend fun playlistSongs(id: Long) = emptyList<Track>()

            override suspend fun savedAlbums() = emptyList<Album>()

            override suspend fun savedArtists() = emptyList<Artist>()

            override suspend fun albumSongs(id: String): List<Track> = throw IOException("offline")

            override suspend fun artistSongs(id: String): List<Track> = throw IOException("offline")

            override suspend fun searchSongs(query: String): List<Track> = throw IOException("offline")
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

    @Test
    fun `the notification's like, shuffle and repeat buttons show their state and change it`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        app.saved = SavedQueue(songs, currentIndex = 1, positionMs = 0, repeatMode = RepeatMode.OFF)
        app.liked.value = setOf("id2")

        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()
        fun icons() = checkNotNull(service.session).mediaButtonPreferences.map { it.icon }
        assertEquals(listOf(CommandButton.ICON_HEART_FILLED, CommandButton.ICON_SHUFFLE_OFF, CommandButton.ICON_REPEAT_OFF), icons())

        assertTrue(service.onSessionAction(SessionActions.LIKE))
        assertTrue(service.onSessionAction(SessionActions.SHUFFLE))
        assertTrue(service.onSessionAction(SessionActions.REPEAT))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("the heart unlikes the song that plays", emptySet<String>(), app.liked.value)
        val queue = checkNotNull(PlaybackEngineRegistry.engine.value).state.value.queue
        assertTrue(queue.shuffled)
        assertEquals(RepeatMode.ALL, queue.repeatMode)
        assertEquals(listOf(CommandButton.ICON_HEART_UNFILLED, CommandButton.ICON_SHUFFLE_ON, CommandButton.ICON_REPEAT_ALL), icons())

        service.onSessionAction(SessionActions.REPEAT)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(CommandButton.ICON_REPEAT_ONE, icons().last())
        assertFalse("no other commands", service.onSessionAction("com.example.ERASE"))

        controller.destroy()
    }

    @Test
    fun `with nothing queued the heart does nothing`() {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.get().onSessionAction(SessionActions.LIKE))
        assertEquals(emptySet<String>(), (RuntimeEnvironment.getApplication() as TestApp).liked.value)
        controller.destroy()
    }

    private fun car(): MediaSession.ControllerInfo = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
        "com.google.android.projection.gearhead", 0, 10_456, MediaLibraryInfo.VERSION_INT, 0, false, Bundle.EMPTY, true,
    )

    @Test
    fun `the car may browse, pick songs, like and shuffle, but the queue is still the app's`() {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()
        val result = service.callback.onConnect(checkNotNull(service.session), car())
        assertTrue(result.isAccepted)
        assertTrue(result.availableSessionCommands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
        assertTrue(result.availableSessionCommands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
        assertTrue(result.availableSessionCommands.contains(SessionCommand(SessionActions.LIKE, Bundle.EMPTY)))
        assertTrue(result.availableSessionCommands.contains(SessionCommand(SessionActions.SHUFFLE, Bundle.EMPTY)))
        assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_SET_MEDIA_ITEM))
        assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_PLAY_PAUSE))
        assertFalse(result.availablePlayerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        controller.destroy()
    }

    @Test
    fun `the car lists liked songs as playable items with their artist and cover`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        app.likedTracks = songs
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()

        val future = service.callback.onGetChildren(checkNotNull(service.session), car(), BrowseId.Liked.mediaId, 0, 2, null)
        idleUntil { future.isDone }
        val items = checkNotNull(future.get().value)
        assertEquals("the first page", listOf(BrowseId.Song(BrowseId.Liked, "id1").mediaId, BrowseId.Song(BrowseId.Liked, "id2").mediaId), items.map { it.mediaId })
        val metadata = items[0].mediaMetadata
        assertEquals("Song 1", metadata.title.toString())
        assertEquals("Artist 1", metadata.artist.toString())
        assertEquals("https://i.example.org/1.jpg", metadata.artworkUri.toString())
        assertEquals(true, metadata.isPlayable)
        assertEquals(false, metadata.isBrowsable)

        val unknown = service.callback.onGetChildren(checkNotNull(service.session), car(), "nowhere", 0, 10, null)
        idleUntil { unknown.isDone }
        assertEquals(SessionError.ERROR_BAD_VALUE, unknown.get().resultCode)
        controller.destroy()
    }

    @Test
    fun `the car lays out playlists, albums and artists as a grid and songs as a list`() {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()
        val session = checkNotNull(service.session)

        val root = service.callback.onGetLibraryRoot(session, car(), null).get()
        val defaults = checkNotNull(root.params).extras
        assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM, defaults.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE))

        val library = service.callback.onGetChildren(session, car(), BrowseId.Library.mediaId, 0, 10, null)
        idleUntil { library.isDone }
        checkNotNull(library.get().value).forEach { folder ->
            assertEquals(folder.mediaId, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM, folder.mediaMetadata.extras?.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE))
        }
        val tabs = service.callback.onGetChildren(session, car(), BrowseId.Root.mediaId, 0, 10, null)
        idleUntil { tabs.isDone }
        val liked = checkNotNull(tabs.get().value).first { it.mediaId == BrowseId.Liked.mediaId }
        assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM, liked.mediaMetadata.extras?.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE))
        controller.destroy()
    }

    @Test
    fun `a song that could not be played tells the car and the lock screen so in words, and they can still try again or skip`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        app.saved = SavedQueue(songs, currentIndex = 0, positionMs = 0, repeatMode = RepeatMode.OFF)
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()
        val shown = checkNotNull(service.session).player
        try {
            // Streams never resolve in tests, as when YouTube refuses one. ExoPlayer retries the load after 1, 2 and 3 s
            // on its clock, which moves only as the test advances it.
            checkNotNull(PlaybackEngineRegistry.engine.value).togglePlay()
            idleUntil(advanceMs = 100) { shown.playerError != null }
            assertEquals("Couldn’t play this song. Try again, skip it, or play a download.", shown.playerError?.message)
            assertEquals(service.player.playerError?.errorCode, shown.playerError?.errorCode)
            assertTrue(shown.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            assertTrue(shown.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `a song picked in the car plays its list from that song`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        app.likedTracks = songs
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()

        val picked = MediaItem.Builder().setMediaId(BrowseId.Song(BrowseId.Liked, "id2").mediaId).build()
        val future = service.callback.onSetMediaItems(checkNotNull(service.session), car(), mutableListOf(picked), C.INDEX_UNSET, C.TIME_UNSET)
        idleUntil { future.isDone }

        val queue = checkNotNull(PlaybackEngineRegistry.engine.value).state.value.queue
        assertEquals(songs, queue.items)
        assertEquals(songs[1], queue.current)
        val handedBack = future.get()
        assertEquals("the session is handed the engine's own playlist", queue.entries.map { it.uid.toString() }, handedBack.mediaItems.map { it.mediaId })
        assertEquals(1, handedBack.startIndex)
        controller.destroy()
    }

    /** Runs the main looper until [done], while ExoPlayer's playback thread works in the background; [advanceMs] moves the clock each round. */
    private fun idleUntil(advanceMs: Long = 0, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(advanceMs))
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

    @Test
    fun `the current song's trim is applied and follows changes to it`() {
        val app = RuntimeEnvironment.getApplication() as TestApp
        app.saved = SavedQueue(songs, currentIndex = 1, positionMs = 0, repeatMode = RepeatMode.OFF)
        app.trims.value = mapOf("id2" to -4, "id3" to 5)

        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()
        assertEquals(-4, service.equaliser.trimDb)

        app.trims.value = mapOf("id2" to 2)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, service.equaliser.trimDb)

        checkNotNull(PlaybackEngineRegistry.engine.value).next()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("the next song has no trim", 0, service.equaliser.trimDb)

        controller.destroy()
    }
}
