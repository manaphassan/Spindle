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
            field = value
            if (value) {
                if (rotationAnimator?.isRunning != true) {
                    startRotation()
                }
            } else {
                stopRotation()
            }
            invalidate()
        }

    var albumArtBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    // Animation state
    private var reelAngle1 = 0f
    private var reelAngle2 = 0f
    private var tapeTravelOffset = 0f
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
    private val tapeMicroGroovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val tapeMotionSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        color = Color.argb(90, 255, 255, 255)
    }
    private val tapeSpoolSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.argb(65, 255, 255, 255)
    }
    private val spoolFlangeSpokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(85, 255, 255, 255)
    }

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

        if (theme.id == com.hana.spindle.theme.CassetteTheme.MONOCHROME_EINK.id) {
            shellInnerBevelPaint.color = Color.BLACK
            windowPaint.color = Color.WHITE
            windowBevelPaint.color = Color.BLACK
            ruledLinePaint.color = Color.BLACK
            reelTeethPaint.color = Color.WHITE
            tapeLinePaint.color = Color.BLACK
            titleTextPaint.color = Color.BLACK
            artistTextPaint.color = Color.BLACK
            sideBadgePaint.color = Color.BLACK
            largeSideBadgePaint.color = Color.BLACK
            tapeMicroGroovePaint.color = Color.argb(60, 0, 0, 0)
            tapeMotionSheenPaint.color = Color.argb(100, 0, 0, 0)
            tapeSpoolSheenPaint.color = Color.argb(80, 0, 0, 0)
            spoolFlangeSpokePaint.color = Color.argb(110, 0, 0, 0)
        } else {
            tapeMicroGroovePaint.color = Color.argb(40, 255, 255, 255)
            tapeMotionSheenPaint.color = Color.argb(90, 255, 255, 255)
            tapeSpoolSheenPaint.color = Color.argb(65, 255, 255, 255)
            spoolFlangeSpokePaint.color = Color.argb(85, 255, 255, 255)
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
                tapeTravelOffset = (tapeTravelOffset + 2.8f * kinematicsState.leftAngularSpeed) % 40f
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
            val textRightBound = labelRect.right - 14f

            if (isVaporwave) {
                // Top neon stripe on label
                val stripeH = labelRect.height() * 0.14f
                canvas.drawRect(labelRect.left, labelRect.top, labelRect.right, labelRect.top + stripeH, labelStripePaint)

                // Top right "SIDE A" or "SIDE B" badge
                val sideText = if (isSideA) "SIDE A" else "SIDE B"
                val sideX = labelRect.right - 18f
                canvas.drawText(sideText, sideX, labelRect.top + stripeH + 24f, sideBadgePaint)
            }

            // Draw ruled lines on vertical paper label
            val lineSpacing = labelRect.height() * 0.26f
            val line1Y = labelRect.top + (if (isVaporwave) labelRect.height() * 0.42f else labelRect.height() * 0.28f)
            val line2Y = line1Y + lineSpacing
            val line3Y = line2Y + lineSpacing

            canvas.drawLine(labelRect.left + 14f, line1Y, textRightBound, line1Y, ruledLinePaint)
            canvas.drawLine(labelRect.left + 14f, line2Y, textRightBound, line2Y, ruledLinePaint)
            if (!isVaporwave) {
                canvas.drawLine(labelRect.left + 14f, line3Y, textRightBound, line3Y, ruledLinePaint)
            }

            // Dynamic title & artist fallback to device name & Android version
            val displayTitle = if (trackTitle.isNotEmpty() && trackTitle != "No Track Loaded") trackTitle else com.hana.spindle.util.DeviceUtils.getDeviceName(context)
            val displayArtist = if (artistName.isNotEmpty() && artistName != "Spindle Audio Player") artistName else com.hana.spindle.util.DeviceUtils.getAndroidVersionString()

            // Handwritten-style text above the lines
            canvas.drawText(displayArtist, labelRect.left + 20f, line1Y - 8f, artistTextPaint)
            canvas.drawText(displayTitle, labelRect.left + 20f, line2Y - 8f, titleTextPaint)
        } else {
            // Horizontal label stripe & text
            canvas.drawRect(labelRect.left, labelRect.top, labelRect.right, labelRect.top + 10f, labelStripePaint)
            val sideText = if (isSideA) "SIDE A" else "SIDE B"
            val sideX = labelRect.right - 24f
            canvas.drawText(sideText, sideX, labelRect.top + 36f, sideBadgePaint)

            val displayTitle = if (trackTitle.isNotEmpty() && trackTitle != "No Track Loaded") trackTitle else com.hana.spindle.util.DeviceUtils.getDeviceName(context)
            val displayArtist = if (artistName.isNotEmpty() && artistName != "Spindle Audio Player") artistName else com.hana.spindle.util.DeviceUtils.getAndroidVersionString()

            canvas.drawText(displayTitle, labelRect.left + 24f, labelRect.top + 60f, titleTextPaint)
            canvas.drawText(displayArtist, labelRect.left + 24f, labelRect.top + 98f, artistTextPaint)
        }

        // 3. Draw Acrylic Cassette Window
        canvas.drawRoundRect(windowRect, 14f, 14f, windowPaint)
        canvas.drawRoundRect(windowRect, 14f, 14f, windowBevelPaint)

        // 4. Calculate Differential Kinematics
        val kinematicsState = kinematics.calculate(progress, baseRadius)

        // 5. Draw Spools & Kinetic Tape Ribbon with concentric micro-grooves
        if (isVertical) {
            // Connecting vertical tape bridge
            val bridgeTopY = hubCenter1.y
            val bridgeBottomY = hubCenter2.y
            val bridgeX = hubCenter1.x + kinematicsState.leftRadius * 0.8f
            canvas.drawLine(bridgeX, bridgeTopY, hubCenter2.x + kinematicsState.rightRadius * 0.8f, bridgeBottomY, tapeLinePaint)

            // Animated traveling tape texture along the vertical ribbon
            val bridgeH = bridgeBottomY - bridgeTopY
            if (bridgeH > 0f) {
                val step = 20f
                val count = (bridgeH / step).toInt() + 1
                for (i in 0 until count) {
                    val lineY = bridgeTopY + ((i * step + tapeTravelOffset) % bridgeH)
                    canvas.drawLine(bridgeX - 4f, lineY, bridgeX + 4f, lineY, tapeMotionSheenPaint)
                }
            }

            // Top Spool (Supply)
            canvas.drawCircle(hubCenter1.x, hubCenter1.y, kinematicsState.leftRadius, tapeRibbonPaint)
            if (kinematicsState.leftRadius > hubRadius + 6f) {
                val deltaR = kinematicsState.leftRadius - hubRadius
                for (g in 1..3) {
                    canvas.drawCircle(hubCenter1.x, hubCenter1.y, hubRadius + deltaR * (g / 4f), tapeMicroGroovePaint)
                }
            }
            drawSpoolRotatingSheenAndSpokes(canvas, hubCenter1.x, hubCenter1.y, hubRadius, kinematicsState.leftRadius, reelAngle1)
            canvas.drawCircle(hubCenter1.x, hubCenter1.y, hubRadius, reelHubPaint)
            drawSpindleTeeth(canvas, hubCenter1.x, hubCenter1.y, hubRadius, reelAngle1)

            // Bottom Spool (Take-up)
            canvas.drawCircle(hubCenter2.x, hubCenter2.y, kinematicsState.rightRadius, tapeRibbonPaint)
            if (kinematicsState.rightRadius > hubRadius + 6f) {
                val deltaR = kinematicsState.rightRadius - hubRadius
                for (g in 1..3) {
                    canvas.drawCircle(hubCenter2.x, hubCenter2.y, hubRadius + deltaR * (g / 4f), tapeMicroGroovePaint)
                }
            }
            drawSpoolRotatingSheenAndSpokes(canvas, hubCenter2.x, hubCenter2.y, hubRadius, kinematicsState.rightRadius, reelAngle2)
            canvas.drawCircle(hubCenter2.x, hubCenter2.y, hubRadius, reelHubPaint)
            drawSpindleTeeth(canvas, hubCenter2.x, hubCenter2.y, hubRadius, reelAngle2)

            // 6. Draw Large "A" or "B" on bottom cassette shell
            val sideText = if (isSideA) "A" else "B"
            canvas.drawText(sideText, w * 0.5f, h * 0.90f, largeSideBadgePaint)
        } else {
            // Horizontal layout
            val bridgeLeftX = hubCenter1.x
            val bridgeRightX = hubCenter2.x
            val bridgeY = hubCenter1.y + kinematicsState.leftRadius * 0.9f
            canvas.drawLine(bridgeLeftX, bridgeY, bridgeRightX, hubCenter2.y + kinematicsState.rightRadius * 0.9f, tapeLinePaint)

            // Animated traveling tape texture along horizontal ribbon
            val bridgeW = bridgeRightX - bridgeLeftX
            if (bridgeW > 0f) {
                val step = 20f
                val count = (bridgeW / step).toInt() + 1
                for (i in 0 until count) {
                    val lineX = bridgeLeftX + ((i * step + tapeTravelOffset) % bridgeW)
                    canvas.drawLine(lineX, bridgeY - 4f, lineX, bridgeY + 4f, tapeMotionSheenPaint)
                }
            }

            // Left Spool
            canvas.drawCircle(hubCenter1.x, hubCenter1.y, kinematicsState.leftRadius, tapeRibbonPaint)
            if (kinematicsState.leftRadius > hubRadius + 6f) {
                val deltaR = kinematicsState.leftRadius - hubRadius
                for (g in 1..3) {
                    canvas.drawCircle(hubCenter1.x, hubCenter1.y, hubRadius + deltaR * (g / 4f), tapeMicroGroovePaint)
                }
            }
            drawSpoolRotatingSheenAndSpokes(canvas, hubCenter1.x, hubCenter1.y, hubRadius, kinematicsState.leftRadius, reelAngle1)
            canvas.drawCircle(hubCenter1.x, hubCenter1.y, hubRadius, reelHubPaint)
            drawSpindleTeeth(canvas, hubCenter1.x, hubCenter1.y, hubRadius, reelAngle1)

            // Right Spool
            canvas.drawCircle(hubCenter2.x, hubCenter2.y, kinematicsState.rightRadius, tapeRibbonPaint)
            if (kinematicsState.rightRadius > hubRadius + 6f) {
                val deltaR = kinematicsState.rightRadius - hubRadius
                for (g in 1..3) {
                    canvas.drawCircle(hubCenter2.x, hubCenter2.y, hubRadius + deltaR * (g / 4f), tapeMicroGroovePaint)
                }
            }
            drawSpoolRotatingSheenAndSpokes(canvas, hubCenter2.x, hubCenter2.y, hubRadius, kinematicsState.rightRadius, reelAngle2)
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

        canvas.save()
        canvas.rotate(angleDegrees, cx, cy)

        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60.0))
            val startX = cx + (teethRadius * Math.cos(angle)).toFloat()
            val startY = cy + (teethRadius * Math.sin(angle)).toFloat()
            val endX = cx + ((teethRadius + toothLength) * Math.cos(angle)).toFloat()
            val endY = cy + ((teethRadius + toothLength) * Math.sin(angle)).toFloat()

            canvas.drawLine(startX, startY, endX, endY, reelTeethPaint)
        }

        canvas.restore()
    }

    private fun drawSpoolRotatingSheenAndSpokes(canvas: Canvas, cx: Float, cy: Float, innerR: Float, outerR: Float, rotationAngle: Float) {
        if (outerR <= innerR + 4f) return

        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)

        // 1. Anisotropic Specular Sheen (2 opposing radial glare cones at 0° and 180°)
        for (cone in 0..1) {
            val baseAngle = cone * 180.0
            for (lineOffset in -1..1) {
                val rad = Math.toRadians(baseAngle + lineOffset * 9.0)
                val cos = Math.cos(rad).toFloat()
                val sin = Math.sin(rad).toFloat()
                canvas.drawLine(
                    cx + innerR * cos,
                    cy + innerR * sin,
                    cx + outerR * cos,
                    cy + outerR * sin,
                    tapeSpoolSheenPaint
                )
            }
        }

        // 2. Three Classic Reel Flange Spoke Windows / Strobe Cutouts (at 0°, 120°, 240°)
        val spokeR = innerR + (outerR - innerR) * 0.38f
        val markerRadius = ((outerR - innerR) * 0.12f).coerceIn(2.5f, 6.0f)
        for (s in 0 until 3) {
            val sAngle = Math.toRadians(s * 120.0)
            val mx = cx + (spokeR * Math.cos(sAngle)).toFloat()
            val my = cy + (spokeR * Math.sin(sAngle)).toFloat()
            canvas.drawCircle(mx, my, markerRadius, spoolFlangeSpokePaint)
        }

        // 3. Spool Tape Anchor Clamp Notch (holds tape to hub core)
        val clampAngle = Math.toRadians(45.0)
        val clampX = cx + (innerR * Math.cos(clampAngle)).toFloat()
        val clampY = cy + (innerR * Math.sin(clampAngle)).toFloat()
        val clampOuterX = cx + ((innerR + (outerR - innerR) * 0.28f) * Math.cos(clampAngle)).toFloat()
        val clampOuterY = cy + ((innerR + (outerR - innerR) * 0.28f) * Math.sin(clampAngle)).toFloat()
        canvas.drawLine(clampX, clampY, clampOuterX, clampOuterY, spoolFlangeSpokePaint)

        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying && rotationAnimator?.isRunning != true) {
            startRotation()
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            if (isPlaying && rotationAnimator?.isRunning != true) {
                startRotation()
            }
        } else {
            stopRotation()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            if (isPlaying && rotationAnimator?.isRunning != true) {
                startRotation()
            }
        } else {
            stopRotation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRotation()
    }
}
