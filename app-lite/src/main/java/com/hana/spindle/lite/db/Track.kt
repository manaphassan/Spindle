package com.hana.spindle.lite.db

/**
 * Immutable, ultra-lightweight track entity for Spindle Lite.
 * Optimized for low heap overhead on 512MB RAM devices.
 */
data class Track(
    val id: Long = 0L,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    val format: String,
    val bitrate: Int = 0,
    val sampleRate: Int = 0
) {
    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", min, sec)
        }

    val formatBadge: String
        get() = when {
            format.contains("FLAC", ignoreCase = true) -> "FLAC 16/44.1"
            format.contains("MP3", ignoreCase = true) -> "MP3 320K"
            format.contains("WAV", ignoreCase = true) -> "WAV PCM"
            format.contains("AAC", ignoreCase = true) -> "AAC LC"
            format.contains("OGG", ignoreCase = true) -> "OGG VORBIS"
            else -> format.uppercase()
        }
}
