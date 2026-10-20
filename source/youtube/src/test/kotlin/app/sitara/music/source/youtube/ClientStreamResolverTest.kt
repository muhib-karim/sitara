package app.sitara.music.source.youtube

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClientStreamResolverTest {
    private val track = Track("gsZauX8gwxo", "expectations", "Artist", "", 200_000, Artwork(listOf(0)))

    private fun response(json: String): JsonObject = JsonParser.`object`().from(json)

    private fun resolver(json: String) = ClientStreamResolver("youtube-test") { response(json) }

    private fun format(itag: Int, mime: String, bitrate: Int, url: String? = "https://rr1.googlevideo.com/videoplayback?itag=$itag&expire=1760000000", extra: String = "") =
        """{"itag":$itag,"mimeType":"$mime","averageBitrate":$bitrate${url?.let { ",\"url\":\"$it\"" } ?: ",\"signatureCipher\":\"s=x\""}$extra}"""

    private fun ok(vararg formats: String) =
        """{"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[${formats.joinToString(",")}]}}"""

    @Test
    fun `picks the highest-bitrate audio format with a direct url`() = runBlocking {
        val json = ok(
            format(18, "video/mp4; codecs=\\\"avc1.42001E\\\"", 500_000),
            format(140, "audio/mp4; codecs=\\\"mp4a.40.2\\\"", 129_000),
            format(251, "audio/webm; codecs=\\\"opus\\\"", 141_000),
            format(250, "audio/webm; codecs=\\\"opus\\\"", 70_000),
        )

        val stream = resolver(json).resolve(track)

        assertEquals("https://rr1.googlevideo.com/videoplayback?itag=251&expire=1760000000", stream.url)
        assertEquals("audio/webm", stream.mimeType)
        assertEquals(141, stream.bitrateKbps)
        assertEquals(1_760_000_000_000L, stream.expiresAtMs)
    }

    @Test
    fun `skips formats that need a deciphered signature`() = runBlocking {
        val json = ok(format(251, "audio/webm; codecs=\\\"opus\\\"", 141_000, url = null), format(140, "audio/mp4; codecs=\\\"mp4a.40.2\\\"", 129_000))

        assertEquals("audio/mp4", resolver(json).resolve(track).mimeType)
    }

    @Test
    fun `prefers the original audio track over a dubbed one`() = runBlocking {
        val json = ok(
            format(251, "audio/webm; codecs=\\\"opus\\\"", 160_000, url = "https://a.example/dubbed", extra = ",\"audioTrack\":{\"audioIsDefault\":false}"),
            format(250, "audio/webm; codecs=\\\"opus\\\"", 120_000, url = "https://a.example/original", extra = ",\"audioTrack\":{\"audioIsDefault\":true}"),
        )

        assertEquals("https://a.example/original", resolver(json).resolve(track).url)
    }

    @Test
    fun `opus wins a bitrate tie with m4a`() = runBlocking {
        val json = ok(format(140, "audio/mp4; codecs=\\\"mp4a.40.2\\\"", 128_000, url = "https://a.example/m4a"), format(251, "audio/webm; codecs=\\\"opus\\\"", 128_000, url = "https://a.example/opus"))

        assertEquals("https://a.example/opus", resolver(json).resolve(track).url)
    }

    @Test
    fun `a refused player response fails with its status and reason`() {
        val json = """{"playabilityStatus":{"status":"UNPLAYABLE","reason":"This video is not available"}}"""

        val e = assertThrows<IOException> { runBlocking { resolver(json).resolve(track) } }

        assertTrue("UNPLAYABLE" in e.message!! && "This video is not available" in e.message!!, e.message)
    }

    @Test
    fun `a response without a playable audio format fails`() {
        val json = ok(format(18, "video/mp4; codecs=\\\"avc1.42001E\\\"", 500_000), format(251, "audio/webm; codecs=\\\"opus\\\"", 141_000, url = "http://insecure.example/a"))

        assertThrows<IOException> { runBlocking { resolver(json).resolve(track) } }
    }

    @Test
    fun `rejects ids that are not YouTube video ids without a request`() {
        var asked = false
        val resolver = ClientStreamResolver("youtube-test") { asked = true; response(ok()) }

        assertThrows<IllegalArgumentException> { runBlocking { resolver.resolve(track.copy(id = "sample:1")) } }
        assertEquals(false, asked)
    }
}
