package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LibraryViewModel(
    application: Application,
    private val repository: VideoRepository
) : AndroidViewModel(application) {

    // Scanner state
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _localVideos = MutableStateFlow<List<LocalVideo>>(emptyList())
    val localVideos: StateFlow<List<LocalVideo>> = _localVideos.asStateFlow()

    private val _demoVideos = MutableStateFlow<List<LocalVideo>>(repository.getDemoVideos())
    val demoVideos: StateFlow<List<LocalVideo>> = _demoVideos.asStateFlow()

    // Combined Videos Stream (Demo + scanned Local videos)
    val allVideosList: StateFlow<List<LocalVideo>> = combine(_localVideos, _demoVideos) { local, demo ->
        local + demo
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.getDemoVideos())

    // Database Streams
    val historyList: StateFlow<List<PlaybackHistory>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritesList: StateFlow<List<FavoriteVideo>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistsList: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Video converter simulation progress (Playit MP3 converter)
    private val _extractionProgress = MutableStateFlow<Float?>(null) // null = not extracting, 0-1 = progress
    val extractionProgress: StateFlow<Float?> = _extractionProgress.asStateFlow()

    private val _extractedMp3s = MutableStateFlow<List<String>>(emptyList())
    val extractedMp3s: StateFlow<List<String>> = _extractedMp3s.asStateFlow()

    init {
        // Scanning is now triggered explicitly after the media permission is granted
        // (see MainActivity), instead of running blindly here, which used to crash
        // or silently return zero videos on Android 13+ without permission.
    }

    fun scanLocalMedia() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val scanned = repository.scanLocalVideos()
                _localVideos.value = scanned
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    // Playlists
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(id: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun addVideoToPlaylist(playlistId: Int, video: LocalVideo) {
        viewModelScope.launch {
            repository.addVideoToPlaylist(playlistId, video.path, video.title, video.duration)
        }
    }

    fun removeVideoFromPlaylist(playlistId: Int, videoPath: String) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(playlistId, videoPath)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun deleteHistoryItem(videoPath: String) {
        viewModelScope.launch {
            repository.deleteHistory(videoPath)
        }
    }

    // Playit MP3 Extractor simulator
    fun extractMp3(video: LocalVideo) {
        if (_extractionProgress.value != null) return // Already extracting
        
        viewModelScope.launch(Dispatchers.Default) {
            _extractionProgress.value = 0.0f
            for (i in 1..20) {
                delay(150)
                _extractionProgress.value = i * 0.05f
            }
            _extractionProgress.value = null
            
            // Extracted successfully! Append to list
            val mp3Name = video.title.replace(Regex("\\.(mp4|mkv|3gp|webm|avi|flv)$", RegexOption.IGNORE_CASE), "") + ".mp3"
            _extractedMp3s.update { it + mp3Name }
        }
    }
}

class LibraryViewModelFactory(
    private val application: Application,
    private val repository: VideoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
