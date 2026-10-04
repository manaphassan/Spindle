package com.hana.spindle.ui

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hana.spindle.R
import com.hana.spindle.databinding.DialogZuneJumpListBinding

/**
 * Authentic Zune Metro 26-Letter Quick-Jump Grid.
 *
 * Implements the classic Windows Phone / Zune Hub Jump List:
 * - 4x7 grid of square tiles for '#', 'A'..'Z', and '↑' (top)
 * - Highlights available letters that exist in current dataset with the accent color
 * - Inactive/empty letter tiles are dimmed and non-clickable
 * - Single-tap jumps directly to the section and dismisses the grid
 */
class ZuneJumpListDialog : DialogFragment() {

    private var _binding: DialogZuneJumpListBinding? = null
    private val binding get() = _binding!!

    var availableLetters: Set<Char> = emptySet()
    var titleText: String = "jump"
    var subtitleText: String = "tap a letter to jump immediately"
    var accentColor: Int = Color.parseColor("#F97316")
    var onLetterSelected: ((String) -> Unit)? = null

    companion object {
        val TILES = listOf(
            "#", "A", "B", "C",
            "D", "E", "F", "G",
            "H", "I", "J", "K",
            "L", "M", "N", "O",
            "P", "Q", "R", "S",
            "T", "U", "V", "W",
            "X", "Y", "Z", "↑"
        )

        fun show(
            fragmentManager: FragmentManager,
            availableLetters: Set<Char>,
            title: String = "jump",
            subtitle: String = "tap a letter to jump immediately",
            accentColor: Int = Color.parseColor("#F97316"),
            onLetterSelected: (String) -> Unit
        ): ZuneJumpListDialog {
            val dialog = ZuneJumpListDialog()
            dialog.availableLetters = availableLetters
            dialog.titleText = title
            dialog.subtitleText = subtitle
            dialog.accentColor = accentColor
            dialog.onLetterSelected = onLetterSelected
            dialog.show(fragmentManager, "ZuneJumpListDialog")
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
        _binding = DialogZuneJumpListBinding.inflate(inflater, container, false)
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

        binding.rvJumpGrid.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.rvJumpGrid.adapter = JumpTileAdapter()

        // Subtle entrance fade animation
        binding.root.alpha = 0f
        binding.root.scaleX = 0.96f
        binding.root.scaleY = 0.96f
        binding.root.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(160)
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class JumpTileAdapter : RecyclerView.Adapter<JumpTileAdapter.TileViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_zune_jump_tile, parent, false)
            return TileViewHolder(view)
        }

        override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
            val item = TILES[position]
            holder.bind(item)
        }

        override fun getItemCount(): Int = TILES.size

        inner class TileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvJumpLetter: TextView = itemView.findViewById(R.id.tvJumpLetter)

            fun bind(item: String) {
                tvJumpLetter.text = item

                val isActive = when {
                    item == "↑" -> true // "To Top" is always active
                    item == "#" -> availableLetters.contains('#')
                    else -> {
                        val ch = item.firstOrNull()?.uppercaseChar()
                        ch != null && (availableLetters.contains(ch) || availableLetters.contains(ch.lowercaseChar()))
                    }
                }

                if (isActive) {
                    tvJumpLetter.setBackgroundResource(R.drawable.bg_metro_jump_tile_active)
                    tvJumpLetter.backgroundTintList = ColorStateList.valueOf(accentColor)
                    tvJumpLetter.setTextColor(Color.WHITE)
                    tvJumpLetter.alpha = 1.0f
                    itemView.isClickable = true
                    itemView.isFocusable = true
                    itemView.setOnClickListener {
                        itemView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onLetterSelected?.invoke(item)
                        dismiss()
                    }
                } else {
                    tvJumpLetter.setBackgroundResource(R.drawable.bg_metro_jump_tile_inactive)
                    tvJumpLetter.backgroundTintList = null
                    tvJumpLetter.setTextColor(Color.parseColor("#4B5563"))
                    tvJumpLetter.alpha = 0.65f
                    itemView.isClickable = false
                    itemView.isFocusable = false
                    itemView.setOnClickListener(null)
                }
            }
        }
    }
}
