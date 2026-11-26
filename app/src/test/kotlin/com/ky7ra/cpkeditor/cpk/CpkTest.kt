package com.ky7ra.cpkeditor.cpk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CpkTest {

    // ---- helpers ---------------------------------------------------------------------------

    private fun bytes(size: Int, seed: Int): ByteArray = ByteArray(size) { ((it * 31 + seed) and 0xFF).toByte() }

    private fun working(
        path: String,
        data: ByteArray,
        state: EntryState = EntryState.ADDED,
        extractSize: Long = data.size.toLong(),
    ) = WorkingEntry(path, data.size.toLong(), extractSize, state, { ByteArrayInputStream(data) })

    private fun newFile(path: String, data: ByteArray) =
        NewFile(path, data.size.toLong()) { ByteArrayInputStream(data) }

    private fun write(entries: List<WorkingEntry>, sorted: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        CpkWriter.write(entries, out, sorted)
        return out.toByteArray()
    }

    private fun open(data: ByteArray) = CpkArchive.open(ByteArraySource(data))

    private fun CpkArchive.read(path: String): ByteArray {
        val e = entries.first { it.path == path }
        return ByteArrayOutputStream().also { extract(e, it) }.toByteArray()
    }

    // ---- structure -------------------------------------------------------------------------

    @Test
    fun writtenArchiveHasExpectedLayoutAndRoundTrips() {
        val a = bytes(10, 1)
        val b = bytes(5000, 2)
        val empty = ByteArray(0)
        val data = write(
            listOf(
                working("data/spc/a.bin", a),
                working("root.txt", b),
                working("data/empty.dat", empty),
            ),
        )

        assertEquals("CPK ", String(data, 0, 4, Charsets.US_ASCII))
        assertEquals("(c)CRI", String(data, 0x7FA, 6, Charsets.US_ASCII))
        assertEquals("TOC ", String(data, 0x800, 4, Charsets.US_ASCII))
        assertEquals(0L, data.size % 0x800L)

        val cpk = open(data)
        assertEquals(listOf("data/spc/a.bin", "root.txt", "data/empty.dat"), cpk.entries.map { it.path })
        assertEquals(data.size.toLong(), cpk.info.fileSize)
        assertEquals(0x800, cpk.info.align)
        for (e in cpk.entries) assertEquals("offset of ${e.path}", 0L, e.offset % 0x800)
        assertArrayEquals(a, cpk.read("data/spc/a.bin"))
        assertArrayEquals(b, cpk.read("root.txt"))
        assertArrayEquals(empty, cpk.read("data/empty.dat"))
        assertEquals("", cpk.entries[1].dirName)
        assertEquals("data/spc", cpk.entries[0].dirName)
        assertEquals(listOf(0, 1, 2), cpk.entries.map { it.id })
    }

    @Test
    fun emptyArchiveIsValid() {
        val cpk = open(write(emptyList()))
        assertTrue(cpk.entries.isEmpty())
    }

    @Test
    fun manyEntriesRoundTrip() {
        val entries = (0 until 3000).map { working("dir${it % 7}/file_$it.bin", bytes(it % 40, it)) }
        val cpk = open(write(entries))
        assertEquals(3000, cpk.entries.size)
        assertArrayEquals(bytes(1234 % 40, 1234), cpk.read("dir${1234 % 7}/file_1234.bin"))
        assertEquals("dir${2999 % 7}/file_2999.bin", cpk.entries.last().path)
    }

    @Test
    fun sortedFlagIsWrittenAndRead() {
        assertTrue(open(write(listOf(working("a", bytes(3, 0))), sorted = true)).info.sorted)
        assertFalse(open(write(listOf(working("a", bytes(3, 0))), sorted = false)).info.sorted)
    }

    @Test
    fun unicodeNamesSurvive() {
        val cpk = open(write(listOf(working("データ/キャラ.bin", bytes(9, 3)))))
        assertEquals("データ/キャラ.bin", cpk.entries.single().path)
    }

    // ---- editing ---------------------------------------------------------------------------

    @Test
    fun addReplaceAndRemoveThroughARewrite() {
        val first = write(
            listOf(
                working("data/a.bin", bytes(100, 1)),
                working("data/b.bin", bytes(200, 2)),
                working("c.bin", bytes(300, 3)),
            ),
        )
        val archive = open(first)
        var list = CpkEditing.fromArchive(archive)
        assertTrue(list.all { it.state == EntryState.ORIGINAL })

        val newB = bytes(777, 9)
        val newD = bytes(64, 8)
        val result = CpkEditing.addOrReplace(
            list,
            listOf(newFile("DATA\\B.BIN", newB), newFile("/new/d.bin", newD), newFile("new//d.bin", newD)),
        )
        assertEquals(1, result.added)
        assertEquals(1, result.replaced)
        list = CpkEditing.remove(result.entries, setOf("c.bin"))

        val second = open(write(list))
        assertEquals(listOf("data/a.bin", "data/b.bin", "new/d.bin"), second.entries.map { it.path })
        assertArrayEquals(bytes(100, 1), second.read("data/a.bin"))
        assertArrayEquals(newB, second.read("data/b.bin"))
        assertArrayEquals(newD, second.read("new/d.bin"))
        assertEquals(
            listOf(EntryState.ORIGINAL, EntryState.REPLACED, EntryState.ADDED),
            list.map { it.state },
        )
    }

    @Test
    fun replacingAnAddedFileKeepsItAddedAndCountsNothingNew() {
        val r1 = CpkEditing.addOrReplace(emptyList(), listOf(newFile("x/y.bin", bytes(4, 1))))
        val r2 = CpkEditing.addOrReplace(r1.entries, listOf(newFile("x/y.bin", bytes(8, 2))))
        assertEquals(0, r2.added)
        assertEquals(0, r2.replaced)
        assertEquals(EntryState.ADDED, r2.entries.single().state)
        assertEquals(8L, r2.entries.single().storedSize)
    }

    @Test
    fun pathNormalisation() {
        assertEquals("a/b/c.txt", CpkPaths.normalize("\\a//b/./c.txt/"))
        assertEquals("dir/f", CpkPaths.join("dir/", "f"))
        assertEquals("f", CpkPaths.join("  ", "f"))
        for (bad in listOf("", "///", "a/../b", "..", "a\u0000b")) {
            try {
                CpkPaths.normalize(bad)
                fail("expected '$bad' to be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- compression -----------------------------------------------------------------------

    private sealed class Token {
        class Literal(val value: Int) : Token()
        class Ref(val distance: Int, val length: Int) : Token()
    }

    /** Test-only encoder: produces a CRILAYLA stream from explicit tokens (back to front). */
    private fun buildLayla(rawHeader: ByteArray, tokens: List<Token>, bodySize: Int): ByteArray {
        val bits = ArrayList<Int>()
        fun put(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add((value shr i) and 1)
        }
        for (t in tokens) {
            when (t) {
                is Token.Literal -> {
                    put(0, 1)
                    put(t.value, 8)
                }
                is Token.Ref -> {
                    put(1, 1)
                    put(t.distance - 3, 13)
                    var rest = t.length - 3
                    var finished = false
                    for (width in intArrayOf(2, 3, 5, 8)) {
                        val max = (1 shl width) - 1
                        if (rest < max) {
                            put(rest, width)
                            finished = true
                            break
                        }
                        put(max, width)
                        rest -= max
                    }
                    if (!finished) {
                        while (rest >= 255) {
                            put(255, 8)
                            rest -= 255
                        }
                        put(rest, 8)
                    }
                }
            }
        }
        while (bits.size % 8 != 0) bits.add(0)
        val stream = ByteArray(bits.size / 8) { i ->
            var v = 0
            for (k in 0 until 8) v = (v shl 1) or bits[i * 8 + k]
            v.toByte()
        }
        val total = 16 + stream.size + 0x100
        val out = ByteArray(total)
        "CRILAYLA".toByteArray(Charsets.US_ASCII).copyInto(out)
        fun le32(at: Int, v: Int) {
            for (i in 0 until 4) out[at + i] = (v shr (8 * i)).toByte()
        }
        le32(8, bodySize)
        le32(12, total - 0x110)
        for (i in stream.indices) out[16 + i] = stream[stream.size - 1 - i] // consumed from the end
        rawHeader.copyInto(out, total - 0x100)
        return out
    }

    @Test
    fun laylaDecodesLiteralsAndBackReferences() {
        val header = bytes(0x100, 5)
        // Output is produced from the back: 'c','b','a' literally, then copy 3 bytes from 3 ahead.
        val packed = buildLayla(
            header,
            listOf(Token.Literal('c'.code), Token.Literal('b'.code), Token.Literal('a'.code), Token.Ref(3, 3)),
            bodySize = 6,
        )
        val out = Layla.decompress(packed)
        assertArrayEquals(header + "abcabc".toByteArray(), out)
    }

    @Test
    fun laylaDecodesLongRunsUsingExtendedLengthCodes() {
        val header = bytes(0x100, 6)
        val n = 700
        val packed = buildLayla(
            header,
            listOf(Token.Literal(0x7A), Token.Literal(0x7A), Token.Literal(0x7A), Token.Ref(3, n - 3)),
            bodySize = n,
        )
        assertArrayEquals(header + ByteArray(n) { 0x7A }, Layla.decompress(packed))
    }

    @Test
    fun laylaRejectsCorruptInput() {
        try {
            Layla.decompress(ByteArray(64))
            fail()
        } catch (_: CpkFormatException) {
        }
        val good = buildLayla(bytes(0x100, 1), listOf(Token.Literal(1), Token.Literal(2), Token.Literal(3)), 3)
        good[8] = 0x7F // claim a much longer body than the stream holds
        try {
            Layla.decompress(good)
            fail()
        } catch (_: CpkFormatException) {
        }
    }

    @Test
    fun compressedEntriesStayCompressedAndExtractDecompressed() {
        val header = bytes(0x100, 7)
        val n = 500
        val packed = buildLayla(
            header,
            listOf(Token.Literal(0x41), Token.Literal(0x41), Token.Literal(0x41), Token.Ref(3, n - 3)),
            bodySize = n,
        )
        val expected = header + ByteArray(n) { 0x41 }
        val entry = WorkingEntry(
            "pack/z.bin", packed.size.toLong(), expected.size.toLong(), EntryState.ADDED,
            { ByteArrayInputStream(packed) },
        )

        val cpk = open(write(listOf(entry, working("plain.bin", bytes(20, 1)))))
        val e = cpk.entries.first { it.path == "pack/z.bin" }
        assertTrue(e.isCompressed)
        assertEquals(packed.size.toLong(), e.storedSize)
        assertEquals(expected.size.toLong(), e.extractSize)
        assertArrayEquals(packed, cpk.openStored(e).readBytes())
        assertArrayEquals(expected, cpk.read("pack/z.bin"))
        assertFalse(cpk.entries.first { it.path == "plain.bin" }.isCompressed)
    }

    // ---- robustness ------------------------------------------------------------------------

    @Test
    fun obfuscatedTablesAreDecrypted() {
        val data = write(listOf(working("a/b.bin", bytes(33, 4)), working("c.bin", bytes(5, 5))))
        // Obfuscate both tables the way CRI's tools do (symmetric XOR stream).
        for (chunkAt in intArrayOf(0, 0x800)) {
            val size = data.u64le(chunkAt + 8).toInt()
            UtfCrypto.xorInPlace(data, chunkAt + 0x10, size)
            assertFalse(UtfCrypto.isPlain(data, chunkAt + 0x10))
        }
        val cpk = open(data)
        assertEquals(listOf("a/b.bin", "c.bin"), cpk.entries.map { it.path })
        assertArrayEquals(bytes(33, 4), cpk.read("a/b.bin"))
    }

    @Test
    fun garbageIsRejectedWithAFormatError() {
        val inputs = listOf(
            ByteArray(10),
            ByteArray(4096),
            bytes(4096, 3),
            write(listOf(working("a", bytes(9, 1)))).copyOf(0x40), // truncated
        )
        for (input in inputs) {
            try {
                open(input)
                fail("expected CpkFormatException")
            } catch (_: CpkFormatException) {
            }
        }
    }

    @Test
    fun corruptedTocNeverCrashesWithAnUnexpectedException() {
        val good = write((0 until 20).map { working("d/f$it", bytes(it, it)) })
        val rnd = java.util.Random(1234)
        repeat(300) {
            val copy = good.copyOf()
            repeat(1 + rnd.nextInt(6)) { copy[0x800 + rnd.nextInt(0x300)] = rnd.nextInt(256).toByte() }
            try {
                open(copy)
            } catch (_: CpkFormatException) {
            }
        }
    }

    @Test
    fun writerDetectsStreamsThatLieAboutTheirSize() {
        val short = WorkingEntry("s.bin", 100, 100, EntryState.ADDED, { ByteArrayInputStream(ByteArray(40)) })
        try {
            write(listOf(short))
            fail()
        } catch (_: IOException) {
        }
        val long = WorkingEntry("l.bin", 10, 10, EntryState.ADDED, { ByteArrayInputStream(ByteArray(40)) })
        try {
            write(listOf(long))
            fail()
        } catch (_: IOException) {
        }
    }

    @Test
    fun writerHonoursCancellation() {
        try {
            CpkWriter.write(
                listOf(working("a", bytes(10, 1))),
                ByteArrayOutputStream(),
                isCancelled = { true },
            )
            fail()
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun progressReachesTheTotal() {
        var last = 0L
        var total = 0L
        CpkWriter.write(
            listOf(working("a", bytes(1000, 1)), working("b", bytes(2000, 2))),
            ByteArrayOutputStream(),
            progress = { done, all ->
                last = done
                total = all
            },
        )
        assertEquals(3000L, total)
        assertEquals(3000L, last)
    }
}
