package app.sitara.music.core.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.StreamResolver
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/**
 * Turns `sitara://track/<id>` into a real stream URL on the player's loader thread.
 * Failures become [IOException]s, which ExoPlayer reports as playback errors instead of crashing.
 */
@OptIn(UnstableApi::class)
class StreamUriResolver(
    private val resolver: StreamResolver,
    private val trackFor: (String) -> Track?,
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (!StreamPolicy.mayLoad(uri.scheme)) throw IOException("Refusing to load ${uri.scheme} URIs")
        if (uri.scheme != StreamPolicy.TRACK_SCHEME) return dataSpec
        val id = uri.lastPathSegment ?: throw IOException("No track id in $uri")
        val track = trackFor(id) ?: throw IOException("Track $id is not in the queue")
        val stream = try {
            runBlocking { resolver.resolve(track) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Resolving $id was interrupted").apply { initCause(e) }
        } catch (e: CancellationException) {
            throw InterruptedIOException("Resolving $id was cancelled").apply { initCause(e) }
        } catch (e: Exception) {
            throw IOException("Could not resolve a stream for $id", e)
        }
        if (!StreamPolicy.mayStream(stream.url)) throw IOException("Stream for $id is not https")
        return dataSpec.withUri(Uri.parse(stream.url))
    }

    companion object {
        fun uriFor(trackId: String): Uri = Uri.Builder().scheme(StreamPolicy.TRACK_SCHEME).authority("track").appendPath(trackId).build()
    }
}
