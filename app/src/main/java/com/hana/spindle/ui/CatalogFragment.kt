package com.hana.spindle.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.data.db.SongEntity
import com.hana.spindle.databinding.FragmentCatalogBinding
import com.hana.spindle.playback.AudioEngine
import com.hana.spindle.ui.catalog.AlbumAdapter
import com.hana.spindle.ui.catalog.SongAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CatalogFragment : Fragment() {

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!

    private lateinit var audioEngine: AudioEngine
    private lateinit var songAdapter: SongAdapter
    private lateinit var albumAdapter: AlbumAdapter

    private var currentTab = 0 // 0 = Songs, 1 = Albums, 2 = Favorites
    private var allSongsList: List<SongEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as SpindleApp
        audioEngine = app.audioEngine

        setupAdapters(app)
        setupTabs()
        observeScanProgress(app)
        loadSongs(app)
    }

    private fun setupAdapters(app: SpindleApp) {
        songAdapter = SongAdapter(
            onSongClicked = { song, index ->
                audioEngine.playQueue(allSongsList, index)
                (activity as? MainActivity)?.navigateToPlayer()
            },
            onRatingChanged = { song, newRating ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.songDao().updateRating(song.id, newRating)
                }
            }
        )

        albumAdapter = AlbumAdapter(app.imageLoader) { album ->
            viewLifecycleOwner.lifecycleScope.launch {
                app.database.songDao().getSongsByAlbum(album.album).collectLatest { albumSongs ->
                    allSongsList = albumSongs
                    audioEngine.playQueue(albumSongs, 0)
                    (activity as? MainActivity)?.navigateToPlayer()
                }
            }
        }

        binding.rvCatalog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCatalog.adapter = songAdapter
    }

    private fun setupTabs() {
        val app = requireActivity().application as SpindleApp

        binding.tabSongs.setOnClickListener {
            selectTab(0)
            binding.rvCatalog.adapter = songAdapter
            loadSongs(app)
        }

        binding.tabAlbums.setOnClickListener {
            selectTab(1)
            binding.rvCatalog.adapter = albumAdapter
            loadAlbums(app)
        }

        binding.tabFavorites.setOnClickListener {
            selectTab(2)
            binding.rvCatalog.adapter = songAdapter
            loadFavorites(app)
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        val activeColor = ContextCompat.getColor(requireContext(), R.color.wm2_red)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.surface_elevated)
        val activeText = ContextCompat.getColor(requireContext(), R.color.white)
        val inactiveText = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.tabSongs.backgroundTintList = ColorStateList.valueOf(if (index == 0) activeColor else inactiveColor)
        binding.tabSongs.setTextColor(if (index == 0) activeText else inactiveText)

        binding.tabAlbums.backgroundTintList = ColorStateList.valueOf(if (index == 1) activeColor else inactiveColor)
        binding.tabAlbums.setTextColor(if (index == 1) activeText else inactiveText)

        binding.tabFavorites.backgroundTintList = ColorStateList.valueOf(if (index == 2) activeColor else inactiveColor)
        binding.tabFavorites.setTextColor(if (index == 2) activeText else inactiveText)
    }

    private fun loadSongs(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getAllSongs().collectLatest { songs ->
                if (currentTab == 0) {
                    allSongsList = songs
                    songAdapter.submitList(songs)
                }
                _binding?.let { b ->
                    if (!app.musicScanner.progress.value.isScanning) {
                        b.tvScanStatus.text = if (songs.isNotEmpty()) "Library: ${songs.size} songs" else "Tap to Scan"
                    }
                }
            }
        }
    }

    private fun loadAlbums(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getAlbums().collectLatest { albums ->
                if (currentTab == 1) {
                    albumAdapter.submitList(albums)
                }
            }
        }
    }

    private fun loadFavorites(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getRatedSongs().collectLatest { ratedSongs ->
                if (currentTab == 2) {
                    allSongsList = ratedSongs
                    songAdapter.submitList(ratedSongs)
                }
            }
        }
    }

    private fun observeScanProgress(app: SpindleApp) {
        binding.tvScanStatus.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                app.musicScanner.scanAll()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            app.musicScanner.progress.collectLatest { progress ->
                _binding?.let { b ->
                    b.tvScanStatus.text = if (progress.isScanning) {
                        "Indexing: ${progress.songsFound} songs..."
                    } else if (progress.songsFound > 0) {
                        "Library: ${progress.songsFound} songs"
                    } else {
                        "Tap to Scan"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
