package com.hana.spindle.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        TrackFtsEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 6,
    exportSchema = true
)
abstract class SpindleDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    fun songDao(): TrackDao = trackDao()
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: SpindleDatabase? = null

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `songs_fts` USING FTS4(
                        content=`songs`,
                        `title`,
                        `artist`,
                        `album`,
                        `composer`,
                        `albumArtist`,
                        `genre`
                    )
                """)
                db.execSQL("""
                    INSERT INTO `songs_fts`(`docid`, `title`, `artist`, `album`, `composer`, `albumArtist`, `genre`)
                    SELECT `rowid`, `title`, `artist`, `album`, `composer`, `albumArtist`, `genre` FROM `songs`
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_songs_fts_BEFORE_UPDATE BEFORE UPDATE ON `songs` BEGIN DELETE FROM `songs_fts` WHERE `docid`=OLD.`rowid`; END;
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_songs_fts_BEFORE_DELETE BEFORE DELETE ON `songs` BEGIN DELETE FROM `songs_fts` WHERE `docid`=OLD.`rowid`; END;
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_songs_fts_AFTER_UPDATE AFTER UPDATE ON `songs` BEGIN INSERT INTO `songs_fts`(`docid`, `title`, `artist`, `album`, `composer`, `albumArtist`, `genre`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`artist`, NEW.`album`, NEW.`composer`, NEW.`albumArtist`, NEW.`genre`); END;
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_songs_fts_AFTER_INSERT AFTER INSERT ON `songs` BEGIN INSERT INTO `songs_fts`(`docid`, `title`, `artist`, `album`, `composer`, `albumArtist`, `genre`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`artist`, NEW.`album`, NEW.`composer`, NEW.`albumArtist`, NEW.`genre`); END;
                """)
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `hasCoverArt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): SpindleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpindleDatabase::class.java,
                    "spindle_music.db"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                // Only pre-FTS schemas (v1-3, never released with migrations) may be rebuilt.
                // Any other missing migration must fail loudly instead of wiping the library.
                .fallbackToDestructiveMigrationFrom(1, 2, 3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
