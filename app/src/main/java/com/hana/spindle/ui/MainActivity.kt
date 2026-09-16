package com.hana.spindle.ui

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hana.spindle.databinding.ActivityMainBinding
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var themeManager: ThemeManager

    private var isPlaying = false
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

                binding.wm2ChassisView.batteryLevel = batteryPct
                binding.wm2ChassisView.isCharging = isCharging
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        themeManager = ThemeManager(this)

        setupThemeObservation()
        setupControls()
        setupCassetteFlip()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(batteryReceiver)
    }

    private fun setupThemeObservation() {
        lifecycleScope.launch {
            themeManager.currentTheme.collectLatest { theme ->
                binding.wm2ChassisView.theme = theme
                binding.cassetteView.theme = theme
                binding.root.setBackgroundColor(theme.chassisColor)
            }
        }
    }

    private fun setupControls() {
        // Toggle Play/Pause on WM-2 pill levers & bottom button
        val togglePlayAction = {
            isPlaying = !isPlaying
            binding.wm2ChassisView.isPlaying = isPlaying
            binding.cassetteView.isPlaying = isPlaying
            binding.btnPlayPause.text = if (isPlaying) "|| PAUSE" else "> PLAY"
        }

        binding.wm2ChassisView.onPlayClicked = togglePlayAction
        binding.wm2ChassisView.onStopClicked = {
            isPlaying = false
            binding.wm2ChassisView.isPlaying = false
            binding.cassetteView.isPlaying = false
            binding.btnPlayPause.text = "> PLAY"
        }

        binding.btnPlayPause.setOnClickListener { togglePlayAction() }

        binding.btnPrev.setOnClickListener {
            Toast.makeText(this, "Previous Track", Toast.LENGTH_SHORT).show()
        }

        binding.btnNext.setOnClickListener {
            Toast.makeText(this, "Next Track", Toast.LENGTH_SHORT).show()
        }

        binding.btnEject.setOnClickListener {
            Toast.makeText(this, "EJECT: Opening Music Catalog", Toast.LENGTH_SHORT).show()
        }

        binding.wm2ChassisView.onPrevClicked = {
            binding.btnPrev.performClick()
        }

        binding.wm2ChassisView.onNextClicked = {
            binding.btnNext.performClick()
        }
    }

    /**
     * Tapping the cassette triggers a smooth 3D Y-axis camera flip transition
     * between Side A (Player) and Side B (Album Tracklist).
     */
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Ensure pressing Home button always brings user back to player screen
    }
}
