package com.example.audiobuk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MusicViewModel = viewModel()) {
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
