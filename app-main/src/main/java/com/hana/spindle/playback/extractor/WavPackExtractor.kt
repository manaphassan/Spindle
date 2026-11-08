package com.hana.spindle.playback.extractor

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import java.io.EOFException

/**
 * Media3 extractor for lossless integer WavPack (.wv): 16/24-bit, mono/stereo. Decoding is done in
 * pure Kotlin ([WavPackDecoder]) and emitted as raw little-endian PCM, so it is bit-exact and runs
 * through the normal audio sink (and the bit-perfect mixer path when enabled).
 *
 * On open it walks the block headers once (skipping payloads) to build a block-accurate seek table,
 * then rewinds and streams. Hybrid/lossy, float, DSD and multichannel WavPack fail with a clear
 * unsupported-feature error instead of playing noise.
 */
@UnstableApi
class WavPackExtractor : Extractor {

    private var output: ExtractorOutput? = null
    private var track: TrackOutput? = null

    private var state = STATE_SCAN
    private val header = ByteArray(WavPackBlockHeader.HEADER_SIZE)
    private var block = ByteArray(0)

    private val blockIndex = ArrayList<Long>()      // first sample of each block
    private val blockPosition = ArrayList<Long>()   // file offset of each block header
    private var sampleRate = 0
    private var formatSent = false
    private var firstPosition = 0L
    private var totalDurationUs = C.TIME_UNSET
    private var scanned = false
    private val pcmHolder = ParsableByteArray()

    companion object {
        private const val STATE_SCAN = 0
        private const val STATE_STREAM = 1
    }

    override fun sniff(input: ExtractorInput): Boolean {
        val buf = ByteArray(WavPackBlockHeader.HEADER_SIZE)
        return try {
            input.peekFully(buf, 0, buf.size)
            WavPackBlockHeader.parse(buf) != null
        } catch (_: EOFException) {
            false
        }
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        this.track = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (state == STATE_SCAN && !scanned) return scan(input, seekPosition)
        state = STATE_STREAM

        try {
            input.readFully(header, 0, header.size)
        } catch (_: EOFException) {
            return Extractor.RESULT_END_OF_INPUT
        }
        val h = WavPackBlockHeader.parse(header) ?: return Extractor.RESULT_END_OF_INPUT // trailing tag
        ensureCapacity(h.totalBlockBytes)
        System.arraycopy(header, 0, block, 0, header.size)
        try {
            input.readFully(block, header.size, h.totalBlockBytes - header.size)
        } catch (_: EOFException) {
            return Extractor.RESULT_END_OF_INPUT
        }
        if (h.blockSamples <= 0) return Extractor.RESULT_CONTINUE // metadata-only block

        val decoded = try {
            WavPackDecoder.decodeBlock(block, 0, h, sampleRate)
        } catch (e: WavPackException) {
            throw ParserException.createForUnsupportedContainerFeature("WavPack: ${e.message}")
        }
        if (!formatSent) sendFormat(decoded)
        val pcm = WavPackDecoder.toPcmBytes(decoded)
        pcmHolder.reset(pcm)
        track?.sampleData(pcmHolder, pcm.size)
        val timeUs = h.blockIndex * 1_000_000L / decoded.sampleRate
        track?.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, pcm.size, 0, null)
        return Extractor.RESULT_CONTINUE
    }

    private fun ensureCapacity(n: Int) { if (block.size < n) block = ByteArray(n) }

    private fun sendFormat(d: WavPackDecodedBlock) {
        sampleRate = d.sampleRate
        val enc = if (d.bitsPerSample == 24) C.ENCODING_PCM_24BIT else C.ENCODING_PCM_16BIT
        track?.format(
            Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setChannelCount(d.channels)
                .setSampleRate(d.sampleRate).setPcmEncoding(enc).build()
        )
        formatSent = true
    }

    /** One pass over the headers (skipping payloads), then rewind to the first block. */
    private fun scan(input: ExtractorInput, seekPosition: PositionHolder): Int {
        var totalSamples = -1L
        var lastEnd = 0L
        var first = true
        while (true) {
            val pos = input.position
            input.resetPeekPosition()
            try {
                input.peekFully(header, 0, header.size)
            } catch (_: EOFException) {
                break
            }
            val h = WavPackBlockHeader.parse(header) ?: break
            if (first) {
                // Decode the first audio block to learn the exact format (incl. custom sample rates).
                if (h.blockSamples > 0) {
                    ensureCapacity(h.totalBlockBytes)
                    input.resetPeekPosition()
                    try {
                        input.peekFully(block, 0, h.totalBlockBytes)
                    } catch (_: EOFException) {
                        throw ParserException.createForMalformedContainer("Truncated WavPack block", null)
                    }
                    val d = try {
                        WavPackDecoder.decodeBlock(block, 0, h)
                    } catch (e: WavPackException) {
                        throw ParserException.createForUnsupportedContainerFeature("WavPack: ${e.message}")
                    }
                    sampleRate = d.sampleRate
                    firstPosition = pos
                    totalSamples = h.totalSamples
                    first = false
                }
            }
            if (h.blockSamples > 0) {
                blockIndex += h.blockIndex
                blockPosition += pos
                lastEnd = h.blockIndex + h.blockSamples
            }
            try {
                input.skipFully(h.totalBlockBytes)
            } catch (_: EOFException) {
                break
            }
        }
        if (sampleRate <= 0 || blockIndex.isEmpty()) throw ParserException.createForMalformedContainer("No WavPack audio", null)
        val samples = if (totalSamples > 0) totalSamples else lastEnd
        totalDurationUs = samples * 1_000_000L / sampleRate
        scanned = true
        output?.seekMap(WavPackSeekMap())
        seekPosition.position = firstPosition
        state = STATE_STREAM
        return Extractor.RESULT_SEEK
    }

    override fun seek(position: Long, timeUs: Long) {
        if (scanned) state = STATE_STREAM
    }

    override fun release() {}

    private inner class WavPackSeekMap : SeekMap {
        override fun isSeekable(): Boolean = true
        override fun getDurationUs(): Long = totalDurationUs
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val target = (timeUs * sampleRate / 1_000_000L).coerceAtLeast(0L)
            var lo = 0; var hi = blockIndex.size - 1; var best = 0
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (blockIndex[mid] <= target) { best = mid; lo = mid + 1 } else hi = mid - 1
            }
            return SeekMap.SeekPoints(SeekPoint(blockIndex[best] * 1_000_000L / sampleRate, blockPosition[best]))
        }
    }
}
