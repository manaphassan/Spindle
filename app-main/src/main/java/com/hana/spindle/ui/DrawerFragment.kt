package com.hana.spindle.ui

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.FragmentDrawerBinding
import com.hana.spindle.launcher.AppInfo
import com.hana.spindle.ui.catalog.MixtapeDialogs
import com.hana.spindle.launcher.AppListAdapter
import com.hana.spindle.launcher.AppListLoader
import com.hana.spindle.launcher.AppPropertiesDialog
import com.hana.spindle.launcher.RecentAppsManager
import com.hana.spindle.core.AudioDspConstants
import com.hana.spindle.core.CrossfadeMode
import com.hana.spindle.licensing.StudioUnlockManager
import com.hana.spindle.licensing.StudioUnlockManager.StudioFeature
import com.hana.spindle.core.ReplayGainMode
import com.hana.spindle.playback.AudioFxController
import com.hana.spindle.playback.RadioStation
import com.hana.spindle.theme.CassetteTheme
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sin

class DrawerFragment : Fragment() {

    private var _binding: FragmentDrawerBinding? = null
    private val binding get() = _binding!!

    private lateinit var themeManager: ThemeManager
    private lateinit var audioFxController: com.hana.spindle.playback.AudioFxController
    private lateinit var appListLoader: AppListLoader
    private lateinit var appAdapter: AppListAdapter
    private lateinit var recentAppsManager: RecentAppsManager
    private var allApps: List<AppInfo> = emptyList()


    private val dspPresets = listOf("FLAT", "BASS_BOOST", "HARMAN", "VOCAL", "CLUB")
    private var currentPresetIndex = 0
    private var currentTabIndex = PANEL_SOUND
    private var openSettingsPage = -1

    // Walkman deck style: brushed chassis, SPINDLE band and FUNCTION switch around the pages.
    private var isDeckStyle = false
    private var deckColorway = com.hana.spindle.ui.cassette.WalkmanColorway.SILVER
    private var chassisDrawable: com.hana.spindle.ui.cassette.WalkmanChassisDrawable? = null
    private val deckStyleListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == com.hana.spindle.ui.cassette.WalkmanColorway.PREF_DECK_STYLE) applyDeckStyle()
    }

    private companion object {
        const val PANEL_SOUND = 0
        const val PANEL_SETTINGS = 1
        const val PANEL_APPS = 2
        const val PANEL_COUNT = 3
    }

    /** System Hub pivots are full pages: one panel per viewport width, no peek. */
    private fun drawerPanelWidth(): Int =
        resources.displayMetrics.widthPixels - if (isDeckStyle) 2 * deckWindowInsetPx() else 0

    /** Window margin plus frame thickness on each side of the panorama in deck style. */
    private fun deckWindowInsetPx(): Int = ((12 + 6) * resources.displayMetrics.density).toInt()

    /** One entry of the Zune settings menu and the cards its page shows. */
    private class SettingsPage(val title: String, val summary: String, val cards: List<View>)

    private fun settingsPages(): List<SettingsPage> =
        listOf(
            SettingsPage("display", "deck style, theme, status bar", listOf(binding.settingsCardDisplay)),
            SettingsPage(
                "playback",
                "buttons, dac, replaygain, crossfade, sounds",
                listOf(binding.settingsCardPlayback)
            ),
            SettingsPage("sleep timer", "fade out and stop", listOf(binding.settingsCardSleep)),
            SettingsPage("output", "source, headphone jack, bluetooth", listOf(binding.settingsCardTelemetry)),
            SettingsPage("library", "music folders, rescan, art and lyrics", listOf(binding.settingsCardLibrary)),
            SettingsPage("radio", "internet stations", listOf(binding.settingsCardRadio)),
            SettingsPage("gestures", "double tap and ambient mode", listOf(binding.settingsCardGestures)),
            SettingsPage("about", "default launcher, studio, version", listOf(binding.settingsCardAbout))
        )

    private var syncAllEqControlsAction: (() -> Unit)? = null
    private var audioManager: AudioManager? = null

    // System Volume Broadcast Receiver for real-time hardware key sync
    private val volumeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION" ||
                intent?.action == "android.media.EXTRA_VOLUME_STREAM_VALUE"
            ) {
                syncVolumeSlider()
            }
        }
    }

    // Storage Access Framework Folder Picker Launcher
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val app = requireActivity().application as SpindleApp
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported on older APIs
            }

            val path = getResolvedPathFromUri(uri)
            app.musicScanner.setCustomMusicPath(path)
            updateMusicPathDisplay(app)
            Toast.makeText(requireContext(), "Music path set to: $path", Toast.LENGTH_SHORT).show()
            binding.btnRescanLibrary.performClick()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as SpindleApp
        themeManager = app.themeManager
        audioFxController = app.audioEngine.audioFxController
        appListLoader = AppListLoader(requireContext())
        recentAppsManager = RecentAppsManager(requireContext())
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        applyDeckStyle()
        setupDeckHeader(app)
        requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(deckStyleListener)
        setupTabNavigation()
        setupDrawerPanorama()
        setupSettingsMenu()
        setupAppDrawer()
        setupDjConsole(app)
        setupDefaultLauncher()
        setupImmersiveModeToggle()
        setupDoubleTapSettings()
        setupSleepTimer(app)
        setupThemeSelector()
        setupDapHardwareSettings(app)
        setupRadioStationManager(app)
        setupAudioMetrics(app)
        setupLibraryRescan(app)
        setupStudioCollectorSuite()
        setupAboutGithub()
    }

    override fun onResume() {
        super.onResume()
        syncVolumeSlider()
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        requireContext().registerReceiver(volumeChangeReceiver, filter)
        reloadApps()
        syncAllEqControlsAction?.invoke()
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(volumeChangeReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    // =========================================================================
    // 1. ZUNE PIVOTS: sound · settings · apps
    // =========================================================================
    private fun setupTabNavigation() {
        binding.btnTabEq.setOnClickListener { selectTab(PANEL_SOUND) }
        binding.btnTabApps.setOnClickListener { selectTab(PANEL_APPS) }
        binding.btnTabSettings.setOnClickListener { selectTab(PANEL_SETTINGS) }

        highlightDrawerTab(PANEL_SOUND)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val mainAct = activity as? MainActivity
                val isDrawerActive = mainAct?.let { it.getCurrentViewPagerItem() == 2 } ?: false
                if (!isDrawerActive) {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                    return
                }
                when {
                    currentTabIndex == PANEL_SETTINGS && openSettingsPage >= 0 -> showSettingsMenu()
                    currentTabIndex != PANEL_SOUND -> selectTab(PANEL_SOUND)
                    else -> mainAct.navigateToPlayer()
                }
            }
        })
    }

    private fun setupDrawerPanorama() {
        applyPanelWidths()

        binding.drawerPanoramaScrollView.post {
            val b = _binding ?: return@post
            b.drawerPanoramaScrollView.scrollTo(0, 0)
            highlightDrawerTab(PANEL_SOUND)
        }

        // Touch disambiguation: vertical list/scroll vs horizontal panorama swipe
        setupTouchDisambiguation(binding.rvApps, binding.drawerPanoramaScrollView)
        setupTouchDisambiguation(binding.containerEq, binding.drawerPanoramaScrollView)
        setupTouchDisambiguation(binding.scrollSettingsContent, binding.drawerPanoramaScrollView)

        // The panorama keeps every horizontal swipe from the outer ViewPager2, except a
        // rightward swipe on the first page (sound), which goes back to the deck.
        binding.drawerPanoramaScrollView.handOffAtStart = true

        // Kinetic parallax title + active tab tracking
        binding.drawerPanoramaScrollView.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            val panelWidth = drawerPanelWidth()
            if (isDeckStyle) binding.systemDeckHeader.pagePosition = scrollX.toFloat() / panelWidth
            val activeIdx = ((scrollX + panelWidth / 2) / panelWidth).coerceIn(0, 2)
            if (activeIdx != currentTabIndex) {
                currentTabIndex = activeIdx
                highlightDrawerTab(activeIdx)
            }
        }

    }

    private fun setupTouchDisambiguation(targetView: View, hsv: HorizontalScrollView) {
        var downX = 0f
        var downY = 0f
        val touchSlop = ViewConfiguration.get(targetView.context).scaledTouchSlop

        if (targetView is RecyclerView) {
            targetView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = e.x
                            downY = e.y
                            hsv.requestDisallowInterceptTouchEvent(false)
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = kotlin.math.abs(e.x - downX)
                            val dy = kotlin.math.abs(e.y - downY)
                            if (dy > dx && dy > touchSlop) {
                                hsv.requestDisallowInterceptTouchEvent(true)
                            } else if (dx > dy && dx > touchSlop) {
                                hsv.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                    }
                    return false
                }
            })
        } else {
            targetView.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.x
                        downY = e.y
                        hsv.requestDisallowInterceptTouchEvent(false)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = kotlin.math.abs(e.x - downX)
                        val dy = kotlin.math.abs(e.y - downY)
                        if (dy > dx && dy > touchSlop) {
                            hsv.requestDisallowInterceptTouchEvent(true)
                        } else if (dx > dy && dx > touchSlop) {
                            hsv.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
                false
            }
        }
    }

    fun scrollToDrawerPanel(index: Int) {
        currentTabIndex = index.coerceIn(0, PANEL_COUNT - 1)
        val targetX = currentTabIndex * drawerPanelWidth()
        binding.drawerPanoramaScrollView.post {
            _binding?.drawerPanoramaScrollView?.smoothScrollTo(targetX, 0)
        }
        highlightDrawerTab(currentTabIndex)
    }

    fun highlightDrawerTab(index: Int) {
        currentTabIndex = index
        val theme = themeManager.currentTheme.value
        val isEink = theme.id == CassetteTheme.MONOCHROME_EINK.id
        val isLight = ColorUtils.calculateLuminance(theme.chassisColor) > 0.5
        val activeText = if (isLight || isEink) Color.BLACK else Color.WHITE
        val inactiveText = if (isLight || isEink) Color.DKGRAY else Color.WHITE

        val tabs = listOf(binding.btnTabEq, binding.btnTabSettings, binding.btnTabApps)
        tabs.forEachIndexed { i, tv ->
            val isActive = (i == index)
            // Zune pivots keep one weight and size; only brightness marks the active pivot.
            tv.alpha = if (isActive) 1.0f else 0.55f
            tv.setTextColor(if (isActive) activeText else inactiveText)
        }

        ZunePivotHeader.arrange(binding.tabScrollView, binding.tabBar, tabs, index)
    }

    private fun selectTab(index: Int) {
        scrollToDrawerPanel(index)
    }

    // =========================================================================
    // WALKMAN DECK STYLE: chassis + band + FUNCTION switch around the same pages
    // =========================================================================
    private fun applyDeckStyle() {
        val b = _binding ?: return
        val style = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            .getString(
                com.hana.spindle.ui.cassette.WalkmanColorway.PREF_DECK_STYLE,
                com.hana.spindle.ui.cassette.WalkmanColorway.DEFAULT_STYLE
            )
        val cw = com.hana.spindle.ui.cassette.WalkmanColorway.fromPref(style)
        val deck = cw != null
        val changed = deck != isDeckStyle || (cw != null && cw != deckColorway)
        isDeckStyle = deck
        cw?.let { deckColorway = it }

        b.systemDeckHeader.visibility = if (deck) View.VISIBLE else View.GONE
        b.systemDeckHeader.colorway = deckColorway
        b.metroHeader.visibility = if (deck) View.GONE else View.VISIBLE
        b.tabScrollView.visibility = if (deck) View.GONE else View.VISIBLE
        b.root.setPadding(0, if (deck) 0 else (8 * resources.displayMetrics.density).toInt(), 0, 0)

        val d = resources.displayMetrics.density
        val pano = b.drawerPanoramaScrollView
        val lp = pano.layoutParams as LinearLayout.LayoutParams
        if (deck) {
            val frame = (6 * d).toInt()
            lp.setMargins((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            pano.setPadding(frame, frame, frame, frame)
            pano.clipToPadding = true
            pano.background = deckWindowDrawable(deckColorway, d)
        } else {
            lp.setMargins(0, 0, 0, 0)
            pano.setPadding(0, 0, 0, 0)
            pano.clipToPadding = false
            pano.background = null
        }
        pano.layoutParams = lp

        if (changed && ::appAdapter.isInitialized) {
            applyPanelWidths()
            buildSettingsMenu()
            applyDrawerTheme(themeManager.currentTheme.value)
            refreshAccentPills()
            pano.post { _binding?.drawerPanoramaScrollView?.scrollTo(currentTabIndex * drawerPanelWidth(), 0) }
        }
    }

    /** Door frame with the smoked glass inset, same proportions as the tape deck's cassette door. */
    private fun deckWindowDrawable(cw: com.hana.spindle.ui.cassette.WalkmanColorway, d: Float) =
        android.graphics.drawable.LayerDrawable(
            arrayOf(
                android.graphics.drawable.GradientDrawable().apply { setColor(cw.doorFrame); cornerRadius = 10 * d },
                android.graphics.drawable.GradientDrawable().apply { setColor(cw.glass); cornerRadius = 7 * d }
            )
        ).apply { (6 * d).toInt().let { setLayerInset(1, it, it, it, it) } }

    private fun applyPanelWidths() {
        val b = _binding ?: return
        val w = drawerPanelWidth()
        listOf(b.panelSoundDeck, b.panelSettings, b.panelApps).forEach { p ->
            p.layoutParams = p.layoutParams.apply { width = w }
        }
        b.drawerPanoramaScrollView.enableSnapping(w, PANEL_COUNT, 0)
    }

    private fun setupDeckHeader(app: SpindleApp) {
        binding.systemDeckHeader.labels = listOf("SOUND", "SETTINGS", "APPS")
        binding.systemDeckHeader.onPageSelected = { index ->
            app.audioEngine.foleyEngine.playSwitchSnap()
            selectTab(index)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.audioEngine.playbackState.collect { _binding?.systemDeckHeader?.isRunning = it.isPlaying }
            }
        }
    }

    /** Pages sit behind dark glass in deck style, so their content always uses dark-theme colours there. */
    private fun contentTheme(requested: CassetteTheme): CassetteTheme =
        if (isDeckStyle && (!requested.isDarkAppTheme || requested.id == CassetteTheme.MONOCHROME_EINK.id)) {
            CassetteTheme.DARK
        } else {
            requested
        }

    /** Preset name plus every other stage that colours the sound, so "FLAT" is never misleading. */
    private fun fxLabel(fx: AudioFxController): String {
        val parts = mutableListOf(fx.currentPresetName.replace("_", " "))
        if (fx.currentTapeFormulation != AudioFxController.TapeFormulation.FLAT) parts += "TAPE ${
            fx.currentTapeFormulation.tag.removePrefix(
                "TYPE "
            )
        }"
        if (fx.currentDolbyMode != AudioFxController.DolbyMode.OFF) parts += fx.currentDolbyMode.label
        if (fx.isTapeSaturationEnabled) parts += "SAT"
        return "FX: " + parts.joinToString(" + ")
    }

    // =========================================================================
    // ZUNE SETTINGS MENU: lowercase text entries, each opening one short page
    // =========================================================================
    private fun setupSettingsMenu() {
        buildSettingsMenu()
        binding.tvSettingsPageTitle.setOnClickListener { showSettingsMenu() }
        showSettingsMenu()
    }

    /** Builds the menu rows for the active style: Zune text entries, or the LCD menu in deck style. */
    private fun buildSettingsMenu() {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        val menu = binding.settingsMenu
        menu.removeAllViews()
        if (isDeckStyle) {
            buildLcdSettingsMenu(ctx, density)
            styleSettingsTitle()
            return
        }
        menu.showDividers = LinearLayout.SHOW_DIVIDER_NONE
        val primary = androidx.core.content.ContextCompat.getColor(ctx, R.color.text_primary)
        val secondary = androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary)
        val ripple = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        settingsPages().forEachIndexed { index, page ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = (56 * density).toInt()
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                setBackgroundResource(ripple)
                isClickable = true
                isFocusable = true
                contentDescription = "${page.title}. ${page.summary}"
                setOnClickListener { openSettingsPage(index) }
            }
            row.addView(TextView(ctx).apply {
                text = page.title
                textSize = 28f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                includeFontPadding = false
                setTextColor(primary)
            })
            row.addView(TextView(ctx).apply {
                text = page.summary
                textSize = 12f
                setTextColor(secondary)
                setPadding(0, (2 * density).toInt(), 0, 0)
            })
            binding.settingsMenu.addView(row)
        }
        styleSettingsTitle()
    }

    /**
     * DAP-style LCD menu: number, title and summary per 51dp row, chevron on the right.
     * The row inverts (butter fill, dark print) while pressed or focused.
     */
    private fun buildLcdSettingsMenu(ctx: Context, density: Float) {
        val cw = deckColorway
        val dp = { v: Float -> (v * density).toInt() }
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val states = arrayOf(
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_focused),
            intArrayOf()
        )

        fun ink(normal: Int) = ColorStateList(states, intArrayOf(cw.glass, cw.glass, normal))
        val menu = binding.settingsMenu
        val pages = settingsPages()

        menu.dividerDrawable = android.graphics.drawable.InsetDrawable(
            android.graphics.drawable.GradientDrawable().apply {
                setColor(ColorUtils.setAlphaComponent(cw.lcdSub, 31))
                setSize(0, maxOf(1, dp(1f)))
            }, dp(4f), 0, dp(4f), 0
        )
        menu.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE

        menu.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8f), dp(10f), dp(8f), dp(8f))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(TextView(ctx).apply {
                text = "MENU"
                typeface = mono
                textSize = 9f
                letterSpacing = 0.08f
                setTextColor(cw.lcdSub)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(ctx).apply {
                text = "${pages.size} SECTIONS"
                typeface = mono
                textSize = 9f
                setTextColor(cw.lcdSub)
            })
        })

        pages.forEachIndexed { index, page ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(51f)
                setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
                background = android.graphics.drawable.StateListDrawable().apply {
                    val lit = android.graphics.drawable.GradientDrawable().apply {
                        setColor(cw.lcdText); cornerRadius = 3 * density
                    }
                    addState(intArrayOf(android.R.attr.state_pressed), lit)
                    addState(intArrayOf(android.R.attr.state_focused), lit)
                }
                isClickable = true
                isFocusable = true
                contentDescription = "${page.title}. ${page.summary}"
                setOnClickListener { openSettingsPage(index) }
            }
            row.addView(TextView(ctx).apply {
                text = String.format(Locale.US, "%02d", index + 1)
                typeface = mono
                textSize = 9f
                setTextColor(ink(cw.lcdSub))
                isDuplicateParentStateEnabled = true
            }, LinearLayout.LayoutParams(dp(26f), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                isDuplicateParentStateEnabled = true
                addView(TextView(ctx).apply {
                    text = page.title.uppercase(Locale.US)
                    typeface = mono
                    textSize = 13f
                    letterSpacing = 0.03f
                    includeFontPadding = false
                    setTextColor(ink(cw.lcdText))
                    isDuplicateParentStateEnabled = true
                })
                addView(TextView(ctx).apply {
                    text = page.summary
                    textSize = 10f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(3f), 0, 0)
                    setTextColor(ink(cw.lcdSub))
                    isDuplicateParentStateEnabled = true
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(ctx).apply {
                text = "›"
                textSize = 20f
                typeface = mono
                setPadding(dp(8f), 0, 0, 0)
                setTextColor(ink(cw.lcdText))
                isDuplicateParentStateEnabled = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            menu.addView(row)
        }
    }

    private var classicTitleTextSizePx = 0f

    /** Sub-page title: Zune "‹ display", or "‹ DISPLAY" in LCD print in deck style. */
    private fun styleSettingsTitle() {
        val title = binding.tvSettingsPageTitle
        if (classicTitleTextSizePx == 0f) classicTitleTextSizePx = title.textSize
        if (isDeckStyle) {
            title.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            title.textSize = 14f
            title.setTextColor(deckColorway.lcdText)
        } else {
            title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, classicTitleTextSizePx)
        }
        if (openSettingsPage >= 0) title.text = settingsTitleText(settingsPages()[openSettingsPage].title)
    }

    private fun settingsTitleText(title: String) =
        if (isDeckStyle) "‹ ${title.uppercase(Locale.US)}" else "‹ $title"

    private fun showSettingsMenu() {
        openSettingsPage = -1
        binding.settingsMenu.visibility = View.VISIBLE
        binding.tvSettingsPageTitle.visibility = View.GONE
        settingsPages().forEach { page -> page.cards.forEach { it.visibility = View.GONE } }
        binding.scrollSettingsContent.scrollTo(0, 0)
    }

    private fun openSettingsPage(index: Int) {
        val pages = settingsPages()
        val page = pages.getOrNull(index) ?: return
        openSettingsPage = index
        (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playMetroTick(1.0f)
        binding.settingsMenu.visibility = View.GONE
        binding.tvSettingsPageTitle.text = settingsTitleText(page.title)
        binding.tvSettingsPageTitle.contentDescription = "${page.title}. Tap to go back to settings"
        binding.tvSettingsPageTitle.visibility = View.VISIBLE
        pages.forEach { p -> p.cards.forEach { it.visibility = if (p === page) View.VISIBLE else View.GONE } }
        binding.scrollSettingsContent.post { _binding?.scrollSettingsContent?.scrollTo(0, 0) }
    }

    // =========================================================================
    // 2. MINIMALIST APP DRAWER WITH NIAGARA-STYLE VERNIER WAVE SCROLLBAR
    // =========================================================================
    private fun setupAppDrawer() {
        appAdapter = AppListAdapter(
            onAppClicked = { appInfo ->
                (requireActivity().application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
                recentAppsManager.recordAppLaunch(appInfo.packageName)
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(appInfo.packageName, appInfo.activityName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                startActivity(launchIntent)
            },
            onAppLongClicked = { appInfo ->
                (requireActivity().application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress(1.2f)
                AppPropertiesDialog.show(requireContext(), appInfo) {
                    reloadApps()
                }
            }
        )

        val density = resources.displayMetrics.density
        binding.rvApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApps.adapter = appAdapter
        appAdapter.isGridMode = false
        binding.rvApps.setPadding(0, 0, (32 * density).toInt(), (24 * density).toInt())
        binding.alphabetIndexView.visibility = View.VISIBLE

        reloadApps()

        binding.etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Zune Metro 26-Letter Quick-Jump Grid Trigger
        binding.btnJumpApps.setOnClickListener {
            val appInstance = requireActivity().application as SpindleApp
            appInstance.audioEngine.foleyEngine.playMetroTick(1.1f)
            val availableLetters = allApps.map { app ->
                val ch = app.label.trim().firstOrNull()?.uppercaseChar() ?: '#'
                if (ch in 'A'..'Z') ch else '#'
            }.toSet()
            val theme = appInstance.themeManager.currentTheme.value
            ZuneJumpListDialog.show(
                fragmentManager = parentFragmentManager,
                availableLetters = availableLetters,
                title = "apps",
                subtitle = "jump directly to installed app",
                accentColor = theme.accentColor
            ) { letter ->
                if (letter == "TOP" || letter == "↑") {
                    (binding.rvApps.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                } else {
                    val targetIndex = if (letter == "#") {
                        allApps.indexOfFirst {
                            val firstChar = it.label.trim().firstOrNull()?.uppercaseChar() ?: ' '
                            firstChar !in 'A'..'Z'
                        }
                    } else {
                        allApps.indexOfFirst {
                            it.label.trim().startsWith(letter, ignoreCase = true)
                        }
                    }
                    if (targetIndex >= 0) {
                        (binding.rvApps.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                            targetIndex,
                            0
                        )
                    }
                }
            }
        }

        binding.alphabetIndexView.setOnLongClickListener {
            binding.btnJumpApps.performClick()
            true
        }


        // Niagara-Style Vernier Wave Alphabet Index Rail
        binding.alphabetIndexView.onLetterSelected = { letter ->
            val targetIndex = if (letter == "#") {
                allApps.indexOfFirst {
                    val firstChar = it.label.trim().firstOrNull()?.uppercaseChar() ?: ' '
                    firstChar !in 'A'..'Z'
                }
            } else {
                allApps.indexOfFirst {
                    it.label.trim().startsWith(letter, ignoreCase = true)
                }
            }

            if (targetIndex >= 0) {
                (binding.rvApps.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(targetIndex, 0)
            }
        }
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter { it.label.contains(query, ignoreCase = true) }
        }

        appAdapter.submitList(filtered)
    }

    // ---- Accent colours: emerald in Classic, LCD butter / tangerine in deck style ----------------

    private val classicAccent = Color.parseColor("#00E676")
    private val classicAccentBg = Color.parseColor("#16261B")
    private val tangerine = Color.parseColor("#F97316")

    /** Re-run whenever the deck style changes so selection pills pick up the new accent. */
    private val pillRefreshers = mutableListOf<() -> Unit>()

    /** Text of a selected pill / live readout. */
    private fun pillOnText(): Int = if (isDeckStyle) deckColorway.lcdText else classicAccent

    /** Background of a selected pill: the accent faintly lit over the panel recess. */
    private fun pillOnBg(): Int =
        if (isDeckStyle) ColorUtils.blendARGB(deckColorway.recess, deckColorway.lcdText, 0.14f) else classicAccentBg

    /** Sound panel controls drawn in the accent: knobs, EQ, curve, sliders and live readouts. */
    private fun styleSoundPanel() {
        val b = _binding ?: return
        val readout = pillOnText()
        val pointer = if (isDeckStyle) tangerine else classicAccent
        listOf(b.knobLow, b.knobMid, b.knobHi, b.knobFilter).forEach {
            it.accentColor = pointer
            it.readoutColor = readout
        }
        b.iso10BandEqView.accentColor = readout
        b.biquadVisualizerView.accentColor = readout
        b.btnFx.setTextColor(readout)
        b.btnFx.backgroundTintList = ColorStateList.valueOf(pillOnBg())
        b.tvVolumeLevel.setTextColor(readout)
        b.tvCrossfeedLevel.setTextColor(readout)
        listOf(b.seekMasterVolume, b.seekCrossfeed).forEach {
            it.progressTintList = ColorStateList.valueOf(readout)
            it.thumbTintList = ColorStateList.valueOf(pointer)
        }
    }

    /**
     * Re-runs the selection pills so they pick up the new accent. Only needed when the deck
     * style changes. Some pill updaters also trigger a full EQ sync, so the sync is held back
     * and run once at the end.
     */
    private fun refreshAccentPills() {
        val sync = syncAllEqControlsAction
        syncAllEqControlsAction = null
        try {
            pillRefreshers.forEach { it() }
        } finally {
            syncAllEqControlsAction = sync
        }
        sync?.invoke()
    }

    // ---- Section widgets (switches, sliders, panels, labels) in deck style ----------------------

    /** Original colours of every widget restyled for deck style, so Classic gets them back exactly. */
    private val deckOriginals = HashMap<View, Any>()

    private class TintPair(val first: ColorStateList?, val second: ColorStateList?)

    /** Roots whose contents follow the deck style. The LCD menu is excluded; it is built per style. */
    private fun deckWidgetRoots(b: FragmentDrawerBinding): List<View> =
        listOf(b.panelSoundDeck, b.scrollSettingsContent)

    private fun restoreDeckWidgets() {
        for ((view, original) in deckOriginals) {
            when {
                view is androidx.appcompat.widget.SwitchCompat && original is TintPair -> {
                    view.thumbTintList = original.first; view.trackTintList = original.second
                }

                view is SeekBar && original is TintPair -> {
                    view.progressTintList = original.first; view.thumbTintList = original.second
                }

                view is TextView && original is ColorStateList -> view.setTextColor(original)
            }
        }
        deckOriginals.clear()
    }

    private fun styleDeckWidgets() {
        val b = _binding ?: return
        val cw = deckColorway
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val thumb = ColorStateList(states, intArrayOf(cw.lcdText, ColorUtils.blendARGB(cw.glass, cw.lcdSub, 0.55f)))
        val track = ColorStateList(
            states,
            intArrayOf(ColorUtils.setAlphaComponent(cw.lcdText, 115), ColorUtils.blendARGB(cw.glass, cw.lcdSub, 0.22f))
        )
        val panel = ColorUtils.blendARGB(cw.glass, cw.lcdSub, 0.07f)

        fun visit(v: View) {
            if (v === b.settingsMenu) return
            when (v) {
                is androidx.cardview.widget.CardView -> v.setCardBackgroundColor(panel)
                is androidx.appcompat.widget.SwitchCompat -> {
                    deckOriginals.getOrPut(v) { TintPair(v.thumbTintList, v.trackTintList) }
                    v.thumbTintList = thumb
                    v.trackTintList = track
                }

                is SeekBar -> {
                    deckOriginals.getOrPut(v) { TintPair(v.progressTintList, v.thumbTintList) }
                    v.progressTintList = ColorStateList.valueOf(cw.lcdText)
                    v.thumbTintList = ColorStateList.valueOf(tangerine)
                }

                is TextView -> if (isSectionLabel(v)) {
                    deckOriginals.getOrPut(v) { v.textColors }
                    v.setTextColor(cw.lcdSub)
                }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        deckWidgetRoots(b).forEach { visit(it) }
    }

    /** Small grey monospace captions ("DAP HARDWARE & AUDIO ENGINE"); not buttons, pills or readouts. */
    private fun isSectionLabel(v: TextView): Boolean {
        if (v.isClickable || v is Button || v is EditText) return false
        if (!isMonospace(v)) return false
        val c = deckOriginals[v] as? ColorStateList
        val color = c?.defaultColor ?: v.currentTextColor
        val r = Color.red(color)
        val g = Color.green(color)
        val bl = Color.blue(color)
        val neutral = kotlin.math.abs(r - g) < 25 && kotlin.math.abs(g - bl) < 25
        val brightness = (r * 299 + g * 587 + bl * 114) / 1000
        return neutral && brightness in 80..190
    }

    private fun isMonospace(v: TextView): Boolean {
        val p = v.paint
        return p.measureText("iiii") == p.measureText("MMMM")
    }


    private fun reloadApps() {
        viewLifecycleOwner.lifecycleScope.launch {
            allApps = appListLoader.loadInstalledApps().sortedBy { it.label.lowercase(Locale.ROOT) }
            _binding?.let { b ->
                filterApps(b.etSearchApps.text.toString())
            }
        }
    }

    // =========================================================================
    // 3. HARDWARE ACOUSTIC EQ & DSP TUNING DECK WITH MASTER VOLUME
    // =========================================================================
    private fun setupDjConsole(app: SpindleApp) {
        val audioEngine = app.audioEngine
        val fxController = audioEngine.audioFxController

        fun updateBiquadVisualizer() {
            _binding?.biquadVisualizerView?.updateDspState(
                isoGainsDb = fxController.isoBandsGainDb,
                isoQFactors = fxController.isoBandsQ,
                isTapeSatEnabled = fxController.isTapeSaturationEnabled,
                tapeSatDrive = fxController.tapeSaturationDrive
            )
        }

        // 1. Calibrated Rotary Potentiometers: LOW, MID, HI, FILTER
        binding.knobLow.label = "LOW"
        binding.knobLow.minValue = -12f
        binding.knobLow.maxValue = 12f
        binding.knobLow.currentValue = fxController.lowGainDb
        binding.knobLow.onValueChanged = { gainDb ->
            fxController.setLowGain(gainDb)
            binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
            updateEqHeadroomLabel(fxController)
            updateBiquadVisualizer()
        }

        binding.knobMid.label = "MID"
        binding.knobMid.minValue = -12f
        binding.knobMid.maxValue = 12f
        binding.knobMid.currentValue = fxController.midGainDb
        binding.knobMid.onValueChanged = { gainDb ->
            fxController.setMidGain(gainDb)
            binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
            updateEqHeadroomLabel(fxController)
            updateBiquadVisualizer()
        }

        binding.knobHi.label = "HI"
        binding.knobHi.minValue = -12f
        binding.knobHi.maxValue = 12f
        binding.knobHi.currentValue = fxController.highGainDb
        binding.knobHi.onValueChanged = { gainDb ->
            fxController.setHighGain(gainDb)
            binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
            updateEqHeadroomLabel(fxController)
            updateBiquadVisualizer()
        }

        binding.knobFilter.label = "FILTER"
        binding.knobFilter.minValue = 0f
        binding.knobFilter.maxValue = 1000f
        binding.knobFilter.currentValue = fxController.bassBoostStrength.toFloat()
        binding.knobFilter.onValueChanged = { strength ->
            fxController.setFilterStrength(strength.toInt())
        }

        // 2. 10-Band ISO Graphic Equalizer View setup
        val bandsText = if (fxController.isHardwareInterpolated) {
            "${fxController.hardwareBandsCount} active hardware bands detected (interpolated to 10 ISO)"
        } else {
            "${fxController.hardwareBandsCount} active hardware bands detected"
        }
        binding.tvEqHardwareBands.text = bandsText

        val openHwSpecs = View.OnClickListener {
            DialogHardwareEqSpecs(fxController).show(parentFragmentManager, "DialogHardwareEqSpecs")
        }
        binding.containerEqHardwareHeader.setOnClickListener(openHwSpecs)
        binding.tvEqHardwareBands.setOnClickListener(openHwSpecs)
        binding.tvEqHardwareBadge.setOnClickListener(openHwSpecs)

        binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
        updateBiquadVisualizer()

        // Sync live audio visualizer waveform pulse with playback engine
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                audioEngine.playbackState.collect { state ->
                    _binding?.biquadVisualizerView?.isPlaying = state.isPlaying
                }
            }
        }

        binding.iso10BandEqView.onBandsChanged = { gains ->
            fxController.setAllIsoBands(gains)
            binding.knobLow.currentValue = fxController.lowGainDb
            binding.knobMid.currentValue = fxController.midGainDb
            binding.knobHi.currentValue = fxController.highGainDb
            updateEqHeadroomLabel(fxController)
            updateBiquadVisualizer()
        }
        binding.iso10BandEqView.onBandDragFinished = {
            binding.iso10BandEqView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        updateEqHeadroomLabel(fxController)

        // 2b. Hardware EQ & DSP Lock Guard
        val prefs = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val isInitiallyLocked = prefs.getBoolean("pref_eq_locked", false)
        fxController.isLocked = isInitiallyLocked
        binding.iso10BandEqView.isLocked = isInitiallyLocked

        fun updateEqLockVisual(locked: Boolean) {
            if (locked) {
                binding.btnEqLock.text = "🔒 LOCKED"
                binding.btnEqLock.setTextColor(Color.parseColor("#EF4444"))
                binding.btnEqLock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3B1219"))
                binding.knobLow.isEnabled = false
                binding.knobMid.isEnabled = false
                binding.knobHi.isEnabled = false
                binding.knobFilter.isEnabled = false
                binding.knobLow.alpha = 0.45f
                binding.knobMid.alpha = 0.45f
                binding.knobHi.alpha = 0.45f
                binding.knobFilter.alpha = 0.45f
                binding.btnFx.alpha = 0.45f
                binding.btnReset.alpha = 0.45f
                binding.layoutAutoEqPills.alpha = 0.45f
            } else {
                binding.btnEqLock.text = "🔓 UNLOCKED"
                binding.btnEqLock.setTextColor(Color.parseColor("#94A3B8"))
                binding.btnEqLock.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1C1E26"))
                binding.knobLow.isEnabled = true
                binding.knobMid.isEnabled = true
                binding.knobHi.isEnabled = true
                binding.knobFilter.isEnabled = true
                binding.knobLow.alpha = 1.0f
                binding.knobMid.alpha = 1.0f
                binding.knobHi.alpha = 1.0f
                binding.knobFilter.alpha = 1.0f
                binding.btnFx.alpha = 1.0f
                binding.btnReset.alpha = 1.0f
                binding.layoutAutoEqPills.alpha = 1.0f
            }
        }
        updateEqLockVisual(isInitiallyLocked)

        binding.btnEqLock.setOnClickListener {
            val newLocked = !fxController.isLocked
            fxController.isLocked = newLocked
            binding.iso10BandEqView.isLocked = newLocked
            prefs.edit().putBoolean("pref_eq_locked", newLocked).apply()
            updateEqLockVisual(newLocked)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val msg = if (newLocked) "EQ & DSP Locked (Protected against accidental touches)" else "EQ & DSP Unlocked"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // 2c. Parametric EQ Mode Toggle
        fun updateEqModeVisual(isParametric: Boolean) {
            binding.btnEqMode.text = if (isParametric) "PARAMETRIC" else "GRAPHIC"
            binding.btnEqMode.setTextColor(
                if (isParametric) pillOnText() else Color.parseColor("#38BDF8")
            )
            binding.btnEqMode.backgroundTintList = ColorStateList.valueOf(
                if (isParametric) pillOnBg() else Color.parseColor("#1C1E26")
            )
            binding.iso10BandEqView.isParametricMode = isParametric
        }
        updateEqModeVisual(fxController.isParametricMode)

        binding.btnEqMode.setOnClickListener {
            if (fxController.isLocked) {
                Toast.makeText(requireContext(), "EQ is locked. Tap UNLOCKED to switch mode.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            val newMode = !fxController.isParametricMode
            fxController.setParametricMode(newMode)
            updateEqModeVisual(newMode)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val msg =
                if (newMode) "Parametric EQ Mode Active (Tap/hold band to cycle Q-Factor)" else "Graphic EQ Mode Active (Standard ISO 1-Octave)"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        binding.iso10BandEqView.onQChanged = { band, q ->
            fxController.setBandQ(band, q)
            updateBiquadVisualizer()
            val bandLabel =
                if (band in AudioDspConstants.ISO_LABELS.indices) AudioDspConstants.ISO_LABELS[band] else "Band $band"
            Toast.makeText(
                requireContext(),
                "$bandLabel Q set to ${String.format(Locale.US, "%.1f", q)}",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 2d. Target Curves Quick Buttons (Settings card)
        val curveButtons = mapOf(
            binding.btnCurveHarman to "HARMAN",
            binding.btnCurveDiffuse to "DIFFUSE",
            binding.btnCurveTube to "TUBE",
            binding.btnCurveAir to "AIR",
            binding.btnCurveFlat to "FLAT"
        )

        fun highlightCurve(selected: String) {
            val activeColor = ColorStateList.valueOf(Color.parseColor("#F97316"))
            val inactiveColor = ColorStateList.valueOf(Color.parseColor("#202334"))
            curveButtons.forEach { (btn, name) ->
                val isSel = name.equals(selected, ignoreCase = true)
                btn.backgroundTintList = if (isSel) activeColor else inactiveColor
                btn.setTextColor(if (isSel) Color.WHITE else Color.parseColor("#A1A1AA"))
            }
        }

        // Custom User EQ Presets & Acoustic Targets Count
        fun updateCustomPresetsBadge() {
            val count = app.userEqPresetManager.getPresets().size
            binding.btnManageUserPresets.text = "PROFILES ($count)"
        }

        // 3. AutoEq Target Profile Buttons (DJ Console Strip)
        (binding.layoutAutoEqPills.parent as? ViewGroup)?.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        val autoEqButtons = listOf(
            binding.btnEqHarman to "HARMAN",
            binding.btnEqCrinacle to "CRINACLE",
            binding.btnEqMoondrop to "MOONDROP",
            binding.btnEqHd600 to "HD600",
            binding.btnEqWarmTape to "WARM_TUBE",
            binding.btnEqVShape to "V_SHAPE",
            binding.btnEqFlat to "FLAT"
        )

        fun refreshAutoEqPills() {
            val currentName = fxController.currentPresetName
            // 1. Highlight built-in static pills
            autoEqButtons.forEach { (btn, name) ->
                val isSelected = name.equals(currentName, ignoreCase = true) ||
                        (name.equals("HARMAN", ignoreCase = true) && currentName.contains(
                            "Harman",
                            ignoreCase = true
                        )) ||
                        (name.equals("CRINACLE", ignoreCase = true) && currentName.contains(
                            "Crinacle",
                            ignoreCase = true
                        )) ||
                        (name.equals("MOONDROP", ignoreCase = true) && currentName.contains(
                            "Moondrop",
                            ignoreCase = true
                        )) ||
                        (name.equals("HD600", ignoreCase = true) && currentName.contains("HD600", ignoreCase = true)) ||
                        (name.equals("WARM_TUBE", ignoreCase = true) && currentName.contains(
                            "Warm",
                            ignoreCase = true
                        )) ||
                        (name.equals("V_SHAPE", ignoreCase = true) && currentName.contains(
                            "V-Shape",
                            ignoreCase = true
                        )) ||
                        (name.equals("FLAT", ignoreCase = true) && currentName.equals("Flat", ignoreCase = true))
                btn.setTextColor(if (isSelected) pillOnText() else Color.parseColor("#A1A1AA"))
                btn.backgroundTintList = ColorStateList.valueOf(
                    if (isSelected) pillOnBg() else Color.parseColor("#181A20")
                )
            }

            // 2. Remove previously added dynamic pills (keep the 7 initial built-ins)
            while (binding.layoutAutoEqPills.childCount > 7) {
                binding.layoutAutoEqPills.removeViewAt(7)
            }

            // 3. Append reference headphone targets & custom presets
            val allActivePresets = app.userEqPresetManager.getPresets().filter { preset ->
                preset.name !in setOf("HARMAN", "CRINACLE", "MOONDROP", "HD600", "WARM_TUBE", "V_SHAPE", "FLAT")
            }
            allActivePresets.forEach { preset ->
                val pill = layoutInflater.inflate(R.layout.item_autoeq_pill, binding.layoutAutoEqPills, false) as Button
                val isSelected = preset.name.equals(currentName, ignoreCase = true)
                val displayName = if (preset.name.length > 18) preset.name.take(16) + "…" else preset.name
                pill.text = displayName.uppercase()
                pill.tag = preset.name
                pill.setTextColor(if (isSelected) pillOnText() else Color.parseColor("#A1A1AA"))
                pill.backgroundTintList = ColorStateList.valueOf(
                    if (isSelected) pillOnBg() else Color.parseColor("#181A20")
                )
                pill.setOnClickListener {
                    if (fxController.isLocked) {
                        Toast.makeText(
                            requireContext(),
                            "EQ & DSP is locked. Tap UNLOCKED to adjust.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    fxController.applyUserPreset(preset)
                    syncAllEqControlsAction?.invoke()
                    pill.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    Toast.makeText(requireContext(), "PRESET: ${preset.name}", Toast.LENGTH_SHORT).show()
                }
                binding.layoutAutoEqPills.addView(pill)
            }

            // 4. Append "+ PRESETS" quick access manager pill
            val btnAdd = layoutInflater.inflate(R.layout.item_autoeq_pill, binding.layoutAutoEqPills, false) as Button
            btnAdd.text = "+ PRESETS"
            btnAdd.tag = "__ADD_PRESETS__"
            btnAdd.setTextColor(Color.parseColor("#F97316"))
            btnAdd.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A1B14"))
            btnAdd.setOnClickListener {
                btnAdd.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val dialog = DialogUserEqPresets(fxController, openSaveDirectly = false) {
                    syncAllEqControlsAction?.invoke()
                }
                dialog.show(parentFragmentManager, "DialogUserEqPresets")
            }
            binding.layoutAutoEqPills.addView(btnAdd)
        }

        fun syncAllEqControls() {
            binding.iso10BandEqView.setBands(fxController.isoBandsGainDb)
            binding.knobLow.currentValue = fxController.lowGainDb
            binding.knobMid.currentValue = fxController.midGainDb
            binding.knobHi.currentValue = fxController.highGainDb
            binding.knobFilter.currentValue = fxController.bassBoostStrength.toFloat()
            binding.seekCrossfeed.progress = fxController.crossfeedStrength / 10
            updateCrossfeedLabel(binding.seekCrossfeed.progress)
            updateEqModeVisual(fxController.isParametricMode)
            updateEqHeadroomLabel(fxController)
            updateBiquadVisualizer()
            binding.btnFx.text = fxLabel(fxController)
            highlightCurve(fxController.currentPresetName)
            updateCustomPresetsBadge()
            refreshAutoEqPills()
        }
        syncAllEqControlsAction = { syncAllEqControls() }

        autoEqButtons.forEach { (btn, name) ->
            btn.setOnClickListener {
                if (fxController.isLocked) {
                    Toast.makeText(requireContext(), "EQ & DSP is locked. Tap UNLOCKED to adjust.", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                fxController.applyPreset(name)
                syncAllEqControls()
                btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        // 4. DSP Preset Cycler Button
        binding.btnFx.setOnClickListener {
            if (fxController.isLocked) {
                Toast.makeText(requireContext(), "EQ & DSP is locked. Tap UNLOCKED to adjust.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            currentPresetIndex = (currentPresetIndex + 1) % dspPresets.size
            val preset = dspPresets[currentPresetIndex]
            fxController.applyPreset(preset)
            syncAllEqControls()
        }

        // 5. Deck Master RESET Button: Flat EQ, 0 gain, 80% volume
        binding.btnReset.setOnClickListener {
            if (fxController.isLocked) {
                Toast.makeText(requireContext(), "EQ & DSP is locked. Tap UNLOCKED to adjust.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            fxController.setLowGain(0f)
            fxController.setMidGain(0f)
            fxController.setHighGain(0f)
            fxController.setFilterStrength(0)
            fxController.applyPreset("FLAT")
            currentPresetIndex = 0

            binding.iso10BandEqView.resetAll()
            binding.knobLow.currentValue = 0f
            binding.knobMid.currentValue = 0f
            binding.knobHi.currentValue = 0f
            binding.knobFilter.currentValue = 0f
            setMasterOutputVolume(80)
            binding.seekMasterVolume.progress = 80
            updateVolumeLabel(80)

            syncAllEqControls()

            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Hardware EQ & Master Output Reset to Flat Reference", Toast.LENGTH_SHORT)
                .show()
        }

        // 4. Master Output Volume Fader (Replaces L/R balance slider)
        syncVolumeSlider()
        binding.seekMasterVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setMasterOutputVolume(progress)
                }
                updateVolumeLabel(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        })

        // 5. Bit-Perfect Direct Bypass Switch
        binding.switchBypass.isChecked = fxController.isBypassEnabled
        updateBypassVisuals(fxController.isBypassEnabled)
        binding.switchBypass.setOnCheckedChangeListener { _, isChecked ->
            fxController.setBypass(isChecked)
            updateBypassVisuals(isChecked)
            val msg =
                if (isChecked) "Hi-Res Passthrough Active (DSP Bypassed)" else "DSP Equalization & Target Profile Enabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // 5b. Harmonic Analog Tape Saturation Switch (Studio Collector)
        binding.switchTapeSaturation.isChecked = app.audioEngine.isTapeSaturationEnabled
        binding.switchTapeSaturation.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked && !StudioUnlockManager.isFeatureUnlocked(
                    requireContext(),
                    StudioFeature.ANALOG_TAPE_SATURATION
                )
            ) {
                buttonView.isChecked = false
                Toast.makeText(
                    requireContext(),
                    "Studio Collector Required: Harmonic Analog Tape Saturation DSP",
                    Toast.LENGTH_LONG
                ).show()
                DialogStudioUnlock(requireContext()) {
                    setupStudioCollectorSuite()
                    binding.switchTapeSaturation.isChecked = app.audioEngine.isTapeSaturationEnabled
                }.show()
                return@setOnCheckedChangeListener
            }
            binding.seekTapeSaturationDrive.isEnabled = isChecked
            binding.containerTapeSaturationDrive.alpha = if (isChecked) 1.0f else 0.4f
            app.audioEngine.setTapeSaturation(isChecked)
            app.audioEngine.foleyEngine.playSwitchSnap()
            val msg =
                if (isChecked) "Harmonic Tape Saturation Active (+3.2dB @ 63Hz Warmth)" else "Tape Saturation DSP Disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // 5c. Tape Saturation Drive Gain Control (0.0 to +6.0 dB)
        val initialDriveDb = fxController.tapeSaturationDriveDb
        val initialDriveProgress = (initialDriveDb * 10f).toInt().coerceIn(0, 60)
        binding.seekTapeSaturationDrive.progress = initialDriveProgress
        binding.tvTapeSaturationDrive.text = String.format(Locale.US, "+%.1f dB", initialDriveDb)
        binding.seekTapeSaturationDrive.isEnabled = app.audioEngine.isTapeSaturationEnabled
        binding.containerTapeSaturationDrive.alpha = if (app.audioEngine.isTapeSaturationEnabled) 1.0f else 0.4f

        binding.seekTapeSaturationDrive.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val driveDb = progress / 10.0f
                binding.tvTapeSaturationDrive.text = String.format(Locale.US, "+%.1f dB", driveDb)
                if (fromUser) {
                    fxController.setTapeSaturationDriveDb(driveDb)
                    updateBiquadVisualizer()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        })

        // 6. Binaural Crossfeed / Soundstage Fader
        binding.seekCrossfeed.progress = fxController.crossfeedStrength / 10
        updateCrossfeedLabel(binding.seekCrossfeed.progress)
        binding.seekCrossfeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioEngine.setCrossfeedStrength(progress * 10)
                }
                updateCrossfeedLabel(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        })

        val crossfeedCycleListener = View.OnClickListener {
            val mode = fxController.cycleCrossfeedMode()
            audioEngine.setCrossfeedMode(mode)
            binding.seekCrossfeed.progress = fxController.crossfeedStrength / 10
            updateCrossfeedLabel(binding.seekCrossfeed.progress)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Crossfeed: ${mode.description}", Toast.LENGTH_SHORT).show()
        }
        binding.tvCrossfeedTitle.setOnClickListener(crossfeedCycleListener)
        binding.tvCrossfeedLevel.setOnClickListener(crossfeedCycleListener)

        // 7. Target Curves Quick Buttons (Settings card)
        curveButtons.forEach { (btn, curveName) ->
            btn.setOnClickListener {
                if (fxController.isLocked) {
                    Toast.makeText(requireContext(), "EQ & DSP is locked. Tap UNLOCKED to adjust.", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                fxController.applyPreset(curveName)
                syncAllEqControls()
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        binding.btnSaveUserPreset.setOnClickListener {
            val dialog = DialogUserEqPresets(fxController, openSaveDirectly = true) {
                syncAllEqControls()
            }
            dialog.show(parentFragmentManager, "DialogUserEqPresets")
        }

        binding.btnManageUserPresets.setOnClickListener {
            val dialog = DialogUserEqPresets(fxController, openSaveDirectly = false) {
                syncAllEqControls()
            }
            dialog.show(parentFragmentManager, "DialogUserEqPresets")
        }

        // Initial sync of all EQ and DSP controls
        syncAllEqControls()

        binding.tvVuBacklightHint.setOnClickListener {
            binding.dualAnalogVuMeter.cycleBacklight()
        }

        // 8. Observe playback state and animate VU Meter, Waveform, and L/R Peak Meter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    audioEngine.playbackState.collectLatest { state ->
                        _binding?.let { b ->
                            val currentTrack = state.currentTrack
                            if (currentTrack != null) {
                                b.tvDjTrackTitle.text = "${currentTrack.title} • ${currentTrack.artist}"
                            } else {
                                b.tvDjTrackTitle.text = "Spindle Direct ALSA / DSD DAC"
                            }
                            b.tvDjTrackTitle.isSelected = true
                        }
                    }
                }

                // 6. Analog VU needles driven by the measured RMS of the audible PCM signal.
                // Radio streams play through a separate player that isn't metered, so the needles rest.
                launch {
                    val levels = FloatArray(4)
                    while (isActive) {
                        val isLocalPlaying = audioEngine.playbackState.value.isPlaying
                        if (isLocalPlaying && audioEngine.outputLevelMeter.read(66L, levels)) {
                            val (lm, rm) = audioEngine.getStereoLevels(1f)
                            _binding?.dualAnalogVuMeter?.setStereoLevels(levels[2] * lm, levels[3] * rm)
                        } else {
                            _binding?.dualAnalogVuMeter?.setStereoLevels(0.0f, 0.0f)
                        }
                        delay(66L)
                    }
                }
            }
        }
    }

    private fun syncVolumeSlider() {
        val am = audioManager ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol <= 0) return
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val progress = ((curVol.toFloat() / maxVol) * 100).toInt().coerceIn(0, 100)
        _binding?.seekMasterVolume?.progress = progress
        updateVolumeLabel(progress)
    }

    private fun setMasterOutputVolume(progress: Int) {
        val am = audioManager ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((progress / 100f) * maxVol).toInt().coerceIn(0, maxVol)
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (e: Exception) {
            // ignore if permissions restrict
        }
    }

    private fun updateVolumeLabel(progress: Int) {
        val dbAttenuation = if (progress <= 0) "-∞" else String.format(Locale.US, "%.1f", 20 * log10(progress / 100f))
        _binding?.tvVolumeLevel?.text = "$progress% • ${dbAttenuation} dB"
    }

    private fun updateBypassVisuals(isBypass: Boolean) {
        _binding?.let { b ->
            if (isBypass) {
                b.tvBypassTitle.setTextColor(Color.parseColor("#00E676"))
                b.tvBypassTitle.text = "● HI-RES PASSTHROUGH (ACTIVE)"
                b.tvBypassSub.text = "DSP processing bypassed for direct device passthrough"
                b.knobLow.alpha = 0.35f
                b.knobMid.alpha = 0.35f
                b.knobHi.alpha = 0.35f
                b.knobFilter.alpha = 0.35f
                b.seekCrossfeed.alpha = 0.35f
                b.btnFx.alpha = 0.35f
                b.iso10BandEqView.alpha = 0.35f
                b.biquadVisualizerView.alpha = 0.35f
                b.layoutAutoEqPills.alpha = 0.35f
            } else {
                b.tvBypassTitle.setTextColor(Color.WHITE)
                b.tvBypassTitle.text = "HI-RES PASSTHROUGH (DEVICE-DEPENDENT)"
                b.tvBypassSub.text = "Bypass DSP effects for native hardware passthrough where supported"
                b.knobLow.alpha = 1.0f
                b.knobMid.alpha = 1.0f
                b.knobHi.alpha = 1.0f
                b.knobFilter.alpha = 1.0f
                b.seekCrossfeed.alpha = 1.0f
                b.btnFx.alpha = 1.0f
                b.iso10BandEqView.alpha = 1.0f
                b.biquadVisualizerView.alpha = 1.0f
                b.layoutAutoEqPills.alpha = 1.0f
            }
        }
    }

    private fun updateEqHeadroomLabel(fxController: com.hana.spindle.playback.AudioFxController) {
        var maxBoost = 0f
        for (gain in fxController.isoBandsGainDb) {
            if (gain > maxBoost) maxBoost = gain
        }
        val headroom = maxBoost * 0.35f
        _binding?.let { b ->
            if (headroom > 0.05f) {
                b.tvEqHeadroom.text = String.format(Locale.US, "-%.1f dB HEADROOM", headroom)
                b.tvEqHeadroom.setTextColor(Color.parseColor("#FFB74D")) // Amber warning/protection
            } else {
                b.tvEqHeadroom.text = "0.0 dB HEADROOM"
                b.tvEqHeadroom.setTextColor(pillOnText()) // Safe
            }
        }
    }

    private fun updateCrossfeedLabel(progress: Int) {
        _binding?.let { b ->
            val mode = audioFxController.currentCrossfeedMode
            b.tvCrossfeedLevel.text = if (progress <= 0) "OFF (0%)" else "${mode.label} ($progress%)"
        }
    }

    // =========================================================================
    // 4. GROUPED SETTINGS: 3 CURATED THEMES
    // =========================================================================
    private fun setupThemeSelector() {
        updateThemeButtonsVisual()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                themeManager.currentTheme.collect { theme ->
                    applyDrawerTheme(theme)
                }
            }
        }

        fun switchTheme(theme: CassetteTheme) {
            themeManager.setTheme(theme)
            updateThemeButtonsVisual()
            (requireActivity().application as? SpindleApp)?.audioEngine?.foleyEngine?.playCarriageEject()
        }

        binding.btnThemeMetalXr.setOnClickListener { switchTheme(CassetteTheme.METAL_XR_TYPE4) }
        binding.btnThemeDark.setOnClickListener { switchTheme(CassetteTheme.DARK) }
        binding.btnThemeLight.setOnClickListener { switchTheme(CassetteTheme.LIGHT) }
        binding.btnThemeEink.setOnClickListener { switchTheme(CassetteTheme.MONOCHROME_EINK) }
        binding.btnThemeTdk.setOnClickListener { switchTheme(CassetteTheme.TDK_SA_90) }
        binding.btnThemeMaxell.setOnClickListener { switchTheme(CassetteTheme.MAXELL_XLII) }
        binding.btnThemeBasf.setOnClickListener { switchTheme(CassetteTheme.BASF_CHROME) }
        binding.btnThemeSkeleton.setOnClickListener { switchTheme(CassetteTheme.SKELETON_REEL) }
        binding.btnThemeStudioReel.setOnClickListener {
            if (!StudioUnlockManager.isFeatureUnlocked(
                    requireContext(),
                    StudioFeature.STUDIO_REEL_SKINS
                )
            ) {
                Toast.makeText(
                    requireContext(),
                    "Studio Collector Required: 10.5\" Aluminum Reel-to-Reel Master Deck",
                    Toast.LENGTH_LONG
                ).show()
                DialogStudioUnlock(requireContext()) {
                    setupStudioCollectorSuite()
                    updateThemeButtonsVisual()
                }.show()
                return@setOnClickListener
            }
            switchTheme(CassetteTheme.REEL_TO_REEL_STUDIO)
        }
        binding.btnThemeSonyHf.setOnClickListener { switchTheme(CassetteTheme.SONY_HF_90) }
        binding.btnThemeDenonHd.setOnClickListener { switchTheme(CassetteTheme.DENON_HD8_100) }
        binding.btnThemeMetal81.setOnClickListener { switchTheme(CassetteTheme.METAL_81_RED) }
    }

    private fun updateThemeButtonsVisual() {
        val curId = themeManager.currentTheme.value.id
        val isEink = curId == CassetteTheme.MONOCHROME_EINK.id
        val activeColor = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#F97316"))

        binding.btnThemeMetalXr.backgroundTintList =
            if (curId == CassetteTheme.METAL_XR_TYPE4.id) activeColor else ColorStateList.valueOf(Color.parseColor("#18191E"))
        binding.btnThemeDark.backgroundTintList =
            if (curId == CassetteTheme.DARK.id) activeColor else ColorStateList.valueOf(Color.parseColor("#202334"))
        binding.btnThemeLight.backgroundTintList =
            if (curId == CassetteTheme.LIGHT.id) activeColor else ColorStateList.valueOf(Color.parseColor("#E5E5E2"))
        binding.btnThemeEink.backgroundTintList =
            if (isEink) activeColor else ColorStateList.valueOf(Color.parseColor("#000000"))

        binding.btnThemeTdk.backgroundTintList =
            if (curId == CassetteTheme.TDK_SA_90.id) activeColor else ColorStateList.valueOf(Color.parseColor("#242220"))
        binding.btnThemeMaxell.backgroundTintList =
            if (curId == CassetteTheme.MAXELL_XLII.id) activeColor else ColorStateList.valueOf(Color.parseColor("#261E14"))
        binding.btnThemeBasf.backgroundTintList =
            if (curId == CassetteTheme.BASF_CHROME.id) activeColor else ColorStateList.valueOf(Color.parseColor("#1F2421"))
        binding.btnThemeSkeleton.backgroundTintList =
            if (curId == CassetteTheme.SKELETON_REEL.id) activeColor else ColorStateList.valueOf(Color.parseColor("#261418"))
        binding.btnThemeStudioReel.backgroundTintList =
            if (curId == CassetteTheme.REEL_TO_REEL_STUDIO.id) activeColor else ColorStateList.valueOf(
                Color.parseColor("#241B14")
            )
        binding.btnThemeSonyHf.backgroundTintList =
            if (curId == CassetteTheme.SONY_HF_90.id) activeColor else ColorStateList.valueOf(Color.parseColor("#181A20"))
        binding.btnThemeDenonHd.backgroundTintList =
            if (curId == CassetteTheme.DENON_HD8_100.id) activeColor else ColorStateList.valueOf(Color.parseColor("#141318"))
        binding.btnThemeMetal81.backgroundTintList =
            if (curId == CassetteTheme.METAL_81_RED.id) activeColor else ColorStateList.valueOf(Color.parseColor("#261014"))

        binding.tvActiveThemeName.text = themeManager.currentTheme.value.name
    }

    private fun setupDapHardwareSettings(app: SpindleApp) {
        val prefs = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)

        // 0. Vintage Lockscreen Player
        binding.switchLockscreenPlayer.isChecked = prefs.getBoolean("pref_lockscreen_player", true)
        binding.switchLockscreenPlayer.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_lockscreen_player", isChecked).apply()
        }

        // 0b. DAP Physical Button Mode
        binding.switchDapHardwareMode.isChecked = prefs.getBoolean("pref_dap_hardware_mode", false)
        binding.switchDapHardwareMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_dap_hardware_mode", isChecked).apply()
            val msg = if (isChecked) {
                "DAP Hardware Mode Active: Physical buttons & wheel prioritized"
            } else {
                "DAP Hardware Mode Disabled"
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // 0c. Cassette Battery Saver (30 FPS)
        binding.switchBatterySaverKinematics.isChecked = prefs.getBoolean("pref_battery_saver_kinematics", false)
        binding.switchBatterySaverKinematics.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_battery_saver_kinematics", isChecked).apply()
            val msg =
                if (isChecked) "Cassette Battery Saver: 30 FPS Active 🔋" else "Cassette High-Performance: 60 FPS ✨"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // 0d. Walkman Physical HOLD Switch Lock
        binding.switchWalkmanHold.isChecked = prefs.getBoolean("pref_walkman_hold_lock_keys", true)
        binding.switchWalkmanHold.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_walkman_hold_lock_keys", isChecked).apply()
        }

        // 0e. MicroSD Low-RAM Buffer Tuning
        binding.switchLowRamBuffer.isChecked = prefs.getBoolean("pref_low_ram_audio_buffer", true)
        binding.switchLowRamBuffer.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_low_ram_audio_buffer", isChecked).apply()
            Toast.makeText(requireContext(), "Buffer setting will apply on next playback session", Toast.LENGTH_SHORT)
                .show()
        }

        // 1. Volume Long-Press Skip
        binding.switchVolumeSkip.isChecked = prefs.getBoolean("pref_volume_skip", true)
        binding.switchVolumeSkip.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_volume_skip", isChecked).apply()
        }

        // Camera Key Play/Stop Remap
        binding.switchCameraKeyPlayPause.isChecked = prefs.getBoolean("pref_camera_key_play_pause", true)
        binding.switchCameraKeyPlayPause.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_camera_key_play_pause", isChecked).apply()
        }

        // 2. Headphone Auto-Pause on Unplug
        binding.switchAutoPauseUnplug.isChecked = prefs.getBoolean("pref_auto_pause_unplug", true)
        binding.switchAutoPauseUnplug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_auto_pause_unplug", isChecked).apply()
        }

        // 3. Headphone Auto-Resume on Plug
        binding.switchAutoResumePlug.isChecked = prefs.getBoolean("pref_auto_resume_plug", false)
        binding.switchAutoResumePlug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_auto_resume_plug", isChecked).apply()
        }

        // 4. Direct USB DAC Bit-Perfect
        binding.switchBitPerfectDirect.isChecked = prefs.getBoolean("pref_bitperfect_direct", true)
        binding.switchBitPerfectDirect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_bitperfect_direct", isChecked).apply()
            app.audioEngine.metricsTracker.updateRouteTelemetry()
        }

        binding.btnUsbDacProbe.setOnClickListener {
            val dac = app.usbDacManager.refreshUsbDacTelemetry()
            app.audioEngine.metricsTracker.updateRouteTelemetry()
            if (dac != null) {
                Toast.makeText(requireContext(), "Detected ${dac.shortBadgeTitle}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "No USB DAC detected on USB bus", Toast.LENGTH_SHORT).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.usbDacManager.usbDacState.collectLatest { dac ->
                    if (dac != null) {
                        binding.tvUsbDacStatusBadge.text = "● USB-OTG DAC ACTIVE"
                        binding.tvUsbDacStatusBadge.setTextColor(Color.parseColor("#00E676"))
                        binding.tvUsbDacTelemetryDetails.text =
                            "${dac.shortBadgeTitle} • VID:${dac.vidHex} PID:${dac.pidHex}\n${dac.audioClass}\n${dac.capabilitiesDescription}"
                    } else {
                        binding.tvUsbDacStatusBadge.text = "NO EXTERNAL USB DAC DETECTED"
                        binding.tvUsbDacStatusBadge.setTextColor(Color.parseColor("#71717A"))
                        binding.tvUsbDacTelemetryDetails.text =
                            "Direct ALSA routing will activate automatically upon USB-OTG connection."
                    }
                }
            }
        }

        // 4b. DLNA / UPnP / Volumio Remote Audio Transport
        val openRemoteDialog = View.OnClickListener {
            com.hana.spindle.remote.DialogRemoteStreamer(app.remoteTransportManager)
                .show(parentFragmentManager, "DialogRemoteStreamer")
        }
        binding.cardRemoteStreamer.setOnClickListener(openRemoteDialog)
        binding.btnRemoteStreamerAction.setOnClickListener(openRemoteDialog)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.remoteTransportManager.activeDevice.collectLatest { device ->
                    if (device != null) {
                        binding.tvRemoteStreamerBadge.text = "● ${device.typeBadge} ACTIVE"
                        binding.tvRemoteStreamerBadge.setTextColor(Color.parseColor("#00E676"))
                        binding.btnRemoteStreamerAction.text = "CONTROL"
                        binding.btnRemoteStreamerAction.setTextColor(Color.parseColor("#00E676"))
                        binding.tvRemoteStreamerDetails.text =
                            "${device.name}\n${device.displaySubtitle}"
                    } else {
                        binding.tvRemoteStreamerBadge.text = "DLNA / VOLUMIO REMOTE TRANSPORT"
                        binding.tvRemoteStreamerBadge.setTextColor(Color.parseColor("#71717A"))
                        binding.btnRemoteStreamerAction.text = "STREAMERS"
                        binding.btnRemoteStreamerAction.setTextColor(Color.WHITE)
                        binding.tvRemoteStreamerDetails.text =
                            "Remote transport control for UPnP/DLNA renderers and Volumio endpoints."
                    }
                }
            }
        }

        // 5. 1-Bit Monochrome E-Ink Mode
        val isEinkTheme = themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id
        val isEinkPref = prefs.getBoolean("pref_eink_mode", isEinkTheme)
        binding.switchEinkMode.isChecked = isEinkPref
        binding.switchEinkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_eink_mode", isChecked).apply()
            if (isChecked) {
                themeManager.setTheme(CassetteTheme.MONOCHROME_EINK)
            } else if (themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id) {
                themeManager.setTheme(CassetteTheme.DARK)
            }
            updateThemeButtonsVisual()
        }

        // 6. Cassette Mechanical Foley SFX & Metro UI Clicks
        binding.switchCassetteFoley.isChecked = app.audioEngine.foleyEngine.isEnabled
        binding.switchCassetteFoley.setOnCheckedChangeListener { _, isChecked ->
            app.audioEngine.foleyEngine.setFoleyEnabled(isChecked)
            if (isChecked) {
                app.audioEngine.foleyEngine.playSwitchSnap()
            }
        }

        binding.switchMetroClicks.isChecked = app.audioEngine.foleyEngine.isMetroClicksEnabled
        binding.switchMetroClicks.setOnCheckedChangeListener { _, isChecked ->
            app.audioEngine.foleyEngine.setMetroClicksEnabled(isChecked)
            if (isChecked) {
                app.audioEngine.foleyEngine.playMetroTick(1.2f)
            }
        }

        binding.switchTactileHaptics.isChecked = app.audioEngine.foleyEngine.haptics.isEnabled
        binding.switchTactileHaptics.setOnCheckedChangeListener { _, isChecked ->
            app.audioEngine.foleyEngine.haptics.setHapticsEnabled(isChecked)
            if (isChecked) {
                app.audioEngine.foleyEngine.playSwitchSnap()
            }
        }

        // 7. ReplayGain Loudness Calibration
        fun updateReplayGainPills(mode: ReplayGainMode) {
            val isOff = mode == ReplayGainMode.OFF
            val isTrack = mode == ReplayGainMode.TRACK
            val isAlbum = mode == ReplayGainMode.ALBUM

            binding.btnRgOff.setTextColor(if (isOff) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnRgOff.backgroundTintList =
                ColorStateList.valueOf(if (isOff) pillOnBg() else Color.parseColor("#242731"))

            binding.btnRgTrack.setTextColor(if (isTrack) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnRgTrack.backgroundTintList =
                ColorStateList.valueOf(if (isTrack) pillOnBg() else Color.parseColor("#242731"))

            binding.btnRgAlbum.setTextColor(if (isAlbum) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnRgAlbum.backgroundTintList =
                ColorStateList.valueOf(if (isAlbum) pillOnBg() else Color.parseColor("#242731"))
        }

        updateReplayGainPills(app.audioEngine.replayGainMode)
        pillRefreshers += { updateReplayGainPills(app.audioEngine.replayGainMode) }

        binding.btnRgOff.setOnClickListener {
            app.audioEngine.setReplayGainMode(ReplayGainMode.OFF)
            updateReplayGainPills(ReplayGainMode.OFF)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "ReplayGain Disabled", Toast.LENGTH_SHORT).show()
        }
        binding.btnRgTrack.setOnClickListener {
            app.audioEngine.setReplayGainMode(ReplayGainMode.TRACK)
            updateReplayGainPills(ReplayGainMode.TRACK)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "ReplayGain Track Gain Active (89 dB)", Toast.LENGTH_SHORT).show()
        }
        binding.btnRgAlbum.setOnClickListener {
            app.audioEngine.setReplayGainMode(ReplayGainMode.ALBUM)
            updateReplayGainPills(ReplayGainMode.ALBUM)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "ReplayGain Album Gain Active (89 dB)", Toast.LENGTH_SHORT).show()
        }

        // 8. Track Transition & Crossfade Mode
        fun updateCrossfadePills(mode: CrossfadeMode) {
            val isGapless = mode == CrossfadeMode.GAPLESS
            val isFade2s = mode == CrossfadeMode.CROSSFADE_2S
            val isFade4s = mode == CrossfadeMode.CROSSFADE_4S

            binding.btnTransGapless.setTextColor(if (isGapless) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnTransGapless.backgroundTintList =
                ColorStateList.valueOf(if (isGapless) pillOnBg() else Color.parseColor("#242731"))

            binding.btnTransFade2s.setTextColor(if (isFade2s) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnTransFade2s.backgroundTintList =
                ColorStateList.valueOf(if (isFade2s) pillOnBg() else Color.parseColor("#242731"))

            binding.btnTransFade4s.setTextColor(if (isFade4s) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnTransFade4s.backgroundTintList =
                ColorStateList.valueOf(if (isFade4s) pillOnBg() else Color.parseColor("#242731"))
        }

        updateCrossfadePills(app.audioEngine.crossfadeMode)
        pillRefreshers += { updateCrossfadePills(app.audioEngine.crossfadeMode) }

        binding.btnTransGapless.setOnClickListener {
            app.audioEngine.setCrossfadeMode(CrossfadeMode.GAPLESS)
            updateCrossfadePills(CrossfadeMode.GAPLESS)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Gapless Playback Enabled (0s)", Toast.LENGTH_SHORT).show()
        }
        binding.btnTransFade2s.setOnClickListener {
            app.audioEngine.setCrossfadeMode(CrossfadeMode.CROSSFADE_2S)
            updateCrossfadePills(CrossfadeMode.CROSSFADE_2S)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Dual-Player Crossfade Enabled (2s)", Toast.LENGTH_SHORT).show()
        }
        binding.btnTransFade4s.setOnClickListener {
            app.audioEngine.setCrossfadeMode(CrossfadeMode.CROSSFADE_4S)
            updateCrossfadePills(CrossfadeMode.CROSSFADE_4S)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Dual-Player Crossfade Enabled (4s)", Toast.LENGTH_SHORT).show()
        }

        // 9. Tape Formulation (Bias) & Dolby NR
        // Pills mirror what the DSP actually applied (changes are ignored while the EQ is locked).
        fun updateTapeBiasPills(@Suppress("UNUSED_PARAMETER") requested: AudioFxController.TapeFormulation) {
            val formulation = audioFxController.currentTapeFormulation
            syncAllEqControlsAction?.invoke()
            val isType1 = formulation == AudioFxController.TapeFormulation.TYPE_I_NORMAL
            val isType2 = formulation == AudioFxController.TapeFormulation.TYPE_II_CHROME
            val isType3 = formulation == AudioFxController.TapeFormulation.TYPE_III_FERRICHROME
            val isType4 = formulation == AudioFxController.TapeFormulation.TYPE_IV_METAL

            binding.btnTapeType1.setTextColor(if (isType1) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnTapeType1.backgroundTintList =
                ColorStateList.valueOf(if (isType1) pillOnBg() else Color.parseColor("#242731"))

            binding.btnTapeType2.setTextColor(if (isType2) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnTapeType2.backgroundTintList =
                ColorStateList.valueOf(if (isType2) pillOnBg() else Color.parseColor("#242731"))

            binding.btnTapeType3.setTextColor(if (isType3) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnTapeType3.backgroundTintList =
                ColorStateList.valueOf(if (isType3) pillOnBg() else Color.parseColor("#242731"))

            binding.btnTapeType4.setTextColor(if (isType4) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnTapeType4.backgroundTintList =
                ColorStateList.valueOf(if (isType4) pillOnBg() else Color.parseColor("#242731"))
        }

        fun updateDolbyPills(@Suppress("UNUSED_PARAMETER") requested: AudioFxController.DolbyMode) {
            val mode = audioFxController.currentDolbyMode
            syncAllEqControlsAction?.invoke()
            val isOff = mode == AudioFxController.DolbyMode.OFF
            val isB = mode == AudioFxController.DolbyMode.DOLBY_B
            val isC = mode == AudioFxController.DolbyMode.DOLBY_C

            binding.btnDolbyOff.setTextColor(if (isOff) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnDolbyOff.backgroundTintList =
                ColorStateList.valueOf(if (isOff) pillOnBg() else Color.parseColor("#242731"))

            binding.btnDolbyB.setTextColor(if (isB) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnDolbyB.backgroundTintList =
                ColorStateList.valueOf(if (isB) pillOnBg() else Color.parseColor("#242731"))

            binding.btnDolbyC.setTextColor(if (isC) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnDolbyC.backgroundTintList =
                ColorStateList.valueOf(if (isC) pillOnBg() else Color.parseColor("#242731"))
        }

        updateTapeBiasPills(app.audioEngine.audioFxController.currentTapeFormulation)
        updateDolbyPills(app.audioEngine.audioFxController.currentDolbyMode)
        pillRefreshers += {
            updateTapeBiasPills(app.audioEngine.audioFxController.currentTapeFormulation)
            updateDolbyPills(app.audioEngine.audioFxController.currentDolbyMode)
        }

        binding.btnTapeType1.setOnClickListener {
            app.audioEngine.setTapeFormulation(AudioFxController.TapeFormulation.TYPE_I_NORMAL)
            updateTapeBiasPills(AudioFxController.TapeFormulation.TYPE_I_NORMAL)
            app.audioEngine.foleyEngine.playSwitchSnap()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Tape Bias: Type I Normal (120µs)", Toast.LENGTH_SHORT).show()
        }
        binding.btnTapeType2.setOnClickListener {
            app.audioEngine.setTapeFormulation(AudioFxController.TapeFormulation.TYPE_II_CHROME)
            updateTapeBiasPills(AudioFxController.TapeFormulation.TYPE_II_CHROME)
            app.audioEngine.foleyEngine.playSwitchSnap()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Tape Bias: Type II Chrome (70µs)", Toast.LENGTH_SHORT).show()
        }
        binding.btnTapeType3.setOnClickListener {
            app.audioEngine.setTapeFormulation(AudioFxController.TapeFormulation.TYPE_III_FERRICHROME)
            updateTapeBiasPills(AudioFxController.TapeFormulation.TYPE_III_FERRICHROME)
            app.audioEngine.foleyEngine.playSwitchSnap()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Tape Bias: Type III FeCr (70µs)", Toast.LENGTH_SHORT).show()
        }
        binding.btnTapeType4.setOnClickListener {
            app.audioEngine.setTapeFormulation(AudioFxController.TapeFormulation.TYPE_IV_METAL)
            updateTapeBiasPills(AudioFxController.TapeFormulation.TYPE_IV_METAL)
            app.audioEngine.foleyEngine.playSwitchSnap()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Tape Bias: Type IV Metal (70µs)", Toast.LENGTH_SHORT).show()
        }

        binding.btnDolbyOff.setOnClickListener {
            app.audioEngine.setDolbyMode(AudioFxController.DolbyMode.OFF)
            updateDolbyPills(AudioFxController.DolbyMode.OFF)
            app.audioEngine.foleyEngine.playSwitchSnap()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Dolby NR: OFF", Toast.LENGTH_SHORT).show()
        }
        binding.btnDolbyB.setOnClickListener {
            app.audioEngine.setDolbyMode(AudioFxController.DolbyMode.DOLBY_B)
            updateDolbyPills(AudioFxController.DolbyMode.DOLBY_B)
            app.audioEngine.foleyEngine.playSwitchSnap()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Dolby NR: B (10dB High Shelf Cut)", Toast.LENGTH_SHORT).show()
        }
        binding.btnDolbyC.setOnClickListener {
            app.audioEngine.setDolbyMode(AudioFxController.DolbyMode.DOLBY_C)
            updateDolbyPills(AudioFxController.DolbyMode.DOLBY_C)
            app.audioEngine.foleyEngine.playSwitchSnap()
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(requireContext(), "Dolby NR: C (20dB High Shelf Cut)", Toast.LENGTH_SHORT).show()
        }

        // 9b. VU Meter Illumination Modes
        fun updateVuTonePills(tone: com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone) {
            val isAmber = tone == com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.WARM_TUNGSTEN
            val isCyan = tone == com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.MCINTOSH_CYAN
            val isGreen = tone == com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.STUDIO_MINT
            val isCream = tone == com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.PARCHMENT

            binding.btnVuToneAmber.setTextColor(if (isAmber) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnVuToneAmber.backgroundTintList =
                ColorStateList.valueOf(if (isAmber) pillOnBg() else Color.parseColor("#242731"))

            binding.btnVuToneCyan.setTextColor(if (isCyan) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnVuToneCyan.backgroundTintList =
                ColorStateList.valueOf(if (isCyan) pillOnBg() else Color.parseColor("#242731"))

            binding.btnVuToneGreen.setTextColor(if (isGreen) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnVuToneGreen.backgroundTintList =
                ColorStateList.valueOf(if (isGreen) pillOnBg() else Color.parseColor("#242731"))

            binding.btnVuToneCream.setTextColor(if (isCream) pillOnText() else Color.parseColor("#A1A1AA"))
            binding.btnVuToneCream.backgroundTintList =
                ColorStateList.valueOf(if (isCream) pillOnBg() else Color.parseColor("#242731"))
        }

        val currentSavedToneName = prefs.getString(
            "pref_vu_backlight_tone",
            com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.WARM_TUNGSTEN.name
        )
        val initialTone = try {
            com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.valueOf(currentSavedToneName ?: "")
        } catch (_: Exception) {
            com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.WARM_TUNGSTEN
        }
        updateVuTonePills(initialTone)
        pillRefreshers += {
            val name = prefs.getString("pref_vu_backlight_tone", initialTone.name)
            updateVuTonePills(
                com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.values().firstOrNull { it.name == name }
                    ?: initialTone
            )
        }

        fun selectVuTone(tone: com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone) {
            prefs.edit().putString("pref_vu_backlight_tone", tone.name).apply()
            updateVuTonePills(tone)
            app.audioEngine.foleyEngine.playSwitchSnap()
            Toast.makeText(requireContext(), "VU Backlight: ${tone.nameLabel}", Toast.LENGTH_SHORT).show()
        }

        binding.btnVuToneAmber.setOnClickListener { selectVuTone(com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.WARM_TUNGSTEN) }
        binding.btnVuToneCyan.setOnClickListener { selectVuTone(com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.MCINTOSH_CYAN) }
        binding.btnVuToneGreen.setOnClickListener { selectVuTone(com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.STUDIO_MINT) }
        binding.btnVuToneCream.setOnClickListener { selectVuTone(com.hana.spindle.ui.eq.DualAnalogVuMeterView.BacklightTone.PARCHMENT) }
    }

    private fun applyDrawerTheme(requested: CassetteTheme) {
        restoreDeckWidgets()
        val theme = contentTheme(requested)
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)
        val isDark = theme.isDarkAppTheme
        val primary = theme.textPrimaryColor
        val secondary = theme.textSecondaryColor
        val cardBg =
            if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#FAFAF9") else Color.parseColor("#171926")

        if (isDeckStyle) {
            val chassis = chassisDrawable?.takeIf { it.colorway == deckColorway }
                ?: com.hana.spindle.ui.cassette.WalkmanChassisDrawable(deckColorway, resources.displayMetrics.density)
                    .also { chassisDrawable?.release(); chassisDrawable = it }
            binding.root.background = chassis
        } else {
            binding.root.setBackgroundColor(theme.chassisColor)
        }
        binding.tvMetroHubTitle.setTextColor(primary)

        // Search Bar adaptation
        binding.etSearchApps.setTextColor(primary)
        binding.etSearchApps.setHintTextColor(secondary)
        binding.etSearchApps.backgroundTintList = ColorStateList.valueOf(
            if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#202334")
        )

        // Alphabet Index Rail
        binding.alphabetIndexView.updateTheme(secondary, theme.accentColor)

        // Propagate text color to AppListAdapter
        appAdapter.updateThemeColors(primary)

        binding.tvBypassTitle.setTextColor(
            if (audioFxController.isBypassEnabled) (if (isEink) Color.BLACK else if (isDark) Color.parseColor(
                "#00E676"
            ) else Color.parseColor("#059669")) else primary
        )
        binding.tvBypassSub.setTextColor(secondary)

        // Propagate isEink to hardware EQ and meter components
        binding.iso10BandEqView.isEink = isEink
        binding.biquadVisualizerView.isEink = isEink
        binding.knobLow.isEink = isEink
        binding.knobMid.isEink = isEink
        binding.knobHi.isEink = isEink
        binding.knobFilter.isEink = isEink
        binding.dualAnalogVuMeter.isEink = isEink

        if (isEink) {
            binding.layoutBtTelemetry.setBackgroundColor(Color.WHITE)
            binding.layoutJackTelemetry.setBackgroundColor(Color.WHITE)
        } else {
            binding.layoutBtTelemetry.setBackgroundColor(Color.parseColor("#0A0B0E"))
            binding.layoutJackTelemetry.setBackgroundColor(Color.parseColor("#0A0B0E"))
        }

        updateCardsRecursively(binding.root, cardBg, primary, secondary, isDark, isEink)
        val headerColor = if (isDark) Color.WHITE else Color.BLACK
        binding.tvMetroHubTitle.setTextColor(headerColor)
        binding.tvSettingsPageTitle.setTextColor(headerColor)
        selectTab(currentTabIndex)
        updateThemeButtonsVisual()
        val accent = if (isEink) Color.BLACK else theme.accentColor
        binding.alphabetIndexView.updateTheme(secondary, accent)
        appAdapter.updateThemeColors(primary)
        styleSettingsTitle()
        styleSoundPanel()
        if (isDeckStyle) styleDeckWidgets()
    }

    private fun updateCardsRecursively(
        view: View,
        cardBg: Int,
        primary: Int,
        secondary: Int,
        isDark: Boolean,
        isEink: Boolean
    ) {
        if (view is androidx.cardview.widget.CardView) {
            view.setCardBackgroundColor(cardBg)
        }
        if (view is TextView && !view.javaClass.simpleName.contains("Button")
            && view.id != R.id.btnTabApps && view.id != R.id.btnTabEq && view.id != R.id.btnTabSettings
        ) {
            if (isEink) {
                view.setTextColor(Color.BLACK)
            } else {
                val current = view.currentTextColor
                val r = Color.red(current)
                val g = Color.green(current)
                val b = Color.blue(current)
                val isNeutral = Math.abs(r - g) < 25 && Math.abs(g - b) < 25
                if (isNeutral) {
                    val brightness = (r * 299 + g * 587 + b * 114) / 1000
                    if (!isDark) {
                        if (brightness > 190) {
                            view.setTextColor(primary)
                        } else if (brightness in 110..190) {
                            view.setTextColor(secondary)
                        }
                    } else {
                        if (brightness < 70) {
                            view.setTextColor(primary)
                        } else if (current == Color.parseColor("#5A5E78") || current == Color.parseColor("#64748B")) {
                            view.setTextColor(secondary)
                        }
                    }
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                updateCardsRecursively(view.getChildAt(i), cardBg, primary, secondary, isDark, isEink)
            }
        }
    }

    // =========================================================================
    // 5. GROUPED SETTINGS: IMMERSIVE DISPLAY & LAUNCHER
    // =========================================================================
    private fun setupImmersiveModeToggle() {
        val mainActivity = activity as? MainActivity ?: return
        binding.switchImmersiveMode.isChecked = mainActivity.isImmersiveModeEnabled
        binding.switchImmersiveMode.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.setImmersiveMode(isChecked)
        }
        setupDeckStyleSelector()
    }

    private fun setupDeckStyleSelector() {
        val prefs = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val styles = com.hana.spindle.ui.cassette.WalkmanColorway.values().map { it.prefValue } +
                com.hana.spindle.ui.cassette.WalkmanColorway.STYLE_CLASSIC

        fun label(style: String?) = com.hana.spindle.ui.cassette.WalkmanColorway.fromPref(style)?.displayName
            ?: "CLASSIC VERTICAL DECK"

        val key = com.hana.spindle.ui.cassette.WalkmanColorway.PREF_DECK_STYLE
        val default = com.hana.spindle.ui.cassette.WalkmanColorway.DEFAULT_STYLE

        // Cassette shell models only restyle the classic deck; the Walkman deck has its own colorways.
        fun syncShellModels(style: String?) {
            val isClassic = com.hana.spindle.ui.cassette.WalkmanColorway.fromPref(style) == null
            binding.tvShellModelsLabel.visibility = if (isClassic) View.VISIBLE else View.GONE
            binding.shellModelsScroll.visibility = if (isClassic) View.VISIBLE else View.GONE
        }

        binding.btnDeckStyle.text = label(prefs.getString(key, default))
        syncShellModels(prefs.getString(key, default))
        binding.btnDeckStyle.setOnClickListener {
            val current = prefs.getString(key, default)
            val next = styles[(styles.indexOf(current).coerceAtLeast(0) + 1) % styles.size]
            prefs.edit().putString(key, next).apply()
            binding.btnDeckStyle.text = label(next)
            syncShellModels(next)
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun setupDoubleTapSettings() {
        val prefs = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("pref_double_tap_sleep", true)
        binding.switchDoubleTapSleep.isChecked = isEnabled
        binding.switchDoubleTapSleep.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_double_tap_sleep", isChecked).apply()
        }

        binding.btnSystemTapToWake.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Open Settings > Display to configure native Tap to Wake",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupSleepTimer(app: SpindleApp) {
        val audioEngine = app.audioEngine

        binding.btnSleep15m.setOnClickListener {
            audioEngine.startSleepTimer(15)
            Toast.makeText(requireContext(), "Sleep timer set: 15 minutes", Toast.LENGTH_SHORT).show()
        }
        binding.btnSleep30m.setOnClickListener {
            audioEngine.startSleepTimer(30)
            Toast.makeText(requireContext(), "Sleep timer set: 30 minutes", Toast.LENGTH_SHORT).show()
        }
        binding.btnSleep45m.setOnClickListener {
            audioEngine.startSleepTimer(45)
            Toast.makeText(requireContext(), "Sleep timer set: 45 minutes", Toast.LENGTH_SHORT).show()
        }
        binding.btnSleep60m.setOnClickListener {
            audioEngine.startSleepTimer(60)
            Toast.makeText(requireContext(), "Sleep timer set: 60 minutes", Toast.LENGTH_SHORT).show()
        }
        binding.btnSleepEndTrack.setOnClickListener {
            audioEngine.startSleepTimer(0, stopAfterCurrentTrack = true)
            Toast.makeText(requireContext(), "Sleep timer set: End of current track", Toast.LENGTH_SHORT).show()
        }
        binding.btnSleepCancel.setOnClickListener {
            audioEngine.cancelSleepTimer()
            Toast.makeText(requireContext(), "Sleep timer cancelled", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                audioEngine.sleepTimerState.collectLatest { timerState ->
                    _binding?.let { b ->
                        if (timerState.isActive) {
                            val mins = timerState.remainingSeconds / 60
                            val secs = timerState.remainingSeconds % 60
                            val label = if (timerState.stopAfterCurrentTrack) {
                                String.format(Locale.US, "TRACK END (%02d:%02d)", mins, secs)
                            } else {
                                String.format(Locale.US, "%02d:%02d REMAINING", mins, secs)
                            }
                            b.tvSleepTimerStatus.text = label
                            b.tvSleepTimerStatus.setTextColor(Color.parseColor("#00E676"))
                            b.tvSleepTimerStatus.setBackgroundColor(Color.parseColor("#16261B"))
                        } else {
                            b.tvSleepTimerStatus.text = "OFF"
                            b.tvSleepTimerStatus.setTextColor(Color.parseColor("#A1A1AA"))
                            b.tvSleepTimerStatus.setBackgroundColor(Color.parseColor("#1E2026"))
                        }
                    }
                }
            }
        }
    }

    private fun setupDefaultLauncher() {
        binding.btnSetDefaultLauncher.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e2: Exception) {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(Intent.createChooser(homeIntent, "Select Spindle as Default Home Launcher"))
                }
            }
        }
    }

    // =========================================================================
    // 6. GROUPED SETTINGS: OFFLINE MUSIC PATH SELECTOR & SCANNER
    // =========================================================================
    private fun setupLibraryRescan(app: SpindleApp) {
        updateMusicPathDisplay(app)

        viewLifecycleOwner.lifecycleScope.launch {
            val count = app.database.trackDao().getTrackCount()
            binding.tvSongCount.text = "$count tracks indexed in local database"
        }

        binding.btnSelectMusicFolder.setOnClickListener {
            try {
                folderPickerLauncher.launch(null)
            } catch (e: Exception) {
                // Fallback direct folder picker dialog
                showDirectFolderChooserDialog(app)
            }
        }

        binding.btnResetMusicFolder.setOnClickListener {
            app.musicScanner.setCustomMusicPath(null)
            updateMusicPathDisplay(app)
            Toast.makeText(requireContext(), "Scan location reset to All Storage", Toast.LENGTH_SHORT).show()
            binding.btnRescanLibrary.performClick()
        }

        binding.btnRescanLibrary.setOnClickListener {
            binding.btnRescanLibrary.isEnabled = false
            binding.btnRescanLibrary.text = "SCANNING STORAGE..."
            binding.progressScan.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                app.musicScanner.scanAll()
                val count = app.database.trackDao().getTrackCount()
                binding.tvSongCount.text = "$count tracks indexed in local database"
                updateMusicPathDisplay(app)
                binding.btnRescanLibrary.text = "RESCAN MUSIC STORAGE"
                binding.btnRescanLibrary.isEnabled = true
                binding.progressScan.visibility = View.GONE
                Toast.makeText(requireContext(), "Scan complete: $count tracks ready", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSyncMicroSdPlaylists.setOnClickListener {
            MixtapeDialogs.showSyncMicroSdMixtapesDialog(
                requireContext(),
                app.database,
                viewLifecycleOwner.lifecycleScope
            )
        }

        binding.btnRebuildIndexCache.setOnClickListener {
            binding.btnRebuildIndexCache.isEnabled = false
            binding.progressScan.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Rebuilding fast binary index cache...", Toast.LENGTH_SHORT).show()

            viewLifecycleOwner.lifecycleScope.launch {
                app.musicScanner.rebuildFastIndexCache()
                val count = app.database.trackDao().getTrackCount()
                binding.tvSongCount.text = "$count tracks indexed in local database"
                updateMusicPathDisplay(app)
                binding.btnRebuildIndexCache.isEnabled = true
                binding.progressScan.visibility = View.GONE
                Toast.makeText(requireContext(), "Fast index cache (.spindle_catalog.bin) updated!", Toast.LENGTH_LONG)
                    .show()
            }
        }

        binding.btnFetchMissingArt.setOnClickListener {
            val dialog = DialogCoverArtFetcher {
                Toast.makeText(requireContext(), "Album artwork cache refreshed", Toast.LENGTH_SHORT).show()
            }
            dialog.show(parentFragmentManager, "DialogCoverArtFetcher")
        }

        binding.btnFetchMissingLyrics.setOnClickListener {
            val dialog = DialogLyricsBulkFetcher {
                Toast.makeText(requireContext(), "Synchronized lyrics library check complete", Toast.LENGTH_SHORT)
                    .show()
            }
            dialog.show(parentFragmentManager, "DialogLyricsBulkFetcher")
        }
    }

    private fun updateMusicPathDisplay(app: SpindleApp) {
        val custom = app.musicScanner.getCustomMusicPath()
        binding.tvCurrentMusicPath.text = if (!custom.isNullOrBlank()) {
            custom
        } else {
            "All Mounted Storage (Internal & MicroSD)"
        }
        val hasCache = app.musicScanner.hasFastIndexCache()
        binding.tvCatalogCacheStatus.text = if (hasCache) {
            "⚡ Fast Index Cache: Active (.spindle_catalog.bin)"
        } else {
            "⚡ Fast Index Cache: Auto-generated on scan"
        }
    }

    private fun getResolvedPathFromUri(uri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val type = split.getOrNull(0) ?: ""
            val relativePath = if (split.size > 1) split[1] else ""
            if ("primary".equals(type, ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
            } else {
                "/storage/$type/$relativePath"
            }
        } catch (e: Exception) {
            uri.path ?: ""
        }
    }

    private fun showDirectFolderChooserDialog(app: SpindleApp) {
        val paths = mutableListOf<String>()
        val defaultMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath
        if (defaultMusic != null && File(defaultMusic).exists()) paths.add(defaultMusic)
        val extStorage = Environment.getExternalStorageDirectory()?.absolutePath
        if (extStorage != null && File(extStorage).exists()) paths.add(extStorage)
        val storageRoot = File("/storage")
        if (storageRoot.exists() && storageRoot.isDirectory) {
            storageRoot.listFiles()?.forEach { f ->
                if (f.isDirectory && f.canRead() && !f.name.equals("emulated", true) && !f.name.equals("self", true)) {
                    paths.add(f.absolutePath)
                }
            }
        }
        paths.add("Enter custom directory path...")

        AlertDialog.Builder(requireContext())
            .setTitle("Select Offline Music Folder")
            .setItems(paths.toTypedArray()) { _, which ->
                if (which == paths.size - 1) {
                    showCustomPathInputDialog(app)
                } else {
                    val chosen = paths[which]
                    app.musicScanner.setCustomMusicPath(chosen)
                    updateMusicPathDisplay(app)
                    binding.btnRescanLibrary.performClick()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showCustomPathInputDialog(app: SpindleApp) {
        val input = EditText(requireContext()).apply {
            hint = "/storage/emulated/0/Music"
            setPadding(32, 24, 32, 24)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Enter Music Folder Path")
            .setView(input)
            .setPositiveButton("Set Path") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isNotBlank()) {
                    app.musicScanner.setCustomMusicPath(path)
                    updateMusicPathDisplay(app)
                    binding.btnRescanLibrary.performClick()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // =========================================================================
    // 7. GROUPED SETTINGS: FM INTERNET RADIO (ADD & DELETE STATIONS)
    // =========================================================================
    private fun setupRadioStationManager(app: SpindleApp) {
        val container = binding.llRadioStationsList
        container.removeAllViews()

        val stations = app.radioStreamEngine.userStations
        for (station in stations) {
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 16, 20, 16)
                background = ContextCompat.getDrawable(context, R.drawable.bg_card_dark)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 10
                }
                layoutParams = params

                val infoLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val tvName = TextView(context).apply {
                        text = "${station.callsign} (${String.format(Locale.US, "%.1f", station.frequencyMhz)} MHz)"
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val tvUrl = TextView(context).apply {
                        text = "${station.genre} • ${station.streamUrl}"
                        setTextColor(Color.parseColor("#71717A"))
                        textSize = 10.5f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    addView(tvName)
                    addView(tvUrl)
                }
                addView(infoLayout)

                // TUNE Button
                val btnTune = Button(context).apply {
                    text = "TUNE"
                    textSize = 10f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F97316"))
                    val btnParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (34 * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = 8
                    }
                    layoutParams = btnParams
                    setOnClickListener {
                        app.radioStreamEngine.playStation(station)
                        (activity as? MainActivity)?.navigateToRadio()
                    }
                }
                addView(btnTune)

                // DELETE Button
                val btnDelete = Button(context).apply {
                    text = "DEL"
                    textSize = 10f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(Color.parseColor("#FB7185"))
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A2E45"))
                    val btnParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (34 * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = 6
                    }
                    layoutParams = btnParams
                    setOnClickListener {
                        app.radioStreamEngine.removeStation(station.frequencyMhz)
                        setupRadioStationManager(app)
                        Toast.makeText(requireContext(), "Deleted ${station.callsign}", Toast.LENGTH_SHORT).show()
                    }
                }
                addView(btnDelete)
            }
            container.addView(card)
        }

        binding.btnOpenAddStation.setOnClickListener {
            showAddStationDialog(app)
        }

        binding.btnResetStations.setOnClickListener {
            app.radioStreamEngine.resetStationsToDefaults()
            setupRadioStationManager(app)
            Toast.makeText(requireContext(), "Radio presets reset to defaults", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddStationDialog(app: SpindleApp) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        val etFreq = EditText(requireContext()).apply {
            hint = "Frequency in MHz (e.g. 96.5)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etCallsign = EditText(requireContext()).apply {
            hint = "Station Callsign / Name (e.g. JAZZ FM)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val etGenre = EditText(requireContext()).apply {
            hint = "Genre (e.g. Jazz / Chill)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val etUrl = EditText(requireContext()).apply {
            hint = "Stream URL (https://...)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        layout.addView(etFreq)
        layout.addView(etCallsign)
        layout.addView(etGenre)
        layout.addView(etUrl)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Custom Radio Station")
            .setView(layout)
            .setPositiveButton("Save Station") { _, _ ->
                val freq = etFreq.text.toString().toFloatOrNull()
                val callsign = etCallsign.text.toString().trim()
                val genre = etGenre.text.toString().trim()
                val url = etUrl.text.toString().trim()

                if (freq != null && freq > 0f && callsign.isNotBlank() && url.isNotBlank()) {
                    val newStation = RadioStation(
                        frequencyMhz = freq,
                        callsign = callsign,
                        rdsName = "$callsign • LIVE FM STREAM",
                        genre = if (genre.isBlank()) "Online Radio" else genre,
                        streamUrl = url
                    )
                    app.radioStreamEngine.addStation(newStation)
                    setupRadioStationManager(app)
                    Toast.makeText(requireContext(), "Added station $callsign ($freq MHz)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Invalid station details. Please check frequency and URL.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // =========================================================================
    // 8. AUDIO HARDWARE TELEMETRY & ABOUT GITHUB LINK
    // =========================================================================
    private fun setupAudioMetrics(app: SpindleApp) {
        val metricsTracker = app.audioEngine.metricsTracker

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                metricsTracker.metrics.collectLatest { metrics ->
                    _binding?.let { b ->
                        b.tvMetricFormat.text = "${metrics.format.uppercase()} (${metrics.bitDepth}-bit)"
                        b.tvMetricSampleRate.text = "${metrics.bitDepth}-bit / ${
                            String.format(
                                Locale.US,
                                "%.1f",
                                metrics.sampleRate / 1000f
                            )
                        } kHz"
                        b.tvMetricBitrate.text = "${metrics.dynamicBitrateKbps} kbps dynamic"
                        b.tvMetricRoute.text = metrics.outputRoute

                        val isEink = app.themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id

                        if (metrics.isBluetoothConnected) {
                            b.tvMetricBtStatus.text = "CONNECTED"
                            b.tvMetricBtStatus.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#00E676"))
                            b.tvMetricBtDevice.text = metrics.bluetoothDeviceName ?: "Bluetooth Device"
                            b.tvMetricBtDevice.setTextColor(if (isEink) Color.BLACK else Color.WHITE)

                            if (metrics.bluetoothBatteryPct != null && metrics.bluetoothBatteryPct >= 0) {
                                val batteryStatus = when {
                                    metrics.bluetoothBatteryPct < 20 -> "Low"
                                    metrics.bluetoothBatteryPct < 50 -> "Adequate"
                                    else -> "Healthy"
                                }
                                b.tvMetricBtBattery.text = "Battery: ${metrics.bluetoothBatteryPct}% ($batteryStatus)"
                                b.tvMetricBtBattery.setTextColor(
                                    if (isEink) Color.BLACK else if (metrics.bluetoothBatteryPct < 20) Color.parseColor(
                                        "#EF4444"
                                    ) else Color.parseColor("#00E676")
                                )
                            } else {
                                b.tvMetricBtBattery.text = "Battery: Telemetry not reported by device"
                                b.tvMetricBtBattery.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#A1A1AA"))
                            }
                        } else {
                            b.tvMetricBtStatus.text = "DISCONNECTED"
                            b.tvMetricBtStatus.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#71717A"))
                            b.tvMetricBtDevice.text = "No Bluetooth Device Connected"
                            b.tvMetricBtDevice.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#A1A1AA"))
                            b.tvMetricBtBattery.text = "Internal / 3.5mm DAC Active"
                            b.tvMetricBtBattery.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#71717A"))
                        }

                        // 3.5mm Headphone Jack Hardware Telemetry
                        if (metrics.jackType != null) {
                            b.layoutJackTelemetry.visibility = View.VISIBLE
                            b.tvMetricJackType.text = metrics.jackType
                            b.tvMetricJackCapabilities.text =
                                metrics.jackCapabilities ?: "Hardware DAC • 16-32bit / up to 384kHz"
                        } else {
                            b.layoutJackTelemetry.visibility = View.GONE
                        }

                        if (metrics.isBitPerfect) {
                            b.tvBitPerfectBadge.text = "● HI-RES PASSTHROUGH"
                            b.tvBitPerfectBadge.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#00E676"))
                        } else {
                            b.tvBitPerfectBadge.text = "AudioFlinger Resampled"
                            b.tvBitPerfectBadge.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#F59E0B"))
                        }
                    }
                }
            }
        }
    }

    private fun setupAboutGithub() {
        try {
            binding.tvAboutVersion.text =
                "Spindle OS v${com.hana.spindle.BuildConfig.VERSION_NAME} (Audiophile Edition)"
        } catch (e: Exception) {
            // fallback to layout default
        }
        binding.btnGithubLink.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/manaphassan/Spindle")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Could not open browser: https://github.com/manaphassan/Spindle",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.btnDonatePaypal.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/manaphassan")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Donate via PayPal: https://paypal.me/manaphassan", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun setupStudioCollectorSuite() {
        fun updateBadge() {
            val isUnlocked = StudioUnlockManager.isStudioUnlocked(requireContext())
            if (isUnlocked) {
                binding.tvStudioStatusCardBadge.text = getString(R.string.studio_badge_unlocked)
                binding.tvStudioStatusCardBadge.setTextColor(Color.parseColor("#00E676"))
            } else {
                binding.tvStudioStatusCardBadge.text = getString(R.string.studio_badge_free)
                binding.tvStudioStatusCardBadge.setTextColor(Color.parseColor("#38BDF8"))
            }
            updateThemeButtonsVisual()
            (requireActivity().application as? SpindleApp)?.audioEngine?.let { engine ->
                binding.switchTapeSaturation.isChecked = engine.isTapeSaturationEnabled
                binding.seekTapeSaturationDrive.isEnabled = engine.isTapeSaturationEnabled
                binding.containerTapeSaturationDrive.alpha = if (engine.isTapeSaturationEnabled) 1.0f else 0.4f
            }
        }

        updateBadge()

        binding.btnManageStudioPass.setOnClickListener {
            DialogStudioUnlock(requireContext()) {
                updateBadge()
            }.show()
        }

        binding.btnCustomLaserEngraving.setOnClickListener {
            if (!StudioUnlockManager.isFeatureUnlocked(
                    requireContext(),
                    StudioFeature.LASER_NAMEPLATE_ENGRAVING
                )
            ) {
                Toast.makeText(requireContext(), getString(R.string.studio_nameplate_locked_prompt), Toast.LENGTH_LONG)
                    .show()
                DialogStudioUnlock(requireContext()) {
                    updateBadge()
                }.show()
                return@setOnClickListener
            }

            val input = EditText(requireContext()).apply {
                hint = getString(R.string.studio_nameplate_hint)
                val current = requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
                    .getString("pref_custom_device_name", null)
                setText(current ?: com.hana.spindle.util.DeviceUtils.getHardwareDeviceName())
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setPadding(32, 24, 32, 24)
            }

            AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.studio_nameplate_title))
                .setView(input)
                .setPositiveButton(getString(R.string.action_done)) { _, _ ->
                    val customName = input.text.toString().trim().uppercase()
                    requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("pref_custom_device_name", customName)
                        .apply()
                    Toast.makeText(requireContext(), getString(R.string.studio_nameplate_saved), Toast.LENGTH_SHORT)
                        .show()
                }
                .setNegativeButton(getString(R.string.action_close), null)
                .setNeutralButton(getString(R.string.action_reset)) { _, _ ->
                    requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .remove("pref_custom_device_name")
                        .apply()
                    Toast.makeText(requireContext(), "Hardware nameplate reset to default", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pillRefreshers.clear()
        requireContext().getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(deckStyleListener)
        chassisDrawable?.release()
        chassisDrawable = null
        _binding = null
    }
}
