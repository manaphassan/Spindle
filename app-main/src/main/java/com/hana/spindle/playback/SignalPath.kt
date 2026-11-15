package com.hana.spindle.playback

import java.util.Locale

enum class SignalStageKind { SOURCE, DECODE, DSP, OUTPUT }

/** One stage of the playback chain. [altered] is true when the stage can change the signal. */
data class SignalStage(val kind: SignalStageKind, val lines: List<String>, val altered: Boolean)

/** Everything the signal-path view needs, gathered from the engine; no Android types. */
data class SignalPathInput(
    val format: String,
    val bitDepth: Int,
    val sampleRateHz: Int,
    val dopActive: Boolean,
    val equalizerActive: Boolean,
    val parametricBands: Int,        // 0 when the parametric EQ is not effective
    val tapeSaturation: Boolean,
    val crossfeed: Boolean,
    val replayGainDb: Float?,        // null when ReplayGain is off
    val mixer: BitPerfectMixerState,
    val outputRoute: String
)

/**
 * Builds a Roon-style "source -> decode -> DSP -> output" description from the live engine state.
 * It only reports what is actually in the path; it never claims bit-perfect unless the platform
 * confirmed it (see [BitPerfectMixerController]).
 */
object SignalPathBuilder {

    fun decoderFor(format: String, dopActive: Boolean): String = when (format.uppercase(Locale.ROOT)) {
        "WV" -> "Spindle WavPack decoder (lossless, pure Kotlin)"
        "DSF", "DSD" -> if (dopActive) "Spindle DSF reader, DoP passthrough" else "Spindle DSF reader, DSD to PCM 88.2 kHz"
        "AIFF", "AIF" -> "Spindle AIFF reader (PCM passthrough)"
        "FLAC" -> "Media3 FLAC"
        "WAV" -> "Media3 WAV (PCM passthrough)"
        "MP3" -> "Media3 MP3"
        "M4A", "ALAC" -> "Media3 MP4 / AAC / ALAC"
        "OGG", "OPUS" -> "Media3 Ogg / Opus"
        else -> "Media3"
    }

    fun build(i: SignalPathInput): List<SignalStage> {
        val rate = formatRate(i.sampleRateHz)
        val depth = if (i.bitDepth > 0) "${i.bitDepth}-bit" else null
        val source = listOfNotNull(i.format.uppercase(Locale.ROOT), depth, rate).joinToString(" · ")

        val dsp = ArrayList<String>()
        if (i.equalizerActive) dsp += "Equalizer (system effect)"
        if (i.parametricBands > 0) dsp += "Parametric EQ · ${i.parametricBands} bands"
        if (i.tapeSaturation) dsp += "Tape saturation"
        if (i.crossfeed) dsp += "Crossfeed"
        i.replayGainDb?.let { dsp += String.format(Locale.US, "ReplayGain %+.1f dB", it) }

        val output = ArrayList<String>()
        output += i.outputRoute
        val m = i.mixer
        output += when {
            m.isVerifiedActive && m.format != null ->
                "Bit-perfect mixer: ${formatRate(m.format.sampleRateHz)} · ${m.format.bitDepth}-bit (platform confirmed)"
            m.status == BitPerfectStatus.NO_MATCH -> "Android mixer will resample (DAC cannot carry this format untouched)"
            m.status == BitPerfectStatus.REJECTED -> "Android refused the bit-perfect request; mixer active"
            m.status == BitPerfectStatus.NO_DEVICE -> "Bit-perfect mixer: no USB / wired DAC"
            m.status == BitPerfectStatus.UNSUPPORTED_OS -> "Bit-perfect mixer needs Android 14+"
            else -> "Android mixer (may resample or apply volume)"
        }

        return listOf(
            SignalStage(SignalStageKind.SOURCE, listOf(source), false),
            SignalStage(SignalStageKind.DECODE, listOf(decoderFor(i.format, i.dopActive)), false),
            SignalStage(SignalStageKind.DSP, dsp, dsp.isNotEmpty()),
            SignalStage(SignalStageKind.OUTPUT, output, !m.isVerifiedActive)
        )
    }

    private fun formatRate(hz: Int): String? = when {
        hz <= 0 -> null
        hz % 1000 == 0 -> "${hz / 1000} kHz"
        else -> String.format(Locale.US, "%.1f kHz", hz / 1000f)
    }
}
