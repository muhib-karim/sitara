package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Track

/** What a lyrics catalogue is asked for: the song's title and artist as a record label would write them. */
data class LyricsQuery(val title: String, val artist: String)

// Bracketed video decoration: "(Official Music Video)", "[Lyrics]", "(Visualizer)", "[4K]". Other brackets ("(Acoustic)") stay.
private val VIDEO_LABEL = Regex("""\s*[(\[][^)\]]*\b(official|video|audio|lyrics?|visuali[sz]er|mv|m/v|hd|hq|4k|explicit)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
private val PIPE_SUFFIX = Regex("""\s+\|.*$""")
private val ARTIST_DASH = Regex("""\s+[-–—]\s+""")
private val VERSION = Regex("""^(remaster|live|version|edit|mix|radio|acoustic|demo|mono|stereo)""", RegexOption.IGNORE_CASE)

/**
 * Cleans a video title for a lyrics search: drops video labels and a " | …" tail, and reads "Artist - Song" titles
 * (common on music videos, whose channel is often a label or "ArtistVEVO") as the artist and the song. A dash
 * followed by a version ("Song - Remastered 2011") stays part of the title.
 */
fun lyricsQuery(track: Track): LyricsQuery {
    val title = track.title.replace(PIPE_SUFFIX, "").replace(VIDEO_LABEL, "").trim()
    val artist = track.artist.removeSuffix("VEVO").trim()
    val parts = title.split(ARTIST_DASH, limit = 2)
    if (parts.size == 2 && parts.all { it.isNotBlank() } && !VERSION.containsMatchIn(parts[1])) return LyricsQuery(parts[1], parts[0])
    return LyricsQuery(title, artist)
}
