package com.ky7ra.cpkeditor.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ky7ra.cpkeditor.R
import com.ky7ra.cpkeditor.cpk.EntryState
import com.ky7ra.cpkeditor.cpk.WorkingEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpkEditorApp(vm: CpkViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    val busy = state.progress != null

    LaunchedEffect(state.notice?.id) {
        val notice = state.notice
        if (notice != null) {
            snackbar.showSnackbar(notice.text)
            vm.consumeNotice(notice.id)
        }
    }

    val openCpk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.openCpk(uri)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.requestAddFiles(uris)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.requestAddFolder(uri)
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.saveAs(uri)
    }
    val extractTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.extractToTree(uri)
    }
    val extractFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.extractSingle(uri)
    }

    val replaceFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.replaceEntry(uri)
    }
    val onReplace: (String) -> Unit = { path ->
        vm.prepareReplace(path)
        replaceFile.launch(arrayOf("*/*"))
    }

    val onExtract: (Set<String>?) -> Unit = { paths ->
        vm.prepareExtract(paths)
        if (paths != null && paths.size == 1) {
            extractFile.launch(paths.first().substringAfterLast('/'))
        } else {
            extractTree.launch(null)
        }
    }
    val closeOrConfirm: () -> Unit = {
        if (state.dirty) {
            confirmClose = true
        } else {
            vm.closeProject()
        }
    }

    BackHandler(enabled = state.hasProject && !busy) {
        if (state.selected.isNotEmpty()) vm.clearSelection() else closeOrConfirm()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = if (state.hasProject) state.title else stringResource(R.string.app_name)
                    Text(
                        text = if (state.dirty) "$title •" else title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (state.hasProject) {
                        IconButton(onClick = closeOrConfirm, enabled = !busy) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_close),
                            )
                        }
                    }
                },
                actions = {
                    if (state.hasProject) {
                        TextButton(onClick = { saveAs.launch(vm.suggestedSaveName()) }, enabled = !busy) {
                            Text(stringResource(R.string.action_save_as))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (!state.hasProject) {
                HomeScreen(
                    onOpen = { openCpk.launch(arrayOf("*/*")) },
                    onNew = vm::newProject,
                )
            } else {
                ArchiveScreen(
                    state = state,
                    vm = vm,
                    onAddFiles = { pickFiles.launch(arrayOf("*/*")) },
                    onAddFolder = { pickFolder.launch(null) },
                    onExtract = onExtract,
                    onReplace = onReplace,
                    onDelete = { confirmDelete = true },
                )
            }
        }
    }

    state.pendingAdd?.let { pending ->
        AddDialog(pending = pending, onConfirm = vm::confirmAdd, onDismiss = vm::cancelAdd)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dialog_delete_title, state.selected.size)) },
            text = { Text(stringResource(R.string.dialog_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteSelected()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(R.string.dialog_discard_title)) },
            text = { Text(stringResource(R.string.dialog_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    vm.closeProject()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    state.progress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(progress.label) },
            text = {
                val fraction = progress.fraction
                if (fraction == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = vm::cancel) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun HomeScreen(onOpen: () -> Unit, onNew: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_open))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_new))
        }
    }
}

@Composable
private fun ArchiveScreen(
    state: UiState,
    vm: CpkViewModel,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onExtract: (Set<String>?) -> Unit,
    onReplace: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val visible = remember(state.entries, state.query) {
        val q = state.query.trim().lowercase()
        if (q.isEmpty()) state.entries else state.entries.filter { it.path.lowercase().contains(q) }
    }
    val totalSize = remember(state.entries) { state.entries.sumOf { it.extractSize } }
    val added = remember(state.entries) { state.entries.count { it.state == EntryState.ADDED } }
    val replaced = remember(state.entries) { state.entries.count { it.state == EntryState.REPLACED } }
    val busy = state.progress != null

    Column(Modifier.fillMaxSize()) {
        var summary = stringResource(
            R.string.summary_files,
            state.entries.size,
            Formatter.formatShortFileSize(context, totalSize),
        )
        if (added + replaced > 0) summary += "  ·  " + stringResource(R.string.summary_changes, added, replaced)
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.hint_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear_selection))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (visible.isEmpty()) {
                Text(
                    text = stringResource(if (state.entries.isEmpty()) R.string.empty_archive else R.string.no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visible) { entry ->
                        EntryRow(
                            entry = entry,
                            selected = entry.path in state.selected,
                            onToggle = { vm.toggleSelected(entry.path) },
                        )
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                if (state.selected.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.selected_count, state.selected.size),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = vm::clearSelection) {
                            Text(stringResource(R.string.action_clear_selection))
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    if (state.selected.isNotEmpty()) {
                        Button(onClick = { onExtract(state.selected) }, enabled = !busy) {
                            Text(stringResource(R.string.action_extract))
                        }
                        if (state.selected.size == 1) {
                            OutlinedButton(onClick = { onReplace(state.selected.first()) }, enabled = !busy) {
                                Text(stringResource(R.string.action_replace))
                            }
                        }
                        OutlinedButton(onClick = onDelete, enabled = !busy) {
                            Text(stringResource(R.string.action_delete))
                        }
                        OutlinedButton(onClick = { vm.selectAll(visible.map { it.path }) }, enabled = !busy) {
                            Text(stringResource(R.string.action_select_all))
                        }
                    } else {
                        Button(onClick = onAddFiles, enabled = !busy) {
                            Text(stringResource(R.string.action_add_files))
                        }
                        OutlinedButton(onClick = onAddFolder, enabled = !busy) {
                            Text(stringResource(R.string.action_add_folder))
                        }
                        OutlinedButton(
                            onClick = { onExtract(null) },
                            enabled = !busy && state.entries.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.action_extract_all))
                        }
                        OutlinedButton(
                            onClick = { vm.selectAll(visible.map { it.path }) },
                            enabled = !busy && visible.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.action_select_all))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: WorkingEntry, selected: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.dir.isNotEmpty()) {
                Text(
                    text = entry.dir,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp, end = 8.dp)) {
            Text(
                text = Formatter.formatShortFileSize(context, entry.extractSize),
                style = MaterialTheme.typography.bodySmall,
            )
            when {
                entry.state == EntryState.ADDED -> Text(
                    text = stringResource(R.string.badge_new),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                entry.state == EntryState.REPLACED -> Text(
                    text = stringResource(R.string.badge_replaced),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                entry.isCompressed -> Text(
                    text = stringResource(R.string.badge_compressed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun AddDialog(pending: PendingAdd, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var dir by rememberSaveable { mutableStateOf("") }
    val what = when (pending) {
        is PendingAdd.Files -> stringResource(R.string.pending_files, pending.uris.size)
        is PendingAdd.Folder -> stringResource(R.string.pending_folder)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_add_body, what))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = dir,
                    onValueChange = { dir = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.dialog_add_dir_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dir) }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
