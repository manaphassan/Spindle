package com.hana.spindle.lite.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.hana.spindle.lite.R
import com.hana.spindle.lite.audio.LiteAudioEngine
import com.hana.spindle.lite.audio.LiteRadioEngine
import com.hana.spindle.lite.audio.LiteRadioStation
import com.hana.spindle.lite.audio.PlaybackListener
import com.hana.spindle.lite.audio.PlaybackState
import com.hana.spindle.lite.databinding.ActivityLiteMainBinding
import com.hana.spindle.lite.databinding.PageLiteDrawerBinding
import com.hana.spindle.lite.databinding.PageLitePlayerBinding
import com.hana.spindle.lite.databinding.PageLiteRadioBinding
import com.hana.spindle.lite.db.LiteDbHelper
import com.hana.spindle.lite.db.LiteMediaScanner
import com.hana.spindle.lite.db.Track
import com.hana.spindle.lite.launcher.LiteAppAdapter
import com.hana.spindle.lite.launcher.LiteAppInfo
import com.hana.spindle.lite.receiver.HardwareButtonReceiver
import com.hana.spindle.lite.receiver.NoisyAudioReceiver
import com.hana.spindle.lite.util.DeviceNameFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Main Android Home Launcher Activity hosting Spindle Lite's 3-screen tactile triad:
 * - Screen 0 (Left): Application Drawer & System Audio Console
 * - Screen 1 (Center): Kinetic Cassette Player Deck (Default Home)
 * - Screen 2 (Right): Bauhaus Online FM Radio Streamer
 *
 * Engineered with zero-allocation, lightweight architecture for single-core ARMv7 (KitKat 4.4 API 19).
 */
class LiteMainActivity : AppCompatActivity(), PlaybackListener {

    private lateinit var binding: ActivityLiteMainBinding
    private lateinit var drawerBinding: PageLiteDrawerBinding
    private lateinit var playerBinding: PageLitePlayerBinding
    private lateinit var radioBinding: PageLiteRadioBinding

    private lateinit var audioEngine: LiteAudioEngine
    private lateinit var radioEngine: LiteRadioEngine
    private lateinit var dbHelper: LiteDbHelper
    private lateinit var mediaScanner: LiteMediaScanner

    private lateinit var trackAdapter: LiteTrackAdapter
    private lateinit var appAdapter: LiteAppAdapter

    private var currentPlaylist: List<Track> = emptyList()
    private var installedApps: List<LiteAppInfo> = emptyList()
    private var noisyReceiver: NoisyAudioReceiver? = null

    companion object {
        private const val PERMISSION_REQUEST_STORAGE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiteMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Inflate 3-page Triad views for ViewPager
        drawerBinding = PageLiteDrawerBinding.inflate(layoutInflater)
        playerBinding = PageLitePlayerBinding.inflate(layoutInflater)
        radioBinding = PageLiteRadioBinding.inflate(layoutInflater)

        // 2. Initialize Core Engines
        dbHelper = LiteDbHelper.getInstance(this)
        audioEngine = LiteAudioEngine(this).apply {
            listener = this@LiteMainActivity
        }
        radioEngine = LiteRadioEngine(this).apply {
            listener = setupRadioListener()
        }
        mediaScanner = LiteMediaScanner(this)

        // 3. Setup 3-Screen ViewPager
        setupViewPager()

        // 4. Setup Subsystems
        setupDrawerPage()
        setupPlayerPage()
        setupRadioPage()
        setupVaultDrawer()

        // 5. System Permissions & Hardware Interception
        checkPermissionsAndLoad()
        setupHardwareButtonHooks()
    }

    private fun setupViewPager() {
        val pagerAdapter = LitePagerAdapter(
            drawerPage = drawerBinding.root,
            playerPage = playerBinding.root,
            radioPage = radioBinding.root
        )
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.currentItem = 1 // Start on Center Page (Cassette Player)
    }

    // --- Page 0: Application Drawer & Audio Console ---

    private fun setupDrawerPage() {
        // Master Volume Tactile Slider
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        drawerBinding.sbMasterVolume.max = maxVol
        drawerBinding.sbMasterVolume.progress = curVol
        syncVolumePercent(curVol, maxVol)

        drawerBinding.sbMasterVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    syncVolumePercent(progress, maxVol)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // App List RecyclerView
        appAdapter = LiteAppAdapter(emptyList()) { appInfo ->
            launchApp(appInfo)
        }
        drawerBinding.rvApps.apply {
            layoutManager = LinearLayoutManager(this@LiteMainActivity)
            adapter = appAdapter
            setHasFixedSize(true)
        }

        // Search Filter
        drawerBinding.etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appAdapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadInstalledApps()
    }

    private fun syncVolumePercent(current: Int, max: Int) {
        val pct = if (max > 0) (current * 100) / max else 0
        drawerBinding.tvVolumePercent.text = "$pct%"
    }

    private fun syncVolumeSlider() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        drawerBinding.sbMasterVolume.progress = curVol
        syncVolumePercent(curVol, maxVol)
    }

    private fun loadInstalledApps() {
        Thread {
            try {
                val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
                val apps = ArrayList<LiteAppInfo>(resolveInfos.size)
                for (ri in resolveInfos) {
                    val pkg = ri.activityInfo.packageName
                    val cls = ri.activityInfo.name
                    val label = ri.loadLabel(packageManager)?.toString() ?: pkg
                    val icon = ri.loadIcon(packageManager)
                    apps.add(LiteAppInfo(label = label, packageName = pkg, className = cls, icon = icon))
                }
                apps.sortBy { it.label.lowercase(Locale.getDefault()) }
                runOnUiThread {
                    installedApps = apps
                    appAdapter.updateApps(apps)
                    drawerBinding.tvAppCount.text = "${apps.size} applications installed"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    drawerBinding.tvAppCount.text = "Installed applications"
                }
            }
        }.start()
    }

    private fun launchApp(appInfo: LiteAppInfo) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Cannot launch ${appInfo.label}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to launch: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Page 1: Kinetic Cassette Deck ---

    private fun setupPlayerPage() {
        // Dynamic Hardware Nameplate with Tap-to-Engrave
        val prefs = getSharedPreferences("spindle_lite_prefs", MODE_PRIVATE)
        var nameplateIdx = prefs.getInt("pref_nameplate_idx", 0)

        fun refreshNameplate() {
            playerBinding.tvDeviceNameplate.text = DeviceNameFormatter.getFormattedNameplate(nameplateIdx)
        }
        refreshNameplate()

        playerBinding.tvDeviceNameplate.setOnClickListener {
            nameplateIdx = (nameplateIdx + 1) % DeviceNameFormatter.NAMEPLATE_PRESETS.size
            prefs.edit().putInt("pref_nameplate_idx", nameplateIdx).apply()
            refreshNameplate()
            Toast.makeText(this, "Engraving: ${playerBinding.tvDeviceNameplate.text}", Toast.LENGTH_SHORT).show()
        }

        // Transport Controls
        playerBinding.btnPlay.setOnClickListener {
            if (radioEngine.isPlaying || radioEngine.isBuffering) {
                radioEngine.stop()
            }
            audioEngine.togglePlayPause()
        }

        playerBinding.btnNext.setOnClickListener {
            audioEngine.next()
        }

        playerBinding.btnPrev.setOnClickListener {
            audioEngine.previous()
        }

        playerBinding.btnEject.setOnClickListener {
            toggleVaultDrawer()
        }
    }

    // --- Page 2: Bauhaus Online FM Radio ---

    private fun setupRadioPage() {
        // Preset Buttons
        radioBinding.btnPresetLofi.setOnClickListener { selectRadioPreset(0) }
        radioBinding.btnPresetAnime.setOnClickListener { selectRadioPreset(1) }
        radioBinding.btnPresetInitialD.setOnClickListener { selectRadioPreset(2) }
        radioBinding.btnPresetCitypop.setOnClickListener { selectRadioPreset(3) }

        // Play/Pause stream toggle
        radioBinding.btnRadioPlayToggle.setOnClickListener { toggleRadioPlayback() }
        radioBinding.radioLcdContainer.setOnClickListener { toggleRadioPlayback() }

        // Tactile Tuning Dial
        radioBinding.radioDialView.onFrequencyChanged = { freq ->
            radioBinding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", freq)
        }

        radioBinding.radioDialView.onFrequencySelected = { freq ->
            snapOrTuneFrequency(freq)
        }

        // Initial preset highlight
        highlightRadioPreset(0)
    }

    private fun highlightRadioPreset(selectedIdx: Int) {
        val buttons = listOf(
            radioBinding.btnPresetLofi,
            radioBinding.btnPresetAnime,
            radioBinding.btnPresetInitialD,
            radioBinding.btnPresetCitypop
        )
        buttons.forEachIndexed { index, button ->
            val isSel = index == selectedIdx
            button.isSelected = isSel
            button.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isSel) R.color.brand_orange else R.color.lite_text_secondary
                )
            )
        }
    }

    private fun selectRadioPreset(index: Int) {
        if (index !in LiteRadioEngine.PRESETS.indices) return
        val station = LiteRadioEngine.PRESETS[index]
        highlightRadioPreset(index)
        radioBinding.radioDialView.currentFrequency = station.frequencyMhz
        radioBinding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", station.frequencyMhz)
        radioBinding.tvRadioStationName.text = station.rdsName

        if (audioEngine.currentTrack != null && audioEngine.isPlaying) {
            audioEngine.pause()
        }

        radioEngine.playStation(station)
    }

    private fun toggleRadioPlayback() {
        if (radioEngine.isPlaying || radioEngine.isBuffering) {
            radioEngine.stop()
        } else {
            if (audioEngine.currentTrack != null && audioEngine.isPlaying) {
                audioEngine.pause()
            }
            val station = radioEngine.currentStation ?: LiteRadioEngine.PRESETS[0]
            radioEngine.playStation(station)
        }
    }

    private fun snapOrTuneFrequency(freq: Float) {
        val closestPreset = LiteRadioEngine.PRESETS.minByOrNull { abs(it.frequencyMhz - freq) }
        if (closestPreset != null && abs(closestPreset.frequencyMhz - freq) <= 1.2f) {
            val idx = LiteRadioEngine.PRESETS.indexOf(closestPreset)
            selectRadioPreset(idx)
        } else {
            radioBinding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", freq)
            radioBinding.tvRadioStationName.text = "MANUAL TUNING • UNTUNED"
            radioBinding.tvRadioRds.text = "No broadcast carrier detected at this frequency"
            radioBinding.tvRadioStreamStatus.text = "STATIC"
            radioBinding.tvRadioStreamStatus.setTextColor(0xFF94A3B8.toInt())
            radioEngine.stop()
        }
    }

    private fun setupRadioListener(): LiteRadioEngine.Listener {
        return object : LiteRadioEngine.Listener {
            override fun onRadioStateChanged(
                isPlaying: Boolean,
                isBuffering: Boolean,
                station: LiteRadioStation?
            ) {
                runOnUiThread {
                    radioBinding.speakerGrilleView.isPlaying = isPlaying
                    radioBinding.btnRadioPlayToggle.setImageResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                    radioBinding.tvRadioStreamStatus.text = when {
                        isBuffering -> "BUFFERING"
                        isPlaying -> "LIVE IN"
                        else -> "STANDBY"
                    }
                    radioBinding.tvRadioStreamStatus.setTextColor(
                        when {
                            isPlaying -> 0xFF10B981.toInt()
                            isBuffering -> 0xFFF59E0B.toInt()
                            else -> 0xFF94A3B8.toInt()
                        }
                    )

                    station?.let { st ->
                        radioBinding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", st.frequencyMhz)
                        radioBinding.tvRadioStationName.text = st.rdsName
                        val presetIdx = LiteRadioEngine.PRESETS.indexOfFirst { it.frequencyMhz == st.frequencyMhz }
                        if (presetIdx >= 0) {
                            highlightRadioPreset(presetIdx)
                        }
                    }
                }
            }

            override fun onRadioBuffering(percent: Int) {
                runOnUiThread {
                    if (radioEngine.isBuffering) {
                        radioBinding.tvRadioStreamStatus.text = "BUFFER $percent%"
                    }
                }
            }

            override fun onRadioError(message: String) {
                runOnUiThread {
                    radioBinding.tvRadioStreamStatus.text = "ERR: OFFLINE"
                    radioBinding.tvRadioStreamStatus.setTextColor(0xFFEF4444.toInt())
                    Toast.makeText(this@LiteMainActivity, "Radio: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- Sliding Tape Vault Drawer Overlay ---

    private fun setupVaultDrawer() {
        trackAdapter = LiteTrackAdapter(emptyList()) { position, _ ->
            if (radioEngine.isPlaying || radioEngine.isBuffering) {
                radioEngine.stop()
            }
            audioEngine.setPlaylist(currentPlaylist, position)
            binding.drawerLayout.visibility = View.GONE
        }
        binding.rvTrackList.apply {
            layoutManager = LinearLayoutManager(this@LiteMainActivity)
            adapter = trackAdapter
            setHasFixedSize(true)
        }

        binding.btnCloseDrawer.setOnClickListener {
            binding.drawerLayout.visibility = View.GONE
        }

        binding.btnScan.setOnClickListener {
            performMediaScan()
        }
    }

    private fun toggleVaultDrawer() {
        if (binding.drawerLayout.visibility == View.VISIBLE) {
            binding.drawerLayout.visibility = View.GONE
        } else {
            binding.drawerLayout.visibility = View.VISIBLE
            refreshTrackList()
        }
    }

    // --- Media Library & Permissions ---

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_STORAGE
                )
                return
            }
        }
        loadLibrary()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_STORAGE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadLibrary()
        }
    }

    private fun loadLibrary() {
        val tracks = dbHelper.getAllTracks()
        if (tracks.isEmpty()) {
            performMediaScan()
        } else {
            updateLibrary(tracks)
        }
    }

    private fun performMediaScan() {
        binding.tvVaultStatus.text = getString(R.string.scanning)
        mediaScanner.startScan(object : LiteMediaScanner.ScanCallback {
            override fun onScanProgress(foundCount: Int) {
                runOnUiThread {
                    binding.tvVaultStatus.text = "Indexing: $foundCount tracks found..."
                }
            }

            override fun onScanComplete(totalTracks: Int) {
                runOnUiThread {
                    refreshTrackList()
                    binding.tvVaultStatus.text = "Vault Ready: $totalTracks tracks indexed"
                }
            }
        })
    }

    private fun refreshTrackList() {
        val tracks = dbHelper.getAllTracks()
        updateLibrary(tracks)
    }

    private fun updateLibrary(tracks: List<Track>) {
        this.currentPlaylist = tracks
        trackAdapter.updateTracks(tracks)
        binding.tvVaultStatus.text = "Vault: ${tracks.size} tracks"
        if (audioEngine.currentTrack == null && tracks.isNotEmpty()) {
            val firstTrack = tracks[0]
            playerBinding.tvTrackTitle.text = firstTrack.title
            playerBinding.tvTrackArtist.text = firstTrack.artist
            playerBinding.tvFormatBadge.text = firstTrack.formatBadge
            playerBinding.tvTimeReadout.text = "00:00 / ${firstTrack.formattedDuration}"
            playerBinding.deckView.setCassetteLabel(firstTrack.tapeBiasType)
        }
    }

    // --- PlaybackListener Implementation (Cassette Deck) ---

    override fun onPlaybackStateChanged(state: PlaybackState) {
        val track = state.currentTrack
        if (track != null) {
            playerBinding.tvTrackTitle.text = track.title
            playerBinding.tvTrackArtist.text = track.artist
            playerBinding.tvFormatBadge.text = track.formatBadge
            playerBinding.tvTimeReadout.text = "${state.formattedPosition} / ${state.formattedDuration}"
            playerBinding.deckView.setCassetteLabel(track.tapeBiasType)
        }

        playerBinding.btnPlay.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        playerBinding.deckView.setPlaybackState(state.isPlaying, state.progressFraction)
    }

    override fun onTrackCompleted(track: Track?) {
        // Automatic gapless transition handled by LiteAudioEngine
    }

    override fun onPlaybackError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    // --- Hardware Button Interception ---

    private fun setupHardwareButtonHooks() {
        HardwareButtonReceiver.buttonListener = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleHardwareAction(event.keyCode)
            } else false
        }
    }

    private fun handleHardwareAction(keyCode: Int): Boolean {
        val isRadioScreen = binding.viewPager.currentItem == 2
        return when (keyCode) {
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (isRadioScreen) {
                    toggleRadioPlayback()
                } else {
                    audioEngine.togglePlayPause()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (isRadioScreen) {
                    radioEngine.tuneNext()
                } else {
                    audioEngine.next()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (isRadioScreen) {
                    radioEngine.tunePrev()
                } else {
                    audioEngine.previous()
                }
                true
            }
            else -> false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val handled = super.onKeyDown(keyCode, event)
                syncVolumeSlider()
                return handled
            }
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (handleHardwareAction(keyCode)) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.visibility == View.VISIBLE) {
            binding.drawerLayout.visibility = View.GONE
        } else if (binding.viewPager.currentItem != 1) {
            // Return to Center Cassette Player Deck
            binding.viewPager.currentItem = 1
        } else {
            // Act as Home launcher: don't exit to blank screen
            super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        noisyReceiver = NoisyAudioReceiver {
            audioEngine.pause()
            radioEngine.stop()
        }
        registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
    }

    override fun onStop() {
        super.onStop()
        noisyReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (ignored: Exception) {}
            noisyReceiver = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
        radioEngine.release()
        LiteBitmapCache.clear()
        HardwareButtonReceiver.buttonListener = null
    }
}
