package com.hana.spindle.lite.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.hana.spindle.lite.R

/**
 * 60 FPS Hardware-Accelerated Vertical Hi-Fi LED Progress & Status Ladder.
 * Designed to mount directly alongside the cassette deck.
 * Inspired by vintage Walkman Pro, Sony TC-D5M, and Marantz Hi-Fi vertical peak indicators.
 * Zero heap allocations in onDraw().
 */
class LiteLedMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- State Variables ---
    private var batteryLevel: Int = 100
    private var isPlaying: Boolean = false
    private var progressFraction: Float = 0.0f
    var onSeekListener: ((Float) -> Unit)? = null

    // --- Pre-allocated Geometry Objects ---
    private val battLedRect = RectF()
    private val runLedRect = RectF()
    private val meterBarRect = RectF()
    private val segmentRects = Array(12) { RectF() }

    private var battLabelX = 0f
    private var battLabelY = 0f
    private var runLabelX = 0f
    private var runLabelY = 0f
    private var centerLabelX = 0f
    private var centerLabelY = 0f

    // --- Pre-allocated Paint Objects ---
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 18f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val slotBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val slotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }

    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val greenGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
    }

    private val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val amberGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
    }

    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val redGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
    }

    private val battInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val runInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        applyThemeTokens()
    }

    private fun applyThemeTokens() {
        textPaint.color = ContextCompat.getColor(context, R.color.lite_text_secondary)

        slotBgPaint.color = ContextCompat.getColor(context, R.color.lite_led_slot_bg)
        slotBorderPaint.color = ContextCompat.getColor(context, R.color.lite_led_slot_border)

        greenPaint.color = ContextCompat.getColor(context, R.color.lite_led_green)
        greenGlowPaint.color = ContextCompat.getColor(context, R.color.lite_led_green_glow)

        amberPaint.color = ContextCompat.getColor(context, R.color.lite_led_amber)
        amberGlowPaint.color = ContextCompat.getColor(context, R.color.lite_led_amber_glow)

        redPaint.color = ContextCompat.getColor(context, R.color.lite_led_red)
        redGlowPaint.color = ContextCompat.getColor(context, R.color.lite_led_red_glow)

        battInactivePaint.color = ContextCompat.getColor(context, R.color.lite_led_batt_inactive)
        runInactivePaint.color = ContextCompat.getColor(context, R.color.lite_led_run_inactive)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val padX = w * 0.12f
        val centerX = w * 0.5f

        textPaint.textSize = (w * 0.28f).coerceIn(7.5f, 10f)
        val textCenterOffset = -((textPaint.descent() + textPaint.ascent()) * 0.5f)

        // 1. BATTERY Status (Top Section)
        val battY = 12f
        battLabelX = centerX
        battLabelY = battY + textCenterOffset
        val battLedTop = battY + 9f
        val battLedH = (w * 0.22f).coerceIn(5f, 9f)
        battLedRect.set(padX + 1f, battLedTop, w - padX - 1f, battLedTop + battLedH)

        // 2. RUN / PLAY Status (Middle Section)
        val runY = battLedTop + battLedH + 11f
        runLabelX = centerX
        runLabelY = runY + textCenterOffset
        val runLedTop = runY + 9f
        val runLedH = battLedH
        runLedRect.set(padX + 1f, runLedTop, w - padX - 1f, runLedTop + runLedH)

        // 3. Center Ladder Scale Marker
        val progY = runLedTop + runLedH + 12f
        centerLabelX = centerX
        centerLabelY = progY + textCenterOffset

        // 4. Vertical 12-Segment Progress Ladder (Bottom-to-Top orientation)
        val ladderTop = progY + 8f
        val ladderBottom = h.toFloat() - 6f
        meterBarRect.set(padX, ladderTop, w - padX, ladderBottom)

        val totalH = meterBarRect.height()
        val count = 12
        val gap = 3.5f
        val segH = ((totalH - (gap * (count - 1))) / count).coerceAtLeast(2f)

        for (i in 0 until count) {
            // i = 0 is bottom (0% progress), i = 11 is top (100% progress)
            val sBottom = meterBarRect.bottom - (i * (segH + gap))
            val sTop = sBottom - segH
            segmentRects[i].set(meterBarRect.left, sTop, meterBarRect.right, sBottom)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        textPaint.textAlign = Paint.Align.CENTER

        // --- 1. Dual Status LEDs & Labels ---
        canvas.drawText("BAT", battLabelX, battLabelY, textPaint)
        drawBatteryLed(canvas)

        canvas.drawText("RUN", runLabelX, runLabelY, textPaint)
        drawRunLed(canvas)

        canvas.drawText("PROG", centerLabelX, centerLabelY, textPaint)

        // --- 2. 12-Segment Vertical LED Progress Ladder (Bottom to Top) ---
        val count = 12
        val litThreshold = (progressFraction * count).toInt().coerceIn(0, count)
        val cornerR = 2f

        for (i in 0 until count) {
            val rect = segmentRects[i]

            // Draw dark recessed bezel slot
            canvas.drawRoundRect(rect, cornerR, cornerR, slotBgPaint)
            canvas.drawRoundRect(rect, cornerR, cornerR, slotBorderPaint)

            // Determine segment color: 0..7 Green, 8..9 Amber, 10..11 Red
            val isLit = (i < litThreshold) || (i == 0 && progressFraction > 0f && isPlaying)
            if (isLit) {
                val (fillPaint, glowPaint) = when (i) {
                    in 0..7 -> Pair(greenPaint, greenGlowPaint)
                    in 8..9 -> Pair(amberPaint, amberGlowPaint)
                    else -> Pair(redPaint, redGlowPaint)
                }
                canvas.drawRoundRect(rect, cornerR, cornerR, fillPaint)
                canvas.drawRoundRect(rect, cornerR, cornerR, glowPaint)
            }
        }
    }

    private fun drawBatteryLed(canvas: Canvas) {
        val cornerR = 2f
        when {
            batteryLevel > 20 -> {
                // Normal: Default OFF state -> muted ruby
                canvas.drawRoundRect(battLedRect, cornerR, cornerR, battInactivePaint)
                canvas.drawRoundRect(battLedRect, cornerR, cornerR, slotBorderPaint)
            }
            batteryLevel in 11..20 -> {
                // Low: Glowing Amber
                canvas.drawRoundRect(battLedRect, cornerR, cornerR, amberPaint)
                canvas.drawRoundRect(battLedRect, cornerR, cornerR, amberGlowPaint)
                canvas.drawRoundRect(battLedRect, cornerR, cornerR, slotBorderPaint)
            }
            batteryLevel in 6..10 -> {
                // Critical low: Glowing Red
                canvas.drawRoundRect(battLedRect, cornerR, cornerR, redPaint)
                canvas.drawRoundRect(battLedRect, cornerR, cornerR, redGlowPaint)
                canvas.drawRoundRect(battLedRect, cornerR, cornerR, slotBorderPaint)
            }
            else -> {
                // Depleted (<= 5%): Blinking Red (450ms cycle)
                val isBlinkOn = (System.currentTimeMillis() / 450L) % 2L == 0L
                postInvalidateDelayed(225L)
                if (isBlinkOn) {
                    canvas.drawRoundRect(battLedRect, cornerR, cornerR, redPaint)
                    canvas.drawRoundRect(battLedRect, cornerR, cornerR, redGlowPaint)
                } else {
                    canvas.drawRoundRect(battLedRect, cornerR, cornerR, battInactivePaint)
                }
                canvas.drawRoundRect(battLedRect, cornerR, cornerR, slotBorderPaint)
            }
        }
    }

    private fun drawRunLed(canvas: Canvas) {
        val cornerR = 2f
        if (!isPlaying) {
            // Paused / Stopped: Muted Forest Green
            canvas.drawRoundRect(runLedRect, cornerR, cornerR, runInactivePaint)
            canvas.drawRoundRect(runLedRect, cornerR, cornerR, slotBorderPaint)
        } else {
            val isNearEnd = progressFraction >= 0.94f
            if (isNearEnd) {
                // Blinking Green near end of track (380ms cycle)
                val isBlinkOn = (System.currentTimeMillis() / 380L) % 2L == 0L
                postInvalidateDelayed(190L)
                if (isBlinkOn) {
                    canvas.drawRoundRect(runLedRect, cornerR, cornerR, greenPaint)
                    canvas.drawRoundRect(runLedRect, cornerR, cornerR, greenGlowPaint)
                } else {
                    canvas.drawRoundRect(runLedRect, cornerR, cornerR, runInactivePaint)
                }
            } else {
                // Solid Phosphor Green while playing
                canvas.drawRoundRect(runLedRect, cornerR, cornerR, greenPaint)
                canvas.drawRoundRect(runLedRect, cornerR, cornerR, greenGlowPaint)
            }
            canvas.drawRoundRect(runLedRect, cornerR, cornerR, slotBorderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (meterBarRect.height() > 0) {
                    val touchY = event.y.coerceIn(meterBarRect.top, meterBarRect.bottom)
                    // Ladder fills bottom-to-top: bottom is 0.0, top is 1.0
                    val fraction = (1.0f - ((touchY - meterBarRect.top) / meterBarRect.height())).coerceIn(0f, 1f)
                    this.progressFraction = fraction
                    invalidate()
                    onSeekListener?.invoke(fraction)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun setBatteryLevel(pct: Int) {
        if (this.batteryLevel != pct) {
            this.batteryLevel = pct.coerceIn(0, 100)
            invalidate()
        }
    }

    fun setPlaybackState(playing: Boolean, fraction: Float) {
        this.isPlaying = playing
        this.progressFraction = fraction.coerceIn(0f, 1f)
        invalidate()
    }
}
