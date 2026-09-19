package com.hana.spindle.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.databinding.ItemFolderBinding

data class FolderItem(
    val name: String,
    val path: String,
    val songCount: Int
)

class FolderAdapter(
    private val onFolderClicked: (FolderItem) -> Unit
) : ListAdapter<FolderItem, FolderAdapter.FolderViewHolder>(DiffCallback) {

    private var textColorPrimary: Int = android.graphics.Color.WHITE
    private var textColorSecondary: Int = android.graphics.Color.parseColor("#94A3B8")

    fun updateThemeColors(primary: Int, secondary: Int) {
        this.textColorPrimary = primary
        this.textColorSecondary = secondary
        notifyDataSetChanged()
    }

    class FolderViewHolder(val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = getItem(position)
        holder.binding.tvFolderName.text = folder.name
        holder.binding.tvFolderName.setTextColor(textColorPrimary)
        holder.binding.tvFolderPath.text = folder.path
        holder.binding.tvFolderPath.setTextColor(textColorSecondary)
        holder.binding.tvFolderCount.text = "${folder.songCount} songs"
        holder.binding.tvFolderCount.setTextColor(textColorSecondary)
        holder.itemView.setOnClickListener { onFolderClicked(folder) }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FolderItem>() {
        override fun areItemsTheSame(oldItem: FolderItem, newItem: FolderItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: FolderItem, newItem: FolderItem): Boolean {
            return oldItem == newItem
        }
    }
}
