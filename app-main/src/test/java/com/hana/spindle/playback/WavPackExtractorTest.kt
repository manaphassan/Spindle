package com.hana.spindle.playback

import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import com.hana.spindle.playback.extractor.WavPackExtractor
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

@UnstableApi
class WavPackExtractorTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("wavpack/$name")!!.readBytes()

    private class Sink : TrackOutput {
        val pcm = ByteArrayOutputStream()
        val times = ArrayList<Long>()
        var format: Format? = null
        override fun format(format: Format) { this.format = format }
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            val b = ByteArray(length); val n = input.read(b, 0, length); if (n > 0) pcm.write(b, 0, n); return n
        }
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            val b = ByteArray(length); data.readBytes(b, 0, length); pcm.write(b)
        }
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) { times += timeUs }
    }

    private class Out(val sink: Sink = Sink()) : ExtractorOutput {
        var seekMap: SeekMap? = null
        override fun track(id: Int, type: Int): TrackOutput = sink
        override fun endTracks() {}
        override fun seekMap(seekMap: SeekMap) { this.seekMap = seekMap }
    }

    private fun input(data: ByteArray, pos: Long = 0): ExtractorInput {
        var p = pos.toInt()
        val reader = object : DataReader {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (p >= data.size) return -1 // C.RESULT_END_OF_INPUT
                val n = minOf(length, data.size - p)
                System.arraycopy(data, p, buffer, offset, n); p += n
                return n
            }
        }
        return DefaultExtractorInput(reader, pos, data.size.toLong())
    }

    /** Drives an extractor like ExoPlayer does, honouring RESULT_SEEK. */
    private fun drive(data: ByteArray, ex: Extractor, startAt: Long = 0, out: Out): Unit {
        var inp = input(data, startAt)
        val holder = PositionHolder()
        while (true) {
            when (ex.read(inp, holder)) {
                Extractor.RESULT_END_OF_INPUT -> return
                Extractor.RESULT_SEEK -> inp = input(data, holder.position)
            }
        }
    }

    @Test fun sniffsAndStreamsExactPcm() {
        val data = res("st16_l2.wv")
        val ex = WavPackExtractor()
        assertTrue(ex.sniff(input(data)))
        assertFalse(WavPackExtractor().sniff(input(ByteArray(64))))
        val out = Out()
        ex.init(out)
        drive(data, ex, 0, out)
        assertArrayEquals(res("st16_l2.raw"), out.sink.pcm.toByteArray())
        assertEquals(44100, out.sink.format!!.sampleRate); assertEquals(2, out.sink.format!!.channelCount)
        assertEquals(0L, out.sink.times.first())
        assertTrue(out.seekMap!!.isSeekable)
        assertEquals(300_000.0, out.seekMap!!.durationUs.toDouble(), 1500.0)
    }

    @Test fun seekLandsOnBlockBoundaryWithCorrectData() {
        val data = res("st16_l1.wv")
        val ex = WavPackExtractor()
        val out = Out(); ex.init(out)
        drive(data, ex, 0, out) // scan + play to learn the seek map
        val sm = out.seekMap!!
        val point = sm.getSeekPoints(200_000L).first
        assertTrue(point.timeUs <= 200_000L && point.timeUs > 0)
        val out2 = Out(); val ex2 = WavPackExtractor(); ex2.init(out2)
        // Scan first (as on open), then seek to the point and stream from there.
        val holder = PositionHolder()
        var inp = input(data, 0)
        while (true) { val r = ex2.read(inp, holder); if (r == Extractor.RESULT_SEEK) break }
        ex2.seek(point.position, point.timeUs)
        drive(data, ex2, point.position, out2)
        val expectedAll = res("st16_l1.raw")
        val skipBytes = Math.round(point.timeUs * 44100 / 1_000_000.0).toInt() * 4
        assertArrayEquals(expectedAll.copyOfRange(skipBytes, expectedAll.size), out2.sink.pcm.toByteArray())
        assertEquals(point.timeUs, out2.sink.times.first())
    }

    @Test fun unsupportedHybridFailsLoudly() {
        val data = res("st16_l0.wv").copyOf()
        data[24] = (data[24].toInt() or 0x08).toByte() // set HYBRID flag in the first header
        val ex = WavPackExtractor(); val out = Out(); ex.init(out)
        assertThrows(androidx.media3.common.ParserException::class.java) { drive(data, ex, 0, out) }
    }
}
