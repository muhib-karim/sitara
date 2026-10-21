package app.sitara.music.source.lyrics

import app.sitara.music.core.model.LyricsTranslation
import com.grack.nanojson.JsonWriter
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** An interceptor plays MyMemory, by default "translating" by upper-casing each line, so these tests never touch the network. */
class MyMemoryTranslatorTest {
    private val sent = mutableListOf<Request>()
    private var answer: (q: String) -> Pair<Int, String> = { q -> 200 to reply(q.uppercase(), detected = "es") }
    private var offline = false
    private val cache = object : TranslationCache {
        val entries = mutableMapOf<Triple<String, String, String>, LyricsTranslation>()

        override suspend fun get(trackId: String, providerId: String, language: String) = entries[Triple(trackId, providerId, language)]

        override suspend fun put(trackId: String, providerId: String, translation: LyricsTranslation) {
            entries[Triple(trackId, providerId, translation.toLanguage)] = translation
        }
    }
    private val interceptor = Interceptor { chain ->
        sent += chain.request()
        if (offline) throw UnknownHostException("api.mymemory.translated.net")
        val (code, body) = answer(chain.request().url.queryParameter("q").orEmpty())
        okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("status $code").body(body.toResponseBody()).build()
    }
    private val translator = MyMemoryTranslator(OkHttpClient.Builder().addInterceptor(interceptor).build(), cache)

    private fun reply(text: String, detected: String? = null, status: Any = 200, quotaFinished: Boolean = false, details: String = ""): String {
        val data = JsonWriter.string().`object`().`object`("responseData").value("translatedText", text)
        if (detected != null) data.value("detectedLanguage", detected)
        return data.end().value("responseStatus", status).value("quotaFinished", quotaFinished).value("responseDetails", details).end().done()
    }

    private suspend fun problemOf(lines: List<String>): TranslationProblem? =
        (runCatching { translator.translate("t1", "lrclib", lines, "en") }.exceptionOrNull() as? TranslationException)?.problem

    @Test
    fun `sends each distinct line once, from a detected language, and gives every line its translation`() = runTest {
        val translation = translator.translate("t1", "lrclib", listOf("Hola", "", "Adiós", "Hola"), "en")

        val request = sent.single()
        assertEquals("api.mymemory.translated.net", request.url.host)
        assertEquals("Hola\nAdiós", request.url.queryParameter("q"))
        assertEquals("autodetect|en", request.url.queryParameter("langpair"))
        assertEquals("Sitara (https://github.com/muhib-karim/sitara)", request.header("User-Agent"))
        assertEquals(LyricsTranslation(listOf("HOLA", "", "ADIÓS", "HOLA"), fromLanguage = "es", toLanguage = "en"), translation)
    }

    @Test
    fun `long lyrics go in requests of at most 500 bytes`() = runTest {
        val lines = List(40) { "Línea número $it de una canción larga" }

        val translation = translator.translate("t1", "lrclib", lines, "en")

        assertTrue(sent.size > 1)
        assertTrue(sent.all { it.url.queryParameter("q")!!.toByteArray().size <= 500 }, "every request within MyMemory's limit")
        assertEquals(lines.map { it.uppercase() }, translation.lines)
    }

    @Test
    fun `lines MyMemory merged are asked for one by one`() = runTest {
        answer = { q -> 200 to reply(if ('\n' in q) "ONE LINE ONLY" else q.uppercase(), detected = "es") }

        val translation = translator.translate("t1", "lrclib", listOf("Hola", "Adiós"), "en")

        assertEquals(listOf("Hola\nAdiós", "Hola", "Adiós"), sent.map { it.url.queryParameter("q") })
        assertEquals(listOf("HOLA", "ADIÓS"), translation.lines)
    }

    @Test
    fun `a translation is kept per song, source and language`() = runTest {
        val first = translator.translate("t1", "lrclib", listOf("Hola"), "en")
        sent.clear()

        assertEquals(first, translator.translate("t1", "lrclib", listOf("Hola"), "en"))
        assertTrue(sent.isEmpty())
        translator.translate("t1", "lrclib", listOf("Hola"), "bn")
        translator.translate("t1", "youtube-music", listOf("Hola"), "en")
        assertEquals(2, sent.size, "another language or source is translated anew")
    }

    @Test
    fun `the free daily limit is its own problem, and nothing is kept`() = runTest {
        answer = { 200 to reply("MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY", status = 429, quotaFinished = true) }

        assertEquals(TranslationProblem.DAILY_LIMIT, problemOf(listOf("Hola")))
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `lyrics already in the language are their own problem`() = runTest {
        answer = { 200 to reply("PLEASE SELECT TWO DISTINCT LANGUAGES", status = "403", details = "PLEASE SELECT TWO DISTINCT LANGUAGES") }

        assertEquals(TranslationProblem.SAME_LANGUAGE, problemOf(listOf("Hello")))
    }

    @Test
    fun `lines already in the language stay untranslated when others are not`() = runTest {
        // 100-byte lines, four to a request: two requests of the English verse, one of the Spanish chorus.
        val lines = List(8) { "Line $it ".padEnd(100, 'a') } + List(4) { "Línea $it ".padEnd(99, 'o') }
        answer = { q -> 200 to if (q.startsWith("Line")) reply("PLEASE SELECT TWO DISTINCT LANGUAGES", status = "403") else reply(q.uppercase(), detected = "es") }

        val translation = translator.translate("t1", "lrclib", lines, "en")

        assertEquals(3, sent.size)
        assertEquals(List(8) { "" } + lines.drop(8).map { it.uppercase() }, translation.lines)
        assertEquals("es", translation.fromLanguage)
    }

    @Test
    fun `no connection is the offline problem`() = runTest {
        offline = true

        assertEquals(TranslationProblem.OFFLINE, problemOf(listOf("Hola")))
    }

    @Test
    fun `a server error is a service problem, and nothing is kept`() = runTest {
        answer = { 503 to "Service Unavailable" }

        assertEquals(TranslationProblem.SERVICE, problemOf(listOf("Hola")))
        assertTrue(cache.entries.isEmpty())
    }
}
