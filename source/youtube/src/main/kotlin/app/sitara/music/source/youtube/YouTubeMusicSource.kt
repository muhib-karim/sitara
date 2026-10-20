package app.sitara.music.source.youtube

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.ArtistPage
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.BrowseSource
import app.sitara.music.source.api.RadioSource
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
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** YouTube Music search (songs, albums, artists, playlists), their pages and radio from related songs; a pasted video link resolves to that one song. Home and library stay with the sample source. */
class YouTubeMusicSource :
    SearchSource,
    BrowseSource,
    RadioSource {
    private val service = ServiceList.YouTube

    override suspend fun search(query: String, filter: SearchFilter): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults.empty(query)
        videoIdFromLink(q)?.let { videoId ->
            val info = io { StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId") }
            return SearchResults(q, listOf(info.toTrack()), emptyList(), emptyList())
        }
        return musicResults(q, filter, musicSearch(q, filter.extractorFilter))
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

    override suspend fun album(id: String): Album? {
        if (!isPlaylistId(id)) return null
        val (info, items) = playlistWithItems(id)
        return albumPage(id, info.name, info.uploaderName, info.uploaderUrl, info.thumbnails, items)
    }

    override suspend fun playlist(id: String): Playlist? {
        if (!isPlaylistId(id)) return null
        val (info, items) = playlistWithItems(id)
        return playlistPage(id, info.name, info.uploaderName, info.thumbnails, items)
    }

    override suspend fun radio(seed: Track, exclude: Set<String>): List<Track>? {
        if (!isVideoId(seed.id)) return null
        val info = io { StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=${seed.id}") }
        return radioTracks(info.relatedItems, exclude + seed.id)
    }

    // shortcut: stops after MAX_PLAYLIST_ITEMS so a huge playlist stays one quick load; page lazily on scroll if people hit the cap.
    private suspend fun playlistWithItems(id: String): Pair<PlaylistInfo, List<StreamInfoItem>> {
        val url = "https://www.youtube.com/playlist?list=$id"
        val info = io { PlaylistInfo.getInfo(service, url) }
        val items = info.relatedItems.toMutableList()
        var next = info.nextPage.takeIf { info.hasNextPage() }
        while (next != null && items.size < MAX_PLAYLIST_ITEMS) {
            val page = next
            val more = io { PlaylistInfo.getMoreItems(service, url, page) }
            items += more.items
            next = more.nextPage.takeIf { more.hasNextPage() }
        }
        return info to items.take(MAX_PLAYLIST_ITEMS)
    }

    private suspend fun musicSearch(query: String, filter: String): List<InfoItem> {
        val info = io { SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query, listOf(filter), "")) }
        // The extractor collects parse failures instead of throwing; with no items at all that is an error, not "nothing found".
        if (info.relatedItems.isEmpty()) info.errors.firstOrNull()?.let { throw it }
        return info.relatedItems
    }

    private suspend fun <T> io(block: () -> T): T = runInterruptible(Dispatchers.IO, block = block)

    private companion object {
        const val MAX_PLAYLIST_ITEMS = 500
    }
}
