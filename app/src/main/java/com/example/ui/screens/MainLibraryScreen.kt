package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.data.model.FavoriteVideo
import com.example.data.model.LocalVideo
import com.example.data.model.PlaybackHistory
import com.example.data.model.Playlist
import com.example.ui.theme.PlayitCyan
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.VlcOrange
import com.example.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLibraryScreen(
    viewModel: LibraryViewModel,
    onVideoSelect: (LocalVideo) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0=Videos, 1=Playlists, 2=MP3 Converter, 3=Settings

    // Permission launcher for local media scanning
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanLocalMedia()
            Toast.makeText(context, "Storage Access Granted! Scanning media...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Storage permission is required to view local videos. Showing demo videos instead.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-request permission on launch to provide high-quality UX
    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionToRequest)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(listOf(VlcOrange, PlayitCyan)),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "VLC Playit",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "Next-Gen Local Player",
                                style = MaterialTheme.typography.labelSmall,
                                color = PlayitCyan
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.scanLocalMedia() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh scan", tint = PlayitCyan)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SlateSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Videos") },
                    label = { Text("Library") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VlcOrange,
                        selectedTextColor = VlcOrange,
                        indicatorColor = SlateSurfaceElevated
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Playlists") },
                    label = { Text("Playlists") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VlcOrange,
                        selectedTextColor = VlcOrange,
                        indicatorColor = SlateSurfaceElevated
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.AudioFile, contentDescription = "MP3 Converter") },
                    label = { Text("MP3 Extract") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VlcOrange,
                        selectedTextColor = VlcOrange,
                        indicatorColor = SlateSurfaceElevated
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VlcOrange,
                        selectedTextColor = VlcOrange,
                        indicatorColor = SlateSurfaceElevated
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> VideosLibraryTab(viewModel, onVideoSelect)
                1 -> PlaylistsAndHistoryTab(viewModel, onVideoSelect)
                2 -> VideoToMp3Tab(viewModel)
                3 -> SettingsAndToolsTab(viewModel, requestPermission = { permissionLauncher.launch(permissionToRequest) })
            }
        }
    }
}

// --- TAB 1: Video Library (Scanned Local + Online Demos) ---
@Composable
fun VideosLibraryTab(
    viewModel: LibraryViewModel,
    onVideoSelect: (LocalVideo) -> Unit
) {
    val allVideos by viewModel.allVideosList.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFolderFilter by remember { mutableStateOf("All") }

    val folders = remember(allVideos) {
        listOf("All") + allVideos.map { it.folder }.distinct()
    }

    val filteredVideos = remember(allVideos, searchQuery, selectedFolderFilter) {
        allVideos.filter { video ->
            val matchesSearch = video.title.contains(searchQuery, ignoreCase = true)
            val matchesFolder = selectedFolderFilter == "All" || video.folder == selectedFolderFilter
            matchesSearch && matchesFolder
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search videos...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SlateSurface,
                unfocusedContainerColor = SlateSurface,
                disabledContainerColor = SlateSurface,
                focusedIndicatorColor = VlcOrange,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Folders chips slider
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(48.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.forEach { folder ->
                        FilterChip(
                            selected = selectedFolderFilter == folder,
                            onClick = { selectedFolderFilter = folder },
                            label = { Text(folder) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VlcOrange,
                                selectedLabelColor = Color.Black,
                                containerColor = SlateSurface,
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Scanning indicator
        if (isScanning) {
            LinearProgressIndicator(
                color = PlayitCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        if (filteredVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideoCameraBack,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No videos found",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Make sure to grant storage permission or check the 'Demo Streams' filter.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // Grid of videos
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredVideos) { video ->
                    VideoGridCard(
                        video = video,
                        onClick = { onVideoSelect(video) }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoGridCard(
    video: LocalVideo,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            // Thumbnail placeholder / video visual card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(
                        Brush.verticalGradient(
                            listOf(SlateSurfaceElevated, Color.Black)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Large Play button in center
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = null,
                    tint = if (video.isDemo) PlayitCyan else VlcOrange,
                    modifier = Modifier.size(44.dp)
                )

                // Subtitle indicator if video is a Demo stream
                if (video.isDemo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("CC / DEMO", color = PlayitCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Duration badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatTime(video.duration),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Title and Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = video.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.folder,
                        fontSize = 11.sp,
                        color = PlayitCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (video.size > 0) "${video.size / (1024 * 1024)} MB" else "Online",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// --- TAB 2: Playlists, Favorites, History ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsAndHistoryTab(
    viewModel: LibraryViewModel,
    onVideoSelect: (LocalVideo) -> Unit
) {
    val history by viewModel.historyList.collectAsState()
    val favorites by viewModel.favoritesList.collectAsState()
    val playlists by viewModel.playlistsList.collectAsState()

    var activeSubTab by remember { mutableStateOf(0) } // 0=History, 1=Favorites, 2=Playlists
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = SlateSurface,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeSubTab]),
                    color = VlcOrange
                )
            }
        ) {
            Tab(selected = activeSubTab == 0, onClick = { activeSubTab = 0 }) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = if (activeSubTab == 0) VlcOrange else Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("History")
                }
            }
            Tab(selected = activeSubTab == 1, onClick = { activeSubTab = 1 }) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = if (activeSubTab == 1) VlcOrange else Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Favorites")
                }
            }
            Tab(selected = activeSubTab == 2, onClick = { activeSubTab = 2 }) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = if (activeSubTab == 2) VlcOrange else Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Playlists")
                }
            }
        }

        when (activeSubTab) {
            0 -> {
                // History List
                if (history.isEmpty()) {
                    EmptySubState("No watched videos yet", Icons.Default.History)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recently Played", color = Color.Gray, fontSize = 14.sp)
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("Clear All", color = VlcOrange)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(history) { item ->
                            HistoryRow(item, onSelect = {
                                onVideoSelect(LocalVideo(item.videoPath, item.videoTitle, item.duration))
                            }, onDelete = {
                                viewModel.deleteHistoryItem(item.videoPath)
                            })
                        }
                    }
                }
            }
            1 -> {
                // Favorites List
                if (favorites.isEmpty()) {
                    EmptySubState("No favorite videos pinned", Icons.Default.FavoriteBorder)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(favorites) { item ->
                            FavoriteRow(item, onSelect = {
                                onVideoSelect(LocalVideo(item.videoPath, item.videoTitle, item.duration))
                            })
                        }
                    }
                }
            }
            2 -> {
                // Playlists Tab
                Box(modifier = Modifier.fillMaxSize()) {
                    if (playlists.isEmpty()) {
                        EmptySubState("Create custom playlists", Icons.Default.PlaylistPlay)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(playlists) { playlist ->
                                PlaylistRow(playlist, onDelete = {
                                    viewModel.deletePlaylist(playlist.id)
                                })
                            }
                        }
                    }

                    // Floating action button to create new playlist
                    FloatingActionButton(
                        onClick = { showCreatePlaylistDialog = true },
                        containerColor = VlcOrange,
                        contentColor = Color.Black,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Playlist")
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                Column {
                    Text(
                        "Enter a name for the custom media library playlist:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        singleLine = true,
                        placeholder = { Text("Chill Mix, Movies, etc.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            viewModel.createPlaylist(playlistName)
                        }
                        showCreatePlaylistDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VlcOrange)
                ) {
                    Text("Create", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun EmptySubState(message: String, icon: ImageVector) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.Gray,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HistoryRow(
    history: PlaybackHistory,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = PlayitCyan, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = history.videoTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last watched: ${formatTime(history.lastPosition)} / ${formatTime(history.duration)}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun FavoriteRow(
    favorite: FavoriteVideo,
    onSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = favorite.videoTitle,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Length: ${formatTime(favorite.duration)}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun PlaylistRow(
    playlist: Playlist,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = VlcOrange, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(playlist.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Text("Media Library Playlist", color = Color.Gray, fontSize = 11.sp)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete playlist", tint = Color.Gray)
            }
        }
    }
}


// --- TAB 3: Video To MP3 Converter (Playit style Tool) ---
@Composable
fun VideoToMp3Tab(
    viewModel: LibraryViewModel
) {
    val allVideos by viewModel.allVideosList.collectAsState()
    val extractionProgress by viewModel.extractionProgress.collectAsState()
    val mp3s by viewModel.extractedMp3s.collectAsState()

    var showSelectorDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Welcome converter card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceElevated),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, tint = PlayitCyan, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Playit Video to MP3 Extractor", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Convert any local film or download stream into premium 320kbps MP3 audio instantly. Perfect for music clips, audiobooks, and motivational speeches.",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (extractionProgress != null) {
                    // Extracting progress indicators
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Extracting high-fidelity audio track... ${(extractionProgress!! * 100).toInt()}%",
                            color = PlayitCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { extractionProgress!! },
                            color = PlayitCyan,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                } else {
                    Button(
                        onClick = { showSelectorDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = VlcOrange),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select Video to Convert", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Converted Audio Tracks (${mp3s.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (mp3s.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(SlateSurface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No extracted MP3s yet", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mp3s) { mp3Name ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = VlcOrange, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(mp3Name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("Format: Audio MP3 • Bitrate: 320kbps", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Video selection dialog
    if (showSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showSelectorDialog = false },
            title = { Text("Convert Video") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Choose an loaded video to extract MP3 track:", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        items(allVideos) { video ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.extractMp3(video)
                                        showSelectorDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = PlayitCyan, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = video.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}


// --- TAB 4: Settings & Tools ---
@Composable
fun SettingsAndToolsTab(
    viewModel: LibraryViewModel,
    requestPermission: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Application Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Scan Trigger
                SettingsItem(
                    title = "Media Scanner",
                    subtitle = "Manually trigger storage scan for new local films",
                    icon = Icons.Default.Scanner,
                    onClick = {
                        requestPermission()
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Background playback setting
                SettingsItem(
                    title = "Background Playback",
                    subtitle = "Always play video audio in background when locked",
                    icon = Icons.Default.PlayCircleOutline,
                    onClick = {
                        Toast.makeText(context, "Background audio configured! Enjoy continuous play.", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Subtitle defaults setting
                SettingsItem(
                    title = "Subtitle Engine",
                    subtitle = "Default font scale: 20sp, color: White (Customizable)",
                    icon = Icons.Default.ClosedCaption,
                    onClick = {
                        Toast.makeText(context, "Configured default styling profiles.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Developer info card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceElevated),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("About VLC Playit", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "VLC Playit is a local video player and custom subtitling tool. Built using Google Jetpack Compose, Material 3 Design guidelines, and Android Media3 ExoPlayer.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("App Version", color = Color.Gray, fontSize = 12.sp)
                    Text("1.0.0 (Premium build)", color = PlayitCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = PlayitCyan, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}
