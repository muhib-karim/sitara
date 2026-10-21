package app.sitara.music.core.model

/** A song of 30 seconds or less is never scrobbled. */
private const val SHORTEST_SCROBBLE_MS = 30_000L

/** A long song scrobbles after this much is heard, however long it is. */
private const val SCROBBLE_AFTER_MS = 240_000L

/** Where plays can be scrobbled. */
enum class ScrobbleService(val label: String) {
    LISTENBRAINZ("ListenBrainz"),
    LASTFM("Last.fm"),
}

/** A play as a scrobbling service hears of it; [startedAtMs] (wall clock) and [trackId] name the play. */
data class Scrobble(val trackId: String, val title: String, val artist: String, val album: String, val durationMs: Long, val startedAtMs: Long)

/** A connected service as the listener set it up; its key or token stays in the data layer. */
data class ScrobbleAccount(
    val service: ScrobbleService,
    val userName: String,
    /** Only songs in the library (liked or in a playlist) are scrobbled. */
    val libraryOnly: Boolean = false,
    /** The service ended the session; plays wait until the listener signs in again. */
    val signInNeeded: Boolean = false,
)

/** Scrobbling as set up: the connected services, and whether the listener paused it for now. */
data class ScrobbleSetup(val paused: Boolean = false, val accounts: List<ScrobbleAccount> = emptyList())

/** The scrobbling rule Last.fm and ListenBrainz share: a song longer than 30 s, heard for half its length or 4 minutes, whichever comes first. */
fun scrobbleCounts(durationMs: Long, heardMs: Long): Boolean = durationMs > SHORTEST_SCROBBLE_MS && heardMs >= minOf(durationMs / 2, SCROBBLE_AFTER_MS)

fun Listen.toScrobble() = Scrobble(track.id, track.title, track.artist, track.album, track.durationMs, startedAtMs)

/** The services [listen] is scrobbled to: none while [paused] or before it counts; a library-only service just for songs [inLibrary]. */
fun scrobbleTargets(listen: Listen, accounts: List<ScrobbleAccount>, paused: Boolean, inLibrary: Boolean): Set<ScrobbleService> {
    if (paused || !scrobbleCounts(listen.track.durationMs, listen.listenedMs)) return emptySet()
    return accounts.filter { inLibrary || !it.libraryOnly }.mapTo(mutableSetOf()) { it.service }
}
