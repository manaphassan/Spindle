package com.hana.spindle.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Audiophile Lockscreen & Home Screen AppWidget for Spindle.
 * Provides instant tactile playback controls, live song telemetry, audio format indicators,
 * and downsampled album artwork while strictly conforming to low-RAM budgets.
 */
class SpindleAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.hana.spindle.widget.ACTION_UPDATE_WIDGET"
        const val ACTION_WIDGET_PLAY_PAUSE = "com.hana.spindle.widget.ACTION_WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_PREV = "com.hana.spindle.widget.ACTION_WIDGET_PREV"
        const val ACTION_WIDGET_NEXT = "com.hana.spindle.widget.ACTION_WIDGET_NEXT"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, SpindleAppWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds != null && allWidgetIds.isNotEmpty()) {
                val intent = Intent(context, SpindleAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext as? SpindleApp ?: run {
            try { SpindleApp.instance } catch (e: Exception) { null }
        } ?: return

        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                app.audioEngine.togglePlayPause()
            }
            ACTION_WIDGET_PREV -> {
                app.audioEngine.playPrevious(forcePreviousSong = true)
            }
            ACTION_WIDGET_NEXT -> {
                app.audioEngine.playNext()
            }
            ACTION_UPDATE_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, SpindleAppWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (allWidgetIds != null && allWidgetIds.isNotEmpty()) {
                    updateWidgetsInternal(context, appWidgetManager, allWidgetIds, app)
                }
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? SpindleApp ?: run {
            try { SpindleApp.instance } catch (e: Exception) { null }
        } ?: return

        updateWidgetsInternal(context, appWidgetManager, appWidgetIds, app)
    }

    private fun updateWidgetsInternal(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        app: SpindleApp
    ) {
        val pendingResult = goAsync()
        val state = app.audioEngine.playbackState.value
        val metrics = app.audioEngine.metricsTracker.metrics.value

        CoroutineScope(Dispatchers.Main).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_spindle_deck)

                    val song = state.currentSong
                    if (song != null) {
                        views.setTextViewText(R.id.widgetTvTitle, song.title)
                        views.setTextViewText(R.id.widgetTvArtist, song.artist)

                        val formatStr = if (song.fileFormat.equals("MP3", ignoreCase = true) && song.bitrateKbps > 0) {
                            "MP3 • ${song.bitrateKbps}k"
                        } else if (song.sampleRate > 0) {
                            val kHz = if (song.sampleRate % 1000 == 0) "${song.sampleRate / 1000}" else String.format(Locale.US, "%.1f", song.sampleRate / 1000f)
                            val bitStr = if (song.bitDepth > 0) "${song.bitDepth}b/" else ""
                            "${song.fileFormat} • $bitStr${kHz}k"
                        } else {
                            song.fileFormat
                        }
                        views.setTextViewText(R.id.widgetTvFormat, formatStr)
                        views.setViewVisibility(R.id.widgetTvFormat, View.VISIBLE)

                        val coverBitmap = app.imageLoader.loadCover(song.path, 160, 160)
                        if (coverBitmap != null) {
                            views.setImageViewBitmap(R.id.widgetIvCover, coverBitmap)
                        } else {
                            views.setImageViewResource(R.id.widgetIvCover, R.drawable.ic_spindle_logo)
                        }
                    } else {
                        views.setTextViewText(R.id.widgetTvTitle, "Spindle Tape Deck")
                        views.setTextViewText(R.id.widgetTvArtist, "Ready to Play")
                        views.setViewVisibility(R.id.widgetTvFormat, View.GONE)
                        views.setImageViewResource(R.id.widgetIvCover, R.drawable.ic_spindle_logo)
                    }

                    // Route badge
                    val routeBadge = when {
                        metrics.outputRoute.contains("USB", ignoreCase = true) -> "• USB DAC"
                        metrics.outputRoute.contains("3.5mm", ignoreCase = true) -> "• 3.5mm"
                        metrics.outputRoute.contains("Bluetooth", ignoreCase = true) -> "• BT"
                        else -> "• Audio"
                    }
                    views.setTextViewText(R.id.widgetTvRoute, routeBadge)

                    // Play/Pause icon
                    views.setImageViewResource(
                        R.id.widgetBtnPlayPause,
                        if (state.isPlaying) R.drawable.ic_np_pause else R.drawable.ic_np_play
                    )

                    // Progress bar
                    val progressPct = (state.progress * 100).toInt().coerceIn(0, 100)
                    views.setProgressBar(R.id.widgetPbProgress, 100, progressPct, false)

                    // Root click opens MainActivity
                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val openPendingIntent = PendingIntent.getActivity(
                        context, 0, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widgetRoot, openPendingIntent)

                    // Transport controls pending intents
                    val playPauseIntent = Intent(context, SpindleAppWidgetProvider::class.java).apply {
                        action = ACTION_WIDGET_PLAY_PAUSE
                    }
                    views.setOnClickPendingIntent(
                        R.id.widgetBtnPlayPause,
                        PendingIntent.getBroadcast(
                            context, 101, playPauseIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )

                    val prevIntent = Intent(context, SpindleAppWidgetProvider::class.java).apply {
                        action = ACTION_WIDGET_PREV
                    }
                    views.setOnClickPendingIntent(
                        R.id.widgetBtnPrev,
                        PendingIntent.getBroadcast(
                            context, 102, prevIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )

                    val nextIntent = Intent(context, SpindleAppWidgetProvider::class.java).apply {
                        action = ACTION_WIDGET_NEXT
                    }
                    views.setOnClickPendingIntent(
                        R.id.widgetBtnNext,
                        PendingIntent.getBroadcast(
                            context, 103, nextIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
