package com.ky7ra.cpkeditor.cpk

import java.io.InputStream

object CpkPaths {
    /**
     * Normalises a user supplied path to the form stored in a CPK: forward slashes, no leading,
     * trailing or doubled separators. Throws [IllegalArgumentException] for unusable paths.
     */
    fun normalize(raw: String): String {
        val parts = raw.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        require(parts.isNotEmpty()) { "Path is empty" }
        require(parts.none { it == ".." }) { "'..' is not allowed in a path" }
        require(parts.none { part -> part.any { it.code < 0x20 } }) { "Path contains control characters" }
        return parts.joinToString("/")
    }

    fun join(dir: String, name: String): String = normalize(if (dir.isBlank()) name else "$dir/$name")
}

enum class EntryState { ORIGINAL, ADDED, REPLACED }

/**
 * A file in the archive being edited. [opener] yields the *stored* bytes; for entries that came
 * from a CPK these may be compressed, which is preserved when saving.
 */
class WorkingEntry(
    val path: String,
    val storedSize: Long,
    val extractSize: Long,
    val state: EntryState,
    val opener: () -> InputStream,
    /** Set only for untouched entries that still live in the opened archive. */
    val archiveEntry: CpkEntry? = null,
) {
    val name: String get() = path.substringAfterLast('/')
    val dir: String get() = path.substringBeforeLast('/', "")
    val isCompressed: Boolean get() = extractSize > storedSize
}

/** A file the user wants to put into the archive. */
class NewFile(val path: String, val size: Long, val opener: () -> InputStream)

class AddResult(val entries: List<WorkingEntry>, val added: Int, val replaced: Int)

object CpkEditing {
    fun fromArchive(archive: CpkArchive): List<WorkingEntry> = archive.entries.map { e ->
        WorkingEntry(
            path = e.path,
            storedSize = e.storedSize,
            extractSize = e.extractSize,
            state = EntryState.ORIGINAL,
            opener = { archive.openStored(e) },
            archiveEntry = e,
        )
    }

    /**
     * Adds [incoming] files. A file whose path already exists (compared case-insensitively, like
     * the game's file system) replaces that entry in place; others are appended.
     */
    fun addOrReplace(current: List<WorkingEntry>, incoming: List<NewFile>): AddResult {
        val result = ArrayList(current)
        val index = HashMap<String, Int>(result.size * 2)
        result.forEachIndexed { i, e -> index[e.path.lowercase()] = i }
        var added = 0
        var replaced = 0
        for (file in incoming) {
            val path = CpkPaths.normalize(file.path)
            val key = path.lowercase()
            val existing = index[key]
            if (existing != null) {
                val old = result[existing]
                val keepAdded = old.state == EntryState.ADDED
                // The entry keeps the spelling it already has in the archive.
                result[existing] = newEntry(old.path, file, if (keepAdded) EntryState.ADDED else EntryState.REPLACED)
                if (!keepAdded) replaced++
            } else {
                index[key] = result.size
                result.add(newEntry(path, file, EntryState.ADDED))
                added++
            }
        }
        return AddResult(result, added, replaced)
    }

    private fun newEntry(path: String, file: NewFile, state: EntryState) = WorkingEntry(
        path = path,
        storedSize = file.size,
        extractSize = file.size,
        state = state,
        opener = file.opener,
    )

    fun remove(current: List<WorkingEntry>, paths: Set<String>): List<WorkingEntry> =
        current.filter { it.path !in paths }
}
