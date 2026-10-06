package com.hana.spindle.ui.catalog

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.hana.spindle.R
import com.hana.spindle.data.db.PlaylistEntity
import com.hana.spindle.data.db.PlaylistSongCrossRef
import com.hana.spindle.data.db.TrackEntity
import com.hana.spindle.data.db.SpindleDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MixtapeDialogs {

    private val TAPE_ACCENTS = intArrayOf(
        Color.parseColor("#F97316"), // Tangerine Orange
        Color.parseColor("#FDE68A"), // Vintage Amber
        Color.parseColor("#FB7185"), // Ruby Rose
        Color.parseColor("#38BDF8"), // Neon Cyan
        Color.parseColor("#34D399"), // Chrome Green
        Color.parseColor("#94A3B8")  // Smoked Steel
    )

    fun showCreateMixtapeDialog(
        context: Context,
        database: SpindleDatabase,
        scope: CoroutineScope,
        onCreated: ((Long) -> Unit)? = null
    ) {
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#1C1D22"))
        }

        val titleView = TextView(context).apply {
            text = context.getString(R.string.mixtape_cut_new)
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * context.resources.displayMetrics.density).toInt())
        }
        view.addView(titleView)

        val input = EditText(context).apply {
            hint = "Mixtape Title (e.g. Late Night Drives)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#272932"))
            val inputPad = (10 * context.resources.displayMetrics.density).toInt()
            setPadding(inputPad, inputPad, inputPad, inputPad)
        }
        view.addView(input)

        val colorLabel = TextView(context).apply {
            text = context.getString(R.string.mixtape_select_accent)
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            val padTop = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(0, padTop, 0, (6 * context.resources.displayMetrics.density).toInt())
        }
        view.addView(colorLabel)

        var selectedColor = TAPE_ACCENTS[0]
        val colorContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val chipViews = mutableListOf<Button>()
        for (color in TAPE_ACCENTS) {
            val chip = Button(context).apply {
                val size = (32 * context.resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (8 * context.resources.displayMetrics.density).toInt()
                }
                backgroundTintList = ColorStateList.valueOf(color)
                text = if (color == selectedColor) "✓" else ""
                setTextColor(Color.BLACK)
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    selectedColor = color
                    chipViews.forEach { it.text = "" }
                    text = "✓"
                }
            }
            chipViews.add(chip)
            colorContainer.addView(chip)
        }
        view.addView(colorContainer)

        AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton(context.getString(R.string.mixtape_record_tape)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    scope.launch(Dispatchers.IO) {
                        val id = database.playlistDao().insertPlaylist(
                            PlaylistEntity(name = name, colorAccent = selectedColor)
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Mixtape '$name' created!", Toast.LENGTH_SHORT).show()
                            onCreated?.invoke(id)
                        }
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun showSaveQueueAsMixtapeDialog(
        context: Context,
        database: SpindleDatabase,
        scope: CoroutineScope,
        queue: List<TrackEntity>,
        onSaved: ((Long) -> Unit)? = null
    ) {
        if (queue.isEmpty()) {
            Toast.makeText(context, "Active playback queue is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#1C1D22"))
        }

        val titleView = TextView(context).apply {
            text = "RECORD QUEUE AS MIXTAPE"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (6 * context.resources.displayMetrics.density).toInt())
        }
        view.addView(titleView)

        val subView = TextView(context).apply {
            text = "${queue.size} tracks currently in queue will be saved."
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, (12 * context.resources.displayMetrics.density).toInt())
        }
        view.addView(subView)

        val input = EditText(context).apply {
            hint = "Mixtape Title (e.g. Session Queue)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#272932"))
            val inputPad = (10 * context.resources.displayMetrics.density).toInt()
            setPadding(inputPad, inputPad, inputPad, inputPad)
        }
        view.addView(input)

        val colorLabel = TextView(context).apply {
            text = context.getString(R.string.mixtape_select_accent)
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            val padTop = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(0, padTop, 0, (6 * context.resources.displayMetrics.density).toInt())
        }
        view.addView(colorLabel)

        var selectedColor = TAPE_ACCENTS[0]
        val colorContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val chipViews = mutableListOf<Button>()
        for (color in TAPE_ACCENTS) {
            val chip = Button(context).apply {
                val size = (32 * context.resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (8 * context.resources.displayMetrics.density).toInt()
                }
                backgroundTintList = ColorStateList.valueOf(color)
                text = if (color == selectedColor) "✓" else ""
                setTextColor(Color.BLACK)
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    selectedColor = color
                    chipViews.forEach { it.text = "" }
                    text = "✓"
                }
            }
            chipViews.add(chip)
            colorContainer.addView(chip)
        }
        view.addView(colorContainer)

        AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton(context.getString(R.string.mixtape_record_tape)) { _, _ ->
                val name = input.text.toString().trim().ifBlank { "Deck Mixtape" }
                scope.launch(Dispatchers.IO) {
                    val playlistId = database.playlistDao().insertPlaylist(
                        PlaylistEntity(name = name, colorAccent = selectedColor)
                    )
                    queue.forEachIndexed { index, track ->
                        database.playlistDao().addSongToPlaylist(
                            PlaylistSongCrossRef(
                                playlistId = playlistId,
                                songId = track.id,
                                orderIndex = index.toLong()
                            )
                        )
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved ${queue.size} tracks to '$name'!", Toast.LENGTH_SHORT).show()
                        onSaved?.invoke(playlistId)
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun showAddToMixtapeDialog(
        context: Context,
        database: SpindleDatabase,
        scope: CoroutineScope,
        track: TrackEntity,
        onAdded: (() -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val playlists = database.playlistDao().getAllPlaylists().first()
            withContext(Dispatchers.Main) {
                if (playlists.isEmpty()) {
                    // Prompt user to create one
                    AlertDialog.Builder(context)
                        .setTitle("No Mixtapes Found")
                        .setMessage("Would you like to record your first custom Mixtape now?")
                        .setPositiveButton("Create Mixtape") { _, _ ->
                            showCreateMixtapeDialog(context, database, scope) { newId ->
                                scope.launch(Dispatchers.IO) {
                                    database.playlistDao().addSongToPlaylist(
                                        PlaylistSongCrossRef(newId, track.id, System.currentTimeMillis())
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Added '${track.title}' to Mixtape!", Toast.LENGTH_SHORT).show()
                                        onAdded?.invoke()
                                    }
                                }
                            }
                        }
                        .setNegativeButton(R.string.action_cancel, null)
                        .show()
                } else {
                    val items = mutableListOf<String>()
                    items.add("[Create New Mixtape]")
                    playlists.forEach { p ->
                        items.add("${p.name} (${p.trackCount} tracks)")
                    }

                    AlertDialog.Builder(context)
                        .setTitle(context.getString(R.string.mixtape_add_to))
                        .setItems(items.toTypedArray()) { _, which ->
                            if (which == 0) {
                                showCreateMixtapeDialog(context, database, scope) { newId ->
                                    scope.launch(Dispatchers.IO) {
                                        database.playlistDao().addSongToPlaylist(
                                            PlaylistSongCrossRef(newId, track.id, System.currentTimeMillis())
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Added '${track.title}' to Mixtape!", Toast.LENGTH_SHORT).show()
                                            onAdded?.invoke()
                                        }
                                    }
                                }
                            } else {
                                val playlist = playlists[which - 1]
                                scope.launch(Dispatchers.IO) {
                                    database.playlistDao().addSongToPlaylist(
                                        PlaylistSongCrossRef(playlist.id, track.id, System.currentTimeMillis())
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Added '${track.title}' to ${playlist.name}!", Toast.LENGTH_SHORT).show()
                                        onAdded?.invoke()
                                    }
                                }
                            }
                        }
                        .setNegativeButton(R.string.action_cancel, null)
                        .show()
                }
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
    }

    /**
     * Interactive Split Point & Tape Capacity Editor Dialog for Side A and Side B tape partitions.
     */
    fun showEditMixtapeSplitDialog(
        context: Context,
        database: SpindleDatabase,
        scope: CoroutineScope,
        playlistId: Long,
        playlistName: String,
        tracks: List<TrackEntity>,
        onUpdated: (() -> Unit)? = null
    ) {
        if (tracks.size <= 1) {
            Toast.makeText(context, "Mixtape must have at least 2 tracks to split into Side A & B", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
        val initialCapMins = prefs.getInt("mixtape_${playlistId}_capacity", 90)
        var selectedCapacity = com.hana.spindle.data.MixtapePartition.TapeCapacity.fromMinutes(initialCapMins)
        val savedSplit = prefs.getInt("mixtape_${playlistId}_split_index", -1)
        val autoSplit = com.hana.spindle.data.MixtapePartition.calculateAutoSplitIndex(tracks)
        var currentSplit = if (savedSplit in 1 until tracks.size) savedSplit else autoSplit

        val scrollView = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#161822"))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        scrollView.addView(container)

        // Title
        val titleView = TextView(context).apply {
            text = "CASSETTE SIDE A / SIDE B EDITOR"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (4 * context.resources.displayMetrics.density).toInt())
        }
        container.addView(titleView)

        val subView = TextView(context).apply {
            text = "$playlistName • ${tracks.size} Tracks"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, (14 * context.resources.displayMetrics.density).toInt())
        }
        container.addView(subView)

        // Section: Tape Length
        val capLabel = TextView(context).apply {
            text = "TAPE CAPACITY (TOTAL RUNTIME)"
            textSize = 11f
            setTextColor(Color.parseColor("#F97316")) // Brand Orange
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, (6 * context.resources.displayMetrics.density).toInt())
        }
        container.addView(capLabel)

        val capRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (12 * context.resources.displayMetrics.density).toInt())
        }

        val capButtons = mutableListOf<Button>()
        val capacities = com.hana.spindle.data.MixtapePartition.TapeCapacity.values()

        // Readout Views to be updated dynamically
        val splitLabel = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#38BDF8")) // Neon Cyan
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, (6 * context.resources.displayMetrics.density).toInt())
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val cardPad = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(cardPad, cardPad, cardPad, cardPad)
            setBackgroundColor(Color.parseColor("#1E202B"))
        }

        val tvSideA = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val tvSideATracks = TextView(context).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, (8 * context.resources.displayMetrics.density).toInt())
        }

        val tvSideB = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val tvSideBTracks = TextView(context).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, (8 * context.resources.displayMetrics.density).toInt())
        }

        val tvWarning = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#FB7185")) // Warning Red
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }

        card.addView(tvSideA)
        card.addView(tvSideATracks)
        card.addView(tvSideB)
        card.addView(tvSideBTracks)
        card.addView(tvWarning)

        fun updateReadout() {
            val part = com.hana.spindle.data.MixtapePartition.partition(tracks, selectedCapacity, currentSplit)
            splitLabel.text = "SPLIT BOUNDARY: TRACK ${currentSplit} / ${tracks.size}"

            val aDurStr = formatDuration(part.sideADurationMs)
            val bDurStr = formatDuration(part.sideBDurationMs)
            val maxSideMin = selectedCapacity.sideMinutes

            tvSideA.text = "SIDE A: ${part.sideA.size} Tracks • $aDurStr / ${maxSideMin}:00"
            val lastTrackA = if (currentSplit - 1 in tracks.indices) tracks[currentSplit - 1].title else ""
            tvSideATracks.text = "Start: ${tracks[0].title} → End: $lastTrackA"

            tvSideB.text = "SIDE B: ${part.sideB.size} Tracks • $bDurStr / ${maxSideMin}:00"
            val firstTrackB = if (currentSplit in tracks.indices) tracks[currentSplit].title else ""
            tvSideBTracks.text = "Start: $firstTrackB → End: ${tracks.last().title}"

            if (part.isOverCapacity) {
                val warnSb = StringBuilder("⚠️ EXCEEDS ${selectedCapacity.label} CAPACITY!")
                if (part.isSideAOverCapacity) {
                    val aOver = formatDuration(part.sideAOvertimeMs)
                    warnSb.append("\n• Side A is $aOver past the tape leader!")
                }
                if (part.isSideBOverCapacity) {
                    val bOver = formatDuration(part.sideBOvertimeMs)
                    warnSb.append("\n• Side B is $bOver past the tape leader!")
                }
                tvWarning.text = warnSb.toString()
                tvWarning.visibility = View.VISIBLE
            } else {
                tvWarning.visibility = View.GONE
            }

            capButtons.forEachIndexed { idx, btn ->
                val cap = capacities[idx]
                if (cap == selectedCapacity) {
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F97316"))
                    btn.setTextColor(Color.BLACK)
                } else {
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#272932"))
                    btn.setTextColor(Color.WHITE)
                }
            }
        }

        capacities.forEach { cap ->
            val btn = Button(context).apply {
                text = "${cap.label}\n(${cap.sideMinutes}m)"
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(0, (44 * context.resources.displayMetrics.density).toInt(), 1f).apply {
                    marginEnd = (4 * context.resources.displayMetrics.density).toInt()
                }
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    selectedCapacity = cap
                    updateReadout()
                }
            }
            capButtons.add(btn)
            capRow.addView(btn)
        }
        container.addView(capRow)

        container.addView(splitLabel)

        // Split Slider
        val seekBar = SeekBar(context).apply {
            max = tracks.size - 2
            progress = (currentSplit - 1).coerceIn(0, max)
            setPadding(
                (12 * context.resources.displayMetrics.density).toInt(),
                (8 * context.resources.displayMetrics.density).toInt(),
                (12 * context.resources.displayMetrics.density).toInt(),
                (12 * context.resources.displayMetrics.density).toInt()
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentSplit = (prog + 1).coerceIn(1, tracks.size - 1)
                        updateReadout()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(seekBar)

        container.addView(card)

        // Auto-Balance Button
        val btnAutoBalance = Button(context).apply {
            text = "AUTO-BALANCE DURATION"
            textSize = 11f
            setTextColor(Color.parseColor("#38BDF8"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#272932"))
            val mTop = (12 * context.resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (38 * context.resources.displayMetrics.density).toInt()).apply {
                topMargin = mTop
            }
            setOnClickListener {
                currentSplit = autoSplit
                seekBar.progress = (currentSplit - 1).coerceIn(0, seekBar.max)
                updateReadout()
            }
        }
        container.addView(btnAutoBalance)

        updateReadout()

        AlertDialog.Builder(context)
            .setView(scrollView)
            .setPositiveButton("SAVE CASSETTE") { _, _ ->
                prefs.edit()
                    .putInt("mixtape_${playlistId}_capacity", selectedCapacity.totalMinutes)
                    .putInt("mixtape_${playlistId}_split_index", currentSplit)
                    .apply()
                Toast.makeText(context, "Saved $playlistName tape settings (Split: Track $currentSplit • ${selectedCapacity.label})", Toast.LENGTH_SHORT).show()
                onUpdated?.invoke()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun exportMixtape(
        context: Context,
        database: SpindleDatabase,
        scope: CoroutineScope,
        playlistId: Long,
        playlistName: String,
        onExported: ((java.io.File) -> Unit)? = null
    ) {
        scope.launch {
            val tracks = database.playlistDao().getTracksForPlaylist(playlistId).first()
            if (tracks.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Cannot export empty mixtape", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val prefs = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            val capMins = prefs.getInt("mixtape_${playlistId}_capacity", 90)
            val cap = com.hana.spindle.data.MixtapePartition.TapeCapacity.fromMinutes(capMins)
            val splitIdx = prefs.getInt("mixtape_${playlistId}_split_index", -1)
            val formulation = prefs.getString("mixtape_${playlistId}_formulation", "TYPE_I_FERRIC") ?: "TYPE_I_FERRIC"

            val dir = com.hana.spindle.data.M3uManager.getDefaultPlaylistDirectory()
            val sanitizedName = playlistName.replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            val targetFile = java.io.File(dir, "$sanitizedName.m3u8")
            val success = com.hana.spindle.data.M3uManager.exportMixtape(
                targetFile,
                playlistName,
                tracks,
                useRelativePaths = true,
                tapeCapacity = cap,
                tapeFormulation = formulation,
                splitIndex = splitIdx
            )
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(context, "Exported portable mixtape with tape tags to:\n${targetFile.name}", Toast.LENGTH_LONG).show()
                    onExported?.invoke(targetFile)
                } else {
                    Toast.makeText(context, "Failed to export mixtape", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Bulk exports all mixtapes in the library to the MicroSD/storage Playlists directory.
     */
    fun showExportAllMixtapesDialog(
        context: Context,
        database: SpindleDatabase,
        scope: CoroutineScope,
        onCompleted: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context)
            .setTitle("Export All Mixtapes to MicroSD")
            .setMessage("Export all custom mixtapes to standard Extended M3U8 files with portable relative paths?\n\nPlaylists will be saved to your MicroSD/Music/Playlists directory, ready for any DAP or music player.")
            .setPositiveButton("Export All") { _, _ ->
                scope.launch {
                    val result = com.hana.spindle.data.M3uManager.exportAllMixtapes(context, database)
                    withContext(Dispatchers.Main) {
                        if (result.playlistsExported > 0) {
                            AlertDialog.Builder(context)
                                .setTitle("Mixtapes Exported Successfully")
                                .setMessage("✓ ${result.playlistsExported} mixtapes exported\n✓ ${result.tracksExported} total tracks mapped\n\nDestination:\n${result.destinationDir.absolutePath}")
                                .setPositiveButton("OK", null)
                                .show()
                            onCompleted?.invoke()
                        } else {
                            Toast.makeText(context, "No non-empty mixtapes found to export", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /**
     * Discovers all M3U/M3U8 playlists across internal and external MicroSD storage,
     * importing them into Spindle.
     */
    fun showSyncMicroSdMixtapesDialog(
        context: Context,
        database: SpindleDatabase,
        scope: CoroutineScope,
        onSynced: (() -> Unit)? = null
    ) {
        Toast.makeText(context, "Scanning MicroSD storage for playlists...", Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = com.hana.spindle.data.M3uManager.syncAllMixtapesFromStorage(context, database)
            withContext(Dispatchers.Main) {
                if (result.playlistsImported > 0) {
                    val listStr = result.playlistNames.joinToString("\n• ", prefix = "• ")
                    AlertDialog.Builder(context)
                        .setTitle("MicroSD Mixtape Sync Complete")
                        .setMessage("Found ${result.playlistsFound} playlist files.\nImported ${result.playlistsImported} mixtapes (${result.totalTracksMapped} tracks):\n\n$listStr")
                        .setPositiveButton("OK") { _, _ ->
                            onSynced?.invoke()
                        }
                        .show()
                } else if (result.playlistsFound > 0) {
                    AlertDialog.Builder(context)
                        .setTitle("Playlists Found But Unmatched")
                        .setMessage("Found ${result.playlistsFound} playlist files, but none of the track paths could be matched to currently indexed library tracks.\n\nRun 'RESCAN MUSIC STORAGE' first to index all audio files.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(context)
                        .setTitle("No MicroSD Playlists Found")
                        .setMessage("No .m3u or .m3u8 playlist files were found on internal or MicroSD storage.\n\nPlace playlist files into your Playlists or Music folders.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    fun showImportM3uDialog(
        context: Context,
        database: SpindleDatabase,
        scope: CoroutineScope,
        onImported: ((Long) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val candidates = com.hana.spindle.data.M3uManager.findPlaylistFiles(context)

            withContext(Dispatchers.Main) {
                if (candidates.isEmpty()) {
                    AlertDialog.Builder(context)
                        .setTitle("No M3U Playlists Found")
                        .setMessage("No .m3u or .m3u8 playlist files found in Music or Downloads directories.\n\nPlace standard M3U files into /sdcard/Music/Playlists/ to import.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val names = candidates.map { "${it.name} (${it.parentFile?.name ?: ""})" }.toTypedArray()
                    AlertDialog.Builder(context)
                        .setTitle("Import M3U Playlist")
                        .setItems(names) { _, which ->
                            val selectedFile = candidates[which]
                            scope.launch {
                                val result = com.hana.spindle.data.M3uManager.importMixtape(selectedFile, database)
                                withContext(Dispatchers.Main) {
                                    if (result != null && result.tracksCount > 0) {
                                        Toast.makeText(context, "Imported '${result.playlistName}' (${result.tracksCount} tracks)!", Toast.LENGTH_SHORT).show()
                                        onImported?.invoke(result.playlistId)
                                    } else {
                                        Toast.makeText(context, "Could not match tracks from '${selectedFile.name}'", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        .setNegativeButton(R.string.action_cancel, null)
                        .show()
                }
            }
        }
    }

    /**
     * Exports a dynamically generated Smart Mixtape to a portable Extended M3U8 file.
     */
    fun exportSmartMixtape(
        context: Context,
        scope: CoroutineScope,
        playlistName: String,
        tracks: List<TrackEntity>,
        onExported: ((java.io.File) -> Unit)? = null
    ) {
        if (tracks.isEmpty()) {
            Toast.makeText(context, "Cannot export empty mixtape", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val dir = com.hana.spindle.data.M3uManager.getDefaultPlaylistDirectory()
            val sanitizedName = playlistName.replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            val targetFile = java.io.File(dir, "$sanitizedName.m3u8")
            val success = com.hana.spindle.data.M3uManager.exportMixtape(targetFile, playlistName, tracks, useRelativePaths = true)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(context, "Exported smart mixtape to:\n${targetFile.name}", Toast.LENGTH_LONG).show()
                    onExported?.invoke(targetFile)
                } else {
                    Toast.makeText(context, "Failed to export smart mixtape", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
