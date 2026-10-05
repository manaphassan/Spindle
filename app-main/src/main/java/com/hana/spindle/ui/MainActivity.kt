package com.hana.spindle.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.ActivityMainBinding
import com.hana.spindle.theme.ThemeManager
import com.hana.spindle.ui.radio.RadioFragment
import com.hana.spindle.playback.HardwareKeyController
import com.hana.spindle.receiver.HardwareButtonReceiver
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main Android Home Launcher Activity hosting a seamless 3-screen ViewPager2:
 *
 * Page 0: Left Drawer (App Drawer, Audio Metrics Telemetry, Studio DJ Mixer EQ Console)
 * Page 1: Center Home Screen (Classic 1980s Chassis & Kinetic Cassette Player)
 * Page 2: Right Online FM Radio (Bauhaus Industrial Tuner with Live ExoPlayer Streaming)
 *
 * Overlay: Full-Screen Audiophile Music Catalog triggered via the Mechanical EJECT Button.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        const val PREF_IMMERSIVE_STATUS_BAR = "pref_immersive_status_bar"
        const val PREF_DOUBLE_TAP_SLEEP = "pref_double_tap_sleep"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var themeManager: ThemeManager
    var isImmersiveModeEnabled: Boolean = true
        private set
    private var previousBrightness: Float = -1f

    private val hardwareKeyController = HardwareKeyController(
        onNavigateToCatalog = { navigateToCatalog(openNowPlaying = false) },
        onNavigateToDrawer = { navigateToDrawer() }
    )

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
        setupAmbientOverlay()
        setupThemeObservation()
        setupBackNavigation()
        if (intent?.getStringExtra("navigate") == "radio") {
            binding.viewPager.post {
                navigateToRadio()
            }
        } else if (intent?.getStringExtra("navigate") == "drawer") {
            binding.viewPager.post {
                navigateToDrawer()
            }
        } else if (intent?.getStringExtra("navigate") == "catalog") {
            val tab = intent.getIntExtra("tab", -1)
            val openNp = intent.getBooleanExtra("open_now_playing", false)
            binding.viewPager.post {
                navigateToCatalog(openNowPlaying = openNp, tab = tab)
            }
        }
        handlePlaybackIntent(intent)
        checkAndRequestStoragePermissions(app)
    }

    private fun handlePlaybackIntent(intent: Intent?) {
        val playPath = intent?.getStringExtra("play_path")
        if (!playPath.isNullOrBlank()) {
            val file = java.io.File(playPath)
            if (file.exists() && file.canRead()) {
                val app = application as SpindleApp
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val track = com.hana.spindle.data.TagParser.parseTrack(file)
                    if (track != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            app.audioEngine.playTrack(track)
                            binding.viewPager.setCurrentItem(1, false)
                        }
                    } else {
                        android.util.Log.e("MainActivity", "Failed to parse track: $playPath")
                    }
                }
            }
        }
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
        syncPlayerFragmentState()
        HardwareButtonReceiver.buttonListener = { keyEvent ->
            val engine = (application as? SpindleApp)?.audioEngine
            if (engine != null) {
                if (keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    hardwareKeyController.onKeyDown(keyEvent.keyCode, keyEvent, this, engine)
                } else if (keyEvent.action == android.view.KeyEvent.ACTION_UP) {
                    hardwareKeyController.onKeyUp(keyEvent.keyCode, keyEvent, this, engine)
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        HardwareButtonReceiver.buttonListener = null
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
                    0 -> RadioFragment()
                    1 -> PlayerFragment()
                    2 -> DrawerFragment()
                    else -> PlayerFragment()
                }
            }
        }

        // Tactile hardware depth page transformer
        binding.viewPager.setPageTransformer { page, position ->
            when {
                position < -1 -> {
                    page.alpha = 0f
                }
                position <= 0 -> {
                    page.alpha = 1f + position * 0.25f
                    val scaleFactor = 0.96f + (1 - kotlin.math.abs(position)) * 0.04f
                    page.scaleX = scaleFactor
                    page.scaleY = scaleFactor
                }
                position <= 1 -> {
                    page.alpha = 1f - position * 0.25f
                    val scaleFactor = 0.96f + (1 - kotlin.math.abs(position)) * 0.04f
                    page.scaleX = scaleFactor
                    page.scaleY = scaleFactor
                }
                else -> {
                    page.alpha = 0f
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

    private fun setupAmbientOverlay() {
        val ambientGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                exitAmbientWake()
                return true
            }
            override fun onDown(e: MotionEvent): Boolean = true
        })

        binding.ambientOverlayContainer.setOnTouchListener { _, event ->
            ambientGestureDetector.onTouchEvent(event)
            true // Consume touches while ambient mode is displayed
        }

        // Live track updates while in ambient mode
        val app = application as? SpindleApp
        if (app != null) {
            lifecycleScope.launch {
                app.audioEngine.playbackState.collectLatest { state ->
                    if (binding.ambientOverlayContainer.visibility == View.VISIBLE) {
                        state.currentTrack?.let { track ->
                            binding.tvAmbientTrack.text = "${track.title} — ${track.artist}"
                        } ?: run {
                            binding.tvAmbientTrack.text = "Spindle Ambient Deck"
                        }
                    }
                }
            }
        }
    }

    fun enterAmbientSleep() {
        val prefs = getSharedPreferences("spindle_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_DOUBLE_TAP_SLEEP, true)) return

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.tvAmbientClock.text = timeFormat.format(Date())

        val app = application as? SpindleApp
        val currentTrack = app?.audioEngine?.playbackState?.value?.currentTrack
        if (currentTrack != null) {
            binding.tvAmbientTrack.text = "${currentTrack.title} — ${currentTrack.artist}"
        } else {
            binding.tvAmbientTrack.text = "Spindle Ambient Deck"
        }

        // Dim display to 1% for low power OLED/E-Ink sleep
        val lp = window.attributes
        previousBrightness = lp.screenBrightness
        lp.screenBrightness = 0.01f
        window.attributes = lp

        binding.ambientOverlayContainer.visibility = View.VISIBLE
        binding.ambientOverlayContainer.alpha = 0f
        binding.ambientOverlayContainer.animate()
            .alpha(1f)
            .setDuration(250)
            .start()
    }

    fun exitAmbientWake() {
        val lp = window.attributes
        lp.screenBrightness = if (previousBrightness >= 0f) previousBrightness else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp

        binding.ambientOverlayContainer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.ambientOverlayContainer.visibility = View.GONE
            }
            .start()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.ambientOverlayContainer.visibility == View.VISIBLE) {
                    exitAmbientWake()
                    return
                }
                if (binding.catalogContainer.visibility == View.VISIBLE) {
                    val catalogFrag = supportFragmentManager.findFragmentById(R.id.catalogContainer) as? CatalogFragment
                    if (catalogFrag != null && catalogFrag.handleBackPressed()) {
                        return
                    }
                    // Close Catalog and return to Cassette Deck
                    navigateToPlayer()
                } else if (binding.viewPager.currentItem != 1) {
                    // Return back to Main Cassette Player screen
                    navigateToPlayer()
                } else {
                    val playerFrag = getPlayerFragment()
                    if (playerFrag != null && playerFrag.handleBackPressed()) {
                        return
                    }
                    // Already on main launcher screen; do not exit
                }
            }
        })
    }

    fun navigateToPlayer() {
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.setCurrentItem(1, false)
        syncPlayerFragmentState()
        if (binding.catalogContainer.visibility == View.VISIBLE) {
            binding.catalogContainer.animate()
                .translationY(binding.root.height.toFloat())
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    binding.catalogContainer.visibility = View.GONE
                    val frag = supportFragmentManager.findFragmentById(R.id.catalogContainer)
                    if (frag != null) {
                        supportFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
                    }
                    syncPlayerFragmentState()
                }.start()
        } else {
            binding.catalogContainer.visibility = View.GONE
            val frag = supportFragmentManager.findFragmentById(R.id.catalogContainer)
            if (frag != null) {
                supportFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
            }
        }
    }

    fun syncPlayerFragmentState() {
        supportFragmentManager.fragments.forEach { frag ->
            if (frag is PlayerFragment) {
                frag.syncState()
            } else {
                frag.childFragmentManager.fragments.forEach { child ->
                    if (child is PlayerFragment) child.syncState()
                }
            }
        }
    }

    private fun getPlayerFragment(): PlayerFragment? {
        supportFragmentManager.fragments.forEach { frag ->
            if (frag is PlayerFragment) return frag
            frag.childFragmentManager.fragments.forEach { child ->
                if (child is PlayerFragment) return child
            }
        }
        return null
    }

    fun getCurrentViewPagerItem(): Int = binding.viewPager.currentItem

    fun navigateToNowPlaying() {
        navigateToCatalog(openNowPlaying = true)
    }

    fun navigateToVault(openNowPlaying: Boolean = false, tab: Int = -1) {
        navigateToCatalog(openNowPlaying, tab)
    }

    fun navigateToCatalog(openNowPlaying: Boolean = false, tab: Int = -1) {
        binding.viewPager.isUserInputEnabled = false
        binding.catalogContainer.visibility = View.VISIBLE
        binding.catalogContainer.bringToFront()
        val h = binding.root.height.toFloat().let { if (it > 0) it else 800f }
        binding.catalogContainer.translationY = h
        binding.catalogContainer.alpha = 0f
        binding.catalogContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        supportFragmentManager.beginTransaction()
            .replace(R.id.catalogContainer, CatalogFragment.newInstance(openNowPlaying = openNowPlaying, initialTab = tab))
            .commitAllowingStateLoss()
    }

    fun navigateToRadio() {
        binding.catalogContainer.visibility = View.GONE
        val frag = supportFragmentManager.findFragmentById(R.id.catalogContainer)
        if (frag != null) {
            supportFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
        }
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.setCurrentItem(0, false)
    }

    fun navigateToDrawer() {
        binding.catalogContainer.visibility = View.GONE
        val frag = supportFragmentManager.findFragmentById(R.id.catalogContainer)
        if (frag != null) {
            supportFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
        }
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.setCurrentItem(2, false)
    }

    private fun checkAndRequestStoragePermissions(app: SpindleApp) {
        val permissionsToRequest = mutableListOf<String>()

        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, storagePermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(storagePermission)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            triggerBackgroundScan(app)
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val app = application as SpindleApp
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val storageIndex = permissions.indexOf(storagePermission)
            if (storageIndex >= 0 && grantResults.getOrNull(storageIndex) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage access granted. Indexing library...", Toast.LENGTH_SHORT).show()
                triggerBackgroundScan(app)
            }
            app.audioEngine.metricsTracker.updateRouteTelemetry()
        }
    }

    fun triggerBackgroundScan(app: SpindleApp) {
        lifecycleScope.launch {
            app.musicScanner.scanAll()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlaybackIntent(intent)
        if (intent.getStringExtra("navigate") == "radio") {
            binding.viewPager.post { navigateToRadio() }
            return
        }
        if (intent.getStringExtra("navigate") == "drawer") {
            binding.viewPager.post { navigateToDrawer() }
            return
        }
        if (intent.getStringExtra("navigate") == "catalog") {
            val tab = intent.getIntExtra("tab", -1)
            val openNp = intent.getBooleanExtra("open_now_playing", false)
            binding.viewPager.post { navigateToCatalog(openNowPlaying = openNp, tab = tab) }
            return
        }
        // Ensure pressing hardware/software Home button always brings user to Cassette Player
        navigateToPlayer()
    }



    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val engine = (application as? SpindleApp)?.audioEngine
        if (engine != null && hardwareKeyController.onKeyDown(keyCode, event, this, engine)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val engine = (application as? SpindleApp)?.audioEngine
        if (engine != null && hardwareKeyController.onKeyLongPress(keyCode, event, this, engine)) {
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val engine = (application as? SpindleApp)?.audioEngine
        if (engine != null && hardwareKeyController.onKeyUp(keyCode, event, this, engine)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
