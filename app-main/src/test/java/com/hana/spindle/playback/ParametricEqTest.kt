package com.hana.spindle.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class ParametricEqTest {
    private val fs = 48000

    private fun settings(vararg b: PeqBand, preamp: Float = 0f) = PeqSettings(true, preamp, b.toList())

    private fun rmsDb(x: DoubleArray, skip: Int) = 10 * log10(x.drop(skip).sumOf { it * it } / (x.size - skip))

    private fun runFloat(s: PeqSettings, freq: Double, seconds: Double = 1.0): Pair<Double, Double> {
        val n = (fs * seconds).toInt()
        val input = DoubleArray(n) { 0.25 * sin(2 * PI * freq * it / fs) }
        val p = ParametricEqProcessor({ s }, { 1 })
        p.configure(AudioFormat(fs, 1, C.ENCODING_PCM_FLOAT))
        p.flush()
        val inBuf = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
        input.forEach { inBuf.putFloat(it.toFloat()) }
        inBuf.flip()
        p.queueInput(inBuf)
        val out = p.output
        val y = DoubleArray(n) { out.getFloat().toDouble() }
        return rmsDb(input, 4800) to rmsDb(y, 4800)
    }

    @Test fun peakBoostAtCenterMatchesGain() {
        val (i, o) = runFloat(settings(PeqBand(PeqType.PEAK, 1000f, 6f, 1f)), 1000.0)
        assertEquals(6.0, o - i, 0.15)
    }

    @Test fun peakIsTransparentFarFromCenter() {
        val (i, o) = runFloat(settings(PeqBand(PeqType.PEAK, 1000f, 6f, 4f)), 8000.0)
        assertEquals(0.0, o - i, 0.2)
    }

    @Test fun shelvesAndPassFiltersBehave() {
        assertEquals(5.0, runFloat(settings(PeqBand(PeqType.LOW_SHELF, 200f, 5f, 0.7f)), 40.0).let { it.second - it.first }, 0.4)
        assertEquals(-4.0, runFloat(settings(PeqBand(PeqType.HIGH_SHELF, 4000f, -4f, 0.7f)), 16000.0).let { it.second - it.first }, 0.4)
        assertTrue(runFloat(settings(PeqBand(PeqType.HIGH_PASS, 500f, 0f, 0.707f)), 50.0).let { it.second - it.first } < -30)
        assertTrue(runFloat(settings(PeqBand(PeqType.LOW_PASS, 500f, 0f, 0.707f)), 8000.0).let { it.second - it.first } < -30)
    }

    @Test fun preampScalesLevel() {
        val (i, o) = runFloat(settings(PeqBand(PeqType.PEAK, 1000f, 0f, 1f), preamp = -6f), 1000.0)
        assertEquals(-6.0, o - i, 0.05)
    }

    @Test fun analyticResponseMatchesCookbook() {
        val c = PeqMath.coefficients(PeqBand(PeqType.PEAK, 1000f, 6f, 1f), fs)
        assertEquals(6.0, PeqMath.magnitudeDb(c, 1000.0, fs), 0.01)
        assertEquals(0.0, PeqMath.magnitudeDb(c, 20.0, fs), 0.2)
    }

    @Test fun int16RoundTripClampsAndKeepsFrameCount() {
        val s = settings(PeqBand(PeqType.PEAK, 1000f, 12f, 1f))
        val p = ParametricEqProcessor({ s }, { 1 })
        p.configure(AudioFormat(fs, 2, C.ENCODING_PCM_16BIT)); p.flush()
        val n = 4800
        val buf = ByteBuffer.allocateDirect(n * 2 * 2).order(ByteOrder.nativeOrder())
        for (k in 0 until n) { val v = (30000 * sin(2 * PI * 1000 * k / fs)).toInt().toShort(); buf.putShort(v); buf.putShort(v) }
        buf.flip()
        p.queueInput(buf)
        assertEquals(n * 2 * 2, p.output.remaining())
    }

    @Test fun inactiveWhenFlatDisabledOrUnsupportedEncoding() {
        fun active(s: PeqSettings, enc: Int): Boolean {
            val p = ParametricEqProcessor({ s }, { 1 })
            p.configure(AudioFormat(fs, 2, enc)); return p.isActive
        }
        assertFalse(active(PeqSettings(true, 0f, listOf(PeqBand(gainDb = 0f))), C.ENCODING_PCM_16BIT))
        assertFalse(active(PeqSettings(false, 0f, listOf(PeqBand(gainDb = 5f))), C.ENCODING_PCM_16BIT))
        assertFalse(active(settings(PeqBand(gainDb = 5f)), C.ENCODING_PCM_24BIT))
        assertTrue(active(settings(PeqBand(gainDb = 5f)), C.ENCODING_PCM_FLOAT))
        assertTrue(active(settings(PeqBand(gainDb = 5f)), C.ENCODING_PCM_16BIT))
    }

    @Test fun parsesAutoEqText() {
        val txt = """
            Preamp: -6.2 dB
            Filter 1: ON LSC Fc 105 Hz Gain 6.9 dB Q 0.70
            Filter 2: ON PK Fc 2100 Hz Gain -3.3 dB Q 1.35
            Filter 3: OFF PK Fc 8000 Hz Gain 2.0 dB Q 2.00
            Filter 4: ON HSC Fc 10000 Hz Gain -2.5 dB
            junk line
        """.trimIndent()
        val s = AutoEqParametricParser.parse(txt)!!
        assertEquals(-6.2f, s.preampDb, 0.001f)
        assertEquals(4, s.bands.size)
        assertEquals(PeqType.LOW_SHELF, s.bands[0].type)
        assertEquals(105f, s.bands[0].freqHz, 0.001f)
        assertEquals(-3.3f, s.bands[1].gainDb, 0.001f)
        assertFalse(s.bands[2].enabled)
        assertEquals(0.707f, s.bands[3].q, 0.001f)
        assertNull(AutoEqParametricParser.parse("nothing useful"))
    }

    @Test fun jsonRoundTrip() {
        val s = PeqSettings(true, -3f, listOf(PeqBand(PeqType.HIGH_SHELF, 9000f, 2.5f, 0.8f, false)))
        assertEquals(s, ParametricEq.fromJson(ParametricEq.toJson(s)))
        assertNull(ParametricEq.fromJson("{bad"))
    }

    @Test fun autoPreampCoversLargestBoost() {
        assertEquals(-4f, settings(PeqBand(gainDb = 4f), PeqBand(gainDb = -6f)).autoPreampDb(), 0.001f)
        assertEquals(0f, settings(PeqBand(gainDb = -3f)).autoPreampDb(), 0.001f)
        assertTrue(sqrt(4.0) == 2.0)
    }
}

class ParametricEqTextRoundTripTest {
    @Test fun serialisedTextParsesBack() {
        val s = PeqSettings(true, -4.5f, listOf(
            PeqBand(PeqType.LOW_SHELF, 105f, 6.9f, 0.7f), PeqBand(PeqType.PEAK, 2100f, -3.3f, 1.35f, false),
            PeqBand(PeqType.HIGH_PASS, 30f, 0f, 0.71f)))
        val back = AutoEqParametricParser.parse(s.toAutoEqText())!!
        assertEquals(s.preampDb, back.preampDb, 0.05f)
        assertEquals(s.bands.map { it.type }, back.bands.map { it.type })
        assertEquals(s.bands.map { it.enabled }, back.bands.map { it.enabled })
        assertEquals(-3.3f, back.bands[1].gainDb, 0.05f)
    }
}
