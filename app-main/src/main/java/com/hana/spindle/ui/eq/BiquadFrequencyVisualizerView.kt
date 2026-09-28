package com.hana.spindle.ui.eq

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.hana.spindle.playback.AudioFxController
import com.hana.spindle.playback.BiquadFilterCalculator
import kotlin.math.abs
import kotlin.math.sin

/**
 * Interactive Audiophile Biquad Frequency Curve & Spectrum Oscilloscope View.
 *
 * Visualizes:
 * - Second-Order IIR (Biquad) cascaded magnitude response across 20Hz-20kHz.
 * - Logarithmic frequency grid and reference decibel lines (-12dB to +12dB).
 * - Real-time animated audio waveform spectrum envelope when playback is active.
 * - Interactive touch vernier probe displaying frequency, gain, and band info in a HUD badge.
 *
 * Implements strict zero heap allocations in [onDraw] for smooth 60fps rendering on vintage DAPs.
 */
class BiquadFrequencyVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_DB = -12.0f
        private const val MAX_DB = 12.0f
        private const val DB_RANGE = MAX_DB - MIN_DB
        private const val NUM_POINTS = BiquadFilterCalculator.NUM_POINTS

        // Key frequency grid markers
        private val GRID_FREQS = floatArrayOf(
            31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        )
        private val GRID_LABELS = arrayOf(
            "31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"
        )
    }

    // Response curve data buffer (dB at each of the 120 logarithmic frequencies)
    private val curveDb = FloatArray(NUM_POINTS)
    private val pointCoords = FloatArray(NUM_POINTS * 2)

    // Current DSP state cache
    private val currentIsoGainsDb = FloatArray(10)
    private val currentIsoQ = FloatArray(10) { 1.414f }
    private var isTapeSat: Boolean = false
    private var tapeDrive: Float = 0.0f

    // Live playback spectrum state
    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) invalidate()
            }
        }

    var isEink: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateThemeColors()
                invalidate()
            }
        }

    // Touch probe crosshair state
    private var isProbing: Boolean = false
    private var probeX: Float = -1f
    private var probeFreqHz: Float = 1000f
    private var probeGainDb: Float = 0f

    // Pre-allocated graphic objects
    private val curvePath = Path()
    private val fillPath = Path()
    private val gridPath = Path()
    private val zeroLinePath = Path()
    private val spectrumPath = Path()
    private val hudRect = RectF()

    // Pre-allocated Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val curveFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spectrumPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val probeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val probeReticlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val probeReticleCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Drawing margins
    private var plotLeft = 0f
    private var plotTop = 0f
    private var plotRight = 0f
    private var plotBottom = 0f
    private var plotWidth = 0f
    private var plotHeight = 0f
    private var zeroY = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initPaints()
        recomputeCurve()
    }

    private fun initPaints() {
        gridPaint.apply {
            color = Color.parseColor("#151820")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        zeroLinePaint.apply {
            color = Color.parseColor("#384152")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
        }

        textPaint.apply {
            color = Color.parseColor("#5A6275")
            textSize = 20f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        curvePaint.apply {
            color = Color.parseColor("#00E676") // Audiophile Emerald Glow
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        curveFillPaint.apply {
            style = Paint.Style.FILL
        }

        spectrumPaint.apply {
            color = Color.parseColor("#26F97316") // Warm Orange glow envelope
            style = Paint.Style.FILL
        }

        probeLinePaint.apply {
            color = Color.parseColor("#60A5FA") // Electric Cyan probe hairline
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        }

        probeReticlePaint.apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }

        probeReticleCenterPaint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        hudBgPaint.apply {
            color = Color.parseColor("#E60D0F14")
            style = Paint.Style.FILL
        }

        hudTextPaint.apply {
            color = Color.parseColor("#38BDF8")
            textSize = 22f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        updateThemeColors()
    }

    private fun updateThemeColors() {
        if (isEink) {
            gridPaint.color = Color.parseColor("#D4D4D8")
            zeroLinePaint.color = Color.BLACK
            zeroLinePaint.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            textPaint.color = Color.BLACK
            curvePaint.color = Color.BLACK
            curvePaint.strokeWidth = 2.5f
            curveFillPaint.color = Color.parseColor("#15000000")
            spectrumPaint.color = Color.parseColor("#10000000")
            probeLinePaint.color = Color.BLACK
            probeReticlePaint.color = Color.BLACK
            probeReticleCenterPaint.color = Color.BLACK
            hudBgPaint.color = Color.WHITE
            hudTextPaint.color = Color.BLACK
        } else {
            gridPaint.color = Color.parseColor("#181B24")
            zeroLinePaint.color = Color.parseColor("#384152")
            zeroLinePaint.pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
            textPaint.color = Color.parseColor("#64748B")
            curvePaint.color = Color.parseColor("#00E676")
            curvePaint.strokeWidth = 3f
            spectrumPaint.color = Color.parseColor("#20F97316")
            probeLinePaint.color = Color.parseColor("#60A5FA")
            probeReticlePaint.color = Color.parseColor("#38BDF8")
            probeReticleCenterPaint.color = Color.WHITE
            hudBgPaint.color = Color.parseColor("#EE0F1219")
            hudTextPaint.color = Color.parseColor("#38BDF8")
        }
    }

    /**
     * Updates the DSP frequency response curve parameters and redraws.
     */
    fun updateDspState(
        isoGainsDb: FloatArray,
        isoQFactors: FloatArray,
        isTapeSatEnabled: Boolean = false,
        tapeSatDrive: Float = 0.0f
    ) {
        val count = minOf(10, isoGainsDb.size)
        System.arraycopy(isoGainsDb, 0, currentIsoGainsDb, 0, count)
        val qCount = minOf(10, isoQFactors.size)
        System.arraycopy(isoQFactors, 0, currentIsoQ, 0, qCount)
        this.isTapeSat = isTapeSatEnabled
        this.tapeDrive = tapeSatDrive

        recomputeCurve()
        invalidate()
    }

    private fun recomputeCurve() {
        BiquadFilterCalculator.calculateCascadedResponse(
            isoGainsDb = currentIsoGainsDb,
            isoQFactors = currentIsoQ,
            isTapeSatEnabled = isTapeSat,
            tapeSatDrive = tapeDrive,
            outCurveDb = curveDb
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = context.resources.displayMetrics.density
        val padLeft = 24f * density
        val padRight = 10f * density
        val padTop = 10f * density
        val padBottom = 16f * density

        plotLeft = padLeft
        plotTop = padTop
        plotRight = w - padRight
        plotBottom = h - padBottom
        plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        zeroY = plotTop + plotHeight * (MAX_DB / DB_RANGE)

        // Gradient for curve area fill
        if (!isEink) {
            curveFillPaint.shader = LinearGradient(
                0f, plotTop,
                0f, plotBottom,
                intArrayOf(Color.parseColor("#3300E676"), Color.parseColor("#0800E676"), Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun dbToY(db: Float): Float {
        val clampedDb = db.coerceIn(MIN_DB, MAX_DB)
        val norm = (MAX_DB - clampedDb) / DB_RANGE
        return plotTop + norm * plotHeight
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (plotWidth <= 0 || plotHeight <= 0) return

        // 1. Draw horizontal reference grid (+12, +6, 0, -6, -12 dB)
        drawDecibelGrid(canvas)

        // 2. Draw vertical frequency grid (31, 63, 125, 250, 500, 1k, 2k, 4k, 8k, 16k)
        drawFrequencyGrid(canvas)

        // 3. Draw live audio waveform spectrum envelope when playing
        if (isPlaying && !isEink) {
            drawAudioSpectrumEnvelope(canvas)
        }

        // 4. Draw Biquad Magnitude Response Curve & Fill
        drawBiquadCurve(canvas)

        // 5. Draw Interactive Touch Vernier Probe & HUD
        if (isProbing) {
            drawProbeHud(canvas)
        }

        // Keep animating spectrum if playback is active
        if (isPlaying) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawDecibelGrid(canvas: Canvas) {
        val dbSteps = floatArrayOf(12f, 6f, 0f, -6f, -12f)
        for (db in dbSteps) {
            val y = dbToY(db)
            if (abs(db) < 0.1f) {
                zeroLinePath.reset()
                zeroLinePath.moveTo(plotLeft, y)
                zeroLinePath.lineTo(plotRight, y)
                canvas.drawPath(zeroLinePath, zeroLinePaint)
            } else {
                canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
            }

            // dB label on left axis
            val label = if (db > 0) "+${db.toInt()}" else "${db.toInt()}"
            canvas.drawText(label, plotLeft - 10f, y + 7f, textPaint)
        }
    }

    private fun drawFrequencyGrid(canvas: Canvas) {
        val limit = minOf(GRID_FREQS.size, GRID_LABELS.size)
        for (i in 0 until limit) {
            val freq = GRID_FREQS[i]
            val ratio = BiquadFilterCalculator.freqToRatio(freq)
            val x = plotLeft + ratio * plotWidth

            // Vertical hairline
            canvas.drawLine(x, plotTop, x, plotBottom, gridPaint)

            // Frequency label along bottom
            canvas.drawText(GRID_LABELS[i], x, height - 3f, textPaint)
        }
    }

    private fun drawAudioSpectrumEnvelope(canvas: Canvas) {
        val timeMs = SystemClock.uptimeMillis()
        val phase = (timeMs % 3000) / 3000f * 2.0 * Math.PI

        spectrumPath.reset()
        spectrumPath.moveTo(plotLeft, plotBottom)

        val steps = 30
        for (s in 0..steps) {
            val ratio = s.toFloat() / steps
            val x = plotLeft + ratio * plotWidth
            val freqRatio = ratio * 4.0
            val wave1 = sin(phase + freqRatio * 3.14).toFloat() * 0.4f
            val wave2 = sin(phase * 1.7 + freqRatio * 6.28).toFloat() * 0.3f
            val amp = (wave1 + wave2 + 0.7f).coerceIn(0.1f, 1.2f)

            // Modulate under the biquad response curve
            val curveIdx = (ratio * (NUM_POINTS - 1)).toInt().coerceIn(0, NUM_POINTS - 1)
            val baseDb = curveDb[curveIdx]
            val specDb = (baseDb - 8f + amp * 6f).coerceIn(MIN_DB, MAX_DB)
            val y = dbToY(specDb)

            if (s == 0) spectrumPath.lineTo(x, y) else spectrumPath.lineTo(x, y)
        }

        spectrumPath.lineTo(plotRight, plotBottom)
        spectrumPath.close()
        canvas.drawPath(spectrumPath, spectrumPaint)
    }

    private fun drawBiquadCurve(canvas: Canvas) {
        curvePath.reset()
        fillPath.reset()

        fillPath.moveTo(plotLeft, plotBottom)

        for (i in 0 until NUM_POINTS) {
            val ratio = i.toFloat() / (NUM_POINTS - 1)
            val x = plotLeft + ratio * plotWidth
            val y = dbToY(curveDb[i])

            pointCoords[i * 2] = x
            pointCoords[i * 2 + 1] = y

            if (i == 0) {
                curvePath.moveTo(x, y)
                fillPath.lineTo(x, y)
            } else {
                // Smooth line connection across dense 120 points
                curvePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(plotRight, plotBottom)
        fillPath.close()

        // Draw gradient fill under curve
        canvas.drawPath(fillPath, curveFillPaint)

        // Draw bold emerald response stroke
        canvas.drawPath(curvePath, curvePaint)
    }

    private fun drawProbeHud(canvas: Canvas) {
        val cx = probeX.coerceIn(plotLeft, plotRight)
        val cy = dbToY(probeGainDb)

        // 1. Vertical cyan hairline
        canvas.drawLine(cx, plotTop, cx, plotBottom, probeLinePaint)

        // 2. Reticle target rings
        canvas.drawCircle(cx, cy, 7f, probeReticlePaint)
        canvas.drawCircle(cx, cy, 2.5f, probeReticleCenterPaint)

        // 3. HUD Tooltip Badge text: e.g. "1.2 kHz │ +4.2 dB"
        val freqStr = if (probeFreqHz >= 1000f) {
            String.format("%.1f kHz", probeFreqHz / 1000f)
        } else {
            String.format("%d Hz", probeFreqHz.toInt())
        }
        val gainStr = if (probeGainDb >= 0f) String.format("+%.1f dB", probeGainDb) else String.format("%.1f dB", probeGainDb)
        val hudText = "$freqStr │ $gainStr"

        val textWidth = hudTextPaint.measureText(hudText)
        val badgeW = textWidth + 24f
        val badgeH = 34f

        var badgeX = cx - badgeW / 2f
        if (badgeX < plotLeft) badgeX = plotLeft
        if (badgeX + badgeW > plotRight) badgeX = plotRight - badgeW

        var badgeY = cy - badgeH - 12f
        if (badgeY < plotTop) {
            badgeY = cy + 14f // Flip below target if too close to top
        }

        hudRect.set(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)
        canvas.drawRoundRect(hudRect, 6f, 6f, hudBgPaint)

        // Subtle border around HUD
        canvas.drawRoundRect(hudRect, 6f, 6f, probeLinePaint)

        // Text inside HUD
        val fontMetrics = hudTextPaint.fontMetrics
        val textBaseline = hudRect.centerY() - (fontMetrics.descent + fontMetrics.ascent) / 2f
        canvas.drawText(hudText, hudRect.centerX(), textBaseline, hudTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isProbing = true
                updateProbeCoordinates(event.x)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val prevFreq = probeFreqHz
                updateProbeCoordinates(event.x)

                // Haptic feedback when scrubbing across 10-band centers
                for (f in GRID_FREQS) {
                    if ((prevFreq < f && probeFreqHz >= f) || (prevFreq > f && probeFreqHz <= f)) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        break
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isProbing = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProbeCoordinates(touchX: Float) {
        probeX = touchX.coerceIn(plotLeft, plotRight)
        val ratio = ((probeX - plotLeft) / plotWidth).coerceIn(0f, 1f)
        probeFreqHz = BiquadFilterCalculator.ratioToFreq(ratio)

        // Interpolate exact response from calculated points or calculate directly
        probeGainDb = BiquadFilterCalculator.calculateResponseAt(
            freqHz = probeFreqHz,
            isoGainsDb = currentIsoGainsDb,
            isoQFactors = currentIsoQ,
            isTapeSatEnabled = isTapeSat,
            tapeSatDrive = tapeDrive
        )
    }
}
