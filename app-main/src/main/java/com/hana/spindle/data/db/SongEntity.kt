package com.hana.spindle.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity representing a single audio track on storage.
 * Indexed by path, album, artist, title, rating, format, and year for instant searching and sorting.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["album"]),
        Index(value = ["artist"]),
        Index(value = ["title"]),
        Index(value = ["rating"]),
        Index(value = ["year"]),
        Index(value = ["fileFormat"])
    ]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val path: String,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String? = null,
    val bitDepth: Int = 16,
    val sampleRate: Int = 44100,
    val fileFormat: String = "FLAC",
    val rating: Int = 0, // 0 to 5 stars
    val dateModified: Long = 0L,
    val bitrateKbps: Int = 0,
    val hasLyrics: Boolean = false,
    val channels: Int = 2,
    val discNumber: Int = 1
)
