package app.sitara.music.core.player

import android.app.Notification
import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler

/** What the download service needs from the application. */
interface DownloadDependencies {
    val songDownloads: SongDownloads
}

/**
 * Runs [SongDownloads] in the foreground with a progress notification, so downloads finish after the app is closed.
 * While a download waits for Wi-Fi the service stays in the foreground (Android 12 and later allow nothing else).
 */
@OptIn(UnstableApi::class)
class SitaraDownloadService :
    DownloadService(
        NOTIFICATION_ID,
        DownloadService.DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.downloads_channel_name,
        0,
    ) {
    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }
    private val openApp by lazy { packageManager.getLaunchIntentForPackage(packageName)?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) } }

    override fun getDownloadManager(): DownloadManager = (application as DownloadDependencies).songDownloads.manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification =
        notifications.buildProgressNotification(this, R.drawable.ic_download_notification, openApp, null, downloads, notMetRequirements)

    private companion object {
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "downloads"
    }
}
