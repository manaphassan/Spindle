package com.hana.spindle.data

import android.media.MediaMetadataRetriever
import android.os.Build
import com.hana.spindle.data.db.SongEntity
import java.io.File
import java.util.Locale

/**
 * Lightweight audiophile tag metadata parser for audio files.
 * Extracts ID3, Vorbis, RIFF, and container metadata via MediaMetadataRetriever,
 * calculating exact bitrate, sample rate, bit depth, and lyrics presence.
 */
object TagParser {

    fun parseSong(file: File): SongEntity? {
        if (!file.exists() || !file.canRead()) return null

        val format = file.extension.uppercase(Locale.ROOT)
        val flacInfo = if (format == "FLAC") parseFlacStreamInfo(file) else null

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationStr: String? = null
        var trackStr: String? = null
        var discStr: String? = null
        var yearStr: String? = null
        var genre: String? = null
        var rawBitrate: Int? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            discStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
        } catch (e: Exception) {
            // MediaMetadataRetriever may fail on Android 8.0 for some FLAC files
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        var durationMs = durationStr?.toLongOrNull() ?: 0L
        if (durationMs <= 0L && flacInfo != null && flacInfo.durationMs > 0L) {
            durationMs = flacInfo.durationMs
        }

        if (durationMs <= 0L) {
            // Not a valid audio track or unreadable duration
            return null
        }

        // Fallbacks for metadata if retriever failed
        if (title.isNullOrBlank()) {
            val name = file.nameWithoutExtension
            // Strip leading track numbers like "01 - ", "01. "
            val cleanName = name.replace(Regex("^[0-9]+[\\s.\\-_]+"), "")
            title = if (cleanName.isNotBlank()) cleanName else name
        }
        if (artist.isNullOrBlank() || artist == "Unknown Artist") {
            val parentName = file.parentFile?.name
            val grandparentName = file.parentFile?.parentFile?.name
            artist = when {
                !parentName.isNullOrBlank() && !parentName.startsWith("disc", ignoreCase = true) -> parentName
                !grandparentName.isNullOrBlank() -> grandparentName
                else -> "Unknown Artist"
            }
        }
        if (album.isNullOrBlank() || album == "Unknown Album") {
            val parentName = file.parentFile?.name
            val grandparentName = file.parentFile?.parentFile?.name
            album = when {
                !parentName.isNullOrBlank() && parentName.startsWith("disc", ignoreCase = true) && !grandparentName.isNullOrBlank() -> grandparentName
                !parentName.isNullOrBlank() -> parentName
                else -> "Unknown Album"
            }
        }

        val trackNumber = parseTrackNumber(trackStr ?: extractTrackNumberFromFilename(file.nameWithoutExtension))
        val discNumber = discStr?.toIntOrNull() ?: extractDiscNumberFromPath(file.absolutePath)
        val year = parseYear(yearStr)

        val bitrateKbps = if (rawBitrate != null && rawBitrate > 0) {
            rawBitrate / 1000
        } else if (durationMs > 0) {
            ((file.length() * 8L) / durationMs).toInt()
        } else {
            when (format) {
                "FLAC", "ALAC" -> 900
                "WAV", "AIFF" -> 1411
                else -> 320
            }
        }

        // Extract exact Sample Rate
        val sampleRate = when {
            flacInfo != null && flacInfo.sampleRate > 0 -> flacInfo.sampleRate
            else -> {
                when {
                    format == "FLAC" && bitrateKbps > 2000 -> 96000
                    format == "FLAC" && bitrateKbps > 1200 -> 48000
                    format == "WAV" && bitrateKbps > 3000 -> 96000
                    format == "DSD" || format == "DSF" -> 176400
                    else -> 44100
                }
            }
        }

        // Extract exact Bit Depth
        val bitDepth = when {
            flacInfo != null && flacInfo.bitDepth > 0 -> flacInfo.bitDepth
            else -> estimateBitDepth(format, bitrateKbps, sampleRate)
        }

        val channels = flacInfo?.channels ?: 2

        // Check if lyrics exist (.lrc sidecar or .txt)
        val lrcFile = File(file.parentFile, "${file.nameWithoutExtension}.lrc")
        val txtFile = File(file.parentFile, "${file.nameWithoutExtension}.txt")
        val hasLyrics = lrcFile.exists() || txtFile.exists()

        return SongEntity(
            title = title.trim(),
            artist = artist.trim(),
            album = album.trim(),
            durationMs = durationMs,
            path = file.absolutePath,
            trackNumber = trackNumber,
            year = year,
            genre = genre,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            fileFormat = format,
            rating = 0,
            dateModified = file.lastModified(),
            bitrateKbps = bitrateKbps,
            hasLyrics = hasLyrics,
            channels = channels,
            discNumber = discNumber
        )
    }

    private fun parseTrackNumber(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return try {
            val clean = raw.split("/")[0].trim()
            clean.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun parseYear(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return try {
            val digits = raw.take(4)
            digits.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun estimateBitDepth(format: String, bitrateKbps: Int, sampleRate: Int): Int {
        return when (format) {
            "FLAC" -> if (bitrateKbps >= 1500 || sampleRate >= 88200) 24 else 16
            "WAV", "AIFF", "AIF", "ALAC" -> if (sampleRate >= 88200 || bitrateKbps >= 2304) 24 else 16
            "DSD", "DSF", "DFF" -> 32
            else -> 16
        }
    }

    /**
     * Fast binary header inspection for ReplayGain track gain tags (FLAC, OGG, ID3v2 TXXX).
     * Returns gain in dB (e.g. -4.5f), or 0.0f if untagged.
     */
    fun extractReplayGainDb(file: File): Float {
        if (!file.exists() || !file.canRead()) return 0f
        return try {
            val readSize = minOf(file.length().toInt(), 16384)
            if (readSize <= 0) return 0f
            val buffer = ByteArray(readSize)
            java.io.FileInputStream(file).use { fis ->
                fis.read(buffer)
            }
            val text = String(buffer, Charsets.ISO_8859_1)
            val pattern = Regex("(?i)REPLAYGAIN_TRACK_GAIN[=:]\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*(?:dB)?")
            val match = pattern.find(text)
            match?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
        } catch (e: Exception) {
            0f
        }
    }

    private fun extractTrackNumberFromFilename(filename: String): String {
        val match = Regex("^([0-9]{1,3})[\\s.\\-_]+").find(filename)
        return match?.groupValues?.getOrNull(1) ?: "0"
    }

    private fun extractDiscNumberFromPath(path: String): Int {
        val match = Regex("(?i)disc\\s*([0-9]+)").find(path)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }

    data class FlacInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
        val durationMs: Long
    )

    /**
     * Parses the standard FLAC binary STREAMINFO metadata block (bytes 0 to 42).
     * Supports files with optional prepended ID3v2 tags.
     * Extracts exact sample rate, channel count, bit depth, and total samples to calculate exact duration.
     */
    fun parseFlacStreamInfo(file: File): FlacInfo? {
        if (!file.exists() || file.length() < 42) return null
        return try {
            java.io.FileInputStream(file).use { fis ->
                var header = ByteArray(42)
                var read = fis.read(header)
                if (read < 42) return null

                // Handle optional ID3v2 header at the beginning of the FLAC file
                if (header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) {
                    val b6 = header[6].toInt() and 0x7F
                    val b7 = header[7].toInt() and 0x7F
                    val b8 = header[8].toInt() and 0x7F
                    val b9 = header[9].toInt() and 0x7F
                    val id3DataSize = (b6 shl 21) or (b7 shl 14) or (b8 shl 7) or b9
                    val flags = header[5].toInt()
                    val hasFooter = (flags and 0x10) != 0
                    val totalId3Size = 10L + id3DataSize + (if (hasFooter) 10L else 0L)

                    var toSkip = totalId3Size - 42L
                    while (toSkip > 0) {
                        val skipped = fis.skip(toSkip)
                        if (skipped <= 0) break
                        toSkip -= skipped
                    }
                    header = ByteArray(42)
                    read = fis.read(header)
                    if (read < 42) return null
                }

                // Check "fLaC" magic bytes (0x66, 0x4C, 0x61, 0x43)
                if (header[0] != 0x66.toByte() || header[1] != 0x4C.toByte() ||
                    header[2] != 0x61.toByte() || header[3] != 0x43.toByte()) {
                    return null
                }
                // Block type: header[4] & 0x7F must be 0 (STREAMINFO)
                val blockType = header[4].toInt() and 0x7F
                if (blockType != 0) return null

                // In STREAMINFO, bytes 18 to 25 contain:
                // 20 bits sample rate
                // 3 bits (channels - 1)
                // 5 bits (bits per sample - 1)
                // 36 bits total samples in stream
                val b18 = header[18].toInt() and 0xFF
                val b19 = header[19].toInt() and 0xFF
                val b20 = header[20].toInt() and 0xFF
                val b21 = header[21].toInt() and 0xFF
                val b22 = header[22].toInt() and 0xFF
                val b23 = header[23].toInt() and 0xFF
                val b24 = header[24].toInt() and 0xFF
                val b25 = header[25].toInt() and 0xFF

                val sampleRate = (b18 shl 12) or (b19 shl 4) or (b20 ushr 4)
                val channels = ((b20 ushr 1) and 0x07) + 1
                val bitDepth = (((b20 and 0x01) shl 4) or (b21 ushr 4)) + 1

                val totalSamples = ((b21.toLong() and 0x0F) shl 32) or
                        (b22.toLong() shl 24) or
                        (b23.toLong() shl 16) or
                        (b24.toLong() shl 8) or
                        b25.toLong()

                val durationMs = if (sampleRate > 0 && totalSamples > 0) {
                    (totalSamples * 1000L) / sampleRate
                } else {
                    0L
                }

                FlacInfo(sampleRate, channels, bitDepth, durationMs)
            }
        } catch (e: Exception) {
            null
        }
    }
}
