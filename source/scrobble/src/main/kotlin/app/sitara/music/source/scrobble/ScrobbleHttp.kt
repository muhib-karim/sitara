package app.sitara.music.source.scrobble

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request

// Scrobbling services ask clients to name themselves; the app and where it lives, nothing about the device or listener.
internal const val SCROBBLE_USER_AGENT = "Sitara (https://github.com/muhib-karim/sitara)"

/** One client for every scrobbling call. */
internal val scrobbleHttp: OkHttpClient by lazy { OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build() }

/** How signing in to a service ended. */
sealed interface SignIn {
    /** Signed in as [userName]; [secret] is the token or session key to keep, encrypted. */
    data class Done(val userName: String, val secret: String) : SignIn

    /** The service said no: a wrong token, or access not allowed. */
    data object Refused : SignIn

    /** The listener has not allowed access in the browser yet. */
    data object Waiting : SignIn

    /** No connection, or the service is down; nothing is known about the credentials. */
    data object Unreachable : SignIn
}

/** A service's answer: its status code and body. */
internal class Answer(val code: Int, val body: String)

/** Runs [request] off the main thread; null when the service cannot be reached. */
internal suspend fun OkHttpClient.call(request: Request): Answer? = try {
    runInterruptible(Dispatchers.IO) {
        newCall(request.newBuilder().header("User-Agent", SCROBBLE_USER_AGENT).build()).execute().use { Answer(it.code, it.body.string()) }
    }
} catch (e: IOException) {
    null
}
