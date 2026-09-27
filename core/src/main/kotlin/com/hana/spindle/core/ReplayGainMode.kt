package com.hana.spindle.core

/**
 * ReplayGain loudness normalization modes for audiophile playback.
 * - OFF: No gain adjustment; raw audio stream dynamics preserved.
 * - TRACK: Per-track gain adjustment to standard 89 dB target level.
 * - ALBUM: Whole-album gain adjustment preserving artist-intended inter-track dynamics.
 */
enum class ReplayGainMode(val displayName: String) {
    OFF("OFF"),
    TRACK("TRACK GAIN"),
    ALBUM("ALBUM GAIN")
}
