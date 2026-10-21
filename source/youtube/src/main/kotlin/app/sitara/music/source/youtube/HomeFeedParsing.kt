package app.sitara.music.source.youtube

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.HomeItem
import app.sitara.music.core.model.HomePage
import app.sitara.music.core.model.HomeShelf
import app.sitara.music.core.model.MoodChip
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.ShelfLayout
import app.sitara.music.core.model.ShelfLink
import app.sitara.music.core.model.Track
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel

private const val SEPARATOR = " • "
private val YEAR = Regex("\\d{4}")

/**
 * A YouTube Music `browse` response for Home, first page or continuation, as a [HomePage]. Only what the app can open
 * survives: songs, albums with their playlist id, playlists and artists. Podcasts, mood buttons and taste-builder
 * shelves are dropped, and so is any shelf left empty.
 */
internal fun parseHomePage(response: JsonObject): HomePage {
    val sections = response.getObject("continuationContents").getObject("sectionListContinuation").takeIf { it.isNotEmpty() }
        ?: response.getObject("contents").getObject("singleColumnBrowseResultsRenderer").getArray("tabs").objects().firstOrNull().orEmpty()
            .getObject("tabRenderer").getObject("content").getObject("sectionListRenderer")
    return HomePage(
        chips = sections.getObject("header").getObject("chipCloudRenderer").getArray("chips").objects().mapNotNull { it.getObject("chipCloudChipRenderer").toChip() },
        shelves = sections.getArray("contents").objects().mapNotNull { it.getObject("musicCarouselShelfRenderer").toShelf() },
        continuation = sections.getArray("continuations").objects().firstOrNull().orEmpty().getObject("nextContinuationData").getString("continuation"),
    )
}

private fun JsonObject.toChip(): MoodChip? {
    val params = getObject("navigationEndpoint").getObject("browseEndpoint").getString("params") ?: return null
    return MoodChip(getObject("text").text(), params, getBoolean("isSelected"))
}

private fun JsonObject.toShelf(): HomeShelf? {
    val header = getObject("header").getObject("musicCarouselShelfBasicHeaderRenderer")
    val title = header.getObject("title").text().ifEmpty { return null }
    val contents = getArray("contents").objects()
    val rows = contents.mapNotNull { it.getObject("musicResponsiveListItemRenderer").takeIf(JsonObject::isNotEmpty)?.toSong() }
    val cards = contents.mapNotNull { it.getObject("musicTwoRowItemRenderer").takeIf(JsonObject::isNotEmpty)?.toCard() }
    val items = (rows.ifEmpty { cards }).distinctBy { it.id }.ifEmpty { return null }
    return HomeShelf(
        title = title,
        strapline = header.getObject("strapline").text().ifEmpty { null },
        layout = if (rows.isNotEmpty()) ShelfLayout.SONG_ROWS else ShelfLayout.CAROUSEL,
        items = items,
        more = header.getObject("moreContentButton").getObject("buttonRenderer").getObject("navigationEndpoint").toLink(),
    )
}

/** A list row: title, then "Artist • 339M plays", then the album. */
private fun JsonObject.toSong(): HomeItem.Song? {
    val columns = getArray("flexColumns").objects().map { it.getObject("musicResponsiveListItemFlexColumnRenderer").getObject("text").getArray("runs").objects() }
    val videoId = getObject("playlistItemData").getString("videoId")?.takeIf(::isVideoId) ?: return null
    val byline = columns.getOrNull(1).orEmpty()
    val album = columns.getOrNull(2)?.firstOrNull()?.takeIf { it.browseId()?.startsWith("MPREb_") == true }?.getString("text").orEmpty()
    val track = Track(
        id = videoId,
        title = columns.firstOrNull()?.firstOrNull()?.getString("text") ?: return null,
        artist = byline.beforeSeparator(),
        album = album,
        durationMs = 0,
        artwork = artwork(videoId, getObject("thumbnail").thumbnails()),
        artistId = byline.artistId(),
    )
    return HomeItem.Song(track)
}

/** A cover card. What it opens decides what it is; anything else (a podcast, an episode) is left out. */
private fun JsonObject.toCard(): HomeItem? {
    val title = getObject("title").text().ifEmpty { return null }
    val subtitle = getObject("subtitle").getArray("runs").objects()
    val images = getObject("thumbnailRenderer").thumbnails()
    val endpoint = getObject("navigationEndpoint")
    endpoint.getObject("watchEndpoint").getString("videoId")?.takeIf(::isVideoId)?.let { videoId ->
        return HomeItem.Song(Track(videoId, title, subtitle.beforeSeparator(), "", 0, artwork(videoId, images), artistId = subtitle.artistId()))
    }
    val browseId = endpoint.browseId() ?: return null
    return when (endpoint.pageType()) {
        "MUSIC_PAGE_TYPE_PLAYLIST" -> browseId.removePrefix("VL").takeIf(::isPlaylistId)?.let { id ->
            HomeItem.PlaylistCard(Playlist(id, title, "", artwork(id, images), emptyList(), curator = subtitle.joinText()))
        }
        // The album page is the album's OLAK5uy… playlist, which the card's play button names.
        "MUSIC_PAGE_TYPE_ALBUM" -> getObject("thumbnailOverlay").getObject("musicItemThumbnailOverlayRenderer").getObject("content")
            .getObject("musicPlayButtonRenderer").getObject("playNavigationEndpoint").getObject("watchPlaylistEndpoint").getString("playlistId")
            ?.takeIf { it.startsWith("OLAK5uy") }?.let { id ->
                // The byline reads "Single • Artist" or "Album • Artist • 2025": the release type, the artists, then the year.
                val credits = subtitle.afterSeparator().ifEmpty { subtitle }
                val year = credits.lastOrNull()?.getString("text")?.takeIf(YEAR::matches)
                val artists = if (year != null) credits.dropLast(1).beforeSeparator() else credits.beforeSeparator()
                HomeItem.AlbumCard(Album(id, title, artists, year?.toInt(), artwork(id, images), emptyList(), credits.artistId()))
            }
        "MUSIC_PAGE_TYPE_ARTIST" -> browseId.takeIf(::isChannelId)?.let { id -> HomeItem.ArtistCard(Artist(id, artistName(title), artwork(id, images))) }
        else -> null
    }
}

private fun JsonObject.toLink(): ShelfLink? {
    val browseId = browseId() ?: return null
    return when {
        browseId.startsWith("VL") && isPlaylistId(browseId.removePrefix("VL")) -> ShelfLink.ToPlaylist(browseId.removePrefix("VL"))
        isChannelId(browseId) -> ShelfLink.ToArtist(browseId)
        else -> null
    }
}

private fun JsonObject.browseId(): String? = getObject("navigationEndpoint").takeIf(JsonObject::isNotEmpty)?.browseId()
    ?: getObject("browseEndpoint").getString("browseId")

private fun JsonObject.pageType(): String? = getObject("browseEndpoint").getObject("browseEndpointContextSupportedConfigs")
    .getObject("browseEndpointContextMusicConfig").getString("pageType")

private fun JsonObject.thumbnails(): List<Image> = getObject("musicThumbnailRenderer").getObject("thumbnail").getArray("thumbnails").objects()
    .mapNotNull { image -> image.getString("url")?.let { Image(it, image.getInt("height"), image.getInt("width"), ResolutionLevel.UNKNOWN) } }

private fun JsonObject.text(): String = getArray("runs").objects().joinText()

private fun List<JsonObject>.joinText(): String = joinToString("") { it.getString("text").orEmpty() }

/** The runs before the first " • ", joined: the artists of a "Artist • 339M plays" byline. */
private fun List<JsonObject>.beforeSeparator(): String = takeWhile { it.getString("text") != SEPARATOR }.joinText()

private fun List<JsonObject>.afterSeparator(): List<JsonObject> = dropWhile { it.getString("text") != SEPARATOR }.drop(1)

private fun List<JsonObject>.artistId(): String? = firstNotNullOfOrNull { run -> run.browseId()?.takeIf(::isChannelId) }

private fun JsonArray.objects(): List<JsonObject> = filterIsInstance<JsonObject>()

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject()
