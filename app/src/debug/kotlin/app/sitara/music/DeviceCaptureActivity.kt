package app.sitara.music

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog

/** Only debug APKs expose this deterministic UI capture route. */
class DeviceCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen").orEmpty()
        val dark = intent.getBooleanExtra("dark", true)
        val reduced = intent.getBooleanExtra("reduced", false)
        setContent { CaptureScreen(screen, dark, reduced) }
    }
}

private data class CaptureCatalog(val home: List<Shelf>, val library: Library, val lyrics: Lyrics)

@Composable
private fun CaptureScreen(screen: String, dark: Boolean, reduced: Boolean) {
    val source = remember { FakeMusicSource() }
    val catalog by produceState<CaptureCatalog?>(null, source) {
        value = CaptureCatalog(source.home(), source.library(), source.lyrics(SampleCatalog.tracks.first())!!)
    }
    val ready = catalog ?: return
    val player = remember(ready) { DemoPlayer(SampleCatalog.tracks, ready.lyrics) }
    var settings by remember { mutableStateOf(SitaraSettings(darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT, reducedMotion = reduced)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter.SONGS) }
    val results by produceState(SearchResults.empty(), query, filter) { value = source.search(query, filter) }
    val details = remember(source) { SampleDetailPages(source) }
    Box(Modifier.fillMaxSize().semantics { contentDescription = "Device capture: $screen" }) {
        SitaraShell(
            settings = settings,
            home = ready.home,
            library = ready.library,
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
                "library" -> Tab.LIBRARY
                "search" -> Tab.SEARCH
                else -> Tab.HOME
            },
            playerExpanded = screen == "player" || screen == "lyrics",
            playerPage = if (screen == "lyrics") 1 else 0,
            greetingText = "Good evening",
        )
    }
}
