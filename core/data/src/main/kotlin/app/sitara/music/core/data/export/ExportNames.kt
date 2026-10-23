package app.sitara.music.core.data.export

/**
 * "Artist - Title.ext" made safe for any folder: characters file systems refuse become spaces, runs of spaces and
 * dots at the ends go, and the name is cut to [MAX_NAME] characters before the extension.
 */
fun exportFileName(artist: String, title: String, extension: String): String {
    val stem = listOf(artist, title).map(::clean).filter { it.isNotEmpty() }.joinToString(" - ").ifEmpty { "Untitled" }
    val cut = if (stem.length > MAX_NAME) stem.take(MAX_NAME).trimEnd(' ', '.', '-') else stem
    return "$cut.$extension"
}

/** The same name with another extension, for the .lrc beside a song. */
fun sidecarName(fileName: String, extension: String): String = fileName.substringBeforeLast('.') + "." + extension

private fun clean(part: String): String = part
    .map { c -> if (c in RESERVED || c.code < 32) ' ' else c }
    .joinToString("")
    .replace(Regex("\\s+"), " ")
    .trim(' ', '.')

private const val RESERVED = "\\/:*?\"<>|"
private const val MAX_NAME = 120

/**
 * An .lrc file for synced lyrics: `[ti:]`, `[ar:]`, `[al:]` and `[length:]` headers added unless the lyrics carry
 * their own, then the lines as they were stored.
 */
fun lrcFile(tags: SongTags, synced: String): String {
    val lines = synced.lines()
    val present = lines.mapNotNull { HEADER.matchEntire(it.trim())?.groupValues?.get(1)?.lowercase() }.toSet()
    val headers = buildList {
        if ("ti" !in present && tags.title.isNotBlank()) add("[ti:${tags.title}]")
        if ("ar" !in present && tags.artist.isNotBlank()) add("[ar:${tags.artist}]")
        if ("al" !in present && tags.album.isNotBlank()) add("[al:${tags.album}]")
        if ("length" !in present && tags.durationMs > 0) {
            val seconds = tags.durationMs / 1000
            add("[length:%02d:%02d]".format(seconds / 60, seconds % 60))
        }
    }
    return (headers + lines.dropLastWhile { it.isBlank() }).joinToString("\n", postfix = "\n")
}

private val HEADER = Regex("\\[([A-Za-z]+):.*]")
