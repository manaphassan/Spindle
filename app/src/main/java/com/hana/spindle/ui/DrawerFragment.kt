package com.hana.spindle.ui

import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.databinding.FragmentDrawerBinding
import com.hana.spindle.launcher.AppInfo
import com.hana.spindle.launcher.AppListAdapter
import com.hana.spindle.launcher.AppListLoader
import com.hana.spindle.theme.CassetteTheme
import com.hana.spindle.theme.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class DrawerFragment : Fragment() {

    private var _binding: FragmentDrawerBinding? = null
    private val binding get() = _binding!!

    private lateinit var themeManager: ThemeManager
    private lateinit var appListLoader: AppListLoader
    private lateinit var appAdapter: AppListAdapter
    private var allApps: List<AppInfo> = emptyList()

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
        appListLoader = AppListLoader(requireContext())

        setupTabs()
        setupAppDrawer()
        setupAudioMetrics(app)
        setupThemeSelector()
    }

    private fun setupTabs() {
        binding.tabApps.setOnClickListener { selectTab(0) }
        binding.tabMetrics.setOnClickListener { selectTab(1) }
        binding.tabEqThemes.setOnClickListener { selectTab(2) }
    }

    private fun selectTab(index: Int) {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.wm2_red)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.surface_elevated)
        val activeText = ContextCompat.getColor(requireContext(), R.color.white)
        val inactiveText = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.tabApps.backgroundTintList = ColorStateList.valueOf(if (index == 0) activeColor else inactiveColor)
        binding.tabApps.setTextColor(if (index == 0) activeText else inactiveText)
        binding.containerApps.visibility = if (index == 0) View.VISIBLE else View.GONE

        binding.tabMetrics.backgroundTintList = ColorStateList.valueOf(if (index == 1) activeColor else inactiveColor)
        binding.tabMetrics.setTextColor(if (index == 1) activeText else inactiveText)
        binding.containerMetrics.visibility = if (index == 1) View.VISIBLE else View.GONE

        binding.tabEqThemes.backgroundTintList = ColorStateList.valueOf(if (index == 2) activeColor else inactiveColor)
        binding.tabEqThemes.setTextColor(if (index == 2) activeText else inactiveText)
        binding.containerEqThemes.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    private fun setupAppDrawer() {
        appAdapter = AppListAdapter { appInfo ->
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(appInfo.packageName, appInfo.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(launchIntent)
        }

        binding.rvApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApps.adapter = appAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            allApps = appListLoader.loadInstalledApps()
            appAdapter.submitList(allApps)
        }

        binding.etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                val filtered = if (query.isEmpty()) {
                    allApps
                } else {
                    allApps.filter { it.label.lowercase(Locale.ROOT).contains(query) }
                }
                appAdapter.submitList(filtered)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })
    }

    private fun setupAudioMetrics(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.audioEngine.metricsTracker.metrics.collectLatest { metrics ->
                _binding?.let { b ->
                    b.tvMetricFormat.text = metrics.format
                    b.tvMetricSampleRate.text = "${metrics.bitDepth}-bit / ${metrics.sampleRate / 1000.0} kHz"
                    b.tvMetricBitrate.text = "${metrics.dynamicBitrateKbps} kbps"
                    b.tvMetricRoute.text = metrics.outputRoute

                    if (metrics.isBitPerfect) {
                        b.tvBitPerfectBadge.text = "● BIT-PERFECT NATIVE"
                        b.tvBitPerfectBadge.setTextColor(Color.parseColor("#00E676"))
                    } else {
                        b.tvBitPerfectBadge.text = "▲ RESAMPLED (48kHz)"
                        b.tvBitPerfectBadge.setTextColor(Color.parseColor("#FFB300"))
                    }
                }
            }
        }
    }

    private fun setupThemeSelector() {
        val container = binding.llThemeButtons
        container.removeAllViews()

        for (theme in CassetteTheme.ALL_PRESETS) {
            val btn = Button(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    140
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                text = "${theme.name} — ${theme.subtitle}"
                textSize = 13f
                isAllCaps = false
                setTextColor(if (theme.isDarkAppTheme) Color.WHITE else Color.parseColor("#1E293B"))
                backgroundTintList = ColorStateList.valueOf(theme.chassisColor)
                setOnClickListener {
                    themeManager.setTheme(theme)
                }
            }
            container.addView(btn)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
