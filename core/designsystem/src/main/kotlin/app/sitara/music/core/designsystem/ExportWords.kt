package app.sitara.music.core.designsystem

import app.sitara.music.core.model.DownloadExport
import app.sitara.music.core.model.ExportProblem

/** What an export of downloads has done, as one line; null before the first export. */
fun exportSummary(export: DownloadExport): String? = when (export) {
    DownloadExport.Idle -> null
    is DownloadExport.Working -> if (export.total == 0) "Getting ready to export" else "Exporting ${export.done + 1} of ${export.total}".takeIf { export.done < export.total } ?: "Finishing the export"
    is DownloadExport.Done -> buildList {
        add("Exported ${songs(export.exported)}")
        if (export.withLyrics > 0) add("${export.withLyrics} with lyrics")
        if (export.untagged > 0) add("${export.untagged} without tags")
        if (export.skipped > 0) add("${export.skipped} skipped, not fully downloaded")
    }.joinToString(" · ")
    is DownloadExport.Failed -> when (export.problem) {
        ExportProblem.NOTHING_DOWNLOADED -> "Nothing downloaded to export yet"
        ExportProblem.FOLDER_UNWRITABLE -> "Couldn't write to that folder. Pick another and try again."
    }
}

private fun songs(count: Int) = if (count == 1) "1 song" else "$count songs"
