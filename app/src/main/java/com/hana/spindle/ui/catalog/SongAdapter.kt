package com.hana.spindle.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.data.db.SongEntity
import com.hana.spindle.databinding.ItemSongBinding
import java.util.Locale
import java.util.concurrent.TimeUnit

class SongAdapter(
    private val onSongClicked: (SongEntity, Int) -> Unit,
    private val onRatingChanged: (SongEntity, Int) -> Unit
) : ListAdapter<SongEntity, SongAdapter.SongViewHolder>(DiffCallback) {

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        val b = holder.binding

        val rawTrack = song.trackNumber
        val trackNum = if (rawTrack > 0) {
            if (rawTrack >= 1000) rawTrack % 1000 else if (rawTrack > 100) rawTrack % 100 else rawTrack
        } else {
            position + 1
        }
        b.tvTrackNumber.text = String.format(Locale.US, "%02d", trackNum)
        b.tvSongTitle.text = song.title
        b.tvSongArtist.text = song.artist

        val formatStr = "${song.fileFormat} ${song.bitDepth}/${song.sampleRate / 1000}k"
        b.tvFormatBadge.text = formatStr
        b.tvDuration.text = formatDuration(song.durationMs)

        // Star rating: 0 to 5 stars
        b.tvRating.text = getStarString(song.rating)
        b.tvRating.setOnClickListener {
            val nextRating = (song.rating + 1) % 6 // Cycles 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 0
            onRatingChanged(song, nextRating)
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
