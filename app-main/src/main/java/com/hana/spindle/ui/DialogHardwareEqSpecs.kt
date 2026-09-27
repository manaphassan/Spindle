package com.hana.spindle.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.databinding.DialogHardwareEqSpecsBinding
import com.hana.spindle.playback.AudioFxController
import java.util.Locale

/**
 * Bottom sheet dialog disclosing physical hardware Equalizer bands,
 * center frequencies, passband ranges, and active DSP interpolation status.
 */
class DialogHardwareEqSpecs(
    private val fxController: AudioFxController
) : BottomSheetDialogFragment() {

    private var _binding: DialogHardwareEqSpecsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogHardwareEqSpecsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseHwEqSpecs.setOnClickListener {
            dismiss()
        }

        val isBypass = fxController.isBypassEnabled
        binding.tvHwEqStatusBadge.text = if (isBypass) "BYPASS (DIRECT)" else "ACTIVE DSP"
        binding.tvHwEqStatusBadge.setTextColor(
            if (isBypass) Color.parseColor("#FFB74D") else Color.parseColor("#00E676")
        )
        binding.tvHwEqStatusBadge.setBackgroundColor(
            if (isBypass) Color.parseColor("#382810") else Color.parseColor("#1B382B")
        )

        val bandsCount = fxController.hardwareBandsCount
        binding.tvHwEqDetectedBands.text = "$bandsCount Physical Bands"

        val range = fxController.getHardwareLevelRangeDb()
        binding.tvHwEqRange.text = String.format(Locale.US, "%.1f to +%.1f dB", range.first, range.second)

        binding.tvHwEqInterpolationMode.text = when {
            fxController.isParametricMode -> "Parametric Multi-Bell Summation"
            fxController.isHardwareInterpolated -> "10-Band ISO Log2 Interpolated"
            else -> "Native 10-Band ISO Graphic"
        }

        var maxBoost = 0f
        for (gain in fxController.isoBandsGainDb) {
            if (gain > maxBoost) maxBoost = gain
        }
        val headroomAttenuation = maxBoost * 0.35f
        binding.tvHwEqHeadroomComp.text = if (headroomAttenuation > 0f) {
            String.format(Locale.US, "-%.1f dB Headroom", headroomAttenuation)
        } else {
            "0.0 dB Headroom"
        }

        // Populate physical hardware bands
        val hwBands = fxController.getHardwareBands()
        binding.containerHwBandsList.removeAllViews()

        if (hwBands.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = "No hardware equalizer attached to active audio session."
                setTextColor(Color.parseColor("#71717A"))
                textSize = 12f
                setPadding(0, 16, 0, 16)
            }
            binding.containerHwBandsList.addView(emptyTv)
        } else {
            for (band in hwBands) {
                val bandRow = createBandRow(band)
                binding.containerHwBandsList.addView(bandRow)
            }
        }
    }

    private fun createBandRow(band: AudioFxController.HardwareEqBand): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15171C"))
            setPadding(24, 16, 24, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
            layoutParams = params
        }

        // Left: Band Index & Center Frequency
        val leftLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        }

        val tvTitle = TextView(requireContext()).apply {
            text = "HARDWARE BAND #${String.format(Locale.US, "%02d", band.index + 1)}"
            setTextColor(Color.parseColor("#71717A"))
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
        }

        val freqText = if (band.centerFreqHz >= 1000) {
            if (band.centerFreqHz % 1000 == 0) "${band.centerFreqHz / 1000} kHz" else String.format(Locale.US, "%.1f kHz", band.centerFreqHz / 1000.0)
        } else {
            "${band.centerFreqHz} Hz"
        }

        val tvCenter = TextView(requireContext()).apply {
            text = freqText
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            typeface = Typeface.MONOSPACE
        }

        leftLayout.addView(tvTitle)
        leftLayout.addView(tvCenter)

        // Middle: Passband Range
        val midLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
        }

        val tvRangeLabel = TextView(requireContext()).apply {
            text = "PASSBAND RANGE"
            setTextColor(Color.parseColor("#71717A"))
            textSize = 9.5f
        }

        val minStr = if (band.minFreqHz >= 1000) "${band.minFreqHz / 1000}k" else "${band.minFreqHz}"
        val maxStr = if (band.maxFreqHz >= 1000) "${band.maxFreqHz / 1000}k" else "${band.maxFreqHz}"
        val tvRangeVal = TextView(requireContext()).apply {
            text = "$minStr – $maxStr Hz"
            setTextColor(Color.parseColor("#E4E4E7"))
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
        }

        midLayout.addView(tvRangeLabel)
        midLayout.addView(tvRangeVal)

        // Right: Active Gain Level
        val rightLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
        }

        val tvGainLabel = TextView(requireContext()).apply {
            text = "APPLIED GAIN"
            setTextColor(Color.parseColor("#71717A"))
            textSize = 9.5f
            gravity = Gravity.END
        }

        val sign = if (band.currentGainDb > 0f) "+" else ""
        val tvGainVal = TextView(requireContext()).apply {
            text = String.format(Locale.US, "%s%.1f dB", sign, band.currentGainDb)
            setTextColor(
                when {
                    band.currentGainDb > 0.05f -> Color.parseColor("#00E676")
                    band.currentGainDb < -0.05f -> Color.parseColor("#FFB74D")
                    else -> Color.parseColor("#A1A1AA")
                }
            )
            textSize = 12.5f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
        }

        rightLayout.addView(tvGainLabel)
        rightLayout.addView(tvGainVal)

        row.addView(leftLayout)
        row.addView(midLayout)
        row.addView(rightLayout)

        return row
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
