package app.sitara.music.source.youtube

import app.sitara.music.core.model.AudioQuality
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.FallbackStreamResolver
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import com.grack.nanojson.JsonObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.generateContentPlaybackNonce
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper

/**
 * Every route to a song's audio, tried in this fixed order until one returns a stream:
 * 1. NewPipe's full extraction ([YouTubeStreamResolver]): the Android client, merged with visionOS streams. It fails as a
 *    whole when YouTube refuses the Android request (UNPLAYABLE, LOGIN_REQUIRED), even if other clients would answer.
 * 2. The visionOS client on its own; NewPipe asks it without a proof-of-origin token.
 * 3. The iOS client, without a token; YouTube is rolling a token requirement out there, so it is last.
 * Each picks the stream for the [quality] asked for at that moment (the network's setting). A route that worked for a
 * song is asked first for that song afterwards. None of them gets past a bot check aimed at
 * the device's network, and YouTube can close any of them at any time.
 */
fun youTubeStreamResolver(quality: () -> AudioQuality): StreamResolver = FallbackStreamResolver(youTubeStreamRoutes(quality))

internal fun youTubeStreamRoutes(quality: () -> AudioQuality): List<StreamResolver> = listOf(
    YouTubeStreamResolver(quality),
    ClientStreamResolver("youtube-visionos", quality) { videoId ->
        YoutubeStreamHelper.getVisionOsPlayerResponse(NewPipe.getPreferredContentCountry(), NewPipe.getPreferredLocalization(), videoId, generateContentPlaybackNonce())
    },
    ClientStreamResolver("youtube-ios", quality) { videoId ->
        YoutubeStreamHelper.getIosPlayerResponse(NewPipe.getPreferredContentCountry(), NewPipe.getPreferredLocalization(), videoId, generateContentPlaybackNonce(), null)
    },
)

/** Asks one InnerTube client for a video's player response and plays its audio format for [quality] that has a direct URL. */
internal class ClientStreamResolver(
    override val id: String,
    private val quality: () -> AudioQuality,
    private val playerResponse: (videoId: String) -> JsonObject,
) : StreamResolver {
    override suspend fun resolve(track: Track): ResolvedStream {
        require(isVideoId(track.id)) { "${track.id} is not a YouTube video id" }
        val response = runInterruptible(Dispatchers.IO) { playerResponse(track.id) }
        val playability = response.getObject("playabilityStatus")
        val status = playability.getString("status")
        if (!status.equals("OK", ignoreCase = true)) throw IOException("$id refused ${track.id}: $status \"${playability.getString("reason")}\"")
        return selectAudioFormat(response.getObject("streamingData").getArray("adaptiveFormats").filterIsInstance<JsonObject>(), quality())
            ?: throw IOException("$id has no playable audio format for ${track.id}")
    }
}
