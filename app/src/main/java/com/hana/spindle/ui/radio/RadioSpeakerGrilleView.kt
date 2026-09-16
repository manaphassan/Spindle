package com.hana.spindle.ui.radio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hardware-accelerated custom view rendering a Braun / Dieter Rams-inspired
 * concentric radial acoustic speaker perforation grille.
 *
 * Implements zero heap allocations in onDraw() for maximum 60fps performance on low-RAM DAPs.
 */
class RadioSpeakerGrilleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val holeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2C30") // Deep acoustic hole interior
        style = Paint.Style.FILL
    }

    private val holeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255) // Neumorphic recessed bevel highlight
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
    }

    private val holeCenters = ArrayList<PointF>(220)
    private var holeRadius = 5.5f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        holeCenters.clear()
        val cx = w * 0.5f
        val cy = h * 0.5f
        val minDim = kotlin.math.min(w, h).toFloat()
        holeRadius = minDim * 0.017f

        // Center hole
        holeCenters.add(PointF(cx, cy))

        // 7 Concentric Rings (Braun T3/TP1 proportion, safely uncropped)
        val maxRadius = minDim * 0.45f
        val ringCounts = intArrayOf(8, 14, 20, 26, 32, 38, 44)
        val ringStep = maxRadius / ringCounts.size

        for (i in ringCounts.indices) {
            val count = ringCounts[i]
            val r = ringStep * (i + 1)
            val angleStep = (2.0 * Math.PI) / count
            for (j in 0 until count) {
                val angle = j * angleStep
                val x = (cx + r * cos(angle)).toFloat()
                val y = (cy + r * sin(angle)).toFloat()
                holeCenters.add(PointF(x, y))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (holeCenters.isEmpty()) return

        for (i in holeCenters.indices) {
            val pt = holeCenters[i]
            // Recessed hole interior
            canvas.drawCircle(pt.x, pt.y, holeRadius, holeShadowPaint)
            // Neumorphic bottom-right highlight bevel
            canvas.drawCircle(pt.x, pt.y + 0.8f, holeRadius, holeHighlightPaint)
        }
    }
}
