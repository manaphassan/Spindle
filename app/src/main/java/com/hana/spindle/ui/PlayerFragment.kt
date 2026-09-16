package com.hana.spindle.ui

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
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
import com.hana.spindle.theme.ChassisStyle
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
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
        themeManager = app.themeManager
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
                    val isWm2 = theme.chassisStyle == ChassisStyle.WM2_RED
                    b.verticalDeckContainer.visibility = if (isWm2) View.GONE else View.VISIBLE
                    b.wm2Container.visibility = if (isWm2) View.VISIBLE else View.GONE

                    b.verticalDeckView.theme = theme
                    b.verticalCassetteView.theme = theme

                    val isVaporwave = theme.chassisStyle == ChassisStyle.VAPORWAVE_80S
                    b.tvVerticalTime.setTextColor(if (isVaporwave) Color.parseColor("#475569") else Color.parseColor("#80FFFFFF"))

                    b.wm2ChassisView.theme = theme
                    b.wm2CassetteView.theme = theme
                    b.root.setBackgroundColor(theme.chassisColor)
                }
            }
        }
    }

    private fun setupAudioPlaybackObservation() {
        viewLifecycleOwner.lifecycleScope.launch {
            audioEngine.playbackState.collectLatest { state ->
                _binding?.let { b ->
                    // 1. Vertical Walkman Deck (Reference 1 & 2)
                    b.verticalDeckView.isPlaying = state.isPlaying
                    b.verticalDeckView.progress = state.progress
                    b.verticalCassetteView.isPlaying = state.isPlaying
                    b.verticalCassetteView.progress = state.progress

                    // 2. WM-2 Red Layout
                    b.wm2ChassisView.isPlaying = state.isPlaying
                    b.wm2CassetteView.isPlaying = state.isPlaying
                    b.wm2CassetteView.progress = state.progress

                    state.currentSong?.let { song ->
                        b.verticalCassetteView.trackTitle = song.title
                        b.verticalCassetteView.artistName = song.artist
                        b.wm2CassetteView.trackTitle = song.title
                        b.wm2CassetteView.artistName = song.artist
                    }

                    val curTimeStr = formatTime(state.currentPositionMs)
                    val totalTimeStr = formatTime(state.durationMs)
                    b.tvVerticalTime.text = "$curTimeStr / $totalTimeStr"

                    b.btnPlayPause.text = if (state.isPlaying) "❚❚ PAUSE" else "▶ PLAY"
                    b.tvCurrentTime.text = curTimeStr
                    b.tvTotalDuration.text = totalTimeStr
                }
            }
        }
    }

    private fun setupControls() {
        // Vertical Deck Controls (Reference 1 & 2)
        binding.verticalDeckView.onPlayClicked = {
            if (audioEngine.playbackState.value.currentSong == null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val app = requireActivity().application as SpindleApp
                    val songs = app.database.songDao().getAllSongs().firstOrNull()
                    if (!songs.isNullOrEmpty()) {
                        audioEngine.playQueue(songs, 0)
                    } else {
                        audioEngine.togglePlayPause()
                    }
                }
            } else {
                audioEngine.togglePlayPause()
            }
        }
        binding.verticalDeckView.onPrevClicked = { audioEngine.playPrevious() }
        binding.verticalDeckView.onNextClicked = { audioEngine.playNext() }
        binding.verticalDeckView.onSeek = { progress ->
            val total = audioEngine.playbackState.value.durationMs
            if (total > 0) {
                audioEngine.seekTo((total * progress).toLong())
            }
        }
        binding.verticalDeckView.onEjectClicked = {
            (activity as? MainActivity)?.navigateToCatalog()
        }

        // WM-2 Red Controls
        binding.wm2ChassisView.onPlayClicked = { audioEngine.togglePlayPause() }
        binding.wm2ChassisView.onStopClicked = { audioEngine.pause() }
        binding.btnPlayPause.setOnClickListener { audioEngine.togglePlayPause() }
        binding.btnPrev.setOnClickListener { audioEngine.playPrevious() }
        binding.btnNext.setOnClickListener { audioEngine.playNext() }
        binding.wm2ChassisView.onPrevClicked = { audioEngine.playPrevious() }
        binding.wm2ChassisView.onNextClicked = { audioEngine.playNext() }
        binding.btnEject.setOnClickListener {
            (activity as? MainActivity)?.navigateToCatalog()
        }
    }

    private fun setupCassetteFlip() {
        binding.verticalCassetteView.setOnClickListener {
            flipCassette(binding.verticalCassetteView)
        }
        binding.wm2CassetteView.setOnClickListener {
            flipCassette(binding.wm2CassetteView)
        }
    }

    private fun flipCassette(view: com.hana.spindle.ui.cassette.CassetteView) {
        val startAngle = if (isSideA) 0f else 180f
        val endAngle = if (isSideA) 180f else 360f

        ObjectAnimator.ofFloat(view, "flipRotationY", startAngle, endAngle).apply {
            duration = 600L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                if (value >= 90f && isSideA) {
                    isSideA = false
                    view.isSideA = false
                } else if (value >= 270f && !isSideA) {
                    isSideA = true
                    view.isSideA = true
                }
            }
            start()
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
