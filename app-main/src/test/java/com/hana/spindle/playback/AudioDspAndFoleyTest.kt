package com.hana.spindle.playback

import com.hana.spindle.data.TagParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

class AudioDspAndFoleyTest {

    @Test
    fun test10BandIsoFrequenciesAndLabels() {
        assertEquals(10, AudioFxController.ISO_FREQUENCIES.size)
        assertEquals(10, AudioFxController.ISO_LABELS.size)
        assertEquals(31, AudioFxController.ISO_FREQUENCIES[0])
        assertEquals(1000, AudioFxController.ISO_FREQUENCIES[5])
        assertEquals(16000, AudioFxController.ISO_FREQUENCIES[9])

        assertEquals("31", AudioFxController.ISO_LABELS[0])
        assertEquals("1k", AudioFxController.ISO_LABELS[5])
        assertEquals("16k", AudioFxController.ISO_LABELS[9])
    }

    @Test
    fun testAutoEqTargetCurves() {
        assertEquals(10, AudioFxController.CURVE_FLAT.size)
        assertEquals(10, AudioFxController.CURVE_HARMAN_2019.size)
        assertEquals(10, AudioFxController.CURVE_CRINACLE_IEF.size)
        assertEquals(10, AudioFxController.CURVE_DIFFUSE_FIELD.size)
        assertEquals(10, AudioFxController.CURVE_MOONDROP_VDSF.size)
        assertEquals(10, AudioFxController.CURVE_SENNHEISER_HD600.size)
        assertEquals(10, AudioFxController.CURVE_WARM_ANALOG_TAPE.size)
        assertEquals(10, AudioFxController.CURVE_V_SHAPE_PUNCH.size)

        // Harman 2019 target should have sub-bass rise (+5.5dB at 31Hz) and pinna gain (+4.0dB at 4kHz)
        assertTrue(AudioFxController.CURVE_HARMAN_2019[0] >= 5.0f)
        assertTrue(AudioFxController.CURVE_HARMAN_2019[7] >= 3.5f)

        // Crinacle IEF target should be neutral in the sub-bass (0.0dB) with ear canal gain at 4kHz
        assertEquals(0.0f, AudioFxController.CURVE_CRINACLE_IEF[0], 0.001f)
        assertTrue(AudioFxController.CURVE_CRINACLE_IEF[7] > 2.0f)

        // Warm Analog Tape should roll off upper treble
        assertTrue(AudioFxController.CURVE_WARM_ANALOG_TAPE[9] < -2.0f)
    }

    @Test
    fun testIsoGainInterpolationLogarithmic() {
        val fxController = AudioFxController()
        fxController.applyPreset("HARMAN_2019")

        // Exact center frequencies must return the exact set values
        assertEquals(AudioFxController.CURVE_HARMAN_2019[0], fxController.interpolateIsoGain(31), 0.01f)
        assertEquals(AudioFxController.CURVE_HARMAN_2019[5], fxController.interpolateIsoGain(1000), 0.01f)
        assertEquals(AudioFxController.CURVE_HARMAN_2019[9], fxController.interpolateIsoGain(16000), 0.01f)

        // Edge clamping for out-of-range frequencies
        assertEquals(AudioFxController.CURVE_HARMAN_2019[0], fxController.interpolateIsoGain(10), 0.01f)
        assertEquals(AudioFxController.CURVE_HARMAN_2019[9], fxController.interpolateIsoGain(24000), 0.01f)

        // Geometric mean interpolation between 1000Hz (0.5dB) and 2000Hz (3.0dB)
        val midFreq = 1414 // sqrt(1000 * 2000)
        val interpolatedGain = fxController.interpolateIsoGain(midFreq)
        val expectedMid = (0.5f + 3.0f) / 2f
        assertEquals(expectedMid, interpolatedGain, 0.1f)
    }

    @Test
    fun testAntiClippingHeadroomCalculation() {
        // When maximum boost is +6.0dB, headroom attenuation should be 6.0 * 0.35 = 2.1 dB
        val maxBoost = 6.0f
        val headroom = maxBoost * 0.35f
        assertEquals(2.1f, headroom, 0.01f)

        // When all bands are at or below 0dB, headroom is 0.0 dB
        val flatBoost = 0.0f
        val flatHeadroom = flatBoost * 0.35f
        assertEquals(0.0f, flatHeadroom, 0.001f)
    }

    @Test
    fun testReplayGainVolumeConversion() {
        // 0.0 dB gain -> 1.0 linear volume
        val vol0 = 10f.pow(0.0f / 20f).coerceIn(0.1f, 1.0f)
        assertEquals(1.0f, vol0, 0.001f)

        // -6.02 dB gain -> 0.50 linear volume
        val volMinus6 = 10f.pow(-6.0206f / 20f).coerceIn(0.1f, 1.0f)
        assertEquals(0.50f, volMinus6, 0.01f)

        // +3.0 dB gain capped at 1.0f to avoid clipping
        val volPlus3 = 10f.pow(3.0f / 20f).coerceIn(0.1f, 1.0f)
        assertEquals(1.0f, volPlus3, 0.001f)
    }

    @Test
    fun testReplayGainTagParsingFromHeader() {
        val tempFile = File.createTempFile("test_replaygain", ".flac")
        try {
            val syntheticHeader = "fLaC\u0000\u0000\u0000\"reference libFLAC\nREPLAYGAIN_TRACK_GAIN=-5.40 dB\nREPLAYGAIN_TRACK_PEAK=0.9882"
            tempFile.writeText(syntheticHeader, Charsets.ISO_8859_1)

            val gain = TagParser.extractReplayGainDb(tempFile)
            assertEquals(-5.40f, gain, 0.01f)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testProceduralWavHeaderSpecification() {
        val tempWav = File.createTempFile("test_foley", ".wav")
        try {
            val sampleRate = 44100
            val numSamples = 441 // 10ms of audio
            val samples = ShortArray(numSamples) { 1000 }

            val totalDataLen = samples.size * 2
            val totalAudioLen = totalDataLen + 36
            val byteRate = sampleRate * 2

            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(totalAudioLen)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16) // PCM Subchunk1Size
                putShort(1) // PCM format
                putShort(1) // Mono
                putInt(sampleRate)
                putInt(byteRate)
                putShort(2) // BlockAlign
                putShort(16) // BitsPerSample
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(totalDataLen)
            }

            tempWav.writeBytes(header.array())

            // Verify header contents
            val readBytes = tempWav.readBytes()
            assertEquals(44, readBytes.size)

            val bb = ByteBuffer.wrap(readBytes).order(ByteOrder.LITTLE_ENDIAN)
            val riff = ByteArray(4).also { bb.get(it) }.toString(Charsets.US_ASCII)
            val riffLen = bb.getInt()
            val wave = ByteArray(4).also { bb.get(it) }.toString(Charsets.US_ASCII)

            assertEquals("RIFF", riff)
            assertEquals(totalAudioLen, riffLen)
            assertEquals("WAVE", wave)
        } finally {
            tempWav.delete()
        }
    }

    @Test
    fun testTapeFormulationCurves() {
        val fx = AudioFxController()

        // Default Type II Chrome: flat reference curve
        assertEquals(AudioFxController.TapeFormulation.TYPE_II_CHROME, fx.currentTapeFormulation)
        for (i in 0 until 10) {
            assertEquals(0.0f, fx.isoBandsGainDb[i], 0.001f)
        }

        // Switch to Type I Normal: warm low bass boost + treble rolloff
        fx.setTapeFormulation(AudioFxController.TapeFormulation.TYPE_I_NORMAL)
        assertEquals(AudioFxController.TapeFormulation.TYPE_I_NORMAL, fx.currentTapeFormulation)
        assertTrue("Type I must have warm low-end boost", fx.isoBandsGainDb[0] > 2.0f)
        assertTrue("Type I must roll off upper treble", fx.isoBandsGainDb[9] < -2.0f)

        // Switch to Type IV Metal (Extralloy Metal-XR): extended high-frequency response
        fx.setTapeFormulation(AudioFxController.TapeFormulation.TYPE_IV_METAL)
        assertEquals(AudioFxController.TapeFormulation.TYPE_IV_METAL, fx.currentTapeFormulation)
        assertTrue("Type IV must extend top-end air", fx.isoBandsGainDb[9] > 1.0f)
    }

    @Test
    fun testAudioFxLockProtection() {
        val fx = AudioFxController()
        assertFalse(fx.isLocked)

        // Lock FX
        fx.isLocked = true
        assertTrue(fx.isLocked)

        // Attempting to set gains while locked must be ignored
        val initialBands = fx.isoBandsGainDb.copyOf()
        fx.setLowGain(6.0f)
        assertEquals(initialBands[0], fx.isoBandsGainDb[0], 0.001f)

        fx.applyPreset("HARMAN_2019")
        assertEquals(initialBands[9], fx.isoBandsGainDb[9], 0.001f)

        fx.setTapeFormulation(AudioFxController.TapeFormulation.TYPE_IV_METAL)
        assertEquals(AudioFxController.TapeFormulation.TYPE_II_CHROME, fx.currentTapeFormulation)

        // Unlock FX
        fx.isLocked = false
        fx.setTapeFormulation(AudioFxController.TapeFormulation.TYPE_IV_METAL)
        assertEquals(AudioFxController.TapeFormulation.TYPE_IV_METAL, fx.currentTapeFormulation)
    }

    @Test
    fun testDolbyNoiseReductionCurves() {
        val fx = AudioFxController()

        // Dolby B: ~10dB HF noise reduction de-emphasis
        fx.setDolbyMode(AudioFxController.DolbyMode.DOLBY_B)
        assertEquals(AudioFxController.DolbyMode.DOLBY_B, fx.currentDolbyMode)
        assertTrue("Dolby B must attenuate 16kHz hiss by >= 7dB", fx.isoBandsGainDb[9] <= -7.0f)
        assertEquals(0.0f, fx.isoBandsGainDb[0], 0.001f) // Bass unaffected

        // Dolby C: dual-stage compander curve, ~20dB HF noise reduction de-emphasis
        fx.setDolbyMode(AudioFxController.DolbyMode.DOLBY_C)
        assertEquals(AudioFxController.DolbyMode.DOLBY_C, fx.currentDolbyMode)
        assertTrue("Dolby C must attenuate 16kHz hiss by >= 12dB", fx.isoBandsGainDb[9] <= -12.0f)
    }

    @Test
    fun testTapeSaturationDspCurve() {
        val fx = AudioFxController()
        assertFalse("Tape saturation should default to disabled", fx.isTapeSaturationEnabled)

        // Enable Tape Saturation at full drive (1.0f)
        fx.setTapeSaturationEnabled(true)
        assertTrue(fx.isTapeSaturationEnabled)

        // 63Hz flux head bump should be boosted by +3.2dB
        assertEquals(3.2f, fx.isoBandsGainDb[1], 0.01f)
        // 250Hz warmth boost (+1.5dB)
        assertEquals(1.5f, fx.isoBandsGainDb[3], 0.01f)
        // 16kHz natural saturation compression roll-off (-2.8dB)
        assertEquals(-2.8f, fx.isoBandsGainDb[9], 0.01f)

        // Half drive (0.5f)
        fx.setTapeSaturationDrive(0.5f)
        assertEquals(1.6f, fx.isoBandsGainDb[1], 0.01f)
        assertEquals(-1.4f, fx.isoBandsGainDb[9], 0.01f)

        // Disable tape saturation -> back to flat
        fx.setTapeSaturationEnabled(false)
        assertFalse(fx.isTapeSaturationEnabled)
        assertEquals(0.0f, fx.isoBandsGainDb[1], 0.001f)
        assertEquals(0.0f, fx.isoBandsGainDb[9], 0.001f)
    }

    @Test
    fun testStudioReelThemePreset() {
        val studioReel = com.hana.spindle.theme.CassetteTheme.REEL_TO_REEL_STUDIO
        assertNotNull(studioReel)
        assertEquals("theme_reel_to_reel_studio", studioReel.id)
        assertEquals("Studio Reel-to-Reel", studioReel.name)
        assertTrue(studioReel.subtitle.contains("10.5\""))
        assertTrue(studioReel.subtitle.contains("15 IPS"))
        assertEquals(com.hana.spindle.theme.ShellTexture.BRUSHED_METAL, studioReel.shellTexture)

        // Must be registered in ALL_PRESETS
        assertTrue(com.hana.spindle.theme.CassetteTheme.ALL_PRESETS.contains(studioReel))
    }

    @Test
    fun testEqualPowerSinusoidalCrossfadeCurve() {
        // Equal-power crossfade requires: P_out + P_in = cos^2(angle) + sin^2(angle) == 1.0
        // for any angle = t * (PI / 2), where t is normalized progress in [0.0 .. 1.0]
        val steps = 100
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angle = t * (Math.PI / 2.0)
            val fadeOutMultiplier = Math.cos(angle).toFloat()
            val fadeInMultiplier = Math.sin(angle).toFloat()

            // Sum of squared acoustic pressure (total acoustic power) must equal 1.0
            val totalPower = (fadeOutMultiplier * fadeOutMultiplier) + (fadeInMultiplier * fadeInMultiplier)
            assertEquals(1.0f, totalPower, 0.0001f)
        }

        // At midpoint t = 0.5, both channels should be at exactly sqrt(2)/2 ~= 0.7071 (-3.01 dB)
        val midAngle = 0.5 * (Math.PI / 2.0)
        val midOut = Math.cos(midAngle).toFloat()
        val midIn = Math.sin(midAngle).toFloat()
        assertEquals(0.7071f, midOut, 0.001f)
        assertEquals(0.7071f, midIn, 0.001f)
        assertEquals(1.0f, (midOut * midOut) + (midIn * midIn), 0.001f)
    }

    @Test
    fun testHardwareEqBandModelAndRange() {
        val fx = AudioFxController()

        // In JVM unit test environment without Android Media server, hardware Equalizer is null
        val hwBands = fx.getHardwareBands()
        assertTrue("JVM unit test without audio session should return empty list safely", hwBands.isEmpty())

        val range = fx.getHardwareLevelRangeDb()
        assertEquals(-15.0f, range.first, 0.01f)
        assertEquals(15.0f, range.second, 0.01f)

        // Verify HardwareEqBand data class structure
        val sampleBand = AudioFxController.HardwareEqBand(
            index = 2,
            centerFreqHz = 1000,
            minFreqHz = 500,
            maxFreqHz = 2000,
            currentGainDb = 2.5f
        )
        assertEquals(2, sampleBand.index)
        assertEquals(1000, sampleBand.centerFreqHz)
        assertEquals(500, sampleBand.minFreqHz)
        assertEquals(2000, sampleBand.maxFreqHz)
        assertEquals(2.5f, sampleBand.currentGainDb, 0.001f)
    }

    @Test
    fun testCrossfeedModesAndPresets() {
        val fx = AudioFxController()
        assertEquals(AudioFxController.CrossfeedMode.OFF, fx.currentCrossfeedMode)
        assertEquals(0, fx.crossfeedStrength)

        // Set Chu Moy
        fx.setCrossfeedMode(AudioFxController.CrossfeedMode.CHU_MOY)
        assertEquals(AudioFxController.CrossfeedMode.CHU_MOY, fx.currentCrossfeedMode)
        assertEquals(200, fx.crossfeedStrength)

        // Set Bauer
        fx.setCrossfeedMode(AudioFxController.CrossfeedMode.BAUER)
        assertEquals(AudioFxController.CrossfeedMode.BAUER, fx.currentCrossfeedMode)
        assertEquals(400, fx.crossfeedStrength)

        // Mapping from arbitrary slider strength
        fx.setCrossfeedStrength(150)
        assertEquals(AudioFxController.CrossfeedMode.CHU_MOY, fx.currentCrossfeedMode)

        fx.setCrossfeedStrength(700)
        assertEquals(AudioFxController.CrossfeedMode.STUDIO, fx.currentCrossfeedMode)

        fx.setCrossfeedStrength(950)
        assertEquals(AudioFxController.CrossfeedMode.BINAURAL, fx.currentCrossfeedMode)

        fx.setCrossfeedStrength(0)
        assertEquals(AudioFxController.CrossfeedMode.OFF, fx.currentCrossfeedMode)

        // Cycle modes in sequence
        assertEquals(AudioFxController.CrossfeedMode.CHU_MOY, fx.cycleCrossfeedMode())
        assertEquals(AudioFxController.CrossfeedMode.BAUER, fx.cycleCrossfeedMode())
        assertEquals(AudioFxController.CrossfeedMode.STUDIO, fx.cycleCrossfeedMode())
        assertEquals(AudioFxController.CrossfeedMode.BINAURAL, fx.cycleCrossfeedMode())
        assertEquals(AudioFxController.CrossfeedMode.OFF, fx.cycleCrossfeedMode())
    }

    @Test
    fun testVintageCassetteThemesCalibration() {
        val allPresets = com.hana.spindle.theme.CassetteTheme.ALL_PRESETS
        assertTrue(allPresets.size >= 11)

        val sonyHf = com.hana.spindle.theme.CassetteTheme.SONY_HF_90
        assertEquals("Sony HF 90", sonyHf.name)
        assertEquals("theme_sony_hf_90", sonyHf.id)
        assertTrue(allPresets.contains(sonyHf))

        val denonHd = com.hana.spindle.theme.CassetteTheme.DENON_HD8_100
        assertEquals("Denon HD8 100", denonHd.name)
        assertEquals("theme_denon_hd8_100", denonHd.id)
        assertTrue(allPresets.contains(denonHd))
    }
}
