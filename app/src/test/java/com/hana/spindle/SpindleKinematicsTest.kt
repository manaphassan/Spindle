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
}
