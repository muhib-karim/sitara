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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
            GlassIconButton(SitaraIcons.Back, stringResource(R.string.settings_back), onBack, Modifier.padding(start = 12.dp, top = 8.dp))
            LargeTitle(stringResource(R.string.settings_backup_and_restore))
            Text(
                stringResource(R.string.settings_backup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SitaraSpacing.Gutter, end = SitaraSpacing.Gutter, bottom = 20.dp),
            )
        }
        item(key = "back-up") {
            SettingsGroup(stringResource(R.string.settings_back_up)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.settings_back_up_description),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!working) PillButton(stringResource(R.string.settings_back_up_button), onBackUp)
                }
            }
        }
        item(key = "restore") {
            SettingsGroup(stringResource(R.string.settings_restore)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (confirming) {
                        Text(
                            stringResource(R.string.settings_restore_confirm),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillButton(stringResource(R.string.settings_choose_backup), { confirming = false; onRestore() })
                            PillButton(stringResource(R.string.settings_cancel), { confirming = false }, primary = false)
                        }
                    } else {
                        Text(stringResource(R.string.settings_restore_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!working) PillButton(stringResource(R.string.settings_restore_button), { confirming = true }, primary = false)
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
                Text(stringResource(R.string.settings_working), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is BackupStatus.BackedUp -> Text(
                stringResource(
                    R.string.settings_backed_up,
                    counted(R.plurals.settings_backup_liked_songs, status.likedSongs),
                    counted(R.plurals.settings_backup_playlists, status.playlists),
                    counted(R.plurals.settings_backup_plays, status.plays),
                ),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
            )
            is BackupStatus.Restored -> Text(
                stringResource(
                    R.string.settings_restored,
                    counted(R.plurals.settings_backup_liked_songs, status.likedSongs),
                    counted(R.plurals.settings_backup_playlists, status.playlists),
                    counted(R.plurals.settings_backup_plays, status.plays),
                ),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
            )
            is BackupStatus.Failed -> Text(wording(status.failure), style = MaterialTheme.typography.bodyMedium, color = colors.error)
        }
    }
}

/** [count] with digit grouping ("1,500 plays"), worded by the [plural] resource. */
@Composable
private fun counted(plural: Int, count: Int) = pluralStringResource(plural, count, "%,d".format(count))

@Composable
private fun wording(failure: BackupFailure): String = stringResource(
    when (failure) {
        BackupFailure.NOT_A_BACKUP -> R.string.settings_backup_not_a_backup
        BackupFailure.DAMAGED -> R.string.settings_backup_damaged
        BackupFailure.NEWER_VERSION -> R.string.settings_backup_newer_version
        BackupFailure.UNREADABLE -> R.string.settings_backup_unreadable
        BackupFailure.UNWRITABLE -> R.string.settings_backup_unwritable
    },
)

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
