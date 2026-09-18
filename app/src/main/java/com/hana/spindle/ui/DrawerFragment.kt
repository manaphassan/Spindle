package com.hana.spindle.ui

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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
import com.hana.spindle.playback.RadioStation
import com.hana.spindle.theme.CassetteTheme
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sin

class DrawerFragment : Fragment() {

    private var _binding: FragmentDrawerBinding? = null
    private val binding get() = _binding!!

    private lateinit var themeManager: ThemeManager
    private lateinit var audioFxController: com.hana.spindle.playback.AudioFxController
    private lateinit var appListLoader: AppListLoader
    private lateinit var appAdapter: AppListAdapter
    private var allApps: List<AppInfo> = emptyList()

    private val dspPresets = listOf("FLAT", "BASS_BOOST", "HARMAN", "VOCAL", "CLUB")
    private var currentPresetIndex = 0
    private var currentTabIndex = 0

    private var audioManager: AudioManager? = null

    // System Volume Broadcast Receiver for real-time hardware key sync
    private val volumeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION" ||
                intent?.action == "android.media.EXTRA_VOLUME_STREAM_VALUE"
            ) {
                syncVolumeSlider()
            }
        }
    }

    // Storage Access Framework Folder Picker Launcher
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val app = requireActivity().application as SpindleApp
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported on older APIs
            }

            val path = getResolvedPathFromUri(uri)
            app.musicScanner.setCustomMusicPath(path)
            updateMusicPathDisplay(app)
            Toast.makeText(requireContext(), "Music path set to: $path", Toast.LENGTH_SHORT).show()
            binding.btnRescanLibrary.performClick()
        }
    }

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
        audioFxController = app.audioEngine.audioFxController
        appListLoader = AppListLoader(requireContext())
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        setupTabNavigation()
        setupAppDrawer()
        setupDjConsole(app)
        setupDefaultLauncher()
        setupImmersiveModeToggle()
        setupDoubleTapSettings()
        setupSleepTimer(app)
        setupThemeSelector()
        setupDapHardwareSettings(app)
        setupRadioStationManager(app)
        setupAudioMetrics(app)
        setupLibraryRescan(app)
        setupAboutGithub()
    }

    override fun onResume() {
        super.onResume()
        syncVolumeSlider()
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        requireContext().registerReceiver(volumeChangeReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(volumeChangeReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    // =========================================================================
    // 1. TOP 3-TAB NAVIGATION
    // =========================================================================
    private fun setupTabNavigation() {
        binding.btnTabApps.setOnClickListener { selectTab(0) }
        binding.btnTabEq.setOnClickListener { selectTab(1) }
        binding.btnTabSettings.setOnClickListener { selectTab(2) }

        selectTab(0)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTabIndex != 0) {
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

        val isDark = themeManager.currentTheme.value.isDarkAppTheme
        val isEink = themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id
        val activeColor = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#F97316"))
        val inactiveColor = ColorStateList.valueOf(if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#202334"))
        val activeText = Color.WHITE
        val inactiveText = if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#2A2E45") else Color.parseColor("#FAFAF9")

        binding.btnTabApps.backgroundTintList = if (index == 0) activeColor else inactiveColor
        binding.btnTabApps.setTextColor(if (index == 0) activeText else inactiveText)

        binding.btnTabEq.backgroundTintList = if (index == 1) activeColor else inactiveColor
        binding.btnTabEq.setTextColor(if (index == 1) activeText else inactiveText)

        binding.btnTabSettings.backgroundTintList = if (index == 2) activeColor else inactiveColor
        binding.btnTabSettings.setTextColor(if (index == 2) activeText else inactiveText)
    }

    // =========================================================================
    // 2. APP DRAWER WITH ALPHABET INDEX RAIL
    // =========================================================================
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
            // Sort installed apps alphabetically
            allApps = appListLoader.loadInstalledApps().sortedBy { it.label.lowercase(Locale.ROOT) }
            appAdapter.submitList(allApps)
        }

        binding.etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Fast-Scroll Alphabet Index Rail
        binding.alphabetIndexView.onLetterSelected = { letter ->
            binding.tvAlphabetPreview.text = letter
            binding.tvAlphabetPreview.visibility = View.VISIBLE

            val targetIndex = if (letter == "#") {
                allApps.indexOfFirst {
                    val firstChar = it.label.firstOrNull() ?: ' '
                    !firstChar.isLetter()
                }
            } else {
                allApps.indexOfFirst {
                    it.label.startsWith(letter, ignoreCase = true)
                }
            }

            if (targetIndex >= 0) {
                (binding.rvApps.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(targetIndex, 0)
            }
        }

        binding.alphabetIndexView.onTouchActiveChanged = { isActive ->
            if (!isActive) {
                binding.tvAlphabetPreview.postDelayed({
                    _binding?.tvAlphabetPreview?.visibility = View.GONE
                }, 150L)
            }
        }
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        appAdapter.submitList(filtered)
    }

    // =========================================================================
    // 3. HARDWARE ACOUSTIC EQ & DSP TUNING DECK WITH MASTER VOLUME
    // =========================================================================
    private fun setupDjConsole(app: SpindleApp) {
        val audioEngine = app.audioEngine
        val fxController = audioEngine.audioFxController

        // 1. Calibrated Rotary Potentiometers: LOW, MID, HI, FILTER
        binding.knobLow.label = "LOW"
        binding.knobLow.minValue = -12f
        binding.knobLow.maxValue = 12f
        binding.knobLow.currentValue = fxController.lowGainDb
        binding.knobLow.onValueChanged = { gainDb ->
            fxController.setLowGain(gainDb)
            binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
            updateEqHeadroomLabel(fxController)
        }

        binding.knobMid.label = "MID"
        binding.knobMid.minValue = -12f
        binding.knobMid.maxValue = 12f
        binding.knobMid.currentValue = fxController.midGainDb
        binding.knobMid.onValueChanged = { gainDb ->
            fxController.setMidGain(gainDb)
            binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
            updateEqHeadroomLabel(fxController)
        }

        binding.knobHi.label = "HI"
        binding.knobHi.minValue = -12f
        binding.knobHi.maxValue = 12f
        binding.knobHi.currentValue = fxController.highGainDb
        binding.knobHi.onValueChanged = { gainDb ->
            fxController.setHighGain(gainDb)
            binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
            updateEqHeadroomLabel(fxController)
        }

        binding.knobFilter.label = "FILTER"
        binding.knobFilter.minValue = 0f
        binding.knobFilter.maxValue = 1000f
        binding.knobFilter.currentValue = fxController.bassBoostStrength.toFloat()
        binding.knobFilter.onValueChanged = { strength ->
            fxController.setFilterStrength(strength.toInt())
        }

        // 2. 10-Band ISO Graphic Equalizer View setup
        binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
        binding.iso10BandEqView.onBandsChanged = { gains ->
            fxController.setAllIsoBands(gains)
            binding.knobLow.currentValue = fxController.lowGainDb
            binding.knobMid.currentValue = fxController.midGainDb
            binding.knobHi.currentValue = fxController.highGainDb
            updateEqHeadroomLabel(fxController)
        }
        binding.iso10BandEqView.onBandDragFinished = {
            binding.iso10BandEqView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        updateEqHeadroomLabel(fxController)

        // 3. AutoEq Target Profile Buttons
        val autoEqButtons = listOf(
            binding.btnEqHarman to "HARMAN",
            binding.btnEqCrinacle to "CRINACLE",
            binding.btnEqMoondrop to "MOONDROP",
            binding.btnEqHd600 to "HD600",
            binding.btnEqWarmTape to "WARM_TUBE",
            binding.btnEqVShape to "V_SHAPE",
            binding.btnEqFlat to "FLAT"
        )

        fun highlightAutoEq(selectedName: String) {
            autoEqButtons.forEach { (btn, name) ->
                val isSelected = name.equals(selectedName, ignoreCase = true)
                btn.setTextColor(if (isSelected) Color.parseColor("#00E676") else Color.parseColor("#A1A1AA"))
                btn.backgroundTintList = ColorStateList.valueOf(
                    if (isSelected) Color.parseColor("#16261B") else Color.parseColor("#181A20")
                )
            }
        }

        autoEqButtons.forEach { (btn, name) ->
            btn.setOnClickListener {
                fxController.applyPreset(name)
                binding.btnFx.text = "FX: ${name.replace("_", " ")}"
                binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
                binding.knobLow.currentValue = fxController.lowGainDb
                binding.knobMid.currentValue = fxController.midGainDb
                binding.knobHi.currentValue = fxController.highGainDb
                binding.knobFilter.currentValue = fxController.bassBoostStrength.toFloat()
                updateEqHeadroomLabel(fxController)
                highlightAutoEq(name)
                btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        // 4. DSP Preset Cycler Button
        binding.btnFx.setOnClickListener {
            currentPresetIndex = (currentPresetIndex + 1) % dspPresets.size
            val preset = dspPresets[currentPresetIndex]
            fxController.applyPreset(preset)
            binding.btnFx.text = "FX: ${preset.replace("_", " ")}"

            binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
            binding.knobLow.currentValue = fxController.lowGainDb
            binding.knobMid.currentValue = fxController.midGainDb
            binding.knobHi.currentValue = fxController.highGainDb
            binding.knobFilter.currentValue = fxController.bassBoostStrength.toFloat()
            updateEqHeadroomLabel(fxController)
            highlightAutoEq(preset)
        }

        // 5. Deck Master RESET Button: Flat EQ, 0 gain, 80% volume
        binding.btnReset.setOnClickListener {
            fxController.setLowGain(0f)
            fxController.setMidGain(0f)
            fxController.setHighGain(0f)
            fxController.setFilterStrength(0)
            fxController.applyPreset("FLAT")
            currentPresetIndex = 0

            binding.iso10BandEqView.resetAll()
            binding.knobLow.currentValue = 0f
            binding.knobMid.currentValue = 0f
            binding.knobHi.currentValue = 0f
            binding.knobFilter.currentValue = 0f
            binding.btnFx.text = "FX: FLAT"
            updateEqHeadroomLabel(fxController)
            highlightAutoEq("FLAT")

            // Reset master output volume to reference 80%
            setMasterOutputVolume(80)
            binding.seekMasterVolume.progress = 80
            updateVolumeLabel(80)

            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Hardware EQ & Master Output Reset to Flat Reference", Toast.LENGTH_SHORT).show()
        }

        // 4. Master Output Volume Fader (Replaces L/R balance slider)
        syncVolumeSlider()
        binding.seekMasterVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setMasterOutputVolume(progress)
                }
                updateVolumeLabel(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        })

        // 5. Bit-Perfect Direct Bypass Switch
        binding.switchBypass.isChecked = fxController.isBypassEnabled
        updateBypassVisuals(fxController.isBypassEnabled)
        binding.switchBypass.setOnCheckedChangeListener { _, isChecked ->
            fxController.setBypass(isChecked)
            updateBypassVisuals(isChecked)
            val msg = if (isChecked) "Bit-Perfect Direct ALSA Bypass Active (0.00% DSP Distortion)" else "DSP Equalization & Target Profile Enabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // 6. Binaural Crossfeed / Soundstage Fader
        binding.seekCrossfeed.progress = fxController.crossfeedStrength / 10
        updateCrossfeedLabel(binding.seekCrossfeed.progress)
        binding.seekCrossfeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    fxController.setCrossfeedStrength(progress * 10)
                }
                updateCrossfeedLabel(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        })

        // 7. Target Curves Quick Buttons
        val curveButtons = mapOf(
            binding.btnCurveHarman to "HARMAN",
            binding.btnCurveDiffuse to "DIFFUSE",
            binding.btnCurveTube to "TUBE",
            binding.btnCurveAir to "AIR",
            binding.btnCurveFlat to "FLAT"
        )
        fun highlightCurve(selected: String) {
            val activeColor = ColorStateList.valueOf(Color.parseColor("#F97316"))
            val inactiveColor = ColorStateList.valueOf(Color.parseColor("#202334"))
            curveButtons.forEach { (btn, name) ->
                val isSel = name.equals(selected, ignoreCase = true)
                btn.backgroundTintList = if (isSel) activeColor else inactiveColor
                btn.setTextColor(if (isSel) Color.WHITE else Color.parseColor("#A1A1AA"))
            }
        }
        highlightCurve("FLAT")

        curveButtons.forEach { (btn, curveName) ->
            btn.setOnClickListener {
                fxController.applyPreset(curveName)
                binding.knobLow.currentValue = fxController.lowGainDb
                binding.knobMid.currentValue = fxController.midGainDb
                binding.knobHi.currentValue = fxController.highGainDb
                binding.knobFilter.currentValue = fxController.bassBoostStrength.toFloat()
                binding.seekCrossfeed.progress = fxController.crossfeedStrength / 10
                updateCrossfeedLabel(binding.seekCrossfeed.progress)
                binding.btnFx.text = "FX: $curveName"
                highlightCurve(curveName)
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        // 8. Observe playback state and animate VU Meter, Waveform, and L/R Peak Meter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    audioEngine.playbackState.collectLatest { state ->
                        _binding?.let { b ->
                            b.waveformView.isPlaying = state.isPlaying
                            b.waveformView.progress = state.progress

                            if (state.currentSong != null) {
                                b.tvDjTrackTitle.text = "${state.currentSong.title} • ${state.currentSong.artist}"
                            } else {
                                b.tvDjTrackTitle.text = "Spindle Direct ALSA / DSD DAC"
                            }
                            b.tvDjTrackTitle.isSelected = true
                        }
                    }
                }

                // 6. Realistic 15Hz Dynamic Ballistics Simulation for LED VU Meter & L/R Peak Meter
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

                            val (leftLvl, rightLvl) = audioEngine.getStereoLevels(totalLevel)
                            _binding?.lrPeakMeter?.leftLevel = leftLvl
                            _binding?.lrPeakMeter?.rightLevel = rightLvl
                        } else {
                            _binding?.ledVuMeter?.audioLevel = 0.0f
                            _binding?.lrPeakMeter?.leftLevel = 0.0f
                            _binding?.lrPeakMeter?.rightLevel = 0.0f
                        }
                        delay(66L)
                    }
                }
            }
        }
    }

    private fun syncVolumeSlider() {
        val am = audioManager ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol <= 0) return
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val progress = ((curVol.toFloat() / maxVol) * 100).toInt().coerceIn(0, 100)
        _binding?.seekMasterVolume?.progress = progress
        updateVolumeLabel(progress)
    }

    private fun setMasterOutputVolume(progress: Int) {
        val am = audioManager ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((progress / 100f) * maxVol).toInt().coerceIn(0, maxVol)
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (e: Exception) {
            // ignore if permissions restrict
        }
    }

    private fun updateVolumeLabel(progress: Int) {
        val dbAttenuation = if (progress <= 0) "-∞" else String.format(Locale.US, "%.1f", 20 * log10(progress / 100f))
        _binding?.tvVolumeLevel?.text = "$progress% • ${dbAttenuation} dB"
    }

    private fun updateBypassVisuals(isBypass: Boolean) {
        _binding?.let { b ->
            if (isBypass) {
                b.tvBypassTitle.setTextColor(Color.parseColor("#00E676"))
                b.tvBypassTitle.text = "● BIT-PERFECT DIRECT BYPASS (ACTIVE)"
                b.tvBypassSub.text = "DSP processing completely disabled for uncolored 0.00% distortion"
                b.knobLow.alpha = 0.35f
                b.knobMid.alpha = 0.35f
                b.knobHi.alpha = 0.35f
                b.knobFilter.alpha = 0.35f
                b.seekCrossfeed.alpha = 0.35f
                b.btnFx.alpha = 0.35f
                b.iso10BandEqView.alpha = 0.35f
                b.layoutAutoEqPills.alpha = 0.35f
            } else {
                b.tvBypassTitle.setTextColor(Color.WHITE)
                b.tvBypassTitle.text = "BIT-PERFECT DIRECT BYPASS"
                b.tvBypassSub.text = "0.00% DSP distortion direct ALSA bypass for USB DACs"
                b.knobLow.alpha = 1.0f
                b.knobMid.alpha = 1.0f
                b.knobHi.alpha = 1.0f
                b.knobFilter.alpha = 1.0f
                b.seekCrossfeed.alpha = 1.0f
                b.btnFx.alpha = 1.0f
                b.iso10BandEqView.alpha = 1.0f
                b.layoutAutoEqPills.alpha = 1.0f
            }
        }
    }

    private fun updateEqHeadroomLabel(fxController: com.hana.spindle.playback.AudioFxController) {
        var maxBoost = 0f
        for (gain in fxController.isoBandsGainDb) {
            if (gain > maxBoost) maxBoost = gain
        }
        val headroom = maxBoost * 0.35f
        _binding?.let { b ->
            if (headroom > 0.05f) {
                b.tvEqHeadroom.text = String.format(Locale.US, "-%.1f dB HEADROOM", headroom)
                b.tvEqHeadroom.setTextColor(Color.parseColor("#FFB74D")) // Amber warning/protection
            } else {
                b.tvEqHeadroom.text = "0.0 dB HEADROOM"
                b.tvEqHeadroom.setTextColor(Color.parseColor("#00E676")) // Safe green
            }
        }
    }

    private fun updateCrossfeedLabel(progress: Int) {
        _binding?.let { b ->
            b.tvCrossfeedLevel.text = if (progress <= 0) "OFF (0%)" else "$progress% • ${progress * 10}ms"
        }
    }

    // =========================================================================
    // 4. GROUPED SETTINGS: 3 CURATED THEMES
    // =========================================================================
    private fun setupThemeSelector() {
        updateThemeButtonsVisual()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                themeManager.currentTheme.collect { theme ->
                    applyDrawerTheme(theme)
                }
            }
        }

        fun switchTheme(theme: CassetteTheme) {
            themeManager.setTheme(theme)
            updateThemeButtonsVisual()
            (requireActivity().application as? SpindleApp)?.audioEngine?.foleyEngine?.playCarriageEject()
        }

        binding.btnThemeDark.setOnClickListener { switchTheme(CassetteTheme.DARK) }
        binding.btnThemeLight.setOnClickListener { switchTheme(CassetteTheme.LIGHT) }
        binding.btnThemeEink.setOnClickListener { switchTheme(CassetteTheme.MONOCHROME_EINK) }
        binding.btnThemeTdk.setOnClickListener { switchTheme(CassetteTheme.TDK_SA_90) }
        binding.btnThemeMaxell.setOnClickListener { switchTheme(CassetteTheme.MAXELL_XLII) }
        binding.btnThemeBasf.setOnClickListener { switchTheme(CassetteTheme.BASF_CHROME) }
        binding.btnThemeSkeleton.setOnClickListener { switchTheme(CassetteTheme.SKELETON_REEL) }
    }

    private fun updateThemeButtonsVisual() {
        val curId = themeManager.currentTheme.value.id
        val isEink = curId == CassetteTheme.MONOCHROME_EINK.id
        val activeColor = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#F97316"))

        binding.btnThemeDark.backgroundTintList = if (curId == CassetteTheme.DARK.id) activeColor else ColorStateList.valueOf(Color.parseColor("#202334"))
        binding.btnThemeLight.backgroundTintList = if (curId == CassetteTheme.LIGHT.id) activeColor else ColorStateList.valueOf(Color.parseColor("#E5E5E2"))
        binding.btnThemeEink.backgroundTintList = if (isEink) activeColor else ColorStateList.valueOf(Color.parseColor("#000000"))

        binding.btnThemeTdk.backgroundTintList = if (curId == CassetteTheme.TDK_SA_90.id) activeColor else ColorStateList.valueOf(Color.parseColor("#242220"))
        binding.btnThemeMaxell.backgroundTintList = if (curId == CassetteTheme.MAXELL_XLII.id) activeColor else ColorStateList.valueOf(Color.parseColor("#261E14"))
        binding.btnThemeBasf.backgroundTintList = if (curId == CassetteTheme.BASF_CHROME.id) activeColor else ColorStateList.valueOf(Color.parseColor("#1F2421"))
        binding.btnThemeSkeleton.backgroundTintList = if (curId == CassetteTheme.SKELETON_REEL.id) activeColor else ColorStateList.valueOf(Color.parseColor("#261418"))

        binding.tvActiveThemeName.text = themeManager.currentTheme.value.name
    }

    private fun setupDapHardwareSettings(app: SpindleApp) {
        val prefs = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)

        // 1. Volume Long-Press Skip
        binding.switchVolumeSkip.isChecked = prefs.getBoolean("pref_volume_skip", true)
        binding.switchVolumeSkip.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_volume_skip", isChecked).apply()
        }

        // 2. Headphone Auto-Pause on Unplug
        binding.switchAutoPauseUnplug.isChecked = prefs.getBoolean("pref_auto_pause_unplug", true)
        binding.switchAutoPauseUnplug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_auto_pause_unplug", isChecked).apply()
        }

        // 3. Headphone Auto-Resume on Plug
        binding.switchAutoResumePlug.isChecked = prefs.getBoolean("pref_auto_resume_plug", false)
        binding.switchAutoResumePlug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_auto_resume_plug", isChecked).apply()
        }

        // 4. Direct USB DAC Bit-Perfect
        binding.switchBitPerfectDirect.isChecked = prefs.getBoolean("pref_bitperfect_direct", true)
        binding.switchBitPerfectDirect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_bitperfect_direct", isChecked).apply()
            app.audioEngine.metricsTracker.updateRouteTelemetry()
        }

        // 5. 1-Bit Monochrome E-Ink Mode
        val isEinkTheme = themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id
        val isEinkPref = prefs.getBoolean("pref_eink_mode", isEinkTheme)
        binding.switchEinkMode.isChecked = isEinkPref
        binding.switchEinkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_eink_mode", isChecked).apply()
            if (isChecked) {
                themeManager.setTheme(CassetteTheme.MONOCHROME_EINK)
            } else if (themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id) {
                themeManager.setTheme(CassetteTheme.DARK)
            }
            updateThemeButtonsVisual()
        }

        // 6. Cassette Mechanical Foley SFX
        binding.switchCassetteFoley.isChecked = app.audioEngine.foleyEngine.isEnabled
        binding.switchCassetteFoley.setOnCheckedChangeListener { _, isChecked ->
            app.audioEngine.foleyEngine.setFoleyEnabled(isChecked)
            if (isChecked) {
                app.audioEngine.foleyEngine.playSwitchSnap()
            }
        }

        // 7. ReplayGain Loudness Calibration
        binding.switchReplayGain.isChecked = app.audioEngine.isReplayGainEnabled
        binding.switchReplayGain.setOnCheckedChangeListener { _, isChecked ->
            app.audioEngine.setReplayGainEnabled(isChecked)
            Toast.makeText(
                requireContext(),
                if (isChecked) "ReplayGain 89dB Calibration Active" else "ReplayGain Normalization Disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun applyDrawerTheme(theme: CassetteTheme) {
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)
        val isDark = theme.isDarkAppTheme
        val primary = theme.textPrimaryColor
        val secondary = theme.textSecondaryColor
        val cardBg = if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#FAFAF9") else Color.parseColor("#171926")

        binding.root.setBackgroundColor(theme.chassisColor)
        binding.tabBar.backgroundTintList = ColorStateList.valueOf(
            if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#171926")
        )
        binding.tvBypassTitle.setTextColor(if (audioFxController.isBypassEnabled) (if (isEink) Color.BLACK else if (isDark) Color.parseColor("#00E676") else Color.parseColor("#059669")) else primary)
        binding.tvBypassSub.setTextColor(secondary)

        updateCardsRecursively(binding.root, cardBg, isEink)
        selectTab(currentTabIndex)
        updateThemeButtonsVisual()
    }

    private fun updateCardsRecursively(view: View, cardBg: Int, isEink: Boolean) {
        if (view is androidx.cardview.widget.CardView) {
            view.setCardBackgroundColor(cardBg)
        }
        if (isEink && view is TextView && view.id != R.id.btnTabApps && view.id != R.id.btnTabEq && view.id != R.id.btnTabSettings) {
            view.setTextColor(Color.BLACK)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                updateCardsRecursively(view.getChildAt(i), cardBg, isEink)
            }
        }
    }

    // =========================================================================
    // 5. GROUPED SETTINGS: IMMERSIVE DISPLAY & LAUNCHER
    // =========================================================================
    private fun setupImmersiveModeToggle() {
        val mainActivity = activity as? MainActivity ?: return
        binding.switchImmersiveMode.isChecked = mainActivity.isImmersiveModeEnabled
        binding.switchImmersiveMode.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.setImmersiveMode(isChecked)
        }
    }

    private fun setupDoubleTapSettings() {
        val prefs = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("pref_double_tap_sleep", true)
        binding.switchDoubleTapSleep.isChecked = isEnabled
        binding.switchDoubleTapSleep.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_double_tap_sleep", isChecked).apply()
        }

        binding.btnSystemTapToWake.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Open Settings > Display to configure native Tap to Wake", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSleepTimer(app: SpindleApp) {
        val audioEngine = app.audioEngine

        binding.btnSleep15m.setOnClickListener {
            audioEngine.startSleepTimer(15)
            Toast.makeText(requireContext(), "Sleep timer set: 15 minutes", Toast.LENGTH_SHORT).show()
        }
        binding.btnSleep30m.setOnClickListener {
            audioEngine.startSleepTimer(30)
            Toast.makeText(requireContext(), "Sleep timer set: 30 minutes", Toast.LENGTH_SHORT).show()
        }
        binding.btnSleep45m.setOnClickListener {
            audioEngine.startSleepTimer(45)
            Toast.makeText(requireContext(), "Sleep timer set: 45 minutes", Toast.LENGTH_SHORT).show()
        }
        binding.btnSleep60m.setOnClickListener {
            audioEngine.startSleepTimer(60)
            Toast.makeText(requireContext(), "Sleep timer set: 60 minutes", Toast.LENGTH_SHORT).show()
        }
        binding.btnSleepEndTrack.setOnClickListener {
            audioEngine.startSleepTimer(0, stopAfterCurrentTrack = true)
            Toast.makeText(requireContext(), "Sleep timer set: End of current track", Toast.LENGTH_SHORT).show()
        }
        binding.btnSleepCancel.setOnClickListener {
            audioEngine.cancelSleepTimer()
            Toast.makeText(requireContext(), "Sleep timer cancelled", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                audioEngine.sleepTimerState.collectLatest { timerState ->
                    _binding?.let { b ->
                        if (timerState.isActive) {
                            val mins = timerState.remainingSeconds / 60
                            val secs = timerState.remainingSeconds % 60
                            val label = if (timerState.stopAfterCurrentTrack) {
                                String.format(Locale.US, "TRACK END (%02d:%02d)", mins, secs)
                            } else {
                                String.format(Locale.US, "%02d:%02d REMAINING", mins, secs)
                            }
                            b.tvSleepTimerStatus.text = label
                            b.tvSleepTimerStatus.setTextColor(Color.parseColor("#00E676"))
                            b.tvSleepTimerStatus.setBackgroundColor(Color.parseColor("#16261B"))
                        } else {
                            b.tvSleepTimerStatus.text = "OFF"
                            b.tvSleepTimerStatus.setTextColor(Color.parseColor("#A1A1AA"))
                            b.tvSleepTimerStatus.setBackgroundColor(Color.parseColor("#1E2026"))
                        }
                    }
                }
            }
        }
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

    // =========================================================================
    // 6. GROUPED SETTINGS: OFFLINE MUSIC PATH SELECTOR & SCANNER
    // =========================================================================
    private fun setupLibraryRescan(app: SpindleApp) {
        updateMusicPathDisplay(app)

        viewLifecycleOwner.lifecycleScope.launch {
            val count = app.database.songDao().getSongCount()
            binding.tvSongCount.text = "$count tracks indexed in local database"
        }

        binding.btnSelectMusicFolder.setOnClickListener {
            try {
                folderPickerLauncher.launch(null)
            } catch (e: Exception) {
                // Fallback direct folder picker dialog
                showDirectFolderChooserDialog(app)
            }
        }

        binding.btnResetMusicFolder.setOnClickListener {
            app.musicScanner.setCustomMusicPath(null)
            updateMusicPathDisplay(app)
            Toast.makeText(requireContext(), "Scan location reset to All Storage", Toast.LENGTH_SHORT).show()
            binding.btnRescanLibrary.performClick()
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

    private fun updateMusicPathDisplay(app: SpindleApp) {
        val custom = app.musicScanner.getCustomMusicPath()
        binding.tvCurrentMusicPath.text = if (!custom.isNullOrBlank()) {
            custom
        } else {
            "All Mounted Storage (Internal & MicroSD)"
        }
    }

    private fun getResolvedPathFromUri(uri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val type = split.getOrNull(0) ?: ""
            val relativePath = if (split.size > 1) split[1] else ""
            if ("primary".equals(type, ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
            } else {
                "/storage/$type/$relativePath"
            }
        } catch (e: Exception) {
            uri.path ?: ""
        }
    }

    private fun showDirectFolderChooserDialog(app: SpindleApp) {
        val paths = mutableListOf<String>()
        val defaultMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath
        if (defaultMusic != null && File(defaultMusic).exists()) paths.add(defaultMusic)
        val extStorage = Environment.getExternalStorageDirectory()?.absolutePath
        if (extStorage != null && File(extStorage).exists()) paths.add(extStorage)
        val storageRoot = File("/storage")
        if (storageRoot.exists() && storageRoot.isDirectory) {
            storageRoot.listFiles()?.forEach { f ->
                if (f.isDirectory && f.canRead() && !f.name.equals("emulated", true) && !f.name.equals("self", true)) {
                    paths.add(f.absolutePath)
                }
            }
        }
        paths.add("Enter custom directory path...")

        AlertDialog.Builder(requireContext())
            .setTitle("Select Offline Music Folder")
            .setItems(paths.toTypedArray()) { _, which ->
                if (which == paths.size - 1) {
                    showCustomPathInputDialog(app)
                } else {
                    val chosen = paths[which]
                    app.musicScanner.setCustomMusicPath(chosen)
                    updateMusicPathDisplay(app)
                    binding.btnRescanLibrary.performClick()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomPathInputDialog(app: SpindleApp) {
        val input = EditText(requireContext()).apply {
            hint = "/storage/emulated/0/Music"
            setPadding(32, 24, 32, 24)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Enter Music Folder Path")
            .setView(input)
            .setPositiveButton("Set Path") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isNotBlank()) {
                    app.musicScanner.setCustomMusicPath(path)
                    updateMusicPathDisplay(app)
                    binding.btnRescanLibrary.performClick()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // =========================================================================
    // 7. GROUPED SETTINGS: FM INTERNET RADIO (ADD & DELETE STATIONS)
    // =========================================================================
    private fun setupRadioStationManager(app: SpindleApp) {
        val container = binding.llRadioStationsList
        container.removeAllViews()

        val stations = app.radioStreamEngine.userStations
        for (station in stations) {
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 16, 20, 16)
                background = ContextCompat.getDrawable(context, R.drawable.bg_card_dark)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 10
                }
                layoutParams = params

                val infoLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val tvName = TextView(context).apply {
                        text = "${station.callsign} (${String.format(Locale.US, "%.1f", station.frequencyMhz)} MHz)"
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val tvUrl = TextView(context).apply {
                        text = "${station.genre} • ${station.streamUrl}"
                        setTextColor(Color.parseColor("#71717A"))
                        textSize = 10.5f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    addView(tvName)
                    addView(tvUrl)
                }
                addView(infoLayout)

                // TUNE Button
                val btnTune = Button(context).apply {
                    text = "TUNE"
                    textSize = 10f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F97316"))
                    val btnParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (34 * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = 8
                    }
                    layoutParams = btnParams
                    setOnClickListener {
                        app.radioStreamEngine.playStation(station)
                        (activity as? MainActivity)?.navigateToRadio()
                    }
                }
                addView(btnTune)

                // DELETE Button
                val btnDelete = Button(context).apply {
                    text = "DEL"
                    textSize = 10f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(Color.parseColor("#FB7185"))
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A2E45"))
                    val btnParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (34 * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = 6
                    }
                    layoutParams = btnParams
                    setOnClickListener {
                        app.radioStreamEngine.removeStation(station.frequencyMhz)
                        setupRadioStationManager(app)
                        Toast.makeText(requireContext(), "Deleted ${station.callsign}", Toast.LENGTH_SHORT).show()
                    }
                }
                addView(btnDelete)
            }
            container.addView(card)
        }

        binding.btnOpenAddStation.setOnClickListener {
            showAddStationDialog(app)
        }

        binding.btnResetStations.setOnClickListener {
            app.radioStreamEngine.resetStationsToDefaults()
            setupRadioStationManager(app)
            Toast.makeText(requireContext(), "Radio presets reset to defaults", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddStationDialog(app: SpindleApp) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        val etFreq = EditText(requireContext()).apply {
            hint = "Frequency in MHz (e.g. 96.5)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etCallsign = EditText(requireContext()).apply {
            hint = "Station Callsign / Name (e.g. JAZZ FM)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val etGenre = EditText(requireContext()).apply {
            hint = "Genre (e.g. Jazz / Chill)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val etUrl = EditText(requireContext()).apply {
            hint = "Stream URL (https://...)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        layout.addView(etFreq)
        layout.addView(etCallsign)
        layout.addView(etGenre)
        layout.addView(etUrl)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Custom Radio Station")
            .setView(layout)
            .setPositiveButton("Save Station") { _, _ ->
                val freq = etFreq.text.toString().toFloatOrNull()
                val callsign = etCallsign.text.toString().trim()
                val genre = etGenre.text.toString().trim()
                val url = etUrl.text.toString().trim()

                if (freq != null && freq > 0f && callsign.isNotBlank() && url.isNotBlank()) {
                    val newStation = RadioStation(
                        frequencyMhz = freq,
                        callsign = callsign,
                        rdsName = "$callsign • LIVE FM STREAM",
                        genre = if (genre.isBlank()) "Online Radio" else genre,
                        streamUrl = url
                    )
                    app.radioStreamEngine.addStation(newStation)
                    setupRadioStationManager(app)
                    Toast.makeText(requireContext(), "Added station $callsign ($freq MHz)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Invalid station details. Please check frequency and URL.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // =========================================================================
    // 8. AUDIO HARDWARE TELEMETRY & ABOUT GITHUB LINK
    // =========================================================================
    private fun setupAudioMetrics(app: SpindleApp) {
        val metricsTracker = app.audioEngine.metricsTracker

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                metricsTracker.metrics.collectLatest { metrics ->
                    _binding?.let { b ->
                        b.tvMetricFormat.text = "${metrics.format.uppercase()} (${metrics.bitDepth}-bit)"
                        b.tvMetricSampleRate.text = "${metrics.bitDepth}-bit / ${String.format(Locale.US, "%.1f", metrics.sampleRate / 1000f)} kHz"
                        b.tvMetricBitrate.text = "${metrics.dynamicBitrateKbps} kbps dynamic"
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
                            b.tvMetricBtBattery.text = "No Bluetooth Gear Connected (Using Direct ALSA)"
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

    private fun setupAboutGithub() {
        binding.btnGithubLink.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/manaphassan/Spindle")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Could not open browser: https://github.com/manaphassan/Spindle", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
