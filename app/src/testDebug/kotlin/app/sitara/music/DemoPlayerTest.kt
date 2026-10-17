package app.sitara.music

import app.sitara.music.core.model.Lyrics
import app.sitara.music.source.fake.SampleCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoPlayerTest {
    @Test
    fun queueSelectionAndSeekingStayWithinTheSeededQueue() {
        val demo = DemoPlayer(SampleCatalog.tracks, Lyrics.Empty)
        demo.playQueueIndex(1)
        assertEquals(SampleCatalog.tracks[2], demo.state.track)
        demo.seekTo(Long.MAX_VALUE)
        assertEquals(demo.state.durationMs, demo.state.positionMs)
        demo.previous()
        assertEquals(SampleCatalog.tracks[1], demo.state.track)
        demo.seekTo(-1)
        assertEquals(0L, demo.state.positionMs)
    }

    @Test
    fun choosingAnAlbumReplacesTheShuffleRestoreQueue() {
        val demo = DemoPlayer(SampleCatalog.tracks, Lyrics.Empty)
        demo.toggleShuffle()
        val album = SampleCatalog.albums.last().tracks
        demo.play(album, 0)
        demo.toggleShuffle()
        demo.toggleShuffle()
        assertEquals(album.drop(1), demo.state.upNext)
    }

    @Test
    fun shufflePreservesCurrentTrackAndRestoresOriginalOrder() {
        val demo = DemoPlayer(SampleCatalog.tracks, Lyrics.Empty)
        demo.next()
        val current = demo.state.track
        demo.toggleShuffle()
        assertEquals(current, demo.state.track)
        demo.toggleShuffle()
        assertEquals(current, demo.state.track)
        assertEquals(SampleCatalog.tracks.drop(2), demo.state.upNext)
    }
}
