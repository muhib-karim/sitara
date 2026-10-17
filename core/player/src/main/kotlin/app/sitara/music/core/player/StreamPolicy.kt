package app.sitara.music.core.player

/**
 * What the player is allowed to open. Items only ever come from the app's own queue, but the
 * session is exported, so the data source refuses anything else: no local files, no content
 * providers, no cleartext.
 */
object StreamPolicy {
    const val TRACK_SCHEME = "sitara"
    private const val HTTPS = "https"

    /** `sitara://` queue tracks, plus https for streams that load further parts (playlists, segments). */
    fun mayLoad(scheme: String?): Boolean = scheme == TRACK_SCHEME || scheme == HTTPS

    fun mayStream(url: String): Boolean = url.startsWith("$HTTPS://")
}
