package com.hana.spindle.lite.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SectionIndexer
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.lite.R
import com.hana.spindle.lite.db.Track

/**
 * Immutable data representation of an artist in Spindle Lite's library.
 * Zero database overhead; derived directly from scanned track metadata.
 */
data class LiteArtist(
    val name: String,
    val tracks: List<Track>,
    val albumCount: Int = tracks.map { it.album.ifBlank { "Unknown Album" } }.distinct().size,
    val totalDurationMs: Long = tracks.sumOf { it.durationMs }
) {
    val formattedDuration: String
        get() {
            val totalSec = totalDurationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", min, sec)
        }
}

/**
 * Ultra-lightweight RecyclerView adapter for Zune HD Metro artist navigation.
 * Reuses item_lite_track layout for maximum efficiency on 512MB RAM.
 */
class LiteArtistAdapter(
    private var artists: List<LiteArtist> = emptyList(),
    private val onArtistClicked: (LiteArtist) -> Unit
) : RecyclerView.Adapter<LiteArtistAdapter.ArtistViewHolder>(), SectionIndexer {

    private var allArtists: List<LiteArtist> = artists
    private var displayedArtists: List<LiteArtist> = artists
    private var sections: Array<String> = emptyArray()
    private var sectionPositions: IntArray = IntArray(0)

    fun updateArtists(newArtists: List<LiteArtist>) {
        this.allArtists = newArtists
        applyFilterInternal("")
    }

    fun getArtists(): List<LiteArtist> = displayedArtists

    fun filter(query: String) {
        applyFilterInternal(query.trim().lowercase())
    }

    private fun applyFilterInternal(q: String) {
        displayedArtists = if (q.isEmpty()) {
            allArtists
        } else {
            allArtists.filter {
                it.name.lowercase().contains(q)
            }
        }
        rebuildSections(displayedArtists)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lite_track, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        holder.bind(displayedArtists[position], position, onArtistClicked)
    }

    override fun getItemCount(): Int = displayedArtists.size

    // --- SectionIndexer for Zune A-Z Quick Jump ---

    override fun getSections(): Array<Any> = sections as Array<Any>

    override fun getPositionForSection(sectionIndex: Int): Int {
        if (sectionIndex < 0 || sectionIndex >= sectionPositions.size) return 0
        return sectionPositions[sectionIndex]
    }

    override fun getSectionForPosition(position: Int): Int {
        if (position < 0) return 0
        for (i in sectionPositions.indices.reversed()) {
            if (position >= sectionPositions[i]) return i
        }
        return 0
    }

    private fun rebuildSections(list: List<LiteArtist>) {
        val map = LinkedHashMap<String, Int>()
        for (i in list.indices) {
            val name = list[i].name.trim()
            val letter = if (name.isNotEmpty() && name[0].isLetter()) {
                name[0].uppercaseChar().toString()
            } else {
                "#"
            }
            if (!map.containsKey(letter)) {
                map[letter] = i
            }
        }
        sections = map.keys.toTypedArray()
        sectionPositions = map.values.toIntArray()
    }

    class ArtistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val vSpine: View = itemView.findViewById(R.id.vTapeSpine)
        private val tvIndex: TextView = itemView.findViewById(R.id.tvTrackIndex)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvItemArtist)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvItemDuration)

        fun bind(artist: LiteArtist, position: Int, onClick: (LiteArtist) -> Unit) {
            val context = itemView.context
            val primaryText = ContextCompat.getColor(context, R.color.lite_metro_text_primary)
            val secondaryText = ContextCompat.getColor(context, R.color.lite_metro_text_secondary)
            val mutedText = ContextCompat.getColor(context, R.color.lite_metro_text_muted)
            val brandOrange = ContextCompat.getColor(context, R.color.brand_orange)

            vSpine.setBackgroundColor(brandOrange)
            tvIndex.text = String.format("%02d", position + 1)
            tvIndex.setTextColor(mutedText)

            tvTitle.text = artist.name
            tvTitle.setTextColor(primaryText)

            val albumStr = if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums"
            val trackStr = if (artist.tracks.size == 1) "1 track" else "${artist.tracks.size} tracks"
            tvArtist.text = "$albumStr • $trackStr"
            tvArtist.setTextColor(secondaryText)

            tvDuration.text = artist.formattedDuration
            tvDuration.setTextColor(mutedText)

            itemView.setOnClickListener { onClick(artist) }
        }
    }
}
