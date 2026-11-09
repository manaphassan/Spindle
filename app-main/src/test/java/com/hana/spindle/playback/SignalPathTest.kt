package com.hana.spindle.playback

import org.junit.Assert.*
import org.junit.Test

class SignalPathTest {
    private fun input(
        format: String = "FLAC", bits: Int = 24, rate: Int = 96000, dop: Boolean = false,
        eq: Boolean = false, peq: Int = 0, tape: Boolean = false, xfeed: Boolean = false, rg: Float? = null,
        mixer: BitPerfectMixerState = BitPerfectMixerState(), route: String = "USB DAC"
    ) = SignalPathInput(format, bits, rate, dop, eq, peq, tape, xfeed, rg, mixer, route)

    private fun stage(list: List<SignalStage>, k: SignalStageKind) = list.first { it.kind == k }

    @Test fun cleanPathWithVerifiedBitPerfectMixer() {
        val s = SignalPathBuilder.build(input(mixer = BitPerfectMixerState(BitPerfectStatus.ACTIVE, MixerFormat(96000, 24))))
        assertEquals(listOf("FLAC · 24-bit · 96 kHz"), stage(s, SignalStageKind.SOURCE).lines)
        assertTrue(stage(s, SignalStageKind.DSP).lines.isEmpty())
        assertFalse(stage(s, SignalStageKind.DSP).altered)
        val out = stage(s, SignalStageKind.OUTPUT)
        assertFalse(out.altered)
        assertTrue(out.lines.last().contains("platform confirmed"))
    }

    @Test fun listsEveryActiveDspStageAndMarksItAltered() {
        val s = SignalPathBuilder.build(input(eq = true, peq = 6, tape = true, xfeed = true, rg = -6.2f))
        val dsp = stage(s, SignalStageKind.DSP)
        assertTrue(dsp.altered)
        assertEquals(5, dsp.lines.size)
        assertTrue(dsp.lines.contains("Parametric EQ · 6 bands"))
        assertTrue(dsp.lines.contains("ReplayGain -6.2 dB"))
    }

    @Test fun neverClaimsBitPerfectWithoutPlatformConfirmation() {
        for (st in BitPerfectStatus.values().filter { it != BitPerfectStatus.ACTIVE }) {
            val out = stage(SignalPathBuilder.build(input(mixer = BitPerfectMixerState(st))), SignalStageKind.OUTPUT)
            assertTrue("$st must be marked altered", out.altered)
            assertFalse("$st must not say confirmed", out.lines.any { it.contains("platform confirmed") })
        }
    }

    @Test fun decodersAreNamedHonestly() {
        assertTrue(SignalPathBuilder.decoderFor("WV", false).contains("WavPack"))
        assertTrue(SignalPathBuilder.decoderFor("DSF", true).contains("DoP"))
        assertTrue(SignalPathBuilder.decoderFor("DSF", false).contains("PCM"))
        assertEquals("Media3 FLAC", SignalPathBuilder.decoderFor("flac", false))
        assertEquals("Media3", SignalPathBuilder.decoderFor("XYZ", false))
    }

    @Test fun formatsOddRatesAndMissingFields() {
        val s = SignalPathBuilder.build(input(format = "wav", bits = 0, rate = 44100))
        assertEquals(listOf("WAV · 44.1 kHz"), stage(s, SignalStageKind.SOURCE).lines)
    }
}
