package com.ky7ra.cpkeditor.cpk

/** A sub-folder shown in a folder listing. */
class BrowseFolder(
    val name: String,
    /** Full path of the folder inside the CPK (no trailing slash). */
    val path: String,
    /** Number of direct children (files and folders). */
    val itemCount: Int,
    /** Paths of every file below this folder, at any depth. */
    val filePaths: List<String>,
)

class BrowseListing(val folders: List<BrowseFolder>, val files: List<WorkingEntry>)

/** Folder-style view over the flat list of CPK entries. Pure logic, no Android dependencies. */
object CpkBrowse {
    /** What lives directly inside [dir] ("" is the root): folders first, then files, both sorted by name. */
    fun list(entries: List<WorkingEntry>, dir: String): BrowseListing {
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        val folderFiles = LinkedHashMap<String, MutableList<String>>()
        val folderChildren = HashMap<String, MutableSet<String>>()
        val files = ArrayList<WorkingEntry>()

        for (e in entries) {
            if (!e.path.startsWith(prefix)) continue
            val rest = e.path.substring(prefix.length)
            val slash = rest.indexOf('/')
            if (slash < 0) {
                files.add(e)
                continue
            }
            val name = rest.substring(0, slash)
            folderFiles.getOrPut(name) { ArrayList() }.add(e.path)
            folderChildren.getOrPut(name) { HashSet() }.add(rest.substring(slash + 1).substringBefore('/'))
        }

        val folders = folderFiles
            .map { (name, paths) -> BrowseFolder(name, prefix + name, folderChildren[name]?.size ?: 0, paths) }
            .sortedBy { it.name.lowercase() }
        return BrowseListing(folders, files.sortedBy { it.name.lowercase() })
    }

    /** Entries whose full path contains [query] (case-insensitive); everything for a blank query. */
    fun search(entries: List<WorkingEntry>, query: String): List<WorkingEntry> {
        val q = query.trim().lowercase()
        return if (q.isEmpty()) entries else entries.filter { it.path.lowercase().contains(q) }
    }

    /** [dir] itself if it still contains files, otherwise its nearest ancestor that does ("" at worst). */
    fun existingDir(entries: List<WorkingEntry>, dir: String): String {
        var d = dir
        while (d.isNotEmpty() && entries.none { it.path.startsWith("$d/") }) d = d.substringBeforeLast('/', "")
        return d
    }
}
