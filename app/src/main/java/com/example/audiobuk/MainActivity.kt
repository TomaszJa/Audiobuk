package com.example.audiobuk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audiobuk.ui.screens.LibraryScreen
import com.example.audiobuk.ui.screens.PlayerScreen
import com.example.audiobuk.ui.theme.AudiobukTheme
import com.example.audiobuk.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudiobukTheme {
                val viewModel: MusicViewModel = viewModel()
                
                LaunchedEffect(intent) {
                    handleIntent(intent, viewModel)
                }
                
                MainApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // This is called if the activity is already running (e.g. user clicks notification while app is in background)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent?, viewModel: MusicViewModel) {
        if (intent?.action == "com.example.audiobuk.OPEN_PLAYER") {
            viewModel.setShowPlayerScreen(true)
        }
    }
}

@Composable
fun MainApp(viewModel: MusicViewModel) {
    val showPlayerScreen by viewModel.showPlayerScreen.collectAsState()

    if (showPlayerScreen) {
        PlayerScreen(
            viewModel = viewModel,
            onBack = { viewModel.setShowPlayerScreen(false) }
        )
    } else {
        LibraryScreen(viewModel = viewModel)
    }
}
