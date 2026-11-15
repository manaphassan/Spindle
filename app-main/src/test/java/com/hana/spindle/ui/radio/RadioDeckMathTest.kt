package com.hana.spindle.ui.radio

import com.hana.spindle.playback.AnalogFmEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioDeckMathTest {

    private val min = AnalogFmEngine.MIN_FREQ
    private val max = AnalogFmEngine.MAX_FREQ
    private val step = AnalogFmEngine.STEP_MHZ

    @Test
    fun dialEndsMapToBandLimits() {
        assertEquals(RadioDeckMath.SCALE_X0, RadioDeckMath.freqToX(min, min, max), 0.001f)
        assertEquals(RadioDeckMath.SCALE_X1, RadioDeckMath.freqToX(max, min, max), 0.001f)
        assertEquals(min, RadioDeckMath.xToFreq(0f, min, max, step), 0.0001f)
        assertEquals(max, RadioDeckMath.xToFreq(400f, min, max, step), 0.0001f)
    }

    @Test
    fun dialRoundTripLandsOnCarrierFrequencies() {
        for (f in floatArrayOf(88.5f, 93.2f, 98.6f, 104.2f)) {
            val x = RadioDeckMath.freqToX(f, min, max)
            assertEquals(f, RadioDeckMath.xToFreq(x, min, max, step), 0.0001f)
        }
    }

    @Test
    fun snapClearsFloatNoiseAndClamps() {
        assertEquals(98.6f, RadioDeckMath.snap(98.60001f, min, max, step), 0f)
        assertEquals(98.7f, RadioDeckMath.snap(98.6f + step, min, max, step), 0f)
        assertEquals(min, RadioDeckMath.snap(80f, min, max, step), 0f)
        assertEquals(max, RadioDeckMath.snap(120f, min, max, step), 0f)
    }

    @Test
    fun levelSegmentsCoverTheMeter() {
        assertEquals(0, RadioDeckMath.levelSegments(0f))
        assertEquals(RadioDeckMath.SEGMENTS, RadioDeckMath.levelSegments(1f))
        assertEquals(RadioDeckMath.SEGMENTS, RadioDeckMath.levelSegments(3f))
        assertEquals(0, RadioDeckMath.levelSegments(-1f))
    }

    @Test
    fun tuneMeterCentresWhenOnCarrier() {
        assertTrue(RadioDeckMath.isCentered(0f))
        assertFalse(RadioDeckMath.isCentered(0.5f))
        assertEquals(0, RadioDeckMath.tuneSegment(-1f))
        assertEquals(RadioDeckMath.SEGMENTS - 1, RadioDeckMath.tuneSegment(1f))
        assertTrue(RadioDeckMath.tuneSegment(-0.4f) < 12)
        assertTrue(RadioDeckMath.tuneSegment(0.4f) > 13)
    }

    @Test
    fun rssiMapsToLinkLevel() {
        assertEquals(0f, RadioDeckMath.rssiToLevel(-110), 0f)
        assertEquals(0.5f, RadioDeckMath.rssiToLevel(-75), 0.001f)
        assertEquals(1f, RadioDeckMath.rssiToLevel(-40), 0f)
    }
}
