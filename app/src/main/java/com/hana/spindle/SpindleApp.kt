package com.hana.spindle

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SpindleApp : Application() {

    val database: com.hana.spindle.data.db.SpindleDatabase by lazy {
        com.hana.spindle.data.db.SpindleDatabase.getInstance(this)
    }

    val imageLoader: com.hana.spindle.data.ImageLoader by lazy {
        com.hana.spindle.data.ImageLoader(this)
    }

    val musicScanner: com.hana.spindle.data.MusicScanner by lazy {
        com.hana.spindle.data.MusicScanner(this, database.songDao())
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "spindle_playback_channel"
        lateinit var instance: SpindleApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Spindle Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for active Spindle audio playback"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
