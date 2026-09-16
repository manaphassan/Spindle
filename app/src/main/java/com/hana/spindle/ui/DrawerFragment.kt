package com.hana.spindle.ui

import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
    private var currentTabIndex = 0

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

        setupTabNavigation()
        setupAppDrawer()
        setupDjConsole(app)
        setupDefaultLauncher()
        setupImmersiveModeToggle()
        setupThemeSelector()
        setupRadioStationManager(app)
        setupAudioMetrics(app)
        setupLibraryRescan(app)
    }

    private fun setupTabNavigation() {
        binding.btnTabApps.setOnClickListener { selectTab(0) }
        binding.btnTabEq.setOnClickListener { selectTab(1) }
        binding.btnTabSettings.setOnClickListener { selectTab(2) }

        selectTab(0)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTabIndex != 0) {
                    // Switch back to Apps tab first
                    selectTab(0)
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun selectTab(index: Int) {
        currentTabIndex = index
        binding.containerApps.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.containerEq.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.containerSettings.visibility = if (index == 2) View.VISIBLE else View.GONE

        val activeColor = ColorStateList.valueOf(Color.parseColor("#E53935")) // Walkman Red
        val inactiveColor = ColorStateList.valueOf(Color.parseColor("#1F2128"))

        binding.btnTabApps.backgroundTintList = if (index == 0) activeColor else inactiveColor
        binding.btnTabApps.setTextColor(if (index == 0) Color.WHITE else Color.parseColor("#A1A1AA"))

        binding.btnTabEq.backgroundTintList = if (index == 1) activeColor else inactiveColor
        binding.btnTabEq.setTextColor(if (index == 1) Color.WHITE else Color.parseColor("#A1A1AA"))

        binding.btnTabSettings.backgroundTintList = if (index == 2) activeColor else inactiveColor
        binding.btnTabSettings.setTextColor(if (index == 2) Color.WHITE else Color.parseColor("#A1A1AA"))
    }

    private fun setupDefaultLauncher() {
        binding.btnSetDefaultLauncher.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e2: Exception) {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(Intent.createChooser(homeIntent, "Select Spindle as Default Home Launcher"))
                }
            }
        }
    }

    private fun setupImmersiveModeToggle() {
        val mainActivity = activity as? MainActivity ?: return
        binding.switchImmersiveMode.isChecked = mainActivity.isImmersiveModeEnabled
        binding.switchImmersiveMode.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.setImmersiveMode(isChecked)
        }
    }

    private fun setupRadioStationManager(app: SpindleApp) {
        val container = binding.llRadioStationsList
        container.removeAllViews()

        val stations = app.radioStreamEngine.userStations
        for (station in stations) {
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 20, 24, 20)
                background = ContextCompat.getDrawable(context, R.drawable.bg_card_dark)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 14
                }
                layoutParams = params

                val infoLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val tvName = TextView(context).apply {
                        text = "${station.callsign} (${String.format(Locale.US, "%.1f", station.frequencyMhz)} MHz)"
                        setTextColor(Color.WHITE)
                        textSize = 13.5f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val tvUrl = TextView(context).apply {
                        text = "${station.genre}\n${station.streamUrl}"
                        setTextColor(Color.parseColor("#71717A"))
                        textSize = 10.5f
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    addView(tvName)
                    addView(tvUrl)
                }
                addView(infoLayout)

                val btnTune = Button(context).apply {
                    text = "TUNE"
                    textSize = 10.5f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
                    val btnParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (36 * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = 12
                    }
                    layoutParams = btnParams
                    setOnClickListener {
                        app.radioStreamEngine.playStation(station)
                        (activity as? MainActivity)?.navigateToRadio()
                    }
                }
                addView(btnTune)
            }
            container.addView(card)
        }

        binding.btnResetStations.setOnClickListener {
            app.radioStreamEngine.resetStationsToDefaults()
            setupRadioStationManager(app)
            Toast.makeText(requireContext(), "Radio presets reset to default", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLibraryRescan(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = app.database.songDao().getSongCount()
            binding.tvSongCount.text = "$count tracks indexed in local database"
        }

        binding.btnRescanLibrary.setOnClickListener {
            binding.btnRescanLibrary.isEnabled = false
            binding.btnRescanLibrary.text = "SCANNING STORAGE..."
            binding.progressScan.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                app.musicScanner.scanAll()
                val count = app.database.songDao().getSongCount()
                binding.tvSongCount.text = "$count tracks indexed in local database"
                binding.btnRescanLibrary.text = "RESCAN MUSIC STORAGE"
                binding.btnRescanLibrary.isEnabled = true
                binding.progressScan.visibility = View.GONE
                Toast.makeText(requireContext(), "Scan complete: $count tracks ready", Toast.LENGTH_SHORT).show()
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        appAdapter.submitList(filtered)
    }

    private fun setupAudioMetrics(app: SpindleApp) {
        val metricsTracker = app.audioEngine.metricsTracker

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                metricsTracker.metrics.collectLatest { metrics ->
                    _binding?.let { b ->
                        b.tvMetricFormat.text = "${metrics.format.uppercase()} (${metrics.bitDepth}-bit)"
                        b.tvMetricSampleRate.text = "${metrics.bitDepth}-bit / ${String.format(Locale.US, "%.1f", metrics.sampleRate / 1000f)} kHz"
                        b.tvMetricBitrate.text = "${metrics.dynamicBitrateKbps} kbps"
                        b.tvMetricRoute.text = metrics.outputRoute

                        if (metrics.bluetoothDeviceName != null && metrics.bluetoothBatteryPct != null) {
                            val batteryStatus = when {
                                metrics.bluetoothBatteryPct < 20 -> "Low"
                                metrics.bluetoothBatteryPct < 50 -> "Adequate"
                                else -> "Healthy"
                            }
                            b.tvMetricBtBattery.text = "● ${metrics.bluetoothDeviceName}: ${metrics.bluetoothBatteryPct}% ($batteryStatus)"
                            b.tvMetricBtBattery.setTextColor(
                                if (metrics.bluetoothBatteryPct < 20) Color.parseColor("#EF4444") else Color.parseColor("#00E676")
                            )
                        } else {
                            b.tvMetricBtBattery.text = "No Bluetooth Gear Connected (Using 3.5mm ALSA Direct)"
                            b.tvMetricBtBattery.setTextColor(Color.parseColor("#A1A1AA"))
                        }

                        if (metrics.isBitPerfect) {
                            b.tvBitPerfectBadge.text = "● BIT-PERFECT NATIVE"
                            b.tvBitPerfectBadge.setTextColor(Color.parseColor("#00E676"))
                        } else {
                            b.tvBitPerfectBadge.text = "▲ AudioFlinger Resampled"
                            b.tvBitPerfectBadge.setTextColor(Color.parseColor("#F59E0B"))
                        }
                    }
                }
            }
        }
    }

    private fun setupThemeSelector() {
        val container = binding.llThemeButtons
        container.removeAllViews()

        val density = resources.displayMetrics.density
        CassetteTheme.ALL_PRESETS.forEach { theme ->
            val btn = Button(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (42 * density).toInt()
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
                text = "${theme.name}\n${theme.subtitle}"
                textSize = 10.5f
                isAllCaps = false
                setPadding((14 * density).toInt(), (4 * density).toInt(), (14 * density).toInt(), (4 * density).toInt())
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

        // 3. RESET Button (replaces CUE) - restores default Flat EQ & 50% Balance
        binding.btnReset.setOnClickListener {
            fxController.setLowGain(0f)
            fxController.setMidGain(0f)
            fxController.setHighGain(0f)
            fxController.setFilterStrength(0)
            fxController.applyPreset("FLAT")
            currentPresetIndex = 0

            binding.knobLow.currentValue = 0f
            binding.knobMid.currentValue = 0f
            binding.knobHi.currentValue = 0f
            binding.knobFilter.currentValue = 0f
            binding.btnFx.text = "FX: FLAT"

            // Reset L & R Audio Balance to Center (50%)
            binding.seekCrossfader.progress = 50
            audioEngine.setBalance(0.5f)

            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "EQ and Audio Balance reset to default", Toast.LENGTH_SHORT).show()
        }

        binding.btnDjPlayPause.setOnClickListener {
            audioEngine.togglePlayPause()
        }

        binding.btnBrowse.setOnClickListener {
            (activity as? MainActivity)?.navigateToCatalog()
        }

        // 4. L and R Audio Balance Slider (replaces A/B crossfader)
        binding.seekCrossfader.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val balance = progress / 100f
                audioEngine.setBalance(balance)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        })

        // 5. Observe playback state and animate VU Meter, Waveform, and L/R Voltage Peak Meter
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

                // 6. Realistic 15Hz Dynamic Ballistics Simulation for LED VU Meter & L/R Peak Spectrum Meter
                launch {
                    var phase = 0.0
                    while (isActive) {
                        val isLocalPlaying = audioEngine.playbackState.value.isPlaying
                        val isRadioPlaying = app.radioStreamEngine.radioState.value.isPlaying
                        val isAudioActive = isLocalPlaying || isRadioPlaying

                        if (isAudioActive) {
                            phase += 0.4
                            val baseLevel = 0.65f + 0.28f * sin(phase).toFloat()
                            val peakJitter = (sin(phase * 2.3) * 0.12f).toFloat()
                            val totalLevel = (baseLevel + peakJitter).coerceIn(0f, 1f)

                            _binding?.ledVuMeter?.audioLevel = totalLevel

                            // Modulate L and R voltage levels by the audio balance slider
                            val (leftLvl, rightLvl) = audioEngine.getStereoLevels(totalLevel)
                            _binding?.lrPeakMeter?.leftLevel = leftLvl
                            _binding?.lrPeakMeter?.rightLevel = rightLvl
                        } else {
                            _binding?.ledVuMeter?.audioLevel = 0.0f
                            _binding?.lrPeakMeter?.leftLevel = 0.0f
                            _binding?.lrPeakMeter?.rightLevel = 0.0f
                        }
                        delay(66L) // ~15 FPS VU & peak meter ballistics
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
