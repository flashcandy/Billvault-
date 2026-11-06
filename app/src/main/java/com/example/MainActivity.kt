package com.example

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

        val initialItemId = intent?.getLongExtra("EXTRA_ITEM_ID", -1L)?.takeIf { it > 0L }

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
                    initialItemId = initialItemId,
                    onThemeChanged = { mode -> themeMode = mode }
                )
            }
        }
    }
}
