package com.hana.spindle.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.hana.spindle.ui.cassette.WalkmanColorway
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Walkman-style header for the System page: the SPINDLE band with a RUN lamp, and a
 * FUNCTION slide switch (SOUND / SETTINGS / APPS) that replaces the Zune pivot strip.
 * The knob tracks [pagePosition] so it slides along while the panorama is swiped.
 *
 * Drawn on a 360 x 104 dp design canvas scaled to the view width.
 */
class SystemDeckHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DESIGN_W = 360f
        private const val DESIGN_H = 104f
        private const val TRACK_X = 16f
        private const val TRACK_W = 328f
        private val TANGERINE = Color.parseColor("#F97316")
        private val EMERALD = Color.parseColor("#00E676")
        private val LAMP_OFF = Color.parseColor("#2C3A33")
        private val HALO = Color.argb(46, 0, 230, 118)
    }

    var labels: List<String> = listOf("SOUND", "SETTINGS", "APPS")
        set(value) {
            field = value; invalidate(); accessibility.invalidateRoot()
        }

    var colorway: WalkmanColorway = WalkmanColorway.SILVER
        set(value) {
            if (field != value) {
                field = value; invalidate()
            }
        }

    /** Fractional page index (0 = first stop). Ignored while the knob is being dragged. */
    var pagePosition: Float = 0f
        set(value) {
            val v = value.coerceIn(0f, (labels.size - 1).toFloat())
            if (field == v) return
            val oldStop = field.roundToInt()
            field = v
            if (dragX == null) invalidate()
            if (oldStop != v.roundToInt()) accessibility.invalidateRoot()
        }

    var isRunning: Boolean = false
        set(value) {
            if (field != value) {
                field = value; invalidate()
            }
        }

    var onPageSelected: ((Int) -> Unit)? = null

    private var scale = 1f
    private var dragX: Float? = null
    private var dragStop = -1

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val print = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 8f
        letterSpacing = 0.15f
    }
    private val stop = Paint(print).apply { textSize = 9f; textAlign = Paint.Align.CENTER }
    private val wordmark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 17f
        letterSpacing = 0.3f
        isFakeBoldText = true
    }
    private val r = RectF()
    private val accessibility = Accessibility()

    init {
        isClickable = true
        ViewCompat.setAccessibilityDelegate(this, accessibility)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w / DESIGN_W * DESIGN_H).roundToInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scale = w / DESIGN_W
    }

    private val step get() = TRACK_W / labels.size

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cw = colorway
        canvas.save()
        canvas.scale(scale, scale)

        fill.color = cw.band
        canvas.drawRect(0f, 0f, DESIGN_W, 44f, fill)
        wordmark.color = cw.wordmark
        canvas.drawText("SPINDLE", 16f, 29f, wordmark)
        fill.color = TANGERINE
        val dotX = 16f + wordmark.measureText("SPINDLE") + 1f
        canvas.drawRect(dotX, 24f, dotX + 5f, 29f, fill)
        text(canvas, "SYSTEM TOOLS", 344f, 20f, cw.bandSub, Paint.Align.RIGHT)
        text(canvas, "RUN", 344f, 34f, cw.bandSub, Paint.Align.RIGHT)
        if (isRunning) {
            fill.color = HALO; canvas.drawCircle(316f, 31f, 6f, fill)
        }
        fill.color = if (isRunning) EMERALD else LAMP_OFF
        canvas.drawCircle(316f, 31f, 3f, fill)

        text(canvas, "FUNCTION", TRACK_X, 62f, cw.print, Paint.Align.LEFT)
        val pos = dragX?.let { knobIndexAt(it) } ?: pagePosition
        val active = pos.roundToInt()
        labels.forEachIndexed { i, label ->
            stop.color = if (i == active) cw.print else cw.printMuted
            stop.isFakeBoldText = i == active
            canvas.drawText(label, TRACK_X + step * i + step / 2f, 75f, stop)
        }
        fill.color = cw.recess
        r.set(TRACK_X, 80f, TRACK_X + TRACK_W, 96f)
        canvas.drawRoundRect(r, 8f, 8f, fill)

        val kx = TRACK_X + step * pos + 3f
        val kw = step - 6f
        fill.color = cw.knob
        r.set(kx, 78f, kx + kw, 98f)
        canvas.drawRoundRect(r, 4f, 4f, fill)
        stroke.color = cw.knobGroove
        stroke.strokeWidth = 0.8f
        canvas.drawRoundRect(r, 4f, 4f, stroke)
        stroke.strokeWidth = 1f
        for (g in -2..2) {
            val gx = kx + kw / 2f + g * 4f
            canvas.drawLine(gx, 82f, gx, 94f, stroke)
        }
        canvas.restore()
    }

    private fun text(c: Canvas, t: String, x: Float, y: Float, color: Int, align: Paint.Align) {
        print.color = color
        print.textAlign = align
        c.drawText(t, x, y, print)
    }

    /** Fractional knob index for a finger at design x, centred under the finger. */
    private fun knobIndexAt(x: Float): Float =
        ((x - TRACK_X) / step - 0.5f).coerceIn(0f, (labels.size - 1).toFloat())

    private fun stopAt(x: Float): Int = ((x - TRACK_X) / step).toInt().coerceIn(0, labels.size - 1)

    private fun inSwitch(y: Float) = y >= 52f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x / scale
        val y = event.y / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!inSwitch(y)) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                dragX = x
                dragStop = stopAt(x)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragX == null) return false
                dragX = x
                val s = knobIndexAt(x).roundToInt()
                if (s != dragStop) {
                    dragStop = s
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val startX = dragX ?: return false
                dragX = null
                parent?.requestDisallowInterceptTouchEvent(false)
                // A tap jumps to the tapped stop; a drag lands where the knob was let go.
                val moved = abs(x - startX) > 4f
                val target = if (moved) knobIndexAt(x).roundToInt() else stopAt(x)
                select(target)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragX = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun select(index: Int) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        pagePosition = index.toFloat()
        invalidate()
        onPageSelected?.invoke(index)
        accessibility.invalidateRoot()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    private inner class Accessibility : ExploreByTouchHelper(this@SystemDeckHeaderView) {
        private val rf = RectF()
        private val rect = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int =
            if (inSwitch(y / scale)) stopAt(x / scale) else INVALID_ID

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            labels.indices.forEach { virtualViewIds.add(it) }
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val selected = virtualViewId == pagePosition.roundToInt()
            node.contentDescription = labels[virtualViewId].lowercase()
            node.roleDescription = "tab"
            node.isSelected = selected
            node.isClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            rf.set(TRACK_X + step * virtualViewId, 52f, TRACK_X + step * (virtualViewId + 1), DESIGN_H)
            rect.set(
                (rf.left * scale).toInt(), (rf.top * scale).toInt(),
                (rf.right * scale).toInt(), (rf.bottom * scale).toInt()
            )
            @Suppress("DEPRECATION")
            node.setBoundsInParent(rect)
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            select(virtualViewId)
            return true
        }
    }
}
