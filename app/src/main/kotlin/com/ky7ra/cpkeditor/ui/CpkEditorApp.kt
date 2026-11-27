@file:OptIn(ExperimentalMaterial3Api::class)

package com.ky7ra.cpkeditor.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ky7ra.cpkeditor.R

@Composable
fun CpkEditorApp(vm: CpkViewModel, settings: AppSettings) {
    if (!settings.onboarded) {
        OnboardingScreen(onDone = settings::completeOnboarding)
    } else {
        MainShell(vm, settings)
    }
}

@Composable
private fun MainShell(vm: CpkViewModel, settings: AppSettings) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable(state.hasProject, state.title) { mutableStateOf(false) }
    val tab = AppTab.entries[tabIndex]
    val busy = state.progress != null
    // The editor page is full screen: no floating nav bar, its own back arrow.
    val editorVisible = tab == AppTab.Home && state.hasProject

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
    val setSearching: (Boolean) -> Unit = { on ->
        searching = on
        if (!on) vm.setQuery("")
    }

    // One place decides what "back" means, for both the system gesture and the top-bar arrow.
    val goBack: () -> Unit = {
        when {
            busy -> Unit
            tab != AppTab.Home -> tabIndex = 0
            searching -> setSearching(false)
            state.selected.isNotEmpty() -> vm.clearSelection()
            state.dir.isNotEmpty() -> vm.goUp()
            else -> closeOrConfirm()
        }
    }
    BackHandler(enabled = busy || tab != AppTab.Home || state.hasProject) { goBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = if (editorVisible) 150.dp else NavBarSpace))
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                AppTab.Home -> if (!state.hasProject) {
                    HomeLanding(
                        name = settings.userName,
                        onOpen = { openCpk.launch(arrayOf("*/*")) },
                        onNew = vm::newProject,
                        bottomInset = NavBarSpace,
                    )
                } else {
                    EditorScreen(
                        state = state,
                        vm = vm,
                        searching = searching,
                        onSearchingChange = setSearching,
                        onBack = goBack,
                        onSaveAs = { saveAs.launch(vm.suggestedSaveName()) },
                        onAddFiles = { pickFiles.launch(arrayOf("*/*")) },
                        onAddFolder = { pickFolder.launch(null) },
                        onExtract = onExtract,
                        onReplace = onReplace,
                        onDelete = { confirmDelete = true },
                        onCloseProject = closeOrConfirm,
                    )
                }
                AppTab.Settings -> SettingsScreen(settings, NavBarSpace)
                AppTab.About -> AboutScreen(NavBarSpace)
            }

            if (!editorVisible) {
                state.progress?.let { progress ->
                    ProgressCard(
                        progress = progress,
                        onCancel = vm::cancel,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp)
                            .padding(bottom = NavBarSpace),
                    )
                }
                FloatingNavBar(
                    selected = tab,
                    onSelect = { tabIndex = it.ordinal },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
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
