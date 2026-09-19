package com.hana.spindle.lite.util

import com.hana.spindle.lite.db.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Unit tests for AudioHeaderParser native stream parsing and Track 24-bit studio formatting.
 */
class AudioHeaderParserTest {

    @Test
    fun testTrackBitDepthFormatting() {
        val flac24Bit = Track(
            title = "Studio Master",
            artist = "Acoustic Trio",
            album = "Direct to DSD",
            durationMs = 240000L,
            filePath = "/Music/master.flac",
            format = "FLAC",
            sampleRate = 96000,
            bitDepth = 24
        )
        assertEquals("FLAC 24b/96k", flac24Bit.formatBadge)
        assertEquals("SPINDLE • TYPE IV METAL BIAS", flac24Bit.tapeBiasType)

        val flac16Bit = Track(
            title = "CD Master",
            artist = "Orchestra",
            album = "Symphony 9",
            durationMs = 360000L,
            filePath = "/Music/symphony.flac",
            format = "FLAC",
            sampleRate = 44100,
            bitDepth = 16
        )
        assertEquals("FLAC 16b/44.1k", flac16Bit.formatBadge)
    }

    @Test
    fun testFlacHeaderParserSynthesizedStream() {
        // Create synthetic valid 42-byte FLAC header:
        // 'f', 'L', 'a', 'C', block_type=0, len=0x00, 0x00, 0x22 (34 bytes),
        // min/max blocksize (4 bytes), min/max framesize (6 bytes)
        // offset 18: sample_rate (20 bits), channels (3 bits), bit_depth (5 bits)
        // Let sample_rate = 96000 = 0x17700
        // channels = 2 -> (channels - 1) = 1 = 0b001
        // bit_depth = 24 -> (bit_depth - 1) = 23 = 0b10111
        // Packing:
        // b18 = sample_rate >> 12 = 0x17 = 23
        // b19 = (sample_rate >> 4) & 0xFF = 0x70 = 112
        // b20 = ((sample_rate & 0x0F) << 4) | (0b001 << 1) | (bit_depth_high_bit = 1)
        //       = (0x0 << 4) | (0b0010) | 1 = 0b00000011 = 0x03
        // b21 = ((23 & 0x0F) << 4) = (0b0111 << 4) = 0x70
        val bytes = ByteArray(42)
        bytes[0] = 0x66.toByte() // f
        bytes[1] = 0x4C.toByte() // L
        bytes[2] = 0x61.toByte() // a
        bytes[3] = 0x43.toByte() // C
        bytes[4] = 0x00.toByte() // STREAMINFO block
        bytes[5] = 0x00.toByte()
        bytes[6] = 0x00.toByte()
        bytes[7] = 0x22.toByte() // length 34

        bytes[18] = 0x17.toByte()
        bytes[19] = 0x70.toByte()
        bytes[20] = 0x03.toByte()
        bytes[21] = 0x70.toByte()

        val tempFile = File.createTempFile("test_audio", ".flac")
        try {
            FileOutputStream(tempFile).use { it.write(bytes) }
            val specs = AudioHeaderParser.parse(tempFile)
            assertNotNull(specs)
            assertEquals(96000, specs!!.sampleRate)
            assertEquals(2, specs.channels)
            assertEquals(24, specs.bitDepth)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testCorruptFileReturnsNull() {
        val tempFile = File.createTempFile("corrupt", ".flac")
        try {
            FileOutputStream(tempFile).use { it.write(byteArrayOf(1, 2, 3, 4, 5)) }
            val specs = AudioHeaderParser.parse(tempFile)
            assertNull(specs)
        } finally {
            tempFile.delete()
        }
    }
}
