package app.sitara.music.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkTile
import app.sitara.music.core.designsystem.EmptyCollectionArtwork
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.designsystem.byline
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track

/** Trending songs, then the listener's own shelves; a fresh install with nothing played says how Home fills up. */
@Composable
fun HomeScreen(
    greeting: String,
    feed: HomeFeed,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onRetryTrending: () -> Unit,
    onAlbum: (Album) -> Unit,
    onPlaylist: (PlaylistSummary) -> Unit,
    onTrackMenu: (Track) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(modifier.fillMaxSize().testTag("home-content"), state = listState, contentPadding = contentPadding) {
        item(key = "title") { LargeTitle(greeting) }
        item(key = "trending-heading") {
            SectionHeader("Trending now", subtitle = (feed.trending as? TrendingState.Ready)?.chart?.label, modifier = Modifier.padding(top = 12.dp))
        }
        item(key = "trending") {
            when (val trending = feed.trending) {
                TrendingState.Loading -> HomeMessage("Loading the charts…")
                TrendingState.Failed -> Column {
                    HomeMessage("The charts didn’t load. Check your connection, then try again.")
                    PillButton("Try again", onRetryTrending, Modifier.padding(horizontal = SitaraSpacing.Gutter))
                }
                is TrendingState.Ready ->
                    if (trending.chart.tracks.isEmpty()) HomeMessage("Nothing is charting here right now.") else QuickPicks(trending.chart.tracks, onPlay, onTrackMenu)
            }
        }
        if (feed.libraryLoaded && !feed.hasLibrary) {
            item(key = "welcome") {
                SectionHeader("Make Home yours", subtitle = "Songs you play, like and save show up here.", modifier = Modifier.padding(top = 20.dp))
            }
        }
        trackShelf("recent", "Recently played", feed.recent, onPlay)
        if (feed.jumpBackIn.isNotEmpty()) {
            item(key = "h-jump") { SectionHeader("Jump back in", modifier = Modifier.padding(top = 12.dp)) }
            item(key = "r-jump") {
                Rail("jump") {
                    items(feed.jumpBackIn, key = { it.key }) { saved ->
                        when (saved) {
                            is SavedCollection.OwnPlaylist -> ArtworkTile(
                                saved.playlist.name, songCount(saved.playlist.songCount), saved.playlist.artwork ?: EmptyCollectionArtwork,
                                { onPlaylist(saved.playlist) }, Modifier.width(156.dp),
                            )
                            is SavedCollection.SavedAlbum -> ArtworkTile(saved.album.title, saved.album.byline, saved.album.artwork, { onAlbum(saved.album) }, Modifier.width(156.dp))
                        }
                    }
                }
            }
        }
        trackShelf("likes", "From your likes", feed.fromLikes, onPlay)
    }
}

private val SavedCollection.key: String get() = when (this) {
    is SavedCollection.OwnPlaylist -> "playlist-${playlist.id}"
    is SavedCollection.SavedAlbum -> "album-${album.id}"
}

private fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"

private fun LazyListScope.trackShelf(key: String, title: String, tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit) {
    if (tracks.isEmpty()) return
    item(key = "h-$key") { SectionHeader(title, modifier = Modifier.padding(top = 12.dp)) }
    item(key = "r-$key") {
        Rail(key) {
            items(tracks.size, key = { tracks[it].id }) { index ->
                val track = tracks[index]
                ArtworkTile(track.title, track.artist, track.artwork, { onPlay(tracks, index) }, Modifier.width(156.dp))
            }
        }
    }
}

@Composable
private fun Rail(key: String, content: LazyListScope.() -> Unit) {
    LazyRow(
        modifier = Modifier.testTag("rail-$key"),
        contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun HomeMessage(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp))
}

@Composable
private fun QuickPicks(tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit, onTrackMenu: (Track) -> Unit) {
    val pages = remember(tracks) { quickPickPages(tracks) }
    val pager = rememberPagerState { pages.size }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val columns = if (availableWidth / LocalDensity.current.fontScale < 320.dp) 1 else 2
        Column {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().testTag("quick-picks")) { page ->
                Column(Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter)) {
                    pages[page].chunked(columns).forEachIndexed { row, pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEachIndexed { column, track ->
                                TrackRow(
                                    track,
                                    onClick = { onPlay(tracks, page * 4 + row * columns + column) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    // Two columns leave no room for the button; a long press opens the same menu.
                                    onMore = { onTrackMenu(track) },
                                    moreButton = columns == 1,
                                )
                            }
                            if (pair.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${pages.size} · Swipe for more",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
                )
            }
        }
    }
}
