package com.ky7ra.cpkeditor.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ky7ra.cpkeditor.R
import com.ky7ra.cpkeditor.cpk.ChannelSource
import com.ky7ra.cpkeditor.cpk.CpkBrowse
import com.ky7ra.cpkeditor.cpk.CpkArchive
import com.ky7ra.cpkeditor.cpk.CpkEditing
import com.ky7ra.cpkeditor.cpk.CpkInfo
import com.ky7ra.cpkeditor.cpk.CpkFormatException
import com.ky7ra.cpkeditor.cpk.CpkPaths
import com.ky7ra.cpkeditor.cpk.CpkPatcher
import com.ky7ra.cpkeditor.cpk.CpkWriter
import com.ky7ra.cpkeditor.cpk.EntryState
import com.ky7ra.cpkeditor.cpk.NewFile
import com.ky7ra.cpkeditor.cpk.WorkingEntry
import com.ky7ra.cpkeditor.data.SafStorage
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface PendingAdd {
    class Files(val uris: List<Uri>) : PendingAdd
    class Folder(val uri: Uri) : PendingAdd
}

/** State of the running task, shown in the progress card. */
data class Progress(
    val label: String,
    val fraction: Float? = null,
    /** File being processed right now, when known. */
    val detail: String? = null,
    val done: Long = 0L,
    val total: Long = 0L,
    /** Estimated seconds left; null until there is enough data for a sensible guess. */
    val etaSeconds: Long? = null,
)

data class Notice(val id: Long, val text: String)

data class UiState(
    val hasProject: Boolean = false,
    val title: String = "",
    val entries: List<WorkingEntry> = emptyList(),
    val dirty: Boolean = false,
    val query: String = "",
    /** Folder currently shown in the editor ('/' separated, \"\" is the root). */
    val dir: String = "",
    val selected: Set<String> = emptySet(),
    val progress: Progress? = null,
    val pendingAdd: PendingAdd? = null,
    val notice: Notice? = null,
    /** Header facts of the opened archive; null for a new, empty one. */
    val cpkInfo: CpkInfo? = null,
)

class CpkViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = SafStorage(application.contentResolver)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var archive: CpkArchive? = null
    private var sourceUri: Uri? = null
    private var cacheFile: File? = null
    private var job: Job? = null
    private var noticeCounter = 0L
    private var lastProgressAt = 0L
    private var taskStartNanos = 0L

    private class ExtractRequest(val paths: Set<String>?)

    private var extractRequest: ExtractRequest? = null
    private var replacePath: String? = null

    // ---- small helpers ---------------------------------------------------------------------

    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private fun notify(text: String) {
        _state.update { it.copy(notice = Notice(++noticeCounter, text)) }
    }

    fun consumeNotice(id: Long) {
        _state.update { if (it.notice?.id == id) it.copy(notice = null) else it }
    }

    private fun reportProgress(done: Long, total: Long) {
        val now = System.nanoTime()
        if (now - lastProgressAt < 80_000_000L && done < total) return
        lastProgressAt = now
        val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
        val elapsed = (now - taskStartNanos) / 1_000_000_000.0
        val eta = if (fraction != null && fraction > 0.01f && done > 0 && elapsed >= 1.5) {
            ((total - done) * elapsed / done).toLong().coerceAtLeast(0L)
        } else {
            null
        }
        _state.update { s ->
            s.progress?.let { s.copy(progress = it.copy(fraction = fraction, done = done, total = total, etaSeconds = eta)) } ?: s
        }
    }

    /** Tells the progress card which file is being worked on. */
    private fun reportEntry(path: String) {
        _state.update { s -> s.progress?.let { s.copy(progress = it.copy(detail = path)) } ?: s }
    }

    private fun launchTask(@StringRes label: Int, block: suspend CoroutineScope.() -> Unit) {
        if (_state.value.progress != null) return
        taskStartNanos = System.nanoTime()
        _state.update { it.copy(progress = Progress(str(label))) }
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                notify(str(R.string.msg_cancelled))
            } catch (e: CpkFormatException) {
                notify(str(R.string.err_not_cpk, e.message ?: ""))
            } catch (e: Exception) {
                notify(str(R.string.err_generic, e.message ?: e.javaClass.simpleName))
            } finally {
                _state.update { it.copy(progress = null) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    // ---- project lifecycle -----------------------------------------------------------------

    fun openCpk(uri: Uri) {
        launchTask(R.string.progress_opening) {
            storage.keepReadAccess(uri)
            val name = storage.info(uri).name
            val (opened, cache) = openArchive(uri)
            val oldArchive = archive
            val oldCache = cacheFile
            archive = opened
            cacheFile = cache
            sourceUri = uri
            oldArchive?.close()
            oldCache?.delete()
            val entries = CpkEditing.fromArchive(opened)
            _state.update { UiState(hasProject = true, title = name, entries = entries, cpkInfo = opened.info, progress = it.progress) }
            notify(str(R.string.msg_opened, name, entries.size))
        }
    }

    /** Opens [uri] with random access, falling back to a cached copy for providers that cannot seek. */
    private fun openArchive(uri: Uri): Pair<CpkArchive, File?> {
        return try {
            CpkArchive.open(storage.openSource(uri)) to null
        } catch (e: CpkFormatException) {
            throw e
        } catch (e: IOException) {
            val cache = File(getApplication<Application>().cacheDir, "open-${System.nanoTime()}.cpk")
            try {
                storage.copyTo(uri, cache)
                CpkArchive.open(ChannelSource.forFile(cache)) to cache
            } catch (t: Throwable) {
                cache.delete()
                throw t
            }
        }
    }

    fun newProject() {
        if (_state.value.progress != null) return
        releaseProject()
        _state.value = UiState(hasProject = true, title = str(R.string.untitled))
    }

    fun closeProject() {
        if (_state.value.progress != null) return
        releaseProject()
        _state.value = UiState()
    }

    private fun releaseProject() {
        archive?.close()
        archive = null
        cacheFile?.delete()
        cacheFile = null
        sourceUri = null
    }

    override fun onCleared() {
        job?.cancel()
        releaseProject()
    }

    /** Name offered in the "save as" picker; never the name of the file that is currently open. */
    fun suggestedSaveName(): String {
        if (sourceUri == null) return str(R.string.default_cpk_name)
        val title = _state.value.title
        val base = if (title.endsWith(".cpk", ignoreCase = true)) title.dropLast(4) else title
        return "${base}_mod.cpk"
    }

    // ---- browsing --------------------------------------------------------------------------

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun toggleSelected(path: String) {
        _state.update {
            val selected = it.selected.toMutableSet()
            if (!selected.add(path)) selected.remove(path)
            it.copy(selected = selected)
        }
    }

    fun selectAll(paths: Collection<String>) {
        _state.update { it.copy(selected = paths.toSet()) }
    }

    fun setSelection(paths: Set<String>) {
        _state.update { it.copy(selected = paths) }
    }

    fun enterDir(dir: String) {
        _state.update { it.copy(dir = dir, query = "") }
    }

    fun goUp() {
        _state.update { it.copy(dir = it.dir.substringBeforeLast('/', "")) }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    // ---- adding / replacing / deleting -----------------------------------------------------

    fun requestAddFiles(uris: List<Uri>) {
        _state.update { it.copy(pendingAdd = PendingAdd.Files(uris)) }
    }

    fun requestAddFolder(uri: Uri) {
        _state.update { it.copy(pendingAdd = PendingAdd.Folder(uri)) }
    }

    fun cancelAdd() {
        _state.update { it.copy(pendingAdd = null) }
    }

    fun confirmAdd(targetDir: String) {
        val request = _state.value.pendingAdd ?: return
        val dir = if (targetDir.isBlank()) {
            ""
        } else {
            try {
                CpkPaths.normalize(targetDir)
            } catch (e: IllegalArgumentException) {
                notify(str(R.string.err_invalid_path))
                return
            }
        }
        _state.update { it.copy(pendingAdd = null) }
        launchTask(R.string.progress_adding) {
            val prefix = if (dir.isEmpty()) "" else "$dir/"
            val incoming: List<NewFile> = when (request) {
                is PendingAdd.Files -> request.uris.map { uri ->
                    storage.keepReadAccess(uri)
                    storage.newFile(uri, prefix + storage.info(uri).name)
                }
                is PendingAdd.Folder -> storage.walkTree(request.uri, prefix)
            }
            if (incoming.isEmpty()) {
                notify(str(R.string.msg_nothing_added))
                return@launchTask
            }
            val result = CpkEditing.addOrReplace(_state.value.entries, incoming)
            _state.update { it.copy(entries = result.entries, dirty = true, selected = emptySet()) }
            notify(str(R.string.msg_added, result.added, result.replaced))
        }
    }

    /** Remembers which entry is being replaced while the system file picker is open. */
    fun prepareReplace(path: String) {
        replacePath = path
    }

    /** Puts the picked file's content into the entry chosen with [prepareReplace], keeping its path. */
    fun replaceEntry(uri: Uri) {
        val path = replacePath ?: return
        replacePath = null
        launchTask(R.string.progress_adding) {
            storage.keepReadAccess(uri)
            val result = CpkEditing.addOrReplace(_state.value.entries, listOf(storage.newFile(uri, path)))
            _state.update { it.copy(entries = result.entries, dirty = true, selected = emptySet()) }
            notify(str(R.string.msg_replaced, path.substringAfterLast('/')))
        }
    }

    fun deleteSelected() {
        val s = _state.value
        if (s.selected.isEmpty() || s.progress != null) return
        val remaining = CpkEditing.remove(s.entries, s.selected)
        val removed = s.entries.size - remaining.size
        _state.update {
            it.copy(
                entries = remaining,
                selected = emptySet(),
                dirty = true,
                dir = CpkBrowse.existingDir(remaining, it.dir),
            )
        }
        notify(str(R.string.msg_removed, removed))
    }

    // ---- extracting ------------------------------------------------------------------------

    /** Remembers what to extract while the system file picker is open. [paths] null means everything. */
    fun prepareExtract(paths: Set<String>?) {
        extractRequest = ExtractRequest(paths)
    }

    private fun extractTargets(): List<WorkingEntry> {
        val paths = extractRequest?.paths
        val entries = _state.value.entries
        return if (paths == null) entries else entries.filter { it.path in paths }
    }

    private fun writeEntry(entry: WorkingEntry, out: OutputStream) {
        val source = archive
        val original = entry.archiveEntry
        if (source != null && original != null) {
            source.extract(original, out)
        } else {
            entry.opener().use { it.copyTo(out, 256 * 1024) }
        }
    }

    private class CountingOutputStream(
        private val out: OutputStream,
        private val onBytes: (Int) -> Unit,
    ) : OutputStream() {
        override fun write(b: Int) {
            out.write(b)
            onBytes(1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            onBytes(len)
        }

        override fun flush() = out.flush()

        override fun close() = out.close()
    }

    fun extractToTree(treeUri: Uri) {
        val targets = extractTargets()
        if (targets.isEmpty()) return
        launchTask(R.string.progress_extracting) {
            val writer = storage.treeWriter(treeUri)
            val total = targets.sumOf { it.extractSize }
            var done = 0L
            for (target in targets) {
                if (!isActive) throw CancellationException()
                reportEntry(target.path)
                writer.open(target.path).use { raw ->
                    val counting = CountingOutputStream(raw) { n ->
                        done += n
                        reportProgress(done, total)
                    }
                    writeEntry(target, counting)
                    counting.flush()
                }
            }
            notify(str(R.string.msg_extracted, targets.size))
        }
    }

    fun extractSingle(uri: Uri) {
        val target = extractTargets().firstOrNull() ?: return
        launchTask(R.string.progress_extracting) {
            reportEntry(target.path)
            try {
                storage.openOutput(uri).use { raw ->
                    var done = 0L
                    val counting = CountingOutputStream(raw) { n ->
                        done += n
                        reportProgress(done, target.extractSize)
                    }
                    writeEntry(target, counting)
                    counting.flush()
                }
            } catch (t: Throwable) {
                storage.delete(uri)
                throw t
            }
            notify(str(R.string.msg_extracted, 1))
        }
    }

    // ---- saving ----------------------------------------------------------------------------

    fun saveAs(uri: Uri) {
        val s = _state.value
        if (s.progress != null) return
        if (uri == sourceUri) {
            notify(str(R.string.err_same_file))
            return
        }
        val entries = s.entries
        // When files were only replaced, patch the original CPK so its structure stays untouched.
        val source = archive
        val plan = if (source != null) CpkPatcher.plan(source, entries) else null
        // The "Sorted" flag is only trustworthy while the original order is untouched.
        val sorted = source?.info?.sorted == true && entries.none { it.state == EntryState.ADDED }
        launchTask(R.string.progress_saving) {
            try {
                storage.openOutput(uri).buffered(1 shl 20).use { out ->
                    if (plan != null) {
                        CpkPatcher.write(
                            plan,
                            out,
                            progress = { done, total -> reportProgress(done, total) },
                            isCancelled = { !isActive },
                            onEntry = ::reportEntry,
                        )
                    } else {
                        CpkWriter.write(
                            entries,
                            out,
                            sorted,
                            progress = { done, total -> reportProgress(done, total) },
                            isCancelled = { !isActive },
                            onEntry = ::reportEntry,
                        )
                    }
                }
            } catch (t: Throwable) {
                storage.delete(uri)
                throw t
            }
            _state.update { it.copy(dirty = false) }
            val name = storage.info(uri).name
            notify(str(if (plan != null) R.string.msg_saved_patch else R.string.msg_saved, name))
        }
    }
}
