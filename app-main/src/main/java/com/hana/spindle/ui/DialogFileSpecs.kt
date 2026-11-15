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

        val isCueVirtual = com.hana.spindle.data.CueSheetParser.isCueVirtualPath(song.path)
        val physicalPath = com.hana.spindle.data.CueSheetParser.getAudioFilePath(song.path)
        val file = File(physicalPath)
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

        if (isCueVirtual) {
            binding.containerSpecCueInfo.visibility = View.VISIBLE
            val trackNum = com.hana.spindle.data.CueSheetParser.getCueTrackNumber(song.path)
            val formattedOffset = com.hana.spindle.data.CueSheetParser.getCueFormattedOffset(song.path)
            binding.tvSpecCueOffset.text = "Track #${String.format(Locale.US, "%02d", trackNum)} • Start Offset: $formattedOffset"
            binding.tvSpecCuePhysicalFile.text = "Physical Container: ${file.name}"
        } else {
            binding.containerSpecCueInfo.visibility = View.GONE
        }

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

        val isBitPerfect = metrics?.isBitPerfect ?: false
        binding.tvSpecBitPerfectBadge.text = when {
            metrics?.isBitPerfectVerified == true -> getString(com.hana.spindle.R.string.bitperfect_badge_verified)
            isBitPerfect -> "DIRECT · NO DSP"
            else -> "DSP / MIXER ACTIVE"
        }
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

        renderSignalPath(app, song, metrics)

        binding.btnCloseSpecs.setOnClickListener {
            dismiss()
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun renderSignalPath(
        app: com.hana.spindle.SpindleApp?,
        track: TrackEntity,
        metrics: com.hana.spindle.playback.AudioMetrics?
    ) {
        val engine = app?.audioEngine ?: return
        val fx = engine.audioFxController
        val peq = com.hana.spindle.playback.ParametricEq.settings.value
        val rgOn = engine.replayGainMode != com.hana.spindle.core.ReplayGainMode.OFF
        val stages = com.hana.spindle.playback.SignalPathBuilder.build(
            com.hana.spindle.playback.SignalPathInput(
                format = track.fileFormat,
                bitDepth = track.bitDepth,
                sampleRateHz = track.sampleRate,
                dopActive = engine.isDopActive,
                equalizerActive = !fx.isBypassEnabled,
                parametricBands = if (peq.isEffective) peq.bands.count { it.enabled } else 0,
                tapeSaturation = fx.isTapeSaturationEnabled,
                crossfeed = fx.crossfeedStrength > 0,
                replayGainDb = if (rgOn) engine.currentReplayGainDb + engine.replayGainPreampDb else null,
                mixer = engine.bitPerfectMixer.state.value,
                outputRoute = metrics?.outputRoute ?: "3.5mm Headphone Jack"
            )
        )
        val sb = StringBuilder()
        stages.forEachIndexed { i, stage ->
            if (i > 0) sb.append("\n")
            val label = getString(
                when (stage.kind) {
                    com.hana.spindle.playback.SignalStageKind.SOURCE -> com.hana.spindle.R.string.signal_stage_source
                    com.hana.spindle.playback.SignalStageKind.DECODE -> com.hana.spindle.R.string.signal_stage_decode
                    com.hana.spindle.playback.SignalStageKind.DSP -> com.hana.spindle.R.string.signal_stage_dsp
                    com.hana.spindle.playback.SignalStageKind.OUTPUT -> com.hana.spindle.R.string.signal_stage_output
                }
            )
            val lines = if (stage.lines.isEmpty()) listOf(getString(com.hana.spindle.R.string.signal_dsp_none)) else stage.lines
            sb.append(label).append("\n")
            lines.forEach { sb.append("  ").append(it).append("\n") }
        }
        binding.tvSpecSignalPath.text = sb.toString().trimEnd()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
