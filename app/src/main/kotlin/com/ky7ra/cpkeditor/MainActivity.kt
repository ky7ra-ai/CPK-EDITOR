package com.ky7ra.cpkeditor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import com.ky7ra.cpkeditor.ui.AppSettings
import com.ky7ra.cpkeditor.ui.CpkEditorApp
import com.ky7ra.cpkeditor.ui.CpkEditorTheme
import com.ky7ra.cpkeditor.ui.CpkViewModel
import com.ky7ra.cpkeditor.ui.ThemeMode

class MainActivity : ComponentActivity() {
    private val viewModel: CpkViewModel by viewModels()
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        enableEdgeToEdge()
        if (savedInstanceState == null) openFromIntent(intent)
        setContent {
            val dark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Keep the status/navigation bar icons readable when the in-app theme differs from the system one.
            DisposableEffect(dark) {
                val bars = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                this@MainActivity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            CpkEditorTheme(darkTheme = dark) {
                CpkEditorApp(viewModel, settings)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromIntent(intent)
    }

    /** "Open with CPK Editor" from a file manager. */
    private fun openFromIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { viewModel.openCpk(it) }
        }
    }
}
