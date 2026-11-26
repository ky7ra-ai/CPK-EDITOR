@file:OptIn(ExperimentalMaterial3Api::class)

package com.ky7ra.cpkeditor.ui

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ky7ra.cpkeditor.R
import com.ky7ra.cpkeditor.cpk.EntryState
import com.ky7ra.cpkeditor.cpk.WorkingEntry

// ---------------------------------------------------------------------------------------------
// Landing (no archive open)
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeLanding(name: String, onOpen: () -> Unit, onNew: () -> Unit, bottomInset: Dp) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtras.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = bottomInset),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "👋  " + stringResource(R.string.greeting, name),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }

        Column {
            Text(
                stringResource(R.string.home_kicker),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
            )
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ActionCard(
                title = stringResource(R.string.action_open),
                subtitle = stringResource(R.string.home_open_sub),
                container = cs.primary,
                content = Color.White,
                badge = Color.White,
                badgeIcon = cs.primary,
                onClick = onOpen,
                modifier = Modifier.weight(1f),
            )
            ActionCard(
                title = stringResource(R.string.action_new),
                subtitle = stringResource(R.string.home_new_sub),
                container = ex.tile,
                content = ex.onTile,
                badge = cs.primary,
                badgeIcon = Color.White,
                onClick = onNew,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, ex.cardBorder),
            )
        }

        WorkflowCard()

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = ex.tile,
            contentColor = ex.onTile,
            border = BorderStroke(1.dp, ex.cardBorder),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ShurikenMark(Modifier.size(30.dp), color = Brand.Orange, hole = ex.tile)
                Text(
                    stringResource(R.string.tip_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ex.onTile.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    badge: Color,
    badgeIcon: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = container,
        contentColor = content,
        border = border,
    ) {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            ShurikenMark(
                Modifier.align(Alignment.TopEnd).offset(x = 32.dp, y = (-32).dp).size(130.dp),
                color = content.copy(alpha = 0.12f),
                hole = Color.Transparent,
            )
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(badge),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = badgeIcon,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowCard() {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtras.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, ex.cardBorder),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.flow_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Top) {
                FlowStep(
                    "1", stringResource(R.string.flow_open), stringResource(R.string.flow_open_sub),
                    cs.primary, Color.White, Modifier.weight(1f),
                )
                FlowLink(Modifier.weight(0.35f))
                FlowStep(
                    "2", stringResource(R.string.flow_edit), stringResource(R.string.flow_edit_sub),
                    ex.tile, ex.onTile, Modifier.weight(1f),
                )
                FlowLink(Modifier.weight(0.35f))
                FlowStep(
                    "3", stringResource(R.string.flow_save), stringResource(R.string.flow_save_sub),
                    cs.surfaceVariant, cs.onSurface, Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FlowStep(
    number: String,
    title: String,
    caption: String,
    container: Color,
    content: Color,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = MaterialTheme.typography.titleMedium, color = content)
        }
        Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FlowLink(modifier: Modifier) {
    Box(modifier.padding(top = 21.dp).height(2.dp).background(MaterialTheme.colorScheme.outline))
}

// ---------------------------------------------------------------------------------------------
// Open archive
// ---------------------------------------------------------------------------------------------

@Composable
fun ProjectScreen(
    state: UiState,
    vm: CpkViewModel,
    bottomInset: Dp,
    onClose: () -> Unit,
    onSaveAs: () -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onExtract: (Set<String>?) -> Unit,
    onReplace: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtras.current
    val context = LocalContext.current
    val busy = state.progress != null

    val visible = remember(state.entries, state.query) {
        val q = state.query.trim().lowercase()
        if (q.isEmpty()) state.entries else state.entries.filter { it.path.lowercase().contains(q) }
    }
    val totalSize = remember(state.entries) { state.entries.sumOf { it.extractSize } }
    val changes = remember(state.entries) { state.entries.count { it.state != EntryState.ORIGINAL } }
    val hasSelection = state.selected.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    onClick = onClose,
                    enabled = !busy,
                    shape = CircleShape,
                    color = cs.surfaceVariant,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.project_kicker),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Text(
                        if (state.dirty) "${state.title} •" else state.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PillButton(
                    stringResource(R.string.action_save_as),
                    onSaveAs,
                    enabled = !busy,
                    compact = true,
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = bottomInset + if (hasSelection) 72.dp else 0.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            value = state.entries.size.toString(),
                            label = stringResource(R.string.stat_files),
                            container = cs.primary,
                            content = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = Formatter.formatShortFileSize(context, totalSize),
                            label = stringResource(R.string.stat_size),
                            container = ex.tile,
                            content = ex.onTile,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, ex.cardBorder),
                        )
                        StatTile(
                            value = changes.toString(),
                            label = stringResource(R.string.stat_changes),
                            container = cs.surface,
                            content = cs.onSurface,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, ex.cardBorder),
                        )
                    }
                }
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PillButton(stringResource(R.string.action_add_files), onAddFiles, enabled = !busy)
                        PillButton(
                            stringResource(R.string.action_add_folder),
                            onAddFolder,
                            style = PillStyle.Dark,
                            enabled = !busy,
                        )
                        PillButton(
                            stringResource(R.string.action_extract_all),
                            { onExtract(null) },
                            style = PillStyle.Outline,
                            enabled = !busy && state.entries.isNotEmpty(),
                        )
                        PillButton(
                            stringResource(R.string.action_select_all),
                            { vm.selectAll(visible.map { it.path }) },
                            style = PillStyle.Outline,
                            enabled = !busy && visible.isNotEmpty(),
                        )
                    }
                }
                item {
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (visible.isEmpty()) {
                    item {
                        Text(
                            stringResource(if (state.entries.isEmpty()) R.string.empty_archive else R.string.no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                } else {
                    items(visible, key = { it.path }) { entry ->
                        EntryCard(
                            entry = entry,
                            selected = entry.path in state.selected,
                            onToggle = { vm.toggleSelected(entry.path) },
                        )
                    }
                }
            }
        }

        if (hasSelection) {
            SelectionBar(
                count = state.selected.size,
                single = state.selected.size == 1,
                busy = busy,
                onClear = vm::clearSelection,
                onExtract = { onExtract(state.selected) },
                onReplace = { onReplace(state.selected.first()) },
                onDelete = onDelete,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomInset),
            )
        }
    }
}

@Composable
private fun EntryCard(entry: WorkingEntry, selected: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtras.current
    val context = LocalContext.current
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = cs.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else ex.cardBorder),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CheckDot(selected)
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.dir.isNotEmpty()) {
                    Text(
                        entry.dir,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    Formatter.formatShortFileSize(context, entry.extractSize),
                    style = MaterialTheme.typography.bodySmall,
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
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    single: Boolean,
    busy: Boolean,
    onClear: () -> Unit,
    onExtract: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ex = LocalExtras.current
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = ex.tile,
        contentColor = ex.onTile,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, ex.cardBorder),
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_selection))
            }
            Text(stringResource(R.string.selected_count, count), style = MaterialTheme.typography.labelLarge)
            PillButton(stringResource(R.string.action_extract), onExtract, enabled = !busy, compact = true)
            if (single) {
                PillButton(
                    stringResource(R.string.action_replace),
                    onReplace,
                    style = PillStyle.Glass,
                    enabled = !busy,
                    compact = true,
                )
            }
            PillButton(
                stringResource(R.string.action_delete),
                onDelete,
                style = PillStyle.Glass,
                enabled = !busy,
                compact = true,
            )
            Spacer(Modifier.size(4.dp))
        }
    }
}
