package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.db.VideoDatabase
import com.example.data.repository.VideoRepository

class MainApplication : Application() {
    lateinit var database: VideoDatabase
        private set
    lateinit var repository: VideoRepository
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Room Database
        database = Room.databaseBuilder(
            applicationContext,
            VideoDatabase::class.java,
            "vlc_playit_database"
        )
        .fallbackToDestructiveMigration()
        .build()

        // Initialize Repository
        repository = VideoRepository(applicationContext, database.videoDao())
    }
}
