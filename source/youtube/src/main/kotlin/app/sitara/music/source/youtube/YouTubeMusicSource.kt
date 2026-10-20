package app.sitara.music.source.youtube

import app.sitara.music.core.model.SearchResults
import app.sitara.music.source.api.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Song search on YouTube Music. Browse and library stay with the sample source for now. */
class YouTubeMusicSource : SearchSource {
    override suspend fun search(query: String): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults.empty(query)
        val service = ServiceList.YouTube
        val info = runInterruptible(Dispatchers.IO) { SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(q, listOf(MUSIC_SONGS), "")) }
        // The extractor collects parse failures instead of throwing; with no songs at all that is an error, not "nothing found".
        if (info.relatedItems.isEmpty()) info.errors.firstOrNull()?.let { throw it }
        // Rows are keyed by video id, and a search can list the same song twice.
        val tracks = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toTrack() }.distinctBy { it.id }
        return SearchResults(q, tracks, emptyList(), emptyList())
    }
}
