package app.sitara.music.core.model

/** Where a song's download stands. */
enum class DownloadState {
    WAITING,
    WAITING_FOR_WIFI,
    WAITING_FOR_NETWORK,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

/** A song kept, or on its way to being kept, on the device for offline play. [percent] is null until the size is known. */
data class SongDownload(
    val track: Track,
    val state: DownloadState,
    val percent: Int?,
    val bytes: Long,
)

/** The songs in this list that are neither downloaded nor on their way (a failed one is tried again), each once; songs on the phone are already there. */
fun List<Track>.toDownload(states: Map<String, DownloadState>): List<Track> =
    filter { !it.isLocal && states[it.id].let { state -> state == null || state == DownloadState.FAILED } }.distinctBy { it.id }

/** Downloads copied out as tagged files to a folder the listener picked. */
sealed interface DownloadExport {
    data object Idle : DownloadExport

    data class Working(val done: Int, val total: Int) : DownloadExport

    /**
     * [exported] files written, [withLyrics] of them with an .lrc beside them, [untagged] copied as they were (a format
     * the exporter cannot tag), [skipped] not exported because their download is not whole.
     */
    data class Done(val exported: Int, val withLyrics: Int, val untagged: Int, val skipped: Int) : DownloadExport

    data class Failed(val problem: ExportProblem) : DownloadExport
}

enum class ExportProblem { NOTHING_DOWNLOADED, FOLDER_UNWRITABLE }
