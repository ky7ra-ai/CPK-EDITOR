package com.ky7ra.cpkeditor.cpk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class CpkBrowseTest {

    private fun w(path: String, size: Int = 1) =
        WorkingEntry(path, size.toLong(), size.toLong(), EntryState.ADDED, { ByteArrayInputStream(ByteArray(size)) })

    private val entries = listOf(
        w("data/spc/a.bin"),
        w("data/spc/b.bin"),
        w("data/x.bin"),
        w("data/sub/deep/c.bin"),
        w("root.txt"),
        w("System/z.bin"),
    )

    @Test
    fun rootListsFoldersFirstThenFiles() {
        val l = CpkBrowse.list(entries, "")
        assertEquals(listOf("data", "System"), l.folders.map { it.name })
        assertEquals(listOf("root.txt"), l.files.map { it.path })
        val data = l.folders.first()
        assertEquals("data", data.path)
        assertEquals(3, data.itemCount) // spc, x.bin, sub
        assertEquals(4, data.filePaths.size)
    }

    @Test
    fun nestedFolderListing() {
        val l = CpkBrowse.list(entries, "data")
        assertEquals(listOf("spc", "sub"), l.folders.map { it.name })
        assertEquals(listOf("data/spc", "data/sub"), l.folders.map { it.path })
        assertEquals(listOf(2, 1), l.folders.map { it.itemCount })
        assertEquals(listOf("data/x.bin"), l.files.map { it.path })
        assertEquals(listOf("data/spc/a.bin", "data/spc/b.bin"), l.folders[0].filePaths)
    }

    @Test
    fun unknownFolderIsEmpty() {
        val l = CpkBrowse.list(entries, "nope")
        assertEquals(0, l.folders.size + l.files.size)
    }

    @Test
    fun searchMatchesFullPathCaseInsensitively() {
        assertEquals(listOf("System/z.bin"), CpkBrowse.search(entries, " system ").map { it.path })
        assertEquals(entries.size, CpkBrowse.search(entries, "  ").size)
        assertEquals(2, CpkBrowse.search(entries, "SPC/").size)
    }

    @Test
    fun existingDirFallsBackToNearestAncestor() {
        assertEquals("data/sub/deep", CpkBrowse.existingDir(entries, "data/sub/deep"))
        val without = entries.filter { it.path != "data/sub/deep/c.bin" }
        assertEquals("data", CpkBrowse.existingDir(without, "data/sub/deep"))
        assertEquals("", CpkBrowse.existingDir(emptyList(), "data"))
        assertEquals("", CpkBrowse.existingDir(entries, ""))
    }

    @Test
    fun writerReportsEntriesInOrder() {
        val seen = ArrayList<String>()
        CpkWriter.write(entries, ByteArrayOutputStream(), onEntry = { seen.add(it) })
        assertEquals(entries.map { it.path }, seen)
    }

    @Test
    fun patcherReportsReplacedAndAppendedEntries() {
        val out = ByteArrayOutputStream()
        CpkWriter.write(listOf(w("a/small.bin", 100), w("a/big.bin", 100), w("keep.bin", 50)), out)
        val archive = CpkArchive.open(ByteArraySource(out.toByteArray()))

        fun file(path: String, size: Int) = NewFile(path, size.toLong()) { ByteArrayInputStream(ByteArray(size)) }
        val edited = CpkEditing.addOrReplace(
            CpkEditing.fromArchive(archive),
            listOf(file("a/small.bin", 40), file("a/big.bin", 5000)),
        ).entries

        val plan = CpkPatcher.plan(archive, edited)!!
        val seen = ArrayList<String>()
        CpkPatcher.write(plan, ByteArrayOutputStream(), onEntry = { seen.add(it) })
        assertEquals(setOf("a/small.bin", "a/big.bin"), seen.toSet())
        assertEquals(2, seen.size)
    }
}
