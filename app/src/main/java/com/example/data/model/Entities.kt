package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val videoPath: String,
    val videoTitle: String,
    val duration: Long,
    val lastPosition: Long,
    val lastPlayed: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteVideo(
    @PrimaryKey val videoPath: String,
    val videoTitle: String,
    val duration: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_videos")
data class PlaylistVideo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val videoPath: String,
    val videoTitle: String,
    val duration: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "subtitle_settings")
data class SubtitleSettings(
    @PrimaryKey val videoPath: String,
    val subtitlePath: String?,
    val delayMs: Long = 0L // Positive means subtitles delayed, negative means advanced
)

data class LocalVideo(
    val path: String,
    val title: String,
    val duration: Long,
    val size: Long = 0L,
    val folder: String = "Download",
    val isDemo: Boolean = false
)
