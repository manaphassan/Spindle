package com.hana.spindle.lite.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.hana.spindle.lite.audio.LiteAnalogFmEngine
import com.hana.spindle.lite.audio.LiteAudioEngine
import com.hana.spindle.lite.audio.LiteRadioEngine
import com.hana.spindle.lite.audio.LiteRadioStation
import com.hana.spindle.lite.audio.PlaybackListener
import com.hana.spindle.lite.audio.PlaybackState
import com.hana.spindle.lite.audio.RadioRegion
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
 * - Screen 0 (Left): App Drawer & Formulation Settings
 * - Screen 1 (Center): Kinetic Cassette Tape Deck Player
 * - Screen 2 (Right): Bauhaus Online FM Radio Streamer & Tactile Analog Tuner
 */
class LiteMainActivity : AppCompatActivity(), PlaybackListener {

    private lateinit var binding: ActivityLiteMainBinding
    private lateinit var drawerBinding: PageLiteDrawerBinding
    private lateinit var playerBinding: PageLitePlayerBinding
    private lateinit var radioBinding: PageLiteRadioBinding

    private lateinit var audioEngine: LiteAudioEngine
    private lateinit var radioEngine: LiteRadioEngine
    private lateinit var analogFmEngine: LiteAnalogFmEngine
    private lateinit var dbHelper: LiteDbHelper
    private lateinit var mediaScanner: LiteMediaScanner

    private lateinit var trackAdapter: LiteTrackAdapter
    private lateinit var appAdapter: LiteAppAdapter

    private var currentPlaylist: List<Track> = emptyList()
    private var installedApps: List<LiteAppInfo> = emptyList()
    private var noisyReceiver: NoisyAudioReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var headsetReceiver: BroadcastReceiver? = null
    private var isAntennaConnected = false
    private var isAnalogMode = false
    private var isForceMono = false
    private var currentRadioRegion = RadioRegion.EU
    private var isScanningFm = false
    private val scanHandler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    private var fmPresets = mutableListOf(88.5f, 91.3f, 98.1f, 105.7f)

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
        analogFmEngine = LiteAnalogFmEngine(this).apply {
            listener = object : LiteAnalogFmEngine.AnalogFmListener {
                override fun onSignalChanged(frequencyMhz: Float, signalStrength: Float, isStereo: Boolean, stationName: String?) {
                    runOnUiThread {
                        radioBinding.signalMeterView.setSignalStrength(signalStrength, isStereo)
                        if (isAnalogMode) {
                            radioBinding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", frequencyMhz)
                            if (stationName != null) {
                                radioBinding.tvRadioStationName.text = stationName
                                radioBinding.tvRadioRds.text = if (isStereo) "ANALOG RF • 19kHz PILOT STEREO" else (if (isForceMono) "ANALOG RF • MONO FILTER" else "ANALOG RF • MONO CARRIER")
                            } else {
                                radioBinding.tvRadioStationName.text = "SEARCHING OTA CARRIER..."
                                radioBinding.tvRadioRds.text = if (isForceMono) "THERMAL HISS • MONO DEFEAT" else "INTER-STATION THERMAL HISS"
                            }
                            val closestIdx = fmPresets.indexOfFirst { abs(it - frequencyMhz) < 0.2f }
                            highlightRadioPreset(closestIdx)
                        }
                    }
                }
            }
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
        setupDrawerTabs()
        setupEqPage()
        setupAppsPage()
        setupSettingsPage()
    }

    private fun setupDrawerTabs() {
        drawerBinding.btnTabEq.setOnClickListener { selectDrawerTab(0) }
        drawerBinding.btnTabApps.setOnClickListener { selectDrawerTab(1) }
        drawerBinding.btnTabSettings.setOnClickListener { selectDrawerTab(2) }
        selectDrawerTab(1) // Default to Apps
    }

    private fun selectDrawerTab(tabIndex: Int) {
        drawerBinding.containerEq.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        drawerBinding.containerApps.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        drawerBinding.containerSettings.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE

        val activeColor = ContextCompat.getColor(this, R.color.brand_orange)
        val inactiveColor = ContextCompat.getColor(this, R.color.lite_text_primary)

        drawerBinding.btnTabEq.setTextColor(if (tabIndex == 0) activeColor else inactiveColor)
        drawerBinding.btnTabApps.setTextColor(if (tabIndex == 1) activeColor else inactiveColor)
        drawerBinding.btnTabSettings.setTextColor(if (tabIndex == 2) activeColor else inactiveColor)
    }

    private fun setupEqPage() {
        val fx = audioEngine.audioFxController
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        // Master Output Gain on EQ Page
        drawerBinding.sbMasterVolumeEq.max = maxVol
        drawerBinding.sbMasterVolumeEq.progress = curVol
        drawerBinding.sbMasterVolumeEq.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    drawerBinding.sbMasterVolume.progress = progress
                    syncVolumePercent(progress, maxVol)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // EQ Preset Buttons
        val presetButtons = mapOf(
            "FLAT" to drawerBinding.btnPresetFlat,
            "BASS+" to drawerBinding.btnPresetBass,
            "VOCAL" to drawerBinding.btnPresetVocal,
            "ROCK" to drawerBinding.btnPresetRock,
            "TAPE" to drawerBinding.btnPresetTape
        )

        fun updatePresetButtons(activePreset: String) {
            val activeColor = ContextCompat.getColor(this, R.color.brand_orange)
            val inactiveColor = ContextCompat.getColor(this, R.color.lite_text_secondary)
            for ((name, btn) in presetButtons) {
                val isSelected = name.equals(activePreset, ignoreCase = true)
                btn.isSelected = isSelected
                btn.setTextColor(if (isSelected) activeColor else inactiveColor)
            }
        }

        fun updateBandViewsFromFx() {
            val bandSeekBars = arrayOf(
                drawerBinding.sbBand0,
                drawerBinding.sbBand1,
                drawerBinding.sbBand2,
                drawerBinding.sbBand3,
                drawerBinding.sbBand4
            )
            val bandGainTexts = arrayOf(
                drawerBinding.tvBand0Gain,
                drawerBinding.tvBand1Gain,
                drawerBinding.tvBand2Gain,
                drawerBinding.tvBand3Gain,
                drawerBinding.tvBand4Gain
            )
            val bandFreqTexts = arrayOf(
                drawerBinding.tvBand0Freq,
                drawerBinding.tvBand1Freq,
                drawerBinding.tvBand2Freq,
                drawerBinding.tvBand3Freq,
                drawerBinding.tvBand4Freq
            )

            for (i in 0 until 5) {
                val gainDb = fx.bandGainsDb[i]
                val progress = (gainDb + 12f).toInt().coerceIn(0, 24)
                bandSeekBars[i].progress = progress
                val sign = if (gainDb > 0) "+" else ""
                bandGainTexts[i].text = String.format(Locale.US, "%s%.0f dB", sign, gainDb)

                val freq = fx.bandFrequenciesHz[i]
                if (freq > 0) {
                    bandFreqTexts[i].text = if (freq >= 1000) String.format(Locale.US, "%.1fk", freq / 1000f) else "$freq"
                }
            }

            drawerBinding.sbBassBoost.progress = fx.bassBoostStrength
            drawerBinding.tvBassBoostPercent.text = "${fx.bassBoostStrength / 10}%"
            updatePresetButtons(fx.currentPreset)
            drawerBinding.btnEqToggle.text = if (fx.isEnabled) "DSP: ON" else "DSP: OFF"
            drawerBinding.btnEqToggle.setTextColor(
                ContextCompat.getColor(this, if (fx.isEnabled) R.color.lite_led_green else R.color.lite_text_muted)
            )
        }

        for ((name, btn) in presetButtons) {
            btn.setOnClickListener {
                fx.applyPreset(name)
                updateBandViewsFromFx()
            }
        }

        drawerBinding.btnEqToggle.setOnClickListener {
            fx.setEnabledState(!fx.isEnabled)
            updateBandViewsFromFx()
        }

        // 5-Band SeekBars
        val bandSeekBars = arrayOf(
            drawerBinding.sbBand0,
            drawerBinding.sbBand1,
            drawerBinding.sbBand2,
            drawerBinding.sbBand3,
            drawerBinding.sbBand4
        )
        val bandGainTexts = arrayOf(
            drawerBinding.tvBand0Gain,
            drawerBinding.tvBand1Gain,
            drawerBinding.tvBand2Gain,
            drawerBinding.tvBand3Gain,
            drawerBinding.tvBand4Gain
        )

        for (i in 0 until 5) {
            val bandIdx = i
            bandSeekBars[i].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val gainDb = (progress - 12).toFloat()
                        fx.setBandGainDb(bandIdx, gainDb)
                        val sign = if (gainDb > 0) "+" else ""
                        bandGainTexts[bandIdx].text = String.format(Locale.US, "%s%.0f dB", sign, gainDb)
                        updatePresetButtons("CUSTOM")
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // Bass Boost SeekBar
        drawerBinding.sbBassBoost.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    fx.setBassBoost(progress)
                    drawerBinding.tvBassBoostPercent.text = "${progress / 10}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        updateBandViewsFromFx()
    }

    private fun setupAppsPage() {
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
                    drawerBinding.sbMasterVolumeEq.progress = progress
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

    private fun setupSettingsPage() {
        val sp = getSharedPreferences("spindle_lite_settings", Context.MODE_PRIVATE)

        // Formulation Theme Buttons
        fun updateThemeButtons(currentLabel: String) {
            val activeColor = ContextCompat.getColor(this, R.color.brand_orange)
            val inactiveColor = ContextCompat.getColor(this, R.color.lite_text_primary)
            val isType1 = currentLabel.contains("TYPE I", ignoreCase = true) || currentLabel.contains("NORMAL", ignoreCase = true)
            val isType4 = currentLabel.contains("TYPE IV", ignoreCase = true) || currentLabel.contains("METAL", ignoreCase = true)
            val isType2 = !isType1 && !isType4

            drawerBinding.btnThemeType1.setTextColor(if (isType1) activeColor else inactiveColor)
            drawerBinding.btnThemeType2.setTextColor(if (isType2) activeColor else inactiveColor)
            drawerBinding.btnThemeType4.setTextColor(if (isType4) activeColor else inactiveColor)
        }

        val savedLabel = sp.getString("cassette_formulation", "SPINDLE • HIGH BIAS TYPE II") ?: "SPINDLE • HIGH BIAS TYPE II"
        playerBinding.deckView.setCassetteLabel(savedLabel)
        updateThemeButtons(savedLabel)

        drawerBinding.btnThemeType1.setOnClickListener {
            val label = "SPINDLE • NORMAL STUDIO TYPE I"
            playerBinding.deckView.setCassetteLabel(label)
            sp.edit().putString("cassette_formulation", label).apply()
            updateThemeButtons(label)
        }

        drawerBinding.btnThemeType2.setOnClickListener {
            val label = "SPINDLE • HIGH BIAS TYPE II"
            playerBinding.deckView.setCassetteLabel(label)
            sp.edit().putString("cassette_formulation", label).apply()
            updateThemeButtons(label)
        }

        drawerBinding.btnThemeType4.setOnClickListener {
            val label = "SPINDLE • METAL MASTER TYPE IV"
            playerBinding.deckView.setCassetteLabel(label)
            sp.edit().putString("cassette_formulation", label).apply()
            updateThemeButtons(label)
        }

        // Hardware Nameplate Engraving
        val defaultNameplate = DeviceNameFormatter.getDeviceNameplate()
        val savedNameplate = sp.getString("device_nameplate", defaultNameplate) ?: defaultNameplate
        playerBinding.tvDeviceNameplate.text = savedNameplate
        drawerBinding.etDeviceNameplate.setText(savedNameplate)
        drawerBinding.tvDiagDevice.text = "DEVICE: $savedNameplate"

        drawerBinding.btnSaveNameplate.setOnClickListener {
            val text = drawerBinding.etDeviceNameplate.text.toString().trim()
            if (text.isNotEmpty()) {
                playerBinding.tvDeviceNameplate.text = text
                drawerBinding.tvDiagDevice.text = "DEVICE: $text"
                sp.edit().putString("device_nameplate", text).apply()
                Toast.makeText(this, "Nameplate engraved: $text", Toast.LENGTH_SHORT).show()
            }
        }

        // Tape Vault Path & Rescan
        drawerBinding.tvCurrentMusicPath.text = "PATH: /storage/sdcard1/Music"
        drawerBinding.btnRescanVault.setOnClickListener {
            performMediaScan()
            Toast.makeText(this, "Scanning MicroSD Tape Vault...", Toast.LENGTH_SHORT).show()
        }

        // Diagnostics
        drawerBinding.tvDiagOs.text = "SYSTEM: ANDROID ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        drawerBinding.tvDiagDac.text = "DAC: QUALCOMM STAGEFRIGHT 16-BIT 44.1kHz"
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        drawerBinding.tvDiagRam.text = "HEAP USED: ${usedMemMb} MB / BUDGET: < 18 MB"
    }

    private fun syncVolumePercent(current: Int, max: Int) {
        val pct = if (max > 0) (current * 100) / max else 0
        drawerBinding.tvVolumePercent.text = "$pct%"
        drawerBinding.tvVolumePercentEq.text = "$pct%"
    }

    private fun syncVolumeSlider() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        drawerBinding.sbMasterVolume.progress = curVol
        drawerBinding.sbMasterVolumeEq.progress = curVol
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
                    drawerBinding.tvDrawerBadge.text = "APPS: ${apps.size}"
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
            playerBinding.tvAndroidVersion.text = DeviceNameFormatter.getAndroidVersionString()
        }
        refreshNameplate()

        playerBinding.llDeviceIdentity.setOnClickListener {
            nameplateIdx = (nameplateIdx + 1) % DeviceNameFormatter.NAMEPLATE_PRESETS.size
            prefs.edit().putInt("pref_nameplate_idx", nameplateIdx).apply()
            refreshNameplate()
            Toast.makeText(this, "Engraving: ${playerBinding.tvDeviceNameplate.text}", Toast.LENGTH_SHORT).show()
        }

        // Interactive 12-Bar LED Progress Seeking
        playerBinding.ledMeterView.onSeekListener = { fraction ->
            audioEngine.seekToFraction(fraction)
        }

        // Default Idle Display (before track is loaded)
        if (audioEngine.currentTrack == null) {
            playerBinding.deckView.setNowPlaying("", "READY • TAP EJECT", "", "")
            playerBinding.tvIndexBadge.text = "INDEX: 000"
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

    // --- Page 2: Bauhaus Online & Analog FM Radio ---

    private fun setupRadioPage() {
        loadRadioSettings()

        // Tactile Rocker Switch: FM (UP) vs DIGI (DOWN)
        radioBinding.rockerSwitchView.setModeWithoutAnimation(isAnalogMode)
        radioBinding.rockerSwitchView.onModeChanged = { isFm ->
            setRadioMode(isFm)
        }

        // Force Mono Toggle Button
        radioBinding.btnRadioMono.setOnClickListener {
            isForceMono = !isForceMono
            analogFmEngine.isForceMono = isForceMono
            updateMonoUi()
            saveRadioSettings()
            Toast.makeText(
                this,
                if (isForceMono) "FM Mono Filter Engaged • 19kHz Pilot Defeated" else "FM Stereo Multi-carrier Auto",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Channel Search (Scan / Seek)
        radioBinding.btnRadioScan.setOnClickListener {
            if (!isAnalogMode) {
                setRadioMode(true)
            }
            if (isScanningFm) {
                stopChannelSearch()
            } else {
                startChannelSearch()
            }
        }

        // Radio Region Selector (US / EU / JP)
        radioBinding.btnRadioRegion.setOnClickListener {
            val nextRegion = when (currentRadioRegion) {
                RadioRegion.US -> RadioRegion.EU
                RadioRegion.EU -> RadioRegion.JP
                RadioRegion.JP -> RadioRegion.US
            }
            setRadioRegion(nextRegion)
        }

        // Play/Pause stream toggle
        radioBinding.btnRadioPlayToggle.setOnClickListener { toggleRadioPlayback() }
        radioBinding.radioLcdContainer.setOnClickListener { toggleRadioPlayback() }

        // Antenna Badge
        radioBinding.tvAntennaBadge.setOnClickListener {
            if (!isAntennaConnected) {
                Toast.makeText(this, "Connect 3.5mm Headphone Wire as Dipole Antenna", Toast.LENGTH_SHORT).show()
            } else {
                setRadioMode(!isAnalogMode)
            }
        }

        // Tactile Tuning Dial
        radioBinding.radioDialView.onFrequencyChanged = { freq ->
            radioBinding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", freq)
            if (isAnalogMode) {
                analogFmEngine.tuneFrequency(freq)
            }
        }

        radioBinding.radioDialView.onFrequencySelected = { freq ->
            if (isAnalogMode) {
                analogFmEngine.tuneFrequency(freq)
                saveRadioSettings()
            } else {
                snapOrTuneFrequency(freq)
            }
        }

        // Apply initial UI states
        updateMonoUi()
        updateRegionUi()
        updatePresetButtonsForCurrentMode()
        setRadioMode(isAnalogMode)
    }

    private fun setRadioRegion(region: RadioRegion) {
        currentRadioRegion = region
        analogFmEngine.setRegion(region)
        radioBinding.radioDialView.setBandLimits(region.minFreq, region.maxFreq, region.stepMhz)
        radioBinding.radioDialView.currentFrequency = radioBinding.radioDialView.currentFrequency.coerceIn(region.minFreq, region.maxFreq)
        updateRegionUi()

        // If FM presets are outside the new band limits, refresh them to region defaults
        if (fmPresets.any { it < region.minFreq || it > region.maxFreq }) {
            fmPresets = when (region) {
                RadioRegion.US -> mutableListOf(88.5f, 92.3f, 97.1f, 104.3f)
                RadioRegion.EU -> mutableListOf(88.5f, 91.3f, 98.1f, 105.7f)
                RadioRegion.JP -> mutableListOf(80.0f, 81.3f, 89.7f, 90.5f)
            }
            updatePresetButtonsForCurrentMode()
        }

        saveRadioSettings()
        Toast.makeText(this, "${region.label} • ${region.minFreq}–${region.maxFreq} MHz", Toast.LENGTH_SHORT).show()
    }

    private fun updateRegionUi() {
        radioBinding.btnRadioRegion.text = currentRadioRegion.label
    }

    private fun updateMonoUi() {
        radioBinding.btnRadioMono.text = if (isForceMono) "MONO: ON" else "MONO"
        radioBinding.btnRadioMono.setTextColor(
            ContextCompat.getColor(
                this,
                if (isForceMono) R.color.brand_orange else R.color.lite_text_secondary
            )
        )
        if (isAnalogMode) {
            radioBinding.tvRadioBadge.text = if (isForceMono) "FM MONO" else "FM STEREO"
        } else {
            radioBinding.tvRadioBadge.text = "DIGI RADIO"
        }
    }

    private fun updatePresetButtonsForCurrentMode() {
        val buttons = listOf(
            radioBinding.btnPresetLofi,
            radioBinding.btnPresetAnime,
            radioBinding.btnPresetInitialD,
            radioBinding.btnPresetCitypop
        )

        if (isAnalogMode) {
            // FM Mode: Show FM saved frequencies, tap to tune, long-press to save current freq
            buttons.forEachIndexed { index, button ->
                val freq = fmPresets.getOrElse(index) { 88.5f }
                button.text = String.format(Locale.US, "%.1f", freq)
                button.setOnClickListener {
                    stopChannelSearch()
                    val targetFreq = fmPresets.getOrElse(index) { 88.5f }
                    radioBinding.radioDialView.currentFrequency = targetFreq
                    radioBinding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", targetFreq)
                    analogFmEngine.tuneFrequency(targetFreq)
                    highlightRadioPreset(index)
                    saveRadioSettings()
                }
                button.setOnLongClickListener {
                    val currentFreq = radioBinding.radioDialView.currentFrequency
                    fmPresets[index] = currentFreq
                    button.text = String.format(Locale.US, "%.1f", currentFreq)
                    button.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    Toast.makeText(
                        this,
                        "FM Preset P${index + 1} Saved: ${String.format(Locale.US, "%.1f", currentFreq)} MHz",
                        Toast.LENGTH_SHORT
                    ).show()
                    saveRadioSettings()
                    true
                }
            }
        } else {
            // Digital Mode: Show digital stations from presets, tap to play stream
            val titles = listOf("LO-FI", "ANIME", "INIT D", "CITYPOP")
            buttons.forEachIndexed { index, button ->
                button.text = titles.getOrElse(index) { "P${index + 1}" }
                button.setOnClickListener {
                    selectRadioPreset(index)
                }
                button.setOnLongClickListener {
                    val station = LiteRadioEngine.PRESETS.getOrNull(index)
                    Toast.makeText(this, "Digital Preset: ${station?.rdsName ?: "Stream"}", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
    }

    private fun startChannelSearch() {
        if (!isAnalogMode) {
            setRadioMode(true)
        }
        if (isScanningFm) return
        isScanningFm = true
        radioBinding.btnRadioScan.text = "STOP"
        radioBinding.btnRadioScan.setTextColor(ContextCompat.getColor(this, R.color.brand_orange))
        radioBinding.tvRadioStreamStatus.text = "SCANNING"
        radioBinding.tvRadioStreamStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_orange))
        radioBinding.tvRadioStationName.text = "SCANNING OTA CARRIER..."
        radioBinding.tvRadioRds.text = "SEARCHING FOR RF MULTIPLEX"

        val startFreq = radioBinding.radioDialView.currentFrequency
        val step = currentRadioRegion.stepMhz
        val min = currentRadioRegion.minFreq
        val max = currentRadioRegion.maxFreq

        var current = startFreq

        scanRunnable = object : Runnable {
            override fun run() {
                if (!isScanningFm) return

                current += step
                if (current > max) {
                    current = min
                }
                current = (Math.round(current * 10f) / 10f)

                radioBinding.radioDialView.currentFrequency = current
                radioBinding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", current)
                analogFmEngine.tuneFrequency(current)

                val strength = analogFmEngine.getSignalStrength(current)
                if (strength >= 0.68f) {
                    stopChannelSearch()
                    val station = analogFmEngine.carrierStations.minByOrNull { abs(it.first - current) }
                    val name = station?.second ?: "FM CARRIER LOCKED"
                    radioBinding.tvRadioStationName.text = name
                    radioBinding.tvRadioStreamStatus.text = "LOCKED"
                    radioBinding.tvRadioStreamStatus.setTextColor(ContextCompat.getColor(this@LiteMainActivity, R.color.lite_led_green))
                    Toast.makeText(
                        this@LiteMainActivity,
                        "Carrier Locked: $name (${String.format(Locale.US, "%.1f", current)} MHz)",
                        Toast.LENGTH_SHORT
                    ).show()
                    saveRadioSettings()
                    return
                }

                if (abs(current - startFreq) < (step / 2f)) {
                    stopChannelSearch()
                    radioBinding.tvRadioStreamStatus.text = "ANALOG RF"
                    radioBinding.tvRadioStreamStatus.setTextColor(ContextCompat.getColor(this@LiteMainActivity, R.color.lite_led_green))
                    Toast.makeText(this@LiteMainActivity, "Scan Complete • No Strong Carrier Found", Toast.LENGTH_SHORT).show()
                    return
                }

                scanHandler.postDelayed(this, 80L)
            }
        }
        scanHandler.postDelayed(scanRunnable!!, 80L)
    }

    private fun stopChannelSearch() {
        isScanningFm = false
        scanRunnable?.let { scanHandler.removeCallbacks(it) }
        scanRunnable = null
        radioBinding.btnRadioScan.text = "SCAN"
        radioBinding.btnRadioScan.setTextColor(ContextCompat.getColor(this, R.color.lite_amber_glow))
        if (isAnalogMode) {
            radioBinding.tvRadioStreamStatus.text = "ANALOG RF"
            radioBinding.tvRadioStreamStatus.setTextColor(ContextCompat.getColor(this, R.color.lite_led_green))
        }
    }

    private fun saveRadioSettings() {
        val sp = getSharedPreferences("spindle_lite_radio_prefs", Context.MODE_PRIVATE)
        sp.edit().apply {
            putBoolean("pref_radio_mode_fm", isAnalogMode)
            putFloat("pref_radio_freq", radioBinding.radioDialView.currentFrequency)
            putString("pref_radio_region", currentRadioRegion.code)
            putBoolean("pref_radio_mono", isForceMono)
            putString("pref_radio_fm_presets", fmPresets.joinToString(","))
            apply()
        }
    }

    private fun loadRadioSettings() {
        val sp = getSharedPreferences("spindle_lite_radio_prefs", Context.MODE_PRIVATE)
        isAnalogMode = sp.getBoolean("pref_radio_mode_fm", false)
        isForceMono = sp.getBoolean("pref_radio_mono", false)
        val regionCode = sp.getString("pref_radio_region", "EU") ?: "EU"
        currentRadioRegion = RadioRegion.fromCode(regionCode)

        val savedPresets = sp.getString("pref_radio_fm_presets", null)
        if (savedPresets != null) {
            val list = savedPresets.split(",").mapNotNull { it.toFloatOrNull() }
            if (list.size == 4) {
                fmPresets.clear()
                fmPresets.addAll(list)
            }
        } else {
            fmPresets = when (currentRadioRegion) {
                RadioRegion.US -> mutableListOf(88.5f, 92.3f, 97.1f, 104.3f)
                RadioRegion.EU -> mutableListOf(88.5f, 91.3f, 98.1f, 105.7f)
                RadioRegion.JP -> mutableListOf(80.0f, 81.3f, 89.7f, 90.5f)
            }
        }

        val savedFreq = sp.getFloat("pref_radio_freq", currentRadioRegion.minFreq)
        radioBinding.radioDialView.setBandLimits(
            currentRadioRegion.minFreq,
            currentRadioRegion.maxFreq,
            currentRadioRegion.stepMhz
        )
        radioBinding.radioDialView.currentFrequency = savedFreq.coerceIn(currentRadioRegion.minFreq, currentRadioRegion.maxFreq)
        analogFmEngine.setRegion(currentRadioRegion)
        analogFmEngine.isForceMono = isForceMono
    }

    private fun updateAntennaUi() {
        if (isAntennaConnected) {
            radioBinding.tvAntennaBadge.text = "ANT: 3.5M"
            radioBinding.tvAntennaBadge.setTextColor(ContextCompat.getColor(this, R.color.lite_led_green))
        } else {
            radioBinding.tvAntennaBadge.text = "ANT: DETACHED"
            radioBinding.tvAntennaBadge.setTextColor(ContextCompat.getColor(this, R.color.lite_text_muted))
        }
    }

    private fun setRadioMode(analog: Boolean) {
        if (analog && !isAntennaConnected) {
            Toast.makeText(this, "Connect 3.5mm Headphone Wire for Optimal RF Reception", Toast.LENGTH_SHORT).show()
        }
        isAnalogMode = analog
        radioBinding.rockerSwitchView.isFmMode = analog
        analogFmEngine.setAnalogMode(analog)
        updatePresetButtonsForCurrentMode()
        updateMonoUi()

        if (analog) {
            radioBinding.tvRadioStreamStatus.text = "ANALOG RF"
            radioBinding.tvRadioStreamStatus.setTextColor(ContextCompat.getColor(this, R.color.lite_led_green))
            radioBinding.speakerGrilleView.isPlaying = true
            radioBinding.btnRadioPlayToggle.setImageResource(R.drawable.ic_pause)
            if (radioEngine.isPlaying || radioEngine.isBuffering) {
                radioEngine.stop()
            }
            if (audioEngine.isPlaying) {
                audioEngine.pause()
            }
            analogFmEngine.tuneFrequency(radioBinding.radioDialView.currentFrequency)
        } else {
            stopChannelSearch()
            radioBinding.tvRadioStreamStatus.text = "STANDBY"
            radioBinding.tvRadioStreamStatus.setTextColor(ContextCompat.getColor(this, R.color.lite_amber_glow))
            radioBinding.signalMeterView.setSignalStrength(0.85f, true)
            radioBinding.speakerGrilleView.isPlaying = radioEngine.isPlaying
            radioBinding.btnRadioPlayToggle.setImageResource(
                if (radioEngine.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
        saveRadioSettings()
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
        if (isAnalogMode) {
            setRadioMode(false)
        }
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
        saveRadioSettings()
    }

    private fun toggleRadioPlayback() {
        if (isAnalogMode) {
            if (analogFmEngine.isAnalogModeEnabled) {
                analogFmEngine.setAnalogMode(false)
                radioBinding.tvRadioStreamStatus.text = "MUTED"
                radioBinding.tvRadioStreamStatus.setTextColor(ContextCompat.getColor(this, R.color.lite_text_muted))
                radioBinding.btnRadioPlayToggle.setImageResource(R.drawable.ic_play)
                radioBinding.speakerGrilleView.isPlaying = false
            } else {
                analogFmEngine.setAnalogMode(true)
                analogFmEngine.tuneFrequency(radioBinding.radioDialView.currentFrequency)
                radioBinding.tvRadioStreamStatus.text = "ANALOG RF"
                radioBinding.tvRadioStreamStatus.setTextColor(ContextCompat.getColor(this, R.color.lite_led_green))
                radioBinding.btnRadioPlayToggle.setImageResource(R.drawable.ic_pause)
                radioBinding.speakerGrilleView.isPlaying = true
            }
            return
        }
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
        if (tracks.isNotEmpty()) {
            if (audioEngine.currentTrack == null) {
                audioEngine.setPlaylist(tracks, startIndex = 0, autoPlay = false)
            }
            val firstTrack = audioEngine.currentTrack ?: tracks[0]
            playerBinding.deckView.setNowPlaying(
                firstTrack.title,
                firstTrack.artist,
                "00:00 / ${firstTrack.formattedDuration}",
                firstTrack.audioSpecsLine
            )
            playerBinding.deckView.setCassetteLabel(firstTrack.tapeBiasType)
            playerBinding.tvIndexBadge.text = "INDEX: 000"
        } else {
            playerBinding.deckView.setNowPlaying("", "NO CASSETTE LOADED", "", "")
            playerBinding.tvIndexBadge.text = "INDEX: 000"
        }
    }

    // --- PlaybackListener Implementation (Cassette Deck) ---

    override fun onPlaybackStateChanged(state: PlaybackState) {
        val track = state.currentTrack
        if (track != null) {
            playerBinding.deckView.setNowPlaying(
                track.title,
                track.artist,
                "${state.formattedPosition} / ${state.formattedDuration}",
                track.audioSpecsLine
            )
            playerBinding.deckView.setCassetteLabel(track.tapeBiasType)
            val counterVal = (state.progressFraction * 999).toInt().coerceIn(0, 999)
            playerBinding.tvIndexBadge.text = String.format(Locale.US, "INDEX: %03d", counterVal)
        } else {
            playerBinding.tvIndexBadge.text = "INDEX: 000"
        }

        playerBinding.btnPlay.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        playerBinding.deckView.setPlaybackState(state.isPlaying, state.progressFraction)
        playerBinding.ledMeterView.setPlaybackState(state.isPlaying, state.progressFraction)
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
            if (isAnalogMode) {
                setRadioMode(false)
            }
        }
        registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )

        // Battery Status Monitoring
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        val pct = (level * 100) / scale
                        playerBinding.ledMeterView.setBatteryLevel(pct)
                    }
                }
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // 3.5mm Headphone Jack Dipole Antenna Monitoring
        headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_HEADSET_PLUG) {
                    val state = intent.getIntExtra("state", 0)
                    isAntennaConnected = (state == 1 || state == 2)
                    updateAntennaUi()
                }
            }
        }
        registerReceiver(headsetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
    }

    override fun onStop() {
        super.onStop()
        noisyReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (ignored: Exception) {}
            noisyReceiver = null
        }
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (ignored: Exception) {}
            batteryReceiver = null
        }
        headsetReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (ignored: Exception) {}
            headsetReceiver = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopChannelSearch()
        audioEngine.release()
        radioEngine.release()
        analogFmEngine.release()
        LiteBitmapCache.clear()
        HardwareButtonReceiver.buttonListener = null
    }
}
