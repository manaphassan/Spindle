package com.hana.spindle.ui.cassette

import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * Exposes the Canvas-drawn keys, switches, cassette and meter of [WalkmanDeckView]
 * to TalkBack and switch access as individual virtual views.
 */
internal class WalkmanDeckAccessibility(private val deck: WalkmanDeckView) : ExploreByTouchHelper(deck) {

    private val boundsF = RectF()
    private val bounds = Rect()

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val id = deck.hitTest(x, y)
        return if (id >= 0) id else INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        for (id in 0 until WalkmanDeckView.ID_COUNT) virtualViewIds.add(id)
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        node.contentDescription = deck.describe(virtualViewId)
        deck.designBounds(virtualViewId, boundsF).roundOut(bounds)
        @Suppress("DEPRECATION")
        node.setBoundsInParent(bounds)
        node.className = android.widget.Button::class.java.name
        node.isClickable = true
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        if (virtualViewId == WalkmanDeckView.KEY_EJECT || virtualViewId == WalkmanDeckView.ID_TAPE) {
            node.isLongClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
        }
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        return when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK -> {
                deck.performVirtualClick(virtualViewId)
                true
            }

            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> deck.performVirtualLongClick(virtualViewId)
            else -> false
        }
    }
}
