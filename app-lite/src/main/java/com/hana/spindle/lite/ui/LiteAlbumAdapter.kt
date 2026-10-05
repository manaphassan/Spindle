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
 * Immutable data representation of an album in Spindle Lite's library.
 * Zero database overhead; derived directly from scanned track metadata.
 */
data class LiteAlbum(
    val name: String,
    val artist: String,
    val tracks: List<Track>,
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
 * Ultra-lightweight RecyclerView adapter for Zune HD Metro album navigation.
 * Reuses item_lite_track layout for maximum efficiency on 512MB RAM.
 */
class LiteAlbumAdapter(
    private var albums: List<LiteAlbum> = emptyList(),
    private val onAlbumClicked: (LiteAlbum) -> Unit
) : RecyclerView.Adapter<LiteAlbumAdapter.AlbumViewHolder>(), SectionIndexer {

    private var allAlbums: List<LiteAlbum> = albums
    private var displayedAlbums: List<LiteAlbum> = albums
    private var sections: Array<String> = emptyArray()
    private var sectionPositions: IntArray = IntArray(0)

    fun updateAlbums(newAlbums: List<LiteAlbum>) {
        this.allAlbums = newAlbums
        applyFilterInternal("")
    }

    fun getAlbums(): List<LiteAlbum> = displayedAlbums

    fun filter(query: String) {
        applyFilterInternal(query.trim().lowercase())
    }

    private fun applyFilterInternal(q: String) {
        displayedAlbums = if (q.isEmpty()) {
            allAlbums
        } else {
            allAlbums.filter {
                it.name.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
        rebuildSections(displayedAlbums)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lite_track, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(displayedAlbums[position], position, onAlbumClicked)
    }

    override fun getItemCount(): Int = displayedAlbums.size

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

    private fun rebuildSections(list: List<LiteAlbum>) {
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

    class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val vSpine: View = itemView.findViewById(R.id.vTapeSpine)
        private val tvIndex: TextView = itemView.findViewById(R.id.tvTrackIndex)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvItemArtist)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvItemDuration)

        fun bind(album: LiteAlbum, position: Int, onClick: (LiteAlbum) -> Unit) {
            val context = itemView.context
            val primaryText = ContextCompat.getColor(context, R.color.lite_metro_text_primary)
            val secondaryText = ContextCompat.getColor(context, R.color.lite_metro_text_secondary)
            val mutedText = ContextCompat.getColor(context, R.color.lite_metro_text_muted)
            val brandOrange = ContextCompat.getColor(context, R.color.brand_orange)

            vSpine.setBackgroundColor(brandOrange)
            tvIndex.text = String.format("%02d", position + 1)
            tvIndex.setTextColor(mutedText)

            tvTitle.text = album.name
            tvTitle.setTextColor(primaryText)

            val trackCountText = if (album.tracks.size == 1) "1 track" else "${album.tracks.size} tracks"
            tvArtist.text = "${album.artist} • $trackCountText"
            tvArtist.setTextColor(secondaryText)

            tvDuration.text = album.formattedDuration
            tvDuration.setTextColor(mutedText)

            itemView.setOnClickListener { onClick(album) }
        }
    }
}
