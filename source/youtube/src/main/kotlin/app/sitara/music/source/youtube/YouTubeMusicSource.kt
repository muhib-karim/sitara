package app.sitara.music.source.youtube

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.ArtistPage
import app.sitara.music.core.model.Chart
import app.sitara.music.core.model.HomePage
import app.sitara.music.core.model.MoodChip
import app.sitara.music.core.model.MoodSection
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.BrowseSource
import app.sitara.music.source.api.ChartsSource
import app.sitara.music.source.api.HomeFeedSource
import app.sitara.music.source.api.MoodsSource
import app.sitara.music.source.api.RadioSource
import app.sitara.music.source.api.SearchSource
import app.sitara.music.source.api.SearchSuggestionSource
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getValidJsonResponseBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getYoutubeMusicClientVersion
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getYoutubeMusicHeaders
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * YouTube Music search (songs, albums, artists, playlists) with its suggestions, their pages, radio from related songs, the
 * YouTube Charts trending list, YouTube Music's own Home and its Moods & genres; a pasted video link resolves to that one song.
 */
class YouTubeMusicSource :
    SearchSource,
    SearchSuggestionSource,
    ChartsSource,
    HomeFeedSource,
    MoodsSource,
    BrowseSource,
    RadioSource {
    private val service = ServiceList.YouTube

    // YouTube answers a Home continuation only for the visitor that was sent the first page.
    @Volatile private var visitorData: String? = null

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

    /** The playlist with up to [limit] of its songs, read page by page, for an import; null when [id] is not a playlist id. */
    suspend fun wholePlaylist(id: String, limit: Int): Playlist? {
        if (!isPlaylistId(id)) return null
        val (info, items) = playlistWithItems(id, limit)
        return playlistPage(id, info.name, info.uploaderName, info.thumbnails, items)
    }

    override suspend fun radio(seed: Track, exclude: Set<String>): List<Track>? {
        if (!isVideoId(seed.id)) return null
        val info = io { StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=${seed.id}") }
        return songsFrom(info.relatedItems, exclude + seed.id)
    }

    /** The device country's trending music videos, or the United Kingdom's where YouTube Charts has no chart for that country. */
    override suspend fun trending(): Chart {
        val country = Locale.getDefault().country.ifEmpty { FALLBACK_COUNTRY }
        return try {
            chart(country)
        } catch (e: UnsupportedContentInCountryException) {
            if (country == FALLBACK_COUNTRY) throw e
            chart(FALLBACK_COUNTRY)
        }
    }

    private suspend fun chart(country: String): Chart {
        val info = io {
            val extractor = service.kioskList.getExtractorById(TRENDING_MUSIC_KIOSK, null)
            extractor.forceContentCountry(ContentCountry(country))
            extractor.fetchPage()
            KioskInfo.getInfo(extractor)
        }
        // Like search, the extractor logs parse failures; an empty chart with an error is a failure.
        if (info.relatedItems.isEmpty()) info.errors.firstOrNull()?.let { throw it }
        return Chart(chartLabel(country), songsFrom(info.relatedItems))
    }

    /** YouTube Music's signed-out Home for the device's language and country; one with nothing the app can show is an error. */
    override suspend fun home(chip: MoodChip?): HomePage {
        val page = musicHome(BROWSE_URL, mapOf("browseId" to HOME_BROWSE_ID) + chip?.let { mapOf("params" to it.params) }.orEmpty())
        if (chip == null && page.shelves.isEmpty()) throw IOException("YouTube Music sent a Home with nothing to show")
        return page
    }

    override suspend fun moreHome(continuation: String): HomePage {
        val token = URLEncoder.encode(continuation, Charsets.UTF_8)
        return musicHome("$BROWSE_URL&ctoken=$token&continuation=$token&type=next", emptyMap())
    }

    private suspend fun musicHome(url: String, fields: Map<String, String>): HomePage {
        val response = music(url, fields)
        response.getObject("responseContext").getString("visitorData")?.let { visitorData = it }
        return parseHomePage(response)
    }

    override suspend fun suggestions(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return parseSuggestions(music(SUGGESTIONS_URL, mapOf("input" to q)))
    }

    override suspend fun moods(): List<MoodSection> =
        parseMoods(music(BROWSE_URL, mapOf("browseId" to MOODS_BROWSE_ID))).ifEmpty { throw IOException("YouTube Music sent no moods or genres") }

    override suspend fun moodPage(params: String): HomePage =
        parseHomePage(music(BROWSE_URL, mapOf("browseId" to MOOD_PAGE_BROWSE_ID, "params" to params)))

    /** A YouTube Music API request as the WEB_REMIX client in the device's language and country, as the Home visitor once there is one. */
    internal suspend fun music(url: String, fields: Map<String, String>): JsonObject {
        val locale = Locale.getDefault()
        return io {
            val client = JsonObject.builder().`object`("context").`object`("client")
                .value("clientName", "WEB_REMIX")
                .value("clientVersion", getYoutubeMusicClientVersion())
                .value("hl", locale.language.ifEmpty { "en" })
                .value("gl", locale.country.ifEmpty { FALLBACK_COUNTRY })
            visitorData?.let { client.value("visitorData", it) }
            val request = client.end().end()
            fields.forEach { (name, value) -> request.value(name, value) }
            val body = JsonWriter.string(request.done()).toByteArray()
            val headers = getYoutubeMusicHeaders() + visitorData?.let { mapOf("X-Goog-Visitor-Id" to listOf(it)) }.orEmpty()
            JsonParser.`object`().from(getValidJsonResponseBody(NewPipe.getDownloader().postWithContentTypeJson(url, headers, body)))
        }
    }

    // shortcut: a playlist page stops after MAX_PLAYLIST_ITEMS so a huge playlist stays one quick load; page lazily on scroll if people hit the cap.
    private suspend fun playlistWithItems(id: String, limit: Int = MAX_PLAYLIST_ITEMS): Pair<PlaylistInfo, List<StreamInfoItem>> {
        val url = "https://www.youtube.com/playlist?list=$id"
        val info = io { PlaylistInfo.getInfo(service, url) }
        val items = info.relatedItems.toMutableList()
        var next = info.nextPage.takeIf { info.hasNextPage() }
        while (next != null && items.size < limit) {
            val page = next
            val more = io { PlaylistInfo.getMoreItems(service, url, page) }
            items += more.items
            next = more.nextPage.takeIf { more.hasNextPage() }
        }
        return info to items.take(limit)
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
        const val TRENDING_MUSIC_KIOSK = "trending_music"
        const val FALLBACK_COUNTRY = "GB"
        const val HOME_BROWSE_ID = "FEmusic_home"
        const val MOODS_BROWSE_ID = "FEmusic_moods_and_genres"
        const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
        const val SUGGESTIONS_URL = "https://music.youtube.com/youtubei/v1/music/get_search_suggestions?prettyPrint=false"
    }
}
