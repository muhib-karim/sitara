package app.sitara.music.source.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Related items are built by hand the way a watch page lists them: songs, videos, mixes and streams mixed together. */
class RadioSelectionTest {
    private fun related(videoId: String, seconds: Long = 210, type: StreamType = StreamType.VIDEO_STREAM) =
        StreamInfoItem(0, "https://www.youtube.com/watch?v=$videoId", "Song $videoId", type).apply {
            uploaderName = "Artist"
            duration = seconds
        }

    @Test
    fun `keeps songs in the order YouTube suggests them`() {
        val picks = radioTracks(listOf(related("aaaaaaaaaaa"), related("bbbbbbbbbbb")), exclude = emptySet())

        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), picks.map { it.id })
    }

    @Test
    fun `drops songs already queued and repeats`() {
        val items = listOf(related("x7kGuhlXmFI"), related("aaaaaaaaaaa"), related("aaaaaaaaaaa"), related("bbbbbbbbbbb"))

        assertEquals(listOf("bbbbbbbbbbb"), radioTracks(items, exclude = setOf("x7kGuhlXmFI", "aaaaaaaaaaa")).map { it.id })
    }

    @Test
    fun `drops live streams, mixes, clips and hour-long compilations`() {
        val items = listOf(
            related("liveliveliv", type = StreamType.LIVE_STREAM),
            related("audiolive01", type = StreamType.AUDIO_LIVE_STREAM),
            PlaylistInfoItem(0, "https://www.youtube.com/playlist?list=RDx7kGuhlXmFI", "Mix - Xefer"),
            related("shortclip01", seconds = 45),
            related("unknownlen1", seconds = -1),
            related("compilation", seconds = 3_600),
            related("keepsong001", seconds = 600),
            related("keepsong002", seconds = 60, type = StreamType.AUDIO_STREAM),
        )

        assertEquals(listOf("keepsong001", "keepsong002"), radioTracks(items, exclude = emptySet()).map { it.id })
    }
}
