package com.hana.spindle.ui

import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.FragmentDrawerBinding
import com.hana.spindle.launcher.AppInfo
import com.hana.spindle.launcher.AppListAdapter
import com.hana.spindle.launcher.AppListLoader
import com.hana.spindle.theme.CassetteTheme
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

class DrawerFragment : Fragment() {

    private var _binding: FragmentDrawerBinding? = null
    private val binding get() = _binding!!

    private lateinit var themeManager: ThemeManager
    private lateinit var appListLoader: AppListLoader
    private lateinit var appAdapter: AppListAdapter
    private var allApps: List<AppInfo> = emptyList()

    private val dspPresets = listOf("FLAT", "BASS_BOOST", "HARMAN", "VOCAL", "CLUB")
    private var currentPresetIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as SpindleApp
        themeManager = app.themeManager
        appListLoader = AppListLoader(requireContext())

        setupSettingsNavigation()
        setupAppDrawer()
        setupAudioMetrics(app)
        setupThemeSelector()
        setupDjConsole(app)
        setupLibraryRescan(app)
    }

    private fun setupSettingsNavigation() {
        binding.btnSettings.setOnClickListener {
            showSettings(true)
        }

        binding.btnBackFromSettings.setOnClickListener {
            showSettings(false)
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.containerSettings.visibility == View.VISIBLE) {
                    showSettings(false)
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun showSettings(show: Boolean) {
        binding.containerApps.visibility = if (show) View.GONE else View.VISIBLE
        binding.containerSettings.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupLibraryRescan(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = app.database.songDao().getSongCount()
            binding.tvSongCount.text = "$count tracks indexed in local database"
        }

        binding.btnRescanLibrary.setOnClickListener {
            binding.btnRescanLibrary.isEnabled = false
            binding.btnRescanLibrary.text = "SCANNING STORAGE..."
            binding.tvScanProgress.text = "Crawling storage and indexing metadata..."

            viewLifecycleOwner.lifecycleScope.launch {
                app.musicScanner.scanAll()
                val count = app.database.songDao().getSongCount()
                binding.tvSongCount.text = "$count tracks indexed in local database"
                binding.btnRescanLibrary.text = "RESCAN MUSIC STORAGE"
                binding.btnRescanLibrary.isEnabled = true
                binding.tvScanProgress.text = "Scan complete • $count tracks ready"
            }
        }
    }

    private fun setupAppDrawer() {
        appAdapter = AppListAdapter { appInfo ->
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(appInfo.packageName, appInfo.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(launchIntent)
        }

        binding.rvApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApps.adapter = appAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            allApps = appListLoader.loadInstalledApps()
            appAdapter.submitList(allApps)
        }

        binding.etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                val filtered = if (query.isEmpty()) {
                    allApps
                } else {
                    allApps.filter { it.label.lowercase(Locale.ROOT).contains(query) }
                }
                appAdapter.submitList(filtered)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })
    }

    private fun setupAudioMetrics(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.audioEngine.metricsTracker.metrics.collectLatest { metrics ->
                _binding?.let { b ->
                    b.tvMetricFormat.text = metrics.format
                    b.tvMetricSampleRate.text = "${metrics.bitDepth}-bit / ${metrics.sampleRate / 1000.0} kHz"
                    b.tvMetricBitrate.text = "${metrics.dynamicBitrateKbps} kbps"
                    b.tvMetricRoute.text = metrics.outputRoute

                    if (metrics.bluetoothBatteryPct != null) {
                        b.tvMetricBtBattery.text = "● ${metrics.bluetoothDeviceName ?: "Audio Gear"}: ${metrics.bluetoothBatteryPct}% (Healthy)"
                        b.tvMetricBtBattery.setTextColor(Color.parseColor("#00E676"))
                    } else if (metrics.outputRoute.contains("Bluetooth", ignoreCase = true)) {
                        b.tvMetricBtBattery.text = "● ${metrics.bluetoothDeviceName ?: "Connected Wireless Audio"} • Connected"
                        b.tvMetricBtBattery.setTextColor(Color.parseColor("#00E676"))
                    } else {
                        b.tvMetricBtBattery.text = "Disconnected (Using Wired 3.5mm ALSA Direct)"
                        b.tvMetricBtBattery.setTextColor(Color.parseColor("#71717A"))
                    }

                    if (metrics.isBitPerfect) {
                        b.tvBitPerfectBadge.text = "● BIT-PERFECT NATIVE"
                        b.tvBitPerfectBadge.setTextColor(Color.parseColor("#00E676"))
                    } else {
                        b.tvBitPerfectBadge.text = "▲ RESAMPLED (48kHz)"
                        b.tvBitPerfectBadge.setTextColor(Color.parseColor("#FFB300"))
                    }
                }
            }
        }
    }

    private fun setupThemeSelector() {
        val container = binding.llThemeButtons
        container.removeAllViews()

        for (theme in CassetteTheme.ALL_PRESETS) {
            val btn = Button(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(0, 0, 16, 0)
                }
                text = theme.name
                textSize = 11.5f
                isAllCaps = true
                setTextColor(if (theme.isDarkAppTheme) Color.WHITE else Color.parseColor("#1E293B"))
                backgroundTintList = ColorStateList.valueOf(theme.chassisColor)
                setOnClickListener {
                    themeManager.setTheme(theme)
                    binding.tvActiveThemeName.text = "${theme.name} (${theme.subtitle})"
                }
            }
            container.addView(btn)
        }
    }

    private fun setupDjConsole(app: SpindleApp) {
        val audioEngine = app.audioEngine
        val fxController = audioEngine.audioFxController

        // 1. Studio Rotary Potentiometers
        binding.knobLow.label = "LOW"
        binding.knobLow.minValue = -12f
        binding.knobLow.maxValue = 12f
        binding.knobLow.currentValue = fxController.lowGainDb
        binding.knobLow.onValueChanged = { gainDb ->
            fxController.setLowGain(gainDb)
        }

        binding.knobMid.label = "MID"
        binding.knobMid.minValue = -12f
        binding.knobMid.maxValue = 12f
        binding.knobMid.currentValue = fxController.midGainDb
        binding.knobMid.onValueChanged = { gainDb ->
            fxController.setMidGain(gainDb)
        }

        binding.knobHi.label = "HI"
        binding.knobHi.minValue = -12f
        binding.knobHi.maxValue = 12f
        binding.knobHi.currentValue = fxController.highGainDb
        binding.knobHi.onValueChanged = { gainDb ->
            fxController.setHighGain(gainDb)
        }

        binding.knobFilter.label = "FILTER"
        binding.knobFilter.minValue = 0f
        binding.knobFilter.maxValue = 1000f
        binding.knobFilter.currentValue = fxController.bassBoostStrength.toFloat()
        binding.knobFilter.onValueChanged = { strength ->
            fxController.setFilterStrength(strength.toInt())
        }

        // 2. FX Preset Button
        binding.btnFx.setOnClickListener {
            currentPresetIndex = (currentPresetIndex + 1) % dspPresets.size
            val preset = dspPresets[currentPresetIndex]
            fxController.applyPreset(preset)
            binding.btnFx.text = "FX: ${preset.replace("_", " ")}"

            // Update knob positions to reflect preset
            binding.knobLow.currentValue = fxController.lowGainDb
            binding.knobMid.currentValue = fxController.midGainDb
            binding.knobHi.currentValue = fxController.highGainDb
            binding.knobFilter.currentValue = fxController.bassBoostStrength.toFloat()
        }

        // 3. Transport Deck Buttons
        binding.btnCue.setOnClickListener {
            audioEngine.seekTo(0)
        }

        binding.btnDeckA.setOnClickListener {
            (activity as? MainActivity)?.navigateToPlayer()
        }

        binding.btnDeckB.setOnClickListener {
            (activity as? MainActivity)?.navigateToRadio()
        }

        binding.btnDjPlayPause.setOnClickListener {
            audioEngine.togglePlayPause()
        }

        binding.btnBrowse.setOnClickListener {
            (activity as? MainActivity)?.navigateToCatalog()
        }

        // 4. Crossfader
        binding.seekCrossfader.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val deckAVol = ((100 - progress) / 50f).coerceIn(0f, 1f)
                    val deckBVol = (progress / 50f).coerceIn(0f, 1f)
                    audioEngine.exoPlayer.volume = deckAVol
                    app.radioStreamEngine.setVolume(deckBVol)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 5. Observe playback state and animate VU Meter & Waveform
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    audioEngine.playbackState.collectLatest { state ->
                        _binding?.let { b ->
                            b.waveformView.isPlaying = state.isPlaying
                            b.waveformView.progress = state.progress

                            b.btnDjPlayPause.setImageResource(
                                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                            )

                            if (state.currentSong != null) {
                                b.tvDjTrackTitle.text = "${state.currentSong.title} • ${state.currentSong.artist}"
                            } else {
                                b.tvDjTrackTitle.text = "Spindle Direct ALSA / DSD DAC"
                            }
                            b.tvDjTrackTitle.isSelected = true
                        }
                    }
                }

                // 6. Realistic 15Hz Dynamic Ballistics Simulation for LED VU Meter
                launch {
                    var phase = 0.0
                    while (isActive) {
                        val isPlaying = audioEngine.playbackState.value.isPlaying
                        if (isPlaying) {
                            phase += 0.4
                            val baseLevel = 0.65f + 0.28f * sin(phase).toFloat()
                            val peakJitter = (sin(phase * 2.3) * 0.12f).toFloat()
                            _binding?.ledVuMeter?.audioLevel = (baseLevel + peakJitter).coerceIn(0f, 1f)
                        } else {
                            _binding?.ledVuMeter?.audioLevel = 0.0f
                        }
                        delay(66L) // ~15 FPS VU meter ballistics
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
