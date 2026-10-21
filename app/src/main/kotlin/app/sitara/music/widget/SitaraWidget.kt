package app.sitara.music.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaButtonReceiver
import app.sitara.music.MainActivity
import app.sitara.music.R
import app.sitara.music.SitaraApplication
import app.sitara.music.core.designsystem.CoverArt
import app.sitara.music.core.designsystem.loadCoverArt
import app.sitara.music.core.player.PlaybackEngine
import app.sitara.music.core.player.PlaybackEngineRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Now playing on the home screen: one row with the transport, or, two rows tall, the heart and a progress line as well.
 * It is drawn from [SitaraApplication.widgetStates], so it changes only when what it shows does.
 */
class SitaraWidget : GlanceAppWidget() {
    // Drawn for the size the launcher gives it, without a copy per size held in the launcher's memory.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as SitaraApplication
        app.followPlayerOnWidgets()
        // Wallpaper colours replace the cover's, as they do in the app.
        val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && app.settings.settings.first().dynamicColor
        provideContent {
            val state by app.widgetStates.collectAsState()
            val artwork = (state as? WidgetState.NowPlaying)?.track?.artwork
            val art by produceState<CoverArt?>(null, artwork) { value = artwork?.let { loadCoverArt(context, it, COVER_PX) } }
            GlanceTheme {
                SitaraWidgetContent(state, art?.bitmap, if (dynamic) null else widgetColors(artwork ?: brandArtwork, art?.colors))
            }
        }
    }

    private companion object {
        const val COVER_PX = 192
    }
}

class SitaraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SitaraWidget()
}

/** Asks MainActivity to open Now Playing. */
internal val OpenPlayer = ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_PLAYER)

/** The widget for [state]; [colors] null draws it in the wallpaper's colours. */
@Composable
fun SitaraWidgetContent(state: WidgetState, cover: Bitmap?, colors: WidgetColors?) {
    val ink = colors?.let(::Ink) ?: Ink.dynamic()
    val tall = LocalSize.current.height >= TALL_MIN_HEIGHT
    val surface = GlanceModifier.fillMaxSize().appWidgetBackground().background(ink.background).cornerRadius(24.dp)
    when (state) {
        is WidgetState.Idle -> Row(surface.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.mipmap.ic_launcher), contentDescription = null, modifier = GlanceModifier.size(48.dp))
            Spacer(GlanceModifier.width(12.dp))
            Text("Sitara", GlanceModifier.defaultWeight(), style = TextStyle(ink.content, fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            val start = if (state.canResume) actionSendBroadcast(resumeIntent(LocalContext.current)) else actionStartActivity<MainActivity>()
            Text(
                "Play something",
                GlanceModifier.height(48.dp).background(ink.accent).cornerRadius(24.dp).padding(horizontal = 20.dp, vertical = 14.dp).clickable(start),
                style = TextStyle(ink.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        is WidgetState.NowPlaying -> {
            val openPlayer = actionStartActivity<MainActivity>(actionParametersOf(OpenPlayer to true))
            if (tall) {
                Column(surface.padding(12.dp).clickable(openPlayer)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Cover(cover, ink)
                        Spacer(GlanceModifier.width(12.dp))
                        Titles(state, ink, GlanceModifier.defaultWeight())
                        if (state.liked) {
                            Control(R.drawable.widget_heart_filled, "Remove from liked songs", actionRunCallback<ToggleLike>(), ink.accent)
                        } else {
                            Control(R.drawable.widget_heart, "Like", actionRunCallback<ToggleLike>(), ink.content)
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    LinearProgressIndicator(state.progress, GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp), color = ink.accent, backgroundColor = ink.track)
                    Spacer(GlanceModifier.defaultWeight())
                    Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                        Transport(state, ink, gap = 24)
                    }
                }
            } else {
                Row(surface.padding(horizontal = 12.dp, vertical = 8.dp).clickable(openPlayer), verticalAlignment = Alignment.CenterVertically) {
                    Cover(cover, ink)
                    Spacer(GlanceModifier.width(12.dp))
                    Titles(state, ink, GlanceModifier.defaultWeight())
                    Transport(state, ink, gap = 0)
                }
            }
        }
    }
}

private val TALL_MIN_HEIGHT = 120.dp

/** The widget's colours as Glance draws them: from the cover, or the wallpaper's (Material You) roles. */
private class Ink(val background: ColorProvider, val content: ColorProvider, val accent: ColorProvider, val onAccent: ColorProvider, val track: ColorProvider) {
    constructor(colors: WidgetColors) : this(
        ColorProvider(Color(colors.background)),
        ColorProvider(Color(colors.content)),
        ColorProvider(Color(colors.accent)),
        ColorProvider(Color(colors.onAccent)),
        ColorProvider(Color(colors.content).copy(alpha = 0.24f)),
    )

    companion object {
        @Composable
        fun dynamic() = GlanceTheme.colors.run { Ink(widgetBackground, onSurface, primary, onPrimary, surfaceVariant) }
    }
}

@Composable
private fun Cover(cover: Bitmap?, ink: Ink) {
    val modifier = GlanceModifier.size(48.dp).cornerRadius(12.dp)
    if (cover == null) {
        Box(modifier.background(ink.track)) {}
    } else {
        Image(ImageProvider(cover), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
private fun Titles(state: WidgetState.NowPlaying, ink: Ink, modifier: GlanceModifier) {
    Column(modifier) {
        Text(state.track.title, style = TextStyle(ink.content, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Text(if (state.failed) "Couldn’t play this song" else state.track.artist, style = TextStyle(ink.content, fontSize = 13.sp), maxLines = 1)
    }
}

/** Previous, play or pause (Try again after a failure) on the accent, and next; [gap] dp between them. */
@Composable
private fun Transport(state: WidgetState.NowPlaying, ink: Ink, gap: Int) {
    Control(R.drawable.widget_previous, "Previous song", actionRunCallback<SkipPrevious>(), ink.content)
    Spacer(GlanceModifier.width(gap.dp))
    val (icon, label) = when {
        state.failed -> R.drawable.widget_retry to "Try again"
        state.playing -> R.drawable.widget_pause to "Pause"
        else -> R.drawable.widget_play to "Play"
    }
    Control(icon, label, actionRunCallback<TogglePlayback>(), ink.onAccent, GlanceModifier.background(ink.accent).cornerRadius(24.dp))
    Spacer(GlanceModifier.width(gap.dp))
    Control(R.drawable.widget_next, "Next song", actionRunCallback<SkipNext>(), ink.content)
}

/** A 48 dp icon button whose name TalkBack reads. */
@Composable
private fun Control(icon: Int, label: String, action: Action, tint: ColorProvider, modifier: GlanceModifier = GlanceModifier) {
    Box(modifier.size(48.dp).semantics { contentDescription = label }.clickable(action), contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(tint))
    }
}

/**
 * Wakes the player through Media3's media button receiver, which starts it in the foreground (allowed after a widget tap
 * on Android 12 and later) and resumes the saved queue, as a headset's play button does.
 */
internal fun resumeIntent(context: Context): Intent = Intent(Intent.ACTION_MEDIA_BUTTON)
    .setClass(context, MediaButtonReceiver::class.java)
    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))

/** Runs [command] on the running player, on the main thread as the engine requires; false when the player is not running. */
private suspend fun onEngine(command: (PlaybackEngine) -> Unit): Boolean = withContext(Dispatchers.Main) {
    PlaybackEngineRegistry.engine.value?.also(command) != null
}

/** Play, pause or try again; with the player not running (the process had ended), it starts and resumes the saved queue. */
class TogglePlayback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (!onEngine { it.togglePlay() }) context.sendBroadcast(resumeIntent(context))
    }
}

// Without a running player, skips do nothing: the widget redraws as idle once the app's process is up.
class SkipNext : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        onEngine { it.next() }
    }
}

class SkipPrevious : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        onEngine { it.previous() }
    }
}

/** Likes the song that plays, or unlikes it. */
class ToggleLike : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val track = withContext(Dispatchers.Main) { PlaybackEngineRegistry.engine.value?.state?.value?.queue?.current } ?: return
        val app = context.applicationContext as SitaraApplication
        app.setLiked(track, !app.isLiked(track.id).first())
    }
}
