package com.ky7ra.cpkeditor.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Small persisted app preferences, backed by Compose state so the UI reacts to changes. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cpk_settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM),
    )
        private set

    var userName by mutableStateOf(prefs.getString(KEY_NAME, null) ?: "")
        private set

    var onboarded by mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false))
        private set

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun completeOnboarding(name: String) {
        userName = name
        onboarded = true
        prefs.edit().putString(KEY_NAME, name).putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun updateName(name: String) {
        userName = name
        prefs.edit().putString(KEY_NAME, name).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_NAME = "name"
        const val KEY_ONBOARDED = "onboarded"
    }
}
