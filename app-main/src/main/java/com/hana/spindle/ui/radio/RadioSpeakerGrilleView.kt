package com.hana.spindle.ui.radio

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hardware-accelerated custom view rendering a Bauhaus-inspired
 * concentric radial acoustic speaker perforation grille with an integrated
 * Acoustic Radial Wave Pulse visualizer (Voice Coil Illumination),
 * tactile top-left FM/Digital toggle switch, and dual hardware status LED diodes (ONLINE & FM).
 *
 * Implements zero heap allocations in onDraw() for silky 60fps performance on low-RAM DAPs.
 */
class RadioSpeakerGrilleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class RadioMode {
        FM,
        DIGITAL
    }

    var radioMode: RadioMode = RadioMode.FM
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var onModeChanged: ((RadioMode) -> Unit)? = null

    var isFmActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

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

    enum class NetworkState {
        OFFLINE,
        CONNECTING,
        ONLINE
    }

    // Internet and stream connectivity state
    var networkState: NetworkState = NetworkState.ONLINE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    // Internet connectivity state for backwards compatibility
    var isOnline: Boolean
        get() = networkState == NetworkState.ONLINE
        set(value) {
            networkState = if (value) NetworkState.ONLINE else NetworkState.OFFLINE
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

    // Resting acoustic mesh styling
    private val holeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#151724") // Deep acoustic hole interior
        style = Paint.Style.FILL
    }

    private val holeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#151724") // Matching outline around speaker holes
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
    }

    // Dynamic Voice Coil Visualizer Paints
    private val activeHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Driver cone rim outline around the speaker hole (same color outline to match speaker hole)
    private val driverRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#151724")
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
    }

    // Top-Left Dual Radio Rocker Switch Paints & Geometries
    private val rockerBezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
    }
    private val rockerWellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rockerActivePaddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rockerInactivePaddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rockerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val rockerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val rockerGripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
    }
    private val rockerPipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EF4444")
    }
    private val toggleTextActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = 15.0f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        letterSpacing = 0.02f
    }
    private val toggleTextInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = 15.0f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        letterSpacing = 0.02f
    }

    private val rockerHousingRect = RectF()
    private val topPaddleRect = RectF()
    private val bottomPaddleRect = RectF()

    // Hardware Status LED Paints (ONLINE & FM Diodes)
    private val ledBezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
    }
    private val ledWellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ledDiodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val fmDiodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ledPipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val ledLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = 19f
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
            val holeColor = Color.BLACK
            holeShadowPaint.color = holeColor
            holeHighlightPaint.color = holeColor
            driverRimPaint.color = holeColor
            ledBezelPaint.color = Color.BLACK
            ledWellPaint.color = Color.WHITE
            rockerBezelPaint.color = Color.BLACK
            rockerWellPaint.color = Color.WHITE
            rockerActivePaddlePaint.color = Color.BLACK
            rockerInactivePaddlePaint.color = Color.WHITE
            rockerHighlightPaint.color = Color.WHITE
            rockerShadowPaint.color = Color.BLACK
            rockerGripPaint.color = Color.GRAY
            rockerPipPaint.color = Color.WHITE
            toggleTextActivePaint.color = Color.WHITE
            toggleTextInactivePaint.color = Color.BLACK
        } else if (!isDarkMode) {
            val holeColor = Color.parseColor("#E5E5E2")
            holeShadowPaint.color = holeColor
            holeHighlightPaint.color = holeColor
            driverRimPaint.color = holeColor
            ledBezelPaint.color = Color.parseColor("#2A2E45")
            ledWellPaint.color = Color.parseColor("#EDEDE8")
            rockerBezelPaint.color = Color.parseColor("#CBD5E1")
            rockerWellPaint.color = Color.parseColor("#CBD5E1")
            rockerActivePaddlePaint.color = Color.parseColor("#FFFFFF")
            rockerInactivePaddlePaint.color = Color.parseColor("#E2E8F0")
            rockerHighlightPaint.color = Color.parseColor("#FFFFFF")
            rockerShadowPaint.color = Color.parseColor("#94A3B8")
            rockerGripPaint.color = Color.parseColor("#E2E8F0")
            rockerPipPaint.color = Color.parseColor("#EF4444")
            toggleTextActivePaint.color = Color.parseColor("#0F172A")
            toggleTextInactivePaint.color = Color.parseColor("#94A3B8")
        } else {
            val holeColor = Color.parseColor("#151724")
            holeShadowPaint.color = holeColor
            holeHighlightPaint.color = holeColor
            driverRimPaint.color = holeColor
            ledBezelPaint.color = Color.parseColor("#353A54")
            ledWellPaint.color = Color.parseColor("#151724")
            rockerBezelPaint.color = Color.parseColor("#353A54")
            rockerWellPaint.color = Color.parseColor("#0F111A")
            rockerActivePaddlePaint.color = Color.parseColor("#26293B")
            rockerInactivePaddlePaint.color = Color.parseColor("#13151F")
            rockerHighlightPaint.color = Color.parseColor("#4B5277")
            rockerShadowPaint.color = Color.parseColor("#090A0F")
            rockerGripPaint.color = Color.parseColor("#1C1E2B")
            rockerPipPaint.color = Color.parseColor("#EF4444")
            toggleTextActivePaint.color = Color.WHITE
            toggleTextInactivePaint.color = Color.parseColor("#646A88")
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

        val density = resources.displayMetrics.density
        val minDim = kotlin.math.min(w, h).toFloat()
        holeRadius = minDim * 0.017f

        // Top-left vintage dual rocker switch housing & paddles geometry (density-aware)
        // Authentic vertical physical rocker switch: FM on TOP, DIGI on BOTTOM
        val rockerLeft = 14f * density
        val rockerTop = 10f * density
        val rockerWidth = 42f * density
        val rockerHeight = 66f * density
        rockerHousingRect.set(rockerLeft, rockerTop, rockerLeft + rockerWidth, rockerTop + rockerHeight)

        val splitY = rockerTop + 33f * density
        topPaddleRect.set(rockerLeft + 2.2f * density, rockerTop + 2.2f * density, rockerLeft + rockerWidth - 2.2f * density, splitY - 1.2f * density)
        bottomPaddleRect.set(rockerLeft + 2.2f * density, splitY + 1.2f * density, rockerLeft + rockerWidth - 2.2f * density, rockerTop + rockerHeight - 2.2f * density)

        // Center hole (Ring 0)
        holeCenters.add(PointF(cx, cy))
        holeRings.add(0)

        // 7 Concentric Rings (Acoustic perforation proportion, safely uncropped)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (rockerHousingRect.contains(event.x, event.y)) {
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (rockerHousingRect.contains(event.x, event.y)) {
                    val splitY = rockerHousingRect.centerY()
                    val newMode = if (event.y < splitY) RadioMode.FM else RadioMode.DIGITAL
                    if (radioMode != newMode) {
                        radioMode = newMode
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onModeChanged?.invoke(radioMode)
                        invalidate()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
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

            // Outline matching speaker hole
            canvas.drawCircle(pt.x, pt.y, holeRadius, holeHighlightPaint)
        }

        // 4. Top-Left Vintage Physical Rocker Switch (FM on TOP, DIGI BELOW)
        val housingCorner = 4.0f
        val paddleCorner = 2.8f

        // Draw recessed rectangular housing well & outer metallic bezel
        canvas.drawRoundRect(rockerHousingRect, housingCorner, housingCorner, rockerWellPaint)
        canvas.drawRoundRect(rockerHousingRect, housingCorner, housingCorner, rockerBezelPaint)

        // Draw central horizontal fulcrum dividing slot line
        val fulcrumY = (topPaddleRect.bottom + bottomPaddleRect.top) * 0.5f
        canvas.drawLine(rockerHousingRect.left + 2f, fulcrumY, rockerHousingRect.right - 2f, fulcrumY, rockerShadowPaint)

        val isFmActive = (radioMode == RadioMode.FM)

        // Top Rocker Paddle (FM)
        val topPaint = if (isFmActive) rockerActivePaddlePaint else rockerInactivePaddlePaint
        canvas.drawRoundRect(topPaddleRect, paddleCorner, paddleCorner, topPaint)

        if (isFmActive) {
            // Raised top-edge specular highlight & bottom drop shadow
            canvas.drawLine(topPaddleRect.left + 2f, topPaddleRect.top + 1.2f, topPaddleRect.right - 2f, topPaddleRect.top + 1.2f, rockerHighlightPaint)
            canvas.drawLine(topPaddleRect.left + 2f, topPaddleRect.bottom - 1.2f, topPaddleRect.right - 2f, topPaddleRect.bottom - 1.2f, rockerShadowPaint)
            // Active tactile indicator pip (ruby diode pip at right side)
            canvas.drawCircle(topPaddleRect.right - 6f, topPaddleRect.centerY(), 2.4f, rockerPipPaint)
        } else {
            // Depressed top-edge cast shadow
            canvas.drawLine(topPaddleRect.left + 2f, topPaddleRect.top + 1.2f, topPaddleRect.right - 2f, topPaddleRect.top + 1.2f, rockerShadowPaint)
        }

        // Subtle tactile grip ribs on top paddle
        val topGripY1 = topPaddleRect.top + topPaddleRect.height() * 0.32f
        val topGripY2 = topPaddleRect.bottom - topPaddleRect.height() * 0.32f
        canvas.drawLine(topPaddleRect.left + 5f, topGripY1, topPaddleRect.right - 10f, topGripY1, rockerGripPaint)
        canvas.drawLine(topPaddleRect.left + 5f, topGripY2, topPaddleRect.right - 10f, topGripY2, rockerGripPaint)

        // Bottom Rocker Paddle (DIGI)
        val bottomPaint = if (!isFmActive) rockerActivePaddlePaint else rockerInactivePaddlePaint
        canvas.drawRoundRect(bottomPaddleRect, paddleCorner, paddleCorner, bottomPaint)

        if (!isFmActive) {
            // Raised bottom-edge specular highlight & top drop shadow
            canvas.drawLine(bottomPaddleRect.left + 2f, bottomPaddleRect.top + 1.2f, bottomPaddleRect.right - 2f, bottomPaddleRect.top + 1.2f, rockerHighlightPaint)
            canvas.drawLine(bottomPaddleRect.left + 2f, bottomPaddleRect.bottom - 1.2f, bottomPaddleRect.right - 2f, bottomPaddleRect.bottom - 1.2f, rockerShadowPaint)
            // Active tactile indicator pip (amber diode pip at right side)
            canvas.drawCircle(bottomPaddleRect.right - 6f, bottomPaddleRect.centerY(), 2.4f, rockerPipPaint)
        } else {
            // Depressed top-edge cast shadow
            canvas.drawLine(bottomPaddleRect.left + 2f, bottomPaddleRect.top + 1.2f, bottomPaddleRect.right - 2f, bottomPaddleRect.top + 1.2f, rockerShadowPaint)
        }

        // Subtle tactile grip ribs on bottom paddle
        val botGripY1 = bottomPaddleRect.top + bottomPaddleRect.height() * 0.32f
        val botGripY2 = bottomPaddleRect.bottom - bottomPaddleRect.height() * 0.32f
        canvas.drawLine(bottomPaddleRect.left + 5f, botGripY1, bottomPaddleRect.right - 10f, botGripY1, rockerGripPaint)
        canvas.drawLine(bottomPaddleRect.left + 5f, botGripY2, bottomPaddleRect.right - 10f, botGripY2, rockerGripPaint)

        // Monospaced Paddle Text Labels: "FM" on TOP, "DIGI" on BOTTOM
        val fmColor = if (isFmActive) (if (isEink) Color.BLACK else Color.parseColor("#F97316")) else (if (isEink) Color.GRAY else Color.parseColor("#646A88"))
        val digColor = if (!isFmActive) (if (isEink) Color.BLACK else Color.parseColor("#F59E0B")) else (if (isEink) Color.GRAY else Color.parseColor("#646A88"))
        toggleTextActivePaint.color = fmColor
        toggleTextInactivePaint.color = digColor

        val fmMetrics = toggleTextActivePaint.fontMetrics
        val fmTextY = topPaddleRect.centerY() - (fmMetrics.ascent + fmMetrics.descent) * 0.5f
        val fmTextX = topPaddleRect.centerX() - (if (isFmActive) 3f else 0f)

        val digMetrics = toggleTextInactivePaint.fontMetrics
        val digTextY = bottomPaddleRect.centerY() - (digMetrics.ascent + digMetrics.descent) * 0.5f
        val digTextX = bottomPaddleRect.centerX() - (if (!isFmActive) 3f else 0f)

        canvas.drawText("FM", fmTextX, fmTextY, toggleTextActivePaint)
        canvas.drawText("DIGI", digTextX, digTextY, toggleTextInactivePaint)

        // 5. Hardware Status LEDs (Top-Right of Speaker)
        val ledMargin = 26f
        val ledRadius = 7.5f
        val ledCenterX = width - ledMargin - 8f

        // Row 1: ONLINE Status LED
        val ledCenterY = 28f
        // Recessed well & bezel
        canvas.drawCircle(ledCenterX, ledCenterY, ledRadius + 3.8f, ledWellPaint)
        canvas.drawCircle(ledCenterX, ledCenterY, ledRadius + 3.8f, ledBezelPaint)

        // Diode color: offline red, connecting amber, online green
        if (isEink) {
            ledDiodePaint.color = when (networkState) {
                NetworkState.OFFLINE -> Color.WHITE
                NetworkState.CONNECTING -> Color.WHITE
                NetworkState.ONLINE -> Color.BLACK
            }
            ledLabelPaint.color = Color.BLACK
        } else {
            val diodeColor = when (networkState) {
                NetworkState.OFFLINE -> Color.parseColor("#EF4444")
                NetworkState.CONNECTING -> Color.parseColor("#F59E0B")
                NetworkState.ONLINE -> Color.parseColor("#00E676")
            }
            ledDiodePaint.color = diodeColor
            ledLabelPaint.color = if (!isDarkMode) Color.parseColor("#5A5E78") else Color.parseColor("#B0B4CE")
        }
        canvas.drawCircle(ledCenterX, ledCenterY, ledRadius, ledDiodePaint)

        // Glass highlight reflection pip
        if (!isEink) {
            canvas.drawCircle(ledCenterX - 2.0f, ledCenterY - 2.0f, 1.8f, ledPipPaint)
        }

        // Status text label next to ONLINE LED
        val onlineLabelText = when (networkState) {
            NetworkState.OFFLINE -> "OFFLINE"
            NetworkState.CONNECTING -> "CONNECTING"
            NetworkState.ONLINE -> "ONLINE"
        }
        canvas.drawText(onlineLabelText, ledCenterX - (ledRadius + 12f), ledCenterY + 5.5f, ledLabelPaint)

        // Row 2: FM Diode Status Indicator (Below ONLINE LED Diode)
        val fmCenterY = ledCenterY + 25f
        // Recessed well & bezel
        canvas.drawCircle(ledCenterX, fmCenterY, ledRadius + 3.8f, ledWellPaint)
        canvas.drawCircle(ledCenterX, fmCenterY, ledRadius + 3.8f, ledBezelPaint)

        // FM Diode Color: Lit glowing Amber (#F59E0B) or Green when active, muted dark when stopped
        if (isEink) {
            fmDiodePaint.color = if (isFmActive) Color.BLACK else Color.WHITE
        } else {
            val fmDiodeColor = if (isFmActive) {
                Color.parseColor("#F59E0B") // Warm Lit Amber
            } else {
                if (isDarkMode) Color.parseColor("#22160C") else Color.parseColor("#D4D4D0") // Muted unlit well
            }
            fmDiodePaint.color = fmDiodeColor
        }
        canvas.drawCircle(ledCenterX, fmCenterY, ledRadius, fmDiodePaint)

        // Glass highlight reflection pip for FM diode
        if (!isEink) {
            canvas.drawCircle(ledCenterX - 2.0f, fmCenterY - 2.0f, 1.8f, ledPipPaint)
        }

        // FM text label next to FM Diode
        val fmLabelText = if (radioMode == RadioMode.FM) "FM" else "DIG"
        canvas.drawText(fmLabelText, ledCenterX - (ledRadius + 12f), fmCenterY + 5.5f, ledLabelPaint)
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
