package com.hana.spindle.ui.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.data.ImageLoader
import com.hana.spindle.data.db.AlbumItem
import com.hana.spindle.databinding.ItemAlbumGridBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumAdapter(
    private val imageLoader: ImageLoader,
    private val onAlbumClicked: (AlbumItem) -> Unit,
    private val onPlayAlbumClicked: (AlbumItem) -> Unit
) : ListAdapter<AlbumItem, AlbumAdapter.AlbumViewHolder>(DiffCallback) {

    private val scope = CoroutineScope(Dispatchers.Main)

    class AlbumViewHolder(val binding: ItemAlbumGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = getItem(position)
        val b = holder.binding

        b.tvGridTitle.text = album.album
        b.tvGridSubtitle.text = album.artist
        b.tvGridTrackCount.text = "${album.trackCount} tracks"

        if (album.year > 0) {
            b.tvGridYear.visibility = View.VISIBLE
            b.tvGridYear.text = album.year.toString()
        } else {
            b.tvGridYear.visibility = View.GONE
        }

        b.tvGridFormat.text = album.format

        // Asynchronously load RGB_565 downsampled cover art (200x200)
        b.ivGridArt.setImageDrawable(null)
        scope.launch {
            val thumb = imageLoader.loadCover(album.representativePath, 200, 200)
            withContext(Dispatchers.Main) {
                b.ivGridArt.setImageBitmap(thumb)
            }
        }

        b.btnGridPlay.setOnClickListener { onPlayAlbumClicked(album) }
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
