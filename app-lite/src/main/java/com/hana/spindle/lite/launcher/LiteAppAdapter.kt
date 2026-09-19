package com.hana.spindle.lite.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SectionIndexer
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.lite.R

class LiteAppAdapter(
    private var allApps: List<LiteAppInfo>,
    private val onAppClick: (LiteAppInfo) -> Unit
) : RecyclerView.Adapter<LiteAppAdapter.AppViewHolder>(), SectionIndexer {

    private var displayedApps: List<LiteAppInfo> = allApps
    private var sections: Array<String> = emptyArray()
    private var sectionPositions: IntArray = IntArray(0)

    init {
        rebuildSections()
    }

    fun updateApps(apps: List<LiteAppInfo>) {
        allApps = apps
        displayedApps = apps
        rebuildSections()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        displayedApps = if (query.isBlank()) {
            allApps
        } else {
            val q = query.trim().lowercase()
            allApps.filter { it.label.lowercase().contains(q) }
        }
        rebuildSections()
        notifyDataSetChanged()
    }

    private fun rebuildSections() {
        val sectionList = mutableListOf<String>()
        val posList = mutableListOf<Int>()
        var currentSection = ""

        displayedApps.forEachIndexed { index, app ->
            val firstChar = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            val section = if (firstChar in "A".."Z") firstChar else "#"
            if (section != currentSection) {
                currentSection = section
                sectionList.add(section)
                posList.add(index)
            }
        }

        sections = sectionList.toTypedArray()
        sectionPositions = posList.toIntArray()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lite_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = displayedApps[position]
        holder.bind(app, onAppClick)
    }

    override fun getItemCount(): Int = displayedApps.size

    override fun getSections(): Array<Any> = sections as Array<Any>

    override fun getPositionForSection(sectionIndex: Int): Int {
        if (sectionIndex < 0 || sectionIndex >= sectionPositions.size) return 0
        return sectionPositions[sectionIndex]
    }

    override fun getSectionForPosition(position: Int): Int {
        if (position < 0 || position >= displayedApps.size) return 0
        for (i in sectionPositions.indices.reversed()) {
            if (position >= sectionPositions[i]) return i
        }
        return 0
    }

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvAppName)

        fun bind(app: LiteAppInfo, onClick: (LiteAppInfo) -> Unit) {
            tvName.text = app.label
            if (app.icon != null) {
                ivIcon.setImageDrawable(app.icon)
            } else {
                ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            itemView.setOnClickListener { onClick(app) }
        }
    }
}
