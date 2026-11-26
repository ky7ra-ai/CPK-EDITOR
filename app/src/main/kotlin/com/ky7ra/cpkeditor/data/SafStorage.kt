package com.ky7ra.cpkeditor.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import com.ky7ra.cpkeditor.cpk.ChannelSource
import com.ky7ra.cpkeditor.cpk.NewFile
import com.ky7ra.cpkeditor.cpk.RandomAccessSource
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/** All file access goes through the Storage Access Framework, so no storage permission is needed. */
class SafStorage(private val resolver: ContentResolver) {

    class DocInfo(val name: String, val size: Long)

    /** Display name and size of a document. [DocInfo.size] is -1 when the provider does not say. */
    fun info(uri: Uri): DocInfo {
        var name: String? = null
        var size = -1L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        } catch (_: Exception) {
            // some providers refuse queries; fall through to defaults
        }
        return DocInfo(name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file", size)
    }

    /** Keeps read access to [uri] alive across the process, when the provider allows it. */
    fun keepReadAccess(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // not every URI is persistable (e.g. ones handed over via "Open with"); that is fine
        }
    }

    /** Opens [uri] for positional reads. Fails with [IOException] when the provider is not seekable. */
    fun openSource(uri: Uri): RandomAccessSource {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException("Cannot open $uri")
        try {
            val stream = FileInputStream(pfd.fileDescriptor)
            val source = ChannelSource(stream.channel, Closeable {
                try {
                    stream.close()
                } finally {
                    pfd.close()
                }
            })
            if (source.length <= 0L) throw IOException("Source is not seekable")
            return source
        } catch (t: Throwable) {
            runCatching { pfd.close() }
            throw t
        }
    }

    /** Copies [uri] into [target]; used for providers that cannot be read at random positions. */
    fun copyTo(uri: Uri, target: File) {
        (resolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot open $uri")).use { input ->
            target.outputStream().use { input.copyTo(it, 256 * 1024) }
        }
    }

    /** A file to be added. Its size is measured by reading when the provider does not report one. */
    fun newFile(uri: Uri, path: String): NewFile {
        val info = info(uri)
        var size = info.size
        if (size < 0) size = measure(uri)
        return NewFile(path, size) { open(uri) }
    }

    private fun measure(uri: Uri): Long {
        var total = 0L
        val buf = ByteArray(64 * 1024)
        open(uri).use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
            }
        }
        return total
    }

    private fun open(uri: Uri) = resolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot open $uri")

    /** Lists every file below the picked folder, with paths relative to it, prefixed by [prefix]. */
    fun walkTree(treeUri: Uri, prefix: String): List<NewFile> {
        val out = ArrayList<NewFile>()
        walk(treeUri, DocumentsContract.getTreeDocumentId(treeUri), prefix, out)
        return out
    }

    private fun walk(treeUri: Uri, dirId: String, prefix: String, out: MutableList<NewFile>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        val subDirs = ArrayList<Pair<String, String>>()
        resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (c.getString(2) == Document.MIME_TYPE_DIR) {
                    subDirs.add(id to name)
                } else {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    val size = if (c.isNull(3)) measure(docUri) else c.getLong(3)
                    out.add(NewFile(prefix + name, size) { open(docUri) })
                }
            }
        }
        for ((id, name) in subDirs) walk(treeUri, id, "$prefix$name/", out)
    }

    fun openOutput(uri: Uri): OutputStream =
        resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write to $uri")

    fun delete(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (_: Exception) {
            // best effort clean-up of a half written file
        }
    }

    fun treeWriter(treeUri: Uri) = TreeWriter(resolver, treeUri)
}

private class Child(val uri: Uri, val isDir: Boolean)

/** Creates files (and the folders above them) inside a picked folder, reusing what exists. */
class TreeWriter(private val resolver: ContentResolver, private val treeUri: Uri) {
    private val root = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )
    private val dirs = HashMap<String, Uri>()
    private val listings = HashMap<Uri, MutableMap<String, Child>>()

    /** Opens an output stream for [path] (relative, '/' separated), replacing an existing file. */
    fun open(path: String): OutputStream {
        val parent = ensureDir(path.substringBeforeLast('/', ""))
        val name = path.substringAfterLast('/')
        val listing = listing(parent)
        val existing = listing[name]
        val target = if (existing != null && !existing.isDir) {
            existing.uri
        } else {
            val created = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
                ?: throw IOException("Cannot create $path")
            listing[name] = Child(created, false)
            created
        }
        return resolver.openOutputStream(target, "wt") ?: throw IOException("Cannot write $path")
    }

    private fun ensureDir(dirPath: String): Uri {
        if (dirPath.isEmpty()) return root
        dirs[dirPath]?.let { return it }
        val parent = ensureDir(dirPath.substringBeforeLast('/', ""))
        val name = dirPath.substringAfterLast('/')
        val listing = listing(parent)
        val existing = listing[name]
        val uri = if (existing != null && existing.isDir) {
            existing.uri
        } else {
            DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, name)
                ?: throw IOException("Cannot create folder $dirPath")
        }
        listing[name] = Child(uri, true)
        dirs[dirPath] = uri
        return uri
    }

    private fun listing(dirUri: Uri): MutableMap<String, Child> = listings.getOrPut(dirUri) {
        val map = HashMap<String, Child>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(dirUri))
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                map[name] = Child(
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    c.getString(2) == Document.MIME_TYPE_DIR,
                )
            }
        }
        map
    }
}
