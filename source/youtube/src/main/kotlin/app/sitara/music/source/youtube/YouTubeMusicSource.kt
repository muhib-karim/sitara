package app.sitara.music.source.youtube

import app.sitara.music.core.model.ArtistPage
import app.sitara.music.core.model.SearchResults
import app.sitara.music.source.api.BrowseSource
import app.sitara.music.source.api.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** YouTube Music search and artist pages; a pasted video link resolves to that one song. Home and library stay with the sample source. */
class YouTubeMusicSource :
    SearchSource,
    BrowseSource {
    private val service = ServiceList.YouTube

    override suspend fun search(query: String): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults.empty(query)
        videoIdFromLink(q)?.let { videoId ->
            val info = io { StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId") }
            return SearchResults(q, listOf(info.toTrack()), emptyList(), emptyList())
        }
        // Rows are keyed by video id, and a search can list the same song twice.
        val tracks = musicSearch(q, MUSIC_SONGS).filterIsInstance<StreamInfoItem>().mapNotNull { it.toTrack() }.distinctBy { it.id }
        return SearchResults(q, tracks, emptyList(), emptyList())
    }

    override suspend fun artist(id: String): ArtistPage? {
        if (!isChannelId(id)) return null
        val channel = io { ChannelInfo.getInfo(service, "https://www.youtube.com/channel/$id") }
        return coroutineScope {
            val releases = async {
                channel.tabs.firstOrNull { ChannelTabs.ALBUMS in it.contentFilters }?.let { tab -> io { ChannelTabInfo.getInfo(service, tab) }.relatedItems }.orEmpty()
            }
            val songs = async { musicSearch(artistName(channel.name), MUSIC_SONGS) }
            artistPage(channel, releases.await(), songs.await())
        }
    }

    private suspend fun musicSearch(query: String, filter: String): List<InfoItem> {
        val info = io { SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query, listOf(filter), "")) }
        // The extractor collects parse failures instead of throwing; with no items at all that is an error, not "nothing found".
        if (info.relatedItems.isEmpty()) info.errors.firstOrNull()?.let { throw it }
        return info.relatedItems
    }

    private suspend fun <T> io(block: () -> T): T = runInterruptible(Dispatchers.IO, block = block)
}
