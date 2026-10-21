package app.sitara.music.widget

import androidx.compose.ui.graphics.toArgb
import app.sitara.music.core.designsystem.ArtworkPalette
import app.sitara.music.core.designsystem.CoverColors
import app.sitara.music.core.designsystem.SitaraColors
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import app.sitara.music.core.player.PlaybackSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the home-screen widget shows. */
sealed interface WidgetState {
    /** Nothing is loaded: Play something resumes the saved queue when [canResume], and opens Home otherwise. */
    data class Idle(val canResume: Boolean) : WidgetState

    data class NowPlaying(val track: Track, val playing: Boolean, val liked: Boolean, val failed: Boolean, val positionMs: Long, val durationMs: Long) : WidgetState {
        /** How far the song has got, 0 while its length is unknown. */
        val progress: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}

/** The widget's state for the player's [snapshot] (null while the player is not running). */
fun widgetState(snapshot: PlaybackSnapshot?, liked: Boolean, canResume: Boolean): WidgetState {
    val track = snapshot?.queue?.current ?: return WidgetState.Idle(canResume)
    return WidgetState.NowPlaying(track, snapshot.isPlaying, liked, snapshot.failed, snapshot.positionMs, snapshot.durationMs)
}

internal const val PROGRESS_STEP_MS = 15_000L

/**
 * The states worth drawing: a change of song, play state, heart or failure at once, and the progress line in 15 s steps.
 * While a song plays, [refresh] has the player report its position every step; while paused nothing runs at all.
 */
internal fun shownWidgetStates(states: StateFlow<WidgetState>, refresh: () -> Unit): Flow<WidgetState> = channelFlow {
    launch {
        states.map { it is WidgetState.NowPlaying && it.playing }.distinctUntilChanged().collectLatest { playing ->
            while (playing) {
                delay(PROGRESS_STEP_MS)
                refresh()
            }
        }
    }
    states.distinctUntilChanged { old, new -> old.drawn() == new.drawn() }.collect { send(it) }
}

// A move within the same 15 s step leaves the progress line where it is.
private fun WidgetState.drawn(): WidgetState = if (this is WidgetState.NowPlaying) copy(positionMs = positionMs / PROGRESS_STEP_MS) else this

/** ARGB colours for the widget drawn from a cover: [content] on [background] and [onAccent] on [accent] read at AA. */
data class WidgetColors(val background: Int, val content: Int, val accent: Int, val onAccent: Int)

/** Sitara's own colours, for the widget before anything has played. */
internal val brandArtwork = Artwork(listOf(SitaraColors.Saffron.toArgb()))

/** The cover's dark wash as the background, black or white text (whichever reads better), and its accent for the play button. */
fun widgetColors(artwork: Artwork, cover: CoverColors?): WidgetColors {
    val theme = ArtworkPalette.coverTheme(artwork, cover, dark = true)
    return WidgetColors(theme.backdropTop, ArtworkPalette.inkOn(theme.backdropTop), theme.accent, theme.onAccent)
}
