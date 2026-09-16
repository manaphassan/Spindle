package com.hana.spindle.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.ActivityMainBinding
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main Android Home Launcher Activity hosting a seamless 3-screen ViewPager2:
 *
 * Page 0: Left Drawer (App Drawer, Audio Metrics Telemetry, EQ & Themes)
 * Page 1: Center Home Screen (Sony Walkman II Red Chassis & Kinetic Cassette Player)
 * Page 2: Right Catalog (Music Library: Folders, Albums, Artists, Songs, Star Ratings)
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as SpindleApp
        themeManager = ThemeManager(this)

        setupViewPager()
        setupThemeObservation()
        setupBackNavigation()
        checkAndRequestStoragePermissions(app)
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> DrawerFragment()
                    1 -> PlayerFragment()
                    2 -> CatalogFragment()
                    else -> PlayerFragment()
                }
            }
        }

        // Center default: Main Cassette Player screen
        binding.viewPager.setCurrentItem(1, false)
        binding.viewPager.offscreenPageLimit = 2
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
                if (binding.viewPager.currentItem != 1) {
                    // Return back to Main Cassette Player screen
                    navigateToPlayer()
                } else {
                    // Already on main launcher screen; do not exit
                }
            }
        })
    }

    fun navigateToPlayer() {
        binding.viewPager.setCurrentItem(1, true)
    }

    fun navigateToCatalog() {
        binding.viewPager.setCurrentItem(2, true)
    }

    fun navigateToDrawer() {
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
