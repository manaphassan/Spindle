package com.hana.spindle.ui.cassette

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.R
import com.hana.spindle.data.db.TrackEntity
import com.hana.spindle.theme.CassetteTheme
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Custom Hardware-accelerated View recreating an authentic folded paper Cassette J-Card.
 * Displays:
 * 1. Classic jewel case spine fold (Album, Artist, Tape model e.g. EXTRALLOY METAL-XR 90, C-90).
 * 2. Booklet fold (Cover Art thumbnail, FLAC / Bit-perfect specs, production notes).
 * 3. Side A / Side B dual-column track listing with A01/B01 numbering, active track indicator,
 *    and direct touch-to-play interaction.
 * 4. Smooth 3D card flip transition back to the physical cassette deck.
 */
class JCardLinerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val btnFlipToDeck: Button
    private val btnFlipBackBottom: Button
    private val btnManageQueue: Button
    private val tvSpineTitle: TextView
    private val tvSpineTapeModel: TextView
    private val tvSpineDuration: TextView
    private val ivJCardCover: ImageView
    private val tvJCardAlbum: TextView
    private val tvJCardArtist: TextView
    private val tvJCardAudioSpecs: TextView
    private val tvJCardTapeNotes: TextView
    private val btnSideA: Button
    private val btnSideB: Button
    private val rvJCardTracks: RecyclerView

    private val trackAdapter = JCardTrackAdapter()

    var onTrackSelected: ((index: Int) -> Unit)? = null
    var onFlipBackClicked: (() -> Unit)? = null
    var onManageQueueClicked: (() -> Unit)? = null

    private var fullQueue: List<TrackEntity> = emptyList()
    private var activeQueueIndex: Int = -1
    private var showingSideB: Boolean = false

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_jcard_liner, this, true)

        btnFlipToDeck = findViewById(R.id.btnFlipToDeck)
        btnFlipBackBottom = findViewById(R.id.btnFlipBackBottom)
        btnManageQueue = findViewById(R.id.btnManageQueue)
        tvSpineTitle = findViewById(R.id.tvSpineTitle)
        tvSpineTapeModel = findViewById(R.id.tvSpineTapeModel)
        tvSpineDuration = findViewById(R.id.tvSpineDuration)
        ivJCardCover = findViewById(R.id.ivJCardCover)
        tvJCardAlbum = findViewById(R.id.tvJCardAlbum)
        tvJCardArtist = findViewById(R.id.tvJCardArtist)
        tvJCardAudioSpecs = findViewById(R.id.tvJCardAudioSpecs)
        tvJCardTapeNotes = findViewById(R.id.tvJCardTapeNotes)
        btnSideA = findViewById(R.id.btnSideA)
        btnSideB = findViewById(R.id.btnSideB)
        rvJCardTracks = findViewById(R.id.rvJCardTracks)

        rvJCardTracks.layoutManager = LinearLayoutManager(context)
        rvJCardTracks.adapter = trackAdapter

        trackAdapter.onItemClicked = { originalIndex ->
            onTrackSelected?.invoke(originalIndex)
        }

        val flipListener = OnClickListener {
            onFlipBackClicked?.invoke()
        }
        btnFlipToDeck.setOnClickListener(flipListener)
        btnFlipBackBottom.setOnClickListener(flipListener)
        btnManageQueue.setOnClickListener {
            onManageQueueClicked?.invoke()
        }

        btnSideA.setOnClickListener {
            if (showingSideB) {
                showingSideB = false
                updateSideSelection()
            }
        }

        btnSideB.setOnClickListener {
            if (!showingSideB) {
                showingSideB = true
                updateSideSelection()
            }
        }
    }

    fun setQueueData(
        queue: List<TrackEntity>,
        activeIndex: Int,
        albumCover: Bitmap?,
        audioFormat: String,
        theme: CassetteTheme
    ) {
        fullQueue = queue
        activeQueueIndex = activeIndex
        activeTheme = theme

        val currentSong = if (activeIndex in queue.indices) queue[activeIndex] else queue.firstOrNull()

        // 1. Cover Artwork
        if (albumCover != null) {
            ivJCardCover.setImageBitmap(albumCover)
        } else {
            ivJCardCover.setImageResource(R.drawable.ic_mixtape_tape)
        }

        // 2. Spine & Metadata
        val albumName = currentSong?.album?.takeIf { it.isNotBlank() } ?: "Spindle Mixtape"
        val artistName = currentSong?.artist?.takeIf { it.isNotBlank() } ?: "Analog Studio"
        tvSpineTitle.text = "${artistName.uppercase()} — ${albumName.uppercase()}"
        tvJCardAlbum.text = albumName
        tvJCardArtist.text = artistName
        tvJCardArtist.setTextColor(theme.accentColor)
        btnManageQueue.setTextColor(theme.accentColor)

        val isMetalXr = (theme.id == CassetteTheme.METAL_XR_TYPE4.id || theme.name.contains("Metal-XR", ignoreCase = true))
        val isSonyHf = (theme.id == CassetteTheme.SONY_HF_90.id || theme.name.contains("Sony HF", ignoreCase = true))
        val isDenon = (theme.id == CassetteTheme.DENON_HD8_100.id || theme.name.contains("Denon", ignoreCase = true))
        val tapeModelName = when {
            isMetalXr -> "EXTRALLOY METAL-XR 90 • TYPE IV"
            isSonyHf -> "SONY HF 90 • TYPE I NORMAL BIAS 120µs"
            isDenon -> "DENON HD8 100 • TYPE II HIGH POSITION 70µs"
            else -> "${theme.name.uppercase()} • C-90"
        }
        tvSpineTapeModel.text = tapeModelName

        val fmt = if (audioFormat.isNotBlank()) audioFormat else "FLAC 24-bit / 96kHz"
        tvJCardAudioSpecs.text = "$fmt • Direct PCM"

        // 3. Side A / Side B Split & Timings
        val splitIndex = getSplitIndex(queue.size)
        val sideADurationMs = queue.take(splitIndex).sumOf { it.durationMs }
        val sideBDurationMs = queue.drop(splitIndex).sumOf { it.durationMs }
        val sideAStr = formatTime(sideADurationMs)
        val sideBStr = formatTime(sideBDurationMs)
        val totalRuntimeStr = formatTime(sideADurationMs + sideBDurationMs)

        tvSpineDuration.text = "A: $sideAStr | B: $sideBStr"
        tvSpineDuration.setTextColor(theme.accentColor)
        tvJCardTapeNotes.text = "Mastered for Spindle DAP • ${queue.size} Tracks • $totalRuntimeStr Total"

        // Auto-switch to Side B if currently playing song is on Side B
        if (activeIndex >= splitIndex && queue.size > 1) {
            showingSideB = true
        }

        updateSideSelection()
    }

    private fun getSplitIndex(total: Int): Int {
        if (total <= 1) return 1
        return ceil(total / 2.0).toInt()
    }

    private var activeTheme: CassetteTheme = CassetteTheme.DARK

    private fun updateSideSelection() {
        val splitIndex = getSplitIndex(fullQueue.size)
        val sideTracks: List<Pair<Int, TrackEntity>>
        val sidePrefix: String
        val accent = activeTheme.accentColor
        val inactive = Color.parseColor("#94A3B8")
        val activeBg = Color.parseColor("#242635")
        val inactiveBg = Color.parseColor("#1C1D26")

        if (!showingSideB) {
            // Side A
            btnSideA.setTextColor(accent)
            btnSideA.setBackgroundColor(activeBg)
            btnSideB.setTextColor(inactive)
            btnSideB.setBackgroundColor(inactiveBg)
            sidePrefix = "A"
            sideTracks = fullQueue.take(splitIndex).mapIndexed { idx, s -> idx to s }
        } else {
            // Side B
            btnSideA.setTextColor(inactive)
            btnSideA.setBackgroundColor(inactiveBg)
            btnSideB.setTextColor(accent)
            btnSideB.setBackgroundColor(activeBg)
            sidePrefix = "B"
            sideTracks = fullQueue.drop(splitIndex).mapIndexed { idx, s -> (splitIndex + idx) to s }
        }

        trackAdapter.submitTracks(sideTracks, activeQueueIndex, sidePrefix, activeTheme)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    // Inner Adapter for J-Card Track Items
    private class JCardTrackAdapter : RecyclerView.Adapter<JCardTrackAdapter.TrackViewHolder>() {

        private val items = mutableListOf<Pair<Int, TrackEntity>>()
        private var activeIdx = -1
        private var prefix = "A"
        private var theme: CassetteTheme = CassetteTheme.DARK
        var onItemClicked: ((Int) -> Unit)? = null

        fun submitTracks(tracks: List<Pair<Int, TrackEntity>>, active: Int, sidePrefix: String, currentTheme: CassetteTheme) {
            items.clear()
            items.addAll(tracks)
            activeIdx = active
            prefix = sidePrefix
            theme = currentTheme
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_jcard_track, parent, false)
            return TrackViewHolder(view)
        }

        override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
            val (originalIndex, song) = items[position]
            val trackNumber = position + 1
            holder.tvIndex.text = String.format(Locale.US, "%s%02d", prefix, trackNumber)
            holder.tvTitle.text = song.title
            holder.tvArtist.text = song.artist

            val totalSec = TimeUnit.MILLISECONDS.toSeconds(song.durationMs)
            holder.tvDuration.text = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)

            val isCurrent = (originalIndex == activeIdx)
            if (isCurrent) {
                holder.tvActiveDot.visibility = View.VISIBLE
                holder.tvTitle.setTextColor(theme.accentColor)
                holder.tvIndex.setTextColor(theme.accentColor)
            } else {
                holder.tvActiveDot.visibility = View.GONE
                holder.tvTitle.setTextColor(theme.textPrimaryColor)
                holder.tvIndex.setTextColor(theme.labelAccentColor)
            }

            holder.itemView.setOnClickListener {
                onItemClicked?.invoke(originalIndex)
            }
        }

        override fun getItemCount(): Int = items.size

        class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvIndex: TextView = itemView.findViewById(R.id.tvTrackIndex)
            val tvActiveDot: TextView = itemView.findViewById(R.id.tvActiveDot)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTrackTitle)
            val tvArtist: TextView = itemView.findViewById(R.id.tvTrackArtist)
            val tvDuration: TextView = itemView.findViewById(R.id.tvTrackDuration)
        }
    }
}
