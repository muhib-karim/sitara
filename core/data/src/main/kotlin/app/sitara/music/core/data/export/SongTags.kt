package app.sitara.music.core.data.export

/**
 * What an exported file says about its song. [cover] is a JPEG or PNG picture ([coverMime] says which); [lyrics] is
 * plain text for the tag, since synced lyrics go into an .lrc file beside the song.
 */
class SongTags(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val cover: ByteArray? = null,
    val coverMime: String? = null,
    val lyrics: String? = null,
)

/** A downloaded file whose audio cannot be tagged in place; the caller copies it as it is. */
class UnsupportedAudioException(message: String) : Exception(message)

/** The kind of file a download was saved as, told by its first bytes; YouTube serves WebM (Opus) and MP4 (AAC). */
enum class AudioContainer { WEBM, MP4, UNKNOWN }

fun sniffContainer(head: ByteArray): AudioContainer = when {
    head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() ->
        AudioContainer.WEBM
    head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> AudioContainer.MP4
    else -> AudioContainer.UNKNOWN
}

/** Width and height of a PNG or JPEG picture, or 0 × 0 when the header cannot be read. */
internal fun pictureSize(bytes: ByteArray): Pair<Int, Int> {
    fun u8(i: Int) = bytes[i].toInt() and 0xFF
    fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
    fun u32(i: Int) = (u16(i) shl 16) or u16(i + 2)
    if (bytes.size >= 24 && u8(0) == 0x89 && bytes[1] == 'P'.code.toByte()) return u32(16) to u32(20)
    if (bytes.size < 4 || u8(0) != 0xFF || u8(1) != 0xD8) return 0 to 0
    var i = 2
    while (i + 9 < bytes.size) {
        if (u8(i) != 0xFF) return 0 to 0
        val marker = u8(i + 1)
        val length = u16(i + 2)
        // SOF0 to SOF15 carry the size, except DHT (C4), JPG (C8) and DAC (CC).
        if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) return u16(i + 7) to u16(i + 5)
        i += 2 + length
    }
    return 0 to 0
}
