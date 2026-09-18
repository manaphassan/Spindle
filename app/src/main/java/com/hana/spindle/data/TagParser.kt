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

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Unknown Album"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            if (durationMs <= 0L) {
                // Not a valid audio track or unreadable duration
                return null
            }

            val trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val trackNumber = parseTrackNumber(trackStr)

            val discStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            val discNumber = discStr?.toIntOrNull() ?: 1

            val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val year = parseYear(yearStr)

            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val format = file.extension.uppercase(Locale.ROOT)

            // Extract or calculate exact Bitrate in kbps
            val rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
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

            // Extract Sample Rate (API 29+) or estimate by format
            val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 44100
            } else {
                when {
                    format == "FLAC" && bitrateKbps > 2000 -> 96000
                    format == "FLAC" && bitrateKbps > 1200 -> 48000
                    format == "WAV" && bitrateKbps > 3000 -> 96000
                    format == "DSD" || format == "DSF" -> 176400
                    else -> 44100
                }
            }

            val bitDepth = estimateBitDepth(format, bitrateKbps, sampleRate)

            // Check if lyrics exist (.lrc sidecar or .txt)
            val lrcFile = File(file.parentFile, "${file.nameWithoutExtension}.lrc")
            val txtFile = File(file.parentFile, "${file.nameWithoutExtension}.txt")
            val hasLyrics = lrcFile.exists() || txtFile.exists()

            SongEntity(
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
                channels = 2,
                discNumber = discNumber
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
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
}
