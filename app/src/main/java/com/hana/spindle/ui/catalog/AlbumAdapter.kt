package com.hana.spindle.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.data.ImageLoader
import com.hana.spindle.data.db.AlbumItem
import com.hana.spindle.databinding.ItemAlbumBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumAdapter(
    private val imageLoader: ImageLoader,
    private val onAlbumClicked: (AlbumItem) -> Unit
) : ListAdapter<AlbumItem, AlbumAdapter.AlbumViewHolder>(DiffCallback) {

    private val scope = CoroutineScope(Dispatchers.Main)

    class AlbumViewHolder(val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = getItem(position)
        val b = holder.binding

        b.tvAlbumName.text = album.album
        b.tvAlbumArtist.text = album.artist
        b.tvTrackCount.text = "${album.trackCount} tracks"

        // Asynchronously load RGB_565 downsampled cover art
        holder.binding.ivAlbumThumb.setImageDrawable(null)
        scope.launch {
            val thumb = imageLoader.loadCover(album.representativePath, 128, 128)
            withContext(Dispatchers.Main) {
                holder.binding.ivAlbumThumb.setImageBitmap(thumb)
            }
        }

        holder.itemView.setOnClickListener { onAlbumClicked(album) }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AlbumItem>() {
        override fun areItemsTheSame(oldItem: AlbumItem, newItem: AlbumItem): Boolean {
            return oldItem.album == newItem.album && oldItem.artist == newItem.artist
        }

        override fun areContentsTheSame(oldItem: AlbumItem, newItem: AlbumItem): Boolean {
            return oldItem == newItem
        }
    }
}
