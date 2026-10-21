package app.sitara.music.source.scrobble

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Scrobble
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.SendOutcome
import kotlinx.coroutines.test.runTest
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A fake Last.fm behind an interceptor, with made-up keys: these tests never reach the network or a real account. */
class LastFmClientTest {
    private val server = FakeServer()
    private val baseUrl = "https://lastfm.test/2.0/".toHttpUrl()
    private val keys = LastFmKeys(apiKey = "k-1", secret = "s-1")
    private val auth = LastFmAuth(keys, server.client, baseUrl)
    private val client = LastFmClient(keys, sessionKey = "sk-1", server.client, baseUrl)

    private val play = Scrobble("x7kGuhlXmFI", "Paper Lanterns", "Orchid Lane", "Harbour Lights", 214_000, startedAtMs = 1_700_000_000_500)

    private fun Request.form(): Map<String, String> = (body as FormBody).let { form -> (0 until form.size).associate { form.name(it) to form.value(it) } }

    @Test
    fun `a call is signed with the parameters in name order and the shared secret, leaving out the format`() {
        // md5("api_keyk-1methodauth.getSessiontokent-1s-1")
        assertEquals("046f4d57642ba5fa3908fa946de9c070", lastFmSignature(mapOf("token" to "t-1", "method" to "auth.getSession", "api_key" to "k-1", "format" to "json"), "s-1"))
        // Non-ASCII names are signed as UTF-8.
        assertEquals(
            "cf4d72174dd30ce0d0dfbe7ae480cb0e",
            lastFmSignature(mapOf("method" to "track.updateNowPlaying", "artist" to "Sigur Rós", "track" to "Hoppípolla", "api_key" to "k-1", "sk" to "s-k-1"), "s-1"),
        )
    }

    @Test
    fun `signing in asks for a token and sends the listener to Last.fm with it`() = runTest {
        server.answer(200, """{"token":"t-1"}""")

        assertEquals("t-1", auth.requestToken())
        assertEquals("https://www.last.fm/api/auth/?api_key=k-1&token=t-1", auth.browserUrl("t-1"))

        val form = server.requests.single().form()
        assertEquals("auth.getToken", form["method"])
        assertEquals(lastFmSignature(form - "api_sig", "s-1"), form["api_sig"])
    }

    @Test
    fun `no token comes back when Last.fm cannot be reached`() = runTest {
        server.fail()
        assertNull(auth.requestToken())
    }

    @Test
    fun `an allowed token becomes a session as the listener`() = runTest {
        server.answer(200, """{"session":{"name":"orchid","key":"sk-9","subscriber":0}}""")

        assertEquals(SignIn.Done("orchid", "sk-9"), auth.session("t-1"))

        val form = server.requests.single().form()
        assertEquals("auth.getSession", form["method"])
        assertEquals("t-1", form["token"])
        assertEquals("046f4d57642ba5fa3908fa946de9c070", form["api_sig"])
    }

    @Test
    fun `a token not allowed yet waits, an expired or refused one is refused`() = runTest {
        server.answer(403, """{"error":14,"message":"This token has not been authorized"}""")
        assertEquals(SignIn.Waiting, auth.session("t-1"))

        server.answer(403, """{"error":15,"message":"This token has expired"}""")
        assertEquals(SignIn.Refused, auth.session("t-1"))

        server.fail()
        assertEquals(SignIn.Unreachable, auth.session("t-1"))
    }

    @Test
    fun `plays are scrobbled together, numbered, in seconds, signed with the session`() = runTest {
        server.answer(200, """{"scrobbles":{"@attr":{"accepted":2,"ignored":0}}}""")

        assertEquals(SendOutcome.SENT, client.submit(listOf(play, play.copy(album = "", startedAtMs = 1_700_000_300_000))))

        val form = server.requests.single().form()
        assertEquals("track.scrobble", form["method"])
        assertEquals("sk-1", form["sk"])
        assertEquals("json", form["format"])
        assertEquals("Orchid Lane", form["artist[0]"])
        assertEquals("Paper Lanterns", form["track[0]"])
        assertEquals("Harbour Lights", form["album[0]"])
        assertEquals("1700000000", form["timestamp[0]"])
        assertEquals("214", form["duration[0]"])
        assertEquals("1700000300", form["timestamp[1]"])
        assertFalse("album[1]" in form, "an empty album is left out")
        assertEquals(lastFmSignature(form - "api_sig", "s-1"), form["api_sig"])
    }

    @Test
    fun `now playing names the song and its length`() = runTest {
        server.answer(200, """{"nowplaying":{}}""")

        assertEquals(SendOutcome.SENT, client.nowPlaying(Track("x7kGuhlXmFI", "Paper Lanterns", "Orchid Lane", "", 214_000, Artwork(listOf(0)))))

        val form = server.requests.single().form()
        assertEquals("track.updateNowPlaying", form["method"])
        assertEquals("Paper Lanterns", form["track"])
        assertEquals("214", form["duration"])
        assertFalse("album" in form)
    }

    @Test
    fun `Last_fm errors become outcomes`() = runTest {
        val outcomes = listOf(
            9 to SendOutcome.SIGN_IN_AGAIN, // invalid session: sign in again
            29 to SendOutcome.TRY_LATER, // rate limit: back off
            11 to SendOutcome.TRY_LATER, // service offline
            16 to SendOutcome.TRY_LATER, // temporarily unavailable
            6 to SendOutcome.REFUSED, // invalid parameters
        )
        for ((error, outcome) in outcomes) {
            server.answer(403, """{"error":$error,"message":"x"}""")
            assertEquals(outcome, client.submit(listOf(play)), "error $error")
        }
        server.answer(503, "<html>Service Unavailable</html>")
        assertEquals(SendOutcome.TRY_LATER, client.submit(listOf(play)), "a server error page")
        server.fail()
        assertEquals(SendOutcome.TRY_LATER, client.submit(listOf(play)), "no connection")
    }
}
