@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.ky7ra.cpkeditor.ui

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ky7ra.cpkeditor.R
import com.ky7ra.cpkeditor.cpk.BrowseFolder
import com.ky7ra.cpkeditor.cpk.CpkBrowse
import com.ky7ra.cpkeditor.cpk.EntryState
import com.ky7ra.cpkeditor.cpk.WorkingEntry

private sealed interface ListRow {
    val key: String

    class Folder(val item: BrowseFolder) : ListRow {
        override val key: String = "d:" + item.path
    }

    class File(val entry: WorkingEntry, val showPath: Boolean) : ListRow {
        override val key: String = "f:" + entry.path
    }
}

/**
 * The editor page: a file card, a folder-style listing, add/replace/extract/delete actions and,
 * while a task runs, the progress card. Back navigation (folder up, close) is handled by the caller.
 */
@Composable
fun EditorScreen(
    state: UiState,
    vm: CpkViewModel,
    searching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSaveAs: () -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onExtract: (Set<String>?) -> Unit,
    onReplace: (String) -> Unit,
    onDelete: () -> Unit,
    onCloseProject: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtras.current
    val context = LocalContext.current
    val busy = state.progress != null
    val searchActive = searching && state.query.isNotBlank()

    val listing = remember(state.entries, state.dir) { CpkBrowse.list(state.entries, state.dir) }
    val rows: List<ListRow> = remember(listing, state.entries, state.query, searchActive) {
        buildList {
            if (searchActive) {
                CpkBrowse.search(state.entries, state.query).forEach { add(ListRow.File(it, showPath = true)) }
            } else {
                listing.folders.forEach { add(ListRow.Folder(it)) }
                listing.files.forEach { add(ListRow.File(it, showPath = false)) }
            }
        }
    }
    val scopePaths = remember(listing) {
        listing.files.map { it.path } + listing.folders.flatMap { it.filePaths }
    }
    val totalSize = remember(state.entries) { state.entries.sumOf { it.extractSize } }
    val added = remember(state.entries) { state.entries.count { it.state == EntryState.ADDED } }
    val replaced = remember(state.entries) { state.entries.count { it.state == EntryState.REPLACED } }
    val compressed = remember(state.entries) { state.entries.count { it.isCompressed } }

    var infoOpen by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) runCatching { focus.requestFocus() }
    }

    val hasSelection = state.selected.isNotEmpty()
    val single = state.selected.size == 1

    val toggleFolder: (BrowseFolder) -> Unit = { f ->
        val all = f.filePaths.isNotEmpty() && state.selected.containsAll(f.filePaths)
        vm.setSelection(if (all) state.selected - f.filePaths.toSet() else state.selected + f.filePaths)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        EditorTopBar(
            busy = busy,
            searching = searching,
            hasEntries = state.entries.isNotEmpty(),
            onBack = onBack,
            onToggleSearch = { onSearchingChange(!searching) },
            onAddFolder = onAddFolder,
            onSelectAll = { vm.setSelection(scopePaths.toSet()) },
            onExtractAll = { onExtract(null) },
            onSaveAs = onSaveAs,
            onClose = onCloseProject,
       )

        val info = state.cpkInfo
        FileCard(
            title = state.title,
            dirty = state.dirty,
            subtitle = stringResource(
                R.string.file_card_sub,
                Formatter.formatShortFileSize(context, totalSize),
                state.entries.size,
           ),
            expanded = infoOpen,
            onToggle = { infoOpen = !infoOpen },
            details = listOf(
                stringResource(R.string.info_archive_size) to
                    (info?.let { Formatter.formatShortFileSize(context, it.fileSize) } ?: "—"),
                stringResource(R.string.info_align) to
                    (info?.let { "0x" + it.align.toString(16).uppercase() } ?: "0x800"),
                stringResource(R.string.stat_changes) to stringResource(R.string.summary_changes, added, replaced),
                stringResource(R.string.badge_compressed) to compressed.toString(),
           ),
       )
        Spacer(Modifier.height(10.dp))

        if (searching) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                shape = CircleShape,
                placeholder = { Text(stringResource(R.string.hint_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_selection))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
           )
            Spacer(Modifier.height(10.dp))
        } else if (state.dir.isNotEmpty()) {
            Breadcrumb(state.dir, onNavigate = vm::enterDir)
            Spacer(Modifier.height(10.dp))
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = cs.surface,
            border = BorderStroke(1.dp, ex.cardBorder),
       ) {
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(
                            when {
                                searchActive -> R.string.no_results
                                state.entries.isEmpty() -> R.string.empty_archive
                                else -> R.string.empty_folder
                            },
                       ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                   )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        when (row) {
                            is ListRow.Folder -> FolderRow(
                                item = row.item,
                                selected = row.item.filePaths.isNotEmpty() &&
                                    state.selected.containsAll(row.item.filePaths),
                                onOpen = { vm.enterDir(row.item.path) },
                                onToggle = { toggleFolder(row.item) },
                           )
                            is ListRow.File -> FileRow(
                                entry = row.entry,
                                showPath = row.showPath,
                                selected = row.entry.path in state.selected,
                                onToggle = { vm.toggleSelected(row.entry.path) },
                           )
                        }
                        if (index < rows.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = 64.dp), color = ex.cardBorder)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddPill(
                text = stringResource(R.string.action_add_files),
                onClick = onAddFiles,
                enabled = !busy,
                modifier = Modifier.weight(1f),
           )
            if (state.dirty) {
                PillButton(
                    text = stringResource(R.string.action_save),
                    onClick = onSaveAs,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
               )
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                label = stringResource(R.string.action_replace),
                icon = Icons.Filled.Refresh,
                container = cs.surfaceVariant,
                content = cs.onSurface,
                enabled = !busy && single,
                onClick = { onReplace(state.selected.first()) },
                modifier = Modifier.weight(1f),
           )
            ActionTile(
                label = stringResource(R.string.action_extract),
                icon = ExtractIcon,
                container = ex.tile,
                content = ex.onTile,
                enabled = !busy && hasSelection,
                onClick = { onExtract(state.selected) },
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, ex.cardBorder),
           )
            ActionTile(
                label = stringResource(R.string.action_delete),
                icon = Icons.Filled.Delete,
                container = cs.primary,
                content = Color.White,
                enabled = !busy && hasSelection,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
           )
        }

        state.progress?.let { progress ->
            Spacer(Modifier.height(10.dp))
            ProgressCard(progress, onCancel = vm::cancel)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EditorTopBar(
    busy: Boolean,
    searching: Boolean,
    hasEntries: Boolean,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onAddFolder: () -> Unit,
    onSelectAll: () -> Unit,
    onExtractAll: () -> Unit,
    onSaveAs: () -> Unit,
    onClose: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        IconButton(onClick = onBack, enabled = !busy, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Text(
            stringResource(R.string.editor_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Center),
       )
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.action_search),
                    tint = if (searching) MaterialTheme.colorScheme.primary else LocalContentColor.current,
               )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_save_as)) },
                        enabled = !busy,
                        onClick = {
                            menu = false
                            onSaveAs()
                        },
                   )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_folder)) },
                        enabled = !busy,
                        onClick = {
                            menu = false
                            onAddFolder()
                        },
                   )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_select_all)) },
                        enabled = !busy && hasEntries,
                        onClick = {
                            menu = false
                            onSelectAll()
                        },
                   )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_extract_all)) },
                        enabled = !busy && hasEntries,
                        onClick = {
                            menu = false
                            onExtractAll()
                        },
                   )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_close)) },
                        enabled = !busy,
                        onClick = {
                            menu = false
                            onClose()
                        },
                   )
                }
            }
        }
    }
}

@Composable
private fun FileCard(
    title: String,
    dirty: Boolean,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    details: List<Pair<String, String>>,
) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtras.current
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, ex.cardBorder),
        modifier = Modifier.fillMaxWidth(),
   ) {
        Column {
            Row(
                Modifier.clickable(onClick = onToggle).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
           ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(ex.tile),
                    contentAlignment = Alignment.Center,
               ) {
                    VersixtyMark(Modifier.size(34.dp), color = Brand.Orange)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                       )
                        if (dirty) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(8.dp).clip(CircleShape).background(cs.primary))
                        }
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                   )
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
               )
            }
            if (expanded) {
                HorizontalDivider(color = ex.cardBorder)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    details.forEach { (label, value) ->
                        Row {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                           )
                            Text(value, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Breadcrumb(dir: String, onNavigate: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val segments = remember(dir) { dir.split('/') }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
   ) {
        Crumb(stringResource(R.string.crumb_root), current = false) { onNavigate("") }
        segments.forEachIndexed { i, segment ->
            Text("›", color = cs.onSurfaceVariant)
            val path = segments.take(i + 1).joinToString("/")
            Crumb(segment, current = i == segments.lastIndex) { onNavigate(path) }
        }
    }
}

@Composable
private fun Crumb(text: String, current: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (current) cs.primary else cs.surfaceVariant,
        contentColor = if (current) Color.White else cs.onSurface,
   ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
       )
    }
}

@Composable
private fun FolderRow(item: BrowseFolder, selected: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) cs.primary.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onOpen, onLongClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
   ) {
        FolderGlyph(Modifier.size(34.dp), color = cs.primary)
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
           )
            Text(
                stringResource(R.string.item_count, item.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
           )
        }
        if (selected) {
            CheckDot(true)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileRow(entry: WorkingEntry, showPath: Boolean, selected: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtras.current
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, entry.extractSize)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) cs.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
   ) {
        DocGlyph(Modifier.size(34.dp), color = cs.onSurface, lines = cs.surface)
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
           )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (showPath && entry.dir.isNotEmpty()) "${entry.dir} • $size" else size,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
               )
                when {
                    entry.state == EntryState.ADDED ->
                        StatusBadge(stringResource(R.string.badge_new), cs.primary, Color.White)
                    entry.state == EntryState.REPLACED ->
                        StatusBadge(stringResource(R.string.badge_replaced), ex.tile, ex.onTile)
                    entry.isCompressed ->
                        StatusBadge(stringResource(R.string.badge_compressed), cs.surfaceVariant, cs.onSurfaceVariant)
                }
            }
        }
        CheckDot(selected)
    }
}

@Composable
private fun AddPill(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        shape = CircleShape,
        color = cs.primary.copy(alpha = 0.06f),
        contentColor = cs.onSurface,
        border = BorderStroke(1.5.dp, cs.primary),
   ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
       ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = content,
        border = border,
   ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
       ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
