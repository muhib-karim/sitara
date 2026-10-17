package app.sitara.music.core.player

import app.cash.turbine.test
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    /** Monotonic time that follows the test scheduler, standing in for elapsedRealtime. */
    private fun TestScope.virtualTime(): TimeSource = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
        override fun read(): Long = testScheduler.currentTime
    }

    @Test
    fun `counts down and fires once when minutes elapse`() = runTest {
        var fired = 0
        val timer = SleepTimer(backgroundScope, timeSource = virtualTime()) { fired++ }
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
        val timer = SleepTimer(backgroundScope, timeSource = virtualTime()) { fired++ }
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
        val timer = SleepTimer(backgroundScope, timeSource = virtualTime()) { fired++ }
        timer.startEndOfTrack()
        assertEquals(SleepTimerState.EndOfTrack, timer.state.value)
        assertTrue(timer.onTrackEnded())
        assertEquals(1, fired)
        assertFalse(timer.onTrackEnded())
    }

    @Test
    fun `rejects non-positive durations`() = runTest {
        val timer = SleepTimer(backgroundScope, timeSource = virtualTime()) {}
        runCatching { timer.start(0) }.also { assertTrue(it.isFailure) }
    }
}
