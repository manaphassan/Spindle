package com.hana.spindle.ui.cassette

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.hana.spindle.theme.CassetteTheme
import com.hana.spindle.theme.ChassisStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, zero-allocation Custom View rendering a kinetic cassette tape.
 *
 * Implements:
 * 1. Dual-orientation rendering:
 *    - Vertical cassette tape for Vertical Studio Deck (with ruled paper label, top/bottom spools, Side A).
 *    - Horizontal panoramic cassette tape for WM-2 Red and 80s Vaporwave.
 * 2. Differential dual-reel tape volume shift (R_supply shrinking, R_takeup growing).
 * 3. 6-tooth rotating spindle teeth with speed varying inversely to spool radius.
 * 4. Side A (Player) <-> Side B (Tracklist) 3D camera flip.
 */
class CassetteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val kinematics = SpindleKinematics()

    var theme: CassetteTheme = CassetteTheme.VERTICAL_STUDIO_DECK
        set(value) {
            field = value
            updatePaints()
            requestLayout()
            invalidate()
        }

    var isSideA: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var trackTitle: String = "No Track Loaded"
        set(value) {
            field = value
            invalidate()
        }

    var artistName: String = "Spindle Audio Player"
        set(value) {
            field = value
            invalidate()
        }

    var progress: Float = 0.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) startRotation() else stopRotation()
            }
        }

    // Animation state
    private var reelAngle1 = 0f
    private var reelAngle2 = 0f
    private var rotationAnimator: ValueAnimator? = null

    // Pre-allocated Paints (Zero allocation in onDraw)
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shellInnerBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelStripePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ruledLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeRibbonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reelHubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reelTeethPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artistTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sideBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val largeSideBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pre-allocated Geometries
    private val shellRect = RectF()
    private val windowRect = RectF()
    private val labelRect = RectF()
    private val hubCenter1 = PointF()
    private val hubCenter2 = PointF()
    private var hubRadius = 0f
    private var baseRadius = 0f

    // 3D Camera for Side A/B Flip
    private val flipCamera = Camera()
    private val flipMatrix = Matrix()
    var flipRotationY: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updatePaints()
    }

    private fun updatePaints() {
        shellPaint.apply {
            color = theme.shellColor
            style = Paint.Style.FILL
        }
        shellInnerBevelPaint.apply {
            color = Color.argb(40, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        windowPaint.apply {
            color = Color.parseColor("#121214")
            style = Paint.Style.FILL
        }
        windowBevelPaint.apply {
            color = Color.argb(70, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        labelPaint.apply {
            color = theme.labelBackgroundColor
            style = Paint.Style.FILL
        }
        labelStripePaint.apply {
            color = theme.labelAccentColor
            style = Paint.Style.FILL
        }
        ruledLinePaint.apply {
            color = Color.argb(50, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        tapeRibbonPaint.apply {
            color = theme.tapeRibbonColor
            style = Paint.Style.FILL
        }
        reelHubPaint.apply {
            color = theme.reelHubColor
            style = Paint.Style.FILL
        }
        reelTeethPaint.apply {
            color = Color.parseColor("#1C1C1E")
            style = Paint.Style.FILL
        }
        tapeLinePaint.apply {
            color = theme.tapeRibbonColor
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        titleTextPaint.apply {
            color = theme.labelTextColor
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        artistTextPaint.apply {
            color = theme.labelTextColor
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        sideBadgePaint.apply {
            color = theme.labelAccentColor
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        largeSideBadgePaint.apply {
            color = theme.labelAccentColor
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        val isVertical = theme.chassisStyle != ChassisStyle.WM2_RED

        if (isVertical) {
            // Vertical Cassette Geometry (Vertical Studio Deck & 80s Vaporwave)
            shellRect.set(w * 0.04f, h * 0.03f, w * 0.96f, h * 0.97f)

            // Paper Label (Upper 40%)
            labelRect.set(w * 0.10f, h * 0.06f, w * 0.90f, h * 0.40f)

            // Central Vertical Window (42% to 76%)
            windowRect.set(w * 0.22f, h * 0.42f, w * 0.78f, h * 0.76f)

            // Top Spool & Bottom Spool
            hubCenter1.set(windowRect.centerX(), windowRect.top + windowRect.height() * 0.28f)
            hubCenter2.set(windowRect.centerX(), windowRect.top + windowRect.height() * 0.72f)

            baseRadius = windowRect.width() * 0.38f
            hubRadius = baseRadius * kinematics.hubRadiusRatio

            artistTextPaint.textSize = w * 0.045f
            titleTextPaint.textSize = w * 0.040f
            sideBadgePaint.textSize = w * 0.042f
            largeSideBadgePaint.textSize = w * 0.16f
        } else {
            // Horizontal Cassette Geometry (WM-2 Red & 80s Vaporwave)
            shellRect.set(w * 0.03f, h * 0.04f, w * 0.97f, h * 0.96f)
            labelRect.set(w * 0.08f, h * 0.08f, w * 0.92f, h * 0.46f)
            windowRect.set(w * 0.16f, h * 0.48f, w * 0.84f, h * 0.88f)

            hubCenter1.set(windowRect.left + windowRect.width() * 0.28f, windowRect.centerY())
            hubCenter2.set(windowRect.left + windowRect.width() * 0.72f, windowRect.centerY())

            baseRadius = windowRect.height() * 0.5f
            hubRadius = baseRadius * kinematics.hubRadiusRatio

            artistTextPaint.textSize = 22f
            titleTextPaint.textSize = 26f
            sideBadgePaint.textSize = 24f
            largeSideBadgePaint.textSize = 36f
        }
    }

    private fun startRotation() {
        if (rotationAnimator?.isRunning == true) return
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val kinematicsState = kinematics.calculate(progress, baseRadius)
                reelAngle1 = (reelAngle1 + 4f * kinematicsState.leftAngularSpeed) % 360f
                reelAngle2 = (reelAngle2 + 4f * kinematicsState.rightAngularSpeed) % 360f
                invalidate()
            }
            start()
        }
    }

    private fun stopRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        canvas.save()

        // 3D Camera Y-Axis Flip Transform (Side A <-> Side B)
        if (flipRotationY != 0f) {
            flipCamera.save()
            flipCamera.rotateY(flipRotationY)
            flipCamera.getMatrix(flipMatrix)
            flipCamera.restore()

            flipMatrix.preTranslate(-w / 2f, -h / 2f)
            flipMatrix.postTranslate(w / 2f, h / 2f)
            canvas.concat(flipMatrix)
        }

        val isVertical = theme.chassisStyle != ChassisStyle.WM2_RED

        // 1. Draw Outer Cassette Shell
        canvas.drawRoundRect(shellRect, 16f, 16f, shellPaint)
        canvas.drawRoundRect(shellRect, 16f, 16f, shellInnerBevelPaint)

        // 2. Draw Paper Label
        canvas.drawRoundRect(labelRect, 8f, 8f, labelPaint)

        if (isVertical) {
            val isVaporwave = theme.chassisStyle == ChassisStyle.VAPORWAVE_80S

            if (isVaporwave) {
                // Top neon stripe on label
                val stripeH = labelRect.height() * 0.14f
                canvas.drawRect(labelRect.left, labelRect.top, labelRect.right, labelRect.top + stripeH, labelStripePaint)

                // Top right "SIDE A" or "SIDE B" badge
                val sideText = if (isSideA) "SIDE A" else "SIDE B"
                canvas.drawText(sideText, labelRect.right - 18f, labelRect.top + stripeH + 24f, sideBadgePaint)
            }

            // Draw ruled lines on vertical paper label
            val lineSpacing = labelRect.height() * 0.26f
            val line1Y = labelRect.top + (if (isVaporwave) labelRect.height() * 0.42f else labelRect.height() * 0.28f)
            val line2Y = line1Y + lineSpacing
            val line3Y = line2Y + lineSpacing

            canvas.drawLine(labelRect.left + 14f, line1Y, labelRect.right - 14f, line1Y, ruledLinePaint)
            canvas.drawLine(labelRect.left + 14f, line2Y, labelRect.right - 14f, line2Y, ruledLinePaint)
            if (!isVaporwave) {
                canvas.drawLine(labelRect.left + 14f, line3Y, labelRect.right - 14f, line3Y, ruledLinePaint)
            }

            // Handwritten-style text above the lines
            canvas.drawText(artistName, labelRect.left + 20f, line1Y - 8f, artistTextPaint)
            canvas.drawText(trackTitle, labelRect.left + 20f, line2Y - 8f, titleTextPaint)
        } else {
            // Horizontal label stripe & text
            canvas.drawRect(labelRect.left, labelRect.top, labelRect.right, labelRect.top + 10f, labelStripePaint)
            val sideText = if (isSideA) "SIDE A" else "SIDE B"
            canvas.drawText(sideText, labelRect.right - 24f, labelRect.top + 36f, sideBadgePaint)
            canvas.drawText(trackTitle, labelRect.left + 24f, labelRect.top + 60f, titleTextPaint)
            canvas.drawText(artistName, labelRect.left + 24f, labelRect.top + 98f, artistTextPaint)
        }

        // 3. Draw Acrylic Cassette Window
        canvas.drawRoundRect(windowRect, 14f, 14f, windowPaint)
        canvas.drawRoundRect(windowRect, 14f, 14f, windowBevelPaint)

        // 4. Calculate Differential Kinematics
        val kinematicsState = kinematics.calculate(progress, baseRadius)

        // 5. Draw Spools & Kinetic Tape Ribbon
        if (isVertical) {
            // Connecting vertical tape bridge
            canvas.drawLine(
                hubCenter1.x + kinematicsState.leftRadius * 0.8f, hubCenter1.y,
                hubCenter2.x + kinematicsState.rightRadius * 0.8f, hubCenter2.y,
                tapeLinePaint
            )

            // Top Spool (Supply)
            canvas.drawCircle(hubCenter1.x, hubCenter1.y, kinematicsState.leftRadius, tapeRibbonPaint)
            canvas.drawCircle(hubCenter1.x, hubCenter1.y, hubRadius, reelHubPaint)
            drawSpindleTeeth(canvas, hubCenter1.x, hubCenter1.y, hubRadius, reelAngle1)

            // Bottom Spool (Take-up)
            canvas.drawCircle(hubCenter2.x, hubCenter2.y, kinematicsState.rightRadius, tapeRibbonPaint)
            canvas.drawCircle(hubCenter2.x, hubCenter2.y, hubRadius, reelHubPaint)
            drawSpindleTeeth(canvas, hubCenter2.x, hubCenter2.y, hubRadius, reelAngle2)

            // 6. Draw Large "A" or "B" on bottom cassette shell
            val sideText = if (isSideA) "A" else "B"
            canvas.drawText(sideText, w * 0.5f, h * 0.90f, largeSideBadgePaint)
        } else {
            // Horizontal layout
            canvas.drawLine(
                hubCenter1.x, hubCenter1.y + kinematicsState.leftRadius * 0.9f,
                hubCenter2.x, hubCenter2.y + kinematicsState.rightRadius * 0.9f,
                tapeLinePaint
            )

            // Left Spool
            canvas.drawCircle(hubCenter1.x, hubCenter1.y, kinematicsState.leftRadius, tapeRibbonPaint)
            canvas.drawCircle(hubCenter1.x, hubCenter1.y, hubRadius, reelHubPaint)
            drawSpindleTeeth(canvas, hubCenter1.x, hubCenter1.y, hubRadius, reelAngle1)

            // Right Spool
            canvas.drawCircle(hubCenter2.x, hubCenter2.y, kinematicsState.rightRadius, tapeRibbonPaint)
            canvas.drawCircle(hubCenter2.x, hubCenter2.y, hubRadius, reelHubPaint)
            drawSpindleTeeth(canvas, hubCenter2.x, hubCenter2.y, hubRadius, reelAngle2)
        }

        canvas.restore()
    }

    /**
     * Draws the classic 6-tooth plastic spindle teeth inside the tape hub.
     */
    private fun drawSpindleTeeth(canvas: Canvas, cx: Float, cy: Float, radius: Float, angleDegrees: Float) {
        val teethRadius = radius * 0.38f
        val toothLength = radius * 0.28f

        canvas.drawCircle(cx, cy, teethRadius, reelTeethPaint)

        for (i in 0 until 6) {
            val angleRad = Math.toRadians((angleDegrees + i * 60).toDouble())
            val startX = (cx + teethRadius * cos(angleRad)).toFloat()
            val startY = (cy + teethRadius * sin(angleRad)).toFloat()
            val endX = (cx + (teethRadius + toothLength) * cos(angleRad)).toFloat()
            val endY = (cy + (teethRadius + toothLength) * sin(angleRad)).toFloat()

            canvas.drawLine(startX, startY, endX, endY, reelTeethPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRotation()
    }
}
