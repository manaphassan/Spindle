package com.hana.spindle.playback

import kotlin.math.*

/**
 * Mathematical Engine for Robert Bristow-Johnson (RBJ) Audio EQ Cookbook Biquad Filters.
 *
 * Computes exact cascaded second-order IIR transfer function magnitude responses across
 * a 120-point logarithmic frequency grid (20 Hz - 20,000 Hz).
 *
 * Implements:
 * - Peaking EQ biquad filter magnitude calculations.
 * - Tape head bump (+3.2dB @ 63Hz) analog warmth emulation.
 * - Zero allocations during curve computation for 60fps real-time UI rendering.
 */
object BiquadFilterCalculator {

    const val NUM_POINTS = 120
    const val MIN_FREQ_HZ = 20.0f
    const val MAX_FREQ_HZ = 20000.0f
    const val SAMPLE_RATE_HZ = 48000.0f

    // 120 precomputed logarithmic frequencies from 20 Hz to 20 kHz
    val LOG_FREQUENCIES: FloatArray = FloatArray(NUM_POINTS) { i ->
        val logMin = ln(MIN_FREQ_HZ)
        val logMax = ln(MAX_FREQ_HZ)
        val ratio = i.toFloat() / (NUM_POINTS - 1)
        exp(logMin + ratio * (logMax - logMin))
    }

    // Structure holding pre-normalized biquad coefficients for a single filter
    class BiquadCoeffs {
        var b0: Double = 1.0
        var b1: Double = 0.0
        var b2: Double = 0.0
        var a1: Double = 0.0
        var a2: Double = 0.0

        fun setPeaking(f0: Float, gainDb: Float, q: Float, sampleRate: Float = SAMPLE_RATE_HZ) {
            if (abs(gainDb) < 0.001f) {
                b0 = 1.0; b1 = 0.0; b2 = 0.0
                a1 = 0.0; a2 = 0.0
                return
            }
            val w0 = 2.0 * Math.PI * f0 / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val a = 10.0.pow(gainDb / 40.0)
            val alpha = sinW0 / (2.0 * q.toDouble().coerceIn(0.2, 10.0))

            val a0 = 1.0 + alpha / a
            b0 = (1.0 + alpha * a) / a0
            b1 = (-2.0 * cosW0) / a0
            b2 = (1.0 - alpha * a) / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha / a) / a0
        }

        fun magnitudeDbAt(freqHz: Float, sampleRate: Float = SAMPLE_RATE_HZ): Float {
            if (b1 == 0.0 && b2 == 0.0 && a1 == 0.0 && a2 == 0.0) return 0.0f
            val w = 2.0 * Math.PI * freqHz / sampleRate
            val cosW = cos(w)
            val sinW = sin(w)
            val cos2W = cos(2.0 * w)
            val sin2W = sin(2.0 * w)

            val numReal = b0 + b1 * cosW + b2 * cos2W
            val numImag = b1 * sinW + b2 * sin2W

            val denReal = 1.0 + a1 * cosW + a2 * cos2W
            val denImag = a1 * sinW + a2 * sin2W

            val numMagSq = numReal * numReal + numImag * numImag
            val denMagSq = denReal * denReal + denImag * denImag

            if (denMagSq < 1e-12) return 0.0f
            val magSq = numMagSq / denMagSq
            return (10.0 * log10(magSq.coerceAtLeast(1e-12))).toFloat()
        }
    }

    // Reusable cache of 10 biquad coefficient objects (1 per ISO band)
    private val filterBank = Array(10) { BiquadCoeffs() }

    /**
     * Calculates the cascaded magnitude response across the pre-allocated [outCurveDb] array.
     * Guaranteed zero heap allocations during call.
     */
    @Synchronized
    fun calculateCascadedResponse(
        isoGainsDb: FloatArray,
        isoQFactors: FloatArray,
        isTapeSatEnabled: Boolean = false,
        tapeSatDrive: Float = 0.0f,
        outCurveDb: FloatArray,
        centerFreqsHz: IntArray? = null
    ) {
        val numBands = min(10, min(isoGainsDb.size, isoQFactors.size))
        val isoFreqs = AudioFxController.ISO_FREQUENCIES

        // 1. Configure coefficients for all active peaking filters
        for (i in 0 until numBands) {
            val freq = if (centerFreqsHz != null && i < centerFreqsHz.size) {
                centerFreqsHz[i].toFloat()
            } else {
                isoFreqs[i].toFloat()
            }
            filterBank[i].setPeaking(
                f0 = freq,
                gainDb = isoGainsDb[i],
                q = isoQFactors[i]
            )
        }

        // 2. Compute summed cascaded response across all 120 logarithmic frequency points
        val limit = min(NUM_POINTS, outCurveDb.size)
        val log63 = ln(63.0)
        val bumpBandwidth = 2.0 * 0.45 * 0.45

        for (p in 0 until limit) {
            val freq = LOG_FREQUENCIES[p]
            var sumDb = 0.0f

            for (b in 0 until numBands) {
                sumDb += filterBank[b].magnitudeDbAt(freq)
            }

            // Analog Tape Head Bump (+3.2 dB centered at 63 Hz bell envelope)
            if (isTapeSatEnabled && tapeSatDrive > 0.01f) {
                val logDist = ln(freq.toDouble()) - log63
                val bump = (3.2 * tapeSatDrive.toDouble() * exp(-(logDist * logDist) / bumpBandwidth)).toFloat()
                sumDb += bump
            }

            outCurveDb[p] = sumDb.coerceIn(-24.0f, 24.0f)
        }
    }

    /**
     * Calculates the exact response in dB at an arbitrary probe frequency (for touch vernier HUD).
     */
    @Synchronized
    fun calculateResponseAt(
        freqHz: Float,
        isoGainsDb: FloatArray,
        isoQFactors: FloatArray,
        isTapeSatEnabled: Boolean = false,
        tapeSatDrive: Float = 0.0f,
        centerFreqsHz: IntArray? = null
    ): Float {
        val numBands = min(10, min(isoGainsDb.size, isoQFactors.size))
        val isoFreqs = AudioFxController.ISO_FREQUENCIES
        var sumDb = 0.0f

        for (b in 0 until numBands) {
            val freq = if (centerFreqsHz != null && b < centerFreqsHz.size) {
                centerFreqsHz[b].toFloat()
            } else {
                isoFreqs[b].toFloat()
            }
            val filter = BiquadCoeffs().apply {
                setPeaking(freq, isoGainsDb[b], isoQFactors[b])
            }
            sumDb += filter.magnitudeDbAt(freqHz)
        }

        if (isTapeSatEnabled && tapeSatDrive > 0.01f) {
            val logDist = ln(freqHz.toDouble()) - ln(63.0)
            val bump = (3.2 * tapeSatDrive.toDouble() * exp(-(logDist * logDist) / (2.0 * 0.45 * 0.45))).toFloat()
            sumDb += bump
        }

        return sumDb.coerceIn(-24.0f, 24.0f)
    }

    /**
     * Converts a horizontal position ratio (0f..1f) to frequency in Hz on a logarithmic scale.
     */
    fun ratioToFreq(ratio: Float): Float {
        val clamped = ratio.coerceIn(0.0f, 1.0f)
        val logMin = ln(MIN_FREQ_HZ)
        val logMax = ln(MAX_FREQ_HZ)
        return exp(logMin + clamped * (logMax - logMin))
    }

    /**
     * Converts frequency in Hz to horizontal position ratio (0f..1f) on a logarithmic scale.
     */
    fun freqToRatio(freqHz: Float): Float {
        val clamped = freqHz.coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ)
        val logMin = ln(MIN_FREQ_HZ)
        val logMax = ln(MAX_FREQ_HZ)
        return ((ln(clamped) - logMin) / (logMax - logMin)).coerceIn(0.0f, 1.0f)
    }
}
