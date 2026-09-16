package com.hana.spindle.ui.catalog

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

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        val b = holder.binding

        b.tvSongTitle.text = song.title
        b.tvSongArtist.text = song.artist

        val isCurrentlyPlaying = song.id == activeSongId
        if (isCurrentlyPlaying) {
            b.tvPlayingWave.visibility = View.VISIBLE
            b.tvSongTitle.setTextColor(Color.parseColor("#00E676"))
        } else {
            b.tvPlayingWave.visibility = View.GONE
            b.tvSongTitle.setTextColor(Color.WHITE)
        }

        val bitrateStr = if (song.bitrateKbps > 0) " ${song.bitrateKbps}k" else ""
        val lyricsStr = if (song.hasLyrics) " • LRC" else ""
        val formatStr = "${song.fileFormat} ${song.bitDepth}/${song.sampleRate / 1000}k$bitrateStr$lyricsStr"
        b.tvFormatBadge.text = formatStr
        b.tvDuration.text = formatDuration(song.durationMs)

        // Star rating
        b.tvRating.text = getStarString(song.rating)
        b.tvRating.setOnClickListener {
            val nextRating = (song.rating + 1) % 6
            onRatingChanged(song, nextRating)
        }

        // Asynchronously load RGB_565 downsampled cover art
        b.ivSongThumb.setImageDrawable(null)
        b.ivSongThumb.setPadding(10, 10, 10, 10)
        b.ivSongThumb.setImageResource(android.R.drawable.ic_media_play)
        scope.launch {
            val thumb = imageLoader.loadCover(song.path, 96, 96)
            if (thumb != null) {
                withContext(Dispatchers.Main) {
                    b.ivSongThumb.setPadding(0, 0, 0, 0)
                    b.ivSongThumb.setImageBitmap(thumb)
                }
            }
        }

        holder.itemView.setOnClickListener { onSongClicked(song, position) }
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
