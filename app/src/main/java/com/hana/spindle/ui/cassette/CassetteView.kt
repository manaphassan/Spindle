package com.hana.spindle.ui.cassette

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.hana.spindle.theme.CassetteTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, zero-allocation Custom View rendering a kinetic cassette tape.
 *
 * Implements:
 * 1. Differential dual-reel tape volume shift (R_left shrinking, R_right growing).
 * 2. 6-tooth rotating spindle teeth with speed varying inversely to spool radius.
 * 3. Side A (Player) <-> Side B (Tracklist) 3D camera flip.
 * 4. Dynamic cassette theme and album art label rendering.
 */
class CassetteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val kinematics = SpindleKinematics()

    var theme: CassetteTheme = CassetteTheme.WM2_RED_HERO
        set(value) {
            field = value
            updatePaints()
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

    var progress: Float = 0.25f // 0.0f to 1.0f
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
    private var reelAngleLeft = 0f
    private var reelAngleRight = 0f
    private var rotationAnimator: ValueAnimator? = null

    // Pre-allocated Paints
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelStripePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeRibbonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reelHubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reelTeethPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artistTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sideBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pre-allocated Geometries
    private val shellRect = RectF()
    private val windowRect = RectF()
    private val labelRect = RectF()
    private val leftHubCenter = PointF()
    private val rightHubCenter = PointF()
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
        windowPaint.apply {
            color = Color.parseColor("#121214") // Acrylic smoked window interior
            style = Paint.Style.FILL
        }
        windowBevelPaint.apply {
            color = Color.argb(80, 255, 255, 255)
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
            strokeWidth = 6f
        }
        titleTextPaint.apply {
            color = theme.labelTextColor
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 28f
        }
        artistTextPaint.apply {
            color = Color.argb(200, Color.red(theme.labelTextColor), Color.green(theme.labelTextColor), Color.blue(theme.labelTextColor))
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 22f
        }
        sideBadgePaint.apply {
            color = theme.labelAccentColor
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 24f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        shellRect.set(w * 0.03f, h * 0.04f, w * 0.97f, h * 0.96f)
        labelRect.set(w * 0.08f, h * 0.08f, w * 0.92f, h * 0.46f)

        // Panoramic Acrylic Window (center-bottom)
        windowRect.set(w * 0.16f, h * 0.48f, w * 0.84f, h * 0.88f)

        leftHubCenter.set(windowRect.left + windowRect.width() * 0.28f, windowRect.centerY())
        rightHubCenter.set(windowRect.left + windowRect.width() * 0.72f, windowRect.centerY())

        baseRadius = windowRect.height() * 0.5f
        hubRadius = baseRadius * kinematics.hubRadiusRatio
    }

    private fun startRotation() {
        if (rotationAnimator?.isRunning == true) return
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val kinematicsState = kinematics.calculate(progress, baseRadius)
                reelAngleLeft = (reelAngleLeft + 4f * kinematicsState.leftAngularSpeed) % 360f
                reelAngleRight = (reelAngleRight + 4f * kinematicsState.rightAngularSpeed) % 360f
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

        // 1. Draw Outer Cassette Shell
        canvas.drawRoundRect(shellRect, 16f, 16f, shellPaint)

        // 2. Draw Cassette Label (Header / Sticker)
        canvas.drawRoundRect(labelRect, 8f, 8f, labelPaint)
        // Accent Stripe on label
        canvas.drawRect(labelRect.left, labelRect.top, labelRect.right, labelRect.top + 10f, labelStripePaint)

        // Label Typography
        val sideText = if (isSideA) "SIDE A" else "SIDE B"
        canvas.drawText(sideText, labelRect.right - 110f, labelRect.top + 36f, sideBadgePaint)
        canvas.drawText(trackTitle, labelRect.left + 24f, labelRect.top + 60f, titleTextPaint)
        canvas.drawText(artistName, labelRect.left + 24f, labelRect.top + 98f, artistTextPaint)

        // 3. Draw Acrylic Cassette Window
        canvas.drawRoundRect(windowRect, 12f, 12f, windowPaint)
        canvas.drawRoundRect(windowRect, 12f, 12f, windowBevelPaint)

        // 4. Calculate Differential Kinematics for Spools
        val kinematicsState = kinematics.calculate(progress, baseRadius)

        // 5. Draw Tape Ribbon Connecting Left and Right Spools
        canvas.drawLine(
            leftHubCenter.x, leftHubCenter.y + kinematicsState.leftRadius * 0.9f,
            rightHubCenter.x, rightHubCenter.y + kinematicsState.rightRadius * 0.9f,
            tapeLinePaint
        )

        // 6. Draw Supply Spool (Left) - Tape Pack & Hub
        canvas.drawCircle(leftHubCenter.x, leftHubCenter.y, kinematicsState.leftRadius, tapeRibbonPaint)
        canvas.drawCircle(leftHubCenter.x, leftHubCenter.y, hubRadius, reelHubPaint)
        drawSpindleTeeth(canvas, leftHubCenter.x, leftHubCenter.y, hubRadius, reelAngleLeft)

        // 7. Draw Take-up Spool (Right) - Tape Pack & Hub
        canvas.drawCircle(rightHubCenter.x, rightHubCenter.y, kinematicsState.rightRadius, tapeRibbonPaint)
        canvas.drawCircle(rightHubCenter.x, rightHubCenter.y, hubRadius, reelHubPaint)
        drawSpindleTeeth(canvas, rightHubCenter.x, rightHubCenter.y, hubRadius, reelAngleRight)

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
