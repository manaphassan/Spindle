package com.hana.spindle.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Real stereo output level meter.
 *
 * Decoded PCM is measured where it enters the audio sink (before Android's session
 * effects, so it reflects the source signal), split into short blocks stamped with
 * their presentation time. Readers only see blocks whose timestamp has already
 * become audible, so meters move with what is heard rather than with the decoder,
 * which runs ahead of playback by the AudioTrack buffer length.
 */
@UnstableApi
class OutputLevelMeter {

    private val taps = CopyOnWriteArrayList<LevelTapAudioSink>()

    internal fun register(tap: LevelTapAudioSink) {
        taps.addIfAbsent(tap)
    }

    internal fun unregister(tap: LevelTapAudioSink) {
        taps.remove(tap)
    }

    /**
     * Writes the loudest levels heard during the last [windowMs] into [out] as linear
     * full-scale values: [peakL, peakR, rmsL, rmsR]. Returns false when no PCM stream is
     * currently being metered (paused, stopped, passthrough or offload), so callers can
     * show an idle meter instead of inventing motion.
     */
    fun read(windowMs: Long, out: FloatArray): Boolean {
        out.fill(0f)
        var active = false
        for (tap in taps) {
            if (tap.accumulateAudible(windowMs * 1000L, out)) active = true
        }
        return active
    }
}

@UnstableApi
class LevelTapAudioSink(
    sink: AudioSink,
    private val meter: OutputLevelMeter,
    /** False while the PCM is not audio samples (e.g. DoP-packed DSD); the meter then rests. */
    private val isMeasurable: () -> Boolean = { true }
) : ForwardingAudioSink(sink) {

    private companion object {
        const val RING_SIZE = 512
        const val BLOCK_FRAMES = 512
        const val STALE_POSITION_MS = 300L
    }

    private val lock = Any()
    private val blockPtsUs = LongArray(RING_SIZE)
    private val blockPeakL = FloatArray(RING_SIZE)
    private val blockPeakR = FloatArray(RING_SIZE)
    private val blockRmsL = FloatArray(RING_SIZE)
    private val blockRmsR = FloatArray(RING_SIZE)
    private var writeIndex = 0
    private var blockCount = 0

    @Volatile
    private var encoding = C.ENCODING_INVALID

    @Volatile
    private var channelCount = 0

    @Volatile
    private var sampleRate = 0

    @Volatile
    private var audiblePositionUs = C.TIME_UNSET

    @Volatile
    private var audiblePositionAtMs = 0L

    private var lastBuffer: ByteBuffer? = null
    private var lastBufferPtsUs = Long.MIN_VALUE

    init {
        meter.register(this)
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val isPcm = MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType
        encoding = if (isPcm) inputFormat.pcmEncoding else C.ENCODING_INVALID
        channelCount = inputFormat.channelCount
        sampleRate = inputFormat.sampleRate
        clearBlocks()
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // The renderer re-submits a partially consumed buffer with the same timestamp;
        // it was measured in full the first time.
        if (buffer !== lastBuffer || presentationTimeUs != lastBufferPtsUs) {
            lastBuffer = buffer
            lastBufferPtsUs = presentationTimeUs
            if (isMeasurable()) measure(buffer, presentationTimeUs)
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val positionUs = super.getCurrentPositionUs(sourceEnded)
        if (positionUs != AudioSink.CURRENT_POSITION_NOT_SET) {
            audiblePositionUs = positionUs
            audiblePositionAtMs = SystemClock.uptimeMillis()
        }
        return positionUs
    }

    override fun flush() {
        clearBlocks()
        super.flush()
    }

    override fun reset() {
        clearBlocks()
        super.reset()
    }

    override fun release() {
        meter.unregister(this)
        clearBlocks()
        super.release()
    }

    internal fun accumulateAudible(windowUs: Long, out: FloatArray): Boolean {
        val positionUs = audiblePositionUs
        if (positionUs == C.TIME_UNSET || encoding == C.ENCODING_INVALID) return false
        if (SystemClock.uptimeMillis() - audiblePositionAtMs > STALE_POSITION_MS) return false
        val fromUs = positionUs - windowUs
        synchronized(lock) {
            for (k in 0 until blockCount) {
                val idx = (writeIndex - 1 - k + RING_SIZE) % RING_SIZE
                val pts = blockPtsUs[idx]
                if (pts > positionUs) continue
                if (pts < fromUs) break
                out[0] = max(out[0], blockPeakL[idx])
                out[1] = max(out[1], blockPeakR[idx])
                out[2] = max(out[2], blockRmsL[idx])
                out[3] = max(out[3], blockRmsR[idx])
            }
        }
        return true
    }

    private fun clearBlocks() {
        synchronized(lock) {
            writeIndex = 0
            blockCount = 0
        }
        lastBuffer = null
        lastBufferPtsUs = Long.MIN_VALUE
        audiblePositionUs = C.TIME_UNSET
    }

    private fun measure(buffer: ByteBuffer, presentationTimeUs: Long) {
        val enc = encoding
        val channels = channelCount
        val rate = sampleRate
        if (channels <= 0 || rate <= 0) return
        val bytesPerSample = when (enc) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val frameBytes = bytesPerSample * channels
        val start = buffer.position()
        val frames = (buffer.limit() - start) / frameBytes
        if (frames <= 0) return

        var frame = 0
        while (frame < frames) {
            val blockEnd = minOf(frame + BLOCK_FRAMES, frames)
            var peakL = 0f
            var peakR = 0f
            var sumL = 0.0
            var sumR = 0.0
            for (f in frame until blockEnd) {
                val base = start + f * frameBytes
                val l = readSample(buffer, base, enc)
                val r = if (channels > 1) readSample(buffer, base + bytesPerSample, enc) else l
                val al = abs(l)
                val ar = abs(r)
                if (al > peakL) peakL = al
                if (ar > peakR) peakR = ar
                sumL += (l * l).toDouble()
                sumR += (r * r).toDouble()
            }
            val n = (blockEnd - frame).toDouble()
            val ptsUs = presentationTimeUs + frame * 1_000_000L / rate
            synchronized(lock) {
                blockPtsUs[writeIndex] = ptsUs
                blockPeakL[writeIndex] = peakL
                blockPeakR[writeIndex] = peakR
                blockRmsL[writeIndex] = sqrt(sumL / n).toFloat()
                blockRmsR[writeIndex] = sqrt(sumR / n).toFloat()
                writeIndex = (writeIndex + 1) % RING_SIZE
                if (blockCount < RING_SIZE) blockCount++
            }
            frame = blockEnd
        }
    }

    private fun readSample(b: ByteBuffer, i: Int, enc: Int): Float {
        return when (enc) {
            C.ENCODING_PCM_16BIT -> {
                val v = (b.get(i).toInt() and 0xFF) or (b.get(i + 1).toInt() shl 8)
                v / 32768f
            }

            C.ENCODING_PCM_24BIT -> {
                val v = (b.get(i).toInt() and 0xFF) or
                        ((b.get(i + 1).toInt() and 0xFF) shl 8) or
                        (b.get(i + 2).toInt() shl 16)
                v / 8388608f
            }

            C.ENCODING_PCM_32BIT -> leInt(b, i) / 2147483648f
            C.ENCODING_PCM_FLOAT -> java.lang.Float.intBitsToFloat(leInt(b, i))
            C.ENCODING_PCM_8BIT -> ((b.get(i).toInt() and 0xFF) - 128) / 128f
            else -> 0f
        }
    }

    private fun leInt(b: ByteBuffer, i: Int): Int =
        (b.get(i).toInt() and 0xFF) or
                ((b.get(i + 1).toInt() and 0xFF) shl 8) or
                ((b.get(i + 2).toInt() and 0xFF) shl 16) or
                (b.get(i + 3).toInt() shl 24)
}
