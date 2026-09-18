package com.hana.spindle.ui.catalog

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * Audiophile Circular Vinyl Turntable Disc View.
 *
 * Improvised Now Playing centerpiece featuring:
 * 1. Clean circular album artwork label with optical anti-aliasing.
 * 2. Authentic concentric vinyl micro-grooves in the outer dark rim.
 * 3. Continuous 33 1/3 RPM vinyl spinning animation when music plays.
 * 4. Metallic center spindle hub / clamp detail with radial strobe dots.
 * 5. Ambient physical elevation shadow.
 * 6. Swipe left / right gesture detection for track skipping.
 * 7. Tap gesture for flipping to synchronized lyrics.
 */
class CircularCoverArcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var coverBitmap: Bitmap? = null
        set(value) {
            field = value
            updateShader()
            invalidate()
        }

    private var cueProgress: Float = 0.0f
    private var tonearmAnimator: ValueAnimator? = null

    private var spinAngle: Float = 0.0f
    private var spinAnimator: ValueAnimator? = null

    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                animateTonearm(value)
                if (value) {
                    startSpinAnimation()
                } else {
                    pauseSpinAnimation()
                }
            } else if (value && cueProgress < 1f && (tonearmAnimator == null || !tonearmAnimator!!.isRunning)) {
                animateTonearm(true)
                if (spinAnimator == null || !spinAnimator!!.isRunning) {
                    startSpinAnimation()
                }
            }
        }

    private fun startSpinAnimation() {
        if (isEink) return // Prevent display ghosting on monochrome e-ink
        spinAnimator?.cancel()
        val current = spinAngle % 360f
        // 33 1/3 RPM authentic turntable aesthetic: ~18s per full 360° rotation
        spinAnimator = ValueAnimator.ofFloat(current, current + 360f).apply {
            duration = 18000L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                spinAngle = (anim.animatedValue as Float) % 360f
                invalidate()
            }
            start()
        }
    }

    private fun pauseSpinAnimation() {
        spinAnimator?.cancel()
        spinAnimator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying) {
            cueProgress = 1f
            if (!isEink && (spinAnimator == null || !spinAnimator!!.isRunning)) {
                startSpinAnimation()
            }
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            if (isPlaying) {
                if (cueProgress < 0.5f) {
                    cueProgress = 1f
                }
                if (!isEink && (spinAnimator == null || !spinAnimator!!.isRunning)) {
                    startSpinAnimation()
                }
                invalidate()
            }
        } else {
            pauseSpinAnimation()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && isPlaying) {
            if (cueProgress < 0.5f) {
                cueProgress = 1f
            }
            if (!isEink && (spinAnimator == null || !spinAnimator!!.isRunning)) {
                startSpinAnimation()
            }
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        spinAnimator?.cancel()
        spinAnimator = null
        tonearmAnimator?.cancel()
    }

    private fun animateTonearm(playing: Boolean) {
        tonearmAnimator?.cancel()
        val target = if (playing) 1f else 0f
        tonearmAnimator = ValueAnimator.ofFloat(cueProgress, target).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                cueProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // Playback progress (0.0f..1.0f) for realistic tonearm groove tracking
    var progress: Float = 0.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (isPlaying) {
                invalidate()
            }
        }

    var onSeek: ((Float) -> Unit)? = null
    var onSeekStarted: (() -> Unit)? = null
    var onSeekStopped: (() -> Unit)? = null

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null
    var onCoverClicked: (() -> Unit)? = null

    var isDarkMode: Boolean = true
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var isEink: Boolean = false
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var accentColor: Int = Color.parseColor("#F97316")
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    private val vinylRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val vinylGroovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }

    private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private val coverShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val centerSpindleClampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val centerSpindleHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tonearmBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tonearmShaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3.2f
    }

    private val tonearmHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tonearmNeedlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tonearmCradlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.5f
    }

    private val needleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tonearmHeadRect = RectF()

    private val coverClipPath = Path()

    private var discRadius = 0f
    private var coverRadius = 0f
    private var centerX = 0f
    private var centerY = 0f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onCoverClicked?.invoke()
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dx) > abs(dy) && abs(dx) > 100 && abs(velocityX) > 150) {
                if (dx < 0) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSwipeLeft?.invoke()
                } else {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSwipeRight?.invoke()
                }
                return true
            }
            return false
        }

        override fun onDown(e: MotionEvent): Boolean = true
    })

    init {
        updatePaints()
    }

    private fun updatePaints() {
        if (isEink) {
            vinylRimPaint.color = Color.WHITE
            vinylRimPaint.style = Paint.Style.FILL
            vinylGroovePaint.color = Color.BLACK
            vinylGroovePaint.style = Paint.Style.STROKE
            vinylGroovePaint.strokeWidth = 2f
            coverShadowPaint.color = Color.TRANSPARENT
            centerSpindleClampPaint.color = Color.BLACK
            centerSpindleHolePaint.color = Color.WHITE
            tonearmBasePaint.color = Color.BLACK
            tonearmShaftPaint.color = Color.BLACK
            tonearmHeadPaint.color = Color.BLACK
            tonearmNeedlePaint.color = Color.BLACK
            tonearmCradlePaint.color = Color.BLACK
        } else if (isDarkMode) {
            vinylRimPaint.color = Color.parseColor("#15161B")
            vinylGroovePaint.color = Color.argb(40, 255, 255, 255)
            coverShadowPaint.color = Color.argb(120, 0, 0, 0)
            centerSpindleClampPaint.color = Color.parseColor("#262938")
            centerSpindleHolePaint.color = Color.parseColor("#0F1016")
            tonearmBasePaint.color = Color.parseColor("#333846")
            tonearmShaftPaint.color = Color.parseColor("#94A3B8")
            tonearmHeadPaint.color = Color.parseColor("#E2E8F0")
            tonearmNeedlePaint.color = accentColor
            tonearmCradlePaint.color = Color.parseColor("#475569")
        } else {
            // Light Theme
            vinylRimPaint.color = Color.parseColor("#222329")
            vinylGroovePaint.color = Color.argb(35, 255, 255, 255)
            coverShadowPaint.color = Color.argb(45, 0, 0, 0)
            centerSpindleClampPaint.color = Color.parseColor("#E5E5E2")
            centerSpindleHolePaint.color = Color.parseColor("#1E2132")
            tonearmBasePaint.color = Color.parseColor("#CBD5E1")
            tonearmShaftPaint.color = Color.parseColor("#64748B")
            tonearmHeadPaint.color = Color.parseColor("#1E293B")
            tonearmNeedlePaint.color = accentColor
            tonearmCradlePaint.color = Color.parseColor("#94A3B8")
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        centerX = w * 0.5f
        centerY = h * 0.5f

        val minDim = min(w, h).toFloat()
        discRadius = minDim * 0.46f
        coverRadius = discRadius * 0.65f // Vinyl label ratio

        coverClipPath.reset()
        coverClipPath.addCircle(centerX, centerY, coverRadius, Path.Direction.CW)

        updatePaints()
        updateShader()
    }

    private fun updateShader() {
        val bmp = coverBitmap
        if (bmp != null && coverRadius > 0f) {
            val matrix = Matrix()
            val scale = max(
                (coverRadius * 2f) / bmp.width,
                (coverRadius * 2f) / bmp.height
            )
            val dx = centerX - (bmp.width * scale * 0.5f)
            val dy = centerY - (bmp.height * scale * 0.5f)
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)

            val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(matrix)
            coverPaint.shader = shader
        } else {
            coverPaint.shader = null
            coverPaint.color = if (isEink) Color.WHITE else if (isDarkMode) Color.parseColor("#262938") else Color.parseColor("#E5E5E2")
        }
    }

    private val vinylSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val sheenArcRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (centerX == 0f || centerY == 0f || discRadius <= 0f) return

        // 1. Soft Ambient Drop Shadow under outer vinyl disc (Stationary)
        canvas.drawCircle(centerX, centerY + 8f, discRadius + 3f, coverShadowPaint)

        // 2. Spinning Vinyl Turntable Platter (Rotates continuously at 33 1/3 RPM when music plays)
        canvas.save()
        canvas.rotate(spinAngle, centerX, centerY)

        // 2a. Vinyl Record Outer Bezel / Rim
        canvas.drawCircle(centerX, centerY, discRadius, vinylRimPaint)
        if (isEink) {
            canvas.drawCircle(centerX, centerY, discRadius, vinylGroovePaint)
        }

        // 2b. Concentric Vinyl Micro-Grooves (Audio track grooves)
        if (!isEink) {
            val grooveSpacing = (discRadius - coverRadius) / 5f
            for (g in 1..4) {
                val r = coverRadius + (g * grooveSpacing)
                canvas.drawCircle(centerX, centerY, r, vinylGroovePaint)
            }

            // Authentic anisotropic light sheen wedges (creates realistic vinyl shimmer as it rotates)
            sheenArcRect.set(centerX - discRadius, centerY - discRadius, centerX + discRadius, centerY + discRadius)
            vinylSheenPaint.color = if (isDarkMode) Color.argb(16, 255, 255, 255) else Color.argb(12, 255, 255, 255)
            canvas.drawArc(sheenArcRect, 35f, 32f, true, vinylSheenPaint)
            canvas.drawArc(sheenArcRect, 215f, 32f, true, vinylSheenPaint)
        }

        // 2c. Centered Circular Album Artwork Label
        canvas.save()
        canvas.clipPath(coverClipPath)
        if (coverPaint.shader != null) {
            canvas.drawPaint(coverPaint)
        } else {
            canvas.drawCircle(centerX, centerY, coverRadius, coverPaint)
            // Default geometric placeholder music notes (vector drawing, zero emojis)
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isEink) Color.BLACK else if (isDarkMode) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
                style = Paint.Style.FILL
            }
            val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = notePaint.color
                style = Paint.Style.STROKE
                strokeWidth = (coverRadius * 0.05f).coerceAtLeast(2.5f)
                strokeCap = Paint.Cap.ROUND
            }
            val noteScale = coverRadius * 0.22f
            val x1 = centerX - noteScale * 0.6f
            val y1 = centerY + noteScale * 0.4f
            canvas.drawCircle(x1, y1, noteScale * 0.32f, notePaint)
            canvas.drawLine(x1 + noteScale * 0.25f, y1, x1 + noteScale * 0.25f, centerY - noteScale * 0.6f, stemPaint)

            val x2 = centerX + noteScale * 0.6f
            val y2 = centerY + noteScale * 0.15f
            canvas.drawCircle(x2, y2, noteScale * 0.32f, notePaint)
            canvas.drawLine(x2 + noteScale * 0.25f, y2, x2 + noteScale * 0.25f, centerY - noteScale * 0.85f, stemPaint)

            canvas.drawLine(x1 + noteScale * 0.25f, centerY - noteScale * 0.6f, x2 + noteScale * 0.25f, centerY - noteScale * 0.85f, stemPaint)
        }
        canvas.restore()

        // 2d. Outer Bezel Ring for Artwork Label
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = if (isEink) Color.BLACK else if (isDarkMode) Color.argb(60, 255, 255, 255) else Color.argb(40, 0, 0, 0)
        }
        canvas.drawCircle(centerX, centerY, coverRadius, borderPaint)

        // 2e. Precision Center Spindle Clamp & Hole with 6 Radial Machined Strobe Dots
        val clampRadius = coverRadius * 0.20f
        val holeRadius = clampRadius * 0.40f

        canvas.drawCircle(centerX, centerY, clampRadius, centerSpindleClampPaint)

        // 6 radial machined strobe dots for visible mechanical rotation cues
        val dotRadius = clampRadius * 0.70f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isEink) Color.WHITE else if (isDarkMode) Color.argb(140, 255, 255, 255) else Color.argb(120, 42, 46, 69)
            style = Paint.Style.FILL
        }
        for (i in 0 until 6) {
            val angleRad = Math.toRadians(i * 60.0)
            val dx = (centerX + cos(angleRad) * dotRadius).toFloat()
            val dy = (centerY + sin(angleRad) * dotRadius).toFloat()
            canvas.drawCircle(dx, dy, clampRadius * 0.09f, dotPaint)
        }

        canvas.drawCircle(centerX, centerY, holeRadius, centerSpindleHolePaint)

        canvas.restore() // End of rotating vinyl platter

        // 3. Vintage Precision Aluminum Tonearm with Cueing Drop/Lift Ritual (Stationary)
        if (!isEink) {
            drawTonearm(canvas)
        }
    }

    private fun drawTonearm(canvas: Canvas) {
        if (discRadius <= 0f) return

        // 1. Precise Pivot Base Coordinates (Positioned on plinth top-right of the vinyl platter)
        val pivotX = (centerX + discRadius * 0.92f).coerceAtMost(width - 24f)
        val pivotY = centerY - discRadius * 0.82f
        val armLength = discRadius * 0.72f

        // 2. Tonearm Rest Cradle Post (Parked position off the vinyl platter)
        val restAngle = 75f
        val cradleRad = Math.toRadians(restAngle.toDouble())
        val cradleDist = armLength * 0.82f
        val cradleX = (pivotX + cos(cradleRad) * cradleDist).toFloat()
        val cradleY = (pivotY + sin(cradleRad) * cradleDist).toFloat()
        val normalRad = cradleRad + Math.PI / 2.0
        val nx = (cos(normalRad) * 6f).toFloat()
        val ny = (sin(normalRad) * 6f).toFloat()
        canvas.drawLine(cradleX - nx, cradleY - ny, cradleX + nx, cradleY + ny, tonearmCradlePaint)
        canvas.drawCircle(cradleX, cradleY, 2.5f, tonearmBasePaint)

        // 3. Dynamic Animated Playing Angle
        // At rest: 75° (parked on cradle post, 1.11 * discRadius outside platter)
        // When playing: smoothly tracks vinyl groove from 94° (outer track ~0.85 * R) to 106° (inner track ~0.70 * R)
        val playAngle = 94f + 12f * progress.coerceIn(0f, 1f)
        val currentAngle = restAngle + (playAngle - restAngle) * cueProgress
        val rad = Math.toRadians(currentAngle.toDouble())

        // 4. Counterweight Stub Behind Pivot
        val counterAngleRad = rad + Math.PI
        val counterLen = discRadius * 0.16f
        val counterX = (pivotX + cos(counterAngleRad) * counterLen).toFloat()
        val counterY = (pivotY + sin(counterAngleRad) * counterLen).toFloat()
        canvas.drawLine(pivotX, pivotY, counterX, counterY, tonearmShaftPaint)
        canvas.drawCircle(counterX, counterY, 8f, tonearmBasePaint)
        canvas.drawCircle(counterX, counterY, 4.5f, centerSpindleClampPaint)

        // 5. Gimbal Pivot Base
        canvas.drawCircle(pivotX, pivotY, 15f, tonearmBasePaint)
        canvas.drawCircle(pivotX, pivotY, 9f, centerSpindleClampPaint)
        canvas.drawCircle(pivotX, pivotY, 4.5f, centerSpindleHolePaint)

        // 6. Slender Aluminum Arm Shaft
        val endX = (pivotX + cos(rad) * armLength).toFloat()
        val endY = (pivotY + sin(rad) * armLength).toFloat()
        canvas.drawLine(pivotX, pivotY, endX, endY, tonearmShaftPaint)

        // 7. Cartridge Headshell with Stylus Needle Jewel
        canvas.save()
        canvas.translate(endX, endY)
        canvas.rotate(currentAngle + 16f)
        tonearmHeadRect.set(-4f, -5f, 16f, 5f)
        canvas.drawRoundRect(tonearmHeadRect, 2.5f, 2.5f, tonearmHeadPaint)
        // Finger lift lever
        canvas.drawLine(10f, 5f, 15f, 9f, tonearmShaftPaint)
        // Stylus needle jewel
        canvas.drawCircle(12f, 0f, 2.4f, tonearmNeedlePaint)
        canvas.restore()

        // 8. Subtle Groove Micro-Glow at Stylus Needle Contact Point when on vinyl
        if (cueProgress > 0.1f && !isEink) {
            val headRad = Math.toRadians((currentAngle + 16f).toDouble())
            val needleX = (endX + cos(headRad) * 12f).toFloat()
            val needleY = (endY + sin(headRad) * 12f).toFloat()
            val glowAlpha = (35 * cueProgress).toInt().coerceIn(0, 255)
            needleGlowPaint.color = Color.argb(glowAlpha, 255, 255, 255)
            canvas.drawCircle(needleX, needleY, 4.5f, needleGlowPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) {
            return true
        }
        return super.onTouchEvent(event)
    }
}
