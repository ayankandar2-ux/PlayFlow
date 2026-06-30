package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.LocalVideo
import com.example.data.repository.VideoRepository
import com.example.utils.SubtitleCue
import com.example.utils.SubtitleParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

enum class AspectRatioMode {
    FIT, STRETCH, FILL, ZOOM, SIXTEEN_NINE, FOUR_THREE
}

data class SeekPreview(
    val seekToTimeMs: Long,
    val totalTimeMs: Long,
    val isForward: Boolean,
    val deltaMs: Long
)

class PlayerViewModel(
    application: Application,
    private val repository: VideoRepository
) : AndroidViewModel(application) {

    // ExoPlayer Instance
    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    // UI States
    private val _currentVideo = MutableStateFlow<LocalVideo?>(null)
    val currentVideo: StateFlow<LocalVideo?> = _currentVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _aspectRatioMode = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatioMode: StateFlow<AspectRatioMode> = _aspectRatioMode.asStateFlow()

    private val _currentTime = MutableStateFlow(0L)
    val currentTime: StateFlow<Long> = _currentTime.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Gestures HUD HUD (Volume / Brightness / Seek Preview)
    private val _volumeProgress = MutableStateFlow<Float?>(null) // null = hidden
    val volumeProgress: StateFlow<Float?> = _volumeProgress.asStateFlow()

    private val _brightnessProgress = MutableStateFlow<Float?>(null) // null = hidden
    val brightnessProgress: StateFlow<Float?> = _brightnessProgress.asStateFlow()

    private val _seekPreview = MutableStateFlow<SeekPreview?>(null) // null = hidden
    val seekPreview: StateFlow<SeekPreview?> = _seekPreview.asStateFlow()

    // Subtitles Engine
    private val _subtitlesEnabled = MutableStateFlow(true)
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    private val _selectedSubtitleTrack = MutableStateFlow("None")
    val selectedSubtitleTrack: StateFlow<String> = _selectedSubtitleTrack.asStateFlow()

    private val _subtitleDelay = MutableStateFlow(0L) // in Ms
    val subtitleDelay: StateFlow<Long> = _subtitleDelay.asStateFlow()

    private val _currentSubtitleText = MutableStateFlow("")
    val currentSubtitleText: StateFlow<String> = _currentSubtitleText.asStateFlow()

    private var subtitleCues = listOf<SubtitleCue>()

    // Sleep Timer (Playit style)
    private val _sleepTimerMinutesLeft = MutableStateFlow(0) // 0 = off
    val sleepTimerMinutesLeft: StateFlow<Int> = _sleepTimerMinutesLeft.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var progressTrackingJob: Job? = null
    private var subtitleSyncJob: Job? = null

    // System Managers
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        // Observe changes to current video to track favorites dynamically
        viewModelScope.launch {
            _currentVideo.collectLatest { video ->
                if (video != null) {
                    repository.isFavorite(video.path).collectLatest { fav ->
                        _isFavorite.value = fav
                    }
                } else {
                    _isFavorite.value = false
                }
            }
        }
    }

    fun initPlayer(context: Context) {
        if (_player == null) {
            _player = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        _duration.value = duration.coerceAtLeast(0L)
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            startTrackingProgress()
                        } else {
                            stopTrackingProgress()
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        // Don't let a playback failure (corrupt file, unsupported codec,
                        // revoked permission, etc.) crash the whole app — just stop and log it.
                        android.util.Log.e("PlayerViewModel", "Playback error", error)
                        _isPlaying.value = false
                    }
                })
            }
            startSubtitleSyncLoop()
        }
    }

    fun playVideo(video: LocalVideo) {
        val p = _player ?: return
        _currentVideo.value = video

        // Setup MediaItem
        try {
            val mediaItem = MediaItem.fromUri(video.path)
            p.setMediaItem(mediaItem)
            p.prepare()
            p.playWhenReady = true
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Failed to load video: ${video.path}", e)
            return
        }

        // Load saved subtitle sync and positions
        viewModelScope.launch {
            val settings = repository.getSubtitleSettings(video.path)
            if (settings != null) {
                _subtitleDelay.value = settings.delayMs
                if (settings.subtitlePath == "demo") {
                    loadDemoSubtitles()
                }
            } else {
                _subtitleDelay.value = 0L
                // Default Big Buck Bunny and Sintel demo videos with automatic demo subtitles!
                if (video.isDemo) {
                    loadDemoSubtitles()
                } else {
                    unloadSubtitles()
                }
            }

            // Save to play history
            repository.insertHistory(video.path, video.title, video.duration, 0L)
        }

        // Reset speed & aspect ratio
        setSpeed(1.0f)
        setAspectRatio(AspectRatioMode.FIT)
    }

    private fun startTrackingProgress() {
        progressTrackingJob?.cancel()
        progressTrackingJob = viewModelScope.launch {
            while (isActive) {
                _player?.let {
                    _currentTime.value = it.currentPosition
                    _duration.value = it.duration.coerceAtLeast(0L)
                    
                    // Periodically update play history (every 5 seconds)
                    if (it.currentPosition % 5000 < 1000) {
                        _currentVideo.value?.let { video ->
                            repository.insertHistory(video.path, video.title, video.duration, it.currentPosition)
                        }
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopTrackingProgress() {
        progressTrackingJob?.cancel()
    }

    // Custom Subtitle Synchronization Loop
    private fun startSubtitleSyncLoop() {
        subtitleSyncJob?.cancel()
        subtitleSyncJob = viewModelScope.launch {
            while (isActive) {
                if (_subtitlesEnabled.value && subtitleCues.isNotEmpty()) {
                    val p = _player
                    if (p != null) {
                        val currentPlaybackTime = p.currentPosition
                        // Subtitle sync delay formula: adjustedPosition = currentPosition + subtitleDelay
                        // Positive delay shifts subtitles forward in time, negative shifts backward
                        val targetTime = currentPlaybackTime + _subtitleDelay.value
                        
                        val activeCue = subtitleCues.find { targetTime in it.start..it.end }
                        _currentSubtitleText.value = activeCue?.text ?: ""
                    }
                } else {
                    _currentSubtitleText.value = ""
                }
                delay(50) // High frequency sync for ultra-low latency subtitling
            }
        }
    }

    // Subtitle track loading
    fun loadDemoSubtitles() {
        subtitleCues = SubtitleParser.parseSrt(SubtitleParser.getDemoSubtitles())
        _selectedSubtitleTrack.value = "Demo English SRT"
        saveSubtitleSettings()
    }

    fun loadCustomSubtitles(srtContent: String, trackName: String) {
        subtitleCues = SubtitleParser.parseSrt(srtContent)
        _selectedSubtitleTrack.value = trackName
        saveSubtitleSettings()
    }

    fun unloadSubtitles() {
        subtitleCues = emptyList()
        _currentSubtitleText.value = ""
        _selectedSubtitleTrack.value = "None"
        saveSubtitleSettings()
    }

    fun setSubtitleDelay(delayMs: Long) {
        _subtitleDelay.value = delayMs
        saveSubtitleSettings()
    }

    private fun saveSubtitleSettings() {
        val video = _currentVideo.value ?: return
        viewModelScope.launch {
            val subPath = when (_selectedSubtitleTrack.value) {
                "None" -> null
                "Demo English SRT" -> "demo"
                else -> "custom"
            }
            repository.saveSubtitleSettings(video.path, subPath, _subtitleDelay.value)
        }
    }

    fun toggleSubtitlesEnabled() {
        _subtitlesEnabled.value = !_subtitlesEnabled.value
    }

    // Controls
    fun togglePlayPause() {
        val p = _player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    fun seekTo(timeMs: Long) {
        _player?.seekTo(timeMs)
        _currentTime.value = timeMs
    }

    fun skipForward() {
        val p = _player ?: return
        val target = (p.currentPosition + 10000).coerceAtMost(p.duration)
        seekTo(target)
    }

    fun skipBackward() {
        val p = _player ?: return
        val target = (p.currentPosition - 10000).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        _player?.playbackParameters = PlaybackParameters(speed)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _aspectRatioMode.value = mode
    }

    fun toggleMute() {
        val p = _player ?: return
        _isMuted.value = !_isMuted.value
        p.volume = if (_isMuted.value) 0.0f else 1.0f
    }

    fun toggleFavorite() {
        val video = _currentVideo.value ?: return
        viewModelScope.launch {
            repository.toggleFavorite(video.path, video.title, video.duration, _isFavorite.value)
        }
    }

    fun toggleLock() {
        _isLocked.value = !_isLocked.value
    }

    // Gesture Helpers (Volume, Brightness, seeking)
    fun adjustVolumeGesture(deltaProgress: Float) {
        if (_isLocked.value) return
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        // Calculate new volume based on vertical scroll delta
        val deltaVol = deltaProgress * maxVol
        val targetVol = (currentVol + deltaVol).coerceIn(0.0f, maxVol.toFloat()).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)

        _volumeProgress.value = targetVol.toFloat() / maxVol.toFloat()
        viewModelScope.launch {
            delay(1500)
            if (_volumeProgress.value == targetVol.toFloat() / maxVol.toFloat()) {
                _volumeProgress.value = null
            }
        }
    }

    fun adjustBrightnessGesture(deltaProgress: Float, currentActivityBrightness: Float): Float {
        if (_isLocked.value) return currentActivityBrightness
        
        // Default window brightness is usually -1.0, normalize it to 0.5 for start
        val initialVal = if (currentActivityBrightness < 0) 0.5f else currentActivityBrightness
        val targetBrightness = (initialVal + deltaProgress).coerceIn(0.01f, 1.0f)
        
        _brightnessProgress.value = targetBrightness
        viewModelScope.launch {
            delay(1500)
            if (_brightnessProgress.value == targetBrightness) {
                _brightnessProgress.value = null
            }
        }
        return targetBrightness
    }

    fun seekGesture(deltaXFraction: Float) {
        if (_isLocked.value) return
        val p = _player ?: return
        val current = p.currentPosition
        val total = p.duration
        if (total <= 0) return

        // Swipe complete screen moves the seek bar by 3 minutes (180 seconds)
        val deltaMs = (deltaXFraction * 180000L).toLong()
        val target = (current + deltaMs).coerceIn(0L, total)

        _seekPreview.value = SeekPreview(
            seekToTimeMs = target,
            totalTimeMs = total,
            isForward = deltaMs > 0,
            deltaMs = Math.abs(deltaMs)
        )
    }

    fun commitSeekGesture() {
        _seekPreview.value?.let {
            seekTo(it.seekToTimeMs)
        }
        _seekPreview.value = null
    }

    fun cancelSeekGesture() {
        _seekPreview.value = null
    }

    // Sleep Timer Logic
    fun startSleepTimer(minutes: Int) {
        _sleepTimerMinutesLeft.value = minutes
        sleepTimerJob?.cancel()
        
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                while (_sleepTimerMinutesLeft.value > 0) {
                    delay(60000) // Count down every minute
                    _sleepTimerMinutesLeft.value -= 1
                }
                // Sleep timer expired! Pause playback
                pausePlayback()
            }
        }
    }

    fun stopSleepTimer() {
        _sleepTimerMinutesLeft.value = 0
        sleepTimerJob?.cancel()
    }

    private fun pausePlayback() {
        _player?.pause()
    }

    // Lifecycle
    fun releasePlayer() {
        stopTrackingProgress()
        subtitleSyncJob?.cancel()
        _player?.release()
        _player = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}

class PlayerViewModelFactory(
    private val application: Application,
    private val repository: VideoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
