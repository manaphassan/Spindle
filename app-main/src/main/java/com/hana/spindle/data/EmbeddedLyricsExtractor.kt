package com.hana.spindle.data

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale

/**
 * Lightweight, zero-allocation-friendly binary parser for extracting embedded lyrics
 * directly from audio file tags:
 * 1. FLAC: Vorbis Comments (LYRICS, UNSYNCEDLYRICS, SYNCEDLYRICS)
 * 2. MP3: ID3v2 frames (USLT, SYLT, TXXX:LYRICS)
 * 3. M4A / AAC: Metadata atom (©lyr)
 */
object EmbeddedLyricsExtractor {

    private const val MAX_METADATA_SCAN_BYTES = 512 * 1024 // Cap header inspection to 512KB

    fun extractLyrics(file: File): String? {
        if (!file.exists() || !file.canRead() || file.length() < 32) return null
        val ext = file.extension.uppercase(Locale.ROOT)
        return try {
            when (ext) {
                "FLAC" -> extractFlacVorbisLyrics(file)
                "MP3" -> extractId3Lyrics(file)
                "M4A", "AAC", "MP4", "ALAC" -> extractMp4Lyrics(file)
                else -> {
                    // Try ID3v2 tag prefix check for arbitrary files (e.g. WAV or OGG with ID3 header)
                    extractId3Lyrics(file)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hasLyrics(file: File): Boolean {
        return extractLyrics(file)?.isNotBlank() == true
    }

    /**
     * Parses FLAC metadata blocks looking for block type 4 (VORBIS_COMMENT).
     */
    private fun extractFlacVorbisLyrics(file: File): String? {
        FileInputStream(file).use { fis ->
            val magic = ByteArray(4)
            if (fis.read(magic) < 4) return null

            // Check if file starts with ID3v2 tag prepended to FLAC
            if (magic[0] == 0x49.toByte() && magic[1] == 0x44.toByte() && magic[2] == 0x33.toByte()) {
                val id3Header = ByteArray(6)
                if (fis.read(id3Header) < 6) return null
                val b6 = id3Header[2].toInt() and 0x7F
                val b7 = id3Header[3].toInt() and 0x7F
                val b8 = id3Header[4].toInt() and 0x7F
                val b9 = id3Header[5].toInt() and 0x7F
                val id3Size = (b6 shl 21) or (b7 shl 14) or (b8 shl 7) or b9
                val flags = id3Header[1].toInt()
                val totalId3Size = 10L + id3Size + (if ((flags and 0x10) != 0) 10L else 0L)
                val toSkip = totalId3Size - 10L
                skipFully(fis, toSkip)

                // Now read fLaC magic
                if (fis.read(magic) < 4) return null
            }

            if (magic[0] != 0x66.toByte() || magic[1] != 0x4C.toByte() ||
                magic[2] != 0x61.toByte() || magic[3] != 0x43.toByte()) {
                return null
            }

            // Iterate FLAC metadata blocks
            var isLastBlock = false
            while (!isLastBlock) {
                val blockHeader = ByteArray(4)
                if (fis.read(blockHeader) < 4) break
                val b0 = blockHeader[0].toInt() and 0xFF
                isLastBlock = (b0 and 0x80) != 0
                val blockType = b0 and 0x7F
                val blockLen = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                               ((blockHeader[2].toInt() and 0xFF) shl 8) or
                               (blockHeader[3].toInt() and 0xFF)

                if (blockLen <= 0 || blockLen > MAX_METADATA_SCAN_BYTES) {
                    break
                }

                if (blockType == 4) {
                    // VORBIS_COMMENT block
                    val commentBuffer = ByteArray(blockLen)
                    var read = 0
                    while (read < blockLen) {
                        val count = fis.read(commentBuffer, read, blockLen - read)
                        if (count <= 0) break
                        read += count
                    }
                    if (read == blockLen) {
                        val lyrics = parseVorbisCommentBuffer(commentBuffer)
                        if (!lyrics.isNullOrBlank()) return lyrics
                    }
                    break
                } else {
                    skipFully(fis, blockLen.toLong())
                }
            }
        }
        return null
    }

    private fun parseVorbisCommentBuffer(buf: ByteArray): String? {
        if (buf.size < 8) return null
        var offset = 0

        // 1. Vendor string length (32-bit little-endian)
        val vendorLen = readInt32LE(buf, offset)
        offset += 4
        if (vendorLen < 0 || offset + vendorLen > buf.size) return null
        offset += vendorLen

        // 2. User comment list length (32-bit little-endian)
        if (offset + 4 > buf.size) return null
        val userCommentCount = readInt32LE(buf, offset)
        offset += 4
        if (userCommentCount < 0) return null

        for (i in 0 until minOf(userCommentCount, 500)) {
            if (offset + 4 > buf.size) break
            val commentLen = readInt32LE(buf, offset)
            offset += 4
            if (commentLen < 0 || offset + commentLen > buf.size) break

            val comment = String(buf, offset, commentLen, Charsets.UTF_8)
            offset += commentLen

            if (comment.startsWith("LYRICS=", ignoreCase = true) ||
                comment.startsWith("UNSYNCEDLYRICS=", ignoreCase = true) ||
                comment.startsWith("SYNCEDLYRICS=", ignoreCase = true)) {
                val value = comment.substringAfter('=').trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }

    /**
     * Extracts ID3v2 USLT (Unsynchronized lyrics) or SYLT (Synchronized lyrics) frames.
     */
    private fun extractId3Lyrics(file: File): String? {
        FileInputStream(file).use { fis ->
            val header = ByteArray(10)
            if (fis.read(header) < 10) return null

            // Check "ID3" magic
            if (header[0] != 0x49.toByte() || header[1] != 0x44.toByte() || header[2] != 0x33.toByte()) {
                return null
            }

            val version = header[3].toInt() and 0xFF // 3 for ID3v2.3, 4 for ID3v2.4
            val flags = header[5].toInt() and 0xFF
            val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                          ((header[7].toInt() and 0x7F) shl 14) or
                          ((header[8].toInt() and 0x7F) shl 7) or
                          (header[9].toInt() and 0x7F)

            if (tagSize <= 0) return null
            val actualToRead = minOf(tagSize, MAX_METADATA_SCAN_BYTES)
            val tagBytes = ByteArray(actualToRead)
            var read = 0
            while (read < actualToRead) {
                val c = fis.read(tagBytes, read, actualToRead - read)
                if (c <= 0) break
                read += c
            }
            if (read < 10) return null

            var offset = 0
            // Extended header handling
            if ((flags and 0x40) != 0) {
                if (offset + 4 <= read) {
                    val extSize = if (version >= 4) {
                        ((tagBytes[offset].toInt() and 0x7F) shl 21) or
                        ((tagBytes[offset + 1].toInt() and 0x7F) shl 14) or
                        ((tagBytes[offset + 2].toInt() and 0x7F) shl 7) or
                        (tagBytes[offset + 3].toInt() and 0x7F)
                    } else {
                        readInt32BE(tagBytes, offset)
                    }
                    offset += extSize.coerceAtLeast(4)
                }
            }

            // Loop through ID3v2 frames
            while (offset + 10 <= read) {
                val frameId = String(tagBytes, offset, 4, Charsets.ISO_8859_1)
                offset += 4

                val frameSize = if (version >= 4) {
                    ((tagBytes[offset].toInt() and 0x7F) shl 21) or
                    ((tagBytes[offset + 1].toInt() and 0x7F) shl 14) or
                    ((tagBytes[offset + 2].toInt() and 0x7F) shl 7) or
                    (tagBytes[offset + 3].toInt() and 0x7F)
                } else {
                    readInt32BE(tagBytes, offset)
                }
                offset += 4
                offset += 2 // skip 2 frame flag bytes

                if (frameSize <= 0 || offset + frameSize > read) break

                if (frameId == "USLT" || frameId == "SYLT") {
                    val frameBytes = tagBytes.copyOfRange(offset, offset + frameSize)
                    val lyrics = parseUsltFrame(frameBytes)
                    if (!lyrics.isNullOrBlank()) return lyrics
                } else if (frameId == "TXXX") {
                    val frameBytes = tagBytes.copyOfRange(offset, offset + frameSize)
                    val lyrics = parseTxxxLyricsFrame(frameBytes)
                    if (!lyrics.isNullOrBlank()) return lyrics
                }

                offset += frameSize
            }
        }
        return null
    }

    private fun parseUsltFrame(frame: ByteArray): String? {
        if (frame.size < 5) return null
        val encodingByte = frame[0].toInt() and 0xFF
        val charset = getCharsetFromEncoding(encodingByte)

        // 3 bytes language
        var textOffset = 4

        // Content descriptor (terminated by 0x00 or 0x00 0x00)
        if (encodingByte == 1 || encodingByte == 2) {
            // 2-byte terminator
            while (textOffset + 1 < frame.size) {
                if (frame[textOffset] == 0.toByte() && frame[textOffset + 1] == 0.toByte()) {
                    textOffset += 2
                    break
                }
                textOffset += 2
            }
        } else {
            // 1-byte terminator
            while (textOffset < frame.size) {
                if (frame[textOffset] == 0.toByte()) {
                    textOffset += 1
                    break
                }
                textOffset += 1
            }
        }

        if (textOffset >= frame.size) return null
        return String(frame, textOffset, frame.size - textOffset, charset).trim()
    }

    private fun parseTxxxLyricsFrame(frame: ByteArray): String? {
        if (frame.size < 3) return null
        val encodingByte = frame[0].toInt() and 0xFF
        val charset = getCharsetFromEncoding(encodingByte)
        val text = String(frame, 1, frame.size - 1, charset)
        val nullIdx = text.indexOf('\u0000')
        if (nullIdx != -1) {
            val desc = text.substring(0, nullIdx).trim()
            val value = text.substring(nullIdx + 1).trim()
            if (desc.equals("LYRICS", ignoreCase = true) && value.isNotEmpty()) {
                return value
            }
        }
        return null
    }

    /**
     * Simple atom scan for MP4 / M4A ©lyr metadata atom.
     */
    private fun extractMp4Lyrics(file: File): String? {
        FileInputStream(file).use { fis ->
            val buf = ByteArray(minOf(file.length().toInt(), MAX_METADATA_SCAN_BYTES))
            val read = fis.read(buf)
            if (read < 16) return null

            // Search for ©lyr atom marker (0xA9, 'l', 'y', 'r')
            val marker = byteArrayOf(0xA9.toByte(), 0x6C.toByte(), 0x79.toByte(), 0x72.toByte())
            val idx = indexOf(buf, marker, 0, read)
            if (idx != -1) {
                // ©lyr atom found. Locate internal 'data' atom (0x64, 0x61, 0x74, 0x61)
                val dataMarker = byteArrayOf(0x64, 0x61, 0x74, 0x61)
                val dataIdx = indexOf(buf, dataMarker, idx, minOf(read, idx + 256))
                if (dataIdx != -1 && dataIdx + 12 < read) {
                    val dataSize = readInt32BE(buf, dataIdx - 4)
                    val textStart = dataIdx + 8 // skip 'data' + 4 flags
                    val textLen = (dataSize - 8).coerceIn(0, read - textStart)
                    if (textLen > 0) {
                        return String(buf, textStart, textLen, Charsets.UTF_8).trim()
                    }
                }
            }
        }
        return null
    }

    private fun getCharsetFromEncoding(b: Int): Charset {
        return when (b) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
    }

    private fun readInt32LE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
               ((buf[offset + 1].toInt() and 0xFF) shl 8) or
               ((buf[offset + 2].toInt() and 0xFF) shl 16) or
               ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readInt32BE(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 24) or
               ((buf[offset + 1].toInt() and 0xFF) shl 16) or
               ((buf[offset + 2].toInt() and 0xFF) shl 8) or
               (buf[offset + 3].toInt() and 0xFF)
    }

    private fun skipFully(isStream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = isStream.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    private fun indexOf(source: ByteArray, target: ByteArray, start: Int, end: Int): Int {
        val max = end - target.size
        for (i in start..max) {
            var found = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }
}
