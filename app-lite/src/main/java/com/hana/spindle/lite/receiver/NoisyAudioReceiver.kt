package com.hana.spindle.lite.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * Automatically pauses playback when 3.5mm headphone jack is unplugged.
 */
class NoisyAudioReceiver(private val onNoisyCallback: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            onNoisyCallback.invoke()
        }
    }
}
