package com.hana.spindle.ui.catalog

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * Custom circular album artwork view with a surrounding radial progress arc,
 * speaker volume icons, and interactive draggable thumb knob.
 * Designed exactly according to the reference image specification.
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

    var progress: Float = 0.0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (abs(field - clamped) > 0.001f) {
                field = clamped
                invalidate()
            }
        }

    var onSeek: ((Float) -> Unit)? = null
    var onSeekStarted: (() -> Unit)? = null
    var onSeekStopped: (() -> Unit)? = null

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

    var accentColor: Int = Color.parseColor("#D71920") // Walkman Crimson default
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    // Arc geometry: starts at 135 deg (bottom-left) and sweeps 270 deg (bottom-right)
    private val startAngle = 135f
    private val sweepAngle = 270f

    private val arcTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val arcProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private val coverShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val speakerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arcBounds = RectF()
    private val coverBounds = RectF()
    private val coverClipPath = Path()
    private var isDraggingThumb = false

    private var arcRadius = 0f
    private var coverRadius = 0f
    private var centerX = 0f
    private var centerY = 0f

    init {
        updatePaints()
    }

    private fun updatePaints() {
        val r = Color.red(accentColor)
        val g = Color.green(accentColor)
        val b = Color.blue(accentColor)

        if (isEink) {
            arcTrackPaint.color = Color.parseColor("#CCCCCC")
            speakerPaint.color = Color.BLACK
            thumbPaint.color = Color.BLACK
            thumbShadowPaint.color = Color.TRANSPARENT
            thumbInnerPaint.color = Color.WHITE
            coverShadowPaint.color = Color.TRANSPARENT
            arcProgressPaint.shader = null
            arcProgressPaint.color = Color.BLACK
        } else if (isDarkMode) {
            arcTrackPaint.color = Color.parseColor("#1E2132")
            speakerPaint.color = Color.parseColor("#B0B4CE")
            thumbPaint.color = Color.parseColor("#FDE68A") // Butter Yellow thumb bead
            thumbShadowPaint.color = Color.argb(90, 253, 230, 138)
            thumbInnerPaint.color = Color.parseColor("#2A2E45")
            coverShadowPaint.color = Color.parseColor("#80151724")
            arcProgressPaint.shader = null
            arcProgressPaint.color = accentColor // Brand Warm Orange
        } else {
            // Light Theme
            arcTrackPaint.color = Color.parseColor("#E5E5E2")
            speakerPaint.color = Color.parseColor("#5A5E78")
            thumbPaint.color = Color.parseColor("#F97316")
            thumbShadowPaint.color = Color.argb(60, 249, 115, 22)
            thumbInnerPaint.color = Color.WHITE
            coverShadowPaint.color = Color.argb(30, 42, 46, 69)
            arcProgressPaint.shader = null
            arcProgressPaint.color = accentColor // Brand Warm Orange
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        centerX = w * 0.5f
        centerY = h * 0.5f

        val minDim = min(w, h).toFloat()
        arcRadius = minDim * 0.44f
        coverRadius = minDim * 0.35f

        val strokeWidth = minDim * 0.016f
        arcTrackPaint.strokeWidth = strokeWidth
        arcProgressPaint.strokeWidth = strokeWidth * 1.15f

        arcBounds.set(
            centerX - arcRadius,
            centerY - arcRadius,
            centerX + arcRadius,
            centerY + arcRadius
        )

        coverBounds.set(
            centerX - coverRadius,
            centerY - coverRadius,
            centerX + coverRadius,
            centerY + coverRadius
        )

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
            coverPaint.color = Color.parseColor("#262933")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (centerX == 0f || centerY == 0f) return

        // 1. Soft Ambient Shadow under circular album art
        canvas.drawCircle(centerX, centerY + 8f, coverRadius + 4f, coverShadowPaint)

        // 2. Circular Album Art
        canvas.save()
        canvas.clipPath(coverClipPath)
        if (coverPaint.shader != null) {
            canvas.drawPaint(coverPaint)
        } else {
            canvas.drawCircle(centerX, centerY, coverRadius, coverPaint)
            // Default placeholder music note glyph
            val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = coverRadius * 0.6f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("♪", centerX, centerY + glyphPaint.textSize * 0.35f, glyphPaint)
        }
        canvas.restore()

        // 3. Unfilled Background Arc Track
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, arcTrackPaint)

        // 4. Filled Radial Progress Arc
        val currentSweep = sweepAngle * progress.coerceIn(0f, 1f)
        if (currentSweep > 0f) {
            canvas.drawArc(arcBounds, startAngle, currentSweep, false, arcProgressPaint)
        }

        // 5. Draggable Radial Thumb Knob at Current Progress Angle
        val currentAngleRad = Math.toRadians((startAngle + currentSweep).toDouble())
        val thumbX = centerX + (arcRadius * cos(currentAngleRad)).toFloat()
        val thumbY = centerY + (arcRadius * sin(currentAngleRad)).toFloat()
        val thumbRadius = arcRadius * 0.085f

        canvas.drawCircle(thumbX, thumbY, thumbRadius * 1.5f, thumbShadowPaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius * 0.35f, thumbInnerPaint)

        // 6. Speaker Icons at Left and Right Ends of Arc
        drawSpeakerIcons(canvas)
    }

    private fun drawSpeakerIcons(canvas: Canvas) {
        val iconDist = arcRadius + 18f
        val iconSize = 18f

        // Left: Mute / Quiet Speaker (at 135 degrees)
        val leftAngleRad = Math.toRadians(startAngle.toDouble() - 14.0)
        val leftX = centerX + (iconDist * cos(leftAngleRad)).toFloat()
        val leftY = centerY + (iconDist * sin(leftAngleRad)).toFloat()

        drawSpeakerGlyph(canvas, leftX, leftY, iconSize, isMuted = true)

        // Right: Loud Speaker (at 405 degrees)
        val rightAngleRad = Math.toRadians((startAngle + sweepAngle).toDouble() + 14.0)
        val rightX = centerX + (iconDist * cos(rightAngleRad)).toFloat()
        val rightY = centerY + (iconDist * sin(rightAngleRad)).toFloat()

        drawSpeakerGlyph(canvas, rightX, rightY, iconSize, isMuted = false)
    }

    private fun drawSpeakerGlyph(canvas: Canvas, cx: Float, cy: Float, size: Float, isMuted: Boolean) {
        val path = Path()
        val half = size * 0.5f

        // Speaker cone
        path.moveTo(cx - half * 0.6f, cy - half * 0.4f)
        path.lineTo(cx - half * 0.1f, cy - half * 0.4f)
        path.lineTo(cx + half * 0.4f, cy - half * 0.8f)
        path.lineTo(cx + half * 0.4f, cy + half * 0.8f)
        path.lineTo(cx - half * 0.1f, cy + half * 0.4f)
        path.lineTo(cx - half * 0.6f, cy + half * 0.4f)
        path.close()
        canvas.drawPath(path, speakerPaint)

        if (!isMuted) {
            // Sound waves
            val wavePaint = Paint(speakerPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                strokeCap = Paint.Cap.ROUND
            }
            val waveRect = RectF(cx, cy - half * 0.6f, cx + half * 0.9f, cy + half * 0.6f)
            canvas.drawArc(waveRect, -45f, 90f, false, wavePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val dist = hypot(dx, dy)
                // Check if touch is near arc track
                if (abs(dist - arcRadius) <= arcRadius * 0.35f) {
                    isDraggingThumb = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onSeekStarted?.invoke()
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    updateProgressFromTouch(dx, dy)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingThumb) {
                    val dx = event.x - centerX
                    val dy = event.y - centerY
                    updateProgressFromTouch(dx, dy)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingThumb) {
                    isDraggingThumb = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onSeekStopped?.invoke()
                    onSeek?.invoke(progress)
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgressFromTouch(dx: Float, dy: Float) {
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f

        // Normalize angle relative to startAngle (135 deg)
        var diff = angle - startAngle
        if (diff < 0) diff += 360f

        if (diff <= sweepAngle + 30f) {
            val newProgress = (diff / sweepAngle).coerceIn(0f, 1f)
            progress = newProgress
            onSeek?.invoke(newProgress)
            invalidate()
        }
    }
}
