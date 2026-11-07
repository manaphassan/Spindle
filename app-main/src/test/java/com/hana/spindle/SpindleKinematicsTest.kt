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

    @Test
    fun testReelInertiaCalculation() {
        // Start of tape: Left (supply) is full spool (max inertia), Right is bare hub (min inertia)
        val startInertia = kinematics.calculateInertia(0.0f)
        assertEquals(1.0f, startInertia.leftInertia, 0.01f)
        assertEquals(0.25f, startInertia.rightInertia, 0.01f)
        assertTrue(startInertia.leftDecayTimeSec > startInertia.rightDecayTimeSec)

        // Midpoint: Both reels have equal mass and inertia
        val midInertia = kinematics.calculateInertia(0.5f)
        assertEquals(midInertia.leftInertia, midInertia.rightInertia, 0.01f)
        assertEquals(midInertia.leftDecayTimeSec, midInertia.rightDecayTimeSec, 0.01f)

        // End of tape: Left is bare hub, Right is full spool
        val endInertia = kinematics.calculateInertia(1.0f)
        assertEquals(0.25f, endInertia.leftInertia, 0.01f)
        assertEquals(1.0f, endInertia.rightInertia, 0.01f)
        assertTrue(endInertia.rightDecayTimeSec > endInertia.leftDecayTimeSec)
    }

    @Test
    fun testFlywheelInertiaAccelerationAndDecay() {
        val tracker = SpindleKinematics.FlywheelInertiaTracker()
        assertEquals(0f, tracker.currentSpeed, 0.001f)

        // Spin up from 0 to 1.0 (PLAY) with 16ms frames
        val speeds = mutableListOf<Float>()
        for (i in 0 until 20) {
            val s = tracker.step(targetSpeed = 1.0f, deltaSec = 0.016f, inertia = 0.5f)
            speeds.add(s)
        }
        // Verify monotonic acceleration
        for (i in 1 until speeds.size) {
            assertTrue("Speed must increase during spin-up", speeds[i] >= speeds[i - 1])
        }
        assertTrue("Speed after 320ms should approach 1.0", tracker.currentSpeed > 0.85f)

        // Coast-down decay to 0 (STOP)
        val decaySpeeds = mutableListOf<Float>()
        for (i in 0 until 60) {
            val s = tracker.step(targetSpeed = 0.0f, deltaSec = 0.016f, inertia = 0.5f)
            decaySpeeds.add(s)
        }
        // Verify monotonic deceleration
        for (i in 1 until decaySpeeds.size) {
            assertTrue("Speed must decrease during coast-down", decaySpeeds[i] <= decaySpeeds[i - 1])
        }
        assertEquals("Speed after coast-down should be 0.0", 0.0f, tracker.currentSpeed, 0.001f)

        // Test inertia comparison: heavy spool vs bare hub coast-down
        val trackerHeavy = SpindleKinematics.FlywheelInertiaTracker(currentSpeed = 1.0f)
        val trackerLight = SpindleKinematics.FlywheelInertiaTracker(currentSpeed = 1.0f)

        // Step both for 150ms
        trackerHeavy.step(targetSpeed = 0f, deltaSec = 0.15f, inertia = 1.0f)
        trackerLight.step(targetSpeed = 0f, deltaSec = 0.15f, inertia = 0.25f)

        assertTrue(
            "Heavy spool with more momentum should maintain higher residual speed than light spool",
            trackerHeavy.currentSpeed > trackerLight.currentSpeed
        )
    }

    @Test
    fun testZeroAllocationKinematicsReusedInstances() {
        val reusableSpool = SpindleKinematics.SpoolState()
        val reusableInertia = SpindleKinematics.ReelInertia()

        val state1 = kinematics.calculate(0.2f, 100f, reusableSpool)
        assertTrue("Must return same instance passed in", state1 === reusableSpool)
        val leftR1 = state1.leftRadius
        val rightR1 = state1.rightRadius

        val state2 = kinematics.calculate(0.8f, 100f, reusableSpool)
        assertTrue("Must return same instance passed in", state2 === reusableSpool)
        assertTrue("Takeup radius at 80% must exceed takeup radius at 20%", state2.rightRadius > rightR1)
        assertTrue("Supply radius at 80% must be smaller than supply radius at 20%", state2.leftRadius < leftR1)

        val inertia1 = kinematics.calculateInertia(0.1f, reusableInertia)
        assertTrue("Must return same instance passed in", inertia1 === reusableInertia)
        val leftI1 = inertia1.leftInertia

        val inertia2 = kinematics.calculateInertia(0.9f, reusableInertia)
        assertTrue("Must return same instance passed in", inertia2 === reusableInertia)
        assertTrue("Supply inertia at 90% must be smaller than at 10%", inertia2.leftInertia < leftI1)
    }
}

