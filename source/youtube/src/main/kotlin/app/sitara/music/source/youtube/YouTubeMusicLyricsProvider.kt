package app.sitara.music.source.youtube

import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.LyricsSong
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.LyricsProvider
import app.sitara.music.source.lyrics.LrcParser
import app.sitara.music.source.lyrics.LyricsCache
import com.grack.nanojson.JsonObject
import java.io.IOException
import kotlinx.coroutines.CancellationException

private const val NOT_FOUND_RETRY_MS = 7 * 24 * 60 * 60 * 1_000L
private const val NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
private const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
private const val LYRICS_PAGE = "MUSIC_PAGE_TYPE_TRACK_LYRICS"

/** YouTube Music's own lyrics for its songs, asked as the app's other YouTube Music requests are. */
fun YouTubeMusicSource.lyricsProvider(cache: LyricsCache): LyricsProvider = YouTubeMusicLyricsProvider(cache, fetch = ::music)

/**
 * The lyrics YouTube Music shows on a song's Lyrics tab: plain text, credited to whoever licensed it (Musixmatch,
 * LyricFind). The song's watch page names the tab's page, which holds the text; a song whose tab cannot be opened has
 * none. Answers are cached by song id like LRCLIB's: found lyrics for good, "nothing found" for a week; a failed call
 * throws [IOException] and is not cached.
 */
class YouTubeMusicLyricsProvider(
    private val cache: LyricsCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fetch: suspend (url: String, fields: Map<String, String>) -> JsonObject,
) : LyricsProvider {
    override val id: String = "youtube-music"
    override val name: String = "YouTube Music"

    override suspend fun lyrics(track: Track): Lyrics? {
        if (!isVideoId(track.id)) return null
        val now = clock()
        val answer = cache.get(track.id)?.takeIf { it.plain != null || now - it.fetchedAtMs < NOT_FOUND_RETRY_MS }
            ?: lookUp(track.id, now).also { remember(track.id, it) }
        val lyrics = LrcParser.parse(answer.plain ?: return null)
        // The tab belongs to this very song, so the song it matched is the song itself.
        return lyrics.takeIf { it.lines.isNotEmpty() }?.copy(source = name, licensor = answer.licensor, matched = LyricsSong(track.title, track.artist, track.durationMs))
    }

    private suspend fun lookUp(videoId: String, now: Long): CachedLyrics {
        val page = lyricsBrowseId(fetch(NEXT_URL, mapOf("videoId" to videoId)))?.let { fetch(BROWSE_URL, mapOf("browseId" to it)) }
        val found = page?.let(::parseLyricsPage)
        return CachedLyrics(synced = null, plain = found?.text, fetchedAtMs = now, licensor = found?.licensor)
    }

    // A cache that cannot be written (disk full) must not hide the lyrics; the song is just asked again next time.
    private suspend fun remember(trackId: String, answer: CachedLyrics) {
        try {
            cache.put(trackId, answer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
    }
}

internal data class MusicLyrics(val text: String, val licensor: String?)

/** The browse id of the watch page's Lyrics tab; null when the tab cannot be opened. A page with no tabs at all is not a watch page. */
internal fun lyricsBrowseId(next: JsonObject): String? {
    val tabs = next.getObject("contents").getObject("singleColumnMusicWatchNextResultsRenderer").getObject("tabbedRenderer")
        .getObject("watchNextTabbedResultsRenderer").getArray("tabs").filterIsInstance<JsonObject>()
    if (tabs.isEmpty()) throw IOException("YouTube Music sent a watch page without its tabs")
    return tabs.map { it.getObject("tabRenderer").getObject("endpoint").getObject("browseEndpoint") }
        .firstOrNull { it.getObject("browseEndpointContextSupportedConfigs").getObject("browseEndpointContextMusicConfig").getString("pageType") == LYRICS_PAGE }
        ?.getString("browseId")
}

/** The lyrics on a Lyrics tab's page and their licensor ("Source: Musixmatch", in the request's language); null for "Lyrics not available". */
internal fun parseLyricsPage(page: JsonObject): MusicLyrics? {
    val shelf = page.getObject("contents").getObject("sectionListRenderer").getArray("contents").filterIsInstance<JsonObject>()
        .map { it.getObject("musicDescriptionShelfRenderer") }.firstOrNull { it.isNotEmpty() } ?: return null
    val text = shelf.getObject("description").runsText().takeIf { it.isNotBlank() } ?: return null
    val footer = shelf.getObject("footer").runsText()
    return MusicLyrics(text, footer.substringAfter(':').trim().takeIf { it.isNotEmpty() })
}

private fun JsonObject.runsText(): String = getArray("runs").filterIsInstance<JsonObject>().joinToString("") { it.getString("text").orEmpty() }
