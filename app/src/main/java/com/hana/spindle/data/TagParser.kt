package com.hana.spindle.data

import android.media.MediaMetadataRetriever
import com.hana.spindle.data.db.SongEntity
import java.io.File
import java.util.Locale

/**
 * Lightweight tag metadata parser for audio files.
 * Extracts ID3, Vorbis, and container metadata via MediaMetadataRetriever.
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

            val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val year = parseYear(yearStr)

            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)

            val format = file.extension.uppercase(Locale.ROOT)

            SongEntity(
                title = title.trim(),
                artist = artist.trim(),
                album = album.trim(),
                durationMs = durationMs,
                path = file.absolutePath,
                trackNumber = trackNumber,
                year = year,
                genre = genre,
                bitDepth = estimateBitDepth(format),
                sampleRate = 44100, // Default baseline, updated during active playback decoding
                fileFormat = format,
                rating = 0,
                dateModified = file.lastModified()
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
            // Handles "01/12" format
            val clean = raw.split("/")[0].trim()
            clean.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun parseYear(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return try {
            // Handles "1973-03-01" format
            val digits = raw.take(4)
            digits.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun estimateBitDepth(format: String): Int {
        return when (format) {
            "FLAC", "WAV", "AIFF", "AIF", "ALAC" -> 24
            "DSD", "DSF", "DFF" -> 32
            else -> 16
        }
    }
}
