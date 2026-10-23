package app.sitara.music.core.data.export

/** The audio of a WebM (Matroska) file: its first audio track's codec, codec private data and frames in order. */
internal class WebmAudio(
    val codecId: String,
    val codecPrivate: ByteArray?,
    val frames: List<ByteArray>,
    /** Samples (at 48 kHz) to drop from the end of the last frame, from a block's DiscardPadding. */
    val discardSamples: Long,
)

/**
 * A reader for the parts of WebM a download needs: the tracks and every block of the first audio track, with lacing.
 * Elements of unknown size (as live streams write clusters) are read up to the next element of their parent.
 */
internal object WebmReader {
    private const val EBML = 0x1A45DFA3L
    private const val SEGMENT = 0x18538067L
    private const val TRACKS = 0x1654AE6BL
    private const val TRACK_ENTRY = 0xAEL
    private const val TRACK_NUMBER = 0xD7L
    private const val TRACK_TYPE = 0x83L
    private const val CODEC_ID = 0x86L
    private const val CODEC_PRIVATE = 0x63A2L
    private const val CLUSTER = 0x1F43B675L
    private const val SIMPLE_BLOCK = 0xA3L
    private const val BLOCK_GROUP = 0xA0L
    private const val BLOCK = 0xA1L
    private const val DISCARD_PADDING = 0x75A2L
    private const val AUDIO_TRACK = 2L

    /** Top-level children of a Segment; an unknown-size cluster ends where one of these starts. */
    private val SEGMENT_CHILDREN = setOf(0x114D9B74L, 0x1549A966L, TRACKS, CLUSTER, 0x1C53BB6BL, 0x1941A469L, 0x1043A770L, 0x1254C367L)

    private class Element(val id: Long, val dataStart: Int, val dataEnd: Int)

    fun read(bytes: ByteArray): WebmAudio {
        var pos = 0
        val header = element(bytes, pos, bytes.size) ?: throw UnsupportedAudioException("Empty file")
        if (header.id != EBML) throw UnsupportedAudioException("Not a WebM file")
        pos = header.dataEnd
        while (pos < bytes.size) {
            val e = element(bytes, pos, bytes.size) ?: break
            if (e.id == SEGMENT) return segment(bytes, e)
            pos = e.dataEnd
        }
        throw UnsupportedAudioException("No segment")
    }

    private fun segment(bytes: ByteArray, segment: Element): WebmAudio {
        var track: Long? = null
        var codecId: String? = null
        var codecPrivate: ByteArray? = null
        val frames = mutableListOf<ByteArray>()
        var discardNs = 0L
        fun block(start: Int, end: Int) {
            if (track == null) return
            val number = vint(bytes, start) ?: return
            if (number.first != track) return
            var at = start + number.second + 3 // timecode (2 bytes) and flags
            if (at > end) return
            val flags = bytes[at - 1].toInt() and 0xFF
            frames += laced(bytes, at, end, (flags shr 1) and 3)
        }
        var pos = segment.dataStart
        while (pos < segment.dataEnd) {
            val e = element(bytes, pos, segment.dataEnd) ?: break
            when (e.id) {
                TRACKS -> if (track == null) {
                    children(bytes, e).filter { it.id == TRACK_ENTRY }.forEach { entry ->
                        if (track != null) return@forEach
                        val fields = children(bytes, entry)
                        val type = fields.firstOrNull { it.id == TRACK_TYPE }?.let { uint(bytes, it) }
                        if (type == AUDIO_TRACK) {
                            track = fields.firstOrNull { it.id == TRACK_NUMBER }?.let { uint(bytes, it) }
                            codecId = fields.firstOrNull { it.id == CODEC_ID }?.let { String(bytes, it.dataStart, it.dataEnd - it.dataStart, Charsets.US_ASCII).trimEnd('\u0000') }
                            codecPrivate = fields.firstOrNull { it.id == CODEC_PRIVATE }?.let { bytes.copyOfRange(it.dataStart, it.dataEnd) }
                        }
                    }
                }
                CLUSTER -> children(bytes, e).forEach { child ->
                    when (child.id) {
                        SIMPLE_BLOCK -> block(child.dataStart, child.dataEnd)
                        BLOCK_GROUP -> children(bytes, child).forEach { part ->
                            if (part.id == BLOCK) block(part.dataStart, part.dataEnd)
                            if (part.id == DISCARD_PADDING) discardNs = sint(bytes, part)
                        }
                    }
                }
            }
            pos = e.dataEnd
        }
        val codec = codecId ?: throw UnsupportedAudioException("No audio track")
        return WebmAudio(codec, codecPrivate, frames, discardSamples = (discardNs.coerceAtLeast(0) * 48_000L) / 1_000_000_000L)
    }

    private fun laced(bytes: ByteArray, start: Int, end: Int, lacing: Int): List<ByteArray> {
        if (lacing == 0) return listOf(bytes.copyOfRange(start, end))
        var at = start
        val count = (bytes[at++].toInt() and 0xFF) + 1
        val sizes = LongArray(count)
        when (lacing) {
            1 -> for (i in 0 until count - 1) { // Xiph: runs of 255 then the rest
                var size = 0L
                do {
                    val b = bytes[at++].toInt() and 0xFF
                    size += b
                } while (b == 255)
                sizes[i] = size
            }
            3 -> { // EBML: the first size, then signed differences
                val first = vint(bytes, at) ?: throw UnsupportedAudioException("Bad lacing")
                sizes[0] = first.first
                at += first.second
                for (i in 1 until count - 1) {
                    val diff = vint(bytes, at) ?: throw UnsupportedAudioException("Bad lacing")
                    val bias = (1L shl (7 * diff.second - 1)) - 1
                    sizes[i] = sizes[i - 1] + diff.first - bias
                    at += diff.second
                }
            }
            2 -> { // fixed: equal parts
                val each = (end - at) / count
                for (i in 0 until count - 1) sizes[i] = each.toLong()
            }
        }
        sizes[count - 1] = (end - at) - sizes.take(count - 1).sum()
        return sizes.map { size ->
            if (size < 0 || at + size > end) throw UnsupportedAudioException("Bad lacing")
            bytes.copyOfRange(at, (at + size).toInt()).also { at += size.toInt() }
        }
    }

    private fun children(bytes: ByteArray, parent: Element): List<Element> {
        val list = mutableListOf<Element>()
        var pos = parent.dataStart
        while (pos < parent.dataEnd) {
            val e = element(bytes, pos, parent.dataEnd) ?: break
            list += e
            pos = e.dataEnd
        }
        return list
    }

    /** The element at [pos], or null at [limit] or where the bytes stop making sense. */
    private fun element(bytes: ByteArray, pos: Int, limit: Int): Element? {
        if (pos >= limit) return null
        val id = idAt(bytes, pos) ?: return null
        val sizeAt = pos + id.second
        val size = vint(bytes, sizeAt) ?: return null
        val dataStart = sizeAt + size.second
        val unknown = size.first == (1L shl (7 * size.second)) - 1
        val dataEnd = if (unknown) unknownEnd(bytes, dataStart, limit, id.first) else (dataStart + size.first).coerceAtMost(limit.toLong()).toInt()
        return Element(id.first, dataStart, dataEnd)
    }

    /** Where an element of unknown size ends: at the next element that is not its child, or at [limit]. */
    private fun unknownEnd(bytes: ByteArray, start: Int, limit: Int, id: Long): Int {
        val stop = if (id == SEGMENT) emptySet() else SEGMENT_CHILDREN
        var pos = start
        while (pos < limit) {
            val child = idAt(bytes, pos) ?: return limit
            if (child.first in stop) return pos
            val size = vint(bytes, pos + child.second) ?: return limit
            val unknown = size.first == (1L shl (7 * size.second)) - 1
            if (unknown) return limit
            pos = (pos + child.second + size.second + size.first).coerceAtMost(limit.toLong()).toInt()
        }
        return limit
    }

    /** An element id with its length marker kept, and its length in bytes. */
    private fun idAt(bytes: ByteArray, pos: Int): Pair<Long, Int>? {
        if (pos >= bytes.size) return null
        val first = bytes[pos].toInt() and 0xFF
        val length = Integer.numberOfLeadingZeros(first) - 23
        if (first == 0 || length > 4 || pos + length > bytes.size) return null
        var value = 0L
        for (i in 0 until length) value = (value shl 8) or (bytes[pos + i].toLong() and 0xFF)
        return value to length
    }

    /** A variable-length integer without its marker, and its length in bytes. */
    private fun vint(bytes: ByteArray, pos: Int): Pair<Long, Int>? {
        if (pos >= bytes.size) return null
        val first = bytes[pos].toInt() and 0xFF
        val length = Integer.numberOfLeadingZeros(first) - 23
        if (first == 0 || length > 8 || pos + length > bytes.size) return null
        var value = (first and (0xFF shr length)).toLong()
        for (i in 1 until length) value = (value shl 8) or (bytes[pos + i].toLong() and 0xFF)
        return value to length
    }

    private fun uint(bytes: ByteArray, e: Element): Long {
        var value = 0L
        for (i in e.dataStart until e.dataEnd) value = (value shl 8) or (bytes[i].toLong() and 0xFF)
        return value
    }

    private fun sint(bytes: ByteArray, e: Element): Long {
        val length = e.dataEnd - e.dataStart
        if (length == 0) return 0
        val value = uint(bytes, e)
        val shift = 64 - 8 * length
        return (value shl shift) shr shift
    }
}
