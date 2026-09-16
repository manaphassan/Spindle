package com.hana.spindle.ui.radio

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * 3D Cylindrical Analog Radio Tuning Thumbwheel inspired by Braun / Dieter Rams audio equipment.
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

    // Touch tracking
    private var lastTouchX = 0f
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var hasMovedSignificantly = false
    private var isDragging = false
    private var lastHapticStep = -1

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
        rollerBgPaint.apply {
            color = Color.parseColor("#E4E4E8") // Soft cream roller body
            style = Paint.Style.FILL
        }

        ridgePaint.apply {
            color = Color.parseColor("#C2C2C8") // Ribbed ridge shadow
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
        }

        ridgeHighlightPaint.apply {
            color = Color.WHITE // Ribbed ridge highlight
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }

        textPaint.apply {
            color = Color.parseColor("#71717A") // Slate calibrated scale numbers
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        cursorPaint.apply {
            color = Color.parseColor("#EF4444") // Iconic red tuning cursor hairline
            style = Paint.Style.STROKE
            strokeWidth = 3.0f
            strokeCap = Paint.Cap.ROUND
        }

        bevelPaint.apply {
            color = Color.parseColor("#D4D4D8")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
        textPaint.textSize = h * 0.22f

        // 3D cylindrical side shadow vignette
        cylinderShader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(
                Color.argb(160, 180, 180, 186),
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(160, 180, 180, 186)
            ),
            floatArrayOf(0.0f, 0.25f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        gradientOverlayPaint.shader = cylinderShader
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
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
                if (!hasMovedSignificantly) {
                    onDialClicked?.invoke()
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
