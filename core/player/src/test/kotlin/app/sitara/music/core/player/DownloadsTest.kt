package app.sitara.music.core.player

import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.scheduler.Requirements
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadsTest {
    private val song = Track(
        "dQw4w9WgXcQ", "Song – “quoted” ✦", "Artist", "Album", 213_000, Artwork(listOf(0xFF102030.toInt(), 7), "https://i.example.org/c.jpg"),
        explicit = true, artistId = "UC123",
    )

    @Test
    fun `a song's details survive the trip through its download`() {
        assertEquals(song, DownloadedTrack.decode(DownloadedTrack.encode(song)))
    }

    @Test
    fun `missing cover image and artist page stay missing, not empty`() {
        val bare = song.copy(artwork = Artwork(listOf(1)), artistId = null, explicit = false)
        assertEquals(bare, DownloadedTrack.decode(DownloadedTrack.encode(bare)))
    }

    @Test
    fun `unreadable data gives no song instead of crashing`() {
        val bytes = DownloadedTrack.encode(song)
        assertNull("truncated", DownloadedTrack.decode(bytes.copyOf(bytes.size / 2)))
        assertNull("unknown version", DownloadedTrack.decode(bytes.copyOf().also { it[0] = 9 }))
        assertNull("empty", DownloadedTrack.decode(ByteArray(0)))
    }

    @Test
    fun `finished, failed and running downloads map straight across`() {
        assertEquals(DownloadState.DOWNLOADED, downloadState(Download.STATE_COMPLETED, Requirements.NETWORK_UNMETERED))
        assertEquals(DownloadState.FAILED, downloadState(Download.STATE_FAILED, 0))
        assertEquals(DownloadState.DOWNLOADING, downloadState(Download.STATE_DOWNLOADING, 0))
    }

    @Test
    fun `a queued download says what it waits for`() {
        assertEquals(DownloadState.WAITING, downloadState(Download.STATE_QUEUED, 0))
        assertEquals("on mobile data with Wi-Fi only on", DownloadState.WAITING_FOR_WIFI, downloadState(Download.STATE_QUEUED, Requirements.NETWORK_UNMETERED))
        assertEquals(
            "offline with Wi-Fi only on",
            DownloadState.WAITING_FOR_WIFI,
            downloadState(Download.STATE_QUEUED, Requirements.NETWORK or Requirements.NETWORK_UNMETERED),
        )
        assertEquals("offline with Wi-Fi only off", DownloadState.WAITING_FOR_NETWORK, downloadState(Download.STATE_RESTARTING, Requirements.NETWORK))
    }

    @Test
    fun `progress is a whole percent once the size is known`() {
        assertNull(downloadPercent(-1f))
        assertEquals(0, downloadPercent(0f))
        assertEquals(42, downloadPercent(42.9f))
        assertEquals(100, downloadPercent(100.4f))
    }
}
