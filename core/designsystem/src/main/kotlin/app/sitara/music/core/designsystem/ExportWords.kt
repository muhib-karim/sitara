package app.sitara.music.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.sitara.music.core.model.DownloadExport
import app.sitara.music.core.model.ExportProblem

/** What an export of downloads has done, as one line; null before the first export. */
@Composable
fun exportSummary(export: DownloadExport): String? = when (export) {
    DownloadExport.Idle -> null
    is DownloadExport.Working -> when {
        export.total == 0 -> stringResource(R.string.ds_export_getting_ready)
        export.done < export.total -> stringResource(R.string.ds_export_progress, export.done + 1, export.total)
        else -> stringResource(R.string.ds_export_finishing)
    }
    is DownloadExport.Done -> listOfNotNull(
        pluralStringResource(R.plurals.ds_export_exported, export.exported, export.exported),
        if (export.withLyrics > 0) stringResource(R.string.ds_export_with_lyrics, export.withLyrics) else null,
        if (export.untagged > 0) stringResource(R.string.ds_export_without_tags, export.untagged) else null,
        if (export.skipped > 0) stringResource(R.string.ds_export_skipped, export.skipped) else null,
    ).joinToString(" · ")
    is DownloadExport.Failed -> when (export.problem) {
        ExportProblem.NOTHING_DOWNLOADED -> stringResource(R.string.ds_export_nothing_downloaded)
        ExportProblem.FOLDER_UNWRITABLE -> stringResource(R.string.ds_export_folder_unwritable)
    }
}
