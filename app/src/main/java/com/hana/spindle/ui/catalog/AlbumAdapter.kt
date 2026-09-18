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

    private var textColorPrimary: Int = android.graphics.Color.WHITE
    private var textColorSecondary: Int = android.graphics.Color.parseColor("#94A3B8")
    private var cardBg: Int = android.graphics.Color.parseColor("#1C1D22")
    private var isEinkMode: Boolean = false

    fun updateThemeColors(primary: Int, secondary: Int, isDark: Boolean, isEink: Boolean) {
        this.isEinkMode = isEink
        this.textColorPrimary = primary
        this.textColorSecondary = secondary
        this.cardBg = if (isEink) android.graphics.Color.WHITE else if (!isDark) android.graphics.Color.parseColor("#E5E5E2") else android.graphics.Color.parseColor("#202334")
        notifyDataSetChanged()
    }

    class AlbumViewHolder(val binding: ItemAlbumGridBinding) : RecyclerView.ViewHolder(binding.root) {
        var loadJob: kotlinx.coroutines.Job? = null
    }

    override fun onViewRecycled(holder: AlbumViewHolder) {
        super.onViewRecycled(holder)
        holder.loadJob?.cancel()
        holder.loadJob = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = getItem(position)
        val b = holder.binding

        b.tvGridTitle.text = album.album
        b.tvGridTitle.setTextColor(textColorPrimary)
        b.tvGridSubtitle.text = album.artist
        b.tvGridSubtitle.setTextColor(textColorSecondary)
        b.cardAlbumRoot.setCardBackgroundColor(cardBg)
        b.tvGridTrackCount.text = "${album.trackCount} tracks"

        if (isEinkMode) {
            b.tvGridTrackCount.setTextColor(android.graphics.Color.BLACK)
            b.tvGridFormat.setTextColor(android.graphics.Color.BLACK)
            b.tvGridFormat.setBackgroundColor(android.graphics.Color.WHITE)
            b.btnGridPlay.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
            b.btnGridPlay.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        } else {
            b.tvGridTrackCount.setTextColor(android.graphics.Color.parseColor("#E2E8F0"))
            b.tvGridFormat.setTextColor(holder.itemView.context.getColor(com.hana.spindle.R.color.vfd_emerald))
            b.tvGridFormat.setBackgroundColor(android.graphics.Color.parseColor("#CC1E293B"))
            b.btnGridPlay.backgroundTintList = android.content.res.ColorStateList.valueOf(holder.itemView.context.getColor(com.hana.spindle.R.color.wm2_red))
            b.btnGridPlay.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }

        if (album.year > 0) {
            b.tvGridYear.visibility = View.VISIBLE
            b.tvGridYear.text = album.year.toString()
            if (isEinkMode) {
                b.tvGridYear.setTextColor(android.graphics.Color.BLACK)
                b.tvGridYear.setBackgroundColor(android.graphics.Color.WHITE)
            } else {
                b.tvGridYear.setTextColor(android.graphics.Color.parseColor("#E2E8F0"))
                b.tvGridYear.setBackgroundColor(android.graphics.Color.parseColor("#B3000000"))
            }
        } else {
            b.tvGridYear.visibility = View.GONE
        }

        b.tvGridFormat.text = album.format

        // Asynchronously load RGB_565 downsampled cover art (200x200) with job management
        if (b.ivGridArt.tag != album.representativePath || b.ivGridArt.drawable == null) {
            holder.loadJob?.cancel()
            b.ivGridArt.tag = album.representativePath
            b.ivGridArt.setImageDrawable(null)
            if (album.format == "MIXTAPE" || album.representativePath.isBlank()) {
                b.ivGridArt.setPadding(32, 32, 32, 32)
                b.ivGridArt.setImageResource(com.hana.spindle.R.drawable.ic_mixtape_tape)
            } else {
                b.ivGridArt.setPadding(0, 0, 0, 0)
                holder.loadJob = scope.launch {
                    val thumb = imageLoader.loadCover(album.representativePath, 200, 200)
                    if (b.ivGridArt.tag == album.representativePath) {
                        withContext(Dispatchers.Main) {
                            if (b.ivGridArt.tag == album.representativePath) {
                                if (thumb != null) {
                                    b.ivGridArt.setImageBitmap(thumb)
                                } else {
                                    b.ivGridArt.setPadding(32, 32, 32, 32)
                                    b.ivGridArt.setImageResource(android.R.drawable.ic_media_play)
                                }
                            }
                        }
                    }
                }
            }
        }

        b.btnGridPlay.setOnClickListener { onPlayAlbumClicked(album) }
        holder.itemView.setOnClickListener { onAlbumClicked(album) }
        holder.itemView.setOnLongClickListener {
            onAlbumLongClicked?.invoke(album)
            onAlbumLongClicked != null
        }
    }

    var onAlbumLongClicked: ((AlbumItem) -> Unit)? = null

    companion object DiffCallback : DiffUtil.ItemCallback<AlbumItem>() {
        override fun areItemsTheSame(oldItem: AlbumItem, newItem: AlbumItem): Boolean {
            return oldItem.album == newItem.album && oldItem.artist == newItem.artist
        }

        override fun areContentsTheSame(oldItem: AlbumItem, newItem: AlbumItem): Boolean {
            return oldItem == newItem
        }
    }
}
