package com.hana.spindle.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.databinding.ItemAppBinding
import com.hana.spindle.databinding.ItemAppGridBinding

/**
 * Universal adapter for the Apps Drawer supporting both List and Grid presentation modes.
 */
class AppListAdapter(
    private val onAppClicked: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.BaseAppViewHolder>(DiffCallback) {

    companion object {
        const val VIEW_TYPE_LIST = 0
        const val VIEW_TYPE_GRID = 1

        object DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem.packageName == newItem.packageName && oldItem.activityName == newItem.activityName
            }

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem.label == newItem.label
            }
        }
    }

    var isGridMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    sealed class BaseAppViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        abstract fun bind(app: AppInfo, onAppClicked: (AppInfo) -> Unit)
    }

    class ListViewHolder(private val binding: ItemAppBinding) : BaseAppViewHolder(binding.root) {
        override fun bind(app: AppInfo, onAppClicked: (AppInfo) -> Unit) {
            binding.tvAppLabel.text = app.label
            binding.ivAppIcon.setImageDrawable(app.icon)
            itemView.setOnClickListener { onAppClicked(app) }
        }
    }

    class GridViewHolder(private val binding: ItemAppGridBinding) : BaseAppViewHolder(binding.root) {
        override fun bind(app: AppInfo, onAppClicked: (AppInfo) -> Unit) {
            binding.tvAppLabel.text = app.label
            binding.ivAppIcon.setImageDrawable(app.icon)
            itemView.setOnClickListener { onAppClicked(app) }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseAppViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GRID) {
            GridViewHolder(ItemAppGridBinding.inflate(inflater, parent, false))
        } else {
            ListViewHolder(ItemAppBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: BaseAppViewHolder, position: Int) {
        holder.bind(getItem(position), onAppClicked)
    }
}
