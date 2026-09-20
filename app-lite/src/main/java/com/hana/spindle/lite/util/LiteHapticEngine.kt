package com.hana.spindle.lite.util

import android.content.Context
import android.os.Vibrator

/**
 * Zero-allocation Mechanical Solenoid & Transport Relay Haptic Engine for Spindle Lite.
 * Engineered for Android 4.4 KitKat on the Sony Ericsson Xperia active.
 * Simulates the physical tactile weight of high-end analog tape deck solenoids,
 * pinch-roller latching, and cassette door damping springs.
 */
class LiteHapticEngine(context: Context) {

    private val vibrator: Vibrator? = try {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } catch (e: Exception) {
        null
    }

    val hasVibrator: Boolean
        get() = vibrator?.hasVibrator() == true

    var isHapticsEnabled: Boolean = true

    // Pre-allocated vibration timing patterns to prevent heap allocation during playback hot-loops
    private val engagePattern = longArrayOf(0, 22, 18, 28) // Dual pulse: solenoid slam + roller latch
    private val ejectPattern = longArrayOf(0, 35, 20, 15)  // Heavy mechanical spring release & door damper

    /**
     * Heavy mechanical solenoid engage.
     * Triggered on PLAY: simulates magnetic head block advancing and pinch roller clamping the capstan.
     */
    fun solenoidEngage() {
        if (!isHapticsEnabled) return
        try {
            vibrator?.vibrate(engagePattern, -1)
        } catch (ignored: Throwable) {}
    }

    /**
     * Sharp solenoid disengage.
     * Triggered on PAUSE / STOP: simulates the mechanical head release latch popping open.
     */
    fun solenoidDisengage() {
        if (!isHapticsEnabled) return
        try {
            vibrator?.vibrate(18L)
        } catch (ignored: Throwable) {}
    }

    /**
     * Quick tape advance / index click.
     * Triggered on NEXT / PREVIOUS: simulates quick transport relay engage.
     */
    fun tapeAdvance() {
        if (!isHapticsEnabled) return
        try {
            vibrator?.vibrate(14L)
        } catch (ignored: Throwable) {}
    }

    /**
     * Cassette door mechanical eject.
     * Triggered on EJECT: simulates heavy damped spring door release.
     */
    fun ejectDoor() {
        if (!isHapticsEnabled) return
        try {
            vibrator?.vibrate(ejectPattern, -1)
        } catch (ignored: Throwable) {}
    }

    /**
     * Micro-tick.
     * Triggered on knob turn, toggle switch flick, or alphabet scrubber index jump.
     */
    fun microTick() {
        if (!isHapticsEnabled) return
        try {
            vibrator?.vibrate(8L)
        } catch (ignored: Throwable) {}
    }
}
