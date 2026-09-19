package com.hana.spindle.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hana.spindle.SpindleApp
import com.hana.spindle.ui.LockscreenActivity

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val app = application as? SpindleApp ?: return
                val isPlaying = app.audioEngine.playbackState.value.isPlaying
                val prefs = getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
                val lockscreenEnabled = prefs.getBoolean("pref_lockscreen_player", true)

                if (isPlaying && lockscreenEnabled) {
                    val lockIntent = Intent(context, LockscreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(lockIntent)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as SpindleApp
        val player = app.audioEngine.exoPlayer
        mediaSession = MediaSession.Builder(this, player).build()

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}

        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
