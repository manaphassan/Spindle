package com.hana.spindle.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.SpindleApp
import com.hana.spindle.data.CoverArtFetcher
import com.hana.spindle.databinding.DialogCoverArtFetcherBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BottomSheet dialog for scanning library for missing album artwork and batch-downloading
 * from online high-resolution metadata providers (Apple iTunes Search API and Deezer API).
 */
class DialogCoverArtFetcher(
    private val onFinished: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: DialogCoverArtFetcherBinding? = null
    private val binding get() = _binding!!

    private var fetchJob: Job? = null
    private var isFetching: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCoverArtFetcherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as SpindleApp

        // 1. Initial quick background scan for missing covers
        scanLibraryStatus(app)

        // 2. Start Download Button
        binding.btnStartFetch.setOnClickListener {
            if (isFetching) return@setOnClickListener
            startCoverDownload(app)
        }

        // 3. Cancel / Dismiss Button
        binding.btnCancelOrDismiss.setOnClickListener {
            if (isFetching) {
                fetchJob?.cancel()
                isFetching = false
                binding.tvCurrentFetchStatus.text = "Download cancelled by user."
                binding.btnStartFetch.isEnabled = true
                binding.btnStartFetch.text = "RESUME DOWNLOAD"
                binding.btnCancelOrDismiss.text = "CLOSE"
            } else {
                dismiss()
            }
        }
    }

    private fun scanLibraryStatus(app: SpindleApp) {
        lifecycleScope.launch {
            binding.tvMissingCoversCount.text = "Scanning..."
            val albums = withContext(Dispatchers.IO) {
                app.database.trackDao().getAlbums().firstOrNull() ?: emptyList()
            }

            binding.tvTotalAlbumsCount.text = "${albums.size} albums"

            val missingCount = withContext(Dispatchers.IO) {
                albums.count { album ->
                    !app.imageLoader.hasCover(album.representativePath, album.album, album.artist)
                }
            }

            _binding?.let { b ->
                b.tvMissingCoversCount.text = "$missingCount missing"
                if (missingCount == 0) {
                    b.btnStartFetch.isEnabled = false
                    b.btnStartFetch.text = "ALL COVERS PRESENT (100%)"
                } else {
                    b.btnStartFetch.isEnabled = true
                    b.btnStartFetch.text = "START DOWNLOAD ($missingCount MISSING)"
                }
            }
        }
    }

    private fun startCoverDownload(app: SpindleApp) {
        isFetching = true
        binding.layoutProgressSection.visibility = View.VISIBLE
        binding.btnStartFetch.isEnabled = false
        binding.btnCancelOrDismiss.text = "CANCEL"
        binding.progressBarFetch.isIndeterminate = true
        binding.tvCurrentFetchStatus.text = "Querying music library index..."

        fetchJob = lifecycleScope.launch {
            val result = app.coverArtFetcher.scanAndFetchMissingCovers { status ->
                lifecycleScope.launch(Dispatchers.Main) {
                    _binding?.let { b ->
                        when (status) {
                            is CoverArtFetcher.FetchStatus.Idle -> {
                                b.progressBarFetch.isIndeterminate = false
                            }
                            is CoverArtFetcher.FetchStatus.Scanning -> {
                                b.progressBarFetch.isIndeterminate = true
                                b.tvCurrentFetchStatus.text = "Inspecting ${status.totalAlbums} albums..."
                            }
                            is CoverArtFetcher.FetchStatus.InProgress -> {
                                b.progressBarFetch.isIndeterminate = false
                                b.progressBarFetch.max = status.total
                                b.progressBarFetch.progress = status.current
                                b.tvCurrentFetchStatus.text = "Downloading (${status.current}/${status.total}): ${status.currentAlbum} — ${status.currentArtist}"
                                b.tvMissingCoversCount.text = "${status.total - status.downloadedCount} remaining"
                            }
                            is CoverArtFetcher.FetchStatus.Finished -> {
                                b.progressBarFetch.isIndeterminate = false
                                b.progressBarFetch.progress = b.progressBarFetch.max
                                b.tvCurrentFetchStatus.text = "Done! Downloaded: ${status.downloadedSuccess} new covers, ${status.failedCount} not found."
                                b.tvMissingCoversCount.text = "${status.missingFound - status.downloadedSuccess} missing"
                                b.btnStartFetch.isEnabled = true
                                b.btnStartFetch.text = "RE-SCAN LIBRARY"
                                b.btnCancelOrDismiss.text = "CLOSE"
                                isFetching = false
                                onFinished?.invoke()
                            }
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
