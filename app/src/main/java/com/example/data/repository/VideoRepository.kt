package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.data.db.VideoDao
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(
    private val context: Context,
    private val videoDao: VideoDao
) {
    // DB flows
    val history: Flow<List<PlaybackHistory>> = videoDao.getHistory()
    val favorites: Flow<List<FavoriteVideo>> = videoDao.getFavorites()
    val playlists: Flow<List<Playlist>> = videoDao.getPlaylists()

    fun isFavorite(videoPath: String): Flow<Boolean> = videoDao.isFavorite(videoPath)
    fun getPlaylistVideos(playlistId: Int): Flow<List<PlaylistVideo>> = videoDao.getPlaylistVideos(playlistId)

    // DB Suspend functions
    suspend fun insertHistory(videoPath: String, title: String, duration: Long, position: Long) {
        videoDao.insertHistory(
            PlaybackHistory(
                videoPath = videoPath,
                videoTitle = title,
                duration = duration,
                lastPosition = position
            )
        )
    }

    suspend fun deleteHistory(videoPath: String) = videoDao.deleteHistoryByPath(videoPath)
    suspend fun clearHistory() = videoDao.clearHistory()

    suspend fun toggleFavorite(videoPath: String, title: String, duration: Long, isFav: Boolean) {
        if (isFav) {
            videoDao.removeFavorite(videoPath)
        } else {
            videoDao.addFavorite(
                FavoriteVideo(
                    videoPath = videoPath,
                    videoTitle = title,
                    duration = duration
                )
            )
        }
    }

    suspend fun createPlaylist(name: String): Long = videoDao.createPlaylist(Playlist(name = name))
    suspend fun deletePlaylist(id: Int) = videoDao.deletePlaylist(id)

    suspend fun addVideoToPlaylist(playlistId: Int, videoPath: String, title: String, duration: Long) {
        videoDao.addVideoToPlaylist(
            PlaylistVideo(
                playlistId = playlistId,
                videoPath = videoPath,
                videoTitle = title,
                duration = duration
            )
        )
    }

    suspend fun removeVideoFromPlaylist(playlistId: Int, videoPath: String) {
        videoDao.removeVideoFromPlaylist(playlistId, videoPath)
    }

    suspend fun clearPlaylist(playlistId: Int) = videoDao.clearPlaylistVideos(playlistId)

    suspend fun getSubtitleSettings(videoPath: String): SubtitleSettings? {
        return videoDao.getSubtitleSettings(videoPath)
    }

    suspend fun saveSubtitleSettings(videoPath: String, subtitlePath: String?, delayMs: Long) {
        videoDao.saveSubtitleSettings(
            SubtitleSettings(
                videoPath = videoPath,
                subtitlePath = subtitlePath,
                delayMs = delayMs
            )
        )
    }

    // Local Video Scanning (MediaStore)
    suspend fun scanLocalVideos(): List<LocalVideo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideo>()
        val uri: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        try {
            val cursor: Cursor? = context.contentResolver.query(
                uri, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (it.moveToNext()) {
                    val path = it.getString(dataCol)
                    val title = it.getString(titleCol)
                    val duration = it.getLong(durationCol)
                    val size = it.getLong(sizeCol)

                    // Resolve folder name
                    val file = File(path)
                    val folderName = file.parentFile?.name ?: "Unknown"

                    list.add(
                        LocalVideo(
                            path = path,
                            title = title,
                            duration = duration,
                            size = size,
                            folder = folderName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error scanning local videos", e)
        }

        list
    }

    // Demo/Online videos for fallback testing in the AI Studio Browser Emulator
    fun getDemoVideos(): List<LocalVideo> {
        return listOf(
            LocalVideo(
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                title = "Big Buck Bunny (Animation)",
                duration = 596000L, // 9 min 56 s
                size = 158 * 1024 * 1024L,
                folder = "Demo Stream",
                isDemo = true
            ),
            LocalVideo(
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                title = "Sintel (Fantasy Adventure)",
                duration = 888000L, // 14 min 48 s
                size = 125 * 1024 * 1024L,
                folder = "Demo Stream",
                isDemo = true
            ),
            LocalVideo(
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                title = "Tears of Steel (Sci-Fi VFX)",
                duration = 734000L, // 12 min 14 s
                size = 110 * 1024 * 1024L,
                folder = "Demo Stream",
                isDemo = true
            ),
            LocalVideo(
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                title = "Elephants Dream (Open Movie)",
                duration = 653000L, // 10 min 53 s
                size = 90 * 1024 * 1024L,
                folder = "Demo Stream",
                isDemo = true
            )
        )
    }
}
