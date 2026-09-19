package com.hana.spindle.lite.ui

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter

/**
 * High-performance, zero-allocation PagerAdapter for Spindle Lite's 3-screen launcher triad:
 * - Page 0: Left Drawer & App Console
 * - Page 1: Kinetic Cassette Deck (Home)
 * - Page 2: Bauhaus FM Online Radio
 *
 * Keeps pre-inflated views in memory for immediate 60fps tactile swipe transitions on ARMv7.
 */
class LitePagerAdapter(
    val drawerPage: View,
    val playerPage: View,
    val radioPage: View
) : PagerAdapter() {

    private val pages = arrayOf(drawerPage, playerPage, radioPage)

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
            0 -> "DRAWER"
            1 -> "DECK"
            2 -> "RADIO"
            else -> ""
        }
    }
}
