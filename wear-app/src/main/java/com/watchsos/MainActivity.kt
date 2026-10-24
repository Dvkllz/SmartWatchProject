package com.watchsos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watchsos.ui.screens.EmergencyScreen
import com.watchsos.ui.theme.SosTheme
import com.watchsos.viewmodel.EmergencyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
                val viewModel: EmergencyViewModel = viewModel()
                val app = application as SosApplication
                val state by viewModel.state.collectAsStateWithLifecycle()
                val preferences by app.uiPreferences.collectAsStateWithLifecycle()
                SosTheme(preferences.theme) {
                    EmergencyScreen(
                        state = state,
                        configurationReady = viewModel.configurationReady,
                        preferences = preferences,
                        onStart = viewModel::start,
                        onFinish = viewModel::finish,
                        onRetry = viewModel::retryFinish,
                        onThemeChange = app::setTheme,
                        onLanguageChange = app::setLanguage,
                    )
                }
        }
    }
}
