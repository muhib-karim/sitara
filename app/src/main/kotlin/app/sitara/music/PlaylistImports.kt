package app.sitara.music

import app.sitara.music.core.data.imports.ImportFailedException
import app.sitara.music.core.data.imports.ImportSource
import app.sitara.music.core.data.imports.PlaylistImporter
import app.sitara.music.core.model.ImportJob
import app.sitara.music.core.model.ImportProblem
import app.sitara.music.core.model.ImportResult
import app.sitara.music.core.model.Track
import app.sitara.music.feature.library.ImportActions
import app.sitara.music.feature.library.ImportState
import app.sitara.music.source.spotify.isSpotifySongLink
import app.sitara.music.source.spotify.spotifyLinkFrom
import app.sitara.music.source.youtube.playlistIdFromLink
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The import screen's state and actions. Opening the screen only reads the database (an unfinished import to resume);
 * the network is asked once the listener imports. Work runs in [scope], so leaving the screen does not stop an import;
 * Cancel stops it at the song being looked up and forgets it.
 */
class PlaylistImports(
    private val scope: CoroutineScope,
    private val importer: PlaylistImporter,
    private val readFile: suspend (uri: String) -> ImportSource.PlaylistFile,
) : ImportActions {
    private sealed interface Phase {
        data class Start(val problem: ImportProblem? = null) : Phase

        data object Reading : Phase

        data class Running(val id: Long, val problem: ImportProblem? = null) : Phase

        data class Done(val result: ImportResult, val leftOut: Int) : Phase
    }

    private val phase = MutableStateFlow<Phase>(Phase.Start())
    private val link = MutableStateFlow("")
    private var work: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ImportState> = phase.flatMapLatest<Phase, ImportState> { phase ->
        when (phase) {
            is Phase.Start -> combine(link, importer.unfinished) { text, unfinished -> ImportState.Start(text, unfinished, phase.problem) }
            Phase.Reading -> flowOf(ImportState.Reading)
            is Phase.Running -> importer.job(phase.id).filterNotNull().map { ImportState.Running(it, phase.problem) }
            is Phase.Done -> flowOf(ImportState.Done(phase.result, phase.leftOut))
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ImportState.Start())

    /** Puts a shared link in the field, unless an import is under way. */
    fun prefill(text: String) {
        if (phase.value is Phase.Reading || phase.value is Phase.Running) return
        link.value = text
        phase.value = Phase.Start()
    }

    override fun setLink(text: String) {
        link.value = text
    }

    override fun importLink() {
        val text = link.value
        val spotify = spotifyLinkFrom(text)
        val id = playlistIdFromLink(text)
        when {
            spotify != null -> start { importer.open(ImportSource.SpotifyList(spotify)) }
            id != null -> start { importer.open(ImportSource.CataloguePlaylist(id)) }
            isSpotifySongLink(text) -> phase.value = Phase.Start(ImportProblem.SPOTIFY_SONG_LINK)
            else -> phase.value = Phase.Start(ImportProblem.LINK_NOT_RECOGNISED)
        }
    }

    override fun importFile(uri: String) = start { importer.open(readFile(uri)) }

    override fun resume() {
        val id = (phase.value as? Phase.Running)?.id ?: (state.value as? ImportState.Start)?.unfinished?.id ?: return
        start { id }
    }

    override fun cancel() {
        val id = (phase.value as? Phase.Running)?.id ?: (state.value as? ImportState.Start)?.unfinished?.id
        val running = work
        scope.launch {
            // The import stops before it is forgotten, so no answer is written for a job that is gone.
            running?.cancelAndJoin()
            id?.let { importer.discard(it) }
            phase.value = Phase.Start()
        }
    }

    override fun choose(position: Int, track: Track) = onJob { importer.choose(it, position, track) }

    override fun skip(position: Int) = onJob { importer.skip(it, position) }

    override fun finish() = onJob { id -> phase.value = done(id, importer.job(id).first() ?: return@onJob) }

    override fun close() {
        link.value = ""
        phase.value = Phase.Start()
    }

    /** Reads the source (unless [open] picks up a job already read), looks every song up, and finishes when nothing needs a look. */
    private fun start(open: suspend () -> Long) {
        work?.cancel()
        work = scope.launch {
            phase.value = Phase.Reading
            val id = try {
                open()
            } catch (e: ImportFailedException) {
                phase.value = Phase.Start(e.problem)
                return@launch
            } catch (e: IOException) {
                phase.value = Phase.Start(ImportProblem.OFFLINE)
                return@launch
            }
            phase.value = Phase.Running(id)
            try {
                importer.match(id)
            } catch (e: IOException) {
                phase.value = Phase.Running(id, ImportProblem.OFFLINE)
                return@launch
            }
            val job = importer.job(id).first() ?: return@launch
            if (job.review.isEmpty() && job.matched > 0) phase.value = done(id, job)
        }
    }

    private suspend fun done(id: Long, job: ImportJob) = Phase.Done(importer.finish(id), leftOut = job.total - job.matched)

    private fun onJob(block: suspend (Long) -> Unit) {
        val id = (phase.value as? Phase.Running)?.id ?: return
        scope.launch { block(id) }
    }
}
