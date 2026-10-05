package com.hana.spindle.ui.catalog

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
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
            val dir = com.hana.spindle.data.M3uManager.getDefaultPlaylistDirectory()
            val sanitizedName = playlistName.replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            val targetFile = java.io.File(dir, "$sanitizedName.m3u8")
            val success = com.hana.spindle.data.M3uManager.exportMixtape(targetFile, playlistName, tracks, useRelativePaths = true)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(context, "Exported portable mixtape to:\n${targetFile.name}", Toast.LENGTH_LONG).show()
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
