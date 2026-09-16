package com.hana.spindle.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.data.db.AlbumItem
import com.hana.spindle.data.db.SongEntity
import com.hana.spindle.databinding.FragmentCatalogBinding
import com.hana.spindle.playback.AudioEngine
import com.hana.spindle.ui.catalog.AlbumAdapter
import com.hana.spindle.ui.catalog.FolderAdapter
import com.hana.spindle.ui.catalog.FolderItem
import com.hana.spindle.ui.catalog.SongAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class CatalogFragment : Fragment() {

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!

    private lateinit var audioEngine: AudioEngine
    private lateinit var songAdapter: SongAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var folderAdapter: FolderAdapter

    private var currentTab = 0 // 0: Songs, 1: Albums, 2: Artists, 3: Folders, 4: Rated
    private var allSongsList: List<SongEntity> = emptyList()
    private var currentDisplayedSongs: List<SongEntity> = emptyList()
    private var allAlbumsList: List<AlbumItem> = emptyList()
    private var allFoldersList: List<FolderItem> = emptyList()

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
        setupQuickActions()
        setupSearch()
        setupMiniPlayer(app)
        observeScanProgress(app)
        loadSongs(app)
    }

    private fun setupAdapters(app: SpindleApp) {
        songAdapter = SongAdapter(
            imageLoader = app.imageLoader,
            onSongClicked = { song, index ->
                audioEngine.playQueue(currentDisplayedSongs, index)
                (activity as? MainActivity)?.navigateToPlayer()
            },
            onRatingChanged = { song, newRating ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.songDao().updateRating(song.id, newRating)
                }
            }
        )

        albumAdapter = AlbumAdapter(
            imageLoader = app.imageLoader,
            onAlbumClicked = { album ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.songDao().getSongsByAlbum(album.album).collectLatest { albumSongs ->
                        currentDisplayedSongs = albumSongs
                        audioEngine.playQueue(albumSongs, 0)
                        (activity as? MainActivity)?.navigateToPlayer()
                    }
                }
            },
            onPlayAlbumClicked = { album ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.songDao().getSongsByAlbum(album.album).collectLatest { albumSongs ->
                        currentDisplayedSongs = albumSongs
                        audioEngine.playQueue(albumSongs, 0)
                        (activity as? MainActivity)?.navigateToPlayer()
                    }
                }
            }
        )

        folderAdapter = FolderAdapter { folder ->
            viewLifecycleOwner.lifecycleScope.launch {
                val folderSongs = allSongsList.filter { File(it.path).parent == folder.path }
                if (folderSongs.isNotEmpty()) {
                    currentDisplayedSongs = folderSongs
                    audioEngine.playQueue(folderSongs, 0)
                    (activity as? MainActivity)?.navigateToPlayer()
                }
            }
        }

        binding.rvCatalog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCatalog.adapter = songAdapter
    }

    private fun setupTabs() {
        val app = requireActivity().application as SpindleApp

        binding.btnBackToPlayer.setOnClickListener {
            (activity as? MainActivity)?.navigateToPlayer()
        }

        binding.tabSongs.setOnClickListener {
            selectTab(0)
            binding.rvCatalog.layoutManager = LinearLayoutManager(requireContext())
            binding.rvCatalog.adapter = songAdapter
            loadSongs(app)
        }

        binding.tabAlbums.setOnClickListener {
            selectTab(1)
            binding.rvCatalog.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.rvCatalog.adapter = albumAdapter
            loadAlbums(app)
        }

        binding.tabArtists.setOnClickListener {
            selectTab(2)
            binding.rvCatalog.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.rvCatalog.adapter = albumAdapter
            loadArtists(app)
        }

        binding.tabFolders.setOnClickListener {
            selectTab(3)
            binding.rvCatalog.layoutManager = LinearLayoutManager(requireContext())
            binding.rvCatalog.adapter = folderAdapter
            loadFolders()
        }

        binding.tabFavorites.setOnClickListener {
            selectTab(4)
            binding.rvCatalog.layoutManager = LinearLayoutManager(requireContext())
            binding.rvCatalog.adapter = songAdapter
            loadFavorites(app)
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        val activeBg = ContextCompat.getColor(requireContext(), R.color.wm2_red)
        val inactiveBg = Color.parseColor("#212228")

        val tabs = listOf(
            binding.tabSongs,
            binding.tabAlbums,
            binding.tabArtists,
            binding.tabFolders,
            binding.tabFavorites
        )

        tabs.forEachIndexed { i, btn ->
            btn.backgroundTintList = ColorStateList.valueOf(if (i == index) activeBg else inactiveBg)
            btn.setTextColor(if (i == index) Color.WHITE else Color.parseColor("#94A3B8"))
        }
    }

    private fun setupQuickActions() {
        binding.btnShuffle.setOnClickListener {
            if (currentDisplayedSongs.isNotEmpty()) {
                val shuffled = currentDisplayedSongs.shuffled()
                audioEngine.playQueue(shuffled, 0)
                (activity as? MainActivity)?.navigateToPlayer()
            }
        }

        binding.btnPlayAll.setOnClickListener {
            if (currentDisplayedSongs.isNotEmpty()) {
                audioEngine.playQueue(currentDisplayedSongs, 0)
                (activity as? MainActivity)?.navigateToPlayer()
            }
        }
    }

    private fun setupSearch() {
        binding.etCatalogSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                when (currentTab) {
                    0, 4 -> {
                        val filtered = if (query.isEmpty()) {
                            allSongsList
                        } else {
                            allSongsList.filter {
                                it.title.lowercase(Locale.ROOT).contains(query) ||
                                it.artist.lowercase(Locale.ROOT).contains(query) ||
                                it.album.lowercase(Locale.ROOT).contains(query)
                            }
                        }
                        currentDisplayedSongs = filtered
                        songAdapter.submitList(filtered)
                        binding.tvCatalogCount.text = "${filtered.size} songs"
                    }
                    1, 2 -> {
                        val filtered = if (query.isEmpty()) {
                            allAlbumsList
                        } else {
                            allAlbumsList.filter {
                                it.album.lowercase(Locale.ROOT).contains(query) ||
                                it.artist.lowercase(Locale.ROOT).contains(query)
                            }
                        }
                        albumAdapter.submitList(filtered)
                        binding.tvCatalogCount.text = "${filtered.size} items"
                    }
                    3 -> {
                        val filtered = if (query.isEmpty()) {
                            allFoldersList
                        } else {
                            allFoldersList.filter { it.name.lowercase(Locale.ROOT).contains(query) }
                        }
                        folderAdapter.submitList(filtered)
                        binding.tvCatalogCount.text = "${filtered.size} folders"
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })
    }

    private fun setupMiniPlayer(app: SpindleApp) {
        binding.cardMiniPlayer.setOnClickListener {
            (activity as? MainActivity)?.navigateToPlayer()
        }

        binding.btnMiniPrev.setOnClickListener { audioEngine.playPrevious() }
        binding.btnMiniPlayPause.setOnClickListener { audioEngine.togglePlayPause() }
        binding.btnMiniNext.setOnClickListener { audioEngine.playNext() }

        viewLifecycleOwner.lifecycleScope.launch {
            audioEngine.playbackState.collectLatest { state ->
                _binding?.let { b ->
                    state.currentSong?.let { song ->
                        b.tvMiniTitle.text = song.title
                        b.tvMiniArtist.text = song.artist

                        // Update waveform in song adapter
                        songAdapter.activeSongId = song.id
                        songAdapter.notifyDataSetChanged()

                        // Load mini cover art
                        viewLifecycleOwner.lifecycleScope.launch {
                            val thumb = app.imageLoader.loadCover(song.path, 96, 96)
                            if (thumb != null) {
                                b.ivMiniArt.setPadding(0, 0, 0, 0)
                                b.ivMiniArt.setImageBitmap(thumb)
                            } else {
                                b.ivMiniArt.setPadding(8, 8, 8, 8)
                                b.ivMiniArt.setImageResource(android.R.drawable.ic_media_play)
                            }
                        }
                    }

                    b.btnMiniPlayPause.setImageResource(
                        if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    )
                }
            }
        }
    }

    private fun loadSongs(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getAllSongs().collectLatest { songs ->
                allSongsList = songs
                currentDisplayedSongs = songs
                songAdapter.submitList(songs)
                _binding?.tvCatalogCount?.text = "${songs.size} songs"
            }
        }
    }

    private fun loadAlbums(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getAlbums().collectLatest { albums ->
                allAlbumsList = albums
                albumAdapter.submitList(albums)
                _binding?.tvCatalogCount?.text = "${albums.size} albums"
            }
        }
    }

    private fun loadArtists(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getAlbums().collectLatest { albums ->
                // Group by artist
                val artists = albums.groupBy { it.artist }.map { (artist, list) ->
                    AlbumItem(
                        album = artist,
                        artist = "${list.size} albums",
                        trackCount = list.sumOf { it.trackCount },
                        representativePath = list.firstOrNull()?.representativePath ?: ""
                    )
                }
                allAlbumsList = artists
                albumAdapter.submitList(artists)
                _binding?.tvCatalogCount?.text = "${artists.size} artists"
            }
        }
    }

    private fun loadFolders() {
        val folders = allSongsList.groupBy { File(it.path).parent ?: "Music" }.map { (dir, songs) ->
            FolderItem(
                name = File(dir).name,
                path = dir,
                songCount = songs.size
            )
        }
        allFoldersList = folders
        folderAdapter.submitList(folders)
        _binding?.tvCatalogCount?.text = "${folders.size} folders"
    }

    private fun loadFavorites(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getRatedSongs().collectLatest { ratedSongs ->
                currentDisplayedSongs = ratedSongs
                songAdapter.submitList(ratedSongs)
                _binding?.tvCatalogCount?.text = "${ratedSongs.size} favorites"
            }
        }
    }

    private fun observeScanProgress(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.musicScanner.progress.collectLatest { prog ->
                val scanning = prog.isScanning
                _binding?.tvScanStatus?.text = if (scanning) "Scanning Library..." else "Ready"
                _binding?.tvScanStatus?.setTextColor(
                    if (scanning) Color.parseColor("#FFB300") else Color.parseColor("#00E676")
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
