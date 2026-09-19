package com.hana.spindle.ui.catalog

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hana.spindle.R
import com.hana.spindle.databinding.DialogSortGroupBinding

class SortGroupBottomSheet(
    private val isAlbumTab: Boolean,
    private val currentTrackSort: TrackSortOrder,
    private val currentAlbumSort: AlbumSortOrder,
    private val currentGroupBy: GroupByMode,
    private val onTrackSortSelected: (TrackSortOrder) -> Unit,
    private val onAlbumSortSelected: (AlbumSortOrder) -> Unit,
    private val onGroupBySelected: (GroupByMode) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogSortGroupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSortGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.wm2_red))

        // 1. Setup Sort Options
        if (isAlbumTab) {
            AlbumSortOrder.values().forEachIndexed { index, order ->
                val rb = RadioButton(requireContext()).apply {
                    id = View.generateViewId()
                    text = order.displayName
                    setTextColor(Color.WHITE)
                    textSize = 13.5f
                    buttonTintList = tintList
                    setPadding(12, 10, 0, 10)
                    isChecked = (order == currentAlbumSort)
                }
                binding.radioGroupSort.addView(rb)
                if (order == currentAlbumSort) binding.radioGroupSort.check(rb.id)

                rb.setOnClickListener {
                    onAlbumSortSelected(order)
                }
            }
        } else {
            TrackSortOrder.values().forEachIndexed { index, order ->
                val rb = RadioButton(requireContext()).apply {
                    id = View.generateViewId()
                    text = order.displayName
                    setTextColor(Color.WHITE)
                    textSize = 13.5f
                    buttonTintList = tintList
                    setPadding(12, 10, 0, 10)
                    isChecked = (order == currentTrackSort)
                }
                binding.radioGroupSort.addView(rb)
                if (order == currentTrackSort) binding.radioGroupSort.check(rb.id)

                rb.setOnClickListener {
                    onTrackSortSelected(order)
                }
            }
        }

        // 2. Setup Group By Options
        GroupByMode.values().forEachIndexed { index, mode ->
            val rb = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = mode.displayName
                setTextColor(Color.WHITE)
                textSize = 13.5f
                buttonTintList = tintList
                setPadding(12, 10, 0, 10)
                isChecked = (mode == currentGroupBy)
            }
            binding.radioGroupGroup.addView(rb)
            if (mode == currentGroupBy) binding.radioGroupGroup.check(rb.id)

            rb.setOnClickListener {
                onGroupBySelected(mode)
            }
        }

        binding.btnDoneSort.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
