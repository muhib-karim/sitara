package app.sitara.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.LocalReducedMotion
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraMotion
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.designsystem.glass
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

enum class Tab(val label: String, val icon: ImageVector) { HOME("Home", SitaraIcons.Home), SEARCH("Search", SitaraIcons.Search), LIBRARY("Library", SitaraIcons.Library) }

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
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var settingsOpen by rememberSaveable { mutableStateOf(initialSettingsOpen) }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    SitaraTheme(settings.themeMode, settings.darkMode, settings.reducedMotion) {
        val haze = rememberHazeState()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barSpace = 64.dp + 16.dp + navBottom
        val contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = barSpace + (if (player.track != null) 72.dp else 0.dp) + 16.dp,
        )
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                if (settingsOpen) {
                    SettingsScreen(settings, onSettingsChange, onBack = { settingsOpen = false }, contentPadding = contentPadding)
                } else {
                    when (tab) {
                        Tab.HOME -> HomeScreen(greetingText, home, onPlay, contentPadding = contentPadding)
                        Tab.SEARCH -> SearchScreen(query, onQueryChange, results, listOf("Lumen Drift", "Night Bus Static", "Gold Hour"), onPlay, contentPadding = contentPadding)
                        Tab.LIBRARY -> LibraryScreen(library, onPlay, contentPadding = contentPadding)
                    }
                    IconButton(
                        onClick = { settingsOpen = true },
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 20.dp, end = 12.dp),
                    ) { Icon(SitaraIcons.Settings, "Settings", tint = MaterialTheme.colorScheme.onBackground) }
                }
            }
            if (!settingsOpen) {
                PillTabBar(
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .glass(haze, MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
            PlayerSheet(player, actions, bottomReserved = if (settingsOpen) navBottom + 8.dp else barSpace + 8.dp, initiallyExpanded = playerExpanded, initialPage = playerPage)
        }
    }
}

@Composable
private fun PillTabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val itemWidth = 96.dp
    val pillX by animateDpAsState(itemWidth * selected.ordinal, SitaraMotion.orReduced(reduced, SitaraMotion.pill()), label = "pill")
    Box(modifier.clip(CircleShape).height(64.dp).padding(6.dp)) {
        Box(
            Modifier
                .offset(x = pillX)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        )
        Row {
            Tab.entries.forEach { t ->
                val active = t == selected
                val tint by animateFloatAsState(if (active) 1f else 0.6f, SitaraMotion.fade(), label = "tint")
                Row(
                    Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(t) }
                        .semantics { contentDescription = t.label },
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = tint)
                    Icon(t.icon, null, tint = color, modifier = Modifier.size(22.dp))
                    if (active) {
                        Text(t.label, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                    }
                }
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
