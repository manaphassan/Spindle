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
import android.net.Uri
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.hana.spindle.data.db.FtsQueryBuilder
import com.hana.spindle.data.db.TrackEntity
import com.hana.spindle.databinding.FragmentCatalogBinding
import com.hana.spindle.playback.AudioEngine
import com.hana.spindle.playback.RepeatMode
import com.hana.spindle.playback.ShuffleMode
import com.hana.spindle.theme.CassetteTheme
import com.hana.spindle.ui.catalog.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class CatalogFragment : Fragment() {

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!

    private lateinit var audioEngine: AudioEngine
    private lateinit var songAdapter: TrackAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var npLyricsAdapter: LyricsAdapter
    private lateinit var albumTracksAdapter: TrackAdapter
    private lateinit var waveformExtractor: com.hana.spindle.data.WaveformExtractor
    private lateinit var searchSuggestionAdapter: SearchSuggestionAdapter
    private var waveformExtractionJob: Job? = null
    private var searchSuggestionJob: Job? = null
    private var mixtapeReorderHelper: androidx.recyclerview.widget.ItemTouchHelper? = null

    private var currentAlbumSongs: List<TrackEntity> = emptyList()
    private var currentAlbumItem: AlbumItem? = null
    private var albumTracksJob: Job? = null
    private var pendingPickAlbum: AlbumItem? = null

    private val pickCoverArtLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handlePickedCoverArt(uri)
        }
    }

    private var isNowPlayingSingleVisible = false
    private var isShowingNpLyrics = false
    private var currentLoadedSongId: Long = -1L
    private var currentMiniSongId: Long = -1L

    // 0: Tracks, 1: Albums, 2: Artists, 3: Folders, 4: Favorites, 5: Mixtapes
    private var currentTab = 0

    private var allSongsList: List<TrackEntity> = emptyList()
    private var currentDisplayedSongs: List<TrackEntity> = emptyList()

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
        private const val ARG_INITIAL_TAB = "arg_initial_tab"

        // Smart Mixtapes Auto-Curated Identifiers
        private const val SMART_ID_RECENTLY_ADDED = -1
        private const val SMART_ID_RECENTLY_PLAYED = -2
        private const val SMART_ID_MOST_PLAYED = -3
        private const val SMART_ID_NEVER_PLAYED = -4
        private const val SMART_ID_FAVORITES = -5
        private const val SMART_ID_HI_RES = -6

        fun newInstance(openNowPlaying: Boolean = false, initialTab: Int = -1): CatalogFragment {
            return CatalogFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_OPEN_NOW_PLAYING, openNowPlaying)
                    putInt(ARG_INITIAL_TAB, initialTab)
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

        val initTab = arguments?.getInt(ARG_INITIAL_TAB, -1) ?: -1
        if (initTab in 0..6) {
            binding.root.post { switchToTab(initTab) }
        } else {
            loadSongs(app)
        }

        if (arguments?.getBoolean(ARG_OPEN_NOW_PLAYING) == true) {
            showNowPlayingSingleAudio(true)
        }
    }

    private fun setupAdapters(app: SpindleApp) {
        songAdapter = TrackAdapter(
            imageLoader = app.imageLoader,
            onTrackClicked = { song, index ->
                audioEngine.playQueue(currentDisplayedSongs, index)
                showNowPlayingSingleAudio(true)
            },
            onRatingChanged = { song, newRating ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.trackDao().updateRating(song.id, newRating)
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
                    if (currentTab == 6) {
                        loadMixtapes(app)
                    }
                }
            }
            onInspectTags = { song ->
                TagInspectorDialog.show(requireContext(), song) {
                    loadSongs(app)
                }
            }
            onViewAudioSpecs = { song ->
                DialogFileSpecs(song).show(parentFragmentManager, "DialogFileSpecs")
            }
        }

        val reorderCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN,
            androidx.recyclerview.widget.ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION || fromPos == toPos) return false
                if (currentAlbumItem?.format != "MIXTAPE") return false

                val mutable = currentAlbumSongs.toMutableList()
                val item = mutable.removeAt(fromPos)
                mutable.add(toPos, item)
                currentAlbumSongs = mutable
                albumTracksAdapter.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.75f
                    viewHolder?.itemView?.scaleX = 1.02f
                    viewHolder?.itemView?.scaleY = 1.02f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                viewHolder.itemView.scaleX = 1.0f
                viewHolder.itemView.scaleY = 1.0f
                if (currentAlbumItem?.format == "MIXTAPE") {
                    val playlistId = currentAlbumItem?.year?.toLong() ?: return
                    val listToSave = currentAlbumSongs
                    viewLifecycleOwner.lifecycleScope.launch {
                        app.database.playlistDao().reorderPlaylist(playlistId, listToSave.map { it.id })
                    }
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || currentAlbumItem?.format != "MIXTAPE") {
                    albumTracksAdapter.notifyItemChanged(pos)
                    return
                }
                val trackToRemove = currentAlbumSongs[pos]
                val playlistId = currentAlbumItem?.year?.toLong() ?: return
                val mutable = currentAlbumSongs.toMutableList()
                mutable.removeAt(pos)
                currentAlbumSongs = mutable
                albumTracksAdapter.submitList(mutable)

                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.playlistDao().removeTrackFromPlaylist(playlistId, trackToRemove.id)
                    app.database.playlistDao().reorderPlaylist(playlistId, mutable.map { it.id })
                    android.widget.Toast.makeText(requireContext(), "Removed '${trackToRemove.title}' from mixtape", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            override fun isLongPressDragEnabled(): Boolean {
                return currentAlbumItem?.format == "MIXTAPE"
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return currentAlbumItem?.format == "MIXTAPE"
            }
        }
        val helper = androidx.recyclerview.widget.ItemTouchHelper(reorderCallback)
        mixtapeReorderHelper = helper
        helper.attachToRecyclerView(binding.rvAlbumTracks)

        albumTracksAdapter = TrackAdapter(
            imageLoader = app.imageLoader,
            onTrackClicked = { song, index ->
                currentDisplayedSongs = currentAlbumSongs
                audioEngine.playQueue(currentAlbumSongs, index)
                showNowPlayingSingleAudio(true)
            },
            onRatingChanged = { song, newRating ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.trackDao().updateRating(song.id, newRating)
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
                    if (currentTab == 6) {
                        loadMixtapes(app)
                    }
                }
            }
            onInspectTags = { song ->
                TagInspectorDialog.show(requireContext(), song) {
                    loadSongs(app)
                }
            }
            onViewAudioSpecs = { song ->
                DialogFileSpecs(song).show(parentFragmentManager, "DialogFileSpecs")
            }
            onStartDrag = { holder ->
                if (currentAlbumItem?.format == "MIXTAPE") {
                    mixtapeReorderHelper?.startDrag(holder)
                }
            }
            onRemoveFromMixtape = { track, _ ->
                if (currentAlbumItem?.format == "MIXTAPE") {
                    val playlistId = currentAlbumItem?.year?.toLong() ?: 0L
                    if (playlistId > 0) {
                        val mutable = currentAlbumSongs.toMutableList()
                        mutable.remove(track)
                        currentAlbumSongs = mutable
                        albumTracksAdapter.submitList(mutable)
                        viewLifecycleOwner.lifecycleScope.launch {
                            app.database.playlistDao().removeTrackFromPlaylist(playlistId, track.id)
                            app.database.playlistDao().reorderPlaylist(playlistId, mutable.map { it.id })
                            android.widget.Toast.makeText(requireContext(), "Removed '${track.title}' from mixtape", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
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
                    getTracksFlowForAlbum(app, album).collectLatest { albumSongs ->
                        showAlbumDetail(album, albumSongs)
                    }
                }
            },
            onPlayAlbumClicked = { album ->
                albumTracksJob?.cancel()
                albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                    getTracksFlowForAlbum(app, album).collectLatest { albumSongs ->
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
                    val options = arrayOf("Play Mixtape", "Export to .M3U", "Delete Mixtape")
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle(album.album)
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        val songs = app.database.playlistDao().getTracksForPlaylist(album.year.toLong()).first()
                                        if (songs.isNotEmpty()) {
                                            currentDisplayedSongs = songs
                                            audioEngine.playQueue(songs, 0)
                                            showNowPlayingSingleAudio(true)
                                        }
                                    }
                                }
                                1 -> {
                                    MixtapeDialogs.exportMixtape(
                                        requireContext(),
                                        app.database,
                                        viewLifecycleOwner.lifecycleScope,
                                        album.year.toLong(),
                                        album.album
                                    )
                                }
                                2 -> {
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
                        .show()
                } else if (album.format == "SMART_MIXTAPE") {
                    val options = arrayOf("Play Smart Tape", "Export to .M3U")
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle(album.album)
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        val songs = getTracksFlowForAlbum(app, album).first()
                                        if (songs.isNotEmpty()) {
                                            currentDisplayedSongs = songs
                                            audioEngine.playQueue(songs, 0)
                                            showNowPlayingSingleAudio(true)
                                        }
                                    }
                                }
                                1 -> {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        val songs = getTracksFlowForAlbum(app, album).first()
                                        MixtapeDialogs.exportSmartMixtape(
                                            requireContext(),
                                            viewLifecycleOwner.lifecycleScope,
                                            album.album,
                                            songs
                                        )
                                    }
                                }
                            }
                        }
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

            if (suggestion.track != null) {
                currentDisplayedSongs = listOf(suggestion.track)
                audioEngine.playQueue(listOf(suggestion.track), 0)
                showNowPlayingSingleAudio(true)
            } else if (suggestion.album != null) {
                val album = suggestion.album
                albumTracksJob?.cancel()
                albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                    getTracksFlowForAlbum(app, album).collectLatest { albumSongs ->
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

        binding.tabComposers.setOnClickListener {
            selectTab(3)
            binding.rvCatalog.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.rvCatalog.adapter = albumAdapter
            loadComposers(app)
        }

        binding.tabFolders.setOnClickListener {
            selectTab(4)
            binding.rvCatalog.layoutManager = LinearLayoutManager(requireContext())
            binding.rvCatalog.adapter = folderAdapter
            loadFolders()
        }

        binding.tabFavorites.setOnClickListener {
            selectTab(5)
            binding.rvCatalog.layoutManager = LinearLayoutManager(requireContext())
            binding.rvCatalog.adapter = songAdapter
            loadFavorites(app)
        }

        binding.tabMixtapes.setOnClickListener {
            selectTab(6)
            binding.rvCatalog.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.rvCatalog.adapter = albumAdapter
            loadMixtapes(app)
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
                        if (currentTab < 6) switchToTab(currentTab + 1)
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
            3 -> binding.tabComposers.performClick()
            4 -> binding.tabFolders.performClick()
            5 -> binding.tabFavorites.performClick()
            6 -> binding.tabMixtapes.performClick()
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        hideAlbumDetail()
        val app = requireActivity().application as SpindleApp
        val theme = app.themeManager.currentTheme.value
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)
        val isDark = theme.isDarkAppTheme
        val activeBg = if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#F97316") else ContextCompat.getColor(requireContext(), R.color.metal81_red)
        val inactiveBg = if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#212228")
        val activeText = Color.WHITE
        val inactiveText = if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#2A2E45") else Color.parseColor("#94A3B8")

        val tabs = listOf(
            binding.tabSongs,
            binding.tabAlbums,
            binding.tabArtists,
            binding.tabComposers,
            binding.tabFolders,
            binding.tabFavorites,
            binding.tabMixtapes
        )

        tabs.forEachIndexed { i, btn ->
            btn.backgroundTintList = ColorStateList.valueOf(if (i == index) activeBg else inactiveBg)
            btn.setTextColor(if (i == index) activeText else inactiveText)
        }

        val isMixtapeTab = (index == 6)
        binding.btnNewMixtape.visibility = if (isMixtapeTab) View.VISIBLE else View.GONE
        binding.btnShuffle.visibility = if (isMixtapeTab) View.GONE else View.VISIBLE
        binding.btnPlayAll.visibility = if (isMixtapeTab) View.GONE else View.VISIBLE

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
            val isAlbum = (currentTab == 1 || currentTab == 2 || currentTab == 3 || currentTab == 6)
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
        val isAlbum = (currentTab == 1 || currentTab == 2 || currentTab == 3 || currentTab == 6)
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
            0, 5 -> {
                val index = currentDisplayedSongs.indexOfFirst {
                    val firstChar = it.title.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (targetChar == '#') !firstChar.isLetter() else firstChar == targetChar
                }
                if (index >= 0) {
                    (lm as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
                }
            }
            1, 2, 3, 6 -> {
                val index = currentDisplayedAlbums.indexOfFirst {
                    val firstChar = it.album.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (targetChar == '#') !firstChar.isLetter() else firstChar == targetChar
                }
                if (index >= 0) {
                    (lm as? GridLayoutManager)?.scrollToPositionWithOffset(index, 0)
                }
            }
            4 -> {
                val index = allFoldersList.indexOfFirst {
                    val firstChar = it.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (targetChar == '#') !firstChar.isLetter() else firstChar == targetChar
                }
                if (index >= 0) {
                    (lm as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
                }
            }
        }
    }

    private fun setupQuickActions() {
        val app = requireActivity().application as SpindleApp
        binding.btnNewMixtape.setOnClickListener {
            val options = arrayOf(
                getString(R.string.mixtape_cut_new),
                "Import .M3U / .M3U8 Playlist...",
                "Export All Mixtapes to MicroSD (.M3U8)",
                "Sync MicroSD Mixtapes (Auto-Import)"
            )
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Mixtapes & MicroSD Sync")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            MixtapeDialogs.showCreateMixtapeDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope) {
                                loadMixtapes(app)
                            }
                        }
                        1 -> {
                            MixtapeDialogs.showImportM3uDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope) {
                                loadMixtapes(app)
                            }
                        }
                        2 -> {
                            MixtapeDialogs.showExportAllMixtapesDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope)
                        }
                        3 -> {
                            MixtapeDialogs.showSyncMicroSdMixtapesDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope) {
                                loadMixtapes(app)
                            }
                        }
                    }
                }
                .show()
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
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(binding.etCatalogSearch.windowToken, 0)
        }

        binding.etCatalogSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                binding.rvSearchSuggestions.visibility = View.GONE
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etCatalogSearch.windowToken, 0)
                true
            } else {
                false
            }
        }

        binding.etCatalogSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    binding.btnSearchClear.visibility = View.GONE
                    binding.rvSearchSuggestions.visibility = View.GONE
                    searchSuggestionJob?.cancel()
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
        val q = query.trim()
        if (q.isEmpty()) {
            binding.rvSearchSuggestions.visibility = View.GONE
            return
        }

        searchSuggestionJob?.cancel()
        val ftsQuery = FtsQueryBuilder.buildPrefixQuery(q)

        searchSuggestionJob = viewLifecycleOwner.lifecycleScope.launch {
            val suggestions = mutableListOf<SearchSuggestion>()
            val app = requireActivity().application as SpindleApp

            if (ftsQuery != null) {
                try {
                    val matchingTracks = withContext(Dispatchers.IO) {
                        app.database.trackDao().searchTracksFtsList(ftsQuery, limit = 4)
                    }
                    for (song in matchingTracks) {
                        suggestions.add(
                            SearchSuggestion(
                                title = song.title,
                                subtitle = "${song.artist} • ${song.fileFormat}",
                                type = SuggestionType.TRACK,
                                track = song
                            )
                        )
                    }

                    val matchingAlbums = withContext(Dispatchers.IO) {
                        app.database.trackDao().searchAlbumsFtsList(ftsQuery, limit = 3)
                    }
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

                    val matchingArtists = withContext(Dispatchers.IO) {
                        app.database.trackDao().searchArtistsFtsList(ftsQuery, limit = 2)
                    }
                    for (artist in matchingArtists) {
                        suggestions.add(
                            SearchSuggestion(
                                title = artist,
                                subtitle = "Artist",
                                type = SuggestionType.ARTIST
                            )
                        )
                    }

                    val matchingComposers = withContext(Dispatchers.IO) {
                        app.database.trackDao().searchComposersFtsList(ftsQuery, limit = 2)
                    }
                    for (composer in matchingComposers) {
                        suggestions.add(
                            SearchSuggestion(
                                title = composer,
                                subtitle = "Classical Composer",
                                type = SuggestionType.COMPOSER
                            )
                        )
                    }
                } catch (e: Exception) {
                    suggestions.clear()
                }
            }

            // Fallback in-memory matching if FTS had no matches or ftsQuery was null
            if (suggestions.isEmpty()) {
                val tokens = q.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
                val fallbackTracks = allSongsList.filter { song ->
                    tokens.all { t ->
                        song.title.contains(t, ignoreCase = true) ||
                        song.artist.contains(t, ignoreCase = true) ||
                        song.album.contains(t, ignoreCase = true) ||
                        song.composer?.contains(t, ignoreCase = true) == true
                    }
                }.take(4)
                for (song in fallbackTracks) {
                    suggestions.add(
                        SearchSuggestion(
                            title = song.title,
                            subtitle = "${song.artist} • ${song.fileFormat}",
                            type = SuggestionType.TRACK,
                            track = song
                        )
                    )
                }

                val fallbackAlbums = allAlbumsList.filter { album ->
                    tokens.all { t ->
                        album.album.contains(t, ignoreCase = true) ||
                        album.artist.contains(t, ignoreCase = true)
                    }
                }.take(3)
                for (album in fallbackAlbums) {
                    suggestions.add(
                        SearchSuggestion(
                            title = album.album,
                            subtitle = "${album.artist} • ${album.trackCount} tracks",
                            type = SuggestionType.ALBUM,
                            album = album
                        )
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                searchSuggestionAdapter.submitList(suggestions)
                binding.rvSearchSuggestions.visibility = View.VISIBLE
            } else {
                binding.rvSearchSuggestions.visibility = View.GONE
            }
        }
    }

    private fun applyFilterAndSort() {
        val query = binding.etCatalogSearch.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""

        when (currentTab) {
            0, 5 -> {
                var list = allSongsList

                // 1. Filter Chips
                list = when (currentFilterChip) {
                    CatalogFilterChip.ALL -> list
                    CatalogFilterChip.FAVORITES -> list.filter { it.isFavorite }
                    CatalogFilterChip.RECENTLY_ADDED -> list.sortedByDescending { it.dateAdded }
                    CatalogFilterChip.MOST_PLAYED -> list.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
                    CatalogFilterChip.COMPOSER -> list.filter { !it.composer.isNullOrBlank() }
                    CatalogFilterChip.DUPLICATES -> {
                        val dupKeys = list.groupBy { it.title.lowercase(Locale.ROOT) to it.artist.lowercase(Locale.ROOT) }
                            .filter { it.value.size > 1 }.keys
                        list.filter { (it.title.lowercase(Locale.ROOT) to it.artist.lowercase(Locale.ROOT)) in dupKeys }
                    }
                    CatalogFilterChip.HI_RES -> list.filter { it.bitDepth >= 24 || it.sampleRate > 48000 || it.fileFormat in setOf("FLAC", "WAV", "DSD", "DSF") }
                    CatalogFilterChip.LOSSLESS -> list.filter { it.fileFormat in setOf("FLAC", "WAV", "ALAC", "AIFF", "DSD", "DSF") }
                    CatalogFilterChip.FLAC -> list.filter { it.fileFormat == "FLAC" }
                    CatalogFilterChip.WAV -> list.filter { it.fileFormat == "WAV" }
                    CatalogFilterChip.MP3 -> list.filter { it.fileFormat == "MP3" }
                    CatalogFilterChip.RATED -> list.filter { it.rating > 0 }
                }

                // 2. Search Query (FTS-style multi-token matching)
                if (query.isNotEmpty()) {
                    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    list = list.filter { track ->
                        tokens.all { t ->
                            track.title.contains(t, ignoreCase = true) ||
                            track.artist.contains(t, ignoreCase = true) ||
                            track.album.contains(t, ignoreCase = true) ||
                            track.albumArtist?.contains(t, ignoreCase = true) == true ||
                            track.composer?.contains(t, ignoreCase = true) == true ||
                            track.genre?.contains(t, ignoreCase = true) == true
                        }
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
                    TrackSortOrder.PLAY_COUNT_DESC -> list.sortedByDescending { it.playCount }
                    TrackSortOrder.LAST_PLAYED_DESC -> list.sortedByDescending { it.lastPlayedAt ?: 0L }
                }

                // 4. Grouping
                if (groupByMode != GroupByMode.NONE) {
                    list = when (groupByMode) {
                        GroupByMode.NONE -> list
                        GroupByMode.ARTIST -> list.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.album.lowercase(Locale.ROOT) }, { it.trackNumber }))
                        GroupByMode.COMPOSER -> list.sortedWith(compareBy({ it.composer?.lowercase(Locale.ROOT) ?: "\uffff" }, { it.album.lowercase(Locale.ROOT) }, { it.trackNumber }))
                        GroupByMode.DECADE -> list.sortedWith(compareBy({ if (it.year > 0) it.year / 10 * 10 else 9999 }, { it.year }, { it.album.lowercase(Locale.ROOT) }, { it.trackNumber }))
                        GroupByMode.INITIAL_LETTER -> list.sortedWith(compareBy({ it.title.trim().firstOrNull()?.uppercaseChar() ?: '#' }, { it.title.lowercase(Locale.ROOT) }))
                        GroupByMode.FORMAT -> list.sortedWith(compareBy({ it.fileFormat.uppercase(Locale.ROOT) }, { it.title.lowercase(Locale.ROOT) }))
                    }
                }

                currentDisplayedSongs = list
                songAdapter.submitList(list)
                binding.tvCatalogCount.text = "${list.size} tracks"
            }
            1, 2, 3, 6 -> {
                var list = allAlbumsList

                // 1. Search Query (FTS-style multi-token matching)
                if (query.isNotEmpty()) {
                    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    list = list.filter { item ->
                        tokens.all { t ->
                            item.album.contains(t, ignoreCase = true) ||
                            item.artist.contains(t, ignoreCase = true)
                        }
                    }
                }

                // 2. Sorting
                list = if (currentTab == 6 && query.isEmpty()) {
                    val smart = list.filter { it.format == "SMART_MIXTAPE" }
                    val custom = list.filter { it.format != "SMART_MIXTAPE" }
                    val sortedCustom = when (albumSortOrder) {
                        AlbumSortOrder.TITLE_ASC -> custom.sortedBy { it.album.lowercase(Locale.ROOT) }
                        AlbumSortOrder.TITLE_DESC -> custom.sortedByDescending { it.album.lowercase(Locale.ROOT) }
                        AlbumSortOrder.ARTIST_ASC -> custom.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.album.lowercase(Locale.ROOT) }))
                        AlbumSortOrder.YEAR_DESC -> custom.sortedByDescending { it.year }
                        AlbumSortOrder.YEAR_ASC -> custom.sortedBy { it.year }
                        AlbumSortOrder.TRACK_COUNT_DESC -> custom.sortedByDescending { it.trackCount }
                        AlbumSortOrder.TRACK_COUNT_ASC -> custom.sortedBy { it.trackCount }
                    }
                    smart + sortedCustom
                } else {
                    when (albumSortOrder) {
                        AlbumSortOrder.TITLE_ASC -> list.sortedBy { it.album.lowercase(Locale.ROOT) }
                        AlbumSortOrder.TITLE_DESC -> list.sortedByDescending { it.album.lowercase(Locale.ROOT) }
                        AlbumSortOrder.ARTIST_ASC -> list.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.album.lowercase(Locale.ROOT) }))
                        AlbumSortOrder.YEAR_DESC -> list.sortedByDescending { it.year }
                        AlbumSortOrder.YEAR_ASC -> list.sortedBy { it.year }
                        AlbumSortOrder.TRACK_COUNT_DESC -> list.sortedByDescending { it.trackCount }
                        AlbumSortOrder.TRACK_COUNT_ASC -> list.sortedBy { it.trackCount }
                    }
                }

                currentDisplayedAlbums = list
                albumAdapter.submitList(list)
                val unitLabel = when (currentTab) {
                    6 -> "mixtapes"
                    3 -> "composers"
                    2 -> "artists"
                    else -> "albums"
                }
                binding.tvCatalogCount.text = "${list.size} $unitLabel"
            }
            4 -> {
                var list = allFoldersList
                if (query.isNotEmpty()) {
                    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    list = list.filter { folder ->
                        tokens.all { t -> folder.name.contains(t, ignoreCase = true) }
                    }
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
                            updateFavoriteIcon(song.isFavorite)

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
            val track = audioEngine.playbackState.value.currentTrack
            if (track != null) {
                val newFavorite = !track.isFavorite
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.trackDao().updateFavorite(track.id, newFavorite)
                }
                updateFavoriteIcon(newFavorite)
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

    fun showAlbumDetail(album: AlbumItem, songs: List<TrackEntity>) {
        currentAlbumItem = album
        currentAlbumSongs = songs

        val app = requireActivity().application as SpindleApp
        val theme = app.themeManager.currentTheme.value
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)

        binding.tvAlbumDetailHeaderTitle.text = when (album.format) {
            "DISCOGRAPHY" -> "ARTIST"
            "COMPOSER" -> "COMPOSER"
            "GENRE" -> "GENRE"
            "MIXTAPE" -> "MIXTAPE"
            "SMART_MIXTAPE" -> "SMART CASSETTE"
            else -> "ALBUM"
        }
        binding.tvAlbumDetailTitle.text = album.album
        binding.tvAlbumDetailArtist.text = album.artist

        val totalDurationMs = songs.sumOf { it.durationMs }
        val durationFormatted = formatTime(totalDurationMs)
        val yearStr = if (album.year > 0 && album.format != "MIXTAPE" && album.format != "SMART_MIXTAPE") "${album.year} • " else ""
        binding.tvAlbumDetailMeta.text = "$yearStr${songs.size} Tracks • $durationFormatted"

        if (album.format == "MIXTAPE") {
            albumTracksAdapter.isReorderable = true
            binding.tvAlbumDetailFormat.text = "CUSTOM CASSETTE (DRAG TO REORDER)"
            binding.tvAlbumDetailFormat.setTextColor(Color.parseColor("#F97316"))
            binding.btnAlbumDetailExportM3u.visibility = View.VISIBLE
            binding.btnAlbumDetailExportM3u.setOnClickListener {
                MixtapeDialogs.exportMixtape(
                    requireContext(),
                    app.database,
                    viewLifecycleOwner.lifecycleScope,
                    album.year.toLong(),
                    album.album
                )
            }
        } else if (album.format == "SMART_MIXTAPE") {
            albumTracksAdapter.isReorderable = false
            binding.tvAlbumDetailFormat.text = "SMART CASSETTE • AUTO-CURATED"
            binding.tvAlbumDetailFormat.setTextColor(Color.parseColor("#FDE68A"))
            binding.btnAlbumDetailExportM3u.visibility = View.VISIBLE
            binding.btnAlbumDetailExportM3u.setOnClickListener {
                MixtapeDialogs.exportSmartMixtape(
                    requireContext(),
                    viewLifecycleOwner.lifecycleScope,
                    album.album,
                    songs
                )
            }
        } else if (album.format == "COMPOSER") {
            albumTracksAdapter.isReorderable = false
            binding.tvAlbumDetailFormat.text = "CLASSICAL COMPOSER"
            binding.tvAlbumDetailFormat.setTextColor(Color.parseColor("#38BDF8"))
            binding.btnAlbumDetailExportM3u.visibility = View.VISIBLE
            binding.btnAlbumDetailExportM3u.setOnClickListener {
                MixtapeDialogs.exportSmartMixtape(
                    requireContext(),
                    viewLifecycleOwner.lifecycleScope,
                    "Composer - ${album.album}",
                    songs
                )
            }
        } else {
            albumTracksAdapter.isReorderable = false
            binding.tvAlbumDetailFormat.text = album.format
            binding.tvAlbumDetailFormat.setTextColor(ContextCompat.getColor(requireContext(), R.color.vfd_emerald))
            binding.btnAlbumDetailExportM3u.visibility = View.GONE
        }

        // Load thumbnail into circular disc cover
        viewLifecycleOwner.lifecycleScope.launch {
            if (album.format == "MIXTAPE" || album.representativePath.isBlank()) {
                binding.ivAlbumDetailCover.setPadding(32, 32, 32, 32)
                binding.ivAlbumDetailCover.setImageResource(com.hana.spindle.R.drawable.ic_mixtape_tape)
                binding.ivAlbumDetailCover.imageTintList = null
                binding.ivAlbumDetailCover.setOnClickListener(null)
            } else {
                val cover = app.imageLoader.loadAlbumCover(album.album, album.artist, album.representativePath, 300, 300)
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

                // Tapping cover opens options dialog (Gallery, Online Fetch, Export cover.jpg)
                binding.ivAlbumDetailCover.setOnClickListener {
                    showCoverArtOptionsDialog(album)
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
            val album = currentAlbumItem ?: return@setOnClickListener
            showCoverArtOptionsDialog(album)
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

    private fun showCoverArtOptionsDialog(album: AlbumItem) {
        val app = requireActivity().application as SpindleApp
        val hasExistingCover = app.imageLoader.hasCover(album.representativePath, album.album, album.artist)

        val options = mutableListOf<String>()
        options.add("Choose from Gallery / Storage")
        options.add("Search Online Artwork (iTunes / Deezer)")
        if (hasExistingCover && album.representativePath.isNotBlank()) {
            options.add("Export cover.jpg to Album Folder")
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(album.album)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Choose from Gallery / Storage" -> {
                        pendingPickAlbum = album
                        try {
                            pickCoverArtLauncher.launch("image/*")
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Could not launch photo picker: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Search Online Artwork (iTunes / Deezer)" -> {
                        fetchCoverOnlineForDetail(album)
                    }
                    "Export cover.jpg to Album Folder" -> {
                        exportCoverToFolderForDetail(album)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handlePickedCoverArt(uri: Uri) {
        val album = pendingPickAlbum ?: return
        val app = requireActivity().application as SpindleApp
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stream = requireContext().contentResolver.openInputStream(uri)
                if (stream != null) {
                    val bitmap = app.imageLoader.decodeStreamToBitmap(stream, 600, 600)
                    stream.close()
                    if (bitmap != null) {
                        val saved = app.imageLoader.saveCustomCover(
                            bitmap = bitmap,
                            audioPath = album.representativePath,
                            album = album.album,
                            artist = album.artist,
                            saveToFolder = true
                        )
                        withContext(Dispatchers.Main) {
                            if (saved) {
                                binding.ivAlbumDetailCover.imageTintList = null
                                binding.ivAlbumDetailCover.setPadding(0, 0, 0, 0)
                                binding.ivAlbumDetailCover.setImageBitmap(bitmap)
                                albumAdapter.notifyDataSetChanged()
                                Toast.makeText(requireContext(), "Cover artwork updated & saved to folder!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireContext(), "Failed to save cover image.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Unsupported or corrupt image file.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchCoverOnlineForDetail(album: AlbumItem) {
        val app = requireActivity().application as SpindleApp
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), "Searching online artwork for \"${album.album}\"...", Toast.LENGTH_SHORT).show()
            val downloaded = app.coverArtFetcher.fetchCoverForAlbum(
                album = album.album,
                artist = album.artist,
                representativePath = album.representativePath,
                saveToFolder = true
            )
            if (downloaded != null) {
                val newCover = app.imageLoader.loadAlbumCover(album.album, album.artist, album.representativePath, 300, 300)
                if (newCover != null) {
                    binding.ivAlbumDetailCover.imageTintList = null
                    binding.ivAlbumDetailCover.setPadding(0, 0, 0, 0)
                    binding.ivAlbumDetailCover.setImageBitmap(newCover)
                    albumAdapter.notifyDataSetChanged()
                    Toast.makeText(requireContext(), "Cover updated & saved to folder (cover.jpg)!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Artwork not found in online catalogs.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportCoverToFolderForDetail(album: AlbumItem) {
        val app = requireActivity().application as SpindleApp
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val downloaded = app.imageLoader.getCoverFileForAlbum(album.album, album.artist)
            val success = if (downloaded.exists() && downloaded.length() > 0) {
                app.imageLoader.saveCoverToAlbumFolder(downloaded, album.representativePath)
            } else {
                val bmp = app.imageLoader.loadAlbumCover(album.album, album.artist, album.representativePath, 600, 600)
                if (bmp != null) {
                    app.imageLoader.saveCustomCover(bmp, album.representativePath, album.album, album.artist, saveToFolder = true)
                } else {
                    false
                }
            }
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(requireContext(), "Exported cover.jpg to album folder!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Could not export cover.jpg (read-only folder or storage restriction).", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun hideAlbumDetail() {
        if (_binding == null || binding.albumDetailContainer.visibility != View.VISIBLE) return
        albumTracksJob?.cancel()
        if (::albumTracksAdapter.isInitialized) {
            albumTracksAdapter.isReorderable = false
        }
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
            app.database.trackDao().getAllTracks().collectLatest { songs ->
                allSongsList = songs
                applyFilterAndSort()
            }
        }
    }

    private fun loadAlbums(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.trackDao().getAlbums().collectLatest { albums ->
                allAlbumsList = albums
                applyFilterAndSort()
            }
        }
    }

    private fun loadArtists(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.trackDao().getAlbums().collectLatest { albums ->
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

    private fun loadComposers(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.trackDao().getAllTracks().collectLatest { songs ->
                val composers = songs.filter { !it.composer.isNullOrBlank() }
                    .groupBy { it.composer!!.trim() }
                    .map { (composer, list) ->
                        AlbumItem(
                            album = composer,
                            artist = "${list.size} compositions",
                            trackCount = list.size,
                            representativePath = list.firstOrNull()?.path ?: "",
                            year = list.maxOfOrNull { it.year } ?: 0,
                            format = "COMPOSER"
                        )
                    }
                allAlbumsList = composers
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
            app.database.trackDao().getAllTracks().collectLatest { songs ->
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
            app.database.trackDao().getHiResTracks().collectLatest { hiResSongs ->
                allSongsList = hiResSongs
                applyFilterAndSort()
            }
        }
    }

    private fun loadFavorites(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.trackDao().getFavoriteTracks().collectLatest { favTracks ->
                allSongsList = favTracks
                applyFilterAndSort()
            }
        }
    }

    private fun getTracksFlowForAlbum(app: SpindleApp, album: AlbumItem): kotlinx.coroutines.flow.Flow<List<TrackEntity>> {
        return when (album.format) {
            "SMART_MIXTAPE" -> {
                when (album.year) {
                    SMART_ID_RECENTLY_ADDED -> app.database.trackDao().getRecentlyAddedTracks(100)
                    SMART_ID_RECENTLY_PLAYED -> app.database.trackDao().getRecentlyPlayedTracks(100)
                    SMART_ID_MOST_PLAYED -> app.database.trackDao().getMostPlayedTracks(100)
                    SMART_ID_NEVER_PLAYED -> app.database.trackDao().getNeverPlayedTracks(100)
                    SMART_ID_FAVORITES -> app.database.trackDao().getFavoriteTracks()
                    SMART_ID_HI_RES -> app.database.trackDao().getHiResTracks()
                    else -> app.database.trackDao().getRecentlyAddedTracks(100)
                }
            }
            "DISCOGRAPHY" -> app.database.trackDao().getTracksByArtist(album.album)
            "COMPOSER" -> app.database.trackDao().getTracksByComposer(album.album)
            "GENRE" -> app.database.trackDao().getTracksByGenre(album.album)
            "MIXTAPE" -> app.database.playlistDao().getTracksForPlaylist(album.year.toLong())
            else -> app.database.trackDao().getTracksByAlbum(album.album)
        }
    }

    private fun loadMixtapes(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                app.database.playlistDao().getAllPlaylists(),
                app.database.trackDao().getAllTracks()
            ) { playlists, allTracks ->
                val smartItems = mutableListOf<AlbumItem>()

                // 1. Recently Added (100 latest additions)
                val recentlyAdded = allTracks.sortedByDescending { it.dateAdded }.take(100)
                if (recentlyAdded.isNotEmpty()) {
                    smartItems.add(
                        AlbumItem(
                            album = "Recently Added",
                            artist = "Smart Tape • Latest Additions",
                            trackCount = recentlyAdded.size,
                            representativePath = recentlyAdded.firstOrNull()?.path ?: "",
                            year = SMART_ID_RECENTLY_ADDED,
                            format = "SMART_MIXTAPE"
                        )
                    )
                }

                // 2. Recently Played (Listening History)
                val recentlyPlayed = allTracks.filter { (it.lastPlayedAt ?: 0L) > 0L }
                    .sortedByDescending { it.lastPlayedAt ?: 0L }
                    .take(100)
                if (recentlyPlayed.isNotEmpty()) {
                    smartItems.add(
                        AlbumItem(
                            album = "Recently Played",
                            artist = "Smart Tape • Listening History",
                            trackCount = recentlyPlayed.size,
                            representativePath = recentlyPlayed.firstOrNull()?.path ?: "",
                            year = SMART_ID_RECENTLY_PLAYED,
                            format = "SMART_MIXTAPE"
                        )
                    )
                }

                // 3. Heavy Rotation (Most Played)
                val mostPlayed = allTracks.filter { it.playCount > 0 }
                    .sortedByDescending { it.playCount }
                    .take(100)
                if (mostPlayed.isNotEmpty()) {
                    smartItems.add(
                        AlbumItem(
                            album = "Heavy Rotation",
                            artist = "Smart Tape • Most Played",
                            trackCount = mostPlayed.size,
                            representativePath = mostPlayed.firstOrNull()?.path ?: "",
                            year = SMART_ID_MOST_PLAYED,
                            format = "SMART_MIXTAPE"
                        )
                    )
                }

                // 4. Uncut Vault (Never Played)
                val neverPlayed = allTracks.filter { it.playCount == 0 }.take(100)
                if (neverPlayed.isNotEmpty()) {
                    smartItems.add(
                        AlbumItem(
                            album = "Uncut Vault",
                            artist = "Smart Tape • Never Played",
                            trackCount = neverPlayed.size,
                            representativePath = neverPlayed.firstOrNull()?.path ?: "",
                            year = SMART_ID_NEVER_PLAYED,
                            format = "SMART_MIXTAPE"
                        )
                    )
                }

                // 5. Bookmarked Favorites
                val favorites = allTracks.filter { it.isFavorite }
                if (favorites.isNotEmpty()) {
                    smartItems.add(
                        AlbumItem(
                            album = "Bookmarked Favorites",
                            artist = "Smart Tape • Starred Tracks",
                            trackCount = favorites.size,
                            representativePath = favorites.firstOrNull()?.path ?: "",
                            year = SMART_ID_FAVORITES,
                            format = "SMART_MIXTAPE"
                        )
                    )
                }

                // 6. Studio Master Hi-Res
                val hiRes = allTracks.filter { it.bitDepth >= 24 || it.sampleRate > 48000 || it.fileFormat in listOf("FLAC", "WAV", "DSD", "DSF", "DFF", "AIFF") }
                if (hiRes.isNotEmpty()) {
                    smartItems.add(
                        AlbumItem(
                            album = "Studio Master Hi-Res",
                            artist = "Smart Tape • 24-bit / 96k+ / DSD",
                            trackCount = hiRes.size,
                            representativePath = hiRes.firstOrNull()?.path ?: "",
                            year = SMART_ID_HI_RES,
                            format = "SMART_MIXTAPE"
                        )
                    )
                }

                // Custom User Mixtapes
                val customItems = playlists.map { playlist ->
                    AlbumItem(
                        album = playlist.name,
                        artist = "${playlist.trackCount} tracks",
                        trackCount = playlist.trackCount,
                        representativePath = "",
                        year = playlist.id.toInt(),
                        format = "MIXTAPE"
                    )
                }

                smartItems + customItems
            }.collectLatest { items ->
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
