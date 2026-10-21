package app.sitara.music

import app.sitara.music.core.data.library.PlayHistoryRepository
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Listen
import app.sitara.music.core.model.ListeningStats
import app.sitara.music.core.model.PlayCount
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningHistoryTest {
    private val kept = mutableListOf<Listen>()
    private val history = object : PlayHistoryRepository {
        override fun recent(limit: Int): Flow<List<Track>> = emptyFlow()
        override val counts: Flow<List<PlayCount>> = emptyFlow()
        override fun stats(sinceMs: Long, zone: ZoneId): Flow<ListeningStats> = emptyFlow()
        override suspend fun record(listen: Listen) { kept += listen }
        override suspend fun clear() = kept.clear()
    }
    private val play = Listen(Track("a", "Song", "Artist", "Album", 200_000, Artwork(listOf(0))), startedAtMs = 1, listenedMs = 40_000)

    @Test fun aPlayIsKeptWhileHistoryIsOn() = runBlocking {
        history.recordUnlessPaused(play, MutableStateFlow(SitaraSettings()))
        assertEquals(listOf(play), kept)
    }

    @Test fun nothingIsKeptWhileHistoryIsPaused() = runBlocking {
        history.recordUnlessPaused(play, MutableStateFlow(SitaraSettings(pauseListeningHistory = true)))
        assertEquals(emptyList<Listen>(), kept)
    }
}
