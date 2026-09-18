package com.hana.spindle.lite.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Native SQLite helper for Spindle Lite.
 * Avoids Room/KAPT runtime overhead to fit within KitKat's strict memory budget.
 */
class LiteDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "spindle_lite.db"
        const val DATABASE_VERSION = 1

        const val TABLE_TRACKS = "tracks"
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_ARTIST = "artist"
        const val COL_ALBUM = "album"
        const val COL_DURATION_MS = "duration_ms"
        const val COL_FILE_PATH = "file_path"
        const val COL_FORMAT = "format"
        const val COL_BITRATE = "bitrate"
        const val COL_SAMPLE_RATE = "sample_rate"

        @Volatile
        private var INSTANCE: LiteDbHelper? = null

        fun getInstance(context: Context): LiteDbHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiteDbHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_TRACKS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT,
                $COL_ALBUM TEXT,
                $COL_DURATION_MS INTEGER,
                $COL_FILE_PATH TEXT UNIQUE,
                $COL_FORMAT TEXT,
                $COL_BITRATE INTEGER,
                $COL_SAMPLE_RATE INTEGER
            );
        """.trimIndent()

        val createIndexQuery = "CREATE INDEX idx_artist_album ON $TABLE_TRACKS($COL_ARTIST, $COL_ALBUM);"

        db.execSQL(createTableQuery)
        db.execSQL(createIndexQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRACKS")
        onCreate(db)
    }

    /**
     * Inserts a list of tracks in a single atomic transaction for maximum indexing speed.
     */
    fun insertTracksBatch(tracks: List<Track>): Int {
        val db = writableDatabase
        var insertedCount = 0
        db.beginTransaction()
        try {
            val values = ContentValues()
            for (track in tracks) {
                values.clear()
                values.put(COL_TITLE, track.title)
                values.put(COL_ARTIST, track.artist)
                values.put(COL_ALBUM, track.album)
                values.put(COL_DURATION_MS, track.durationMs)
                values.put(COL_FILE_PATH, track.filePath)
                values.put(COL_FORMAT, track.format)
                values.put(COL_BITRATE, track.bitrate)
                values.put(COL_SAMPLE_RATE, track.sampleRate)

                val result = db.insertWithOnConflict(
                    TABLE_TRACKS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (result != -1L) insertedCount++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return insertedCount
    }

    /**
     * Returns all indexed tracks sorted by artist, album, and title.
     */
    fun getAllTracks(): List<Track> {
        val trackList = ArrayList<Track>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_TRACKS,
            null,
            null,
            null,
            null,
            null,
            "$COL_ARTIST ASC, $COL_ALBUM ASC, $COL_TITLE ASC"
        )

        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_ID)
            val titleIdx = it.getColumnIndexOrThrow(COL_TITLE)
            val artistIdx = it.getColumnIndexOrThrow(COL_ARTIST)
            val albumIdx = it.getColumnIndexOrThrow(COL_ALBUM)
            val durationIdx = it.getColumnIndexOrThrow(COL_DURATION_MS)
            val pathIdx = it.getColumnIndexOrThrow(COL_FILE_PATH)
            val formatIdx = it.getColumnIndexOrThrow(COL_FORMAT)
            val bitrateIdx = it.getColumnIndexOrThrow(COL_BITRATE)
            val sampleRateIdx = it.getColumnIndexOrThrow(COL_SAMPLE_RATE)

            while (it.moveToNext()) {
                trackList.add(
                    Track(
                        id = it.getLong(idIdx),
                        title = it.getString(titleIdx) ?: "Unknown Title",
                        artist = it.getString(artistIdx) ?: "Unknown Artist",
                        album = it.getString(albumIdx) ?: "Spindle Vault",
                        durationMs = it.getLong(durationIdx),
                        filePath = it.getString(pathIdx),
                        format = it.getString(formatIdx) ?: "AUDIO",
                        bitrate = it.getInt(bitrateIdx),
                        sampleRate = it.getInt(sampleRateIdx)
                    )
                )
            }
        }
        return trackList
    }

    fun getTrackCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_TRACKS", null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }
}
