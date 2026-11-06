package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.preferences.SettingsManager
import com.example.ui.navigation.MainNavGraph
import com.example.ui.theme.BillValidTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming share or view intents
        handleIntent(intent)

        setContent {
            var themeMode by remember { mutableStateOf(viewModel.settingsManager.themeMode) }

            val isDark = when (themeMode) {
                SettingsManager.THEME_LIGHT -> false
                SettingsManager.THEME_SYSTEM -> isSystemInDarkTheme()
                else -> true // Default Nothing OS Dark Theme
            }

            BillValidTheme(darkTheme = isDark) {
                MainNavGraph(
                    viewModel = viewModel,
                    onThemeChanged = { mode -> themeMode = mode }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                viewModel.processInvoiceUri(uri)
            }
        } else if (Intent.ACTION_VIEW == action && intent.data != null) {
            viewModel.processInvoiceUri(intent.data!!)
        }
    }
}
