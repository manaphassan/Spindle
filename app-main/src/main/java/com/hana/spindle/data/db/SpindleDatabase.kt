package com.hana.spindle.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SpindleDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    fun songDao(): TrackDao = trackDao()
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: SpindleDatabase? = null

        fun getInstance(context: Context): SpindleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpindleDatabase::class.java,
                    "spindle_music.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
