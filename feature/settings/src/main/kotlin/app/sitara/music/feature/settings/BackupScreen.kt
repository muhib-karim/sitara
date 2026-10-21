package app.sitara.music.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSpacing

/** Why a backup or restore did not happen; the library is untouched in every case. */
enum class BackupFailure { NOT_A_BACKUP, DAMAGED, NEWER_VERSION, UNREADABLE, UNWRITABLE }

/** Where Backup and restore stands; counts say what the last backup held or the last restore brought back. */
sealed interface BackupStatus {
    data object Idle : BackupStatus

    data object Working : BackupStatus

    data class BackedUp(val likedSongs: Int, val playlists: Int, val plays: Int) : BackupStatus

    data class Restored(val likedSongs: Int, val playlists: Int, val plays: Int) : BackupStatus

    data class Failed(val failure: BackupFailure) : BackupStatus
}

/**
 * Settings → Backup and restore: write the library and settings to a file the listener chooses, or replace them with a
 * backup after saying so. [onBackUp] and [onRestore] open the system's file pickers; the app does the rest.
 */
@Composable
fun BackupScreen(status: BackupStatus, onBackUp: () -> Unit, onRestore: () -> Unit, onBack: () -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val working = status == BackupStatus.Working
    LazyColumn(Modifier.fillMaxSize().testTag("backup-content"), contentPadding = contentPadding) {
        item(key = "title") {
            GlassIconButton(SitaraIcons.Back, "Back", onBack, Modifier.padding(start = 12.dp, top = 8.dp))
            LargeTitle("Backup and restore")
            Text(
                "A backup is one file with your liked songs, saved albums and artists, playlists, listening history, each song’s volume and " +
                    "lyrics source, and your settings. Keep it anywhere, then restore it on this phone or a new one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SitaraSpacing.Gutter, end = SitaraSpacing.Gutter, bottom = 20.dp),
            )
        }
        item(key = "back-up") {
            SettingsGroup("Back up") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Downloads, songs on this phone and scrobbling sign-ins stay out: songs on the phone are listed by where they are, not copied.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!working) PillButton("Back up to a file", onBackUp)
                }
            }
        }
        item(key = "restore") {
            SettingsGroup("Restore") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (confirming) {
                        Text(
                            "Replace your library with a backup? Liked songs, playlists, history and settings on this phone become the backup’s. " +
                                "A damaged file changes nothing.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillButton("Choose a backup", { confirming = false; onRestore() })
                            PillButton("Cancel", { confirming = false }, primary = false)
                        }
                    } else {
                        Text("Restoring replaces what is on this phone now.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!working) PillButton("Restore from a file", { confirming = true }, primary = false)
                    }
                }
            }
        }
        if (status != BackupStatus.Idle) item(key = "status") { StatusCard(status) }
    }
}

@Composable
private fun StatusCard(status: BackupStatus) {
    Column(
        Modifier.padding(horizontal = SitaraSpacing.Gutter).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val colors = MaterialTheme.colorScheme
        when (status) {
            BackupStatus.Idle -> Unit
            BackupStatus.Working -> {
                Text("Working on it…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is BackupStatus.BackedUp -> Text(
                "Backed up ${counted(status.likedSongs, "liked song")}, ${counted(status.playlists, "playlist")} and ${counted(status.plays, "play")}.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
            )
            is BackupStatus.Restored -> Text(
                "Restored ${counted(status.likedSongs, "liked song")}, ${counted(status.playlists, "playlist")} and ${counted(status.plays, "play")}, and your settings.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
            )
            is BackupStatus.Failed -> Text(wording(status.failure), style = MaterialTheme.typography.bodyMedium, color = colors.error)
        }
    }
}

private fun counted(count: Int, thing: String) = if (count == 1) "1 $thing" else "%,d ${thing}s".format(count)

private fun wording(failure: BackupFailure): String = when (failure) {
    BackupFailure.NOT_A_BACKUP -> "That file isn’t a Sitara backup. Nothing was changed."
    BackupFailure.DAMAGED -> "That backup is damaged or incomplete, so nothing was changed. Try another copy."
    BackupFailure.NEWER_VERSION -> "That backup was made by a newer Sitara. Update Sitara, then restore it. Nothing was changed."
    BackupFailure.UNREADABLE -> "That file couldn’t be opened. Nothing was changed."
    BackupFailure.UNWRITABLE -> "The backup couldn’t be saved there. Try another place."
}

/** What Backup and restore asks the app to do with the files the system's pickers returned. */
interface BackupActions {
    /** Writes a backup into the file created at [uri]. */
    fun backUp(uri: String)

    /** Checks the backup at [uri] whole, then replaces the library and settings with it. */
    fun restore(uri: String)

    object None : BackupActions {
        override fun backUp(uri: String) = Unit
        override fun restore(uri: String) = Unit
    }
}
