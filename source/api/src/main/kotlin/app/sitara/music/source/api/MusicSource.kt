package app.sitara.music.source.api

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.ArtistPage
import app.sitara.music.core.model.Chart
import app.sitara.music.core.model.HomePage
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.MoodChip
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Track
import java.util.concurrent.ConcurrentHashMap

/** Catalogue search on its own, for sources that do not offer browse or a library yet. */
interface SearchSource {
    suspend fun search(query: String, filter: SearchFilter = SearchFilter.SONGS): SearchResults
}

/** Detail pages. Each call returns null when [id] does not belong to this catalogue, so catalogues can be tried in turn. */
interface BrowseSource {
    suspend fun artist(id: String): ArtistPage?

    suspend fun album(id: String): Album?

    suspend fun playlist(id: String): Playlist?
}

/** Songs to play after [seed] ("radio"), none of them in [exclude]; null when [seed] is not from this catalogue. */
interface RadioSource {
    suspend fun radio(seed: Track, exclude: Set<String>): List<Track>?
}

/** The songs trending now, for Home. */
interface ChartsSource {
    suspend fun trending(): Chart
}

/** The catalogue's own Home, page by page. */
interface HomeFeedSource {
    /** The first page, filtered to [chip] when one is given. */
    suspend fun home(chip: MoodChip? = null): HomePage

    /** The shelves after a page, from that page's [HomePage.continuation]. */
    suspend fun moreHome(continuation: String): HomePage
}

/** A whole catalogue with a listener's library, as the offline sample source offers it to tests and fixtures. */
interface MusicSource : SearchSource {
    val id: String

    suspend fun library(): Library
}

/** A playable stream for a track. [expiresAtMs] lets callers refresh signed URLs early. */
data class ResolvedStream(
    val url: String,
    val mimeType: String,
    val bitrateKbps: Int,
    val expiresAtMs: Long? = null,
    val loudnessLufs: Float? = null,
)

/** Turns a catalogue track into a stream URL. Implementations are tried in order with fallback. */
interface StreamResolver {
    val id: String

    suspend fun resolve(track: Track): ResolvedStream
}

interface LyricsProvider {
    val id: String

    suspend fun lyrics(track: Track): Lyrics?
}

enum class HealthStatus { HEALTHY, DEGRADED, DOWN }

data class HealthReport(
    val sourceId: String,
    val status: HealthStatus,
    val latencyMs: Long,
    val detail: String? = null,
)

/** Cheap probe used by the source-health dashboard and by resolver fail-over. */
interface HealthCheck {
    suspend fun check(): HealthReport
}

/**
 * Tries each resolver in order and returns the first stream, so a broken
 * resolver degrades to the next one instead of failing playback. The resolver
 * that last worked for a song is asked first next time, for as long as this
 * object lives (the app keeps one per process).
 */
class FallbackStreamResolver(
    private val resolvers: List<StreamResolver>,
) : StreamResolver {
    init {
        require(resolvers.isNotEmpty()) { "At least one resolver is required" }
    }

    override val id: String = resolvers.joinToString("+") { it.id }

    // Track id -> the resolver that served it. One small entry per song played; read and written from loader threads.
    private val winners = ConcurrentHashMap<String, StreamResolver>()

    override suspend fun resolve(track: Track): ResolvedStream {
        val failures = mutableListOf<Throwable>()
        val remembered = winners[track.id]
        val order = if (remembered == null) resolvers else listOf(remembered) + (resolvers - remembered)
        for (resolver in order) {
            try {
                return resolver.resolve(track).also { winners[track.id] = resolver }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += e
            }
        }
        throw StreamUnavailableException(track.id, failures)
    }
}

class StreamUnavailableException(
    trackId: String,
    val causes: List<Throwable>,
) : Exception("No resolver could stream $trackId (${causes.size} failed)") {
    init {
        causes.forEach { addSuppressed(it) } // so a logged stack trace shows why each resolver failed
    }
}
