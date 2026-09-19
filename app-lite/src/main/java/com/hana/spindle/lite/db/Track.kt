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
    val sampleRate: Int = 0,
    val bitDepth: Int = 0
) {
    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", min, sec)
        }

    val formatBadge: String
        get() {
            val upper = format.uppercase()
            val rateStr = when {
                sampleRate >= 192000 -> "192k"
                sampleRate >= 96000 -> "96k"
                sampleRate >= 88200 -> "88.2k"
                sampleRate >= 48000 -> "48k"
                sampleRate >= 44100 -> "44.1k"
                sampleRate > 0 -> "${sampleRate / 1000}k"
                else -> ""
            }
            val bitStr = if (bitrate > 0) "${bitrate}K" else ""

            return when {
                upper.contains("FLAC") -> {
                    val depthStr = if (bitDepth > 0) "${bitDepth}b/" else ""
                    if (rateStr.isNotEmpty()) "FLAC $depthStr$rateStr" else "FLAC 16/44.1"
                }
                upper.contains("WAV") -> {
                    val depthStr = if (bitDepth > 0) "${bitDepth}b/" else ""
                    if (rateStr.isNotEmpty()) "WAV $depthStr$rateStr" else "WAV PCM"
                }
                upper.contains("MP3") -> {
                    if (bitStr.isNotEmpty()) "MP3 $bitStr" else "MP3 320K"
                }
                upper.contains("AAC") || upper.contains("M4A") -> {
                    if (bitStr.isNotEmpty()) "AAC $bitStr" else "AAC LC"
                }
                upper.contains("OGG") -> {
                    if (bitStr.isNotEmpty()) "OGG $bitStr" else "OGG VORBIS"
                }
                else -> upper.ifEmpty { "AUDIO" }
            }
        }

    val tapeBiasType: String
        get() = when {
            format.contains("FLAC", ignoreCase = true) || format.contains("WAV", ignoreCase = true) ->
                "SPINDLE • TYPE IV METAL BIAS"
            (format.contains("MP3", ignoreCase = true) && (bitrate >= 256 || bitrate == 0)) ||
            format.contains("AAC", ignoreCase = true) || format.contains("M4A", ignoreCase = true) ->
                "SPINDLE • TYPE II HIGH BIAS (CrO2)"
            else ->
                "SPINDLE • TYPE I NORMAL BIAS (Fe2O3)"
        }
}
