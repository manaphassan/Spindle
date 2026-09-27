package com.hana.spindle.core

/**
 * Audio transition modes between consecutive tracks in a queue.
 * - GAPLESS: Zero latency, sample-accurate gapless transition.
 * - CROSSFADE_2S: 2-second equal-power sinusoidal crossfade.
 * - CROSSFADE_4S: 4-second equal-power sinusoidal crossfade.
 */
enum class CrossfadeMode(val durationMs: Long, val displayName: String) {
    GAPLESS(0L, "GAPLESS (0s)"),
    CROSSFADE_2S(2000L, "CROSSFADE 2s"),
    CROSSFADE_4S(4000L, "CROSSFADE 4s")
}
