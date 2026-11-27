@file:OptIn(ExperimentalMaterial3Api::class)

package com.ky7ra.cpkeditor.ui

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ky7ra.cpkeditor.R

/** Card shown while a task runs: percentage ring, current file, bytes done and estimated time left. */
@Composable
fun ProgressCard(progress: Progress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val fraction = progress.fraction

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, LocalExtras.current.cardBorder),
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                if (fraction != null) {
                    CircularProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxSize(),
                        color = cs.primary,
                        strokeWidth = 6.dp,
                        trackColor = cs.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )
                    Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = cs.primary,
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(progress.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                progress.detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progress.total > 0) {
                    var line = Formatter.formatShortFileSize(context, progress.done) + " / " +
                        Formatter.formatShortFileSize(context, progress.total)
                    progress.etaSeconds?.let { line += " • " + stringResource(R.string.progress_eta, etaText(it)) }
                    Text(
                        line,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
                        color = cs.primary,
                        trackColor = cs.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun etaText(seconds: Long): String =
    if (seconds < 60) {
        stringResource(R.string.eta_sec, seconds.toInt())
    } else {
        stringResource(R.string.eta_min, (seconds / 60).toInt(), (seconds % 60).toInt())
    }
