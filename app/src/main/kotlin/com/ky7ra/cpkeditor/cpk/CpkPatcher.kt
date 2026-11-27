package com.ky7ra.cpkeditor.cpk

import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CancellationException

internal class PatchRegion(
    val start: Long,
    val length: Long,
    /** Path of the file whose data this region holds, for progress reporting; null for table regions. */
    val label: String?,
    val write: (OutputStream, ByteArray, () -> Boolean) -> Unit,
)

/** What [CpkPatcher.write] will do. Created by [CpkPatcher.plan]. */
class CpkPatchPlan internal constructor(
    internal val archive: CpkArchive,
    internal val regions: List<PatchRegion>,
    internal val appended: List<WorkingEntry>,
    val newLength: Long,
    /** How many files get new content. */
    val replacedCount: Int,
    /** How many of those did not fit their old slot and were appended at the end of the file. */
    val appendedCount: Int,
)

/**
 * Saves edits by *patching* the original CPK instead of rebuilding it: every original byte is kept
 * (header, ETOC/ITOC/GTOC, file order and alignment, obfuscation) except
 *  - the TOC rows of replaced files (size, extract size, offset),
 *  - the data of replaced files that fit their old slot (overwritten in place), and
 *  - when something had to be appended: the header's FileSize.
 * Files that do not fit their old slot are appended after the end of the original file.
 *
 * Only possible when the entry list is the original one with some contents replaced: no files
 * added or removed. Otherwise [plan] returns null and [CpkWriter] must be used.
 */
object CpkPatcher {
    private const val COPY_BUFFER = 256 * 1024

    fun plan(archive: CpkArchive, entries: List<WorkingEntry>): CpkPatchPlan? {
        val orig = archive.entries
        if (entries.size != orig.size) return null
        for (i in orig.indices) {
            if (entries[i].state == EntryState.ADDED || entries[i].path != orig[i].path) return null
        }
        val changed = orig.indices.filter { entries[it].state == EntryState.REPLACED }

        val align = archive.info.align.toLong().coerceAtLeast(1)
        val srcLen = archive.sourceLength
        val toc = archive.tocData
        val tocBuf = toc.buf.copyOf()
        val shared = sharedFlags(orig)

        val regions = ArrayList<PatchRegion>()
        val appended = ArrayList<WorkingEntry>()
        var appendPos = alignUp(srcLen, align)

        for (i in changed) {
            val e = entries[i]
            val o = orig[i]
            val fitsOldSlot = !shared[i] && o.storedSize > 0 && e.storedSize <= o.storedSize &&
                o.offset >= 0 && o.offset + o.storedSize <= srcLen
            val newOffset: Long
            if (fitsOldSlot) {
                newOffset = o.offset
                regions.add(
                    PatchRegion(o.offset, o.storedSize, e.path) { out, buffer, isCancelled ->
                        streamEntry(e, out, buffer, isCancelled)
                        writeZeros(out, o.storedSize - e.storedSize)
                    },
                )
            } else {
                newOffset = appendPos
                appendPos = alignUp(appendPos + e.storedSize, align)
                appended.add(e)
            }
            if (!put(tocBuf, toc.table.rowSlot(i, "FileSize"), e.storedSize) ||
                !put(tocBuf, toc.table.rowSlot(i, "ExtractSize"), e.extractSize) ||
                !put(tocBuf, toc.table.rowSlot(i, "FileOffset"), newOffset - archive.dataBase)
            ) {
                return null // the TOC does not store these as plain per-row numbers
            }
        }

        val newLength = if (appended.isEmpty()) srcLen else appendPos

        if (changed.isNotEmpty()) {
            val bytes = if (toc.encrypted) obfuscated(tocBuf) else tocBuf
            regions.add(PatchRegion(toc.position + 0x10, bytes.size.toLong(), null) { out, _, _ -> out.write(bytes) })
        }
        if (appended.isNotEmpty()) {
            val header = archive.headerData
            val slot = header.table.rowSlot(0, "FileSize")
            if (slot != null) {
                val buf = header.buf.copyOf()
                if (put(buf, slot, newLength)) {
                    val bytes = if (header.encrypted) obfuscated(buf) else buf
                    regions.add(PatchRegion(header.position + 0x10, bytes.size.toLong(), null) { out, _, _ -> out.write(bytes) })
                }
            }
        }

        val sorted = regions.sortedBy { it.start }
        for (k in sorted.indices) {
            if (sorted[k].start < 0 || sorted[k].start + sorted[k].length > srcLen) return null
            if (k > 0 && sorted[k].start < sorted[k - 1].start + sorted[k - 1].length) return null
        }
        return CpkPatchPlan(archive, sorted, appended, newLength, changed.size, appended.size)
    }

    /** Writes the patched CPK to [out] sequentially. @param progress (bytesWritten, totalBytes) */
    fun write(
        plan: CpkPatchPlan,
        out: OutputStream,
        progress: ((Long, Long) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
        onEntry: ((String) -> Unit)? = null,
    ) {
        val archive = plan.archive
        val srcLen = archive.sourceLength
        var written = 0L
        val sink = object : OutputStream() {
            private fun advance(n: Int) {
                written += n
                progress?.invoke(written, plan.newLength)
            }

            override fun write(b: Int) {
                out.write(b)
                advance(1)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                out.write(b, off, len)
                advance(len)
            }

            override fun flush() = out.flush()
        }

        val buffer = ByteArray(COPY_BUFFER)
        var pos = 0L
        for (r in plan.regions) {
            archive.copyRaw(pos, r.start, sink, buffer, isCancelled)
            r.label?.let { onEntry?.invoke(it) }
            r.write(sink, buffer, isCancelled)
            pos = r.start + r.length
        }
        archive.copyRaw(pos, srcLen, sink, buffer, isCancelled)

        if (plan.appended.isNotEmpty()) {
            val align = archive.info.align.toLong().coerceAtLeast(1)
            writeZeros(sink, alignUp(srcLen, align) - srcLen)
            for (e in plan.appended) {
                onEntry?.invoke(e.path)
                streamEntry(e, sink, buffer, isCancelled)
                writeZeros(sink, alignUp(e.storedSize, align) - e.storedSize)
            }
        }
        sink.flush()
        if (written != plan.newLength) throw IOException("Patched CPK has $written bytes, expected ${plan.newLength}")
    }

    private fun obfuscated(table: ByteArray): ByteArray {
        UtfCrypto.xorInPlace(table, 0, table.size)
        return table
    }

    private fun put(buf: ByteArray, slot: UtfTable.Slot?, value: Long): Boolean {
        if (slot == null || value < 0 || slot.size > 8) return false
        if (slot.size < 8 && value >= (1L shl (8 * slot.size))) return false
        for (k in 0 until slot.size) buf[slot.pos + k] = (value shr (8 * (slot.size - 1 - k))).toByte()
        return true
    }

    /** Entries whose data overlaps another entry's data (deduplicated CPKs); never overwritten in place. */
    private fun sharedFlags(entries: List<CpkEntry>): BooleanArray {
        val shared = BooleanArray(entries.size)
        val order = entries.indices.filter { entries[it].storedSize > 0 }.sortedBy { entries[it].offset }
        var clusterStart = 0
        var clusterEnd = Long.MIN_VALUE
        fun closeCluster(endExclusive: Int) {
            if (endExclusive - clusterStart > 1) for (k in clusterStart until endExclusive) shared[order[k]] = true
        }
        for ((k, i) in order.withIndex()) {
            val e = entries[i]
            if (k > 0 && e.offset >= clusterEnd) {
                closeCluster(k)
                clusterStart = k
                clusterEnd = Long.MIN_VALUE
            }
            clusterEnd = maxOf(clusterEnd, e.offset + e.storedSize)
        }
        closeCluster(order.size)
        return shared
    }

    private fun streamEntry(e: WorkingEntry, out: OutputStream, buffer: ByteArray, isCancelled: () -> Boolean) {
        var remaining = e.storedSize
        e.opener().use { input ->
            while (remaining > 0) {
                if (isCancelled()) throw CancellationException("CPK write cancelled")
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (n < 0) throw IOException("'${e.path}' ended $remaining bytes early")
                out.write(buffer, 0, n)
                remaining -= n
            }
            if (input.read() >= 0) throw IOException("'${e.path}' grew while it was being saved")
        }
    }

    private fun writeZeros(out: OutputStream, count: Long) {
        var left = count
        val zeros = ByteArray(minOf(maxOf(left, 1L), 65536L).toInt())
        while (left > 0) {
            val n = minOf(left, zeros.size.toLong()).toInt()
            out.write(zeros, 0, n)
            left -= n
        }
    }
}
