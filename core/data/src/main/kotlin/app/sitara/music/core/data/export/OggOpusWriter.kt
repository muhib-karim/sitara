package app.sitara.music.core.data.export

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Base64

/**
 * Rewrites a WebM file's Opus audio as an Ogg Opus file (RFC 7845) with [tags] as its comments, the cover as a
 * `METADATA_BLOCK_PICTURE`. The frames are copied as they are, so nothing is decoded or re-encoded.
 */
fun webmOpusToOgg(webm: ByteArray, output: OutputStream, tags: SongTags) {
    val audio = WebmReader.read(webm)
    if (audio.codecId != "A_OPUS") throw UnsupportedAudioException("Not Opus: ${audio.codecId}")
    val head = audio.codecPrivate?.takeIf { it.size >= 19 && String(it, 0, 8, Charsets.US_ASCII) == "OpusHead" }
        ?: throw UnsupportedAudioException("No OpusHead")
    OggStream(output, serial = SERIAL).apply {
        page(listOf(head), granule = 0, first = true)
        page(listOf(opusTags(tags)), granule = 0)
        var granule = 0L
        val pending = mutableListOf<ByteArray>()
        var pendingBytes = 0
        audio.frames.forEachIndexed { index, frame ->
            granule += opusSamples(frame)
            pending += frame
            pendingBytes += frame.size
            val last = index == audio.frames.lastIndex
            if (last || pendingBytes >= PAGE_TARGET_BYTES || pending.size >= PAGE_MAX_PACKETS) {
                val end = if (last) (granule - audio.discardSamples).coerceAtLeast(0) else granule
                page(pending.toList(), end, last = last)
                pending.clear()
                pendingBytes = 0
            }
        }
        if (audio.frames.isEmpty()) page(emptyList(), 0, last = true)
    }
}

/** The OpusTags header packet: Vorbis comments for the song, plain lyrics and the cover. */
internal fun opusTags(tags: SongTags): ByteArray {
    val comments = buildList {
        if (tags.title.isNotBlank()) add("TITLE=${tags.title}")
        if (tags.artist.isNotBlank()) add("ARTIST=${tags.artist}")
        if (tags.album.isNotBlank()) add("ALBUM=${tags.album}")
        tags.lyrics?.takeIf { it.isNotBlank() }?.let { add("LYRICS=$it") }
        tags.cover?.let { add("METADATA_BLOCK_PICTURE=" + Base64.getEncoder().encodeToString(flacPicture(it, tags.coverMime ?: "image/jpeg"))) }
    }
    val out = ByteArrayOutputStream()
    out.write("OpusTags".toByteArray(Charsets.US_ASCII))
    val vendor = VENDOR.toByteArray(Charsets.UTF_8)
    out.le32(vendor.size)
    out.write(vendor)
    out.le32(comments.size)
    comments.forEach { comment ->
        val bytes = comment.toByteArray(Charsets.UTF_8)
        out.le32(bytes.size)
        out.write(bytes)
    }
    return out.toByteArray()
}

/** A FLAC picture block (front cover), the form Vorbis comments carry pictures in. */
internal fun flacPicture(picture: ByteArray, mime: String): ByteArray {
    val out = ByteArrayOutputStream()
    val (width, height) = pictureSize(picture)
    val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
    out.be32(FRONT_COVER)
    out.be32(mimeBytes.size)
    out.write(mimeBytes)
    out.be32(0) // no description
    out.be32(width)
    out.be32(height)
    out.be32(24) // colour depth
    out.be32(0) // not indexed
    out.be32(picture.size)
    out.write(picture)
    return out.toByteArray()
}

/** Samples at 48 kHz in an Opus packet, from its table-of-contents byte (RFC 6716, 3.1). */
internal fun opusSamples(packet: ByteArray): Int {
    if (packet.isEmpty()) return 0
    val toc = packet[0].toInt() and 0xFF
    val config = toc shr 3
    val frameSamples = when {
        config < 12 -> intArrayOf(480, 960, 1920, 2880)[config % 4] // SILK: 10, 20, 40, 60 ms
        config < 16 -> intArrayOf(480, 960)[config % 2] // hybrid: 10, 20 ms
        else -> intArrayOf(120, 240, 480, 960)[config % 4] // CELT: 2.5, 5, 10, 20 ms
    }
    val frames = when (toc and 3) {
        0 -> 1
        3 -> if (packet.size > 1) packet[1].toInt() and 0x3F else 0
        else -> 2
    }
    return frameSamples * frames
}

/** Writes Ogg pages (RFC 3533) of one logical stream. */
internal class OggStream(private val output: OutputStream, private val serial: Int) {
    private var sequence = 0

    /** One page holding whole [packets]; a packet too long for one page continues on the pages after it. */
    fun page(packets: List<ByteArray>, granule: Long, first: Boolean = false, last: Boolean = false) {
        val lacing = ByteArrayOutputStream()
        val body = ByteArrayOutputStream()
        var segments = 0
        var startsContinued = false
        fun flush(granuleOfPage: Long, isLast: Boolean) {
            val flags = (if (startsContinued) 1 else 0) or (if (first && sequence == 0) 2 else 0) or (if (isLast) 4 else 0)
            writePage(flags, granuleOfPage, lacing.toByteArray(), body.toByteArray())
            lacing.reset()
            body.reset()
            segments = 0
            startsContinued = false
        }
        packets.forEach { packet ->
            var offset = 0
            while (true) {
                val chunk = minOf(255, packet.size - offset)
                if (segments == 255) {
                    flush(-1, isLast = false) // the packet goes on past this page: no granule ends here
                    startsContinued = true
                }
                lacing.write(chunk)
                body.write(packet, offset, chunk)
                segments++
                offset += chunk
                if (chunk < 255) break
            }
        }
        flush(granule, last)
    }

    private fun writePage(flags: Int, granule: Long, lacing: ByteArray, body: ByteArray) {
        val page = ByteArrayOutputStream()
        page.write("OggS".toByteArray(Charsets.US_ASCII))
        page.write(0) // version
        page.write(flags)
        page.le64(granule)
        page.le32(serial)
        page.le32(sequence++)
        page.le32(0) // checksum, filled in below
        page.write(lacing.size)
        page.write(lacing)
        page.write(body)
        val bytes = page.toByteArray()
        val crc = oggCrc(bytes)
        bytes[22] = crc.toByte()
        bytes[23] = (crc ushr 8).toByte()
        bytes[24] = (crc ushr 16).toByte()
        bytes[25] = (crc ushr 24).toByte()
        output.write(bytes)
    }
}

/** Ogg's CRC-32: polynomial 0x04C11DB7, not reflected, starting from 0. */
internal fun oggCrc(bytes: ByteArray): Int {
    var crc = 0
    for (b in bytes) crc = (crc shl 8) xor CRC_TABLE[((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF]
    return crc
}

private val CRC_TABLE = IntArray(256) { i ->
    var r = i shl 24
    repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
    r
}

private fun OutputStream.le32(value: Int) {
    write(value)
    write(value ushr 8)
    write(value ushr 16)
    write(value ushr 24)
}

private fun OutputStream.le64(value: Long) {
    le32(value.toInt())
    le32((value ushr 32).toInt())
}

private fun OutputStream.be32(value: Int) {
    write(value ushr 24)
    write(value ushr 16)
    write(value ushr 8)
    write(value)
}

private const val SERIAL = 0x53495441 // "SITA"
private const val VENDOR = "Sitara"
private const val FRONT_COVER = 3
private const val PAGE_TARGET_BYTES = 4_000
private const val PAGE_MAX_PACKETS = 50
