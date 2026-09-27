package com.hana.spindle.data.db

import androidx.room.Entity
import androidx.room.Fts4

/**
 * SQLite FTS4 Virtual Table Entity for high-speed full-text search across all audio metadata.
 * Uses TrackEntity ("songs") as external content table.
 *
 * Indexed fields:
 * - title
 * - artist
 * - album
 * - composer
 * - albumArtist
 * - genre
 *
 * Conforms to §6.6 of Spindle Architecture & Remediation Plan.
 */
@Entity(tableName = "songs_fts")
@Fts4(contentEntity = TrackEntity::class)
data class TrackFtsEntity(
    val title: String,
    val artist: String,
    val album: String,
    val composer: String?,
    val albumArtist: String?,
    val genre: String?
)
