package com.hana.spindle.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * High-performance, tactile Alphabet Quick-Scroll Rail.
 *
 * Renders '#', 'A' through 'Z' with Swiss minimalist typography.
 * Supports fluid vertical drag tracking with mechanical clock-tick haptics
 * and zero object allocations in onDraw() for 60fps responsiveness on low-RAM DAPs.
 */
class AlphabetIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        val ALPHABET = arrayOf(
            "#", "A", "B", "C", "D", "E", "F", "G", "H", "I",
            "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S",
            "T", "U", "V", "W", "X", "Y", "Z"
        )
    }

    var onLetterSelected: ((String) -> Unit)? = null
    var onTouchActiveChanged: ((Boolean) -> Unit)? = null

    private var selectedIndex = -1

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71717A") // Muted metallic grey
        textSize = 10f * resources.displayMetrics.density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935") // Walkman Red accent
        textSize = 11f * resources.displayMetrics.density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (h <= 0 || w <= 0) return

        val itemHeight = h / ALPHABET.size
        val cx = w / 2f

        for (i in ALPHABET.indices) {
            val letter = ALPHABET[i]
            val cy = i * itemHeight + itemHeight * 0.5f

            val paint = if (i == selectedIndex) selectedTextPaint else textPaint
            // Vertically center text
            val baseline = cy - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(letter, cx, baseline, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val h = height.toFloat()
        if (h <= 0) return super.onTouchEvent(event)

        val itemHeight = h / ALPHABET.size

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onTouchActiveChanged?.invoke(true)
                updateSelection(event.y, itemHeight)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateSelection(event.y, itemHeight)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                selectedIndex = -1
                onTouchActiveChanged?.invoke(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSelection(y: Float, itemHeight: Float) {
        val newIndex = (y / itemHeight).toInt().coerceIn(0, ALPHABET.size - 1)
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onLetterSelected?.invoke(ALPHABET[newIndex])
            invalidate()
        }
    }
}
