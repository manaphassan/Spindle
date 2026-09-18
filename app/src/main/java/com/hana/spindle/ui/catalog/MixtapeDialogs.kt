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
import com.hana.spindle.data.db.SongEntity
import com.hana.spindle.data.db.SpindleDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MixtapeDialogs {

    private val TAPE_ACCENTS = intArrayOf(
        Color.parseColor("#F97316"), // Walkman Orange
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
            text = "CUT NEW MIXTAPE 📼"
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
            text = "Select Tape Shell Accent:"
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
            .setPositiveButton("RECORD TAPE") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    scope.launch {
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
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showAddToMixtapeDialog(
        context: Context,
        database: SpindleDatabase,
        scope: CoroutineScope,
        song: SongEntity,
        onAdded: (() -> Unit)? = null
    ) {
        scope.launch {
            val playlists = database.playlistDao().getAllPlaylists().first()
            withContext(Dispatchers.Main) {
                if (playlists.isEmpty()) {
                    // Prompt user to create one
                    AlertDialog.Builder(context)
                        .setTitle("No Mixtapes Found")
                        .setMessage("Would you like to record your first custom Mixtape now?")
                        .setPositiveButton("+ Create Mixtape") { _, _ ->
                            showCreateMixtapeDialog(context, database, scope) { newId ->
                                scope.launch {
                                    database.playlistDao().addSongToPlaylist(
                                        PlaylistSongCrossRef(newId, song.id, System.currentTimeMillis())
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Added '${song.title}' to Mixtape!", Toast.LENGTH_SHORT).show()
                                        onAdded?.invoke()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val items = mutableListOf<String>()
                    items.add("➕ [Create New Mixtape]")
                    playlists.forEach { p ->
                        items.add("📼 ${p.name} (${p.trackCount} tracks)")
                    }

                    AlertDialog.Builder(context)
                        .setTitle("Add to Mixtape")
                        .setItems(items.toTypedArray()) { _, which ->
                            if (which == 0) {
                                showCreateMixtapeDialog(context, database, scope) { newId ->
                                    scope.launch {
                                        database.playlistDao().addSongToPlaylist(
                                            PlaylistSongCrossRef(newId, song.id, System.currentTimeMillis())
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Added '${song.title}' to Mixtape!", Toast.LENGTH_SHORT).show()
                                            onAdded?.invoke()
                                        }
                                    }
                                }
                            } else {
                                val playlist = playlists[which - 1]
                                scope.launch {
                                    database.playlistDao().addSongToPlaylist(
                                        PlaylistSongCrossRef(playlist.id, song.id, System.currentTimeMillis())
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Added '${song.title}' to ${playlist.name}!", Toast.LENGTH_SHORT).show()
                                        onAdded?.invoke()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }
}
