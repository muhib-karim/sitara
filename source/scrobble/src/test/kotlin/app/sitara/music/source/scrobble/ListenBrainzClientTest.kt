package app.sitara.music.source.scrobble

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Scrobble
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.localTrackId
import app.sitara.music.source.api.SendOutcome
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** A fake ListenBrainz behind an interceptor: these tests never reach the network or a real account. */
class ListenBrainzClientTest {
    private val server = FakeServer()
    private val baseUrl = "https://listenbrainz.test/".toHttpUrl()
    private val client = ListenBrainzClient("token-1", server.client, baseUrl)

    private val play = Scrobble("x7kGuhlXmFI", "Paper Lanterns", "Orchid Lane", "Harbour Lights", 214_000, startedAtMs = 1_700_000_000_500)
    private val track = Track("x7kGuhlXmFI", "Paper Lanterns", "Orchid Lane", "", 214_000, Artwork(listOf(0)))

    private fun Request.json(): JsonObject = JsonParser.`object`().from(Buffer().also { body!!.writeTo(it) }.readUtf8())

    @Test
    fun `a valid token signs in as its user`() = runTest {
        server.answer(200, """{"code":200,"message":"Token valid.","valid":true,"user_name":"orchid"}""")

        assertEquals(SignIn.Done("orchid", "token-1"), checkListenBrainzToken("token-1", server.client, baseUrl))

        val sent = server.requests.single()
        assertEquals("/1/validate-token", sent.url.encodedPath)
        assertEquals("Token token-1", sent.header("Authorization"))
    }

    @Test
    fun `an invalid token is refused, however ListenBrainz says it`() = runTest {
        server.answer(200, """{"code":200,"message":"Token invalid.","valid":false}""")
        assertEquals(SignIn.Refused, checkListenBrainzToken("nope", server.client, baseUrl))

        server.answer(401, """{"code":401,"error":"Invalid authorization token."}""")
        assertEquals(SignIn.Refused, checkListenBrainzToken("nope", server.client, baseUrl))
    }

    @Test
    fun `a token checked with no connection is not called invalid`() = runTest {
        server.fail()
        assertEquals(SignIn.Unreachable, checkListenBrainzToken("token-1", server.client, baseUrl))
    }

    @Test
    fun `one play is a single listen with the song, its length and where it played`() = runTest {
        server.answer(200, """{"status":"ok"}""")

        assertEquals(SendOutcome.SENT, client.submit(listOf(play)))

        val sent = server.requests.single()
        assertEquals("/1/submit-listens", sent.url.encodedPath)
        assertEquals("Token token-1", sent.header("Authorization"))
        val body = sent.json()
        assertEquals("single", body.getString("listen_type"))
        val listen = body.getArray("payload").getObject(0)
        assertEquals(1_700_000_000L, listen.getLong("listened_at"))
        val metadata = listen.getObject("track_metadata")
        assertEquals("Orchid Lane", metadata.getString("artist_name"))
        assertEquals("Paper Lanterns", metadata.getString("track_name"))
        assertEquals("Harbour Lights", metadata.getString("release_name"))
        val info = metadata.getObject("additional_info")
        assertEquals(214_000L, info.getLong("duration_ms"))
        assertEquals("https://music.youtube.com/watch?v=x7kGuhlXmFI", info.getString("origin_url"))
        assertEquals("Sitara", info.getString("submission_client"))
    }

    @Test
    fun `a song on the phone is sent without a link to where it played`() = runTest {
        server.answer(200, """{"status":"ok"}""")

        client.submit(listOf(play.copy(trackId = localTrackId("content://media/external/audio/media/42"))))

        val info = server.requests.single().json().getArray("payload").getObject(0).getObject("track_metadata").getObject("additional_info")
        assertFalse(info.has("origin_url"))
        assertEquals("Sitara", info.getString("media_player"))
    }

    @Test
    fun `several plays go as one import, and a song with no album names none`() = runTest {
        server.answer(200, """{"status":"ok"}""")

        client.submit(listOf(play, play.copy(album = "", startedAtMs = 1_700_000_300_000)))

        val body = server.requests.single().json()
        assertEquals("import", body.getString("listen_type"))
        val payload = body.getArray("payload")
        assertEquals(2, payload.size)
        assertFalse(payload.getObject(1).getObject("track_metadata").has("release_name"))
    }

    @Test
    fun `now playing names the song without a time`() = runTest {
        server.answer(200, """{"status":"ok"}""")

        assertEquals(SendOutcome.SENT, client.nowPlaying(track))

        val body = server.requests.single().json()
        assertEquals("playing_now", body.getString("listen_type"))
        val listen = body.getArray("payload").getObject(0)
        assertFalse(listen.has("listened_at"))
        assertFalse(listen.getObject("track_metadata").has("release_name"), "an empty album is left out")
    }

    @Test
    fun `answers become outcomes`() = runTest {
        val outcomes = listOf(401 to SendOutcome.SIGN_IN_AGAIN, 429 to SendOutcome.TRY_LATER, 503 to SendOutcome.TRY_LATER, 400 to SendOutcome.REFUSED)
        for ((code, outcome) in outcomes) {
            server.answer(code, """{"code":$code,"error":"x"}""")
            assertEquals(outcome, client.submit(listOf(play)), "HTTP $code")
        }
        server.fail()
        assertEquals(SendOutcome.TRY_LATER, client.submit(listOf(play)), "no connection")
    }
}
