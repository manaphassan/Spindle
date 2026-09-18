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

    // Dynamic actual song waveform data (defaults to elegant 64-bar profile)
    private var barHeights: FloatArray = FloatArray(64) { i ->
        val x = i.toFloat() / 64f
        (0.22f + 0.48f * sin(x * Math.PI).toFloat()).coerceIn(0.15f, 0.9f)
    }

    fun setWaveformData(amplitudes: FloatArray) {
        if (amplitudes.isNotEmpty()) {
            barHeights = amplitudes
            invalidate()
        }
    }

    val waveformData: FloatArray
        get() = barHeights

    private val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val inactiveBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val playHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barRect = RectF()

    init {
        updatePaints()
    }

    private fun updatePaints() {
        if (isEink) {
            activeBarPaint.color = Color.BLACK
            activeBarPaint.style = Paint.Style.FILL
            inactiveBarPaint.color = Color.BLACK
            inactiveBarPaint.style = Paint.Style.STROKE
            inactiveBarPaint.strokeWidth = 1.2f
            playHeadPaint.color = Color.BLACK
            playHeadPaint.style = Paint.Style.FILL
        } else if (isDarkMode) {
            activeBarPaint.color = accentColor
            activeBarPaint.style = Paint.Style.FILL
            inactiveBarPaint.color = Color.parseColor("#353A54")
            inactiveBarPaint.style = Paint.Style.FILL
            playHeadPaint.color = Color.WHITE
            playHeadPaint.style = Paint.Style.FILL
        } else {
            activeBarPaint.color = accentColor
            activeBarPaint.style = Paint.Style.FILL
            inactiveBarPaint.color = Color.parseColor("#D5D5D2")
            inactiveBarPaint.style = Paint.Style.FILL
            playHeadPaint.color = Color.parseColor("#1E2132")
            playHeadPaint.style = Paint.Style.FILL
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val count = barHeights.size
        if (count == 0) return

        val gap = (w * 0.008f).coerceAtLeast(1.5f)
        val totalGaps = gap * (count - 1)
        val barWidth = ((w - totalGaps) / count.toFloat()).coerceAtLeast(1.5f)
        val centerY = h * 0.5f

        val activeIndex = ((count - 1) * progress).toInt().coerceIn(0, count - 1)
        val now = System.currentTimeMillis()

        for (i in 0 until count) {
            val startX = i * (barWidth + gap)
            var heightFraction = barHeights[i]

            // Subtle breathing modulation on active playing section
            if (isPlaying && i <= activeIndex) {
                val wavePhase = (now / 220.0) + (i * 0.18)
                val pulse = (sin(wavePhase) * 0.06).toFloat()
                heightFraction = (heightFraction + pulse).coerceIn(0.12f, 1.0f)
            }

            val barHeight = (h * heightFraction * 0.90f).coerceAtLeast(barWidth)
            barRect.set(
                startX,
                centerY - barHeight * 0.5f,
                startX + barWidth,
                centerY + barHeight * 0.5f
            )

            val paint = if (i <= activeIndex) activeBarPaint else inactiveBarPaint
            val cornerRadius = barWidth * 0.5f
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, paint)

            // Distinct active playhead marker
            if (i == activeIndex && progress > 0.01f && progress < 0.99f) {
                val pipRadius = barWidth * 0.65f
                canvas.drawCircle(barRect.centerX(), centerY - barHeight * 0.5f - pipRadius * 0.5f, pipRadius, playHeadPaint)
            }
        }

        if (isPlaying) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateSeek(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateSeek(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                updateSeek(event.x)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private var lastHapticBarIndex: Int = -1

    private fun updateSeek(x: Float) {
        val newProgress = (x / width.toFloat()).coerceIn(0f, 1f)
        progress = newProgress
        onSeek?.invoke(newProgress)
        val barIndex = (newProgress * barHeights.size).toInt().coerceIn(0, barHeights.size - 1)
        if (barIndex != lastHapticBarIndex) {
            lastHapticBarIndex = barIndex
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
