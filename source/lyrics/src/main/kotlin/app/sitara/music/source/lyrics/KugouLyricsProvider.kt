package app.sitara.music.source.lyrics

import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.LyricsSong
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.LyricsProvider
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Lyrics from KuGou over a client with sensible timeouts. */
fun kugouLyrics(cache: LyricsCache): LyricsProvider =
    KugouLyricsProvider(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(), cache)

/**
 * Synced lyrics from KuGou's public lyrics service (lyrics.kugou.com, no key), which knows many songs LRCLIB does not,
 * Asian releases above all. A search by "artist - title" and length gives candidates; the first of the same length
 * (within [KUGOU_DURATION_TOLERANCE_MS]) whose title matches is downloaded as LRC. The credit lines KuGou puts before
 * the first sung line ("Lyrics by", "词：", the title repeated) are dropped. Cached like LRCLIB: found lyrics for good,
 * "nothing found" for a week; a call that fails throws [IOException] and is not cached.
 */
class KugouLyricsProvider(
    private val client: OkHttpClient,
    private val cache: LyricsCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val baseUrl: HttpUrl = "https://lyrics.kugou.com/".toHttpUrl(),
) : LyricsProvider {
    override val id: String = "kugou"
    override val name: String = "KuGou"

    override suspend fun lyrics(track: Track): Lyrics? {
        val now = clock()
        val answer = cache.get(track.id)?.takeIf { it.synced != null || it.plain != null || now - it.fetchedAtMs < NOT_FOUND_RETRY_MS }
            ?: lookUp(track, now).also { remember(track.id, it) }
        val synced = answer.synced
        val plain = answer.plain
        val lyrics = when {
            synced != null -> LrcParser.parse(synced, track.durationMs.takeIf { it > 0 })
            plain != null -> LrcParser.parse(plain)
            else -> return null
        }
        return lyrics.takeIf { it.lines.isNotEmpty() }?.copy(source = name, matched = answer.matched)
    }

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
        val body = get("search") {
            addQueryParameter("ver", "1")
            addQueryParameter("man", "yes")
            addQueryParameter("client", "pc")
            addQueryParameter("keyword", if (query.artist.isBlank()) query.title else "${query.artist} - ${query.title}")
            if (track.durationMs > 0) addQueryParameter("duration", track.durationMs.toString())
        }
        val candidates = parse(body).getArray("candidates")?.filterIsInstance<JsonObject>().orEmpty()
        val pick = candidates.firstOrNull { matches(it, query.title, query.artist, track.durationMs) } ?: return CachedLyrics(null, null, now)
        val download = parse(
            get("download") {
                addQueryParameter("ver", "1")
                addQueryParameter("client", "pc")
                addQueryParameter("fmt", "lrc")
                addQueryParameter("charset", "utf8")
                addQueryParameter("id", pick.getString("id").orEmpty())
                addQueryParameter("accesskey", pick.getString("accesskey").orEmpty())
            },
        )
        val text = download.getString("content")?.let { content ->
            runCatching { String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8) }.getOrNull()
        }?.let { withoutCredits(it, pick.getString("song").orEmpty(), pick.getString("singer").orEmpty()) }
            ?: return CachedLyrics(null, null, now)
        val matched = LyricsSong(pick.getString("song").orEmpty(), pick.getString("singer").orEmpty(), pick.getLong("duration"))
        val synced = TIMESTAMP.containsMatchIn(text)
        return CachedLyrics(text.takeIf { synced }, text.takeIf { !synced }, now, matched)
    }

    private fun parse(body: String): JsonObject = try {
        JsonParser.`object`().from(body)
    } catch (e: JsonParserException) {
        throw IOException("KuGou answered something other than JSON", e)
    }

    private suspend fun get(path: String, query: HttpUrl.Builder.() -> Unit): String {
        val url = baseUrl.newBuilder().addPathSegments(path).apply(query).build()
        val request = Request.Builder().url(url).header("User-Agent", LRCLIB_USER_AGENT).build()
        return runInterruptible(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("KuGou answered ${response.code} for ${url.encodedPath}")
                response.body.string()
            }
        }
    }
}

private const val NOT_FOUND_RETRY_MS = 7 * 24 * 60 * 60 * 1_000L

/** KuGou's lengths are rounded more loosely than LRCLIB's. */
internal const val KUGOU_DURATION_TOLERANCE_MS = 3_000L

private val TIMESTAMP = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")

/**
 * A candidate of the same length (when both are known) by the same artist (when the song names one) whose title is the
 * song's: equal once case, spaces, punctuation and bracketed notes are set aside, or one holding the other with at
 * least [TITLE_SHARE] of its letters, so "Love" is not taken for "Love Story".
 */
internal fun matches(candidate: JsonObject, title: String, artist: String, durationMs: Long): Boolean {
    val length = candidate.getLong("duration")
    if (durationMs > 0 && length > 0 && abs(length - durationMs) > KUGOU_DURATION_TOLERANCE_MS) return false
    val wanted = title.withoutNotes().normalised()
    val found = candidate.getString("song").orEmpty().withoutNotes().normalised()
    if (wanted.isEmpty() || found.isEmpty()) return false
    val shorter = minOf(wanted.length, found.length).toDouble()
    val titleMatches = wanted == found || ((found.contains(wanted) || wanted.contains(found)) && shorter / maxOf(wanted.length, found.length) >= TITLE_SHARE)
    return titleMatches && sameArtist(candidate.getString("singer").orEmpty(), artist)
}

/** Whether a credit names one of [artist]'s artists; any credit passes when the song names none. */
private fun sameArtist(credit: String, artist: String): Boolean {
    val wanted = artist.artistNames()
    if (wanted.isEmpty()) return true
    val found = credit.artistNames()
    return found.any { name -> wanted.any { it == name || it.contains(name) || name.contains(it) } }
}

private fun String.artistNames(): List<String> = split(ARTIST_SEPARATORS).map { it.normalised() }.filter { it.isNotEmpty() }

private fun String.withoutNotes(): String = replace(NOTES, " ")

private const val TITLE_SHARE = 0.75
private val NOTES = Regex("""\([^)]*\)|\[[^\]]*]|（[^）]*）""")
private val ARTIST_SEPARATORS = Regex("""\s*(?:,|、|/|&|;|\bfeat\.?|\bft\.?|\band\b|\bx\b)\s*""", RegexOption.IGNORE_CASE)

private fun String.normalised() = lowercase().filter { it.isLetterOrDigit() }

// Credits KuGou writes as lyric lines: "Lyrics by：…", "词：…", "Composed by: …", "制作人：…".
private val CREDIT = Regex(
    """^\s*(?:lyrics?|lyricist|words|music|composed|composer|written|writer|arranged|arranger|produced|producer|mixed|mastered|recorded|vocals?|""" +
        """作词|作曲|词|曲|编曲|制作人|制作|混音|母带|录音|和声|监制|出品|发行)(?:\s*by)?\s*[:：]""",
    RegexOption.IGNORE_CASE,
)

/**
 * The lyrics without the credit lines before the first sung line: lines that name a credit, or repeat the title and
 * artist ("Yellow - Coldplay (酷玩乐队)"). Header tags (`[ti:]`) and everything after the first sung line stay.
 */
internal fun withoutCredits(lrc: String, title: String, artist: String): String {
    val lines = lrc.lines()
    val result = mutableListOf<String>()
    var sung = false
    val titleKey = title.normalised()
    val artistKey = artist.normalised()
    for (line in lines) {
        val text = line.replace(TIMESTAMP, "").trim()
        if (!sung && TIMESTAMP.containsMatchIn(line)) {
            val key = text.normalised()
            val repeatsSong = titleKey.isNotEmpty() && key.contains(titleKey) && artistKey.isNotEmpty() && key.contains(artistKey) && " - " in text
            if (text.isEmpty() || CREDIT.containsMatchIn(text) || repeatsSong) continue
            sung = true
        }
        result += line
    }
    return result.joinToString("\n").trim()
}
