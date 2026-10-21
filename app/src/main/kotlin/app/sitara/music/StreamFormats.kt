package app.sitara.music

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.sitara.music.core.model.StreamFormat
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers the codec and bitrate each song last streamed in, for the readout in Now Playing. */
class FormatRecordingResolver(private val upstream: StreamResolver) : StreamResolver {
    override val id: String get() = upstream.id

    // Lookup order, oldest first. A StateFlow drops a value equal to the current one, and maps compare without
    // order, so the order lives here and the flow only publishes copies.
    private val byLookup = LinkedHashMap<String, StreamFormat>()
    private val recorded = MutableStateFlow<Map<String, StreamFormat>>(emptyMap())

    /** By track id, the most recent [MAX_FORMATS] songs. */
    val formats: StateFlow<Map<String, StreamFormat>> = recorded.asStateFlow()

    override suspend fun resolve(track: Track): ResolvedStream = upstream.resolve(track).also { stream ->
        synchronized(byLookup) {
            byLookup.remove(track.id)
            byLookup[track.id] = StreamFormat(stream.codec, stream.bitrateKbps)
            while (byLookup.size > MAX_FORMATS) byLookup.remove(byLookup.keys.first())
            recorded.value = byLookup.toMap()
        }
    }

    private companion object {
        const val MAX_FORMATS = 50
    }
}

/** Wi-Fi or Ethernet, the networks the Wi-Fi quality setting is for; anything else counts as mobile data. */
fun ConnectivityManager.onWifi(): Boolean {
    val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
