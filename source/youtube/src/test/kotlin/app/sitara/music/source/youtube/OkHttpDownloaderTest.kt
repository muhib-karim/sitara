package app.sitara.music.source.youtube

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** An interceptor answers every call, so these tests never touch the network. */
class OkHttpDownloaderTest {
    private var sent: okhttp3.Request? = null

    private fun downloader(code: Int = 200, body: String = "") = OkHttpDownloader(
        OkHttpClient.Builder().addInterceptor { chain ->
            sent = chain.request()
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("status $code")
                .header("Content-Type", "application/json; charset=utf-8")
                .body(body.toResponseBody())
                .build()
        }.build(),
    )

    @Test
    fun `GET keeps every header value and adds a browser user agent`() {
        val headers = mapOf("Accept-Language" to listOf("en-GB"), "X-Goog-Visitor-Id" to listOf("a", "b"))

        downloader().execute(Request("GET", "https://music.youtube.com/search", headers, null, null, false))

        val request = sent!!
        assertEquals("GET", request.method)
        assertEquals("https://music.youtube.com/search", request.url.toString())
        assertEquals(listOf("en-GB"), request.headers("Accept-Language"))
        assertEquals(listOf("a", "b"), request.headers("X-Goog-Visitor-Id"))
        assertEquals(BROWSER_USER_AGENT, request.header("User-Agent"))
    }

    @Test
    fun `an explicit user agent is not replaced`() {
        downloader().execute(Request("GET", "https://www.youtube.com/", mapOf("User-Agent" to listOf("com.google.android.youtube/20")), null, null, false))

        assertEquals(listOf("com.google.android.youtube/20"), sent!!.headers("User-Agent"))
    }

    @Test
    fun `POST sends the payload bytes unchanged`() {
        val payload = """{"context":{}}""".toByteArray()

        downloader().execute(Request("POST", "https://www.youtube.com/youtubei/v1/player", mapOf("Content-Type" to listOf("application/json")), payload, null, false))

        val request = sent!!
        assertEquals("POST", request.method)
        assertEquals("application/json", request.header("Content-Type"))
        assertArrayEquals(payload, Buffer().also { request.body!!.writeTo(it) }.readByteArray())
    }

    @Test
    fun `POST without a payload sends an empty body`() {
        downloader().execute(Request("POST", "https://www.youtube.com/youtubei/v1/next", emptyMap(), null, null, false))

        assertEquals(0L, sent!!.body!!.contentLength())
    }

    @Test
    fun `response keeps status, body, headers and final url`() {
        val response = downloader(code = 404, body = """{"error":true}""")
            .execute(Request("GET", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", emptyMap(), null, null, false))

        assertEquals(404, response.responseCode())
        assertEquals("""{"error":true}""", response.responseBody())
        assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", response.latestUrl())
    }

    @Test
    fun `rate limiting becomes the extractor's captcha error`() {
        val error = assertThrows<ReCaptchaException> {
            downloader(code = 429).execute(Request("GET", "https://www.youtube.com/results", emptyMap(), null, null, false))
        }

        assertEquals("https://www.youtube.com/results", error.url)
    }
}
