package com.ky7ra.cpkeditor.cpk

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Column value types of a CRI "@UTF" table (low nibble of the column flags). */
internal object UtfType {
    const val U8 = 0x0
    const val S8 = 0x1
    const val U16 = 0x2
    const val S16 = 0x3
    const val U32 = 0x4
    const val S32 = 0x5
    const val U64 = 0x6
    const val S64 = 0x7
    const val FLOAT = 0x8
    const val DOUBLE = 0x9
    const val STRING = 0xA
    const val DATA = 0xB
    const val GUID = 0xC

    fun size(type: Int): Int = when (type) {
        U8, S8 -> 1
        U16, S16 -> 2
        U32, S32, FLOAT, STRING -> 4
        U64, S64, DOUBLE, DATA -> 8
        GUID -> 16
        else -> throw CpkFormatException("Unknown @UTF column type 0x${type.toString(16)}")
    }
}

/**
 * Some CPKs obfuscate their tables with a simple XOR stream. The same operation encrypts and
 * decrypts. A table is considered obfuscated when it does not start with "@UTF".
 */
internal object UtfCrypto {
    fun isPlain(b: ByteArray, off: Int): Boolean =
        b.size >= off + 4 && b[off] == '@'.code.toByte() && b[off + 1] == 'U'.code.toByte() &&
            b[off + 2] == 'T'.code.toByte() && b[off + 3] == 'F'.code.toByte()

    fun xorInPlace(b: ByteArray, off: Int, len: Int) {
        var m = 0x655F
        val t = 0x4115
        for (i in 0 until len) {
            b[off + i] = (b[off + i].toInt() xor (m and 0xFF)).toByte()
            m *= t // intentionally wraps at 32 bits
        }
    }
}

/**
 * Read-only view over one "@UTF" table (all multi-byte values are big endian).
 *
 * Header layout (offsets from the start of "@UTF"):
 * 0x04 size-after-this-field, 0x08 u16 version, 0x0A u16 rows offset, 0x0C u32 strings offset,
 * 0x10 u32 data offset, 0x14 u32 table-name string, 0x18 u16 column count, 0x1A u16 row width,
 * 0x1C u32 row count, 0x20 schema. The three offsets are relative to 0x08.
 */
internal class UtfTable(private val buf: ByteArray, base: Int) {
    private class Column(val type: Int, val constPos: Int, val rowOffset: Int)

    private val columns = HashMap<String, Column>()
    private val rowsBase: Int
    private val stringsBase: Int
    private val rowWidth: Int
    val rowCount: Int
    val tableName: String

    init {
        if (base < 0 || base + 0x20 > buf.size || !UtfCrypto.isPlain(buf, base)) {
            throw CpkFormatException("Not a valid @UTF table")
        }
        rowsBase = checkedOffset(base + 8L + buf.u16be(base + 0x0A))
        stringsBase = checkedOffset(base + 8L + buf.u32be(base + 0x0C))
        rowWidth = buf.u16be(base + 0x1A)
        val count = buf.u32be(base + 0x1C)
        if (count > Int.MAX_VALUE || rowsBase + count * rowWidth > buf.size) {
            throw CpkFormatException("@UTF row data exceeds the table")
        }
        rowCount = count.toInt()
        tableName = stringAt(buf.u32be(base + 0x14))

        var p = base + 0x20
        var rowOff = 0
        repeat(buf.u16be(base + 0x18)) {
            val flags = buf.u8(p++)
            val type = flags and 0x0F
            var name = ""
            if (flags and 0x10 != 0) {
                name = stringAt(buf.u32be(p))
                p += 4
            }
            val size = UtfType.size(type)
            var constPos = -1
            var rowOffset = -1
            if (flags and 0x20 != 0) {
                constPos = p
                p += size
            } else if (flags and 0x40 != 0) {
                rowOffset = rowOff
                rowOff += size
            }
            columns[name] = Column(type, constPos, rowOffset)
        }
        if (rowOff > rowWidth) throw CpkFormatException("@UTF schema is wider than its rows")
    }

    private fun checkedOffset(v: Long): Int {
        if (v < 0 || v > buf.size) throw CpkFormatException("@UTF offset out of range")
        return v.toInt()
    }

    private fun stringAt(offset: Long): String {
        val start = checkedOffset(stringsBase + offset)
        var end = start
        while (end < buf.size && buf[end] != 0.toByte()) end++
        return String(buf, start, end - start, Charsets.UTF_8)
    }

    fun has(name: String): Boolean = columns.containsKey(name)

    private fun valuePos(c: Column, row: Int): Int = when {
        c.constPos >= 0 -> c.constPos
        c.rowOffset >= 0 -> rowsBase + row * rowWidth + c.rowOffset
        else -> -1
    }

    /** Integer column value; 0 (or [default]) when the column is missing or has no data. */
    fun long(row: Int, name: String, default: Long = 0L): Long {
        val c = columns[name] ?: return default
        val pos = valuePos(c, row)
        if (pos < 0) return default
        return when (c.type) {
            UtfType.U8 -> buf.u8(pos).toLong()
            UtfType.S8 -> buf[pos].toLong()
            UtfType.U16 -> buf.u16be(pos).toLong()
            UtfType.S16 -> buf.u16be(pos).toShort().toLong()
            UtfType.U32 -> buf.u32be(pos)
            UtfType.S32 -> buf.u32be(pos).toInt().toLong()
            UtfType.U64, UtfType.S64 -> buf.u64be(pos)
            else -> default
        }
    }

    /** String column value, or null when the column is missing, not a string, or has no data. */
    fun string(row: Int, name: String): String? {
        val c = columns[name] ?: return null
        if (c.type != UtfType.STRING) return null
        val pos = valuePos(c, row)
        if (pos < 0) return null
        return stringAt(buf.u32be(pos))
    }
}

/**
 * Builds a "@UTF" table in which every column is stored per row (the layout CRI's own tools emit
 * for CPK header and TOC tables). Supported column types: integers and strings.
 */
internal class UtfBuilder(
    private val tableName: String,
    private val columns: List<Pair<String, Int>>,
) {
    private val rows = ArrayList<Array<Any>>()

    fun addRow(vararg values: Any) {
        require(values.size == columns.size) { "expected ${columns.size} values, got ${values.size}" }
        rows.add(Array(values.size) { values[it] })
    }

    fun build(): ByteArray {
        val strings = ByteArrayOutputStream()
        val index = HashMap<String, Int>()
        fun str(s: String): Int = index.getOrPut(s) {
            val offset = strings.size()
            strings.write(s.toByteArray(Charsets.UTF_8))
            strings.write(0)
            offset
        }

        str("<NULL>") // CRI convention: string offset 0 is the null string
        val tableNameOffset = str(tableName)
        val columnNameOffsets = columns.map { str(it.first) }
        val rowWidth = columns.sumOf { UtfType.size(it.second) }

        val rowBytes = ByteArrayOutputStream()
        DataOutputStream(rowBytes).use { out ->
            for (row in rows) {
                for ((i, col) in columns.withIndex()) {
                    val v = row[i]
                    when (col.second) {
                        UtfType.U8, UtfType.S8 -> out.writeByte((v as Number).toInt())
                        UtfType.U16, UtfType.S16 -> out.writeShort((v as Number).toInt())
                        UtfType.U32, UtfType.S32 -> out.writeInt((v as Number).toLong().toInt())
                        UtfType.U64, UtfType.S64 -> out.writeLong((v as Number).toLong())
                        UtfType.STRING -> out.writeInt(str(v as String))
                        else -> throw IllegalArgumentException("unsupported column type ${col.second}")
                    }
                }
            }
        }

        val stringBytes = strings.toByteArray()
        val rowsAbs = 0x20 + columns.size * 5
        val stringsAbs = rowsAbs + rowBytes.size()
        val total = alignUp((stringsAbs + stringBytes.size).toLong(), 8).toInt()

        val bb = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        bb.put("@UTF".toByteArray(Charsets.US_ASCII))
        bb.putInt(total - 8)
        bb.putShort(1)
        bb.putShort((rowsAbs - 8).toShort())
        bb.putInt(stringsAbs - 8)
        bb.putInt(total - 8) // no data section: its offset is the end of the table
        bb.putInt(tableNameOffset)
        bb.putShort(columns.size.toShort())
        bb.putShort(rowWidth.toShort())
        bb.putInt(rows.size)
        for ((i, col) in columns.withIndex()) {
            bb.put((0x50 or col.second).toByte()) // has name + stored per row
            bb.putInt(columnNameOffsets[i])
        }
        bb.put(rowBytes.toByteArray())
        bb.put(stringBytes)
        return bb.array()
    }
}
