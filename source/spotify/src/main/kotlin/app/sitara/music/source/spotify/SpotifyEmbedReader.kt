package app.sitara.music.source.spotify

import app.sitara.music.core.model.SPOTIFY_PUBLIC_LIMIT
import app.sitara.music.core.model.SourceSong
import app.sitara.music.core.model.SourceSongList
import app.sitara.music.core.model.SpotifyLink
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Spotify's answer about a list it will not show: not found or private ([NOT_FOUND]), or a page Sitara cannot read ([UNREADABLE]). */
class SpotifyException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NOT_FOUND, UNREADABLE }
}

/** Reads public Spotify lists with a client with sensible timeouts. */
fun spotifyEmbedReader(): SpotifyEmbedReader = SpotifyEmbedReader(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build())

/**
 * The songs of a public Spotify playlist or album, read from the page Spotify serves to embed it in other sites
 * (`open.spotify.com/embed/...`), which needs no account or key. That page lists at most [EMBED_LIMIT] songs, so a
 * longer playlist comes back [SourceSongList.truncated]. Throws [IOException] when Spotify cannot be reached and
 * [SpotifyException] when the list is missing, private, or the page no longer has the expected shape.
 */
class SpotifyEmbedReader(private val client: OkHttpClient, private val baseUrl: HttpUrl = "https://open.spotify.com/".toHttpUrl()) {
    suspend fun read(link: SpotifyLink): SourceSongList {
        val kind = if (link.kind == SpotifyLink.Kind.PLAYLIST) "playlist" else "album"
        val url = baseUrl.newBuilder().addPathSegments("embed/$kind/${link.id}").build()
        val page = runInterruptible(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
                when {
                    response.code == 404 -> throw SpotifyException(SpotifyException.Reason.NOT_FOUND)
                    !response.isSuccessful -> throw IOException("Spotify answered ${response.code}")
                    else -> response.body.string()
                }
            }
        }
        return parseEmbedPage(page)
    }
}

/** The list in an embed page's `__NEXT_DATA__` JSON. */
internal fun parseEmbedPage(page: String): SourceSongList {
    val json = NEXT_DATA.find(page)?.groupValues?.get(1) ?: throw SpotifyException(SpotifyException.Reason.UNREADABLE)
    val props = try {
        JsonParser.`object`().from(json).getObject("props")?.getObject("pageProps")
    } catch (e: JsonParserException) {
        throw SpotifyException(SpotifyException.Reason.UNREADABLE, e)
    } ?: throw SpotifyException(SpotifyException.Reason.UNREADABLE)
    if (props.getInt("status", 200) == 404) throw SpotifyException(SpotifyException.Reason.NOT_FOUND)
    // nanojson answers a missing key with an empty object, so the list's presence is checked by name.
    val entity = props.getObject("state")?.getObject("data")?.getObject("entity")
    val tracks = entity?.takeIf { it.containsKey("trackList") }?.getArray("trackList") ?: throw SpotifyException(SpotifyException.Reason.UNREADABLE)
    val album = if (entity.getString("type") == "album") entity.getString("name").orEmpty() else ""
    val songs = tracks.filterIsInstance<JsonObject>().mapNotNull { track ->
        val title = track.getString("title")?.trim().orEmpty()
        if (title.isEmpty()) return@mapNotNull null
        // Artists come joined by a comma and a no-break space; the importer searches by the first.
        val artists = track.getString("subtitle").orEmpty().split(',').map { it.replace(' ', ' ').trim() }.filter(String::isNotEmpty)
        SourceSong(title = title, artist = artists.joinToString(", "), album = album, durationMs = track.getLong("duration").takeIf { it > 0 })
    }
    return SourceSongList(entity.getString("name").orEmpty(), songs, truncated = tracks.size >= EMBED_LIMIT)
}

/** The most songs Spotify's embed page lists. */
const val EMBED_LIMIT = SPOTIFY_PUBLIC_LIMIT

private val NEXT_DATA = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

// A browser's name: the embed page is served to browsers.
private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
