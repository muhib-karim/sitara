package app.sitara.music.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.formatListening
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.PlaceholderBlock
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SelectionPill
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.rememberPlaceholderPulse
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.ListeningStats
import app.sitara.music.core.model.SongTotal
import app.sitara.music.core.model.StatsRange
import app.sitara.music.core.model.Track
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What Stats shows for the chosen range. */
sealed interface StatsState {
    data object Loading : StatsState

    data object Failed : StatsState

    data class Ready(val stats: ListeningStats) : StatsState
}

/**
 * The listener's stats for one range, from the history kept on the phone: time heard and plays, when in the day they
 * listen, then their top songs (a tap plays them from there), artists (a tap opens the artist) and albums. Share
 * previews the range as a card and sends it as an image.
 */
@Composable
fun StatsScreen(
    state: StatsState,
    range: StatsRange,
    onRange: (StatsRange) -> Unit,
    onBack: () -> Unit,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onArtist: (artistId: String) -> Unit,
    contentPadding: PaddingValues,
) {
    var sharing by rememberSaveable { mutableStateOf(false) }
    val shareable = (state as? StatsState.Ready)?.stats?.takeUnless { it.isEmpty }
    if (sharing && shareable != null) StatsShareSheet(shareable, range) { sharing = false }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("stats-content"), contentPadding = contentPadding) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassIconButton(SitaraIcons.Back, "Back", onBack)
                Text(
                    "Your stats", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (shareable != null) GlassIconButton(SitaraIcons.Share, "Share your stats", { sharing = true })
            }
        }
        item(key = "ranges") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsRange.entries.forEach { SelectionPill(it.label, it == range, { onRange(it) }) }
            }
        }
        when (state) {
            StatsState.Loading -> item(key = "loading", contentType = "skeleton") { StatsSkeleton() }
            StatsState.Failed -> item(key = "failed") {
                StatsMessage("Your listening history couldn’t be read. Restart Sitara to try again.")
            }
            is StatsState.Ready -> if (state.stats.isEmpty) {
                item(key = "empty") { EmptyStats(range) }
            } else {
                statsSections(state.stats, range, onPlay, onArtist)
            }
        }
    }
}

private fun LazyListScope.statsSections(
    stats: ListeningStats,
    range: StatsRange,
    onPlay: (List<Track>, Int) -> Unit,
    onArtist: (String) -> Unit,
) {
    item(key = "totals") { Totals(stats, range) }
    item(key = "hours-heading") {
        SectionHeader("When you listen", Modifier.padding(top = 12.dp), subtitle = stats.peakHour?.let { "Most around ${hourLabel(it)}" })
    }
    item(key = "hours") { HourChart(stats) }
    item(key = "songs-heading") { SectionHeader("Top songs", Modifier.padding(top = 12.dp)) }
    val tracks = stats.topSongs.map { it.track }
    itemsIndexed(stats.topSongs, key = { _, song -> "song-${song.track.id}" }) { index, song -> RankedSong(index + 1, song) { onPlay(tracks, index) } }
    item(key = "artists-heading") { SectionHeader("Top artists", Modifier.padding(top = 12.dp)) }
    item(key = "artists") {
        StatRail(stats.topArtists, key = { it.artistId ?: it.name }) { artist ->
            StatTile(artist.name, playsAndTime(artist.plays, artist.listenedMs), artist.artwork, round = true, onClick = artist.artistId?.let { id -> { onArtist(id) } })
        }
    }
    if (stats.topAlbums.isNotEmpty()) {
        item(key = "albums-heading") { SectionHeader("Top albums", Modifier.padding(top = 12.dp)) }
        item(key = "albums") {
            StatRail(stats.topAlbums, key = { "${it.title}|${it.artist}" }) { album ->
                StatTile(album.title, "${album.artist} · ${playsAndTime(album.plays, album.listenedMs)}", album.artwork, round = false, onClick = null)
            }
        }
    }
}

/** Time heard as the hero number, plays beside it, both in the accent's card. */
@Composable
private fun Totals(stats: ListeningStats, range: StatsRange) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(SitaraRadius.Artwork))
            .background(colors.surfaceContainerHigh).semantics(mergeDescendants = true) {}.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(formatListening(stats.listenedMs), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = colors.primary)
        Text("listened ${range.phrase}", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        Text(plays(stats.plays), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * Time heard by the hour each play started, as 24 bars from midnight, the busiest in the accent. TalkBack reads one
 * summary instead of the bars: the busiest hour and the three busiest hours with their time.
 */
@Composable
private fun HourChart(stats: ListeningStats) {
    val hours = stats.listenedByHour
    val peakHour = stats.peakHour
    val busiest = hours.indices.filter { hours[it] > 0 }.sortedByDescending { hours[it] }.take(3)
    val summary = "Listening by hour of the day. " + busiest.joinToString("; ") { "${hourLabel(it)}: ${formatListening(hours[it])}" }
    val peak = MaterialTheme.colorScheme.primary
    val rest = peak.copy(alpha = 0.45f)
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp).clearAndSetSemantics { contentDescription = summary }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val most = hours.max().coerceAtLeast(1)
            val slot = size.width / hours.size
            val bar = slot * 0.62f
            val floor = 3.dp.toPx()
            hours.forEachIndexed { hour, ms ->
                val height = if (ms == 0L) floor else maxOf(floor, size.height * ms / most)
                drawRoundRect(
                    color = when {
                        ms == 0L -> baseline
                        hour == peakHour -> peak
                        else -> rest
                    },
                    topLeft = Offset(hour * slot + (slot - bar) / 2, size.height - height),
                    size = Size(bar, height),
                    cornerRadius = CornerRadius(bar / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf(0, 6, 12, 18).forEach {
                Text(hourLabel(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RankedSong(rank: Int, song: SongTotal, onClick: () -> Unit) {
    val track = song.track
    val detail = playsAndTime(song.plays, song.listenedMs)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$rank, ${track.title} by ${track.artist}, $detail" }
            .padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "$rank", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End, modifier = Modifier.width(24.dp),
        )
        ArtworkImage(track.artwork, Modifier.size(48.dp), cornerRadius = SitaraRadius.Cover)
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist} · $detail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun <T> StatRail(items: List<T>, key: (T) -> Any, tile: @Composable (T) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        items(items, key = key) { Column(Modifier.width(TileWidth)) { tile(it) } }
    }
}

/** A cover with its name and plays; it opens a page only when there is one ([onClick] null: an album, or an artist without a catalogue id). */
@Composable
private fun StatTile(title: String, subtitle: String, artwork: Artwork, round: Boolean, onClick: (() -> Unit)?) {
    Column(
        Modifier.clip(RoundedCornerShape(topStart = SitaraRadius.Tile, topEnd = SitaraRadius.Tile))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $subtitle" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ArtworkImage(artwork, Modifier.fillMaxWidth(), cornerRadius = if (round) 500.dp else SitaraRadius.Tile)
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** No plays in the range yet: covers in the theme's colours and one honest line about what counts. */
@Composable
private fun EmptyStats(range: StatsRange) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FannedCovers()
        Text(
            if (range == StatsRange.ALL_TIME) "Not enough listening yet" else "Nothing played ${range.phrase} yet",
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
        )
        Text(
            "A play counts after 30 seconds, or half of a short song. Keep listening and your top songs, artists and hours appear here.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatsMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(SitaraSpacing.Gutter))
}

@Composable
private fun StatsSkeleton() {
    val pulse = rememberPlaceholderPulse()
    Column(
        Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp).clearAndSetSemantics { contentDescription = "Loading your stats" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlaceholderBlock(pulse, Modifier.fillMaxWidth().height(132.dp), RoundedCornerShape(SitaraRadius.Artwork))
        PlaceholderBlock(pulse, Modifier.fillMaxWidth().height(120.dp), RoundedCornerShape(SitaraRadius.Tile))
        repeat(3) { PlaceholderBlock(pulse, Modifier.fillMaxWidth().height(48.dp), CircleShape) }
    }
}

private val StatsRange.phrase: String get() = if (this == StatsRange.ALL_TIME) "in all" else label.lowercase()

private fun plays(count: Int) = if (count == 1) "1 play" else "$count plays"

private fun playsAndTime(count: Int, ms: Long) = "${plays(count)} · ${formatListening(ms)}"

/** The hour in the phone's own clock style: "9:00 PM" or "21:00". */
private fun hourLabel(hour: Int): String = LocalTime.of(hour, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
