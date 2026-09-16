package com.hana.spindle.ui

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.FragmentPlayerBinding
import com.hana.spindle.playback.AudioEngine
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var themeManager: ThemeManager
    private lateinit var audioEngine: AudioEngine

    private var isSideA = true

    // Battery Receiver for WM-2 Hardware LED
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 80
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                _binding?.wm2ChassisView?.batteryLevel = batteryPct
                _binding?.wm2ChassisView?.isCharging = isCharging
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
        themeManager = ThemeManager(requireContext())
        audioEngine = app.audioEngine

        setupThemeObservation()
        setupAudioPlaybackObservation()
        setupControls()
        setupCassetteFlip()
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(batteryReceiver)
    }

    private fun setupThemeObservation() {
        viewLifecycleOwner.lifecycleScope.launch {
            themeManager.currentTheme.collectLatest { theme ->
                _binding?.let { b ->
                    b.wm2ChassisView.theme = theme
                    b.cassetteView.theme = theme
                    b.root.setBackgroundColor(theme.chassisColor)
                }
            }
        }
    }

    private fun setupAudioPlaybackObservation() {
        viewLifecycleOwner.lifecycleScope.launch {
            audioEngine.playbackState.collectLatest { state ->
                _binding?.let { b ->
                    b.wm2ChassisView.isPlaying = state.isPlaying
                    b.cassetteView.isPlaying = state.isPlaying
                    b.cassetteView.progress = state.progress

                    state.currentSong?.let { song ->
                        b.cassetteView.trackTitle = song.title
                        b.cassetteView.artistName = song.artist
                    }

                    b.btnPlayPause.text = if (state.isPlaying) "❚❚ PAUSE" else "▶ PLAY"
                    b.tvCurrentTime.text = formatTime(state.currentPositionMs)
                    b.tvTotalDuration.text = formatTime(state.durationMs)
                }
            }
        }
    }

    private fun setupControls() {
        binding.wm2ChassisView.onPlayClicked = { audioEngine.togglePlayPause() }
        binding.wm2ChassisView.onStopClicked = { audioEngine.pause() }

        binding.btnPlayPause.setOnClickListener { audioEngine.togglePlayPause() }
        binding.btnPrev.setOnClickListener { audioEngine.playPrevious() }
        binding.btnNext.setOnClickListener { audioEngine.playNext() }

        binding.wm2ChassisView.onPrevClicked = { audioEngine.playPrevious() }
        binding.wm2ChassisView.onNextClicked = { audioEngine.playNext() }

        // EJECT button slides the view pager smoothly to the Catalog screen (index 2)
        binding.btnEject.setOnClickListener {
            (activity as? MainActivity)?.navigateToCatalog()
        }
    }

    private fun setupCassetteFlip() {
        binding.cassetteView.setOnClickListener {
            val startAngle = if (isSideA) 0f else 180f
            val endAngle = if (isSideA) 180f else 360f

            ObjectAnimator.ofFloat(binding.cassetteView, "flipRotationY", startAngle, endAngle).apply {
                duration = 600L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    if (value >= 90f && isSideA) {
                        isSideA = false
                        binding.cassetteView.isSideA = false
                    } else if (value >= 270f && !isSideA) {
                        isSideA = true
                        binding.cassetteView.isSideA = true
                    }
                }
                start()
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
