package com.hana.spindle.playback.extractor

/** SharedPreferences key (spindle_prefs) for opt-in DSD-over-PCM (DoP) output. */
const val PREF_DSD_DOP = "pref_dsd_dop"

/**
 * DoP (DSD over PCM, DoP 1.1) packing. Each 24-bit PCM word carries 16 consecutive DSD bits of one
 * channel in its low 16 bits (oldest bit most significant) and an 8-bit marker (0x05 / 0xFA,
 * alternating every frame) in the top byte. A DAC that recognises the markers plays native DSD.
 * Any gain, resampling or effect in the path destroys the stream, so DoP is only ever used on a
 * platform-verified bit-perfect mixer path (see [DsdOutput]).
 */
object DopPacker {
    const val MARKER_A = 0x05
    const val MARKER_B = 0xFA

    /** DoP PCM sample rate for a DSD rate (DSD64 2 822 400 Hz -> 176 400 Hz), or 0 if not exact. */
    fun pcmRateFor(dsdRateHz: Int): Int = if (dsdRateHz > 0 && dsdRateHz % 16 == 0) dsdRateHz / 16 else 0

    private val REVERSED = ByteArray(256) { i ->
        var v = i; var r = 0
        repeat(8) { r = (r shl 1) or (v and 1); v = v shr 1 }
        r.toByte()
    }

    /**
     * Packs DSD bytes into 24-bit little-endian DoP frames.
     *
     * @param src planar DSD data: channel `c` bytes start at `src[c * channelStride + srcOffset]`
     * @param bytesPerChannel DSD bytes available per channel (an odd trailing byte is dropped)
     * @param lsbFirst true for DSF (bits stored least-significant first), false for DSDIFF
     * @param markerPhase 0 or 1: marker used for the first frame (0 = 0x05)
     * @return the number of bytes written to [out] and the marker phase to continue with
     */
    fun pack(
        src: ByteArray, srcOffset: Int, channelStride: Int, channels: Int, bytesPerChannel: Int,
        lsbFirst: Boolean, markerPhase: Int, out: ByteArray
    ): Pair<Int, Int> {
        val frames = bytesPerChannel / 2
        var o = 0
        var phase = markerPhase and 1
        for (f in 0 until frames) {
            val marker = if (phase == 0) MARKER_A else MARKER_B
            for (c in 0 until channels) {
                val base = c * channelStride + srcOffset + f * 2
                var older = src[base].toInt() and 0xFF
                var newer = src[base + 1].toInt() and 0xFF
                if (lsbFirst) { older = REVERSED[older].toInt() and 0xFF; newer = REVERSED[newer].toInt() and 0xFF }
                out[o++] = newer.toByte()      // little-endian: low byte first
                out[o++] = older.toByte()
                out[o++] = marker.toByte()
            }
            phase = phase xor 1
        }
        return o to phase
    }
}

/**
 * Process-wide DSD output policy shared by the engine and [DsfExtractor]. DoP is attempted only
 * when the user opted in AND [negotiator] confirms (the engine wires it to the bit-perfect mixer)
 * that the platform accepted an exact mixer format for the DoP rate at 24-bit. Otherwise the
 * extractor falls back to its PCM decimation path, so a failed negotiation never emits DoP words.
 */
object DsdOutput {
    @Volatile var dopRequested: Boolean = false

    /** Returns true only if a verified bit-perfect path for (pcmRateHz, 24-bit) is active. */
    @Volatile var negotiator: ((pcmRateHz: Int) -> Boolean)? = null

    fun tryEnableDop(dsdRateHz: Int): Boolean {
        if (!dopRequested) return false
        val rate = DopPacker.pcmRateFor(dsdRateHz)
        if (rate <= 0 || rate > 705_600) return false
        return try { negotiator?.invoke(rate) == true } catch (e: Exception) { false }
    }
}
