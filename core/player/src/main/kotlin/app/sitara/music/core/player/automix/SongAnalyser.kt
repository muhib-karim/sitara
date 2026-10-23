package app.sitara.music.core.player.automix

import app.sitara.music.core.model.RegionBeats
import app.sitara.music.core.model.SongBeats
import app.sitara.music.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Where automix keeps analyses between plays; the app backs it with Room. */
interface SongBeatsStore {
    suspend fun get(trackId: String): SongBeats?

    suspend fun put(beats: SongBeats)
}

/** The start or the end of a song. */
enum class SongSide { INTRO, OUTRO }

/** Decoded audio of one side of a song: mono samples, their rate, and the song time of the first one. */
class Pcm(val samples: FloatArray, val sampleRate: Int, val startMs: Long)

/** Decodes the first or last [REGION_MS] of a song; null when it cannot be read. */
fun interface RegionDecoder {
    suspend fun decode(track: Track, side: SongSide): Pcm?
}

/** Thrown by a decoder that may not read the song now (a stream on a metered network); it is asked again later. */
class AnalysisDeferredException : Exception("analysis deferred")

/** How much of each end of a song automix reads. */
const val REGION_MS = 30_000L

/**
 * Finds the beat at the end of the playing song and the start of the next one while [enabled], one side at a time on a
 * background thread, each side once per song: analyses are kept in [store] and the latest in memory for the
 * crossfader, which reads them on the main thread through [beatsOf]. A side that cannot be decoded is not retried
 * until the service starts again; one deferred (a stream on a metered network) is asked again at the next change.
 */
class SongAnalyser(
    private val scope: CoroutineScope,
    private val store: SongBeatsStore?,
    private val decoder: RegionDecoder,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    var enabled = false

    private val known = object : LinkedHashMap<String, SongBeats>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SongBeats>?) = size > MEMORY_SONGS
    }
    private val asked = mutableSetOf<Pair<String, SongSide>>()
    private val jobs = mutableMapOf<Pair<String, SongSide>, Job>()
    private val oneAtATime = Mutex()

    /** The analysis of [trackId] so far, if any. Main thread. */
    fun beatsOf(trackId: String): SongBeats? = known[trackId]

    /** Asks for the outro of [current] and the intro of [next]. Main thread. */
    fun follow(current: Track?, next: Track?) {
        if (!enabled) return
        val wanted = listOfNotNull(current?.let { it to SongSide.OUTRO }, next?.let { it to SongSide.INTRO })
        val keys = wanted.map { (track, side) -> track.id to side }.toSet()
        // Songs skipped past are not analysed now (no stream read for nothing); they are asked again if they come back.
        jobs.keys.filter { it !in keys }.forEach { key ->
            jobs.remove(key)?.cancel()
            asked.remove(key)
        }
        wanted.forEach { (track, side) -> ask(track, side) }
    }

    private fun ask(track: Track, side: SongSide) {
        val key = track.id to side
        if (!asked.add(key) || known[track.id]?.has(side) == true) return
        jobs[key] = scope.launch {
            try {
                oneAtATime.withLock {
                    val beats = try {
                        findOrAnalyse(track, side)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: AnalysisDeferredException) {
                        asked.remove(key)
                        null
                    } catch (e: Exception) {
                        null
                    }
                    if (beats != null) known[track.id] = merge(known[track.id], beats)
                }
            } finally {
                jobs.remove(key)
            }
        }
    }

    private suspend fun findOrAnalyse(track: Track, side: SongSide): SongBeats? {
        val stored = store?.get(track.id)
        if (stored?.has(side) == true) return stored
        val region = withContext(work) {
            val pcm = decoder.decode(track, side) ?: return@withContext null
            val (samples, rate) = downsampleForAnalysis(pcm.samples, pcm.sampleRate)
            BeatTracker.analyse(samples, rate, pcm.startMs).let { RegionBeats(it.grid, it.loudnessDb, it.firstSoundMs, it.lastSoundMs) }
        } ?: return stored
        val found = if (side == SongSide.INTRO) SongBeats(track.id, intro = region) else SongBeats(track.id, outro = region)
        store?.put(found)
        return merge(stored, found)
    }

    private fun SongBeats.has(side: SongSide) = if (side == SongSide.INTRO) intro != null else outro != null

    private fun merge(old: SongBeats?, new: SongBeats) = SongBeats(new.trackId, new.intro ?: old?.intro, new.outro ?: old?.outro)

    companion object {
        /** Bumped when analysis changes enough that kept results should be redone. */
        const val VERSION = 1
        private const val MEMORY_SONGS = 64
    }
}
