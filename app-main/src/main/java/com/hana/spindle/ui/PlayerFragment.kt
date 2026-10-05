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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.data.db.TrackEntity
import com.hana.spindle.databinding.FragmentPlayerBinding
import com.hana.spindle.playback.AudioEngine
import com.hana.spindle.playback.RepeatMode
import com.hana.spindle.playback.ShuffleMode
import com.hana.spindle.ui.catalog.QueueBottomSheet
import com.hana.spindle.ui.eq.DualAnalogVuMeterView
import com.hana.spindle.theme.CassetteTheme
import com.hana.spindle.theme.ChassisStyle
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
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
    private var isVuMeterVisible = false
    private var isUserScrubbingVu = false
    private var currentAlbumCoverBitmap: Bitmap? = null
    private var currentFormatString: String = ""
    private var spoolFoleyStreamId = 0

    // Battery Receiver for Metal-81 Hardware LED
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 80
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                _binding?.metal81ChassisView?.batteryLevel = batteryPct
                _binding?.metal81ChassisView?.isCharging = isCharging
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

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val mainAct = activity as? MainActivity
                val isPlayerPage = mainAct?.let { it.getCurrentViewPagerItem() == 1 } ?: false
                if (isPlayerPage && handleBackPressed()) {
                    return
                }
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        // Read initial battery status
        try {
            val initialBattery = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (initialBattery != null) {
                val level = initialBattery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = initialBattery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 80
                binding.verticalDeckView.batteryLevel = batteryPct
                binding.metal81ChassisView.batteryLevel = batteryPct
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
        if (spoolFoleyStreamId != 0) {
            audioEngine.foleyEngine.stopMotorSpoolLoop(spoolFoleyStreamId)
            spoolFoleyStreamId = 0
        }
    }

    private fun setupThemeObservation() {
        viewLifecycleOwner.lifecycleScope.launch {
            themeManager.currentTheme.collectLatest { theme ->
                _binding?.let { b ->
                    val isMetal81 = theme.chassisStyle == ChassisStyle.METAL_81_RED
                    b.verticalDeckContainer.visibility = if (isMetal81 || isVuMeterVisible) View.GONE else View.VISIBLE
                    b.metal81Container.visibility = if (isMetal81 && !isVuMeterVisible) View.VISIBLE else View.GONE
                    b.vuMeterContainer.visibility = if (isVuMeterVisible) View.VISIBLE else View.GONE

                    b.verticalDeckView.theme = theme
                    b.verticalCassetteView.theme = theme
                    b.vuMeterView.isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)

                    val isVaporwave = theme.chassisStyle == ChassisStyle.VAPORWAVE_80S
                    b.tvVerticalTime.setTextColor(if (isVaporwave) Color.parseColor("#475569") else Color.parseColor("#80FFFFFF"))

                    b.metal81ChassisView.theme = theme
                    b.metal81CassetteView.theme = theme
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

            // 2. Metal-81 Red Layout
            b.metal81ChassisView.isPlaying = state.isPlaying
            b.metal81CassetteView.isPlaying = state.isPlaying
            b.metal81CassetteView.progress = state.progress

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
                b.metal81CassetteView.trackTitle = song.title
                b.metal81CassetteView.artistName = song.artist
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
                b.metal81CassetteView.trackTitle = hardwareName
                b.metal81CassetteView.artistName = ""
                b.metal81CassetteView.albumArtBitmap = null
            }

            // Sync live synchronized lyric ticker
            val activeLyric = state.currentLyrics?.lines?.getOrNull(state.activeLyricIndex)?.text ?: ""
            b.verticalDeckView.currentLyricText = activeLyric

            b.verticalDeckView.currentTimeMs = state.currentPositionMs

            val curTimeStr = formatTime(state.currentPositionMs)
            b.tvCurrentTime.text = curTimeStr
            b.tvTotalDuration.text = formatTime(state.durationMs)

            b.btnPlayPause.text = if (state.isPlaying) "PAUSE" else "PLAY"

            // 3. Dual Analog VU Meter Display
            syncVuMeterState(state)
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


        // 15Hz Stereo Ballistics Simulation for Analog VU Needle Galvanometer
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var phase = 0.0
                while (isActive) {
                    if (isVuMeterVisible && _binding != null) {
                        val isPlaying = audioEngine.playbackState.value.isPlaying
                        if (isPlaying) {
                            phase += 0.4
                            val baseLevel = 0.65f + 0.28f * kotlin.math.sin(phase).toFloat()
                            val peakJitter = (kotlin.math.sin(phase * 2.3) * 0.12f).toFloat()
                            val totalLevel = (baseLevel + peakJitter).coerceIn(0f, 1f)

                            val (leftLvl, rightLvl) = audioEngine.getStereoLevels(totalLevel)
                            _binding?.vuMeterView?.setStereoLevels(leftLvl, rightLvl)

                            val leftDb = (20f * kotlin.math.log10(leftLvl.coerceIn(0.001f, 1f))).coerceIn(-30f, 0f)
                            val rightDb = (20f * kotlin.math.log10(rightLvl.coerceIn(0.001f, 1f))).coerceIn(-30f, 0f)
                            _binding?.tvVuLeftDb?.text = String.format(Locale.US, "L: %.1f dB", leftDb)
                            _binding?.tvVuRightDb?.text = String.format(Locale.US, "R: %.1f dB", rightDb)
                        } else {
                            _binding?.vuMeterView?.setStereoLevels(0.0f, 0.0f)
                            _binding?.tvVuLeftDb?.text = "L: -∞ dB"
                            _binding?.tvVuRightDb?.text = "R: -∞ dB"
                        }
                    }
                    delay(66L)
                }
            }
        }
    }

    private fun setupControls() {
        // Vertical Deck Controls (Reference 1 & 2)
        binding.verticalDeckView.onPlayClicked = {
            if (audioEngine.playbackState.value.currentTrack == null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val app = requireActivity().application as SpindleApp
                    val tracks = app.database.trackDao().getAllTracks().firstOrNull()
                    if (!tracks.isNullOrEmpty()) {
                        audioEngine.playQueue(tracks, 0)
                    } else {
                        audioEngine.togglePlayPause()
                    }
                }
            } else {
                audioEngine.togglePlayPause()
            }
        }
        binding.verticalDeckView.onPrevClicked = { audioEngine.playPrevious(forcePreviousSong = false) }
        binding.verticalDeckView.onNextClicked = { audioEngine.playNext() }
        binding.verticalDeckView.onNextAlbumClicked = { audioEngine.playNextAlbum() }
        binding.verticalDeckView.onPrevAlbumClicked = { audioEngine.playPreviousAlbum() }
        binding.verticalDeckView.onHoldSeekForward = { audioEngine.fastForward(com.hana.spindle.core.SpindleGlossary.SEEK_STEP_HOLD_MS) }
        binding.verticalDeckView.onHoldSeekRewind = { audioEngine.rewind(com.hana.spindle.core.SpindleGlossary.SEEK_STEP_HOLD_MS) }

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
        binding.verticalDeckView.onVuMeterClicked = {
            openVuMeter(animate = true)
        }
        binding.verticalDeckView.onQueueClicked = {
            openQueueBottomSheet()
        }
        binding.jCardLinerView.onFlipBackClicked = {
            closeJCardLiner(animate = true)
        }
        binding.jCardLinerView.onManageQueueClicked = {
            openQueueBottomSheet()
        }
        binding.jCardLinerView.onTrackSelected = { trackIndex ->
            audioEngine.playQueueIndex(trackIndex)
            closeJCardLiner(animate = true)
        }

        // Automatic Cassette 3D Flip on Auto-Reverse Side Transition
        audioEngine.onTapeSideFlip = { isSideA ->
            if (this.isSideA != isSideA) {
                flipCassette(binding.metal81CassetteView)
            }
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
            binding.metal81CassetteView.trackTitle = hardwareName
            binding.metal81CassetteView.artistName = ""
            android.widget.Toast.makeText(requireContext(), "Cassette Ejected & Stopped", android.widget.Toast.LENGTH_SHORT).show()
        }
        binding.verticalDeckView.onDoubleTapChassis = {
            (activity as? MainActivity)?.enterAmbientSleep()
        }
        binding.verticalDeckView.onTitleClicked = {
            (activity as? MainActivity)?.navigateToCatalog(openNowPlaying = true)
        }
        binding.verticalDeckView.onLyricsClicked = {
            DialogLyricsSheet().show(parentFragmentManager, "DialogLyricsSheet")
        }
        binding.verticalDeckView.onTapeTypeClicked = { type ->
            audioEngine.setTapeFormulation(type)
            audioEngine.foleyEngine.playSwitchSnap()
            android.widget.Toast.makeText(requireContext(), "TAPE: ${type.label}", android.widget.Toast.LENGTH_SHORT).show()
        }
        binding.verticalDeckView.onEqBadgeLongClicked = {
            val fxController = (requireActivity().application as SpindleApp).audioEngine.audioFxController
            val dialog = DialogUserEqPresets(fxController, openSaveDirectly = false) {
                syncState()
            }
            dialog.show(parentFragmentManager, "DialogUserEqPresets")
        }
        binding.verticalDeckView.onDolbyClicked = { mode ->
            audioEngine.setDolbyMode(mode)
            audioEngine.foleyEngine.playSwitchSnap()
            android.widget.Toast.makeText(requireContext(), "DOLBY NR: ${mode.label}", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Metal-81 Red Controls
        binding.metal81ChassisView.onPlayClicked = { audioEngine.togglePlayPause() }
        binding.metal81ChassisView.onStopClicked = { audioEngine.pause() }
        binding.metal81ChassisView.onEjectClicked = {
            (activity as? MainActivity)?.navigateToCatalog()
        }
        binding.btnPlayPause.setOnClickListener { audioEngine.togglePlayPause() }
        binding.btnPrev.setOnClickListener { audioEngine.playPrevious(forcePreviousSong = false) }
        binding.btnNext.setOnClickListener { audioEngine.playNext() }
        binding.metal81ChassisView.onPrevClicked = { audioEngine.playPrevious(forcePreviousSong = false) }
        binding.metal81ChassisView.onNextClicked = { audioEngine.playNext() }
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
            binding.metal81CassetteView.trackTitle = hardwareName
            binding.metal81CassetteView.artistName = ""
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

        setupVuMeterControls()
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

    private fun setupVuMeterControls() {
        val b = _binding ?: return

        // Return to Cassette Deck
        b.btnVuBackToDeck.setOnClickListener {
            audioEngine.foleyEngine.playReleaseClick()
            closeVuMeter(animate = true)
        }

        // Backlight tone cycle
        b.btnVuBacklightTone.setOnClickListener {
            cycleVuBacklight()
        }

        // Observe backlight changes from direct touch on meter face
        b.vuMeterView.onBacklightChanged = { tone ->
            updateVuBacklightButton(tone)
            val prefs = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("pref_vu_backlight_tone", tone.name).apply()
        }

        // Restore saved backlight preference
        val prefs = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val savedToneName = prefs.getString("pref_vu_backlight_tone", DualAnalogVuMeterView.BacklightTone.WARM_TUNGSTEN.name)
        val tone = try {
            DualAnalogVuMeterView.BacklightTone.valueOf(savedToneName ?: "")
        } catch (_: Exception) {
            DualAnalogVuMeterView.BacklightTone.WARM_TUNGSTEN
        }
        b.vuMeterView.currentBacklight = tone
        updateVuBacklightButton(tone)

        // Transport controls
        b.btnVuPlayPause.setOnClickListener {
            audioEngine.foleyEngine.playTilePress()
            audioEngine.togglePlayPause()
        }

        b.btnVuPrev.setOnClickListener {
            audioEngine.foleyEngine.playTilePress()
            audioEngine.playPrevious(forcePreviousSong = false)
        }

        b.btnVuNext.setOnClickListener {
            audioEngine.foleyEngine.playTilePress()
            audioEngine.playNext()
        }

        b.btnVuRepeat.setOnClickListener {
            audioEngine.foleyEngine.playMetroTick()
            audioEngine.toggleRepeat()
        }

        b.btnVuShuffle.setOnClickListener {
            audioEngine.foleyEngine.playMetroTick()
            audioEngine.toggleShuffle()
        }

        b.btnVuQueue.setOnClickListener {
            audioEngine.foleyEngine.playTilePress()
            openQueueBottomSheet()
        }

        // Scrubbable seek bar
        b.seekVuProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val total = audioEngine.playbackState.value.durationMs
                    if (total > 0) {
                        val seekMs = (total * (progress / 1000f)).toLong()
                        b.tvVuCurrentTime.text = formatTime(seekMs)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbingVu = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbingVu = false
                val progress = seekBar?.progress ?: 0
                val total = audioEngine.playbackState.value.durationMs
                if (total > 0) {
                    val seekMs = (total * (progress / 1000f)).toLong()
                    audioEngine.seekTo(seekMs)
                    audioEngine.foleyEngine.playMetroTick()
                }
            }
        })
    }

    private fun cycleVuBacklight() {
        val b = _binding ?: return
        b.vuMeterView.cycleBacklight()
        val tone = b.vuMeterView.currentBacklight
        updateVuBacklightButton(tone)
        val prefs = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("pref_vu_backlight_tone", tone.name).apply()
        audioEngine.foleyEngine.playMetroTick()
    }

    private fun updateVuBacklightButton(tone: DualAnalogVuMeterView.BacklightTone) {
        _binding?.btnVuBacklightTone?.text = "${tone.nameLabel} 💡"
    }

    fun openVuMeter(animate: Boolean) {
        if (isVuMeterVisible) return
        if (isJCardVisible) {
            closeJCardLiner(animate = false)
        }
        isVuMeterVisible = true
        audioEngine.foleyEngine.playSwitchSnap()

        val deckContainer = binding.verticalDeckContainer
        val vuContainer = binding.vuMeterContainer

        syncVuMeterState(audioEngine.playbackState.value)

        if (!animate) {
            deckContainer.visibility = View.GONE
            deckContainer.rotationY = 0f
            vuContainer.visibility = View.VISIBLE
            vuContainer.rotationY = 0f
            return
        }

        val distance = 8000f * resources.displayMetrics.density
        deckContainer.cameraDistance = distance
        vuContainer.cameraDistance = distance

        vuContainer.visibility = View.VISIBLE
        vuContainer.rotationY = -90f
        vuContainer.alpha = 0f

        deckContainer.animate()
            .rotationY(90f)
            .setDuration(240L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                deckContainer.visibility = View.GONE
                deckContainer.rotationY = 0f
                vuContainer.alpha = 1f
                vuContainer.animate()
                    .rotationY(0f)
                    .setDuration(240L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    fun closeVuMeter(animate: Boolean) {
        if (!isVuMeterVisible) return
        isVuMeterVisible = false
        audioEngine.foleyEngine.playSwitchSnap()

        val deckContainer = binding.verticalDeckContainer
        val vuContainer = binding.vuMeterContainer

        if (!animate) {
            vuContainer.visibility = View.GONE
            vuContainer.rotationY = 0f
            deckContainer.visibility = View.VISIBLE
            deckContainer.rotationY = 0f
            return
        }

        val distance = 8000f * resources.displayMetrics.density
        deckContainer.cameraDistance = distance
        vuContainer.cameraDistance = distance

        deckContainer.visibility = View.VISIBLE
        deckContainer.rotationY = 90f
        deckContainer.alpha = 0f

        vuContainer.animate()
            .rotationY(-90f)
            .setDuration(240L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                vuContainer.visibility = View.GONE
                vuContainer.rotationY = 0f
                deckContainer.alpha = 1f
                deckContainer.animate()
                    .rotationY(0f)
                    .setDuration(240L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    fun handleBackPressed(): Boolean {
        if (isJCardVisible) {
            closeJCardLiner(animate = true)
            return true
        }
        if (isVuMeterVisible) {
            closeVuMeter(animate = true)
            return true
        }
        return false
    }

    private fun syncVuMeterState(state: com.hana.spindle.playback.PlaybackState) {
        _binding?.let { b ->
            state.currentSong?.let { song ->
                b.tvVuTrackTitle.text = song.title
                b.tvVuTrackTitle.isSelected = true
                b.tvVuTrackArtist.text = "${song.artist} • ${song.album}"
                b.tvVuFormatBadge.text = currentFormatString.ifEmpty { "HI-RES AUDIO" }
            } ?: run {
                val hardwareName = com.hana.spindle.util.DeviceUtils.getHardwareDeviceName()
                b.tvVuTrackTitle.text = hardwareName
                b.tvVuTrackTitle.isSelected = true
                b.tvVuTrackArtist.text = "Spindle Direct ALSA Output"
                b.tvVuFormatBadge.text = "DIRECT DAC"
            }

            val bias = audioEngine.audioFxController.currentTapeFormulation.tag
            val dolby = audioEngine.audioFxController.currentDolbyMode.badgeText
            b.tvVuDspMode.text = "BIAS: $bias • NR: $dolby"

            b.tvVuCurrentTime.text = formatTime(state.currentPositionMs)
            b.tvVuTotalDuration.text = formatTime(state.durationMs)

            if (!isUserScrubbingVu) {
                val progressInt = if (state.durationMs > 0) {
                    ((state.currentPositionMs * 1000) / state.durationMs).toInt()
                } else 0
                b.seekVuProgress.progress = progressInt.coerceIn(0, 1000)
            }

            b.btnVuPlayPause.setImageResource(if (state.isPlaying) R.drawable.ic_np_pause else R.drawable.ic_np_play)

            when (state.repeatMode) {
                RepeatMode.OFF -> {
                    b.btnVuRepeat.setColorFilter(Color.parseColor("#71717A"))
                }
                RepeatMode.ALL -> {
                    b.btnVuRepeat.setColorFilter(Color.parseColor("#F97316"))
                }
                RepeatMode.ONE -> {
                    b.btnVuRepeat.setColorFilter(Color.parseColor("#FDE68A"))
                }
            }

            if (state.shuffleMode != ShuffleMode.OFF) {
                b.btnVuShuffle.setColorFilter(Color.parseColor("#F97316"))
            } else {
                b.btnVuShuffle.setColorFilter(Color.parseColor("#71717A"))
            }
        }
    }

    private fun setupCassetteFlip() {
        binding.verticalCassetteView.setOnClickListener {
            if (audioEngine.playbackState.value.currentSong != null) {
                (activity as? MainActivity)?.navigateToCatalog(openNowPlaying = true)
            } else {
                flipCassette(binding.verticalCassetteView)
            }
        }
        binding.metal81CassetteView.setOnClickListener {
            if (audioEngine.playbackState.value.currentSong != null) {
                (activity as? MainActivity)?.navigateToCatalog(openNowPlaying = true)
            } else {
                flipCassette(binding.metal81CassetteView)
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

    fun openQueueBottomSheet() {
        QueueBottomSheet().show(childFragmentManager, "QueueBottomSheet")
    }

    override fun onDestroyView() {
        if (spoolFoleyStreamId != 0) {
            audioEngine.foleyEngine.stopMotorSpoolLoop(spoolFoleyStreamId)
            spoolFoleyStreamId = 0
        }
        super.onDestroyView()
        _binding = null
    }
}
