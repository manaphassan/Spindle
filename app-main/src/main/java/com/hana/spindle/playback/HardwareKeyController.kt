package com.hana.spindle.playback

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.widget.Toast

import com.hana.spindle.R
import com.hana.spindle.core.SpindleGlossary

/**
 * Centralized Hardware Key & DAP Button Matrix Controller (§8.2).
 * Unifies physical button handling across:
 * 1. Foreground MainActivity (Home Launcher & Deck)
 * 2. Foreground LockscreenActivity (Keyguard Cassette Deck)
 * 3. MediaSessionService (MediaSession callback)
 * 4. HardwareButtonReceiver (OS Broadcasts for MEDIA_BUTTON and CAMERA_BUTTON)
 *
 * Implements:
 * - Dedicated DAP Mode (prioritizing D-pad, hardware wheel, transport keys)
 * - Volume Buttons Long-Press Skip (Hold Vol+ to skip next, Vol- to skip previous)
 * - Hold = Seek and Tap = Skip on Hardware Transport Keys (FF/REW/NEXT/PREV)
 * - Xperia 2-Stage Camera Shutter Remap (Full-press: Play/Pause, Half-press Focus: Next Track)
 * - Standard Media Keys (Headset Hook, Play, Pause, Stop, Next, Prev, Fast Forward, Rewind)
 */
class HardwareKeyController(
    private val onNavigateToVault: (() -> Unit)? = null,
    private val onNavigateToDrawer: (() -> Unit)? = null,
    @Deprecated("Use onNavigateToVault in accordance with canonical glossary", ReplaceWith("onNavigateToVault"))
    private val onNavigateToCatalog: (() -> Unit)? = onNavigateToVault
) {
    var isVolumeLongPress: Boolean = false
        private set

    var isSeekLongPress: Boolean = false
        private set

    private var wasSyntheticDownHandled: Boolean = false

    fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
        context: Context,
        audioEngine: AudioTransport
    ): Boolean {
        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val isDapMode = prefs.getBoolean("pref_dap_hardware_mode", false)

        // 1. Dedicated Audiophile DAP Hardware Mode Interception
        if (isDapMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    if (event != null) {
                        event.startTracking()
                        isSeekLongPress = false
                        return true
                    } else {
                        wasSyntheticDownHandled = true
                        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                            audioEngine.playNext()
                        } else {
                            audioEngine.playPrevious(forcePreviousSong = false)
                        }
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    audioEngine.togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    audioEngine.play()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    audioEngine.stop()
                    return true
                }
                KeyEvent.KEYCODE_FOCUS -> {
                    // Half-shutter key advances to next track on classic hardware (Sony Xperia)
                    audioEngine.playNext()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    audioEngine.rewind(SpindleGlossary.SEEK_STEP_MANUAL_MS)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    audioEngine.fastForward(SpindleGlossary.SEEK_STEP_MANUAL_MS)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_TOGGLE_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    return true
                }
                KeyEvent.KEYCODE_SEARCH -> {
                    (onNavigateToVault ?: onNavigateToCatalog)?.invoke()
                    return true
                }
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_APP_SWITCH -> {
                    onNavigateToDrawer?.invoke()
                    return true
                }
            }
        }

        // 2. Volume Buttons Long-Press Skip Tracking
        val volumeSkipEnabled = prefs.getBoolean("pref_volume_skip", true)
        if (volumeSkipEnabled && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            event?.startTracking()
            if (event?.repeatCount == 0) {
                isVolumeLongPress = false
            }
            return true
        }

        // 3. Dedicated Hardware Camera Button Remapped to Play/Pause & Next
        val cameraKeyRemap = prefs.getBoolean("pref_camera_key_play_pause", true)
        if (cameraKeyRemap) {
            when (keyCode) {
                KeyEvent.KEYCODE_CAMERA -> {
                    audioEngine.togglePlayPause()
                    val isPlaying = audioEngine.isPlaying
                    val resId = if (isPlaying) R.string.toast_audio_playing else R.string.toast_audio_paused
                    showToast(context, resId)
                    return true
                }
                KeyEvent.KEYCODE_FOCUS -> {
                    audioEngine.playNext()
                    showToast(context, R.string.toast_next_track)
                    return true
                }
            }
        }

        // 4. Standard Fallback Media Keys
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (event != null) {
                    event.startTracking()
                    isSeekLongPress = false
                    return true
                } else {
                    wasSyntheticDownHandled = true
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                        audioEngine.playNext()
                    } else {
                        audioEngine.playPrevious(forcePreviousSong = false)
                    }
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                audioEngine.togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                audioEngine.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                audioEngine.stop()
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            event?.startTracking()
            return false
        }

        return false
    }

    fun onKeyLongPress(
        keyCode: Int,
        event: KeyEvent?,
        context: Context,
        audioEngine: AudioTransport
    ): Boolean {
        // Transport keys hold = continuous seek (±5000ms)
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                isSeekLongPress = true
                audioEngine.fastForward(SpindleGlossary.SEEK_STEP_MANUAL_MS)
                showToast(context, R.string.toast_fast_forward)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                isSeekLongPress = true
                audioEngine.rewind(SpindleGlossary.SEEK_STEP_MANUAL_MS)
                showToast(context, R.string.toast_rewind)
                return true
            }
        }

        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val volumeSkipEnabled = prefs.getBoolean("pref_volume_skip", true)

        if (volumeSkipEnabled) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    isVolumeLongPress = true
                    audioEngine.playNext()
                    showToast(context, R.string.toast_next_track)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    isVolumeLongPress = true
                    audioEngine.playPrevious(forcePreviousSong = true)
                    showToast(context, R.string.toast_prev_track)
                    return true
                }
            }
        }
        return false
    }

    private fun showToast(context: Context, resId: Int) {
        try {
            val message = context.getString(resId)
            Toast.makeText(context, message, Toast.LENGTH_SHORT)?.show()
        } catch (_: Throwable) {}
    }

    fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?,
        context: Context,
        audioEngine: AudioTransport
    ): Boolean {
        // 1. Handle transport keys (tap = skip, hold = seek)
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (wasSyntheticDownHandled) {
                    wasSyntheticDownHandled = false
                    return true
                }
                if (isSeekLongPress) {
                    isSeekLongPress = false
                    return true
                }
                // Tap detected -> skip track
                if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                    audioEngine.playNext()
                } else {
                    audioEngine.playPrevious(forcePreviousSong = false)
                }
                return true
            }
        }

        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val isDapMode = prefs.getBoolean("pref_dap_hardware_mode", false)

        if (isDapMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_CAMERA,
                KeyEvent.KEYCODE_FOCUS,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_SEARCH,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_APP_SWITCH -> return true
            }
        }

        val volumeSkipEnabled = prefs.getBoolean("pref_volume_skip", true)
        if (volumeSkipEnabled && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (isVolumeLongPress) {
                isVolumeLongPress = false
                return true
            }
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                AudioManager.ADJUST_RAISE
            } else {
                AudioManager.ADJUST_LOWER
            }
            audioManager?.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            return true
        }

        // Always consume Camera Shutter key onKeyUp if remap is active,
        // preventing the OEM system camera app from launching
        val cameraKeyRemap = prefs.getBoolean("pref_camera_key_play_pause", true)
        if (cameraKeyRemap && (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_FOCUS)) {
            return true
        }

        return false
    }

    companion object {
        /**
         * Dispatches a broadcast KeyEvent (e.g. from HardwareButtonReceiver or MediaSessionService).
         */
        fun handleBroadcastKeyEvent(
            keyEvent: KeyEvent,
            context: Context,
            audioEngine: AudioTransport
        ): Boolean {
            return handleKeyEventInternal(keyEvent.action, keyEvent.keyCode, context, audioEngine)
        }

        /**
         * Core key action dispatcher accepting raw action and keyCode for testability and broadcast handling.
         */
        fun handleKeyEventInternal(
            action: Int,
            keyCode: Int,
            context: Context,
            audioEngine: AudioTransport
        ): Boolean {
            if (action != KeyEvent.ACTION_DOWN) {
                // Return true for KEYCODE_CAMERA on key up to prevent OEM camera launch
                if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_FOCUS) {
                    val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
                    return prefs.getBoolean("pref_camera_key_play_pause", true)
                }
                return false
            }

            val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            val isDapMode = prefs.getBoolean("pref_dap_hardware_mode", false)
            val cameraKeyRemap = prefs.getBoolean("pref_camera_key_play_pause", true)

            when (keyCode) {
                KeyEvent.KEYCODE_CAMERA -> {
                    if (cameraKeyRemap) {
                        audioEngine.togglePlayPause()
                        return true
                    }
                }
                KeyEvent.KEYCODE_FOCUS -> {
                    if (cameraKeyRemap || isDapMode) {
                        audioEngine.playNext()
                        return true
                    }
                }
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    audioEngine.togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    audioEngine.play()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    audioEngine.stop()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    audioEngine.playNext()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    audioEngine.playPrevious(forcePreviousSong = false)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (isDapMode) {
                        audioEngine.togglePlayPause()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isDapMode) {
                        audioEngine.rewind(SpindleGlossary.SEEK_STEP_MANUAL_MS)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isDapMode) {
                        audioEngine.fastForward(SpindleGlossary.SEEK_STEP_MANUAL_MS)
                        return true
                    }
                }
            }
            return false
        }
    }
}
