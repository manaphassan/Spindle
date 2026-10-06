package com.hana.spindle.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FlacStreamInfoTest {

    @Test
    fun parse_realWorld16Bit44100HzFlacHeader() {
        // Real-world CSD from 01 - Bling-Bang-Bang-Born.flac:
        // 10 00 (minBlock=4096)
        // 10 00 (maxBlock=4096)
        // 00 00 0e (minFrame=14)
        // 00 35 57 (maxFrame=13655)
        // 0a c4 42 f0 (sampleRate=44100, channels=2, bps=16)
        // remaining bytes of STREAMINFO (total 34 bytes)
        val csd = byteArrayOf(
            0x10.toByte(), 0x00.toByte(),
            0x10.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x0e.toByte(),
            0x00.toByte(), 0x35.toByte(), 0x57.toByte(),
            0x0a.toByte(), 0xc4.toByte(), 0x42.toByte(), 0xf0.toByte(),
            0x00.toByte(), 0x71.toByte(), 0x23.toByte(), 0xff.toByte(),
            0x5a.toByte(), 0xbe.toByte(), 0xd4.toByte(), 0xfe.toByte(),
            0xf5.toByte(), 0xd7.toByte(), 0x34.toByte(), 0x20.toByte(),
            0x8b.toByte(), 0x25.toByte(), 0xc5.toByte(), 0xb2.toByte(),
            0x86.toByte(), 0xb5.toByte(), 0x10.toByte(), 0x6b.toByte()
        )

        val info = FlacStreamInfo.parse(csd)
        assertNotNull(info)
        info!!
        assertEquals(4096, info.minBlockSize)
        assertEquals(4096, info.maxBlockSize)
        assertEquals(14, info.minFrameSize)
        assertEquals(13655, info.maxFrameSize)
        assertEquals(44100, info.sampleRate)
        assertEquals(2, info.channels)
        assertEquals(16, info.bitsPerSample)
        assertEquals(16, info.bitWidth)
    }

    @Test
    fun parse_24Bit96kHzFlacHeader() {
        // 96000 Hz = 0x17700
        // b10 = 0x17
        // b11 = 0x70
        // b12: sample rate low 4 bits (0x0), channels-1 (1 = 0b001 -> shl 1 = 0b010), bps-1 bit 4 (1)
        //      b12 = 0x00 or 0x02 or 0x01 = 0x03
        // b13: bps-1 bits 3..0 (24-1=23=0b10111 -> bits 3..0 is 0b0111 = 7 = 0x70), sample count upper 4 (0x0)
        //      b13 = 0x70
        val csd = ByteArray(34).apply {
            this[0] = 0x10.toByte()
            this[1] = 0x00.toByte() // minBlock 4096
            this[2] = 0x10.toByte()
            this[3] = 0x00.toByte() // maxBlock 4096
            this[10] = 0x17.toByte()
            this[11] = 0x70.toByte()
            this[12] = 0x03.toByte()
            this[13] = 0x70.toByte()
        }

        val info = FlacStreamInfo.parse(csd)
        assertNotNull(info)
        info!!
        assertEquals(96000, info.sampleRate)
        assertEquals(2, info.channels)
        assertEquals(24, info.bitsPerSample)
        assertEquals(24, info.bitWidth)
    }

    @Test
    fun parse_withFLACMetadataBlockHeaderOffset() {
        // 4 bytes "fLaC" + 4 bytes metadata header + 34 bytes STREAMINFO = 42 bytes
        val container = ByteArray(42)
        container[0] = 0x66.toByte() // 'f'
        container[1] = 0x4c.toByte() // 'L'
        container[2] = 0x61.toByte() // 'a'
        container[3] = 0x43.toByte() // 'C'
        container[4] = 0x00.toByte() // isLast=0, type=0 (STREAMINFO)
        container[5] = 0x00.toByte()
        container[6] = 0x00.toByte()
        container[7] = 0x22.toByte() // length = 34

        // STREAMINFO at offset 8:
        container[8] = 0x10.toByte()
        container[9] = 0x00.toByte() // minBlock 4096
        container[10] = 0x10.toByte()
        container[11] = 0x00.toByte() // maxBlock 4096
        container[18] = 0x0a.toByte()
        container[19] = 0xc4.toByte()
        container[20] = 0x42.toByte()
        container[21] = 0xf0.toByte() // 44.1kHz, 2ch, 16bit

        val info = FlacStreamInfo.parse(container)
        assertNotNull(info)
        info!!
        assertEquals(4096, info.minBlockSize)
        assertEquals(44100, info.sampleRate)
        assertEquals(2, info.channels)
        assertEquals(16, info.bitsPerSample)
        assertEquals(16, info.bitWidth)
    }

    @Test
    fun parse_shortArrayReturnsNull() {
        val short = ByteArray(10)
        assertNull(FlacStreamInfo.parse(short))
    }
}
