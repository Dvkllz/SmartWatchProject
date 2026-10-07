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
            SosTheme {
                val viewModel: EmergencyViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                EmergencyScreen(state, viewModel.configurationReady, viewModel::start, viewModel::finish, viewModel::retryFinish)
            }
        }
    }
}
