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
        var leftRadius: Float = 0f,        // Supply spool radius in pixels
        var rightRadius: Float = 0f,       // Take-up spool radius in pixels
        var leftAngularSpeed: Float = 0f,  // Relative rotational speed for left reel
        var rightAngularSpeed: Float = 0f  // Relative rotational speed for right reel
    )

    data class ReelInertia(
        var leftInertia: Float = 0.6f,        // Normalized rotational inertia of left reel (0.25 bare hub .. 1.0 full spool)
        var rightInertia: Float = 0.6f,       // Normalized rotational inertia of right reel (0.25 bare hub .. 1.0 full spool)
        var leftDecayTimeSec: Float = 0.26f,   // Deceleration coast-down damping time constant (seconds)
        var rightDecayTimeSec: Float = 0.26f   // Deceleration coast-down damping time constant (seconds)
    )

    /**
     * Calculates rotational inertia and coast-down damping time constants for both reels.
     * Full spools possess significantly greater physical mass and rotational momentum than bare hubs.
     * Reuses [out] instance if provided to guarantee zero heap allocations.
     */
    fun calculateInertia(progress: Float, out: ReelInertia = ReelInertia()): ReelInertia {
        val p = progress.coerceIn(0f, 1f)
        val leftI = (0.25f + 0.75f * (1f - p)).coerceIn(0.25f, 1.0f)
        val rightI = (0.25f + 0.75f * p).coerceIn(0.25f, 1.0f)

        // Baseline decay time: 0.12s + up to 0.24s inertia scaling (150ms bare hub .. 360ms full spool)
        val leftDecay = 0.12f + 0.24f * leftI
        val rightDecay = 0.12f + 0.24f * rightI

        out.leftInertia = leftI
        out.rightInertia = rightI
        out.leftDecayTimeSec = leftDecay
        out.rightDecayTimeSec = rightDecay
        return out
    }

    /**
     * Flywheel kinetic momentum tracker simulating physical motor spin-up,
     * frictional coast-down damping, and smooth fast-wind release transitions.
     * Zero-heap-allocation per frame.
     */
    class FlywheelInertiaTracker(
        var currentSpeed: Float = 0f,
        val accelerationTimeSec: Float = 0.16f,
        val baseDecayTimeSec: Float = 0.26f
    ) {
        /**
         * Step physics simulation forward by [deltaSec].
         *
         * @param targetSpeed Normalized target speed (0.0f = stop, 1.0f = play, >1.0f = fast wind).
         * @param deltaSec Elapsed time in seconds.
         * @param inertia Reel inertia factor (0.25 to 1.0).
         * @return Updated [currentSpeed].
         */
        fun step(targetSpeed: Float, deltaSec: Float, inertia: Float = 0.6f): Float {
            val clampedInertia = inertia.coerceIn(0.2f, 1.2f)
            val diff = targetSpeed - currentSpeed
            if (kotlin.math.abs(diff) < 0.0005f) {
                currentSpeed = targetSpeed
                return currentSpeed
            }

            val isAccelerating = kotlin.math.abs(targetSpeed) > kotlin.math.abs(currentSpeed)
            val tau = if (isAccelerating) {
                accelerationTimeSec * clampedInertia
            } else {
                baseDecayTimeSec * clampedInertia
            }

            // Exponential damping: alpha = 1 - exp(-dt / tau)
            val alpha = (1.0f - kotlin.math.exp(-deltaSec / tau)).coerceIn(0f, 1f)
            currentSpeed += alpha * diff

            // Snap to exact target when close enough to avoid endless micro-renderings
            if (kotlin.math.abs(targetSpeed - currentSpeed) < 0.01f && kotlin.math.abs(targetSpeed) < 0.01f) {
                currentSpeed = 0f
            }
            return currentSpeed
        }

        fun reset(speed: Float = 0f) {
            currentSpeed = speed
        }
    }

    /**
     * Calculates reel radii and angular velocities based on playback progress.
     * Reuses [out] instance if provided to guarantee zero heap allocations.
     *
     * @param progress Playback progress normalized between 0.0f (start) and 1.0f (end).
     * @param baseDimension The base reference radius dimension in pixels.
     * @param out Reusable SpoolState instance to write results into.
     */
    fun calculate(progress: Float, baseDimension: Float, out: SpoolState = SpoolState()): SpoolState {
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

        out.leftRadius = rLeft
        out.rightRadius = rRight
        out.leftAngularSpeed = speedLeft
        out.rightAngularSpeed = speedRight
        return out
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

