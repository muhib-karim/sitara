package app.sitara.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.designsystem.ShareSheetContent
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.designsystem.preview
import app.sitara.music.core.model.AccentColor
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.AutomixBlend
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.DownloadExport
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.EqualiserPreset
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.ImportProblem
import app.sitara.music.core.model.ImportResult
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.LibraryLayout
import app.sitara.music.core.model.ListeningStats
import app.sitara.music.core.model.LocalFolder
import app.sitara.music.core.model.LocalSong
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.LyricsLine
import app.sitara.music.core.model.LyricsSong
import app.sitara.music.core.model.LyricsTranslation
import app.sitara.music.core.model.PlaybackTuning
import app.sitara.music.core.model.ScrobbleAccount
import app.sitara.music.core.model.ScrobbleService
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.StatsRange
import app.sitara.music.core.model.StreamFormat
import app.sitara.music.core.model.SurfaceTone
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.UserPlaylist
import app.sitara.music.core.model.localTrackId
import app.sitara.music.core.model.shareTarget
import app.sitara.music.feature.home.FeedState
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.home.OnlineHome
import app.sitara.music.feature.home.TrendingState
import app.sitara.music.feature.library.AddToPlaylist
import app.sitara.music.feature.library.CollectionPreviewSheet
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.library.ImportState
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.LocalKind
import app.sitara.music.feature.library.LocalLibraryState
import app.sitara.music.feature.library.SavedIds
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.feature.library.SongActions
import app.sitara.music.feature.library.StatsShareCard
import app.sitara.music.feature.library.StatsState
import app.sitara.music.feature.player.EqualiserPanel
import app.sitara.music.feature.player.LyricCardStyle
import app.sitara.music.feature.player.LyricShareCard
import app.sitara.music.feature.player.LyricsSourceList
import app.sitara.music.feature.player.LyricsState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerMoreOptions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.player.SleepTimerOptions
import app.sitara.music.feature.player.SleepTimerStatus
import app.sitara.music.feature.player.SongVolume
import app.sitara.music.feature.player.SpeedAndPitch
import app.sitara.music.feature.player.TranslationLanguages
import app.sitara.music.feature.player.TranslationState
import app.sitara.music.feature.search.MoodsState
import app.sitara.music.feature.search.SearchLanding
import app.sitara.music.feature.search.SearchState
import app.sitara.music.feature.settings.BackupFailure
import app.sitara.music.feature.settings.BackupStatus
import app.sitara.music.feature.settings.ScrobbleServiceState
import app.sitara.music.feature.settings.ScrobblingState
import app.sitara.music.feature.settings.SignInProblem
import app.sitara.music.feature.settings.SignInStep
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import app.sitara.music.source.lyrics.LyricsMatch
import app.sitara.music.source.lyrics.TranslationProblem
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Records/verifies baselines through the existing CI workflows only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @OptIn(DelicateCoilApi::class)
    @Before
    fun drawSampleCovers() = SingletonImageLoader.setUnsafe(sampleImageLoader(ApplicationProvider.getApplicationContext()))

    @OptIn(DelicateCoilApi::class)
    @After
    fun releaseImageLoader() = SingletonImageLoader.reset()

    private val source = FakeMusicSource()
    private val library: Library = runBlocking { source.library() }
    private val home = sampleHomeFeed(runBlocking { source.home() }, sampleSavedLibrary(library))
    private val offline = home.copy(online = OnlineHome(feed = FeedState.Failed, trending = TrendingState.Ready(runBlocking { source.trending() })))
    private val track = SampleCatalog.tracks.first()
    private val sampleLyrics = runBlocking { source.lyrics(track)!! }
    private val playing = PlayerUiState(
        track = track,
        isPlaying = true,
        positionMs = 15_300,
        durationMs = track.durationMs,
        upNext = SampleCatalog.tracks.drop(1),
        lyrics = LyricsState.Found(sampleLyrics.copy(source = "LRCLIB")),
        lyricsSources = listOf("LRCLIB", "YouTube Music"),
    )
    private val lrclib = sampleLyrics.copy(source = "LRCLIB", matched = LyricsSong(track.title, track.artist, track.durationMs + 1_000))
    private val youtubeMusic = Lyrics(
        sampleLyrics.lines.map { LyricsLine(0, 0, it.text) }, synced = false, source = "YouTube Music", licensor = "Musixmatch",
        matched = LyricsSong(track.title, track.artist, track.durationMs),
    )
    private val bothSources = LyricsState.Found(lrclib, listOf(LyricsMatch("lrclib", lrclib), LyricsMatch("youtube-music", youtubeMusic)))
    private val spanish = LyricsTranslation(
        listOf(
            "Las farolas tararean una melodía tranquila", "Lunas de papel sobre los tejados", "Cada ventana guarda una chispa", "Sitara, ilumina la oscuridad",
            "Sostén el estribillo en tus manos", "Dobla la estática en planes", "Nunca nos quedamos sin tiempo", "Solo un poco fuera de lugar",
            "Sitara, quédate, quédate un rato", "Convierte toda la noche en una sonrisa",
        ),
        fromLanguage = "en", toLanguage = "es",
    )

    // The sample catalogue answers each filter on its own; merged, every result group shows at once.
    private fun allKinds(query: String) = runBlocking {
        SearchFilter.entries.map { source.search(query, it) }
            .reduce { all, more -> all.copy(tracks = all.tracks + more.tracks, albums = all.albums + more.albums, artists = all.artists + more.artists) }
    }

    private val cardLines = listOf("Every window keeps a spark", "Sitara, light the dark")

    // Public-domain verse in each script: Rabindranath Tagore (Bangla), Kabir (Hindi, Devanagari), Mirza Ghalib (Urdu, right to left).
    private val bangla = listOf("আমার সোনার বাংলা, আমি তোমায় ভালোবাসি।", "চিরদিন তোমার আকাশ, তোমার বাতাস, আমার প্রাণে বাজায় বাঁশি॥")
    private val hindi = listOf("पोथी पढ़ि पढ़ि जग मुआ, पंडित भया न कोय।", "ढाई आखर प्रेम का, पढ़े सो पंडित होय॥")
    private val urdu = listOf("ہزاروں خواہشیں ایسی کہ ہر خواہش پہ دم نکلے", "بہت نکلے مرے ارمان لیکن پھر بھی کم نکلے")

    private fun lyricsIn(lines: List<String>) = playing.copy(
        lyrics = LyricsState.Found(Lyrics(lines.mapIndexed { i, text -> LyricsLine(i * 10_000L, (i + 1) * 10_000L, text) }, synced = true, source = "LRCLIB")),
        positionMs = 3_000,
    )

    private val searchLanding = SearchLanding(recent = listOf("Lumen Drift", "night drive", "jazz piano"), moods = MoodsState.Ready(runBlocking { source.moods() }))

    private fun shot(
        name: String,
        dark: Boolean,
        tab: Tab = Tab.HOME,
        query: String = "",
        settingsOpen: Boolean = false,
        expanded: Boolean = false,
        page: Int = 0,
        pureBlack: Boolean = false,
        accent: AccentColor = AccentColor.SAFFRON,
        tone: SurfaceTone = SurfaceTone.NEUTRAL,
        artworkAcrossApp: Boolean = false,
        reduced: Boolean = true,
        fontScale: Float = 1f,
        playerState: PlayerUiState = playing,
        search: SearchState = SearchState.Loaded(query.trim(), allKinds(query)),
        filter: SearchFilter = SearchFilter.SONGS,
        detail: DetailState? = null,
        route: DetailRoute? = null,
        pages: DetailPages? = null,
        saved: LibraryState = LibraryState.Ready(sampleSavedLibrary(library)),
        homeFeed: HomeFeed = home,
        savedIds: SavedIds = SavedIds(),
        downloads: List<SongDownload> = emptyList(),
        equaliser: EqualiserSettings = EqualiserSettings(),
        landing: SearchLanding = searchLanding,
        libraryLayout: LibraryLayout = LibraryLayout.GRID,
        imports: ImportState = ImportState.Start(),
        scrobbling: ScrobblingState = ScrobblingState(),
        local: LocalLibraryState = LocalLibraryState(),
        backup: BackupStatus = BackupStatus.Idle,
        export: DownloadExport = DownloadExport.Idle,
        greeting: String = "Good evening",
        beforeCapture: () -> Unit = {},
    ) {
        val settings = SitaraSettings(
            darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT, pureBlack = pureBlack, accent = accent, surfaceTone = tone, artworkColorsAcrossApp = artworkAcrossApp,
            // Robolectric has no wallpaper to take colours from; the shots show Sitara's own accents.
            dynamicColor = false, reducedMotion = reduced, libraryLayout = libraryLayout,
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SitaraShell(
                    settings = settings,
                    home = homeFeed,
                    library = saved,
                    query = query,
                    searchFilter = filter,
                    search = search,
                    player = playerState.copy(equaliser = equaliser),
                    actions = PlayerActions.None,
                    details = pages ?: detail?.let(::FixedPage) ?: SampleDetailPages(source),
                    onQueryChange = {},
                    onSearchFilterChange = {},
                    onRetrySearch = {},
                    onPlay = { _, _ -> },
                    onShuffle = {},
                    onSettingsChange = {},
                    initialTab = tab,
                    initialDetail = route ?: detail?.let { DetailRoute.Album("fixture") },
                    initialSettingsOpen = settingsOpen,
                    playerExpanded = expanded,
                    playerPage = page,
                    greetingText = greeting,
                    savedIds = savedIds,
                    downloads = downloads,
                    searchLanding = landing,
                    imports = imports,
                    scrobbling = scrobbling,
                    local = local,
                    backup = backup,
                    export = export,
                )
            }
        }
        beforeCapture()
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun miniPlayerDark() = shot("mini_player", dark = true)

    // Songs on the phone: sample songs under content URIs, one folder Sitara lost access to, and a file without tags.
    private val musicFolder = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
    private val onThePhone = LocalLibraryState(
        songs = SampleCatalog.tracks.take(7).mapIndexed { i, t ->
            LocalSong(t.copy(id = localTrackId("content://media/external/audio/media/${i + 1}")), musicFolder.takeIf { i >= 5 }, "Music/${t.album}/${t.title}.mp3", i + 1, 100L - i)
        } + LocalSong(
            Track(localTrackId("content://media/external/audio/media/99"), "field recording 03", "Unknown artist", "Unknown album", 12_000, Artwork(listOf(0xFF2E2A4A.toInt(), 0xFF9A7BFF.toInt()))),
            null, "Recordings/field recording 03.m4a", null, 1,
        ),
        folders = listOf(LocalFolder(musicFolder, "Music", songCount = 2, linked = false), LocalFolder("$musicFolder%2FLive", "Live", songCount = 0, linked = true)),
        musicAccess = true,
    )

    private fun localKind(label: String) = compose.onNode(hasText(label) and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)

    @Test fun onThisPhoneDark() = shot("on_this_phone", dark = true, tab = Tab.LIBRARY, route = DetailRoute.OnThisPhone, playerState = PlayerUiState(), local = onThePhone)
    @Test fun onThisPhoneLight() = shot("on_this_phone", dark = false, tab = Tab.LIBRARY, route = DetailRoute.OnThisPhone, playerState = PlayerUiState(), local = onThePhone)
    @Test fun onThisPhoneList() = shot(
        "on_this_phone_list", dark = true, tab = Tab.LIBRARY, route = DetailRoute.OnThisPhone, libraryLayout = LibraryLayout.LIST, local = onThePhone,
    )
    @Test fun onThisPhoneAlbums() = shot("on_this_phone_albums", dark = true, tab = Tab.LIBRARY, route = DetailRoute.OnThisPhone, local = onThePhone) { localKind("Albums") }
    @Test fun onThisPhoneFolders() = shot("on_this_phone_folders", dark = true, tab = Tab.LIBRARY, route = DetailRoute.OnThisPhone, libraryLayout = LibraryLayout.LIST, local = onThePhone) {
        localKind("Folders")
        compose.onNodeWithTag("local-content").performScrollToNode(hasText("Relink folder"))
    }
    @Test fun onThisPhoneNoAccess() = shot(
        "on_this_phone_empty", dark = true, tab = Tab.LIBRARY, route = DetailRoute.OnThisPhone, playerState = PlayerUiState(), local = LocalLibraryState(musicAccess = false),
    )
    @Test fun onThisPhoneNoAccessLight() = shot(
        "on_this_phone_empty", dark = false, tab = Tab.LIBRARY, route = DetailRoute.OnThisPhone, playerState = PlayerUiState(), local = LocalLibraryState(musicAccess = false),
    )
    @Test fun onThisPhoneNothingFound() = shot(
        "on_this_phone_nothing_found", dark = true, tab = Tab.LIBRARY, route = DetailRoute.OnThisPhone, playerState = PlayerUiState(), local = LocalLibraryState(musicAccess = true),
    )
    @Test fun onThisPhoneAlbum() = shot(
        "on_this_phone_album", dark = true, tab = Tab.LIBRARY, route = DetailRoute.LocalGroup(LocalKind.ALBUMS, onThePhone.songs.first().track.album.lowercase()), local = onThePhone,
    )
    @Test fun libraryWithSongsOnThePhone() = shot("library_on_this_phone", dark = true, tab = Tab.LIBRARY, local = onThePhone)
    @Test fun searchFindsSongsOnThePhone() {
        val query = onThePhone.songs.first().track.title.substringBefore(' ')
        shot("search_on_this_phone", dark = true, tab = Tab.SEARCH, query = query, local = onThePhone)
    }

    @Test fun backupDark() = shot("backup", dark = true, tab = Tab.SETTINGS, route = DetailRoute.Backup, settingsOpen = true)
    @Test fun backupLight() = shot("backup", dark = false, tab = Tab.SETTINGS, route = DetailRoute.Backup, settingsOpen = true)
    @Test fun backupConfirm() = shot("backup_confirm", dark = true, tab = Tab.SETTINGS, route = DetailRoute.Backup, settingsOpen = true) {
        compose.onNodeWithText("Restore from a file").performSemanticsAction(SemanticsActions.OnClick)
    }
    @Test fun backupDone() = shot(
        "backup_restored", dark = true, tab = Tab.SETTINGS, route = DetailRoute.Backup, settingsOpen = true, backup = BackupStatus.Restored(1_204, 6, 3_481),
    )
    @Test fun backupDamaged() = shot(
        "backup_damaged", dark = true, tab = Tab.SETTINGS, route = DetailRoute.Backup, settingsOpen = true, backup = BackupStatus.Failed(BackupFailure.DAMAGED),
    )

    // Last sent on an earlier day, so the words do not depend on when the test runs.
    private val listenBrainz = ScrobbleServiceState(
        ScrobbleService.LISTENBRAINZ, ScrobbleAccount(ScrobbleService.LISTENBRAINZ, "orchidlane"), pending = 3, lastSentAt = 1_792_521_720_000,
    )
    private val connected = ScrobblingState(services = listOf(listenBrainz, ScrobbleServiceState(ScrobbleService.LASTFM)))
    private val signIn = ScrobblingState(
        services = listOf(
            ScrobbleServiceState(ScrobbleService.LISTENBRAINZ, signIn = SignInStep.Failed(SignInProblem.REFUSED)),
            ScrobbleServiceState(ScrobbleService.LASTFM, ScrobbleAccount(ScrobbleService.LASTFM, "orchidlane", signInNeeded = true), pending = 12),
        ),
    )

    @Test fun scrobblingDark() = shot("scrobbling", dark = true, tab = Tab.SETTINGS, route = DetailRoute.Scrobbling, scrobbling = connected)
    @Test fun scrobblingLight() = shot("scrobbling", dark = false, tab = Tab.SETTINGS, route = DetailRoute.Scrobbling, scrobbling = connected)
    @Test fun scrobblingPureBlack() = shot("scrobbling_amoled", dark = true, pureBlack = true, tab = Tab.SETTINGS, route = DetailRoute.Scrobbling, scrobbling = connected)
    @Test fun scrobblingLargeText() = shot("scrobbling_large_text", dark = true, fontScale = 2f, tab = Tab.SETTINGS, route = DetailRoute.Scrobbling, scrobbling = connected) {
        compose.onNodeWithTag("scrobbling-content").performScrollToKey(ScrobbleService.LISTENBRAINZ.name)
    }
    @Test fun scrobblingLibraryOnly() = shot(
        "scrobbling_library_only", dark = true, tab = Tab.SETTINGS, route = DetailRoute.Scrobbling,
        scrobbling = connected.copy(services = listOf(listenBrainz.copy(account = listenBrainz.account!!.copy(libraryOnly = true), pending = 0), ScrobbleServiceState(ScrobbleService.LASTFM))),
    )
    @Test fun scrobblingSignIn() = shot("scrobbling_sign_in", dark = true, tab = Tab.SETTINGS, route = DetailRoute.Scrobbling, scrobbling = signIn)
    @Test fun scrobblingInBrowser() = shot(
        "scrobbling_in_browser", dark = true, tab = Tab.SETTINGS, route = DetailRoute.Scrobbling,
        scrobbling = ScrobblingState(services = listOf(ScrobbleServiceState(ScrobbleService.LISTENBRAINZ), ScrobbleServiceState(ScrobbleService.LASTFM, signIn = SignInStep.InBrowser))),
    )
    @Test fun scrobblingWithoutLastFmKey() = shot(
        "scrobbling_no_key", dark = true, tab = Tab.SETTINGS, route = DetailRoute.Scrobbling,
        scrobbling = ScrobblingState(services = listOf(ScrobbleServiceState(ScrobbleService.LISTENBRAINZ), ScrobbleServiceState(ScrobbleService.LASTFM, available = false))),
    )
    @Test fun settingsHistory() = shot("settings_history", dark = true, settingsOpen = true, scrobbling = connected) {
        compose.onNodeWithTag("settings-content").performScrollToKey("history")
    }

    @Test fun miniPlayerLight() = shot("mini_player", dark = false)

    @Test fun homeWithoutQueue() = shot("home_empty_queue", dark = true, playerState = PlayerUiState())

    @Test fun playerAmoled() = shot("player_amoled", dark = true, expanded = true, pureBlack = true)

    @Test fun playerBlur() = shot("player_blur", dark = true, expanded = true, reduced = false)

    @Test fun lyricsBlur() = shot("lyrics_blur", dark = true, expanded = true, page = 1, reduced = false)

    @Test fun lyricsLastLine() = shot("lyrics_last_line", dark = true, expanded = true, page = 1, playerState = playing.copy(positionMs = 40_000))

    @Test fun lyricsPlain() = shot("lyrics_plain", dark = true, expanded = true, page = 1, playerState = playing.copy(lyrics = LyricsState.Found(sampleLyrics.copy(synced = false, source = "LRCLIB"))))

    @Test fun lyricsSourceSheet() = sheet("lyrics_sources") { LyricsSourceList(playing.copy(lyrics = bothSources)) {} }
    @Test fun lyricsSourceSheetOneSource() = sheet("lyrics_sources_one") { LyricsSourceList(playing.copy(lyrics = LyricsState.Found(lrclib, listOf(LyricsMatch("lrclib", lrclib))))) {} }
    @Test fun lyricsTranslatedDark() = shot("lyrics_translated", dark = true, expanded = true, page = 1, playerState = playing.copy(translation = TranslationState.Translated(spanish)))
    @Test fun lyricsTranslatedLight() = shot("lyrics_translated", dark = false, expanded = true, page = 1, playerState = playing.copy(translation = TranslationState.Translated(spanish)))
    @Test fun lyricsTranslatedPlain() = shot(
        "lyrics_translated_plain", dark = true, expanded = true, page = 1,
        playerState = playing.copy(lyrics = LyricsState.Found(youtubeMusic), translation = TranslationState.Translated(spanish)),
    )
    @Test fun lyricsTranslating() = shot("lyrics_translating", dark = true, expanded = true, page = 1, playerState = playing.copy(translation = TranslationState.Translating("es")))
    @Test fun lyricsTranslationOffline() = shot(
        "lyrics_translation_offline", dark = true, expanded = true, page = 1, playerState = playing.copy(translation = TranslationState.Failed(TranslationProblem.OFFLINE, "es")),
    )
    @Test fun lyricsTranslationDailyLimit() = shot(
        "lyrics_translation_limit", dark = true, expanded = true, page = 1, playerState = playing.copy(translation = TranslationState.Failed(TranslationProblem.DAILY_LIMIT, "es")),
    )
    @Test fun translationLanguages() = sheet("translation_languages") { TranslationLanguages("es") {} }
    @Test fun lyricsPicking() = shot("lyrics_picking", dark = true, expanded = true, page = 1) {
        compose.onNodeWithText("Every window keeps a spark").performTouchInput { longClick() }
        compose.onNodeWithText("Sitara, light the dark").performClick()
    }
    @Test fun lyricCardArtwork() = sheet("lyric_card_artwork") { LyricShareCard(LyricCardStyle.ARTWORK, cardLines, track, "Lyrics from LRCLIB") }
    @Test fun lyricCardColour() = sheet("lyric_card_colour") { LyricShareCard(LyricCardStyle.COLOUR, cardLines, track, "Lyrics from LRCLIB") }
    @Test fun lyricCardLight() = sheet("lyric_card_light") { LyricShareCard(LyricCardStyle.LIGHT, cardLines, track, "Lyrics from YouTube Music · Musixmatch") }
    @Test fun lyricCardFiveLines() = sheet("lyric_card_five_lines") { LyricShareCard(LyricCardStyle.ARTWORK, sampleLyrics.lines.take(5).map { it.text }, track, "Lyrics from LRCLIB") }
    @Test fun lyricsBangla() = shot("lyrics_bangla", dark = true, expanded = true, page = 1, playerState = lyricsIn(bangla))
    @Test fun lyricsHindi() = shot("lyrics_hindi", dark = true, expanded = true, page = 1, playerState = lyricsIn(hindi))
    @Test fun lyricsUrdu() = shot("lyrics_urdu", dark = true, expanded = true, page = 1, playerState = lyricsIn(urdu))
    @Test fun lyricsUrduTranslated() = shot(
        "lyrics_urdu_translated", dark = true, expanded = true, page = 1,
        playerState = lyricsIn(urdu).copy(
            translation = TranslationState.Translated(
                LyricsTranslation(listOf("Thousands of desires, each worth dying for", "Many of my wishes came true, yet still too few"), fromLanguage = "ur", toLanguage = "en"),
            ),
        ),
    )
    @Test fun lyricCardBangla() = sheet("lyric_card_bangla") { LyricShareCard(LyricCardStyle.ARTWORK, bangla, track, "Lyrics from LRCLIB") }
    @Test fun lyricCardHindi() = sheet("lyric_card_hindi") { LyricShareCard(LyricCardStyle.COLOUR, hindi, track, "Lyrics from LRCLIB") }
    @Test fun lyricCardUrdu() = sheet("lyric_card_urdu") { LyricShareCard(LyricCardStyle.LIGHT, urdu, track, "Lyrics from LRCLIB") }
    @Test fun lyricsEmpty() = shot("lyrics_empty", dark = true, expanded = true, page = 1, playerState = playing.copy(lyrics = LyricsState.NotFound))

    @Test fun lyricsLoading() = shot("lyrics_loading", dark = true, expanded = true, page = 1, playerState = playing.copy(lyrics = LyricsState.Loading))

    @Test fun lyricsFailed() = shot("lyrics_failed", dark = true, expanded = true, page = 1, playerState = playing.copy(lyrics = LyricsState.Failed))

    // Bangla, right to left (Arabic locale, no Arabic strings yet, so English text in a mirrored layout) and 200 % text.
    @Config(qualifiers = "bn-w411dp-h891dp-xxhdpi")
    @Test fun homeBangla() = shot("home_bn", dark = true, greeting = "শুভ সন্ধ্যা")

    @Config(qualifiers = "bn-w411dp-h891dp-xxhdpi")
    @Test fun libraryBangla() = shot("library_bn", dark = false, tab = Tab.LIBRARY)

    @Config(qualifiers = "bn-w411dp-h891dp-xxhdpi")
    @Test fun settingsBangla() = shot("settings_bn", dark = true, settingsOpen = true)

    @Config(qualifiers = "bn-w411dp-h891dp-xxhdpi")
    @Test fun playerBangla() = shot("player_bn", dark = true, expanded = true)

    @Config(qualifiers = "bn-w411dp-h891dp-xxhdpi")
    @Test fun upNextBangla() = shot("up_next_bn", dark = true, expanded = true, page = 2)

    @Config(qualifiers = "bn-w411dp-h891dp-xxhdpi")
    @Test fun searchBangla() = shot("search_bn", dark = true, tab = Tab.SEARCH, query = "lumen")

    @Config(qualifiers = "bn-w411dp-h891dp-xxhdpi")
    @Test fun settingsBanglaLargeText() = shot("settings_bn_large_text", dark = true, settingsOpen = true, fontScale = 2f)

    @Config(qualifiers = "bn-w411dp-h891dp-xxhdpi")
    @Test fun libraryBanglaLargeText() = shot("library_bn_large_text", dark = true, tab = Tab.LIBRARY, fontScale = 2f)

    @Config(qualifiers = "ar-ldrtl-w411dp-h891dp-xxhdpi")
    @Test fun homeRightToLeft() = shot("home_rtl", dark = true)

    @Config(qualifiers = "ar-ldrtl-w411dp-h891dp-xxhdpi")
    @Test fun playerRightToLeft() = shot("player_rtl", dark = true, expanded = true)

    @Config(qualifiers = "ar-ldrtl-w411dp-h891dp-xxhdpi")
    @Test fun albumRightToLeft() = shot("album_rtl", dark = true, tab = Tab.SEARCH, detail = DetailState.AlbumReady(Fixtures.album))

    @Test fun libraryLargeText() = shot("library_large_text", dark = true, tab = Tab.LIBRARY, fontScale = 2f)

    @Test fun searchLargeText() = shot("search_large_text", dark = true, tab = Tab.SEARCH, query = "lumen", fontScale = 2f)

    @Test fun settingsLargeText() = shot("settings_large_text", dark = true, settingsOpen = true, fontScale = 2f)

    @Test fun albumLargeText() = shot("album_large_text", dark = true, tab = Tab.SEARCH, detail = DetailState.AlbumReady(Fixtures.album), fontScale = 2f)

    @Test fun upNextLargeText() = shot("up_next_large_text", dark = true, expanded = true, page = 2, fontScale = 2f)

    @Test fun lyricsLargeText() = shot("lyrics_large_text", dark = true, expanded = true, page = 1, fontScale = 2f)

    @Test fun miniPlayerLargeText() = shot("mini_large_text", dark = true, fontScale = 2f)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun lyricsLandscape() = shot("lyrics_landscape", dark = true, expanded = true, page = 1)

    @Test fun playerLargeText() = shot("player_large_text", dark = true, expanded = true, fontScale = 2f)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun playerLandscape() = shot("player_landscape", dark = true, expanded = true)

    @Test
    @Config(sdk = [30])
    fun playerLegacyBlurFallback() = shot("player_api30", dark = true, expanded = true, reduced = false)

    @Test fun homeDark() = shot("home", dark = true)

    @Test fun homeLight() = shot("home", dark = false)

    @Test fun searchDark() = shot("search", dark = true, tab = Tab.SEARCH, query = "night")

    @Test fun searchLight() = shot("search", dark = false, tab = Tab.SEARCH, query = "night")

    @Test fun libraryDark() = shot("library", dark = true, tab = Tab.LIBRARY)

    @Test fun libraryLight() = shot("library", dark = false, tab = Tab.LIBRARY)

    @Test fun playerDark() = shot("player", dark = true) {
        compose.onNodeWithContentDescription("Open player").performTouchInput { swipeUp(startY = bottom, endY = -100f) }
    }

    @Test fun playerLight() = shot("player", dark = false, expanded = true)

    @Test fun playerAutomix() = shot("player_automix", dark = true, expanded = true, playerState = playing.copy(blend = AutomixBlend(124, 128, 8)))

    @Test fun lyricsDark() = shot("lyrics", dark = true, expanded = true, page = 1)

    @Test fun lyricsLight() = shot("lyrics", dark = false, expanded = true, page = 1)

    @Test fun settingsDark() = shot("settings", dark = true, settingsOpen = true)

    @Test fun settingsLight() = shot("settings", dark = false, settingsOpen = true)

    @Test fun appearanceAccentDark() = shot("appearance_violet", dark = true, settingsOpen = true, accent = AccentColor.VIOLET)

    @Test fun appearanceMonsoonDark() = shot("appearance_monsoon", dark = true, settingsOpen = true, accent = AccentColor.TEAL, tone = SurfaceTone.COOL)

    @Test fun appearanceMonsoonLight() = shot("appearance_monsoon", dark = false, settingsOpen = true, accent = AccentColor.TEAL, tone = SurfaceTone.COOL)

    @Test fun homeIndigoInk() = shot("home_indigo_ink", dark = true, accent = AccentColor.VIOLET, tone = SurfaceTone.INK)

    @Test fun appearanceAccentLight() = shot("appearance_violet", dark = false, settingsOpen = true, accent = AccentColor.VIOLET)

    @Test fun homeArtworkColoursDark() = shot("home_artwork_colours", dark = true, accent = AccentColor.ARTWORK, artworkAcrossApp = true)

    @Test fun homeArtworkColoursLight() = shot("home_artwork_colours", dark = false, accent = AccentColor.ARTWORK, artworkAcrossApp = true)

    @Test fun homeQuickPicksSecondPage() = shot("home_quick_page2", dark = true) {
        compose.onNodeWithTag("quick-picks").performTouchInput { swipeLeft(startX = right * 0.75f, endX = right * 0.15f) }
    }

    @Test fun homeLoadingDark() = shot("home_loading", dark = true, playerState = PlayerUiState(), homeFeed = HomeFeed())

    @Test fun homeLoadingLight() = shot("home_loading", dark = false, playerState = PlayerUiState(), homeFeed = HomeFeed())

    @Test fun homeOfflineDark() = shot("home_offline", dark = true, homeFeed = offline)

    @Test fun homeOfflineLight() = shot("home_offline", dark = false, homeFeed = offline)

    @Test fun homeOfflineWithoutChart() = shot("home_offline_no_chart", dark = true, homeFeed = offline.copy(online = offline.online.copy(trending = TrendingState.Failed)))

    @Test fun homeMoodSelected() = shot(
        "home_mood", dark = true,
        homeFeed = sampleHomeFeed(runBlocking { source.home(home.online.chips.first()) }, sampleSavedLibrary(library)),
    )

    @Test fun homeFreshInstall() = shot("home_fresh", dark = true, playerState = PlayerUiState(), homeFeed = HomeFeed(home.online, libraryLoaded = true))

    @Test fun homeFeedShelves() = shot("home_shelves", dark = true) {
        compose.onNodeWithTag("home-content").performScrollToIndex(10)
    }

    @Test fun homeBottomLargeText() = shot("home_bottom_large_text", dark = true, fontScale = 2f) {
        compose.onNodeWithTag("home-content").performScrollToIndex(15)
    }

    @Test fun libraryList() = shot("library_list", dark = true, tab = Tab.LIBRARY, libraryLayout = LibraryLayout.LIST)

    @Test fun librarySongs() = shot("library_songs", dark = true, tab = Tab.LIBRARY) {
        compose.onNode(hasText("Songs") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("library-content").performScrollToIndex(5)
    }

    @Test fun libraryEmpty() = shot("library_empty", dark = true, tab = Tab.LIBRARY, saved = LibraryState.Ready(SavedLibrary(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())))

    @Test fun libraryLoading() = shot("library_loading", dark = true, tab = Tab.LIBRARY, saved = LibraryState.Loading)

    @Test fun albumDark() = shot("album", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Album(library.albums.first().id))
    @Test fun albumLight() = shot("album", dark = false, tab = Tab.LIBRARY, route = DetailRoute.Album(library.albums.first().id))
    @Test fun artistDark() = shot("artist", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Artist(library.artists.first().id))
    @Test fun artistLight() = shot("artist", dark = false, tab = Tab.LIBRARY, route = DetailRoute.Artist(library.artists.first().id))

    @Test fun likedPage() = shot("liked_page", dark = true, tab = Tab.LIBRARY, detail = DetailState.LikedReady(Fixtures.playlist.tracks))
    @Test fun likedEmpty() = shot("liked_empty", dark = true, tab = Tab.LIBRARY, detail = DetailState.LikedReady(emptyList()))
    @Test fun userPlaylistDark() = shot("user_playlist", dark = true, tab = Tab.LIBRARY, detail = DetailState.UserPlaylistReady(UserPlaylist(1, "Late night", Fixtures.playlist.tracks)))
    @Test fun userPlaylistLight() = shot("user_playlist", dark = false, tab = Tab.LIBRARY, detail = DetailState.UserPlaylistReady(UserPlaylist(1, "Late night", Fixtures.playlist.tracks)))
    @Test fun upNext() = shot("up_next", dark = true, expanded = true, page = 2)

    // Two radio songs under their own heading, and the Undo offer after a removal; the frozen clock holds the snackbar up.
    @Test fun upNextUndo() = shot("up_next_undo", dark = true, expanded = true, page = 2, playerState = playing.copy(upNext = playing.upNext.take(5), upNextFromRadio = 2)) {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Remove ${playing.upNext[1].title} from the queue").performClick()
        compose.mainClock.advanceTimeBy(1_000)
    }
    @Test fun playerCoverFailed() = shot(
        "player_cover_failed", dark = true, expanded = true,
        playerState = playing.copy(track = track.copy(artwork = track.artwork.copy(imageUrl = "https://offline.invalid/cover.jpg"))),
    )

    @Test fun miniPlayerFailed() = shot("mini_player_failed", dark = true, playerState = playing.copy(isPlaying = false, positionMs = 0, failed = true))

    @Test fun playerFailed() = shot("player_failed", dark = true, expanded = true, playerState = playing.copy(isPlaying = false, positionMs = 0, failed = true))

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun playerFailedSmallPhone() = shot("player_failed_small", dark = true, expanded = true, playerState = playing.copy(isPlaying = false, positionMs = 0, failed = true))

    @Test fun playerLiked() = shot("player_liked", dark = true, expanded = true, playerState = playing.copy(liked = true))

    @Test fun songActionsSheet() = sheet("song_actions") { SongActions(Fixtures.playlist.tracks.first(), liked = true, download = null, {}, {}, {}, {}, {}, {}) }
    @Test fun speedAndPitchSheet() = sheet("speed_pitch") { SpeedAndPitch(PlaybackTuning(speed = 1.25f, semitones = -2), PlayerActions.None) }
    @Test fun sleepTimerSheet() = sheet("sleep_timer") { SleepTimerOptions(SleepTimerStatus.Counting(754_000), PlayerActions.None) { it() } }
    @Test fun playerTimerAndSpeedSet() = shot(
        "player_timer_speed", dark = true, expanded = true,
        playerState = playing.copy(sleepTimer = SleepTimerStatus.Counting(754_000), tuning = PlaybackTuning(speed = 1.5f)),
    )
    @Test fun songActionsDownloaded() = sheet("song_actions_downloaded") {
        SongActions(Fixtures.playlist.tracks.first(), liked = false, download = DownloadState.DOWNLOADED, {}, {}, {}, {}, {}, {})
    }

    @Test fun libraryDownloads() = shot("library_downloads", dark = true, tab = Tab.LIBRARY, downloads = Fixtures.downloads) {
        compose.onNode(hasText("Downloads") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("library-content").performScrollToKey("download-${Fixtures.downloads.last().track.id}")
    }
    @Test fun libraryDownloadsLight() = shot("library_downloads", dark = false, tab = Tab.LIBRARY, downloads = Fixtures.downloads) {
        compose.onNode(hasText("Downloads") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("library-content").performScrollToKey("download-${Fixtures.downloads.last().track.id}")
    }
    @Test fun albumPageDownloads() = shot(
        "album_page_downloads", dark = true, tab = Tab.SEARCH, detail = DetailState.AlbumReady(Fixtures.album),
        // Downloaded songs at the top, above the dock, so their badges show; one still missing keeps Download all.
        savedIds = SavedIds(
            downloads = Fixtures.album.tracks.take(3).zip(listOf(DownloadState.DOWNLOADED, DownloadState.DOWNLOADED, DownloadState.DOWNLOADING))
                .associate { (track, state) -> track.id to state },
        ),
    )
    @Test fun statsDark() = shot("stats", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Stats)
    @Test fun statsLight() = shot("stats", dark = false, tab = Tab.LIBRARY, route = DetailRoute.Stats)
    @Test fun statsTopArtistsAndAlbums() = shot("stats_top_artists", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Stats) {
        compose.onNodeWithTag("stats-content").performScrollToNode(hasText("Top albums"))
    }
    @Test fun statsEmpty() = shot(
        "stats_empty", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Stats,
        pages = object : DetailPages by SampleDetailPages(source) {
            override fun stats(range: StatsRange) = MutableStateFlow<StatsState>(StatsState.Ready(ListeningStats.of(emptyList(), emptyList(), ZoneOffset.UTC)))
        },
    )
    @Test fun statsShareCard() = sheet("stats_share_card") {
        StatsShareCard(sampleListeningStats(StatsRange.WEEK), StatsRange.WEEK, Modifier.padding(SitaraSpacing.Gutter))
    }
    @Test fun importStartDark() = shot("import_start", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Import)
    @Test fun importStartLight() = shot("import_start", dark = false, tab = Tab.LIBRARY, route = DetailRoute.Import)
    @Test fun importStartProblem() = shot(
        "import_start_problem", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Import,
        imports = ImportState.Start("https://example.com/my-mix", unfinished = Fixtures.importMatching, problem = ImportProblem.LINK_NOT_RECOGNISED),
    )
    @Test fun importSpotify() = shot(
        "import_spotify", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Import,
        imports = ImportState.Running(Fixtures.importMatching.copy(name = "Monsoon Evenings", pending = 0, matched = 92, uncertain = 0, notFound = 8, fromSpotify = true)),
    )
    @Test fun importMatching() = shot("import_matching", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Import, imports = ImportState.Running(Fixtures.importMatching))
    @Test fun importReviewDark() = shot("import_review", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Import, imports = ImportState.Running(Fixtures.importReview))
    @Test fun importReviewLight() = shot("import_review", dark = false, tab = Tab.LIBRARY, route = DetailRoute.Import, imports = ImportState.Running(Fixtures.importReview))
    @Test fun importReviewLargeText() = shot(
        "import_review_large_text", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Import, fontScale = 2f, imports = ImportState.Running(Fixtures.importReview),
    ) {
        compose.onNodeWithTag("import-content").performScrollToKey("review-4")
    }
    @Test fun importDone() = shot(
        "import_done", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Import,
        imports = ImportState.Done(ImportResult(playlistId = 1, name = "Road trip", added = 109, updated = false), leftOut = 11),
    )
    @Test fun addToPlaylistSheet() = sheet("add_to_playlist") { AddToPlaylist(Fixtures.playlist.tracks.first(), sampleSavedLibrary(library).playlists, {}, {}) }

    // Sheets open in a dialog window; their content is captured on the sheet's own surface colour.
    private fun sheet(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SitaraTheme(SitaraSettings(dynamicColor = false, reducedMotion = true), dark = true) {
                Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_dark.png")
    }

    @Test fun collectionPreview() = sheet("collection_preview") {
        CollectionPreviewSheet(Fixtures.album.preview(), DetailState.AlbumReady(Fixtures.album), { _, _ -> }, {}, {}, {}, onShare = {})
    }

    @Test fun shareSheet() = sheet("share_sheet") {
        ShareSheetContent(Fixtures.album.shareTarget()!!, onSendLink = {}, onShareImage = {})
    }

    @Test fun searchIdleDark() = shot("search_idle", dark = true, tab = Tab.SEARCH)
    @Test fun searchIdleLight() = shot("search_idle", dark = false, tab = Tab.SEARCH)
    @Test fun searchNoRecentSearches() = shot("search_no_recent", dark = true, tab = Tab.SEARCH, landing = searchLanding.copy(recent = emptyList()))
    @Test fun searchMoodsLoading() = shot("search_moods_loading", dark = true, tab = Tab.SEARCH, landing = searchLanding.copy(moods = MoodsState.Loading))
    @Test fun searchSuggestions() = shot("search_suggestions", dark = true, tab = Tab.SEARCH, query = "Lu", landing = searchLanding.copy(suggestions = runBlocking { source.suggestions("Lu") }))
    @Test fun moodPage() = shot("mood_page", dark = true, tab = Tab.SEARCH, route = DetailRoute.Mood("sample:mood:Jazz", "Jazz"))
    @Test fun searchFailed() = shot("search_failed", dark = true, tab = Tab.SEARCH, query = "lofi", search = SearchState.Failed("lofi"))
    @Test fun searchGrouped() = shot("search_grouped", dark = true, tab = Tab.SEARCH, query = "Lumen Drift")

    @Test fun artistPageDark() = shot("artist_page", dark = true, tab = Tab.SEARCH, detail = DetailState.ArtistReady(Fixtures.artistPage))
    @Test fun artistPageLight() = shot("artist_page", dark = false, tab = Tab.SEARCH, detail = DetailState.ArtistReady(Fixtures.artistPage))
    @Test fun albumPageDark() = shot("album_page", dark = true, tab = Tab.SEARCH, detail = DetailState.AlbumReady(Fixtures.album))
    @Test fun playlistPageDark() = shot("playlist_page", dark = true, tab = Tab.SEARCH, detail = DetailState.PlaylistReady(Fixtures.playlist))
    @Test fun detailLoading() = shot("detail_loading", dark = true, tab = Tab.SEARCH, detail = DetailState.Loading)
    @Test fun detailFailed() = shot("detail_failed", dark = true, tab = Tab.SEARCH, detail = DetailState.Failed)
    @Test fun albumEmpty() = shot("album_empty", dark = true, tab = Tab.SEARCH, detail = DetailState.AlbumReady(Fixtures.album.copy(tracks = emptyList())))
    @Test fun searchAlbumsFilter() = shot(
        "search_albums", dark = true, tab = Tab.SEARCH, query = "orchid", filter = SearchFilter.ALBUMS,
        search = SearchState.Loaded("orchid", SearchResults.empty("orchid").copy(albums = Fixtures.artistPage.releases), SearchFilter.ALBUMS),
    )

    @Test fun settingsMotion() = shot("settings_motion", dark = true, settingsOpen = true) {
        compose.onNodeWithTag("settings-content").performScrollToKey("motion")
    }

    @Test fun settingsPlayback() = shot("settings_playback", dark = true, settingsOpen = true) {
        compose.onNodeWithTag("settings-content").performScrollToKey("playback")
    }

    @Test fun playerStreamReadout() = shot("player_stream_readout", dark = true, expanded = true, playerState = playing.copy(stream = StreamFormat("Opus", 160)))

    @Test fun settingsSound() = shot("settings_sound", dark = true, settingsOpen = true) {
        compose.onNodeWithTag("settings-content").performScrollToKey("sound")
    }

    @Test fun settingsDownloads() = shot("settings_downloads", dark = true, settingsOpen = true, downloads = Fixtures.downloads) {
        compose.onNodeWithTag("settings-content").performScrollToKey("downloads")
    }
    @Test fun settingsDownloadsExported() = shot(
        "settings_downloads_exported", dark = true, settingsOpen = true, downloads = Fixtures.downloads,
        export = DownloadExport.Done(exported = 12, withLyrics = 9, untagged = 0, skipped = 1),
    ) {
        compose.onNodeWithTag("settings-content").performScrollToKey("downloads")
    }
    @Test fun libraryDownloadsExporting() = shot(
        "library_downloads_exporting", dark = false, tab = Tab.LIBRARY, downloads = Fixtures.downloads, export = DownloadExport.Working(done = 4, total = 12),
    ) {
        compose.onNode(hasText("Downloads") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("library-content").performScrollToKey("download-${Fixtures.downloads.last().track.id}")
    }

    private val rock = EqualiserSettings().withPreset(EqualiserPreset.ROCK)

    // The Sound group at the top, so its first row is clear of the dock; then make sure the page opened before capturing.
    private fun openEqualiser() {
        compose.onNodeWithTag("settings-content").performScrollToKey("sound")
        compose.onNodeWithText("Equaliser").performClick()
        compose.onNodeWithContentDescription("Preamp").assertExists()
    }

    @Test fun equaliserDark() = shot("equaliser", dark = true, settingsOpen = true, equaliser = rock) { openEqualiser() }
    @Test fun equaliserLight() = shot("equaliser", dark = false, settingsOpen = true, equaliser = rock) { openEqualiser() }
    @Test fun equaliserEnd() = shot("equaliser_end", dark = true, settingsOpen = true, equaliser = rock) {
        openEqualiser()
        // Scrolled right to the end, where the page's bottom padding lifts Reset clear of the dock.
        compose.onNodeWithTag("equaliser-content").performTouchInput { repeat(3) { swipeUp() } }
    }
    @Test fun equaliserCustomWithPhoneOption() = sheet("equaliser_custom") { EqualiserPanel(rock.withBand(0, 9), systemEqualiser = true, onChange = {}, onOpenSystemEqualiser = {}) }
    @Test fun equaliserPhone() = sheet("equaliser_phone") { EqualiserPanel(rock.copy(useSystem = true), systemEqualiser = true, onChange = {}, onOpenSystemEqualiser = {}) }
    @Test fun playerMoreOptions() = sheet("player_more") { PlayerMoreOptions(playing.copy(equaliser = rock, trimDb = -2), {}, {}, {}) }
    @Test fun songVolume() = sheet("song_volume") { SongVolume(playing.copy(trimDb = 3), PlayerActions.None) }

    @Test fun scrubberMidpoint() = shot(
        "player_scrubber_midpoint", dark = true, expanded = true,
        playerState = playing.copy(positionMs = track.durationMs / 2),
    )

    @Test
    @Config(qualifiers = "w320dp-h720dp-xxhdpi")
    fun homeNarrowLargeText() = shot("home_narrow_large_text", dark = true, fontScale = 2f)

    @Test
    @Config(qualifiers = "w320dp-h720dp-xxhdpi")
    fun libraryNarrowLargeText() = shot("library_narrow_large_text", dark = true, tab = Tab.LIBRARY, fontScale = 2f)

    @Test
    @Config(qualifiers = "w320dp-h720dp-xxhdpi")
    fun searchNarrowLargeText() = shot("search_narrow_large_text", dark = true, tab = Tab.SEARCH, fontScale = 2f)
}

/** Shows one page whatever route is open, so a screenshot needs no catalogue. */
internal class FixedPage(state: DetailState) : DetailPages {
    private val flow = MutableStateFlow(state)

    override fun state(route: DetailRoute): StateFlow<DetailState> = flow

    override fun moodPage(route: DetailRoute.Mood): StateFlow<FeedState> = error("A fixed page is a catalogue or own page, not a mood")

    override fun stats(range: StatsRange): StateFlow<StatsState> = error("A fixed page is a catalogue or own page, not stats")

    override fun retry(route: DetailRoute) = Unit
}
