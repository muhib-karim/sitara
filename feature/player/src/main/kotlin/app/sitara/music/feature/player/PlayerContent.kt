package app.sitara.music.feature.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkBackdrop
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.ArtworkTheme
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.GlassSurface
import app.sitara.music.core.designsystem.LocalArtworkWash
import app.sitara.music.core.designsystem.LocalGlassHaze
import app.sitara.music.core.designsystem.LocalReducedMotion
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraColors
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.designsystem.animateArtworkColor
import app.sitara.music.core.designsystem.dragHandle
import app.sitara.music.core.designsystem.rememberArtworkTheme
import app.sitara.music.core.designsystem.rememberReorderState
import app.sitara.music.core.designsystem.reorderableRow
import app.sitara.music.core.designsystem.sitaraColorScheme
import app.sitara.music.core.model.ThemeMode
import app.sitara.music.core.model.Track
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

@Composable
internal fun FullPlayer(
    state: PlayerUiState,
    actions: PlayerActions,
    onCollapse: () -> Unit,
    initialPage: Int,
    artworkModifier: Modifier,
    onArtist: (String) -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val track = state.track ?: return
    val reduced = LocalReducedMotion.current
    val wash = LocalArtworkWash.current
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, 2)) { 3 }
    val scope = rememberCoroutineScope()
    val backdrop = rememberHazeState()
    fun selectPage(page: Int) {
        scope.launch { if (reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    }
    val theme = rememberArtworkTheme(track.artwork)
    val stage = sitaraColorScheme(if (wash) ThemeMode.ARTWORK else ThemeMode.AMOLED, dark = true)
    // The artwork theme's accent passes AA against the stage, so controls and highlights take it as primary.
    val accent = animateArtworkColor(theme.accent, "stage accent")
    val onAccent = animateArtworkColor(theme.onAccent, "stage on accent")
    MaterialTheme(colorScheme = if (wash) stage.copy(primary = accent, onPrimary = onAccent) else stage) {
        CompositionLocalProvider(LocalGlassHaze provides backdrop) {
            Box(Modifier.fillMaxSize()) {
                ArtworkBackdrop(track.artwork, Modifier.fillMaxSize().hazeSource(backdrop), theme)
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(SitaraIcons.ChevronDown, "Collapse player", onCollapse)
                        GlassSurface(Modifier.weight(1f), shape = CircleShape) {
                            Row(Modifier.fillMaxWidth().padding(4.dp)) {
                                listOf("Playing", "Lyrics", "Up next").forEachIndexed { index, label ->
                                    val selected = pager.currentPage == index
                                    Box(
                                        Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape)
                                            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                            .selectable(selected, role = Role.Tab, onClick = { selectPage(index) }).padding(horizontal = 4.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(label, color = if (selected) SitaraColors.StageContent else SitaraColors.StageSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        GlassIconButton(SitaraIcons.Timer, state.sleepTimerLabel?.let { "Sleep timer $it" } ?: "Sleep timer off", actions::cycleSleepTimer)
                    }
                    HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = true) { page ->
                        when (page) {
                            0 -> PlayingPage(state, theme, actions, artworkModifier, onQueue = { selectPage(2) }, onArtist = onArtist, onAddToPlaylist = onAddToPlaylist)
                            1 -> LyricsPanel(state, actions)
                            else -> Column(Modifier.fillMaxSize()) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Up next", style = MaterialTheme.typography.headlineMedium, color = SitaraColors.StageContent, modifier = Modifier.weight(1f))
                                    PillButton("Now playing", { selectPage(0) }, primary = false)
                                }
                                RadioControl(state.radio, actions::startRadio, Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                                if (state.upNext.isEmpty()) {
                                    Text("You're at the end of the queue", color = SitaraColors.StageSecondary, modifier = Modifier.padding(24.dp).weight(1f))
                                } else {
                                    UpNextList(state.upNext, actions, Modifier.weight(1f))
                                }
                                PlayerControls(state, actions, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayingPage(
    state: PlayerUiState,
    theme: ArtworkTheme,
    actions: PlayerActions,
    artworkModifier: Modifier,
    onQueue: () -> Unit,
    onArtist: (String) -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val heading: @Composable () -> Unit = {
        TrackHeading(state, onQueue = onQueue, onArtist = onArtist, onLike = actions::toggleLike, onAddToPlaylist = onAddToPlaylist)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val stageHeight = maxHeight
        val landscape = maxWidth > stageHeight
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtworkStage(state, theme, artworkModifier, Modifier.weight(1f).fillMaxSize())
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, bottom = 12.dp)) {
                    heading()
                    PlayerControls(state, actions)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
                // Reserve room for transport while keeping the sleeve prominent on tall phones.
                ArtworkStage(state, theme, artworkModifier, Modifier.fillMaxWidth().height((stageHeight - 240.dp).coerceIn(180.dp, 440.dp)))
                Spacer(Modifier.height(24.dp))
                heading()
                Spacer(Modifier.height(12.dp))
                PlayerControls(state, actions)
            }
        }
    }
}

@Composable
private fun ArtworkStage(state: PlayerUiState, theme: ArtworkTheme, artworkModifier: Modifier, modifier: Modifier) {
    val track = state.track ?: return
    val reduced = LocalReducedMotion.current
    val wash = LocalArtworkWash.current
    val tint = animateArtworkColor(theme.accent, "sleeve glow")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 420.dp)
        if (wash && !reduced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                Modifier.padding(top = 24.dp).size(side * 0.9f).blur(48.dp, BlurredEdgeTreatment.Unbounded)
                    .background(tint.copy(alpha = 0.35f), RoundedCornerShape(SitaraRadius.Artwork)),
            )
        }
        ArtworkImage(track.artwork, artworkModifier.size(side), cornerRadius = SitaraRadius.Artwork)
    }
}

@Composable
private fun TrackHeading(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onQueue: (() -> Unit)? = null,
    onArtist: ((String) -> Unit)? = null,
    onLike: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
) {
    val track = state.track ?: return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (compact) {
            ArtworkImage(track.artwork, Modifier.size(44.dp), cornerRadius = SitaraRadius.Cover)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, color = SitaraColors.StageContent, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val artistId = track.artistId
            val link = if (onArtist != null && artistId != null) {
                Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClickLabel = "Open artist") { onArtist(artistId) }
            } else {
                Modifier
            }
            Text(track.artist, color = SitaraColors.StageSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = link)
        }
        if (onLike != null) {
            GlassIconButton(
                if (state.liked) SitaraIcons.HeartFilled else SitaraIcons.Heart,
                if (state.liked) "Remove from liked songs" else "Like",
                onLike,
                tint = if (state.liked) MaterialTheme.colorScheme.primary else SitaraColors.StageContent,
            )
        }
        if (onAddToPlaylist != null) GlassIconButton(SitaraIcons.Add, "Add to playlist", onAddToPlaylist)
        if (onQueue != null) GlassIconButton(SitaraIcons.Queue, "Open queue", onQueue)
    }
}

@Composable
private fun LyricsPanel(state: PlayerUiState, actions: PlayerActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Column(Modifier.weight(1f)) {
                    TrackHeading(state, Modifier.padding(vertical = 8.dp), compact = true)
                    LyricsPage(state.lyrics, state.positionMs, SitaraColors.StageContent, actions::seekTo, actions::retryLyrics, Modifier.weight(1f))
                }
                PlayerControls(state, actions, Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 12.dp))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TrackHeading(state, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), compact = true)
                LyricsPage(state.lyrics, state.positionMs, SitaraColors.StageContent, actions::seekTo, actions::retryLyrics, Modifier.weight(1f))
                PlayerControls(state, actions, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }
    }
}

/** Up next with drag to reorder and remove; shows a moved song in its new place before the queue confirms it. */
@Composable
private fun UpNextList(upNext: List<Track>, actions: PlayerActions, modifier: Modifier) {
    val reorder = rememberReorderState()
    var order by remember(upNext) { mutableStateOf(upNext) }
    val move = { from: Int, to: Int ->
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        actions.moveUpNext(from, to)
    }
    LazyColumn(modifier.testTag("up-next")) {
        itemsIndexed(order, key = { index, item -> "$index-${item.id}" }, contentType = { _, _ -> "queue track" }) { index, item ->
            TrackRow(item, onClick = { actions.playQueueIndex(index) }, modifier = Modifier.reorderableRow(reorder, index, order.size, move)) {
                IconButton(onClick = { actions.removeUpNext(index) }) {
                    Icon(SitaraIcons.Close, "Remove ${item.title} from the queue", tint = SitaraColors.StageSecondary)
                }
                Icon(
                    SitaraIcons.DragHandle, null, tint = SitaraColors.StageSecondary,
                    modifier = Modifier.size(48.dp).dragHandle(reorder, index, order.size, move).padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun RadioControl(status: RadioStatus, onStart: () -> Unit, modifier: Modifier = Modifier) {
    when (status) {
        RadioStatus.READY -> PillButton("Start radio", onStart, modifier, primary = false)
        RadioStatus.LOADING -> Text("Finding similar songs…", color = SitaraColors.StageSecondary, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        RadioStatus.UNAVAILABLE -> Text("No similar songs found for this one", color = SitaraColors.StageSecondary, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
    }
}
