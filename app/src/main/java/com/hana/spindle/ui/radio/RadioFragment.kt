package com.hana.spindle.ui.radio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.FragmentRadioBinding
import com.hana.spindle.playback.RadioStreamEngine
import com.hana.spindle.theme.CassetteTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

/**
 * Bauhaus / Industrial Minimalist Online FM Radio Interface.
 * Features acoustic radial speaker grille with real-time live audio voice coil
 * illumination, hardware internet status LED, 3D cylindrical tuning dial,
 * vintage backlit LCD matrix display, and 60:30:10 tri-theme support.
 */
class RadioFragment : Fragment() {

    private var _binding: FragmentRadioBinding? = null
    private val binding get() = _binding!!

    private val spindleApp: SpindleApp
        get() = requireActivity().application as SpindleApp

    private val radioEngine: RadioStreamEngine
        get() = spindleApp.radioStreamEngine

    private val presetButtons by lazy {
        listOf(
            binding.btnPreset0,
            binding.btnPreset1,
            binding.btnPreset2,
            binding.btnPreset3
        )
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var audioPulseJob: Job? = null

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
        setupPresets()
        setupTuningDial()
        setupSkipButtons()
        setupNetworkMonitoring()
        observeState()
        observeTheme()
        observeBluetoothTelemetry()
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

        // Initial check
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
        val stations = RadioStreamEngine.PRESET_STATIONS
        for (i in presetButtons.indices) {
            if (i < stations.size) {
                val station = stations[i]
                presetButtons[i].text = station.callsign
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
    }

    private fun setupTuningDial() {
        binding.tuningDialView.onFrequencyChanged = { freq ->
            radioEngine.tuneTo(freq)
            updateSignalTelemetry(freq)
        }
        binding.tuningDialView.onDialClicked = {
            radioEngine.togglePlayPause()
        }
        binding.cardLcd.setOnClickListener {
            radioEngine.togglePlayPause()
        }
    }

    private fun updateSignalTelemetry(freq: Float) {
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
        binding.signalMeterView.isStereo = isStereo
    }

    private fun setupSkipButtons() {
        binding.btnTunePrev.setOnClickListener {
            radioEngine.seekPrevStation()
            binding.tuningDialView.currentFreq = radioEngine.radioState.value.currentFrequency
        }
        binding.btnTuneNext.setOnClickListener {
            radioEngine.seekNextStation()
            binding.tuningDialView.currentFreq = radioEngine.radioState.value.currentFrequency
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
            binding.tvRadioNowPlaying.setTextColor(Color.BLACK)
            binding.tvRadioRdsName.setTextColor(Color.BLACK)
        } else if (!isDark) {
            binding.cardLcd.setCardBackgroundColor(Color.parseColor("#2A2E45"))
            binding.tvRadioFrequency.setTextColor(Color.parseColor("#FDE68A"))
            binding.tvRadioNowPlaying.setTextColor(Color.parseColor("#FAFAF9"))
            binding.tvRadioRdsName.setTextColor(Color.parseColor("#FB7185"))
        } else {
            binding.cardLcd.setCardBackgroundColor(Color.parseColor("#171926"))
            binding.tvRadioFrequency.setTextColor(Color.parseColor("#F97316"))
            binding.tvRadioNowPlaying.setTextColor(Color.parseColor("#FAFAF9"))
            binding.tvRadioRdsName.setTextColor(Color.parseColor("#FDE68A"))
        }

        renderPresetButtons(radioEngine.radioState.value)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                radioEngine.radioState.collect { state ->
                    renderState(state)
                }
            }
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
        binding.tvRadioRdsName.isSelected = true // Enables horizontal marquee auto-scroll

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

        // Visualizer inside speaker grille
        binding.speakerGrilleView.isPlaying = state.isPlaying
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
        binding.tvRadioNowPlaying.isSelected = true // Enables horizontal marquee auto-scroll

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
                // Generate dynamic realistic audio envelope synced at 30fps
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
            val station = RadioStreamEngine.PRESET_STATIONS.getOrNull(i)
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
