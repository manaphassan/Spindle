package com.hana.spindle.lite.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.lite.R
import com.hana.spindle.lite.db.Track
import java.io.File

/**
 * Lightweight file hierarchy browser adapter for Spindle Lite.
 * Allows audiophiles to navigate directory trees on MicroSD cards (/Music/Artist/Album/)
 * with zero heap bloat and instant response.
 */
class LiteFolderAdapter(
    private val onDirectoryClicked: (File) -> Unit,
    private val onAudioFileClicked: (File, List<File>, Int) -> Unit
) : RecyclerView.Adapter<LiteFolderAdapter.FolderViewHolder>() {

    sealed class Item {
        data class Directory(val file: File, val childCount: Int) : Item()
        data class Audio(val file: File, val track: Track?) : Item()
    }

    private var items: List<Item> = emptyList()
    private var activeFilePath: String? = null

    fun setItems(newItems: List<Item>, currentPlayingPath: String? = null) {
        this.items = newItems
        this.activeFilePath = currentPlayingPath
        notifyDataSetChanged()
    }

    fun getItems(): List<Item> = items

    fun setActivePath(path: String?) {
        this.activeFilePath = path
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lite_track, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(items[position], activeFilePath, items, position, onDirectoryClicked, onAudioFileClicked)
    }

    override fun getItemCount(): Int = items.size

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIndex: TextView = itemView.findViewById(R.id.tvTrackIndex)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvItemArtist)
        private val tvFormat: TextView = itemView.findViewById(R.id.tvItemFormat)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvItemDuration)

        fun bind(
            item: Item,
            activePath: String?,
            allItems: List<Item>,
            position: Int,
            onDirClick: (File) -> Unit,
            onAudioClick: (File, List<File>, Int) -> Unit
        ) {
            val context = itemView.context
            val brandOrange = ContextCompat.getColor(context, R.color.brand_orange)
            val primaryText = ContextCompat.getColor(context, R.color.lite_text_primary)
            val secondaryText = ContextCompat.getColor(context, R.color.lite_text_secondary)
            val mutedText = ContextCompat.getColor(context, R.color.lite_text_muted)
            val amberGlow = ContextCompat.getColor(context, R.color.lite_amber_glow)

            when (item) {
                is Item.Directory -> {
                    tvIndex.text = "DIR"
                    tvIndex.setTextColor(amberGlow)
                    tvTitle.text = item.file.name
                    tvTitle.setTextColor(primaryText)
                    tvArtist.text = "${item.childCount} items in folder"
                    tvArtist.setTextColor(secondaryText)
                    tvFormat.text = "DIR"
                    tvFormat.setTextColor(amberGlow)
                    tvDuration.text = ""

                    itemView.setOnClickListener {
                        onDirClick(item.file)
                    }
                }
                is Item.Audio -> {
                    val isCurrent = item.file.absolutePath == activePath
                    val track = item.track

                    tvIndex.text = if (isCurrent) ">" else String.format("%02d", position + 1)
                    tvIndex.setTextColor(if (isCurrent) brandOrange else mutedText)

                    tvTitle.text = track?.title ?: item.file.nameWithoutExtension
                    tvTitle.setTextColor(if (isCurrent) brandOrange else primaryText)

                    tvArtist.text = track?.artist ?: item.file.parentFile?.name ?: "Audio"
                    tvArtist.setTextColor(secondaryText)

                    tvFormat.text = track?.formatBadge ?: item.file.extension.uppercase()
                    tvFormat.setTextColor(brandOrange)

                    tvDuration.text = track?.formattedDuration ?: ""
                    tvDuration.setTextColor(mutedText)

                    itemView.setOnClickListener {
                        val audioFiles = allItems.filterIsInstance<Item.Audio>().map { it.file }
                        val audioIndex = audioFiles.indexOf(item.file).coerceAtLeast(0)
                        onAudioClick(item.file, audioFiles, audioIndex)
                    }
                }
            }
        }
    }
}
