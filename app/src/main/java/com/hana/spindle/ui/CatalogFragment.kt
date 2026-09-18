package com.hana.spindle.ui

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
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.R
import com.hana.spindle.SpindleApp
import com.hana.spindle.data.db.AlbumItem
import com.hana.spindle.data.db.SongEntity
import com.hana.spindle.databinding.FragmentCatalogBinding
import com.hana.spindle.playback.AudioEngine
import com.hana.spindle.playback.RepeatMode
import com.hana.spindle.playback.ShuffleMode
import com.hana.spindle.theme.CassetteTheme
import com.hana.spindle.ui.catalog.*
import kotlinx.coroutines.Job
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
    private lateinit var npLyricsAdapter: LyricsAdapter
    private lateinit var albumTracksAdapter: SongAdapter
    private lateinit var waveformExtractor: com.hana.spindle.data.WaveformExtractor
    private lateinit var searchSuggestionAdapter: SearchSuggestionAdapter
    private var waveformExtractionJob: Job? = null

    private var currentAlbumSongs: List<SongEntity> = emptyList()
    private var currentAlbumItem: AlbumItem? = null
    private var albumTracksJob: Job? = null

    private var isNowPlayingSingleVisible = false
    private var isShowingNpLyrics = false
    private var currentLoadedSongId: Long = -1L
    private var currentMiniSongId: Long = -1L

    // 0: Tracks, 1: Albums, 2: Artists, 3: Folders, 4: Favorites
    private var currentTab = 0

    private var allSongsList: List<SongEntity> = emptyList()
    private var currentDisplayedSongs: List<SongEntity> = emptyList()

    private var allAlbumsList: List<AlbumItem> = emptyList()
    private var currentDisplayedAlbums: List<AlbumItem> = emptyList()

    private var allFoldersList: List<FolderItem> = emptyList()
    private var currentFilterChip = CatalogFilterChip.ALL

    // Poweramp-style Sorting & Grouping
    private var trackSortOrder = TrackSortOrder.TITLE_ASC
    private var albumSortOrder = AlbumSortOrder.TITLE_ASC
    private var groupByMode = GroupByMode.NONE

    companion object {
        private const val ARG_OPEN_NOW_PLAYING = "arg_open_now_playing"

        fun newInstance(openNowPlaying: Boolean = false): CatalogFragment {
            return CatalogFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_OPEN_NOW_PLAYING, openNowPlaying)
                }
            }
        }
    }

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
        waveformExtractor = com.hana.spindle.data.WaveformExtractor(requireContext())

        setupAdapters(app)
        setupTabs()
        observeTheme(app)
        setupSortAndGroup()
        setupAlphabetIndex()
        setupQuickActions()
        setupSearch()
        setupMiniPlayer(app)
        setupNowPlayingSingleAudio(app)
        observeAudioMetrics(app)
        observeScanProgress(app)
        loadSongs(app)

        if (arguments?.getBoolean(ARG_OPEN_NOW_PLAYING) == true) {
            showNowPlayingSingleAudio(true)
        }
    }

    private fun setupAdapters(app: SpindleApp) {
        songAdapter = SongAdapter(
            imageLoader = app.imageLoader,
            onSongClicked = { song, index ->
                audioEngine.playQueue(currentDisplayedSongs, index)
                showNowPlayingSingleAudio(true)
            },
            onRatingChanged = { song, newRating ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.songDao().updateRating(song.id, newRating)
                }
            }
        ).apply {
            onPlayNext = { song ->
                audioEngine.playNextInQueue(song)
                android.widget.Toast.makeText(requireContext(), "Will play next: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
            }
            onAddToQueue = { song ->
                audioEngine.addToQueue(song)
                android.widget.Toast.makeText(requireContext(), "Added to queue: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
            }
            onAddToMixtape = { song ->
                MixtapeDialogs.showAddToMixtapeDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope, song) {
                    if (currentTab == 7) {
                        loadMixtapes(app)
                    }
                }
            }
            onInspectTags = { song ->
                TagInspectorDialog.show(requireContext(), song) {
                    loadSongs(app)
                }
            }
        }

        albumTracksAdapter = SongAdapter(
            imageLoader = app.imageLoader,
            onSongClicked = { song, index ->
                currentDisplayedSongs = currentAlbumSongs
                audioEngine.playQueue(currentAlbumSongs, index)
                showNowPlayingSingleAudio(true)
            },
            onRatingChanged = { song, newRating ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.songDao().updateRating(song.id, newRating)
                }
            }
        ).apply {
            showTrackNumbers = true
            onPlayNext = { song ->
                audioEngine.playNextInQueue(song)
                android.widget.Toast.makeText(requireContext(), "Will play next: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
            }
            onAddToQueue = { song ->
                audioEngine.addToQueue(song)
                android.widget.Toast.makeText(requireContext(), "Added to queue: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
            }
            onAddToMixtape = { song ->
                MixtapeDialogs.showAddToMixtapeDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope, song) {
                    if (currentTab == 7) {
                        loadMixtapes(app)
                    }
                }
            }
            onInspectTags = { song ->
                TagInspectorDialog.show(requireContext(), song) {
                    loadSongs(app)
                }
            }
        }

        binding.rvAlbumTracks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAlbumTracks.adapter = albumTracksAdapter

        albumAdapter = AlbumAdapter(
            imageLoader = app.imageLoader,
            onAlbumClicked = { album ->
                albumTracksJob?.cancel()
                albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                    val songsFlow = when (album.format) {
                        "DISCOGRAPHY" -> app.database.songDao().getSongsByArtist(album.album)
                        "GENRE" -> app.database.songDao().getSongsByGenre(album.album)
                        "MIXTAPE" -> app.database.playlistDao().getSongsForPlaylist(album.year.toLong())
                        else -> app.database.songDao().getSongsByAlbum(album.album)
                    }
                    songsFlow.collectLatest { albumSongs ->
                        showAlbumDetail(album, albumSongs)
                    }
                }
            },
            onPlayAlbumClicked = { album ->
                albumTracksJob?.cancel()
                albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                    val songsFlow = when (album.format) {
                        "DISCOGRAPHY" -> app.database.songDao().getSongsByArtist(album.album)
                        "GENRE" -> app.database.songDao().getSongsByGenre(album.album)
                        "MIXTAPE" -> app.database.playlistDao().getSongsForPlaylist(album.year.toLong())
                        else -> app.database.songDao().getSongsByAlbum(album.album)
                    }
                    songsFlow.collectLatest { albumSongs ->
                        if (albumSongs.isNotEmpty()) {
                            currentDisplayedSongs = albumSongs
                            audioEngine.playQueue(albumSongs, 0)
                            showNowPlayingSingleAudio(true)
                        }
                    }
                }
            }
        ).apply {
            onAlbumLongClicked = { album ->
                if (album.format == "MIXTAPE") {
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Delete Mixtape")
                        .setMessage("Are you sure you want to delete '${album.album}'? (Music files will not be deleted)")
                        .setPositiveButton("Delete") { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                app.database.playlistDao().deletePlaylist(album.year.toLong())
                                android.widget.Toast.makeText(requireContext(), "Deleted mixtape '${album.album}'", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        folderAdapter = FolderAdapter { folder ->
            viewLifecycleOwner.lifecycleScope.launch {
                val folderSongs = allSongsList.filter { File(it.path).parent == folder.path }
                if (folderSongs.isNotEmpty()) {
                    currentDisplayedSongs = folderSongs
                    audioEngine.playQueue(folderSongs, 0)
                    showNowPlayingSingleAudio(true)
                }
            }
        }

        binding.rvCatalog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCatalog.adapter = songAdapter

        searchSuggestionAdapter = SearchSuggestionAdapter { suggestion ->
            binding.rvSearchSuggestions.visibility = View.GONE
            binding.etCatalogSearch.clearFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(binding.etCatalogSearch.windowToken, 0)

            if (suggestion.song != null) {
                currentDisplayedSongs = listOf(suggestion.song)
                audioEngine.playQueue(listOf(suggestion.song), 0)
                showNowPlayingSingleAudio(true)
            } else if (suggestion.album != null) {
                val album = suggestion.album
                albumTracksJob?.cancel()
                albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                    val songsFlow = when (album.format) {
                        "DISCOGRAPHY" -> app.database.songDao().getSongsByArtist(album.album)
                        "GENRE" -> app.database.songDao().getSongsByGenre(album.album)
                        "MIXTAPE" -> app.database.playlistDao().getSongsForPlaylist(album.year.toLong())
                        else -> app.database.songDao().getSongsByAlbum(album.album)
                    }
                    songsFlow.collectLatest { albumSongs ->
                        showAlbumDetail(album, albumSongs)
                    }
                }
            } else {
                binding.etCatalogSearch.setText(suggestion.title)
                binding.etCatalogSearch.setSelection(suggestion.title.length)
            }
        }
        binding.rvSearchSuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchSuggestions.adapter = searchSuggestionAdapter

        npLyricsAdapter = LyricsAdapter { timeMs ->
            audioEngine.seekTo(timeMs)
        }
        binding.rvNpLyrics.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNpLyrics.adapter = npLyricsAdapter
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

        setupCatalogSwipe()
    }

    private fun setupCatalogSwipe() {
        val gestureDetector = android.view.GestureDetector(requireContext(), object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: android.view.MotionEvent?,
                e2: android.view.MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) && kotlin.math.abs(diffX) > 120 && kotlin.math.abs(velocityX) > 200) {
                    if (diffX < 0) {
                        if (currentTab < 4) switchToTab(currentTab + 1)
                    } else {
                        if (currentTab > 0) switchToTab(currentTab - 1)
                    }
                    return true
                }
                return false
            }
        })
        binding.rvCatalog.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }
        })
    }

    private fun switchToTab(index: Int) {
        when (index) {
            0 -> binding.tabSongs.performClick()
            1 -> binding.tabAlbums.performClick()
            2 -> binding.tabArtists.performClick()
            3 -> binding.tabFolders.performClick()
            4 -> binding.tabFavorites.performClick()
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        hideAlbumDetail()
        val app = requireActivity().application as SpindleApp
        val theme = app.themeManager.currentTheme.value
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)
        val isDark = theme.isDarkAppTheme
        val activeBg = if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#F97316") else ContextCompat.getColor(requireContext(), R.color.wm2_red)
        val inactiveBg = if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#212228")
        val activeText = Color.WHITE
        val inactiveText = if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#2A2E45") else Color.parseColor("#94A3B8")

        val tabs = listOf(
            binding.tabSongs,
            binding.tabAlbums,
            binding.tabArtists,
            binding.tabFolders,
            binding.tabFavorites
        )

        tabs.forEachIndexed { i, btn ->
            btn.backgroundTintList = ColorStateList.valueOf(if (i == index) activeBg else inactiveBg)
            btn.setTextColor(if (i == index) activeText else inactiveText)
        }

        binding.btnNewMixtape.visibility = View.GONE
        binding.btnShuffle.visibility = View.VISIBLE
        binding.btnPlayAll.visibility = View.VISIBLE

        updateSortLabel()
    }

    private fun observeTheme(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.themeManager.currentTheme.collect { theme ->
                    applyTheme(theme)
                }
            }
        }
    }

    private fun applyTheme(theme: CassetteTheme) {
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)
        val isDark = theme.isDarkAppTheme
        val primary = theme.textPrimaryColor
        val secondary = theme.textSecondaryColor
        val accent = theme.accentColor

        binding.root.setBackgroundColor(theme.chassisColor)
        binding.catalogHeaderContainer.setBackgroundColor(if (isEink) Color.WHITE else theme.surfaceColor)
        binding.tvCatalogHeaderTitle.setTextColor(primary)
        binding.btnBackToPlayer.imageTintList = ColorStateList.valueOf(primary)
        binding.tvScanStatus.setTextColor(if (isEink) Color.BLACK else ContextCompat.getColor(requireContext(), R.color.vfd_emerald))

        binding.searchContainer.backgroundTintList = ColorStateList.valueOf(
            if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#202334")
        )
        binding.ivSearchIcon.imageTintList = ColorStateList.valueOf(secondary)
        binding.etCatalogSearch.setTextColor(primary)
        binding.etCatalogSearch.setHintTextColor(secondary)
        binding.btnSearchClear.imageTintList = ColorStateList.valueOf(secondary)

        binding.tvCatalogCount.setTextColor(secondary)
        binding.btnSortGroup.backgroundTintList = ColorStateList.valueOf(
            if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#202334")
        )
        binding.tvCurrentSortLabel.setTextColor(primary)
        binding.btnShuffle.backgroundTintList = ColorStateList.valueOf(
            if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#202334")
        )
        binding.btnShuffle.setTextColor(primary)
        binding.btnPlayAll.backgroundTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else accent)
        binding.btnPlayAll.setTextColor(Color.WHITE)

        // Mini player
        binding.cardMiniPlayer.setCardBackgroundColor(
            if (isEink) Color.WHITE else if (!isDark) Color.WHITE else Color.parseColor("#1E2132")
        )
        binding.tvMiniTitle.setTextColor(primary)
        binding.tvMiniArtist.setTextColor(secondary)
        binding.btnMiniPrev.imageTintList = ColorStateList.valueOf(primary)
        binding.btnMiniNext.imageTintList = ColorStateList.valueOf(primary)
        binding.btnMiniPlayPause.backgroundTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else accent)
        binding.btnMiniPlayPause.imageTintList = ColorStateList.valueOf(Color.WHITE)

        // Album Detail View
        binding.albumDetailContainer.setBackgroundColor(
            if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#FAFAF9") else Color.parseColor("#151724")
        )
        binding.btnAlbumDetailBack.imageTintList = ColorStateList.valueOf(primary)
        binding.tvAlbumDetailHeaderTitle.setTextColor(primary)
        binding.tvAlbumDetailFormat.setTextColor(if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#2A2E45") else Color.parseColor("#00E676"))
        binding.tvAlbumDetailFormat.backgroundTintList = ColorStateList.valueOf(if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#202334"))
        binding.cardAlbumDetailCover.setCardBackgroundColor(if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#202334"))
        binding.btnAlbumDetailCoverPlay.backgroundTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else accent)
        binding.btnAlbumDetailCoverPlay.imageTintList = ColorStateList.valueOf(Color.WHITE)
        binding.tvAlbumDetailTitle.setTextColor(primary)
        binding.tvAlbumDetailArtist.setTextColor(secondary)
        binding.tvAlbumDetailMeta.setTextColor(secondary)
        binding.btnAlbumDetailPlayAll.backgroundTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else accent)
        binding.btnAlbumDetailPlayAll.setTextColor(Color.WHITE)
        binding.btnAlbumDetailShuffle.backgroundTintList = ColorStateList.valueOf(if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#202334"))
        binding.btnAlbumDetailShuffle.setTextColor(primary)
        binding.vAlbumDetailDivider.setBackgroundColor(if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#24252B"))

        // Single Audio Now Playing
        binding.nowPlayingSingleContainer.setBackgroundColor(
            if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#FAFAF9") else Color.parseColor("#2A2E45")
        )
        binding.btnNpBack.imageTintList = ColorStateList.valueOf(primary)
        binding.btnNpFavorite.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#FB7185"))
        binding.btnNpMenu.imageTintList = ColorStateList.valueOf(primary)
        binding.tvNpTitle.setTextColor(primary)
        binding.tvNpArtist.setTextColor(secondary)
        binding.tvNpCurrentTime.setTextColor(primary)
        binding.tvNpTotalDuration.setTextColor(secondary)
        binding.btnNpPrev.imageTintList = ColorStateList.valueOf(primary)
        binding.btnNpNext.imageTintList = ColorStateList.valueOf(primary)
        binding.btnNpPlayPause.backgroundTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else accent)
        binding.btnNpPlayPause.imageTintList = ColorStateList.valueOf(Color.WHITE)
        binding.btnNpLyrics.imageTintList = ColorStateList.valueOf(secondary)
        binding.btnNpRepeat.imageTintList = ColorStateList.valueOf(secondary)
        binding.btnNpShuffle.imageTintList = ColorStateList.valueOf(secondary)
        binding.btnNpQueue.imageTintList = ColorStateList.valueOf(secondary)

        binding.circularCoverArcView.isDarkMode = isDark
        binding.circularCoverArcView.isEink = isEink
        binding.circularCoverArcView.accentColor = accent

        binding.audioWaveformView.isDarkMode = isDark
        binding.audioWaveformView.isEink = isEink
        binding.audioWaveformView.accentColor = accent

        binding.catalogAlphabetIndex.updateTheme(secondary, accent)

        // Propagate to Adapters
        songAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        albumAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        if (::albumTracksAdapter.isInitialized) {
            albumTracksAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        }
        folderAdapter.updateThemeColors(primary, secondary)
        if (::searchSuggestionAdapter.isInitialized) {
            searchSuggestionAdapter.updateThemeColors(primary, secondary, accent, isEink)
        }

        // Update active tab buttons visual
        selectTab(currentTab)
    }

    private fun setupSortAndGroup() {
        binding.btnSortGroup.setOnClickListener {
            val isAlbum = (currentTab == 1 || currentTab == 2)
            val dialog = SortGroupBottomSheet(
                isAlbumTab = isAlbum,
                currentTrackSort = trackSortOrder,
                currentAlbumSort = albumSortOrder,
                currentGroupBy = groupByMode,
                onTrackSortSelected = { newSort ->
                    trackSortOrder = newSort
                    updateSortLabel()
                    applyFilterAndSort()
                },
                onAlbumSortSelected = { newSort ->
                    albumSortOrder = newSort
                    updateSortLabel()
                    applyFilterAndSort()
                },
                onGroupBySelected = { newGroup ->
                    groupByMode = newGroup
                    applyFilterAndSort()
                }
            )
            dialog.show(parentFragmentManager, "SortGroupDialog")
        }
        updateSortLabel()
    }

    private fun updateSortLabel() {
        val isAlbum = (currentTab == 1 || currentTab == 2)
        val sortText = if (isAlbum) albumSortOrder.displayName else trackSortOrder.displayName
        binding.tvCurrentSortLabel.text = "Sort: $sortText"
    }

    private fun setupAlphabetIndex() {
        binding.catalogAlphabetIndex.onLetterSelected = { letter ->
            binding.tvCatalogLetterPreview.text = letter
            binding.tvCatalogLetterPreview.visibility = View.VISIBLE
            binding.tvCatalogLetterPreview.removeCallbacks(hideLetterPreviewRunnable)
            binding.tvCatalogLetterPreview.postDelayed(hideLetterPreviewRunnable, 800L)

            scrollToLetter(letter)
        }
    }

    private val hideLetterPreviewRunnable = Runnable {
        _binding?.tvCatalogLetterPreview?.visibility = View.GONE
    }

    private fun scrollToLetter(letter: String) {
        val targetChar = letter.firstOrNull()?.uppercaseChar() ?: return
        val lm = binding.rvCatalog.layoutManager ?: return

        when (currentTab) {
            0, 4 -> {
                val index = currentDisplayedSongs.indexOfFirst {
                    val firstChar = it.title.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (targetChar == '#') !firstChar.isLetter() else firstChar == targetChar
                }
                if (index >= 0) {
                    (lm as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
                }
            }
            1, 2 -> {
                val index = currentDisplayedAlbums.indexOfFirst {
                    val firstChar = it.album.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (targetChar == '#') !firstChar.isLetter() else firstChar == targetChar
                }
                if (index >= 0) {
                    (lm as? GridLayoutManager)?.scrollToPositionWithOffset(index, 0)
                }
            }
        }
    }

    private fun setupQuickActions() {
        val app = requireActivity().application as SpindleApp
        binding.btnNewMixtape.setOnClickListener {
            MixtapeDialogs.showCreateMixtapeDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope) {
                loadMixtapes(app)
            }
        }

        binding.btnShuffle.setOnClickListener {
            if (currentDisplayedSongs.isNotEmpty()) {
                audioEngine.setShuffleMode(com.hana.spindle.playback.ShuffleMode.ALL)
                audioEngine.playQueue(currentDisplayedSongs, 0)
                (activity as? MainActivity)?.navigateToPlayer()
            }
        }

        binding.btnPlayAll.setOnClickListener {
            if (currentDisplayedSongs.isNotEmpty()) {
                audioEngine.setShuffleMode(com.hana.spindle.playback.ShuffleMode.OFF)
                audioEngine.playQueue(currentDisplayedSongs, 0)
                (activity as? MainActivity)?.navigateToPlayer()
            }
        }
    }

    private fun setupSearch() {
        binding.btnSearchClear.setOnClickListener {
            binding.etCatalogSearch.text?.clear()
            binding.rvSearchSuggestions.visibility = View.GONE
            binding.btnSearchClear.visibility = View.GONE
        }

        binding.etCatalogSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    binding.btnSearchClear.visibility = View.GONE
                    binding.rvSearchSuggestions.visibility = View.GONE
                } else {
                    binding.btnSearchClear.visibility = View.VISIBLE
                    updateSearchSuggestions(query)
                }
                applyFilterAndSort()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })
    }

    private fun updateSearchSuggestions(query: String) {
        val q = query.lowercase(Locale.ROOT)
        val suggestions = mutableListOf<SearchSuggestion>()

        // 1. Matches in Tracks (up to 4)
        val matchingSongs = allSongsList.filter {
            it.title.lowercase(Locale.ROOT).contains(q) || it.artist.lowercase(Locale.ROOT).contains(q)
        }.take(4)
        for (song in matchingSongs) {
            suggestions.add(
                SearchSuggestion(
                    title = song.title,
                    subtitle = "${song.artist} • ${song.fileFormat}",
                    type = SuggestionType.TRACK,
                    song = song
                )
            )
        }

        // 2. Matches in Albums (up to 3)
        val matchingAlbums = allAlbumsList.filter {
            it.album.lowercase(Locale.ROOT).contains(q)
        }.take(3)
        for (album in matchingAlbums) {
            suggestions.add(
                SearchSuggestion(
                    title = album.album,
                    subtitle = "${album.artist} • ${album.trackCount} tracks",
                    type = SuggestionType.ALBUM,
                    album = album
                )
            )
        }

        // 3. Matches in Artists (up to 2)
        val matchingArtists = allSongsList.map { it.artist }.distinct().filter {
            it.lowercase(Locale.ROOT).contains(q)
        }.take(2)
        for (artist in matchingArtists) {
            suggestions.add(
                SearchSuggestion(
                    title = artist,
                    subtitle = "Artist",
                    type = SuggestionType.ARTIST
                )
            )
        }

        if (suggestions.isNotEmpty()) {
            searchSuggestionAdapter.submitList(suggestions)
            binding.rvSearchSuggestions.visibility = View.VISIBLE
        } else {
            binding.rvSearchSuggestions.visibility = View.GONE
        }
    }

    private fun applyFilterAndSort() {
        val query = binding.etCatalogSearch.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""

        when (currentTab) {
            0, 4 -> {
                var list = allSongsList

                // 1. Filter Chips
                list = when (currentFilterChip) {
                    CatalogFilterChip.ALL -> list
                    CatalogFilterChip.HI_RES -> list.filter { it.bitDepth >= 24 || it.sampleRate > 48000 || it.fileFormat in setOf("FLAC", "WAV", "DSD", "DSF") }
                    CatalogFilterChip.LOSSLESS -> list.filter { it.fileFormat in setOf("FLAC", "WAV", "ALAC", "AIFF", "DSD", "DSF") }
                    CatalogFilterChip.FLAC -> list.filter { it.fileFormat == "FLAC" }
                    CatalogFilterChip.WAV -> list.filter { it.fileFormat == "WAV" }
                    CatalogFilterChip.MP3 -> list.filter { it.fileFormat == "MP3" }
                    CatalogFilterChip.RATED -> list.filter { it.rating > 0 }
                }

                // 2. Search Query
                if (query.isNotEmpty()) {
                    list = list.filter {
                        it.title.lowercase(Locale.ROOT).contains(query) ||
                        it.artist.lowercase(Locale.ROOT).contains(query) ||
                        it.album.lowercase(Locale.ROOT).contains(query)
                    }
                }

                // 3. Sorting
                list = when (trackSortOrder) {
                    TrackSortOrder.TITLE_ASC -> list.sortedBy { it.title.lowercase(Locale.ROOT) }
                    TrackSortOrder.TITLE_DESC -> list.sortedByDescending { it.title.lowercase(Locale.ROOT) }
                    TrackSortOrder.ARTIST_ASC -> list.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.title.lowercase(Locale.ROOT) }))
                    TrackSortOrder.ALBUM_ASC -> list.sortedWith(compareBy({ it.album.lowercase(Locale.ROOT) }, { it.trackNumber }))
                    TrackSortOrder.TRACK_NUMBER -> list.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
                    TrackSortOrder.YEAR_DESC -> list.sortedByDescending { it.year }
                    TrackSortOrder.YEAR_ASC -> list.sortedBy { it.year }
                    TrackSortOrder.BITRATE_DESC -> list.sortedByDescending { it.bitrateKbps }
                    TrackSortOrder.DURATION_DESC -> list.sortedByDescending { it.durationMs }
                    TrackSortOrder.DATE_MODIFIED_DESC -> list.sortedByDescending { it.dateModified }
                }

                currentDisplayedSongs = list
                songAdapter.submitList(list)
                binding.tvCatalogCount.text = "${list.size} tracks"
            }
            1, 2 -> {
                var list = allAlbumsList

                // 1. Search Query
                if (query.isNotEmpty()) {
                    list = list.filter {
                        it.album.lowercase(Locale.ROOT).contains(query) ||
                        it.artist.lowercase(Locale.ROOT).contains(query)
                    }
                }

                // 2. Sorting
                list = when (albumSortOrder) {
                    AlbumSortOrder.TITLE_ASC -> list.sortedBy { it.album.lowercase(Locale.ROOT) }
                    AlbumSortOrder.TITLE_DESC -> list.sortedByDescending { it.album.lowercase(Locale.ROOT) }
                    AlbumSortOrder.ARTIST_ASC -> list.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.album.lowercase(Locale.ROOT) }))
                    AlbumSortOrder.YEAR_DESC -> list.sortedByDescending { it.year }
                    AlbumSortOrder.YEAR_ASC -> list.sortedBy { it.year }
                    AlbumSortOrder.TRACK_COUNT_DESC -> list.sortedByDescending { it.trackCount }
                    AlbumSortOrder.TRACK_COUNT_ASC -> list.sortedBy { it.trackCount }
                }

                currentDisplayedAlbums = list
                albumAdapter.submitList(list)
                binding.tvCatalogCount.text = "${list.size} albums"
            }
            3 -> {
                var list = allFoldersList
                if (query.isNotEmpty()) {
                    list = list.filter { it.name.lowercase(Locale.ROOT).contains(query) }
                }
                folderAdapter.submitList(list)
                binding.tvCatalogCount.text = "${list.size} folders"
            }
        }
    }

    private fun setupMiniPlayer(app: SpindleApp) {
        binding.cardMiniPlayer.setOnClickListener {
            if (audioEngine.playbackState.value.currentSong != null) {
                showNowPlayingSingleAudio(true)
            }
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

                        val formatStr = "${song.fileFormat} ${song.bitDepth}/${song.sampleRate / 1000}k"
                        b.tvMiniFormat.visibility = View.VISIBLE
                        b.tvMiniFormat.text = formatStr

                        if (songAdapter.activeSongId != song.id) {
                            val oldActiveId = songAdapter.activeSongId
                            songAdapter.activeSongId = song.id
                            val oldPos = currentDisplayedSongs.indexOfFirst { it.id == oldActiveId }
                            val newPos = currentDisplayedSongs.indexOfFirst { it.id == song.id }
                            if (oldPos != -1) songAdapter.notifyItemChanged(oldPos)
                            if (newPos != -1) songAdapter.notifyItemChanged(newPos)
                        }
                        if (::albumTracksAdapter.isInitialized && albumTracksAdapter.activeSongId != song.id) {
                            val oldActiveId = albumTracksAdapter.activeSongId
                            albumTracksAdapter.activeSongId = song.id
                            val oldPos = currentAlbumSongs.indexOfFirst { it.id == oldActiveId }
                            val newPos = currentAlbumSongs.indexOfFirst { it.id == song.id }
                            if (oldPos != -1) albumTracksAdapter.notifyItemChanged(oldPos)
                            if (newPos != -1) albumTracksAdapter.notifyItemChanged(newPos)
                        }

                        // Update Now Playing Single Audio metadata
                        b.tvNpTitle.text = song.title
                        b.tvNpArtist.text = song.artist

                        if (song.id != currentLoadedSongId) {
                            currentLoadedSongId = song.id
                            updateFavoriteIcon(song.rating > 0)

                            // Load circular cover art and extract accent color
                            viewLifecycleOwner.lifecycleScope.launch {
                                val cover = app.imageLoader.loadCover(song.path, 500, 500)
                                b.circularCoverArcView.coverBitmap = cover
                                val accent = app.imageLoader.extractAccentColor(song.path)
                                npLyricsAdapter.accentColor = accent
                                b.circularCoverArcView.accentColor = accent
                                b.audioWaveformView.accentColor = accent
                                b.btnNpPlayPause.backgroundTintList = ColorStateList.valueOf(accent)
                            }

                            // Extract real 64-bar song waveform amplitudes
                            waveformExtractionJob?.cancel()
                            waveformExtractionJob = viewLifecycleOwner.lifecycleScope.launch {
                                val wave = waveformExtractor.getWaveform(song.path, 64)
                                b.audioWaveformView.setWaveformData(wave)
                            }
                        }

                        // Synchronized lyrics
                        val lyrics = state.currentLyrics
                        if (lyrics != null && lyrics.lines.isNotEmpty()) {
                            npLyricsAdapter.lines = lyrics.lines
                            b.tvNpNoLyrics.visibility = View.GONE
                            b.rvNpLyrics.visibility = View.VISIBLE
                        } else {
                            npLyricsAdapter.lines = emptyList()
                            b.tvNpNoLyrics.visibility = View.VISIBLE
                            b.rvNpLyrics.visibility = View.GONE
                        }

                        // Load mini cover art only when track changes
                        if (song.id != currentMiniSongId) {
                            currentMiniSongId = song.id
                            viewLifecycleOwner.lifecycleScope.launch {
                                val isEink = app.themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id
                                val thumb = app.imageLoader.loadCover(song.path, 96, 96)
                                if (thumb != null) {
                                    b.ivMiniArt.imageTintList = null
                                    b.ivMiniArt.setPadding(0, 0, 0, 0)
                                    b.ivMiniArt.setImageBitmap(thumb)
                                } else {
                                    b.ivMiniArt.setPadding(8, 8, 8, 8)
                                    b.ivMiniArt.setImageResource(android.R.drawable.ic_media_play)
                                    b.ivMiniArt.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#64748B"))
                                }
                            }
                        }
                    }

                    // Progress and timings
                    b.circularCoverArcView.isPlaying = state.isPlaying
                    b.circularCoverArcView.progress = state.progress
                    b.audioWaveformView.progress = state.progress
                    b.audioWaveformView.isPlaying = state.isPlaying

                    b.tvNpCurrentTime.text = formatTime(state.currentPositionMs)
                    b.tvNpTotalDuration.text = formatTime(state.durationMs)

                    b.btnMiniPlayPause.setImageResource(
                        if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    )
                    b.btnNpPlayPause.setImageResource(
                        if (state.isPlaying) R.drawable.ic_np_pause else R.drawable.ic_np_play
                    )

                    updateShuffleIcon(state.shuffleMode)
                    updateRepeatIcon(state.repeatMode)

                    // Auto-scroll lyrics
                    if (state.activeLyricIndex >= 0 && isShowingNpLyrics) {
                        npLyricsAdapter.activeIndex = state.activeLyricIndex
                        (b.rvNpLyrics.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                            state.activeLyricIndex,
                            b.rvNpLyrics.height / 3
                        )
                    }
                }
            }
        }
    }

    private fun setupNowPlayingSingleAudio(app: SpindleApp) {
        binding.btnNpBack.setOnClickListener {
            arguments?.putBoolean(ARG_OPEN_NOW_PLAYING, false)
            showNowPlayingSingleAudio(false)
        }
        binding.btnNpQueue.setOnClickListener {
            QueueBottomSheet().show(childFragmentManager, "QueueBottomSheet")
        }

        binding.btnNpPlayPause.setOnClickListener { audioEngine.togglePlayPause() }
        binding.btnNpPrev.setOnClickListener { audioEngine.playPrevious(forcePreviousSong = true) }
        binding.btnNpNext.setOnClickListener { audioEngine.playNext() }

        binding.btnNpRepeat.setOnClickListener {
            val nextMode = audioEngine.toggleRepeat()
            updateRepeatIcon(nextMode)
        }

        binding.btnNpShuffle.setOnClickListener {
            val nextMode = audioEngine.toggleShuffle()
            updateShuffleIcon(nextMode)
        }

        binding.btnNpFavorite.setOnClickListener {
            val song = audioEngine.playbackState.value.currentSong
            if (song != null) {
                val newRating = if (song.rating > 0) 0 else 5
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.songDao().updateRating(song.id, newRating)
                }
                updateFavoriteIcon(newRating > 0)
            }
        }

        val openSpecsAction = View.OnClickListener {
            val song = audioEngine.playbackState.value.currentSong
            if (song != null) {
                DialogFileSpecs(song).show(parentFragmentManager, "DialogFileSpecs")
            }
        }
        binding.btnNpMenu.setOnClickListener(openSpecsAction)

        // Audiophile turntable vinyl swipe gestures
        binding.circularCoverArcView.onSwipeLeft = {
            audioEngine.playNext()
        }
        binding.circularCoverArcView.onSwipeRight = {
            audioEngine.playPrevious(forcePreviousSong = true)
        }

        // Swipe down on Now Playing screen to dismiss to music catalog
        val npSwipeDownDetector = android.view.GestureDetector(requireContext(), object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: android.view.MotionEvent?,
                e2: android.view.MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (diffY > 120 && kotlin.math.abs(diffY) > kotlin.math.abs(diffX) && velocityY > 200) {
                    arguments?.putBoolean(ARG_OPEN_NOW_PLAYING, false)
                    showNowPlayingSingleAudio(false)
                    return true
                }
                return false
            }
        })
        binding.nowPlayingSingleContainer.setOnTouchListener { _, event ->
            npSwipeDownDetector.onTouchEvent(event)
            true
        }

        binding.circularCoverArcView.onSeek = { progress ->
            val duration = audioEngine.playbackState.value.durationMs
            if (duration > 0) {
                audioEngine.seekTo((duration * progress).toLong())
            }
        }

        binding.audioWaveformView.onSeek = { progress ->
            val duration = audioEngine.playbackState.value.durationMs
            if (duration > 0) {
                audioEngine.seekTo((duration * progress).toLong())
            }
        }

        // Live Synchronized Lyrics Panel
        val app = requireActivity().application as SpindleApp
        val toggleLyrics = View.OnClickListener {
            isShowingNpLyrics = !isShowingNpLyrics
            binding.cardNpLyrics.visibility = if (isShowingNpLyrics) View.VISIBLE else View.GONE
            val accent = app.themeManager.currentTheme.value.accentColor
            binding.btnNpLyrics.imageTintList = ColorStateList.valueOf(
                if (isShowingNpLyrics) accent else Color.parseColor("#94A3B8")
            )
        }
        binding.btnNpLyrics.setOnClickListener(toggleLyrics)
        binding.cardNpLyrics.setOnClickListener(toggleLyrics)
        binding.circularCoverArcView.onCoverClicked = {
            toggleLyrics.onClick(binding.circularCoverArcView)
        }
    }

    private fun updateFavoriteIcon(isFav: Boolean) {
        val app = requireActivity().application as SpindleApp
        val isEink = app.themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id
        val favColor = if (isEink) Color.BLACK else Color.parseColor("#FB7185")
        if (isFav) {
            binding.btnNpFavorite.setImageResource(R.drawable.ic_np_heart_filled)
            binding.btnNpFavorite.imageTintList = ColorStateList.valueOf(favColor)
        } else {
            binding.btnNpFavorite.setImageResource(R.drawable.ic_np_heart)
            binding.btnNpFavorite.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#B0B4CE"))
        }
    }

    private fun updateShuffleIcon(mode: ShuffleMode) {
        val app = requireActivity().application as SpindleApp
        val isEink = app.themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id
        val accent = if (isEink) Color.BLACK else app.themeManager.currentTheme.value.accentColor
        when (mode) {
            ShuffleMode.OFF -> {
                binding.btnNpShuffle.alpha = 0.4f
                binding.btnNpShuffle.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#94A3B8"))
            }
            ShuffleMode.ALL -> {
                binding.btnNpShuffle.alpha = 1.0f
                binding.btnNpShuffle.imageTintList = ColorStateList.valueOf(accent)
            }
            ShuffleMode.ALBUM -> {
                binding.btnNpShuffle.alpha = 1.0f
                binding.btnNpShuffle.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#FDE68A"))
            }
        }
    }

    private fun updateRepeatIcon(mode: RepeatMode) {
        val app = requireActivity().application as SpindleApp
        val isEink = app.themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id
        val accent = if (isEink) Color.BLACK else app.themeManager.currentTheme.value.accentColor
        when (mode) {
            RepeatMode.OFF -> {
                binding.btnNpRepeat.alpha = 0.4f
                binding.btnNpRepeat.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#94A3B8"))
            }
            RepeatMode.ALL -> {
                binding.btnNpRepeat.alpha = 1.0f
                binding.btnNpRepeat.imageTintList = ColorStateList.valueOf(accent)
            }
            RepeatMode.ONE -> {
                binding.btnNpRepeat.alpha = 1.0f
                binding.btnNpRepeat.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#FB7185"))
            }
        }
    }

    private fun observeAudioMetrics(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.audioEngine.metricsTracker.metrics.collectLatest { metrics ->
                    _binding?.let { b ->
                        val theme = app.themeManager.currentTheme.value
                        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)
                        val isDark = theme.isDarkAppTheme
                        val routeInfo = if (metrics.isBluetoothConnected) {
                            val btName = metrics.bluetoothDeviceName ?: "BT Audio"
                            val battInfo = if (metrics.bluetoothBatteryPct != null && metrics.bluetoothBatteryPct >= 0) {
                                " (${metrics.bluetoothBatteryPct}%)"
                            } else ""
                            "${metrics.outputRoute} • $btName$battInfo"
                        } else {
                            metrics.outputRoute
                        }
                    }
                }
            }
        }
    }

    fun showNowPlayingSingleAudio(show: Boolean) {
        isNowPlayingSingleVisible = show
        if (show) {
            val playback = audioEngine.playbackState.value
            binding.circularCoverArcView.isPlaying = playback.isPlaying
            binding.circularCoverArcView.progress = playback.progress
            binding.nowPlayingSingleContainer.visibility = View.VISIBLE
            binding.nowPlayingSingleContainer.translationY = 320f
            binding.nowPlayingSingleContainer.alpha = 0f
            binding.nowPlayingSingleContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } else {
            binding.nowPlayingSingleContainer.animate()
                .translationY(320f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    binding.nowPlayingSingleContainer.visibility = View.GONE
                    binding.cardNpLyrics.visibility = View.GONE
                    isShowingNpLyrics = false
                    binding.btnNpLyrics.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                }.start()
        }
    }

    fun showAlbumDetail(album: AlbumItem, songs: List<SongEntity>) {
        currentAlbumItem = album
        currentAlbumSongs = songs

        val app = requireActivity().application as SpindleApp
        val theme = app.themeManager.currentTheme.value
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)

        binding.tvAlbumDetailHeaderTitle.text = when (album.format) {
            "DISCOGRAPHY" -> "ARTIST"
            "GENRE" -> "GENRE"
            "MIXTAPE" -> "MIXTAPE"
            else -> "ALBUM"
        }
        binding.tvAlbumDetailTitle.text = album.album
        binding.tvAlbumDetailArtist.text = album.artist

        val totalDurationMs = songs.sumOf { it.durationMs }
        val durationFormatted = formatTime(totalDurationMs)
        val yearStr = if (album.year > 0 && album.format != "MIXTAPE") "${album.year} • " else ""
        binding.tvAlbumDetailMeta.text = "$yearStr${songs.size} Tracks • $durationFormatted"
        binding.tvAlbumDetailFormat.text = if (album.format == "MIXTAPE") "CUSTOM CASSETTE" else album.format

        // Load thumbnail into circular disc cover
        viewLifecycleOwner.lifecycleScope.launch {
            if (album.format == "MIXTAPE" || album.representativePath.isBlank()) {
                binding.ivAlbumDetailCover.setPadding(32, 32, 32, 32)
                binding.ivAlbumDetailCover.setImageResource(com.hana.spindle.R.drawable.ic_mixtape_tape)
                binding.ivAlbumDetailCover.imageTintList = null
            } else {
                val cover = app.imageLoader.loadCover(album.representativePath, 300, 300)
                if (cover != null) {
                    binding.ivAlbumDetailCover.imageTintList = null
                    binding.ivAlbumDetailCover.setPadding(0, 0, 0, 0)
                    binding.ivAlbumDetailCover.setImageBitmap(cover)
                } else {
                    binding.ivAlbumDetailCover.setPadding(24, 24, 24, 24)
                    binding.ivAlbumDetailCover.setImageResource(android.R.drawable.ic_media_play)
                    binding.ivAlbumDetailCover.imageTintList = ColorStateList.valueOf(
                        if (isEink) Color.BLACK else Color.parseColor("#94A3B8")
                    )
                }
            }
        }

        // Submit songs to albumTracksAdapter
        albumTracksAdapter.submitList(songs)

        // Wire Action Pill Buttons
        binding.btnAlbumDetailPlayAll.setOnClickListener {
            if (songs.isNotEmpty()) {
                currentDisplayedSongs = songs
                audioEngine.playQueue(songs, 0)
                showNowPlayingSingleAudio(true)
            }
        }

        binding.btnAlbumDetailShuffle.setOnClickListener {
            if (songs.isNotEmpty()) {
                currentDisplayedSongs = songs
                val shuffled = songs.shuffled()
                audioEngine.playQueue(shuffled, 0)
                showNowPlayingSingleAudio(true)
            }
        }

        binding.cardAlbumDetailCover.setOnClickListener {
            if (songs.isNotEmpty()) {
                currentDisplayedSongs = songs
                audioEngine.playQueue(songs, 0)
                showNowPlayingSingleAudio(true)
            }
        }

        binding.btnAlbumDetailCoverPlay.setOnClickListener {
            if (songs.isNotEmpty()) {
                currentDisplayedSongs = songs
                audioEngine.playQueue(songs, 0)
                showNowPlayingSingleAudio(true)
            }
        }

        binding.btnAlbumDetailBack.setOnClickListener {
            hideAlbumDetail()
        }

        // Animate Container in
        binding.albumDetailContainer.visibility = View.VISIBLE
        binding.albumDetailContainer.translationX = 200f
        binding.albumDetailContainer.alpha = 0f
        binding.albumDetailContainer.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    fun hideAlbumDetail() {
        if (_binding == null || binding.albumDetailContainer.visibility != View.VISIBLE) return
        albumTracksJob?.cancel()
        binding.albumDetailContainer.animate()
            .translationX(200f)
            .alpha(0f)
            .setDuration(180)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                _binding?.albumDetailContainer?.visibility = View.GONE
            }
            .start()
    }

    fun handleBackPressed(): Boolean {
        if (binding.nowPlayingSingleContainer.visibility == View.VISIBLE) {
            if (binding.cardNpLyrics.visibility == View.VISIBLE) {
                binding.cardNpLyrics.visibility = View.GONE
                isShowingNpLyrics = false
                binding.btnNpLyrics.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                return true
            }
            arguments?.putBoolean(ARG_OPEN_NOW_PLAYING, false)
            showNowPlayingSingleAudio(false)
            return true
        }
        if (binding.albumDetailContainer.visibility == View.VISIBLE) {
            hideAlbumDetail()
            return true
        }
        return false
    }

    private fun formatTime(millis: Long): String {
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(millis) -
                java.util.concurrent.TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun loadSongs(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getAllSongs().collectLatest { songs ->
                allSongsList = songs
                applyFilterAndSort()
            }
        }
    }

    private fun loadAlbums(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getAlbums().collectLatest { albums ->
                allAlbumsList = albums
                applyFilterAndSort()
            }
        }
    }

    private fun loadArtists(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getAlbums().collectLatest { albums ->
                val artists = albums.groupBy { it.artist }.map { (artist, list) ->
                    AlbumItem(
                        album = artist,
                        artist = "${list.size} albums",
                        trackCount = list.sumOf { it.trackCount },
                        representativePath = list.firstOrNull()?.representativePath ?: "",
                        year = list.maxOfOrNull { it.year } ?: 0,
                        format = "DISCOGRAPHY"
                    )
                }
                allAlbumsList = artists
                applyFilterAndSort()
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
        applyFilterAndSort()
    }

    private fun loadGenres(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getAllSongs().collectLatest { songs ->
                val genres = songs.filter { !it.genre.isNullOrBlank() }
                    .groupBy { it.genre!! }
                    .map { (genre, list) ->
                        AlbumItem(
                            album = genre,
                            artist = "${list.size} tracks",
                            trackCount = list.size,
                            representativePath = list.firstOrNull()?.path ?: "",
                            year = 0,
                            format = "GENRE"
                        )
                    }
                allAlbumsList = genres
                applyFilterAndSort()
            }
        }
    }

    private fun loadHiRes(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getHiResSongs().collectLatest { hiResSongs ->
                allSongsList = hiResSongs
                applyFilterAndSort()
            }
        }
    }

    private fun loadFavorites(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.songDao().getRatedSongs().collectLatest { ratedSongs ->
                allSongsList = ratedSongs
                applyFilterAndSort()
            }
        }
    }

    private fun loadMixtapes(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.playlistDao().getAllPlaylists().collectLatest { playlists ->
                val items = playlists.map { playlist ->
                    AlbumItem(
                        album = playlist.name,
                        artist = "${playlist.trackCount} tracks",
                        trackCount = playlist.trackCount,
                        representativePath = "",
                        year = playlist.id.toInt(),
                        format = "MIXTAPE"
                    )
                }
                allAlbumsList = items
                applyFilterAndSort()
            }
        }
    }

    private fun observeScanProgress(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.musicScanner.progress.collectLatest { prog ->
                val scanning = prog.isScanning
                _binding?.tvScanStatus?.visibility = if (scanning) View.VISIBLE else View.GONE
                _binding?.tvScanStatus?.text = if (scanning) "Scanning..." else ""
                _binding?.tvScanStatus?.setTextColor(Color.parseColor("#FDE68A"))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
