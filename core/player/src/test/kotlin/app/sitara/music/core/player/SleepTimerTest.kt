package app.sitara.music.core.player

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    @Test
    fun `counts down and fires once when minutes elapse`() = runTest {
        var fired = 0
        val timer = SleepTimer(backgroundScope, clock = { testScheduler.currentTime }) { fired++ }
        timer.state.test {
            assertEquals(SleepTimerState.Off, awaitItem())
            timer.start(minutes = 1)
            assertEquals(SleepTimerState.Counting(60_000), awaitItem())
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(SleepTimerState.Counting(59_000), awaitItem())
            advanceTimeBy(59_000)
            runCurrent()
            assertEquals(SleepTimerState.Off, expectMostRecentItem())
        }
        assertEquals(1, fired)
    }

    @Test
    fun `cancel stops the countdown`() = runTest {
        var fired = 0
        val timer = SleepTimer(backgroundScope, clock = { testScheduler.currentTime }) { fired++ }
        timer.start(minutes = 5)
        advanceTimeBy(10_000)
        timer.cancel()
        advanceTimeBy(10 * 60_000)
        assertEquals(SleepTimerState.Off, timer.state.value)
        assertEquals(0, fired)
    }

    @Test
    fun `end of track waits for the track to finish`() = runTest {
        var fired = 0
        val timer = SleepTimer(backgroundScope, clock = { testScheduler.currentTime }) { fired++ }
        timer.startEndOfTrack()
        assertEquals(SleepTimerState.EndOfTrack, timer.state.value)
        assertTrue(timer.onTrackEnded())
        assertEquals(1, fired)
        assertFalse(timer.onTrackEnded())
    }

    @Test
    fun `rejects non-positive durations`() = runTest {
        val timer = SleepTimer(backgroundScope, clock = { 0 }) {}
        runCatching { timer.start(0) }.also { assertTrue(it.isFailure) }
    }
}
