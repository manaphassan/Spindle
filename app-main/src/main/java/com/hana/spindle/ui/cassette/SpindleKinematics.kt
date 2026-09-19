package com.hana.spindle.ui.cassette

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mathematical kinematic model for a differential dual-reel cassette tape.
 *
 * Real cassette tapes move at a constant linear velocity (v = 4.7625 cm/s).
 * As tape winds from the supply spool (left/top) to the take-up spool (right/bottom),
 * the tape pack radii and angular velocities vary according to the conservation of tape volume.
 *
 * Also models:
 * - Vintage motor wow & flutter micro-perturbations (4.5Hz capstan flutter + 0.55Hz reel wow).
 * - Physical reel hub wobble / eccentricity (runout).
 * - Exact geometric tangent points from spool outer perimeter to tape guide rollers.
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

    /**
     * Calculates subtle speed modulation due to motor wow and flutter.
     * Combines 4.5Hz capstan flutter with 0.55Hz eccentric reel wow.
     * Returns a speed multiplier centered around 1.0f (~ +/- 0.8% variation).
     */
    fun getWowFlutterMultiplier(timeSeconds: Float): Float {
        val flutter = 0.0075f * sin(2.0 * PI * 4.5 * timeSeconds).toFloat()
        val wow = 0.0035f * sin(2.0 * PI * 0.55 * timeSeconds).toFloat()
        return 1.0f + flutter + wow
    }

    /**
     * Calculates kinetic reel wobble eccentricity offset along X axis.
     */
    fun getWobbleOffsetX(angleDeg: Float, amplitudePx: Float = 0.85f): Float {
        val rad = Math.toRadians(angleDeg.toDouble())
        return (amplitudePx * sin(rad)).toFloat()
    }

    /**
     * Calculates kinetic reel wobble eccentricity offset along Y axis.
     */
    fun getWobbleOffsetY(angleDeg: Float, amplitudePx: Float = 0.85f): Float {
        val rad = Math.toRadians(angleDeg.toDouble())
        return (amplitudePx * cos(rad)).toFloat()
    }

    /**
     * Calculates geometric tangent contact points between a spool circle and a guide roller.
     * Writes [spoolTx, spoolTy, rollerTx, rollerTy] directly into the provided [outPoints] array
     * with zero heap allocations.
     *
     * @param cx Center X of spool
     * @param cy Center Y of spool
     * @param spoolR Current radius of spool tape pack
     * @param rx Center X of guide roller
     * @param ry Center Y of guide roller
     * @param rollerR Outer radius of guide roller
     * @param isTopSpool True if top supply spool, false if bottom take-up spool
     * @param outPoints 4-element FloatArray to store [spoolTx, spoolTy, rollerTx, rollerTy]
     */
    fun calculateSpoolToRollerTangent(
        cx: Float,
        cy: Float,
        spoolR: Float,
        rx: Float,
        ry: Float,
        rollerR: Float,
        isTopSpool: Boolean,
        outPoints: FloatArray
    ) {
        val dx = (rx - cx).toDouble()
        val dy = (ry - cy).toDouble()
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= 1.0) {
            outPoints[0] = cx
            outPoints[1] = cy
            outPoints[2] = rx
            outPoints[3] = ry
            return
        }

        val baseAngle = atan2(dy, dx)
        // Outer common tangent angle offset: cos(alpha) = (R_spool - R_roller) / dist
        val rDiff = ((spoolR - rollerR).toDouble() / dist).coerceIn(-0.999, 0.999)
        val alpha = acos(rDiff)

        // For top supply reel, tape feeds from the upper/outer tangent clockwise to roller
        // For bottom take-up reel, tape enters the lower/outer tangent counter-clockwise
        val tangentAngle = if (isTopSpool) {
            baseAngle - alpha
        } else {
            baseAngle + alpha
        }

        val cosT = cos(tangentAngle)
        val sinT = sin(tangentAngle)

        // Point on spool outer perimeter
        outPoints[0] = (cx + spoolR * cosT).toFloat()
        outPoints[1] = (cy + spoolR * sinT).toFloat()

        // Point on guide roller perimeter
        outPoints[2] = (rx + rollerR * cosT).toFloat()
        outPoints[3] = (ry + rollerR * sinT).toFloat()
    }
}

