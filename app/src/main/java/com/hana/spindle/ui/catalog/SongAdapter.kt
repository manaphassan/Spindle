package com.hana.spindle.ui.catalog

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.R
import com.hana.spindle.data.ImageLoader
import com.hana.spindle.data.db.SongEntity
import com.hana.spindle.databinding.ItemSongBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class SongAdapter(
    private val imageLoader: ImageLoader,
    private val onSongClicked: (SongEntity, Int) -> Unit,
    private val onRatingChanged: (SongEntity, Int) -> Unit
) : ListAdapter<SongEntity, SongAdapter.SongViewHolder>(DiffCallback) {

    private val scope = CoroutineScope(Dispatchers.Main)
    var activeSongId: Long? = null
    var showTrackNumbers: Boolean = false
    var onPlayNext: ((SongEntity) -> Unit)? = null
    var onAddToQueue: ((SongEntity) -> Unit)? = null
    var onAddToMixtape: ((SongEntity) -> Unit)? = null
    var onInspectTags: ((SongEntity) -> Unit)? = null

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

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        val b = holder.binding

        if (showTrackNumbers) {
            b.tvTrackNumber.visibility = View.VISIBLE
            val rawTrack = song.trackNumber
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

        b.tvSongTitle.text = song.title
        b.tvSongArtist.text = song.artist

        val isCurrentlyPlaying = song.id == activeSongId
        val activeColor = if (isEinkMode) Color.BLACK else Color.parseColor("#F97316")
        if (isCurrentlyPlaying) {
            b.tvPlayingWave.visibility = View.VISIBLE
            b.tvPlayingWave.setTextColor(activeColor)
            b.tvSongTitle.setTextColor(activeColor)
        } else {
            b.tvPlayingWave.visibility = View.GONE
            b.tvSongTitle.setTextColor(textColorPrimary)
        }

        b.tvSongArtist.setTextColor(textColorSecondary)
        b.tvDuration.setTextColor(textColorSecondary)
        b.tvFormatBadge.setBackgroundColor(formatBadgeBg)
        b.tvFormatBadge.setTextColor(formatBadgeText)
        b.cardSongThumb.setCardBackgroundColor(cardThumbBg)

        val bitrateStr = if (song.bitrateKbps > 0) " ${song.bitrateKbps}k" else ""
        val lyricsStr = if (song.hasLyrics) " • LRC" else ""
        val formatStr = "${song.fileFormat} ${song.bitDepth}/${song.sampleRate / 1000}k$bitrateStr$lyricsStr"
        b.tvFormatBadge.text = formatStr
        b.tvDuration.text = formatDuration(song.durationMs)

        // Star rating
        b.tvRating.setTextColor(if (isEinkMode) Color.BLACK else Color.parseColor("#FDE68A"))
        b.tvRating.text = getStarString(song.rating)
        b.tvRating.setOnClickListener {
            val nextRating = (song.rating + 1) % 6
            onRatingChanged(song, nextRating)
        }

        // Asynchronously load RGB_565 downsampled cover art
        b.ivSongThumb.setImageDrawable(null)
        b.ivSongThumb.setPadding(10, 10, 10, 10)
        b.ivSongThumb.setImageResource(android.R.drawable.ic_media_play)
        b.ivSongThumb.imageTintList = ColorStateList.valueOf(if (isEinkMode) Color.BLACK else Color.parseColor("#64748B"))
        scope.launch {
            val thumb = imageLoader.loadCover(song.path, 96, 96)
            withContext(Dispatchers.Main) {
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

        holder.itemView.setOnClickListener { onSongClicked(song, position) }

        holder.itemView.setOnLongClickListener {
            val context = holder.itemView.context
            val options = arrayOf("▶ Play Now", "⏭ Play Next", "➕ Add to Queue", "📼 Add to Mixtape", "🏷️ Inspect & Edit Tags")
            android.app.AlertDialog.Builder(context)
                .setTitle(song.title)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> onSongClicked(song, holder.bindingAdapterPosition)
                        1 -> onPlayNext?.invoke(song)
                        2 -> onAddToQueue?.invoke(song)
                        3 -> onAddToMixtape?.invoke(song)
                        4 -> onInspectTags?.invoke(song)
                    }
                }
                .show()
            true
        }
    }

    private fun getStarString(rating: Int): String {
        return buildString {
            for (i in 1..5) {
                append(if (i <= rating) "★" else "☆")
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SongEntity>() {
        override fun areItemsTheSame(oldItem: SongEntity, newItem: SongEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SongEntity, newItem: SongEntity): Boolean {
            return oldItem == newItem
        }
    }
}
