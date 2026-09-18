package com.hana.spindle.ui.eq

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/**
 * Symmetric Audio Waveform Visualizer View inspired by pro DJ software and hardware consoles.
 * Renders high-density vertical bars mirroring top and bottom around the horizontal centerline,
 * animating dynamically during music playback.
 *
 * Implements zero heap allocations in onDraw().
 */
class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) invalidate()
            }
        }

    var progress: Float = 0.0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var isEink: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                initPaints()
                if (width > 0) {
                    val barWidth = (width.toFloat() / numBars) * 0.55f
                    barPlayedPaint.strokeWidth = barWidth
                    barUnplayedPaint.strokeWidth = if (value) 1.2f else barWidth
                }
                invalidate()
            }
        }

    // Pre-allocated Paints
    private val barPlayedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barUnplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pre-calculated static seed profile
    private val numBars = 72
    private val baseHeights = FloatArray(numBars) { i ->
        val x = i.toFloat() / numBars
        val envelope = (sin(x * Math.PI)).toFloat() // Bell curve envelope
        val noise = (sin(i * 1.3) * 0.4 + sin(i * 3.7) * 0.3 + 0.5).toFloat().coerceIn(0.2f, 1.0f)
        (envelope * noise).coerceIn(0.1f, 1.0f)
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initPaints()
    }

    private fun initPaints() {
        if (isEink) {
            barPlayedPaint.apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

            barUnplayedPaint.apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

            centerLinePaint.apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 1.0f
            }
        } else {
            barPlayedPaint.apply {
                color = Color.parseColor("#F97316") // Hero Warm Orange played bars
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

            barUnplayedPaint.apply {
                color = Color.parseColor("#353A54") // Muted Dark Indigo unplayed bars
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

            centerLinePaint.apply {
                color = Color.parseColor("#2A2E45") // Brand Dark Indigo framing line
                style = Paint.Style.STROKE
                strokeWidth = 1.0f
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        val barWidth = (w.toFloat() / numBars) * 0.55f
        barPlayedPaint.strokeWidth = barWidth
        barUnplayedPaint.strokeWidth = if (isEink) 1.2f else barWidth
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val cy = h * 0.5f
        val maxBarHalfHeight = (h * 0.45f)
        val stepX = w / numBars
        val playedCount = (progress * numBars).toInt()
        val timeSec = if (isPlaying) (SystemClock.uptimeMillis() % 100000L) / 250.0 else 0.0

        // Center horizon line
        canvas.drawLine(0f, cy, w, cy, centerLinePaint)

        for (i in 0 until numBars) {
            val x = (i * stepX) + (stepX * 0.5f)
            val baseH = baseHeights[i]

            // Dynamic bounce modulation if active playback
            val bounce = if (isPlaying) {
                (1.0f + 0.35f * sin(timeSec + i * 0.4)).toFloat()
            } else 1.0f

            val halfH = (baseH * maxBarHalfHeight * bounce).coerceIn(2f, maxBarHalfHeight)
            val paint = if (i <= playedCount) barPlayedPaint else barUnplayedPaint

            // Symmetrical top and bottom bar
            canvas.drawLine(x, cy - halfH, x, cy + halfH, paint)
        }

        if (isPlaying) {
            postInvalidateOnAnimation()
        }
    }
}
