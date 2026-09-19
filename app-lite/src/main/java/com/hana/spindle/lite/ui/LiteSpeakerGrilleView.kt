package com.hana.spindle.lite.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.hana.spindle.lite.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bauhaus-inspired concentric acoustic speaker perforation grille.
 * Optimized specifically for 320x480 HVGA (160 dpi) with strict zero heap allocations in onDraw().
 */
class LiteSpeakerGrilleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    startAnimation()
                } else {
                    stopAnimation()
                }
            }
        }

    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val chassisBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val holeRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val centerCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val centerCapBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val pilotLedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val boundsRect = RectF()
    private val maxHoles = 120
    private val holeCoords = FloatArray(maxHoles * 2)
    private var totalHoles = 0

    private var centerCapRadius = 0f
    private var holeRadius = 0f
    private var pulsePhase = 0f
    private var pulseAnimator: ValueAnimator? = null

    init {
        val cChassis = ContextCompat.getColor(context, R.color.lite_bg_surface)
        val cBorder = ContextCompat.getColor(context, R.color.lite_border)
        val cHole = ContextCompat.getColor(context, R.color.lite_bg_chassis)
        val cCap = ContextCompat.getColor(context, R.color.lite_deck_surface)
        val cPilot = ContextCompat.getColor(context, R.color.brand_orange)

        chassisPaint.color = cChassis
        chassisBorderPaint.color = cBorder
        holePaint.color = cHole
        holeRimPaint.color = cBorder
        centerCapPaint.color = cCap
        centerCapBorderPaint.color = cBorder
        pilotLedPaint.color = cPilot
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val pad = w * 0.04f
        boundsRect.set(pad, pad, w - pad, h - pad)

        val cx = boundsRect.centerX()
        val cy = boundsRect.centerY()
        val maxR = minOf(boundsRect.width(), boundsRect.height()) * 0.44f

        centerCapRadius = maxR * 0.28f
        holeRadius = w * 0.016f

        // Pre-calculate concentric rings of holes
        var writeIdx = 0
        val rings = 4
        for (r in 1..rings) {
            val radius = centerCapRadius + (r * (maxR - centerCapRadius) / (rings + 0.5f))
            val count = 8 + (r * 6)
            for (i in 0 until count) {
                if (writeIdx + 1 < holeCoords.size) {
                    val angle = (i * 2.0 * Math.PI / count).toFloat()
                    holeCoords[writeIdx++] = cx + radius * cos(angle)
                    holeCoords[writeIdx++] = cy + radius * sin(angle)
                }
            }
        }
        totalHoles = writeIdx / 2
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (totalHoles == 0) return

        val radius = width * 0.03f
        canvas.drawRoundRect(boundsRect, radius, radius, chassisPaint)
        canvas.drawRoundRect(boundsRect, radius, radius, chassisBorderPaint)

        // Draw Perforated Acoustic Holes
        for (i in 0 until totalHoles) {
            val hx = holeCoords[i * 2]
            val hy = holeCoords[i * 2 + 1]
            canvas.drawCircle(hx, hy, holeRadius, holePaint)
            canvas.drawCircle(hx, hy, holeRadius, holeRimPaint)
        }

        // Center Acoustic Driver Cone / Dust Cap
        val cx = boundsRect.centerX()
        val cy = boundsRect.centerY()
        canvas.drawCircle(cx, cy, centerCapRadius, centerCapPaint)
        canvas.drawCircle(cx, cy, centerCapRadius, centerCapBorderPaint)

        // Subtle Stereo / Power Pilot LED at Center
        val ledR = centerCapRadius * 0.25f
        if (isPlaying) {
            val animatedLedR = ledR * (1.0f + 0.15f * sin(pulsePhase))
            canvas.drawCircle(cx, cy, animatedLedR, pilotLedPaint)
        } else {
            canvas.drawCircle(cx, cy, ledR * 0.7f, holeRimPaint)
        }
    }

    private fun startAnimation() {
        if (pulseAnimator != null) return
        pulseAnimator = ValueAnimator.ofFloat(0f, (2.0 * Math.PI).toFloat()).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulsePhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulsePhase = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
