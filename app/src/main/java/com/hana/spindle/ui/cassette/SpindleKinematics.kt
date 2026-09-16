package com.hana.spindle.ui.cassette

import kotlin.math.sqrt

/**
 * Mathematical kinematic model for a differential dual-reel cassette tape.
 *
 * Real cassette tapes move at a constant linear velocity (v = 4.7625 cm/s).
 * As tape winds from the supply spool (left) to the take-up spool (right),
 * the tape pack radii and angular velocities vary according to the conservation of tape volume.
 */
class SpindleKinematics(
    val hubRadiusRatio: Float = 0.28f,  // Bare plastic hub radius relative to window half-height
    val maxTapeRadiusRatio: Float = 0.76f // Full tape spool radius relative to window half-height
) {

    data class SpoolState(
        val leftRadius: Float,        // Supply spool radius in pixels
        val rightRadius: Float,       // Take-up spool radius in pixels
        val leftAngularSpeed: Float,  // Relative rotational speed for left reel
        val rightAngularSpeed: Float  // Relative rotational speed for right reel
    )

    /**
     * Calculates reel radii and angular velocities based on playback progress.
     *
     * @param progress Playback progress normalized between 0.0f (start) and 1.0f (end).
     * @param baseDimension The base reference radius dimension in pixels.
     */
    fun calculate(progress: Float, baseDimension: Float): SpoolState {
        val p = progress.coerceIn(0f, 1f)
        val rHub = hubRadiusRatio * baseDimension
        val rMax = maxTapeRadiusRatio * baseDimension

        val rHubSq = rHub * rHub
        val deltaSq = (rMax * rMax) - rHubSq

        // Conservation of tape volume / area:
        // Area_left(p) = pi * (R_hub^2 + Delta^2 * (1 - p))
        // Area_right(p) = pi * (R_hub^2 + Delta^2 * p)
        val rLeft = sqrt(rHubSq + deltaSq * (1f - p))
        val rRight = sqrt(rHubSq + deltaSq * p)

        // Angular velocity omega = v / R.
        // Normalized so that when R = rHub, speed multiplier is 1.0f.
        val speedLeft = rHub / rLeft
        val speedRight = rHub / rRight

        return SpoolState(
            leftRadius = rLeft,
            rightRadius = rRight,
            leftAngularSpeed = speedLeft,
            rightAngularSpeed = speedRight
        )
    }
}
