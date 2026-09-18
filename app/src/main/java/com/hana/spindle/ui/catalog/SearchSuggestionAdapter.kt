package com.hana.spindle.ui.catalog

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.R
import com.hana.spindle.data.db.AlbumItem
import com.hana.spindle.data.db.SongEntity
import com.hana.spindle.databinding.ItemSearchSuggestionBinding

enum class SuggestionType {
    TRACK,
    ALBUM,
    ARTIST
}

data class SearchSuggestion(
    val title: String,
    val subtitle: String,
    val type: SuggestionType,
    val song: SongEntity? = null,
    val album: AlbumItem? = null
)

class SearchSuggestionAdapter(
    private val onSuggestionClicked: (SearchSuggestion) -> Unit
) : ListAdapter<SearchSuggestion, SearchSuggestionAdapter.SuggestionViewHolder>(DiffCallback) {

    private var textColorPrimary: Int = Color.WHITE
    private var textColorSecondary: Int = Color.parseColor("#94A3B8")
    private var accentColor: Int = Color.parseColor("#F97316")
    private var isEinkMode: Boolean = false

    fun updateThemeColors(primary: Int, secondary: Int, accent: Int, isEink: Boolean) {
        this.textColorPrimary = primary
        this.textColorSecondary = secondary
        this.accentColor = accent
        this.isEinkMode = isEink
        notifyDataSetChanged()
    }

    class SuggestionViewHolder(val binding: ItemSearchSuggestionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemSearchSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        b.tvSuggestionTitle.text = item.title
        b.tvSuggestionTitle.setTextColor(textColorPrimary)
        b.tvSuggestionSubtitle.text = item.subtitle
        b.tvSuggestionSubtitle.setTextColor(textColorSecondary)

        b.tvSuggestionType.text = item.type.name
        if (isEinkMode) {
            b.tvSuggestionType.setTextColor(Color.BLACK)
            b.tvSuggestionType.setBackgroundColor(Color.WHITE)
            b.ivSuggestionIcon.imageTintList = ColorStateList.valueOf(Color.BLACK)
        } else {
            b.tvSuggestionType.setTextColor(accentColor)
            b.tvSuggestionType.setBackgroundColor(Color.parseColor("#151722"))
            b.ivSuggestionIcon.imageTintList = ColorStateList.valueOf(textColorSecondary)
        }

        val iconRes = when (item.type) {
            SuggestionType.TRACK -> android.R.drawable.ic_media_play
            SuggestionType.ALBUM -> android.R.drawable.ic_menu_agenda
            SuggestionType.ARTIST -> android.R.drawable.ic_menu_myplaces
        }
        b.ivSuggestionIcon.setImageResource(iconRes)

        holder.itemView.setOnClickListener {
            onSuggestionClicked(item)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SearchSuggestion>() {
        override fun areItemsTheSame(oldItem: SearchSuggestion, newItem: SearchSuggestion): Boolean {
            return oldItem.title == newItem.title && oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: SearchSuggestion, newItem: SearchSuggestion): Boolean {
            return oldItem == newItem
        }
    }
}
