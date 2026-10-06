package com.hana.spindle.playback

import com.hana.spindle.core.SpindleGlossary

/**
 * Common playback transport interface for hardware buttons, media sessions,
 * and remote transport controllers (§8.2).
 */
interface AudioTransport {
    fun playNext()
    fun playPrevious(forcePreviousSong: Boolean = false)
    fun togglePlayPause()
    fun play()
    fun pause()
    fun stop() {
        pause()
        seekTo(0L)
    }
    fun rewind(deltaMs: Long = SpindleGlossary.SEEK_STEP_MANUAL_MS)
    fun fastForward(deltaMs: Long = SpindleGlossary.SEEK_STEP_MANUAL_MS)
    fun seekTo(positionMs: Long) = Unit
    val isPlaying: Boolean
}
