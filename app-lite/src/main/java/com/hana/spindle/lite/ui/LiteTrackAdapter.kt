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
 * Designed for instant 60 FPS scrolling on low-spec hardware with fast SectionIndexer jump support.
 */
class LiteTrackAdapter(
    private var tracks: List<Track> = emptyList(),
    private val onTrackClicked: (Int, Track) -> Unit
) : RecyclerView.Adapter<LiteTrackAdapter.TrackViewHolder>(), SectionIndexer {

    private var sections: Array<String> = emptyArray()
    private var sectionPositions: IntArray = IntArray(0)

    fun updateTracks(newTracks: List<Track>) {
        this.tracks = newTracks
        rebuildSections(newTracks)
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
        holder.bind(position, track, onTrackClicked)
    }

    override fun getItemCount(): Int = tracks.size

    class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIndex: TextView = itemView.findViewById(R.id.tvTrackIndex)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvItemArtist)
        private val tvFormat: TextView = itemView.findViewById(R.id.tvItemFormat)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvItemDuration)

        fun bind(position: Int, track: Track, onClick: (Int, Track) -> Unit) {
            tvIndex.text = String.format("%02d", position + 1)
            tvTitle.text = track.title
            tvArtist.text = if (track.album.isNotEmpty()) "${track.artist} • ${track.album}" else track.artist
            tvFormat.text = track.formatBadge
            tvDuration.text = track.formattedDuration

            itemView.setOnClickListener {
                onClick(position, track)
            }
        }
    }
}
