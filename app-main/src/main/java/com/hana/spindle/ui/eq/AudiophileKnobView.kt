package com.hana.spindle.ui.eq

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * Audiophile Rotary Studio Potentiometer View inspired by hardware DJ consoles.
 * Features 3D beveled tactile rotary knob, perimeter detent dots, glowing mint-emerald notch,
 * and high-precision vertical touch tracking with mechanical detent haptics.
 *
 * Implements zero heap allocations in onDraw() for 60fps performance on low-RAM DAPs.
 */
class AudiophileKnobView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var label: String = "LOW"
        set(value) {
            field = value
            invalidate()
        }

    var minValue: Float = -12.0f
    var maxValue: Float = 12.0f
    var currentValue: Float = 0.0f
        set(value) {
            val clamped = value.coerceIn(minValue, maxValue)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var onValueChanged: ((Float) -> Unit)? = null

    var isEink: Boolean = false
        set(value) {
            field = value
            initPaints()
            invalidate()
        }

    // Touch interaction
    private var lastTouchY = 0f
    private var isDragging = false
    private var lastHapticDetent = false

    // Pre-allocated Paints
    private val knobShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pre-allocated Coordinates & Radii
    private var cx = 0f
    private var cy = 0f
    private var knobRadius = 0f
    private var dotOrbitRadius = 0f
    private val numDots = 11 // 11 discrete calibration dots (-135° to +135°)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initPaints()
    }

    private fun initPaints() {
        if (isEink) {
            knobShadowPaint.apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            knobBodyPaint.apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            knobBevelPaint.apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 2.0f
            }
            notchPaint.apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 3.5f
                strokeCap = Paint.Cap.ROUND
            }
            dotPaint.apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            activeDotPaint.apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            labelPaint.apply {
                color = Color.BLACK
                textSize = 24f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            valuePaint.apply {
                color = Color.BLACK
                textSize = 18f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            return
        }

        knobShadowPaint.apply {
            color = Color.parseColor("#0D0E10") // Deep recess shadow
            style = Paint.Style.FILL
        }

        knobBodyPaint.apply {
            color = Color.parseColor("#1F2126") // Matte dark hardware potentiometer body
            style = Paint.Style.FILL
        }

        knobBevelPaint.apply {
            color = Color.parseColor("#2E3138") // Subtle edge bevel
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
        }

        notchPaint.apply {
            color = Color.parseColor("#00E676") // Radiant mint emerald indicator line
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
        }

        dotPaint.apply {
            color = Color.parseColor("#383B44") // Inactive perimeter calibration dots
            style = Paint.Style.FILL
        }

        activeDotPaint.apply {
            color = Color.parseColor("#00E676") // Active/center detent dot
            style = Paint.Style.FILL
        }

        labelPaint.apply {
            color = Color.parseColor("#8E929E") // Subtle gray potentiometer label
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        valuePaint.apply {
            color = Color.parseColor("#00E676") // Emerald dB readout
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        cx = w * 0.5f
        cy = h * 0.5f
        val minDim = min(w, h).toFloat()
        knobRadius = minDim * 0.35f
        dotOrbitRadius = minDim * 0.44f

        labelPaint.textSize = minDim * 0.13f
        valuePaint.textSize = minDim * 0.09f
        notchPaint.strokeWidth = minDim * 0.035f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cx == 0f || cy == 0f) return

        val normalized = if (maxValue > minValue) {
            (currentValue - minValue) / (maxValue - minValue)
        } else 0.5f
        // Angular sweep: -135° to +135° (270° total sweep, 0° is straight up)
        val currentAngleDeg = -135f + normalized * 270f

        // 1. Draw Perimeter Calibration Dots (-135° to +135°)
        val startAngleRad = Math.toRadians(-135.0 - 90.0)
        val sweepAngleRad = Math.toRadians(270.0)
        val dotRadius = knobRadius * 0.06f

        for (i in 0 until numDots) {
            val frac = i.toFloat() / (numDots - 1)
            val angle = startAngleRad + frac * sweepAngleRad
            val dx = cx + (dotOrbitRadius * cos(angle)).toFloat()
            val dy = cy + (dotOrbitRadius * sin(angle)).toFloat()

            val isCenterDetent = (i == (numDots - 1) / 2)
            val isCurrentActive = (frac <= normalized)

            val r = if (isCenterDetent) dotRadius * 1.3f else dotRadius
            if (isEink) {
                if (isCurrentActive) {
                    dotPaint.style = Paint.Style.FILL
                    canvas.drawCircle(dx, dy, r, dotPaint)
                } else {
                    dotPaint.style = Paint.Style.STROKE
                    dotPaint.strokeWidth = 1.2f
                    canvas.drawCircle(dx, dy, r, dotPaint)
                }
            } else {
                val paint = if (isCurrentActive) activeDotPaint else dotPaint
                canvas.drawCircle(dx, dy, r, paint)
            }
        }

        // 2. Draw Recessed Knob Base Shadow
        if (!isEink) {
            canvas.drawCircle(cx, cy + 2f, knobRadius + 3f, knobShadowPaint)
        }

        // 3. Draw Potentiometer Knob Body & Bevel
        canvas.drawCircle(cx, cy, knobRadius, knobBodyPaint)
        canvas.drawCircle(cx, cy, knobRadius, knobBevelPaint)

        // 4. Draw Indicator Notch Line (Mint Emerald)
        val notchAngleRad = Math.toRadians((currentAngleDeg - 90f).toDouble())
        val innerNotchR = knobRadius * 0.52f
        val outerNotchR = knobRadius * 0.88f
        val x1 = cx + (innerNotchR * cos(notchAngleRad)).toFloat()
        val y1 = cy + (innerNotchR * sin(notchAngleRad)).toFloat()
        val x2 = cx + (outerNotchR * cos(notchAngleRad)).toFloat()
        val y2 = cy + (outerNotchR * sin(notchAngleRad)).toFloat()
        canvas.drawLine(x1, y1, x2, y2, notchPaint)

        // 5. Draw Potentiometer Center Label (e.g. LOW, MID, HI)
        val textY = cy + (labelPaint.textSize * 0.35f)
        canvas.drawText(label, cx, textY, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isDragging = true
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dy = lastTouchY - event.y // Dragging UP increases value
                    lastTouchY = event.y

                    val range = maxValue - minValue
                    val delta = (dy / (height * 0.6f)) * range
                    val newVal = (currentValue + delta).coerceIn(minValue, maxValue)

                    // Check center detent crossing (near 0)
                    val isZero = abs(newVal) < 0.35f
                    if (isZero && !lastHapticDetent) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        lastHapticDetent = true
                    } else if (!isZero) {
                        lastHapticDetent = false
                    }

                    if (newVal != currentValue) {
                        currentValue = newVal
                        onValueChanged?.invoke(newVal)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
