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
        const val DATABASE_VERSION = 2

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
        const val COL_BIT_DEPTH = "bit_depth"

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
                $COL_SAMPLE_RATE INTEGER,
                $COL_BIT_DEPTH INTEGER DEFAULT 0
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
                values.put(COL_BIT_DEPTH, track.bitDepth)

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
            while (it.moveToNext()) {
                trackList.add(readTrackFromCursor(it))
            }
        }
        return trackList
    }

    /**
     * Looks up an indexed track by absolute file path.
     */
    fun getTrackByPath(filePath: String): Track? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TRACKS,
            null,
            "$COL_FILE_PATH = ?",
            arrayOf(filePath),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return readTrackFromCursor(it)
            }
        }
        return null
    }

    /**
     * Returns all indexed tracks located in a specific directory (prefix match on file path).
     */
    fun getTracksInFolder(folderPath: String): List<Track> {
        val trackList = ArrayList<Track>()
        val db = readableDatabase
        val folderPrefix = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
        val cursor = db.query(
            TABLE_TRACKS,
            null,
            "$COL_FILE_PATH LIKE ? AND $COL_FILE_PATH NOT LIKE ?",
            arrayOf("$folderPrefix%", "$folderPrefix%/%"),
            null,
            null,
            "$COL_TITLE ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                trackList.add(readTrackFromCursor(it))
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

    private fun readTrackFromCursor(cursor: Cursor): Track {
        val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
        val titleIdx = cursor.getColumnIndexOrThrow(COL_TITLE)
        val artistIdx = cursor.getColumnIndexOrThrow(COL_ARTIST)
        val albumIdx = cursor.getColumnIndexOrThrow(COL_ALBUM)
        val durationIdx = cursor.getColumnIndexOrThrow(COL_DURATION_MS)
        val pathIdx = cursor.getColumnIndexOrThrow(COL_FILE_PATH)
        val formatIdx = cursor.getColumnIndexOrThrow(COL_FORMAT)
        val bitrateIdx = cursor.getColumnIndexOrThrow(COL_BITRATE)
        val sampleRateIdx = cursor.getColumnIndexOrThrow(COL_SAMPLE_RATE)
        val bitDepthIdx = cursor.getColumnIndex(COL_BIT_DEPTH)

        return Track(
            id = cursor.getLong(idIdx),
            title = cursor.getString(titleIdx) ?: "Unknown Title",
            artist = cursor.getString(artistIdx) ?: "Unknown Artist",
            album = cursor.getString(albumIdx) ?: "Spindle Vault",
            durationMs = cursor.getLong(durationIdx),
            filePath = cursor.getString(pathIdx),
            format = cursor.getString(formatIdx) ?: "AUDIO",
            bitrate = cursor.getInt(bitrateIdx),
            sampleRate = cursor.getInt(sampleRateIdx),
            bitDepth = if (bitDepthIdx != -1) cursor.getInt(bitDepthIdx) else 0
        )
    }
}
