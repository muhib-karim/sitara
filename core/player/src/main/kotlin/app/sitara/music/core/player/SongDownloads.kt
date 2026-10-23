package app.sitara.music.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.StreamResolver
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Songs kept on the device for offline play. Media3's [DownloadManager] fetches each song through the same
 * `sitara://track/<id>` URI and [StreamUriResolver] the player uses, so a fresh stream URL is resolved whenever a
 * download starts or resumes. Both write to and read from [cache] under that URI, so the player finds a downloaded
 * song there and never touches the network for it. Each song's details travel in its download request.
 * Create and use on the main thread.
 */
@OptIn(UnstableApi::class)
class SongDownloads(
    private val context: Context,
    resolver: StreamResolver,
    private val scope: CoroutineScope,
    wifiOnly: Flow<Boolean>,
) {
    private val database = StandaloneDatabaseProvider(context)

    /** Holds only downloads: the player reads it but never writes streamed songs into it. */
    val cache: Cache = SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), database)

    val manager = DownloadManager(
        context,
        database,
        cache,
        ResolvingDataSource.Factory(DefaultDataSource.Factory(context), StreamUriResolver(resolver, ::storedTrack)),
        // Each download fetches on its own thread.
        Executor(Runnable::run),
    ).apply { requirements = requirementsFor(wifiOnly = true) }

    private val _songs = MutableStateFlow<List<SongDownload>>(emptyList())

    /** Every download, newest first. */
    val songs: StateFlow<List<SongDownload>> = _songs.asStateFlow()

    private var downloads = emptyMap<String, Download>()
    private var progressJob: Job? = null

    init {
        manager.addListener(
            object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    scope.launch { loadStored() }
                }

                override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                    update(downloads + (download.request.id to download))
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    update(downloads - download.request.id)
                }

                override fun onRequirementsStateChanged(downloadManager: DownloadManager, requirements: Requirements, notMetRequirements: Int) = publish()
            },
        )
        scope.launch { wifiOnly.collect { manager.requirements = requirementsFor(it) } }
    }

    /** Queues [tracks]; the download service keeps going after the app is closed. */
    fun download(tracks: List<Track>) = tracks.forEach { track ->
        val request = DownloadRequest.Builder(track.id, StreamUriResolver.uriFor(track.id)).setData(DownloadedTrack.encode(track)).build()
        DownloadService.sendAddDownload(context, SitaraDownloadService::class.java, request, false)
    }

    /** Cancels a running download or deletes a finished one. */
    fun remove(trackId: String) = DownloadService.sendRemoveDownload(context, SitaraDownloadService::class.java, trackId, false)

    fun removeAll() = DownloadService.sendRemoveAllDownloads(context, SitaraDownloadService::class.java, false)

    /** Songs downloaded in full, newest first, read from the download index so they are right before [songs] has loaded. */
    suspend fun downloaded(): List<Track> = withContext(Dispatchers.IO) {
        manager.downloadIndex.getDownloads(Download.STATE_COMPLETED).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.download) }
        }
    }.sortedByDescending { it.startTimeMs }.mapNotNull { DownloadedTrack.decode(it.request.data) }

    /** The downloaded file of [trackId], or null unless all of it is in the cache (see [readWholeDownload]). */
    suspend fun readWhole(trackId: String): ByteArray? = withContext(Dispatchers.IO) { readWholeDownload(cache, trackId) }

    /** Starts the service so downloads left unfinished when the process ended carry on; call while the app is in the foreground. */
    fun resumeUnfinished() = DownloadService.start(context, SitaraDownloadService::class.java)

    private suspend fun loadStored() {
        val stored = withContext(Dispatchers.IO) {
            manager.downloadIndex.getDownloads().use { cursor ->
                buildMap<String, Download> { while (cursor.moveToNext()) put(cursor.download.request.id, cursor.download) }
            }
        }
        // Changes reported while the index was being read are newer.
        update(stored + downloads)
    }

    private fun update(next: Map<String, Download>) {
        downloads = next
        publish()
        // Media3 reports progress only when asked, so poll while something downloads.
        val downloading = next.values.any { it.state == Download.STATE_DOWNLOADING }
        if (downloading && progressJob == null) {
            progressJob = scope.launch {
                while (true) {
                    delay(PROGRESS_INTERVAL_MS)
                    downloads = downloads + manager.currentDownloads.associateBy { it.request.id }
                    publish()
                }
            }
        } else if (!downloading) {
            progressJob?.cancel()
            progressJob = null
        }
    }

    private fun publish() {
        val notMet = manager.notMetRequirements
        _songs.value = downloads.values.filter { it.state != Download.STATE_REMOVING }.sortedByDescending { it.startTimeMs }.mapNotNull { download ->
            DownloadedTrack.decode(download.request.data)?.let { track ->
                SongDownload(track, downloadState(download.state, notMet), downloadPercent(download.percentDownloaded), download.bytesDownloaded)
            }
        }
    }

    /** Called on a download thread when a download opens its stream. */
    private fun storedTrack(id: String): Track? = manager.downloadIndex.getDownload(id)?.request?.data?.let(DownloadedTrack::decode)

    private fun requirementsFor(wifiOnly: Boolean) = Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

    private companion object {
        const val PROGRESS_INTERVAL_MS = 1_000L
    }
}
