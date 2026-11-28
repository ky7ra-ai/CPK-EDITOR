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
                VersixtyMark(Modifier.size(30.dp), color = Brand.Orange)
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
            VersixtyMark(
                Modifier.align(Alignment.TopEnd).offset(x = 32.dp, y = (-32).dp).size(130.dp),
                color = content.copy(alpha = 0.12f),
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
