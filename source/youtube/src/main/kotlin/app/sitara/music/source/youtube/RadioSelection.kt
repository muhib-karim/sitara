package app.sitara.music.source.youtube

import app.sitara.music.core.model.Track
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

// Song length bounds: shorter is a clip or a Short, longer is a compilation or a mix video.
private const val MIN_SONG_SECONDS = 60L
private const val MAX_SONG_SECONDS = 600L

/** Songs to queue from a watch page's related items, in YouTube's order, skipping anything in [exclude] and repeats. */
internal fun radioTracks(related: List<InfoItem>, exclude: Set<String>): List<Track> = related.filterIsInstance<StreamInfoItem>()
    .filter { it.streamType == StreamType.VIDEO_STREAM || it.streamType == StreamType.AUDIO_STREAM }
    .filter { it.duration in MIN_SONG_SECONDS..MAX_SONG_SECONDS }
    .mapNotNull { it.toTrack() }
    .filter { it.id !in exclude }
    .distinctBy { it.id }
