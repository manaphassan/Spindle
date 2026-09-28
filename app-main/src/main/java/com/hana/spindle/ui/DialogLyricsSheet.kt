package com.hana.spindle.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.SpindleApp
import com.hana.spindle.data.LyricsData
import com.hana.spindle.databinding.DialogLyricsSheetBinding
import kotlinx.coroutines.launch

/**
 * Audiophile retro bottom sheet dialog displaying synchronized lyrics for the cassette player.
 * Features:
 * - Real-time auto-scrolling with active line glow
 * - Tap-to-seek to any lyric timestamp
 * - Real-time time sync offset adjustment (±0.5s)
 * - 1-tap download and refresh from LRCLIB
 */
class DialogLyricsSheet : BottomSheetDialogFragment() {

    private var _binding: DialogLyricsSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var lyricsAdapter: LyricsAdapter
    private var isDownloading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLyricsSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as SpindleApp
        val audioEngine = app.audioEngine

        // Setup RecyclerView & Adapter
        lyricsAdapter = LyricsAdapter { timeMs ->
            audioEngine.seekTo(timeMs)
        }
        val theme = app.themeManager.currentTheme.value
        lyricsAdapter.accentColor = theme.vfdGlowColor

        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvLyrics.layoutManager = layoutManager
        binding.rvLyrics.adapter = lyricsAdapter

        binding.btnSheetClose.setOnClickListener {
            dismiss()
        }

        // Offset Calibration Controls
        binding.btnOffsetMinus.setOnClickListener {
            val currentOffset = audioEngine.playbackState.value.currentLyrics?.offsetMs ?: 0L
            val newOffset = currentOffset - 500L
            audioEngine.setLyricsOffset(newOffset)
            updateOffsetDisplay(newOffset)
        }

        binding.btnOffsetPlus.setOnClickListener {
            val currentOffset = audioEngine.playbackState.value.currentLyrics?.offsetMs ?: 0L
            val newOffset = currentOffset + 500L
            audioEngine.setLyricsOffset(newOffset)
            updateOffsetDisplay(newOffset)
        }

        binding.btnOffsetReset.setOnClickListener {
            audioEngine.setLyricsOffset(0L)
            updateOffsetDisplay(0L)
        }

        // Manual Download & Reload from LRCLIB
        val fetchAction = View.OnClickListener {
            if (isDownloading) return@OnClickListener
            val track = audioEngine.playbackState.value.currentTrack ?: return@OnClickListener
            isDownloading = true
            binding.progressLoadingLyrics.visibility = View.VISIBLE
            binding.btnDownloadLyrics.isEnabled = false
            binding.btnReloadLyrics.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val downloaded = app.lyricsFetcher.fetchLyricsForTrack(track, forceRefresh = true)
                isDownloading = false
                binding.progressLoadingLyrics.visibility = View.GONE
                binding.btnDownloadLyrics.isEnabled = true
                binding.btnReloadLyrics.isEnabled = true

                if (downloaded != null && downloaded.lines.isNotEmpty()) {
                    audioEngine.reloadLyricsForCurrentTrack(forceDownload = false)
                    Toast.makeText(requireContext(), "Synchronized lyrics downloaded from LRCLIB!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "No lyrics found on LRCLIB for this track.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnDownloadLyrics.setOnClickListener(fetchAction)
        binding.btnReloadLyrics.setOnClickListener(fetchAction)

        // Observe playback state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                audioEngine.playbackState.collect { state ->
                    val track = state.currentTrack
                    if (track != null) {
                        binding.tvSheetTitle.text = track.title
                        binding.tvSheetArtist.text = track.artist
                    } else {
                        binding.tvSheetTitle.text = "No Track Playing"
                        binding.tvSheetArtist.text = ""
                    }

                    val lyrics = state.currentLyrics
                    if (lyrics != null && lyrics.lines.isNotEmpty()) {
                        binding.emptyContainer.visibility = View.GONE
                        binding.rvLyrics.visibility = View.VISIBLE
                        binding.layoutOffsetToolbar.visibility = if (lyrics.isSynced) View.VISIBLE else View.GONE

                        if (lyricsAdapter.lines !== lyrics.lines) {
                            lyricsAdapter.lines = lyrics.lines
                        }
                        lyricsAdapter.activeIndex = state.activeLyricIndex

                        if (lyrics.isSynced) {
                            binding.tvSheetLyricsBadge.text = "SYNCED LRC"
                            binding.tvSheetLyricsBadge.setTextColor(Color.parseColor("#00E676"))
                            binding.tvSheetLyricsBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#122B1E"))
                            updateOffsetDisplay(lyrics.offsetMs)
                        } else {
                            binding.tvSheetLyricsBadge.text = "PLAIN TEXT"
                            binding.tvSheetLyricsBadge.setTextColor(Color.parseColor("#F59E0B"))
                            binding.tvSheetLyricsBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#261D10"))
                        }

                        // Auto-scroll active line to vertical center
                        if (state.activeLyricIndex in lyrics.lines.indices && binding.rvLyrics.height > 0) {
                            layoutManager.scrollToPositionWithOffset(
                                state.activeLyricIndex,
                                binding.rvLyrics.height / 3
                            )
                        }
                    } else {
                        binding.emptyContainer.visibility = View.VISIBLE
                        binding.rvLyrics.visibility = View.GONE
                        binding.layoutOffsetToolbar.visibility = View.GONE
                        lyricsAdapter.lines = emptyList()

                        binding.tvSheetLyricsBadge.text = "NO LYRICS"
                        binding.tvSheetLyricsBadge.setTextColor(Color.parseColor("#EF4444"))
                        binding.tvSheetLyricsBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2B1214"))
                    }
                }
            }
        }
    }

    private fun updateOffsetDisplay(offsetMs: Long) {
        val sign = if (offsetMs > 0) "+" else if (offsetMs < 0) "-" else "±"
        val absSec = kotlin.math.abs(offsetMs) / 1000.0
        binding.tvOffsetValue.text = String.format("%s%.1fs", sign, absSec)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
