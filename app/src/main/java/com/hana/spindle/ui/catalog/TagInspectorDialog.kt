package com.hana.spindle.ui.catalog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.hana.spindle.SpindleApp
import com.hana.spindle.data.TagParser
import com.hana.spindle.data.db.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object TagInspectorDialog {

    fun show(
        context: Context,
        song: SongEntity,
        onMetadataUpdated: ((SongEntity) -> Unit)? = null
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val file = File(song.path)
        val fileSizeMb = if (file.exists()) String.format(Locale.US, "%.2f MB", file.length() / (1024f * 1024f)) else "N/A"
        val sampleRateKhz = String.format(Locale.US, "%.1f kHz", song.sampleRate / 1000f)
        val durationMinSec = String.format(
            Locale.US,
            "%02d:%02d",
            (song.durationMs / 1000) / 60,
            (song.durationMs / 1000) % 60
        )
        val replayGainDb = TagParser.extractReplayGainDb(file)
        val replayGainStr = if (replayGainDb != 0f) String.format(Locale.US, "%+.2f dB", replayGainDb) else "None (0.0 dB)"

        // Procedural View Layout with Retro Cassette Dark Bay Styling
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = true
        }

        val root = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121316"))
            setPadding(dp(20f), dp(20f), dp(20f), dp(20f))
        }
        scrollView.addView(root)

        // Title Header
        val tvHeader = TextView(context).apply {
            text = "AUDIO STREAM TELEMETRY & TAGS 🏷️"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 12.5f
            typeface = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(12f))
        }
        root.addView(tvHeader)

        // Technical Specs Card Bay
        val specsCard = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0B0E"))
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        }

        fun addSpecRow(label: String, value: String) {
            val row = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, dp(2f), 0, dp(2f))
            }
            val tvLabel = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#71717A"))
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(100f), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val tvValue = TextView(context).apply {
                text = value
                setTextColor(Color.parseColor("#E4E4E7"))
                textSize = 10.5f
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(tvLabel)
            row.addView(tvValue)
            specsCard.addView(row)
        }

        addSpecRow("FORMAT:", "${song.fileFormat} • $sampleRateKhz / ${song.bitDepth}-bit")
        addSpecRow("BITRATE:", "${if (song.bitrateKbps > 0) song.bitrateKbps else 1411} kbps • 2-Ch Stereo")
        addSpecRow("DURATION:", "$durationMinSec • $fileSizeMb")
        addSpecRow("REPLAYGAIN:", replayGainStr)
        addSpecRow("LOCATION:", file.name)

        root.addView(specsCard)

        // Divider
        val divider = android.view.View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1f)
            ).apply { setMargins(0, dp(14f), 0, dp(14f)) }
            setBackgroundColor(Color.parseColor("#1E2026"))
        }
        root.addView(divider)

        // Edit Fields Header
        val tvEditHeader = TextView(context).apply {
            text = "METADATA EDITOR"
            setTextColor(Color.parseColor("#71717A"))
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8f))
        }
        root.addView(tvEditHeader)

        fun createInputField(label: String, initialValue: String): EditText {
            val tvFieldLabel = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#A1A1AA"))
                textSize = 10.5f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, dp(4f), 0, dp(2f))
            }
            root.addView(tvFieldLabel)

            val editText = EditText(context).apply {
                setText(initialValue)
                setTextColor(Color.WHITE)
                textSize = 12.5f
                setBackgroundColor(Color.parseColor("#181A20"))
                setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
                typeface = android.graphics.Typeface.SANS_SERIF
                setSingleLine(true)
            }
            root.addView(editText)
            return editText
        }

        val etTitle = createInputField("TITLE:", song.title)
        val etArtist = createInputField("ARTIST:", song.artist)
        val etAlbum = createInputField("ALBUM:", song.album)
        val etYear = createInputField("YEAR:", if (song.year > 0) song.year.toString() else "")
        val etTrack = createInputField("TRACK #:", if (song.trackNumber > 0) song.trackNumber.toString() else "")
        val etGenre = createInputField("GENRE:", song.genre ?: "")

        // Buttons Bar
        val btnBar = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, dp(18f), 0, 0)
        }

        val btnCancel = Button(context).apply {
            text = "CANCEL"
            setTextColor(Color.parseColor("#A1A1AA"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener { dialog.dismiss() }
        }
        btnBar.addView(btnCancel)

        val btnSave = Button(context).apply {
            text = "SAVE METADATA"
            setTextColor(Color.parseColor("#0A0B0D"))
            setBackgroundColor(Color.parseColor("#00E676"))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(14f), dp(6f), dp(14f), dp(6f))

            setOnClickListener {
                val newTitle = etTitle.text.toString().trim().ifEmpty { song.title }
                val newArtist = etArtist.text.toString().trim().ifEmpty { song.artist }
                val newAlbum = etAlbum.text.toString().trim().ifEmpty { song.album }
                val newYear = etYear.text.toString().trim().toIntOrNull() ?: song.year
                val newTrack = etTrack.text.toString().trim().toIntOrNull() ?: song.trackNumber
                val newGenre = etGenre.text.toString().trim().ifEmpty { null }

                val updatedSong = song.copy(
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    year = newYear,
                    trackNumber = newTrack,
                    genre = newGenre
                )

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as SpindleApp
                        app.database.songDao().updateSongMetadata(
                            id = song.id,
                            title = newTitle,
                            artist = newArtist,
                            album = newAlbum,
                            year = newYear,
                            trackNumber = newTrack,
                            genre = newGenre
                        )

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Metadata updated for: $newTitle", Toast.LENGTH_SHORT).show()
                            onMetadataUpdated?.invoke(updatedSong)
                            dialog.dismiss()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to update metadata: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        btnBar.addView(btnSave)
        root.addView(btnBar)

        dialog.setContentView(scrollView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }
}
