package app.sitara.music.core.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.scheduler.Requirements
import app.sitara.music.core.model.DownloadState

/** Media3's state for one download as Sitara shows it; [notMetRequirements] says what a waiting download waits for. */
@OptIn(UnstableApi::class)
internal fun downloadState(state: Int, notMetRequirements: Int): DownloadState = when (state) {
    Download.STATE_COMPLETED -> DownloadState.DOWNLOADED
    Download.STATE_FAILED -> DownloadState.FAILED
    Download.STATE_DOWNLOADING -> DownloadState.DOWNLOADING
    // Queued, stopped or restarting.
    else -> when {
        (notMetRequirements and Requirements.NETWORK_UNMETERED) != 0 -> DownloadState.WAITING_FOR_WIFI
        (notMetRequirements and Requirements.NETWORK) != 0 -> DownloadState.WAITING_FOR_NETWORK
        else -> DownloadState.WAITING
    }
}

/** Whole percent, or null while Media3 does not know the size yet (it reports -1). */
internal fun downloadPercent(percentDownloaded: Float): Int? = if (percentDownloaded < 0) null else percentDownloaded.toInt().coerceIn(0, 100)
