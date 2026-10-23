package app.sitara.music

import android.app.Activity
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.os.Bundle
import android.util.Log
import app.sitara.music.core.player.automix.BeatTracker
import app.sitara.music.core.player.automix.SongSide
import app.sitara.music.core.player.automix.decodeSide
import app.sitara.music.core.player.automix.downsampleForAnalysis
import java.io.File
import kotlin.concurrent.thread

/**
 * Debug only: decodes the start and the end of a 126 BPM drum track bundled with the debug build, through the
 * platform's extractor and decoder as automix does, and logs what the beat tracker finds ("AUTOMIX CHECK ..."), for
 * Device screens to check on the emulator.
 */
class AutomixCheckActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bytes = assets.open("automix-check.ogg").use { it.readBytes() }
        thread {
            val lines = SongSide.entries.map { side ->
                val result = runCatching {
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(Bytes(bytes))
                        val pcm = decodeSide(extractor, side, TRACK_MS) ?: error("nothing decoded")
                        val (samples, rate) = downsampleForAnalysis(pcm.samples, pcm.sampleRate)
                        val analysis = BeatTracker.analyse(samples, rate, pcm.startMs)
                        val grid = analysis.grid ?: error("no beat")
                        "bpm=%.1f confidence=%.2f start=%d downbeat=%d".format(grid.bpm, grid.confidence, pcm.startMs, grid.downbeatMs)
                    } finally {
                        extractor.release()
                    }
                }
                "AUTOMIX CHECK $side " + result.getOrElse { "FAILED ${it.message}" }
            }
            lines.forEach { Log.i(TAG, it) }
            File(getExternalFilesDir(null), "automix-check.txt").writeText(lines.joinToString("\n", postfix = "\n"))
            finish()
        }
    }

    private class Bytes(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize() = bytes.size.toLong()

        override fun close() = Unit
    }

    private companion object {
        const val TAG = "SitaraAutomix"
        const val TRACK_MS = 34_000L
    }
}
