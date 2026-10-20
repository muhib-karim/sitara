package app.sitara.music.source.youtube

import app.sitara.music.core.model.Track
import java.util.Locale
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

// Song length bounds: shorter is a clip or a Short, longer is a compilation or a mix video.
private const val MIN_SONG_SECONDS = 60L
private const val MAX_SONG_SECONDS = 600L

/** The songs among [items] (a watch page's related items, a chart), in YouTube's order, skipping anything in [exclude] and repeats. */
internal fun songsFrom(items: List<InfoItem>, exclude: Set<String> = emptySet()): List<Track> = items.filterIsInstance<StreamInfoItem>()
    .filter { it.streamType == StreamType.VIDEO_STREAM || it.streamType == StreamType.AUDIO_STREAM }
    .filter { it.duration in MIN_SONG_SECONDS..MAX_SONG_SECONDS }
    .mapNotNull { it.toTrack() }
    .filter { it.id !in exclude }
    .distinctBy { it.id }

/** "YouTube Charts · India" for country code "IN", named in English like the rest of the app. */
internal fun chartLabel(countryCode: String): String = "YouTube Charts · " + Locale.Builder().setRegion(countryCode).build().getDisplayCountry(Locale.ENGLISH)
