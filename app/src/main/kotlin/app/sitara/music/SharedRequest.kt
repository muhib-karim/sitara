package app.sitara.music

import android.content.Intent
import app.sitara.music.core.model.Track
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.youtube.playlistIdFromLink
import app.sitara.music.source.youtube.videoIdFromLink

private const val MAX_SHARED_QUERY = 200

/** A link or text another app sent to Sitara: shown as a search, and played once it resolves when [playVideoId] is set. */
data class SharedRequest(val query: String, val playVideoId: String? = null)

/** Maps an incoming VIEW or SEND intent to a request. Only a validated video id or bounded plain text gets through. */
fun sharedRequest(action: String?, data: String?, text: String?): SharedRequest? {
    val payload = payload(action, data, text) ?: return null
    val videoId = videoIdFromLink(payload)
    return when {
        videoId != null -> SharedRequest("https://music.youtube.com/watch?v=$videoId", playVideoId = videoId)
        action == Intent.ACTION_SEND -> SharedRequest(payload.take(MAX_SHARED_QUERY))
        else -> null
    }
}

/** The linked song to start, once the search for [request] has resolved to exactly that song. */
fun autoplayTrack(state: SearchState, request: SharedRequest): Track? =
    (state as? SearchState.Loaded)?.takeIf { it.query == request.query }?.results?.tracks?.singleOrNull()?.takeIf { it.id == request.playVideoId }

/**
 * A playlist link opened in or shared to Sitara, rebuilt from its validated id, for the import screen; null for anything
 * else, including a song played from a playlist, which plays the song.
 */
fun sharedPlaylistLink(action: String?, data: String?, text: String?): String? {
    val payload = payload(action, data, text) ?: return null
    if (videoIdFromLink(payload) != null) return null
    return playlistIdFromLink(payload)?.let { "https://music.youtube.com/playlist?list=$it" }
}

private fun payload(action: String?, data: String?, text: String?): String? = when (action) {
    Intent.ACTION_VIEW -> data
    Intent.ACTION_SEND -> text
    else -> null
}?.trim()?.takeIf { it.isNotEmpty() }
