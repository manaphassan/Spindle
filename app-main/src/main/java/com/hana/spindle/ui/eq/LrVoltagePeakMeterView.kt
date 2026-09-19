package com.hana.spindle.ui.eq

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Dual-Channel L & R Voltage Peak Spectrum Meter inspired by professional studio hardware consoles.
 * Displays independent 5-segment LED ladders for Left (L) and Right (R) channels with dynamic peak ballistics.
 * Zero heap allocations in onDraw().
 */
class LrVoltagePeakMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val SEGMENT_COUNT = 5
    }

    var leftLevel: Float = 0.0f
        set(value) {
            val clamped = value.coerceIn(0.0f, 1.0f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var rightLevel: Float = 0.0f
        set(value) {
            val clamped = value.coerceIn(0.0f, 1.0f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var isEink: Boolean = false
        set(value) {
            field = value
            updateThemePaints()
            invalidate()
        }

    // Pre-allocated paints
    private val ledGreenOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676") // Vibrant mint emerald
        style = Paint.Style.FILL
    }
    private val ledGreenOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#14291B") // Dark extinguished green diode
        style = Paint.Style.FILL
    }

    private val ledAmberOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B") // Amber warning
        style = Paint.Style.FILL
    }
    private val ledAmberOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E200B") // Dark extinguished amber diode
        style = Paint.Style.FILL
    }

    private val ledRedOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444") // Coral peak alert
        style = Paint.Style.FILL
    }
    private val ledRedOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B1313") // Dark extinguished red diode
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71717A")
        textSize = 20f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E929E")
        textSize = 18f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private fun updateThemePaints() {
        if (isEink) {
            ledGreenOnPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            ledGreenOffPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
            ledAmberOnPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            ledAmberOffPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
            ledRedOnPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            ledRedOffPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
            textPaint.color = Color.BLACK
            headerPaint.color = Color.BLACK
        } else {
            ledGreenOnPaint.apply { color = Color.parseColor("#00E676"); style = Paint.Style.FILL }
            ledGreenOffPaint.apply { color = Color.parseColor("#14291B"); style = Paint.Style.FILL }
            ledAmberOnPaint.apply { color = Color.parseColor("#F59E0B"); style = Paint.Style.FILL }
            ledAmberOffPaint.apply { color = Color.parseColor("#2E200B"); style = Paint.Style.FILL }
            ledRedOnPaint.apply { color = Color.parseColor("#EF4444"); style = Paint.Style.FILL }
            ledRedOffPaint.apply { color = Color.parseColor("#2B1313"); style = Paint.Style.FILL }
            textPaint.color = Color.parseColor("#71717A")
            headerPaint.color = Color.parseColor("#8E929E")
        }
    }

    private val segmentRect = RectF()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        textPaint.textSize = (h * 0.16f).coerceIn(16f, 24f)
        headerPaint.textSize = (h * 0.14f).coerceIn(14f, 20f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val headerHeight = h * 0.20f
        val footerHeight = h * 0.24f
        val meterHeight = h - headerHeight - footerHeight
        val colWidth = w * 0.38f
        val leftColX = w * 0.10f
        val rightColX = w * 0.52f

        // Top Header: "PEAK"
        canvas.drawText("PEAK", w * 0.5f, headerHeight * 0.70f, headerPaint)

        // Draw 5 horizontal bar segments from bottom (segment 0) to top (segment 4)
        val segHeight = meterHeight / SEGMENT_COUNT
        val segGap = segHeight * 0.20f
        val actualSegH = segHeight - segGap
        val cornerR = actualSegH * 0.35f

        for (i in 0 until SEGMENT_COUNT) {
            // Segment threshold (0 to 4)
            val threshold = (i + 1).toFloat() / SEGMENT_COUNT.toFloat()
            val isLeftLit = leftLevel >= (threshold - 0.10f)
            val isRightLit = rightLevel >= (threshold - 0.10f)

            // Top segment (i=4) is Red Peak, segment 3 is Amber, 0..2 are Green
            val (leftPaint, rightPaint) = when (i) {
                4 -> Pair(
                    if (isLeftLit) ledRedOnPaint else ledRedOffPaint,
                    if (isRightLit) ledRedOnPaint else ledRedOffPaint
                )
                3 -> Pair(
                    if (isLeftLit) ledAmberOnPaint else ledAmberOffPaint,
                    if (isRightLit) ledAmberOnPaint else ledAmberOffPaint
                )
                else -> Pair(
                    if (isLeftLit) ledGreenOnPaint else ledGreenOffPaint,
                    if (isRightLit) ledGreenOnPaint else ledGreenOffPaint
                )
            }

            // Y coordinate: i=0 at bottom, i=4 at top
            val segY = headerHeight + meterHeight - ((i + 1) * segHeight)

            // Left Column
            segmentRect.set(leftColX, segY, leftColX + colWidth, segY + actualSegH)
            canvas.drawRoundRect(segmentRect, cornerR, cornerR, leftPaint)

            // Right Column
            segmentRect.set(rightColX, segY, rightColX + colWidth, segY + actualSegH)
            canvas.drawRoundRect(segmentRect, cornerR, cornerR, rightPaint)
        }

        // Bottom Channel Labels: "L" and "R"
        val labelY = h - (footerHeight * 0.25f)
        canvas.drawText("L", leftColX + (colWidth * 0.5f), labelY, textPaint)
        canvas.drawText("R", rightColX + (colWidth * 0.5f), labelY, textPaint)
    }
}
