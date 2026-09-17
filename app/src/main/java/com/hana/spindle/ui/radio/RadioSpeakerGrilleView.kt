package com.hana.spindle.ui.radio

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hardware-accelerated custom view rendering a Braun / Dieter Rams-inspired
 * concentric radial acoustic speaker perforation grille with an integrated
 * Acoustic Radial Wave Pulse visualizer (Voice Coil Illumination).
 *
 * Implements zero heap allocations in onDraw() for silky 60fps performance on low-RAM DAPs.
 */
class RadioSpeakerGrilleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    startVisualizer()
                } else {
                    stopVisualizer()
                }
            }
        }

    // Real-time audio amplitude sync (0.0f to 1.0f)
    var liveAudioLevel: Float = 0.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    // Internet connectivity state
    var isOnline: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isDarkMode: Boolean = true
        set(value) {
            field = value
            updateThemePaints()
            invalidate()
        }

    var isEink: Boolean = false
        set(value) {
            field = value
            updateThemePaints()
            invalidate()
        }

    // Resting dark acoustic mesh styling
    private val holeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#151720") // Deep acoustic hole interior
        style = Paint.Style.FILL
    }

    private val holeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 255, 255, 255) // Metallic dark bevel highlight
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
    }

    // Dynamic Voice Coil Visualizer Paints
    private val activeHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val driverRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#161922")
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
    }

    // Top-Right Online/Offline Hardware Status LED Paints
    private val ledBezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val ledWellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ledDiodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ledPipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val ledLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = 22f
        textAlign = Paint.Align.RIGHT
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        letterSpacing = 0.08f
    }

    // Hole coordinates & Ring grouping
    private val holeCenters = ArrayList<PointF>(200)
    private val holeRings = ArrayList<Int>(200)
    private var holeRadius = 5.5f
    private var driverMaxRadius = 0f
    private var centerPtX = 0f
    private var centerPtY = 0f

    // Visualizer animation state
    private var visualizerAnimator: ValueAnimator? = null
    private var animPhase = 0f
    private var glowFactor = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updateThemePaints()
    }

    private fun updateThemePaints() {
        if (isEink) {
            holeShadowPaint.color = Color.BLACK
            holeHighlightPaint.color = Color.parseColor("#E5E5E5")
            driverRimPaint.color = Color.BLACK
            ledBezelPaint.color = Color.BLACK
            ledWellPaint.color = Color.WHITE
        } else if (!isDarkMode) {
            holeShadowPaint.color = Color.parseColor("#E5E5E2")
            holeHighlightPaint.color = Color.WHITE
            driverRimPaint.color = Color.parseColor("#2A2E45")
            ledBezelPaint.color = Color.parseColor("#2A2E45")
            ledWellPaint.color = Color.parseColor("#EDEDE8")
        } else {
            holeShadowPaint.color = Color.parseColor("#151724")
            holeHighlightPaint.color = Color.argb(40, 250, 250, 249)
            driverRimPaint.color = Color.parseColor("#1E2132")
            ledBezelPaint.color = Color.parseColor("#353A54")
            ledWellPaint.color = Color.parseColor("#151724")
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        holeCenters.clear()
        holeRings.clear()

        val cx = w * 0.5f
        val cy = h * 0.5f
        centerPtX = cx
        centerPtY = cy

        val minDim = kotlin.math.min(w, h).toFloat()
        holeRadius = minDim * 0.017f

        // Center hole (Ring 0)
        holeCenters.add(PointF(cx, cy))
        holeRings.add(0)

        // 7 Concentric Rings (Braun T3/TP1 proportion, safely uncropped)
        driverMaxRadius = minDim * 0.45f
        val ringCounts = intArrayOf(8, 14, 20, 26, 32, 38, 44)
        val ringStep = driverMaxRadius / ringCounts.size

        for (i in ringCounts.indices) {
            val count = ringCounts[i]
            val ringIndex = i + 1
            val r = ringStep * ringIndex
            val angleStep = (2.0 * Math.PI) / count
            for (j in 0 until count) {
                val angle = j * angleStep
                val x = (cx + r * cos(angle)).toFloat()
                val y = (cy + r * sin(angle)).toFloat()
                holeCenters.add(PointF(x, y))
                holeRings.add(ringIndex)
            }
        }
    }

    private fun startVisualizer() {
        if (visualizerAnimator?.isRunning == true) return
        visualizerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animPhase += 0.12f
                if (animPhase > 1000f) animPhase = 0f
                if (glowFactor < 1.0f) {
                    glowFactor = (glowFactor + 0.12f).coerceAtMost(1.0f)
                }
                invalidate()
            }
            start()
        }
    }

    private fun stopVisualizer() {
        visualizerAnimator?.cancel()
        visualizerAnimator = null
        glowFactor = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (holeCenters.isEmpty()) return

        // 1. Subtle acoustic speaker driver cone rim behind perforation
        if (driverMaxRadius > 0f) {
            canvas.drawCircle(centerPtX, centerPtY, driverMaxRadius * 1.04f, driverRimPaint)
        }

        val isVisualizing = (isPlaying || liveAudioLevel > 0.01f) && (glowFactor > 0.01f || liveAudioLevel > 0.01f)

        // 2. Pre-calculate ring intensities for the 8 rings (0 to 7) to avoid re-calculation per hole
        var r0 = 0f; var r1 = 0f; var r2 = 0f; var r3 = 0f
        var r4 = 0f; var r5 = 0f; var r6 = 0f; var r7 = 0f

        if (isVisualizing) {
            val audioBoost = if (liveAudioLevel > 0.01f) liveAudioLevel else 0.5f
            // Core beat / voice coil bass pulse
            val beat = (sin(animPhase * 3.0f) * 0.5f + 0.5f) * audioBoost
            // Ripple wave propagation outward
            fun calcRing(ringIdx: Int): Float {
                val bass = (1.0f - ringIdx * 0.11f).coerceAtLeast(0f) * beat
                val ripple = (sin(animPhase * 4.2f - ringIdx * 0.72f) * 0.5f + 0.5f) * audioBoost
                val shimmer = (sin(animPhase * 6.5f + ringIdx * 1.1f) * 0.5f + 0.5f) * (ringIdx / 7f) * 0.35f
                val baseLevel = (bass * 0.55f + ripple * 0.45f + shimmer).coerceIn(0f, 1f)
                val effectiveFactor = if (liveAudioLevel > 0.01f) (glowFactor * 0.4f + liveAudioLevel * 0.6f) else glowFactor
                return baseLevel * effectiveFactor
            }
            r0 = calcRing(0)
            r1 = calcRing(1)
            r2 = calcRing(2)
            r3 = calcRing(3)
            r4 = calcRing(4)
            r5 = calcRing(5)
            r6 = calcRing(6)
            r7 = calcRing(7)
        }

        // 3. Draw Perforation Holes with Internal Acoustic Illumination
        for (i in holeCenters.indices) {
            val pt = holeCenters[i]
            val ring = holeRings[i]

            // Draw base dark recessed hole interior
            canvas.drawCircle(pt.x, pt.y, holeRadius, holeShadowPaint)

            if (isVisualizing) {
                val intensity = when (ring) {
                    0 -> r0
                    1 -> r1
                    2 -> r2
                    3 -> r3
                    4 -> r4
                    5 -> r5
                    6 -> r6
                    else -> r7
                }

                if (intensity > 0.06f) {
                    if (isEink) {
                        activeHolePaint.color = Color.BLACK
                    } else {
                        // Warm filament tube orange/amber to radiant gold illumination
                        val r = 249
                        val g = (115 + (130 * intensity)).toInt().coerceIn(0, 255)
                        val b = (22 + (110 * intensity)).toInt().coerceIn(0, 255)
                        val a = (50 + (205 * intensity)).toInt().coerceIn(0, 255)
                        activeHolePaint.color = Color.argb(a, r, g, b)
                    }

                    // Draw glowing internal core inside the hole
                    val glowR = holeRadius * (0.35f + 0.65f * intensity)
                    canvas.drawCircle(pt.x, pt.y, glowR, activeHolePaint)
                }
            }

            // Neumorphic bottom-right highlight bevel
            canvas.drawCircle(pt.x, pt.y + 0.8f, holeRadius, holeHighlightPaint)
        }

        // 4. Hardware Network Status LED (Top-Right of Speaker)
        val ledMargin = 28f
        val ledCenterY = ledMargin + 8f
        val ledCenterX = width - ledMargin - 8f
        val ledRadius = 5.0f

        // Recessed well
        canvas.drawCircle(ledCenterX, ledCenterY, ledRadius + 4f, ledWellPaint)
        canvas.drawCircle(ledCenterX, ledCenterY, ledRadius + 4f, ledBezelPaint)

        // Diode color
        if (isEink) {
            ledDiodePaint.color = if (isOnline) Color.BLACK else Color.WHITE
            ledLabelPaint.color = Color.BLACK
        } else {
            val diodeColor = if (isOnline) Color.parseColor("#00E676") else Color.parseColor("#FB7185")
            ledDiodePaint.color = diodeColor
            ledLabelPaint.color = if (!isDarkMode) Color.parseColor("#5A5E78") else Color.parseColor("#B0B4CE")
        }
        canvas.drawCircle(ledCenterX, ledCenterY, ledRadius, ledDiodePaint)

        // Glass highlight reflection pip
        if (!isEink) {
            canvas.drawCircle(ledCenterX - 1.5f, ledCenterY - 1.5f, 1.3f, ledPipPaint)
        }

        // Tiny status text label next to LED
        val labelText = if (isOnline) "ONLINE" else "OFFLINE"
        canvas.drawText(labelText, ledCenterX - 16f, ledCenterY + 4f, ledLabelPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying) {
            startVisualizer()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopVisualizer()
    }
}
