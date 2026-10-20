package app.sitara.music.core.model

import kotlin.math.pow

/** The built-in equaliser: ten peaking bands an octave apart and a preamp, in whole decibels. Off, or with every gain at 0, it leaves the sound alone. */
data class EqualiserSettings(
    val enabled: Boolean = false,
    val bandsDb: List<Int> = List(BAND_COUNT) { 0 },
    val preampDb: Int = 0,
) {
    init {
        require(bandsDb.size == BAND_COUNT) { "expected $BAND_COUNT bands, got ${bandsDb.size}" }
        require(bandsDb.all { it in -MAX_GAIN_DB..MAX_GAIN_DB }) { "band gain out of range: $bandsDb" }
        require(preampDb in -MAX_GAIN_DB..MAX_GAIN_DB) { "preamp out of range: $preampDb" }
    }

    /** Whether the equaliser leaves the sound as it is. */
    val isFlat: Boolean get() = !enabled || (preampDb == 0 && bandsDb.all { it == 0 })

    companion object {
        const val BAND_COUNT = 10
        const val MAX_GAIN_DB = 12

        /** 31.25 Hz to 16 kHz, an octave apart. */
        val BAND_CENTRES_HZ: List<Double> = List(BAND_COUNT) { 1_000.0 * 2.0.pow(it - 5) }
    }
}
