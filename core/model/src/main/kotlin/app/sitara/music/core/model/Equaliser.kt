package app.sitara.music.core.model

import kotlin.math.pow

/**
 * The built-in equaliser: ten peaking bands an octave apart and a preamp, in whole decibels. Off, or with every gain at
 * 0, it leaves the sound alone. With [useSystem] the device's own equaliser gets the audio session instead.
 */
data class EqualiserSettings(
    val enabled: Boolean = false,
    val bandsDb: List<Int> = List(BAND_COUNT) { 0 },
    val preampDb: Int = 0,
    val useSystem: Boolean = false,
) {
    init {
        require(bandsDb.size == BAND_COUNT) { "expected $BAND_COUNT bands, got ${bandsDb.size}" }
        require(bandsDb.all { it in -MAX_GAIN_DB..MAX_GAIN_DB }) { "band gain out of range: $bandsDb" }
        require(preampDb in -MAX_GAIN_DB..MAX_GAIN_DB) { "preamp out of range: $preampDb" }
    }

    /** Whether the equaliser leaves the sound as it is. */
    val isFlat: Boolean get() = !enabled || (preampDb == 0 && bandsDb.all { it == 0 })

    /** The preset these bands and preamp match, or null for the listener's own. */
    val preset: EqualiserPreset? get() = EqualiserPreset.entries.firstOrNull { it.bandsDb == bandsDb && it.preampDb == preampDb }

    // Choosing a sound turns the equaliser on, so no control is ever without effect.
    fun withPreset(preset: EqualiserPreset) = copy(enabled = true, bandsDb = preset.bandsDb, preampDb = preset.preampDb)

    fun withBand(index: Int, db: Int) = copy(enabled = true, bandsDb = bandsDb.toMutableList().apply { set(index, db.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)) })

    fun withPreamp(db: Int) = copy(enabled = true, preampDb = db.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB))

    fun reset() = copy(bandsDb = EqualiserPreset.FLAT.bandsDb, preampDb = 0)

    /** Whether the device's equaliser has the sound; it can only when the device has one. */
    fun systemActive(systemAvailable: Boolean) = useSystem && systemAvailable

    /** What the built-in equaliser plays: off while the device's has the sound, so the two never shape it together. */
    fun builtIn(systemAvailable: Boolean) = if (systemActive(systemAvailable)) copy(enabled = false) else this

    companion object {
        const val BAND_COUNT = 10
        const val MAX_GAIN_DB = 12

        /** 31.25 Hz to 16 kHz, an octave apart. */
        val BAND_CENTRES_HZ: List<Double> = List(BAND_COUNT) { 1_000.0 * 2.0.pow(it - 5) }
    }
}

/** Starting points for the bands, lowest band first. Each carries a preamp that cancels its largest boost, so a preset adds no level. */
enum class EqualiserPreset(val label: String, val bandsDb: List<Int>) {
    FLAT("Flat", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    BASS_BOOST("Bass boost", listOf(6, 5, 4, 2, 0, 0, 0, 0, 0, 0)),
    TREBLE_BOOST("Treble boost", listOf(0, 0, 0, 0, 0, 1, 2, 4, 5, 6)),
    VOCAL("Vocal", listOf(-2, -2, -1, 1, 3, 4, 3, 1, 0, -1)),
    ROCK("Rock", listOf(4, 3, 1, -1, -2, -1, 1, 3, 4, 4)),
    POP("Pop", listOf(-1, 0, 2, 3, 4, 3, 1, 0, -1, -1)),
    CLASSICAL("Classical", listOf(3, 2, 1, 0, 0, 0, -1, 0, 2, 3)),
    ELECTRONIC("Electronic", listOf(5, 4, 1, 0, -2, 1, 0, 2, 4, 5)),
    ACOUSTIC("Acoustic", listOf(3, 2, 1, 1, 2, 2, 3, 3, 2, 1)),

    // For quiet listening: less of the deep bass that carries through walls and of the sharp top, voices kept clear.
    LATE_NIGHT("Late night", listOf(-4, -3, -1, 0, 1, 2, 2, 0, -2, -3)),
    ;

    val preampDb: Int get() = -maxOf(0, bandsDb.max())
}

/** A listener's volume trim for one song, applied before the equaliser, for a song mastered too loud or too quiet. */
object SongGain {
    const val MAX_TRIM_DB = 6
}
