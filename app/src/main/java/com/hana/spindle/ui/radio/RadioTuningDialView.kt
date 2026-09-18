package com.hana.spindle.ui.radio

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.roundToInt

/**
 * 3D Cylindrical Analog Radio Tuning Thumbwheel inspired by classic industrial audio equipment.
 * Features realistic vertical cylindrical shading, ribbed roller ridges, calibrated frequency markings,
 * and tactile mechanical detent haptic vibrations during scrolling.
 */
class RadioTuningDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var minFreq = 87.5f
    var maxFreq = 108.0f
    var currentFreq = 98.2f
        set(value) {
            val clamped = value.coerceIn(minFreq, maxFreq)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var onFrequencyChanged: ((Float) -> Unit)? = null
    var onDialClicked: (() -> Unit)? = null

    var isDarkMode: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                updateThemePaints()
                invalidate()
            }
        }

    var isEink: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateThemePaints()
                invalidate()
            }
        }

    // Touch tracking
    private var lastTouchX = 0f
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var hasMovedSignificantly = false
    private var isDragging = false
    private var lastHapticStep = -1

    // Flywheel mechanical inertia kinetics
    private var velocityTracker: VelocityTracker? = null
    private var flywheelAnimator: TimeAnimator? = null
    private var flywheelVelocity: Float = 0f

    // Pre-allocated Paints
    private val rollerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ridgeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pre-allocated Geometries
    private val boundsRect = RectF()
    private var cylinderShader: LinearGradient? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initPaints()
    }

    private fun initPaints() {
        textPaint.apply {
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        cursorPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.0f
            strokeCap = Paint.Cap.ROUND
        }

        bevelPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        ridgePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
        }

        ridgeHighlightPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }

        updateThemePaints()
    }

    private fun updateThemePaints() {
        if (isEink) {
            rollerBgPaint.color = Color.WHITE
            ridgePaint.color = Color.BLACK
            ridgeHighlightPaint.color = Color.WHITE
            textPaint.color = Color.BLACK
            cursorPaint.color = Color.BLACK
            bevelPaint.color = Color.BLACK
        } else if (!isDarkMode) {
            rollerBgPaint.color = Color.parseColor("#E5E5E2")
            ridgePaint.color = Color.parseColor("#D1D5DB")
            ridgeHighlightPaint.color = Color.parseColor("#FFFFFF")
            textPaint.color = Color.parseColor("#2A2E45")
            cursorPaint.color = Color.parseColor("#F97316")
            bevelPaint.color = Color.parseColor("#CBD5E1")
        } else {
            rollerBgPaint.color = Color.parseColor("#171926")
            ridgePaint.color = Color.parseColor("#1F2233")
            ridgeHighlightPaint.color = Color.parseColor("#353A54")
            textPaint.color = Color.parseColor("#FAFAF9")
            cursorPaint.color = Color.parseColor("#F97316")
            bevelPaint.color = Color.parseColor("#2A2E45")
        }

        val w = width.toFloat()
        if (w > 0f) {
            updateShader(w)
        }
    }

    private fun updateShader(w: Float) {
        val edgeAlpha = if (isEink) 0 else if (!isDarkMode) 80 else 220
        val edgeColor = if (!isDarkMode) Color.argb(edgeAlpha, 180, 180, 180) else Color.argb(edgeAlpha, 10, 12, 16)
        cylinderShader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(
                edgeColor,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                edgeColor
            ),
            floatArrayOf(0.0f, 0.25f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        gradientOverlayPaint.shader = cylinderShader
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
        textPaint.textSize = h * 0.22f
        updateShader(w.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // 1. Draw Cylindrical Roller Body
        val cornerR = 12f
        canvas.drawRoundRect(boundsRect, cornerR, cornerR, rollerBgPaint)

        // 2. Draw Moving Ribbed Ridges and Calibrated Scale
        val centerX = w * 0.5f
        val pixelsPerMhz = w * 0.40f // Width spanned by 1 MHz

        // Frequency range visible on the dial
        val visibleHalfRange = (centerX / pixelsPerMhz) + 1.0f
        val startMhz = (currentFreq - visibleHalfRange).coerceAtLeast(minFreq)
        val endMhz = (currentFreq + visibleHalfRange).coerceAtMost(maxFreq)

        // Draw ridges every 0.1 MHz
        var freqStep = (startMhz * 10f).roundToInt() / 10f
        val ridgeTop = h * 0.44f
        val ridgeBottom = h * 0.88f

        while (freqStep <= endMhz) {
            val offsetFromCenter = (freqStep - currentFreq) * pixelsPerMhz
            val x = centerX + offsetFromCenter

            if (x in 0f..w) {
                val isMajor = ((freqStep * 10).roundToInt() % 10 == 0) // Whole MHz (e.g. 97, 98, 99)
                val isHalf = ((freqStep * 10).roundToInt() % 5 == 0) // 0.5 MHz

                val topY = if (isMajor) ridgeTop - 6f else if (isHalf) ridgeTop + 2f else ridgeTop + 8f

                // Ribbed ridge line with highlight
                canvas.drawLine(x - 1f, topY, x - 1f, ridgeBottom, ridgePaint)
                canvas.drawLine(x + 1f, topY, x + 1f, ridgeBottom, ridgeHighlightPaint)

                // Frequency label above major ridges
                if (isMajor) {
                    val mhzInt = freqStep.roundToInt()
                    canvas.drawText("$mhzInt", x, h * 0.32f, textPaint)
                }
            }
            freqStep = ((freqStep + 0.1f) * 10f).roundToInt() / 10f
        }

        // 3. 3D Cylindrical Edge Shading
        canvas.drawRoundRect(boundsRect, cornerR, cornerR, gradientOverlayPaint)
        canvas.drawRoundRect(boundsRect, cornerR, cornerR, bevelPaint)

        // 4. Stationary Center Red Tuning Hairline Indicator
        canvas.drawLine(centerX, 4f, centerX, h - 4f, cursorPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopFlywheel()
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun startFlywheel(initialVelocityMhzPerSec: Float) {
        stopFlywheel()
        flywheelVelocity = initialVelocityMhzPerSec
        flywheelAnimator = TimeAnimator().apply {
            setTimeListener { _, _, deltaTimeMs ->
                val dt = deltaTimeMs / 1000f
                if (dt <= 0f || dt > 0.1f) return@setTimeListener

                val deltaMhz = flywheelVelocity * dt
                val targetFreq = currentFreq + deltaMhz
                val clamped = targetFreq.coerceIn(minFreq, maxFreq)

                if (clamped != currentFreq) {
                    currentFreq = clamped
                    onFrequencyChanged?.invoke(currentFreq)

                    val stepIndex = (currentFreq * 10f).roundToInt()
                    if (stepIndex != lastHapticStep) {
                        lastHapticStep = stepIndex
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }

                // Friction decay (exponential: ~0.90 per 60Hz frame)
                flywheelVelocity *= Math.pow(0.88, (dt * 60.0)).toFloat()

                // Stop if boundary reached or speed is negligible
                if (kotlin.math.abs(flywheelVelocity) < 0.08f || targetFreq <= minFreq || targetFreq >= maxFreq) {
                    stopFlywheel()
                }
            }
            start()
        }
    }

    private fun stopFlywheel() {
        flywheelAnimator?.cancel()
        flywheelAnimator = null
        flywheelVelocity = 0f
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                stopFlywheel()
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply {
                    addMovement(event)
                }
                lastTouchX = event.x
                startTouchX = event.x
                startTouchY = event.y
                hasMovedSignificantly = false
                isDragging = true
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    velocityTracker?.addMovement(event)
                    if (kotlin.math.abs(event.x - startTouchX) > 12f || kotlin.math.abs(event.y - startTouchY) > 12f) {
                        hasMovedSignificantly = true
                    }
                    val dx = event.x - lastTouchX
                    lastTouchX = event.x

                    // Invert: dragging right rolls dial left (lower frequency), dragging left rolls right (higher frequency)
                    val pixelsPerMhz = width * 0.40f
                    val deltaMhz = -dx / pixelsPerMhz
                    val newFreq = (currentFreq + deltaMhz).coerceIn(minFreq, maxFreq)

                    if (newFreq != currentFreq) {
                        currentFreq = newFreq
                        onFrequencyChanged?.invoke(currentFreq)

                        // Haptic feedback tick on every 0.1 MHz detent
                        val stepIndex = (newFreq * 10f).roundToInt()
                        if (stepIndex != lastHapticStep) {
                            lastHapticStep = stepIndex
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                if (!hasMovedSignificantly) {
                    onDialClicked?.invoke()
                    performClick()
                } else if (kotlin.math.abs(vx) > 300f) {
                    // Flywheel inertia fling!
                    val pixelsPerMhz = width * 0.40f
                    val velocityMhzPerSec = -vx / pixelsPerMhz
                    startFlywheel(velocityMhzPerSec)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
