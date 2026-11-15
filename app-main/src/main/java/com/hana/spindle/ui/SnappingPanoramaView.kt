package com.hana.spindle.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.HorizontalScrollView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Zune panorama viewport: a [HorizontalScrollView] that always comes to rest on a
 * panel edge. A flick moves exactly one panel in its direction; a slow drag settles
 * on the nearest panel. Snapping stays off until [enableSnapping] is called, so the
 * view behaves like a plain HorizontalScrollView elsewhere.
 *
 * Touches are observed in [dispatchTouchEvent] so drags that begin on child lists
 * (which consume ACTION_DOWN) are still tracked.
 */
class SnappingPanoramaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private var panelWidthPx = 0
    private var panelCount = 0
    private var velocityTracker: VelocityTracker? = null
    private var startPanel = 0
    private val flingThreshold = 400f * resources.displayMetrics.density
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var handedOff = false

    /**
     * When true, a rightward swipe on the first panel is handed to the parent pager
     * (System Hub → deck). Otherwise the panorama keeps every horizontal gesture.
     */
    var handOffAtStart = false

    /** Called with the panel index each time the panorama settles. */
    var onPanelSnapped: ((Int) -> Unit)? = null

    /**
     * Turns on snapping. [trailingSpacePx] is added after the last panel so it can
     * rest flush left like the others (the visible "peek" of the next panel).
     */
    fun enableSnapping(panelWidthPx: Int, panelCount: Int, trailingSpacePx: Int) {
        this.panelWidthPx = panelWidthPx
        this.panelCount = panelCount
        getChildAt(0)?.let { it.setPadding(it.paddingLeft, it.paddingTop, trailingSpacePx, it.paddingBottom) }
    }

    fun scrollToPanel(index: Int, animate: Boolean = true) {
        if (panelWidthPx <= 0) return
        val target = index.coerceIn(0, (panelCount - 1).coerceAtLeast(0)) * panelWidthPx
        if (animate) smoothScrollTo(target, 0) else scrollTo(target, 0)
    }

    private fun nearestPanel(): Int =
        if (panelWidthPx <= 0) 0 else (scrollX.toFloat() / panelWidthPx).roundToInt().coerceIn(0, panelCount - 1)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (panelWidthPx <= 0 || panelCount <= 1) return super.dispatchTouchEvent(ev)

        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain()
            startPanel = nearestPanel()
            downX = ev.x
            downY = ev.y
            handedOff = false
        }
        // Track in parent-relative coordinates so our own scrolling doesn't skew velocity.
        val tracked = MotionEvent.obtain(ev).apply { offsetLocation(left.toFloat(), top.toFloat()) }
        velocityTracker?.addMovement(tracked)
        tracked.recycle()

        if (ev.actionMasked == MotionEvent.ACTION_MOVE && handOffAtStart && !handedOff && scrollX <= 0) {
            val dx = ev.x - downX
            if (dx > touchSlop && dx > abs(ev.y - downY)) handedOff = true
        }

        val handled = super.dispatchTouchEvent(ev)

        // An outer pager (ViewPager2) would otherwise steal horizontal drags before we see them.
        // Re-assert after dispatch because children may clear the flag on their way up.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(!handedOff)
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val vt = velocityTracker
                vt?.computeCurrentVelocity(1000)
                val vx = vt?.xVelocity ?: 0f
                recycleTracker()
                settle(vx)
            }

            MotionEvent.ACTION_CANCEL -> {
                recycleTracker()
                settle(0f)
            }
        }
        return handled
    }

    private fun settle(velocityX: Float) {
        val target = when {
            abs(velocityX) < flingThreshold -> nearestPanel()
            velocityX < 0 -> startPanel + 1 // finger moved left: reveal the next panel
            else -> startPanel - 1
        }.coerceIn(0, panelCount - 1)
        // Posted so it replaces the free fling HorizontalScrollView just started.
        post {
            smoothScrollTo(target * panelWidthPx, 0)
            onPanelSnapped?.invoke(target)
        }
    }

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onDetachedFromWindow() {
        recycleTracker()
        super.onDetachedFromWindow()
    }
}
