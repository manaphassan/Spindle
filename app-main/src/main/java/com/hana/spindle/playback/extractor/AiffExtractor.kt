package com.hana.spindle.playback.extractor

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
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
import kotlin.math.min

/**
 * Native, bit-perfect Audio Interchange File Format (AIFF / AIFC) Extractor for AndroidX Media3.
 *
 * Implements IFF chunk demuxing:
 * - FORM chunk (AIFF / AIFC magic header validation)
 * - COMM chunk (channels, sample rate from 80-bit IEEE 754 float, sample size: 16/24/32-bit)
 * - SSND chunk (raw PCM data extraction with sample-accurate seek map)
 */
@UnstableApi
class AiffExtractor : Extractor {

    private var extractorOutput: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null

    private var state = STATE_READ_HEADER
    private var channels: Short = 0
    private var numSampleFrames: Long = 0L
    private var sampleSize: Short = 0
    private var sampleRate: Int = 0
    private var bytesPerFrame: Int = 0
    private var durationUs: Long = 0L

    private var ssndDataStartPosition: Long = 0L
    private var ssndDataRemainingBytes: Long = 0L
    private var currentPositionUs: Long = 0L

    private val scratch = ParsableByteArray(16)

    companion object {
        private const val STATE_READ_HEADER = 0
        private const val STATE_READ_CHUNKS = 1
        private const val STATE_STREAM_DATA = 2

        private const val FOURCC_FORM = 0x464F524D // "FORM"
        private const val FOURCC_AIFF = 0x41494646 // "AIFF"
        private const val FOURCC_AIFC = 0x41494643 // "AIFC"
        private const val FOURCC_COMM = 0x434F4D4D // "COMM"
        private const val FOURCC_SSND = 0x53534E44 // "SSND"

        /**
         * Converts an 80-bit IEEE 754 Extended Precision Float byte array to an integer sample rate.
         */
        fun readIeeeExtendedFloat(bytes: ByteArray): Int {
            if (bytes.size < 10) return 44100
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val exponent = ((b0 and 0x7F) shl 8) or b1

            var significand = 0L
            for (i in 2..9) {
                significand = (significand shl 8) or (bytes[i].toLong() and 0xFFL)
            }

            if (exponent == 0 && significand == 0L) return 44100
            val expShift = exponent - 16383 - 63
            val uSignificand = significand.toULong().toDouble()
            val rate = if (expShift >= 0) {
                uSignificand * Math.pow(2.0, expShift.toDouble())
            } else {
                uSignificand / Math.pow(2.0, (-expShift).toDouble())
            }
            return rate.toInt().coerceIn(8000, 384000)
        }
    }

    override fun sniff(input: ExtractorInput): Boolean {
        scratch.reset(12)
        try {
            input.peekFully(scratch.data, 0, 12)
        } catch (_: EOFException) {
            return false
        }
        val form = scratch.readInt()
        if (form != FOURCC_FORM) return false
        scratch.skipBytes(4) // form size
        val aiff = scratch.readInt()
        return aiff == FOURCC_AIFF || aiff == FOURCC_AIFC
    }

    override fun init(output: ExtractorOutput) {
        this.extractorOutput = output
        this.trackOutput = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        when (state) {
            STATE_READ_HEADER -> {
                input.skipFully(12) // Skip "FORM" + size + "AIFF"
                state = STATE_READ_CHUNKS
                return Extractor.RESULT_CONTINUE
            }

            STATE_READ_CHUNKS -> {
                while (state == STATE_READ_CHUNKS) {
                    scratch.reset(8)
                    try {
                        input.readFully(scratch.data, 0, 8)
                    } catch (_: EOFException) {
                        return Extractor.RESULT_END_OF_INPUT
                    }

                    val chunkId = scratch.readInt()
                    val chunkSize = scratch.readInt().toLong() and 0xFFFFFFFFL

                    when (chunkId) {
                        FOURCC_COMM -> {
                            val commBuf = ByteArray(min(chunkSize.toInt(), 32))
                            input.readFully(commBuf, 0, commBuf.size)
                            val parsable = ParsableByteArray(commBuf)

                            channels = parsable.readShort()
                            numSampleFrames = parsable.readInt().toLong() and 0xFFFFFFFFL
                            sampleSize = parsable.readShort()

                            // 80-bit IEEE 754 extended precision float (10 bytes)
                            val rateBytes = ByteArray(10)
                            parsable.readBytes(rateBytes, 0, 10)
                            sampleRate = readIeeeExtendedFloat(rateBytes)

                            bytesPerFrame = channels * (sampleSize / 8)
                            durationUs = if (sampleRate > 0) {
                                (numSampleFrames * 1_000_000L) / sampleRate
                            } else 0L

                            // Skip any remaining padding in COMM chunk
                            val commRemaining = chunkSize - commBuf.size
                            if (commRemaining > 0) {
                                input.skipFully(commRemaining.toInt())
                            }
                            if ((chunkSize % 2L) != 0L) {
                                input.skipFully(1) // IFF chunk padding
                            }
                        }

                        FOURCC_SSND -> {
                            scratch.reset(8)
                            input.readFully(scratch.data, 0, 8)
                            val offset = scratch.readInt().toLong() and 0xFFFFFFFFL
                            scratch.skipBytes(4) // blockSize

                            if (offset > 0) {
                                input.skipFully(offset.toInt())
                            }

                            ssndDataStartPosition = input.position
                            ssndDataRemainingBytes = chunkSize - 8 - offset

                            val pcmEncoding = when (sampleSize.toInt()) {
                                16 -> C.ENCODING_PCM_16BIT_BIG_ENDIAN
                                24 -> C.ENCODING_PCM_24BIT_BIG_ENDIAN
                                32 -> C.ENCODING_PCM_32BIT_BIG_ENDIAN
                                else -> C.ENCODING_PCM_16BIT_BIG_ENDIAN
                            }

                            val format = Format.Builder()
                                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                                .setChannelCount(channels.toInt())
                                .setSampleRate(sampleRate)
                                .setPcmEncoding(pcmEncoding)
                                .build()

                            trackOutput?.format(format)

                            val seekMap = AiffSeekMap(
                                durationUs = durationUs,
                                dataStartPos = ssndDataStartPosition,
                                bytesPerFrame = bytesPerFrame,
                                sampleRate = sampleRate,
                                dataLength = ssndDataRemainingBytes
                            )
                            extractorOutput?.seekMap(seekMap)

                            state = STATE_STREAM_DATA
                        }

                        else -> {
                            // Skip unknown IFF chunks (e.g. "NAME", "AUTH", "ANNO", "COMT")
                            input.skipFully(chunkSize.toInt())
                            if ((chunkSize % 2L) != 0L) {
                                input.skipFully(1) // IFF chunk padding
                            }
                        }
                    }
                }
                return Extractor.RESULT_CONTINUE
            }

            STATE_STREAM_DATA -> {
                if (ssndDataRemainingBytes <= 0L) {
                    return Extractor.RESULT_END_OF_INPUT
                }

                val toRead = min(4096L, ssndDataRemainingBytes).toInt()
                val bytesRead = trackOutput?.sampleData(input, toRead, true) ?: -1
                if (bytesRead == -1) {
                    return Extractor.RESULT_END_OF_INPUT
                }

                ssndDataRemainingBytes -= bytesRead
                val framesRead = if (bytesPerFrame > 0) bytesRead / bytesPerFrame else 0
                val sampleDurationUs = if (sampleRate > 0) (framesRead * 1_000_000L) / sampleRate else 0L

                trackOutput?.sampleMetadata(
                    currentPositionUs,
                    C.BUFFER_FLAG_KEY_FRAME,
                    bytesRead,
                    0,
                    null
                )

                currentPositionUs += sampleDurationUs
                return Extractor.RESULT_CONTINUE
            }

            else -> return Extractor.RESULT_END_OF_INPUT
        }
    }

    override fun seek(position: Long, timeUs: Long) {
        state = if (position == 0L || ssndDataStartPosition == 0L) {
            STATE_READ_HEADER
        } else {
            STATE_STREAM_DATA
        }
        currentPositionUs = timeUs
        if (position > ssndDataStartPosition) {
            ssndDataRemainingBytes -= (position - ssndDataStartPosition)
        }
    }

    override fun release() {
        // No-op
    }


    private class AiffSeekMap(
        private val durationUs: Long,
        private val dataStartPos: Long,
        private val bytesPerFrame: Int,
        private val sampleRate: Int,
        private val dataLength: Long
    ) : SeekMap {
        override fun isSeekable(): Boolean = true
        override fun getDurationUs(): Long = durationUs
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            if (sampleRate <= 0 || bytesPerFrame <= 0) {
                return SeekMap.SeekPoints(SeekPoint(0L, dataStartPos))
            }
            val frameIndex = (timeUs * sampleRate) / 1_000_000L
            val byteOffset = (frameIndex * bytesPerFrame).coerceIn(0L, dataLength)
            val seekPoint = SeekPoint(timeUs, dataStartPos + byteOffset)
            return SeekMap.SeekPoints(seekPoint)
        }
    }
}
