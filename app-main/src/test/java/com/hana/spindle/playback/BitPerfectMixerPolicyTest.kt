package com.hana.spindle.playback

import org.junit.Assert.*
import org.junit.Test

class BitPerfectMixerPolicyTest {
    private val dac = listOf(
        MixerFormat(44100, 16), MixerFormat(44100, 24), MixerFormat(44100, 32),
        MixerFormat(96000, 24), MixerFormat(192000, 32)
    )

    @Test fun exactRateAndDepthWins() {
        assertEquals(MixerFormat(44100, 16), BitPerfectMixerPolicy.select(dac, 44100, 16))
        assertEquals(MixerFormat(96000, 24), BitPerfectMixerPolicy.select(dac, 96000, 24))
    }

    @Test fun deepSourcePrefers24ThenAccepts32() {
        assertEquals(MixerFormat(44100, 24), BitPerfectMixerPolicy.select(dac, 44100, 24))
        assertEquals(MixerFormat(192000, 32), BitPerfectMixerPolicy.select(dac, 192000, 24))
        assertEquals(MixerFormat(44100, 24), BitPerfectMixerPolicy.select(dac, 44100, 32))
    }

    @Test fun neverChoosesDifferentRateOrFewerBits() {
        assertNull(BitPerfectMixerPolicy.select(dac, 48000, 16))      // rate not offered
        assertNull(BitPerfectMixerPolicy.select(dac, 88200, 24))
        assertNull(BitPerfectMixerPolicy.select(listOf(MixerFormat(96000, 16)), 96000, 24)) // would truncate
        assertNull(BitPerfectMixerPolicy.select(listOf(MixerFormat(96000, 24)), 96000, 16)) // would pad
        assertNull(BitPerfectMixerPolicy.select(emptyList(), 44100, 16))
    }

    @Test fun verifiedClaimNeedsPlatformConfirmationAndNoDsp() {
        assertTrue(AudioMetricsTracker.computeVerifiedBitPerfect(true, false))
        assertFalse(AudioMetricsTracker.computeVerifiedBitPerfect(true, true))
        assertFalse(AudioMetricsTracker.computeVerifiedBitPerfect(false, false))
    }

    @Test fun onlyActiveStateIsVerified() {
        for (s in BitPerfectStatus.values()) {
            assertEquals(s == BitPerfectStatus.ACTIVE, BitPerfectMixerState(s).isVerifiedActive)
        }
    }
}
