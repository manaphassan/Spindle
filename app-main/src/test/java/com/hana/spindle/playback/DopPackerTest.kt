package com.hana.spindle.playback

import com.hana.spindle.playback.extractor.DopPacker
import com.hana.spindle.playback.extractor.DsdOutput
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DopPackerTest {
    @After fun reset() { DsdOutput.dopRequested = false; DsdOutput.negotiator = null }

    @Test fun ratesFollowTheDoPSpec() {
        assertEquals(176_400, DopPacker.pcmRateFor(2_822_400))   // DSD64
        assertEquals(352_800, DopPacker.pcmRateFor(5_644_800))   // DSD128
        assertEquals(705_600, DopPacker.pcmRateFor(11_289_600))  // DSD256
        assertEquals(0, DopPacker.pcmRateFor(0))
        assertEquals(0, DopPacker.pcmRateFor(2_822_401))
    }

    @Test fun packsMarkersBitsAndLittleEndianBytes() {
        // Stereo, one block per channel of 4 bytes: L = 01 02 03 04, R = A0 B0 C0 D0 (DSF, LSB first)
        val src = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0xA0.toByte(), 0xB0.toByte(), 0xC0.toByte(), 0xD0.toByte())
        val out = ByteArray(2 * 2 * 3)
        val (n, phase) = DopPacker.pack(src, 0, 4, 2, 4, lsbFirst = true, markerPhase = 0, out = out)
        assertEquals(12, n)
        assertEquals(0, phase) // two frames flip the phase twice
        // Frame 0: marker 0x05, L bytes (01,02) reversed -> 80,40 ; R (A0,B0) reversed -> 05,0D
        assertArrayEquals(byteArrayOf(0x40, 0x80.toByte(), 0x05), out.copyOfRange(0, 3))
        assertArrayEquals(byteArrayOf(0x0D, 0x05, 0x05), out.copyOfRange(3, 6))
        // Frame 1: marker 0xFA, L (03,04) reversed -> C0,20 ; R (C0,D0) reversed -> 03,0B
        assertArrayEquals(byteArrayOf(0x20, 0xC0.toByte(), 0xFA.toByte()), out.copyOfRange(6, 9))
        assertArrayEquals(byteArrayOf(0x0B, 0x03, 0xFA.toByte()), out.copyOfRange(9, 12))
    }

    @Test fun msbFirstSourcesAreNotReversed() {
        val src = byteArrayOf(0x12, 0x34)
        val out = ByteArray(3)
        DopPacker.pack(src, 0, 2, 1, 2, lsbFirst = false, markerPhase = 1, out = out)
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0xFA.toByte()), out)
    }

    @Test fun phaseContinuesAcrossBlocksAndOddByteIsDropped() {
        val src = ByteArray(6) { 0x55 }
        val out = ByteArray(9)
        val (n, phase) = DopPacker.pack(src, 0, 3, 2, 3, lsbFirst = false, markerPhase = 0, out = out) // 1 frame/ch used
        assertEquals(6, n)
        assertEquals(1, phase)
    }

    @Test fun negotiationIsRequiredAndFailsClosed() {
        assertFalse(DsdOutput.tryEnableDop(2_822_400))                       // not requested
        DsdOutput.dopRequested = true
        assertFalse(DsdOutput.tryEnableDop(2_822_400))                       // no negotiator
        DsdOutput.negotiator = { false }
        assertFalse(DsdOutput.tryEnableDop(2_822_400))                       // platform refused
        DsdOutput.negotiator = { throw IllegalStateException() }
        assertFalse(DsdOutput.tryEnableDop(2_822_400))                       // errors never enable DoP
        var asked = 0
        DsdOutput.negotiator = { r -> asked = r; true }
        assertTrue(DsdOutput.tryEnableDop(2_822_400))
        assertEquals(176_400, asked)
        assertFalse(DsdOutput.tryEnableDop(22_579_200 * 2))                  // above 705.6 kHz: unsupported
    }
}
