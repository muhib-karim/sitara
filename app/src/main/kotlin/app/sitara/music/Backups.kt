package app.sitara.music

import android.content.ContentResolver
import android.net.Uri
import app.sitara.music.core.data.SettingsCodec
import app.sitara.music.core.data.SettingsRepository
import app.sitara.music.core.data.backup.BackupException
import app.sitara.music.core.data.backup.BackupProblem
import app.sitara.music.core.data.backup.LibraryBackups
import app.sitara.music.feature.settings.BackupActions
import app.sitara.music.feature.settings.BackupFailure
import app.sitara.music.feature.settings.BackupStatus
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → Backup and restore: backup files written and read through the content resolver; a restore applies its settings once the library is back. */
class Backups(
    private val scope: CoroutineScope,
    private val backups: LibraryBackups,
    private val settings: SettingsRepository,
    private val resolver: ContentResolver,
) : BackupActions {
    private val status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val state: StateFlow<BackupStatus> = status

    // A file that cannot be written or read (I/O, a revoked grant) ends as the job's failure.
    override fun backUp(uri: String) = job(BackupFailure.UNWRITABLE) {
        val file = backups.export(SettingsCodec.encode(settings.settings.first()), BuildConfig.VERSION_NAME)
        withContext(Dispatchers.IO) {
            // "wt" truncates: a shorter backup over an older file must not keep the old file's tail.
            resolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(file.text.toByteArray()) } ?: throw IOException("No stream for $uri")
        }
        file.summary.run { BackupStatus.BackedUp(likedSongs, playlists, plays) }
    }

    override fun restore(uri: String) = job(BackupFailure.UNREADABLE) {
        val text = withContext(Dispatchers.IO) { read(Uri.parse(uri)) }
        val restored = try {
            backups.restore(text)
        } catch (e: BackupException) {
            return@job BackupStatus.Failed(
                when (e.problem) {
                    BackupProblem.NOT_A_BACKUP -> BackupFailure.NOT_A_BACKUP
                    BackupProblem.DAMAGED -> BackupFailure.DAMAGED
                    BackupProblem.NEWER_VERSION -> BackupFailure.NEWER_VERSION
                },
            )
        }
        // shortcut: the settings store is not part of Room's transaction; a failure here leaves the library restored and the settings as they were.
        settings.update { SettingsCodec.decode(restored.settings) }
        restored.summary.run { BackupStatus.Restored(likedSongs, playlists, plays) }
    }

    // One at a time; the page hides both buttons while one runs. Anything unforeseen is worded as [failure], never a crash.
    private fun job(failure: BackupFailure, work: suspend () -> BackupStatus) {
        if (status.value == BackupStatus.Working) return
        status.value = BackupStatus.Working
        scope.launch {
            status.value = try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupStatus.Failed(failure)
            }
        }
    }

    private fun read(uri: Uri): String = resolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            out.write(buffer, 0, count)
            // Far beyond any library's backup (30,000 plays take about 9 MB); a bigger file is not one.
            // shortcut: the whole file is parsed in memory; stream it (JsonReader) if real backups ever near this size.
            if (out.size() > MAX_BACKUP_BYTES) return@use ""
        }
        out.toString(Charsets.UTF_8.name())
    } ?: throw IOException("No stream for $uri")

    private companion object {
        const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    }
}
