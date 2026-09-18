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
    var onTouchPositionChanged: ((letter: String, touchY: Float) -> Unit)? = null
    var onTouchActiveChanged: ((Boolean) -> Unit)? = null

    private var selectedIndex = -1

    private val density get() = resources.displayMetrics.density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71717A") // Muted metallic grey
        textSize = 10f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val neighborTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A1A1AA")
        textSize = 13f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 17f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val activePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F97316")
        style = Paint.Style.FILL
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.FILL
    }

    init {
        isClickable = true
    }

    fun updateTheme(textColor: Int, accentColor: Int) {
        textPaint.color = textColor
        neighborTextPaint.color = textColor
        activePillPaint.color = accentColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (h <= 0 || w <= 0) return

        val itemHeight = h / ALPHABET.size
        val cx = w / 2f

        // Draw subtle track when touched
        if (selectedIndex >= 0) {
            val trackR = w * 0.42f
            canvas.drawRoundRect(cx - trackR, 4f, cx + trackR, h - 4f, trackR, trackR, trackPaint)
        }

        for (i in ALPHABET.indices) {
            val letter = ALPHABET[i]
            val cy = i * itemHeight + itemHeight * 0.5f

            when {
                i == selectedIndex -> {
                    // Draw magnified highlight pill behind active letter
                    val pillRadius = (w * 0.55f).coerceAtMost(itemHeight * 1.3f)
                    canvas.drawCircle(cx, cy, pillRadius, activePillPaint)
                    val baseline = cy - (selectedTextPaint.descent() + selectedTextPaint.ascent()) / 2f
                    canvas.drawText(letter, cx, baseline, selectedTextPaint)
                }
                i == selectedIndex - 1 || i == selectedIndex + 1 -> {
                    val baseline = cy - (neighborTextPaint.descent() + neighborTextPaint.ascent()) / 2f
                    canvas.drawText(letter, cx, baseline, neighborTextPaint)
                }
                else -> {
                    val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(letter, cx, baseline, textPaint)
                }
            }
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
        val cy = newIndex * itemHeight + itemHeight * 0.5f
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onLetterSelected?.invoke(ALPHABET[newIndex])
            invalidate()
        }
        onTouchPositionChanged?.invoke(ALPHABET[newIndex], cy)
    }
}
