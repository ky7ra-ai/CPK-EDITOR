package com.ky7ra.cpkeditor.cpk

import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** One file inside an opened CPK, as described by its TOC. [offset] is absolute in the CPK file. */
class CpkEntry(
    val dirName: String,
    val fileName: String,
    val storedSize: Long,
    val extractSize: Long,
    val offset: Long,
    val id: Int,
) {
    val path: String = if (dirName.isEmpty()) fileName else "$dirName/$fileName"

    /** True when the stored bytes are compressed (CRILAYLA) and extracting needs inflating. */
    val isCompressed: Boolean get() = extractSize > storedSize
}

/** Header facts worth keeping when the archive is rewritten. */
class CpkInfo(
    val version: Int,
    val revision: Int,
    val align: Int,
    val sorted: Boolean,
    val fileSize: Long,
)

class CpkArchive private constructor(
    private val source: RandomAccessSource,
    val entries: List<CpkEntry>,
    val info: CpkInfo,
) : Closeable {

    /** Stored (possibly compressed) bytes of [entry], streamed straight from the archive. */
    fun openStored(entry: CpkEntry): InputStream = RangeInputStream(source, entry.offset, entry.storedSize)

    /** Writes the extracted (decompressed if needed) bytes of [entry] to [out]. */
    fun extract(entry: CpkEntry, out: OutputStream) {
        if (!entry.isCompressed) {
            openStored(entry).use { it.copyTo(out, 256 * 1024) }
            return
        }
        if (entry.storedSize > Int.MAX_VALUE) throw CpkFormatException("Compressed entry is too large to extract")
        val stored = source.readFully(entry.offset, entry.storedSize.toInt())
        // Entries flagged compressed that do not carry the CRILAYLA magic are copied untouched.
        out.write(if (Layla.isLayla(stored)) Layla.decompress(stored) else stored)
    }

    override fun close() = source.close()

    companion object {
        private const val MAX_TABLE_BYTES = 512L * 1024 * 1024

        /**
         * Parses [source]. On success the archive owns [source] and closes it in [close]; on any
         * failure the source is closed here.
         */
        fun open(source: RandomAccessSource): CpkArchive {
            try {
                return parse(source)
            } catch (t: Throwable) {
                runCatching { source.close() }
                throw when (t) {
                    is IndexOutOfBoundsException -> CpkFormatException("Corrupt CPK structure", t)
                    is EOFException -> CpkFormatException("CPK is truncated", t)
                    else -> t
                }
            }
        }

        private fun parse(source: RandomAccessSource): CpkArchive {
            if (source.length < 0x20) throw CpkFormatException("File is too small to be a CPK")
            val header = readTable(source, 0, "CPK ")
            if (header.rowCount < 1) throw CpkFormatException("CPK header table is empty")

            val tocOffset = header.long(0, "TocOffset")
            val contentOffset = header.long(0, "ContentOffset")
            if (tocOffset <= 0) {
                throw CpkFormatException(
                    if (header.long(0, "ItocOffset") > 0) {
                        "ID-only (ITOC) CPK archives without file names are not supported yet"
                    } else {
                        "This CPK has no file table (TOC)"
                    },
                )
            }
            val toc = readTable(source, tocOffset, "TOC ")

            // File offsets in the TOC are relative to whichever of TocOffset / ContentOffset comes first.
            var base = tocOffset
            if (contentOffset in 1L until base) base = contentOffset

            val hasId = toc.has("ID")
            val entries = ArrayList<CpkEntry>(toc.rowCount)
            for (row in 0 until toc.rowCount) {
                val stored = toc.long(row, "FileSize")
                var extracted = toc.long(row, "ExtractSize")
                if (extracted <= 0) extracted = stored
                entries.add(
                    CpkEntry(
                        dirName = cleanName(toc.string(row, "DirName")),
                        fileName = cleanName(toc.string(row, "FileName")),
                        storedSize = stored,
                        extractSize = extracted,
                        offset = base + toc.long(row, "FileOffset"),
                        id = if (hasId) toc.long(row, "ID").toInt() else row,
                    ),
                )
            }

            val info = CpkInfo(
                version = header.long(0, "Version", 7).toInt(),
                revision = header.long(0, "Revision", 0).toInt(),
                align = header.long(0, "Align", CpkWriter.DEFAULT_ALIGN.toLong()).toInt()
                    .takeIf { it > 0 } ?: CpkWriter.DEFAULT_ALIGN,
                sorted = header.long(0, "Sorted") != 0L,
                fileSize = source.length,
            )
            return CpkArchive(source, entries, info)
        }

        private fun cleanName(s: String?): String =
            if (s == null || s == "<NULL>") "" else s.replace('\\', '/').trim('/')

        /** Reads a chunk (4-char magic, u32 flags, u64 size, then an @UTF table) at [position]. */
        private fun readTable(source: RandomAccessSource, position: Long, magic: String): UtfTable {
            if (position < 0 || position + 0x10 > source.length) {
                throw CpkFormatException("'$magic' table lies outside the file")
            }
            val head = source.readFully(position, 0x10)
            if (String(head, 0, 4, Charsets.US_ASCII) != magic) {
                throw CpkFormatException("Missing '$magic' table at 0x${position.toString(16)}")
            }
            val size = head.u64le(8)
            if (size < 0x20 || size > MAX_TABLE_BYTES || position + 0x10 + size > source.length) {
                throw CpkFormatException("'$magic' table has an invalid size")
            }
            val buf = source.readFully(position + 0x10, size.toInt())
            if (!UtfCrypto.isPlain(buf, 0)) UtfCrypto.xorInPlace(buf, 0, buf.size)
            return UtfTable(buf, 0)
        }
    }
}

/** Streams a byte range of a [RandomAccessSource]. */
internal class RangeInputStream(
    private val source: RandomAccessSource,
    start: Long,
    length: Long,
) : InputStream() {
    private var pos = start
    private val end = start + length

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos >= end) return -1
        val n = source.read(pos, b, off, minOf(len.toLong(), end - pos).toInt())
        if (n <= 0) throw EOFException("CPK ended before the entry did")
        pos += n
        return n
    }

    override fun available(): Int = minOf(end - pos, Int.MAX_VALUE.toLong()).toInt()
}
