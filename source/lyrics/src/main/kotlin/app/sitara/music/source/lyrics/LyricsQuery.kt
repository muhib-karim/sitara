package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Track

/** What a lyrics catalogue is asked for: the song's title and artist as a record label would write them. */
data class LyricsQuery(val title: String, val artist: String)

// Bracketed video decoration: "(Official Music Video)", "[Lyrics]", "(Visualizer)", "[4K]". Other brackets ("(Acoustic)") stay.
private val VIDEO_LABEL = Regex("""\s*[(\[][^)\]]*\b(official|video|audio|lyrics?|visuali[sz]er|mv|m/v|hd|hq|4k|explicit)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
// Featured artists: lyrics catalogues file the song under its own title and main artist.
private val BRACKETED_FEATURE = Regex("""\s*[(\[]\s*(?:feat\.?|ft\.?|featuring)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
private val TRAILING_FEATURE = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
private val PIPE_SUFFIX = Regex("""\s+\|.*$""")
private val ARTIST_DASH = Regex("""\s+[-–—]\s+""")
private val VERSION = Regex("""^(remaster|live|version|edit|mix|radio|acoustic|demo|mono|stereo)""", RegexOption.IGNORE_CASE)

/**
 * Cleans a video title for a lyrics search: drops video labels, featured artists ("(feat. X)", "ft. X") and a
 * " | …" tail, and reads "Artist - Song" titles (common on music videos, whose channel is often a label or
 * "ArtistVEVO") as the artist and the song. A dash followed by a version ("Song - Remastered 2011") stays part of the
 * title. "ArtistVEVO" and "Artist - Topic" channel names lose their suffix, and a VEVO name written as one word
 * ("OliviaRodrigoVEVO") is split back into words ("Olivia Rodrigo"), the way LRCLIB files the artist.
 */
fun lyricsQuery(track: Track): LyricsQuery {
    val title = track.title.replace(PIPE_SUFFIX, "").replace(VIDEO_LABEL, "").replace(BRACKETED_FEATURE, "").trim()
    val channel = track.artist.removeSuffix(" - Topic").trim()
    val artist = if (channel.endsWith("VEVO")) channel.removeSuffix("VEVO").trim().splitJoinedWords() else channel
    val parts = title.split(ARTIST_DASH, limit = 2)
    if (parts.size == 2 && parts.all { it.isNotBlank() } && !VERSION.containsMatchIn(parts[1])) {
        return LyricsQuery(parts[1].withoutFeature(), parts[0].withoutFeature())
    }
    return LyricsQuery(title.withoutFeature(), artist)
}

private fun String.withoutFeature(): String = replace(TRAILING_FEATURE, "").trim()

// A lower-case letter followed by a capital: the word break VEVO channel names leave out.
private val JOINED_WORDS = Regex("""(?<=\p{Ll})(?=\p{Lu})""")

private fun String.splitJoinedWords(): String = if (' ' in this) this else replace(JOINED_WORDS, " ")
