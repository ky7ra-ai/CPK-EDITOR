package com.ky7ra.cpkeditor.cpk

/**
 * Decompressor for CRI "CRILAYLA" streams, the compression used for CPK entries whose stored
 * size is smaller than their extracted size.
 *
 * Layout: "CRILAYLA", u32le body size, u32le offset of the raw 0x100-byte header (relative to
 * 0x10), the compressed bit stream (read backwards from the end), then the raw header. The
 * result is that raw header followed by the decompressed body, written from the back to the front.
 */
object Layla {
    private val MAGIC = "CRILAYLA".toByteArray(Charsets.US_ASCII)
    private val VLE_BITS = intArrayOf(2, 3, 5, 8)
    private const val RAW_HEADER = 0x100

    fun isLayla(data: ByteArray): Boolean {
        if (data.size < 16) return false
        for (i in MAGIC.indices) if (data[i] != MAGIC[i]) return false
        return true
    }

    fun decompress(input: ByteArray): ByteArray {
        if (!isLayla(input)) throw CpkFormatException("Not a CRILAYLA stream")
        val bodySize = input.u32le(8)
        val headerOffset = input.u32le(12) + 0x10
        if (bodySize > Int.MAX_VALUE - RAW_HEADER) throw CpkFormatException("CRILAYLA body is too large")
        if (headerOffset + RAW_HEADER > input.size) throw CpkFormatException("CRILAYLA header offset is invalid")

        val total = bodySize.toInt() + RAW_HEADER
        val out = ByteArray(total)
        System.arraycopy(input, headerOffset.toInt(), out, 0, RAW_HEADER)

        val bits = BackwardBitReader(input, input.size - RAW_HEADER - 1)
        val outEnd = total - 1
        var produced = 0
        val size = bodySize.toInt()
        while (produced < size) {
            if (bits.read(1) != 0) {
                val distance = bits.read(13) + 3
                var length = 3
                var level = 0
                while (level < VLE_BITS.size) {
                    val v = bits.read(VLE_BITS[level])
                    length += v
                    if (v != (1 shl VLE_BITS[level]) - 1) break
                    level++
                }
                if (level == VLE_BITS.size) {
                    var v: Int
                    do {
                        v = bits.read(8)
                        length += v
                    } while (v == 255)
                }
                var i = 0
                while (i < length && produced < size) {
                    val dst = outEnd - produced
                    val src = dst + distance
                    if (src > outEnd) throw CpkFormatException("CRILAYLA back-reference is out of range")
                    out[dst] = out[src]
                    produced++
                    i++
                }
            } else {
                out[outEnd - produced] = bits.read(8).toByte()
                produced++
            }
        }
        return out
    }

    /** Reads bits MSB-first from bytes taken from the end of the buffer toward the start. */
    private class BackwardBitReader(private val data: ByteArray, private var pos: Int) {
        private var pool = 0
        private var left = 0

        fun read(count: Int): Int {
            var result = 0
            var produced = 0
            while (produced < count) {
                if (left == 0) {
                    if (pos < 16) throw CpkFormatException("CRILAYLA stream ended unexpectedly")
                    pool = data[pos].toInt() and 0xFF
                    pos--
                    left = 8
                }
                val take = minOf(left, count - produced)
                result = (result shl take) or ((pool shr (left - take)) and ((1 shl take) - 1))
                left -= take
                produced += take
            }
            return result
        }
    }
}
