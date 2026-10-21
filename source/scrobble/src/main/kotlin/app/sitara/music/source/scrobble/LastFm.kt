package app.sitara.music.source.scrobble

import app.sitara.music.core.model.Scrobble
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ScrobbleClient
import app.sitara.music.source.api.SendOutcome
import com.grack.nanojson.JsonObject
import java.security.MessageDigest
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private val LASTFM = "https://ws.audioscrobbler.com/2.0/".toHttpUrl()

/** Last.fm's error codes this app acts on (https://www.last.fm/api/errorcodes). */
private const val INVALID_SESSION = 9
private const val SERVICE_OFFLINE = 11
private const val TOKEN_NOT_AUTHORIZED = 14
private const val TEMPORARILY_UNAVAILABLE = 16
private const val RATE_LIMITED = 29

/** The app's Last.fm API account, from the build; a build without one cannot use Last.fm. */
class LastFmKeys(val apiKey: String, val secret: String)

/** Last.fm's call signature: every parameter but the format, by name, as name and value, then the shared secret; MD5 in hex. */
internal fun lastFmSignature(params: Map<String, String>, secret: String): String {
    val text = params.filterKeys { it != "format" }.toSortedMap().entries.joinToString("") { (name, value) -> name + value } + secret
    return MessageDigest.getInstance("MD5").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}

/** A Last.fm answer: its status, the error code it names, and its JSON body (null for a page that is not JSON). */
private class LastFmAnswer(val code: Int, val error: Int?, val body: JsonObject?)

/** Signed calls to the Last.fm API, as POST forms answered in JSON. */
private class LastFmApi(private val keys: LastFmKeys, private val http: OkHttpClient, private val baseUrl: HttpUrl) {
    /** Null when Last.fm cannot be reached. */
    suspend fun call(method: String, params: Map<String, String>): LastFmAnswer? {
        val signed = params + mapOf("method" to method, "api_key" to keys.apiKey)
        val form = FormBody.Builder().apply {
            signed.forEach { (name, value) -> add(name, value) }
            add("api_sig", lastFmSignature(signed, keys.secret))
            add("format", "json")
        }.build()
        val answer = http.call(Request.Builder().url(baseUrl).post(form).build()) ?: return null
        val json = answer.body.asJsonObject()
        return LastFmAnswer(answer.code, json?.takeIf { it.has("error") }?.getInt("error"), json)
    }
}

/** Last.fm's web sign-in: a token, the listener allowing Sitara on last.fm in the browser, then a session for that token. */
class LastFmAuth(private val keys: LastFmKeys, http: OkHttpClient = scrobbleHttp, baseUrl: HttpUrl = LASTFM) {
    private val api = LastFmApi(keys, http, baseUrl)

    /** A token for [browserUrl]; null when Last.fm cannot be reached. */
    suspend fun requestToken(): String? = api.call("auth.getToken", emptyMap())?.body?.getString("token")?.takeIf { it.isNotBlank() }

    fun browserUrl(token: String) = "https://www.last.fm/api/auth/?api_key=${keys.apiKey}&token=$token"

    /** The session for [token] once the listener allowed it; [SignIn.Waiting] until then. */
    suspend fun session(token: String): SignIn {
        val answer = api.call("auth.getSession", mapOf("token" to token)) ?: return SignIn.Unreachable
        val session = answer.body?.getObject("session")
        val name = session?.getString("name")
        val key = session?.getString("key")
        return when {
            answer.error == TOKEN_NOT_AUTHORIZED -> SignIn.Waiting
            answer.error != null -> SignIn.Refused
            name.isNullOrBlank() || key.isNullOrBlank() -> SignIn.Unreachable
            else -> SignIn.Done(name, key)
        }
    }
}

/** Scrobbles to Last.fm with a session from [LastFmAuth]. */
class LastFmClient(keys: LastFmKeys, private val sessionKey: String, http: OkHttpClient = scrobbleHttp, baseUrl: HttpUrl = LASTFM) : ScrobbleClient {
    private val api = LastFmApi(keys, http, baseUrl)

    override suspend fun nowPlaying(track: Track): SendOutcome {
        val params = mutableMapOf("artist" to track.artist, "track" to track.title, "duration" to (track.durationMs / 1_000).toString())
        if (track.album.isNotBlank()) params["album"] = track.album
        return outcome(api.call("track.updateNowPlaying", params + ("sk" to sessionKey)))
    }

    override suspend fun submit(scrobbles: List<Scrobble>): SendOutcome {
        val params = mutableMapOf("sk" to sessionKey)
        scrobbles.forEachIndexed { i, play ->
            params["artist[$i]"] = play.artist
            params["track[$i]"] = play.title
            params["timestamp[$i]"] = (play.startedAtMs / 1_000).toString()
            params["duration[$i]"] = (play.durationMs / 1_000).toString()
            if (play.album.isNotBlank()) params["album[$i]"] = play.album
        }
        return outcome(api.call("track.scrobble", params))
    }

    private fun outcome(answer: LastFmAnswer?): SendOutcome = when (answer?.error) {
        // No answer, or a server error page rather than Last.fm's JSON, is tried again.
        null -> if (answer?.code == 200) SendOutcome.SENT else SendOutcome.TRY_LATER
        INVALID_SESSION -> SendOutcome.SIGN_IN_AGAIN
        SERVICE_OFFLINE, TEMPORARILY_UNAVAILABLE, RATE_LIMITED -> SendOutcome.TRY_LATER
        else -> SendOutcome.REFUSED
    }
}
