package com.hana.spindle.ui.catalog

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * Modern audiophile audio waveform visualizer matching the reference image.
 * Renders symmetric vertical bars with active/inactive progress coloring and touch seeking.
 */
class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var progress: Float = 0.0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (abs(field - clamped) > 0.001f) {
                field = clamped
                invalidate()
            }
        }

    var isPlaying: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isDarkMode: Boolean = true
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var isEink: Boolean = false
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var accentColor: Int = Color.parseColor("#F97316")
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var onSeek: ((Float) -> Unit)? = null

    // Base waveform envelope shape (symmetrical peak in center like image)
    private val barHeights = floatArrayOf(
        0.18f, 0.22f, 0.28f, 0.24f, 0.32f, 0.38f, 0.45f, 0.42f, 0.55f, 0.68f,
        0.82f, 1.00f, 0.86f, 0.72f, 0.58f, 0.48f, 0.42f, 0.36f, 0.30f, 0.25f,
        0.20f, 0.24f, 0.28f, 0.22f, 0.18f, 0.15f, 0.12f
    )

    private val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val inactiveBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barRect = RectF()

    init {
        updatePaints()
    }

    private fun updatePaints() {
        if (isEink) {
            activeBarPaint.color = Color.WHITE
            inactiveBarPaint.color = Color.parseColor("#333333")
        } else if (isDarkMode) {
            activeBarPaint.color = accentColor
            inactiveBarPaint.color = Color.parseColor("#353A54")
        } else {
            activeBarPaint.color = accentColor
            inactiveBarPaint.color = Color.parseColor("#E5E5E2")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val count = barHeights.size
        val gap = w * 0.018f
        val totalGaps = gap * (count - 1)
        val barWidth = (w - totalGaps) / count.toFloat()
        val centerY = h * 0.5f

        val activeIndex = (count * progress).toInt()
        val now = System.currentTimeMillis()

        for (i in 0 until count) {
            val startX = i * (barWidth + gap)
            var heightFraction = barHeights[i]

            // Micro-bouncing dynamic animation when playing
            if (isPlaying) {
                val bounce = (sin((now / 180.0) + (i * 0.45)) * 0.12).toFloat()
                heightFraction = (heightFraction + bounce).coerceIn(0.12f, 1.0f)
            }

            val barHeight = h * heightFraction * 0.88f
            barRect.set(
                startX,
                centerY - barHeight * 0.5f,
                startX + barWidth,
                centerY + barHeight * 0.5f
            )

            val paint = if (i <= activeIndex) activeBarPaint else inactiveBarPaint
            canvas.drawRoundRect(barRect, barWidth * 0.5f, barWidth * 0.5f, paint)
        }

        if (isPlaying) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val newProgress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                progress = newProgress
                onSeek?.invoke(newProgress)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
