package app.sitara.music.source.lyrics

import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.LyricsProvider
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

// LRCLIB asks clients to say who they are; the app's name and where it lives, nothing about the device or listener.
internal const val LRCLIB_USER_AGENT = "Sitara (https://github.com/muhib-karim/sitara)"
private const val NOT_FOUND_RETRY_MS = 7 * 24 * 60 * 60 * 1_000L

// LRCLIB's own matching tolerance for a song's length.
private const val DURATION_TOLERANCE_S = 2.0

/** Where a provider keeps its answers, by song id. */
interface LyricsCache {
    suspend fun get(trackId: String): CachedLyrics?

    suspend fun put(trackId: String, lyrics: CachedLyrics)
}

/** Lyrics from LRCLIB over a client with sensible timeouts. */
fun lrclibLyrics(cache: LyricsCache): LyricsProvider =
    LrclibLyricsProvider(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(), cache)

/**
 * Synced or plain lyrics from LRCLIB (https://lrclib.net, free, no key). Asks for the exact song first, then
 * searches and keeps a result of the same length, preferring synced lyrics. Answers are cached by song id: found
 * lyrics for good, "nothing found" for a week. Returns null when there are no lyrics; throws [IOException] when
 * LRCLIB cannot be reached or answers with an error, which is not cached.
 */
class LrclibLyricsProvider(
    private val client: OkHttpClient,
    private val cache: LyricsCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val baseUrl: HttpUrl = "https://lrclib.net/".toHttpUrl(),
) : LyricsProvider {
    override val id: String = "lrclib"

    override suspend fun lyrics(track: Track): Lyrics? {
        val now = clock()
        val answer = cache.get(track.id)?.takeIf { it.synced != null || it.plain != null || now - it.fetchedAtMs < NOT_FOUND_RETRY_MS }
            ?: lookUp(track, now).also { remember(track.id, it) }
        val lyrics = when {
            answer.synced != null -> LrcParser.parse(answer.synced, track.durationMs.takeIf { it > 0 })
            answer.plain != null -> LrcParser.parse(answer.plain)
            else -> return null
        }
        return lyrics.takeIf { it.lines.isNotEmpty() }?.copy(source = "LRCLIB")
    }

    // A cache that cannot be written (disk full) must not hide lyrics LRCLIB found; the song is just asked again next time.
    private suspend fun remember(trackId: String, answer: CachedLyrics) {
        try {
            cache.put(trackId, answer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
    }

    private suspend fun lookUp(track: Track, now: Long): CachedLyrics {
        val query = lyricsQuery(track)
        val seconds = track.durationMs / 1_000.0
        val exact = get("api/get") {
            addQueryParameter("track_name", query.title)
            addQueryParameter("artist_name", query.artist)
            if (track.durationMs > 0) addQueryParameter("duration", (track.durationMs / 1_000).toString())
        }?.let { JsonParser.`object`().from(it) }
        val match = exact ?: get("api/search") {
            addQueryParameter("track_name", query.title)
            addQueryParameter("artist_name", query.artist)
        }?.let { body -> bestMatch(JsonParser.array().from(body).filterIsInstance<JsonObject>(), seconds.takeIf { track.durationMs > 0 }) }
        return CachedLyrics(match?.text("syncedLyrics"), match?.text("plainLyrics"), now)
    }

    /** The body of a successful call, null for 404 (no such song); any other failure throws. */
    private suspend fun get(path: String, query: HttpUrl.Builder.() -> Unit): String? {
        val url = baseUrl.newBuilder().addPathSegments(path).apply(query).build()
        val request = Request.Builder().url(url).header("User-Agent", LRCLIB_USER_AGENT).build()
        return runInterruptible(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body.string()
                    response.code == 404 -> null
                    else -> throw IOException("LRCLIB answered ${response.code} for ${url.encodedPath}")
                }
            }
        }
    }
}

/** Of [results] as long as the song (within LRCLIB's tolerance; any length when unknown), the first synced one, else the first with plain lyrics. */
internal fun bestMatch(results: List<JsonObject>, seconds: Double?): JsonObject? {
    val sameLength = results.filter { seconds == null || abs(it.getDouble("duration") - seconds) <= DURATION_TOLERANCE_S }
    return sameLength.firstOrNull { it.text("syncedLyrics") != null } ?: sameLength.firstOrNull { it.text("plainLyrics") != null }
}

private fun JsonObject.text(key: String): String? = getString(key)?.takeIf { it.isNotBlank() }
