package app.sitara.music.feature.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.sitara.music.core.common.formatListening
import app.sitara.music.core.designsystem.ArtworkBackdrop
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraColors
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.model.ListeningStats
import app.sitara.music.core.model.StatsRange
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The card shows this many top songs. */
private const val CARD_SONGS = 5

/** The card as it will be shared, with Share image above it so it stays in reach on a short phone. */
@Composable
internal fun StatsShareSheet(stats: ListeningStats, range: StatsRange, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val card = rememberGraphicsLayer()
    SitaraSheet(onDismiss) { close ->
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = SitaraSpacing.Gutter, end = SitaraSpacing.Gutter, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Share your stats", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                PillButton("Share image", { scope.launch { shareStatsCard(context, card.toImageBitmap().asAndroidBitmap()); close {} } })
            }
            StatsShareCard(
                stats, range,
                // What the card draws is recorded once more into a layer, which becomes the shared image.
                Modifier.drawWithContent {
                    card.record { this@drawWithContent.drawContent() }
                    drawLayer(card)
                },
            )
        }
    }
}

/**
 * A 9:16 card over the top song's cover, blurred and shaded like Now Playing so its white and grey text keeps 4.5:1:
 * the range, the top song's cover, time heard, the top artist and the top five songs. TalkBack reads it as one summary.
 */
@Composable
fun StatsShareCard(stats: ListeningStats, range: StatsRange, modifier: Modifier = Modifier) {
    val top = stats.topSongs.take(CARD_SONGS)
    val artist = stats.topArtists.firstOrNull()
    val heading = "${range.label} on Sitara"
    val summary = listOfNotNull(
        heading,
        "${formatListening(stats.listenedMs)} listened",
        artist?.let { "Top artist: ${it.name}" },
        "Top songs: " + top.mapIndexed { i, song -> "${i + 1}, ${song.track.title} by ${song.track.artist}" }.joinToString("; "),
    ).joinToString(". ")
    Box(
        modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(SitaraRadius.Artwork))
            .testTag("stats-share-card").clearAndSetSemantics { contentDescription = summary },
    ) {
        top.firstOrNull()?.let { ArtworkBackdrop(it.track.artwork, Modifier.fillMaxSize()) }
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(SitaraIcons.Star, null, tint = SitaraColors.StageContent, modifier = Modifier.size(16.dp))
                Text(heading, style = MaterialTheme.typography.labelLarge, color = SitaraColors.StageSecondary)
            }
            top.firstOrNull()?.let { ArtworkImage(it.track.artwork, Modifier.padding(top = 20.dp).fillMaxWidth(0.42f), cornerRadius = SitaraRadius.Tile) }
            Spacer(Modifier.weight(1f))
            Text(formatListening(stats.listenedMs), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = SitaraColors.StageContent)
            Text("listened", style = MaterialTheme.typography.titleMedium, color = SitaraColors.StageSecondary)
            artist?.let {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ArtworkImage(it.artwork, Modifier.size(44.dp), cornerRadius = 500.dp)
                    Column {
                        Text("Top artist", style = MaterialTheme.typography.labelMedium, color = SitaraColors.StageSecondary)
                        Text(it.name, style = MaterialTheme.typography.titleMedium, color = SitaraColors.StageContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text("Top songs", style = MaterialTheme.typography.labelMedium, color = SitaraColors.StageSecondary, modifier = Modifier.padding(top = 16.dp))
            top.forEachIndexed { i, song ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}", style = MaterialTheme.typography.titleSmall, color = SitaraColors.StageSecondary,
                        textAlign = TextAlign.End, modifier = Modifier.width(20.dp).padding(end = 8.dp),
                    )
                    Column {
                        Text(song.track.title, style = MaterialTheme.typography.titleSmall, color = SitaraColors.StageContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.track.artist, style = MaterialTheme.typography.bodySmall, color = SitaraColors.StageSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Offers [image] to the share sheet as a PNG: written to the cache, so no storage permission is needed, and handed out
 * through the app's FileProvider ("<package>.files", declared in the app's manifest) with read access for the chosen app only.
 */
suspend fun shareStatsCard(context: Context, image: Bitmap) {
    val file = File(context.cacheDir, "shared/sitara-stats.png")
    withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The clip lets the share sheet show the image in its preview.
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, "Share your stats"))
}
