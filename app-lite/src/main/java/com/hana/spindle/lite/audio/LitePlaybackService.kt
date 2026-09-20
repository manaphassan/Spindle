package com.hana.spindle.lite.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.hana.spindle.lite.R
import com.hana.spindle.lite.db.Track
import com.hana.spindle.lite.ui.LiteMainActivity

/**
 * Ultra-lightweight Foreground Playback Service for Spindle Lite.
 * Engineered for background resilience, lockscreen controls, and minimal battery impact:
 * - Runs foreground service with low-overhead media notification (API 19 through API 35).
 * - Manages CPU WakeLock only while actively playing, releasing on pause.
 * - Throttles UI progress runnable to 0% CPU overhead during screen-off deep sleep.
 * - Handles audio noisy intent (headphone disconnect) to prevent unwanted speaker blare.
 */
class LitePlaybackService : Service(), PlaybackListener {

    inner class LocalBinder : Binder() {
        val service: LitePlaybackService
            get() = this@LitePlaybackService
    }

    private val binder = LocalBinder()

    lateinit var audioEngine: LiteAudioEngine
        private set
    lateinit var radioEngine: LiteRadioEngine
        private set
    lateinit var analogFmEngine: LiteAnalogFmEngine
        private set

    private var wakeLock: PowerManager.WakeLock? = null
    private var isScreenReceiverRegistered = false
    private var isNoisyReceiverRegistered = false
    private var isForegroundActive = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    audioEngine.isScreenOn = false
                }
                Intent.ACTION_SCREEN_ON -> {
                    audioEngine.isScreenOn = true
                }
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (audioEngine.isPlaying) {
                    audioEngine.pause()
                }
                if (radioEngine.isPlaying || radioEngine.isBuffering) {
                    radioEngine.stop()
                }
                if (analogFmEngine.isAnalogModeEnabled) {
                    analogFmEngine.setAnalogMode(false)
                }
                updateNotification()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "spindle_lite_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_PLAY = "com.hana.spindle.lite.ACTION_PLAY"
        const val ACTION_PAUSE = "com.hana.spindle.lite.ACTION_PAUSE"
        const val ACTION_TOGGLE_PLAY = "com.hana.spindle.lite.ACTION_TOGGLE_PLAY"
        const val ACTION_NEXT = "com.hana.spindle.lite.ACTION_NEXT"
        const val ACTION_PREV = "com.hana.spindle.lite.ACTION_PREV"
        const val ACTION_STOP = "com.hana.spindle.lite.ACTION_STOP"

        @Volatile
        var instance: LitePlaybackService? = null
            private set

        @Volatile
        private var sharedAudioEngine: LiteAudioEngine? = null
        @Volatile
        private var sharedRadioEngine: LiteRadioEngine? = null
        @Volatile
        private var sharedAnalogFmEngine: LiteAnalogFmEngine? = null

        fun getAudioEngine(context: Context): LiteAudioEngine {
            return sharedAudioEngine ?: synchronized(this) {
                sharedAudioEngine ?: LiteAudioEngine(context.applicationContext).also { sharedAudioEngine = it }
            }
        }

        fun getRadioEngine(context: Context): LiteRadioEngine {
            return sharedRadioEngine ?: synchronized(this) {
                sharedRadioEngine ?: LiteRadioEngine(context.applicationContext).also { sharedRadioEngine = it }
            }
        }

        fun getAnalogFmEngine(context: Context): LiteAnalogFmEngine {
            return sharedAnalogFmEngine ?: synchronized(this) {
                sharedAnalogFmEngine ?: LiteAnalogFmEngine(context.applicationContext).also { sharedAnalogFmEngine = it }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioEngine = getAudioEngine(applicationContext)
        radioEngine = getRadioEngine(applicationContext)
        analogFmEngine = getAnalogFmEngine(applicationContext)
        instance = this

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SpindleLite:WakeLock")?.apply {
            setReferenceCounted(false)
        }

        audioEngine.serviceListener = this

        createNotificationChannel()
        registerScreenReceiver()
        registerNoisyReceiver()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> audioEngine.play()
            ACTION_PAUSE -> audioEngine.pause()
            ACTION_TOGGLE_PLAY -> {
                if (radioEngine.isPlaying || radioEngine.isBuffering) {
                    radioEngine.togglePlayPause()
                } else {
                    audioEngine.togglePlayPause()
                }
            }
            ACTION_NEXT -> {
                if (radioEngine.isPlaying || radioEngine.isBuffering) {
                    radioEngine.tuneNext()
                } else {
                    audioEngine.next()
                }
            }
            ACTION_PREV -> {
                if (radioEngine.isPlaying || radioEngine.isBuffering) {
                    radioEngine.tunePrev()
                } else {
                    audioEngine.previous()
                }
            }
            ACTION_STOP -> {
                audioEngine.pause()
                radioEngine.stop()
                if (analogFmEngine.isAnalogModeEnabled) {
                    analogFmEngine.setAnalogMode(false)
                }
                releaseWakeLock()
                stopForeground(true)
                isForegroundActive = false
                stopSelf()
            }
        }
        return START_STICKY
    }

    // --- PlaybackListener for Notification & WakeLock ---

    override fun onPlaybackStateChanged(state: PlaybackState) {
        if (state.isPlaying) {
            acquireWakeLock()
            val notification = buildNotification(state.currentTrack, true)
            if (!isForegroundActive) {
                startForegroundCompat(notification)
                isForegroundActive = true
            } else {
                updateNotificationWith(notification)
            }
        } else {
            releaseWakeLock()
            val notification = buildNotification(state.currentTrack, false)
            updateNotificationWith(notification)
            // Allow user to dismiss notification when paused
            if (isForegroundActive) {
                stopForeground(false)
                isForegroundActive = false
            }
        }
    }

    override fun onTrackCompleted(track: Track?) {
        // Notification updates automatically on next track's onPlaybackStateChanged
    }

    override fun onPlaybackError(errorMessage: String) {
        releaseWakeLock()
        if (isForegroundActive) {
            stopForeground(false)
            isForegroundActive = false
        }
    }

    fun updateNotificationForRadio(stationName: String, isPlaying: Boolean) {
        if (isPlaying) {
            acquireWakeLock()
            val notification = buildRadioNotification(stationName, true)
            if (!isForegroundActive) {
                startForegroundCompat(notification)
                isForegroundActive = true
            } else {
                updateNotificationWith(notification)
            }
        } else {
            releaseWakeLock()
            val notification = buildRadioNotification(stationName, false)
            updateNotificationWith(notification)
            if (isForegroundActive) {
                stopForeground(false)
                isForegroundActive = false
            }
        }
    }

    private fun updateNotification() {
        val track = audioEngine.currentTrack
        val isPlaying = audioEngine.isPlaying
        if (track != null) {
            val notification = buildNotification(track, isPlaying)
            updateNotificationWith(notification)
        }
    }

    private fun buildNotification(track: Track?, isPlaying: Boolean): Notification {
        val title = track?.title ?: getString(R.string.app_name)
        val artist = track?.artist ?: "Ready"
        val specs = track?.audioSpecsLine ?: "Spindle Cassette Deck"

        val openAppIntent = Intent(this, LiteMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val openAppPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pFlags)

        val prevIntent = PendingIntent.getService(this, 1, Intent(this, LitePlaybackService::class.java).apply {
            action = ACTION_PREV
        }, pFlags)

        val playPauseIntent = PendingIntent.getService(this, 2, Intent(this, LitePlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PLAY
        }, pFlags)

        val nextIntent = PendingIntent.getService(this, 3, Intent(this, LitePlaybackService::class.java).apply {
            action = ACTION_NEXT
        }, pFlags)

        val stopIntent = PendingIntent.getService(this, 4, Intent(this, LitePlaybackService::class.java).apply {
            action = ACTION_STOP
        }, pFlags)

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(title)
            .setContentText(if (track != null) "$artist • $specs" else artist)
            .setSubText(track?.formatBadge)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .addAction(R.drawable.ic_eject, "Stop", stopIntent)
            .build()
    }

    private fun buildRadioNotification(stationName: String, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, LiteMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val openAppPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pFlags)

        val playPauseIntent = PendingIntent.getService(this, 2, Intent(this, LitePlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PLAY
        }, pFlags)

        val nextIntent = PendingIntent.getService(this, 3, Intent(this, LitePlaybackService::class.java).apply {
            action = ACTION_NEXT
        }, pFlags)

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("FM / DIGI RADIO")
            .setContentText(stationName)
            .setSubText("Spindle Radio Receiver")
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(R.drawable.ic_skip_next, "Next Station", nextIntent)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Fallback for restricted legacy devices
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationWith(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spindle Lite Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background playback and hardware transport controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes safety timeout
            }
        } catch (ignored: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {}
    }

    private fun registerScreenReceiver() {
        if (!isScreenReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenReceiver, filter)
            isScreenReceiverRegistered = true
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            isNoisyReceiverRegistered = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()

        if (isScreenReceiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (ignored: Exception) {}
            isScreenReceiverRegistered = false
        }
        if (isNoisyReceiverRegistered) {
            try { unregisterReceiver(noisyReceiver) } catch (ignored: Exception) {}
            isNoisyReceiverRegistered = false
        }

        audioEngine.serviceListener = null
        audioEngine.release()
        radioEngine.release()
        analogFmEngine.release()
        sharedAudioEngine = null
        sharedRadioEngine = null
        sharedAnalogFmEngine = null
        instance = null
    }
}
