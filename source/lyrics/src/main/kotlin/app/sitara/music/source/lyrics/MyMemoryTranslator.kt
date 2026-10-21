package app.sitara.music.source.lyrics

import app.sitara.music.core.model.LyricsTranslation
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

// MyMemory takes at most 500 bytes of text per request.
private const val MAX_QUERY_BYTES = 500

/** Why a translation failed, as the listener is told. */
enum class TranslationProblem { OFFLINE, DAILY_LIMIT, SAME_LANGUAGE, SERVICE }

class TranslationException(val problem: TranslationProblem, message: String, cause: Throwable? = null) : IOException(message, cause)

/** Where translations are kept, by song, lyrics source and language. */
interface TranslationCache {
    suspend fun get(trackId: String, providerId: String, language: String): LyricsTranslation?

    suspend fun put(trackId: String, providerId: String, translation: LyricsTranslation)
}

/** Translations from MyMemory over a client with sensible timeouts. */
fun myMemoryTranslator(cache: TranslationCache): MyMemoryTranslator =
    MyMemoryTranslator(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(), cache)

/**
 * Lyrics translated by MyMemory's public API (https://mymemory.translated.net, free without a key: 5,000 characters a
 * day per address). Each distinct non-blank line is sent once, lines joined in requests of at most 500 bytes, the source
 * language detected; lines MyMemory merges are asked for one by one. Lines already in the target language stay as they
 * were, and lyrics wholly in it are [TranslationProblem.SAME_LANGUAGE]. Translations are kept per song, source and
 * language; a failure throws [TranslationException] and keeps nothing.
 */
class MyMemoryTranslator(
    private val client: OkHttpClient,
    private val cache: TranslationCache,
    private val baseUrl: HttpUrl = "https://api.mymemory.translated.net/".toHttpUrl(),
) {
    suspend fun translate(trackId: String, providerId: String, lines: List<String>, language: String): LyricsTranslation {
        cache.get(trackId, providerId, language)?.let { return it }
        val distinct = lines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val translated = mutableMapOf<String, String>()
        var detected: String? = null
        var sameLanguage = 0
        val chunks = chunked(distinct)
        for (chunk in chunks) {
            val answer = ask(chunk.joinToString("\n"), language)
            if (answer == null) {
                sameLanguage++
                continue
            }
            detected = detected ?: answer.detected
            val parts = answer.text.split("\n")
            if (parts.size == chunk.size) {
                chunk.zip(parts).forEach { (line, part) -> translated[line] = part.trim() }
            } else {
                chunk.forEach { line -> translated[line] = ask(line, language)?.text?.replace('\n', ' ')?.trim().orEmpty() }
            }
        }
        if (chunks.isNotEmpty() && sameLanguage == chunks.size) throw TranslationException(TranslationProblem.SAME_LANGUAGE, "The lyrics are already in $language")
        val translation = LyricsTranslation(lines.map { translated[it.trim()].orEmpty() }, detected, language)
        cache.put(trackId, providerId, translation)
        return translation
    }

    private class Answer(val text: String, val detected: String?)

    /** MyMemory's translation of [text] into [language]; null when the text is already in it. */
    private suspend fun ask(text: String, language: String): Answer? {
        val url = baseUrl.newBuilder().addPathSegment("get").addQueryParameter("q", text).addQueryParameter("langpair", "autodetect|$language").build()
        val request = Request.Builder().url(url).header("User-Agent", LRCLIB_USER_AGENT).build()
        val (code, body) = try {
            runInterruptible(Dispatchers.IO) { client.newCall(request).execute().use { it.code to it.body.string() } }
        } catch (e: UnknownHostException) {
            throw TranslationException(TranslationProblem.OFFLINE, "No connection to MyMemory", e)
        } catch (e: ConnectException) {
            throw TranslationException(TranslationProblem.OFFLINE, "No connection to MyMemory", e)
        }
        if (code == 429) throw TranslationException(TranslationProblem.DAILY_LIMIT, "MyMemory's free daily limit is used up")
        if (code !in 200..299) throw TranslationException(TranslationProblem.SERVICE, "MyMemory answered $code")
        val json = try {
            JsonParser.`object`().from(body)
        } catch (e: JsonParserException) {
            throw TranslationException(TranslationProblem.SERVICE, "MyMemory sent an answer that is not JSON", e)
        }
        // responseStatus is sometimes a number, sometimes a string.
        val status = json.get("responseStatus")?.toString()?.toDoubleOrNull()?.toInt()
        val data = json.getObject("responseData")
        val translated = data.getString("translatedText").orEmpty()
        return when {
            json.getBoolean("quotaFinished") || status == 429 || translated.startsWith("MYMEMORY WARNING") ->
                throw TranslationException(TranslationProblem.DAILY_LIMIT, "MyMemory's free daily limit is used up")
            status == 403 && "DISTINCT LANGUAGES" in translated.uppercase() -> null
            status != 200 -> throw TranslationException(TranslationProblem.SERVICE, "MyMemory refused: ${json.getString("responseDetails").orEmpty()}")
            else -> Answer(translated, data.getString("detectedLanguage")?.substringBefore('-')?.lowercase())
        }
    }
}

/** [lines] in order, packed into groups whose newline-joined text stays within MyMemory's limit. */
// shortcut: a single line over 500 bytes goes alone and MyMemory refuses it (a service problem); split such lines at word breaks if real lyrics have them.
private fun chunked(lines: List<String>): List<List<String>> {
    val chunks = mutableListOf<MutableList<String>>()
    var bytes = 0
    for (line in lines) {
        val size = line.toByteArray().size
        val current = chunks.lastOrNull()
        if (current == null || bytes + 1 + size > MAX_QUERY_BYTES) {
            chunks += mutableListOf(line)
            bytes = size
        } else {
            current += line
            bytes += 1 + size
        }
    }
    return chunks
}
