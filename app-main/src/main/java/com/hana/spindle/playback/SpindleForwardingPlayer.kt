package com.hana.spindle.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Custom [ForwardingPlayer] that routes media playback commands from
 * external controllers (MediaSession, Bluetooth AVRCP, Android System Notification)
 * through Spindle's canonical [AudioEngine] and [AudioTransport].
 *
 * This ensures that system-level play, pause, next, previous, and seek actions
 * execute the full Spindle audio pipeline (mechanical solenoid foley,
 * universal >3s previous behavior, play counts, lyrics sync, and telemetry).
 */
class SpindleForwardingPlayer(
    player: Player,
    private val audioEngine: AudioEngine
) : ForwardingPlayer(player) {

    override fun play() {
        audioEngine.play()
    }

    override fun pause() {
        audioEngine.pause()
    }

    override fun stop() {
        audioEngine.stop()
    }

    override fun seekToNext() {
        audioEngine.playNext()
    }

    override fun seekToNextMediaItem() {
        audioEngine.playNext()
    }

    override fun seekToPrevious() {
        audioEngine.playPrevious(forcePreviousSong = false)
    }

    override fun seekToPreviousMediaItem() {
        audioEngine.playPrevious(forcePreviousSong = false)
    }

    override fun seekTo(positionMs: Long) {
        audioEngine.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) {
            audioEngine.seekTo(positionMs)
        } else {
            audioEngine.playQueueIndex(mediaItemIndex)
            if (positionMs > 0L) {
                audioEngine.seekTo(positionMs)
            }
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        val builder = super.getAvailableCommands().buildUpon()
        if (audioEngine.playbackState.value.currentTrack != null) {
            builder.addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_STOP
            )
        }
        return builder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        if (audioEngine.playbackState.value.currentTrack != null) {
            when (command) {
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_STOP -> return true
            }
        }
        return super.isCommandAvailable(command)
    }
}
