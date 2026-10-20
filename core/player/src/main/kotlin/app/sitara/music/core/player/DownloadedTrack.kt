package app.sitara.music.core.player

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** A song's details stored with its download, so the Downloads list and offline playback need no network. */
internal object DownloadedTrack {
    private const val VERSION = 1

    fun encode(track: Track): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeUTF(track.id)
            out.writeUTF(track.title)
            out.writeUTF(track.artist)
            out.writeUTF(track.album)
            out.writeLong(track.durationMs)
            out.writeInt(track.artwork.colors.size)
            track.artwork.colors.forEach(out::writeInt)
            out.writeOptional(track.artwork.imageUrl)
            out.writeBoolean(track.explicit)
            out.writeOptional(track.artistId)
        }
        return bytes.toByteArray()
    }

    /** Null for data this version cannot read, so one bad entry never hides the other downloads. */
    fun decode(data: ByteArray): Track? = try {
        DataInputStream(ByteArrayInputStream(data)).use { input ->
            if (input.readByte().toInt() != VERSION) return null
            val id = input.readUTF()
            val title = input.readUTF()
            val artist = input.readUTF()
            val album = input.readUTF()
            val durationMs = input.readLong()
            val colorCount = input.readInt()
            if (colorCount !in 1..MAX_COLORS) return null
            val colors = List(colorCount) { input.readInt() }
            val imageUrl = input.readOptional()
            Track(id, title, artist, album, durationMs, Artwork(colors, imageUrl), explicit = input.readBoolean(), artistId = input.readOptional())
        }
    } catch (e: IOException) {
        null
    }

    private fun DataOutputStream.writeOptional(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readOptional(): String? = if (readBoolean()) readUTF() else null

    private const val MAX_COLORS = 16
}
