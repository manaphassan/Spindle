package com.hana.spindle.lite.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 60 FPS Hardware-Accelerated Kinetic Cassette Deck View for Spindle Lite.
 * Zero-allocation in onDraw(): All Paint, RectF, and Path objects are pre-allocated.
 * Scales fluidly to 3.0" 320x480 HVGA displays and modern high-res screens.
 */
class LiteDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- State Variables ---
    private var isPlaying = false
    private var progressFraction = 0.0f
    private var leftRotationDegrees = 0.0f
    private var rightRotationDegrees = 0.0f
    private var lastFrameTimeNanos = 0L

    // --- Pre-allocated Geometry Objects ---
    private val chassisRect = RectF()
    private val windowRect = RectF()
    private val labelRect = RectF()
    private val bridgeRect = RectF()
    private val tapePath = Path()

    // Hub Coordinates & Dimensions
    private var leftHubX = 0f
    private var rightHubX = 0f
    private var hubCenterY = 0f
    private var hubRadius = 0f
    private var minHubRadius = 0f
    private var maxTapeRadius = 0f

    // --- Pre-allocated Paint Objects ---
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2E45") // Brand Indigo
        style = Paint.Style.FILL
    }

    private val chassisBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B405D")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#181A27") // Deep tape chamber
        style = Paint.Style.FILL
    }

    private val windowBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B405D")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FDE68A") // Cassette Amber Label
        style = Paint.Style.FILL
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E2132")
        textSize = 20f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val tapePackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A2218") // Dark oxide brown
        style = Paint.Style.FILL
    }

    private val tapeRibbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B1810")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val hubBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAF9") // White hub
        style = Paint.Style.FILL
    }

    private val hubCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#181A27")
        style = Paint.Style.FILL
    }

    private val hubSpokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#181A27")
        style = Paint.Style.FILL
    }

    private val accentSpokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F97316") // Spindle Orange hero spoke
        style = Paint.Style.FILL
    }

    private val rollerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0B4CE")
        style = Paint.Style.FILL
    }

    init {
        // Enforce hardware layer for buttery smooth 60 FPS drawing
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val padding = w * 0.04f
        chassisRect.set(padding, padding, w - padding, h - padding)

        val windowWidth = chassisRect.width() * 0.78f
        val windowHeight = chassisRect.height() * 0.44f
        val windowLeft = chassisRect.centerX() - (windowWidth / 2f)
        val windowTop = chassisRect.centerY() - (windowHeight / 2f) + (h * 0.02f)
        windowRect.set(windowLeft, windowTop, windowLeft + windowWidth, windowTop + windowHeight)

        // Top Label Strip
        val labelHeight = chassisRect.height() * 0.16f
        val labelTop = chassisRect.top + (chassisRect.height() * 0.06f)
        labelRect.set(chassisRect.left + (chassisRect.width() * 0.08f), labelTop, chassisRect.right - (chassisRect.width() * 0.08f), labelTop + labelHeight)
        labelTextPaint.textSize = labelHeight * 0.42f

        // Hub Centers & Tape Geometry
        hubCenterY = windowRect.centerY()
        val hubSpacing = windowRect.width() * 0.28f
        leftHubX = windowRect.centerX() - hubSpacing
        rightHubX = windowRect.centerX() + hubSpacing

        minHubRadius = windowHeight * 0.22f
        hubRadius = minHubRadius
        maxTapeRadius = windowHeight * 0.44f

        // Center Spindle Bridge
        val bridgeWidth = windowRect.width() * 0.20f
        val bridgeHeight = windowHeight * 0.65f
        bridgeRect.set(windowRect.centerX() - (bridgeWidth / 2f), windowRect.centerY() - (bridgeHeight / 2f), windowRect.centerX() + (bridgeWidth / 2f), windowRect.centerY() + (bridgeHeight / 2f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Draw Cassette Outer Body (Chassis)
        val cornerRadius = w * 0.03f
        canvas.drawRoundRect(chassisRect, cornerRadius, cornerRadius, chassisPaint)
        canvas.drawRoundRect(chassisRect, cornerRadius, cornerRadius, chassisBorderPaint)

        // 2. Draw Label Strip
        val labelRadius = cornerRadius * 0.6f
        canvas.drawRoundRect(labelRect, labelRadius, labelRadius, labelPaint)
        canvas.drawText(
            "SPINDLE • HIGH BIAS TYPE II",
            labelRect.centerX(),
            labelRect.centerY() + (labelTextPaint.textSize * 0.35f),
            labelTextPaint
        )

        // 3. Draw Tape Chamber Window
        val winRadius = cornerRadius * 0.5f
        canvas.drawRoundRect(windowRect, winRadius, winRadius, windowPaint)

        // 4. Calculate Kinetic Tape Pack Radii (Conservation of tape area)
        val rMinSq = minHubRadius * minHubRadius
        val rMaxSq = maxTapeRadius * maxTapeRadius
        val diffSq = rMaxSq - rMinSq

        // Supply (left) shrinks from max to min as progress goes 0 -> 1
        val leftRadius = sqrt((1.0f - progressFraction) * diffSq + rMinSq)
        // Takeup (right) grows from min to max as progress goes 0 -> 1
        val rightRadius = sqrt(progressFraction * diffSq + rMinSq)

        // Draw Left & Right Magnetic Tape Packs
        canvas.drawCircle(leftHubX, hubCenterY, leftRadius, tapePackPaint)
        canvas.drawCircle(rightHubX, hubCenterY, rightRadius, tapePackPaint)

        // 5. Draw Tangent Tape Ribbon connecting the two reels
        tapePath.reset()
        tapePath.moveTo(leftHubX, hubCenterY + leftRadius)
        tapePath.lineTo(rightHubX, hubCenterY + rightRadius)
        canvas.drawPath(tapePath, tapeRibbonPaint)

        // 6. Draw Tape Guide Rollers & Capstans
        val rollerRadius = minHubRadius * 0.35f
        canvas.drawCircle(windowRect.left + rollerRadius * 1.5f, windowRect.bottom - rollerRadius * 1.5f, rollerRadius, rollerPaint)
        canvas.drawCircle(windowRect.right - rollerRadius * 1.5f, windowRect.bottom - rollerRadius * 1.5f, rollerRadius, rollerPaint)

        // 7. Draw Center Window Spindle Bridge
        canvas.drawRoundRect(bridgeRect, 4f, 4f, chassisPaint)
        canvas.drawRoundRect(bridgeRect, 4f, 4f, windowBorderPaint)

        // 8. Draw Rotating Hubs
        drawHub(canvas, leftHubX, hubCenterY, leftRotationDegrees)
        drawHub(canvas, rightHubX, hubCenterY, rightRotationDegrees)

        // 9. Draw Window Border
        canvas.drawRoundRect(windowRect, winRadius, winRadius, windowBorderPaint)

        // 10. Frame Loop Animation
        if (isPlaying) {
            val now = System.nanoTime()
            if (lastFrameTimeNanos > 0L) {
                val dtSeconds = (now - lastFrameTimeNanos) / 1_000_000_000.0f
                // Tape velocity constant v = 4.7625 cm/s -> rotational speed proportional to 1/radius
                val baseSpeed = 160.0f // deg/sec base
                val leftSpeed = baseSpeed * (maxTapeRadius / leftRadius)
                val rightSpeed = baseSpeed * (maxTapeRadius / rightRadius)

                leftRotationDegrees = (leftRotationDegrees + leftSpeed * dtSeconds) % 360f
                rightRotationDegrees = (rightRotationDegrees + rightSpeed * dtSeconds) % 360f
            }
            lastFrameTimeNanos = now
            postInvalidateOnAnimation()
        } else {
            lastFrameTimeNanos = 0L
        }
    }

    private fun drawHub(canvas: Canvas, cx: Float, cy: Float, rotationDeg: Float) {
        // Outer White Plastic Hub Ring
        canvas.drawCircle(cx, cy, hubRadius, hubBodyPaint)

        // 6-Tooth Hub Spokes
        val spokeRadius = hubRadius * 0.72f
        val spokeWidth = hubRadius * 0.22f
        val centerHoleRadius = hubRadius * 0.38f

        for (i in 0 until 6) {
            val angleDeg = rotationDeg + (i * 60f)
            val angleRad = angleDeg * (PI.toFloat() / 180f)
            val spokeX = cx + (spokeRadius * cos(angleRad))
            val spokeY = cy + (spokeRadius * sin(angleRad))

            val paint = if (i == 0) accentSpokePaint else hubSpokePaint
            canvas.drawCircle(spokeX, spokeY, spokeWidth * 0.5f, paint)
        }

        // Center Spindle Axle Hole
        canvas.drawCircle(cx, cy, centerHoleRadius, hubCenterPaint)
    }

    fun setPlaybackState(playing: Boolean, fraction: Float) {
        this.isPlaying = playing
        this.progressFraction = fraction.coerceIn(0f, 1f)
        if (playing) {
            postInvalidateOnAnimation()
        } else {
            invalidate()
        }
    }
}
