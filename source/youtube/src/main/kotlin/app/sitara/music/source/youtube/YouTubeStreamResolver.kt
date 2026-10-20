package app.sitara.music.source.youtube

import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

internal fun isVideoId(id: String): Boolean = VIDEO_ID.matches(id)

/** Resolves YouTube tracks (id = video id) to their best audio-only stream. Errors propagate to the fallback chain. */
class YouTubeStreamResolver : StreamResolver {
    override val id: String = "youtube"

    override suspend fun resolve(track: Track): ResolvedStream {
        require(isVideoId(track.id)) { "${track.id} is not a YouTube video id" }
        // Interruptible, so a cancelled load stops the blocking extractor call instead of waiting for it.
        val info = runInterruptible(Dispatchers.IO) { StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=${track.id}") }
        return selectAudioStream(info.audioStreams) ?: throw IOException("No playable audio stream for ${track.id}")
    }
}
