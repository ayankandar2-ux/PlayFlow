package com.example.data.db

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    // History
    @Query("SELECT * FROM playback_history ORDER BY lastPlayed DESC")
    fun getHistory(): Flow<List<PlaybackHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistory)

    @Query("DELETE FROM playback_history WHERE videoPath = :videoPath")
    suspend fun deleteHistoryByPath(videoPath: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    // Favorites
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getFavorites(): Flow<List<FavoriteVideo>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoPath = :videoPath)")
    fun isFavorite(videoPath: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteVideo)

    @Query("DELETE FROM favorites WHERE videoPath = :videoPath")
    suspend fun removeFavorite(videoPath: String)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Int)

    // Playlist Videos
    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getPlaylistVideos(playlistId: Int): Flow<List<PlaylistVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addVideoToPlaylist(video: PlaylistVideo)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoPath = :videoPath")
    suspend fun removeVideoFromPlaylist(playlistId: Int, videoPath: String)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun clearPlaylistVideos(playlistId: Int)

    // Subtitle Settings
    @Query("SELECT * FROM subtitle_settings WHERE videoPath = :videoPath")
    suspend fun getSubtitleSettings(videoPath: String): SubtitleSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSubtitleSettings(settings: SubtitleSettings)
}

@Database(
    entities = [
        PlaybackHistory::class,
        FavoriteVideo::class,
        Playlist::class,
        PlaylistVideo::class,
        SubtitleSettings::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VideoDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
}
