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
import kotlinx.coroutines.flow.update

/** Remembers the codec and bitrate each song last streamed in, for the readout in Now Playing. */
class FormatRecordingResolver(private val upstream: StreamResolver) : StreamResolver {
    override val id: String get() = upstream.id

    private val recorded = MutableStateFlow<Map<String, StreamFormat>>(emptyMap())

    /** By track id, the most recent [MAX_FORMATS] songs. */
    val formats: StateFlow<Map<String, StreamFormat>> = recorded.asStateFlow()

    override suspend fun resolve(track: Track): ResolvedStream = upstream.resolve(track).also { stream ->
        recorded.update { formats ->
            // Insertion order is lookup order: a song looked up again moves to the end, the oldest goes first.
            LinkedHashMap(formats).apply {
                remove(track.id)
                put(track.id, StreamFormat(stream.codec, stream.bitrateKbps))
                while (size > MAX_FORMATS) remove(keys.first())
            }
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
