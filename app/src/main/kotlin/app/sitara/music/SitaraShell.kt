package app.sitara.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ScrollEdge
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.ShelfLink
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.HomeActions
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.home.HomeScreen
import app.sitara.music.feature.library.AddToPlaylist
import app.sitara.music.feature.library.DetailScreen
import app.sitara.music.feature.library.LibraryActions
import app.sitara.music.feature.library.LibraryScreen
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.SavedIds
import app.sitara.music.feature.library.SongActions
import app.sitara.music.feature.player.EqualiserScreen
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerSheet
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.player.equaliserSummary
import app.sitara.music.feature.search.SearchScreen
import app.sitara.music.feature.search.SearchState
import app.sitara.music.feature.settings.SettingsScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", SitaraIcons.Home),
    SEARCH("Search", SitaraIcons.Search),
    LIBRARY("Library", SitaraIcons.Library),
    SETTINGS("Settings", SitaraIcons.Settings),
}

@Composable
fun SitaraRoot(vm: AppViewModel, systemReducedMotion: Boolean) {
    val settings by vm.settings.collectAsState()
    val player by vm.playerState.collectAsState()
    val home by vm.home.collectAsState()
    val library by vm.library.collectAsState()
    val savedIds by vm.savedIds.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val query by vm.query.collectAsState()
    val searchFilter by vm.searchFilter.collectAsState()
    val search by vm.search.collectAsState()
    val downloads by vm.downloads.collectAsState()
    SitaraShell(
        settings = settings.let { if (systemReducedMotion) it.copy(reducedMotion = true) else it },
        home = home,
        library = library,
        savedIds = savedIds,
        playlists = playlists,
        query = query,
        searchFilter = searchFilter,
        search = search,
        player = player,
        actions = vm,
        libraryActions = vm,
        details = vm,
        onQueryChange = { vm.query.value = it },
        onSearchFilterChange = { vm.searchFilter.value = it },
        onRetrySearch = vm::retrySearch,
        homeActions = vm.homeActions,
        onPlay = vm::play,
        onShuffle = vm::shuffle,
        onSettingsChange = vm::updateSettings,
        tabRequests = vm.requestedTabs,
        downloads = downloads,
        onRemoveAllDownloads = vm::removeAllDownloads,
    )
}

/** App chrome and saveable browse navigation (tabs plus a back stack of detail pages); playback/data are supplied by the caller. */
@Composable
fun SitaraShell(
    settings: SitaraSettings,
    home: HomeFeed,
    library: LibraryState,
    query: String,
    searchFilter: SearchFilter,
    search: SearchState,
    player: PlayerUiState,
    actions: PlayerActions,
    details: DetailPages,
    onQueryChange: (String) -> Unit,
    onSearchFilterChange: (SearchFilter) -> Unit,
    onRetrySearch: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onSettingsChange: (SitaraSettings) -> Unit,
    initialTab: Tab = Tab.HOME,
    initialDetail: DetailRoute? = null,
    initialSettingsOpen: Boolean = false,
    playerExpanded: Boolean = false,
    playerPage: Int = 0,
    greetingText: String = greeting(),
    tabRequests: Flow<Tab> = emptyFlow(),
    savedIds: SavedIds = SavedIds(),
    playlists: List<PlaylistSummary> = emptyList(),
    libraryActions: LibraryActions = LibraryActions.None,
    homeActions: HomeActions = HomeActions.None,
    downloads: List<SongDownload> = emptyList(),
    onRemoveAllDownloads: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(if (initialSettingsOpen) Tab.SETTINGS else initialTab) }
    var detailStack by rememberSaveable(stateSaver = DetailStackSaver) { mutableStateOf(listOfNotNull(initialDetail)) }
    val browseState = rememberSaveableStateHolder()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTrack by remember { mutableStateOf<Track?>(null) }
    var equaliserOpen by rememberSaveable { mutableStateOf(false) }
    // Reading on (scrolling towards the end) folds the dock into one row; scrolling back or its tab unfolds it.
    var dockFolded by rememberSaveable { mutableStateOf(false) }
    val foldOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                when {
                    consumed.y < -FOLD_SLOP -> dockFolded = true
                    consumed.y > FOLD_SLOP -> dockFolded = false
                }
                return Offset.Zero
            }
        }
    }
    val openMenu: (Track) -> Unit = { menuTrack = it }
    val open: (DetailRoute) -> Unit = { detailStack = detailStack + it }
    val closeDetails: () -> Unit = { detailStack = emptyList() }
    LaunchedEffect(tabRequests) {
        tabRequests.collect { closeDetails(); tab = it }
    }
    BackHandler(enabled = detailStack.isNotEmpty() || tab == Tab.SETTINGS) {
        when {
            detailStack.isNotEmpty() -> detailStack = detailStack.dropLast(1)
            equaliserOpen -> equaliserOpen = false
            else -> tab = Tab.HOME
        }
    }
    SitaraTheme(settings.themeMode, settings.darkMode, settings.reducedMotion) {
        val haze = rememberHazeState()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val fontScale = LocalDensity.current.fontScale
        val miniHeight = SitaraSpacing.MiniPlayerHeight * fontScale.coerceAtLeast(1f)
        val minimumDockHeight = SitaraSpacing.NavigationHeight + (if (player.track != null) miniHeight else 0.dp)
        // The unfolded height: content pads for the dock at its tallest, so folding never moves the page.
        var measuredDockHeight by remember(player.track != null, fontScale) { mutableStateOf(0.dp) }
        val dockHeight = maxOf(minimumDockHeight, measuredDockHeight)
        val contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = dockHeight + navBottom + SitaraSpacing.DockBottom + SitaraSpacing.Section,
        )
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxSize().hazeSource(haze).nestedScroll(foldOnScroll)) {
                val route = detailStack.lastOrNull()
                val openAlbum: (Album) -> Unit = { open(DetailRoute.Album(it.id)) }
                val openArtist: (Artist) -> Unit = { open(DetailRoute.Artist(it.id)) }
                // The stack depth keeps two visits to the same page apart, each with its own scroll position.
                browseState.SaveableStateProvider(route?.let { "${detailStack.size}-${it.encode()}" } ?: "tab-${tab.name}") {
                    if (route != null) {
                        val state by remember(details, route) { details.state(route) }.collectAsState()
                        DetailScreen(
                            state, settings, savedIds, libraryActions,
                            onBack = { detailStack = detailStack.dropLast(1) },
                            onRetry = { details.retry(route) },
                            onPlay = onPlay,
                            onShuffle = onShuffle,
                            onArtist = { open(DetailRoute.Artist(it)) },
                            onAlbum = { open(DetailRoute.Album(it)) },
                            onTrackMenu = openMenu,
                            contentPadding = contentPadding,
                        )
                    } else {
                        when (tab) {
                            Tab.HOME -> HomeScreen(
                                greetingText, home, homeActions, onPlay, openMenu, openAlbum,
                                onPlaylist = { open(DetailRoute.Playlist(it.id)) },
                                onArtist = openArtist,
                                onOwnPlaylist = { open(DetailRoute.UserPlaylist(it.id)) },
                                onShelfLink = { link ->
                                    when (link) {
                                        is ShelfLink.ToPlaylist -> open(DetailRoute.Playlist(link.id))
                                        is ShelfLink.ToArtist -> open(DetailRoute.Artist(link.id))
                                    }
                                },
                                contentPadding = contentPadding,
                            )
                            Tab.SEARCH -> SearchScreen(
                                query, onQueryChange, searchFilter, onSearchFilterChange, search, onRetrySearch,
                                listOf("Lo-fi beats", "Jazz piano", "Classical focus"), onPlay,
                                openAlbum, openArtist, { open(DetailRoute.Playlist(it.id)) }, openMenu, contentPadding = contentPadding,
                            )
                            Tab.LIBRARY -> LibraryScreen(
                                library, downloads, onPlay,
                                onLikedSongs = { open(DetailRoute.LikedSongs) },
                                onPlaylist = { open(DetailRoute.UserPlaylist(it.id)) },
                                onAlbum = openAlbum,
                                onArtist = openArtist,
                                onNewPlaylist = { libraryActions.createPlaylist(it, null) },
                                library = libraryActions,
                                contentPadding = contentPadding,
                            )
                            Tab.SETTINGS -> if (equaliserOpen) {
                                EqualiserScreen(
                                    player.equaliser, player.systemEqualiser, actions::setEqualiser, actions::openSystemEqualiser,
                                    onBack = { equaliserOpen = false }, contentPadding = contentPadding,
                                )
                            } else {
                                SettingsScreen(
                                    settings, onSettingsChange, downloads, onRemoveAllDownloads,
                                    onBack = { tab = Tab.HOME }, contentPadding = contentPadding,
                                    equaliserSummary = equaliserSummary(player.equaliser, player.systemEqualiser),
                                    onOpenEqualiser = { equaliserOpen = true },
                                )
                            }
                        }
                    }
                }
            }
            ScrollEdge(haze, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(contentPadding.calculateTopPadding() + 16.dp))
            ScrollEdge(haze, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(navBottom + 16.dp), top = false)
            PlayerSheet(
                player,
                actions,
                bottomReserved = navBottom + SitaraSpacing.DockBottom,
                initiallyExpanded = playerExpanded,
                initialPage = playerPage,
                haze = haze,
                folded = dockFolded,
                navigation = { accent, folded ->
                    FloatingNav(tab, accent, folded, onSelect = { closeDetails(); equaliserOpen = false; dockFolded = false; tab = it }, onUnfold = { dockFolded = false })
                },
                onDockHeightChanged = { measuredDockHeight = maxOf(measuredDockHeight, it) },
                onArtist = { open(DetailRoute.Artist(it)) },
                onAddToPlaylist = { playlistTrack = it },
            )
            menuTrack?.let { track ->
                SitaraSheet(onDismiss = { menuTrack = null }) { close ->
                    val liked = track.id in savedIds.tracks
                    SongActions(
                        track, liked, savedIds.downloads[track.id],
                        onToggleLike = { close { libraryActions.setLiked(track, !liked) } },
                        onPlayNext = { close { actions.playNext(track) } },
                        onAddToQueue = { close { actions.addToQueue(track) } },
                        onAddToPlaylist = { close { playlistTrack = track } },
                        onDownload = { close { libraryActions.download(listOf(track)) } },
                        onRemoveDownload = { close { libraryActions.removeDownload(track.id) } },
                    )
                }
            }
            playlistTrack?.let { track ->
                SitaraSheet(onDismiss = { playlistTrack = null }) { close ->
                    AddToPlaylist(
                        track, playlists,
                        onPick = { close { libraryActions.addToPlaylist(it, track) } },
                        onCreate = { close { libraryActions.createPlaylist(it, track) } },
                    )
                }
            }
        }
    }
}

private val DetailStackSaver = listSaver<List<DetailRoute>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> saved.mapNotNull(::decodeDetailRoute) },
)

/** The tabs, the open one in the cover's [accent] on a glass indicator. [folded]: only the open tab, which unfolds the rest. */
@Composable
private fun FloatingNav(selected: Tab, accent: Color, folded: Boolean, onSelect: (Tab) -> Unit, onUnfold: () -> Unit) {
    val content = MaterialTheme.colorScheme.onSurface
    val indicator = content.copy(alpha = 0.10f)
    if (folded) {
        Box(
            Modifier.padding(start = 8.dp).size(SitaraSpacing.TouchTarget).clip(CircleShape).background(indicator)
                .clickable(role = Role.Button, onClick = onUnfold).semantics { contentDescription = "${selected.label}, show all tabs" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(selected.icon, null, tint = accent, modifier = Modifier.size(22.dp))
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(SitaraSpacing.NavigationHeight).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Tab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.width(56.dp).height(30.dp).clip(CircleShape).background(if (active) indicator else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, null, tint = if (active) accent else content.copy(alpha = 0.65f), modifier = Modifier.size(22.dp))
                }
                Text(
                    tab.label,
                    color = if (active) content else content.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Pixels of scroll that count as reading on or going back, so a settling list does not flip the dock.
private const val FOLD_SLOP = 2f

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}
