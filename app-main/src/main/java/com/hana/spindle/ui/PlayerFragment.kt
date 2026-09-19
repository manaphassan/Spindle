package com.hana.spindle.ui

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hana.spindle.SpindleApp
import com.hana.spindle.data.db.SongEntity
import com.hana.spindle.databinding.FragmentPlayerBinding
import com.hana.spindle.playback.AudioEngine
import com.hana.spindle.playback.RepeatMode
import com.hana.spindle.playback.ShuffleMode
import com.hana.spindle.theme.CassetteTheme
import com.hana.spindle.theme.ChassisStyle
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var themeManager: ThemeManager
    private lateinit var audioEngine: AudioEngine
    private var isSideA = true
    private var isJCardVisible = false
    private var currentAlbumCoverBitmap: Bitmap? = null
    private var currentFormatString: String = ""
    private var spoolFoleyStreamId = 0

    // Battery Receiver for WM-2 Hardware LED
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 80
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                _binding?.wm2ChassisView?.batteryLevel = batteryPct
                _binding?.wm2ChassisView?.isCharging = isCharging
                _binding?.verticalDeckView?.batteryLevel = batteryPct
                _binding?.verticalDeckView?.isLowBattery = (batteryPct <= 20)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as SpindleApp
        themeManager = app.themeManager
        audioEngine = app.audioEngine

        setupThemeObservation()
        setupAudioPlaybackObservation(app)
        setupControls()
        setupCassetteFlip()

        // Read initial battery status
        try {
            val initialBattery = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (initialBattery != null) {
                val level = initialBattery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = initialBattery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 80
                binding.verticalDeckView.batteryLevel = batteryPct
                binding.wm2ChassisView.batteryLevel = batteryPct
            }
        } catch (_: Exception) {}

        // Immediate state sync upon view creation
        syncState()
    }

    fun syncState() {
        if (_binding != null) {
            syncPlaybackState(audioEngine.playbackState.value)
        }
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        syncState()
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(batteryReceiver)
    }

    private fun setupThemeObservation() {
        viewLifecycleOwner.lifecycleScope.launch {
            themeManager.currentTheme.collectLatest { theme ->
                _binding?.let { b ->
                    val isWm2 = theme.chassisStyle == ChassisStyle.WM2_RED
                    b.verticalDeckContainer.visibility = if (isWm2) View.GONE else View.VISIBLE
                    b.wm2Container.visibility = if (isWm2) View.VISIBLE else View.GONE

                    b.verticalDeckView.theme = theme
                    b.verticalCassetteView.theme = theme

                    val isVaporwave = theme.chassisStyle == ChassisStyle.VAPORWAVE_80S
                    b.tvVerticalTime.setTextColor(if (isVaporwave) Color.parseColor("#475569") else Color.parseColor("#80FFFFFF"))

                    b.wm2ChassisView.theme = theme
                    b.wm2CassetteView.theme = theme
                    b.root.setBackgroundColor(theme.chassisColor)
                }
            }
        }
    }

    private fun syncPlaybackState(state: com.hana.spindle.playback.PlaybackState) {
        _binding?.let { b ->
            val app = (activity?.application as? SpindleApp) ?: return

            // 1. Vertical Cassette Deck
            b.verticalDeckView.isPlaying = state.isPlaying
            b.verticalDeckView.progress = state.progress
            b.verticalDeckView.tapeFormulation = audioEngine.audioFxController.currentTapeFormulation
            b.verticalDeckView.dolbyMode = audioEngine.audioFxController.currentDolbyMode
            b.verticalCassetteView.isPlaying = state.isPlaying
            b.verticalCassetteView.progress = state.progress

            // 2. WM-2 Red Layout
            b.wm2ChassisView.isPlaying = state.isPlaying
            b.wm2CassetteView.isPlaying = state.isPlaying
            b.wm2CassetteView.progress = state.progress

            state.currentSong?.let { song ->
                // Deck values
                b.verticalDeckView.isSongLoaded = true
                b.verticalDeckView.trackTitle = song.title
                b.verticalDeckView.artistName = song.artist
                b.verticalDeckView.durationMs = song.durationMs
                val formatStr = if (song.fileFormat.equals("MP3", ignoreCase = true) && song.bitrateKbps > 0) {
                    "MP3 • ${song.bitrateKbps}kbps"
                } else if (song.sampleRate > 0) {
                    val kHz = if (song.sampleRate % 1000 == 0) "${song.sampleRate / 1000}" else String.format(Locale.US, "%.1f", song.sampleRate / 1000f)
                    val bitStr = if (song.bitDepth > 0) "${song.bitDepth}-bit / " else ""
                    "${song.fileFormat} • $bitStr${kHz}kHz"
                } else {
                    song.fileFormat
                }
                currentFormatString = formatStr
                b.verticalDeckView.audioFormat = formatStr

                // Dynamic cover art bitmap & accent color
                viewLifecycleOwner.lifecycleScope.launch {
                    val coverBitmap = app.imageLoader.loadCover(song.path, 160, 160)
                    currentAlbumCoverBitmap = coverBitmap
                    _binding?.verticalDeckView?.albumArtBitmap = coverBitmap
                    val accentColor = app.imageLoader.extractAccentColor(song.path)
                    _binding?.verticalDeckView?.setCoverAccentColor(accentColor)

                    if (isJCardVisible) {
                        _binding?.jCardLinerView?.setQueueData(
                            queue = audioEngine.currentQueueFlow.value,
                            activeIndex = audioEngine.currentQueueIndexFlow.value,
                            albumCover = coverBitmap,
                            audioFormat = currentFormatString,
                            theme = themeManager.currentTheme.value
                        )
                    }
                }

                b.verticalCassetteView.trackTitle = song.title
                b.verticalCassetteView.artistName = song.artist
                b.wm2CassetteView.trackTitle = song.title
                b.wm2CassetteView.artistName = song.artist
            } ?: run {
                val hardwareName = com.hana.spindle.util.DeviceUtils.getHardwareDeviceName()
                b.verticalDeckView.isSongLoaded = false
                b.verticalDeckView.trackTitle = hardwareName
                b.verticalDeckView.deviceName = hardwareName
                b.verticalDeckView.artistName = ""
                b.verticalDeckView.durationMs = 0L
                b.verticalDeckView.audioFormat = ""
                b.verticalDeckView.albumArtBitmap = null
                b.verticalDeckView.androidVersionText = com.hana.spindle.util.DeviceUtils.getAndroidVersionString()
                b.verticalCassetteView.trackTitle = hardwareName
                b.verticalCassetteView.artistName = ""
                b.verticalCassetteView.albumArtBitmap = null
                b.wm2CassetteView.trackTitle = hardwareName
                b.wm2CassetteView.artistName = ""
                b.wm2CassetteView.albumArtBitmap = null
            }

            // Sync live synchronized lyric ticker
            val activeLyric = state.currentLyrics?.lines?.getOrNull(state.activeLyricIndex)?.text ?: ""
            b.verticalDeckView.currentLyricText = activeLyric

            b.verticalDeckView.currentTimeMs = state.currentPositionMs

            val curTimeStr = formatTime(state.currentPositionMs)
            b.tvCurrentTime.text = curTimeStr
            b.tvTotalDuration.text = formatTime(state.durationMs)

            b.btnPlayPause.text = if (state.isPlaying) "PAUSE" else "PLAY"
        }
    }

    private fun setupAudioPlaybackObservation(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            audioEngine.playbackState.collectLatest { state ->
                syncPlaybackState(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            app.audioEngine.metricsTracker.metrics.collectLatest { metrics ->
                _binding?.let { b ->
                    b.verticalDeckView.outputRoute = metrics.outputRoute
                    b.verticalDeckView.isBitPerfect = metrics.isBitPerfect
                    b.verticalDeckView.bluetoothDeviceName = metrics.bluetoothDeviceName
                    b.verticalDeckView.bluetoothBatteryPct = metrics.bluetoothBatteryPct
                    b.verticalDeckView.isBluetoothConnected = metrics.isBluetoothConnected
                    b.verticalDeckView.bluetoothConnectionStatus = metrics.bluetoothConnectionStatus
                }
            }
        }
    }

    private fun setupControls() {
        // Vertical Deck Controls (Reference 1 & 2)
        binding.verticalDeckView.onPlayClicked = {
            if (audioEngine.playbackState.value.currentSong == null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val app = requireActivity().application as SpindleApp
                    val songs = app.database.songDao().getAllSongs().firstOrNull()
                    if (!songs.isNullOrEmpty()) {
                        audioEngine.playQueue(songs, 0)
                    } else {
                        audioEngine.togglePlayPause()
                    }
                }
            } else {
                audioEngine.togglePlayPause()
            }
        }
        binding.verticalDeckView.onPrevClicked = { audioEngine.playPrevious(forcePreviousSong = true) }
        binding.verticalDeckView.onNextClicked = { audioEngine.playNext() }
        binding.verticalDeckView.onNextAlbumClicked = { audioEngine.playNextAlbum() }
        binding.verticalDeckView.onPrevAlbumClicked = { audioEngine.playPreviousAlbum() }
        binding.verticalDeckView.onHoldSeekForward = { audioEngine.fastForward(2500L) }
        binding.verticalDeckView.onHoldSeekRewind = { audioEngine.rewind(2500L) }

        // Continuous high-speed motor gear spool foley with dynamic pitch & auto-stop
        binding.verticalDeckView.onHoldSeekStart = { isForward ->
            spoolFoleyStreamId = audioEngine.foleyEngine.startMotorSpoolLoop(0.95f)
        }
        binding.verticalDeckView.onHoldSeekProgress = { pitch ->
            audioEngine.foleyEngine.setMotorSpoolPitch(spoolFoleyStreamId, pitch)
        }
        binding.verticalDeckView.onHoldSeekStop = {
            audioEngine.foleyEngine.stopMotorSpoolLoop(spoolFoleyStreamId)
            audioEngine.foleyEngine.playReleaseClick()
        }
        binding.verticalDeckView.onLeaderAutoStop = {
            audioEngine.foleyEngine.stopMotorSpoolLoop(spoolFoleyStreamId)
            audioEngine.foleyEngine.playAutoStopClack()
            android.widget.Toast.makeText(requireContext(), "AUTO-STOP • TAPE LEADER", android.widget.Toast.LENGTH_SHORT).show()
        }

        // 3D Card Flip to J-Card Liner Notes
        binding.verticalDeckView.onJCardClicked = {
            openJCardLiner(animate = true)
        }
        binding.jCardLinerView.onFlipBackClicked = {
            closeJCardLiner(animate = true)
        }
        binding.jCardLinerView.onTrackSelected = { trackIndex ->
            audioEngine.playQueueIndex(trackIndex)
            closeJCardLiner(animate = true)
        }

        binding.verticalDeckView.onSeek = { progress ->
            val total = audioEngine.playbackState.value.durationMs
            if (total > 0) {
                audioEngine.seekTo((total * progress).toLong())
            }
        }
        binding.verticalDeckView.onEjectClicked = {
            // Single-press Eject: ONLY open music catalogue and keep playing song, do NOT stop it
            (activity as? MainActivity)?.navigateToCatalog()
        }
        binding.verticalDeckView.onEjectLongClicked = {
            // Hold/Long-press Eject: stop song, eject cassette, and reset title to default hardware name
            audioEngine.ejectCassette()
            val hardwareName = com.hana.spindle.util.DeviceUtils.getHardwareDeviceName()
            binding.verticalDeckView.isSongLoaded = false
            binding.verticalDeckView.trackTitle = hardwareName
            binding.verticalDeckView.deviceName = hardwareName
            binding.verticalDeckView.artistName = ""
            binding.verticalDeckView.durationMs = 0L
            binding.verticalDeckView.audioFormat = ""
            binding.verticalCassetteView.trackTitle = hardwareName
            binding.verticalCassetteView.artistName = ""
            binding.wm2CassetteView.trackTitle = hardwareName
            binding.wm2CassetteView.artistName = ""
            android.widget.Toast.makeText(requireContext(), "Cassette Ejected & Stopped", android.widget.Toast.LENGTH_SHORT).show()
        }
        binding.verticalDeckView.onDoubleTapChassis = {
            (activity as? MainActivity)?.enterAmbientSleep()
        }
        binding.verticalDeckView.onTitleClicked = {
            (activity as? MainActivity)?.navigateToCatalog(openNowPlaying = true)
        }
        binding.verticalDeckView.onTapeTypeClicked = { type ->
            audioEngine.setTapeFormulation(type)
            audioEngine.foleyEngine.playSwitchSnap()
            android.widget.Toast.makeText(requireContext(), "TAPE: ${type.label}", android.widget.Toast.LENGTH_SHORT).show()
        }
        binding.verticalDeckView.onDolbyClicked = { mode ->
            audioEngine.setDolbyMode(mode)
            audioEngine.foleyEngine.playSwitchSnap()
            android.widget.Toast.makeText(requireContext(), "DOLBY NR: ${mode.label}", android.widget.Toast.LENGTH_SHORT).show()
        }

        // WM-2 Red Controls
        binding.wm2ChassisView.onPlayClicked = { audioEngine.togglePlayPause() }
        binding.wm2ChassisView.onStopClicked = { audioEngine.pause() }
        binding.wm2ChassisView.onEjectClicked = {
            (activity as? MainActivity)?.navigateToCatalog()
        }
        binding.btnPlayPause.setOnClickListener { audioEngine.togglePlayPause() }
        binding.btnPrev.setOnClickListener { audioEngine.playPrevious() }
        binding.btnNext.setOnClickListener { audioEngine.playNext() }
        binding.wm2ChassisView.onPrevClicked = { audioEngine.playPrevious() }
        binding.wm2ChassisView.onNextClicked = { audioEngine.playNext() }
        binding.btnEject.setOnClickListener {
            // Single-press: only open music catalogue, don't stop music
            (activity as? MainActivity)?.navigateToCatalog()
        }
        binding.btnEject.setOnLongClickListener {
            audioEngine.ejectCassette()
            val hardwareName = com.hana.spindle.util.DeviceUtils.getHardwareDeviceName()
            binding.verticalDeckView.isSongLoaded = false
            binding.verticalDeckView.trackTitle = hardwareName
            binding.verticalDeckView.deviceName = hardwareName
            binding.verticalDeckView.artistName = ""
            binding.verticalDeckView.durationMs = 0L
            binding.verticalDeckView.audioFormat = ""
            binding.verticalCassetteView.trackTitle = hardwareName
            binding.verticalCassetteView.artistName = ""
            binding.wm2CassetteView.trackTitle = hardwareName
            binding.wm2CassetteView.artistName = ""
            android.widget.Toast.makeText(requireContext(), "Cassette Ejected & Stopped", android.widget.Toast.LENGTH_SHORT).show()
            true
        }

        // Background double tap to sleep gesture
        val rootGestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                (activity as? MainActivity)?.enterAmbientSleep()
                return true
            }
            override fun onDown(e: MotionEvent): Boolean = true
        })
        binding.root.setOnTouchListener { _, event ->
            rootGestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun openJCardLiner(animate: Boolean) {
        if (isJCardVisible) return
        isJCardVisible = true
        audioEngine.foleyEngine.playSwitchSnap()

        val deck = binding.verticalDeckView
        val jcard = binding.jCardLinerView

        val queue = audioEngine.currentQueueFlow.value
        val activeIdx = audioEngine.currentQueueIndexFlow.value
        val cover = currentAlbumCoverBitmap
        val format = currentFormatString
        val theme = themeManager.currentTheme.value
        jcard.setQueueData(queue, activeIdx, cover, format, theme)

        if (!animate) {
            deck.visibility = View.GONE
            deck.rotationY = 0f
            jcard.visibility = View.VISIBLE
            jcard.rotationY = 0f
            return
        }

        val distance = 8000f * resources.displayMetrics.density
        deck.cameraDistance = distance
        jcard.cameraDistance = distance

        jcard.visibility = View.VISIBLE
        jcard.rotationY = -90f
        jcard.alpha = 0f

        deck.animate()
            .rotationY(90f)
            .setDuration(240L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                deck.visibility = View.GONE
                deck.rotationY = 0f
                jcard.alpha = 1f
                jcard.animate()
                    .rotationY(0f)
                    .setDuration(240L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun closeJCardLiner(animate: Boolean) {
        if (!isJCardVisible) return
        isJCardVisible = false
        audioEngine.foleyEngine.playSwitchSnap()

        val deck = binding.verticalDeckView
        val jcard = binding.jCardLinerView

        if (!animate) {
            jcard.visibility = View.GONE
            jcard.rotationY = 0f
            deck.visibility = View.VISIBLE
            deck.rotationY = 0f
            return
        }

        val distance = 8000f * resources.displayMetrics.density
        deck.cameraDistance = distance
        jcard.cameraDistance = distance

        deck.visibility = View.VISIBLE
        deck.rotationY = 90f
        deck.alpha = 0f

        jcard.animate()
            .rotationY(-90f)
            .setDuration(240L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                jcard.visibility = View.GONE
                jcard.rotationY = 0f
                deck.alpha = 1f
                deck.animate()
                    .rotationY(0f)
                    .setDuration(240L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun setupCassetteFlip() {
        binding.verticalCassetteView.setOnClickListener {
            if (audioEngine.playbackState.value.currentSong != null) {
                (activity as? MainActivity)?.navigateToCatalog(openNowPlaying = true)
            } else {
                flipCassette(binding.verticalCassetteView)
            }
        }
        binding.wm2CassetteView.setOnClickListener {
            if (audioEngine.playbackState.value.currentSong != null) {
                (activity as? MainActivity)?.navigateToCatalog(openNowPlaying = true)
            } else {
                flipCassette(binding.wm2CassetteView)
            }
        }
    }

    private fun flipCassette(view: com.hana.spindle.ui.cassette.CassetteView) {
        val startAngle = if (isSideA) 0f else 180f
        val endAngle = if (isSideA) 180f else 360f

        ObjectAnimator.ofFloat(view, "flipRotationY", startAngle, endAngle).apply {
            duration = 600L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                if (value >= 90f && isSideA) {
                    isSideA = false
                    view.isSideA = false
                } else if (value >= 270f && !isSideA) {
                    isSideA = true
                    view.isSideA = true
                }
            }
            start()
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
