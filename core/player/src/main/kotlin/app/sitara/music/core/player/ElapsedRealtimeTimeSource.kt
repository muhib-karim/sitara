package app.sitara.music.core.player

import android.os.SystemClock
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit

/** Monotonic time that keeps counting through deep sleep, unlike System.nanoTime on Android. */
object ElapsedRealtimeTimeSource : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
    override fun read(): Long = SystemClock.elapsedRealtime()
}
