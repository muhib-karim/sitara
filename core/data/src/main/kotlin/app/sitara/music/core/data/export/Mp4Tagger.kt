package app.sitara.music.core.data.export

import java.io.ByteArrayOutputStream

/**
 * An MP4 (M4A) file with [tags] written as iTunes-style metadata (`moov/udta/meta/ilst`): title, artist, album, plain
 * lyrics and the cover. Items the file already has for other fields are kept. When `moov` grows and sits before the
 * audio, every absolute offset into the file (chunk offsets, fragment base offsets, the fragment index) moves with it.
 */
fun tagMp4(file: ByteArray, tags: SongTags): ByteArray {
    val top = boxes(file, 0, file.size)
    val moov = top.firstOrNull { it.type == "moov" } ?: throw UnsupportedAudioException("No moov box")
    val newMoov = rebuildMoov(file, moov, tags, delta = 0)
    val delta = newMoov.size - moov.size
    val shifted = if (delta == 0) newMoov else rebuildMoov(file, moov, tags, delta)
    val out = ByteArrayOutputStream(file.size + delta)
    top.forEach { box ->
        when {
            box === moov -> out.write(shifted)
            box.start > moov.start && delta != 0 && box.type == "moof" -> out.write(shiftMoof(file, box, delta, moov.start))
            box.start > moov.start && delta != 0 && box.type == "mfra" -> out.write(shiftMfra(file, box, delta, moov.start))
            else -> out.write(file, box.start, box.size)
        }
    }
    return out.toByteArray()
}

internal class Box(val type: String, val start: Int, val size: Int, val headerSize: Int) {
    val dataStart get() = start + headerSize
    val end get() = start + size
}

internal fun boxes(file: ByteArray, from: Int, to: Int): List<Box> {
    val list = mutableListOf<Box>()
    var pos = from
    while (pos + 8 <= to) {
        var size = u32(file, pos)
        var header = 8
        if (size == 1L) {
            size = u64(file, pos + 8)
            header = 16
        } else if (size == 0L) {
            size = (to - pos).toLong()
        }
        if (size < header || pos + size > to) throw UnsupportedAudioException("Bad box at $pos")
        list += Box(String(file, pos + 4, 4, Charsets.ISO_8859_1), pos, size.toInt(), header)
        pos += size.toInt()
    }
    return list
}

private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "dinf", "mvex", "udta")

/** `moov` with the new metadata, and chunk offsets after the old `moov` moved by [delta]. */
private fun rebuildMoov(file: ByteArray, moov: Box, tags: SongTags, delta: Int): ByteArray {
    val children = boxes(file, moov.dataStart, moov.end)
    val body = ByteArrayOutputStream()
    var wroteUdta = false
    children.forEach { child ->
        when (child.type) {
            "udta" -> {
                body.write(rebuildUdta(file, child, tags))
                wroteUdta = true
            }
            else -> body.write(copyShifted(file, child, delta, moov.start))
        }
    }
    if (!wroteUdta) body.write(box("udta", metaBox(tags, emptyList())))
    return box("moov", body.toByteArray())
}

/** A box copied with `stco`/`co64` entries pointing past [after] moved by [delta]. */
private fun copyShifted(file: ByteArray, box: Box, delta: Int, after: Int): ByteArray {
    if (delta == 0) return file.copyOfRange(box.start, box.end)
    return when (box.type) {
        in CONTAINERS -> box(box.type, ByteArrayOutputStream().apply { boxes(file, box.dataStart, box.end).forEach { write(copyShifted(file, it, delta, after)) } }.toByteArray())
        "stco" -> file.copyOfRange(box.start, box.end).also { bytes ->
            val count = u32(bytes, box.headerSize + 4).toInt()
            for (i in 0 until count) {
                val at = box.headerSize + 8 + 4 * i
                val offset = u32(bytes, at)
                if (offset > after) put32(bytes, at, offset + delta)
            }
        }
        "co64" -> file.copyOfRange(box.start, box.end).also { bytes ->
            val count = u32(bytes, box.headerSize + 4).toInt()
            for (i in 0 until count) {
                val at = box.headerSize + 8 + 8 * i
                val offset = u64(bytes, at)
                if (offset > after) put64(bytes, at, offset + delta)
            }
        }
        else -> file.copyOfRange(box.start, box.end)
    }
}

private fun rebuildUdta(file: ByteArray, udta: Box, tags: SongTags): ByteArray {
    val body = ByteArrayOutputStream()
    var kept = emptyList<Pair<String, ByteArray>>()
    boxes(file, udta.dataStart, udta.end).forEach { child ->
        if (child.type == "meta") {
            // meta is a full box: four bytes of version and flags before its children
            val ilst = boxes(file, child.dataStart + 4, child.end).firstOrNull { it.type == "ilst" }
            kept = ilst?.let { list -> boxes(file, list.dataStart, list.end).map { it.type to file.copyOfRange(it.start, it.end) } }.orEmpty()
        } else {
            body.write(file, child.start, child.size)
        }
    }
    body.write(metaBox(tags, kept))
    return box("udta", body.toByteArray())
}

private const val NAME = "©nam"
private const val ARTIST = "©ART"
private const val ALBUM = "©alb"
private const val LYRICS = "©lyr"
private const val COVER = "covr"
private val WRITTEN = setOf(NAME, ARTIST, ALBUM, LYRICS, COVER)

private fun metaBox(tags: SongTags, kept: List<Pair<String, ByteArray>>): ByteArray {
    val items = ByteArrayOutputStream()
    kept.filter { it.first !in WRITTEN }.forEach { items.write(it.second) }
    fun text(type: String, value: String?) {
        if (!value.isNullOrBlank()) items.write(box(type, dataBox(UTF8, value.toByteArray(Charsets.UTF_8))))
    }
    text(NAME, tags.title)
    text(ARTIST, tags.artist)
    text(ALBUM, tags.album)
    text(LYRICS, tags.lyrics)
    tags.cover?.let { items.write(box(COVER, dataBox(if (tags.coverMime == "image/png") PNG else JPEG, it))) }
    val handler = ByteArrayOutputStream().apply {
        write(ByteArray(4)) // version and flags
        write(ByteArray(4)) // pre-defined
        write("mdir".toByteArray(Charsets.ISO_8859_1))
        write("appl".toByteArray(Charsets.ISO_8859_1))
        write(ByteArray(9)) // reserved, then an empty name
    }.toByteArray()
    val body = ByteArrayOutputStream().apply {
        write(ByteArray(4)) // version and flags
        write(box("hdlr", handler))
        write(box("ilst", items.toByteArray()))
    }
    return box("meta", body.toByteArray())
}

private const val UTF8 = 1
private const val JPEG = 13
private const val PNG = 14

private fun dataBox(type: Int, payload: ByteArray): ByteArray = box("data", ByteArray(8).also { put32(it, 0, type.toLong()) } + payload)

/** A `moof` whose `tfhd` base data offset (when the file states one) moves by [delta]. */
private fun shiftMoof(file: ByteArray, moof: Box, delta: Int, after: Int): ByteArray {
    val bytes = file.copyOfRange(moof.start, moof.end)
    boxes(file, moof.dataStart, moof.end).filter { it.type == "traf" }.forEach { traf ->
        boxes(file, traf.dataStart, traf.end).filter { it.type == "tfhd" }.forEach { tfhd ->
            val flags = u32(file, tfhd.dataStart) and 0xFFFFFF
            if (flags and 1L != 0L) {
                val at = tfhd.dataStart + 8 - moof.start
                val offset = u64(bytes, at)
                if (offset > after) put64(bytes, at, offset + delta)
            }
        }
    }
    return bytes
}

/** An `mfra` whose `tfra` entries (absolute `moof` offsets) move by [delta]. */
private fun shiftMfra(file: ByteArray, mfra: Box, delta: Int, after: Int): ByteArray {
    val bytes = file.copyOfRange(mfra.start, mfra.end)
    boxes(file, mfra.dataStart, mfra.end).filter { it.type == "tfra" }.forEach { tfra ->
        val base = tfra.dataStart - mfra.start
        val version = bytes[base].toInt()
        val sizes = u32(bytes, base + 8).toInt()
        val entrySize = (if (version == 1) 16 else 8) + ((sizes shr 4) and 3) + 1 + ((sizes shr 2) and 3) + 1 + (sizes and 3) + 1
        val count = u32(bytes, base + 12).toInt()
        for (i in 0 until count) {
            val at = base + 16 + i * entrySize + (if (version == 1) 8 else 4)
            if (version == 1) {
                val offset = u64(bytes, at)
                if (offset > after) put64(bytes, at, offset + delta)
            } else {
                val offset = u32(bytes, at)
                if (offset > after) put32(bytes, at, offset + delta)
            }
        }
    }
    return bytes
}

internal fun box(type: String, body: ByteArray): ByteArray = ByteArray(8).also {
    put32(it, 0, (body.size + 8).toLong())
    type.toByteArray(Charsets.ISO_8859_1).copyInto(it, 4)
} + body

internal fun u32(b: ByteArray, at: Int): Long =
    ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

internal fun u64(b: ByteArray, at: Int): Long = (u32(b, at) shl 32) or u32(b, at + 4)

private fun put32(b: ByteArray, at: Int, value: Long) {
    for (i in 0..3) b[at + i] = (value ushr (24 - 8 * i)).toByte()
}

private fun put64(b: ByteArray, at: Int, value: Long) {
    put32(b, at, value ushr 32)
    put32(b, at + 4, value and 0xFFFFFFFFL)
}
