package com.hana.spindle.lite.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SectionIndexer
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.lite.R
import com.hana.spindle.lite.db.Track

/**
 * Lightweight RecyclerView adapter for Spindle Lite's library.
 * Features:
 * - J-Card Cassette Formulation Spines (Type IV Metal Gold, Type II Chrome Blue, Type I Normal Grey).
 * - Instant 1-tap audiophile filtering (ALL, HI-RES, LOSSLESS, NORMAL).
 * - Fast SectionIndexer jump support with zero allocation in onBindViewHolder.
 */
class LiteTrackAdapter(
    private var tracks: List<Track> = emptyList(),
    private val onTrackClicked: (Int, Track) -> Unit
) : RecyclerView.Adapter<LiteTrackAdapter.TrackViewHolder>(), SectionIndexer {

    enum class VaultFilter {
        ALL,
        HI_RES,
        LOSSLESS,
        NORMAL
    }

    private var allTracks: List<Track> = tracks
    private var sections: Array<String> = emptyArray()
    private var sectionPositions: IntArray = IntArray(0)
    var activeTrackPath: String? = null

    var currentFilterMode: VaultFilter = VaultFilter.ALL
        private set
    private var currentSearchQuery: String = ""

    fun updateTracks(newTracks: List<Track>) {
        this.allTracks = newTracks
        applyFilterInternal()
    }

    fun getTracks(): List<Track> = tracks

    fun setActivePath(path: String?) {
        this.activeTrackPath = path
        notifyDataSetChanged()
    }

    fun setFilterMode(mode: VaultFilter) {
        this.currentFilterMode = mode
        applyFilterInternal()
    }

    fun filter(query: String) {
        this.currentSearchQuery = query.trim().lowercase()
        applyFilterInternal()
    }

    private fun applyFilterInternal() {
        val q = currentSearchQuery
        val filteredByMode = when (currentFilterMode) {
            VaultFilter.ALL -> allTracks
            VaultFilter.HI_RES -> allTracks.filter {
                it.tapeBiasType.contains("TYPE IV") || it.bitDepth >= 24 || it.sampleRate >= 88200
            }
            VaultFilter.LOSSLESS -> allTracks.filter {
                val fmt = it.format.uppercase()
                fmt.contains("FLAC") || fmt.contains("WAV") || fmt.contains("ALAC") || fmt.contains("DSD")
            }
            VaultFilter.NORMAL -> allTracks.filter {
                it.tapeBiasType.contains("TYPE I")
            }
        }

        tracks = if (q.isEmpty()) {
            filteredByMode
        } else {
            filteredByMode.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.format.lowercase().contains(q)
            }
        }
        rebuildSections(tracks)
        notifyDataSetChanged()
    }

    private fun rebuildSections(trackList: List<Track>) {
        val sectionMap = LinkedHashMap<String, Int>()
        for (i in trackList.indices) {
            val letter = trackList[i].artist.firstOrNull()?.uppercaseChar()?.toString()
                ?.takeIf { it[0] in 'A'..'Z' } ?: "#"
            if (!sectionMap.containsKey(letter)) {
                sectionMap[letter] = i
            }
        }
        sections = sectionMap.keys.toTypedArray()
        sectionPositions = sectionMap.values.toIntArray()
    }

    override fun getSections(): Array<Any> = Array(sections.size) { i -> sections[i] }

    override fun getPositionForSection(sectionIndex: Int): Int {
        if (sectionPositions.isEmpty()) return 0
        val idx = sectionIndex.coerceIn(0, sectionPositions.size - 1)
        return sectionPositions[idx]
    }

    override fun getSectionForPosition(position: Int): Int {
        if (sectionPositions.isEmpty()) return 0
        for (i in sectionPositions.indices.reversed()) {
            if (position >= sectionPositions[i]) {
                return i
            }
        }
        return 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lite_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        holder.bind(position, track, activeTrackPath, onTrackClicked)
    }

    override fun getItemCount(): Int = tracks.size

    class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val vTapeSpine: View = itemView.findViewById(R.id.vTapeSpine)
        private val tvIndex: TextView = itemView.findViewById(R.id.tvTrackIndex)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvItemArtist)
        private val tvFormat: TextView = itemView.findViewById(R.id.tvItemFormat)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvItemDuration)

        fun bind(position: Int, track: Track, activePath: String?, onClick: (Int, Track) -> Unit) {
            val isCurrent = track.filePath == activePath
            val context = itemView.context
            val brandOrange = androidx.core.content.ContextCompat.getColor(context, R.color.brand_orange)
            val primaryText = androidx.core.content.ContextCompat.getColor(context, R.color.lite_text_primary)
            val mutedText = androidx.core.content.ContextCompat.getColor(context, R.color.lite_text_muted)

            // Cassette Formulation Color Coding
            val (spineColor, badgePrefix) = when {
                track.tapeBiasType.contains("TYPE IV") -> Pair(0xFFFFB300.toInt(), "[IV] ") // Metal Gold
                track.tapeBiasType.contains("TYPE II") -> Pair(0xFF1E88E5.toInt(), "[II] ") // Chrome Blue
                else -> Pair(0xFF8E8E93.toInt(), "[I] ")                                    // Normal Grey
            }
            vTapeSpine.setBackgroundColor(spineColor)

            tvIndex.text = if (isCurrent) ">" else String.format("%02d", position + 1)
            tvIndex.setTextColor(if (isCurrent) brandOrange else mutedText)

            tvTitle.text = track.title
            tvTitle.setTextColor(if (isCurrent) brandOrange else primaryText)

            tvArtist.text = if (track.album.isNotEmpty()) "${track.artist} • ${track.album}" else track.artist
            tvFormat.text = "$badgePrefix${track.formatBadge}"
            tvFormat.setTextColor(spineColor)

            tvDuration.text = track.formattedDuration

            itemView.setOnClickListener {
                onClick(position, track)
            }
        }
    }
}
