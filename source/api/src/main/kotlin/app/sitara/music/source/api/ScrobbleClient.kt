package app.sitara.music.source.api

import app.sitara.music.core.model.Scrobble
import app.sitara.music.core.model.Track

/** How a scrobbling service answered. */
enum class SendOutcome {
    /** Taken (a service may still ignore a play it judges a duplicate). */
    SENT,

    /** No connection, a rate limit or a server error: try again later with the same plays. */
    TRY_LATER,

    /** The token or session is no longer valid; the listener has to sign in again. */
    SIGN_IN_AGAIN,

    /** The service will never take these plays as they are. */
    REFUSED,
}

/** One signed-in scrobbling service. Never throws for network or service errors; they come back as a [SendOutcome]. */
interface ScrobbleClient {
    /** Tells the service what is playing now; it keeps nothing of it, so a failure is not retried. */
    suspend fun nowPlaying(track: Track): SendOutcome

    /** Scrobbles up to 50 plays at once. */
    suspend fun submit(scrobbles: List<Scrobble>): SendOutcome
}
