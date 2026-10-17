package app.sitara.music.source.fake

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.ListeningMood
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.Track

/**
 * Invented artists, albums and songs used by the fake source, previews and screenshot tests.
 * Covers are colour specs rendered as gradients at runtime, so nothing here is copyrighted.
 */
object SampleCatalog {
    private fun art(vararg argb: Long) = Artwork(argb.map { it.toInt() })

    val artists = listOf(
        Artist("ar-lumen", "Lumen Drift", art(0xFF6C5CE7, 0xFF00CEC9)),
        Artist("ar-ondine", "Ondine Vale", art(0xFFFF6B9A, 0xFFFFC371)),
        Artist("ar-kestrel", "Kestrel & The Tide", art(0xFF0F2027, 0xFF2C5364)),
        Artist("ar-mira", "Mira Sol", art(0xFFF5C451, 0xFFE17055)),
        Artist("ar-northbound", "Northbound Static", art(0xFF8B7CFF, 0xFF1E1B4B)),
    )

    private var trackCounter = 0

    private fun track(title: String, artist: Artist, album: String, seconds: Int, vararg argb: Long): Track {
        trackCounter++
        return Track(
            id = "tr-%02d".format(trackCounter),
            title = title,
            artist = artist.name,
            album = album,
            durationMs = seconds * 1_000L,
            artwork = art(*argb),
        )
    }

    val albums: List<Album> = listOf(
        Album(
            "al-afterglow", "Afterglow Atlas", "Lumen Drift", 2025, art(0xFF6C5CE7, 0xFF00CEC9, 0xFF0B0B14),
            listOf(
                track("Starlit Avenue", artists[0], "Afterglow Atlas", 372, 0xFF6C5CE7, 0xFF00CEC9, 0xFF0B0B14),
                track("Glass Horizon", artists[0], "Afterglow Atlas", 425, 0xFF6C5CE7, 0xFF00CEC9, 0xFF0B0B14),
                track("Low Orbit Lullaby", artists[0], "Afterglow Atlas", 344, 0xFF6C5CE7, 0xFF00CEC9, 0xFF0B0B14),
            ),
        ),
        Album(
            "al-saltwater", "Saltwater Letters", "Ondine Vale", 2024, art(0xFFFF6B9A, 0xFFFFC371),
            listOf(
                track("Paper Boats", artists[1], "Saltwater Letters", 302, 0xFFFF6B9A, 0xFFFFC371),
                track("Tidepool Hearts", artists[1], "Saltwater Letters", 361, 0xFFFF6B9A, 0xFFFFC371),
            ),
        ),
        Album(
            "al-deepwater", "Deep Water Radio", "Kestrel & The Tide", 2023, art(0xFF0F2027, 0xFF2C5364, 0xFF4CA1AF),
            listOf(
                track("Lighthouse Frequency", artists[2], "Deep Water Radio", 290, 0xFF0F2027, 0xFF2C5364, 0xFF4CA1AF),
                track("Undertow", artists[2], "Deep Water Radio", 333, 0xFF0F2027, 0xFF2C5364, 0xFF4CA1AF),
            ),
        ),
        Album(
            "al-goldhour", "Gold Hour", "Mira Sol", 2026, art(0xFFF5C451, 0xFFE17055),
            listOf(
                track("Marigold", artists[3], "Gold Hour", 318, 0xFFF5C451, 0xFFE17055),
                track("Sunroom", artists[3], "Gold Hour", 275, 0xFFF5C451, 0xFFE17055),
            ),
        ),
        Album(
            "al-signal", "Signal / Noise", "Northbound Static", 2025, art(0xFF8B7CFF, 0xFF1E1B4B),
            listOf(
                track("Night Bus Static", artists[4], "Signal / Noise", 401, 0xFF8B7CFF, 0xFF1E1B4B),
                track("Neon Transit", artists[4], "Signal / Noise", 356, 0xFF8B7CFF, 0xFF1E1B4B),
            ),
        ),
    )

    val tracks: List<Track> = albums.flatMap { it.tracks }

    val playlists = listOf(
        Playlist("pl-night", "Night Drive", "Slow-burning synths for empty roads", art(0xFF1E1B4B, 0xFF8B7CFF, 0xFFFF6B9A), tracks.filterIndexed { i, _ -> i % 2 == 0 }, setOf(ListeningMood.UNWIND)),
        Playlist("pl-focus", "Deep Focus", "Long tracks, no surprises", art(0xFF0F2027, 0xFF4CA1AF), tracks.take(5), setOf(ListeningMood.FOCUS)),
        Playlist("pl-sun", "Sunday Gold", "Warm, bright, unhurried", art(0xFFF5C451, 0xFFFF6B9A), tracks.takeLast(5), setOf(ListeningMood.UPLIFT)),
    )

    /** Original sample lyrics with word timings, written for the demo. */
    val demoLyricsLrc: String = """
        [ti:Starlit Avenue]
        [00:04.00]<00:04.00>Streetlights <00:04.80>hum <00:05.30>a <00:05.60>quiet <00:06.20>tune
        [00:07.60]<00:07.60>Paper <00:08.10>moons <00:08.70>above <00:09.30>the <00:09.60>roofs
        [00:11.20]<00:11.20>Every <00:11.80>window <00:12.50>keeps <00:13.00>a <00:13.30>spark
        [00:14.80]<00:14.80>Sitara, <00:15.80>light <00:16.30>the <00:16.60>dark
        [00:18.60]<00:18.60>Hold <00:19.10>the <00:19.40>chorus <00:20.10>in <00:20.40>your <00:20.80>hands
        [00:22.20]<00:22.20>Fold <00:22.70>the <00:23.00>static <00:23.70>into <00:24.20>plans
        [00:25.80]<00:25.80>We <00:26.20>were <00:26.50>never <00:27.10>out <00:27.50>of <00:27.80>time
        [00:29.40]<00:29.40>Just <00:29.90>a <00:30.20>little <00:30.80>out <00:31.20>of <00:31.50>line
        [00:33.00]<00:33.00>Sitara, <00:34.00>stay, <00:34.80>stay <00:35.60>a <00:35.90>while
        [00:37.40]<00:37.40>Turn <00:37.90>the <00:38.20>whole <00:38.70>night <00:39.20>into <00:39.70>a <00:40.00>smile
    """.trimIndent()

    /** Royalty-free algorithmic demo tracks (SoundHelix examples). */
    fun demoStreamUrl(index: Int): String = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-${(index % 16) + 1}.mp3"
}
