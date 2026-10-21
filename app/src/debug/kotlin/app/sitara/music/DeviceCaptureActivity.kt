package app.sitara.music

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.sitara.music.core.data.local.hasMusicPermission
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.LibraryLayout
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.LocalLibraryState
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.feature.search.MoodsState
import app.sitara.music.feature.search.SearchLanding
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi

/** Only debug APKs expose this deterministic UI capture route. */
class DeviceCaptureActivity : ComponentActivity() {
    @OptIn(DelicateCoilApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen").orEmpty()
        // Songs on the phone show their own embedded covers through the app's image loader.
        if (screen != "phone") SingletonImageLoader.setUnsafe(sampleImageLoader(applicationContext))
        enableEdgeToEdge()
        // Otherwise Android keeps a grey navigation bar scrim over the dock once the keyboard has closed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val dark = intent.getBooleanExtra("dark", true)
        val reduced = intent.getBooleanExtra("reduced", false)
        setContent { CaptureScreen(screen, dark, reduced) }
    }
}

private data class CaptureCatalog(val home: HomeFeed, val library: SavedLibrary, val lyrics: Lyrics, val moods: MoodsState)

@Composable
private fun CaptureScreen(screen: String, dark: Boolean, reduced: Boolean) {
    val source = remember { FakeMusicSource() }
    val catalog by produceState<CaptureCatalog?>(null, source) {
        val saved = sampleSavedLibrary(source.library())
        value = CaptureCatalog(sampleHomeFeed(source.home(), saved), saved, source.lyrics(SampleCatalog.tracks.first())!!, MoodsState.Ready(source.moods()))
    }
    val ready = catalog ?: return
    val player = remember(ready) { DemoPlayer(SampleCatalog.tracks, ready.lyrics) }
    // On this phone as a list, so every fixture song is on screen for the check that reads them.
    val layout = if (screen == "phone") LibraryLayout.LIST else LibraryLayout.GRID
    var settings by remember { mutableStateOf(SitaraSettings(darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT, reducedMotion = reduced, libraryLayout = layout)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter.SONGS) }
    val results by produceState(SearchResults.empty(), query, filter) { value = source.search(query, filter) }
    val suggestions by produceState(emptyList<String>(), query) { value = source.suggestions(query).filterNot { it.equals(query.trim(), ignoreCase = true) } }
    val details = remember(source) { SampleDetailPages(source) }
    // "phone" reads the real index: the fixture files the workflow put in the emulator's Music folder, through MediaStore.
    val context = LocalContext.current
    val local by produceState(LocalLibraryState(), screen) {
        if (screen != "phone") return@produceState
        val index = (context.applicationContext as SitaraApplication).library.local
        index.rescan()
        index.songs.collect { value = LocalLibraryState(it, musicAccess = context.hasMusicPermission()) }
    }
    Box(Modifier.fillMaxSize().semantics { contentDescription = "Device capture: $screen" }) {
        SitaraShell(
            settings = settings,
            home = ready.home,
            library = LibraryState.Ready(ready.library),
            query = query,
            searchFilter = filter,
            search = SearchState.Loaded(results.query.trim(), results, filter),
            player = player.state,
            actions = player,
            details = details,
            onQueryChange = { query = it },
            onSearchFilterChange = { filter = it },
            onRetrySearch = {},
            onPlay = player::play,
            onShuffle = { player.play(it.shuffled(), 0) },
            onSettingsChange = { settings = it },
            initialTab = when (screen) {
                "library", "phone" -> Tab.LIBRARY
                "search" -> Tab.SEARCH
                else -> Tab.HOME
            },
            initialSettingsOpen = screen == "settings",
            initialDetail = DetailRoute.OnThisPhone.takeIf { screen == "phone" },
            local = local,
            playerExpanded = screen == "player" || screen == "lyrics",
            playerPage = if (screen == "lyrics") 1 else 0,
            greetingText = "Good evening",
            searchLanding = SearchLanding(listOf("Lumen Drift", "night drive"), suggestions, ready.moods),
        )
    }
}
