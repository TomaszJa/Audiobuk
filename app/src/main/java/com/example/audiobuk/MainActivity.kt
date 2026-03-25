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
import com.example.audiobuk.viewmodel.AudioBookViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val intentFlow = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        intentFlow.value = intent

        setContent {
            AudiobukTheme {
                val viewModel: AudioBookViewModel = viewModel()
                val currentIntent by intentFlow.collectAsState()
                
                LaunchedEffect(currentIntent) {
                    currentIntent?.let { handleIntent(it, viewModel) }
                }
                
                MainApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentFlow.value = intent
    }

    private fun handleIntent(intent: Intent, viewModel: AudioBookViewModel) {
        if (intent.action == "com.example.audiobuk.OPEN_PLAYER") {
            viewModel.setShowPlayerScreen(true)
        }
    }
}

@Composable
fun MainApp(viewModel: AudioBookViewModel) {
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
