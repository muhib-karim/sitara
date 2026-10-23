package app.sitara.music.core.model

import kotlin.math.ceil
import kotlin.math.floor

/** A steady beat: [bpm] beats a minute in four-beat bars, one bar line at [downbeatMs] (song time). */
data class BeatGrid(val bpm: Double, val downbeatMs: Long, val confidence: Float) {
    val beatMs: Double get() = 60_000.0 / bpm
    val barMs: Double get() = 4 * beatMs

    /** The first bar line at or after [ms]. */
    fun downbeatAtOrAfter(ms: Long): Long = (downbeatMs + ceil((ms - downbeatMs) / barMs - EPSILON) * barMs).toLong()

    /** The last bar line at or before [ms]. */
    fun downbeatAtOrBefore(ms: Long): Long = (downbeatMs + floor((ms - downbeatMs) / barMs + EPSILON) * barMs).toLong()

    /** Where [ms] falls between beats, 0 on a beat up to just under 1. */
    fun beatPhase(ms: Double): Double {
        val beats = (ms - downbeatMs) / beatMs
        return beats - floor(beats)
    }

    private companion object {
        const val EPSILON = 1e-6
    }
}

/**
 * What automix knows about a song: the beat and level of its first and last stretch, where sound starts and stops.
 * Either side is null until analysed, and its grid null when it has no steady beat.
 */
data class SongBeats(
    val trackId: String,
    val intro: RegionBeats? = null,
    val outro: RegionBeats? = null,
)

/** One analysed stretch of a song: its beat (null without a steady one), level in dBFS and the span with sound. */
data class RegionBeats(val grid: BeatGrid?, val loudnessDb: Float, val firstSoundMs: Long, val lastSoundMs: Long)

/** An automix blend while it plays, for the player screen: tempos as heard and the blend's length in bars. */
data class AutomixBlend(val fromBpm: Int, val toBpm: Int, val bars: Int)
