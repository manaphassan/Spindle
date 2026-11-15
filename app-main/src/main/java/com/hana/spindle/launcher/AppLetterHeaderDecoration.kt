package com.hana.spindle.launcher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView

/**
 * Draws an LCD-style letter divider ("C ───") above the first app of each initial.
 * Implemented as a decoration so adapter positions stay 1:1 with the app list, which
 * the A–Z jumps rely on.
 */
class AppLetterHeaderDecoration(
    private val density: Float,
    private val labelAt: (Int) -> String?,
    letterColor: Int,
    private val startInsetPx: Float
) : RecyclerView.ItemDecoration() {

    private val headerHeight = 24f * density
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 11f * density
        color = letterColor
    }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(letterColor, 56)
        strokeWidth = maxOf(1f, density)
    }

    private fun initialOf(position: Int): Char? {
        val ch = labelAt(position)?.trim()?.firstOrNull()?.uppercaseChar() ?: return null
        return if (ch in 'A'..'Z') ch else '#'
    }

    private fun startsGroup(position: Int): Boolean =
        position == 0 || (position > 0 && initialOf(position) != initialOf(position - 1))

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos != RecyclerView.NO_POSITION && startsGroup(pos)) outRect.top = headerHeight.toInt()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val right = parent.width - parent.paddingRight.toFloat()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION || !startsGroup(pos)) continue
            val letter = initialOf(pos)?.toString() ?: continue
            val top = child.top + child.translationY - headerHeight
            val baseline = top + headerHeight * 0.68f
            val x = parent.paddingLeft + startInsetPx
            c.drawText(letter, x, baseline, letterPaint)
            val ruleY = top + headerHeight * 0.5f + density
            c.drawLine(x + letterPaint.measureText(letter) + 8f * density, ruleY, right, ruleY, rulePaint)
        }
    }
}
