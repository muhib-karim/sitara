package app.sitara.music.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.model.ImportJob
import app.sitara.music.core.model.ImportProblem
import app.sitara.music.core.model.ImportResult
import app.sitara.music.core.model.ImportReviewRow
import app.sitara.music.core.model.MatchState
import app.sitara.music.core.model.Track

/** Where an import stands, as the import screen shows it. */
sealed interface ImportState {
    /** Waiting for a link or a file; [unfinished] is an import cut short, [problem] why the last attempt failed. */
    data class Start(val link: String = "", val unfinished: ImportJob? = null, val problem: ImportProblem? = null) : ImportState

    /** Reading the playlist or the file. */
    data object Reading : ImportState

    /** Looking songs up (while [ImportJob.pending] > 0), then reviewing the uncertain ones; [problem] stopped the lookups. */
    data class Running(val job: ImportJob, val problem: ImportProblem? = null) : ImportState

    /** Finished: the playlist made or updated, and how many songs of the source were left out. */
    data class Done(val result: ImportResult, val leftOut: Int) : ImportState
}

/** What the import screen asks for; the app runs the import. */
interface ImportActions {
    fun setLink(text: String)

    fun importLink()

    /** Imports the playlist file the system file picker returned, by its content URI. */
    fun importFile(uri: String)

    /** Goes on with the unfinished import, or tries the lookups again after a problem. */
    fun resume()

    /** Stops the import under way, or the unfinished one, and forgets it. */
    fun cancel()

    fun choose(position: Int, track: Track)

    fun skip(position: Int)

    fun finish()

    /** Back to an empty start after a finished import. */
    fun close()

    object None : ImportActions {
        override fun setLink(text: String) = Unit
        override fun importLink() = Unit
        override fun importFile(uri: String) = Unit
        override fun resume() = Unit
        override fun cancel() = Unit
        override fun choose(position: Int, track: Track) = Unit
        override fun skip(position: Int) = Unit
        override fun finish() = Unit
        override fun close() = Unit
    }
}

/**
 * Import playlist: paste a YouTube or YouTube Music playlist link or pick an M3U or CSV file, watch the songs being
 * found, check the uncertain matches, and finish into a playlist of the library.
 */
@Composable
fun ImportScreen(
    state: ImportState,
    actions: ImportActions,
    onPickFile: () -> Unit,
    onOpenPlaylist: (id: Long) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    var choosingFor by rememberSaveable { mutableStateOf<Int?>(null) }
    val review = (state as? ImportState.Running)?.job?.review.orEmpty()
    review.firstOrNull { it.position == choosingFor }?.let { row ->
        OtherMatchesSheet(row, onPick = { actions.choose(row.position, it) }, onDismiss = { choosingFor = null })
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("import-content"), contentPadding = contentPadding) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassIconButton(SitaraIcons.Back, "Back", onBack)
                Text(
                    "Import playlist", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
        }
        when (state) {
            is ImportState.Start -> start(state, actions, onPickFile)
            ImportState.Reading -> item(key = "reading") {
                ImportCard {
                    Text("Reading the playlist…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    PillButton("Cancel", actions::cancel, primary = false)
                }
            }
            is ImportState.Running -> running(state, actions, onReviewOthers = { choosingFor = it })
            is ImportState.Done -> item(key = "done") { DoneCard(state, onOpenPlaylist, actions::close) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.start(state: ImportState.Start, actions: ImportActions, onPickFile: () -> Unit) {
    state.unfinished?.let { job ->
        item(key = "unfinished") {
            ImportCard {
                Text("Finish importing “${job.name}”", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(checkedOf(job), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Resume", actions::resume)
                    PillButton("Discard", actions::cancel, primary = false)
                }
            }
        }
    }
    item(key = "link-heading") { SectionHeader("From a link", Modifier.padding(top = 8.dp), subtitle = "A YouTube or YouTube Music playlist") }
    item(key = "link") {
        val linkProblem = state.problem?.takeIf { it in LINK_PROBLEMS }
        Column(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                state.link, actions::setLink, Modifier.fillMaxWidth().testTag("import-link"),
                label = { Text("Playlist link") },
                placeholder = { Text("music.youtube.com/playlist?list=…") },
                singleLine = true,
                isError = linkProblem != null,
                supportingText = if (linkProblem != null) {
                    { Text(wording(linkProblem), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { actions.importLink() }),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Import link", actions::importLink)
                if (state.problem == ImportProblem.OFFLINE) PillButton("Try again", actions::importLink, primary = false)
            }
        }
    }
    item(key = "file-heading") { SectionHeader("From a file", Modifier.padding(top = 12.dp), subtitle = "M3U, M3U8 or CSV with title and artist columns") }
    item(key = "file") {
        val fileProblem = state.problem?.takeIf { it !in LINK_PROBLEMS }
        Column(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            fileProblem?.let { ProblemText(wording(it)) }
            PillButton(if (fileProblem != null) "Choose another file" else "Choose a file", onPickFile, primary = false)
            Text(
                "Up to 2,000 songs. Songs without a YouTube link are found by searching for their title and artist.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.running(state: ImportState.Running, actions: ImportActions, onReviewOthers: (Int) -> Unit) {
    val job = state.job
    item(key = "progress") {
        ImportCard {
            Text(job.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val checked = job.total - job.pending
            if (job.pending > 0) {
                LinearProgressIndicator(
                    progress = { checked.toFloat() / job.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = checkedOf(job) },
                )
            }
            Counts(job)
            when {
                state.problem != null -> {
                    ProblemText(wording(state.problem))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Try again", actions::resume)
                        PillButton("Cancel import", actions::cancel, primary = false)
                    }
                }
                job.pending > 0 -> PillButton("Cancel import", actions::cancel, Modifier.padding(top = 4.dp), primary = false)
                job.matched == 0 -> {
                    Text("None of these songs were found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PillButton("Discard import", actions::cancel, primary = false)
                }
                else -> {
                    val target = if (job.updatesPlaylist) "Update “${job.name}”" else "Add to your library"
                    Text(
                        "${songCount(job.matched)} ready. $target with the songs found; uncertain ones you don’t use are left out.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(if (job.updatesPlaylist) "Update playlist" else "Finish", actions::finish)
                        PillButton("Cancel import", actions::cancel, primary = false)
                    }
                }
            }
        }
    }
    if (job.pending == 0 && job.review.isNotEmpty()) {
        item(key = "review-heading") { SectionHeader("Check these matches", Modifier.padding(top = 8.dp), subtitle = "Not sure these are the songs on your list") }
        items(job.review, key = { "review-${it.position}" }) { row -> ReviewRow(row, actions, onReviewOthers) }
    }
}

/** Found, to check and not found, each with its number, read as one line by TalkBack. */
@Composable
private fun Counts(job: ImportJob) {
    val counts = listOf("Found" to job.matched, "To check" to job.uncertain, "Not found" to job.notFound)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        counts.forEach { (label, count) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(SitaraRadius.Cover)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(12.dp),
            ) {
                Text("$count", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The source's line, the match offered with its cover, and Use this / Other matches / Skip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewRow(row: ImportReviewRow, actions: ImportActions, onReviewOthers: (Int) -> Unit) {
    val match = row.match
    Column(Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("On your list: ${row.source.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (match != null) {
            val status = when (row.state) {
                MatchState.MATCHED -> "Added"
                MatchState.SKIPPED -> "Skipped"
                else -> "Not decided"
            }
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "Best match: ${match.title} by ${match.artist}. $status" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArtworkImage(match.artwork, Modifier.size(56.dp), cornerRadius = SitaraRadius.Cover)
                Column(Modifier.weight(1f)) {
                    Text(match.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(match.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (row.state == MatchState.MATCHED) Icon(SitaraIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.state != MatchState.MATCHED) PillButton("Use this", { actions.choose(row.position, match) })
                if (row.candidates.size > 1) PillButton("Other matches", { onReviewOthers(row.position) }, primary = false)
                if (row.state != MatchState.SKIPPED) PillButton("Skip", { actions.skip(row.position) }, primary = false)
            }
        }
    }
}

/** Every candidate for one row, best first; a tap takes it. */
@Composable
private fun OtherMatchesSheet(row: ImportReviewRow, onPick: (Track) -> Unit, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { close ->
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp)) {
            Text(
                "Matches for ${row.source.label}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(row.candidates, key = { it.id }) { track ->
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
                            .clickable(role = Role.Button, onClickLabel = "Use this song") { close { onPick(track) } }
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ArtworkImage(track.artwork, Modifier.size(48.dp), cornerRadius = SitaraRadius.Cover)
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (track == row.match && row.state == MatchState.MATCHED) Icon(SitaraIcons.Check, "Chosen", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneCard(state: ImportState.Done, onOpenPlaylist: (Long) -> Unit, onImportAnother: () -> Unit) {
    val result = state.result
    ImportCard {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Icon(SitaraIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Text(
            if (result.updated) "Updated “${result.name}”" else "“${result.name}” is in your library",
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            songCount(result.added) + (if (state.leftOut > 0) " · ${state.leftOut} left out" else ""),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Open playlist", { onOpenPlaylist(result.playlistId) })
            PillButton("Import another", onImportAnother, primary = false)
        }
    }
}

@Composable
private fun ImportCard(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(SitaraRadius.Artwork))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ProblemText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

private val LINK_PROBLEMS = setOf(ImportProblem.LINK_NOT_RECOGNISED, ImportProblem.PLAYLIST_UNAVAILABLE, ImportProblem.OFFLINE)

private fun checkedOf(job: ImportJob) = "${job.total - job.pending} of ${songCount(job.total)} checked"

internal fun wording(problem: ImportProblem): String = when (problem) {
    ImportProblem.LINK_NOT_RECOGNISED -> "That isn’t a YouTube or YouTube Music playlist link. In YouTube, use Share, then Copy link."
    ImportProblem.PLAYLIST_UNAVAILABLE -> "This playlist can’t be opened. It may be private or deleted."
    ImportProblem.OFFLINE -> "Couldn’t reach YouTube Music. Check your connection and try again."
    ImportProblem.NOTHING_TO_IMPORT -> "There are no songs to import there."
    ImportProblem.FILE_UNREADABLE -> "That file couldn’t be read. Choose an M3U, M3U8 or CSV file."
    ImportProblem.FILE_TOO_LARGE -> "That file is too large to import (over 4 MB)."
}
