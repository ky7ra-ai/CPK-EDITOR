package com.ky7ra.cpkeditor.cpk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CpkPatcherTest {

    private fun bytes(size: Int, seed: Int) = ByteArray(size) { ((it * 17 + seed) and 0xFF).toByte() }

    private fun working(path: String, data: ByteArray) =
        WorkingEntry(path, data.size.toLong(), data.size.toLong(), EntryState.ADDED, { ByteArrayInputStream(data) })

    private val a = bytes(3000, 1)
    private val b = bytes(100, 2)
    private val c = bytes(5000, 3)

    /** A CPK from our writer plus 0x1000 bytes of trailing "tables" that must survive patching. */
    private fun baseCpk(): Pair<ByteArray, ByteArray> {
        val out = ByteArrayOutputStream()
        CpkWriter.write(
            listOf(working("data/a.bin", a), working("data/b.bin", b), working("c.bin", c)),
            out,
            sorted = true,
        )
        val trailer = bytes(0x1000, 99)
        return (out.toByteArray() + trailer) to trailer
    }

    private fun open(data: ByteArray) = CpkArchive.open(ByteArraySource(data))

    private fun CpkArchive.read(path: String): ByteArray {
        val e = entries.first { it.path == path }
        return ByteArrayOutputStream().also { extract(e, it) }.toByteArray()
    }

    private fun replace(archive: CpkArchive, vararg changes: Pair<String, ByteArray>): List<WorkingEntry> {
        val files = changes.map { (p, d) -> NewFile(p, d.size.toLong()) { ByteArrayInputStream(d) } }
        return CpkEditing.addOrReplace(CpkEditing.fromArchive(archive), files).entries
    }

    private fun patch(archive: CpkArchive, entries: List<WorkingEntry>): ByteArray {
        val plan = CpkPatcher.plan(archive, entries)
        assertNotNull("expected a patch plan", plan)
        val out = ByteArrayOutputStream()
        var last = 0L
        CpkPatcher.write(plan!!, out, progress = { done, _ -> last = done })
        assertEquals(plan.newLength, out.size().toLong())
        assertEquals(plan.newLength, last)
        return out.toByteArray()
    }

    @Test
    fun noChangesGivesAByteIdenticalCopy() {
        val (data, _) = baseCpk()
        val archive = open(data)
        assertArrayEquals(data, patch(archive, CpkEditing.fromArchive(archive)))
    }

    @Test
    fun smallerFileIsWrittenIntoItsOldSlotAndNothingElseMoves() {
        val (data, trailer) = baseCpk()
        val archive = open(data)
        val newB = bytes(60, 7)
        val out = patch(archive, replace(archive, "DATA/b.bin" to newB))

        assertEquals("size must not change", data.size, out.size)
        val patched = open(out)
        assertEquals(archive.entries.map { it.path }, patched.entries.map { it.path })
        assertArrayEquals(newB, patched.read("data/b.bin"))
        assertEquals(60L, patched.entries[1].storedSize)
        assertEquals(60L, patched.entries[1].extractSize)
        assertEquals(archive.entries[1].offset, patched.entries[1].offset)
        assertArrayEquals(a, patched.read("data/a.bin"))
        assertArrayEquals(c, patched.read("c.bin"))

        // header area untouched, trailing tables untouched, old slot padded with zeros
        assertArrayEquals(data.copyOf(0x800), out.copyOf(0x800))
        assertArrayEquals(trailer, out.copyOfRange(out.size - trailer.size, out.size))
        val slot = archive.entries[1].offset.toInt()
        assertTrue((60 until 100).all { out[slot + it] == 0.toByte() })
        assertTrue(patched.info.sorted)
    }

    @Test
    fun biggerFileIsAppendedAfterTheOriginalEnd() {
        val (data, trailer) = baseCpk()
        val archive = open(data)
        val newA = bytes(9000, 5)
        val out = patch(archive, replace(archive, "data/a.bin" to newA))

        val expectedStart = alignUp(data.size.toLong(), 0x800)
        assertEquals(expectedStart + alignUp(9000, 0x800), out.size.toLong())
        val patched = open(out)
        assertEquals(expectedStart, patched.entries[0].offset)
        assertArrayEquals(newA, patched.read("data/a.bin"))
        assertArrayEquals(b, patched.read("data/b.bin"))
        assertArrayEquals(c, patched.read("c.bin"))
        // the original bytes (including trailing tables) are still where they were
        assertArrayEquals(
            trailer,
            out.copyOfRange(data.size - trailer.size, data.size),
        )
        assertArrayEquals(
            data.copyOfRange(archive.entries[1].offset.toInt(), data.size),
            out.copyOfRange(archive.entries[1].offset.toInt(), data.size),
        )
    }

    @Test
    fun mixedReplacementsInOneSave() {
        val (data, _) = baseCpk()
        val archive = open(data)
        val newB = bytes(100, 11) // exactly the old size
        val newC = bytes(20000, 12) // appended
        val out = patch(archive, replace(archive, "data/b.bin" to newB, "c.bin" to newC))
        val patched = open(out)
        assertArrayEquals(newB, patched.read("data/b.bin"))
        assertArrayEquals(newC, patched.read("c.bin"))
        assertArrayEquals(a, patched.read("data/a.bin"))
        assertEquals(archive.entries[1].offset, patched.entries[1].offset)
        assertTrue(patched.entries[2].offset >= data.size)
    }

    @Test
    fun filesSharingDataAreNeverOverwrittenInPlace() {
        val (data, _) = baseCpk()
        // Make entry 1 point at entry 0's data (as deduplicated CPKs do).
        val first = open(data)
        val toc = first.tocData
        val shared = data.copyOf()
        val tocStart = toc.position.toInt() + 0x10
        fun poke(row: Int, column: String, value: Long) {
            val slot = toc.table.rowSlot(row, column)!!
            for (k in 0 until slot.size) shared[tocStart + slot.pos + k] = (value shr (8 * (slot.size - 1 - k))).toByte()
        }
        poke(1, "FileOffset", first.entries[0].offset - first.dataBase)
        poke(1, "FileSize", first.entries[0].storedSize)
        poke(1, "ExtractSize", first.entries[0].storedSize)

        val archive = open(shared)
        assertArrayEquals(a, archive.read("data/b.bin"))
        val newA = bytes(10, 21) // would fit in place if the data were not shared
        val out = patch(archive, replace(archive, "data/a.bin" to newA))
        val patched = open(out)
        assertArrayEquals(newA, patched.read("data/a.bin"))
        assertArrayEquals("the file sharing the old data must be unaffected", a, patched.read("data/b.bin"))
        assertTrue(patched.entries[0].offset >= data.size)
    }

    @Test
    fun obfuscatedTablesStayObfuscated() {
        val (data, _) = baseCpk()
        for (chunkAt in intArrayOf(0, 0x800)) {
            UtfCrypto.xorInPlace(data, chunkAt + 0x10, data.u64le(chunkAt + 8).toInt())
        }
        val archive = open(data)
        val out = patch(archive, replace(archive, "data/b.bin" to bytes(30, 4), "data/a.bin" to bytes(7000, 5)))
        assertFalse(UtfCrypto.isPlain(out, 0x810))
        assertFalse(UtfCrypto.isPlain(out, 0x10))
        val patched = open(out)
        assertArrayEquals(bytes(30, 4), patched.read("data/b.bin"))
        assertArrayEquals(bytes(7000, 5), patched.read("data/a.bin"))
        assertArrayEquals(c, patched.read("c.bin"))
    }

    @Test
    fun patchingIsNotOfferedWhenFilesWereAddedOrRemoved() {
        val (data, _) = baseCpk()
        val archive = open(data)
        val original = CpkEditing.fromArchive(archive)
        assertNull(CpkPatcher.plan(archive, CpkEditing.remove(original, setOf("c.bin"))))
        val added = CpkEditing.addOrReplace(original, listOf(NewFile("new.bin", 3) { ByteArrayInputStream(ByteArray(3)) }))
        assertNull(CpkPatcher.plan(archive, added.entries))
        assertNotNull(CpkPatcher.plan(archive, original))
    }

    @Test
    fun streamsThatLieAboutTheirSizeAreRejected() {
        val (data, _) = baseCpk()
        val archive = open(data)
        val liar = NewFile("data/b.bin", 50) { ByteArrayInputStream(ByteArray(20)) }
        val entries = CpkEditing.addOrReplace(CpkEditing.fromArchive(archive), listOf(liar)).entries
        try {
            patch(archive, entries)
            fail()
        } catch (_: IOException) {
        }
    }
}
