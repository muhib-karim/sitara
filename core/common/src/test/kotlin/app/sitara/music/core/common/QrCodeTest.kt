package app.sitara.music.core.common

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Every code is read back by an independent decoder (zxing, test only) from a drawn picture with its quiet zone. */
class QrCodeTest {
    private fun decode(code: QrCode, scale: Int = 4): String {
        val quiet = 4
        val side = (code.size + 2 * quiet) * scale
        val pixels = IntArray(side * side) { i ->
            val x = (i % side) / scale - quiet
            val y = (i / side) / scale - quiet
            if (x in 0 until code.size && y in 0 until code.size && code[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true)).text
    }

    @Test
    fun `a song link reads back`() {
        val link = "https://music.youtube.com/watch?v=x7kGuhlXmFI"
        assertEquals(link, decode(QrCode.encode(link)))
    }

    @Test
    fun `a long playlist link reads back at every error correction level`() {
        val link = "https://music.youtube.com/playlist?list=OLAK5uy_kHG1zp_rUfi2bCQqGqK8JTU7DlbmzyEhM&si=AbCdEfGhIjKlMnOp"
        QrEcc.entries.forEach { ecc -> assertEquals(link, decode(QrCode.encode(link, ecc)), "level $ecc") }
    }

    @Test
    fun `text outside ASCII reads back`() {
        val text = "সিতারা · Sitara — ستارہ"
        assertEquals(text, decode(QrCode.encode(text)))
    }

    @Test
    fun `the smallest version that holds the text is used`() {
        assertEquals(1, QrCode.encode("HELLO").version)
        assertEquals(21, QrCode.encode("HELLO").size)
        val v = QrCode.encode("https://music.youtube.com/watch?v=x7kGuhlXmFI").version
        assertTrue(v in 3..4, "version $v")
    }

    @Test
    fun `every version from 1 to 40 reads back`() {
        for (version in listOf(1, 2, 6, 7, 10, 14, 20, 27, 32, 40)) {
            // The most bytes that fit this version at level L, so the code is exactly that version.
            val bytes = QrCode.dataCodewords(version, QrEcc.L) - if (version <= 9) 2 else 3
            val text = "x".repeat(bytes)
            val code = QrCode.encode(text, QrEcc.L)
            assertEquals(version, code.version)
            assertEquals(text, decode(code, scale = 2))
        }
    }

    @Test
    fun `text too long for version 40 is refused`() {
        assertThrows<IllegalArgumentException> { QrCode.encode("x".repeat(3_000), QrEcc.L) }
    }
}
