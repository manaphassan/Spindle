package com.hana.spindle.licensing

import org.junit.Assert.*
import org.junit.Test

class StudioUnlockManagerTest {

    @Test
    fun testOfflineTokenGenerationAndVerification() {
        // Generate valid sponsor token with seed "ABCD"
        val token = StudioUnlockManager.generateSponsorToken("ABCD")
        assertTrue(token.startsWith("SPINDLE-STUDIO-ABCD-"))
        assertEquals(4, token.split("-").size)

        // Verifying the generated token must succeed
        assertTrue("Generated token must pass verification", StudioUnlockManager.verifyOfflineToken(token))
    }

    @Test
    fun testPreconfiguredMasterKeys() {
        assertTrue(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-GOLD-PASS"))
        assertTrue(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-LIFETIME-2026"))
    }

    @Test
    fun testInvalidTokenRejection() {
        // Corrupted checksum
        assertFalse(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-ABCD-0000"))
        assertFalse(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-1234-FFFF"))

        // Malformed format
        assertFalse(StudioUnlockManager.verifyOfflineToken("INVALID-KEY"))
        assertFalse(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-123"))
        assertFalse(StudioUnlockManager.verifyOfflineToken("SPINDLE-STUDIO-12345-ABCD"))
        assertFalse(StudioUnlockManager.verifyOfflineToken(""))
    }

    @Test
    fun testDeterministicChecksum() {
        val token1 = StudioUnlockManager.generateSponsorToken("7788")
        val token2 = StudioUnlockManager.generateSponsorToken("7788")
        assertEquals("Tokens with identical seeds must be deterministic", token1, token2)

        val token3 = StudioUnlockManager.generateSponsorToken("9999")
        assertNotEquals(token1, token3)
        assertTrue(StudioUnlockManager.verifyOfflineToken(token3))
    }

    @Test
    fun testStudioFeaturesEnum() {
        val features = StudioUnlockManager.StudioFeature.values()
        assertEquals(5, features.size)
        assertTrue(features.contains(StudioUnlockManager.StudioFeature.ANALOG_TAPE_SATURATION))
        assertTrue(features.contains(StudioUnlockManager.StudioFeature.TUBE_WARMTH_DSP))
        assertTrue(features.contains(StudioUnlockManager.StudioFeature.LASER_NAMEPLATE_ENGRAVING))
        assertTrue(features.contains(StudioUnlockManager.StudioFeature.STUDIO_REEL_SKINS))
        assertTrue(features.contains(StudioUnlockManager.StudioFeature.PROCEDURAL_FOLEY))
    }
}
