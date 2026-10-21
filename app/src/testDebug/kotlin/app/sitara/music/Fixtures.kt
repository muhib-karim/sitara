package app.sitara.music

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.ArtistPage
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.ImportJob
import app.sitara.music.core.model.ImportReviewRow
import app.sitara.music.core.model.MatchState
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.SourceSong
import app.sitara.music.core.model.Track

/** Hand-built pages shaped like YouTube Music data (no year on albums, channel and playlist ids), with invented names. */
internal object Fixtures {
    private val dusk = Artwork(listOf(0xFF9A7BFF.toInt(), 0xFF2B1D5C.toInt()))
    private val ember = Artwork(listOf(0xFFF07B4F.toInt(), 0xFF3D1A12.toInt()))
    private val tide = Artwork(listOf(0xFF2BB39A.toInt(), 0xFF0E3B3A.toInt()))
    private const val ARTIST_ID = "UCfixturefixturefixture1"

    private fun song(id: String, title: String, seconds: Long, art: Artwork = dusk) =
        Track(id, title, "Orchid Lane", "Paper Lanterns", seconds * 1_000, art, artistId = ARTIST_ID)

    private val songs = listOf(
        song("fixture0001", "Paper Lanterns", 214),
        song("fixture0002", "Harbour Lights", 187),
        song("fixture0003", "Slow Comet", 241),
        song("fixture0004", "Monsoon Letters", 205),
    )

    val album = Album("OLAK5uy_fixture", "Paper Lanterns", "Orchid Lane", null, dusk, songs, ARTIST_ID)

    val artistPage = ArtistPage(
        artist = Artist(ARTIST_ID, "Orchid Lane", dusk),
        bannerUrl = null,
        topSongs = songs.take(3),
        releases = listOf(
            album.copy(tracks = emptyList()),
            Album("OLAK5uy_fixture2", "Harbour Lights", "Orchid Lane", 2024, ember, emptyList(), ARTIST_ID),
            Album("OLAK5uy_fixture3", "Low Tide EP", "Orchid Lane", null, tide, emptyList(), ARTIST_ID),
        ),
    )

    val playlist = Playlist(
        id = "PLfixture",
        title = "Late Night Drive",
        description = "",
        artwork = ember,
        tracks = songs.reversed() + song("fixture0005", "Northern Static", 263, tide),
        curator = "Sitara listeners",
    )

    /** One download in each state the Library shows, newest first: 8.5 MB on the device in all. */
    val downloads = songs.let { (first, second, third, fourth) ->
        listOf(
            SongDownload(first, DownloadState.DOWNLOADING, 42, 1_700_000),
            SongDownload(second, DownloadState.WAITING_FOR_WIFI, null, 0),
            SongDownload(third, DownloadState.FAILED, 12, 600_000),
            SongDownload(fourth, DownloadState.DOWNLOADED, 100, 6_200_000),
        )
    }

    val downloadStates = downloads.associate { it.track.id to it.state }

    private val coverVersion = song("fixture0005", "Harbour Lights (Live)", 251, ember).copy(artist = "Kite Parade")

    /** An import of a 120-song file, 37 songs looked up so far. */
    val importMatching = ImportJob(
        id = 1, name = "Road trip", pending = 83, matched = 31, uncertain = 4, notFound = 2, skipped = 0, updatesPlaylist = false, review = emptyList(),
    )

    /** The same import looked up to the end, with three songs to check: undecided, taken and skipped. */
    val importReview = importMatching.copy(
        pending = 0, matched = 109, uncertain = 1, notFound = 9, skipped = 1,
        review = listOf(
            ImportReviewRow(4, SourceSong("Harbour Lights", "Orchid Lane"), MatchState.UNCERTAIN, coverVersion, listOf(coverVersion, songs[1])),
            ImportReviewRow(17, SourceSong("Slow Comet (Radio Edit)", "Orchid Lane"), MatchState.MATCHED, songs[2], listOf(songs[2])),
            ImportReviewRow(52, SourceSong("Monsoon Letter", "Orchid Lanes"), MatchState.SKIPPED, songs[3], listOf(songs[3], songs[0])),
        ),
    )
}
