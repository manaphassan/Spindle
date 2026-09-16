package com.hana.spindle.ui.radio

import android.graphics.Color
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
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Braun / Dieter Rams-inspired Neumorphic Online FM Radio Interface.
 * Features acoustic radial speaker grille, 3D cylindrical tuning thumbwheel,
 * vintage backlit LCD matrix display, and live internet stream tuning.
 */
class RadioFragment : Fragment() {

    private var _binding: FragmentRadioBinding? = null
    private val binding get() = _binding!!

    private val radioEngine: RadioStreamEngine
        get() = (requireActivity().application as SpindleApp).radioStreamEngine

    private val presetButtons by lazy {
        listOf(
            binding.btnPreset0,
            binding.btnPreset1,
            binding.btnPreset2,
            binding.btnPreset3
        )
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
        setupPresets()
        setupTuningDial()
        setupSkipButtons()
        observeState()
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
        }
        binding.tuningDialView.onDialClicked = {
            radioEngine.togglePlayPause()
        }
        binding.cardLcd.setOnClickListener {
            radioEngine.togglePlayPause()
        }
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
        // Frequency readout
        binding.tvRadioFrequency.text = String.format(Locale.US, "%.1f", state.currentFrequency)

        // RDS Name marquee
        if (state.currentStation != null) {
            binding.tvRadioRdsName.text = "${state.currentStation.callsign} • ${state.currentStation.rdsName}"
        } else {
            binding.tvRadioRdsName.text = "STATIC DETECTED • TUNING NEEDED"
        }
        binding.tvRadioRdsName.isSelected = true // Enables horizontal marquee auto-scroll

        // Stream status
        binding.tvRadioStreamStatus.text = when {
            state.isBuffering -> "BUFFERING..."
            state.isPlaying -> "ıll LIVE IN"
            else -> "■ STOPPED"
        }
        binding.tvRadioStreamStatus.setTextColor(
            when {
                state.isPlaying -> Color.parseColor("#2D5A27")
                state.isBuffering -> Color.parseColor("#B45309")
                else -> Color.parseColor("#71717A")
            }
        )

        // Highlight active preset button
        for (i in presetButtons.indices) {
            val station = RadioStreamEngine.PRESET_STATIONS.getOrNull(i)
            val isCurrent = (station != null && station.callsign == state.currentStation?.callsign)
            presetButtons[i].setTextColor(
                if (isCurrent) Color.parseColor("#EF4444") else Color.parseColor("#52525B")
            )
            presetButtons[i].backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (isCurrent && state.isPlaying) Color.parseColor("#FFFFFF") else Color.parseColor("#EDEDF2")
            )
        }

        // Sync tuning dial indicator if not dragging
        if (binding.tuningDialView.currentFreq != state.currentFrequency) {
            binding.tuningDialView.currentFreq = state.currentFrequency
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
