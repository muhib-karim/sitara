package app.sitara.music

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.sitara.music.core.data.local.musicPermission
import app.sitara.music.core.designsystem.ScrollEdge
import app.sitara.music.core.designsystem.ShareSheetContent
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.designsystem.shareQrCard
import app.sitara.music.core.designsystem.shareText
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.DownloadExport
import app.sitara.music.core.model.LibrarySort
import app.sitara.music.core.model.LocalFolder
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.ShareTarget
import app.sitara.music.core.model.ShelfLink
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.StatsRange
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.matching
import app.sitara.music.core.model.shareTarget
import app.sitara.music.feature.home.HomeActions
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.home.HomeScreen
import app.sitara.music.feature.home.MoodScreen
import app.sitara.music.feature.library.AddToPlaylist
import app.sitara.music.feature.library.DetailScreen
import app.sitara.music.feature.library.ImportActions
import app.sitara.music.feature.library.ImportScreen
import app.sitara.music.feature.library.ImportState
import app.sitara.music.feature.library.LibraryActions
import app.sitara.music.feature.library.LibraryScreen
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.LocalActions
import app.sitara.music.feature.library.LocalGroupScreen
import app.sitara.music.feature.library.LocalLibraryState
import app.sitara.music.feature.library.LocalScreen
import app.sitara.music.feature.library.PlaylistNameSheet
import app.sitara.music.feature.library.SavedIds
import app.sitara.music.feature.library.SongActions
import app.sitara.music.feature.library.StatsScreen
import app.sitara.music.feature.library.groups
import app.sitara.music.feature.player.EqualiserScreen
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerSheet
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.player.equaliserSummary
import app.sitara.music.feature.search.SearchActions
import app.sitara.music.feature.search.SearchLanding
import app.sitara.music.feature.search.SearchScreen
import app.sitara.music.feature.search.SearchState
import app.sitara.music.feature.settings.BackupActions
import app.sitara.music.feature.settings.BackupScreen
import app.sitara.music.feature.settings.BackupStatus
import app.sitara.music.feature.settings.ScrobblingActions
import app.sitara.music.feature.settings.ScrobblingScreen
import app.sitara.music.feature.settings.ScrobblingState
import app.sitara.music.feature.settings.SettingsScreen
import app.sitara.music.feature.settings.SignInStep
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

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
    val searchLanding by vm.searchLanding.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val imports by vm.imports.state.collectAsState()
    val scrobbling by vm.scrobbling.state.collectAsState()
    val local by vm.local.collectAsState()
    val backup by vm.backups.state.collectAsState()
    val export by vm.exports.state.collectAsState()
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
        onSearchFilterChange = vm::setSearchFilter,
        onRetrySearch = vm::retrySearch,
        homeActions = vm.homeActions,
        onPlay = vm::play,
        onShuffle = vm::shuffle,
        onSettingsChange = vm::updateSettings,
        tabRequests = vm.requestedTabs,
        playerRequests = vm.requestedPlayer,
        downloads = downloads,
        onRemoveAllDownloads = vm::removeAllDownloads,
        onClearHistory = vm::clearListeningHistory,
        searchLanding = searchLanding,
        searchActions = vm,
        imports = imports,
        importActions = vm.imports,
        importRequests = vm.requestedImport,
        scrobbling = scrobbling,
        scrobblingActions = vm.scrobbling,
        local = local,
        localActions = vm,
        backup = backup,
        backupActions = vm.backups,
        export = export,
        onExportDownloads = vm.exports::export,
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
    playerRequests: Flow<Unit> = emptyFlow(),
    savedIds: SavedIds = SavedIds(),
    playlists: List<PlaylistSummary> = emptyList(),
    libraryActions: LibraryActions = LibraryActions.None,
    homeActions: HomeActions = HomeActions.None,
    downloads: List<SongDownload> = emptyList(),
    onRemoveAllDownloads: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    searchLanding: SearchLanding = SearchLanding(),
    searchActions: SearchActions = SearchActions.None,
    imports: ImportState = ImportState.Start(),
    importActions: ImportActions = ImportActions.None,
    importRequests: Flow<Unit> = emptyFlow(),
    scrobbling: ScrobblingState = ScrobblingState(),
    scrobblingActions: ScrobblingActions = ScrobblingActions.None,
    local: LocalLibraryState = LocalLibraryState(),
    localActions: LocalActions = LocalActions.None,
    backup: BackupStatus = BackupStatus.Idle,
    backupActions: BackupActions = BackupActions.None,
    export: DownloadExport = DownloadExport.Idle,
    /** Exports downloads to the folder (a tree URI) the listener picked. */
    onExportDownloads: (String) -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(if (initialSettingsOpen) Tab.SETTINGS else initialTab) }
    var detailStack by rememberSaveable(stateSaver = DetailStackSaver) { mutableStateOf(listOfNotNull(initialDetail)) }
    val browseState = rememberSaveableStateHolder()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTrack by remember { mutableStateOf<Track?>(null) }
    var queueToSave by remember { mutableStateOf<List<Track>?>(null) }
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
    var shareTarget by remember { mutableStateOf<ShareTarget?>(null) }
    val openMenu: (Track) -> Unit = { menuTrack = it }
    val open: (DetailRoute) -> Unit = { detailStack = detailStack + it }
    val closeDetails: () -> Unit = { detailStack = emptyList() }
    LaunchedEffect(tabRequests) {
        tabRequests.collect { closeDetails(); tab = it }
    }
    LaunchedEffect(importRequests) {
        importRequests.collect { tab = Tab.LIBRARY; detailStack = listOf(DetailRoute.Import) }
    }
    // The system file picker: no storage permission, and the app reads only the file the listener picks.
    val pickPlaylistFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importActions.importFile(it.toString()) } }
    // The system folder picker grants read access to the folder alone; Sitara keeps that grant across restarts.
    var relinking by remember { mutableStateOf<LocalFolder?>(null) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val folder = relinking
        relinking = null
        if (uri != null) folder?.let { localActions.relinkFolder(it, uri.toString()) } ?: localActions.addFolder(uri.toString())
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_TYPE)) { uri -> uri?.let { backupActions.backUp(it.toString()) } }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { backupActions.restore(it.toString()) } }
    // The folder picker's grant lasts while the app runs, long enough for one export; nothing is kept.
    val pickExportFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { onExportDownloads(it.toString()) } }
    val activity = LocalActivity.current
    val askMusicAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Once the system stops asking (denied for good), only Sitara's page in the system settings can grant it.
        if (!granted && activity != null && !activity.shouldShowRequestPermissionRationale(musicPermission)) activity.openAppSettings()
        localActions.rescan()
    }
    BackHandler(enabled = detailStack.isNotEmpty() || tab == Tab.SETTINGS) {
        when {
            detailStack.isNotEmpty() -> detailStack = detailStack.dropLast(1)
            equaliserOpen -> equaliserOpen = false
            else -> tab = Tab.HOME
        }
    }
    SitaraTheme(settings, nowPlaying = player.track?.artwork) {
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
                    val onShelfLink: (ShelfLink) -> Unit = { link ->
                        when (link) {
                            is ShelfLink.ToPlaylist -> open(DetailRoute.Playlist(link.id))
                            is ShelfLink.ToArtist -> open(DetailRoute.Artist(link.id))
                        }
                    }
                    if (route is DetailRoute.Mood) {
                        val state by remember(details, route) { details.moodPage(route) }.collectAsState()
                        MoodScreen(
                            route.title, state,
                            onBack = { detailStack = detailStack.dropLast(1) },
                            onRetry = { details.retry(route) },
                            onPlay = onPlay,
                            onTrackMenu = openMenu,
                            onAlbum = openAlbum,
                            onPlaylist = { open(DetailRoute.Playlist(it.id)) },
                            onArtist = openArtist,
                            onShelfLink = onShelfLink,
                            contentPadding = contentPadding,
                        )
                    } else if (route == DetailRoute.Stats) {
                        var range by rememberSaveable { mutableStateOf(StatsRange.WEEK) }
                        val state by remember(details, range) { details.stats(range) }.collectAsState()
                        StatsScreen(
                            state, range, settings.pauseListeningHistory, onRange = { range = it },
                            onBack = { detailStack = detailStack.dropLast(1) },
                            onPlay = onPlay,
                            onArtist = { open(DetailRoute.Artist(it)) },
                            contentPadding = contentPadding,
                        )
                    } else if (route == DetailRoute.Import) {
                        ImportScreen(
                            imports, importActions,
                            onPickFile = { pickPlaylistFile.launch(PLAYLIST_FILE_TYPES) },
                            onOpenPlaylist = { id -> importActions.close(); detailStack = detailStack.dropLast(1) + DetailRoute.UserPlaylist(id) },
                            onBack = { detailStack = detailStack.dropLast(1) },
                            contentPadding = contentPadding,
                        )
                    } else if (route == DetailRoute.Scrobbling) {
                        // Coming back from Last.fm's page in the browser finishes its sign-in; once per return, not per state change.
                        val inBrowser by rememberUpdatedState(scrobbling.services.any { it.signIn == SignInStep.InBrowser })
                        LifecycleResumeEffect(Unit) {
                            if (inBrowser) scrobblingActions.finishLastFmSignIn()
                            onPauseOrDispose {}
                        }
                        ScrobblingScreen(scrobbling, scrobblingActions, onBack = { detailStack = detailStack.dropLast(1) }, contentPadding = contentPadding)
                    } else if (route == DetailRoute.Backup) {
                        BackupScreen(
                            backup,
                            onBackUp = { createBackup.launch("sitara-backup-${LocalDate.now()}.json") },
                            // Some file apps label .json files as plain text or bytes.
                            onRestore = { openBackup.launch(arrayOf(BACKUP_TYPE, "text/plain", "application/octet-stream")) },
                            onBack = { detailStack = detailStack.dropLast(1) },
                            contentPadding = contentPadding,
                        )
                    } else if (route == DetailRoute.OnThisPhone) {
                        // Read again on every return too: access may just have been granted in the system's settings.
                        LifecycleResumeEffect(Unit) {
                            localActions.rescan()
                            onPauseOrDispose {}
                        }
                        LocalScreen(
                            local, settings.libraryLayout, settings.librarySort, (library as? LibraryState.Ready)?.library?.counts.orEmpty(),
                            onBack = { detailStack = detailStack.dropLast(1) },
                            onPlay = onPlay,
                            onShuffle = onShuffle,
                            onTrackMenu = openMenu,
                            onGroup = { kind, group -> open(DetailRoute.LocalGroup(kind, group.key)) },
                            onAllowAccess = { askMusicAccess.launch(musicPermission) },
                            onChooseFolder = { pickFolder.launch(null) },
                            onRelink = { relinking = it; pickFolder.launch(Uri.parse(it.uri)) },
                            actions = localActions,
                            library = libraryActions,
                            contentPadding = contentPadding,
                        )
                    } else if (route is DetailRoute.LocalGroup) {
                        val group = remember(local.songs, route) { local.songs.groups(route.kind, LibrarySort.RECENTLY_ADDED).firstOrNull { it.key == route.key } }
                        LocalGroupScreen(group, onBack = { detailStack = detailStack.dropLast(1) }, onPlay = onPlay, onShuffle = onShuffle, onTrackMenu = openMenu, contentPadding = contentPadding)
                    } else if (route != null) {
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
                            onShare = { shareTarget = it },
                        )
                    } else {
                        when (tab) {
                            Tab.HOME -> HomeScreen(
                                greetingText, home, homeActions, onPlay, openMenu, openAlbum,
                                onPlaylist = { open(DetailRoute.Playlist(it.id)) },
                                onArtist = openArtist,
                                onOwnPlaylist = { open(DetailRoute.UserPlaylist(it.id)) },
                                onShelfLink = onShelfLink,
                                contentPadding = contentPadding,
                            )
                            Tab.SEARCH -> SearchScreen(
                                query, onQueryChange, searchFilter, onSearchFilterChange, search, onRetrySearch, searchLanding, searchActions,
                                onPlay, openAlbum, openArtist, onPlaylist = { open(DetailRoute.Playlist(it.id)) },
                                onMood = { open(DetailRoute.Mood(it.params, it.title)) }, onTrackMenu = openMenu, contentPadding = contentPadding,
                                onThisPhone = remember(query, local.songs) { local.songs.matching(query) },
                            )
                            Tab.LIBRARY -> LibraryScreen(
                                library, downloads, settings.libraryLayout, settings.librarySort, onPlay,
                                onLikedSongs = { open(DetailRoute.LikedSongs) },
                                onStats = { open(DetailRoute.Stats) },
                                onImport = { open(DetailRoute.Import) },
                                onOnThisPhone = { open(DetailRoute.OnThisPhone) },
                                onPlaylist = { open(DetailRoute.UserPlaylist(it.id)) },
                                onAlbum = openAlbum,
                                onArtist = openArtist,
                                onNewPlaylist = { libraryActions.createPlaylist(it, emptyList()) },
                                onTrackMenu = openMenu,
                                onFindMusic = { tab = Tab.SEARCH },
                                library = libraryActions,
                                contentPadding = contentPadding,
                                localSongs = local.songs.size,
                                export = export,
                                onExportDownloads = { pickExportFolder.launch(null) },
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
                                    onOpenStats = { open(DetailRoute.Stats) },
                                    onClearHistory = onClearHistory,
                                    onImportPlaylist = { open(DetailRoute.Import) },
                                    onOpenBackup = { open(DetailRoute.Backup) },
                                    scrobblingSummary = scrobbling.summary,
                                    onOpenScrobbling = { open(DetailRoute.Scrobbling) },
                                    export = export,
                                    onExportDownloads = { pickExportFolder.launch(null) },
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
                expandRequests = playerRequests,
                initialPage = playerPage,
                haze = haze,
                folded = dockFolded,
                navigation = { accent, folded ->
                    FloatingNav(tab, accent, folded, onSelect = { closeDetails(); equaliserOpen = false; dockFolded = false; tab = it }, onUnfold = { dockFolded = false })
                },
                onDockHeightChanged = { measuredDockHeight = maxOf(measuredDockHeight, it) },
                onArtist = { open(DetailRoute.Artist(it)) },
                onAddToPlaylist = { playlistTrack = it },
                onSaveQueue = { queueToSave = it },
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
                        onShare = { close { shareTarget = track.shareTarget() } },
                    )
                }
            }
            shareTarget?.let { target ->
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                SitaraSheet(onDismiss = { shareTarget = null }) { close ->
                    ShareSheetContent(
                        target,
                        onSendLink = { close { shareText(context, "${target.title}: ${target.link}", "Share link") } },
                        onShareImage = { close { scope.launch { shareQrCard(context, target) } } },
                    )
                }
            }
            queueToSave?.let { tracks ->
                PlaylistNameSheet("Save queue as playlist", "", "Save", onConfirm = { queueToSave = null; libraryActions.createPlaylist(it, tracks) }, onDismiss = { queueToSave = null })
            }
            playlistTrack?.let { track ->
                SitaraSheet(onDismiss = { playlistTrack = null }) { close ->
                    AddToPlaylist(
                        track, playlists,
                        onPick = { close { libraryActions.addToPlaylist(it, track) } },
                        onCreate = { close { libraryActions.createPlaylist(it, listOf(track)) } },
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

// M3U and CSV files go by many types across file managers; anything else is refused by the parser with a worded error.
private val PLAYLIST_FILE_TYPES = arrayOf(
    "audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "text/csv", "text/comma-separated-values", "text/plain",
    "application/octet-stream",
)

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}

/** Sitara's page in the system settings, where a permission the system no longer asks for can be granted. */
private fun Activity.openAppSettings() = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

private const val BACKUP_TYPE = "application/json"
