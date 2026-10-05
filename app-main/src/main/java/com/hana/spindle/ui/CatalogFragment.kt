package com.hana.spindle.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.net.Uri
import android.widget.Button
import android.widget.HorizontalScrollView
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

/**
 * Canonical alias for VaultFragment.
 * Conforms to canonical terminology authority (docs/GLOSSARY.md).
 */
typealias VaultFragment = CatalogFragment

class CatalogFragment : Fragment() {

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!

    private lateinit var audioEngine: AudioEngine
    private lateinit var tracksListAdapter: TrackAdapter
    private var songAdapter: TrackAdapter
        get() = tracksListAdapter
        set(value) { tracksListAdapter = value }

    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var npLyricsAdapter: LyricsAdapter
    private lateinit var albumTracksAdapter: TrackAdapter
    private lateinit var waveformExtractor: com.hana.spindle.data.WaveformExtractor
    private lateinit var searchSuggestionAdapter: SearchSuggestionAdapter
    private lateinit var tracksAdapter: TrackAdapter
    private lateinit var albumsAdapter: AlbumAdapter
    private lateinit var artistsAdapter: AlbumAdapter
    private lateinit var mixtapesAdapter: AlbumAdapter
    private lateinit var foldersAdapter: FolderAdapter
    private lateinit var favoritesAdapter: TrackAdapter
    private lateinit var composersAdapter: AlbumAdapter
    private var currentCatalogPanelIndex = 0
    private var waveformExtractionJob: Job? = null
    private var searchSuggestionJob: Job? = null
    private var mixtapeReorderHelper: androidx.recyclerview.widget.ItemTouchHelper? = null

    private var currentAlbumTracks: List<TrackEntity> = emptyList()
    @Deprecated("Use currentAlbumTracks in accordance with canonical glossary", ReplaceWith("currentAlbumTracks"))
    private var currentAlbumSongs: List<TrackEntity>
        get() = currentAlbumTracks
        set(value) { currentAlbumTracks = value }

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
    private var isPanoramaVisible = true
    private var currentLoadedTrackId: Long = -1L
    @Deprecated("Use currentLoadedTrackId in accordance with canonical glossary", ReplaceWith("currentLoadedTrackId"))
    private var currentLoadedSongId: Long
        get() = currentLoadedTrackId
        set(value) { currentLoadedTrackId = value }

    private var currentMiniTrackId: Long = -1L
    @Deprecated("Use currentMiniTrackId in accordance with canonical glossary", ReplaceWith("currentMiniTrackId"))
    private var currentMiniSongId: Long
        get() = currentMiniTrackId
        set(value) { currentMiniTrackId = value }

    // 0: Tracks, 1: Albums, 2: Artists, 3: Mixtapes, 4: Folders, 5: Favorites, 6: Composers
    private var currentTab = 0

    private var allTracksList: List<TrackEntity> = emptyList()
    @Deprecated("Use allTracksList in accordance with canonical glossary", ReplaceWith("allTracksList"))
    private var allSongsList: List<TrackEntity>
        get() = allTracksList
        set(value) { allTracksList = value }

    private var currentDisplayedTracks: List<TrackEntity> = emptyList()
    @Deprecated("Use currentDisplayedTracks in accordance with canonical glossary", ReplaceWith("currentDisplayedTracks"))
    private var currentDisplayedSongs: List<TrackEntity>
        get() = currentDisplayedTracks
        set(value) { currentDisplayedTracks = value }

    private var allAlbumsList: List<AlbumItem> = emptyList()
    private var currentDisplayedAlbums: List<AlbumItem> = emptyList()

    private var allArtistsList: List<AlbumItem> = emptyList()
    private var allMixtapesList: List<AlbumItem> = emptyList()
    private var allFoldersList: List<FolderItem> = emptyList()
    private var allFavoritesList: List<TrackEntity> = emptyList()
    private var allComposersList: List<AlbumItem> = emptyList()
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
        setupPanorama(app)
        setupCollectionPanorama(app)
        observeAudioMetrics(app)
        observeScanProgress(app)

        val initTab = arguments?.getInt(ARG_INITIAL_TAB, -1) ?: -1
        if (initTab in 0..6) {
            showPanorama(false)
            binding.root.post { scrollToCatalogPanel(initTab) }
        } else {
            showPanorama(true)
        }

        if (arguments?.getBoolean(ARG_OPEN_NOW_PLAYING) == true) {
            showNowPlayingSingleAudio(true)
        }
    }

    private fun setupAdapters(app: SpindleApp) {
        tracksListAdapter = TrackAdapter(
            imageLoader = app.imageLoader,
            onTrackClicked = { track, index ->
                audioEngine.playQueue(currentDisplayedTracks, index)
                showNowPlayingSingleAudio(true)
            },
            onRatingChanged = { track, newRating ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.trackDao().updateRating(track.id, newRating)
                }
            }
        ).apply {
            onPlayNext = { track ->
                audioEngine.playNextInQueue(track)
                android.widget.Toast.makeText(requireContext(), getString(R.string.toast_will_play_next, track.title), android.widget.Toast.LENGTH_SHORT).show()
            }
            onAddToQueue = { track ->
                audioEngine.addToQueue(track)
                android.widget.Toast.makeText(requireContext(), getString(R.string.toast_added_to_queue, track.title), android.widget.Toast.LENGTH_SHORT).show()
            }
            onAddToMixtape = { track ->
                MixtapeDialogs.showAddToMixtapeDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope, track) {
                    if (currentTab == 6) {
                        loadMixtapes(app)
                    }
                }
            }
            onInspectTags = { track ->
                TagInspectorDialog.show(requireContext(), track) {
                    loadTracks(app)
                }
            }
            onViewAudioSpecs = { track ->
                DialogFileSpecs(track).show(parentFragmentManager, "DialogFileSpecs")
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
                if (currentAlbumItem?.isMixtape != true) return false

                val mutable = currentAlbumTracks.toMutableList()
                val item = mutable.removeAt(fromPos)
                mutable.add(toPos, item)
                currentAlbumTracks = mutable
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
                if (currentAlbumItem?.isMixtape == true) {
                    val playlistId = currentAlbumItem?.year?.toLong() ?: return
                    val listToSave = currentAlbumTracks
                    viewLifecycleOwner.lifecycleScope.launch {
                        app.database.playlistDao().reorderPlaylist(playlistId, listToSave.map { it.id })
                    }
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || currentAlbumItem?.isMixtape != true) {
                    albumTracksAdapter.notifyItemChanged(pos)
                    return
                }
                val trackToRemove = currentAlbumTracks[pos]
                val playlistId = currentAlbumItem?.year?.toLong() ?: return
                val mutable = currentAlbumTracks.toMutableList()
                mutable.removeAt(pos)
                currentAlbumTracks = mutable
                albumTracksAdapter.submitList(mutable)

                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.playlistDao().removeTrackFromPlaylist(playlistId, trackToRemove.id)
                    app.database.playlistDao().reorderPlaylist(playlistId, mutable.map { it.id })
                    android.widget.Toast.makeText(requireContext(), getString(R.string.toast_removed_from_mixtape, trackToRemove.title), android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            override fun isLongPressDragEnabled(): Boolean {
                return currentAlbumItem?.isMixtape == true
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return currentAlbumItem?.isMixtape == true
            }
        }
        val helper = androidx.recyclerview.widget.ItemTouchHelper(reorderCallback)
        mixtapeReorderHelper = helper
        helper.attachToRecyclerView(binding.rvAlbumTracks)

        albumTracksAdapter = TrackAdapter(
            imageLoader = app.imageLoader,
            onTrackClicked = { track, index ->
                currentDisplayedTracks = currentAlbumTracks
                audioEngine.playQueue(currentAlbumTracks, index)
                showNowPlayingSingleAudio(true)
            },
            onRatingChanged = { track, newRating ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.trackDao().updateRating(track.id, newRating)
                }
            }
        ).apply {
            showTrackNumbers = true
            onPlayNext = { track ->
                audioEngine.playNextInQueue(track)
                android.widget.Toast.makeText(requireContext(), getString(R.string.toast_will_play_next, track.title), android.widget.Toast.LENGTH_SHORT).show()
            }
            onAddToQueue = { track ->
                audioEngine.addToQueue(track)
                android.widget.Toast.makeText(requireContext(), getString(R.string.toast_added_to_queue, track.title), android.widget.Toast.LENGTH_SHORT).show()
            }
            onAddToMixtape = { track ->
                MixtapeDialogs.showAddToMixtapeDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope, track) {
                    if (currentTab == 6) {
                        loadMixtapes(app)
                    }
                }
            }
            onInspectTags = { track ->
                TagInspectorDialog.show(requireContext(), track) {
                    loadTracks(app)
                }
            }
            onViewAudioSpecs = { track ->
                DialogFileSpecs(track).show(parentFragmentManager, "DialogFileSpecs")
            }
            onStartDrag = { holder ->
                if (currentAlbumItem?.isMixtape == true) {
                    mixtapeReorderHelper?.startDrag(holder)
                }
            }
            onRemoveFromMixtape = { track, _ ->
                if (currentAlbumItem?.isMixtape == true) {
                    val playlistId = currentAlbumItem?.year?.toLong() ?: 0L
                    if (playlistId > 0) {
                        val mutable = currentAlbumTracks.toMutableList()
                        mutable.remove(track)
                        currentAlbumTracks = mutable
                        albumTracksAdapter.submitList(mutable)
                        viewLifecycleOwner.lifecycleScope.launch {
                            app.database.playlistDao().removeTrackFromPlaylist(playlistId, track.id)
                            app.database.playlistDao().reorderPlaylist(playlistId, mutable.map { it.id })
                            android.widget.Toast.makeText(requireContext(), getString(R.string.toast_removed_from_mixtape, track.title), android.widget.Toast.LENGTH_SHORT).show()
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
                    getTracksFlowForAlbum(app, album).collectLatest { albumTracks ->
                        showAlbumDetail(album, albumTracks)
                    }
                }
            },
            onPlayAlbumClicked = { album ->
                albumTracksJob?.cancel()
                albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                    getTracksFlowForAlbum(app, album).collectLatest { albumTracks ->
                        if (albumTracks.isNotEmpty()) {
                            currentDisplayedTracks = albumTracks
                            audioEngine.playQueue(albumTracks, 0)
                            showNowPlayingSingleAudio(true)
                        }
                    }
                }
            }
        ).apply {
            onAlbumLongClicked = { album ->
                val isPinned = isAlbumPinned(album)
                val pinOption = if (isPinned) "Unpin from Start Screen" else "Pin to Start Screen"
                if (album.isMixtape) {
                    val options = arrayOf(pinOption, "Play Mixtape", "Export to .M3U", "Delete Mixtape")
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle(album.album)
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> {
                                    val pinnedNow = togglePinAlbum(album)
                                    val msg = if (pinnedNow) "Pinned '${album.album}' to Start Screen" else "Unpinned '${album.album}' from Start Screen"
                                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                }
                                1 -> {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        val tracks = app.database.playlistDao().getTracksForPlaylist(album.year.toLong()).first()
                                        if (tracks.isNotEmpty()) {
                                            currentDisplayedTracks = tracks
                                            audioEngine.playQueue(tracks, 0)
                                            showNowPlayingSingleAudio(true)
                                        }
                                    }
                                }
                                2 -> {
                                    MixtapeDialogs.exportMixtape(
                                        requireContext(),
                                        app.database,
                                        viewLifecycleOwner.lifecycleScope,
                                        album.year.toLong(),
                                        album.album
                                    )
                                }
                                3 -> {
                                    android.app.AlertDialog.Builder(requireContext())
                                        .setTitle("Delete Mixtape")
                                        .setMessage("Are you sure you want to delete '${album.album}'? (Music files will not be deleted)")
                                        .setPositiveButton("Delete") { _, _ ->
                                            viewLifecycleOwner.lifecycleScope.launch {
                                                app.database.playlistDao().deletePlaylist(album.year.toLong())
                                                android.widget.Toast.makeText(requireContext(), getString(R.string.toast_deleted_mixtape, album.album), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .setNegativeButton(R.string.action_cancel, null)
                                        .show()
                                }
                            }
                        }
                        .show()
                } else if (album.format == "SMART_MIXTAPE") {
                    val options = arrayOf(pinOption, "Play Smart Tape", "Export to .M3U")
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle(album.album)
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> {
                                    val pinnedNow = togglePinAlbum(album)
                                    val msg = if (pinnedNow) "Pinned '${album.album}' to Start Screen" else "Unpinned '${album.album}' from Start Screen"
                                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                }
                                1 -> {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        val tracks = getTracksFlowForAlbum(app, album).first()
                                        if (tracks.isNotEmpty()) {
                                            currentDisplayedTracks = tracks
                                            audioEngine.playQueue(tracks, 0)
                                            showNowPlayingSingleAudio(true)
                                        }
                                    }
                                }
                                2 -> {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        val tracks = getTracksFlowForAlbum(app, album).first()
                                        MixtapeDialogs.exportSmartMixtape(
                                            requireContext(),
                                            viewLifecycleOwner.lifecycleScope,
                                            album.album,
                                            tracks
                                        )
                                    }
                                }
                            }
                        }
                        .show()
                } else {
                    val options = arrayOf(pinOption, "Play Album", "View Details")
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle(album.album)
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> {
                                    val pinnedNow = togglePinAlbum(album)
                                    val msg = if (pinnedNow) "Pinned '${album.album}' to Start Screen" else "Unpinned '${album.album}' from Start Screen"
                                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                }
                                1 -> {
                                    albumTracksJob?.cancel()
                                    albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                                        getTracksFlowForAlbum(app, album).collectLatest { albumTracks ->
                                            if (albumTracks.isNotEmpty()) {
                                                currentDisplayedTracks = albumTracks
                                                audioEngine.playQueue(albumTracks, 0)
                                                showNowPlayingSingleAudio(true)
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    albumTracksJob?.cancel()
                                    albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                                        getTracksFlowForAlbum(app, album).collectLatest { albumTracks ->
                                            showAlbumDetail(album, albumTracks)
                                        }
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
                val folderTracks = allTracksList.filter { File(it.path).parent == folder.path }
                if (folderTracks.isNotEmpty()) {
                    currentDisplayedTracks = folderTracks
                    audioEngine.playQueue(folderTracks, 0)
                    showNowPlayingSingleAudio(true)
                }
            }
        }

        // Initialize 7 Panorama Collection Adapters
        tracksAdapter = TrackAdapter(
            imageLoader = app.imageLoader,
            onTrackClicked = { track, index ->
                currentDisplayedTracks = allTracksList
                audioEngine.playQueue(allTracksList, index)
                showNowPlayingSingleAudio(true)
            },
            onRatingChanged = { track, newRating ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.trackDao().updateRating(track.id, newRating)
                }
            }
        ).apply {
            onPlayNext = { track ->
                audioEngine.playNextInQueue(track)
                android.widget.Toast.makeText(requireContext(), getString(R.string.toast_will_play_next, track.title), android.widget.Toast.LENGTH_SHORT).show()
            }
            onAddToQueue = { track ->
                audioEngine.addToQueue(track)
                android.widget.Toast.makeText(requireContext(), getString(R.string.toast_added_to_queue, track.title), android.widget.Toast.LENGTH_SHORT).show()
            }
            onAddToMixtape = { track ->
                MixtapeDialogs.showAddToMixtapeDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope, track) {
                    loadMixtapes(app)
                }
            }
            onInspectTags = { track ->
                TagInspectorDialog.show(requireContext(), track) {
                    loadTracks(app)
                }
            }
            onViewAudioSpecs = { track ->
                DialogFileSpecs(track).show(parentFragmentManager, "DialogFileSpecs")
            }
        }

        val onAlbumClickAction: (AlbumItem) -> Unit = { album ->
            albumTracksJob?.cancel()
            albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                getTracksFlowForAlbum(app, album).collectLatest { albumTracks ->
                    showAlbumDetail(album, albumTracks)
                }
            }
        }

        val onAlbumPlayAction: (AlbumItem) -> Unit = { album ->
            albumTracksJob?.cancel()
            albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                getTracksFlowForAlbum(app, album).collectLatest { albumTracks ->
                    if (albumTracks.isNotEmpty()) {
                        currentDisplayedTracks = albumTracks
                        audioEngine.playQueue(albumTracks, 0)
                        showNowPlayingSingleAudio(true)
                    }
                }
            }
        }

        albumsAdapter = AlbumAdapter(
            imageLoader = app.imageLoader,
            onAlbumClicked = onAlbumClickAction,
            onPlayAlbumClicked = onAlbumPlayAction
        ).apply {
            onAlbumLongClicked = albumAdapter.onAlbumLongClicked
        }

        artistsAdapter = AlbumAdapter(
            imageLoader = app.imageLoader,
            onAlbumClicked = onAlbumClickAction,
            onPlayAlbumClicked = onAlbumPlayAction
        ).apply {
            onAlbumLongClicked = albumAdapter.onAlbumLongClicked
        }

        mixtapesAdapter = AlbumAdapter(
            imageLoader = app.imageLoader,
            onAlbumClicked = onAlbumClickAction,
            onPlayAlbumClicked = onAlbumPlayAction
        ).apply {
            onAlbumLongClicked = albumAdapter.onAlbumLongClicked
        }

        foldersAdapter = FolderAdapter { folder ->
            viewLifecycleOwner.lifecycleScope.launch {
                val folderTracks = allTracksList.filter { File(it.path).parent == folder.path }
                if (folderTracks.isNotEmpty()) {
                    val albumItem = AlbumItem(
                        album = folder.name,
                        artist = folder.path,
                        trackCount = folderTracks.size,
                        representativePath = folderTracks.firstOrNull()?.path ?: "",
                        year = 0,
                        format = "FOLDER"
                    )
                    showAlbumDetail(albumItem, folderTracks)
                }
            }
        }

        favoritesAdapter = TrackAdapter(
            imageLoader = app.imageLoader,
            onTrackClicked = { track, index ->
                val favs = allTracksList.filter { it.isFavorite }
                currentDisplayedTracks = favs
                audioEngine.playQueue(favs, index)
                showNowPlayingSingleAudio(true)
            },
            onRatingChanged = { track, newRating ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.database.trackDao().updateRating(track.id, newRating)
                }
            }
        )

        composersAdapter = AlbumAdapter(
            imageLoader = app.imageLoader,
            onAlbumClicked = onAlbumClickAction,
            onPlayAlbumClicked = onAlbumPlayAction
        )

        binding.rvCatalog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCatalog.adapter = tracksListAdapter

        searchSuggestionAdapter = SearchSuggestionAdapter { suggestion ->
            binding.rvSearchSuggestions.visibility = View.GONE
            binding.etCatalogSearch.clearFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(binding.etCatalogSearch.windowToken, 0)

            if (suggestion.track != null) {
                currentDisplayedTracks = listOf(suggestion.track)
                audioEngine.playQueue(listOf(suggestion.track), 0)
                showNowPlayingSingleAudio(true)
            } else if (suggestion.album != null) {
                val album = suggestion.album
                albumTracksJob?.cancel()
                albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                    getTracksFlowForAlbum(app, album).collectLatest { albumTracks ->
                        showAlbumDetail(album, albumTracks)
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
            if (binding.nowPlayingSingleContainer.visibility == View.VISIBLE) {
                showNowPlayingSingleAudio(false)
            } else if (binding.albumDetailContainer.visibility == View.VISIBLE) {
                hideAlbumDetail()
            } else if (!isPanoramaVisible) {
                showPanorama(true)
            } else {
                (activity as? MainActivity)?.navigateToPlayer()
            }
        }

        binding.tabSongs.setOnClickListener { scrollToCatalogPanel(0) }
        binding.tabAlbums.setOnClickListener { scrollToCatalogPanel(1) }
        binding.tabArtists.setOnClickListener { scrollToCatalogPanel(2) }
        binding.tabMixtapes.setOnClickListener { scrollToCatalogPanel(3) }
        binding.tabFolders.setOnClickListener { scrollToCatalogPanel(4) }
        binding.tabFavorites.setOnClickListener { scrollToCatalogPanel(5) }
        binding.tabComposers.setOnClickListener { scrollToCatalogPanel(6) }
    }

    fun scrollToCatalogPanel(index: Int) {
        currentCatalogPanelIndex = index.coerceIn(0, 6)
        val cBinding = _binding?.layoutCatalogCollectionPanorama ?: return
        val dm = resources.displayMetrics
        val panelWidth = dm.widthPixels - (52 * dm.density).toInt()
        val targetX = currentCatalogPanelIndex * panelWidth
        cBinding.catalogPanoramaScrollView.post {
            cBinding.catalogPanoramaScrollView.smoothScrollTo(targetX, 0)
        }
        highlightCatalogTab(currentCatalogPanelIndex)
    }

    private fun highlightCatalogTab(index: Int) {
        currentTab = index
        hideAlbumDetail()
        val app = requireActivity().application as? SpindleApp
        val theme = app?.themeManager?.currentTheme?.value
        val isEink = (theme?.id == CassetteTheme.MONOCHROME_EINK.id)

        val tabs = listOf(
            binding.tabSongs,
            binding.tabAlbums,
            binding.tabArtists,
            binding.tabMixtapes,
            binding.tabFolders,
            binding.tabFavorites,
            binding.tabComposers
        )

        tabs.forEachIndexed { i, tv ->
            val isActive = (i == index)
            tv.alpha = if (isActive) 1.0f else 0.40f
            tv.setTextColor(if (isActive) (if (isEink) Color.BLACK else Color.WHITE) else (if (isEink) Color.GRAY else Color.WHITE))
            tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            tv.textSize = if (isActive) 20f else 18f
        }

        val targetTab = tabs.getOrNull(index) ?: return
        binding.tabScrollView.post {
            val scrollX = targetTab.left - (binding.tabScrollView.width - targetTab.width) / 2
            binding.tabScrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    private fun switchToTab(index: Int) {
        scrollToCatalogPanel(index)
    }

    private fun selectTab(index: Int) {
        scrollToCatalogPanel(index)
    }

    private fun setupTouchDisambiguation(rv: RecyclerView, hsv: HorizontalScrollView) {
        var downX = 0f
        var downY = 0f
        val touchSlop = ViewConfiguration.get(rv.context).scaledTouchSlop

        rv.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(targetRv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.x
                        downY = e.y
                        hsv.requestDisallowInterceptTouchEvent(false)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = kotlin.math.abs(e.x - downX)
                        val dy = kotlin.math.abs(e.y - downY)
                        if (dy > touchSlop && dy > dx) {
                            targetRv.parent?.requestDisallowInterceptTouchEvent(true)
                        } else if (dx > touchSlop && dx > dy) {
                            targetRv.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        targetRv.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                return false
            }
        })
    }

    private fun setupCollectionPanorama(app: SpindleApp) {
        val cBinding = _binding?.layoutCatalogCollectionPanorama ?: return
        val dm = resources.displayMetrics
        val panelWidth = dm.widthPixels - (52 * dm.density).toInt()

        val panels = listOf(
            cBinding.panelTracks,
            cBinding.panelAlbums,
            cBinding.panelArtists,
            cBinding.panelMixtapes,
            cBinding.panelFolders,
            cBinding.panelFavorites,
            cBinding.panelComposers
        )
        panels.forEach { panel ->
            val lp = panel.layoutParams
            lp.width = panelWidth
            panel.layoutParams = lp
        }

        // Layout managers
        cBinding.rvPanoramaTracks.layoutManager = LinearLayoutManager(requireContext())
        cBinding.rvPanoramaAlbums.layoutManager = GridLayoutManager(requireContext(), 2)
        cBinding.rvPanoramaArtists.layoutManager = GridLayoutManager(requireContext(), 2)
        cBinding.rvPanoramaMixtapes.layoutManager = GridLayoutManager(requireContext(), 2)
        cBinding.rvPanoramaFolders.layoutManager = LinearLayoutManager(requireContext())
        cBinding.rvPanoramaFavorites.layoutManager = LinearLayoutManager(requireContext())
        cBinding.rvPanoramaComposers.layoutManager = GridLayoutManager(requireContext(), 2)

        // Adapters
        cBinding.rvPanoramaTracks.adapter = tracksAdapter
        cBinding.rvPanoramaAlbums.adapter = albumsAdapter
        cBinding.rvPanoramaArtists.adapter = artistsAdapter
        cBinding.rvPanoramaMixtapes.adapter = mixtapesAdapter
        cBinding.rvPanoramaFolders.adapter = foldersAdapter
        cBinding.rvPanoramaFavorites.adapter = favoritesAdapter
        cBinding.rvPanoramaComposers.adapter = composersAdapter

        // Touch disambiguation
        val listRecyclers = listOf(
            cBinding.rvPanoramaTracks,
            cBinding.rvPanoramaAlbums,
            cBinding.rvPanoramaArtists,
            cBinding.rvPanoramaMixtapes,
            cBinding.rvPanoramaFolders,
            cBinding.rvPanoramaFavorites,
            cBinding.rvPanoramaComposers
        )
        listRecyclers.forEach { rv ->
            setupTouchDisambiguation(rv, cBinding.catalogPanoramaScrollView)
        }

        // Parallax and active panel tracking
        cBinding.catalogPanoramaScrollView.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            cBinding.tvCatalogParallaxTitle.translationX = -scrollX * 0.22f
            val activeIdx = ((scrollX + panelWidth / 2) / panelWidth).coerceIn(0, 6)
            if (activeIdx != currentCatalogPanelIndex) {
                currentCatalogPanelIndex = activeIdx
                highlightCatalogTab(activeIdx)
            }
        }

        // Action buttons
        cBinding.btnShuffleTracks.setOnClickListener {
            if (allTracksList.isNotEmpty()) {
                val shuffled = allTracksList.shuffled()
                currentDisplayedTracks = shuffled
                audioEngine.playQueue(shuffled, 0)
                showNowPlayingSingleAudio(true)
            }
        }
        cBinding.btnPlayTracks.setOnClickListener {
            if (allTracksList.isNotEmpty()) {
                currentDisplayedTracks = allTracksList
                audioEngine.playQueue(allTracksList, 0)
                showNowPlayingSingleAudio(true)
            }
        }
        cBinding.btnSortTracks.setOnClickListener {
            val dialog = SortGroupBottomSheet(
                isAlbumTab = false,
                currentTrackSort = trackSortOrder,
                currentAlbumSort = albumSortOrder,
                currentGroupBy = groupByMode,
                onTrackSortSelected = { newSort ->
                    trackSortOrder = newSort
                    applyFilterAndSort()
                },
                onAlbumSortSelected = { newSort ->
                    albumSortOrder = newSort
                    applyFilterAndSort()
                },
                onGroupBySelected = { newGroup ->
                    groupByMode = newGroup
                    applyFilterAndSort()
                }
            )
            dialog.show(parentFragmentManager, "SortTracksDialog")
        }
        cBinding.btnSortAlbums.setOnClickListener {
            val dialog = SortGroupBottomSheet(
                isAlbumTab = true,
                currentTrackSort = trackSortOrder,
                currentAlbumSort = albumSortOrder,
                currentGroupBy = groupByMode,
                onTrackSortSelected = { newSort ->
                    trackSortOrder = newSort
                    applyFilterAndSort()
                },
                onAlbumSortSelected = { newSort ->
                    albumSortOrder = newSort
                    applyFilterAndSort()
                },
                onGroupBySelected = { newGroup ->
                    groupByMode = newGroup
                    applyFilterAndSort()
                }
            )
            dialog.show(parentFragmentManager, "SortAlbumsDialog")
        }
        cBinding.btnNewMixtape.setOnClickListener {
            MixtapeDialogs.showCreateMixtapeDialog(requireContext(), app.database, viewLifecycleOwner.lifecycleScope) {
                loadMixtapes(app)
            }
        }

        // Alphabet index & Zune Quick-Jump on panorama tracks panel
        cBinding.btnJumpTracks.setOnClickListener {
            openZuneJumpList()
        }
        cBinding.tvLetterPreviewTracks.setOnClickListener {
            openZuneJumpList()
        }
        cBinding.alphabetIndexTracks.setOnLongClickListener {
            openZuneJumpList()
            true
        }
        cBinding.alphabetIndexTracks.onLetterSelected = { letter ->
            cBinding.tvLetterPreviewTracks.text = letter
            cBinding.tvLetterPreviewTracks.visibility = View.VISIBLE
            cBinding.tvLetterPreviewTracks.removeCallbacks(hideLetterPreviewRunnable)
            cBinding.tvLetterPreviewTracks.postDelayed(hideLetterPreviewRunnable, 800L)
            scrollToLetter(letter)
        }
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
        binding.tvMetroHeroTitle.setTextColor(primary)
        binding.layoutPanorama.tvPanoramaParallaxTitle.setTextColor(primary)
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
        currentAlbumItem?.let { updateAlbumDetailPinButton(it) }
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
        binding.tvNpArtistHeader.setTextColor(primary)
        binding.tvNpAlbumHeader.setTextColor(if (isEink) primary else ContextCompat.getColor(requireContext(), R.color.brand_orange))
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
        binding.tvNpLyricsLabel.setTextColor(secondary)
        binding.tvNpRepeatLabel.setTextColor(secondary)
        binding.tvNpShuffleLabel.setTextColor(secondary)
        binding.tvNpQueueLabel.setTextColor(secondary)

        binding.circularCoverArcView.isDarkMode = isDark
        binding.circularCoverArcView.isEink = isEink
        binding.circularCoverArcView.accentColor = accent

        binding.audioWaveformView.isDarkMode = isDark
        binding.audioWaveformView.isEink = isEink
        binding.audioWaveformView.accentColor = accent

        binding.catalogAlphabetIndex.updateTheme(secondary, accent)

        // Propagate to Adapters
        tracksListAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        albumAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        if (::albumTracksAdapter.isInitialized) {
            albumTracksAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        }
        folderAdapter.updateThemeColors(primary, secondary)
        if (::searchSuggestionAdapter.isInitialized) {
            searchSuggestionAdapter.updateThemeColors(primary, secondary, accent, isEink)
        }
        if (::tracksAdapter.isInitialized) tracksAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        if (::albumsAdapter.isInitialized) albumsAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        if (::artistsAdapter.isInitialized) artistsAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        if (::mixtapesAdapter.isInitialized) mixtapesAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        if (::foldersAdapter.isInitialized) foldersAdapter.updateThemeColors(primary, secondary)
        if (::favoritesAdapter.isInitialized) favoritesAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        if (::composersAdapter.isInitialized) composersAdapter.updateThemeColors(primary, secondary, isDark, isEink)
        _binding?.layoutCatalogCollectionPanorama?.alphabetIndexTracks?.updateTheme(secondary, accent)
        _binding?.layoutCatalogCollectionPanorama?.tvCatalogParallaxTitle?.setTextColor(primary)

        // Update active tab buttons visual
        highlightCatalogTab(currentCatalogPanelIndex)
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
        binding.btnCatalogJump.setOnClickListener {
            openZuneJumpList()
        }
        binding.tvCatalogLetterPreview.setOnClickListener {
            openZuneJumpList()
        }
        binding.catalogAlphabetIndex.setOnLongClickListener {
            openZuneJumpList()
            true
        }
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
        _binding?.layoutCatalogCollectionPanorama?.tvLetterPreviewTracks?.visibility = View.GONE
    }

    private fun openZuneJumpList() {
        val availableLetters: Set<Char> = when (currentTab) {
            0, 5 -> {
                currentDisplayedTracks.map { track ->
                    val ch = track.title.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (ch in 'A'..'Z') ch else '#'
                }.toSet()
            }
            1, 2, 3, 6 -> {
                currentDisplayedAlbums.map { album ->
                    val ch = album.album.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (ch in 'A'..'Z') ch else '#'
                }.toSet()
            }
            4 -> {
                allFoldersList.map { folder ->
                    val ch = folder.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (ch in 'A'..'Z') ch else '#'
                }.toSet()
            }
            else -> emptySet()
        }

        val appInstance = requireActivity().application as SpindleApp
        val theme = appInstance.themeManager.currentTheme.value
        val title = when (currentTab) {
            0 -> "tracks"
            1 -> "albums"
            2 -> "artists"
            3 -> "composers"
            4 -> "folders"
            5 -> "favorites"
            6 -> "mixtapes"
            else -> "music"
        }

        appInstance.audioEngine.foleyEngine.playMetroTick(1.1f)
        ZuneJumpListDialog.show(
            fragmentManager = parentFragmentManager,
            availableLetters = availableLetters,
            title = title,
            subtitle = "jump directly in $title",
            accentColor = theme.accentColor
        ) { letter ->
            scrollToLetter(letter)
        }
    }

    private fun scrollToLetter(letter: String) {
        val isTop = (letter == "TOP" || letter == "↑")
        (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playMetroTick(if (isTop) 1.4f else 1.0f)
        val targetChar = if (isTop) ' ' else (letter.firstOrNull()?.uppercaseChar() ?: return)
        val lm = binding.rvCatalog.layoutManager
        val panoramaLm = _binding?.layoutCatalogCollectionPanorama?.rvPanoramaTracks?.layoutManager as? LinearLayoutManager

        if (isTop) {
            (lm as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
            (lm as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
            panoramaLm?.scrollToPositionWithOffset(0, 0)
            return
        }

        when (currentTab) {
            0, 5 -> {
                val index = currentDisplayedTracks.indexOfFirst {
                    val firstChar = it.title.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (targetChar == '#') firstChar !in 'A'..'Z' else firstChar == targetChar
                }
                if (index >= 0) {
                    (lm as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
                    panoramaLm?.scrollToPositionWithOffset(index, 0)
                }
            }
            1, 2, 3, 6 -> {
                val index = currentDisplayedAlbums.indexOfFirst {
                    val firstChar = it.album.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (targetChar == '#') firstChar !in 'A'..'Z' else firstChar == targetChar
                }
                if (index >= 0) {
                    (lm as? GridLayoutManager)?.scrollToPositionWithOffset(index, 0)
                }
            }
            4 -> {
                val index = allFoldersList.indexOfFirst {
                    val firstChar = it.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
                    if (targetChar == '#') firstChar !in 'A'..'Z' else firstChar == targetChar
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
            if (currentDisplayedTracks.isNotEmpty()) {
                audioEngine.setShuffleMode(com.hana.spindle.playback.ShuffleMode.ALL)
                audioEngine.playQueue(currentDisplayedTracks, 0)
                (activity as? MainActivity)?.navigateToPlayer()
            }
        }

        binding.btnPlayAll.setOnClickListener {
            if (currentDisplayedTracks.isNotEmpty()) {
                audioEngine.setShuffleMode(com.hana.spindle.playback.ShuffleMode.OFF)
                audioEngine.playQueue(currentDisplayedTracks, 0)
                (activity as? MainActivity)?.navigateToPlayer()
            }
        }
    }

    private fun setupSearch() {
        binding.etCatalogSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && isPanoramaVisible) {
                showPanorama(false)
                switchToTab(0)
            }
        }

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
                val fallbackTracks = allTracksList.filter { track ->
                    tokens.all { t ->
                        track.title.contains(t, ignoreCase = true) ||
                        track.artist.contains(t, ignoreCase = true) ||
                        track.album.contains(t, ignoreCase = true) ||
                        track.composer?.contains(t, ignoreCase = true) == true
                    }
                }.take(4)
                for (track in fallbackTracks) {
                    suggestions.add(
                        SearchSuggestion(
                            title = track.title,
                            subtitle = "${track.artist} • ${track.fileFormat}",
                            type = SuggestionType.TRACK,
                            track = track
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
                var list = if (currentTab == 5) allFavoritesList else allTracksList

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

                currentDisplayedTracks = list
                tracksListAdapter.submitList(list)
                if (currentTab == 0 && ::tracksAdapter.isInitialized) {
                    tracksAdapter.submitList(list)
                    _binding?.layoutCatalogCollectionPanorama?.tvTracksCount?.text = "${list.size} tracks"
                } else if (currentTab == 5 && ::favoritesAdapter.isInitialized) {
                    favoritesAdapter.submitList(list)
                    _binding?.layoutCatalogCollectionPanorama?.tvFavoritesCount?.text = "${list.size} starred tracks"
                }
                binding.tvCatalogCount.text = "${list.size} tracks"
            }
            1, 2, 3, 6 -> {
                var list = when (currentTab) {
                    1 -> allAlbumsList
                    2 -> allArtistsList
                    3 -> allMixtapesList
                    6 -> allComposersList
                    else -> allAlbumsList
                }

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
                list = if (currentTab == 3 && query.isEmpty()) {
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
                when (currentTab) {
                    1 -> {
                        if (::albumsAdapter.isInitialized) albumsAdapter.submitList(list)
                        _binding?.layoutCatalogCollectionPanorama?.tvAlbumsCount?.text = "${list.size} albums"
                    }
                    2 -> {
                        if (::artistsAdapter.isInitialized) artistsAdapter.submitList(list)
                        _binding?.layoutCatalogCollectionPanorama?.tvArtistsCount?.text = "${list.size} artists"
                    }
                    3 -> {
                        if (::mixtapesAdapter.isInitialized) mixtapesAdapter.submitList(list)
                        _binding?.layoutCatalogCollectionPanorama?.tvMixtapesCount?.text = "${list.size} mixtapes"
                    }
                    6 -> {
                        if (::composersAdapter.isInitialized) composersAdapter.submitList(list)
                        _binding?.layoutCatalogCollectionPanorama?.tvComposersCount?.text = "${list.size} composers"
                    }
                }
                val unitLabel = when (currentTab) {
                    3 -> "mixtapes"
                    6 -> "composers"
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
                if (::foldersAdapter.isInitialized) {
                    foldersAdapter.submitList(list)
                    _binding?.layoutCatalogCollectionPanorama?.tvFoldersCount?.text = "${list.size} storage folders"
                }
                binding.tvCatalogCount.text = "${list.size} folders"
            }
        }

        updateAllPanoramaPanels(query)
    }

    private fun updateAllPanoramaPanels(query: String) {
        val cBinding = _binding?.layoutCatalogCollectionPanorama ?: return
        val tokens = if (query.isNotEmpty()) query.split(Regex("\\s+")).filter { it.isNotEmpty() } else emptyList()

        // Panel 0: Tracks
        if (::tracksAdapter.isInitialized) {
            var tList = allTracksList
            if (tokens.isNotEmpty()) {
                tList = tList.filter { track ->
                    tokens.all { t ->
                        track.title.contains(t, ignoreCase = true) ||
                        track.artist.contains(t, ignoreCase = true) ||
                        track.album.contains(t, ignoreCase = true) ||
                        track.composer?.contains(t, ignoreCase = true) == true
                    }
                }
            }
            tList = when (trackSortOrder) {
                TrackSortOrder.TITLE_ASC -> tList.sortedBy { it.title.lowercase(Locale.ROOT) }
                TrackSortOrder.TITLE_DESC -> tList.sortedByDescending { it.title.lowercase(Locale.ROOT) }
                TrackSortOrder.ARTIST_ASC -> tList.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.title.lowercase(Locale.ROOT) }))
                TrackSortOrder.ALBUM_ASC -> tList.sortedWith(compareBy({ it.album.lowercase(Locale.ROOT) }, { it.trackNumber }))
                TrackSortOrder.TRACK_NUMBER -> tList.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
                TrackSortOrder.YEAR_DESC -> tList.sortedByDescending { it.year }
                TrackSortOrder.YEAR_ASC -> tList.sortedBy { it.year }
                TrackSortOrder.BITRATE_DESC -> tList.sortedByDescending { it.bitrateKbps }
                TrackSortOrder.DURATION_DESC -> tList.sortedByDescending { it.durationMs }
                TrackSortOrder.DATE_MODIFIED_DESC -> tList.sortedByDescending { it.dateModified }
                TrackSortOrder.PLAY_COUNT_DESC -> tList.sortedByDescending { it.playCount }
                TrackSortOrder.LAST_PLAYED_DESC -> tList.sortedByDescending { it.lastPlayedAt ?: 0L }
            }
            tracksAdapter.submitList(tList)
            cBinding.tvTracksCount.text = "${tList.size} tracks"
        }

        fun filterAndSortAlbumItems(source: List<AlbumItem>): List<AlbumItem> {
            var items = source
            if (tokens.isNotEmpty()) {
                items = items.filter { item ->
                    tokens.all { t ->
                        item.album.contains(t, ignoreCase = true) ||
                        item.artist.contains(t, ignoreCase = true)
                    }
                }
            }
            return when (albumSortOrder) {
                AlbumSortOrder.TITLE_ASC -> items.sortedBy { it.album.lowercase(Locale.ROOT) }
                AlbumSortOrder.TITLE_DESC -> items.sortedByDescending { it.album.lowercase(Locale.ROOT) }
                AlbumSortOrder.ARTIST_ASC -> items.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.album.lowercase(Locale.ROOT) }))
                AlbumSortOrder.YEAR_DESC -> items.sortedByDescending { it.year }
                AlbumSortOrder.YEAR_ASC -> items.sortedBy { it.year }
                AlbumSortOrder.TRACK_COUNT_DESC -> items.sortedByDescending { it.trackCount }
                AlbumSortOrder.TRACK_COUNT_ASC -> items.sortedBy { it.trackCount }
            }
        }

        // Panel 1: Albums
        if (::albumsAdapter.isInitialized) {
            val aList = filterAndSortAlbumItems(allAlbumsList)
            albumsAdapter.submitList(aList)
            cBinding.tvAlbumsCount.text = "${aList.size} albums"
        }

        // Panel 2: Artists
        if (::artistsAdapter.isInitialized) {
            val artList = filterAndSortAlbumItems(allArtistsList)
            artistsAdapter.submitList(artList)
            cBinding.tvArtistsCount.text = "${artList.size} artists"
        }

        // Panel 3: Mixtapes
        if (::mixtapesAdapter.isInitialized) {
            var mList = allMixtapesList
            if (tokens.isNotEmpty()) {
                mList = mList.filter { item ->
                    tokens.all { t ->
                        item.album.contains(t, ignoreCase = true) ||
                        item.artist.contains(t, ignoreCase = true)
                    }
                }
            }
            val smart = mList.filter { it.format == "SMART_MIXTAPE" }
            val custom = mList.filter { it.format != "SMART_MIXTAPE" }
            val sortedCustom = when (albumSortOrder) {
                AlbumSortOrder.TITLE_ASC -> custom.sortedBy { it.album.lowercase(Locale.ROOT) }
                AlbumSortOrder.TITLE_DESC -> custom.sortedByDescending { it.album.lowercase(Locale.ROOT) }
                AlbumSortOrder.ARTIST_ASC -> custom.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.album.lowercase(Locale.ROOT) }))
                AlbumSortOrder.YEAR_DESC -> custom.sortedByDescending { it.year }
                AlbumSortOrder.YEAR_ASC -> custom.sortedBy { it.year }
                AlbumSortOrder.TRACK_COUNT_DESC -> custom.sortedByDescending { it.trackCount }
                AlbumSortOrder.TRACK_COUNT_ASC -> custom.sortedBy { it.trackCount }
            }
            val finalMList = smart + sortedCustom
            mixtapesAdapter.submitList(finalMList)
            cBinding.tvMixtapesCount.text = "${finalMList.size} mixtapes"
        }

        // Panel 4: Folders
        if (::foldersAdapter.isInitialized) {
            var fList = allFoldersList
            if (tokens.isNotEmpty()) {
                fList = fList.filter { folder ->
                    tokens.all { t -> folder.name.contains(t, ignoreCase = true) }
                }
            }
            foldersAdapter.submitList(fList)
            cBinding.tvFoldersCount.text = "${fList.size} storage folders"
        }

        // Panel 5: Favorites
        if (::favoritesAdapter.isInitialized) {
            var favList = allFavoritesList
            if (tokens.isNotEmpty()) {
                favList = favList.filter { track ->
                    tokens.all { t ->
                        track.title.contains(t, ignoreCase = true) ||
                        track.artist.contains(t, ignoreCase = true) ||
                        track.album.contains(t, ignoreCase = true)
                    }
                }
            }
            favList = when (trackSortOrder) {
                TrackSortOrder.TITLE_ASC -> favList.sortedBy { it.title.lowercase(Locale.ROOT) }
                TrackSortOrder.TITLE_DESC -> favList.sortedByDescending { it.title.lowercase(Locale.ROOT) }
                TrackSortOrder.ARTIST_ASC -> favList.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.title.lowercase(Locale.ROOT) }))
                TrackSortOrder.ALBUM_ASC -> favList.sortedWith(compareBy({ it.album.lowercase(Locale.ROOT) }, { it.trackNumber }))
                TrackSortOrder.TRACK_NUMBER -> favList.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
                TrackSortOrder.YEAR_DESC -> favList.sortedByDescending { it.year }
                TrackSortOrder.YEAR_ASC -> favList.sortedBy { it.year }
                TrackSortOrder.BITRATE_DESC -> favList.sortedByDescending { it.bitrateKbps }
                TrackSortOrder.DURATION_DESC -> favList.sortedByDescending { it.durationMs }
                TrackSortOrder.DATE_MODIFIED_DESC -> favList.sortedByDescending { it.dateModified }
                TrackSortOrder.PLAY_COUNT_DESC -> favList.sortedByDescending { it.playCount }
                TrackSortOrder.LAST_PLAYED_DESC -> favList.sortedByDescending { it.lastPlayedAt ?: 0L }
            }
            favoritesAdapter.submitList(favList)
            cBinding.tvFavoritesCount.text = "${favList.size} starred tracks"
        }

        // Panel 6: Composers
        if (::composersAdapter.isInitialized) {
            val cList = filterAndSortAlbumItems(allComposersList)
            composersAdapter.submitList(cList)
            cBinding.tvComposersCount.text = "${cList.size} composers"
        }
    }

    private fun setupMiniPlayer(app: SpindleApp) {
        binding.cardMiniPlayer.setOnClickListener {
            if (audioEngine.playbackState.value.currentTrack != null) {
                showNowPlayingSingleAudio(true)
            }
        }

        binding.btnMiniPrev.setOnClickListener { audioEngine.playPrevious(forcePreviousSong = false) }
        binding.btnMiniPlayPause.setOnClickListener { audioEngine.togglePlayPause() }
        binding.btnMiniNext.setOnClickListener { audioEngine.playNext() }

        viewLifecycleOwner.lifecycleScope.launch {
            audioEngine.playbackState.collectLatest { state ->
                _binding?.let { b ->
                    state.currentTrack?.let { track ->
                        b.tvMiniTitle.text = track.title
                        b.tvMiniArtist.text = track.artist

                        val formatStr = "${track.fileFormat} ${track.bitDepth}/${track.sampleRate / 1000}k"
                        b.tvMiniFormat.visibility = View.VISIBLE
                        b.tvMiniFormat.text = formatStr

                        if (tracksListAdapter.activeTrackId != track.id) {
                            val oldActiveId = tracksListAdapter.activeTrackId
                            tracksListAdapter.activeTrackId = track.id
                            val oldPos = currentDisplayedTracks.indexOfFirst { it.id == oldActiveId }
                            val newPos = currentDisplayedTracks.indexOfFirst { it.id == track.id }
                            if (oldPos != -1) tracksListAdapter.notifyItemChanged(oldPos)
                            if (newPos != -1) tracksListAdapter.notifyItemChanged(newPos)
                        }
                        if (::albumTracksAdapter.isInitialized && albumTracksAdapter.activeTrackId != track.id) {
                            val oldActiveId = albumTracksAdapter.activeTrackId
                            albumTracksAdapter.activeTrackId = track.id
                            val oldPos = currentAlbumTracks.indexOfFirst { it.id == oldActiveId }
                            val newPos = currentAlbumTracks.indexOfFirst { it.id == track.id }
                            if (oldPos != -1) albumTracksAdapter.notifyItemChanged(oldPos)
                            if (newPos != -1) albumTracksAdapter.notifyItemChanged(newPos)
                        }

                        // Update Now Playing Single Audio metadata
                        b.tvNpTitle.text = track.title
                        b.tvNpArtist.text = track.artist
                        b.tvNpArtistHeader.text = track.artist.lowercase(Locale.ROOT)
                        b.tvNpAlbumHeader.text = if (track.year > 0) "${track.album.uppercase(Locale.ROOT)} (${track.year})" else track.album.uppercase(Locale.ROOT)
                        updateNowPlayingUpNextPreview()

                        if (track.id != currentLoadedTrackId) {
                            currentLoadedTrackId = track.id
                            updateFavoriteIcon(track.isFavorite)

                            // Load circular cover art, ambient backdrop, and extract accent color
                            viewLifecycleOwner.lifecycleScope.launch {
                                val cover = app.imageLoader.loadCover(track.path, 500, 500)
                                b.circularCoverArcView.coverBitmap = cover
                                b.ivNpAmbientArt.setImageBitmap(cover)
                                if (b.nowPlayingSingleContainer.visibility == View.VISIBLE) {
                                    startAmbientKenBurns()
                                }
                                val accent = app.imageLoader.extractAccentColor(track.path)
                                npLyricsAdapter.accentColor = accent
                                b.circularCoverArcView.accentColor = accent
                                b.audioWaveformView.accentColor = accent
                                b.btnNpPlayPause.backgroundTintList = ColorStateList.valueOf(accent)
                            }

                            // Extract real 64-bar song waveform amplitudes
                            waveformExtractionJob?.cancel()
                            waveformExtractionJob = viewLifecycleOwner.lifecycleScope.launch {
                                val wave = waveformExtractor.getWaveform(track.path, 64)
                                b.audioWaveformView.setWaveformData(wave)
                            }
                        }

                        // Synchronized lyrics
                        val lyrics = state.currentLyrics
                        if (lyrics != null && lyrics.lines.isNotEmpty()) {
                            npLyricsAdapter.lines = lyrics.lines
                            b.layoutNpEmptyLyrics.visibility = View.GONE
                            b.rvNpLyrics.visibility = View.VISIBLE
                        } else {
                            npLyricsAdapter.lines = emptyList()
                            b.layoutNpEmptyLyrics.visibility = View.VISIBLE
                            b.rvNpLyrics.visibility = View.GONE
                        }

                        // Load mini cover art only when track changes
                        if (track.id != currentMiniTrackId) {
                            currentMiniTrackId = track.id
                            viewLifecycleOwner.lifecycleScope.launch {
                                val isEink = app.themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id
                                val thumb = app.imageLoader.loadCover(track.path, 96, 96)
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

                    if (isPanoramaVisible) {
                        updatePanoramaPlayback(app, state)
                    }

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
        binding.layoutNpQueue.setOnClickListener { binding.btnNpQueue.performClick() }
        binding.layoutNpRepeat.setOnClickListener { binding.btnNpRepeat.performClick() }
        binding.layoutNpShuffle.setOnClickListener { binding.btnNpShuffle.performClick() }
        binding.layoutNpLyrics.setOnClickListener { binding.btnNpLyrics.performClick() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    audioEngine.currentQueueFlow.collectLatest {
                        updateNowPlayingUpNextPreview()
                        updatePanoramaUpNextPreview()
                    }
                }
                launch {
                    audioEngine.currentQueueIndexFlow.collectLatest {
                        updateNowPlayingUpNextPreview()
                        updatePanoramaUpNextPreview()
                    }
                }
            }
        }

        binding.btnNpPlayPause.setOnClickListener { audioEngine.togglePlayPause() }
        binding.btnNpPrev.setOnClickListener { audioEngine.playPrevious(forcePreviousSong = false) }
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
            val track = audioEngine.playbackState.value.currentTrack
            if (track != null) {
                DialogFileSpecs(track).show(parentFragmentManager, "DialogFileSpecs")
            }
        }
        binding.btnNpMenu.setOnClickListener(openSpecsAction)

        // Audiophile turntable vinyl swipe gestures
        binding.circularCoverArcView.onSwipeLeft = {
            audioEngine.playNext()
        }
        binding.circularCoverArcView.onSwipeRight = {
            audioEngine.playPrevious(forcePreviousSong = false)
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
            if (isShowingNpLyrics) {
                binding.btnNpLyrics.setBackgroundResource(R.drawable.bg_metro_circle_button_active)
                binding.btnNpLyrics.imageTintList = ColorStateList.valueOf(accent)
                binding.tvNpLyricsLabel.setTextColor(accent)
            } else {
                binding.btnNpLyrics.setBackgroundResource(R.drawable.bg_metro_circle_button)
                binding.btnNpLyrics.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                binding.tvNpLyricsLabel.setTextColor(Color.parseColor("#94A3B8"))
            }
        }
        binding.btnNpLyrics.setOnClickListener(toggleLyrics)
        binding.cardNpLyrics.setOnClickListener(toggleLyrics)
        binding.circularCoverArcView.onCoverClicked = {
            toggleLyrics.onClick(binding.circularCoverArcView)
        }

        binding.btnNpDownloadLyrics.setOnClickListener {
            val track = audioEngine.playbackState.value.currentTrack ?: return@setOnClickListener
            binding.progressNpLyrics.visibility = View.VISIBLE
            binding.btnNpDownloadLyrics.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val downloaded = app.lyricsFetcher.fetchLyricsForTrack(track, forceRefresh = true)
                binding.progressNpLyrics.visibility = View.GONE
                binding.btnNpDownloadLyrics.isEnabled = true
                if (downloaded != null && downloaded.lines.isNotEmpty()) {
                    audioEngine.reloadLyricsForCurrentTrack(forceDownload = false)
                    Toast.makeText(requireContext(), "Synchronized lyrics downloaded from LRCLIB!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "No lyrics found on LRCLIB for this track.", Toast.LENGTH_SHORT).show()
                }
            }
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
                binding.btnNpShuffle.setBackgroundResource(R.drawable.bg_metro_circle_button)
                binding.btnNpShuffle.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#94A3B8"))
                binding.tvNpShuffleLabel.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#94A3B8"))
            }
            ShuffleMode.ALL -> {
                binding.btnNpShuffle.setBackgroundResource(R.drawable.bg_metro_circle_button_active)
                binding.btnNpShuffle.imageTintList = ColorStateList.valueOf(accent)
                binding.tvNpShuffleLabel.setTextColor(accent)
            }
            ShuffleMode.ALBUM -> {
                binding.btnNpShuffle.setBackgroundResource(R.drawable.bg_metro_circle_button_active)
                binding.btnNpShuffle.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#FDE68A"))
                binding.tvNpShuffleLabel.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#FDE68A"))
            }
        }
    }

    private fun updateRepeatIcon(mode: RepeatMode) {
        val app = requireActivity().application as SpindleApp
        val isEink = app.themeManager.currentTheme.value.id == CassetteTheme.MONOCHROME_EINK.id
        val accent = if (isEink) Color.BLACK else app.themeManager.currentTheme.value.accentColor
        when (mode) {
            RepeatMode.OFF -> {
                binding.btnNpRepeat.setBackgroundResource(R.drawable.bg_metro_circle_button)
                binding.btnNpRepeat.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#94A3B8"))
                binding.tvNpRepeatLabel.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#94A3B8"))
            }
            RepeatMode.ALL -> {
                binding.btnNpRepeat.setBackgroundResource(R.drawable.bg_metro_circle_button_active)
                binding.btnNpRepeat.imageTintList = ColorStateList.valueOf(accent)
                binding.tvNpRepeatLabel.setTextColor(accent)
            }
            RepeatMode.ONE -> {
                binding.btnNpRepeat.setBackgroundResource(R.drawable.bg_metro_circle_button_active)
                binding.btnNpRepeat.imageTintList = ColorStateList.valueOf(if (isEink) Color.BLACK else Color.parseColor("#FB7185"))
                binding.tvNpRepeatLabel.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#FB7185"))
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

    private fun startAmbientKenBurns() {
        val iv = _binding?.ivNpAmbientArt ?: return
        iv.clearAnimation()
        iv.scaleX = 1.0f
        iv.scaleY = 1.0f
        iv.translationX = 0f
        iv.translationY = 0f
        iv.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .translationX(-16f)
            .translationY(-10f)
            .setDuration(20000L)
            .setInterpolator(android.view.animation.LinearInterpolator())
            .withEndAction {
                val b = _binding?.ivNpAmbientArt ?: return@withEndAction
                b.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationX(12f)
                    .translationY(8f)
                    .setDuration(20000L)
                    .setInterpolator(android.view.animation.LinearInterpolator())
                    .start()
            }
            .start()
    }

    private fun stopAmbientKenBurns() {
        _binding?.ivNpAmbientArt?.animate()?.cancel()
    }

    fun showNowPlayingSingleAudio(show: Boolean) {
        isNowPlayingSingleVisible = show
        if (show) {
            val playback = audioEngine.playbackState.value
            val track = playback.currentTrack
            if (track != null) {
                binding.tvNpTitle.text = track.title
                binding.tvNpArtist.text = track.artist
                binding.tvNpArtistHeader.text = track.artist.lowercase(Locale.ROOT)
                binding.tvNpAlbumHeader.text = if (track.year > 0) "${track.album.uppercase(Locale.ROOT)} (${track.year})" else track.album.uppercase(Locale.ROOT)
                val app = requireActivity().application as SpindleApp
                viewLifecycleOwner.lifecycleScope.launch {
                    val cover = app.imageLoader.loadCover(track.path, 500, 500)
                    _binding?.ivNpAmbientArt?.setImageBitmap(cover)
                }
            }
            startAmbientKenBurns()
            updateNowPlayingUpNextPreview()
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
            stopAmbientKenBurns()
            binding.nowPlayingSingleContainer.animate()
                .translationY(320f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    binding.nowPlayingSingleContainer.visibility = View.GONE
                    binding.cardNpLyrics.visibility = View.GONE
                    isShowingNpLyrics = false
                    binding.btnNpLyrics.setBackgroundResource(R.drawable.bg_metro_circle_button)
                    binding.btnNpLyrics.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                    binding.tvNpLyricsLabel.setTextColor(Color.parseColor("#94A3B8"))
                }.start()
        }
    }

    private fun updateNowPlayingUpNextPreview() {
        val b = _binding ?: return
        val queue = audioEngine.currentQueueFlow.value
        val currentIndex = audioEngine.currentQueueIndexFlow.value
        if (queue.isEmpty() || currentIndex < 0 || currentIndex >= queue.size) {
            b.layoutNpUpNext.visibility = View.GONE
            return
        }

        val upNextViews = listOf(b.tvNpNextTrack1, b.tvNpNextTrack2, b.tvNpNextTrack3)
        var visibleCount = 0

        for (i in 0 until 3) {
            val nextIdx = currentIndex + 1 + i
            val tv = upNextViews[i]
            if (nextIdx < queue.size) {
                val nextTrack = queue[nextIdx]
                val trackNumStr = if (nextTrack.trackNumber > 0) String.format(Locale.US, "%02d ", nextTrack.trackNumber) else ""
                tv.text = "$trackNumStr${nextTrack.title}"
                tv.visibility = View.VISIBLE
                tv.setOnClickListener {
                    audioEngine.playQueueIndex(nextIdx)
                }
                visibleCount++
            } else {
                tv.visibility = View.GONE
            }
        }
        b.layoutNpUpNext.visibility = if (visibleCount > 0) View.VISIBLE else View.GONE
    }

    fun showAlbumDetail(album: AlbumItem, tracks: List<TrackEntity>) {
        currentAlbumItem = album
        currentAlbumTracks = tracks

        val app = requireActivity().application as SpindleApp
        val theme = app.themeManager.currentTheme.value
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)

        binding.tvAlbumDetailHeaderTitle.text = when (album.format) {
            "DISCOGRAPHY" -> "ARTIST"
            "COMPOSER" -> "COMPOSER"
            "GENRE" -> "GENRE"
            AlbumItem.FORMAT_MIXTAPE -> "MIXTAPE"
            AlbumItem.FORMAT_SMART_MIXTAPE -> "SMART CASSETTE"
            else -> "ALBUM"
        }
        binding.tvAlbumDetailTitle.text = album.album
        binding.tvAlbumDetailArtist.text = album.artist

        val totalDurationMs = tracks.sumOf { it.durationMs }
        val durationFormatted = formatTime(totalDurationMs)
        val yearStr = if (album.year > 0 && !album.isMixtape) "${album.year} • " else ""

        val mixtapePartition = if (album.isMixtape && tracks.size > 1) {
            com.hana.spindle.data.MixtapePartition.partition(tracks)
        } else null

        if (mixtapePartition != null) {
            val sideADur = formatTime(mixtapePartition.sideADurationMs)
            val sideBDur = formatTime(mixtapePartition.sideBDurationMs)
            binding.tvAlbumDetailMeta.text = "$yearStr${tracks.size} Tracks • $durationFormatted\nSIDE A: ${mixtapePartition.sideA.size} trk ($sideADur) • SIDE B: ${mixtapePartition.sideB.size} trk ($sideBDur)"
            albumTracksAdapter.showSideLetters = true
            albumTracksAdapter.splitIndex = mixtapePartition.splitIndex
        } else {
            binding.tvAlbumDetailMeta.text = "$yearStr${tracks.size} Tracks • $durationFormatted"
            albumTracksAdapter.showSideLetters = false
            albumTracksAdapter.splitIndex = -1
        }

        if (album.format == AlbumItem.FORMAT_MIXTAPE) {
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
        } else if (album.isSmartMixtape) {
            albumTracksAdapter.isReorderable = false
            binding.tvAlbumDetailFormat.text = "SMART CASSETTE • AUTO-CURATED"
            binding.tvAlbumDetailFormat.setTextColor(Color.parseColor("#FDE68A"))
            binding.btnAlbumDetailExportM3u.visibility = View.VISIBLE
            binding.btnAlbumDetailExportM3u.setOnClickListener {
                MixtapeDialogs.exportSmartMixtape(
                    requireContext(),
                    viewLifecycleOwner.lifecycleScope,
                    album.album,
                    tracks
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
                    tracks
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
            if (album.isMixtape || album.representativePath.isBlank()) {
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

        // Submit tracks to albumTracksAdapter
        albumTracksAdapter.submitList(tracks)

        // Wire Action Pill Buttons
        binding.btnAlbumDetailPlayAll.setOnClickListener {
            app.audioEngine.foleyEngine.playTilePress()
            if (tracks.isNotEmpty()) {
                currentDisplayedTracks = tracks
                audioEngine.setMixtapeSplitIndex(mixtapePartition?.splitIndex ?: -1)
                audioEngine.playQueue(tracks, 0)
                showNowPlayingSingleAudio(true)
            }
        }

        binding.btnAlbumDetailShuffle.setOnClickListener {
            app.audioEngine.foleyEngine.playTilePress()
            if (tracks.isNotEmpty()) {
                currentDisplayedTracks = tracks
                val shuffled = tracks.shuffled()
                audioEngine.playQueue(shuffled, 0)
                showNowPlayingSingleAudio(true)
            }
        }

        updateAlbumDetailPinButton(album)
        binding.btnAlbumDetailPin.setOnClickListener {
            binding.btnAlbumDetailPin.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val isPinned = togglePinAlbum(album)
            app.audioEngine.foleyEngine.playPinAction(isPinned)
            updateAlbumDetailPinButton(album)
            val msg = if (isPinned) "Pinned '${album.album}' to Start Screen" else "Unpinned '${album.album}' from Start Screen"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        binding.cardAlbumDetailCover.setOnClickListener {
            val album = currentAlbumItem ?: return@setOnClickListener
            showCoverArtOptionsDialog(album)
        }

        binding.btnAlbumDetailCoverPlay.setOnClickListener {
            app.audioEngine.foleyEngine.playTilePress()
            if (tracks.isNotEmpty()) {
                currentDisplayedTracks = tracks
                audioEngine.playQueue(tracks, 0)
                showNowPlayingSingleAudio(true)
            }
        }

        binding.btnAlbumDetailBack.setOnClickListener {
            app.audioEngine.foleyEngine.playReleaseClick()
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
            .setNegativeButton(R.string.action_cancel, null)
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
        if (!isPanoramaVisible) {
            showPanorama(true)
            return true
        }
        return false
    }

    private fun setupPanorama(app: SpindleApp) {
        val dm = resources.displayMetrics
        val peekPx = (52 * dm.density).toInt()
        val panelWidth = dm.widthPixels - peekPx
        val pBinding = binding.layoutPanorama

        pBinding.panelNowPlaying.layoutParams = pBinding.panelNowPlaying.layoutParams.apply { width = panelWidth }
        pBinding.panelExplore.layoutParams = pBinding.panelExplore.layoutParams.apply { width = panelWidth }
        pBinding.panelRecent.layoutParams = pBinding.panelRecent.layoutParams.apply { width = panelWidth }
        pBinding.panelTools.layoutParams = pBinding.panelTools.layoutParams.apply { width = panelWidth }

        pBinding.panoramaScrollView.isFocusable = false
        pBinding.panoramaScrollView.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        pBinding.panoramaScrollView.post {
            pBinding.panoramaScrollView.scrollTo(0, 0)
        }

        var lastScrolledPanel = 0
        pBinding.panoramaScrollView.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            pBinding.tvPanoramaParallaxTitle.translationX = -scrollX * 0.22f
            val currentPanel = (scrollX + panelWidth / 2) / panelWidth
            if (currentPanel != lastScrolledPanel) {
                lastScrolledPanel = currentPanel
                pBinding.panoramaScrollView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playMetroTick(0.95f)
            }
        }

        // Panel 1: Now Playing & Queue Peek
        pBinding.cardPanoramaNowPlaying.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            showNowPlayingSingleAudio(true)
        }
        pBinding.btnPanoramaExpandPlayer.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            showNowPlayingSingleAudio(true)
        }
        pBinding.btnPanoramaPlayPause.setOnClickListener {
            audioEngine.togglePlayPause()
        }
        pBinding.btnPanoramaQueue.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            QueueBottomSheet().show(childFragmentManager, "QueueBottomSheet")
        }
        pBinding.layoutPanoramaUpNext.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            QueueBottomSheet().show(childFragmentManager, "QueueBottomSheet")
        }

        // Panel 2: Explore Typographic Menu
        pBinding.menuRowTracks.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            showPanorama(false)
            scrollToCatalogPanel(0)
        }
        pBinding.menuRowAlbums.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            showPanorama(false)
            scrollToCatalogPanel(1)
        }
        pBinding.menuRowArtists.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            showPanorama(false)
            scrollToCatalogPanel(2)
        }
        pBinding.menuRowMixtapes.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            showPanorama(false)
            scrollToCatalogPanel(3)
        }
        pBinding.menuRowFolders.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            showPanorama(false)
            scrollToCatalogPanel(4)
        }
        pBinding.menuRowFavorites.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            showPanorama(false)
            scrollToCatalogPanel(5)
        }
        pBinding.menuRowComposers.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            showPanorama(false)
            scrollToCatalogPanel(6)
        }

        // Panel 4: Sound & Tools Shortcuts
        pBinding.cardToolSoundDeck.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            (activity as? MainActivity)?.navigateToDrawer()
        }
        pBinding.cardToolRadio.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            (activity as? MainActivity)?.navigateToRadio()
        }
        pBinding.cardToolApps.setOnClickListener {
            (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
            (activity as? MainActivity)?.navigateToDrawer()
        }
    }

    private fun showPanorama(show: Boolean) {
        isPanoramaVisible = show
        val app = requireActivity().application as? SpindleApp
        if (show) {
            binding.layoutPanorama.root.visibility = View.VISIBLE
            binding.layoutCatalogCollectionPanorama.root.visibility = View.GONE
            binding.catalogContentContainer.visibility = View.GONE
            binding.tabScrollView.visibility = View.GONE
            binding.actionBarContainer.visibility = View.GONE
            binding.tvMetroHeroTitle.visibility = View.GONE
            binding.cardMiniPlayer.visibility = View.GONE
            binding.tvCatalogHeaderTitle.text = "MUSIC HUB"
            binding.layoutPanorama.panoramaScrollView.post {
                binding.layoutPanorama.panoramaScrollView.scrollTo(0, 0)
                binding.layoutPanorama.tvPanoramaParallaxTitle.translationX = 0f
            }
            if (app != null) {
                updatePanoramaData(app)
            }
        } else {
            binding.layoutPanorama.root.visibility = View.GONE
            binding.layoutCatalogCollectionPanorama.root.visibility = View.VISIBLE
            binding.catalogContentContainer.visibility = View.GONE
            binding.tabScrollView.visibility = View.VISIBLE
            binding.actionBarContainer.visibility = View.GONE
            binding.tvMetroHeroTitle.visibility = View.GONE
            binding.cardMiniPlayer.visibility = View.VISIBLE
            binding.tvCatalogHeaderTitle.text = "MUSIC HUB"
            if (app != null) {
                loadAllCatalogData(app)
            }
        }
    }

    private fun loadAllCatalogData(app: SpindleApp) {
        loadTracks(app)
        loadAlbums(app)
        loadArtists(app)
        loadMixtapes(app)
        loadFolders()
        loadFavorites(app)
        loadComposers(app)
    }

    private fun updatePanoramaData(app: SpindleApp) {
        val state = audioEngine.playbackState.value
        updatePanoramaPlayback(app, state)
        updatePanoramaExploreCounts(app)
        updatePanoramaRecentTiles(app)
    }

    private fun updatePanoramaPlayback(app: SpindleApp, state: com.hana.spindle.playback.PlaybackState) {
        val pBinding = _binding?.layoutPanorama ?: return
        val track = state.currentTrack
        if (track != null) {
            pBinding.tvPanoramaNpTitle.text = track.title
            val artistAlbum = if (track.album.isNotBlank()) "${track.artist} • ${track.album}" else track.artist
            pBinding.tvPanoramaNpArtist.text = artistAlbum
            viewLifecycleOwner.lifecycleScope.launch {
                val cover = app.imageLoader.loadCover(track.path, 400, 400)
                if (cover != null) {
                    pBinding.ivPanoramaArt.setImageBitmap(cover)
                } else {
                    pBinding.ivPanoramaArt.setImageResource(R.drawable.bg_circle_play)
                }
            }
        } else {
            pBinding.tvPanoramaNpTitle.text = "No track playing"
            pBinding.tvPanoramaNpArtist.text = "Tap to choose a track"
            pBinding.ivPanoramaArt.setImageResource(R.drawable.bg_circle_play)
        }
        pBinding.btnPanoramaPlayPause.setImageResource(
            if (state.isPlaying) R.drawable.ic_np_pause else R.drawable.ic_np_play
        )
        updatePanoramaUpNextPreview()
    }

    private fun updatePanoramaUpNextPreview() {
        val pBinding = _binding?.layoutPanorama ?: return
        val queue = audioEngine.currentQueueFlow.value
        val currentIndex = audioEngine.currentQueueIndexFlow.value
        val upNextViews = listOf(pBinding.tvPanoramaNext1, pBinding.tvPanoramaNext2, pBinding.tvPanoramaNext3)
        if (queue.isEmpty() || currentIndex < 0 || currentIndex >= queue.size) {
            upNextViews.forEach { it.visibility = View.GONE }
            return
        }
        for (i in 0 until 3) {
            val nextIdx = currentIndex + 1 + i
            val tv = upNextViews[i]
            if (nextIdx < queue.size) {
                val nextTrack = queue[nextIdx]
                val trackNumStr = if (nextTrack.trackNumber > 0) String.format(Locale.US, "%02d ", nextTrack.trackNumber) else ""
                tv.text = "$trackNumStr${nextTrack.title}"
                tv.visibility = View.VISIBLE
                tv.setOnClickListener {
                    audioEngine.playQueueIndex(nextIdx)
                }
            } else {
                tv.visibility = View.GONE
            }
        }
    }

    private fun updatePanoramaExploreCounts(app: SpindleApp) {
        val pBinding = _binding?.layoutPanorama ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val trackCount = withContext(Dispatchers.IO) { app.database.trackDao().getTrackCount() }
                pBinding.tvMenuTracksCount.text = java.text.NumberFormat.getNumberInstance(Locale.US).format(trackCount)
            } catch (_: Exception) {}
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val albums = withContext(Dispatchers.IO) { app.database.trackDao().getAlbums().first() }
                pBinding.tvMenuAlbumsCount.text = albums.size.toString()
            } catch (_: Exception) {}
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val artists = withContext(Dispatchers.IO) { app.database.trackDao().getArtists().first() }
                pBinding.tvMenuArtistsCount.text = artists.size.toString()
            } catch (_: Exception) {}
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val mixtapes = withContext(Dispatchers.IO) { app.database.playlistDao().getAllPlaylists().first() }
                pBinding.tvMenuMixtapesCount.text = (mixtapes.size + 6).toString()
            } catch (_: Exception) {}
        }
    }

    private fun updatePanoramaRecentTiles(app: SpindleApp) {
        val pBinding = _binding?.layoutPanorama ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pinned = getPinnedAlbums()
                pBinding.tvHeaderRecent.text = if (pinned.isNotEmpty()) "pinned + recent" else "recent & new"

                val tileAlbums = mutableListOf<AlbumItem>()
                val seen = mutableSetOf<String>()

                for (p in pinned) {
                    if (seen.add(p.album)) {
                        tileAlbums.add(p)
                        if (tileAlbums.size == 4) break
                    }
                }

                if (tileAlbums.size < 4) {
                    val recentTracks = withContext(Dispatchers.IO) { app.database.trackDao().getRecentlyAddedTracks(50).first() }
                    for (t in recentTracks) {
                        if (t.album.isNotBlank() && seen.add(t.album)) {
                            tileAlbums.add(AlbumItem(
                                album = t.album,
                                artist = t.artist,
                                trackCount = 1,
                                representativePath = t.path,
                                year = t.year,
                                format = t.fileFormat
                            ))
                            if (tileAlbums.size == 4) break
                        }
                    }
                }

                if (tileAlbums.size < 4) {
                    val allAlbums = withContext(Dispatchers.IO) { app.database.trackDao().getAlbums().first() }
                    for (a in allAlbums) {
                        if (seen.add(a.album)) {
                            tileAlbums.add(a)
                            if (tileAlbums.size == 4) break
                        }
                    }
                }
                bindRecentAlbumTiles(app, tileAlbums, getPinnedKeys())
            } catch (_: Exception) {}
        }
    }

    private fun bindRecentAlbumTiles(app: SpindleApp, albums: List<AlbumItem>, pinnedKeys: Set<String> = emptySet()) {
        val pBinding = _binding?.layoutPanorama ?: return
        val tiles = listOf(pBinding.tileRecent1, pBinding.tileRecent2, pBinding.tileRecent3, pBinding.tileRecent4)
        val arts = listOf(pBinding.ivTileArt1, pBinding.ivTileArt2, pBinding.ivTileArt3, pBinding.ivTileArt4)
        val titles = listOf(pBinding.tvTileTitle1, pBinding.tvTileTitle2, pBinding.tvTileTitle3, pBinding.tvTileTitle4)
        val artists = listOf(pBinding.tvTileArtist1, pBinding.tvTileArtist2, pBinding.tvTileArtist3, pBinding.tvTileArtist4)
        val pins = listOf(pBinding.tvTilePin1, pBinding.tvTilePin2, pBinding.tvTilePin3, pBinding.tvTilePin4)

        for (i in 0 until 4) {
            if (i < albums.size) {
                val album = albums[i]
                tiles[i].visibility = View.VISIBLE
                titles[i].text = album.album
                artists[i].text = album.artist

                val isPinned = pinnedKeys.contains(getAlbumPinKey(album))
                pins[i].visibility = if (isPinned) View.VISIBLE else View.GONE

                viewLifecycleOwner.lifecycleScope.launch {
                    val bmp = app.imageLoader.loadAlbumCover(album.album, album.artist, album.representativePath, 200, 200)
                    if (bmp != null) {
                        arts[i].setImageBitmap(bmp)
                    } else {
                        arts[i].setImageResource(R.drawable.bg_circle_play)
                    }
                }
                tiles[i].setOnClickListener {
                    (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playTilePress()
                    albumTracksJob?.cancel()
                    albumTracksJob = viewLifecycleOwner.lifecycleScope.launch {
                        getTracksFlowForAlbum(app, album).collectLatest { albumTracks ->
                            showAlbumDetail(album, albumTracks)
                        }
                    }
                }
                tiles[i].setOnLongClickListener {
                    tiles[i].performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    val pinnedNow = togglePinAlbum(album)
                    (activity?.application as? SpindleApp)?.audioEngine?.foleyEngine?.playPinAction(pinnedNow)
                    val msg = if (pinnedNow) "Pinned '${album.album}' to Start Screen" else "Unpinned '${album.album}' from Start Screen"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    true
                }
            } else {
                tiles[i].visibility = View.INVISIBLE
            }
        }
    }

    private fun getPinnedKeys(): Set<String> {
        val ctx = context ?: return emptySet()
        val prefs = ctx.getSharedPreferences("spindle_pinned_tiles", Context.MODE_PRIVATE)
        return prefs.getStringSet("pinned_keys", emptySet()) ?: emptySet()
    }

    private fun isAlbumPinned(album: AlbumItem): Boolean {
        return getPinnedKeys().contains(getAlbumPinKey(album))
    }

    private fun getAlbumPinKey(album: AlbumItem): String {
        return "${album.format}:${album.album}:${album.artist}"
    }

    private fun togglePinAlbum(album: AlbumItem): Boolean {
        val ctx = context ?: return false
        val prefs = ctx.getSharedPreferences("spindle_pinned_tiles", Context.MODE_PRIVATE)
        val current = prefs.getStringSet("pinned_keys", emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = getAlbumPinKey(album)
        val isNowPinned: Boolean
        if (current.contains(key)) {
            current.remove(key)
            prefs.edit().remove("pin_data_$key").putStringSet("pinned_keys", current).apply()
            isNowPinned = false
        } else {
            current.add(key)
            val payload = org.json.JSONObject().apply {
                put("album", album.album)
                put("artist", album.artist)
                put("trackCount", album.trackCount)
                put("path", album.representativePath)
                put("year", album.year)
                put("format", album.format)
            }.toString()
            prefs.edit().putString("pin_data_$key", payload).putStringSet("pinned_keys", current).apply()
            isNowPinned = true
        }
        val app = activity?.application as? SpindleApp
        if (app != null) {
            updatePanoramaRecentTiles(app)
        }
        return isNowPinned
    }

    private fun getPinnedAlbums(): List<AlbumItem> {
        val ctx = context ?: return emptyList()
        val prefs = ctx.getSharedPreferences("spindle_pinned_tiles", Context.MODE_PRIVATE)
        val keys = prefs.getStringSet("pinned_keys", emptySet()) ?: emptySet()
        val list = mutableListOf<AlbumItem>()
        for (key in keys) {
            val raw = prefs.getString("pin_data_$key", null) ?: continue
            try {
                val json = org.json.JSONObject(raw)
                list.add(AlbumItem(
                    album = json.getString("album"),
                    artist = json.optString("artist", ""),
                    trackCount = json.optInt("trackCount", 1),
                    representativePath = json.optString("path", ""),
                    year = json.optInt("year", 0),
                    format = json.optString("format", "FLAC")
                ))
            } catch (_: Exception) {}
        }
        return list
    }

    private fun updateAlbumDetailPinButton(album: AlbumItem) {
        val isPinned = isAlbumPinned(album)
        val app = activity?.application as? SpindleApp
        val theme = app?.themeManager?.currentTheme?.value
        val isEink = theme?.id == CassetteTheme.MONOCHROME_EINK.id
        val accent = theme?.accentColor ?: Color.parseColor("#F97316")

        if (isPinned) {
            binding.btnAlbumDetailPin.text = "UNPIN"
            binding.btnAlbumDetailPin.setTextColor(if (isEink) Color.BLACK else Color.parseColor("#94A3B8"))
            binding.btnAlbumDetailPin.backgroundTintList = ColorStateList.valueOf(
                if (isEink) Color.WHITE else Color.parseColor("#1E293B")
            )
        } else {
            binding.btnAlbumDetailPin.text = "PIN"
            binding.btnAlbumDetailPin.setTextColor(if (isEink) Color.BLACK else accent)
            binding.btnAlbumDetailPin.backgroundTintList = ColorStateList.valueOf(
                if (isEink) Color.WHITE else Color.parseColor("#202334")
            )
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(millis) -
                java.util.concurrent.TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun loadTracks(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.trackDao().getAllTracks().collectLatest { tracks ->
                allTracksList = tracks
                if (::tracksAdapter.isInitialized) {
                    tracksAdapter.submitList(tracks)
                }
                _binding?.layoutCatalogCollectionPanorama?.tvTracksCount?.text = "${tracks.size} tracks"
                if (allFoldersList.isEmpty()) {
                    loadFolders()
                }
                applyFilterAndSort()
            }
        }
    }

    @Deprecated("Use loadTracks(app)", ReplaceWith("loadTracks(app)"))
    private fun loadSongs(app: SpindleApp) = loadTracks(app)

    private fun loadAlbums(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.trackDao().getAlbums().collectLatest { albums ->
                allAlbumsList = albums
                if (::albumsAdapter.isInitialized) {
                    albumsAdapter.submitList(albums)
                }
                _binding?.layoutCatalogCollectionPanorama?.tvAlbumsCount?.text = "${albums.size} albums"
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
                allArtistsList = artists
                if (::artistsAdapter.isInitialized) {
                    artistsAdapter.submitList(artists)
                }
                _binding?.layoutCatalogCollectionPanorama?.tvArtistsCount?.text = "${artists.size} artists"
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
                allComposersList = composers
                if (::composersAdapter.isInitialized) {
                    composersAdapter.submitList(composers)
                }
                _binding?.layoutCatalogCollectionPanorama?.tvComposersCount?.text = "${composers.size} composers"
                applyFilterAndSort()
            }
        }
    }

    private fun loadFolders() {
        val folders = allTracksList.groupBy { File(it.path).parent ?: "Music" }.map { (dir, tracks) ->
            FolderItem(
                name = File(dir).name,
                path = dir,
                trackCount = tracks.size
            )
        }
        allFoldersList = folders
        if (::foldersAdapter.isInitialized) {
            foldersAdapter.submitList(folders)
        }
        _binding?.layoutCatalogCollectionPanorama?.tvFoldersCount?.text = "${folders.size} storage folders"
        applyFilterAndSort()
    }

    private fun loadGenres(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.trackDao().getAllTracks().collectLatest { tracks ->
                val genres = tracks.filter { !it.genre.isNullOrBlank() }
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
            app.database.trackDao().getHiResTracks().collectLatest { hiResTracks ->
                allTracksList = hiResTracks
                applyFilterAndSort()
            }
        }
    }

    private fun loadFavorites(app: SpindleApp) {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.trackDao().getFavoriteTracks().collectLatest { favTracks ->
                allFavoritesList = favTracks
                if (::favoritesAdapter.isInitialized) {
                    favoritesAdapter.submitList(favTracks)
                }
                _binding?.layoutCatalogCollectionPanorama?.tvFavoritesCount?.text = "${favTracks.size} starred tracks"
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
            AlbumItem.FORMAT_MIXTAPE -> app.database.playlistDao().getTracksForPlaylist(album.year.toLong())
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
                            format = AlbumItem.FORMAT_SMART_MIXTAPE
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
                            format = AlbumItem.FORMAT_SMART_MIXTAPE
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
                            format = AlbumItem.FORMAT_SMART_MIXTAPE
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
                            format = AlbumItem.FORMAT_SMART_MIXTAPE
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
                            format = AlbumItem.FORMAT_SMART_MIXTAPE
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
                            format = AlbumItem.FORMAT_SMART_MIXTAPE
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
                        format = AlbumItem.FORMAT_MIXTAPE
                    )
                }

                smartItems + customItems
            }.collectLatest { items ->
                allMixtapesList = items
                if (::mixtapesAdapter.isInitialized) {
                    mixtapesAdapter.submitList(items)
                }
                _binding?.layoutCatalogCollectionPanorama?.tvMixtapesCount?.text = "${items.size} mixtapes"
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
