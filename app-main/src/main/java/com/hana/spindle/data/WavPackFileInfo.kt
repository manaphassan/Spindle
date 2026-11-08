package com.hana.spindle.data

import com.hana.spindle.playback.extractor.WavPackBlockHeader
import com.hana.spindle.playback.extractor.WavPackDecoder
import com.hana.spindle.playback.extractor.WavPackException
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/** Stream facts and APEv2 tags for a .wv file, read without MediaMetadataRetriever (which can't parse WavPack). */
data class WavPackFileInfo(
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val durationMs: Long,
    val tags: Map<String, String>
) {
    companion object {
        private const val MAX_FIRST_BLOCK = 8 * 1024 * 1024

        fun read(file: File): WavPackFileInfo? = try {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(WavPackBlockHeader.HEADER_SIZE)
                raf.readFully(head)
                val h = WavPackBlockHeader.parse(head) ?: return null
                if (h.totalBlockBytes > MAX_FIRST_BLOCK) return null
                val block = ByteArray(h.totalBlockBytes)
                System.arraycopy(head, 0, block, 0, head.size)
                raf.readFully(block, head.size, block.size - head.size)
                val d = WavPackDecoder.decodeBlock(block, 0, h)
                val total = if (h.totalSamples > 0) h.totalSamples else return null
                WavPackFileInfo(d.sampleRate, d.bitsPerSample, d.channels, total * 1000L / d.sampleRate, readApeTags(raf))
            }
        } catch (e: WavPackException) {
            null
        } catch (e: Exception) {
            null
        }

        /** Minimal APEv2 reader (footer at end of file, optionally before a 128-byte ID3v1 tag). */
        fun readApeTags(raf: RandomAccessFile): Map<String, String> {
            val len = raf.length()
            for (back in longArrayOf(32, 160)) {
                if (len < back) continue
                val footer = ByteArray(32)
                raf.seek(len - back); raf.readFully(footer)
                if (String(footer, 0, 8, Charsets.ISO_8859_1) != "APETAGEX") continue
                fun u32(o: Int) = (footer[o].toLong() and 0xFF) or ((footer[o + 1].toLong() and 0xFF) shl 8) or
                    ((footer[o + 2].toLong() and 0xFF) shl 16) or ((footer[o + 3].toLong() and 0xFF) shl 24)
                val size = u32(12); val count = u32(16)
                if (size < 32 || size > 1_000_000 || count > 1000) return emptyMap()
                val body = ByteArray((size - 32).toInt())
                raf.seek(len - back + 32 - size); raf.readFully(body)
                return parseApeItems(body, count.toInt())
            }
            return emptyMap()
        }

        fun parseApeItems(body: ByteArray, count: Int): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            var p = 0
            repeat(count) {
                if (p + 8 > body.size) return out
                val vlen = (body[p].toInt() and 0xFF) or ((body[p + 1].toInt() and 0xFF) shl 8) or
                    ((body[p + 2].toInt() and 0xFF) shl 16) or ((body[p + 3].toInt() and 0xFF) shl 24)
                val flags = body[p + 4].toInt() and 0xFF
                var q = p + 8
                val keyStart = q
                while (q < body.size && body[q] != 0.toByte()) q++
                if (q >= body.size || vlen < 0 || q + 1 + vlen > body.size) return out
                val key = String(body, keyStart, q - keyStart, Charsets.ISO_8859_1).lowercase(Locale.ROOT)
                if ((flags and 6) == 0) { // text item (not binary / external)
                    out[key] = String(body, q + 1, vlen, Charsets.UTF_8).substringBefore('\u0000').trim()
                }
                p = q + 1 + vlen
            }
            return out
        }
    }
}
