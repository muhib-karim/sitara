package app.sitara.music.core.player

import app.cash.turbine.test
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
    fun `a custom number of minutes expires exactly then`() = runTest {
        var fired = 0
        val timer = SleepTimer(backgroundScope, timeSource = virtualTime()) { fired++ }
        timer.start(minutes = 37)
        advanceTimeBy(37 * 60_000L - 1)
        runCurrent()
        assertEquals(0, fired)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, fired)
    }

    @Test
    fun `extending while counting moves the expiry by that much`() = runTest {
        var fired = 0
        val timer = SleepTimer(backgroundScope, timeSource = virtualTime()) { fired++ }
        timer.start(minutes = 1)
        advanceTimeBy(30_000)
        timer.extend(minutes = 5)
        assertEquals(SleepTimerState.Counting(330_000), timer.state.value)
        timer.extend(minutes = 15)
        assertEquals(SleepTimerState.Counting(1_230_000), timer.state.value)

        advanceTimeBy(1_230_000 - 1)
        runCurrent()
        assertEquals(0, fired)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, fired)
        assertEquals(SleepTimerState.Off, timer.state.value)
    }

    @Test
    fun `extending in the last second still counts on`() = runTest {
        var fired = 0
        val timer = SleepTimer(backgroundScope, timeSource = virtualTime()) { fired++ }
        timer.start(minutes = 1)
        advanceTimeBy(59_500)
        runCurrent()
        timer.extend(minutes = 5)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, fired)
        assertEquals("the next whole-second tick reads the new deadline", SleepTimerState.Counting(300_000), timer.state.value)
    }

    @Test
    fun `there is nothing to extend when no countdown runs`() = runTest {
        val timer = SleepTimer(backgroundScope, timeSource = virtualTime()) {}
        timer.extend(minutes = 5)
        assertEquals(SleepTimerState.Off, timer.state.value)
        timer.startEndOfTrack()
        timer.extend(minutes = 5)
        assertEquals(SleepTimerState.EndOfTrack, timer.state.value)
    }

    @Test
    fun `cancel after an extension never fires`() = runTest {
        var fired = 0
        val timer = SleepTimer(backgroundScope, timeSource = virtualTime()) { fired++ }
        timer.start(minutes = 1)
        timer.extend(minutes = 5)
        timer.cancel()
        timer.extend(minutes = 5)
        advanceTimeBy(60 * 60_000L)
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
