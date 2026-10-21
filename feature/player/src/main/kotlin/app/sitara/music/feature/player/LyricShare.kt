package app.sitara.music.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sitara.music.core.designsystem.ArtworkBackdrop
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.InterDisplay
import app.sitara.music.core.designsystem.LocalArtworkWash
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraColors
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.rememberArtworkTheme
import app.sitara.music.core.designsystem.shareImage
import app.sitara.music.core.model.Track
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** A card holds at most this many lines. */
internal const val MAX_CARD_LINES = 5

// The card is a 1080 x 1350 image: laid out at 360 x 450 dp at three pixels to the dp, whatever the phone's density.
private const val CARD_WIDTH_PX = 1080
private const val CARD_HEIGHT_PX = 1350
private val CardDensity = Density(3f, fontScale = 1f)

/** How a lyric card is coloured: over the blurred cover, in the cover's colour, or dark on paper white. */
enum class LyricCardStyle(val label: String) { ARTWORK("Artwork"), COLOUR("Colour"), LIGHT("Light") }

/**
 * The lines picked for a card after a tap on [tapped]: a tap beyond the run reaches to it (keeping at most five lines,
 * ending at the tapped one), a tap on an end lets that line go, a tap inside ends the run there. The last line stays.
 */
internal fun extendSelection(run: IntRange, tapped: Int): IntRange = when {
    tapped > run.last -> maxOf(run.first, tapped - MAX_CARD_LINES + 1)..tapped
    tapped < run.first -> tapped..minOf(run.last, tapped + MAX_CARD_LINES - 1)
    run.first == run.last -> run
    tapped == run.first -> run.first + 1..run.last
    tapped == run.last -> run.first..run.last - 1
    else -> run.first..tapped
}

/** The card editor: the three styles, the card as it will be shared, and Share image. */
@Composable
internal fun LyricShareSheet(lines: List<String>, track: Track, credit: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val card = rememberGraphicsLayer()
    var style by rememberSaveable { mutableStateOf(LyricCardStyle.ARTWORK) }
    SitaraSheet(onDismiss) { close ->
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = SitaraSpacing.Gutter, end = SitaraSpacing.Gutter, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Share lyrics", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                PillButton("Share image", { scope.launch { shareImage(context, card.toImageBitmap().asAndroidBitmap(), "sitara-lyrics.png", "Share lyrics"); close {} } })
            }
            Row(Modifier.fillMaxWidth().selectableGroup().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LyricCardStyle.entries.forEach { option -> StyleChip(option.label, option == style) { style = option } }
            }
            Box(
                Modifier.clip(RoundedCornerShape(SitaraRadius.Artwork)).fittedCard()
                    // What the card draws is recorded once more into a layer at its full size, which becomes the shared image.
                    .drawWithContent {
                        card.record { this@drawWithContent.drawContent() }
                        drawLayer(card)
                    },
            ) {
                LyricShareCard(style, lines, track, credit)
            }
        }
    }
}

@Composable
private fun StyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val fill = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.heightIn(min = SitaraSpacing.TouchTarget).clip(CircleShape).background(fill)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}

/** Lays the card out at its full pixel size and shows it scaled to the width it is given. */
private fun Modifier.fittedCard(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(CARD_WIDTH_PX, CARD_HEIGHT_PX))
    val scale = constraints.maxWidth / CARD_WIDTH_PX.toFloat()
    layout(constraints.maxWidth, (CARD_HEIGHT_PX * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/**
 * A 1080 x 1350 card of [lines] in big heavy type, with the song, the lyrics' [credit] and Sitara's mark, in [style]. On
 * the cover backdrop and the cover colour the text keeps the contrast the player gives it; paper white uses near-black ink.
 */
@Composable
fun LyricShareCard(style: LyricCardStyle, lines: List<String>, track: Track, credit: String, modifier: Modifier = Modifier) {
    val theme = rememberArtworkTheme(track.artwork)
    val (ink, secondary) = when (style) {
        LyricCardStyle.ARTWORK -> SitaraColors.StageContent to SitaraColors.StageSecondary
        LyricCardStyle.COLOUR -> Color(theme.onAccent) to Color(theme.onAccent).copy(alpha = 0.8f)
        LyricCardStyle.LIGHT -> PaperInk to PaperSecondary
    }
    val characters = lines.sumOf { it.length }
    val size = when {
        characters <= 60 -> 30.sp
        characters <= 140 -> 25.sp
        else -> 21.sp
    }
    val lyricStyle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.ExtraBold, fontSize = size, lineHeight = size * 1.2f, letterSpacing = (-0.4).sp)
    CompositionLocalProvider(LocalDensity provides CardDensity, LocalArtworkWash provides true) {
        Box(
            modifier.size(360.dp, 450.dp).testTag("lyric-share-card")
                .clearAndSetSemantics { contentDescription = "Lyric card: ${lines.joinToString(" / ")}. ${track.title} by ${track.artist}. $credit" },
        ) {
            when (style) {
                LyricCardStyle.ARTWORK -> ArtworkBackdrop(track.artwork, Modifier.fillMaxSize())
                LyricCardStyle.COLOUR -> Box(Modifier.fillMaxSize().background(Color(theme.accent)))
                LyricCardStyle.LIGHT -> Box(Modifier.fillMaxSize().background(Paper))
            }
            Column(Modifier.fillMaxSize().padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtworkImage(track.artwork, Modifier.size(48.dp), cornerRadius = 10.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lines.forEach { Text(it, style = lyricStyle, color = ink, modifier = Modifier.fillMaxWidth()) }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(credit, style = MaterialTheme.typography.labelMedium, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(SitaraIcons.Star, null, tint = ink, modifier = Modifier.padding(start = 12.dp).size(14.dp))
                    Text("Sitara", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

private val Paper = Color(0xFFF7F4EF)
private val PaperInk = Color(0xFF151515)
private val PaperSecondary = Color(0xFF555555)
