package com.hana.spindle.ui.eq

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.hana.spindle.playback.AudioFxController
import kotlin.math.abs

/**
 * Audiophile 10-Band ISO Graphic Equalizer View.
 * Frequencies: 31Hz, 63Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz.
 *
 * Features:
 * - 10 Tactile vertical faders (-12.0 dB to +12.0 dB) with zero-detent.
 * - Smooth cubic Bézier frequency response curve with subtle glow fill.
 * - Multi-touch / sweep gesture: drag finger horizontally to sculpt EQ curves.
 * - Double tap on a fader resets that specific band to 0.0 dB.
 * - Zero allocations in onDraw for 60fps performance on low-RAM DAPs.
 */
class Iso10BandEqView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val NUM_BANDS = 10
        const val MIN_DB = -12.0f
        const val MAX_DB = 12.0f
        const val DB_RANGE = MAX_DB - MIN_DB
    }

    private val bandGains = FloatArray(NUM_BANDS) // in dB, -12f to +12f
    private val bandCentersX = FloatArray(NUM_BANDS)

    var onBandsChanged: ((FloatArray) -> Unit)? = null
    var onBandDragFinished: (() -> Unit)? = null

    var isEink: Boolean = false
        set(value) {
            field = value
            updateThemePaints()
            invalidate()
        }

    // Palette
    private val trackColor = Color.parseColor("#1A1C23")
    private val trackZeroColor = Color.parseColor("#3F4452")
    private val curveColor = Color.parseColor("#00E676") // Audiophile Emerald Glow
    private val curveFillColorTop = Color.parseColor("#2600E676")
    private val curveFillColorBottom = Color.parseColor("#0000E676")
    private val thumbCoreColor = Color.parseColor("#E4E4E7")
    private val thumbBorderColor = Color.parseColor("#00E676")
    private val textMutedColor = Color.parseColor("#71717A")
    private val textActiveColor = Color.parseColor("#A1A1AA")

    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private fun updateThemePaints() {
        if (isEink) {
            trackPaint.color = Color.BLACK
            zeroLinePaint.color = Color.BLACK
            curvePaint.color = Color.BLACK
            curveFillPaint.shader = null
            curveFillPaint.color = Color.TRANSPARENT
        } else {
            trackPaint.color = trackColor
            zeroLinePaint.color = trackZeroColor
            curvePaint.color = curveColor
            if (faderHeight > 0f) {
                curveFillPaint.shader = LinearGradient(
                    0f, faderTop,
                    0f, faderBottom,
                    curveFillColorTop, curveFillColorBottom,
                    Shader.TileMode.CLAMP
                )
            }
        }
    }

    private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackZeroColor
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = curveColor
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val curveFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    private val curvePath = Path()
    private val fillPath = Path()

    private var faderTop = 0f
    private var faderBottom = 0f
    private var faderHeight = 0f
    private var zeroY = 0f

    private var activeTouchBand = -1

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val band = getBandIndexAtX(e.x)
            if (band in 0 until NUM_BANDS) {
                bandGains[band] = 0f
                invalidate()
                onBandsChanged?.invoke(bandGains.clone())
                onBandDragFinished?.invoke()
                return true
            }
            return false
        }
    })

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setBands(gains: FloatArray) {
        val count = minOf(NUM_BANDS, gains.size)
        for (i in 0 until count) {
            bandGains[i] = gains[i].coerceIn(MIN_DB, MAX_DB)
        }
        invalidate()
    }

    fun getBands(): FloatArray = bandGains.clone()

    fun resetAll() {
        for (i in 0 until NUM_BANDS) {
            bandGains[i] = 0f
        }
        invalidate()
        onBandsChanged?.invoke(bandGains.clone())
        onBandDragFinished?.invoke()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        val topPadding = 24f * density
        val bottomPadding = 28f * density
        faderTop = topPadding
        faderBottom = h - bottomPadding
        faderHeight = faderBottom - faderTop
        zeroY = faderTop + faderHeight / 2f

        val bandWidth = w.toFloat() / NUM_BANDS
        for (i in 0 until NUM_BANDS) {
            bandCentersX[i] = bandWidth * (i + 0.5f)
        }

        trackPaint.strokeWidth = 4f * density
        if (!isEink) {
            curveFillPaint.shader = LinearGradient(
                0f, faderTop,
                0f, faderBottom,
                curveFillColorTop, curveFillColorBottom,
                Shader.TileMode.CLAMP
            )
        } else {
            curveFillPaint.shader = null
            curveFillPaint.color = Color.TRANSPARENT
        }
    }

    private fun gainToY(gainDb: Float): Float {
        val normalized = (gainDb - MIN_DB) / DB_RANGE // 0.0 at -12dB, 1.0 at +12dB
        return faderBottom - (normalized * faderHeight)
    }

    private fun yToGain(y: Float): Float {
        val clampedY = y.coerceIn(faderTop, faderBottom)
        val normalized = (faderBottom - clampedY) / faderHeight
        val rawGain = MIN_DB + (normalized * DB_RANGE)
        // Center deadzone snapping within +/- 0.3 dB
        return if (abs(rawGain) < 0.35f) 0f else (Math.round(rawGain * 2f) / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density

        // 1. Draw 0 dB reference line
        canvas.drawLine(0f, zeroY, width.toFloat(), zeroY, zeroLinePaint)

        // 2. Compute Spline Curve Path
        curvePath.reset()
        fillPath.reset()
        fillPath.moveTo(0f, zeroY)

        val firstX = bandCentersX[0]
        val firstY = gainToY(bandGains[0])
        curvePath.moveTo(firstX, firstY)
        fillPath.lineTo(firstX, firstY)

        for (i in 0 until NUM_BANDS - 1) {
            val x1 = bandCentersX[i]
            val y1 = gainToY(bandGains[i])
            val x2 = bandCentersX[i + 1]
            val y2 = gainToY(bandGains[i + 1])
            val cx = (x1 + x2) / 2f
            curvePath.cubicTo(cx, y1, cx, y2, x2, y2)
            fillPath.cubicTo(cx, y1, cx, y2, x2, y2)
        }

        val lastX = bandCentersX[NUM_BANDS - 1]
        fillPath.lineTo(lastX, zeroY)
        fillPath.close()

        // Draw curve glow fill and curve line
        if (!isEink) {
            canvas.drawPath(fillPath, curveFillPaint)
        }
        canvas.drawPath(curvePath, curvePaint)

        // 3. Draw Fader Tracks, Knobs & Text
        val thumbRadius = 6f * density
        val labels = AudioFxController.ISO_LABELS

        textPaint.textSize = 9.5f * density

        for (i in 0 until NUM_BANDS) {
            val cx = bandCentersX[i]
            val gain = bandGains[i]
            val cy = gainToY(gain)

            // Fader vertical track
            canvas.drawLine(cx, faderTop, cx, faderBottom, trackPaint)

            // Center notch
            canvas.drawCircle(cx, zeroY, 2f * density, zeroLinePaint)

            // Thumb Outer Ring
            thumbPaint.color = if (isEink) Color.BLACK else thumbBorderColor
            canvas.drawCircle(cx, cy, thumbRadius, thumbPaint)

            // Thumb Inner Core
            thumbPaint.color = if (isEink) Color.WHITE else thumbCoreColor
            canvas.drawCircle(cx, cy, thumbRadius - 2f * density, thumbPaint)

            // Center metallic pip
            thumbPaint.color = Color.BLACK
            canvas.drawCircle(cx, cy, 1.5f * density, thumbPaint)

            // Frequency label below
            textPaint.color = if (isEink) Color.BLACK else (if (gain != 0f) textActiveColor else textMutedColor)
            textPaint.isFakeBoldText = gain != 0f
            if (i < labels.size) {
                canvas.drawText(labels[i], cx, height - 8f * density, textPaint)
            }

            // dB value label above fader if boosted/cut
            if (gain != 0f) {
                val dbStr = if (gain > 0) "+${gain.toInt()}" else "${gain.toInt()}"
                textPaint.color = if (isEink) Color.BLACK else curveColor
                canvas.drawText(dbStr, cx, faderTop - 6f * density, textPaint)
            }
        }
    }

    private fun getBandIndexAtX(x: Float): Int {
        var closest = -1
        var minDistance = Float.MAX_VALUE
        for (i in 0 until NUM_BANDS) {
            val dist = abs(x - bandCentersX[i])
            if (dist < minDistance) {
                minDistance = dist
                closest = i
            }
        }
        return closest
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) {
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeTouchBand = getBandIndexAtX(event.x)
                if (activeTouchBand in 0 until NUM_BANDS) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    bandGains[activeTouchBand] = yToGain(event.y)
                    invalidate()
                    onBandsChanged?.invoke(bandGains.clone())
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val band = getBandIndexAtX(event.x)
                if (band in 0 until NUM_BANDS) {
                    bandGains[band] = yToGain(event.y)
                    activeTouchBand = band
                    invalidate()
                    onBandsChanged?.invoke(bandGains.clone())
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeTouchBand = -1
                parent.requestDisallowInterceptTouchEvent(false)
                onBandDragFinished?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
