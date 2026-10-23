package app.sitara.music.core.data.imports

import app.sitara.music.core.data.library.toColumns
import app.sitara.music.core.data.library.toTrack
import app.sitara.music.core.model.ImportJob
import app.sitara.music.core.model.ImportProblem
import app.sitara.music.core.model.ImportResult
import app.sitara.music.core.model.ImportReviewRow
import app.sitara.music.core.model.MatchState
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SourceSong
import app.sitara.music.core.model.SourceSongList
import app.sitara.music.core.model.SpotifyLink
import app.sitara.music.core.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** What an import reads songs from; [key] names the source, so importing it again updates the same playlist. */
sealed interface ImportSource {
    val key: String

    /** A playlist of the catalogue, by its id. */
    data class CataloguePlaylist(val id: String) : ImportSource {
        override val key: String get() = "playlist:$id"
    }

    /** An M3U or CSV file the listener picked, by its name and its text. */
    data class PlaylistFile(val fileName: String, val text: String) : ImportSource {
        override val key: String get() = "file:$fileName"
    }

    /** A public Spotify playlist or album; its songs are looked up in the catalogue like a file's. */
    data class SpotifyList(val link: SpotifyLink) : ImportSource {
        override val key: String get() = "$SPOTIFY_KEY_PREFIX${link.kind.name.lowercase()}:${link.id}"
    }
}

/** How the key of every Spotify import starts. */
const val SPOTIFY_KEY_PREFIX = "spotify:"

/** The catalogue an import reads playlists from and looks songs up in. Failures the listener can act on are [ImportFailedException]s. */
interface ImportCatalogue {
    suspend fun playlist(id: String): Playlist

    /** The song [link] points to, or null when it is not a song link of this catalogue (decided without the network). */
    suspend fun song(link: String): Track?

    /** Songs for [query], best first. */
    suspend fun search(query: String): List<Track>
}

class ImportFailedException(val problem: ImportProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/**
 * Turns a playlist link or file into a playlist in the library. Every step is kept in the database as it happens:
 * [open] reads the source once, [match] looks up one song at a time and keeps each answer, so an import cut short
 * (cancelled, offline, the app closed) goes on where it stopped and never adds a song twice.
 */
class PlaylistImporter(
    private val dao: ImportDao,
    private val catalogue: ImportCatalogue,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Reads a public Spotify list; failures the listener can act on are [ImportFailedException]s. */
    private val spotify: suspend (SpotifyLink) -> SourceSongList = { throw ImportFailedException(ImportProblem.LINK_NOT_RECOGNISED) },
) {
    /** The newest import not finished yet, if any. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val unfinished: Flow<ImportJob?> = dao.unfinished().flatMapLatest<_, ImportJob?> { job -> job?.let { job(it.id) } ?: flowOf(null) }

    /** The import [id] as far as it has got; null once discarded or replaced by a newer import of its source. */
    fun job(id: Long): Flow<ImportJob?> = combine(dao.observeJob(id), dao.counts(id), dao.reviewRows(id), dao.candidates(id)) { job, counts, review, candidates ->
        job ?: return@combine null
        val byState = counts.associate { MatchState.valueOf(it.state) to it.count }
        val offered = candidates.groupBy({ it.position }) { it.track.toTrack() }
        ImportJob(
            id = job.id,
            name = job.name,
            pending = byState[MatchState.PENDING] ?: 0,
            matched = byState[MatchState.MATCHED] ?: 0,
            uncertain = byState[MatchState.UNCERTAIN] ?: 0,
            notFound = byState[MatchState.NOT_FOUND] ?: 0,
            skipped = byState[MatchState.SKIPPED] ?: 0,
            updatesPlaylist = job.playlistId != null,
            fromSpotify = job.sourceKey.startsWith(SPOTIFY_KEY_PREFIX),
            review = review.map { ImportReviewRow(it.position, it.source(), MatchState.valueOf(it.state), it.match?.toTrack(), offered[it.position].orEmpty()) },
        )
    }

    /**
     * Starts importing [source] and returns the job's id. An unfinished import of the same source is picked up instead,
     * without reading the source again. After a finished one, songs the listener (or the importer) decided keep that decision.
     */
    suspend fun open(source: ImportSource): Long {
        val earlier = dao.jobs(source.key)
        earlier.firstOrNull { !it.finished }?.let { return it.id }
        val (name, rows) = read(source)
        if (rows.isEmpty()) throw ImportFailedException(ImportProblem.NOTHING_TO_IMPORT)
        val previous = earlier.firstOrNull { it.finished }
        val decided = previous?.let { dao.rows(it.id) }.orEmpty().filter { it.state in DECIDED }.associateBy { it.source() }
        val carried = rows.map { row -> decided[row.source()]?.let { row.copy(state = it.state, match = it.match) } ?: row }
        return dao.create(ImportJobEntity(sourceKey = source.key, name = name, playlistId = previous?.playlistId?.takeIf { dao.playlistExists(it) }, finished = false, createdAt = clock()), carried)
    }

    /** Looks up every song still pending, keeping each answer as it comes; cancelling stops at the song being looked up. */
    suspend fun match(id: Long) {
        while (true) {
            val row = dao.nextPending(id) ?: return
            val (decided, candidates) = lookUp(row)
            dao.record(decided, candidates)
        }
    }

    /** The listener takes [track] for the row at [position]. */
    suspend fun choose(id: Long, position: Int, track: Track) {
        val row = dao.row(id, position) ?: return
        dao.update(row.copy(state = MatchState.MATCHED.name, match = track.toColumns()))
    }

    /** The listener leaves the row at [position] out. */
    suspend fun skip(id: Long, position: Int) {
        val row = dao.row(id, position) ?: return
        dao.update(row.copy(state = MatchState.SKIPPED.name))
    }

    /** Puts the matched songs, in source order and once each, into the job's playlist; rows still uncertain are left out. */
    suspend fun finish(id: Long): ImportResult {
        val job = checkNotNull(dao.job(id)) { "No import $id" }
        val rows = dao.rows(id)
        check(rows.none { it.state == MatchState.PENDING.name }) { "Import $id is still looking songs up" }
        val tracks = rows.filter { it.state == MatchState.MATCHED.name }.mapNotNull { it.match }.distinctBy { it.id }
        val updates = job.playlistId?.let { dao.playlistExists(it) } == true
        return ImportResult(dao.finish(job, tracks, clock()), job.name, tracks.size, updates)
    }

    suspend fun discard(id: Long) = dao.delete(id)

    private suspend fun read(source: ImportSource): Pair<String, List<ImportRowEntity>> = when (source) {
        is ImportSource.CataloguePlaylist -> {
            val playlist = catalogue.playlist(source.id)
            playlist.title.ifBlank { source.id } to playlist.tracks.take(MAX_SONGS).mapIndexed { position, track ->
                ImportRowEntity(0, position, track.title, track.artist, track.album, track.durationMs, null, MatchState.MATCHED.name, false, track.toColumns())
            }
        }
        is ImportSource.PlaylistFile -> {
            val name = source.fileName.substringBeforeLast('.').ifBlank { source.fileName }
            name to parsePlaylistFile(source.fileName, source.text).pending()
        }
        is ImportSource.SpotifyList -> {
            val list = spotify(source.link)
            list.name.ifBlank { "Spotify playlist" } to list.songs.pending()
        }
    }

    private fun List<SourceSong>.pending() = take(MAX_SONGS).mapIndexed { position, song ->
        ImportRowEntity(0, position, song.title, song.artist, song.album, song.durationMs, song.link, MatchState.PENDING.name, false, null)
    }

    private suspend fun lookUp(row: ImportRowEntity): Pair<ImportRowEntity, List<ImportCandidateEntity>> {
        val source = row.source()
        source.link?.let { link -> catalogue.song(link) }?.let { return row.copy(state = MatchState.MATCHED.name, match = it.toColumns()) to emptyList() }
        val query = listOf(source.artist, source.title).filter(String::isNotBlank).joinToString(" ")
        val ranked = if (query.isEmpty()) emptyList() else catalogue.search(query).take(CANDIDATES).map { it to matchScore(source, it) }.sortedByDescending { it.second }
        val (best, score) = ranked.firstOrNull() ?: return row.copy(state = MatchState.NOT_FOUND.name) to emptyList()
        return when {
            score >= CONFIDENT -> row.copy(state = MatchState.MATCHED.name, match = best.toColumns()) to emptyList()
            score >= PLAUSIBLE -> row.copy(state = MatchState.UNCERTAIN.name, needsReview = true, match = best.toColumns()) to
                ranked.mapIndexed { ordinal, (track, _) -> ImportCandidateEntity(row.jobId, row.position, ordinal, track.toColumns()) }
            else -> row.copy(state = MatchState.NOT_FOUND.name) to emptyList()
        }
    }

    companion object {
        /** Songs read from one source at most. */
        const val MAX_SONGS = 2_000

        // Search results scored per song; the best of them are offered when the match is uncertain.
        private const val CANDIDATES = 5
        private val DECIDED = setOf(MatchState.MATCHED.name, MatchState.SKIPPED.name)
    }
}
