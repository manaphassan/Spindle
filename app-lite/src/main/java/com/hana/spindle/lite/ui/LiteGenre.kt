package com.hana.spindle.lite.ui

import com.hana.spindle.lite.db.Track

/**
 * Immutable data representation of a genre in Spindle Lite's library.
 * Zero database overhead; derived directly from scanned track metadata.
 */
data class LiteGenre(
    val name: String,
    val tracks: List<Track>,
    val albumCount: Int = tracks.map { it.album.ifBlank { "Unknown Album" } }.distinct().size,
    val totalDurationMs: Long = tracks.sumOf { it.durationMs }
) {
    val formattedDuration: String
        get() {
            val totalSec = totalDurationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", min, sec)
        }
}
