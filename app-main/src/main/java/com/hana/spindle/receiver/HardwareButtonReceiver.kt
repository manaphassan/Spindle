package com.hana.spindle.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import com.hana.spindle.SpindleApp
import com.hana.spindle.playback.HardwareKeyController

/**
 * Handles physical hardware button broadcasts for Spindle DAP (§8.2).
 * Intercepts side keys (2-stage camera shutter, headset buttons, media keys)
 * when Spindle is running in the background, screen is locked, or phone is in pocket.
 *
 * Routes to active foreground listener if registered, or directly to AudioEngine.
 */
class HardwareButtonReceiver : BroadcastReceiver() {

    companion object {
        var buttonListener: ((KeyEvent) -> Boolean)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_MEDIA_BUTTON || action == Intent.ACTION_CAMERA_BUTTON) {
            val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            if (keyEvent != null) {
                val app = context.applicationContext as? SpindleApp
                val handled = dispatchKeyEvent(keyEvent, context, app?.audioEngine)
                if (handled && isOrderedBroadcast) {
                    abortBroadcast()
                }
            }
        }
    }

    fun dispatchKeyEvent(
        keyEvent: KeyEvent,
        context: Context,
        engine: com.hana.spindle.playback.AudioTransport?
    ): Boolean {
        val handledByListener = buttonListener?.invoke(keyEvent) ?: false
        if (handledByListener) return true

        if (engine != null) {
            return HardwareKeyController.handleBroadcastKeyEvent(keyEvent, context, engine)
        }
        return false
    }
}
