package com.hana.spindle.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.data.LyricLine
import com.hana.spindle.databinding.ItemLyricLineBinding

class LyricsAdapter(
    private val onLineClicked: (Long) -> Unit
) : RecyclerView.Adapter<LyricsAdapter.LyricViewHolder>() {

    var lines: List<LyricLine> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var activeIndex: Int = -1
        set(value) {
            if (field != value) {
                val prev = field
                field = value
                if (prev in lines.indices) notifyItemChanged(prev)
                if (value in lines.indices) notifyItemChanged(value)
            }
        }

    var accentColor: Int = Color.parseColor("#00E676")

    class LyricViewHolder(val binding: ItemLyricLineBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
        val binding = ItemLyricLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LyricViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
        val line = lines[position]
        val b = holder.binding
        val isActive = position == activeIndex

        b.tvLyricText.text = line.text

        if (isActive) {
            b.tvLyricText.setTextColor(accentColor)
            b.tvLyricText.textSize = 17.5f
            b.tvLyricText.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            b.tvLyricText.alpha = 1.0f
        } else {
            b.tvLyricText.setTextColor(Color.WHITE)
            b.tvLyricText.textSize = 14f
            b.tvLyricText.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            b.tvLyricText.alpha = 0.45f
        }

        holder.itemView.setOnClickListener {
            onLineClicked(line.timeMs)
        }
    }

    override fun getItemCount(): Int = lines.size
}
