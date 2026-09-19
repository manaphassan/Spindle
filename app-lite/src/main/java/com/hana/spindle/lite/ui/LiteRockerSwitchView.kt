package com.hana.spindle.lite.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.hana.spindle.lite.R

/**
 * Authentic 2-position Vintage Hi-Fi Audiophile Rocker Switch.
 * Features 3D chamfered paddle geometry, physical pivot seam, tactile grip ribs,
 * illuminated indicator pips, and silk-screened "FM" (Top) and "DIGI" (Bottom) labels.
 * Strict zero heap allocations in onDraw().
 */
class LiteRockerSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onModeChanged: ((isFm: Boolean) -> Unit)? = null

    /**
     * True = FM mode (rocker tilted UP), False = DIGI mode (rocker tilted DOWN).
     */
    var isFmMode: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                animateTilt(if (value) 1.0f else -1.0f)
            }
        }

    fun setModeWithoutAnimation(isFm: Boolean) {
        isFmMode = isFm
        tiltProgress = if (isFm) 1.0f else -1.0f
        updateShaders()
        invalidate()
    }

    // -1.0f (Full DIGI / DOWN) to +1.0f (Full FM / UP)
    private var tiltProgress: Float = 1.0f
    private var tiltAnimator: ValueAnimator? = null

    // --- Pre-allocated Geometry Objects ---
    private val wellRect = RectF()
    private val topPaddleRect = RectF()
    private val bottomPaddleRect = RectF()
    private val indicatorRect = RectF()

    // --- Pre-allocated Paint Objects ---
    private val wellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val paddleTopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paddleBottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paddleBezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val pivotLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val gripRibPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Cached Palette Colors
    private var colorOrange = 0
    private var colorAmber = 0
    private var colorMuted = 0
    private var colorSurfaceElevated = 0
    private var colorInner = 0
    private var colorBorder = 0
    private var colorChassis = 0
    private var colorHighlight = 0

    init {
        colorOrange = ContextCompat.getColor(context, R.color.brand_orange)
        colorAmber = ContextCompat.getColor(context, R.color.lite_amber_glow)
        colorMuted = ContextCompat.getColor(context, R.color.lite_text_muted)
        colorSurfaceElevated = ContextCompat.getColor(context, R.color.lite_surface_elevated)
        colorInner = ContextCompat.getColor(context, R.color.lite_deck_inner)
        colorBorder = ContextCompat.getColor(context, R.color.lite_border)
        colorChassis = ContextCompat.getColor(context, R.color.lite_bg_chassis)
        colorHighlight = Color.argb(60, 255, 255, 255)

        wellBgPaint.color = colorInner
        wellBorderPaint.color = colorBorder
        pivotLinePaint.color = colorInner

        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        labelPaint.textSize = (h * 0.15f).coerceIn(10f, 15f)

        val labelMargin = labelPaint.textSize + 4f
        val horizontalPadding = (w * 0.12f).coerceAtLeast(3f)

        // Well occupies the middle region between top and bottom labels
        val wellTop = labelMargin + 3f
        val wellBottom = h - labelMargin - 3f
        wellRect.set(horizontalPadding, wellTop, w - horizontalPadding, wellBottom)

        updatePaddleGeometry()
        updateShaders()
    }

    private fun updatePaddleGeometry() {
        val paddleMargin = 2.5f
        val pLeft = wellRect.left + paddleMargin
        val pRight = wellRect.right - paddleMargin
        val pTop = wellRect.top + paddleMargin
        val pBottom = wellRect.bottom - paddleMargin
        val pCenterY = (pTop + pBottom) / 2f

        topPaddleRect.set(pLeft, pTop, pRight, pCenterY)
        bottomPaddleRect.set(pLeft, pCenterY, pRight, pBottom)
    }

    private fun updateShaders() {
        if (topPaddleRect.isEmpty || bottomPaddleRect.isEmpty) return

        // Top Paddle Shading: bright when tilted UP (+1f), shaded dark when tilted DOWN (-1f)
        val topIntensity = ((tiltProgress + 1f) / 2f).coerceIn(0f, 1f)
        val topC1 = blendColors(colorInner, colorSurfaceElevated, topIntensity)
        val topC2 = blendColors(Color.BLACK, colorChassis, topIntensity)
        paddleTopPaint.shader = LinearGradient(
            0f, topPaddleRect.top, 0f, topPaddleRect.bottom,
            topC1, topC2, Shader.TileMode.CLAMP
        )

        // Bottom Paddle Shading: bright when tilted DOWN (-1f), shaded dark when tilted UP (+1f)
        val bottomIntensity = ((1f - tiltProgress) / 2f).coerceIn(0f, 1f)
        val btmC1 = blendColors(Color.BLACK, colorChassis, bottomIntensity)
        val btmC2 = blendColors(colorInner, colorSurfaceElevated, bottomIntensity)
        paddleBottomPaint.shader = LinearGradient(
            0f, bottomPaddleRect.top, 0f, bottomPaddleRect.bottom,
            btmC1, btmC2, Shader.TileMode.CLAMP
        )

        paddleBezelPaint.color = blendColors(colorBorder, colorHighlight, 0.4f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f

        // 1. Top Silk-screened Label: "FM"
        val isFmActive = tiltProgress > 0f
        labelPaint.color = if (isFmActive) colorOrange else colorMuted
        labelPaint.alpha = if (isFmActive) 255 else 140
        canvas.drawText("FM", cx, labelPaint.textSize + 1f, labelPaint)

        // 2. Recessed Switch Well (Chassis Cavity)
        val wellCorner = 4f
        canvas.drawRoundRect(wellRect, wellCorner, wellCorner, wellBgPaint)
        canvas.drawRoundRect(wellRect, wellCorner, wellCorner, wellBorderPaint)

        // 3. Rocker Paddle (Upper Half & Lower Half)
        val paddleCorner = 2.5f

        // Draw Top Half
        canvas.drawRoundRect(topPaddleRect, paddleCorner, paddleCorner, paddleTopPaint)
        canvas.drawRoundRect(topPaddleRect, paddleCorner, paddleCorner, paddleBezelPaint)

        // Draw Bottom Half
        canvas.drawRoundRect(bottomPaddleRect, paddleCorner, paddleCorner, paddleBottomPaint)
        canvas.drawRoundRect(bottomPaddleRect, paddleCorner, paddleCorner, paddleBezelPaint)

        // 4. Center Physical Pivot Seam
        val centerY = (wellRect.top + wellRect.bottom) / 2f
        canvas.drawLine(wellRect.left + 1f, centerY, wellRect.right - 1f, centerY, pivotLinePaint)

        // 5. Tactile Ribbed Grip Grooves & Active Indicator Dot
        val paddleWidth = topPaddleRect.width()
        val gripMargin = paddleWidth * 0.25f

        if (isFmActive) {
            // FM is actively pressed: draw active orange pip on top half and grip ribs
            indicatorPaint.color = colorOrange
            val pipY = topPaddleRect.top + (topPaddleRect.height() * 0.35f)
            val pipRadius = (paddleWidth * 0.08f).coerceIn(2f, 3.5f)
            canvas.drawCircle(cx, pipY, pipRadius, indicatorPaint)

            // Horizontal ribbed grip line below pip
            gripRibPaint.color = colorHighlight
            val ribY = topPaddleRect.bottom - (topPaddleRect.height() * 0.28f)
            canvas.drawLine(cx - gripMargin, ribY, cx + gripMargin, ribY, gripRibPaint)
        } else {
            // DIGI is actively pressed: draw active amber pip on bottom half and grip ribs
            indicatorPaint.color = colorAmber
            val pipY = bottomPaddleRect.bottom - (bottomPaddleRect.height() * 0.35f)
            val pipRadius = (paddleWidth * 0.08f).coerceIn(2f, 3.5f)
            canvas.drawCircle(cx, pipY, pipRadius, indicatorPaint)

            // Horizontal ribbed grip line above pip
            gripRibPaint.color = colorHighlight
            val ribY = bottomPaddleRect.top + (bottomPaddleRect.height() * 0.28f)
            canvas.drawLine(cx - gripMargin, ribY, cx + gripMargin, ribY, gripRibPaint)
        }

        // 6. Bottom Silk-screened Label: "DIGI"
        val isDigiActive = tiltProgress < 0f
        labelPaint.color = if (isDigiActive) colorAmber else colorMuted
        labelPaint.alpha = if (isDigiActive) 255 else 140
        canvas.drawText("DIGI", cx, h - 3f, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val y = event.y
                val centerY = height / 2f

                val newMode = if (y < centerY) {
                    true // Clicked upper half -> FM
                } else {
                    false // Clicked lower half -> DIGI
                }

                // If user tapped directly in the same zone, toggle to alternate
                val finalMode = if (newMode == isFmMode) !isFmMode else newMode
                toggleMode(finalMode)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun toggleMode(fm: Boolean) {
        if (isFmMode != fm) {
            isFmMode = fm
            animateTilt(if (fm) 1.0f else -1.0f)
            onModeChanged?.invoke(fm)
        }
    }

    private fun animateTilt(target: Float) {
        tiltAnimator?.cancel()
        tiltAnimator = ValueAnimator.ofFloat(tiltProgress, target).apply {
            duration = 140L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                tiltProgress = anim.animatedValue as Float
                updateShaders()
                invalidate()
            }
            start()
        }
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val ir = 1f - r
        val a = (Color.alpha(c1) * ir + Color.alpha(c2) * r).toInt()
        val red = (Color.red(c1) * ir + Color.red(c2) * r).toInt()
        val g = (Color.green(c1) * ir + Color.green(c2) * r).toInt()
        val b = (Color.blue(c1) * ir + Color.blue(c2) * r).toInt()
        return Color.argb(a, red, g, b)
    }
}
