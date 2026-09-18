package com.hana.spindle.lite.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

/**
 * Handles hardware button intents (e.g. 2-stage camera shutter button on legacy devices, headset hook).
 */
class HardwareButtonReceiver : BroadcastReceiver() {

    companion object {
        var buttonListener: ((KeyEvent) -> Boolean)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON || intent?.action == Intent.ACTION_CAMERA_BUTTON) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null) {
                val handled = buttonListener?.invoke(keyEvent) ?: false
                if (handled && isOrderedBroadcast) {
                    abortBroadcast()
                }
            }
        }
    }
}
