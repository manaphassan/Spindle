package com.hana.spindle.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.ActivityMainBinding
import com.hana.spindle.theme.ThemeManager
import com.hana.spindle.ui.radio.RadioFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main Android Home Launcher Activity hosting a seamless 3-screen ViewPager2:
 *
 * Page 0: Left Drawer (App Drawer, Audio Metrics Telemetry, Dark DJ Mixer EQ Console)
 * Page 1: Center Home Screen (Sony Walkman II Chassis & Kinetic Cassette Player)
 * Page 2: Right Online FM Radio (Braun Neumorphic Tuner with Live ExoPlayer Streaming)
 *
 * Overlay: Full-Screen Audiophile Music Catalog triggered via the Mechanical ⏏ EJECT Button.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        const val PREF_IMMERSIVE_STATUS_BAR = "pref_immersive_status_bar"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var themeManager: ThemeManager
    var isImmersiveModeEnabled: Boolean = true
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as SpindleApp
        themeManager = app.themeManager

        val prefs = getSharedPreferences("spindle_prefs", MODE_PRIVATE)
        isImmersiveModeEnabled = prefs.getBoolean(PREF_IMMERSIVE_STATUS_BAR, true)
        applyImmersiveFlags()

        setupViewPager()
        setupCatalogContainer()
        setupThemeObservation()
        setupBackNavigation()
        checkAndRequestStoragePermissions(app)
    }

    fun setImmersiveMode(enable: Boolean) {
        isImmersiveModeEnabled = enable
        getSharedPreferences("spindle_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_IMMERSIVE_STATUS_BAR, enable)
            .apply()
        applyImmersiveFlags()
    }

    fun applyImmersiveFlags() {
        if (isImmersiveModeEnabled) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_VISIBLE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (isImmersiveModeEnabled) {
            applyImmersiveFlags()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isImmersiveModeEnabled) {
            applyImmersiveFlags()
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> DrawerFragment()
                    1 -> PlayerFragment()
                    2 -> RadioFragment()
                    else -> PlayerFragment()
                }
            }
        }

        // Center default: Main Cassette Player screen
        binding.viewPager.setCurrentItem(1, false)
        binding.viewPager.offscreenPageLimit = 2
    }

    private fun setupCatalogContainer() {
        // Initially container is gone
        binding.catalogContainer.visibility = View.GONE
    }

    private fun setupThemeObservation() {
        lifecycleScope.launch {
            themeManager.currentTheme.collectLatest { theme ->
                binding.viewPager.setBackgroundColor(theme.chassisColor)
                window.statusBarColor = theme.chassisColor
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.catalogContainer.visibility == View.VISIBLE) {
                    // Close Catalog and return to Cassette Deck
                    navigateToPlayer()
                } else if (binding.viewPager.currentItem != 1) {
                    // Return back to Main Cassette Player screen
                    navigateToPlayer()
                } else {
                    // Already on main launcher screen; do not exit
                }
            }
        })
    }

    fun navigateToPlayer() {
        binding.catalogContainer.visibility = View.GONE
        val frag = supportFragmentManager.findFragmentById(R.id.catalogContainer)
        if (frag != null) {
            supportFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
        }
        binding.viewPager.setCurrentItem(1, true)
    }

    fun navigateToCatalog() {
        binding.catalogContainer.visibility = View.VISIBLE
        binding.catalogContainer.bringToFront()
        supportFragmentManager.beginTransaction()
            .replace(R.id.catalogContainer, CatalogFragment())
            .commitAllowingStateLoss()
    }

    fun navigateToRadio() {
        binding.catalogContainer.visibility = View.GONE
        val frag = supportFragmentManager.findFragmentById(R.id.catalogContainer)
        if (frag != null) {
            supportFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
        }
        binding.viewPager.setCurrentItem(2, true)
    }

    fun navigateToDrawer() {
        binding.catalogContainer.visibility = View.GONE
        val frag = supportFragmentManager.findFragmentById(R.id.catalogContainer)
        if (frag != null) {
            supportFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
        }
        binding.viewPager.setCurrentItem(0, true)
    }

    private fun checkAndRequestStoragePermissions(app: SpindleApp) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            triggerBackgroundScan(app)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val app = application as SpindleApp
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Storage access granted. Indexing library...", Toast.LENGTH_SHORT).show()
            triggerBackgroundScan(app)
        } else {
            Toast.makeText(this, "Storage permission required to index music", Toast.LENGTH_LONG).show()
        }
    }

    fun triggerBackgroundScan(app: SpindleApp) {
        lifecycleScope.launch {
            app.musicScanner.scanAll()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Ensure pressing hardware/software Home button always brings user to Cassette Player
        navigateToPlayer()
    }
}
