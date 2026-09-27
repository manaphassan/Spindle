package com.hana.spindle.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.data.db.TrackEntity
import com.hana.spindle.databinding.DialogFileSpecsBinding
import java.io.File
import java.util.Locale

class DialogFileSpecs(
    private val track: TrackEntity
) : BottomSheetDialogFragment() {
    private val song: TrackEntity get() = track

    private var _binding: DialogFileSpecsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFileSpecsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val file = File(song.path)
        val fileLengthMb = if (file.exists()) {
            String.format(Locale.US, "%.2f MB", file.length().toDouble() / (1024.0 * 1024.0))
        } else {
            "Unknown"
        }

        val calculatedBitrate = if (song.bitrateKbps > 0) {
            "${song.bitrateKbps} kbps"
        } else if (song.durationMs > 0 && file.exists()) {
            "${((file.length() * 8L) / song.durationMs).toInt()} kbps"
        } else {
            "320 kbps"
        }

        val sampleRateKHz = if (song.sampleRate % 1000 == 0) {
            "${song.sampleRate / 1000}.0 kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", song.sampleRate / 1000.0)
        }

        val isHiRes = song.bitDepth >= 24 || song.sampleRate >= 88200 || song.fileFormat in setOf("FLAC", "WAV", "DSD", "DSF", "AIFF")
        val isLossless = song.fileFormat in setOf("FLAC", "WAV", "ALAC", "AIFF", "DSD", "DSF")

        binding.tvSpecFormat.text = "${song.fileFormat} (${if (isLossless) "Lossless" else "Lossy"})"
        binding.tvSpecBitrate.text = calculatedBitrate
        binding.tvSpecSampleRate.text = "${song.sampleRate} Hz ($sampleRateKHz)"
        binding.tvSpecBitDepth.text = "${song.bitDepth}-bit ${if (song.bitDepth >= 24) "Studio Master" else "CD Quality"}"
        binding.tvSpecChannels.text = "${song.channels} Channels (Stereo)"
        binding.tvSpecQuality.text = if (isHiRes) "Hi-Res Studio Master" else if (isLossless) "Lossless CD Quality" else "Compressed Audio"

        binding.tvSpecPath.text = song.path
        binding.tvSpecSize.text = fileLengthMb

        val lrcFile = File(file.parentFile, "${file.nameWithoutExtension}.lrc")
        val txtFile = File(file.parentFile, "${file.nameWithoutExtension}.txt")
        val lyricsStatus = if (lrcFile.exists()) {
            "Synchronized (.lrc file)"
        } else if (txtFile.exists()) {
            "Plain text (.txt file)"
        } else if (song.hasLyrics) {
            "Embedded Metadata"
        } else {
            "None found"
        }
        binding.tvSpecLyrics.text = lyricsStatus

        // Output Route & Hardware DAC Telemetry
        val app = requireActivity().application as? com.hana.spindle.SpindleApp
        val metrics = app?.audioEngine?.metricsTracker?.metrics?.value
        val usbDac = app?.usbDacManager?.usbDacState?.value

        val routeText = metrics?.outputRoute ?: "3.5mm Headphone Jack"
        binding.tvSpecOutputRoute.text = routeText

        val isBitPerfect = metrics?.isBitPerfect ?: true
        binding.tvSpecBitPerfectBadge.text = if (isBitPerfect) "BIT-PERFECT" else "RESAMPLED"
        binding.tvSpecBitPerfectBadge.setTextColor(
            if (isBitPerfect) android.graphics.Color.parseColor("#00E676") else android.graphics.Color.parseColor("#FFB74D")
        )

        if (usbDac != null) {
            binding.tvSpecDacStatus.text = "${usbDac.brandBadge} External"
            binding.tvSpecDacStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))

            binding.containerSpecUsbDac.visibility = View.VISIBLE
            binding.tvSpecUsbDacName.text = usbDac.productName
            binding.tvSpecUsbDacTelemetry.text = "VID: ${usbDac.vidHex} • PID: ${usbDac.pidHex} • ${usbDac.audioClass}"
            binding.tvSpecUsbDacCapabilities.text = usbDac.capabilitiesDescription
        } else {
            binding.containerSpecUsbDac.visibility = View.GONE
            val jackInfo = app?.audioEngine?.metricsTracker?.inspectHeadphoneJack()
            if (jackInfo?.jackType != null) {
                binding.tvSpecDacStatus.text = "3.5mm HiFi DAC"
                binding.tvSpecDacStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))
            } else if (metrics?.isBluetoothConnected == true) {
                binding.tvSpecDacStatus.text = "Bluetooth DSP"
                binding.tvSpecDacStatus.setTextColor(android.graphics.Color.parseColor("#64B5F6"))
            } else {
                binding.tvSpecDacStatus.text = "Internal Qualcomm"
                binding.tvSpecDacStatus.setTextColor(android.graphics.Color.parseColor("#A1A1AA"))
            }
        }

        binding.btnCloseSpecs.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
