package com.hana.spindle.ui.radio

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Everything [RadioDeckView] shows, built by [RadioFragment] from the FM and stream engines.
 * Equality is used to skip redundant redraws, so keep it a plain data class.
 */
data class RadioDeckModel(
    val isNet: Boolean = false,
    val frequency: Float = 88.5f,
    val stations: List<DialMark> = emptyList(),
    val presets: List<PresetKey> = emptyList(),
    val activePreset: Int = -1,
    val genre: String = "",
    val lcdPilotText: String = "STEREO",
    val lcdPilotLit: Boolean = false,
    val warning: String? = null,
    val lineTag: String = "",
    val line: String = "",
    val lineWarn: Boolean = false,
    val region: String = "",
    val hint: String = "",
    val onAir: Boolean = false,
    val lampLabel: String = "STEREO",
    val lampOn: Boolean = false,
    val forceMono: Boolean = false,
    val scanning: Boolean = false,
    /** FM selected but no headphone antenna: tuning controls are dead until it is plugged in. */
    val fmDead: Boolean = false,
    val meter: Meter = Meter()
) {
    data class DialMark(val frequency: Float, val callsign: String)

    data class PresetKey(
        val label: String,
        val number: String,
        val freqText: String,
        val enabled: Boolean = true
    )

    /**
     * Two meter rows. Row 1 is always a bar. Row 2 is either a centre-zero tuning
     * meter (FM) or a second bar (NET). Levels are 0..1.
     */
    data class Meter(
        val row1Name: String = "SIG",
        val row1Level: Float = 0f,
        val row2Name: String = "TUNE",
        val row2CenterZero: Boolean = true,
        val row2Level: Float = 0f,
        val tuneDeviation: Float = 0f,
        val tuneActive: Boolean = false,
        val showLockLamp: Boolean = true,
        val locked: Boolean = false,
        /** Draws a tick over row 1 at this level (e.g. the scan lock threshold); negative hides it. */
        val threshold: Float = -1f,
        val scaleLeft: String = "",
        val scaleCenter: String = "",
        val scaleRight: String = ""
    )
}

/** Pure mapping helpers shared by the painter, the touch handling and the unit tests. */
object RadioDeckMath {
    const val SEGMENTS = 26
    const val SCALE_X0 = 44f
    const val SCALE_X1 = 316f
    const val WHEEL_DETENT_DP = 8f
    private const val CENTER_WINDOW = 0.12f

    fun freqToX(freq: Float, min: Float, max: Float): Float =
        SCALE_X0 + ((freq - min) / (max - min)).coerceIn(0f, 1f) * (SCALE_X1 - SCALE_X0)

    fun xToFreq(x: Float, min: Float, max: Float, step: Float): Float {
        val raw = min + ((x - SCALE_X0) / (SCALE_X1 - SCALE_X0)).coerceIn(0f, 1f) * (max - min)
        return snap(raw, min, max, step)
    }

    /** Rounds to the nearest tuning step and clears float noise (98.60001 -> 98.6). */
    fun snap(freq: Float, min: Float, max: Float, step: Float): Float {
        val steps = ((freq - min) / step).roundToInt()
        return (Math.round((min + steps * step) * 100f) / 100f).coerceIn(min, max)
    }

    fun levelSegments(level: Float): Int = (level.coerceIn(0f, 1f) * SEGMENTS).roundToInt()

    fun isCentered(deviation: Float): Boolean = abs(deviation) < CENTER_WINDOW

    /** Segment lit by an off-centre tuning deviation in -1..1 (left = below the carrier). */
    fun tuneSegment(deviation: Float): Int =
        ((deviation.coerceIn(-1f, 1f) + 1f) / 2f * (SEGMENTS - 1)).roundToInt()

    /** Wi-Fi RSSI in dBm mapped to 0..1 (-100 dBm empty, -50 dBm full). */
    fun rssiToLevel(dbm: Int): Float = ((dbm + 100) / 50f).coerceIn(0f, 1f)
}
