package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.data.model.LocalVideo
import com.example.ui.screens.MainLibraryScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.LibraryViewModelFactory
import com.example.ui.viewmodel.PlayerViewModel
import com.example.ui.viewmodel.PlayerViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Retrieve repository from Application
        val app = application as MainApplication
        val repository = app.repository

        // Initialize ViewModels using Factories
        val libraryViewModel: LibraryViewModel by viewModels {
            LibraryViewModelFactory(application, repository)
        }
        val playerViewModel: PlayerViewModel by viewModels {
            PlayerViewModelFactory(application, repository)
        }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var activeVideo by remember { mutableStateOf<LocalVideo?>(null) }

                    if (activeVideo == null) {
                        MainLibraryScreen(
                            viewModel = libraryViewModel,
                            onVideoSelect = { video ->
                                activeVideo = video
                                playerViewModel.playVideo(video)
                            }
                        )
                    } else {
                        VideoPlayerScreen(
                            viewModel = playerViewModel,
                            onBackClick = {
                                playerViewModel.releasePlayer()
                                activeVideo = null
                                // Re-trigger library scan when returning to sync state
                                libraryViewModel.scanLocalMedia()
                            }
                        )
                    }
                }
            }
        }
    }
}
