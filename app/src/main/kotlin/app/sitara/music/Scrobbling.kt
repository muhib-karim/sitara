package app.sitara.music

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.sitara.music.core.data.scrobble.ScrobbleAccounts
import app.sitara.music.core.data.scrobble.ScrobbleOutbox
import app.sitara.music.core.model.Listen
import app.sitara.music.core.model.ScrobbleService
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.scrobbleTargets
import app.sitara.music.core.model.toScrobble
import app.sitara.music.source.api.ScrobbleClient
import app.sitara.music.source.api.SendOutcome
import app.sitara.music.source.scrobble.LastFmAuth
import app.sitara.music.source.scrobble.LastFmClient
import app.sitara.music.source.scrobble.LastFmKeys
import app.sitara.music.source.scrobble.ListenBrainzClient
import app.sitara.music.source.scrobble.SignIn
import app.sitara.music.source.scrobble.checkListenBrainzToken
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Scrobbling to the services the listener connected: "now playing" as each play starts (not retried), and each play
 * that counts into the [outbox], sent by [ScrobbleWorker] once there is a connection.
 */
class Scrobbling(
    private val context: Context,
    val accounts: ScrobbleAccounts,
    val outbox: ScrobbleOutbox,
    /** Null in a build made without a Last.fm API key. */
    private val lastFmKeys: LastFmKeys?,
    private val inLibrary: suspend (Track) -> Boolean,
) {
    /** Whether this build can use [service]. */
    fun available(service: ScrobbleService) = when (service) {
        ScrobbleService.LISTENBRAINZ -> true
        ScrobbleService.LASTFM -> lastFmKeys != null
    }

    /** Tells each signed-in service what started playing, unless scrobbling is paused. */
    suspend fun onPlayStarted(track: Track) {
        val setup = accounts.setup.first()
        if (setup.paused) return
        for (account in setup.accounts) {
            if (account.signInNeeded || (account.libraryOnly && !inLibrary(track))) continue
            client(account.service)?.nowPlaying(track)
        }
    }

    /** Keeps [listen] for each service it goes to and asks for a send; a play reported again is kept once. */
    suspend fun onListened(listen: Listen) {
        val setup = accounts.setup.first()
        val targets = scrobbleTargets(listen, setup.accounts, setup.paused, setup.accounts.any { it.libraryOnly } && inLibrary(listen.track))
        if (targets.isNotEmpty() && outbox.add(listen.toScrobble(), targets)) send(now = false)
    }

    /** Asks [ScrobbleWorker] to send waiting plays once there is a connection; [now] starts over a send waiting out a back-off. */
    fun send(now: Boolean) {
        val request = OneTimeWorkRequestBuilder<ScrobbleWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        // A send already queued or running drains every waiting play, new ones included.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    /** Sends every service's waiting plays; false when one should be tried again later. A service whose session ended waits for a new sign-in. */
    suspend fun sendWaiting(): Boolean {
        var tryLater = false
        for (account in accounts.setup.first().accounts.filterNot { it.signInNeeded }) {
            val client = client(account.service)
            val outcome = if (client == null) SendOutcome.SIGN_IN_AGAIN else outbox.send(account.service, client)
            when (outcome) {
                SendOutcome.SIGN_IN_AGAIN -> accounts.signInNeeded(account.service)
                SendOutcome.TRY_LATER -> tryLater = true
                SendOutcome.SENT, SendOutcome.REFUSED -> Unit
            }
        }
        return !tryLater
    }

    /** Checks a ListenBrainz user token and connects as its user; plays waiting from before a sign-out go out. */
    suspend fun connectListenBrainz(token: String): SignIn {
        val result = checkListenBrainzToken(token.trim())
        if (result is SignIn.Done) {
            accounts.connect(ScrobbleService.LISTENBRAINZ, result.userName, result.secret)
            send(now = true)
        }
        return result
    }

    /** Starts Last.fm's web sign-in: the token and the page where the listener allows it; null when Last.fm cannot be reached. */
    suspend fun startLastFmSignIn(): Pair<String, String>? {
        val auth = LastFmAuth(lastFmKeys ?: return null)
        val token = auth.requestToken() ?: return null
        return token to auth.browserUrl(token)
    }

    /** Connects Last.fm once the listener allowed [token] in the browser. */
    suspend fun finishLastFmSignIn(token: String): SignIn {
        val result = LastFmAuth(lastFmKeys ?: return SignIn.Refused).session(token)
        if (result is SignIn.Done) {
            accounts.connect(ScrobbleService.LASTFM, result.userName, result.secret)
            send(now = true)
        }
        return result
    }

    /** Forgets [service]'s sign-in and the plays waiting for it. */
    suspend fun disconnect(service: ScrobbleService) {
        accounts.disconnect(service)
        outbox.forget(service)
    }

    // A credential that cannot be opened (a cleared Keystore) reads as signed out.
    private suspend fun client(service: ScrobbleService): ScrobbleClient? {
        val secret = accounts.secret(service) ?: return null
        return when (service) {
            ScrobbleService.LISTENBRAINZ -> ListenBrainzClient(secret)
            ScrobbleService.LASTFM -> lastFmKeys?.let { LastFmClient(it, secret) }
        }
    }

    private companion object {
        const val WORK = "scrobble"
        const val BACKOFF_SECONDS = 30L
    }
}

/** Sends waiting scrobbles; WorkManager runs it with a connection, retries it with a growing back-off and keeps it across restarts. */
class ScrobbleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if ((applicationContext as SitaraApplication).scrobbling.sendWaiting()) Result.success() else Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A store that cannot be read now is tried again; the plays stay in the outbox.
        Log.w("Sitara", "Scrobble send failed", e)
        Result.retry()
    }
}
