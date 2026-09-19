package com.hana.spindle.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Audiophile "Vernier Wave" Kinetic Alphabet Quick-Scroll Rail.
 *
 * Inspired by Niagara Launcher and Braun/Sony audio vernier tuning scales.
 * Features:
 * - Fluid parabolic wave deflection that bows outward towards the thumb on drag.
 * - Progressive typographic magnification from 10sp up to 20sp.
 * - Machined tactile capsule pill indicator at the wave crest.
 * - Mechanical stepped clock-tick haptics on letter boundary crossing.
 * - Zero object allocation in onDraw() for 60fps responsiveness on compact low-RAM DAPs.
 */
class AlphabetIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        val ALPHABET = arrayOf(
            "#", "A", "B", "C", "D", "E", "F", "G", "H", "I",
            "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S",
            "T", "U", "V", "W", "X", "Y", "Z"
        )
    }

    var onLetterSelected: ((String) -> Unit)? = null
    var onTouchPositionChanged: ((letter: String, touchY: Float) -> Unit)? = null
    var onTouchActiveChanged: ((Boolean) -> Unit)? = null

    private var selectedIndex = -1
    private var currentTouchY = 0f
    private var waveProgress = 0f
    private var waveAnimator: ValueAnimator? = null

    private val density get() = resources.displayMetrics.density

    // Geometry constants
    private val baseMarginEnd get() = 12f * density
    private val maxWaveDeflection get() = 52f * density
    private val waveRadius get() = 120f * density

    private val baseTextSize get() = 9.5f * density
    private val maxTextSize get() = 20f * density

    // Paints
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71717A")
        textSize = baseTextSize
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val activePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F97316")
        style = Paint.Style.FILL
    }

    private val activePillGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 249, 115, 22)
        style = Paint.Style.FILL
    }

    private val spinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 255, 255, 255)
        strokeWidth = 1.2f * density
        style = Paint.Style.STROKE
    }

    private val spinePath = Path()
    private val pillRect = RectF()

    init {
        isClickable = true
    }

    fun updateTheme(textColor: Int, accentColor: Int) {
        textPaint.color = textColor
        activePillPaint.color = accentColor
        activePillGlowPaint.color = Color.argb(
            60,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (h <= 0 || w <= 0) return

        val itemHeight = h / ALPHABET.size
        val baseX = w - baseMarginEnd

        spinePath.reset()
        var hasSpineStarted = false

        // Draw connecting spine when wave is active
        if (waveProgress > 0.05f) {
            for (i in ALPHABET.indices) {
                val cy = i * itemHeight + itemHeight * 0.5f
                val dist = abs(cy - currentTouchY)
                val curve = if (dist < waveRadius) {
                    val d = 1f - (dist / waveRadius)
                    ((1f - cos(d * PI.toFloat())) / 2f) * waveProgress
                } else 0f

                val x = baseX - (maxWaveDeflection * curve)
                if (!hasSpineStarted) {
                    spinePath.moveTo(x, cy)
                    hasSpineStarted = true
                } else {
                    spinePath.lineTo(x, cy)
                }
            }
            canvas.drawPath(spinePath, spinePaint)
        }

        // Draw alphabet letters along the kinetic wave
        for (i in ALPHABET.indices) {
            val letter = ALPHABET[i]
            val cy = i * itemHeight + itemHeight * 0.5f

            val dist = abs(cy - currentTouchY)
            val curve = if (waveProgress > 0f && dist < waveRadius) {
                val d = 1f - (dist / waveRadius)
                ((1f - cos(d * PI.toFloat())) / 2f) * waveProgress
            } else 0f

            val x = baseX - (maxWaveDeflection * curve)
            val isSelected = (i == selectedIndex && waveProgress > 0.1f)

            if (isSelected) {
                // Machined callout pill capsule at the wave apex
                val pillW = 28f * density
                val pillH = 24f * density
                pillRect.set(x - pillW * 0.5f, cy - pillH * 0.5f, x + pillW * 0.5f, cy + pillH * 0.5f)

                // Outer halo / glow
                canvas.drawRoundRect(
                    pillRect.left - 3f * density,
                    pillRect.top - 3f * density,
                    pillRect.right + 3f * density,
                    pillRect.bottom + 3f * density,
                    14f * density,
                    14f * density,
                    activePillGlowPaint
                )

                // Solid capsule
                canvas.drawRoundRect(pillRect, 12f * density, 12f * density, activePillPaint)

                // Bold high-contrast white letter
                textPaint.textSize = maxTextSize
                textPaint.color = Color.WHITE
                textPaint.alpha = 255
            } else {
                // Smooth interpolation for neighboring letters
                textPaint.textSize = baseTextSize + (maxTextSize * 0.55f - baseTextSize) * curve
                val alphaFactor = 0.45f + 0.55f * curve
                textPaint.color = if (curve > 0.35f) Color.WHITE else Color.parseColor("#A1A1AA")
                textPaint.alpha = (255 * alphaFactor).toInt().coerceIn(70, 255)
            }

            val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(letter, x, baseline, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val h = height.toFloat()
        if (h <= 0) return super.onTouchEvent(event)

        val itemHeight = h / ALPHABET.size

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onTouchActiveChanged?.invoke(true)
                currentTouchY = event.y

                startWaveAnimation(targetProgress = 1f, durationMs = 140L)
                updateSelection(event.y, itemHeight)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentTouchY = event.y
                updateSelection(event.y, itemHeight)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onTouchActiveChanged?.invoke(false)

                startWaveAnimation(targetProgress = 0f, durationMs = 180L) {
                    selectedIndex = -1
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startWaveAnimation(targetProgress: Float, durationMs: Long, onEnd: (() -> Unit)? = null) {
        waveAnimator?.cancel()
        waveAnimator = ValueAnimator.ofFloat(waveProgress, targetProgress).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                waveProgress = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun updateSelection(y: Float, itemHeight: Float) {
        val newIndex = (y / itemHeight).toInt().coerceIn(0, ALPHABET.size - 1)
        val cy = newIndex * itemHeight + itemHeight * 0.5f
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onLetterSelected?.invoke(ALPHABET[newIndex])
            invalidate()
        }
        onTouchPositionChanged?.invoke(ALPHABET[newIndex], cy)
    }
}
