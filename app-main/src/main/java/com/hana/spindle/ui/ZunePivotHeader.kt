package com.hana.spindle.ui

import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnPreDraw

/**
 * Zune pivot header strip: the active pivot always sits at the left edge and the
 * others follow in order, wrapping around (e.g. "apps settings sound" when apps is
 * active). Headers slide into place; ones that wrap to the end fade in from the right.
 *
 * Call after the caller has set each tab's colour/alpha for the new state.
 */
object ZunePivotHeader {

    fun arrange(strip: HorizontalScrollView?, bar: LinearLayout, tabsInOrder: List<TextView>, active: Int) {
        if (active !in tabsInOrder.indices) return
        val desired = tabsInOrder.indices.map { tabsInOrder[(active + it) % tabsInOrder.size] }
        // Every header gets the same trailing gap, since any of them can end up in the middle.
        val gap = tabsInOrder.maxOf { (it.layoutParams as? LinearLayout.LayoutParams)?.marginEnd ?: 0 }
        tabsInOrder.forEach { tab ->
            (tab.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                if (lp.marginEnd != gap) {
                    lp.marginEnd = gap; tab.layoutParams = lp
                }
            }
        }
        val current = (0 until bar.childCount).map { bar.getChildAt(it) }
        strip?.scrollTo(0, 0)
        if (current == desired) return

        val wasLaidOut = bar.isLaidOut
        val oldLeft = desired.associateWith { it.left }
        val targetAlpha = desired.associateWith { it.alpha }

        bar.removeAllViews()
        desired.forEach { bar.addView(it) }
        if (!wasLaidOut) return

        val wrapShift = 24f * bar.resources.displayMetrics.density
        bar.doOnPreDraw {
            desired.forEach { tab ->
                val delta = (oldLeft[tab] ?: tab.left) - tab.left
                tab.animate().cancel()
                if (delta < 0) {
                    // Wrapped from the front to the end of the strip.
                    tab.translationX = wrapShift
                    tab.alpha = 0f
                } else {
                    tab.translationX = delta.toFloat()
                }
                tab.animate()
                    .translationX(0f)
                    .alpha(targetAlpha[tab] ?: 1f)
                    .setDuration(240L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }
}
