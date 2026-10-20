package app.sitara.music.source.api

import app.sitara.music.core.model.ArtistPage
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.Track

/** Catalogue search on its own, for sources that do not offer browse or a library yet. */
interface SearchSource {
    suspend fun search(query: String): SearchResults
}

/** Detail pages. Each call returns null when [id] does not belong to this catalogue, so catalogues can be tried in turn. */
interface BrowseSource {
    suspend fun artist(id: String): ArtistPage?
}

/** Catalogue access: everything the browse screens need. */
interface MusicSource : SearchSource {
    val id: String

    suspend fun home(): List<Shelf>

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
 * resolver degrades to the next one instead of failing playback.
 */
class FallbackStreamResolver(
    private val resolvers: List<StreamResolver>,
) : StreamResolver {
    init {
        require(resolvers.isNotEmpty()) { "At least one resolver is required" }
    }

    override val id: String = resolvers.joinToString("+") { it.id }

    override suspend fun resolve(track: Track): ResolvedStream {
        val failures = mutableListOf<Throwable>()
        for (resolver in resolvers) {
            try {
                return resolver.resolve(track)
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
) : Exception("No resolver could stream $trackId (${causes.size} failed)")
