package com.hana.spindle.playback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Tactile Haptic Synthesis Engine for mechanical skeuomorphic feedback.
 *
 * Translates vintage physical cassette deck acoustics and Zune Metro UI kinetic interactions
 * into sharp, calibrated LRA (Linear Resonant Actuator) tactile vibration waveforms:
 * 1. Solenoid Clack: Heavy mechanical engagement pulse (EFFECT_HEAVY_CLICK or 35ms burst).
 * 2. Stop/Release Click: Sharp spring release transient (EFFECT_CLICK or 18ms pulse).
 * 3. Carriage Eject: Double-stage mechanical latch pop & chassis thud.
 * 4. Switch Detent Snap: Crisp metallic toggle snap (EFFECT_CLICK or 12ms pulse).
 * 5. Metro Detent Tick: Ultra-short micro-tick for jump-lists & sliders (EFFECT_TICK or 8ms pulse).
 * 6. Tile Press: Tactile sprung relay catch for tiles and pills (EFFECT_CLICK or 15ms pulse).
 * 7. Rotary Ratchet: Stepped thumbwheel indexing click (EFFECT_TICK or 6ms pulse).
 *
 * Gracefully handles devices without vibration hardware (e.g. audiophile DAPs) or disabled haptics.
 */
class SpindleHaptics(private val context: Context) {

    companion object {
        private const val TAG = "SpindleHaptics"
        const val PREF_HAPTICS_ENABLED = "pref_tactile_haptics_enabled"

        // Vibration timing profiles in ms
        const val DURATION_SOLENOID_MS = 35L
        const val DURATION_RELEASE_MS = 18L
        const val DURATION_SWITCH_MS = 12L
        const val DURATION_METRO_TICK_MS = 8L
        const val DURATION_TILE_PRESS_MS = 15L
        const val DURATION_ROTARY_RATCHET_MS = 6L
    }

    private val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)

    var isEnabled: Boolean = prefs.getBoolean(PREF_HAPTICS_ENABLED, true)
        private set

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to obtain vibrator service", e)
            null
        }
    }

    val hasVibrator: Boolean
        get() = try {
            vibrator?.hasVibrator() == true
        } catch (_: Throwable) {
            false
        }

    fun setHapticsEnabled(enabled: Boolean) {
        isEnabled = enabled
        prefs.edit().putBoolean(PREF_HAPTICS_ENABLED, enabled).apply()
    }

    fun vibrateSolenoid() {
        if (!isEnabled || !hasVibrator) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(DURATION_SOLENOID_MS, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(DURATION_SOLENOID_MS)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "vibrateSolenoid failed", e)
        }
    }

    fun vibrateRelease() {
        if (!isEnabled || !hasVibrator) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(DURATION_RELEASE_MS, 200))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(DURATION_RELEASE_MS)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "vibrateRelease failed", e)
        }
    }

    fun vibrateSwitchSnap() {
        if (!isEnabled || !hasVibrator) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(DURATION_SWITCH_MS, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(DURATION_SWITCH_MS)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "vibrateSwitchSnap failed", e)
        }
    }

    fun vibrateMetroTick() {
        if (!isEnabled || !hasVibrator) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(DURATION_METRO_TICK_MS, 120))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(DURATION_METRO_TICK_MS)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "vibrateMetroTick failed", e)
        }
    }

    fun vibrateTilePress() {
        if (!isEnabled || !hasVibrator) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(DURATION_TILE_PRESS_MS, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(DURATION_TILE_PRESS_MS)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "vibrateTilePress failed", e)
        }
    }

    fun vibrateRotaryRatchet() {
        if (!isEnabled || !hasVibrator) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(DURATION_ROTARY_RATCHET_MS, 140))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(DURATION_ROTARY_RATCHET_MS)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "vibrateRotaryRatchet failed", e)
        }
    }

    fun vibrateCarriageEject() {
        if (!isEnabled || !hasVibrator) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Two-stage pop & thud: 10ms click, 15ms pause, 25ms thud
                val timings = longArrayOf(0, 10, 15, 25)
                val amplitudes = intArrayOf(0, 220, 0, 180)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "vibrateCarriageEject failed", e)
        }
    }
}
