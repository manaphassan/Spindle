package com.hana.spindle.ui.radio

import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

/** Exposes the Canvas-drawn controls of [RadioDeckView] to TalkBack and switch access. */
internal class RadioDeckAccessibility(private val deck: RadioDeckView) : ExploreByTouchHelper(deck) {

    private val boundsF = RectF()
    private val bounds = Rect()

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val id = deck.hitTest(x, y)
        return if (id >= 0) id else INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        for (id in 0 until RadioDeckView.ID_COUNT) virtualViewIds.add(id)
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        node.contentDescription = deck.describe(virtualViewId)
        deck.designBounds(virtualViewId, boundsF).roundOut(bounds)
        @Suppress("DEPRECATION")
        node.setBoundsInParent(bounds)
        val enabled = deck.isEnabledTarget(virtualViewId)
        node.isEnabled = enabled
        if (deck.isTunable(virtualViewId)) {
            node.className = android.widget.SeekBar::class.java.name
            if (enabled) {
                node.isScrollable = true
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
            }
            return
        }
        node.className = android.widget.Button::class.java.name
        if (!enabled) return
        node.isClickable = true
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        if (virtualViewId in RadioDeckView.ID_KEY_FIRST until RadioDeckGeometry.KEY_SCAN && !deck.model.isNet) {
            node.isLongClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
        }
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        return when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK -> {
                deck.performVirtualClick(virtualViewId); true
            }

            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> deck.performVirtualLongClick(virtualViewId)
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> {
                if (!deck.isTunable(virtualViewId)) return false
                deck.stepTuning(action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
                invalidateVirtualView(virtualViewId)
                true
            }

            else -> false
        }
    }
}
