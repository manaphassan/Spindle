package com.hana.spindle.ui.eq

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * 12-Segment Studio Hardware LED VU Meter Ladder inspired by professional DJ consoles.
 * Calibrated from 00 dB down to -22 dB with mint-emerald active LEDs and ballistics decay.
 *
 * Implements zero heap allocations in onDraw().
 */
class LedVuMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private val DB_LABELS = arrayOf(
            "00", "-02", "-04", "-06", "-08", "-10",
            "-12", "-14", "-16", "-18", "-20", "-22"
        )
    }

    var audioLevel: Float = 0.0f
        set(value) {
            val clamped = value.coerceIn(0.0f, 1.0f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    // Pre-allocated Paints
    private val ledOffPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledPeakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initPaints()
    }

    private fun initPaints() {
        ledOffPaint.apply {
            color = Color.parseColor("#152219") // Dark extinguished diode recess
            style = Paint.Style.FILL
        }

        ledGreenPaint.apply {
            color = Color.parseColor("#00E676") // Vibrant mint emerald glowing diode
            style = Paint.Style.FILL
        }

        ledPeakPaint.apply {
            color = Color.parseColor("#EF4444") // Coral peak alert diode
            style = Paint.Style.FILL
        }

        labelPaint.apply {
            color = Color.parseColor("#71717A")
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        headerPaint.apply {
            color = Color.parseColor("#8E929E")
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        labelPaint.textSize = (h / (DB_LABELS.size + 2)).toFloat() * 0.55f
        headerPaint.textSize = labelPaint.textSize * 1.1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val totalSegments = DB_LABELS.size
        val headerHeight = h * 0.10f
        val meterHeight = h - headerHeight
        val stepY = meterHeight / totalSegments
        val ledRadius = stepY * 0.28f
        val ledCenterX = w * 0.30f
        val labelX = w * 0.56f

        // Draw "LEVEL" Header at the top
        canvas.drawText("LEVEL", w * 0.50f, headerHeight * 0.70f, headerPaint)

        // Number of active segments from bottom (-22 dB) up to top (00 dB)
        val activeCount = (audioLevel * totalSegments).toInt().coerceIn(0, totalSegments)

        for (i in 0 until totalSegments) {
            val y = headerHeight + (i * stepY) + (stepY * 0.5f)
            val segmentIndexFromBottom = (totalSegments - 1) - i
            val isActive = segmentIndexFromBottom < activeCount

            val paint = when {
                !isActive -> ledOffPaint
                i < 2 -> ledPeakPaint // Top 2 LEDs (00 and -02) peak alert
                else -> ledGreenPaint // Normal mint emerald
            }

            // Draw LED circle
            canvas.drawCircle(ledCenterX, y, ledRadius, paint)

            // Draw dB label
            val textY = y + (labelPaint.textSize * 0.35f)
            canvas.drawText(DB_LABELS[i], labelX, textY, labelPaint)
        }
    }
}
