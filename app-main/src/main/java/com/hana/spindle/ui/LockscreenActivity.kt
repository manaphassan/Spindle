package com.hana.spindle.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.ActivityLockscreenBinding
import com.hana.spindle.playback.AudioEngine
import com.hana.spindle.playback.PlaybackState
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Dedicated Full-Screen Retro Cassette Lockscreen Activity.
 * Runs directly over the Android Keyguard when music is playing,
 * turning the DAP into an authentic standalone physical Walkman.
 */
class LockscreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockscreenBinding
    private lateinit var audioEngine: AudioEngine
    private lateinit var themeManager: ThemeManager

    private var touchStartY = 0f
    private var isDraggingUnlock = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 80
                binding.lockDeckView.batteryLevel = batteryPct
                binding.lockDeckView.isLowBattery = (batteryPct <= 20)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Window to display directly over keyguard and wake screen
        configureKeyguardFlags()

        binding = ActivityLockscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyImmersiveMode()

        val app = application as SpindleApp
        audioEngine = app.audioEngine
        themeManager = app.themeManager

        setupDeckTransport()
        setupUnlockGestures()
        setupAudioObservation()
        setupThemeObservation()
    }

    private fun configureKeyguardFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        syncState(audioEngine.playbackState.value)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }

    private fun setupDeckTransport() {
        binding.lockDeckView.apply {
            onPlayClicked = { audioEngine.togglePlayPause() }
            onNextClicked = { audioEngine.playNext() }
            onPrevClicked = { audioEngine.playPrevious(forcePreviousSong = true) }
            onNextAlbumClicked = { audioEngine.playNextAlbum() }
            onPrevAlbumClicked = { audioEngine.playPreviousAlbum() }
            onHoldSeekForward = { audioEngine.fastForward(2500L) }
            onHoldSeekRewind = { audioEngine.rewind(2500L) }
            onSeek = { progress ->
                val total = audioEngine.playbackState.value.durationMs
                if (total > 0) {
                    audioEngine.seekTo((total * progress).toLong())
                }
            }
            onEjectClicked = { unlockDevice() }
            onTitleClicked = { unlockDevice() }
        }
    }

    private fun setupUnlockGestures() {
        // Tapping the unlock bar
        binding.layoutUnlockBar.setOnClickListener {
            unlockDevice()
        }

        // Swipe up gesture detection on root view
        binding.lockscreenRoot.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.rawY
                    isDraggingUnlock = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = touchStartY - event.rawY
                    if (deltaY > 60f) {
                        isDraggingUnlock = true
                        // Parallax upward slide preview
                        binding.lockscreenRoot.translationY = -deltaY * 0.4f
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaY = touchStartY - event.rawY
                    if (isDraggingUnlock && deltaY > 120f) {
                        unlockDevice()
                    } else {
                        binding.lockscreenRoot.animate()
                            .translationY(0f)
                            .setDuration(200L)
                            .start()
                    }
                    isDraggingUnlock = false
                    true
                }
                else -> false
            }
        }
    }

    private fun unlockDevice() {
        binding.lockscreenRoot.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km?.requestDismissKeyguard(this, null)
        }
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        unlockDevice()
    }

    private fun setupAudioObservation() {
        lifecycleScope.launch {
            audioEngine.playbackState.collectLatest { state ->
                syncState(state)
            }
        }
        lifecycleScope.launch {
            audioEngine.metricsTracker.metrics.collectLatest { metrics ->
                binding.lockDeckView.outputRoute = metrics.outputRoute
                binding.lockDeckView.isBitPerfect = metrics.isBitPerfect
                binding.lockDeckView.bluetoothDeviceName = metrics.bluetoothDeviceName
                binding.lockDeckView.bluetoothBatteryPct = metrics.bluetoothBatteryPct
                binding.lockDeckView.isBluetoothConnected = metrics.isBluetoothConnected
                binding.lockDeckView.bluetoothConnectionStatus = metrics.bluetoothConnectionStatus
            }
        }
    }

    private fun syncState(state: PlaybackState) {
        val song = state.currentSong
        val app = application as SpindleApp
        binding.lockDeckView.apply {
            if (song != null) {
                isSongLoaded = true
                trackTitle = song.title
                artistName = song.artist
                durationMs = if (song.durationMs > 0) song.durationMs else state.durationMs
                val formatStr = if (song.fileFormat.equals("MP3", ignoreCase = true) && song.bitrateKbps > 0) {
                    "MP3 • ${song.bitrateKbps}kbps"
                } else if (song.sampleRate > 0) {
                    val kHz = if (song.sampleRate % 1000 == 0) "${song.sampleRate / 1000}" else String.format(java.util.Locale.US, "%.1f", song.sampleRate / 1000f)
                    val bitStr = if (song.bitDepth > 0) "${song.bitDepth}-bit / " else ""
                    "${song.fileFormat} • $bitStr${kHz}kHz"
                } else {
                    song.fileFormat
                }
                audioFormat = formatStr
                progress = if (state.durationMs > 0) state.currentPositionMs.toFloat() / state.durationMs else 0f
                isPlaying = state.isPlaying

                lifecycleScope.launch {
                    val accentColor = app.imageLoader.extractAccentColor(song.path)
                    binding.lockDeckView.setCoverAccentColor(accentColor)
                }
            } else {
                isSongLoaded = false
                trackTitle = "Spindle DAP"
                artistName = "No Cassette Loaded"
                durationMs = 0L
                audioFormat = "Direct ALSA"
                progress = 0f
                isPlaying = false
                albumArtBitmap = null
            }
        }
    }

    private fun setupThemeObservation() {
        lifecycleScope.launch {
            themeManager.currentTheme.collectLatest { theme ->
                binding.lockDeckView.theme = theme
                binding.lockscreenRoot.setBackgroundColor(theme.chassisColor)
            }
        }
    }
}
