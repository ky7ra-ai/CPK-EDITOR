@file:OptIn(ExperimentalMaterial3Api::class)

package com.ky7ra.cpkeditor.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ky7ra.cpkeditor.R

private const val REPO_URL = "https://github.com/ky7ra-ai/CPK-EDITOR"

@Suppress("DEPRECATION")
private fun readVersion(context: Context): Pair<String, Long> = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    (info.versionName ?: "?") to code
} catch (_: Exception) {
    "?" to 0L
}

@Composable
fun AboutScreen(bottomInset: Dp) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtras.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val (versionName, versionCode) = remember { readVersion(context) }
    val versionText = "$versionName ($versionCode)"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.padding(top = 8.dp, bottom = 6.dp)) {
            Text(
                stringResource(R.string.about_kicker),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        }

        Surface(
            shape = RoundedCornerShape(36.dp),
            color = ex.tile,
            contentColor = ex.onTile,
            border = BorderStroke(1.dp, ex.cardBorder),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    "忍",
                    fontSize = 190.sp,
                    fontWeight = FontWeight.Black,
                    color = Brand.Orange,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 24.dp, y = (-20).dp),
                )
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShurikenMark(Modifier.size(48.dp), color = Brand.Orange, hole = ex.tile)
                    Spacer(Modifier.height(44.dp))
                    Text(
                        stringResource(R.string.app_name),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.about_version) + " " + versionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ex.onTile.copy(alpha = 0.7f),
                    )
                }
            }
        }

        InfoRow(stringResource(R.string.about_version), versionText)
        InfoRow(stringResource(R.string.about_developer), stringResource(R.string.about_developer_value))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cs.surface,
            border = BorderStroke(1.dp, ex.cardBorder),
        ) {
            Text(
                stringResource(R.string.about_desc),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(18.dp),
            )
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceVariant,
        ) {
            Text(
                stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(18.dp),
            )
        }

        PillButton(
            text = stringResource(R.string.about_github),
            onClick = { uriHandler.openUri(REPO_URL) },
            style = PillStyle.Dark,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, LocalExtras.current.cardBorder),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
