package com.ky7ra.cpkeditor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ky7ra.cpkeditor.ui.CpkEditorApp
import com.ky7ra.cpkeditor.ui.CpkEditorTheme
import com.ky7ra.cpkeditor.ui.CpkViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CpkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openFromIntent(intent)
        setContent {
            CpkEditorTheme {
                CpkEditorApp(viewModel)
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
