package app.sitara.music.core.player.automix

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Annotated fixtures for the beat tracker: drum patterns synthesised in the test, so every beat time is known
 * exactly. A kick on beats 1 and 3 (the downbeat louder), a snare on 2 and 4, hi-hats on eighths (the off-beat ones
 * pushed late by [swing], as a share of an eighth), a bass note that changes at each bar line, and background noise.
 */
internal class BeatFixture(
    val bpm: Double,
    val seconds: Double = 30.0,
    val sampleRate: Int = 11_025,
    val swing: Double = 0.0,
    val noise: Float = 0.02f,
    /** Silence before the first beat. */
    val introSeconds: Double = 0.0,
    seed: Int = 7,
) {
    private val random = Random(seed)
    val samples = FloatArray((seconds * sampleRate).toInt())
    val beatsMs: List<Long>
    val downbeatsMs: List<Long>

    init {
        val beatS = 60.0 / bpm
        val beats = mutableListOf<Long>()
        val downbeats = mutableListOf<Long>()
        var index = 0
        var t = introSeconds
        while (t < seconds - 0.2) {
            beats += (t * 1_000).toLong()
            val inBar = index % 4
            if (inBar == 0) {
                downbeats += (t * 1_000).toLong()
                kick(t, 1.0f)
                bass(t, beatS * 4, BASS_NOTES[(index / 4) % BASS_NOTES.size])
            }
            if (inBar == 2) kick(t, 0.6f)
            if (inBar == 1 || inBar == 3) snare(t, 0.5f)
            hat(t, 0.15f)
            hat(t + beatS / 2 * (1 + swing), 0.1f)
            index++
            t += beatS
        }
        beatsMs = beats
        downbeatsMs = downbeats
        for (i in samples.indices) samples[i] += (random.nextFloat() * 2 - 1) * noise
    }

    private fun add(startS: Double, lengthS: Double, voice: (Double) -> Double) {
        val start = (startS * sampleRate).toInt()
        val end = minOf(samples.size, start + (lengthS * sampleRate).toInt())
        for (i in maxOf(0, start) until end) samples[i] += voice((i - start).toDouble() / sampleRate).toFloat()
    }

    private fun kick(t: Double, level: Float) = add(t, 0.15) { s -> level * exp(-s * 30) * sin(2 * PI * (50 + 80 * exp(-s * 40)) * s) }

    private fun snare(t: Double, level: Float) = add(t, 0.12) { s -> level * exp(-s * 35) * (random.nextDouble() * 2 - 1) }

    private fun hat(t: Double, level: Float) {
        var last = 0.0
        add(t, 0.04) { s ->
            val white = random.nextDouble() * 2 - 1
            val high = white - last // a crude high-pass
            last = white
            level * exp(-s * 120) * high
        }
    }

    private fun bass(t: Double, lengthS: Double, hz: Double) = add(t, lengthS * 0.9) { s -> 0.25 * exp(-s * 2) * sin(2 * PI * hz * s) }

    private companion object {
        val BASS_NOTES = listOf(55.0, 65.4, 49.0, 73.4)
    }
}

/** Slow chords with no attack at all: nothing to tap along to. */
internal fun padFixture(seconds: Double = 30.0, sampleRate: Int = 11_025): FloatArray = FloatArray((seconds * sampleRate).toInt()) { i ->
    val s = i.toDouble() / sampleRate
    val swell = 0.5 + 0.5 * sin(2 * PI * s / 7.3)
    (swell * 0.2 * (sin(2 * PI * 220.0 * s) + sin(2 * PI * 277.2 * s) + sin(2 * PI * 329.6 * s))).toFloat()
}
