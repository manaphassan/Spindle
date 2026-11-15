package com.hana.spindle.playback

import com.hana.spindle.playback.extractor.WavPackBlockHeader
import com.hana.spindle.playback.extractor.WavPackDecoder
import com.hana.spindle.playback.extractor.WavPackException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class WavPackDecoderTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("wavpack/$name")!!.readBytes()

    /** Decodes every block of a .wv resource and returns PCM bytes, sample rate and channels. */
    private fun decodeAll(name: String): Triple<ByteArray, Int, Int> {
        val data = res("$name.wv")
        val out = ByteArrayOutputStream()
        var p = 0; var rate = 0; var ch = 0
        while (p + WavPackBlockHeader.HEADER_SIZE <= data.size) {
            val h: WavPackBlockHeader = WavPackBlockHeader.parse(data, p) ?: break // trailing APE tag
            if (h.blockSamples > 0) {
                val b = WavPackDecoder.decodeBlock(data, p, h)
                out.write(WavPackDecoder.toPcmBytes(b)); rate = b.sampleRate; ch = b.channels
            }
            p += h.totalBlockBytes
        }
        return Triple(out.toByteArray(), rate, ch)
    }

    private fun check(name: String, rate: Int, ch: Int) {
        val (pcm, r, c) = decodeAll(name)
        val expected = res("$name.raw")
        assertEquals("$name rate", rate, r)
        assertEquals("$name channels", ch, c)
        assertEquals("$name length", expected.size, pcm.size)
        val bad = pcm.indices.firstOrNull { pcm[it] != expected[it] }
        assertNull("$name first mismatch at byte $bad", bad)
    }

    @Test fun stereo16AllCompressionLevels() {
        check("st16_l0", 44100, 2); check("st16_l1", 44100, 2); check("st16_l2", 44100, 2); check("st16_l3", 44100, 2)
    }
    @Test fun mono16() = check("mono16_l2", 22050, 1)
    @Test fun stereo16At96k() = check("st16_96k_l1", 96000, 2)
    @Test fun stereo24() { check("st24_l1", 44100, 2); check("st24_l3", 48000, 2) }

    @Test fun rejectsGarbageAndUnsupportedFeatures() {
        assertNull(WavPackBlockHeader.parse(ByteArray(40)))
        val data = res("st16_l0.wv")
        val h = WavPackBlockHeader.parse(data, 0)!!
        val hybrid = h.copy(flags = h.flags or WavPackBlockHeader.HYBRID_FLAG)
        assertThrows(WavPackException::class.java) { WavPackDecoder.decodeBlock(data, 0, hybrid) }
        val truncated = data.copyOf(100)
        assertThrows(WavPackException::class.java) { WavPackDecoder.decodeBlock(truncated, 0, h) }
    }
}

class WavPackFileInfoTest {
    private fun resFile(name: String): java.io.File {
        val f = java.io.File.createTempFile("wvtest", ".wv")
        f.deleteOnExit()
        f.writeBytes(javaClass.classLoader!!.getResourceAsStream("wavpack/$name")!!.readBytes())
        return f
    }

    @Test fun readsStreamFactsAndDuration() {
        val i = com.hana.spindle.data.WavPackFileInfo.read(resFile("st16_l1.wv"))!!
        assertEquals(44100, i.sampleRate); assertEquals(16, i.bitDepth); assertEquals(2, i.channels)
        assertEquals(300L, i.durationMs) // 0.3 s of audio
        val m = com.hana.spindle.data.WavPackFileInfo.read(resFile("mono16_l2.wv"))!!
        assertEquals(1, m.channels); assertEquals(22050, m.sampleRate)
        assertEquals(24, com.hana.spindle.data.WavPackFileInfo.read(resFile("st24_l1.wv"))!!.bitDepth)
    }

    @Test fun parsesApeV2Items() {
        fun item(k: String, v: String): ByteArray {
            val vb = v.toByteArray(); val kb = k.toByteArray()
            return byteArrayOf(vb.size.toByte(), 0, 0, 0, 0, 0, 0, 0) + kb + byteArrayOf(0) + vb
        }
        val body = item("Title", "Hello") + item("Artist", "Ann") + item("Cover Art (Front)", "x").also { it[4] = 2 }
        val tags = com.hana.spindle.data.WavPackFileInfo.parseApeItems(body, 3)
        assertEquals("Hello", tags["title"]); assertEquals("Ann", tags["artist"])
        assertNull(tags["cover art (front)"]) // binary/locator items are skipped
        assertTrue(com.hana.spindle.data.WavPackFileInfo.parseApeItems(byteArrayOf(1, 2), 5).isEmpty())
    }
}
