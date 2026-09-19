package com.hana.spindle.lite.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.hana.spindle.lite.R

/**
 * Tactile horizontal analog radio tuning dial view.
 * Renders calibrated FM frequency tick marks (88.0 - 108.0 MHz) with center indicator cursor.
 */
class LiteRadioDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onFrequencyChanged: ((Float) -> Unit)? = null
    var onFrequencySelected: ((Float) -> Unit)? = null

    var minFrequency: Float = 87.5f
    var maxFrequency: Float = 108.0f
    var stepSize: Float = 0.1f

    var currentFrequency: Float = 88.5f
        set(value) {
            val clamped = value.coerceIn(minFrequency, maxFrequency)
            if (field != clamped) {
                field = clamped
                onFrequencyChanged?.invoke(field)
                invalidate()
            }
        }

    fun setBandLimits(minFreq: Float, maxFreq: Float, step: Float) {
        minFrequency = minFreq
        maxFrequency = maxFreq
        stepSize = step
        currentFrequency = currentFrequency.coerceIn(minFrequency, maxFrequency)
        invalidate()
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val boundsRect = RectF()
    private var lastTouchX = 0f

    init {
        val cBg = ContextCompat.getColor(context, R.color.lite_bg_surface)
        val cBorder = ContextCompat.getColor(context, R.color.lite_border)
        val cText = ContextCompat.getColor(context, R.color.lite_text_muted)
        val cCursor = ContextCompat.getColor(context, R.color.brand_orange)

        bgPaint.color = cBg
        borderPaint.color = cBorder
        majorTickPaint.color = ContextCompat.getColor(context, R.color.lite_text_secondary)
        minorTickPaint.color = cBorder
        textPaint.color = cText
        cursorPaint.color = cCursor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        boundsRect.set(2f, 2f, w - 2f, h - 2f)
        textPaint.textSize = h * 0.28f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val radius = 8f
        canvas.drawRoundRect(boundsRect, radius, radius, bgPaint)
        canvas.drawRoundRect(boundsRect, radius, radius, borderPaint)

        val cx = w / 2f
        val pixelsPerMhz = w / 6.0f // Visible window covers ~6 MHz

        // Draw frequency ticks
        val minVisibleFreq = currentFrequency - 3.5f
        val maxVisibleFreq = currentFrequency + 3.5f

        val startFreq10 = (minVisibleFreq * 10).toInt()
        val endFreq10 = (maxVisibleFreq * 10).toInt()

        for (f10 in startFreq10..endFreq10) {
            val freq = f10 / 10.0f
            if (freq < minFrequency || freq > maxFrequency) continue

            val x = cx + ((freq - currentFrequency) * pixelsPerMhz)
            if (x < boundsRect.left + 4 || x > boundsRect.right - 4) continue

            val isWhole = f10 % 10 == 0
            val isHalf = f10 % 5 == 0

            if (isWhole) {
                // Major tick with frequency label
                val tickH = h * 0.45f
                canvas.drawLine(x, boundsRect.top + 6, x, boundsRect.top + 6 + tickH, majorTickPaint)
                canvas.drawText((f10 / 10).toString(), x, boundsRect.bottom - 6, textPaint)
            } else if (isHalf) {
                val tickH = h * 0.30f
                canvas.drawLine(x, boundsRect.top + 6, x, boundsRect.top + 6 + tickH, minorTickPaint)
            } else {
                val tickH = h * 0.18f
                canvas.drawLine(x, boundsRect.top + 6, x, boundsRect.top + 6 + tickH, minorTickPaint)
            }
        }

        // Center Tuning Cursor (Safety Orange)
        canvas.drawLine(cx, boundsRect.top + 2, cx, boundsRect.bottom - 2, cursorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                lastTouchX = event.x
                val pixelsPerMhz = width / 6.0f
                val deltaMhz = -dx / pixelsPerMhz
                currentFrequency = (currentFrequency + deltaMhz).coerceIn(minFrequency, maxFrequency)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onFrequencySelected?.invoke(currentFrequency)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
