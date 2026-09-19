package com.hana.spindle.lite.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DeviceNameFormatter custom engraving presets.
 */
class DeviceNameFormatterTest {

    @Test
    fun testDefaultAutoNameplate() {
        val nameplate = DeviceNameFormatter.getFormattedNameplate(0)
        assertTrue(nameplate.isNotEmpty())
    }

    @Test
    fun testAudiophileEngravingPresets() {
        assertEquals("SPINDLE • REFERENCE DAP", DeviceNameFormatter.getFormattedNameplate(1))
        assertEquals("HIGH BIAS • MASTER RECORDER", DeviceNameFormatter.getFormattedNameplate(2))
        assertEquals("DIRECT ALSA • 192K LOSSLESS", DeviceNameFormatter.getFormattedNameplate(3))
        assertEquals("SATSUMA AUDIO EDITION", DeviceNameFormatter.getFormattedNameplate(4))
    }
}
