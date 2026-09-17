package com.hana.spindle.ui.cassette

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.hana.spindle.theme.CassetteTheme

/**
 * Hardware-accelerated custom view rendering the Sony Walkman II (WM-2) Red chassis.
 *
 * Implements:
 * 1. Anodized crimson red chassis with bevels and radiuses.
 * 2. Asymmetrical diagonal black control bezel (top-right).
 * 3. Knurled metallic volume dial.
 * 4. Classic pill-shaped PLAY lever (with green dot) and STOP lever (with red dot).
 * 5. Rewind & Fast-Forward circular buttons.
 * 6. Live Battery status LED.
 * 7. "STEREO SPINDLE II" stacked vintage typography.
 *
 * Designed with zero allocations in onDraw() for maximum battery life and 60fps smoothness on low-RAM DAPs.
 */
class Wm2ChassisView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Theme state
    var theme: CassetteTheme = CassetteTheme.WM2_RED_HERO
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    // Playback and UI state
    var isPlaying: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var batteryLevel: Int = 85 // Percentage 0-100
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var isCharging: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // Touch callback listeners
    var onPlayClicked: (() -> Unit)? = null
    var onStopClicked: (() -> Unit)? = null
    var onPrevClicked: (() -> Unit)? = null
    var onNextClicked: (() -> Unit)? = null
    var onEjectClicked: (() -> Unit)? = null

    // Pre-allocated Paints (Zero allocations in onDraw)
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val diagonalBezelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dialBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dialKnurlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leverBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leverShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val greenDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val redSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val batteryLedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pre-allocated Geometries
    private val chassisRect = RectF()
    private val diagonalPath = Path()
    private val playLeverRect = RectF()
    private val stopLeverRect = RectF()
    private val dialCenter = PointF()
    private val ffCenter = PointF()
    private val rewCenter = PointF()
    private val batteryLedCenter = PointF()
    private var dialRadius = 0f
    private var smallButtonRadius = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updatePaints()
    }

    private fun updatePaints() {
        chassisPaint.apply {
            color = theme.chassisColor
            style = Paint.Style.FILL
        }
        bevelPaint.apply {
            color = Color.argb(40, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        diagonalBezelPaint.apply {
            color = theme.diagonalBezelColor
            style = Paint.Style.FILL
        }
        dialBasePaint.apply {
            color = theme.dialColor
            style = Paint.Style.FILL
        }
        dialKnurlPaint.apply {
            color = Color.parseColor("#9CA3AF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        leverBodyPaint.apply {
            color = Color.parseColor("#E5E7EB")
            style = Paint.Style.FILL
        }
        leverShadowPaint.apply {
            color = Color.parseColor("#9CA3AF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        greenDotPaint.apply {
            color = Color.parseColor("#00C853") // Iconic WM-2 green dot
            style = Paint.Style.FILL
        }
        redSquarePaint.apply {
            color = Color.parseColor("#D50000") // Iconic WM-2 red square
            style = Paint.Style.FILL
        }
        buttonPaint.apply {
            color = Color.parseColor("#D1D5DB")
            style = Paint.Style.FILL
        }
        batteryLedPaint.apply {
            color = Color.parseColor("#00E676")
            style = Paint.Style.FILL
        }
        textPaint.apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 32f
            letterSpacing = 0.08f
        }
        subTextPaint.apply {
            color = Color.argb(200, 255, 255, 255)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 18f
            letterSpacing = 0.15f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        chassisRect.set(0f, 0f, w.toFloat(), h.toFloat())

        // Diagonal Bezel (top right quadrant)
        val diagStartX = w * 0.58f
        val diagStartY = 0f
        val diagCornerX = w.toFloat()
        val diagBottomY = h * 0.48f

        diagonalPath.reset()
        diagonalPath.moveTo(diagStartX, diagStartY)
        diagonalPath.lineTo(w.toFloat(), diagStartY)
        diagonalPath.lineTo(w.toFloat(), diagBottomY)
        diagonalPath.lineTo(diagStartX, diagStartY + (diagBottomY - diagStartY) * 0.75f)
        diagonalPath.close()

        // Knurled Volume Dial (top right)
        dialRadius = w * 0.09f
        dialCenter.set(w * 0.84f, h * 0.07f)

        // FF & REW small buttons
        smallButtonRadius = w * 0.032f
        rewCenter.set(w * 0.72f, h * 0.15f)
        ffCenter.set(w * 0.86f, h * 0.15f)

        // Battery LED
        batteryLedCenter.set(w * 0.78f, h * 0.21f)

        // Pill-shaped Levers: PLAY & STOP
        val leverWidth = w * 0.28f
        val leverHeight = h * 0.055f
        val leverLeft = w * 0.66f

        playLeverRect.set(leverLeft, h * 0.26f, leverLeft + leverWidth, h * 0.26f + leverHeight)
        stopLeverRect.set(leverLeft, h * 0.34f, leverLeft + leverWidth, h * 0.34f + leverHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw Main Red Chassis
        canvas.drawRoundRect(chassisRect, 24f, 24f, chassisPaint)
        canvas.drawRoundRect(chassisRect, 24f, 24f, bevelPaint)

        // 2. Draw Top-Right Diagonal Black Bezel
        canvas.drawPath(diagonalPath, diagonalBezelPaint)

        // 3. Draw Knurled Volume Dial
        canvas.drawCircle(dialCenter.x, dialCenter.y, dialRadius, dialBasePaint)
        canvas.drawCircle(dialCenter.x, dialCenter.y, dialRadius * 0.7f, dialKnurlPaint)
        canvas.drawCircle(dialCenter.x, dialCenter.y, dialRadius * 0.3f, bevelPaint)

        // 4. Draw FF and REW Small Metallic Buttons
        canvas.drawCircle(rewCenter.x, rewCenter.y, smallButtonRadius, buttonPaint)
        canvas.drawCircle(rewCenter.x, rewCenter.y, smallButtonRadius, bevelPaint)
        canvas.drawCircle(ffCenter.x, ffCenter.y, smallButtonRadius, buttonPaint)
        canvas.drawCircle(ffCenter.x, ffCenter.y, smallButtonRadius, bevelPaint)

        // 5. Draw Battery Indicator LED
        batteryLedPaint.color = when {
            isCharging -> Color.parseColor("#F97316")
            batteryLevel >= 20 -> Color.parseColor("#FDE68A")
            batteryLevel >= 10 -> Color.parseColor("#F97316")
            else -> Color.parseColor("#FB7185")
        }
        canvas.drawCircle(batteryLedCenter.x, batteryLedCenter.y, 8f, batteryLedPaint)

        // 6. Draw Pill-Shaped Levers (PLAY & STOP)
        // PLAY Lever
        val cornerRadius = playLeverRect.height() / 2f
        canvas.drawRoundRect(playLeverRect, cornerRadius, cornerRadius, leverBodyPaint)
        canvas.drawRoundRect(playLeverRect, cornerRadius, cornerRadius, leverShadowPaint)
        // Emerald Green Dot on PLAY lever
        val greenDotRadius = cornerRadius * 0.45f
        canvas.drawCircle(
            playLeverRect.left + cornerRadius,
            playLeverRect.centerY(),
            greenDotRadius,
            greenDotPaint
        )

        // STOP Lever
        canvas.drawRoundRect(stopLeverRect, cornerRadius, cornerRadius, leverBodyPaint)
        canvas.drawRoundRect(stopLeverRect, cornerRadius, cornerRadius, leverShadowPaint)
        // Crimson Red Square on STOP lever
        val sqHalf = cornerRadius * 0.4f
        canvas.drawRect(
            stopLeverRect.right - cornerRadius - sqHalf,
            stopLeverRect.centerY() - sqHalf,
            stopLeverRect.right - cornerRadius + sqHalf,
            stopLeverRect.centerY() + sqHalf,
            redSquarePaint
        )

        // 7. Draw Stacked Vintage Typography (Bottom-Left)
        val textX = w * 0.08f
        val textY = h * 0.92f
        canvas.drawText("STEREO", textX, textY - 34f, subTextPaint)
        canvas.drawText("SPINDLE II", textX, textY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y

            when {
                playLeverRect.contains(x, y) -> {
                    performClick()
                    onPlayClicked?.invoke()
                    return true
                }
                stopLeverRect.contains(x, y) -> {
                    performClick()
                    onStopClicked?.invoke()
                    return true
                }
                isInsideCircle(x, y, rewCenter.x, rewCenter.y, smallButtonRadius * 2) -> {
                    performClick()
                    onPrevClicked?.invoke()
                    return true
                }
                isInsideCircle(x, y, ffCenter.x, ffCenter.y, smallButtonRadius * 2) -> {
                    performClick()
                    onNextClicked?.invoke()
                    return true
                }
            }
        }
        return true
    }

    private fun isInsideCircle(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        return (dx * dx + dy * dy) <= (r * r)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
