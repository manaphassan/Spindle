package com.hana.spindle.playback

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
    fun rewind(deltaMs: Long = 10_000L)
    fun fastForward(deltaMs: Long = 10_000L)
    val isPlaying: Boolean
}
