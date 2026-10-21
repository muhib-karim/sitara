package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.LyricsProvider
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

private const val SOURCE_TIMEOUT_MS = 10_000L

/** One source's lyrics for a song. */
data class LyricsMatch(val providerId: String, val lyrics: Lyrics)

/** The source the listener picked for each song, by song id. */
interface LyricsChoices {
    suspend fun get(trackId: String): String?

    suspend fun put(trackId: String, providerId: String)
}

/**
 * Asks every lyrics source at once and ranks what they found: synced before plain, then lyrics filed under a song as long
 * as this one (within LRCLIB's tolerance), then under its exact title and artist; ties keep the sources' order. A source
 * that fails or says nothing within [timeoutMs] is left out. The source the listener picked for a song comes first while
 * it still has lyrics for it. Each source caches its own answers.
 */
class LyricsSources(
    private val providers: List<LyricsProvider>,
    private val choices: LyricsChoices,
    private val timeoutMs: Long = SOURCE_TIMEOUT_MS,
) {
    /** Every source's name, in the order they are asked. */
    val names: List<String> = providers.map { it.name }

    /** Every source's lyrics for [track], the ones to show first; empty when none has any. Throws when none has any and a source could not answer, so the lookup can be tried again. */
    // shortcut: lyrics show once every source answered (at most timeoutMs); show the first synced answer early if a slow source delays them in use.
    suspend fun lookUp(track: Track): List<LyricsMatch> {
        val answers = coroutineScope { providers.map { async { answer(it, track) } }.awaitAll() }
        val found = rank(track, answers.mapNotNull { it.getOrNull() })
        if (found.isEmpty()) answers.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        val picked = choices.get(track.id)
        return found.sortedByDescending { it.providerId == picked }
    }

    /** Shows [providerId]'s lyrics for [track] from now on. */
    suspend fun choose(track: Track, providerId: String) = choices.put(track.id, providerId)

    private suspend fun answer(provider: LyricsProvider, track: Track): Result<LyricsMatch?> = try {
        Result.success(withTimeout(timeoutMs) { provider.lyrics(track) }?.let { LyricsMatch(provider.id, it) })
    } catch (e: TimeoutCancellationException) {
        Result.failure(IOException("${provider.name} did not answer in time", e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun rank(track: Track, found: List<LyricsMatch>): List<LyricsMatch> {
    val query = lyricsQuery(track)
    return found.sortedWith(
        compareByDescending<LyricsMatch> { it.lyrics.synced }
            .thenByDescending { match ->
                val length = match.lyrics.matched?.durationMs ?: 0
                length > 0 && track.durationMs > 0 && abs(length - track.durationMs) <= DURATION_TOLERANCE_S * 1_000
            }
            .thenByDescending { match ->
                val song = match.lyrics.matched
                song != null && (song.title.equals(track.title, ignoreCase = true) || song.title.equals(query.title, ignoreCase = true)) &&
                    (song.artist.equals(track.artist, ignoreCase = true) || song.artist.equals(query.artist, ignoreCase = true))
            },
    )
}
