@file:OptIn(ExperimentalMaterial3Api::class)

package com.ky7ra.cpkeditor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ky7ra.cpkeditor.R

@Composable
fun SettingsScreen(settings: AppSettings, bottomInset: Dp) {
    val cs = MaterialTheme.colorScheme

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
                stringResource(R.string.settings_kicker),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
            )
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)
        }

        SectionLabel(stringResource(R.string.settings_appearance))
        ThemeOption(
            mode = ThemeMode.DARK,
            title = stringResource(R.string.theme_dark),
            desc = stringResource(R.string.theme_dark_desc),
            selected = settings.themeMode == ThemeMode.DARK,
            onClick = { settings.setTheme(ThemeMode.DARK) },
        )
        ThemeOption(
            mode = ThemeMode.LIGHT,
            title = stringResource(R.string.theme_light),
            desc = stringResource(R.string.theme_light_desc),
            selected = settings.themeMode == ThemeMode.LIGHT,
            onClick = { settings.setTheme(ThemeMode.LIGHT) },
        )
        ThemeOption(
            mode = ThemeMode.SYSTEM,
            title = stringResource(R.string.theme_system),
            desc = stringResource(R.string.theme_system_desc),
            selected = settings.themeMode == ThemeMode.SYSTEM,
            onClick = { settings.setTheme(ThemeMode.SYSTEM) },
        )

        SectionLabel(stringResource(R.string.settings_profile), Modifier.padding(top = 10.dp))
        var draft by remember(settings.userName) { mutableStateOf(settings.userName) }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(24) },
            singleLine = true,
            shape = CircleShape,
            label = { Text(stringResource(R.string.settings_name_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        PillButton(
            text = stringResource(R.string.settings_name_save),
            onClick = { settings.updateName(draft.trim()) },
            enabled = draft.isNotBlank() && draft.trim() != settings.userName,
        )
    }
}

@Composable
private fun ThemeOption(mode: ThemeMode, title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtras.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else ex.cardBorder),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ThemeSwatch(mode)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            CheckDot(selected)
        }
    }
}

/** Tiny preview of what a theme mode looks like. */
@Composable
private fun ThemeSwatch(mode: ThemeMode) {
    val shape = RoundedCornerShape(14.dp)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(44.dp).clip(shape).border(1.dp, outline, shape)) {
        val light = Color(0xFFF4F4F5)
        when (mode) {
            ThemeMode.LIGHT -> drawRect(light)
            ThemeMode.DARK -> drawRect(Brand.Ink)
            ThemeMode.SYSTEM -> {
                drawRect(light)
                drawRect(Brand.Ink, topLeft = Offset(size.width / 2f, 0f), size = Size(size.width / 2f, size.height))
            }
        }
        drawCircle(Brand.Orange, radius = size.minDimension * 0.18f, center = Offset(size.width / 2f, size.height / 2f))
    }
}
