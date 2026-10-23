package app.sitara.music.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkTile
import app.sitara.music.core.designsystem.EmptyCollectionArtwork
import app.sitara.music.core.designsystem.GlassSurface
import app.sitara.music.core.designsystem.HeaderAction
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SelectionPill
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.designsystem.byline
import app.sitara.music.core.designsystem.preview
import app.sitara.music.core.designsystem.previewOnLongClick
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.HomeItem
import app.sitara.music.core.model.HomeShelf
import app.sitara.music.core.model.MoodChip
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.ShelfLayout
import app.sitara.music.core.model.ShelfLink
import app.sitara.music.core.model.Track

internal val CardWidth = 150.dp

/**
 * Mood chips, then the catalogue's shelves with the listener's own (Listen again, Your likes, Jump back in) after the
 * first one; more shelves load as the list nears its end. When the feed fails, the trending chart and the listener's
 * shelves stand in, so Home is never empty. A chosen mood shows only the catalogue's shelves for it.
 */
@Composable
fun HomeScreen(
    greeting: String,
    feed: HomeFeed,
    actions: HomeActions,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
    onOwnPlaylist: (PlaylistSummary) -> Unit,
    onShelfLink: (ShelfLink) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
) {
    val online = feed.online
    val mood = online.selectedChip
    val ready = online.feed as? FeedState.Ready

    // Keys carry the mood, so the shelves of another mood start unscrolled instead of inheriting a row's position.
    fun LazyListScope.feedShelf(index: Int, shelf: HomeShelf) =
        feedShelf("${mood?.title}-$index", index, shelf, onPlay, onTrackMenu, onAlbum, onPlaylist, onArtist, onShelfLink)

    LazyColumn(modifier.fillMaxSize().testTag("home-content"), state = listState, contentPadding = contentPadding) {
        item(key = "title") { LargeTitle(greeting) }
        when {
            online.chips.isNotEmpty() -> item(key = "chips") { MoodChips(online.chips, actions::selectMood) }
            online.feed == FeedState.Loading -> item(key = "chips-loading", contentType = "skeleton") { ChipsSkeleton() }
        }
        when (val state = online.feed) {
            FeedState.Loading -> item(key = "loading", contentType = "skeleton") { RowsSkeleton(Modifier.testTag("home-loading")) }
            FeedState.Failed -> {
                item(key = "offline") { OfflineNotice(actions::retryHome) }
                if (mood == null) trending(online.trending, onPlay, onTrackMenu)
            }
            is FeedState.Ready -> if (state.shelves.isEmpty()) {
                item(key = "nothing") {
                    HomeMessage(mood?.let { stringResource(R.string.home_nothing_under, it.title) } ?: stringResource(R.string.home_nothing_under_this_mood))
                }
            } else {
                feedShelf(0, state.shelves.first())
            }
        }
        if (mood == null) {
            if (online.feed == FeedState.Failed && feed.libraryLoaded && !feed.hasLibrary) {
                item(key = "welcome") {
                    SectionHeader(stringResource(R.string.home_make_home_yours), subtitle = stringResource(R.string.home_make_home_yours_description), modifier = Modifier.padding(top = 20.dp))
                }
            }
            trackShelf("recent", R.string.home_listen_again, feed.recent, onPlay, onTrackMenu)
            trackShelf("likes", R.string.home_your_likes, feed.fromLikes, onPlay, onTrackMenu)
            if (feed.jumpBackIn.isNotEmpty()) {
                item(key = "h-jump") { SectionHeader(stringResource(R.string.home_jump_back_in), modifier = Modifier.padding(top = 12.dp)) }
                item(key = "r-jump") {
                    Rail("jump") {
                        items(feed.jumpBackIn, key = { it.key }) { saved ->
                            when (saved) {
                                is SavedCollection.OwnPlaylist -> ArtworkTile(
                                    saved.playlist.name, songCount(saved.playlist.songCount), EmptyCollectionArtwork,
                                    { onOwnPlaylist(saved.playlist) }, Modifier.width(CardWidth), covers = saved.playlist.covers,
                                    onLongClick = previewOnLongClick(saved.playlist.preview()),
                                )
                                is SavedCollection.SavedAlbum -> ArtworkTile(
                                    saved.album.title, saved.album.byline, saved.album.artwork, { onAlbum(saved.album) }, Modifier.width(CardWidth),
                                    onLongClick = previewOnLongClick(saved.album.preview()),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (online.feed == FeedState.Loading) {
            items(2, key = { "loading-$it" }, contentType = { "skeleton" }) { CarouselSkeleton() }
        }
        ready?.shelves?.drop(1)?.forEachIndexed { index, shelf -> feedShelf(index + 1, shelf) }
        if (ready != null && (ready.continuation != null || ready.paging == Paging.FAILED)) {
            item(key = "paging", contentType = "skeleton") {
                if (ready.paging == Paging.FAILED) {
                    Column(Modifier.padding(top = 12.dp)) {
                        HomeMessage(stringResource(R.string.home_more_failed))
                        PillButton(stringResource(R.string.home_try_again), actions::loadMoreHome, Modifier.padding(horizontal = SitaraSpacing.Gutter))
                    }
                } else {
                    CarouselSkeleton()
                }
            }
        }
    }

    // The next page loads while the last shelves come into view, so scrolling rarely meets the placeholder.
    val nearEnd by remember(listState) {
        derivedStateOf { listState.layoutInfo.run { totalItemsCount > 0 && (visibleItemsInfo.lastOrNull()?.index ?: 0) >= totalItemsCount - 3 } }
    }
    LaunchedEffect(nearEnd, ready?.continuation, ready?.paging) {
        if (nearEnd && ready?.continuation != null && ready.paging == Paging.IDLE) actions.loadMoreHome()
    }
}

private fun LazyListScope.trending(trending: TrendingState, onPlay: (List<Track>, Int) -> Unit, onTrackMenu: (Track) -> Unit) {
    when (trending) {
        TrendingState.Loading -> item(key = "trending-loading", contentType = "skeleton") { RowsSkeleton() }
        // The notice above already says the network is not answering; a second error would repeat it.
        TrendingState.Failed -> Unit
        is TrendingState.Ready -> {
            item(key = "trending-heading") { SectionHeader(stringResource(R.string.home_trending_now), subtitle = trending.chart.label, modifier = Modifier.padding(top = 12.dp)) }
            item(key = "trending") {
                if (trending.chart.tracks.isEmpty()) HomeMessage(stringResource(R.string.home_nothing_charting)) else QuickPicks(trending.chart.tracks, onPlay, onTrackMenu)
            }
        }
    }
}

private fun List<HomeItem>.songs(): List<Track> = filterIsInstance<HomeItem.Song>().map { it.track }

// YouTube Music writes straplines in capitals ("FOR RELAXING"); under a title they read as a quiet line instead.
private fun sentenceCase(text: String) = text.lowercase().replaceFirstChar { it.titlecase() }

private val SavedCollection.key: String get() = when (this) {
    is SavedCollection.OwnPlaylist -> "playlist-${playlist.id}"
    is SavedCollection.SavedAlbum -> "album-${album.id}"
}

@Composable
private fun songCount(count: Int) = pluralStringResource(R.plurals.home_songs, count, count)

/** Shelf [index] of a catalogue feed: its header with a More link where there is one, then song rows or a carousel. */
internal fun LazyListScope.feedShelf(
    key: String,
    index: Int,
    shelf: HomeShelf,
    onPlay: (List<Track>, Int) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
    onShelfLink: (ShelfLink) -> Unit,
) {
    item(key = "feed-h-$key", contentType = "header") {
        SectionHeader(
            shelf.title, Modifier.padding(top = 12.dp), subtitle = shelf.strapline?.let(::sentenceCase),
            action = shelf.more?.let { link -> @Composable { HeaderAction(stringResource(R.string.home_more), { onShelfLink(link) }, description = stringResource(R.string.home_more_from, shelf.title)) } },
        )
    }
    item(key = "feed-$key", contentType = shelf.layout) {
        when (shelf.layout) {
            ShelfLayout.SONG_ROWS -> QuickPicks(remember(shelf) { shelf.items.songs() }, onPlay, onTrackMenu)
            ShelfLayout.CAROUSEL -> FeedCarousel("feed-$index", shelf.items, onPlay, onTrackMenu, onAlbum, onPlaylist, onArtist)
        }
    }
}

private fun LazyListScope.trackShelf(key: String, title: Int, tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit, onTrackMenu: (Track) -> Unit) {
    if (tracks.isEmpty()) return
    item(key = "h-$key") { SectionHeader(stringResource(title), modifier = Modifier.padding(top = 12.dp)) }
    item(key = "r-$key") {
        Rail(key) {
            items(tracks.size, key = { tracks[it].id }) { index ->
                val track = tracks[index]
                ArtworkTile(track.title, track.artist, track.artwork, { onPlay(tracks, index) }, Modifier.width(CardWidth), onLongClick = { onTrackMenu(track) })
            }
        }
    }
}

@Composable
private fun FeedCarousel(
    key: String,
    items: List<HomeItem>,
    onPlay: (List<Track>, Int) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
) {
    val songs = remember(items) { items.songs() }
    Rail(key) {
        items(items, key = { it.id }) { item ->
            val card = Modifier.width(CardWidth)
            when (item) {
                is HomeItem.Song -> ArtworkTile(
                    item.track.title, item.track.artist, item.track.artwork, { onPlay(songs, songs.indexOf(item.track)) }, card,
                    onLongClick = { onTrackMenu(item.track) },
                )
                is HomeItem.AlbumCard -> ArtworkTile(item.album.title, item.album.byline, item.album.artwork, { onAlbum(item.album) }, card, onLongClick = previewOnLongClick(item.album.preview()))
                is HomeItem.PlaylistCard -> ArtworkTile(
                    item.playlist.title, item.playlist.curator, item.playlist.artwork, { onPlaylist(item.playlist) }, card, onLongClick = previewOnLongClick(item.playlist.preview()),
                )
                is HomeItem.ArtistCard -> ArtworkTile(item.artist.name, stringResource(R.string.home_artist), item.artist.artwork, { onArtist(item.artist) }, card, round = true, onLongClick = previewOnLongClick(item.artist.preview()))
            }
        }
    }
}

@Composable
private fun Rail(key: String, content: LazyListScope.() -> Unit) {
    LazyRow(
        modifier = Modifier.testTag("rail-$key"),
        contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(CardGap),
        content = content,
    )
}

internal val CardGap = 12.dp

@Composable
private fun MoodChips(chips: List<MoodChip>, onSelect: (MoodChip) -> Unit) {
    LazyRow(
        modifier = Modifier.testTag("mood-chips"),
        contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.title }) { chip -> SelectionPill(chip.title, chip.selected, { onSelect(chip) }) }
    }
}

@Composable
private fun OfflineNotice(onRetry: () -> Unit) {
    GlassSurface(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(SitaraRadius.Artwork)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.home_didnt_load), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.home_offline_description),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { PillButton(stringResource(R.string.home_try_again), onRetry) }
        }
    }
}

@Composable
internal fun HomeMessage(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp))
}

/** Songs four to a page, each page a column of rows; the next page peeks in at the edge, which says the shelf scrolls. */
@Composable
private fun QuickPicks(tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit, onTrackMenu: (Track) -> Unit) {
    val pages = remember(tracks) { quickPickPages(tracks) }
    val pager = rememberPagerState { pages.size }
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxWidth().testTag("quick-picks"),
        contentPadding = PaddingValues(start = SitaraSpacing.Gutter, end = if (pages.size > 1) PagePeek else SitaraSpacing.Gutter),
        pageSpacing = CardGap,
        verticalAlignment = Alignment.Top,
    ) { page ->
        Column(Modifier.fillMaxWidth()) {
            pages[page].forEachIndexed { row, track ->
                TrackRow(
                    track,
                    onClick = { onPlay(tracks, page * 4 + row) },
                    contentPadding = PaddingValues(vertical = 6.dp),
                    onMore = { onTrackMenu(track) },
                )
            }
        }
    }
}

private val PagePeek = 48.dp
