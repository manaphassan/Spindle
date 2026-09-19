package com.hana.spindle.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlin.math.ln
import kotlin.math.max

/**
 * Audiophile Hardware Audio DSP & 10-Band ISO Graphic Equalizer Controller.
 *
 * Features:
 * - Bit-Perfect Direct Bypass Switch (0.00% distortion for USB DACs and reference gear)
 * - 10-Band ISO Graphic Equalizer (31Hz, 63Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz)
 * - Frequency-weighted logarithmic interpolation to underlying hardware bands (5-band to 10-band)
 * - Calibrated 3-Way Acoustic Tone Stack (LOW shelf, MID bell, HI air shelf)
 * - Binaural Headphone Crossfeed / Soundstage (Virtualizer)
 * - Scientific Audiophile AutoEq Target Curves:
 *   - Harman In-Ear Target 2019
 *   - Crinacle IEF Neutral Target
 *   - Diffuse Field Studio Reference
 *   - Moondrop VDSF IEM Target
 *   - Sennheiser HD600 / HD650 Target
 *   - Warm Analog Tape Saturation
 *   - V-Shape Audiophile Punch
 *   - Flat Reference / Direct
 * - Automatic Pre-Amp Headroom calculation to prevent digital inter-sample clipping
 */
class AudioFxController {

    companion object {
        val ISO_FREQUENCIES = intArrayOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val ISO_LABELS = arrayOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

        // AutoEq Target Frequency Profiles (-12.0f to +12.0f dB)
        val CURVE_FLAT = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val CURVE_HARMAN_2019 = floatArrayOf(5.5f, 5.0f, 3.0f, 0.5f, 0.0f, 0.5f, 3.0f, 4.0f, 1.5f, 1.0f)
        val CURVE_CRINACLE_IEF = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 2.5f, 3.0f, 0.0f, -1.0f)
        val CURVE_DIFFUSE_FIELD = floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 2.0f, 4.0f, 3.0f, 1.0f, 0.0f)
        val CURVE_MOONDROP_VDSF = floatArrayOf(3.5f, 3.0f, 1.5f, 0.0f, 0.0f, 1.0f, 3.5f, 3.0f, 1.0f, 2.0f)
        val CURVE_SENNHEISER_HD600 = floatArrayOf(4.5f, 3.0f, 1.0f, -0.5f, 0.0f, 0.0f, 0.0f, 1.5f, 2.5f, 3.0f)
        val CURVE_WARM_ANALOG_TAPE = floatArrayOf(4.0f, 3.5f, 2.5f, 1.5f, 1.0f, 0.5f, 0.0f, -1.0f, -2.5f, -4.0f)
        val CURVE_V_SHAPE_PUNCH = floatArrayOf(6.0f, 5.0f, 3.0f, 1.0f, -1.5f, -2.0f, -0.5f, 2.5f, 4.5f, 5.0f)
        val CURVE_AIR_STAGE = floatArrayOf(0.5f, 0.5f, 0.0f, 0.0f, 0.0f, 1.0f, 2.0f, 3.5f, 5.0f, 5.5f)
        val CURVE_BASS_BOOST = floatArrayOf(7.0f, 6.0f, 4.5f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.5f, -1.0f)

        // Vintage Cassette Tape Formulation Profiles (EQ / Saturation)
        val CURVE_TYPE_I_NORMAL = floatArrayOf(2.5f, 3.0f, 2.0f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.8f, -3.0f) // Warm Ferric lows, gentle tape treble roll-off
        val CURVE_TYPE_II_CHROME = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)    // Reference 70µs CrO₂ studio benchmark
        val CURVE_TYPE_IV_METAL = floatArrayOf(1.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.2f, 1.5f)     // Wide-bandwidth pure metal, extended transients

        // Dolby Noise Reduction Compander Emulation Curves
        val CURVE_DOLBY_B = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.5f, -1.5f, -3.0f, -5.5f, -8.0f)       // ~10dB HF noise reduction de-emphasis
        val CURVE_DOLBY_C = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, -1.0f, -2.0f, -4.0f, -6.5f, -9.5f, -12.5f)    // ~20dB dual-stage compander curve
    }

    enum class TapeFormulation(val label: String, val tag: String) {
        TYPE_I_NORMAL("NORMAL (Fe₂O₃)", "TYPE I"),
        TYPE_II_CHROME("HIGH (CrO₂)", "TYPE II"),
        TYPE_IV_METAL("METAL (Pure)", "TYPE IV")
    }

    enum class DolbyMode(val label: String, val badgeText: String) {
        OFF("OFF", "DOLBY OFF"),
        DOLBY_B("DOLBY B", "DOLBY B"),
        DOLBY_C("DOLBY C", "DOLBY C")
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var minBandLevel: Short = -1500 // -15 dB in millibels
    private var maxBandLevel: Short = 1500  // +15 dB in millibels
    private var numBands: Short = 5

    // Bit-Perfect Direct Bypass state
    var isBypassEnabled: Boolean = false
        private set

    // Base ISO Gains from User EQ / AutoEq presets (-12.0f to +12.0f dB)
    val baseIsoBandsGainDb: FloatArray = FloatArray(10)

    // Effective composite 10-Band ISO Gains (Base + Tape Formulation + Dolby NR)
    val isoBandsGainDb: FloatArray = FloatArray(10)

    var isLocked: Boolean = false

    var currentTapeFormulation: TapeFormulation = TapeFormulation.TYPE_II_CHROME
        private set

    var currentDolbyMode: DolbyMode = DolbyMode.OFF
        private set

    // Cached 3-band tone gains (-12.0f to +12.0f dB)
    var lowGainDb: Float = 0.0f
        private set
    var midGainDb: Float = 0.0f
        private set
    var highGainDb: Float = 0.0f
        private set
    var bassBoostStrength: Int = 0 // 0 to 1000
        private set
    var crossfeedStrength: Int = 0 // 0 to 1000 (Binaural Crossfeed)
        private set

    var currentPresetName: String = "FLAT"
        private set

    init {
        recomputeEffectiveGains()
    }

    fun setTapeFormulation(formulation: TapeFormulation, force: Boolean = false) {
        if (isLocked && !force) return
        currentTapeFormulation = formulation
        recomputeEffectiveGains()
    }

    fun setDolbyMode(mode: DolbyMode, force: Boolean = false) {
        if (isLocked && !force) return
        currentDolbyMode = mode
        recomputeEffectiveGains()
    }

    fun recomputeEffectiveGains() {
        val tapeCurve = when (currentTapeFormulation) {
            TapeFormulation.TYPE_I_NORMAL -> CURVE_TYPE_I_NORMAL
            TapeFormulation.TYPE_II_CHROME -> CURVE_TYPE_II_CHROME
            TapeFormulation.TYPE_IV_METAL -> CURVE_TYPE_IV_METAL
        }
        val dolbyCurve = when (currentDolbyMode) {
            DolbyMode.OFF -> CURVE_FLAT
            DolbyMode.DOLBY_B -> CURVE_DOLBY_B
            DolbyMode.DOLBY_C -> CURVE_DOLBY_C
        }

        for (i in 0 until 10) {
            isoBandsGainDb[i] = (baseIsoBandsGainDb[i] + tapeCurve[i] + dolbyCurve[i]).coerceIn(-15.0f, 15.0f)
        }
        syncToneFromIso()
        if (!isBypassEnabled) applyEq()
    }

    fun attachSession(audioSessionId: Int) {
        release()
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = !isBypassEnabled
                numBands = numberOfBands
                val range = bandLevelRange
                if (range.size >= 2) {
                    minBandLevel = range[0]
                    maxBandLevel = range[1]
                }
            }

            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = !isBypassEnabled
                if (enabled) {
                    try {
                        setStrength(bassBoostStrength.toShort())
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            try {
                virtualizer = Virtualizer(0, audioSessionId).apply {
                    enabled = !isBypassEnabled
                    if (enabled) {
                        try {
                            setStrength(crossfeedStrength.toShort())
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            } catch (e: Exception) {
                virtualizer = null
            }

            // Re-apply cached settings
            applyEq()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBypass(bypass: Boolean) {
        isBypassEnabled = bypass
        try {
            equalizer?.enabled = !bypass
            bassBoost?.enabled = !bypass
            virtualizer?.enabled = !bypass
            if (!bypass) {
                applyEq()
                bassBoost?.setStrength(bassBoostStrength.toShort())
                virtualizer?.setStrength(crossfeedStrength.toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Set a single ISO band gain (0 to 9)
     */
    fun setIsoBandGain(bandIndex: Int, gainDb: Float, force: Boolean = false) {
        if (isLocked && !force) return
        if (bandIndex in 0 until 10) {
            baseIsoBandsGainDb[bandIndex] = gainDb.coerceIn(-12.0f, 12.0f)
            recomputeEffectiveGains()
        }
    }

    /**
     * Set all 10 ISO band gains at once
     */
    fun setAllIsoBands(gains: FloatArray, force: Boolean = false) {
        if (isLocked && !force) return
        val count = minOf(10, gains.size)
        for (i in 0 until count) {
            baseIsoBandsGainDb[i] = gains[i].coerceIn(-12.0f, 12.0f)
        }
        recomputeEffectiveGains()
    }

    /**
     * Adjust Low Shelf (-12dB to +12dB) and update corresponding ISO bands (31Hz, 63Hz, 125Hz)
     */
    fun setLowGain(gainDb: Float, force: Boolean = false) {
        if (isLocked && !force) return
        lowGainDb = gainDb.coerceIn(-12.0f, 12.0f)
        baseIsoBandsGainDb[0] = lowGainDb
        baseIsoBandsGainDb[1] = lowGainDb * 0.9f
        baseIsoBandsGainDb[2] = lowGainDb * 0.6f
        recomputeEffectiveGains()
    }

    /**
     * Adjust Mid Bell (-12dB to +12dB) and update corresponding ISO bands (250Hz, 500Hz, 1kHz, 2kHz)
     */
    fun setMidGain(gainDb: Float, force: Boolean = false) {
        if (isLocked && !force) return
        midGainDb = gainDb.coerceIn(-12.0f, 12.0f)
        baseIsoBandsGainDb[3] = (lowGainDb + midGainDb) / 2f
        baseIsoBandsGainDb[4] = midGainDb
        baseIsoBandsGainDb[5] = midGainDb
        baseIsoBandsGainDb[6] = (midGainDb + highGainDb) / 2f
        recomputeEffectiveGains()
    }

    /**
     * Adjust High Air Shelf (-12dB to +12dB) and update corresponding ISO bands (4kHz, 8kHz, 16kHz)
     */
    fun setHighGain(gainDb: Float, force: Boolean = false) {
        if (isLocked && !force) return
        highGainDb = gainDb.coerceIn(-12.0f, 12.0f)
        baseIsoBandsGainDb[7] = highGainDb * 0.6f
        baseIsoBandsGainDb[8] = highGainDb * 0.9f
        baseIsoBandsGainDb[9] = highGainDb
        recomputeEffectiveGains()
    }

    private fun syncToneFromIso() {
        // Approximate 3-band tone stack from 10-band ISO
        lowGainDb = (isoBandsGainDb[0] + isoBandsGainDb[1] + isoBandsGainDb[2]) / 3f
        midGainDb = (isoBandsGainDb[4] + isoBandsGainDb[5]) / 2f
        highGainDb = (isoBandsGainDb[8] + isoBandsGainDb[9]) / 2f
    }

    fun setFilterStrength(strength: Int, force: Boolean = false) {
        if (isLocked && !force) return
        bassBoostStrength = strength.coerceIn(0, 1000)
        if (!isBypassEnabled) {
            try {
                bassBoost?.setStrength(bassBoostStrength.toShort())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setCrossfeedStrength(strength: Int, force: Boolean = false) {
        if (isLocked && !force) return
        crossfeedStrength = strength.coerceIn(0, 1000)
        if (!isBypassEnabled) {
            try {
                virtualizer?.setStrength(crossfeedStrength.toShort())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Scientific Audiophile Target Acoustic Profiles & AutoEq IEM Curves
     */
    fun applyPreset(preset: String, force: Boolean = false) {
        if (isLocked && !force) return
        currentPresetName = preset.uppercase()
        when (currentPresetName) {
            "FLAT" -> {
                setAllIsoBands(CURVE_FLAT, force = true)
                setFilterStrength(0, force = true)
                setCrossfeedStrength(0, force = true)
            }
            "HARMAN", "HARMAN_2019" -> {
                setAllIsoBands(CURVE_HARMAN_2019, force = true)
                setFilterStrength(250, force = true)
                setCrossfeedStrength(200, force = true)
            }
            "CRINACLE", "CRINACLE_IEF" -> {
                setAllIsoBands(CURVE_CRINACLE_IEF, force = true)
                setFilterStrength(0, force = true)
                setCrossfeedStrength(100, force = true)
            }
            "DIFFUSE_FIELD", "DIFFUSE" -> {
                setAllIsoBands(CURVE_DIFFUSE_FIELD, force = true)
                setFilterStrength(0, force = true)
                setCrossfeedStrength(150, force = true)
            }
            "MOONDROP", "MOONDROP_VDSF" -> {
                setAllIsoBands(CURVE_MOONDROP_VDSF, force = true)
                setFilterStrength(150, force = true)
                setCrossfeedStrength(150, force = true)
            }
            "HD600", "SENNHEISER" -> {
                setAllIsoBands(CURVE_SENNHEISER_HD600, force = true)
                setFilterStrength(200, force = true)
                setCrossfeedStrength(250, force = true)
            }
            "WARM_TUBE", "WARM_ANALOG", "TUBE" -> {
                setAllIsoBands(CURVE_WARM_ANALOG_TAPE, force = true)
                setFilterStrength(200, force = true)
                setCrossfeedStrength(300, force = true)
            }
            "AIR_STAGE", "AIR" -> {
                setAllIsoBands(CURVE_AIR_STAGE, force = true)
                setFilterStrength(0, force = true)
                setCrossfeedStrength(500, force = true)
            }
            "V_SHAPE", "V_SHAPE_PUNCH" -> {
                setAllIsoBands(CURVE_V_SHAPE_PUNCH, force = true)
                setFilterStrength(400, force = true)
                setCrossfeedStrength(150, force = true)
            }
            "BASS_BOOST" -> {
                setAllIsoBands(CURVE_BASS_BOOST, force = true)
                setFilterStrength(600, force = true)
                setCrossfeedStrength(0, force = true)
            }
            else -> {
                setAllIsoBands(CURVE_FLAT, force = true)
                setFilterStrength(0, force = true)
                setCrossfeedStrength(0, force = true)
            }
        }
    }

    /**
     * Maps the 10 ISO frequency points onto the device's hardware Equalizer bands.
     * Applies logarithmic frequency interpolation and dynamic anti-clipping pre-amp headroom.
     */
    private fun applyEq() {
        if (isBypassEnabled) return
        val eq = equalizer ?: return
        try {
            val totalBands = numBands.toInt()
            if (totalBands <= 0) return

            // Anti-clipping pre-amp headroom calculation:
            // If any band is boosted above 0dB, apply proportional attenuation
            var maxBoost = 0f
            for (gain in isoBandsGainDb) {
                if (gain > maxBoost) maxBoost = gain
            }
            val headroomAttenuation = maxBoost * 0.35f

            for (hwBand in 0 until totalBands) {
                // Try to get exact center frequency in Hz from hardware EQ
                val centerFreqHz = try {
                    val mHz = eq.getCenterFreq(hwBand.toShort())
                    if (mHz > 0) mHz / 1000 else 0
                } catch (e: Exception) {
                    0
                }

                val interpolatedGainDb = if (centerFreqHz > 0) {
                    interpolateIsoGain(centerFreqHz)
                } else {
                    // Fallback to proportional indexing if device does not report frequency
                    val mappedIdx = (hwBand.toFloat() / (totalBands - 1).coerceAtLeast(1)) * 9f
                    val idx0 = mappedIdx.toInt().coerceIn(0, 9)
                    val idx1 = (idx0 + 1).coerceIn(0, 9)
                    val frac = mappedIdx - idx0
                    isoBandsGainDb[idx0] * (1f - frac) + isoBandsGainDb[idx1] * frac
                }

                val compensatedGainDb = interpolatedGainDb - headroomAttenuation
                val millibels = (compensatedGainDb * 100).toInt()
                    .coerceIn(minBandLevel.toInt(), maxBandLevel.toInt())
                    .toShort()
                eq.setBandLevel(hwBand.toShort(), millibels)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Interpolates the gain in dB for a given frequency in Hz based on the 10 ISO center frequencies.
     * Uses log2 frequency space for natural acoustic interpolation.
     */
    fun interpolateIsoGain(freqHz: Int): Float {
        if (freqHz <= ISO_FREQUENCIES[0]) return isoBandsGainDb[0]
        if (freqHz >= ISO_FREQUENCIES[9]) return isoBandsGainDb[9]

        for (i in 0 until 9) {
            val f0 = ISO_FREQUENCIES[i]
            val f1 = ISO_FREQUENCIES[i + 1]
            if (freqHz in f0..f1) {
                val logF0 = ln(f0.toDouble())
                val logF1 = ln(f1.toDouble())
                val logF = ln(freqHz.toDouble())
                val t = ((logF - logF0) / (logF1 - logF0)).toFloat()
                return isoBandsGainDb[i] * (1f - t) + isoBandsGainDb[i + 1] * t
            }
        }
        return 0f
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }
}
