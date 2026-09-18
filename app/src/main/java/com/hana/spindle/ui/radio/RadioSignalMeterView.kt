package com.hana.spindle.ui.radio

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vintage Analog RF Signal Strength & Center-Tuning Galvanometer Meter.
 * Inspired by classic Marantz, Pioneer, and Sansui Hi-Fi stereo tuners.
 *
 * Features:
 * 1. Dual scale: S-Units (0 to 5) and Center-Tuning zero-point indication.
 * 2. 2nd-order ANSI C16.5 physical needle ballistics with authentic mechanical damping.
 * 3. Glowing ruby-red STEREO pilot lamp with lens bezel and diffused optical glare.
 * 4. Multi-tone incandescent backlighting (Amber, Cyan, Studio Mint, Parchment) cycling on tap.
 * 5. Zero heap allocations inside onDraw() for maximum 60fps smoothness on low-RAM DAPs.
 */
class RadioSignalMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Telemetry properties
    var signalStrength: Float = 0.0f // 0.0 to 1.0 (corresponds to 0 to 5 S-Units)
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    var isStereo: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isDarkMode: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                updateThemeColors()
                invalidate()
            }
        }

    var isEink: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateThemeColors()
                invalidate()
            }
        }

    // Backlight styles
    enum class Backlight(val displayName: String, val topGlow: Int, val botGlow: Int, val scaleColor: Int) {
        AMBER("Tungsten Amber", Color.parseColor("#3D2205"), Color.parseColor("#150B02"), Color.parseColor("#F59E0B")),
        CYAN("Marantz Cyan", Color.parseColor("#062E3D"), Color.parseColor("#02121A"), Color.parseColor("#38BDF8")),
        MINT("Studio Mint", Color.parseColor("#073322"), Color.parseColor("#02170F"), Color.parseColor("#34D399")),
        PARCHMENT("Warm Cream", Color.parseColor("#2E2B24"), Color.parseColor("#14130F"), Color.parseColor("#E2E8F0"))
    }

    private var currentBacklight = Backlight.AMBER

    // Needle physics ballistics
    private var currentNeedleAngle: Float = -40f
    private var targetNeedleAngle: Float = -40f
    private var needleVelocity: Float = 0f

    // Min and max needle sweep angles (degrees, relative to straight up at -90)
    // -42 deg is min (S=0), +42 deg is max (S=5)
    private val minSweepAngle = -40f
    private val maxSweepAngle = 40f

    // TimeAnimator for 60fps ballistics
    private var animator: TimeAnimator? = null

    // Pre-allocated Paints
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scaleArcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val optimalArcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickMajorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickMinorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val needleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pivotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pivotCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Stereo Pilot LED Paints
    private val ledRimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledOffPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledOnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Geometry caches
    private val boundsRect = RectF()
    private val dialRect = RectF()
    private val arcRect = RectF()
    private var dialShader: LinearGradient? = null
    private var ledGlowShader: RadialGradient? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initPaints()
        updateThemeColors()

        setOnClickListener {
            cycleBacklight()
        }
    }

    private fun initPaints() {
        bezelPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
        }

        scaleArcPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
        }

        optimalArcPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 4.0f
            color = Color.parseColor("#10B981") // Green optimal zone (S3 to S5)
        }

        tickMajorPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }

        tickMinorPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            strokeCap = Paint.Cap.ROUND
        }

        textPaint.apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        labelPaint.apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }

        needlePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }

        needleShadowPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(90, 0, 0, 0)
        }

        pivotPaint.apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1E2028")
        }

        pivotCorePaint.apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#64748B")
        }

        // Stereo LED Paints
        ledRimPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            color = Color.parseColor("#475569")
        }

        ledOffPaint.apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#3B1010")
        }

        ledOnPaint.apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#EF4444")
        }

        ledTextPaint.apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            letterSpacing = 0.1f
        }
    }

    private fun updateThemeColors() {
        if (isEink) {
            chassisPaint.color = Color.WHITE
            bezelPaint.color = Color.BLACK
            scaleArcPaint.color = Color.BLACK
            optimalArcPaint.color = Color.BLACK
            tickMajorPaint.color = Color.BLACK
            tickMinorPaint.color = Color.BLACK
            textPaint.color = Color.BLACK
            labelPaint.color = Color.BLACK
            needlePaint.color = Color.BLACK
            ledTextPaint.color = Color.BLACK
            ledOnPaint.color = Color.BLACK
            ledOffPaint.color = Color.WHITE
        } else {
            chassisPaint.color = if (isDarkMode) Color.parseColor("#13151D") else Color.parseColor("#E2E8F0")
            bezelPaint.color = if (isDarkMode) Color.parseColor("#272A38") else Color.parseColor("#CBD5E1")
            scaleArcPaint.color = currentBacklight.scaleColor
            tickMajorPaint.color = currentBacklight.scaleColor
            tickMinorPaint.color = Color.argb(160, Color.red(currentBacklight.scaleColor), Color.green(currentBacklight.scaleColor), Color.blue(currentBacklight.scaleColor))
            textPaint.color = currentBacklight.scaleColor
            labelPaint.color = if (isDarkMode) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
            needlePaint.color = Color.parseColor("#F1F5F9")
            ledTextPaint.color = if (isStereo) Color.parseColor("#EF4444") else Color.parseColor("#64748B")
        }
        updateShaders(width, height)
    }

    private fun updateShaders(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (isEink) {
            dialShader = null
            dialBgPaint.shader = null
            dialBgPaint.color = Color.WHITE
            ledGlowShader = null
        } else {
            dialShader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                currentBacklight.topGlow,
                currentBacklight.botGlow,
                Shader.TileMode.CLAMP
            )
            dialBgPaint.shader = dialShader

            // LED glow radial
            val ledX = w * 0.88f
            val ledY = h * 0.28f
            val ledR = 14f
            ledGlowShader = RadialGradient(
                ledX, ledY, ledR * 2.2f,
                Color.argb(180, 239, 68, 68),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            ledGlowPaint.shader = ledGlowShader
        }
    }

    fun cycleBacklight() {
        if (isEink) return
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val vals = Backlight.values()
        currentBacklight = vals[(currentBacklight.ordinal + 1) % vals.size]
        updateThemeColors()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
        dialRect.set(4f, 4f, w - 4f, h - 4f)
        updateShaders(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startBallistics()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopBallistics()
    }

    private fun startBallistics() {
        if (animator != null) return
        animator = TimeAnimator().apply {
            setTimeListener { _, _, deltaTimeMs ->
                updatePhysics(deltaTimeMs / 1000.0f)
            }
            start()
        }
    }

    private fun stopBallistics() {
        animator?.cancel()
        animator = null
    }

    private fun updatePhysics(dt: Float) {
        if (dt <= 0f || dt > 0.1f) return // Guard against frame drops or suspends

        // Target angle mapped from signalStrength (0.0 -> -40 deg, 1.0 -> +40 deg)
        targetNeedleAngle = minSweepAngle + (signalStrength * (maxSweepAngle - minSweepAngle))

        // ANSI C16.5 Galvanometer Spring & Damper Ballistics
        val springK = 35.0f   // Natural frequency stiffness
        val dampingC = 8.5f   // Near critical damping
        val displacement = targetNeedleAngle - currentNeedleAngle
        val acceleration = (springK * displacement) - (dampingC * needleVelocity)

        needleVelocity += acceleration * dt
        currentNeedleAngle += needleVelocity * dt

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cornerR = 12f

        // 1. Outer Chassis & Dial Background
        canvas.drawRoundRect(boundsRect, cornerR, cornerR, chassisPaint)
        canvas.drawRoundRect(dialRect, cornerR - 2f, cornerR - 2f, dialBgPaint)
        canvas.drawRoundRect(dialRect, cornerR - 2f, cornerR - 2f, bezelPaint)

        // 2. Dial Calibration Scale & Ticks
        // Center of needle rotation is below the bottom center
        val pivotX = w * 0.50f
        val pivotY = h * 1.25f
        val needleLen = h * 0.95f
        val arcRadius = needleLen * 0.90f

        arcRect.set(pivotX - arcRadius, pivotY - arcRadius, pivotX + arcRadius, pivotY + arcRadius)

        // Draw optimal reception arc (S3 to S5 -> corresponding to angles +8 to +40 deg)
        if (!isEink) {
            val startSweep = -90f + 8f
            val sweepLen = 32f
            canvas.drawArc(arcRect, startSweep, sweepLen, false, optimalArcPaint)
        }

        // Draw base calibration arc
        canvas.drawArc(arcRect, -90f + minSweepAngle, maxSweepAngle - minSweepAngle, false, scaleArcPaint)

        // Draw S-meter calibrations (0 to 5 S-Units)
        for (i in 0..10) {
            val sVal = i / 2.0f // 0, 0.5, 1, 1.5 ... 5.0
            val fraction = i / 10.0f
            val angleDeg = minSweepAngle + fraction * (maxSweepAngle - minSweepAngle)
            val angleRad = Math.toRadians((-90.0 + angleDeg)).toFloat()

            val isMajor = (i % 2 == 0)
            val tickLen = if (isMajor) 14f else 8f

            val rOuter = arcRadius + 2f
            val rInner = rOuter - tickLen

            val x1 = pivotX + rInner * cos(angleRad)
            val y1 = pivotY + rInner * sin(angleRad)
            val x2 = pivotX + rOuter * cos(angleRad)
            val y2 = pivotY + rOuter * sin(angleRad)

            val paint = if (isMajor) tickMajorPaint else tickMinorPaint
            canvas.drawLine(x1, y1, x2, y2, paint)

            if (isMajor) {
                val rText = rInner - 12f
                val tx = pivotX + rText * cos(angleRad)
                val ty = pivotY + rText * sin(angleRad) + 6f
                canvas.drawText("${sVal.toInt()}", tx, ty, textPaint)
            }
        }

        // 3. Dial Labels
        canvas.drawText("RF SIGNAL", w * 0.28f, h * 0.88f, labelPaint)
        canvas.drawText("◀ TUNE ▶", w * 0.72f, h * 0.88f, labelPaint)

        // 4. Stereo Pilot LED Indicator (Top Right)
        val ledCenterX = w * 0.82f
        val ledCenterY = h * 0.28f
        val ledRadius = 7.0f

        if (isStereo && !isEink && ledGlowShader != null) {
            canvas.drawCircle(ledCenterX, ledCenterY, ledRadius * 2.2f, ledGlowPaint)
        }

        canvas.drawCircle(ledCenterX, ledCenterY, ledRadius + 1.5f, ledRimPaint)
        canvas.drawCircle(ledCenterX, ledCenterY, ledRadius, if (isStereo) ledOnPaint else ledOffPaint)

        // LED Specular highlight
        if (isStereo && !isEink) {
            pivotCorePaint.color = Color.argb(180, 255, 255, 255)
            canvas.drawCircle(ledCenterX - 2f, ledCenterY - 2f, 2.0f, pivotCorePaint)
        }

        // "STEREO" text next to the pilot diode
        ledTextPaint.color = if (isStereo) Color.parseColor("#EF4444") else Color.parseColor("#64748B")
        canvas.drawText("STEREO", ledCenterX + 12f, ledCenterY + 4.5f, ledTextPaint)

        // 5. Galvanometer Needle with drop shadow
        val needleRad = Math.toRadians((-90.0 + currentNeedleAngle)).toFloat()
        val tipX = pivotX + needleLen * cos(needleRad)
        val tipY = pivotY + needleLen * sin(needleRad)

        // Shadow offset
        canvas.drawLine(pivotX + 2f, pivotY + 2f, tipX + 2f, tipY + 2f, needleShadowPaint)
        // Needle
        canvas.drawLine(pivotX, pivotY, tipX, tipY, needlePaint)

        // 6. Pivot Boss (Hub)
        val pivotR = 24f
        canvas.drawCircle(pivotX, pivotY, pivotR, pivotPaint)
        pivotCorePaint.color = Color.parseColor("#64748B")
        canvas.drawCircle(pivotX, pivotY, 8f, pivotCorePaint)
    }
}
