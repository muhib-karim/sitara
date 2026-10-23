package app.sitara.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.LyricsState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A gate on every main screen: each control TalkBack can press has a name, and a touch target of at least 48 × 48 dp
 * (Compose's minimum interactive size counts). Long lists are walked by scrolling them to the end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AccessibilityGateTest {
    @get:Rule val compose = createComposeRule()

    private val source = FakeMusicSource()
    private val library = runBlocking { source.library() }
    private val home = sampleHomeFeed(runBlocking { source.home() }, sampleSavedLibrary(library))
    private val track = SampleCatalog.tracks.first()
    private val playing = PlayerUiState(
        track = track, isPlaying = true, positionMs = 15_000, durationMs = track.durationMs, upNext = SampleCatalog.tracks.drop(1),
        lyrics = LyricsState.Found(runBlocking { source.lyrics(track)!! }), lyricsSources = listOf("LRCLIB"),
    )

    private fun render(tab: Tab = Tab.HOME, route: DetailRoute? = null, settingsOpen: Boolean = false, expanded: Boolean = false, page: Int = 0, details: DetailPages? = null) {
        compose.setContent {
            SitaraShell(
                settings = SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true, dynamicColor = false),
                home = home,
                library = LibraryState.Ready(sampleSavedLibrary(library)),
                query = "",
                searchFilter = SearchFilter.SONGS,
                search = SearchState.Idle,
                player = playing,
                actions = PlayerActions.None,
                details = details ?: SampleDetailPages(source),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = { _, _ -> },
                onShuffle = {},
                onSettingsChange = {},
                initialTab = tab,
                initialDetail = route,
                initialSettingsOpen = settingsOpen,
                playerExpanded = expanded,
                playerPage = page,
                greetingText = "Good evening",
            )
        }
        compose.waitForIdle()
    }

    private fun SemanticsNode.label(): String? =
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")?.takeIf { it.isNotBlank() }
            ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }?.takeIf { it.isNotBlank() }
            ?: config.getOrNull(SemanticsProperties.EditableText)?.text?.takeIf { it.isNotBlank() }
            ?: config.getOrNull(SemanticsActions.OnClick)?.label

    private var checked = 0
    private var headings = 0

    /** Problems among the controls on screen now: unnamed ones, and ones whose touch target is under 48 dp. */
    private fun problems(screen: String): Set<String> {
        val minimum = with(compose.density) { 47.5.dp.toPx() }
        return compose.onRoot().fetchSemanticsNode().let { root -> all(root) }
            .filter { it.config.contains(SemanticsActions.OnClick) && !it.config.contains(SemanticsProperties.Disabled) }
            .also { checked += it.size }
            .also { headings += all(compose.onRoot().fetchSemanticsNode()).count { node -> node.config.contains(SemanticsProperties.Heading) } }
            .mapNotNull { node ->
                val name = node.label()
                val bounds = node.touchBoundsInRoot
                // Controls cut by the screen's edge (a carousel's next card) are measured where they are whole.
                val clipped = bounds.left <= 0f || bounds.top <= 0f
                when {
                    name == null -> "$screen: a control without a name at $bounds"
                    !clipped && (bounds.width < minimum || bounds.height < minimum) ->
                        "$screen: \"$name\" is ${with(compose.density) { bounds.width.toDp() }} × ${with(compose.density) { bounds.height.toDp() }}"
                    else -> null
                }
            }.toSet()
    }

    private fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)

    /** Checks the screen, then scrolls its tallest list down a page at a time, checking each page. */
    private fun walk(screen: String, pages: Int = 6): Set<String> {
        val found = problems(screen).toMutableSet()
        repeat(pages) {
            val lists = compose.onAllNodes(hasScrollAction())
            val tallest = lists.fetchSemanticsNodes().withIndex().maxByOrNull { it.value.boundsInRoot.height }?.index ?: return found
            lists[tallest].performTouchInput { swipeUp() }
            compose.waitForIdle()
            found += problems(screen)
        }
        return found
    }

    private fun assertNone(found: Set<String>) {
        assertTrue("Accessibility problems:\n" + found.sorted().joinToString("\n"), found.isEmpty())
        assertTrue("the gate saw no controls at all", checked > 0)
        assertTrue("no heading for TalkBack to jump between", headings > 0)
        println("accessibility gate: $checked controls checked")
    }

    @Test fun home() {
        render()
        assertNone(walk("Home"))
    }

    @Test fun search() {
        render(Tab.SEARCH)
        assertNone(walk("Search"))
    }

    @Test fun libraryTab() {
        render(Tab.LIBRARY)
        assertNone(walk("Library"))
    }

    @Test fun albumPage() {
        render(Tab.SEARCH, route = DetailRoute.Album("fixture"), details = FixedPage(DetailState.AlbumReady(Fixtures.album)))
        assertNone(walk("Album page"))
    }

    @Test fun nowPlaying() {
        render(expanded = true)
        assertNone(problems("Now Playing"))
    }

    @Test fun lyrics() {
        render(expanded = true, page = 1)
        assertNone(problems("Lyrics"))
    }

    @Test fun upNext() {
        render(expanded = true, page = 2)
        assertNone(walk("Up next", pages = 3))
    }

    @Test fun settings() {
        render(settingsOpen = true)
        assertNone(walk("Settings", pages = 12))
    }

    private fun page(route: DetailRoute) {
        render(Tab.LIBRARY, route = route)
        assertNone(walk(route.toString(), pages = 4))
    }

    @Test fun importPage() = page(DetailRoute.Import)

    @Test fun backupPage() = page(DetailRoute.Backup)

    @Test fun statsPage() = page(DetailRoute.Stats)

    @Test fun scrobblingPage() = page(DetailRoute.Scrobbling)

    @Test fun onThisPhonePage() = page(DetailRoute.OnThisPhone)
}
