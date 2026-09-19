package com.hana.spindle

import com.hana.spindle.ui.cassette.SpindleKinematics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpindleKinematicsTest {

    private val kinematics = SpindleKinematics(hubRadiusRatio = 0.28f, maxTapeRadiusRatio = 0.76f)
    private val baseDimension = 100f

    @Test
    fun testStartOfTrack_supplyReelIsFull_takeupReelIsBare() {
        val state = kinematics.calculate(0.0f, baseDimension)

        // At 0%, left spool should be full (76px) and right spool should be bare hub (28px)
        assertEquals(76f, state.leftRadius, 0.01f)
        assertEquals(28f, state.rightRadius, 0.01f)

        // Linear velocity is constant, so full spool rotates slower than bare hub
        assertTrue("Left spool should rotate slower than right spool", state.leftAngularSpeed < state.rightAngularSpeed)
        assertEquals(1.0f, state.rightAngularSpeed, 0.01f) // Bare hub normalized speed = 1.0
    }

    @Test
    fun testMidpoint_bothReelsHaveEqualVolumeAndSpeed() {
        val state = kinematics.calculate(0.5f, baseDimension)

        assertEquals(state.leftRadius, state.rightRadius, 0.01f)
        assertEquals(state.leftAngularSpeed, state.rightAngularSpeed, 0.01f)
    }

    @Test
    fun testEndOfTrack_supplyReelIsBare_takeupReelIsFull() {
        val state = kinematics.calculate(1.0f, baseDimension)

        assertEquals(28f, state.leftRadius, 0.01f)
        assertEquals(76f, state.rightRadius, 0.01f)

        // Left bare spool rotates faster than right full spool
        assertTrue("Left bare spool should rotate faster than right full spool", state.leftAngularSpeed > state.rightAngularSpeed)
        assertEquals(1.0f, state.leftAngularSpeed, 0.01f)
    }

    @Test
    fun testWowAndFlutterVelocityModulation() {
        for (t in 0..100) {
            val mult = kinematics.getWowFlutterMultiplier(t * 0.1f)
            assertTrue("Multiplier must be within +/- 2% of 1.0", mult in 0.98f..1.02f)
        }
    }

    @Test
    fun testWobbleOffsetsEccentricity() {
        val amp = 0.85f
        for (deg in 0 until 360 step 15) {
            val ox = kinematics.getWobbleOffsetX(deg.toFloat(), amp)
            val oy = kinematics.getWobbleOffsetY(deg.toFloat(), amp)
            assertTrue("Wobble X must be within amplitude", kotlin.math.abs(ox) <= amp + 0.001f)
            assertTrue("Wobble Y must be within amplitude", kotlin.math.abs(oy) <= amp + 0.001f)
        }
    }

    @Test
    fun testSpoolToRollerTangentCalculation() {
        val outPoints = FloatArray(4)
        kinematics.calculateSpoolToRollerTangent(
            cx = 100f, cy = 100f, spoolR = 40f,
            rx = 300f, ry = 150f, rollerR = 10f,
            isTopSpool = true, outPoints = outPoints
        )
        // Spool tangent point
        assertTrue("Spool tangent X must not be NaN", !outPoints[0].isNaN())
        assertTrue("Spool tangent Y must not be NaN", !outPoints[1].isNaN())
        // Roller tangent point
        assertTrue("Roller tangent X must not be NaN", !outPoints[2].isNaN())
        assertTrue("Roller tangent Y must not be NaN", !outPoints[3].isNaN())

        // Verify distance from spool center to spool tangent point equals spool radius
        val dSpool = kotlin.math.hypot(outPoints[0] - 100f, outPoints[1] - 100f)
        assertEquals(40f, dSpool, 0.01f)

        // Verify distance from roller center to roller tangent point equals roller radius
        val dRoller = kotlin.math.hypot(outPoints[2] - 300f, outPoints[3] - 150f)
        assertEquals(10f, dRoller, 0.01f)
    }
}
