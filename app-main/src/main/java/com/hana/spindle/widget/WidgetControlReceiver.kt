package com.hana.spindle.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hana.spindle.SpindleApp

/**
 * Handles the widget's transport buttons. Registered with exported=false so only this app's own
 * (immutable) PendingIntents can reach it; other apps cannot remote-control playback.
 */
class WidgetControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.hana.spindle.widget.ACTION_WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_PREV = "com.hana.spindle.widget.ACTION_WIDGET_PREV"
        const val ACTION_WIDGET_NEXT = "com.hana.spindle.widget.ACTION_WIDGET_NEXT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? SpindleApp ?: return
        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> app.audioEngine.togglePlayPause()
            ACTION_WIDGET_PREV -> app.audioEngine.playPrevious(forcePreviousSong = false)
            ACTION_WIDGET_NEXT -> app.audioEngine.playNext()
        }
    }
}
