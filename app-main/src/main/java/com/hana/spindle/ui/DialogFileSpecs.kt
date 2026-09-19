package com.hana.spindle.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.data.db.SongEntity
import com.hana.spindle.databinding.DialogFileSpecsBinding
import java.io.File
import java.util.Locale

class DialogFileSpecs(
    private val song: SongEntity
) : BottomSheetDialogFragment() {

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

        binding.btnCloseSpecs.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
