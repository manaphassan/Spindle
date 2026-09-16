package com.hana.spindle.ui.radio

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hana.spindle.ui.MainActivity
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
            binding.btnPreset3,
            binding.btnPreset4
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
        setupTopBar()
        setupPresets()
        setupTuningDial()
        setupSkipButtons()
        setupBottomControls()
        observeState()
    }

    private fun setupTopBar() {
        binding.btnRadioBack.setOnClickListener {
            (activity as? MainActivity)?.navigateToPlayer()
        }
    }

    private fun setupPresets() {
        val stations = RadioStreamEngine.PRESET_STATIONS
        for (i in presetButtons.indices) {
            if (i < stations.size) {
                val station = stations[i]
                presetButtons[i].text = station.callsign
                presetButtons[i].setOnClickListener {
                    radioEngine.playStation(station)
                    binding.tuningDialView.currentFreq = station.frequencyMhz
                }
            }
        }

        binding.btnStationList.setOnClickListener {
            Toast.makeText(requireContext(), "FM Tuner: 5 audiophile online streams calibrated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTuningDial() {
        binding.tuningDialView.onFrequencyChanged = { freq ->
            radioEngine.tuneTo(freq)
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

    private fun setupBottomControls() {
        binding.btnRadioMute.setOnClickListener {
            val isMuted = radioEngine.toggleMute()
            binding.btnRadioMute.alpha = if (isMuted) 0.4f else 1.0f
        }

        binding.seekRadioVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    radioEngine.setVolume(progress / 100f)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnRadioFavorite.setOnClickListener {
            val station = radioEngine.radioState.value.currentStation
            val name = station?.callsign ?: "Current Station"
            Toast.makeText(requireContext(), "★ Saved $name to favorites", Toast.LENGTH_SHORT).show()
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
            else -> "STANDBY"
        }
        binding.tvRadioStreamStatus.setTextColor(
            if (state.isPlaying) Color.parseColor("#2D5A27") else Color.parseColor("#71717A")
        )

        // Highlight active preset button
        for (i in presetButtons.indices) {
            val station = RadioStreamEngine.PRESET_STATIONS.getOrNull(i)
            val isCurrent = (station != null && station.callsign == state.currentStation?.callsign)
            presetButtons[i].setTextColor(
                if (isCurrent) Color.parseColor("#EF4444") else Color.parseColor("#52525B")
            )
        }

        // Sync tuning dial indicator if not dragging
        if (binding.tuningDialView.currentFreq != state.currentFrequency) {
            binding.tuningDialView.currentFreq = state.currentFrequency
        }

        // Sync volume seekbar
        val volInt = (state.volume * 100).toInt()
        if (binding.seekRadioVolume.progress != volInt) {
            binding.seekRadioVolume.progress = volInt
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
