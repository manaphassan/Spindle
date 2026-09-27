package com.hana.spindle.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDacManagerTest {

    @Test
    fun testUsbDacInfoHexFormattingAndBadge() {
        val dac = UsbDacInfo(
            isConnected = true,
            deviceName = "/dev/bus/usb/001/002",
            manufacturer = "FiiO",
            productName = "KA3 High-Resolution DAC",
            vendorId = 0x2972,
            productId = 0x0047,
            audioClass = "USB Audio Class 2.0 (High-Speed)",
            maxSampleRateHz = 768000,
            bitDepthBits = 32,
            isKnownAudiophileBrand = true,
            brandBadge = "FiiO",
            capabilitiesDescription = "Direct ALSA PCM • Up to 32-bit/768kHz • Asynchronous"
        )

        assertEquals("0x2972", dac.vidHex)
        assertEquals("0x0047", dac.pidHex)
        assertEquals("FiiO KA3 High-Resolution DAC", dac.shortBadgeTitle)
        assertTrue(dac.isKnownAudiophileBrand)
        assertTrue(dac.isConnected)
    }

    @Test
    fun testBrandResolutionByVendorId() {
        assertEquals("FiiO", UsbDacManager.resolveBrand(0x2972, null, null))
        assertEquals("Moondrop", UsbDacManager.resolveBrand(0x2FC6, null, null))
        assertEquals("Qudelix", UsbDacManager.resolveBrand(0x2D87, null, null))
        assertEquals("AudioQuest", UsbDacManager.resolveBrand(0x21B4, null, null))
        assertEquals("iFi Audio", UsbDacManager.resolveBrand(0x20B1, null, null))
        assertEquals("Chord", UsbDacManager.resolveBrand(0x04D8, null, null))
        assertEquals("Topping / SMSL", UsbDacManager.resolveBrand(0x152A, null, null))
        assertEquals("Shanling", UsbDacManager.resolveBrand(0x2772, null, null))
        assertEquals("HiBy", UsbDacManager.resolveBrand(0x2F72, null, null))
        assertEquals("Apple", UsbDacManager.resolveBrand(0x05AC, null, null))
        assertEquals("Realtek Hi-Res", UsbDacManager.resolveBrand(0x0BDA, null, null))
        assertEquals("Conexant / CX", UsbDacManager.resolveBrand(0x2207, null, null))
    }

    @Test
    fun testBrandResolutionByDescriptorStrings() {
        assertEquals("FiiO", UsbDacManager.resolveBrand(0x9999, "FiiO Electronics Co.", "BTR7"))
        assertEquals("Moondrop", UsbDacManager.resolveBrand(0x9999, "MOONDROP", "Dawn Pro"))
        assertEquals("AudioQuest", UsbDacManager.resolveBrand(0x9999, "AudioQuest", "DragonFly Cobalt"))
        assertEquals("Chord", UsbDacManager.resolveBrand(0x9999, null, "Chord Mojo 2"))
        assertEquals("iFi Audio", UsbDacManager.resolveBrand(0x9999, "AMR/iFi", "GO bar"))
        assertEquals("Qudelix", UsbDacManager.resolveBrand(0x9999, "Qudelix Inc.", "5K"))
        assertEquals("Sony Hi-Res", UsbDacManager.resolveBrand(0x9999, "Sony", "Walkman USB Audio"))
        assertEquals("Astell&Kern", UsbDacManager.resolveBrand(0x9999, "IRIVER", "Astell&Kern HC3"))
        assertEquals("Cayin", UsbDacManager.resolveBrand(0x9999, "Cayin", "RU7 1-Bit Resistor DAC"))
        assertEquals("Hidizs", UsbDacManager.resolveBrand(0x9999, "Hidizs", "S9 Pro Martha"))
    }

    @Test
    fun testProductNameCatalogMapping() {
        // FiiO catalog
        assertEquals("KA3 High-Resolution DAC", UsbDacManager.resolveProductName(0x2972, 0x0047, null))
        assertEquals("KA13 Dual CS43198 Desktop Mode", UsbDacManager.resolveProductName(0x2972, 0x0051, null))
        assertEquals("KA17 Dual ES9069Q Flagship", UsbDacManager.resolveProductName(0x2972, 0x0052, null))
        assertEquals("BTR7 Dual THX AAA DAC", UsbDacManager.resolveProductName(0x2972, 0x0022, null))

        // Moondrop catalog
        assertEquals("Dawn Pro Dual CS43131", UsbDacManager.resolveProductName(0x2FC6, 0xF011, null))
        assertEquals("Moonriver 2 Flagship DAC", UsbDacManager.resolveProductName(0x2FC6, 0xF020, null))

        // AudioQuest catalog
        assertEquals("DragonFly Cobalt Flagship", UsbDacManager.resolveProductName(0x21B4, 0x0082, null))
        assertEquals("DragonFly Red v1.0", UsbDacManager.resolveProductName(0x21B4, 0x0081, null))

        // Qudelix catalog
        assertEquals("Qudelix-5K Reference DAC", UsbDacManager.resolveProductName(0x2D87, 0x0001, null))

        // Custom device name overrides null catalog
        assertEquals("Custom CustomDongle", UsbDacManager.resolveProductName(0x2972, 0x9999, "Custom CustomDongle"))

        // Uncatalogued fallback
        assertEquals("USB DAC [VID:1234 PID:5678]", UsbDacManager.resolveProductName(0x1234, 0x5678, null))
    }

    @Test
    fun testMaxSpecsEstimation() {
        // Flagship 768kHz / 32-bit DACs
        val fiioKa17 = UsbDacManager.estimateMaxSpecs(0x2972, 0x0052, isUac2 = true)
        assertEquals(768000, fiioKa17.first)
        assertEquals(32, fiioKa17.second)

        val chordMojo = UsbDacManager.estimateMaxSpecs(0x04D8, 0x0001, isUac2 = true)
        assertEquals(768000, chordMojo.first)
        assertEquals(32, chordMojo.second)

        // 384kHz / 32-bit DACs
        val qudelix = UsbDacManager.estimateMaxSpecs(0x2D87, 0x0001, isUac2 = true)
        assertEquals(384000, qudelix.first)
        assertEquals(32, qudelix.second)

        // AudioQuest DragonFly (Adaptive UAC1 - 96kHz / 24-bit)
        val dragonFly = UsbDacManager.estimateMaxSpecs(0x21B4, 0x0082, isUac2 = false)
        assertEquals(96000, dragonFly.first)
        assertEquals(24, dragonFly.second)

        // Generic UAC2 (192kHz / 24-bit)
        val genericUac2 = UsbDacManager.estimateMaxSpecs(0x9999, 0x1111, isUac2 = true)
        assertEquals(192000, genericUac2.first)
        assertEquals(24, genericUac2.second)
    }

    @Test
    fun testAudioMetricsUsbDacIntegration() {
        val initial = AudioMetrics()
        assertFalse(initial.isUsbDacConnected)
        assertEquals(null, initial.usbDacName)

        val withUsb = initial.copy(
            isUsbDacConnected = true,
            usbDacName = "FiiO KA3 High-Resolution DAC",
            usbDacVid = "0x2972",
            usbDacPid = "0x0047",
            usbDacAudioClass = "USB Audio Class 2.0 (High-Speed)",
            usbDacMaxSampleRateHz = 768000,
            usbDacBitDepthBits = 32,
            usbDacCapabilities = "Direct ALSA PCM • Up to 32-bit/768kHz • Asynchronous",
            usbDacBrandBadge = "FiiO",
            outputRoute = "USB DAC (FiiO KA3 High-Resolution DAC)",
            isBitPerfect = true
        )

        assertTrue(withUsb.isUsbDacConnected)
        assertEquals("0x2972", withUsb.usbDacVid)
        assertEquals(768000, withUsb.usbDacMaxSampleRateHz)
        assertEquals("FiiO", withUsb.usbDacBrandBadge)
        assertTrue(withUsb.outputRoute.contains("FiiO KA3"))
        assertTrue(withUsb.isBitPerfect)
    }
}
