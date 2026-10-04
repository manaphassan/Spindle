package com.hana.spindle.lite.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.lite.R
import com.hana.spindle.lite.databinding.DialogLiteZuneJumpListBinding

/**
 * Pure Kotlin Zune Metro jump list helper containing tile definitions and letter matching logic.
 */
object LiteZuneJumpList {
    val TILES = listOf(
        "#", "A", "B", "C",
        "D", "E", "F", "G",
        "H", "I", "J", "K",
        "L", "M", "N", "O",
        "P", "Q", "R", "S",
        "T", "U", "V", "W",
        "X", "Y", "Z", "↑"
    )

    fun isLetterAvailable(tile: String, availableLetters: Set<Char>): Boolean {
        return when (tile) {
            "↑" -> true
            "#" -> availableLetters.any { !it.isLetter() }
            else -> tile.firstOrNull()?.let { availableLetters.contains(it) } ?: false
        }
    }

    fun findSectionIndex(tile: String, labels: List<String>): Int {
        return if (tile == "↑") {
            0
        } else {
            labels.indexOfFirst {
                val ch = it.firstOrNull()?.uppercaseChar() ?: '#'
                if (tile == "#") !ch.isLetter() else ch == tile[0]
            }
        }
    }
}

/**
 * Authentic Zune Metro 26-Letter Quick-Jump Grid for Spindle Lite.
 *
 * Implements the classic Windows Phone / Zune Hub Jump List:
 * - 4x7 grid of square tiles for '#', 'A'..'Z', and '↑' (top)
 * - Highlights available letters that exist in current dataset with accent color
 * - Inactive/empty letter tiles are dimmed and non-clickable
 * - Single-tap jumps directly to the section and dismisses the grid
 * - Ultra-low heap footprint, fully compatible with KitKat 4.4 (API 19)
 */
class LiteZuneJumpListDialog : DialogFragment() {

    private var _binding: DialogLiteZuneJumpListBinding? = null
    private val binding get() = _binding!!

    var availableLetters: Set<Char> = emptySet()
    var titleText: String = "jump"
    var subtitleText: String = "tap a letter to jump immediately"
    var onLetterSelected: ((String) -> Unit)? = null

    companion object {
        val TILES get() = LiteZuneJumpList.TILES

        fun show(
            fragmentManager: FragmentManager,
            availableLetters: Set<Char>,
            title: String = "jump",
            subtitle: String = "tap a letter to jump immediately",
            onLetterSelected: (String) -> Unit
        ): LiteZuneJumpListDialog {
            val dialog = LiteZuneJumpListDialog()
            dialog.availableLetters = availableLetters
            dialog.titleText = title
            dialog.subtitleText = subtitle
            dialog.onLetterSelected = onLetterSelected
            dialog.show(fragmentManager, "LiteZuneJumpListDialog")
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLiteZuneJumpListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvJumpTitle.text = titleText
        binding.tvJumpSubtitle.text = subtitleText
        binding.btnJumpClose.setOnClickListener {
            dismiss()
        }

        binding.rvJumpGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = JumpTileAdapter()
            setHasFixedSize(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class JumpTileAdapter : RecyclerView.Adapter<JumpTileAdapter.TileViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lite_zune_jump_tile, parent, false)
            return TileViewHolder(view)
        }

        override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
            val label = TILES[position]
            val isAvailable = LiteZuneJumpList.isLetterAvailable(label, availableLetters)
            holder.bind(label, isAvailable)
        }

        override fun getItemCount(): Int = TILES.size

        inner class TileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvLetter: TextView = itemView.findViewById(R.id.tvJumpLetter)

            fun bind(label: String, isAvailable: Boolean) {
                tvLetter.text = label
                val context = itemView.context

                val activeBgColor = ContextCompat.getColor(context, R.color.brand_orange)
                val activeTextColor = ContextCompat.getColor(context, R.color.lite_metro_text_primary)
                val inactiveBgColor = ContextCompat.getColor(context, R.color.lite_metro_tile_inactive)
                val inactiveTextColor = ContextCompat.getColor(context, R.color.lite_metro_tile_inactive_text)

                if (isAvailable) {
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(activeBgColor)
                        cornerRadius = 4f
                    }
                    tvLetter.background = shape
                    tvLetter.setTextColor(activeTextColor)
                    tvLetter.alpha = 1.0f
                    itemView.isEnabled = true
                    itemView.setOnClickListener {
                        itemView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onLetterSelected?.invoke(label)
                        dismiss()
                    }
                } else {
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(inactiveBgColor)
                        cornerRadius = 4f
                    }
                    tvLetter.background = shape
                    tvLetter.setTextColor(inactiveTextColor)
                    tvLetter.alpha = 0.45f
                    itemView.isEnabled = false
                    itemView.setOnClickListener(null)
                }
            }
        }
    }
}
