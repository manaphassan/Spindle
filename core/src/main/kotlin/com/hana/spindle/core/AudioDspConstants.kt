package com.hana.spindle.core

/**
 * Shared Audiophile DSP Constants & Scientific Target EQ Curves.
 *
 * Provides a single, cross-module source of truth for ISO frequency standards,
 * AutoEq acoustic targets, and tape formulation curves.
 */
object AudioDspConstants {

    /** 10-Band ISO Center Frequencies in Hz */
    val ISO_FREQUENCIES = intArrayOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    /** 10-Band ISO Display Labels */
    val ISO_LABELS = arrayOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    // --- AutoEq Target Frequency Profiles (-12.0f to +12.0f dB) ---

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

    // Expanded Audiophile AutoEq Target Reference Curves
    /** Harman Over-Ear 2020 Target with acoustic sub-bass shelf and 3kHz ear-canal gain */
    val CURVE_HARMAN_OVER_EAR_2020 = floatArrayOf(6.0f, 5.5f, 3.5f, 1.0f, 0.0f, 0.5f, 3.0f, 3.5f, 1.0f, 0.5f)
    /** Crinacle IEF Neutral target with +6dB Harman-style sub-bass shelf */
    val CURVE_CRINACLE_IEF_BASS = floatArrayOf(6.0f, 5.0f, 2.5f, 0.0f, 0.0f, 0.5f, 2.5f, 3.0f, 0.0f, -1.0f)
    /** Etymotic ER4 Reference Studio Diffuse target with ear-canal acoustic compensation */
    val CURVE_ETYMOTIC_ER4 = floatArrayOf(-1.0f, -0.5f, 0.0f, 0.0f, 1.0f, 2.5f, 5.0f, 4.0f, 0.5f, -2.0f)
    /** Sony MDR-7506 Professional Studio Monitor benchmark curve */
    val CURVE_SONY_MDR7506 = floatArrayOf(2.5f, 3.0f, 1.5f, -0.5f, 0.0f, 1.0f, 2.0f, 4.0f, 3.5f, 1.0f)
    /** Sennheiser HD650 / HD6XX Mid-Centric Reference Audiophile curve */
    val CURVE_SENNHEISER_HD650 = floatArrayOf(4.0f, 2.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 2.0f, 2.5f)

    // --- Vintage Cassette Tape Formulation Profiles (EQ / Saturation) ---

    /** Warm Ferric lows, gentle tape treble roll-off */
    val CURVE_TYPE_I_NORMAL = floatArrayOf(2.5f, 3.0f, 2.0f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.8f, -3.0f)
    /** Reference 70µs CrO₂ studio benchmark */
    val CURVE_TYPE_II_CHROME = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    /** Dual-layer hybrid (Ferric base for deep punch + Chromium top for crisp 70µs highs) */
    val CURVE_TYPE_III_FERRICHROME = floatArrayOf(2.0f, 2.2f, 1.5f, 0.5f, 0.0f, 0.0f, 0.5f, 0.8f, 1.0f, 0.5f)
    /** Wide-bandwidth pure metal, extended transients */
    val CURVE_TYPE_IV_METAL = floatArrayOf(1.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.2f, 1.5f)

    // --- Noise Reduction Compander Emulation Curves ---

    /** ~10dB HF noise reduction de-emphasis */
    val CURVE_DOLBY_B = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.5f, -1.5f, -3.0f, -5.5f, -8.0f)
    /** ~20dB dual-stage compander curve */
    val CURVE_DOLBY_C = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, -1.0f, -2.0f, -4.0f, -6.5f, -9.5f, -12.5f)

    // --- Studio Collector Harmonic Tape Saturation DSP ---

    /**
     * Non-linear 1/3-octave magnetic flux head bump (+3.2dB @ 63Hz),
     * smooth mid-range warmth (+1.5dB @ 250Hz-500Hz),
     * and natural high-frequency hysteresis tape saturation compression roll-off (-1.5dB @ 8kHz, -2.8dB @ 16kHz).
     */
    val CURVE_TAPE_SATURATION_WARMTH = floatArrayOf(2.2f, 3.2f, 2.5f, 1.5f, 1.0f, 0.2f, -0.4f, -0.8f, -1.5f, -2.8f)

    /**
     * Converts tape drive gain (0 dB to +6 dB) to non-linear saturation factor alpha (0.5 to 2.0).
     */
    fun driveDbToAlpha(driveDb: Float): Float {
        val clamped = driveDb.coerceIn(0.0f, 6.0f)
        return Math.pow(10.0, (clamped / 20.0)).toFloat()
    }

    /**
     * Non-linear tape magnetic hysteresis transfer function:
     * y(x) = tanh(alpha * x) / tanh(alpha)
     * where alpha is the saturation drive level (0.5 to 2.0).
     * Compresses peaks smoothly with 3rd-harmonic warmth while preventing harsh hard clipping.
     */
    fun evaluateTapeSaturationTanh(sample: Float, driveAlpha: Float = 1.2f): Float {
        val alpha = driveAlpha.coerceIn(0.5f, 2.5f)
        val norm = kotlin.math.tanh(alpha.toDouble()).toFloat()
        if (norm <= 0.001f) return sample
        val saturated = kotlin.math.tanh((alpha * sample).toDouble()).toFloat()
        return saturated / norm
    }

    /**
     * Computes frequency gain array for analog tape saturation based on tape drive gain (0.0 to 6.0 dB).
     * Head bump at 63Hz and mid warmth scale with drive level while high-frequency compression increases.
     */
    fun computeTapeSaturationGains(driveDb: Float): FloatArray {
        val clampedDrive = driveDb.coerceIn(0f, 6.0f)
        val driveFactor = clampedDrive / 6.0f // 6.0 dB = 1.0f (full nominal tape saturation)
        val result = FloatArray(10)
        for (i in 0 until 10) {
            result[i] = CURVE_TAPE_SATURATION_WARMTH[i] * driveFactor
        }
        return result
    }

    /**
     * Studio-grade soft-knee peak limiter.
     * Prevents harsh digital clipping when positive ReplayGain pre-amps (+3dB to +12dB)
     * or EQ boosts push linear gain above unity (1.0 = 0 dBFS).
     * Linear up to -0.5 dBFS (threshold ~0.944), smoothly compressing peaks with a tanh knee above.
     */
    fun applySoftKneeLimiter(linearGain: Float): Float {
        if (linearGain <= 0.944f) return linearGain.coerceAtLeast(0.05f)
        val threshold = 0.944f
        val excess = linearGain - threshold
        val compressed = threshold + 0.20f * kotlin.math.tanh((excess / 0.25f).toDouble()).toFloat()
        return compressed.coerceIn(0.05f, 1.15f)
    }
}
