package com.hana.spindle.lite.ui

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter

/**
 * High-performance, zero-allocation PagerAdapter for Spindle Lite's 3-screen launcher triad:
 * - Page 0: Bauhaus FM Online Radio & Analog Tuner (Left Screen)
 * - Page 1: Kinetic Cassette Deck (Center Screen / Home)
 * - Page 2: System Hub & App Drawer (Right Screen)
 *
 * Keeps pre-inflated views in memory for immediate 60fps tactile swipe transitions on ARMv7.
 */
class LitePagerAdapter(
    val radioPage: View,
    val playerPage: View,
    val drawerPage: View
) : PagerAdapter() {

    private val pages = arrayOf(radioPage, playerPage, drawerPage)

    override fun getCount(): Int = pages.size

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = pages[position]
        if (view.parent == null) {
            container.addView(view)
        }
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        // Retain view in container or remove if requested by ViewPager
        container.removeView(`object` as View)
    }

    override fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> "RADIO"
            1 -> "DECK"
            2 -> "SYSTEM"
            else -> ""
        }
    }
}
