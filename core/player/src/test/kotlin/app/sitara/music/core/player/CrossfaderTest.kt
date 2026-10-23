package app.sitara.music.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.BeatGrid
import app.sitara.music.core.model.RegionBeats
import app.sitara.music.core.model.SongBeats
import app.sitara.music.core.model.Track
import app.sitara.music.core.player.automix.AutomixPlanner
import app.sitara.music.core.player.automix.Transition
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The crossfader's blends over mocked players and virtual time: where it hands over, and that a cancelled blend leaves nothing behind. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrossfaderTest {
    private val scope = TestScope()
    private val main = mockk<ExoPlayer>(relaxed = true)
    private val tail = mockk<ExoPlayer>(relaxed = true)
    private val a = MediaItem.Builder().setMediaId("a").setUri("sitara://track/a").build()
    private val b = MediaItem.Builder().setMediaId("b").setUri("sitara://track/b").build()
    private val c = MediaItem.Builder().setMediaId("c").setUri("sitara://track/c").build()
    private val tracks = mapOf(
        "a" to Track("a", "Out", "Artist", "First album", 240_000, Artwork(listOf(0))),
        "b" to Track("b", "In", "Artist", "Second album", 200_000, Artwork(listOf(0))),
    )
    private val beats = mutableMapOf<String, SongBeats>()
    private val mainVolumes = mutableListOf<Float>()
    private val tailParameters = mutableListOf<PlaybackParameters>()
    private val blends = mutableListOf<Transition?>()
    private val target = slot<PlayerMessage.Target>()
    private var position = 0L
    private var tailPosition = 0L
    private lateinit var crossfader: Crossfader

    @Before
    fun setUp() {
        every { main.volume = capture(mainVolumes) } just runs
        every { tail.playbackParameters = capture(tailParameters) } just runs
        every { main.currentPosition } answers { position }
        every { main.currentMediaItem } returns a
        every { main.duration } returns 240_000
        every { main.isPlaying } returns true
        every { main.nextMediaItemIndex } returns 1
        every { main.currentMediaItemIndex } returns 0
        every { main.getMediaItemAt(1) } returns b
        every { main.repeatMode } returns Player.REPEAT_MODE_OFF
        every { main.pauseAtEndOfMediaItems } returns false
        every { main.playbackParameters } returns PlaybackParameters.DEFAULT
        val message = mockk<PlayerMessage>(relaxed = true)
        every { main.createMessage(capture(target)) } returns message
        every { message.setLooper(any()) } returns message
        every { message.setPosition(any<Int>(), any<Long>()) } returns message
        every { message.setDeleteAfterDelivery(any()) } returns message
        every { message.send() } returns message
        // The main player moves to the next song where it is asked to.
        every { main.seekTo(1, any<Long>()) } answers { position = secondArg() }
        every { tail.playbackState } returns Player.STATE_READY
        every { tail.isPlaying } returns true
        every { tail.currentPosition } answers { tailPosition }
        crossfader = Crossfader(
            main, tail, scope, trackOf = { tracks[it.mediaId] }, beatsOf = { beats[it] }, now = { scope.testScheduler.currentTime },
            onBlend = { blends += it },
        ) {}
    }

    private fun withBeats() {
        beats["a"] = SongBeats("a", outro = RegionBeats(BeatGrid(124.0, 210_321, 0.8f), -10f, 210_000, 239_000))
        beats["b"] = SongBeats("b", intro = RegionBeats(BeatGrid(126.0, 1_480, 0.8f), -12f, 1_450, 30_000))
    }

    private val plan get() = AutomixPlanner.plan(beats["a"], 240_000, beats["b"], 0)

    /** Plays up to [atMs] with the watch running, so the tail is armed when it should be. */
    private fun playTo(atMs: Long) {
        position = atMs
        crossfader.onIsPlayingChanged(true)
        scope.runCurrent()
    }

    /** The fade point's message arrives and the blend runs for [ms] of virtual time. */
    private fun blendFor(ms: Long, fadeAtMs: Long) {
        target.captured.handleMessage(0, null)
        tailPosition = fadeAtMs
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }

    @Test
    fun `an automix blend is armed for the planned bar line and the next song starts on its own`() {
        withBeats()
        crossfader.automix = true
        playTo(plan.fadeAtMs - 5_000)

        verify { tail.setMediaItem(a, plan.fadeAtMs) }
        blendFor(500, plan.fadeAtMs)

        verify { main.seekTo(1, 1_480) }
        assertEquals(PlaybackParameters(126f / 124f, 1f), tailParameters.last())
        assertTrue(blends.last()!!.onBeat)
    }

    @Test
    fun `automix without beats falls back to a six second crossfade even with crossfade off`() {
        crossfader.automix = true
        playTo(240_000 - 6_000 - 5_000)

        verify { tail.setMediaItem(a, 234_000) }
        blendFor(500, 234_000)
        verify { main.seekTo(1, 0) }
    }

    @Test
    fun `with automix off and crossfade at four seconds the blend is at the end, as before`() {
        withBeats()
        crossfader.windowMs = 4_000
        playTo(230_000)

        verify { tail.setMediaItem(a, 236_000) }
        blendFor(500, 236_000)
        verify { main.seekTo(1, 0) }
    }

    private fun assertNothingLeftBehind() {
        assertEquals(1f, mainVolumes.last())
        assertEquals(PlaybackParameters.DEFAULT, tailParameters.last())
        verify { tail.stop() }
        assertNull(blends.last())
    }

    private fun midBlend() {
        withBeats()
        crossfader.automix = true
        playTo(plan.fadeAtMs - 5_000)
        blendFor(2_000, plan.fadeAtMs)
        tailPosition = plan.fadeAtMs + plan.windowMs / 2
        scope.advanceTimeBy(100)
        scope.runCurrent()
        assertTrue("the incoming song is not yet at full volume", mainVolumes.last() < 1f)
    }

    @Test
    fun `pausing mid-blend leaves no attenuation or stretch behind`() {
        midBlend()
        crossfader.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertNothingLeftBehind()
    }

    @Test
    fun `seeking mid-blend leaves no attenuation or stretch behind`() {
        midBlend()
        val elsewhere = Player.PositionInfo(null, 1, c, null, 1, 30_000, 30_000, -1, -1)
        crossfader.onPositionDiscontinuity(elsewhere, elsewhere, Player.DISCONTINUITY_REASON_SEEK)
        assertNothingLeftBehind()
    }

    @Test
    fun `skipping mid-blend leaves no attenuation or stretch behind`() {
        midBlend()
        crossfader.onMediaItemTransition(c, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        assertNothingLeftBehind()
    }

    @Test
    fun `an interruption mid-blend leaves no attenuation or stretch behind`() {
        midBlend()
        crossfader.onPlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
        assertNothingLeftBehind()
    }

    @Test
    fun `turning automix off mid-blend leaves no attenuation or stretch behind`() {
        midBlend()
        crossfader.automix = false
        assertNothingLeftBehind()
    }

    @Test
    fun `a blend that runs to its end leaves the next song at full volume`() {
        midBlend()
        tailPosition = plan.fadeAtMs + plan.windowMs
        scope.advanceTimeBy(100)
        scope.runCurrent()
        assertNothingLeftBehind()
    }

    @Test
    fun `when the next song runs ahead of the beat the outgoing one is hurried, a little`() {
        withBeats()
        crossfader.automix = true
        playTo(plan.fadeAtMs - 5_000)
        blendFor(100, plan.fadeAtMs)
        val base = 126f / 124f
        position = 1_480 + (60_000 / 126.0 * 0.1).toLong() // a tenth of a beat ahead
        tailPosition = plan.fadeAtMs
        scope.advanceTimeBy(400)
        scope.runCurrent()
        val speed = tailParameters.last().speed
        assertTrue("speed $speed", speed > base && speed <= base * 1.03f)
    }

    @Test
    fun `a queue edit before the blend drops the prepared tail so the next plan follows the new queue`() {
        withBeats()
        crossfader.automix = true
        playTo(plan.fadeAtMs - 5_000)
        crossfader.onTimelineChanged(mockk<Timeline>(relaxed = true), Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        verify { tail.stop() }
        assertEquals(1f, mainVolumes.last())
    }
}
