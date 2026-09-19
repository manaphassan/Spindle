package com.hana.spindle.ui.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.FragmentRadioBinding
import com.hana.spindle.playback.AnalogFmEngine
import com.hana.spindle.playback.RadioStreamEngine
import com.hana.spindle.theme.CassetteTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

/**
 * Bauhaus / Industrial Minimalist Online & Analog FM Radio Interface.
 * Features acoustic radial speaker grille with real-time live audio voice coil
 * illumination, authentic 2-position vertical rocker switch (FM on TOP, DIGI BELOW),
 * hardware internet status LED, 3D cylindrical tuning dial, RF S-Meter galvanometer,
 * Force Mono noise defeat, channel scan with carrier lock, regional tuning, and dynamic presets.
 */
class RadioFragment : Fragment() {

    private var _binding: FragmentRadioBinding? = null
    private val binding get() = _binding!!

    private val spindleApp: SpindleApp
        get() = requireActivity().application as SpindleApp

    private val radioEngine: RadioStreamEngine
        get() = spindleApp.radioStreamEngine

    private val analogFmEngine by lazy {
        AnalogFmEngine(requireContext())
    }

    private val presetButtons by lazy {
        listOf(
            binding.btnPreset0,
            binding.btnPreset1,
            binding.btnPreset2,
            binding.btnPreset3
        )
    }

    private var isFmMode = false
    private var isForceMono = false
    private var isScanning = false
    private var scanJob: Job? = null
    private val fmPresets = mutableListOf(88.5f, 93.2f, 98.6f, 104.2f)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var audioPulseJob: Job? = null

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                val isPlugged = (state == 1)
                onHeadsetPlugStateChanged(isPlugged)
            }
        }
    }

    private fun isWiredHeadsetPlugged(): Boolean {
        val am = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.isWiredHeadsetOn
    }

    private fun onHeadsetPlugStateChanged(isPlugged: Boolean) {
        if (!isPlugged && isFmMode) {
            stopScan()
            analogFmEngine.stopAnalogAudio()
            isFmMode = false
            binding.speakerGrilleView.radioMode = RadioSpeakerGrilleView.RadioMode.DIGITAL
            updateRadioModeDisplay(RadioSpeakerGrilleView.RadioMode.DIGITAL)
            updatePresetsForActiveMode()
            binding.tvRadioRdsName.text = "ANTENNA UNPLUGGED • FM DISABLED"
            binding.tvRadioStreamStatus.text = "NO ANTENNA"
            binding.tvRadioStreamStatus.setTextColor(Color.parseColor("#EF4444"))
            binding.signalMeterView.signalStrength = 0f
            binding.signalMeterView.centerTuningDeviation = 0f
            binding.signalMeterView.isStereo = false
            binding.speakerGrilleView.isPlaying = false
            binding.speakerGrilleView.isFmActive = false
            stopAudioSync()
            saveRadioSettings()
            Toast.makeText(context ?: return, "Headphone antenna unplugged! FM disabled.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRadioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadRadioSettings()
        setupModeToggle()
        setupFmControls()
        setupPresets()
        setupTuningDial()
        setupSkipButtons()
        setupNetworkMonitoring()
        setupAnalogFmEngine()
        observeState()
        observeTheme()
        observeBluetoothTelemetry()
        applyLoadedSettings()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        requireContext().registerReceiver(headsetReceiver, filter)
        if (isFmMode && !isWiredHeadsetPlugged()) {
            onHeadsetPlugStateChanged(false)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(headsetReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun loadRadioSettings() {
        val prefs = requireContext().getSharedPreferences("spindle_radio_prefs", Context.MODE_PRIVATE)
        val settingsVer = prefs.getInt("radio_settings_version", 0)
        if (settingsVer < 3) {
            prefs.edit()
                .putInt("radio_settings_version", 3)
                .putString("radio_mode", "DIGITAL")
                .putFloat("radio_frequency", 88.5f)
                .putFloat("fm_preset_0", 88.5f)
                .putFloat("fm_preset_1", 93.2f)
                .putFloat("fm_preset_2", 98.6f)
                .putFloat("fm_preset_3", 104.2f)
                .apply()
        }
        isFmMode = (prefs.getString("radio_mode", "DIGITAL") == "FM")
        isForceMono = prefs.getBoolean("radio_force_mono", false)

        fmPresets[0] = prefs.getFloat("fm_preset_0", 88.5f)
        fmPresets[1] = prefs.getFloat("fm_preset_1", 93.2f)
        fmPresets[2] = prefs.getFloat("fm_preset_2", 98.6f)
        fmPresets[3] = prefs.getFloat("fm_preset_3", 104.2f)
    }

    private fun saveRadioSettings() {
        val prefs = requireContext().getSharedPreferences("spindle_radio_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("radio_mode", if (isFmMode) "FM" else "DIGITAL")
            putBoolean("radio_force_mono", isForceMono)
            putFloat("radio_frequency", binding.tuningDialView.currentFreq)
            putFloat("fm_preset_0", fmPresets[0])
            putFloat("fm_preset_1", fmPresets[1])
            putFloat("fm_preset_2", fmPresets[2])
            putFloat("fm_preset_3", fmPresets[3])
            apply()
        }
    }

    private fun applyLoadedSettings() {
        if (isFmMode && !isWiredHeadsetPlugged()) {
            isFmMode = false
            saveRadioSettings()
        }
        binding.speakerGrilleView.radioMode = if (isFmMode) {
            RadioSpeakerGrilleView.RadioMode.FM
        } else {
            RadioSpeakerGrilleView.RadioMode.DIGITAL
        }
        analogFmEngine.isForceMono = isForceMono
        binding.tuningDialView.setBandLimits(AnalogFmEngine.MIN_FREQ, AnalogFmEngine.MAX_FREQ, AnalogFmEngine.STEP_MHZ)

        val prefs = requireContext().getSharedPreferences("spindle_radio_prefs", Context.MODE_PRIVATE)
        val savedFreq = prefs.getFloat("radio_frequency", 88.5f).coerceIn(AnalogFmEngine.MIN_FREQ, AnalogFmEngine.MAX_FREQ)
        binding.tuningDialView.currentFreq = savedFreq

        updateRadioModeDisplay(binding.speakerGrilleView.radioMode)
        updateMonoButtonState()
        updatePresetsForActiveMode()

        if (isFmMode) {
            analogFmEngine.startAnalogAudio()
            analogFmEngine.tuneFrequency(savedFreq)
            syncFmAudio(savedFreq)
        }
    }

    private fun syncFmAudio(freq: Float) {
        if (!isFmMode || isScanning) return
        // STRICT REQUIREMENT: In FM mode, NEVER play internet stream (ExoPlayer)!
        if (radioEngine.radioState.value.isPlaying || radioEngine.radioState.value.isBuffering) {
            radioEngine.pause()
        }
        val strength = analogFmEngine.getSignalStrength(freq)
        val deviation = analogFmEngine.getCarrierDeviation(freq)
        binding.signalMeterView.signalStrength = strength
        binding.signalMeterView.centerTuningDeviation = deviation

        if (strength >= 0.25f) {
            startAudioSync()
            binding.speakerGrilleView.isPlaying = true
            binding.speakerGrilleView.isFmActive = true
        } else {
            stopAudioSync()
            binding.speakerGrilleView.isPlaying = false
            binding.speakerGrilleView.isFmActive = false
        }
    }

    private fun setupModeToggle() {
        binding.speakerGrilleView.onModeChanged = { mode ->
            if (mode == RadioSpeakerGrilleView.RadioMode.FM && !isWiredHeadsetPlugged()) {
                // Revert switch to DIGITAL and alert user
                binding.speakerGrilleView.radioMode = RadioSpeakerGrilleView.RadioMode.DIGITAL
                binding.speakerGrilleView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                binding.tvRadioRdsName.text = "HEADPHONES REQUIRED AS FM ANTENNA"
                binding.tvRadioStreamStatus.text = "NO ANTENNA"
                binding.tvRadioStreamStatus.setTextColor(Color.parseColor("#EF4444"))
                Toast.makeText(
                    requireContext(),
                    "FM requires 3.5mm wired headphones connected as an antenna",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                isFmMode = (mode == RadioSpeakerGrilleView.RadioMode.FM)
                updateRadioModeDisplay(mode)
                updatePresetsForActiveMode()

                if (isFmMode) {
                    // Strictly halt internet streaming audio
                    radioEngine.pause()
                    stopAudioSync()
                    analogFmEngine.startAnalogAudio()
                    val curFreq = binding.tuningDialView.currentFreq
                    analogFmEngine.tuneFrequency(curFreq)
                    syncFmAudio(curFreq)
                } else {
                    stopScan()
                    analogFmEngine.stopAnalogAudio()
                    radioEngine.setVolume(0.8f)
                    binding.signalMeterView.signalStrength = 0f
                    binding.signalMeterView.centerTuningDeviation = 0f
                    binding.signalMeterView.isStereo = false
                    val isAudioActive = radioEngine.radioState.value.isPlaying
                    binding.tvRadioStreamStatus.text = if (isAudioActive) "LIVE IN" else "STOPPED"
                    binding.tvRadioStreamStatus.setTextColor(if (isAudioActive) Color.parseColor("#10B981") else Color.parseColor("#64748B"))
                    renderState(radioEngine.radioState.value)
                }
                saveRadioSettings()
            }
        }
    }

    private fun updateRadioModeDisplay(mode: RadioSpeakerGrilleView.RadioMode) {
        when (mode) {
            RadioSpeakerGrilleView.RadioMode.FM -> {
                binding.tvRadioBandMode.text = if (isForceMono) "FM MONO" else "FM"
                binding.tvRadioFreqUnit.text = " MHz"
                binding.btnRadioMono.visibility = View.VISIBLE
                binding.btnRadioScan.visibility = View.VISIBLE
            }
            RadioSpeakerGrilleView.RadioMode.DIGITAL -> {
                binding.tvRadioBandMode.text = "DIGITAL"
                binding.tvRadioFreqUnit.text = " DAB+"
                binding.btnRadioMono.visibility = View.GONE
                binding.btnRadioScan.visibility = View.GONE
            }
        }
    }

    private fun setupFmControls() {
        binding.btnRadioMono.setOnClickListener {
            if (!isFmMode) return@setOnClickListener
            isForceMono = !isForceMono
            analogFmEngine.isForceMono = isForceMono
            updateMonoButtonState()
            binding.tvRadioBandMode.text = if (isForceMono) "FM MONO" else "FM"
            saveRadioSettings()
        }

        binding.btnRadioScan.setOnClickListener {
            if (!isFmMode) return@setOnClickListener
            if (isScanning) {
                stopScan()
            } else {
                startScan()
            }
        }
    }

    private fun updateMonoButtonState() {
        if (isForceMono) {
            binding.btnRadioMono.text = "[MONO: ON]"
            binding.btnRadioMono.setTextColor(Color.parseColor("#F97316"))
        } else {
            binding.btnRadioMono.text = "[MONO]"
            binding.btnRadioMono.setTextColor(Color.parseColor("#64748B"))
        }
    }

    private fun startScan() {
        if (isScanning) return
        isScanning = true
        radioEngine.pause()
        stopAudioSync()
        binding.btnRadioScan.text = "[STOP]"
        binding.btnRadioScan.setTextColor(Color.parseColor("#FBBF24"))
        binding.tvRadioRdsName.text = "SEARCHING LOCAL STATIONS..."
        binding.tvRadioStreamStatus.text = "SCANNING..."
        binding.tvRadioStreamStatus.setTextColor(Color.parseColor("#FBBF24"))

        scanJob = viewLifecycleOwner.lifecycleScope.launch {
            val step = AnalogFmEngine.STEP_MHZ
            val min = AnalogFmEngine.MIN_FREQ
            val max = AnalogFmEngine.MAX_FREQ
            val startFreq = binding.tuningDialView.currentFreq
            var cur = startFreq

            // Skip current carrier resonance window
            cur += 0.2f
            if (cur > max) cur = min

            while (isActive && isScanning) {
                cur += step
                if (cur > max) {
                    cur = min
                }
                val nextFreq = (kotlin.math.round(cur * 10f) / 10f)
                binding.tuningDialView.currentFreq = nextFreq
                binding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", nextFreq)
                analogFmEngine.tuneFrequency(nextFreq)

                val strength = analogFmEngine.getSignalStrength(nextFreq)
                if (strength >= 0.68f) {
                    // Local station carrier locked!
                    stopScan()
                    analogFmEngine.tuneFrequency(nextFreq)
                    syncFmAudio(nextFreq)
                    highlightActivePreset(nextFreq)
                    saveRadioSettings()
                    break
                }
                delay(60L)
            }
        }
    }

    private fun stopScan() {
        isScanning = false
        scanJob?.cancel()
        scanJob = null
        binding.btnRadioScan.text = "[SCAN]"
        binding.btnRadioScan.setTextColor(Color.parseColor("#94A3B8"))
        if (isFmMode) {
            val curFreq = binding.tuningDialView.currentFreq
            val strength = analogFmEngine.getSignalStrength(curFreq)
            if (strength >= 0.65f) {
                binding.tvRadioStreamStatus.text = "LOCKED"
                binding.tvRadioStreamStatus.setTextColor(Color.parseColor("#10B981"))
            } else {
                binding.tvRadioStreamStatus.text = "OTA LIVE"
                binding.tvRadioStreamStatus.setTextColor(Color.parseColor("#F97316"))
            }
        }
    }

    private fun setupAnalogFmEngine() {
        analogFmEngine.listener = object : AnalogFmEngine.AnalogFmListener {
            override fun onSignalChanged(frequencyMhz: Float, signalStrength: Float, isStereo: Boolean, stationName: String?) {
                if (!isFmMode || isScanning) return
                binding.signalMeterView.signalStrength = signalStrength
                binding.signalMeterView.centerTuningDeviation = analogFmEngine.getCarrierDeviation(frequencyMhz)
                binding.signalMeterView.isStereo = isStereo && !isForceMono
                binding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", frequencyMhz)

                if (stationName != null) {
                    binding.tvRadioRdsName.text = stationName
                    val statusText = when {
                        signalStrength >= 0.65f -> "LOCKED"
                        signalStrength >= 0.25f -> "OTA LIVE"
                        else -> "WEAK SIGNAL"
                    }
                    val statusColor = when {
                        signalStrength >= 0.65f -> Color.parseColor("#10B981")
                        signalStrength >= 0.25f -> Color.parseColor("#F97316")
                        else -> Color.parseColor("#FBBF24")
                    }
                    binding.tvRadioStreamStatus.text = statusText
                    binding.tvRadioStreamStatus.setTextColor(statusColor)
                    binding.tvRadioNowPlaying.text = if (isStereo && !isForceMono) "OTA FM STEREO • LOCAL BROADCAST" else "OTA FM MONO • LOCAL BROADCAST"
                } else {
                    binding.tvRadioRdsName.text = "STATIC DETECTED • TUNING NEEDED"
                    binding.tvRadioStreamStatus.text = "OTA FM"
                    binding.tvRadioStreamStatus.setTextColor(Color.parseColor("#64748B"))
                    binding.tvRadioNowPlaying.text = "ANALOG FM RECEIVER"
                }
            }
        }
    }

    private var isDeviceOnline: Boolean = true

    private fun updateNetworkStatus() {
        val state = radioEngine.radioState.value
        val netState = when {
            !isDeviceOnline -> RadioSpeakerGrilleView.NetworkState.OFFLINE
            state.isBuffering -> RadioSpeakerGrilleView.NetworkState.CONNECTING
            else -> RadioSpeakerGrilleView.NetworkState.ONLINE
        }
        _binding?.speakerGrilleView?.networkState = netState
    }

    private fun setupNetworkMonitoring() {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            isDeviceOnline = true
            updateNetworkStatus()
            return
        }

        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        isDeviceOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        updateNetworkStatus()

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                view?.post {
                    isDeviceOnline = true
                    updateNetworkStatus()
                }
            }

            override fun onLost(network: Network) {
                view?.post {
                    isDeviceOnline = false
                    updateNetworkStatus()
                }
            }

            override fun onUnavailable() {
                view?.post {
                    isDeviceOnline = false
                    updateNetworkStatus()
                }
            }
        }
        networkCallback = callback
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            isDeviceOnline = true
            updateNetworkStatus()
        }
    }

    private fun setupPresets() {
        updatePresetsForActiveMode()
    }

    private fun updatePresetsForActiveMode() {
        if (isFmMode) {
            for (i in presetButtons.indices) {
                val freq = fmPresets.getOrElse(i) { 88.5f }
                presetButtons[i].text = String.format(Locale.US, "%.1f", freq)
                presetButtons[i].setOnClickListener {
                    stopScan()
                    binding.tuningDialView.currentFreq = freq
                    analogFmEngine.tuneFrequency(freq)
                    syncFmAudio(freq)
                    highlightActivePreset(freq)
                    saveRadioSettings()
                }
                presetButtons[i].setOnLongClickListener {
                    val curFreq = binding.tuningDialView.currentFreq
                    fmPresets[i] = curFreq
                    presetButtons[i].text = String.format(Locale.US, "%.1f", curFreq)
                    presetButtons[i].performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    Toast.makeText(
                        requireContext(),
                        "FM Preset P${i + 1} Saved: ${String.format(Locale.US, "%.1f MHz", curFreq)}",
                        Toast.LENGTH_SHORT
                    ).show()
                    saveRadioSettings()
                    highlightActivePreset(curFreq)
                    true
                }
            }
            highlightActivePreset(binding.tuningDialView.currentFreq)
        } else {
            val stations = radioEngine.userStations.ifEmpty { RadioStreamEngine.PRESET_STATIONS }
            for (i in presetButtons.indices) {
                if (i < stations.size) {
                    val station = stations[i]
                    presetButtons[i].text = station.callsign
                    presetButtons[i].setOnLongClickListener(null)
                    presetButtons[i].setOnClickListener {
                        val current = radioEngine.radioState.value.currentStation
                        val isAudioActive = radioEngine.radioState.value.isPlaying || radioEngine.radioState.value.isBuffering
                        if (current?.frequencyMhz == station.frequencyMhz && isAudioActive) {
                            radioEngine.pause()
                        } else {
                            radioEngine.playStation(station)
                            binding.tuningDialView.currentFreq = station.frequencyMhz
                        }
                    }
                }
            }
            renderPresetButtons(radioEngine.radioState.value)
        }
    }

    private fun highlightActivePreset(curFreq: Float) {
        val app = activity?.application as? SpindleApp ?: return
        val theme = app.themeManager.currentTheme.value
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)
        val isDark = theme.isDarkAppTheme

        for (i in presetButtons.indices) {
            val freq = fmPresets.getOrElse(i) { -1f }
            val isMatch = kotlin.math.abs(freq - curFreq) < 0.15f

            val activeColor = if (isEink) Color.BLACK else Color.parseColor("#F97316")
            val inactiveText = if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#2A2E45") else Color.parseColor("#FAFAF9")
            val activeBg = if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#CBD5E1") else Color.parseColor("#2A2E45")
            val inactiveBg = if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#F1F5F9") else Color.parseColor("#202334")

            presetButtons[i].setTextColor(if (isMatch) (if (isEink) Color.WHITE else activeColor) else inactiveText)
            presetButtons[i].backgroundTintList = ColorStateList.valueOf(if (isMatch) activeBg else inactiveBg)
        }
    }

    private fun setupTuningDial() {
        binding.tuningDialView.onFrequencyChanged = { freq ->
            if (isFmMode) {
                analogFmEngine.tuneFrequency(freq)
                syncFmAudio(freq)
                highlightActivePreset(freq)
                saveRadioSettings()
            } else {
                radioEngine.tuneTo(freq)
                updateSignalTelemetry(freq)
            }
        }
        binding.tuningDialView.onDialClicked = {
            if (isFmMode) {
                if (analogFmEngine.isAnalogModeEnabled) {
                    analogFmEngine.stopAnalogAudio()
                    binding.tvRadioStreamStatus.text = "MUTED"
                    binding.tvRadioStreamStatus.setTextColor(Color.parseColor("#64748B"))
                    binding.speakerGrilleView.isPlaying = false
                    stopAudioSync()
                } else {
                    analogFmEngine.startAnalogAudio()
                    syncFmAudio(binding.tuningDialView.currentFreq)
                }
            } else {
                radioEngine.togglePlayPause()
            }
        }
        binding.cardLcd.setOnClickListener {
            if (isFmMode) {
                if (analogFmEngine.isAnalogModeEnabled) {
                    analogFmEngine.stopAnalogAudio()
                    binding.tvRadioStreamStatus.text = "MUTED"
                    binding.tvRadioStreamStatus.setTextColor(Color.parseColor("#64748B"))
                    binding.speakerGrilleView.isPlaying = false
                    stopAudioSync()
                } else {
                    analogFmEngine.startAnalogAudio()
                    syncFmAudio(binding.tuningDialView.currentFreq)
                }
            } else {
                radioEngine.togglePlayPause()
            }
        }
    }

    private fun updateSignalTelemetry(freq: Float) {
        if (isFmMode) return
        val stations = radioEngine.userStations
        var minDistance = Float.MAX_VALUE

        for (st in stations) {
            val dist = kotlin.math.abs(freq - st.frequencyMhz)
            if (dist < minDistance) {
                minDistance = dist
            }
        }

        val maxTuningRange = 0.35f
        val strength: Float
        val isStereo: Boolean

        if (minDistance <= maxTuningRange) {
            val normDist = minDistance / maxTuningRange
            strength = (1.0f - normDist) * 0.90f + 0.10f
            isStereo = (minDistance <= 0.08f)
        } else {
            strength = 0.05f
            isStereo = false
        }

        binding.signalMeterView.signalStrength = strength
        binding.signalMeterView.centerTuningDeviation = 0f
        binding.signalMeterView.isStereo = isStereo
    }

    private fun setupSkipButtons() {
        binding.btnTunePrev.setOnClickListener {
            if (isFmMode) {
                val carriers = analogFmEngine.carrierStations.sortedBy { it.first }
                val cur = binding.tuningDialView.currentFreq
                val prevStation = carriers.lastOrNull { it.first < cur - 0.15f } ?: carriers.lastOrNull()
                if (prevStation != null) {
                    binding.tuningDialView.currentFreq = prevStation.first
                    analogFmEngine.tuneFrequency(prevStation.first)
                    syncFmAudio(prevStation.first)
                    highlightActivePreset(prevStation.first)
                    saveRadioSettings()
                }
            } else {
                radioEngine.seekPrevStation()
                binding.tuningDialView.currentFreq = radioEngine.radioState.value.currentFrequency
            }
        }
        binding.btnTuneNext.setOnClickListener {
            if (isFmMode) {
                val carriers = analogFmEngine.carrierStations.sortedBy { it.first }
                val cur = binding.tuningDialView.currentFreq
                val nextStation = carriers.firstOrNull { it.first > cur + 0.15f } ?: carriers.firstOrNull()
                if (nextStation != null) {
                    binding.tuningDialView.currentFreq = nextStation.first
                    analogFmEngine.tuneFrequency(nextStation.first)
                    syncFmAudio(nextStation.first)
                    highlightActivePreset(nextStation.first)
                    saveRadioSettings()
                }
            } else {
                radioEngine.seekNextStation()
                binding.tuningDialView.currentFreq = radioEngine.radioState.value.currentFrequency
            }
        }
    }

    private fun observeTheme() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                spindleApp.themeManager.currentTheme.collect { theme ->
                    applyTheme(theme)
                }
            }
        }
    }

    private fun applyTheme(theme: CassetteTheme) {
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)
        val isDark = theme.isDarkAppTheme
        binding.root.setBackgroundColor(theme.chassisColor)

        binding.speakerGrilleView.isDarkMode = isDark
        binding.speakerGrilleView.isEink = isEink

        binding.tuningDialView.isDarkMode = isDark
        binding.tuningDialView.isEink = isEink

        binding.signalMeterView.isDarkMode = isDark
        binding.signalMeterView.isEink = isEink

        val skipBgColor = when {
            isEink -> Color.WHITE
            isDark -> Color.parseColor("#1F222C")
            else -> Color.parseColor("#DCDCD8")
        }
        val skipBg = ColorStateList.valueOf(skipBgColor)
        binding.btnTunePrev.backgroundTintList = skipBg
        binding.btnTuneNext.backgroundTintList = skipBg

        val skipTint = ColorStateList.valueOf(if (isEink) Color.BLACK else theme.textPrimaryColor)
        binding.btnTunePrev.imageTintList = skipTint
        binding.btnTuneNext.imageTintList = skipTint

        val presetCardBg = if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#181A22")
        binding.cardPresets.setCardBackgroundColor(presetCardBg)

        if (isEink) {
            binding.cardLcd.setCardBackgroundColor(Color.WHITE)
            binding.tvRadioFrequency.setTextColor(Color.BLACK)
            binding.tvRadioFreqUnit.setTextColor(Color.BLACK)
            binding.tvRadioBandMode.setTextColor(Color.BLACK)
            binding.tvRadioNowPlaying.setTextColor(Color.BLACK)
            binding.tvRadioRdsName.setTextColor(Color.BLACK)
        } else if (!isDark) {
            binding.cardLcd.setCardBackgroundColor(Color.parseColor("#2A2E45"))
            binding.tvRadioFrequency.setTextColor(Color.parseColor("#FDE68A"))
            binding.tvRadioFreqUnit.setTextColor(Color.parseColor("#FDE68A"))
            binding.tvRadioBandMode.setTextColor(Color.parseColor("#94A3B8"))
            binding.tvRadioNowPlaying.setTextColor(Color.parseColor("#FAFAF9"))
            binding.tvRadioRdsName.setTextColor(Color.parseColor("#FB7185"))
        } else {
            binding.cardLcd.setCardBackgroundColor(Color.parseColor("#171926"))
            binding.tvRadioFrequency.setTextColor(Color.parseColor("#F97316"))
            binding.tvRadioFreqUnit.setTextColor(Color.parseColor("#FBBF24"))
            binding.tvRadioBandMode.setTextColor(Color.parseColor("#94A3B8"))
            binding.tvRadioNowPlaying.setTextColor(Color.parseColor("#FAFAF9"))
            binding.tvRadioRdsName.setTextColor(Color.parseColor("#FDE68A"))
        }

        if (isFmMode) {
            highlightActivePreset(binding.tuningDialView.currentFreq)
        } else {
            renderPresetButtons(radioEngine.radioState.value)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                radioEngine.radioState.collect { state ->
                    if (!isFmMode) {
                        renderState(state)
                    } else {
                        renderFmPlaybackState(state)
                    }
                }
            }
        }
    }

    private fun renderFmPlaybackState(state: com.hana.spindle.playback.RadioPlaybackState) {
        updateNetworkStatus()
        val strength = binding.signalMeterView.signalStrength
        val isLocked = strength >= 0.65f
        val isCarrierTuned = strength >= 0.25f

        val isFmActive = analogFmEngine.isAnalogModeEnabled
        binding.speakerGrilleView.isFmActive = isFmActive
        binding.speakerGrilleView.isPlaying = isFmActive && isCarrierTuned

        if (isFmActive && isCarrierTuned) {
            startAudioSync()
            binding.tvRadioStreamStatus.text = if (isLocked) "LOCKED" else "OTA LIVE"
            binding.tvRadioStreamStatus.setTextColor(
                if (isLocked) Color.parseColor("#10B981") else Color.parseColor("#F97316")
            )
        } else if (isFmActive) {
            stopAudioSync()
            binding.tvRadioStreamStatus.text = "OTA FM"
            binding.tvRadioStreamStatus.setTextColor(Color.parseColor("#64748B"))
        } else {
            stopAudioSync()
            binding.tvRadioStreamStatus.text = "MUTED"
            binding.tvRadioStreamStatus.setTextColor(Color.parseColor("#64748B"))
        }
    }

    private fun renderState(state: com.hana.spindle.playback.RadioPlaybackState) {
        updateNetworkStatus()
        updateSignalTelemetry(state.currentFrequency)

        // Frequency readout
        binding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", state.currentFrequency)

        // RDS Name marquee
        if (state.currentStation != null) {
            binding.tvRadioRdsName.text = "${state.currentStation.callsign} • ${state.currentStation.rdsName}"
        } else {
            binding.tvRadioRdsName.text = "STATIC DETECTED • TUNING NEEDED"
        }
        binding.tvRadioRdsName.isSelected = true

        val isEink = spindleApp.themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id

        // Stream status
        binding.tvRadioStreamStatus.text = when {
            state.isBuffering -> "BUFFERING..."
            state.isPlaying -> "LIVE IN"
            else -> "STOPPED"
        }
        binding.tvRadioStreamStatus.setTextColor(
            if (isEink) Color.BLACK else when {
                state.isPlaying -> Color.parseColor("#F97316")
                state.isBuffering -> Color.parseColor("#FDE68A")
                else -> Color.parseColor("#64748B")
            }
        )

        // Visualizer & FM Status Diode inside speaker grille
        binding.speakerGrilleView.isPlaying = state.isPlaying
        binding.speakerGrilleView.isFmActive = state.isPlaying || state.isBuffering
        if (state.isPlaying) {
            startAudioSync()
        } else {
            stopAudioSync()
        }

        // Now Playing Title
        val nowPlaying = when {
            state.isBuffering -> "CONNECTING LIVE STREAM..."
            !state.isPlaying -> "STOPPED"
            !state.nowPlayingTitle.isNullOrBlank() -> state.nowPlayingTitle
            state.currentStation != null -> "LIVE BROADCAST • ${state.currentStation.genre.uppercase()}"
            else -> "LIVE FM TUNER"
        }
        binding.tvRadioNowPlaying.text = nowPlaying
        binding.tvRadioNowPlaying.isSelected = true

        // Highlight active preset button
        renderPresetButtons(state)

        // Sync tuning dial indicator if not dragging
        if (binding.tuningDialView.currentFreq != state.currentFrequency) {
            binding.tuningDialView.currentFreq = state.currentFrequency
        }
    }

    private fun startAudioSync() {
        if (audioPulseJob?.isActive == true) return
        audioPulseJob = viewLifecycleOwner.lifecycleScope.launch {
            var step = 0f
            while (isActive) {
                step += 0.2f
                val envelope = (0.35f + 0.35f * sin(step) + 0.25f * sin(step * 2.3f + 1.1f)).coerceIn(0.15f, 0.95f)
                _binding?.speakerGrilleView?.liveAudioLevel = envelope
                delay(33L)
            }
        }
    }

    private fun stopAudioSync() {
        audioPulseJob?.cancel()
        audioPulseJob = null
        _binding?.speakerGrilleView?.liveAudioLevel = 0f
    }

    private fun renderPresetButtons(state: com.hana.spindle.playback.RadioPlaybackState) {
        val app = activity?.application as? SpindleApp ?: return
        val theme = app.themeManager.currentTheme.value
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)
        val isDark = theme.isDarkAppTheme

        for (i in presetButtons.indices) {
            val station = radioEngine.userStations.ifEmpty { RadioStreamEngine.PRESET_STATIONS }.getOrNull(i)
            val isCurrent = (station != null && station.callsign == state.currentStation?.callsign)
            val activeColor = if (isEink) Color.BLACK else theme.accentColor
            val inactiveText = if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#2A2E45") else Color.parseColor("#FAFAF9")
            val activeBg = if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#CBD5E1") else Color.parseColor("#2A2E45")
            val inactiveBg = if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#F1F5F9") else Color.parseColor("#202334")

            presetButtons[i].setTextColor(
                if (isCurrent) (if (isEink) Color.WHITE else activeColor) else inactiveText
            )
            presetButtons[i].backgroundTintList = ColorStateList.valueOf(
                if (isCurrent && state.isPlaying) activeBg else inactiveBg
            )
        }
    }

    private fun observeBluetoothTelemetry() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                spindleApp.audioEngine.metricsTracker.metrics.collect { metrics ->
                    _binding?.let { b ->
                        val isEink = spindleApp.themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id
                        if (metrics.isBluetoothConnected) {
                            b.tvRadioBtBadge.visibility = View.VISIBLE
                            val devName = metrics.bluetoothDeviceName?.takeIf { it.isNotBlank() } ?: "BT"
                            val bat = if (metrics.bluetoothBatteryPct != null && metrics.bluetoothBatteryPct >= 0) " (${metrics.bluetoothBatteryPct}%)" else ""
                            b.tvRadioBtBadge.text = "$devName$bat"
                            b.tvRadioBtBadge.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#10B981"))
                        } else {
                            b.tvRadioBtBadge.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAudioSync()
        stopScan()
        analogFmEngine.release()
        try {
            requireContext().unregisterReceiver(headsetReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        networkCallback?.let {
            val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            try {
                cm?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        networkCallback = null
        _binding = null
    }
}
