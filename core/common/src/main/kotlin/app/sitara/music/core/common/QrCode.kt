package app.sitara.music.core.common

/** How much of a QR code can be damaged and still read: about 7, 15, 25 and 30 %. */
enum class QrEcc(internal val formatBits: Int, internal val ordinalInTables: Int) {
    L(1, 0),
    M(0, 1),
    Q(3, 2),
    H(2, 3),
}

/**
 * A QR code (ISO/IEC 18004, model 2) for a piece of text in byte mode, at the smallest version (1 to 40) that holds it,
 * with the mask that scores the fewest penalty points. [get] is true for a dark module; the quiet zone around the code
 * is left to whoever draws it.
 */
class QrCode private constructor(val version: Int, val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]

    companion object {
        /** The code for [text] (UTF-8); throws [IllegalArgumentException] when it is too long for version 40. */
        fun encode(text: String, ecc: QrEcc = QrEcc.M): QrCode {
            val data = text.toByteArray(Charsets.UTF_8)
            val version = (1..40).firstOrNull { v -> 4 + countBits(v) + data.size * 8 <= dataCodewords(v, ecc) * 8 }
                ?: throw IllegalArgumentException("Too long for a QR code: ${data.size} bytes")
            val codewords = withErrorCorrection(dataBits(data, version, ecc), version, ecc)
            val builder = Builder(version)
            builder.drawFunctionPatterns()
            builder.placeData(codewords)
            val best = (0 until 8).minBy { mask ->
                builder.applyMask(mask)
                builder.drawFormat(ecc, mask)
                builder.penalty().also { builder.applyMask(mask) } // a mask is its own inverse
            }
            builder.applyMask(best)
            builder.drawFormat(ecc, best)
            return QrCode(version, builder.size, builder.dark)
        }

        private fun countBits(version: Int) = if (version <= 9) 8 else 16

        /** Mode, length, the bytes, the terminator, then padding to the version's data capacity. */
        private fun dataBits(data: ByteArray, version: Int, ecc: QrEcc): ByteArray {
            val bits = BitBuffer()
            bits.append(0b0100, 4)
            bits.append(data.size, countBits(version))
            data.forEach { bits.append(it.toInt() and 0xFF, 8) }
            val capacity = dataCodewords(version, ecc) * 8
            bits.append(0, minOf(4, capacity - bits.size))
            bits.append(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capacity) {
                bits.append(pad, 8)
                pad = pad xor (0xEC xor 0x11)
            }
            return bits.toBytes()
        }

        /** The data split into blocks, each followed by its Reed-Solomon codewords, interleaved as the standard lays out. */
        private fun withErrorCorrection(data: ByteArray, version: Int, ecc: QrEcc): ByteArray {
            val blocks = ECC_BLOCKS[ecc.ordinalInTables][version]
            val eccLength = ECC_PER_BLOCK[ecc.ordinalInTables][version]
            val raw = rawDataModules(version) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLength = raw / blocks
            val divisor = rsDivisor(eccLength)
            val dataBlocks = mutableListOf<ByteArray>()
            var offset = 0
            for (i in 0 until blocks) {
                val dataLength = shortLength - eccLength + if (i < shortBlocks) 0 else 1
                dataBlocks += data.copyOfRange(offset, offset + dataLength)
                offset += dataLength
            }
            val eccBlocks = dataBlocks.map { rsRemainder(it, divisor) }
            val out = ArrayList<Byte>(raw)
            for (i in 0 until shortLength - eccLength + 1) dataBlocks.forEach { block -> if (i < block.size) out += block[i] }
            for (i in 0 until eccLength) eccBlocks.forEach { block -> out += block[i] }
            return out.toByteArray()
        }

        internal fun dataCodewords(version: Int, ecc: QrEcc) =
            rawDataModules(version) / 8 - ECC_PER_BLOCK[ecc.ordinalInTables][version] * ECC_BLOCKS[ecc.ordinalInTables][version]

        /** Modules left for data and error correction once the function patterns are drawn. */
        private fun rawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val align = version / 7 + 2
                result -= (25 * align - 10) * align - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        private fun rsDivisor(degree: Int): IntArray {
            val result = IntArray(degree).also { it[degree - 1] = 1 }
            var root = 1
            repeat(degree) {
                for (j in 0 until degree) {
                    result[j] = gfMultiply(result[j], root)
                    if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
                }
                root = gfMultiply(root, 0x02)
            }
            return result
        }

        private fun rsRemainder(data: ByteArray, divisor: IntArray): ByteArray {
            val result = IntArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor result[0]
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
            }
            return ByteArray(result.size) { result[it].toByte() }
        }

        /** Multiplication in GF(2^8) modulo x^8 + x^4 + x^3 + x^2 + 1. */
        private fun gfMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        // Error correction codewords per block and number of blocks, by level (L, M, Q, H) and version (index 0 unused).
        private val ECC_PER_BLOCK = arrayOf(
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
            intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
        )
        private val ECC_BLOCKS = arrayOf(
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
            intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
            intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81),
        )
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val size get() = bits.size

        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits += (value ushr i) and 1 == 1
        }

        fun toBytes() = ByteArray(bits.size / 8) { i -> (0 until 8).fold(0) { acc, j -> (acc shl 1) or if (bits[i * 8 + j]) 1 else 0 }.toByte() }
    }

    /** The module grid while it is drawn: which modules are dark and which belong to function patterns. */
    private class Builder(val version: Int) {
        val size = version * 4 + 17
        val dark = BooleanArray(size * size)
        private val function = BooleanArray(size * size)

        private fun set(x: Int, y: Int, value: Boolean) {
            dark[y * size + x] = value
            function[y * size + x] = true
        }

        fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val positions = alignmentPositions()
            for (i in positions.indices) {
                for (j in positions.indices) {
                    val corner = (i == 0 && j == 0) || (i == 0 && j == positions.size - 1) || (i == positions.size - 1 && j == 0)
                    if (!corner) alignment(positions[i], positions[j])
                }
            }
            drawFormat(QrEcc.L, 0) // reserves the format areas; drawn for real once the mask is chosen
            drawVersion()
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until size && y in 0 until size) {
                        val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                        set(x, y, distance != 2 && distance != 4)
                    }
                }
            }
        }

        private fun alignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) set(cx + dx, cy + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = if (version == 32) 26 else (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
            return IntArray(count) { i -> if (i == 0) 6 else size - 7 - (count - 1 - i) * step }
        }

        fun drawFormat(ecc: QrEcc, mask: Int) {
            val data = ecc.formatBits shl 3 or mask
            var remainder = data
            repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
            val bits = (data shl 10 or remainder) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true) // the dark module
        }

        private fun drawVersion() {
            if (version < 7) return
            var remainder = version
            repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
            val bits = version shl 12 or remainder
            for (i in 0 until 18) {
                val bit = (bits ushr i) and 1 != 0
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, bit)
                set(b, a, bit)
            }
        }

        /** Codewords in the zigzag order: two columns at a time from the right, up then down, skipping the timing column. */
        fun placeData(codewords: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vertical in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = (right + 1) and 2 == 0
                        val y = if (upward) size - 1 - vertical else vertical
                        if (!function[y * size + x] && i < codewords.size * 8) {
                            dark[y * size + x] = (codewords[i ushr 3].toInt() ushr (7 - (i and 7))) and 1 != 0
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val flip = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                    if (flip && !function[y * size + x]) dark[y * size + x] = !dark[y * size + x]
                }
            }
        }

        /** The standard's four penalty rules: long runs, 2 × 2 blocks, finder look-alikes and an uneven dark share. */
        fun penalty(): Int {
            var points = 0
            fun at(x: Int, y: Int) = dark[y * size + x]
            for (horizontal in listOf(true, false)) {
                for (a in 0 until size) {
                    var run = 1
                    for (b in 1 until size) {
                        val same = if (horizontal) at(b, a) == at(b - 1, a) else at(a, b) == at(a, b - 1)
                        if (same) {
                            run++
                        } else {
                            if (run >= 5) points += run - 2
                            run = 1
                        }
                    }
                    if (run >= 5) points += run - 2
                    for (b in 0..size - 11) {
                        val window = BooleanArray(11) { k -> if (horizontal) at(b + k, a) else at(a, b + k) }
                        if (window.contentEquals(FINDER_LIKE) || window.contentEquals(FINDER_LIKE.reversedArray())) points += 40
                    }
                }
            }
            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val c = at(x, y)
                    if (c == at(x + 1, y) && c == at(x, y + 1) && c == at(x + 1, y + 1)) points += 3
                }
            }
            val darkCount = dark.count { it }
            val total = size * size
            val k = (kotlin.math.abs(darkCount * 20 - total * 10) + total - 1) / total - 1
            return points + k.coerceAtLeast(0) * 10
        }

        private companion object {
            val FINDER_LIKE = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        }
    }
}
