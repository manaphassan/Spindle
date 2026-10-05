package com.hana.spindle.data

import android.media.MediaMetadataRetriever
import android.os.Build
import com.hana.spindle.data.db.TrackEntity
import java.io.File
import java.util.Locale

/**
 * Lightweight audiophile tag metadata parser for audio files.
 * Extracts ID3, Vorbis, RIFF, and container metadata via MediaMetadataRetriever,
 * calculating exact bitrate, sample rate, bit depth, and lyrics presence.
 */
object TagParser {

    fun parseTrack(file: File): TrackEntity? {
        if (!file.exists() || !file.canRead()) return null

        val format = file.extension.uppercase(Locale.ROOT)
        val flacInfo = if (format == "FLAC") parseFlacStreamInfo(file) else null
        val wavInfo = if (format == "WAV") parseWavStreamInfo(file) else null
        val aiffInfo = if (format == "AIFF" || format == "AIF") parseAiffStreamInfo(file) else null
        val dsfInfo = if (format == "DSF" || format == "DSD") parseDsfStreamInfo(file) else null

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var composer: String? = null
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
            albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: albumArtist
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            discStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
        } catch (e: Exception) {
            // MediaMetadataRetriever may fail on Android 8.0 for some FLAC/AIFF/DSF files
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        var durationMs = durationStr?.toLongOrNull() ?: 0L
        if (durationMs <= 0L && flacInfo != null && flacInfo.durationMs > 0L) {
            durationMs = flacInfo.durationMs
        }
        if (durationMs <= 0L && aiffInfo != null && aiffInfo.durationMs > 0L) {
            durationMs = aiffInfo.durationMs
        }
        if (durationMs <= 0L && dsfInfo != null && dsfInfo.durationMs > 0L) {
            durationMs = dsfInfo.durationMs
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
            wavInfo != null && wavInfo.sampleRate > 0 -> wavInfo.sampleRate
            aiffInfo != null && aiffInfo.sampleRate > 0 -> aiffInfo.sampleRate
            dsfInfo != null && dsfInfo.sampleRate > 0 -> dsfInfo.sampleRate
            else -> {
                when {
                    format == "FLAC" && bitrateKbps > 2000 -> 96000
                    format == "FLAC" && bitrateKbps > 1200 -> 48000
                    format == "WAV" && bitrateKbps > 3000 -> 96000
                    format == "DSD" || format == "DSF" -> 2822400
                    else -> 44100
                }
            }
        }

        // Extract exact Bit Depth
        val bitDepth = when {
            flacInfo != null && flacInfo.bitDepth > 0 -> flacInfo.bitDepth
            wavInfo != null && wavInfo.bitDepth > 0 -> wavInfo.bitDepth
            aiffInfo != null && aiffInfo.bitDepth > 0 -> aiffInfo.bitDepth
            dsfInfo != null && dsfInfo.bitDepth > 0 -> dsfInfo.bitDepth
            else -> estimateBitDepth(format, bitrateKbps, sampleRate)
        }

        val channels = flacInfo?.channels ?: wavInfo?.channels ?: aiffInfo?.channels ?: dsfInfo?.channels ?: 2

        // Check if lyrics exist (.lrc sidecar, .txt, or embedded ID3/Vorbis tags)
        val lrcFile = File(file.parentFile, "${file.nameWithoutExtension}.lrc")
        val txtFile = File(file.parentFile, "${file.nameWithoutExtension}.txt")
        val hasLyrics = lrcFile.exists() || txtFile.exists() || EmbeddedLyricsExtractor.hasLyrics(file)

        return TrackEntity(
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
            isFavorite = false,
            albumArtist = albumArtist?.trim(),
            dateAdded = System.currentTimeMillis(),
            playCount = 0,
            lastPlayedAt = null,
            composer = composer?.trim(),
            dateModified = file.lastModified(),
            bitrateKbps = bitrateKbps,
            hasLyrics = hasLyrics,
            channels = channels,
            discNumber = discNumber
        )
    }

    @Deprecated("Use parseTrack instead", ReplaceWith("parseTrack(file)"))
    fun parseSong(file: File): TrackEntity? = parseTrack(file)

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

    data class ReplayGainInfo(
        val trackGainDb: Float = 0f,
        val albumGainDb: Float = 0f
    )

    /**
     * Inspects audio file header for both Track Gain and Album Gain ReplayGain tags.
     */
    fun extractReplayGainInfo(file: File): ReplayGainInfo {
        if (!file.exists() || !file.canRead()) return ReplayGainInfo()
        return try {
            val readSize = minOf(file.length().toInt(), 16384)
            if (readSize <= 0) return ReplayGainInfo()
            val buffer = ByteArray(readSize)
            java.io.FileInputStream(file).use { fis ->
                fis.read(buffer)
            }
            val text = String(buffer, Charsets.ISO_8859_1)
            val trackPattern = Regex("(?i)REPLAYGAIN_TRACK_GAIN[=:]\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*(?:dB)?")
            val albumPattern = Regex("(?i)REPLAYGAIN_ALBUM_GAIN[=:]\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*(?:dB)?")
            val trackMatch = trackPattern.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
            val albumMatch = albumPattern.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
            ReplayGainInfo(trackGainDb = trackMatch, albumGainDb = albumMatch)
        } catch (e: Exception) {
            ReplayGainInfo()
        }
    }

    /**
     * Fast binary header inspection for ReplayGain track or album gain tags (FLAC, OGG, ID3v2 TXXX).
     * Returns gain in dB (e.g. -4.5f), or 0.0f if untagged.
     */
    fun extractReplayGainDb(
        file: File,
        mode: com.hana.spindle.core.ReplayGainMode = com.hana.spindle.core.ReplayGainMode.TRACK
    ): Float {
        val info = extractReplayGainInfo(file)
        return when (mode) {
            com.hana.spindle.core.ReplayGainMode.OFF -> 0f
            com.hana.spindle.core.ReplayGainMode.TRACK -> info.trackGainDb
            com.hana.spindle.core.ReplayGainMode.ALBUM -> if (info.albumGainDb != 0f) info.albumGainDb else info.trackGainDb
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

    data class WavInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int
    )

    fun parseWavStreamInfo(file: File): WavInfo? {
        if (!file.exists() || file.length() < 44) return null
        return try {
            java.io.FileInputStream(file).use { fis ->
                val header = ByteArray(64)
                val read = fis.read(header)
                if (read < 44) return null

                if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte() ||
                    header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte() ||
                    header[8] != 'W'.code.toByte() || header[9] != 'A'.code.toByte() ||
                    header[10] != 'V'.code.toByte() || header[11] != 'E'.code.toByte()
                ) {
                    return null
                }

                var offset = 12
                while (offset + 16 <= read) {
                    if (header[offset] == 'f'.code.toByte() &&
                        header[offset + 1] == 'm'.code.toByte() &&
                        header[offset + 2] == 't'.code.toByte() &&
                        header[offset + 3] == ' '.code.toByte()
                    ) {
                        val channels = (header[offset + 10].toInt() and 0xFF) or
                                ((header[offset + 11].toInt() and 0xFF) shl 8)

                        val sampleRate = (header[offset + 12].toInt() and 0xFF) or
                                ((header[offset + 13].toInt() and 0xFF) shl 8) or
                                ((header[offset + 14].toInt() and 0xFF) shl 16) or
                                ((header[offset + 15].toInt() and 0xFF) shl 24)

                        val bitDepth = if (offset + 22 <= read) {
                            (header[offset + 22].toInt() and 0xFF) or
                                    ((header[offset + 23].toInt() and 0xFF) shl 8)
                        } else 16

                        if (sampleRate in 8000..384000 && bitDepth in 8..32) {
                            return WavInfo(sampleRate, channels, bitDepth)
                        }
                    }
                    offset++
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    data class AiffInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
        val durationMs: Long
    )

    fun parseAiffStreamInfo(file: File): AiffInfo? {
        if (!file.exists() || file.length() < 30) return null
        return try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(12)
                if (raf.read(magic) < 12) return null
                if (magic[0] != 'F'.code.toByte() || magic[1] != 'O'.code.toByte() ||
                    magic[2] != 'R'.code.toByte() || magic[3] != 'M'.code.toByte()
                ) return null

                val formType = String(magic, 8, 4, Charsets.US_ASCII)
                if (formType != "AIFF" && formType != "AIFC") return null

                val fileLength = file.length()
                while (raf.filePointer + 8 <= fileLength) {
                    val chunkHeader = ByteArray(8)
                    if (raf.read(chunkHeader) < 8) break
                    val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    val chunkSize = ((chunkHeader[4].toLong() and 0xFF) shl 24) or
                            ((chunkHeader[5].toLong() and 0xFF) shl 16) or
                            ((chunkHeader[6].toLong() and 0xFF) shl 8) or
                            (chunkHeader[7].toLong() and 0xFF)

                    if (chunkId == "COMM") {
                        val commBuf = ByteArray(minOf(chunkSize.toInt(), 32))
                        if (raf.read(commBuf) < 18) return null
                        val channels = ((commBuf[0].toInt() and 0xFF) shl 8) or (commBuf[1].toInt() and 0xFF)
                        val numSampleFrames = ((commBuf[2].toLong() and 0xFF) shl 24) or
                                ((commBuf[3].toLong() and 0xFF) shl 16) or
                                ((commBuf[4].toLong() and 0xFF) shl 8) or
                                (commBuf[5].toLong() and 0xFF)
                        val sampleSize = ((commBuf[6].toInt() and 0xFF) shl 8) or (commBuf[7].toInt() and 0xFF)

                        val rateBytes = ByteArray(10)
                        System.arraycopy(commBuf, 8, rateBytes, 0, 10)
                        val sampleRate = com.hana.spindle.playback.extractor.AiffExtractor.readIeeeExtendedFloat(rateBytes)
                        val durationMs = if (sampleRate > 0) (numSampleFrames * 1000L) / sampleRate else 0L

                        if (sampleRate in 8000..384000 && sampleSize in 8..32) {
                            return AiffInfo(sampleRate, channels, sampleSize, durationMs)
                        }
                        return null
                    } else {
                        // Skip chunk data + padding byte if odd size
                        val skipBytes = chunkSize + (chunkSize % 2L)
                        raf.seek(raf.filePointer + skipBytes)
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    data class DsfInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
        val durationMs: Long
    )

    fun parseDsfStreamInfo(file: File): DsfInfo? {
        if (!file.exists() || file.length() < 92) return null
        return try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                // 1. Read 'DSD ' header chunk (28 bytes)
                val header = ByteArray(28)
                if (raf.read(header) < 28) return null
                if (header[0] != 'D'.code.toByte() || header[1] != 'S'.code.toByte() ||
                    header[2] != 'D'.code.toByte() || header[3] != ' '.code.toByte()
                ) return null

                // 2. Read 'fmt ' chunk header (12 bytes: 4 magic + 8 size)
                val fmtHdr = ByteArray(12)
                if (raf.read(fmtHdr) < 12) return null
                if (fmtHdr[0] != 'f'.code.toByte() || fmtHdr[1] != 'm'.code.toByte() ||
                    fmtHdr[2] != 't'.code.toByte() || fmtHdr[3] != ' '.code.toByte()
                ) return null

                val fmtSize = ((fmtHdr[4].toLong() and 0xFF)) or
                        ((fmtHdr[5].toLong() and 0xFF) shl 8) or
                        ((fmtHdr[6].toLong() and 0xFF) shl 16) or
                        ((fmtHdr[7].toLong() and 0xFF) shl 24) or
                        ((fmtHdr[8].toLong() and 0xFF) shl 32) or
                        ((fmtHdr[9].toLong() and 0xFF) shl 40) or
                        ((fmtHdr[10].toLong() and 0xFF) shl 48) or
                        ((fmtHdr[11].toLong() and 0xFF) shl 56)

                val payloadSize = (fmtSize - 12).toInt().coerceIn(40, 256)
                val payload = ByteArray(payloadSize)
                if (raf.read(payload) < payloadSize) return null

                // payload offsets (little-endian):
                // 12..15: channelCount
                // 16..19: samplingFrequency
                // 24..31: sampleCount
                val channels = (payload[12].toInt() and 0xFF) or ((payload[13].toInt() and 0xFF) shl 8)
                val sampleRate = (payload[16].toInt() and 0xFF) or
                        ((payload[17].toInt() and 0xFF) shl 8) or
                        ((payload[18].toInt() and 0xFF) shl 16) or
                        ((payload[19].toInt() and 0xFF) shl 24)
                val sampleCount = (payload[24].toLong() and 0xFF) or
                        ((payload[25].toLong() and 0xFF) shl 8) or
                        ((payload[26].toLong() and 0xFF) shl 16) or
                        ((payload[27].toLong() and 0xFF) shl 24) or
                        ((payload[28].toLong() and 0xFF) shl 32) or
                        ((payload[29].toLong() and 0xFF) shl 40) or
                        ((payload[30].toLong() and 0xFF) shl 48) or
                        ((payload[31].toLong() and 0xFF) shl 56)

                val durationMs = if (sampleRate > 0) (sampleCount * 1000L) / sampleRate else 0L
                if (sampleRate >= 2822400 && channels in 1..8) {
                    DsfInfo(sampleRate, channels, 1, durationMs)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
