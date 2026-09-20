package com.hana.spindle.lite.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.hana.spindle.lite.audio.LitePlaybackService

/**
 * Handles hardware button intents for Spindle Lite.
 * Intercepts physical side buttons (2-stage camera shutter, headset buttons, media keys).
 * Routes to foreground activity if active, or falls back directly to LitePlaybackService
 * when backgrounded or screen is locked.
 */
class HardwareButtonReceiver : BroadcastReceiver() {

    companion object {
        var buttonListener: ((KeyEvent) -> Boolean)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_MEDIA_BUTTON || action == Intent.ACTION_CAMERA_BUTTON) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                val handled = buttonListener?.invoke(keyEvent) ?: false
                if (handled) {
                    if (isOrderedBroadcast) abortBroadcast()
                    return
                }

                // Fallback to active LitePlaybackService when activity is backgrounded or screen is off
                val service = LitePlaybackService.instance
                if (service != null) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_CAMERA,
                        KeyEvent.KEYCODE_HEADSETHOOK,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (service.radioEngine.isPlaying || service.radioEngine.isBuffering) {
                                service.radioEngine.togglePlayPause()
                            } else {
                                service.audioEngine.togglePlayPause()
                            }
                            if (isOrderedBroadcast) abortBroadcast()
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (service.radioEngine.isBuffering || service.radioEngine.isPlaying) {
                                val st = service.radioEngine.currentStation ?: com.hana.spindle.lite.audio.LiteRadioEngine.PRESETS[0]
                                service.radioEngine.playStation(st)
                            } else {
                                service.audioEngine.play()
                            }
                            if (isOrderedBroadcast) abortBroadcast()
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_STOP -> {
                            if (service.radioEngine.isPlaying || service.radioEngine.isBuffering) {
                                service.radioEngine.stop()
                            } else {
                                service.audioEngine.pause()
                            }
                            if (isOrderedBroadcast) abortBroadcast()
                        }
                        KeyEvent.KEYCODE_FOCUS, // Half-press physical shutter on Xperia active
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            if (service.radioEngine.isPlaying || service.radioEngine.isBuffering) {
                                service.radioEngine.tuneNext()
                            } else {
                                service.audioEngine.next()
                            }
                            if (isOrderedBroadcast) abortBroadcast()
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            if (service.radioEngine.isPlaying || service.radioEngine.isBuffering) {
                                service.radioEngine.tunePrev()
                            } else {
                                service.audioEngine.previous()
                            }
                            if (isOrderedBroadcast) abortBroadcast()
                        }
                    }
                }
            }
        }
    }
}
