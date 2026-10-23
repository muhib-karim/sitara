package app.sitara.music.core.data.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatsTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/export/$name")).readBytes()

    // A real 1 × 1 PNG header is enough for the picture block's size fields.
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10) +
        byteArrayOf(0, 0, 0, 13) + "IHDR".toByteArray() + byteArrayOf(0, 0, 0, 2, 0, 0, 0, 3) + ByteArray(40) { 7 }

    private val tags = SongTags(
        title = "Saffron Morning",
        artist = "Sitara Ensemble",
        album = "Orbit — রাগ",
        durationMs = 2_000,
        cover = png,
        coverMime = "image/png",
        lyrics = "First line\nSecond line",
    )

    /** Keeps each output under build/ so it can be checked with ffprobe by hand. */
    private fun keep(name: String, bytes: ByteArray) {
        File("build/tmp/export-check").apply { mkdirs() }.resolve(name).writeBytes(bytes)
    }

    @Test
    fun `containers are told apart by their first bytes`() {
        assertEquals(AudioContainer.WEBM, sniffContainer(fixture("tone.webm").copyOf(16)))
        assertEquals(AudioContainer.MP4, sniffContainer(fixture("tone.m4a").copyOf(16)))
        assertEquals(AudioContainer.MP4, sniffContainer(fixture("tone_frag.m4a").copyOf(16)))
        assertEquals(AudioContainer.UNKNOWN, sniffContainer("ID3hello".toByteArray()))
    }

    @Test
    fun `opus tags read back from the ogg file`() {
        val out = ByteArrayOutputStream().also { webmOpusToOgg(fixture("tone.webm"), it, tags) }.toByteArray()
        keep("tone.opus", out)
        val packets = OggReader(out).packets()
        assertTrue(String(packets[0], 0, 8, Charsets.US_ASCII) == "OpusHead")
        val comments = opusComments(packets[1])
        assertEquals("Saffron Morning", comments["TITLE"])
        assertEquals("Sitara Ensemble", comments["ARTIST"])
        assertEquals("Orbit — রাগ", comments["ALBUM"])
        assertEquals("First line\nSecond line", comments["LYRICS"])
        val picture = Base64.getDecoder().decode(comments.getValue("METADATA_BLOCK_PICTURE"))
        assertEquals(3, u32(picture, 0).toInt()) // front cover
        val mimeLength = u32(picture, 4).toInt()
        assertEquals("image/png", String(picture, 8, mimeLength))
        assertEquals(2L, u32(picture, 8 + mimeLength + 4)) // width from the PNG header
        assertEquals(3L, u32(picture, 8 + mimeLength + 8))
        assertArrayEquals(png, picture.copyOfRange(picture.size - png.size, picture.size))
    }

    @Test
    fun `ogg pages have valid checksums and granules that only grow`() {
        val out = ByteArrayOutputStream().also { webmOpusToOgg(fixture("tone.webm"), it, tags) }.toByteArray()
        val pages = OggReader(out).pages()
        assertTrue(pages.size > 3)
        pages.forEach { page ->
            val copy = page.bytes.copyOf().also { for (i in 22..25) it[i] = 0 }
            assertEquals(u32le(page.bytes, 22), oggCrc(copy))
        }
        assertEquals(2, pages.first().flags and 2) // beginning of stream
        assertEquals(4, pages.last().flags and 4) // end of stream
        assertEquals(0L, pages[1].granule)
        val audio = pages.drop(2).map { it.granule }.filter { it >= 0 }
        assertEquals(audio, audio.sorted())
    }

    @Test
    fun `the last granule counts every sample of two seconds plus the pre-skip`() {
        val webm = fixture("tone.webm")
        val out = ByteArrayOutputStream().also { webmOpusToOgg(webm, it, tags) }.toByteArray()
        val reader = OggReader(out)
        val preSkip = reader.packets()[0].let { (it[10].toInt() and 0xFF) or ((it[11].toInt() and 0xFF) shl 8) }
        val samples = reader.pages().last().granule - preSkip
        // ffmpeg's encoder pads the last frame and marks the padding; two seconds at 48 kHz, give or take one frame.
        assertTrue("samples $samples", samples in 96_000L - 960..96_000L + 960)
    }

    @Test
    fun `a large cover spans several ogg pages and still reads back`() {
        val big = png + ByteArray(200_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream().also { webmOpusToOgg(fixture("tone.webm"), it, SongTags("T", "A", "", 0, big, "image/png")) }.toByteArray()
        val comments = opusComments(OggReader(out).packets()[1])
        val picture = Base64.getDecoder().decode(comments.getValue("METADATA_BLOCK_PICTURE"))
        assertArrayEquals(big, picture.copyOfRange(picture.size - big.size, picture.size))
    }

    @Test(expected = UnsupportedAudioException::class)
    fun `webm that is not opus is refused`() {
        val webm = fixture("tone.webm")
        val at = String(webm, Charsets.ISO_8859_1).indexOf("A_OPUS")
        val vorbis = webm.copyOf().also { "A_VORB".toByteArray().copyInto(it, at) }
        webmOpusToOgg(vorbis, ByteArrayOutputStream(), tags)
    }

    @Test
    fun `fragmented mp4 tags read back and fragments still line up`() {
        val input = fixture("tone_frag.m4a")
        val out = tagMp4(input, tags)
        keep("tone_frag.m4a", out)
        val items = ilst(out)
        assertEquals("Saffron Morning", items["©nam"]?.let { String(it, Charsets.UTF_8) })
        assertEquals("Sitara Ensemble", items["©ART"]?.let { String(it, Charsets.UTF_8) })
        assertEquals("Orbit — রাগ", items["©alb"]?.let { String(it, Charsets.UTF_8) })
        assertEquals("First line\nSecond line", items["©lyr"]?.let { String(it, Charsets.UTF_8) })
        assertArrayEquals(png, items["covr"])
        // Every mdat keeps its bytes, and mfra's fragment offsets point at moof boxes in the new file.
        val before = boxes(input, 0, input.size).filter { it.type == "mdat" }.map { input.copyOfRange(it.start, it.end).toList() }
        val after = boxes(out, 0, out.size)
        assertEquals(before, after.filter { it.type == "mdat" }.map { out.copyOfRange(it.start, it.end).toList() })
        val moofStarts = after.filter { it.type == "moof" }.map { it.start.toLong() }.toSet()
        tfraOffsets(out).forEach { assertTrue("offset $it", it in moofStarts) }
    }

    @Test
    fun `chunk offsets move with a grown moov in a plain mp4`() {
        val input = fixture("tone.m4a")
        val out = tagMp4(input, tags)
        keep("tone.m4a", out)
        val inMdat = boxes(input, 0, input.size).first { it.type == "mdat" }
        val outMdat = boxes(out, 0, out.size).first { it.type == "mdat" }
        val shift = outMdat.start - inMdat.start
        assertTrue(shift > 0)
        assertEquals(stco(input).map { it + shift }, stco(out))
        stco(out).forEach { assertTrue(it >= outMdat.dataStart && it < outMdat.end) }
        assertEquals("Saffron Morning", ilst(out)["©nam"]?.let { String(it, Charsets.UTF_8) })
    }

    @Test
    fun `tagging twice replaces the items instead of adding more`() {
        val once = tagMp4(fixture("tone.m4a"), tags)
        val twice = tagMp4(once, SongTags("New title", "Sitara Ensemble", "", 2_000))
        val items = ilstAll(twice)
        assertEquals(1, items.count { it.first == "©nam" })
        assertEquals("New title", String(items.first { it.first == "©nam" }.second, Charsets.UTF_8))
        assertTrue(items.none { it.first == "covr" })
        assertTrue(items.any { it.first == "©too" }) // the encoder's own item is kept
    }

    @Test
    fun `names drop characters file systems refuse`() {
        assertEquals("AC DC - Back in Black.opus", exportFileName("AC/DC", "Back in Black", "opus"))
        assertEquals("Artist - What Why.m4a", exportFileName("Artist", "What? Why*", "m4a"))
        assertEquals("Untitled.opus", exportFileName("", "  ", "opus"))
        assertEquals("Sitara - x.lrc", sidecarName("Sitara - x.opus", "lrc"))
        val long = exportFileName("A".repeat(100), "B".repeat(100), "opus")
        assertTrue(long.length <= 125)
        assertTrue(long.endsWith(".opus"))
    }

    @Test
    fun `lrc files gain headers only when missing`() {
        val plain = lrcFile(tags, "[00:01.00]First line\n[00:02.50]Second line\n")
        assertEquals(
            "[ti:Saffron Morning]\n[ar:Sitara Ensemble]\n[al:Orbit — রাগ]\n[length:00:02]\n[00:01.00]First line\n[00:02.50]Second line\n",
            plain,
        )
        val own = lrcFile(tags, "[ti:Own]\n[ar:Own artist]\n[00:01.00]First line")
        assertTrue(own.startsWith("[al:Orbit — রাগ]\n[length:00:02]\n[ti:Own]"))
    }

    @Test
    fun `picture sizes come from png and jpeg headers`() {
        assertEquals(2 to 3, pictureSize(png))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 4, 0, 0) +
            byteArrayOf(0xFF.toByte(), 0xC0.toByte(), 0, 11, 8, 0x01, 0x40, 0x00, 0xF0.toByte(), 3, 0, 0, 0)
        assertEquals(240 to 320, pictureSize(jpeg))
        assertEquals(0 to 0, pictureSize(byteArrayOf(1, 2, 3)))
    }

    // Readers on the test side, written from the specifications rather than from the writer.

    private class Page(val bytes: ByteArray, val flags: Int, val granule: Long, val lacing: IntArray, val bodyStart: Int)

    private class OggReader(private val file: ByteArray) {
        fun pages(): List<Page> {
            val pages = mutableListOf<Page>()
            var pos = 0
            while (pos < file.size) {
                assertEquals("OggS", String(file, pos, 4, Charsets.US_ASCII))
                val count = file[pos + 26].toInt() and 0xFF
                val lacing = IntArray(count) { file[pos + 27 + it].toInt() and 0xFF }
                val length = 27 + count + lacing.sum()
                pages += Page(file.copyOfRange(pos, pos + length), file[pos + 5].toInt(), u64le(file, pos + 6), lacing, 27 + count)
                pos += length
            }
            return pages
        }

        fun packets(): List<ByteArray> {
            val packets = mutableListOf<ByteArray>()
            val current = ByteArrayOutputStream()
            pages().forEach { page ->
                var at = page.bodyStart
                page.lacing.forEach { value ->
                    current.write(page.bytes, at, value)
                    at += value
                    if (value < 255) {
                        packets += current.toByteArray()
                        current.reset()
                    }
                }
            }
            return packets
        }
    }

    private fun opusComments(packet: ByteArray): Map<String, String> {
        assertEquals("OpusTags", String(packet, 0, 8, Charsets.US_ASCII))
        var at = 8
        at += 4 + u32le(packet, at)
        val count = u32le(packet, at)
        at += 4
        return (0 until count).associate {
            val length = u32le(packet, at)
            val text = String(packet, at + 4, length, Charsets.UTF_8)
            at += 4 + length
            text.substringBefore('=') to text.substringAfter('=')
        }
    }

    private fun child(file: ByteArray, parent: Box, type: String, skip: Int = 0): Box =
        checkNotNull(boxes(file, parent.dataStart + skip, parent.end).firstOrNull { it.type == type }) { "no $type" }

    private fun ilstAll(file: ByteArray): List<Pair<String, ByteArray>> {
        val moov = boxes(file, 0, file.size).first { it.type == "moov" }
        val meta = child(file, child(file, moov, "udta"), "meta")
        val ilst = child(file, meta, "ilst", skip = 4)
        return boxes(file, ilst.dataStart, ilst.end).map { item ->
            val data = boxes(file, item.dataStart, item.end).first { it.type == "data" }
            item.type to file.copyOfRange(data.dataStart + 8, data.end)
        }
    }

    private fun ilst(file: ByteArray): Map<String, ByteArray> = ilstAll(file).toMap()

    private fun stco(file: ByteArray): List<Long> {
        fun find(from: Int, to: Int): Box? = boxes(file, from, to).firstNotNullOfOrNull { box ->
            when (box.type) {
                "stco" -> box
                "moov", "trak", "mdia", "minf", "stbl" -> find(box.dataStart, box.end)
                else -> null
            }
        }
        val box = checkNotNull(find(0, file.size))
        val count = u32(file, box.dataStart + 4).toInt()
        return (0 until count).map { u32(file, box.dataStart + 8 + 4 * it) }
    }

    private fun tfraOffsets(file: ByteArray): List<Long> {
        val mfra = boxes(file, 0, file.size).first { it.type == "mfra" }
        val tfra = boxes(file, mfra.dataStart, mfra.end).first { it.type == "tfra" }
        val version = file[tfra.dataStart].toInt()
        val sizes = u32(file, tfra.dataStart + 8).toInt()
        val entry = (if (version == 1) 16 else 8) + ((sizes shr 4) and 3) + ((sizes shr 2) and 3) + (sizes and 3) + 3
        val count = u32(file, tfra.dataStart + 12).toInt()
        return (0 until count).map {
            val at = tfra.dataStart + 16 + it * entry
            if (version == 1) u64(file, at + 8) else u32(file, at + 4)
        }
    }
}

private fun u32le(b: ByteArray, at: Int): Int =
    (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

private fun u64le(b: ByteArray, at: Int): Long = (u32le(b, at).toLong() and 0xFFFFFFFFL) or (u32le(b, at + 4).toLong() shl 32)
