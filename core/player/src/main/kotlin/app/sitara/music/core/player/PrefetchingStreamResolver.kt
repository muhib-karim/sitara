package app.sitara.music.core.player

import app.sitara.music.core.model.Track
import app.sitara.music.core.model.isLocal
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Looks up the next song's stream while the current one plays, so the player finds it ready at the boundary and the
 * songs join without a gap; a skip to it starts at once too. Holds one prepared song: preparing another cancels the
 * first lookup. A preparation that failed or is older than [MAX_AGE] (stream addresses expire) is looked up afresh.
 */
class PrefetchingStreamResolver(
    private val upstream: StreamResolver,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
) : StreamResolver {
    override val id: String get() = upstream.id

    private class Prepared(val trackId: String, val at: TimeMark, val stream: Deferred<ResolvedStream?>) {
        val fresh: Boolean get() = at.elapsedNow() < MAX_AGE
    }

    // Prepared on the main thread, read by the player's loader thread.
    private val slot = AtomicReference<Prepared?>(null)

    fun prepare(track: Track) {
        if (track.isLocal) return
        val current = slot.get()
        if (current != null && current.trackId == track.id && current.fresh) return
        val lookup = scope.async {
            try {
                upstream.resolve(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // the load itself tries again and reports the error
            }
        }
        slot.getAndSet(Prepared(track.id, timeSource.markNow(), lookup))?.stream?.cancel()
    }

    override suspend fun resolve(track: Track): ResolvedStream {
        val prepared = slot.get()?.takeIf { it.trackId == track.id && it.fresh }
        val stream = try {
            prepared?.stream?.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive() // only the preparation was replaced
            null
        }
        return stream ?: upstream.resolve(track)
    }

    companion object {
        val MAX_AGE = 30.minutes
    }
}
