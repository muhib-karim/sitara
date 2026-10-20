package app.sitara.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ScrollEdge
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.HomeScreen
import app.sitara.music.feature.library.DetailScreen
import app.sitara.music.feature.library.LibraryScreen
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerSheet
import app.sitara.music.feature.player.PlayerUiState
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
    val query by vm.query.collectAsState()
    val searchFilter by vm.searchFilter.collectAsState()
    val search by vm.search.collectAsState()
    SitaraShell(
        settings = settings.let { if (systemReducedMotion) it.copy(reducedMotion = true) else it },
        home = home,
        library = library,
        query = query,
        searchFilter = searchFilter,
        search = search,
        player = player,
        actions = vm,
        details = vm,
        onQueryChange = { vm.query.value = it },
        onSearchFilterChange = { vm.searchFilter.value = it },
        onRetrySearch = vm::retrySearch,
        onPlay = vm::play,
        onShuffle = vm::shuffle,
        onSettingsChange = vm::updateSettings,
        tabRequests = vm.requestedTabs,
    )
}

/** App chrome and saveable browse navigation (tabs plus a back stack of detail pages); playback/data are supplied by the caller. */
@Composable
fun SitaraShell(
    settings: SitaraSettings,
    home: List<Shelf>,
    library: Library,
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
) {
    var tab by rememberSaveable { mutableStateOf(if (initialSettingsOpen) Tab.SETTINGS else initialTab) }
    var detailStack by rememberSaveable(stateSaver = DetailStackSaver) { mutableStateOf(listOfNotNull(initialDetail)) }
    val browseState = rememberSaveableStateHolder()
    val open: (DetailRoute) -> Unit = { detailStack = detailStack + it }
    val closeDetails: () -> Unit = { detailStack = emptyList() }
    LaunchedEffect(tabRequests) {
        tabRequests.collect { closeDetails(); tab = it }
    }
    BackHandler(enabled = detailStack.isNotEmpty() || tab == Tab.SETTINGS) {
        if (detailStack.isNotEmpty()) detailStack = detailStack.dropLast(1) else tab = Tab.HOME
    }
    SitaraTheme(settings.themeMode, settings.darkMode, settings.reducedMotion) {
        val haze = rememberHazeState()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val fontScale = LocalDensity.current.fontScale
        val miniHeight = SitaraSpacing.MiniPlayerHeight * fontScale.coerceAtLeast(1f)
        val minimumDockHeight = SitaraSpacing.NavigationHeight + (if (player.track != null) miniHeight else 0.dp)
        var measuredDockHeight by remember(player.track != null, fontScale) { mutableStateOf(0.dp) }
        val dockHeight = maxOf(minimumDockHeight, measuredDockHeight)
        val contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = dockHeight + navBottom + SitaraSpacing.DockBottom + SitaraSpacing.Section,
        )
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                val route = detailStack.lastOrNull()
                val openAlbum: (Album) -> Unit = { open(DetailRoute.Album(it.id)) }
                val openArtist: (Artist) -> Unit = { open(DetailRoute.Artist(it.id)) }
                // The stack depth keeps two visits to the same page apart, each with its own scroll position.
                browseState.SaveableStateProvider(route?.let { "${detailStack.size}-${it.encode()}" } ?: "tab-${tab.name}") {
                    if (route != null) {
                        val state by remember(details, route) { details.state(route) }.collectAsState()
                        DetailScreen(
                            state, settings,
                            onBack = { detailStack = detailStack.dropLast(1) },
                            onRetry = { details.retry(route) },
                            onPlay = onPlay,
                            onShuffle = onShuffle,
                            onArtist = { open(DetailRoute.Artist(it)) },
                            onAlbum = { open(DetailRoute.Album(it)) },
                            contentPadding = contentPadding,
                        )
                    } else {
                        when (tab) {
                            Tab.HOME -> HomeScreen(
                                greetingText, home, onPlay, openAlbum, contentPadding = contentPadding,
                                note = "These shelves are built-in sample music. Search finds real songs, albums, artists and playlists.",
                            )
                            Tab.SEARCH -> SearchScreen(
                                query, onQueryChange, searchFilter, onSearchFilterChange, search, onRetrySearch,
                                listOf("Lo-fi beats", "Jazz piano", "Classical focus"), onPlay, library,
                                openAlbum, openArtist, { open(DetailRoute.Playlist(it.id)) }, contentPadding = contentPadding,
                            )
                            Tab.LIBRARY -> LibraryScreen(
                                library, onPlay, openAlbum, openArtist, contentPadding = contentPadding,
                                note = "A built-in sample library. Saving your own music is not available yet.",
                            )
                            Tab.SETTINGS -> SettingsScreen(settings, onSettingsChange, onBack = { tab = Tab.HOME }, contentPadding = contentPadding)
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
                navigation = { FloatingNav(tab, onSelect = { closeDetails(); tab = it }) },
                onDockHeightChanged = { measuredDockHeight = it },
                onArtist = { open(DetailRoute.Artist(it)) },
            )
        }
    }
}

private val DetailStackSaver = listSaver<List<DetailRoute>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> saved.mapNotNull(::decodeDetailRoute) },
)

@Composable
private fun FloatingNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().height(SitaraSpacing.NavigationHeight).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Tab.entries.forEach { tab ->
            val active = tab == selected
            val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.width(40.dp).height(28.dp).clip(CircleShape)
                        .background(if (active) color.copy(alpha = 0.12f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Text(tab.label, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}
