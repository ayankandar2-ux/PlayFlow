package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

        // The permission needed to read videos differs by Android version:
        // Android 13+ (API 33+) uses the granular READ_MEDIA_VIDEO permission.
        // Older versions use READ_EXTERNAL_STORAGE.
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        setContent {
            MyApplicationTheme {
                var hasPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, mediaPermission) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasPermission = granted
                    if (granted) {
                        libraryViewModel.scanLocalMedia()
                    }
                }

                LaunchedEffect(Unit) {
                    if (!hasPermission) {
                        permissionLauncher.launch(mediaPermission)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var activeVideo by remember { mutableStateOf<LocalVideo?>(null) }

                    if (!hasPermission) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "PlayFlow needs permission to access videos on your device.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                                Button(onClick = { permissionLauncher.launch(mediaPermission) }) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    } else if (activeVideo == null) {
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
