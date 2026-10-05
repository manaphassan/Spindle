package com.hana.spindle.lite.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SectionIndexer
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.lite.R

/**
 * Ultra-lightweight RecyclerView adapter for Zune HD Metro genre navigation.
 * Reuses item_lite_track layout for maximum efficiency on 512MB RAM.
 */
class LiteGenreAdapter(
    private var genres: List<LiteGenre> = emptyList(),
    private val onGenreClicked: (LiteGenre) -> Unit
) : RecyclerView.Adapter<LiteGenreAdapter.GenreViewHolder>(), SectionIndexer {

    private var allGenres: List<LiteGenre> = genres
    private var displayedGenres: List<LiteGenre> = genres
    private var sections: Array<String> = emptyArray()
    private var sectionPositions: IntArray = IntArray(0)

    fun updateGenres(newGenres: List<LiteGenre>) {
        this.allGenres = newGenres
        applyFilterInternal("")
    }

    fun getGenres(): List<LiteGenre> = displayedGenres

    fun filter(query: String) {
        applyFilterInternal(query.trim().lowercase())
    }

    private fun applyFilterInternal(q: String) {
        displayedGenres = if (q.isEmpty()) {
            allGenres
        } else {
            allGenres.filter {
                it.name.lowercase().contains(q)
            }
        }
        rebuildSections(displayedGenres)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lite_track, parent, false)
        return GenreViewHolder(view)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        holder.bind(displayedGenres[position], position, onGenreClicked)
    }

    override fun getItemCount(): Int = displayedGenres.size

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

    private fun rebuildSections(list: List<LiteGenre>) {
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

    class GenreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val vSpine: View = itemView.findViewById(R.id.vTapeSpine)
        private val tvIndex: TextView = itemView.findViewById(R.id.tvTrackIndex)
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvItemArtist)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvItemDuration)

        fun bind(genre: LiteGenre, position: Int, onClick: (LiteGenre) -> Unit) {
            val context = itemView.context
            val primaryText = ContextCompat.getColor(context, R.color.lite_metro_text_primary)
            val secondaryText = ContextCompat.getColor(context, R.color.lite_metro_text_secondary)
            val mutedText = ContextCompat.getColor(context, R.color.lite_metro_text_muted)
            val brandOrange = ContextCompat.getColor(context, R.color.brand_orange)

            vSpine.setBackgroundColor(brandOrange)
            tvIndex.text = String.format("%02d", position + 1)
            tvIndex.setTextColor(mutedText)

            tvTitle.text = genre.name
            tvTitle.setTextColor(primaryText)

            val trackStr = if (genre.tracks.size == 1) "1 track" else "${genre.tracks.size} tracks"
            val albumStr = if (genre.albumCount == 1) "1 album" else "${genre.albumCount} albums"
            tvArtist.text = "$trackStr • $albumStr"
            tvArtist.setTextColor(secondaryText)

            tvDuration.text = genre.formattedDuration
            tvDuration.setTextColor(mutedText)

            val firstArtPath = genre.tracks.firstOrNull()?.filePath.orEmpty()
            LiteBitmapCache.loadAsync(firstArtPath, ivThumbnail, R.drawable.ic_album_placeholder)

            itemView.setOnClickListener { onClick(genre) }
        }
    }
}
