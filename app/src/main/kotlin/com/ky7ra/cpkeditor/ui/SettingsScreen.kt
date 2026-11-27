@file:OptIn(ExperimentalMaterial3Api::class)

package com.ky7ra.cpkeditor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
        Text(
            stringResource(R.string.settings_name_label),
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant,
        )
        TextField(
            value = draft,
            onValueChange = { draft = it.take(24) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            textStyle = MaterialTheme.typography.titleMedium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = cs.surfaceVariant,
                unfocusedContainerColor = cs.surfaceVariant,
                disabledContainerColor = cs.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        PillButton(
            text = stringResource(R.string.settings_name_save),
            onClick = { settings.updateName(draft.trim()) },
            enabled = draft.isNotBlank() && draft.trim() != settings.userName,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
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
        color = if (selected) lerp(cs.surface, cs.primary, 0.08f) else cs.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else ex.cardBorder),
    ) {
        Row(
            Modifier.padding(14.dp),
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

/** Small rounded square previewing a theme mode, with the brand-orange dot in the middle. */
@Composable
private fun ThemeSwatch(mode: ThemeMode) {
    val shape = RoundedCornerShape(16.dp)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(52.dp).clip(shape).border(1.dp, outline, shape)) {
        val light = Color(0xFFF4F4F5)
        when (mode) {
            ThemeMode.LIGHT -> drawRect(light)
            ThemeMode.DARK -> drawRect(Brand.Ink)
            ThemeMode.SYSTEM -> {
                drawRect(light)
                drawRect(Brand.Ink, topLeft = Offset(size.width / 2f, 0f), size = Size(size.width / 2f, size.height))
            }
        }
        drawCircle(Brand.Orange, radius = size.minDimension * 0.17f, center = Offset(size.width / 2f, size.height / 2f))
    }
}
