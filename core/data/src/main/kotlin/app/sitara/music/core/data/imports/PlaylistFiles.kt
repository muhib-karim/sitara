package app.sitara.music.core.data.imports

import app.sitara.music.core.model.SourceSong
import java.net.URI

/** The songs of an M3U or CSV playlist file: by its extension, else M3U when its first line is an M3U tag, else CSV. */
fun parsePlaylistFile(fileName: String, text: String): List<SourceSong> = when (fileName.substringAfterLast('.', "").lowercase()) {
    "m3u", "m3u8" -> parseM3u(text)
    "csv", "tsv", "txt" -> parseCsv(text)
    else -> if (lines(text).firstOrNull { it.isNotBlank() }?.trim()?.startsWith("#EXT") == true) parseM3u(text) else parseCsv(text)
}

/**
 * The songs of an M3U or M3U8 playlist. An `#EXTINF:<seconds>,Artist - Title` line names the entry after it; without one,
 * a file is named after its file name. Web addresses are kept as links; other `#` lines are comments.
 */
fun parseM3u(text: String): List<SourceSong> {
    val songs = mutableListOf<SourceSong>()
    var info: String? = null
    for (raw in lines(text)) {
        val line = raw.trim()
        when {
            line.isEmpty() -> Unit
            line.startsWith("#EXTINF:", ignoreCase = true) -> info = line.substring("#EXTINF:".length)
            line.startsWith("#") -> Unit
            else -> {
                val web = line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true)
                val seconds = info?.substringBefore(',')?.trim()?.substringBefore(' ')?.toLongOrNull()?.takeIf { it > 0 }
                val display = info?.substringAfter(',', "")?.trim()?.takeIf { it.isNotEmpty() } ?: if (web) "" else fileStem(line)
                val (artist, title) = splitArtistTitle(display)
                songs += SourceSong(title = title, artist = artist, durationMs = seconds?.times(1_000), link = line.takeIf { web })
                info = null
            }
        }
    }
    return songs
}

/**
 * The songs of a CSV file (comma, semicolon or tab separated, fields quoted with `"`). With a header row, the title,
 * artist, album, duration and link columns are found by name; without one, the columns are title, artist and album.
 */
fun parseCsv(text: String): List<SourceSong> {
    val clean = text.removePrefix(BOM)
    val records = csvRecords(clean, delimiterOf(lines(clean).firstOrNull().orEmpty())).filter { record -> record.any { it.isNotBlank() } }
    val header = records.firstOrNull()?.map(::headerKey)
    val columns = if (header != null && header.any { it in TITLE_HEADERS }) Columns.of(header) else null
    return records.drop(if (columns != null) 1 else 0).mapNotNull { record -> (columns ?: Columns.POSITIONAL).song(record) }
}

private const val BOM = "﻿"

private fun lines(text: String): List<String> = text.removePrefix(BOM).split("\r\n", "\n", "\r")

/** "Artist - Title" split at the first spaced dash; anything else is all title. */
private fun splitArtistTitle(display: String): Pair<String, String> {
    val at = display.indexOf(" - ")
    return if (at < 0) "" to display else display.substring(0, at).trim() to display.substring(at + 3).trim()
}

/** A path's file name without its extension, with underscores read as spaces; `file:` URLs are decoded first. */
private fun fileStem(location: String): String {
    val path = if (location.startsWith("file:", ignoreCase = true)) runCatching { URI(location).path }.getOrNull() ?: location else location
    val name = path.substringAfterLast('/').substringAfterLast('\\')
    val stem = if (name.substringAfterLast('.', "").length in 1..4) name.substringBeforeLast('.') else name
    return stem.replace('_', ' ').trim()
}

/** The delimiter used most outside quotes on the first line; a comma when there is none. */
private fun delimiterOf(firstLine: String): Char {
    var quoted = false
    val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
    for (c in firstLine) {
        when {
            c == '"' -> quoted = !quoted
            !quoted && c in counts -> counts[c] = counts.getValue(c) + 1
        }
    }
    return counts.entries.filter { it.value > 0 }.maxByOrNull { it.value }?.key ?: ','
}

/** RFC 4180 records: quoted fields may hold the delimiter, line breaks and doubled quotes. */
private fun csvRecords(text: String, delimiter: Char): List<List<String>> {
    val records = mutableListOf<List<String>>()
    var record = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> field.append('"').also { i++ }
            c == '"' && quoted -> quoted = false
            c == '"' && field.isBlank() -> {
                field.clear() // spaces before an opening quote are not part of the field
                quoted = true
            }
            quoted -> field.append(c)
            c == delimiter -> record += field.toString().also { field.clear() }
            c == '\r' || c == '\n' -> {
                if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                record += field.toString().also { field.clear() }
                records += record
                record = mutableListOf()
            }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || record.isNotEmpty()) records += record + field.toString()
    return records
}

private fun headerKey(cell: String): String = cell.lowercase().filter { it.isLetter() }

private val TITLE_HEADERS = setOf("title", "track", "trackname", "tracktitle", "song", "songname", "songtitle", "name")
private val ARTIST_HEADERS = setOf("artist", "artists", "artistname", "artistnames", "performer")
private val ALBUM_HEADERS = setOf("album", "albumname", "albumtitle")
private val DURATION_MS_HEADERS = setOf("durationms", "lengthms")
private val DURATION_HEADERS = setOf("duration", "length", "time")
private val LINK_HEADERS = setOf("url", "link", "youtube", "youtubeurl", "youtubelink", "videourl")

/** Column indexes of a CSV's fields; -1 for a field the file does not have. */
private data class Columns(val title: Int, val artist: Int, val album: Int, val durationMs: Int, val duration: Int, val link: Int) {
    fun song(record: List<String>): SourceSong? {
        fun cell(index: Int) = record.getOrNull(index)?.trim().orEmpty()
        val title = cell(title)
        val link = cell(link).takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        if (title.isEmpty() && link == null) return null
        val durationMs = cell(durationMs).toLongOrNull()?.takeIf { it > 0 } ?: clockDurationMs(cell(duration))
        return SourceSong(title = title, artist = cell(artist), album = cell(album), durationMs = durationMs, link = link)
    }

    companion object {
        val POSITIONAL = Columns(title = 0, artist = 1, album = 2, durationMs = -1, duration = -1, link = -1)

        fun of(header: List<String>) = Columns(
            title = header.indexOfFirst { it in TITLE_HEADERS },
            artist = header.indexOfFirst { it in ARTIST_HEADERS },
            album = header.indexOfFirst { it in ALBUM_HEADERS },
            durationMs = header.indexOfFirst { it in DURATION_MS_HEADERS },
            duration = header.indexOfFirst { it in DURATION_HEADERS },
            link = header.indexOfFirst { it in LINK_HEADERS },
        )
    }
}

/** "95", "3:25" or "1:02:03" as milliseconds; null for anything else. */
private fun clockDurationMs(text: String): Long? {
    val parts = text.split(':').map { it.toLongOrNull() ?: return null }
    if (parts.size !in 1..3) return null
    return parts.fold(0L) { total, part -> total * 60 + part }.takeIf { it > 0 }?.times(1_000)
}
