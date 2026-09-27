package com.hana.spindle.ui.catalog

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.data.ImageLoader
import com.hana.spindle.data.db.TrackEntity
import com.hana.spindle.databinding.ItemSongBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class TrackAdapter(
    private val imageLoader: ImageLoader,
    private val onTrackClicked: (TrackEntity, Int) -> Unit,
    private val onRatingChanged: (TrackEntity, Int) -> Unit
) : ListAdapter<TrackEntity, TrackAdapter.TrackViewHolder>(DiffCallback) {

    private val scope = CoroutineScope(Dispatchers.Main)
    var activeTrackId: Long? = null
    var showTrackNumbers: Boolean = false
    var isReorderable: Boolean = false
    var onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
    var onRemoveFromMixtape: ((TrackEntity, Int) -> Unit)? = null
    var onPlayNext: ((TrackEntity) -> Unit)? = null
    var onAddToQueue: ((TrackEntity) -> Unit)? = null
    var onAddToMixtape: ((TrackEntity) -> Unit)? = null
    var onInspectTags: ((TrackEntity) -> Unit)? = null

    var activeSongId: Long?
        get() = activeTrackId
        set(value) { activeTrackId = value }

    private var textColorPrimary: Int = Color.parseColor("#FAFAF9")
    private var textColorSecondary: Int = Color.parseColor("#B0B4CE")
    private var formatBadgeBg: Int = Color.parseColor("#1E2132")
    private var formatBadgeText: Int = Color.parseColor("#F97316")
    private var cardThumbBg: Int = Color.parseColor("#202334")
    private var isEinkMode: Boolean = false

    fun updateThemeColors(primary: Int, secondary: Int, isDark: Boolean, isEink: Boolean) {
        this.isEinkMode = isEink
        this.textColorPrimary = primary
        this.textColorSecondary = secondary
        this.formatBadgeBg = if (isEink) Color.TRANSPARENT else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#1E2132")
        this.formatBadgeText = if (isEink) Color.BLACK else if (!isDark) Color.parseColor("#2A2E45") else Color.parseColor("#F97316")
        this.cardThumbBg = if (isEink) Color.WHITE else if (!isDark) Color.parseColor("#E5E5E2") else Color.parseColor("#202334")
        notifyDataSetChanged()
    }

    class TrackViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        var loadJob: kotlinx.coroutines.Job? = null
    }

    override fun onViewRecycled(holder: TrackViewHolder) {
        super.onViewRecycled(holder)
        holder.loadJob?.cancel()
        holder.loadJob = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = getItem(position)
        val b = holder.binding

        if (showTrackNumbers) {
            b.tvTrackNumber.visibility = View.VISIBLE
            val rawTrack = track.trackNumber
            val trackNum = when {
                rawTrack >= 1000 -> rawTrack % 1000
                rawTrack > 0 -> rawTrack
                else -> position + 1
            }
            b.tvTrackNumber.text = String.format(Locale.US, "%02d", trackNum)
            b.tvTrackNumber.setTextColor(textColorSecondary)
        } else {
            b.tvTrackNumber.visibility = View.GONE
        }

        b.tvSongTitle.text = track.title
        b.tvSongArtist.text = track.artist

        val isCurrentlyPlaying = track.id == activeTrackId
        val activeColor = if (isEinkMode) Color.BLACK else Color.parseColor("#F97316")
        if (isCurrentlyPlaying) {
            b.ivPlayingWave.visibility = View.VISIBLE
            b.ivPlayingWave.imageTintList = ColorStateList.valueOf(activeColor)
            b.tvSongTitle.setTextColor(activeColor)
        } else {
            b.ivPlayingWave.visibility = View.GONE
            b.tvSongTitle.setTextColor(textColorPrimary)
        }

        b.tvSongArtist.setTextColor(textColorSecondary)
        b.tvDuration.setTextColor(textColorSecondary)
        b.tvFormatBadge.setBackgroundColor(formatBadgeBg)
        b.tvFormatBadge.setTextColor(formatBadgeText)
        b.cardSongThumb.setCardBackgroundColor(cardThumbBg)

        val bitrateStr = if (track.bitrateKbps > 0) " ${track.bitrateKbps}k" else ""
        val lyricsStr = if (track.hasLyrics) " • LRC" else ""
        val formatStr = "${track.fileFormat} ${track.bitDepth}/${track.sampleRate / 1000}k$bitrateStr$lyricsStr"
        b.tvFormatBadge.text = formatStr
        b.tvDuration.text = formatDuration(track.durationMs)

        // Rating
        b.tvRating.setTextColor(if (isEinkMode) Color.BLACK else Color.parseColor("#FDE68A"))
        b.tvRating.text = getRatingString(track.rating)
        b.tvRating.setOnClickListener {
            val nextRating = (track.rating + 1) % 6
            onRatingChanged(track, nextRating)
        }

        // Asynchronously load RGB_565 downsampled cover art with job management
        if (b.ivSongThumb.tag != track.path || b.ivSongThumb.drawable == null) {
            holder.loadJob?.cancel()
            b.ivSongThumb.tag = track.path
            b.ivSongThumb.setImageDrawable(null)
            b.ivSongThumb.setPadding(10, 10, 10, 10)
            b.ivSongThumb.setImageResource(android.R.drawable.ic_media_play)
            b.ivSongThumb.imageTintList = ColorStateList.valueOf(if (isEinkMode) Color.BLACK else Color.parseColor("#64748B"))

            holder.loadJob = scope.launch {
                val thumb = imageLoader.loadCover(track.path, 96, 96)
                if (b.ivSongThumb.tag == track.path) {
                    withContext(Dispatchers.Main) {
                        if (b.ivSongThumb.tag == track.path) {
                            if (thumb != null) {
                                b.ivSongThumb.imageTintList = null
                                b.ivSongThumb.setPadding(0, 0, 0, 0)
                                b.ivSongThumb.setImageBitmap(thumb)
                            } else {
                                b.ivSongThumb.setPadding(10, 10, 10, 10)
                                b.ivSongThumb.setImageResource(android.R.drawable.ic_media_play)
                                b.ivSongThumb.imageTintList = ColorStateList.valueOf(if (isEinkMode) Color.BLACK else Color.parseColor("#64748B"))
                            }
                        }
                    }
                }
            }
        }

        // Drag Handle for Mixtape Reordering
        if (isReorderable) {
            b.ivDragHandle.visibility = View.VISIBLE
            b.ivDragHandle.imageTintList = ColorStateList.valueOf(textColorSecondary)
            b.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    onStartDrag?.invoke(holder)
                }
                false
            }
        } else {
            b.ivDragHandle.visibility = View.GONE
            b.ivDragHandle.setOnTouchListener(null)
        }

        val showTrackOptions = {
            val context = holder.itemView.context
            val optionsList = mutableListOf("Play Now", "Play Next", "Add to Queue", "Add to Mixtape", "Inspect & Edit Tags")
            if (isReorderable && onRemoveFromMixtape != null) {
                optionsList.add("Remove from Mixtape")
            }
            android.app.AlertDialog.Builder(context)
                .setTitle(track.title)
                .setItems(optionsList.toTypedArray()) { _, which ->
                    when (which) {
                        0 -> onTrackClicked(track, holder.bindingAdapterPosition)
                        1 -> onPlayNext?.invoke(track)
                        2 -> onAddToQueue?.invoke(track)
                        3 -> onAddToMixtape?.invoke(track)
                        4 -> onInspectTags?.invoke(track)
                        5 -> onRemoveFromMixtape?.invoke(track, holder.bindingAdapterPosition)
                    }
                }
                .show()
        }

        b.btnSongMenu.setOnClickListener { showTrackOptions() }

        holder.itemView.setOnClickListener { onTrackClicked(track, position) }

        holder.itemView.setOnLongClickListener {
            if (!isReorderable) {
                showTrackOptions()
                true
            } else {
                false
            }
        }
    }

    private fun getRatingString(rating: Int): String {
        return if (rating > 0) "$rating/5" else ""
    }

    private fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<TrackEntity>() {
        override fun areItemsTheSame(oldItem: TrackEntity, newItem: TrackEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrackEntity, newItem: TrackEntity): Boolean {
            return oldItem == newItem
        }
    }
}
