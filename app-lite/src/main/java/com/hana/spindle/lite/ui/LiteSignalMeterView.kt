package com.hana.spindle.lite.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.hana.spindle.lite.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 60 FPS Vintage Analog RF Signal Strength & Center-Tuning Galvanometer S-Meter.
 * Inspired by Marantz Model 120, Sansui TU-9900, and Accuphase T-100 Hi-Fi tuners.
 * Features 2nd-order damped needle ballistics, ruby-red STEREO 19 kHz pilot light,
 * and dual incandescent backlights (Tungsten Amber & Marantz Cyan).
 * Zero heap allocations in onDraw().
 */
class LiteSignalMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- State Variables ---
    private var targetSignal: Float = 0.0f    // 0.0f (S0) to 1.0f (S5)
    private var currentSignal: Float = 0.0f   // Damped physical needle position
    private var needleVelocity: Float = 0.0f  // 2nd-order ballistic velocity
    private var isStereoLocked: Boolean = false
    private var isCyanBacklight: Boolean = false
    private var lastTickTimeNanos: Long = 0L

    // --- Pre-allocated Geometry Objects ---
    private val meterRect = RectF()
    private val bezelRect = RectF()
    private val pilotLedRect = RectF()
    private val needlePath = Path()
    private val arcRect = RectF()

    // --- Pre-allocated Paint Objects ---
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val scaleArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val scaleTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val scaleOptimalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 14f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }
    private val pivotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pilotLedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pilotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Cached Theme Colors
    private var bgAmber = 0
    private var scaleAmber = 0
    private var bgCyan = 0
    private var scaleCyan = 0
    private var needleColor = 0
    private var stereoLedOn = 0
    private var stereoLedOff = 0
    private var bezelColor = 0
    private var optimalGreen = 0

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        applyThemeTokens()

        setOnClickListener {
            isCyanBacklight = !isCyanBacklight
            updateBacklight()
            invalidate()
        }
    }

    private fun applyThemeTokens() {
        bgAmber = ContextCompat.getColor(context, R.color.lite_meter_bg_amber)
        scaleAmber = ContextCompat.getColor(context, R.color.lite_meter_scale_amber)
        bgCyan = ContextCompat.getColor(context, R.color.lite_meter_bg_cyan)
        scaleCyan = ContextCompat.getColor(context, R.color.lite_meter_scale_cyan)
        needleColor = ContextCompat.getColor(context, R.color.lite_meter_needle)
        stereoLedOn = ContextCompat.getColor(context, R.color.lite_stereo_led_on)
        stereoLedOff = ContextCompat.getColor(context, R.color.lite_stereo_led_off)
        bezelColor = ContextCompat.getColor(context, R.color.lite_border)
        optimalGreen = ContextCompat.getColor(context, R.color.lite_led_green)

        bezelPaint.color = bezelColor
        needlePaint.color = needleColor
        pivotPaint.color = needleColor
        scaleOptimalPaint.color = optimalGreen

        updateBacklight()
    }

    private fun updateBacklight() {
        if (isCyanBacklight) {
            bgPaint.color = bgCyan
            scaleArcPaint.color = scaleCyan
            scaleTickPaint.color = scaleCyan
            textPaint.color = scaleCyan
        } else {
            bgPaint.color = bgAmber
            scaleArcPaint.color = scaleAmber
            scaleTickPaint.color = scaleAmber
            textPaint.color = scaleAmber
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val pad = 4f
        meterRect.set(pad, pad, w - pad, h - pad)
        bezelRect.set(meterRect)

        // Arc bounding box (pivot point at bottom center)
        val pivotX = w * 0.5f
        val pivotY = h * 1.05f
        val radius = h * 0.90f
        arcRect.set(pivotX - radius, pivotY - radius, pivotX + radius, pivotY + radius)

        // Top right STEREO pilot LED badge
        val ledW = w * 0.08f
        val ledH = h * 0.16f
        val ledR = w - pad - 12f
        val ledL = ledR - ledW
        val ledT = pad + 10f
        val ledB = ledT + ledH
        pilotLedRect.set(ledL, ledT, ledR, ledB)

        textPaint.textSize = (h * 0.16f).coerceAtLeast(10f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Draw Dial Face & Bezel
        val cornerR = 6f
        canvas.drawRoundRect(meterRect, cornerR, cornerR, bgPaint)
        canvas.drawRoundRect(bezelRect, cornerR, cornerR, bezelPaint)

        // 2. Draw S-Meter Scale Arc (from 220 deg to 320 deg, 100 deg sweep)
        val startAngle = 225f
        val sweepAngle = 90f
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, scaleArcPaint)

        // Draw Optimal Green Band from S3 to S5 (approx 265 deg to 315 deg)
        canvas.drawArc(arcRect, 275f, 40f, false, scaleOptimalPaint)

        // 3. Draw S-Units Markings: 0, 1, 2, 3, 4, 5
        val pivotX = w * 0.5f
        val pivotY = h * 1.05f
        val arcRadius = h * 0.90f
        val tickOuter = arcRadius + 4f
        val tickInner = arcRadius - 4f

        for (s in 0..5) {
            val frac = s / 5.0f
            val angleDeg = startAngle + (frac * sweepAngle)
            val angleRad = angleDeg * (PI.toFloat() / 180f)
            val cosA = cos(angleRad)
            val sinA = sin(angleRad)

            val x1 = pivotX + (tickInner * cosA)
            val y1 = pivotY + (tickInner * sinA)
            val x2 = pivotX + (tickOuter * cosA)
            val y2 = pivotY + (tickOuter * sinA)
            canvas.drawLine(x1, y1, x2, y2, scaleTickPaint)

            // Number label
            val textR = arcRadius - 13f
            val tx = pivotX + (textR * cosA)
            val ty = pivotY + (textR * sinA) + (textPaint.textSize * 0.35f)
            canvas.drawText("$s", tx, ty, textPaint)
        }

        // Top-Left Dial Label: "SIGNAL"
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("SIGNAL", meterRect.left + 8f, meterRect.top + textPaint.textSize + 2f, textPaint)

        // 4. Draw STEREO Pilot Lamp (Top Right)
        val pilotR = pilotLedRect.height() * 0.35f
        if (isStereoLocked) {
            pilotLedPaint.color = stereoLedOn
            pilotGlowPaint.color = Color.argb(120, 239, 68, 68)
            canvas.drawRoundRect(pilotLedRect, pilotR, pilotR, pilotLedPaint)
            canvas.drawRoundRect(pilotLedRect, pilotR, pilotR, pilotGlowPaint)
        } else {
            pilotLedPaint.color = stereoLedOff
            canvas.drawRoundRect(pilotLedRect, pilotR, pilotR, pilotLedPaint)
        }
        canvas.drawRoundRect(pilotLedRect, pilotR, pilotR, bezelPaint)

        // Pilot label "ST"
        val stX = pilotLedRect.left - 6f
        val stY = pilotLedRect.centerY() + (textPaint.textSize * 0.35f)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("ST", stX, stY, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        // 5. Physical Galvanometer Needle (2nd-order damped ballistics)
        val needleAngleDeg = startAngle + (currentSignal.coerceIn(0f, 1f) * sweepAngle)
        val needleAngleRad = needleAngleDeg * (PI.toFloat() / 180f)
        val needleLength = arcRadius * 0.96f

        val needleEndX = pivotX + (needleLength * cos(needleAngleRad))
        val needleEndY = pivotY + (needleLength * sin(needleAngleRad))

        needlePath.reset()
        needlePath.moveTo(pivotX, pivotY)
        needlePath.lineTo(needleEndX, needleEndY)
        canvas.drawPath(needlePath, needlePaint)

        // Pivot cap
        canvas.drawCircle(pivotX, pivotY, 8f, pivotPaint)

        // 6. Physics Step Loop (Mass-Spring-Damper)
        val now = System.nanoTime()
        if (lastTickTimeNanos > 0L) {
            val dt = ((now - lastTickTimeNanos) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
            val springK = 35.0f // Spring stiffness
            val damping = 8.5f  // Critical damping factor

            val displacement = targetSignal - currentSignal
            val acceleration = (displacement * springK) - (needleVelocity * damping)
            needleVelocity += acceleration * dt
            currentSignal += needleVelocity * dt

            // Clamp and rest check
            if (kotlin.math.abs(displacement) > 0.002f || kotlin.math.abs(needleVelocity) > 0.005f) {
                postInvalidateOnAnimation()
            }
        }
        lastTickTimeNanos = now
    }

    fun setSignalStrength(strength: Float, stereo: Boolean) {
        val clamped = strength.coerceIn(0f, 1f)
        if (clamped != targetSignal || stereo != isStereoLocked) {
            this.targetSignal = clamped
            this.isStereoLocked = stereo
            lastTickTimeNanos = System.nanoTime()
            postInvalidateOnAnimation()
        }
    }
}
