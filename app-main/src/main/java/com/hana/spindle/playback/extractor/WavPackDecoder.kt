package com.hana.spindle.playback.extractor

import kotlin.math.pow
import kotlin.math.roundToInt

/** Thrown for streams this decoder cannot or will not decode (never for valid supported input). */
class WavPackException(message: String) : Exception(message)

/** Parsed 32-byte WavPack block header. */
data class WavPackBlockHeader(
    val blockSize: Int,          // bytes after the first 8 (ckSize)
    val version: Int,
    val totalSamples: Long,      // -1 when unknown
    val blockIndex: Long,
    val blockSamples: Int,
    val flags: Int,
    val crc: Int
) {
    val bytesPerSample: Int get() = (flags and 3) + 1
    val isMono: Boolean get() = (flags and MONO_FLAG) != 0
    val isHybrid: Boolean get() = (flags and HYBRID_FLAG) != 0
    val isFloat: Boolean get() = (flags and FLOAT_DATA) != 0
    val isInt32: Boolean get() = (flags and INT32_DATA) != 0
    val isDsd: Boolean get() = (flags and DSD_FLAG) != 0
    val isInitial: Boolean get() = (flags and INITIAL_BLOCK) != 0
    val isFinal: Boolean get() = (flags and FINAL_BLOCK) != 0
    val shift: Int get() = (flags shr 13) and 0x1f
    val sampleRateIndex: Int get() = (flags shr 23) and 0xf
    /** Channels carried in this block's bitstream (1 for mono / false-stereo, else 2). */
    val codedChannels: Int get() = if ((flags and (MONO_FLAG or FALSE_STEREO)) != 0) 1 else 2
    val totalBlockBytes: Int get() = blockSize + 8

    companion object {
        const val HEADER_SIZE = 32
        const val MONO_FLAG = 0x4
        const val HYBRID_FLAG = 0x8
        const val JOINT_STEREO = 0x10
        const val FLOAT_DATA = 0x80
        const val INT32_DATA = 0x100
        const val INITIAL_BLOCK = 0x800
        const val FINAL_BLOCK = 0x1000
        const val FALSE_STEREO = 0x40000000
        const val DSD_FLAG = 0x80000000.toInt()
        val SAMPLE_RATES = intArrayOf(6000, 8000, 9600, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 64000, 88200, 96000, 192000)

        fun isSignature(b: ByteArray, o: Int) =
            b[o] == 'w'.code.toByte() && b[o + 1] == 'v'.code.toByte() && b[o + 2] == 'p'.code.toByte() && b[o + 3] == 'k'.code.toByte()

        /** Parses a header at [o]; null if the signature or basic sanity checks fail. */
        fun parse(b: ByteArray, o: Int = 0): WavPackBlockHeader? {
            if (b.size - o < HEADER_SIZE || !isSignature(b, o)) return null
            fun u16(p: Int) = (b[o + p].toInt() and 0xFF) or ((b[o + p + 1].toInt() and 0xFF) shl 8)
            fun u32(p: Int) = u16(p).toLong() or (u16(p + 2).toLong() shl 16)
            val ck = u32(4)
            val version = u16(8)
            if (ck < 24 || ck > 16 * 1024 * 1024 || version < 0x402 || version > 0x410) return null
            val totalLow = u32(12)
            val total = if (totalLow == 0xFFFFFFFFL) -1L else totalLow or ((b[o + 11].toLong() and 0xFF) shl 32)
            return WavPackBlockHeader(
                blockSize = ck.toInt(), version = version, totalSamples = total,
                blockIndex = u32(16) or ((b[o + 10].toLong() and 0xFF) shl 32),
                blockSamples = u32(20).toInt(), flags = u32(24).toInt(), crc = u32(28).toInt()
            )
        }
    }
}

/** One decoded block: interleaved signed samples (already shifted to full scale of [bitsPerSample]). */
class WavPackDecodedBlock(val samples: IntArray, val frames: Int, val channels: Int, val bitsPerSample: Int, val sampleRate: Int)

/**
 * Pure-Kotlin decoder for lossless integer WavPack (version 4.x) mono and stereo blocks, 16 and 24-bit.
 * Hybrid/lossy, float, 32-bit-integer, DSD and >2-channel streams are rejected with [WavPackException].
 * CRC is not verified here (callers/tests compare decoded PCM).
 */
object WavPackDecoder {
    private const val MAX_TERM = 8
    private const val LIMIT_ONES = 16

    private val EXP2_TABLE = IntArray(256) { i -> (2.0.pow(i / 256.0) * 256.0).roundToInt() - 256 }

    private fun exp2s(log: Int): Int {
        if (log < 0) return -exp2s(-log)
        val value = EXP2_TABLE[log and 0xFF] or 0x100
        val l = log shr 8
        return if (l <= 9) value shr (9 - l) else value shl (l - 9)
    }

    private fun restoreWeight(w: Int): Int {
        var r = w shl 3
        if (r > 0) r += (r + 64) shr 7
        return r
    }

    private fun applyWeight(weight: Int, sample: Int): Int =
        if (sample != sample.toShort().toInt()) {
            (((((sample and 0xFFFF) * weight) shr 9) + (((sample and 0xFFFF.inv()) shr 9) * weight) + 1) shr 1)
        } else {
            (weight * sample + 512) shr 10
        }

    private class Pass(val term: Int, val delta: Int) {
        var weightA = 0; var weightB = 0
        val samplesA = IntArray(MAX_TERM); val samplesB = IntArray(MAX_TERM)
    }

    private class Bits(private val d: ByteArray, start: Int, private val end: Int) {
        private var pos = start
        private var sr = 0
        private var bc = 0
        fun bit(): Int {
            if (bc == 0) {
                if (pos >= end) throw WavPackException("Bitstream ended early")
                sr = d[pos++].toInt() and 0xFF
                bc = 8
            }
            val v = sr and 1
            sr = sr ushr 1
            bc--
            return v
        }
        fun bits(n: Int): Int { var v = 0; for (i in 0 until n) v = v or (bit() shl i); return v }
        fun code(maxcode: Int): Int {
            if (maxcode < 2) return if (maxcode != 0) bit() else 0
            val bitcount = 32 - Integer.numberOfLeadingZeros(maxcode)
            val extras = (1 shl bitcount) - maxcode - 1
            var c = bits(bitcount - 1)
            if (c >= extras) { c = (c shl 1) - extras; if (bit() != 0) c++ }
            return c
        }
    }

    private class Entropy(mono: Boolean) {
        val median = Array(2) { IntArray(3) }
        var zerosAcc = 0
        var holdingZero = false
        var holdingOne = false
        val stereo = !mono
    }

    private fun getWord(bs: Bits, e: Entropy, chan: Int): Int {
        val c = e.median[chan]
        if ((e.median[0][0] and 1.inv()) == 0 && !e.holdingZero && !e.holdingOne && (e.median[1][0] and 1.inv()) == 0) {
            if (e.zerosAcc != 0) {
                if (--e.zerosAcc != 0) return 0
            } else {
                var cbits = 0
                while (cbits < 33 && bs.bit() != 0) cbits++
                if (cbits == 33) throw WavPackException("Bad zero run")
                if (cbits < 2) e.zerosAcc = cbits
                else {
                    var mask = 1; e.zerosAcc = 0
                    while (--cbits != 0) { if (bs.bit() != 0) e.zerosAcc = e.zerosAcc or mask; mask = mask shl 1 }
                    e.zerosAcc = e.zerosAcc or mask
                }
                if (e.zerosAcc != 0) {
                    e.median[0].fill(0); e.median[1].fill(0)
                    return 0
                }
            }
        }

        var ones: Int
        val forcedZero = e.holdingZero
        if (forcedZero) {
            ones = 0; e.holdingZero = false
        } else {
            ones = 0
            while (ones < LIMIT_ONES + 1 && bs.bit() != 0) ones++
            if (ones == LIMIT_ONES + 1) throw WavPackException("Bad ones run")
            if (ones == LIMIT_ONES) {
                var cbits = 0
                while (cbits < 33 && bs.bit() != 0) cbits++
                if (cbits == 33) throw WavPackException("Bad ones count")
                if (cbits < 2) ones = cbits
                else {
                    var mask = 1; ones = 0
                    while (--cbits != 0) { if (bs.bit() != 0) ones = ones or mask; mask = mask shl 1 }
                    ones = ones or mask
                }
                ones += LIMIT_ONES
            }
        }

        if (e.holdingOne) {
            e.holdingOne = (ones and 1) != 0
            ones = (ones shr 1) + 1
        } else {
            e.holdingOne = (ones and 1) != 0
            ones = ones shr 1
        }
        // A zero that was held over (no bits read) completes its pair, so none is held for the next word.
        e.holdingZero = !forcedZero && !e.holdingOne

        fun med(i: Int) = (c[i] ushr 4) + 1
        fun inc(i: Int, div: Int) { c[i] += ((c[i] + div) / div) * 5 }
        fun dec(i: Int, div: Int) { c[i] -= ((c[i] + (div - 2)) / div) * 2 }

        var low: Int
        val high: Int
        if (ones == 0) {
            low = 0; high = med(0) - 1; dec(0, 128)
        } else {
            low = med(0); inc(0, 128)
            if (ones == 1) {
                high = low + med(1) - 1; dec(1, 64)
            } else {
                low += med(1); inc(1, 64)
                if (ones == 2) {
                    high = low + med(2) - 1; dec(2, 32)
                } else {
                    low += (ones - 2) * med(2)
                    high = low + med(2) - 1; inc(2, 32)
                }
            }
        }
        val lo = low and 0x7fffffff
        val hi = high and 0x7fffffff
        val mid = bs.code(hi - lo) + lo
        return if (bs.bit() != 0) mid.inv() else mid
    }

    /** Decodes one 1- or 2-channel block (header + metadata + bitstream) located at [offset]. */
    fun decodeBlock(data: ByteArray, offset: Int, header: WavPackBlockHeader, sampleRateOverride: Int = 0): WavPackDecodedBlock {
        if (header.isHybrid) throw WavPackException("Hybrid (lossy) WavPack is not supported")
        if (header.isFloat) throw WavPackException("Float WavPack is not supported")
        if (header.isDsd) throw WavPackException("DSD WavPack is not supported")
        if (!(header.isInitial && header.isFinal)) throw WavPackException("Multichannel WavPack is not supported")
        var bytes = header.bytesPerSample
        if (!header.isInt32 && bytes != 2 && bytes != 3) throw WavPackException("Only 16 and 24-bit WavPack is supported")
        val coded = header.codedChannels
        val frames = header.blockSamples
        val end = offset + header.totalBlockBytes
        if (end > data.size) throw WavPackException("Truncated block")

        var passes = emptyArray<Pass>()
        var entropy: Entropy? = null
        var bsStart = -1; var bsEnd = -1
        var rate = sampleRateOverride
        var weightsData: Pair<Int, Int>? = null
        var samplesData: Pair<Int, Int>? = null
        var int32Info: IntArray? = null

        var p = offset + WavPackBlockHeader.HEADER_SIZE
        while (p + 2 <= end) {
            val idByte = data[p].toInt() and 0xFF
            var words = data[p + 1].toInt() and 0xFF
            var hdr = 2
            if ((idByte and 0x80) != 0) {
                words = words or ((data[p + 2].toInt() and 0xFF) shl 8) or ((data[p + 3].toInt() and 0xFF) shl 16)
                hdr = 4
            }
            var len = words * 2
            if ((idByte and 0x40) != 0) len -= 1
            val s = p + hdr
            if (s + len > end || len < 0) throw WavPackException("Corrupt metadata")
            when (idByte and 0x3f) {
                0x2 -> { // decorrelation terms (stored last-pass-first)
                    if (len > 16) throw WavPackException("Too many decorrelation terms")
                    passes = Array(len) { i ->
                        val b = data[s + (len - 1 - i)].toInt() and 0xFF
                        val term = (b and 0x1f) - 5
                        if (term == 0 || term < -3 || (term > MAX_TERM && term != 17 && term != 18)) throw WavPackException("Bad term")
                        Pass(term, (b shr 5) and 7)
                    }
                }
                0x9 -> if (len >= 4) int32Info = IntArray(4) { data[s + it].toInt() and 0xFF }
                0x3 -> weightsData = s to len
                0x4 -> samplesData = s to len
                0x5 -> {
                    if (len != (if (coded == 1) 6 else 12)) throw WavPackException("Bad entropy vars")
                    val e = Entropy(coded == 1)
                    for (ch in 0 until coded) for (k in 0 until 3) {
                        val q = s + ch * 6 + k * 2
                        e.median[ch][k] = exp2s(((data[q].toInt() and 0xFF) or (data[q + 1].toInt() shl 8)).toShort().toInt())
                    }
                    entropy = e
                }
                0xa -> { bsStart = s; bsEnd = s + len }
                0x27 -> if (len >= 3) rate = (data[s].toInt() and 0xFF) or ((data[s + 1].toInt() and 0xFF) shl 8) or ((data[s + 2].toInt() and 0xFF) shl 16)
            }
            p = s + len + ((len and 1))
        }
        if (bsStart < 0) throw WavPackException("No audio bitstream")
        val ent = entropy ?: throw WavPackException("Missing entropy variables")

        // Initial weights (reverse order, mirrors the encoder)
        weightsData?.let { (s, len) ->
            var termcnt = if (coded == 1) len else len / 2
            if (termcnt > passes.size) throw WavPackException("Too many weights")
            var i = passes.size
            var q = s
            while (--i >= 0 && termcnt-- > 0) {
                passes[i].weightA = restoreWeight(data[q++].toInt())
                if (coded == 2) passes[i].weightB = restoreWeight(data[q++].toInt())
            }
        }
        // Initial history samples (reverse order)
        samplesData?.let { (s, len) ->
            var q = s; val endq = s + len
            fun s16(): Int { val v = ((data[q].toInt() and 0xFF) or (data[q + 1].toInt() shl 8)).toShort().toInt(); q += 2; return exp2s(v) }
            var i = passes.size
            while (--i >= 0 && q < endq) {
                val d = passes[i]
                if (d.term > MAX_TERM) {
                    d.samplesA[0] = s16(); d.samplesA[1] = s16()
                    if (coded == 2) { d.samplesB[0] = s16(); d.samplesB[1] = s16() }
                } else if (d.term < 0) {
                    d.samplesA[0] = s16(); d.samplesB[0] = s16()
                } else {
                    for (m in 0 until d.term) {
                        d.samplesA[m] = s16()
                        if (coded == 2) d.samplesB[m] = s16()
                    }
                }
            }
        }

        // Entropy-decode residuals
        val buf = IntArray(frames * coded)
        val bs = Bits(data, bsStart, bsEnd)
        if (coded == 1) {
            for (i in 0 until frames) buf[i] = getWord(bs, ent, 0)
        } else {
            var i = 0
            while (i < frames * 2) { buf[i] = getWord(bs, ent, 0); buf[i + 1] = getWord(bs, ent, 1); i += 2 }
        }

        // Decorrelation passes, applied in stored-reverse order (index 0 first)
        for (d in passes) if (coded == 1) monoPass(d, buf, frames) else stereoPass(d, buf, frames)

        if (coded == 2 && (header.flags and WavPackBlockHeader.JOINT_STEREO) != 0) {
            var i = 0
            while (i < frames * 2) { buf[i + 1] -= (buf[i] shr 1); buf[i] += buf[i + 1]; i += 2 }
        }
        if (header.isInt32) {
            // 24/16-bit audio carried in a 32-bit container with trailing zero bits (sent_bits, zeros, ones, dups).
            val info = int32Info ?: throw WavPackException("Missing 32-bit info")
            val zeros = info[1]
            if (info[0] != 0 || info[2] != 0 || info[3] != 0 || (zeros != 8 && zeros != 16)) {
                throw WavPackException("Unsupported 32-bit WavPack layout")
            }
            bytes = (32 - zeros) / 8
        }
        val shift = header.shift
        if (shift != 0) for (i in buf.indices) buf[i] = buf[i] shl shift

        val channels = if ((header.flags and WavPackBlockHeader.FALSE_STEREO) != 0) 2 else coded
        val out = if (channels == coded) buf else IntArray(frames * 2).also { for (i in 0 until frames) { it[2 * i] = buf[i]; it[2 * i + 1] = buf[i] } }
        val sr = if (rate > 0) rate else WavPackBlockHeader.SAMPLE_RATES.getOrNull(header.sampleRateIndex) ?: throw WavPackException("Unknown sample rate")
        return WavPackDecodedBlock(out, frames, channels, bytes * 8, sr)
    }

    private fun monoPass(d: Pass, buf: IntArray, frames: Int) {
        val a = d.samplesA
        when {
            d.term == 17 || d.term == 18 -> for (i in 0 until frames) {
                val sam = if (d.term == 17) 2 * a[0] - a[1] else (3 * a[0] - a[1]) shr 1
                a[1] = a[0]
                a[0] = applyWeight(d.weightA, sam) + buf[i]
                d.weightA = updateWeight(d.weightA, d.delta, sam, buf[i])
                buf[i] = a[0]
            }
            else -> {
                var m = 0; var k = d.term and (MAX_TERM - 1)
                for (i in 0 until frames) {
                    val sam = a[m]
                    a[k] = applyWeight(d.weightA, sam) + buf[i]
                    d.weightA = updateWeight(d.weightA, d.delta, sam, buf[i])
                    buf[i] = a[k]
                    m = (m + 1) and (MAX_TERM - 1); k = (k + 1) and (MAX_TERM - 1)
                }
            }
        }
    }

    private fun updateWeight(weight: Int, delta: Int, source: Int, result: Int): Int {
        if (source == 0 || result == 0) return weight
        val s = (source xor result) shr 31
        return (delta xor s) + (weight - s)
    }

    private fun updateWeightClip(weight: Int, delta: Int, source: Int, result: Int): Int {
        if (source == 0 || result == 0) return weight
        val s = (source xor result) shr 31
        var w = (weight xor s) + (delta - s)
        if (w > 1024) w = 1024
        return (w xor s) - s
    }

    private fun stereoPass(d: Pass, buf: IntArray, frames: Int) {
        val a = d.samplesA; val b = d.samplesB
        val n = frames * 2
        when {
            d.term == 17 || d.term == 18 -> {
                var i = 0
                while (i < n) {
                    val samA = if (d.term == 17) 2 * a[0] - a[1] else (3 * a[0] - a[1]) shr 1
                    a[1] = a[0]
                    a[0] = applyWeight(d.weightA, samA) + buf[i]
                    d.weightA = updateWeight(d.weightA, d.delta, samA, buf[i])
                    buf[i] = a[0]
                    val samB = if (d.term == 17) 2 * b[0] - b[1] else (3 * b[0] - b[1]) shr 1
                    b[1] = b[0]
                    b[0] = applyWeight(d.weightB, samB) + buf[i + 1]
                    d.weightB = updateWeight(d.weightB, d.delta, samB, buf[i + 1])
                    buf[i + 1] = b[0]
                    i += 2
                }
            }
            d.term > 0 -> {
                var m = 0; var k = d.term and (MAX_TERM - 1)
                var i = 0
                while (i < n) {
                    val samA = a[m]
                    a[k] = applyWeight(d.weightA, samA) + buf[i]
                    d.weightA = updateWeight(d.weightA, d.delta, samA, buf[i])
                    buf[i] = a[k]
                    val samB = b[m]
                    b[k] = applyWeight(d.weightB, samB) + buf[i + 1]
                    d.weightB = updateWeight(d.weightB, d.delta, samB, buf[i + 1])
                    buf[i + 1] = b[k]
                    m = (m + 1) and (MAX_TERM - 1); k = (k + 1) and (MAX_TERM - 1)
                    i += 2
                }
            }
            d.term == -1 -> {
                var i = 0
                while (i < n) {
                    val inL = buf[i]; val inR = buf[i + 1]
                    val left = applyWeight(d.weightA, a[0]) + inL
                    d.weightA = updateWeightClip(d.weightA, d.delta, a[0], inL)
                    val right = applyWeight(d.weightB, left) + inR
                    d.weightB = updateWeightClip(d.weightB, d.delta, left, inR)
                    a[0] = right
                    buf[i] = left; buf[i + 1] = right
                    i += 2
                }
            }
            d.term == -2 -> {
                var i = 0
                while (i < n) {
                    val inL = buf[i]; val inR = buf[i + 1]
                    val right = applyWeight(d.weightB, b[0]) + inR
                    d.weightB = updateWeightClip(d.weightB, d.delta, b[0], inR)
                    val left = applyWeight(d.weightA, right) + inL
                    d.weightA = updateWeightClip(d.weightA, d.delta, right, inL)
                    b[0] = left
                    buf[i] = left; buf[i + 1] = right
                    i += 2
                }
            }
            else -> { // -3
                var i = 0
                while (i < n) {
                    val inL = buf[i]; val inR = buf[i + 1]
                    val left = applyWeight(d.weightA, a[0]) + inL
                    d.weightA = updateWeightClip(d.weightA, d.delta, a[0], inL)
                    val right = applyWeight(d.weightB, b[0]) + inR
                    d.weightB = updateWeightClip(d.weightB, d.delta, b[0], inR)
                    a[0] = right; b[0] = left
                    buf[i] = left; buf[i + 1] = right
                    i += 2
                }
            }
        }
    }

    /** Little-endian signed PCM bytes for [block] at its native 16 or 24-bit depth. */
    fun toPcmBytes(block: WavPackDecodedBlock): ByteArray {
        val bytes = block.bitsPerSample / 8
        val out = ByteArray(block.samples.size * bytes)
        var o = 0
        for (v in block.samples) { for (k in 0 until bytes) out[o++] = (v shr (8 * k)).toByte() }
        return out
    }
}
