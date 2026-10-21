package app.sitara.music.source.scrobble

import app.sitara.music.core.model.Scrobble
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ScrobbleClient
import app.sitara.music.source.api.SendOutcome
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val LISTENBRAINZ = "https://api.listenbrainz.org/".toHttpUrl()
private val JSON = "application/json".toMediaType()

/** Asks ListenBrainz whose [token] it is (https://listenbrainz.org/settings/ shows a listener's token). */
suspend fun checkListenBrainzToken(token: String, http: OkHttpClient = scrobbleHttp, baseUrl: HttpUrl = LISTENBRAINZ): SignIn {
    val request = Request.Builder().url(baseUrl.resolve("1/validate-token")!!).header("Authorization", "Token $token").build()
    val answer = http.call(request) ?: return SignIn.Unreachable
    if (answer.code == 401) return SignIn.Refused
    if (answer.code != 200) return SignIn.Unreachable
    val reply = answer.body.asJsonObject() ?: return SignIn.Unreachable
    val user = reply.getString("user_name")
    return if (reply.getBoolean("valid") && !user.isNullOrBlank()) SignIn.Done(user, token) else SignIn.Refused
}

/** Scrobbles to ListenBrainz with a listener's user token (submit-listens API). */
class ListenBrainzClient(private val token: String, private val http: OkHttpClient = scrobbleHttp, private val baseUrl: HttpUrl = LISTENBRAINZ) : ScrobbleClient {
    override suspend fun nowPlaying(track: Track) =
        submit("playing_now", listOf(Scrobble(track.id, track.title, track.artist, track.album, track.durationMs, startedAtMs = 0)), timed = false)

    override suspend fun submit(scrobbles: List<Scrobble>) = submit(if (scrobbles.size == 1) "single" else "import", scrobbles, timed = true)

    private suspend fun submit(type: String, scrobbles: List<Scrobble>, timed: Boolean): SendOutcome {
        val writer = JsonWriter.string().`object`().value("listen_type", type).array("payload")
        for (play in scrobbles) {
            writer.`object`()
            if (timed) writer.value("listened_at", play.startedAtMs / 1_000)
            writer.`object`("track_metadata").value("artist_name", play.artist).value("track_name", play.title)
            if (play.album.isNotBlank()) writer.value("release_name", play.album)
            writer.`object`("additional_info")
                .value("duration_ms", play.durationMs)
                .value("origin_url", "https://music.youtube.com/watch?v=${play.trackId}")
                .value("media_player", "Sitara")
                .value("submission_client", "Sitara")
                .end().end().end()
        }
        val body = writer.end().end().done()
        val request = Request.Builder().url(baseUrl.resolve("1/submit-listens")!!).header("Authorization", "Token $token").post(body.toRequestBody(JSON)).build()
        val answer = http.call(request) ?: return SendOutcome.TRY_LATER
        return when (answer.code) {
            200 -> SendOutcome.SENT
            401 -> SendOutcome.SIGN_IN_AGAIN
            429, in 500..599 -> SendOutcome.TRY_LATER
            else -> SendOutcome.REFUSED
        }
    }
}

internal fun String.asJsonObject(): JsonObject? = try {
    JsonParser.`object`().from(this)
} catch (e: JsonParserException) {
    null
}
