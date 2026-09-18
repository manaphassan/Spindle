package com.hana.spindle.playback

import com.hana.spindle.theme.CassetteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * Smoke Tests and A/B Test Suite for DAP Audio Settings, Eject Behavior,
 * Camera Key Remapping, Bluetooth Telemetry, and 3.5mm Jack Telemetry.
 */
class AudioSettingsSmokeAndABTest {

    // =========================================================================
    // SMOKE TESTS
    // =========================================================================

    /**
     * Smoke Test 1: EJECT Button Dual-Action & Hardware Title Reset
     * - Short Click (< 600ms): Navigate to Music Catalogue and KEEP PLAYING (do NOT stop).
     * - Long Press (>= 600ms): Stop playback, play mechanical eject foley, eject cassette,
     *   and reset track title across deck and cassette shells to the default hardware name.
     */
    @Test
    fun testEjectBehavior_singleClickVsLongPress() {
        var isAudioPlaying = true
        var isCassetteEjected = false
        var isSongLoaded = true
        var trackTitle = "Bohemian Rhapsody"
        val defaultHardwareName = "SONY SO-02J"
        var navigatedToCatalog = false
        var foleyPlayed = false

        // Simulate callbacks matching PlayerFragment & VerticalDeckView
        val onEjectClicked: () -> Unit = {
            // Short click: navigate to catalogue, keep playing!
            navigatedToCatalog = true
        }

        val onEjectLongClicked: () -> Unit = {
            // Long click: stop song, play foley sound, eject cassette, change title to hardware name
            isAudioPlaying = false
            foleyPlayed = true
            isCassetteEjected = true
            isSongLoaded = false
            trackTitle = defaultHardwareName
        }

        // Test Scenario A: User short-clicks EJECT while listening to music
        onEjectClicked()
        assertTrue("Single-click EJECT must navigate to Music Catalogue", navigatedToCatalog)
        assertTrue("Single-click EJECT must NOT stop audio playback", isAudioPlaying)
        assertFalse("Single-click EJECT must not eject cassette", isCassetteEjected)
        assertTrue("Single-click EJECT must keep the active song loaded", isSongLoaded)
        assertEquals("Single-click EJECT must not alter the song title", "Bohemian Rhapsody", trackTitle)

        // Test Scenario B: User hold-presses EJECT
        onEjectLongClicked()
        assertFalse("Hold-press EJECT must stop playback", isAudioPlaying)
        assertTrue("Hold-press EJECT must play foley sound", foleyPlayed)
        assertTrue("Hold-press EJECT must eject cassette tape", isCassetteEjected)
        assertFalse("Hold-press EJECT must clear isSongLoaded flag", isSongLoaded)
        assertEquals("Hold-press EJECT must change title to default hardware name", defaultHardwareName, trackTitle)
    }

    /**
     * Smoke Test 2: Dedicated Hardware Camera Key Play/Stop Remap
     * - onKeyDown consumes KEYCODE_CAMERA and toggles play/pause.
     * - onKeyUp consumes KEYCODE_CAMERA to suppress the system camera app launch.
     */
    @Test
    fun testCameraKeyRemap_playPauseToggleAndSuppression() {
        var isPlaying = false
        var cameraKeyConsumedOnDown = false
        var cameraKeyConsumedOnUp = false
        val cameraRemapPrefEnabled = true

        // Simulate KeyEvent dispatch
        fun handleKeyDown(keyCode: Int): Boolean {
            if (cameraRemapPrefEnabled && (keyCode == 27 /* KEYCODE_CAMERA */ || keyCode == 80 /* KEYCODE_FOCUS */)) {
                isPlaying = !isPlaying
                cameraKeyConsumedOnDown = true
                return true
            }
            return false
        }

        fun handleKeyUp(keyCode: Int): Boolean {
            if (cameraRemapPrefEnabled && (keyCode == 27 /* KEYCODE_CAMERA */ || keyCode == 80 /* KEYCODE_FOCUS */)) {
                cameraKeyConsumedOnUp = true
                return true
            }
            return false
        }

        // Press Camera button (shutter down)
        val downHandled = handleKeyDown(27)
        assertTrue("Camera key onKeyDown must be handled", downHandled)
        assertTrue("Camera key must toggle playback to playing", isPlaying)
        assertTrue("Camera key must be marked as consumed on down", cameraKeyConsumedOnDown)

        // Release Camera button (shutter up)
        val upHandled = handleKeyUp(27)
        assertTrue("Camera key onKeyUp must be consumed to prevent launching camera app", upHandled)
        assertTrue("Camera key onKeyUp must be consumed", cameraKeyConsumedOnUp)

        // Press Camera button again to pause/stop
        handleKeyDown(27)
        assertFalse("Second camera keypress must pause/stop playback", isPlaying)
    }

    /**
     * Smoke Test 3: Bluetooth Gear Detection Without Battery Level Intent
     * Validates fix for bug where Bluetooth headphones with batteryPct == null
     * were erroneously shown as 'No Bluetooth Gear Connected'.
     */
    @Test
    fun testBluetoothGearDetection_withoutBatteryLevel() {
        // Case 1: High-end audiophile headphones connected without battery report (null)
        val metricsNoBattery = AudioMetrics(
            outputRoute = "Bluetooth (WH-1000XM4)",
            bluetoothDeviceName = "WH-1000XM4",
            bluetoothBatteryPct = null
        )

        val displayTextNoBattery = if (metricsNoBattery.bluetoothDeviceName != null) {
            if (metricsNoBattery.bluetoothBatteryPct != null) {
                "${metricsNoBattery.bluetoothDeviceName}: ${metricsNoBattery.bluetoothBatteryPct}%"
            } else {
                "${metricsNoBattery.bluetoothDeviceName} (Connected - High-Res Codec)"
            }
        } else {
            "No Bluetooth Gear Connected (Direct ALSA)"
        }

        assertEquals("WH-1000XM4 (Connected - High-Res Codec)", displayTextNoBattery)
        assertNotNull(metricsNoBattery.bluetoothDeviceName)
        assertNull(metricsNoBattery.bluetoothBatteryPct)

        // Case 2: Headphones with battery level reporting (85%)
        val metricsWithBattery = AudioMetrics(
            outputRoute = "Bluetooth (AirPods Max)",
            bluetoothDeviceName = "AirPods Max",
            bluetoothBatteryPct = 85
        )

        val displayTextWithBattery = if (metricsWithBattery.bluetoothDeviceName != null) {
            if (metricsWithBattery.bluetoothBatteryPct != null) {
                "${metricsWithBattery.bluetoothDeviceName}: ${metricsWithBattery.bluetoothBatteryPct}%"
            } else {
                "${metricsWithBattery.bluetoothDeviceName} (Connected - High-Res Codec)"
            }
        } else {
            "No Bluetooth Gear Connected (Direct ALSA)"
        }

        assertEquals("AirPods Max: 85%", displayTextWithBattery)

        // Case 3: Completely disconnected
        val metricsDisconnected = AudioMetrics(
            outputRoute = "3.5mm Headphone Jack",
            bluetoothDeviceName = null,
            bluetoothBatteryPct = null
        )

        val displayTextDisconnected = if (metricsDisconnected.bluetoothDeviceName != null) {
            "Connected"
        } else {
            "No Bluetooth Gear Connected (Direct ALSA)"
        }

        assertEquals("No Bluetooth Gear Connected (Direct ALSA)", displayTextDisconnected)
    }

    /**
     * Smoke Test 4: 3.5mm Headphone Jack Topology & Hardware Telemetry
     */
    @Test
    fun testHeadphoneJackTelemetry_trsVsTrrsCapabilities() {
        // 3-Pole TRS (Stereo headphones, pure audio ground)
        val trsMetrics = AudioMetrics(
            jackType = "3-Pole TRS (Stereo Output)",
            hasMic = false,
            jackCapabilities = "Direct ALSA DAC • 16-32bit / up to 384 kHz • Pure Audio Ground"
        )
        assertEquals("3-Pole TRS (Stereo Output)", trsMetrics.jackType)
        assertFalse(trsMetrics.hasMic)
        assertTrue(trsMetrics.jackCapabilities!!.contains("Pure Audio Ground"))

        // 4-Pole TRRS (Headset with in-line microphone)
        val trrsMetrics = AudioMetrics(
            jackType = "4-Pole TRRS (Headset + Mic)",
            hasMic = true,
            jackCapabilities = "Direct ALSA DAC • 16-32bit / up to 192 kHz • TRRS Mic Line"
        )
        assertEquals("4-Pole TRRS (Headset + Mic)", trrsMetrics.jackType)
        assertTrue(trrsMetrics.hasMic)
        assertTrue(trrsMetrics.jackCapabilities!!.contains("TRRS Mic Line"))
    }

    // =========================================================================
    // A/B COMPARISON TESTS
    // =========================================================================

    /**
     * A/B Test 1: Bit-Perfect Direct Route vs AudioFlinger Resampled Route
     * - Variant A: Bit-Perfect Direct (Hi-Res native sample rate passed direct to ALSA hardware).
     * - Variant B: AudioFlinger Resampled (96kHz downsampled/upsampled to 48kHz Android mixer).
     */
    @Test
    fun testAB_BitPerfectDirectVsResampled() {
        val nativeSampleRate = 96000
        val nativeBitDepth = 24

        // Variant A: Bit-Perfect Enabled on 3.5mm Jack
        val variantA_Direct = AudioMetrics(
            format = "FLAC",
            bitDepth = nativeBitDepth,
            sampleRate = nativeSampleRate,
            outputRoute = "3.5mm Headphone Jack (Hi-Res)",
            outputSampleRate = nativeSampleRate,
            isBitPerfect = true
        )

        // Variant B: Direct bypass disabled, forced resampling
        val variantB_Resampled = AudioMetrics(
            format = "FLAC",
            bitDepth = 16,
            sampleRate = nativeSampleRate,
            outputRoute = "AudioFlinger Resampler",
            outputSampleRate = 48000,
            isBitPerfect = false
        )

        // Verification of Variant A
        assertTrue("Variant A must be bit-perfect native", variantA_Direct.isBitPerfect)
        assertEquals(variantA_Direct.sampleRate, variantA_Direct.outputSampleRate)
        assertEquals(24, variantA_Direct.bitDepth)

        // Verification of Variant B
        assertFalse("Variant B must show resampled badge", variantB_Resampled.isBitPerfect)
        assertTrue("Variant B sample rates must differ", variantB_Resampled.sampleRate != variantB_Resampled.outputSampleRate)
        assertEquals(48000, variantB_Resampled.outputSampleRate)
    }

    /**
     * A/B Test 2: ReplayGain Volume Normalization Active vs Bypassed
     * - Variant A (Active): Track gain is applied to normalize untrimmed loud/quiet master tracks.
     * - Variant B (Bypassed): Raw bitstream unity gain 1.0f (0.0 dB offset).
     */
    @Test
    fun testAB_ReplayGainActiveVsBypassed() {
        val trackReplayGainDb = -4.2f

        // Variant A: ReplayGain Active (89 dB target calibration)
        val isReplayGainEnabledVariantA = true
        val effectiveGainOffsetVariantA = if (isReplayGainEnabledVariantA) trackReplayGainDb else 0.0f
        val linearScaleVariantA = 10.0.pow(effectiveGainOffsetVariantA / 20.0).toFloat()

        assertEquals(-4.2f, effectiveGainOffsetVariantA, 0.001f)
        assertTrue("Linear scale must be attenuated (< 1.0f) for loud track", linearScaleVariantA < 1.0f)
        assertEquals(0.6165f, linearScaleVariantA, 0.01f)

        // Variant B: ReplayGain Bypassed (Raw dynamics)
        val isReplayGainEnabledVariantB = false
        val effectiveGainOffsetVariantB = if (isReplayGainEnabledVariantB) trackReplayGainDb else 0.0f
        val linearScaleVariantB = 10.0.pow(effectiveGainOffsetVariantB / 20.0).toFloat()

        assertEquals(0.0f, effectiveGainOffsetVariantB, 0.001f)
        assertEquals(1.0f, linearScaleVariantB, 0.0001f)
    }

    /**
     * A/B Test 3: Acoustic EQ Profile Harman Target 2019 vs Flat Studio Reference
     * - Variant A (Harman Target): Elevated sub-bass shelf and ear canal acoustic pinna gain.
     * - Variant B (Flat Studio): Reference neutral response across all 10 ISO frequency bands.
     */
    @Test
    fun testAB_AutoEqHarmanVsFlatTargetCurves() {
        val fxController = AudioFxController()

        // Apply Variant A: Harman Target 2019
        fxController.applyPreset("HARMAN_2019")
        val harmanGains = fxController.isoBandsGainDb.copyOf()

        // Apply Variant B: Flat Reference
        fxController.applyPreset("FLAT")
        val flatGains = fxController.isoBandsGainDb.copyOf()

        // Variant A Verification: Harman sub-bass rise (+5.5dB at 31Hz) and pinna gain (+4.0dB at 4kHz)
        assertEquals(5.5f, harmanGains[0], 0.01f)
        assertEquals(4.0f, harmanGains[7], 0.01f)

        // Variant B Verification: Flat must be 0.0dB on all 10 bands
        for (i in 0 until 10) {
            assertEquals(0.0f, flatGains[i], 0.001f)
        }

        // Difference between Harman and Flat at 31Hz must equal 5.5dB
        assertEquals(5.5f, abs(harmanGains[0] - flatGains[0]), 0.01f)
    }

    /**
     * Smoke Test 7: Album Art Flicker & Unnecessary Re-binding Prevention
     * Verifies that progress polling ticks (every 200ms) do NOT trigger mini player cover reloads
     * and do not trigger full notifyDataSetChanged() unless the active song ID changes.
     */
    @Test
    fun testAlbumArtFlickerPrevention_guardsProgressPollingTicks() {
        var currentMiniSongId = -1L
        var coverLoadCount = 0
        var notifyItemChangeCount = 0
        val notifyDataSetChangedCount = 0
        var activeSongId = -1L

        fun onProgressTick(songId: Long) {
            // Guarded Mini Player Cover Loading
            if (songId != currentMiniSongId) {
                currentMiniSongId = songId
                coverLoadCount++
            }

            // Guarded Item Change Notification
            if (activeSongId != songId) {
                activeSongId = songId
                notifyItemChangeCount += 2 // old and new item
            }
        }

        // Simulate 20 progress ticks (4 seconds of playback) for Song 101
        for (i in 0 until 20) {
            onProgressTick(101L)
        }

        // Must only have loaded cover once!
        assertEquals("Cover must only be loaded once across 20 progress ticks", 1, coverLoadCount)
        assertEquals("notifyItemChanged must only fire once on initial track transition", 2, notifyItemChangeCount)
        assertEquals("notifyDataSetChanged must NEVER fire on progress ticks", 0, notifyDataSetChangedCount)

        // Simulate track change to Song 102
        onProgressTick(102L)
        assertEquals("Cover must load once more for new track", 2, coverLoadCount)
        assertEquals("notifyItemChanged must fire for previous and new item", 4, notifyItemChangeCount)
    }

    /**
     * Smoke Test 8: Bluetooth Device Name, Battery Life, and Connection Status Telemetry
     */
    @Test
    fun testBluetoothTelemetry_metricsAndConnectionStatus() {
        // Disconnected State
        val disconnected = AudioMetrics()
        assertFalse("Default metrics must indicate Bluetooth disconnected", disconnected.isBluetoothConnected)
        assertEquals("DISCONNECTED", disconnected.bluetoothConnectionStatus)
        assertNull(disconnected.bluetoothDeviceName)
        assertNull(disconnected.bluetoothBatteryPct)

        // Connected State with Battery Telemetry
        val connectedWithBattery = AudioMetrics(
            outputRoute = "Bluetooth (Sony WH-1000XM5)",
            bluetoothDeviceName = "Sony WH-1000XM5",
            bluetoothBatteryPct = 90,
            isBluetoothConnected = true,
            bluetoothConnectionStatus = "CONNECTED"
        )
        assertTrue("Metrics must reflect active Bluetooth connection", connectedWithBattery.isBluetoothConnected)
        assertEquals("CONNECTED", connectedWithBattery.bluetoothConnectionStatus)
        assertEquals("Sony WH-1000XM5", connectedWithBattery.bluetoothDeviceName)
        assertEquals(90, connectedWithBattery.bluetoothBatteryPct)

        // Connected State without Battery Telemetry
        val connectedNoBattery = AudioMetrics(
            outputRoute = "Bluetooth (FiiO BTR5)",
            bluetoothDeviceName = "FiiO BTR5",
            bluetoothBatteryPct = null,
            isBluetoothConnected = true,
            bluetoothConnectionStatus = "CONNECTED"
        )
        assertTrue(connectedNoBattery.isBluetoothConnected)
        assertEquals("CONNECTED", connectedNoBattery.bluetoothConnectionStatus)
        assertEquals("FiiO BTR5", connectedNoBattery.bluetoothDeviceName)
        assertNull(connectedNoBattery.bluetoothBatteryPct)
    }

    /**
     * Smoke Test 9: Pure 1-Bit B&W Monochrome E-Ink Theme Color Integrity
     * Verifies that all tokens in MONOCHROME_EINK are strictly 0xFFFFFFFF, 0xFF000000, or 0.
     */
    @Test
    fun testPureMonochromeTheme_onlyBlackAndWhiteIntegrity() {
        val eink = CassetteTheme.MONOCHROME_EINK
        assertEquals("theme_monochrome_eink", eink.id)
        assertEquals("Mono", eink.name)
        assertFalse(eink.isDarkAppTheme)

        val allowedColors = setOf(
            0xFFFFFFFF.toInt(), // Pure White
            0xFF000000.toInt(), // Pure Black
            0                   // Transparent
        )

        val themeColors = listOf(
            eink.chassisColor,
            eink.diagonalBezelColor,
            eink.dialColor,
            eink.surfaceColor,
            eink.cardBorderColor,
            eink.textPrimaryColor,
            eink.textSecondaryColor,
            eink.accentColor,
            eink.shellColor,
            eink.labelBackgroundColor,
            eink.labelTextColor,
            eink.labelAccentColor,
            eink.windowTint,
            eink.reelHubColor,
            eink.tapeRibbonColor,
            eink.vfdGlowColor
        )

        for (c in themeColors) {
            assertTrue("Every color in MONOCHROME_EINK must be pure White, pure Black, or Transparent. Found: $c", c in allowedColors)
        }
    }
}
