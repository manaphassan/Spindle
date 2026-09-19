package com.hana.spindle.lite.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.hana.spindle.lite.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 60 FPS Hardware-Accelerated Kinetic Cassette Deck View for Spindle Lite.
 * Zero-allocation in onDraw(): All Paint, RectF, Path, and array objects are pre-allocated.
 * Scales fluidly to 3.0" 320x480 HVGA displays and modern high-res screens.
 */
class LiteDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- State Variables ---
    private var isPlaying = false
    private var progressFraction = 0.0f
    private var leftRotationDegrees = 0.0f
    private var rightRotationDegrees = 0.0f
    private var lastFrameTimeNanos = 0L
    private var cassetteLabel: String = "SPINDLE • HIGH BIAS TYPE II"

    // --- Pre-allocated Geometry Objects ---
    private val chassisRect = RectF()
    private val windowRect = RectF()
    private val labelRect = RectF()
    private val bridgeRect = RectF()
    private val counterRect = RectF()
    private val headRect = RectF()
    private val tapePath = Path()
    private val glarePath = Path()
    private val counterChars = CharArray(3)
    private val screwCoords = FloatArray(8)
    private var screwRadius = 0f
    private var capstanLeftX = 0f
    private var capstanRightX = 0f
    private var capstanY = 0f
    private var capstanRadius = 0f

    // Hub Coordinates & Dimensions
    private var leftHubX = 0f
    private var rightHubX = 0f
    private var hubCenterY = 0f
    private var hubRadius = 0f
    private var minHubRadius = 0f
    private var maxTapeRadius = 0f

    // --- Cached Theme Tokens for Collector Formulations ---
    private var metalChassisColor = 0
    private var metalBorderColor = 0
    private var metalLabelColor = 0
    private var metalLabelTextColor = 0
    private var metalSpokeColor = 0
    private var metalTapePackColor = 0

    private var type2ChassisColor = 0
    private var type2BorderColor = 0
    private var type2LabelColor = 0
    private var type2LabelTextColor = 0
    private var type2SpokeColor = 0
    private var type2TapePackColor = 0

    private var type1ChassisColor = 0
    private var type1BorderColor = 0
    private var type1LabelColor = 0
    private var type1LabelTextColor = 0
    private var type1SpokeColor = 0
    private var type1TapePackColor = 0

    // --- Pre-allocated Paint Objects ---
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val chassisBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val windowBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val tapePackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tapeRibbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val hubBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hubCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hubSpokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val accentSpokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rollerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rivetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rivetSlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val hubGroovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val counterBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val counterBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val counterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val capstanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(16, 255, 255, 255)
        style = Paint.Style.FILL
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        applyThemeTokens()
    }

    private fun applyThemeTokens() {
        val cInner = ContextCompat.getColor(context, R.color.lite_deck_inner)
        val cTapeRibbon = ContextCompat.getColor(context, R.color.lite_tape_ribbon)
        val cHubBody = ContextCompat.getColor(context, R.color.lite_reel_hub)
        val cRoller = ContextCompat.getColor(context, R.color.lite_roller)
        val cRivet = ContextCompat.getColor(context, R.color.lite_rivet)
        val cCounterBg = ContextCompat.getColor(context, R.color.lite_counter_bg)
        val cCounterText = ContextCompat.getColor(context, R.color.lite_amber_glow)
        val cHead = ContextCompat.getColor(context, R.color.lite_tape_head)
        val cCapstan = ContextCompat.getColor(context, R.color.lite_capstan_pin)

        // Cache Type IV Metal Master
        metalChassisColor = ContextCompat.getColor(context, R.color.lite_metal_chassis)
        metalBorderColor = ContextCompat.getColor(context, R.color.lite_metal_border)
        metalLabelColor = ContextCompat.getColor(context, R.color.lite_metal_label)
        metalLabelTextColor = ContextCompat.getColor(context, R.color.lite_metal_label_text)
        metalSpokeColor = ContextCompat.getColor(context, R.color.lite_metal_hero_spoke)
        metalTapePackColor = ContextCompat.getColor(context, R.color.lite_metal_tape_pack)

        // Cache Type II Chrome Hi-Bias
        type2ChassisColor = ContextCompat.getColor(context, R.color.lite_deck_surface)
        type2BorderColor = ContextCompat.getColor(context, R.color.lite_border)
        type2LabelColor = ContextCompat.getColor(context, R.color.lite_tape_label)
        type2LabelTextColor = ContextCompat.getColor(context, R.color.lite_tape_label_text)
        type2SpokeColor = ContextCompat.getColor(context, R.color.brand_orange)
        type2TapePackColor = ContextCompat.getColor(context, R.color.lite_tape_pack)

        // Cache Type I Normal Studio
        type1ChassisColor = ContextCompat.getColor(context, R.color.lite_type1_chassis)
        type1BorderColor = ContextCompat.getColor(context, R.color.lite_type1_border)
        type1LabelColor = ContextCompat.getColor(context, R.color.lite_type1_label)
        type1LabelTextColor = ContextCompat.getColor(context, R.color.lite_type1_label_text)
        type1SpokeColor = ContextCompat.getColor(context, R.color.lite_type1_hero_spoke)
        type1TapePackColor = ContextCompat.getColor(context, R.color.lite_type1_tape_pack)

        windowPaint.color = cInner
        tapeRibbonPaint.color = cTapeRibbon
        hubBodyPaint.color = cHubBody
        hubCenterPaint.color = cInner
        hubSpokePaint.color = cInner
        rollerPaint.color = cRoller
        rivetPaint.color = cRivet
        rivetSlotPaint.color = cInner
        counterBgPaint.color = cCounterBg
        counterBorderPaint.color = type2BorderColor
        counterTextPaint.color = cCounterText
        headPaint.color = cHead
        capstanPaint.color = cCapstan

        updateFormulationStyling(cassetteLabel)
    }

    private fun updateFormulationStyling(label: String) {
        when {
            label.contains("METAL", ignoreCase = true) -> {
                chassisPaint.color = metalChassisColor
                chassisBorderPaint.color = metalBorderColor
                windowBorderPaint.color = metalBorderColor
                hubGroovePaint.color = metalBorderColor
                labelPaint.color = metalLabelColor
                labelTextPaint.color = metalLabelTextColor
                accentSpokePaint.color = metalSpokeColor
                tapePackPaint.color = metalTapePackColor
            }
            label.contains("TYPE I", ignoreCase = true) || label.contains("NORMAL", ignoreCase = true) -> {
                chassisPaint.color = type1ChassisColor
                chassisBorderPaint.color = type1BorderColor
                windowBorderPaint.color = type1BorderColor
                hubGroovePaint.color = type1BorderColor
                labelPaint.color = type1LabelColor
                labelTextPaint.color = type1LabelTextColor
                accentSpokePaint.color = type1SpokeColor
                tapePackPaint.color = type1TapePackColor
            }
            else -> {
                chassisPaint.color = type2ChassisColor
                chassisBorderPaint.color = type2BorderColor
                windowBorderPaint.color = type2BorderColor
                hubGroovePaint.color = type2BorderColor
                labelPaint.color = type2LabelColor
                labelTextPaint.color = type2LabelTextColor
                accentSpokePaint.color = type2SpokeColor
                tapePackPaint.color = type2TapePackColor
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val padding = w * 0.04f
        chassisRect.set(padding, padding, w - padding, h - padding)

        // Corner Fastener Screws
        val screwOffset = padding * 1.5f
        screwRadius = padding * 0.45f
        screwCoords[0] = chassisRect.left + screwOffset
        screwCoords[1] = chassisRect.top + screwOffset
        screwCoords[2] = chassisRect.right - screwOffset
        screwCoords[3] = chassisRect.top + screwOffset
        screwCoords[4] = chassisRect.left + screwOffset
        screwCoords[5] = chassisRect.bottom - screwOffset
        screwCoords[6] = chassisRect.right - screwOffset
        screwCoords[7] = chassisRect.bottom - screwOffset

        val windowWidth = chassisRect.width() * 0.78f
        val windowHeight = chassisRect.height() * 0.44f
        val windowLeft = chassisRect.centerX() - (windowWidth / 2f)
        val windowTop = chassisRect.centerY() - (windowHeight / 2f) + (h * 0.02f)
        windowRect.set(windowLeft, windowTop, windowLeft + windowWidth, windowTop + windowHeight)

        // Top Label Strip
        val labelHeight = chassisRect.height() * 0.16f
        val labelTop = chassisRect.top + (chassisRect.height() * 0.06f)
        labelRect.set(chassisRect.left + (chassisRect.width() * 0.08f), labelTop, chassisRect.right - (chassisRect.width() * 0.08f), labelTop + labelHeight)
        labelTextPaint.textSize = labelHeight * 0.42f

        // Hub Centers & Tape Geometry
        hubCenterY = windowRect.centerY()
        val hubSpacing = windowRect.width() * 0.28f
        leftHubX = windowRect.centerX() - hubSpacing
        rightHubX = windowRect.centerX() + hubSpacing

        minHubRadius = windowHeight * 0.22f
        hubRadius = minHubRadius
        maxTapeRadius = windowHeight * 0.44f

        // Center Spindle Bridge
        val bridgeWidth = windowRect.width() * 0.20f
        val bridgeHeight = windowHeight * 0.65f
        bridgeRect.set(windowRect.centerX() - (bridgeWidth / 2f), windowRect.centerY() - (bridgeHeight / 2f), windowRect.centerX() + (bridgeWidth / 2f), windowRect.centerY() + (bridgeHeight / 2f))

        // Mechanical Tape Counter inside center bridge
        val counterW = bridgeWidth * 0.78f
        val counterH = bridgeHeight * 0.28f
        counterRect.set(
            windowRect.centerX() - (counterW / 2f),
            windowRect.centerY() - (counterH / 2f),
            windowRect.centerX() + (counterW / 2f),
            windowRect.centerY() + (counterH / 2f)
        )
        counterTextPaint.textSize = counterH * 0.72f

        // Acrylic Diagonal Glare Sheen
        glarePath.reset()
        glarePath.moveTo(windowRect.left + windowRect.width() * 0.42f, windowRect.top)
        glarePath.lineTo(windowRect.left + windowRect.width() * 0.64f, windowRect.top)
        glarePath.lineTo(windowRect.left + windowRect.width() * 0.34f, windowRect.bottom)
        glarePath.lineTo(windowRect.left + windowRect.width() * 0.12f, windowRect.bottom)
        glarePath.close()

        // Center Playback Tape Head Housing at window base
        val headWidth = bridgeWidth * 0.90f
        val headHeight = windowHeight * 0.16f
        headRect.set(
            windowRect.centerX() - (headWidth / 2f),
            windowRect.bottom - headHeight,
            windowRect.centerX() + (headWidth / 2f),
            windowRect.bottom
        )

        // Dual Brass Capstan Guide Pins
        val rollerRadius = minHubRadius * 0.35f
        capstanRadius = rollerRadius * 0.42f
        capstanY = windowRect.bottom - rollerRadius * 1.5f
        capstanLeftX = windowRect.left + rollerRadius * 1.5f
        capstanRightX = windowRect.right - rollerRadius * 1.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Draw Cassette Outer Body (Chassis)
        val cornerRadius = w * 0.03f
        canvas.drawRoundRect(chassisRect, cornerRadius, cornerRadius, chassisPaint)
        canvas.drawRoundRect(chassisRect, cornerRadius, cornerRadius, chassisBorderPaint)

        // 4 Corner Fastener Screws
        for (i in 0 until 4) {
            val sx = screwCoords[i * 2]
            val sy = screwCoords[i * 2 + 1]
            canvas.drawCircle(sx, sy, screwRadius, rivetPaint)
            canvas.drawLine(sx - screwRadius * 0.65f, sy, sx + screwRadius * 0.65f, sy, rivetSlotPaint)
        }

        // 2. Draw Label Strip
        val labelRadius = cornerRadius * 0.6f
        canvas.drawRoundRect(labelRect, labelRadius, labelRadius, labelPaint)
        canvas.drawText(
            cassetteLabel,
            labelRect.centerX(),
            labelRect.centerY() + (labelTextPaint.textSize * 0.35f),
            labelTextPaint
        )

        // 3. Draw Tape Chamber Window
        val winRadius = cornerRadius * 0.5f
        canvas.drawRoundRect(windowRect, winRadius, winRadius, windowPaint)

        // 4. Calculate Kinetic Tape Pack Radii (Conservation of tape area)
        val rMinSq = minHubRadius * minHubRadius
        val rMaxSq = maxTapeRadius * maxTapeRadius
        val diffSq = rMaxSq - rMinSq

        // Supply (left) shrinks from max to min as progress goes 0 -> 1
        val leftRadius = sqrt((1.0f - progressFraction) * diffSq + rMinSq)
        // Takeup (right) grows from min to max as progress goes 0 -> 1
        val rightRadius = sqrt(progressFraction * diffSq + rMinSq)

        // Draw Left & Right Magnetic Tape Packs
        canvas.drawCircle(leftHubX, hubCenterY, leftRadius, tapePackPaint)
        canvas.drawCircle(rightHubX, hubCenterY, rightRadius, tapePackPaint)

        // 5. Draw Tangent Tape Ribbon connecting the two reels
        tapePath.reset()
        tapePath.moveTo(leftHubX, hubCenterY + leftRadius)
        tapePath.lineTo(rightHubX, hubCenterY + rightRadius)
        canvas.drawPath(tapePath, tapeRibbonPaint)

        // 6. Draw Tape Guide Rollers & Capstans
        val rollerRadius = minHubRadius * 0.35f
        canvas.drawCircle(capstanLeftX, capstanY, rollerRadius, rollerPaint)
        canvas.drawCircle(capstanRightX, capstanY, rollerRadius, rollerPaint)

        // Brass Capstan Guide Pins
        canvas.drawCircle(capstanLeftX, capstanY, capstanRadius, capstanPaint)
        canvas.drawCircle(capstanRightX, capstanY, capstanRadius, capstanPaint)

        // 7. Draw Playback Head Housing & Center Bridge
        canvas.drawRoundRect(headRect, 3f, 3f, headPaint)
        canvas.drawRoundRect(headRect, 3f, 3f, windowBorderPaint)

        canvas.drawRoundRect(bridgeRect, 4f, 4f, chassisPaint)
        canvas.drawRoundRect(bridgeRect, 4f, 4f, windowBorderPaint)

        // Render 3-Digit Mechanical Tape Drum Counter (Zero-allocation)
        val counterVal = (progressFraction * 999).toInt().coerceIn(0, 999)
        counterChars[0] = ('0'.code + (counterVal / 100)).toChar()
        counterChars[1] = ('0'.code + ((counterVal / 10) % 10)).toChar()
        counterChars[2] = ('0'.code + (counterVal % 10)).toChar()

        canvas.drawRoundRect(counterRect, 2f, 2f, counterBgPaint)
        canvas.drawRoundRect(counterRect, 2f, 2f, counterBorderPaint)
        canvas.drawText(
            counterChars,
            0,
            3,
            counterRect.centerX(),
            counterRect.centerY() + (counterTextPaint.textSize * 0.35f),
            counterTextPaint
        )

        // 8. Draw Rotating Hubs
        drawHub(canvas, leftHubX, hubCenterY, leftRotationDegrees)
        drawHub(canvas, rightHubX, hubCenterY, rightRotationDegrees)

        // 9. Draw Acrylic Diagonal Window Glare Sheen
        canvas.drawPath(glarePath, glarePaint)

        // 10. Draw Window Border
        canvas.drawRoundRect(windowRect, winRadius, winRadius, windowBorderPaint)

        // 11. Frame Loop Animation
        if (isPlaying) {
            val now = System.nanoTime()
            if (lastFrameTimeNanos > 0L) {
                val dtSeconds = (now - lastFrameTimeNanos) / 1_000_000_000.0f
                val baseSpeed = 160.0f
                val leftSpeed = baseSpeed * (maxTapeRadius / leftRadius)
                val rightSpeed = baseSpeed * (maxTapeRadius / rightRadius)

                leftRotationDegrees = (leftRotationDegrees + leftSpeed * dtSeconds) % 360f
                rightRotationDegrees = (rightRotationDegrees + rightSpeed * dtSeconds) % 360f
            }
            lastFrameTimeNanos = now
            postInvalidateOnAnimation()
        } else {
            lastFrameTimeNanos = 0L
        }
    }

    private fun drawHub(canvas: Canvas, cx: Float, cy: Float, rotationDeg: Float) {
        // Outer White Plastic Hub Ring
        canvas.drawCircle(cx, cy, hubRadius, hubBodyPaint)

        // Precision Machined Concentric Hub Groove
        canvas.drawCircle(cx, cy, hubRadius * 0.88f, hubGroovePaint)

        // 6-Tooth Hub Spokes
        val spokeRadius = hubRadius * 0.72f
        val spokeWidth = hubRadius * 0.22f
        val centerHoleRadius = hubRadius * 0.38f

        for (i in 0 until 6) {
            val angleDeg = rotationDeg + (i * 60f)
            val angleRad = angleDeg * (PI.toFloat() / 180f)
            val spokeX = cx + (spokeRadius * cos(angleRad))
            val spokeY = cy + (spokeRadius * sin(angleRad))

            val paint = if (i == 0) accentSpokePaint else hubSpokePaint
            canvas.drawCircle(spokeX, spokeY, spokeWidth * 0.5f, paint)
        }

        // Center Spindle Axle Hole
        canvas.drawCircle(cx, cy, centerHoleRadius, hubCenterPaint)
    }

    fun setCassetteLabel(label: String) {
        if (this.cassetteLabel != label) {
            this.cassetteLabel = label
            updateFormulationStyling(label)
            invalidate()
        }
    }

    fun setPlaybackState(playing: Boolean, fraction: Float) {
        this.isPlaying = playing
        this.progressFraction = fraction.coerceIn(0f, 1f)
        if (playing) {
            postInvalidateOnAnimation()
        } else {
            invalidate()
        }
    }
}
