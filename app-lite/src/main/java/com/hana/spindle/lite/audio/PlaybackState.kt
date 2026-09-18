package com.hana.spindle.lite.audio

import com.hana.spindle.lite.db.Track

/**
 * Playback state model and listener for Spindle Lite.
 */
data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val trackIndex: Int = 0,
    val totalTracks: Int = 0
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedPosition: String
        get() {
            val totalSec = currentPositionMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", min, sec)
        }

    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", min, sec)
        }
}

interface PlaybackListener {
    fun onPlaybackStateChanged(state: PlaybackState)
    fun onTrackCompleted(track: Track?)
    fun onPlaybackError(errorMessage: String)
}
