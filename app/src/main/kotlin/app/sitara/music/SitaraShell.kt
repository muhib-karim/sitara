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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.HomeScreen
import app.sitara.music.feature.library.LibraryScreen
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerSheet
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchScreen
import app.sitara.music.feature.settings.SettingsScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

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
    val results by vm.results.collectAsState()
    SitaraShell(
        settings = settings.let { if (systemReducedMotion) it.copy(reducedMotion = true) else it },
        home = home,
        library = library,
        query = query,
        results = results,
        player = player,
        actions = vm,
        onQueryChange = { vm.query.value = it },
        onPlay = vm::play,
        onSettingsChange = vm::updateSettings,
    )
}

/** The whole app chrome, stateless so screenshot tests render it with sample data. */
@Composable
fun SitaraShell(
    settings: SitaraSettings,
    home: List<Shelf>,
    library: Library,
    query: String,
    results: SearchResults,
    player: PlayerUiState,
    actions: PlayerActions,
    onQueryChange: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onSettingsChange: (SitaraSettings) -> Unit,
    initialTab: Tab = Tab.HOME,
    initialSettingsOpen: Boolean = false,
    playerExpanded: Boolean = false,
    playerPage: Int = 0,
    greetingText: String = greeting(),
) {
    var tab by rememberSaveable { mutableStateOf(if (initialSettingsOpen) Tab.SETTINGS else initialTab) }
    BackHandler(enabled = tab == Tab.SETTINGS) { tab = Tab.HOME }
    SitaraTheme(settings.themeMode, settings.darkMode, settings.reducedMotion) {
        val haze = rememberHazeState()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val miniHeight = SitaraSpacing.MiniPlayerHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val dockHeight = SitaraSpacing.NavigationHeight + if (player.track != null) miniHeight else 0.dp
        val contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = dockHeight + navBottom + SitaraSpacing.DockBottom + 16.dp,
        )
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(greetingText, home, onPlay, contentPadding = contentPadding)
                    Tab.SEARCH -> SearchScreen(query, onQueryChange, results, listOf("Lumen Drift", "Night Bus Static", "Gold Hour"), onPlay, contentPadding = contentPadding)
                    Tab.LIBRARY -> LibraryScreen(library, onPlay, contentPadding = contentPadding)
                    Tab.SETTINGS -> SettingsScreen(settings, onSettingsChange, onBack = { tab = Tab.HOME }, contentPadding = contentPadding)
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
                navigation = { FloatingNav(tab, onSelect = { tab = it }) },
            )
        }
    }
}

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
