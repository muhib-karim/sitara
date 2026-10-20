package app.sitara.music.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadsTest {
    private val songs = (1..4).map { Track("id$it", "Song $it", "Artist", "Album", 180_000, Artwork(listOf(0))) }

    @Test
    fun `download all skips songs already downloaded or on their way`() {
        val states = mapOf("id1" to DownloadState.DOWNLOADED, "id2" to DownloadState.DOWNLOADING, "id3" to DownloadState.WAITING_FOR_WIFI)
        assertEquals(listOf(songs[3]), songs.toDownload(states))
    }

    @Test
    fun `a failed download is tried again`() {
        assertEquals(listOf(songs[0]), songs.take(1).toDownload(mapOf("id1" to DownloadState.FAILED)))
    }

    @Test
    fun `a song listed twice downloads once`() {
        assertEquals(listOf(songs[0], songs[1]), listOf(songs[0], songs[1], songs[0]).toDownload(emptyMap()))
    }
}
