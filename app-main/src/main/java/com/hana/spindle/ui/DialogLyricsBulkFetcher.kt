package com.hana.spindle.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.data.LyricsFetcher
import com.hana.spindle.databinding.DialogLyricsBulkFetcherBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BottomSheet dialog for scanning library for missing synchronized lyrics and batch-downloading
 * from the free, open-source community LRCLIB database.
 */
class DialogLyricsBulkFetcher(
    private val onFinished: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: DialogLyricsBulkFetcherBinding? = null
    private val binding get() = _binding!!

    private var fetchJob: Job? = null
    private var isFetching: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLyricsBulkFetcherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as SpindleApp

        // 1. Initial quick background scan for missing lyrics count
        scanLibraryStatus(app)

        // 2. Start Download Button
        binding.btnStartFetch.setOnClickListener {
            if (isFetching) return@setOnClickListener
            startLyricsDownload(app)
        }

        // 3. Cancel / Dismiss Button
        binding.btnCancelOrDismiss.setOnClickListener {
            if (isFetching) {
                fetchJob?.cancel()
                isFetching = false
                binding.tvCurrentFetchStatus.text = "Download cancelled by user."
                binding.btnCancelOrDismiss.text = "DISMISS"
                binding.btnStartFetch.isEnabled = true
            } else {
                dismiss()
            }
        }
    }

    private fun scanLibraryStatus(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (total, missing) = withContext(Dispatchers.IO) {
                val allTracksCount = app.database.trackDao().getTrackCount()
                val missingList = app.database.trackDao().getTracksWithoutLyrics()
                Pair(allTracksCount, missingList.size)
            }

            binding.tvStatTotalScanned.text = total.toString()
            binding.tvStatMissingLyrics.text = missing.toString()
            binding.tvStatDownloaded.text = "0"

            if (missing == 0) {
                binding.btnStartFetch.isEnabled = false
                binding.btnStartFetch.text = "ALL TRACKS HAVE LYRICS"
                binding.btnStartFetch.setTextColor(android.graphics.Color.parseColor("#64748B"))
            } else {
                binding.btnStartFetch.isEnabled = true
                binding.btnStartFetch.text = "SCAN & DOWNLOAD ($missing MISSING)"
            }
        }
    }

    private fun startLyricsDownload(app: SpindleApp) {
        isFetching = true
        binding.btnStartFetch.isEnabled = false
        binding.btnCancelOrDismiss.text = getString(R.string.action_cancel)
        binding.layoutProgressSection.visibility = View.VISIBLE

        fetchJob = viewLifecycleOwner.lifecycleScope.launch {
            app.lyricsFetcher.scanAndFetchMissingLyrics(saveToSidecar = true) { status ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    when (status) {
                        is LyricsFetcher.FetchStatus.Idle -> {}
                        is LyricsFetcher.FetchStatus.Scanning -> {
                            binding.tvCurrentFetchStatus.text = "Querying LRCLIB catalog..."
                            binding.progressFetchLyrics.isIndeterminate = true
                        }
                        is LyricsFetcher.FetchStatus.InProgress -> {
                            binding.progressFetchLyrics.isIndeterminate = false
                            val pct = if (status.total > 0) (status.current * 100) / status.total else 0
                            binding.progressFetchLyrics.progress = pct
                            binding.tvCurrentProgressFraction.text = "${status.current} / ${status.total}"
                            binding.tvCurrentFetchItem.text = "${status.currentTitle} • ${status.currentArtist}"
                            binding.tvCurrentFetchStatus.text = "Fetching lyrics from LRCLIB..."
                            binding.tvStatDownloaded.text = status.downloadedCount.toString()
                        }
                        is LyricsFetcher.FetchStatus.Finished -> {
                            isFetching = false
                            binding.progressFetchLyrics.progress = 100
                            binding.tvCurrentProgressFraction.text = "${status.missingFound} / ${status.missingFound}"
                            binding.tvCurrentFetchStatus.text = "Done! Downloaded ${status.downloadedSuccess} synced lyrics."
                            binding.tvStatDownloaded.text = status.downloadedSuccess.toString()
                            binding.btnCancelOrDismiss.text = "DONE"
                            binding.btnStartFetch.isEnabled = false
                            binding.btnStartFetch.text = "DOWNLOAD COMPLETE"
                            onFinished?.invoke()
                        }
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        fetchJob?.cancel()
        onFinished?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
