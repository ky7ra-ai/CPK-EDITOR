package com.ky7ra.cpkeditor.cpk

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

/**
 * Writes a complete CPK to a purely sequential [OutputStream] (so it works with Android's
 * document providers, which cannot seek).
 *
 * Layout produced: 0x800-byte header area ("CPK " chunk + "(c)CRI" tag), the "TOC " chunk at
 * 0x800, then every file aligned to [CpkWriter.DEFAULT_ALIGN]. Entries keep the order they are
 * given in. Stored bytes are copied verbatim, so compressed entries stay compressed. ETOC, ITOC
 * and GTOC tables are not emitted.
 */
object CpkWriter {
    const val DEFAULT_ALIGN = 0x800

    private const val HEADER_AREA = 0x800
    private const val COPY_BUFFER = 256 * 1024
    private const val MAX_STORED = 0xFFFFFFFFL

    /**
     * @param sorted value of the header's "Sorted" flag. Pass true only when [entries] are in the
     * same order as the archive they came from and nothing was appended.
     * @param progress called with (bytesWritten, totalBytes) of file data.
     * @param isCancelled polled between chunks; a true result aborts with [CancellationException].
     */
    fun write(
        entries: List<WorkingEntry>,
        out: OutputStream,
        sorted: Boolean = false,
        progress: ((Long, Long) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
    ) {
        val align = DEFAULT_ALIGN.toLong()
        val count = entries.size
        for (e in entries) {
            if (e.storedSize > MAX_STORED || e.extractSize > MAX_STORED) {
                throw IOException("'${e.path}' is larger than 4 GiB, which a CPK entry cannot hold")
            }
        }

        // The TOC has fixed-width rows, so its size does not depend on the offsets it will hold.
        val tocUtfDraft = buildToc(entries, LongArray(count))
        val tocOffset = HEADER_AREA.toLong()
        val contentOffset = alignUp(tocOffset + 0x10 + tocUtfDraft.size, align)

        val absolute = LongArray(count)
        var pos = contentOffset
        for ((i, e) in entries.withIndex()) {
            absolute[i] = pos
            pos = alignUp(pos + e.storedSize, align)
        }
        val fileSize = pos
        val tocUtf = buildToc(entries, LongArray(count) { absolute[it] - tocOffset })

        val headerUtf = buildHeader(
            fileSize = fileSize,
            contentOffset = contentOffset,
            contentSize = fileSize - contentOffset,
            tocOffset = tocOffset,
            tocSize = 0x10L + tocUtf.size,
            packed = entries.sumOf { it.storedSize },
            data = entries.sumOf { it.extractSize },
            files = count,
            sorted = sorted,
        )

        val headerChunk = chunk("CPK ", headerUtf)
        check(headerChunk.size <= HEADER_AREA - 6) { "CPK header does not fit its area" }
        out.write(headerChunk)
        writeZeros(out, HEADER_AREA - 6 - headerChunk.size.toLong())
        out.write("(c)CRI".toByteArray(Charsets.US_ASCII))

        val tocChunk = chunk("TOC ", tocUtf)
        out.write(tocChunk)
        writeZeros(out, contentOffset - tocOffset - tocChunk.size)

        val total = entries.sumOf { it.storedSize }
        var done = 0L
        val buffer = ByteArray(COPY_BUFFER)
        for (e in entries) {
            if (isCancelled()) throw CancellationException("CPK write cancelled")
            var remaining = e.storedSize
            e.opener().use { input ->
                while (remaining > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (n < 0) throw IOException("'${e.path}' ended ${remaining} bytes early")
                    out.write(buffer, 0, n)
                    remaining -= n
                    done += n
                    progress?.invoke(done, total)
                    if (isCancelled()) throw CancellationException("CPK write cancelled")
                }
                if (input.read() >= 0) throw IOException("'${e.path}' grew while it was being saved")
            }
            writeZeros(out, alignUp(e.storedSize, align) - e.storedSize)
        }
        out.flush()
    }

    private fun buildToc(entries: List<WorkingEntry>, relativeOffsets: LongArray): ByteArray {
        val table = UtfBuilder(
            "CpkTocInfo",
            listOf(
                "DirName" to UtfType.STRING,
                "FileName" to UtfType.STRING,
                "FileSize" to UtfType.U32,
                "ExtractSize" to UtfType.U32,
                "FileOffset" to UtfType.U64,
                "ID" to UtfType.U32,
                "UserString" to UtfType.STRING,
            ),
        )
        for ((i, e) in entries.withIndex()) {
            table.addRow(
                if (e.dir.isEmpty()) "<NULL>" else e.dir,
                e.name,
                e.storedSize,
                e.extractSize,
                relativeOffsets[i],
                i.toLong(),
                "<NULL>",
            )
        }
        return table.build()
    }

    private fun buildHeader(
        fileSize: Long,
        contentOffset: Long,
        contentSize: Long,
        tocOffset: Long,
        tocSize: Long,
        packed: Long,
        data: Long,
        files: Int,
        sorted: Boolean,
    ): ByteArray {
        val table = UtfBuilder(
            "CpkHeader",
            listOf(
                "UpdateDateTime" to UtfType.U64,
                "FileSize" to UtfType.U64,
                "ContentOffset" to UtfType.U64,
                "ContentSize" to UtfType.U64,
                "TocOffset" to UtfType.U64,
                "TocSize" to UtfType.U64,
                "EtocOffset" to UtfType.U64,
                "EtocSize" to UtfType.U64,
                "ItocOffset" to UtfType.U64,
                "ItocSize" to UtfType.U64,
                "GtocOffset" to UtfType.U64,
                "GtocSize" to UtfType.U64,
                "EnabledPackedSize" to UtfType.U64,
                "EnabledDataSize" to UtfType.U64,
                "Files" to UtfType.U32,
                "Groups" to UtfType.U32,
                "Attrs" to UtfType.U32,
                "Version" to UtfType.U16,
                "Revision" to UtfType.U16,
                "Align" to UtfType.U16,
                "Sorted" to UtfType.U16,
                "EnableFileName" to UtfType.U16,
                "EID" to UtfType.U16,
                "CpkMode" to UtfType.U32,
                "Tvers" to UtfType.STRING,
                "Comment" to UtfType.STRING,
            ),
        )
        table.addRow(
            0L, fileSize, contentOffset, contentSize, tocOffset, tocSize,
            0L, 0L, 0L, 0L, 0L, 0L,
            packed, data,
            files.toLong(), 0L, 0L,
            7, 0, DEFAULT_ALIGN, if (sorted) 1 else 0, 1, 0,
            1L,
            "CPK-EDITOR", "<NULL>",
        )
        return table.build()
    }

    /** "CPK " / "TOC " chunk: magic, u32 0xFF, u64 size (all little endian), then the @UTF table. */
    private fun chunk(magic: String, utf: ByteArray): ByteArray {
        val bb = ByteBuffer.allocate(0x10 + utf.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(magic.toByteArray(Charsets.US_ASCII))
        bb.putInt(0xFF)
        bb.putLong(utf.size.toLong())
        bb.put(utf)
        return bb.array()
    }

    private fun writeZeros(out: OutputStream, count: Long) {
        var left = count
        val zeros = ByteArray(minOf(left, 8192L).toInt().coerceAtLeast(1))
        while (left > 0) {
            val n = minOf(left, zeros.size.toLong()).toInt()
            out.write(zeros, 0, n)
            left -= n
        }
    }
}
