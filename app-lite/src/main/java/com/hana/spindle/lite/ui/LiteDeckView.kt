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
 * 60 FPS Hardware-Accelerated Kinetic Vertical Cassette Deck View for Spindle Lite.
 * Zero-allocation in onDraw(): All Paint, RectF, Path, and array objects are pre-allocated.
 *
 * Hyper-detailed vertical audiophile mechanical rendering:
 * 1. Portrait-oriented precision molded cassette shell (64mm x 100mm ratio matching phone screen).
 * 2. Left column studio paper label / spine with formulation accent stripe, Side "A" badge pill,
 *    faint ruled lines, vertical title typography, and Dolby calibration markings.
 * 3. Central clear acrylic window framing two vertically stacked Delrin spools (Top Supply Reel,
 *    Bottom Take-Up Reel) with dynamic magnetic tape packs, winding micro-grooves, rotating anisotropic
 *    sheen glints, and 3-digit mechanical drum counter with odometer partitions.
 * 4. Right side trapezoidal chin with authentic vertical tape ribbon loop path wrapping around
 *    upper and lower guide rollers, running across the erase head, bronze leaf spring with felt
 *    pressure pad, and permalloy playback head with dual azimuth adjustment screws.
 * 5. 5 assembly fastener screws with slotted drive heads, tactile shoulder grip ribs, deck locator
 *    pin holes, and embossed shell markings ("MADE IN JAPAN", "A").
 */
class LiteDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- State Variables ---
    private var isPlaying = false
    private var progressFraction = 0.0f
    private var topRotationDegrees = 0.0f
    private var bottomRotationDegrees = 0.0f
    private var lastFrameTimeNanos = 0L
    private var cassetteLabel: String = "SPINDLE • HIGH BIAS TYPE II"
    private var nowPlayingTitle: String = ""
    private var nowPlayingArtist: String = ""
    private var nowPlayingTime: String = ""
    private var nowPlayingSpecs: String = ""

    // --- Pre-allocated Geometry Objects ---
    private val chassisRect = RectF()
    private val innerBevelRect = RectF()
    private val writeProtectTopRect = RectF()
    private val writeProtectBottomRect = RectF()
    private val type2NotchRect = RectF()

    private val labelWellRect = RectF()
    private val labelRect = RectF()
    private val labelStripeRect = RectF()
    private val labelSideBadgeRect = RectF()

    private val windowWellRect = RectF()
    private val windowRect = RectF()
    private val bridgeRect = RectF()

    private val headChamberRect = RectF()
    private val headRect = RectF()
    private val headCoreRect = RectF()
    private val springPadRect = RectF()
    private val feltPadRect = RectF()

    private val shellPath = Path()
    private val innerBevelPath = Path()
    private val trapezePath = Path()
    private val tapeLoopPath = Path()
    private val glarePath1 = Path()
    private val glarePath2 = Path()

    // 5 Assembly Screws: 4 corners + 1 center-left
    private val screwCoords = FloatArray(10)
    private val screwRotations = floatArrayOf(24f, 68f, 42f, 15f, 78f)
    private var screwRadius = 0f

    // Tactile grip ribs (4 per shoulder)
    private val topGripCoords = FloatArray(16) // 4 ribs * 4 coords
    private val bottomGripCoords = FloatArray(16)

    // Deck Locator Pin Holes & Azimuth Screws
    private var locatorPinX = 0f
    private var locatorPinTopY = 0f
    private var locatorPinBottomY = 0f
    private var locatorPinRadius = 0f

    private var headScrewX = 0f
    private var headScrewTopY = 0f
    private var headScrewBottomY = 0f
    private var headScrewRadius = 0f

    // Capstan Guide Pins & Pinch Rollers (Right side)
    private var capstanX = 0f
    private var topCapstanY = 0f
    private var bottomCapstanY = 0f
    private var capstanRadius = 0f
    private var rollerRadius = 0f

    // Hub Coordinates & Dimensions (Vertical stacking)
    private var hubCenterX = 0f
    private var topHubY = 0f
    private var bottomHubY = 0f
    private var hubRadius = 0f
    private var minHubRadius = 0f
    private var maxTapeRadius = 0f

    // Ruler Tick Positions (Vertical gauge)
    private val rulerTickYs = FloatArray(5)
    private val rulerLabels = arrayOf("100", "50", "0", "50", "100")

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
        strokeWidth = 2.5f
    }
    private val innerBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(40, 255, 255, 255)
    }
    private val trapezePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(35, 0, 0, 0)
    }
    private val writeProtectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#12141C")
    }
    private val writeProtectBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(50, 255, 255, 255)
    }

    private val labelWellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(60, 0, 0, 0)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelStripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelRuledLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(45, 0, 0, 0)
    }
    private val labelSideBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelSideTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val labelStripeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val labelTitleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val labelSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        color = Color.argb(135, 0, 0, 0)
    }
    private val labelSpecsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.argb(165, 120, 53, 15)
    }

    private val windowWellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.argb(65, 0, 0, 0)
    }
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val windowBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val tapePackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tapeRibbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val tapeMicroGroovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(38, 255, 255, 255)
    }
    private val tapeSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.argb(55, 255, 255, 255)
    }

    private val hubBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hubCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hubSpokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val accentSpokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hubStrobeHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 255, 255, 255)
    }
    private val hubGroovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val hubClampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EF4444")
    }

    private val rollerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rollerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#1B1E2B")
    }
    private val capstanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val locatorPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#12141F")
    }

    private val feltPadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E7E5E4")
    }
    private val springPadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#B45309")
    }

    private val rivetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rivetSlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val gripRibPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        color = Color.argb(60, 0, 0, 0)
    }
    private val gripRibHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
        color = Color.argb(30, 255, 255, 255)
    }


    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val headCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4A4F68")
    }
    private val headGapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.parseColor("#12141F")
    }
    private val headScrewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#94A3B8")
    }

    private val rulerTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(80, 253, 230, 138)
    }
    private val rulerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = Color.argb(100, 253, 230, 138)
    }

    private val shellEmbossTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.argb(55, 255, 255, 255)
    }

    private val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(16, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val glarePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(10, 255, 255, 255)
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
                labelTitleTextPaint.color = metalLabelTextColor
                labelStripePaint.color = ContextCompat.getColor(context, R.color.brand_orange)
                labelSideBadgePaint.color = Color.parseColor("#EF4444")
                accentSpokePaint.color = metalSpokeColor
                tapePackPaint.color = metalTapePackColor
                hubClampPaint.color = Color.parseColor("#3B82F6")
            }
            label.contains("TYPE I", ignoreCase = true) || label.contains("NORMAL", ignoreCase = true) -> {
                chassisPaint.color = type1ChassisColor
                chassisBorderPaint.color = type1BorderColor
                windowBorderPaint.color = type1BorderColor
                hubGroovePaint.color = type1BorderColor
                labelPaint.color = type1LabelColor
                labelTitleTextPaint.color = type1LabelTextColor
                labelStripePaint.color = ContextCompat.getColor(context, R.color.lite_type1_hero_spoke)
                labelSideBadgePaint.color = ContextCompat.getColor(context, R.color.lite_type1_hero_spoke)
                accentSpokePaint.color = type1SpokeColor
                tapePackPaint.color = type1TapePackColor
                hubClampPaint.color = Color.parseColor("#10B981")
            }
            else -> {
                chassisPaint.color = type2ChassisColor
                chassisBorderPaint.color = type2BorderColor
                windowBorderPaint.color = type2BorderColor
                hubGroovePaint.color = type2BorderColor
                labelPaint.color = type2LabelColor
                labelTitleTextPaint.color = type2LabelTextColor
                labelStripePaint.color = ContextCompat.getColor(context, R.color.brand_orange)
                labelSideBadgePaint.color = ContextCompat.getColor(context, R.color.brand_orange)
                accentSpokePaint.color = type2SpokeColor
                tapePackPaint.color = type2TapePackColor
                hubClampPaint.color = Color.parseColor("#EF4444")
            }
        }

        fitLabelText()
    }

    private fun fitLabelText() {
        val maxLabelTextWidth = (labelRect.bottom - labelSideBadgeRect.bottom) * 0.84f
        val lw = labelRect.width()
        if (maxLabelTextWidth > 0f && lw > 0f) {
            // Line 1: Title (top)
            val displayTitle = if (nowPlayingTitle.isNotBlank()) nowPlayingTitle else cassetteLabel
            var targetTitleSize = lw * 0.17f
            labelTitleTextPaint.textSize = targetTitleSize
            val measuredTitle = labelTitleTextPaint.measureText(displayTitle)
            if (measuredTitle > maxLabelTextWidth && measuredTitle > 0f) {
                targetTitleSize *= (maxLabelTextWidth / measuredTitle)
                labelTitleTextPaint.textSize = targetTitleSize.coerceAtLeast(8f)
            }

            // Line 2: Artist, time duration (middle)
            val displayArtistTime = if (nowPlayingTime.isNotBlank()) {
                if (nowPlayingArtist.isNotBlank()) "$nowPlayingArtist • $nowPlayingTime" else nowPlayingTime
            } else if (nowPlayingArtist.isNotBlank()) {
                nowPlayingArtist
            } else {
                "STUDIO MASTER • STEREO"
            }
            var targetSubSize = lw * 0.125f
            labelSubTextPaint.textSize = targetSubSize
            val measuredSub = labelSubTextPaint.measureText(displayArtistTime)
            if (measuredSub > maxLabelTextWidth && measuredSub > 0f) {
                targetSubSize *= (maxLabelTextWidth / measuredSub)
                labelSubTextPaint.textSize = targetSubSize.coerceAtLeast(6.5f)
            }

            // Line 3: Format bits kHz (below)
            val displaySpecs = if (nowPlayingSpecs.isNotBlank()) {
                nowPlayingSpecs
            } else {
                "MP3 • 320 kbps • 44.1 kHz"
            }
            var targetSpecsSize = lw * 0.115f
            labelSpecsTextPaint.textSize = targetSpecsSize
            val measuredSpecs = labelSpecsTextPaint.measureText(displaySpecs)
            if (measuredSpecs > maxLabelTextWidth && measuredSpecs > 0f) {
                targetSpecsSize *= (maxLabelTextWidth / measuredSpecs)
                labelSpecsTextPaint.textSize = targetSpecsSize.coerceAtLeast(6f)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        // Proportional centering of vertical cassette shell
        val padX = w * 0.035f
        val padY = h * 0.02f
        chassisRect.set(padX, padY, w - padX, h - padY)

        val cw = chassisRect.width()
        val ch = chassisRect.height()

        val innerOffset = 3f
        innerBevelRect.set(
            chassisRect.left + innerOffset,
            chassisRect.top + innerOffset,
            chassisRect.right - innerOffset,
            chassisRect.bottom - innerOffset
        )

        // Molded Shell Outline Path
        val cornerR = w * 0.035f
        shellPath.reset()
        shellPath.addRoundRect(chassisRect, cornerR, cornerR, Path.Direction.CW)

        innerBevelPath.reset()
        innerBevelPath.addRoundRect(innerBevelRect, cornerR - 1f, cornerR - 1f, Path.Direction.CW)

        // Write-Protect Tab Notches along the LEFT edge (spine) of the vertical cassette
        val notchW = cw * 0.035f
        val notchH = ch * 0.065f
        writeProtectTopRect.set(
            chassisRect.left,
            chassisRect.top + ch * 0.12f,
            chassisRect.left + notchW,
            chassisRect.top + ch * 0.12f + notchH
        )
        writeProtectBottomRect.set(
            chassisRect.left,
            chassisRect.bottom - ch * 0.12f - notchH,
            chassisRect.left + notchW,
            chassisRect.bottom - ch * 0.12f
        )
        type2NotchRect.set(
            chassisRect.left,
            chassisRect.centerY() - notchH * 0.4f,
            chassisRect.left + notchW * 0.8f,
            chassisRect.centerY() + notchH * 0.4f
        )

        // Trapezoidal Chin on the RIGHT side (where the tape head and capstans reside)
        val trapW = cw * 0.17f
        val trapInnerX = chassisRect.right - trapW
        val trapOuterX = chassisRect.right
        val trapTopY = chassisRect.top + ch * 0.14f
        val trapBottomY = chassisRect.bottom - ch * 0.14f

        trapezePath.reset()
        trapezePath.moveTo(trapOuterX, chassisRect.top + ch * 0.09f)
        trapezePath.lineTo(trapInnerX, trapTopY)
        trapezePath.lineTo(trapInnerX, trapBottomY)
        trapezePath.lineTo(trapOuterX, chassisRect.bottom - ch * 0.09f)
        trapezePath.close()

        headChamberRect.set(trapInnerX, trapTopY, trapOuterX, trapBottomY)

        // Permalloy Playback Head in center of right chin
        val headCenterY = chassisRect.centerY()
        val headW = trapW * 0.72f
        val headH = ch * 0.16f
        headRect.set(
            trapOuterX - headW,
            headCenterY - headH * 0.5f,
            trapOuterX,
            headCenterY + headH * 0.5f
        )
        val coreMargin = headH * 0.18f
        headCoreRect.set(
            headRect.left + 2f,
            headCenterY - headH * 0.28f,
            headRect.right - 3f,
            headCenterY + headH * 0.28f
        )

        // Bronze Leaf Spring and Felt Pressure Pad directly behind head
        val feltW = headW * 0.28f
        val feltH = headH * 0.38f
        feltPadRect.set(
            headRect.left - feltW,
            headCenterY - feltH * 0.5f,
            headRect.left,
            headCenterY + feltH * 0.5f
        )
        springPadRect.set(
            feltPadRect.left - feltW * 0.45f,
            headCenterY - feltH * 0.85f,
            feltPadRect.left,
            headCenterY + feltH * 0.85f
        )

        // Azimuth adjustment micro-screws (above and below head)
        headScrewRadius = headW * 0.10f
        headScrewX = headRect.centerX()
        headScrewTopY = headRect.top - headScrewRadius * 2.2f
        headScrewBottomY = headRect.bottom + headScrewRadius * 2.2f

        // Upper & Lower Guide Rollers & Brass Capstans (Right side corners of chin)
        capstanX = trapInnerX + trapW * 0.46f
        rollerRadius = ch * 0.038f
        capstanRadius = rollerRadius * 0.38f
        topCapstanY = trapTopY + rollerRadius * 1.5f
        bottomCapstanY = trapBottomY - rollerRadius * 1.5f

        // Deck Locator Pin Holes (Top-right & Bottom-right outer corners)
        locatorPinRadius = screwRadius * 0.85f
        locatorPinX = chassisRect.right - cw * 0.07f
        locatorPinTopY = chassisRect.top + ch * 0.08f
        locatorPinBottomY = chassisRect.bottom - ch * 0.08f

        // 5 Assembly Fastener Screws: 4 corners + 1 center-left
        val screwOffset = padX * 1.6f
        screwRadius = padX * 0.42f
        screwCoords[0] = chassisRect.left + screwOffset
        screwCoords[1] = chassisRect.top + screwOffset
        screwCoords[2] = chassisRect.right - screwOffset
        screwCoords[3] = chassisRect.top + screwOffset
        screwCoords[4] = chassisRect.left + screwOffset
        screwCoords[5] = chassisRect.bottom - screwOffset * 0.9f
        screwCoords[6] = chassisRect.right - screwOffset
        screwCoords[7] = chassisRect.bottom - screwOffset * 0.9f
        screwCoords[8] = chassisRect.left + screwOffset * 0.85f
        screwCoords[9] = chassisRect.centerY()

        // Tactile Grip Ribs (4 ribs on top and bottom outer shoulders)
        val ribXStart = chassisRect.left + cw * 0.16f
        val ribSpacing = cw * 0.026f
        val ribLen = ch * 0.038f
        for (r in 0 until 4) {
            val rx = ribXStart + (r * ribSpacing)
            val idx = r * 4
            // Top ribs
            topGripCoords[idx] = rx
            topGripCoords[idx + 1] = chassisRect.top + 2f
            topGripCoords[idx + 2] = rx
            topGripCoords[idx + 3] = chassisRect.top + 2f + ribLen
            // Bottom ribs
            bottomGripCoords[idx] = rx
            bottomGripCoords[idx + 1] = chassisRect.bottom - 2f - ribLen
            bottomGripCoords[idx + 2] = rx
            bottomGripCoords[idx + 3] = chassisRect.bottom - 2f
        }

        // Hub Dimensions (White Delrin Spindle Drive Hub)
        hubRadius = cw * 0.122f
        minHubRadius = hubRadius
        maxTapeRadius = hubRadius * 1.65f

        // Clear Acrylic Window Slit (Narrow vertical slit matching White Spindle Drive Hub with 2px precision margin)
        val winW = hubRadius * 2f + 4f
        hubCenterX = chassisRect.centerX()
        val winLeft = hubCenterX - winW * 0.5f
        val winRight = hubCenterX + winW * 0.5f
        val winTop = chassisRect.top + ch * 0.07f
        val winBottom = chassisRect.bottom - ch * 0.07f
        windowRect.set(winLeft, winTop, winRight, winBottom)

        val wellOffset = 1.5f
        windowWellRect.set(
            windowRect.left - wellOffset,
            windowRect.top - wellOffset,
            windowRect.right + wellOffset,
            windowRect.bottom + wellOffset
        )

        // Spindle Hub Centers (Vertically stacked: Top Supply Reel, Bottom Take-Up Reel)
        val hubSpacing = windowRect.height() * 0.28f
        topHubY = windowRect.centerY() - hubSpacing
        bottomHubY = windowRect.centerY() + hubSpacing

        // Center Spindle Bridge (Between top and bottom spools)
        val bridgeW = winW - 4f
        val bridgeH = windowRect.height() * 0.13f
        bridgeRect.set(
            hubCenterX - bridgeW / 2f,
            windowRect.centerY() - bridgeH / 2f,
            hubCenterX + bridgeW / 2f,
            windowRect.centerY() + bridgeH / 2f
        )


        // Tape Volume Ruler Graduations (Ticks along window slit)
        val rulerStep = windowRect.height() * 0.14f
        rulerTickYs[0] = windowRect.centerY() - rulerStep * 2
        rulerTickYs[1] = windowRect.centerY() - rulerStep
        rulerTickYs[2] = windowRect.centerY()
        rulerTickYs[3] = windowRect.centerY() + rulerStep
        rulerTickYs[4] = windowRect.centerY() + rulerStep * 2
        rulerTextPaint.textSize = winW * 0.12f

        // Primary Acrylic Diagonal Glare Sheen
        glarePath1.reset()
        glarePath1.moveTo(windowRect.left, windowRect.top + windowRect.height() * 0.35f)
        glarePath1.lineTo(windowRect.left, windowRect.top + windowRect.height() * 0.55f)
        glarePath1.lineTo(windowRect.right, windowRect.top + windowRect.height() * 0.30f)
        glarePath1.lineTo(windowRect.right, windowRect.top + windowRect.height() * 0.10f)
        glarePath1.close()

        // Secondary Parallel Glint
        glarePath2.reset()
        glarePath2.moveTo(windowRect.left, windowRect.top + windowRect.height() * 0.60f)
        glarePath2.lineTo(windowRect.left, windowRect.top + windowRect.height() * 0.68f)
        glarePath2.lineTo(windowRect.right, windowRect.top + windowRect.height() * 0.43f)
        glarePath2.lineTo(windowRect.right, windowRect.top + windowRect.height() * 0.35f)
        glarePath2.close()

        // Studio Paper Label (Left Column / Spine)
        val labelLeft = chassisRect.left + cw * 0.05f
        val labelRight = windowRect.left - cw * 0.035f
        val labelTop = chassisRect.top + ch * 0.07f
        val labelBottom = chassisRect.bottom - ch * 0.07f
        labelRect.set(labelLeft, labelTop, labelRight, labelBottom)

        labelWellRect.set(
            labelLeft - 1.5f,
            labelTop - 1.5f,
            labelRight + 1.5f,
            labelBottom + 1.5f
        )

        // Top Accent Stripe across label
        val stripeH = ch * 0.055f
        labelStripeRect.set(labelLeft, labelTop, labelRight, labelTop + stripeH)
        labelStripeTextPaint.textSize = stripeH * 0.55f

        // Side "A" Badge Pill
        val badgeW = (labelRight - labelLeft) * 0.60f
        val badgeH = stripeH * 0.85f
        val badgeLeft = labelLeft + ((labelRight - labelLeft) - badgeW) / 2f
        val badgeTop = labelStripeRect.bottom + ch * 0.015f
        labelSideBadgeRect.set(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH)
        labelSideTextPaint.textSize = badgeH * 0.70f

        labelSubTextPaint.textSize = (labelRight - labelLeft) * 0.14f
        shellEmbossTextPaint.textSize = ch * 0.030f

        fitLabelText()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // =========================================================================
        // 1. VERTICAL CASSETTE OUTER MOLDED SHELL
        // =========================================================================
        canvas.drawPath(shellPath, chassisPaint)
        canvas.drawPath(innerBevelPath, innerBevelPaint)

        // Write-protect tab notches (Left Edge)
        canvas.drawRect(writeProtectTopRect, writeProtectPaint)
        canvas.drawRect(writeProtectTopRect, writeProtectBorderPaint)
        canvas.drawRect(writeProtectBottomRect, writeProtectPaint)
        canvas.drawRect(writeProtectBottomRect, writeProtectBorderPaint)
        canvas.drawRect(type2NotchRect, writeProtectPaint)
        canvas.drawRect(type2NotchRect, writeProtectBorderPaint)

        // Molded Trapeze Head Chamber Shadow (Right Side)
        canvas.drawPath(trapezePath, trapezePaint)

        // Tactile Shoulder Grip Ribs (Top and Bottom shoulders)
        for (r in 0 until 4) {
            val idx = r * 4
            // Top ribs
            canvas.drawLine(topGripCoords[idx], topGripCoords[idx + 1], topGripCoords[idx + 2], topGripCoords[idx + 3], gripRibPaint)
            canvas.drawLine(topGripCoords[idx] + 1f, topGripCoords[idx + 1], topGripCoords[idx + 2] + 1f, topGripCoords[idx + 3], gripRibHighlightPaint)
            // Bottom ribs
            canvas.drawLine(bottomGripCoords[idx], bottomGripCoords[idx + 1], bottomGripCoords[idx + 2], bottomGripCoords[idx + 3], gripRibPaint)
            canvas.drawLine(bottomGripCoords[idx] + 1f, bottomGripCoords[idx + 1], bottomGripCoords[idx + 2] + 1f, bottomGripCoords[idx + 3], gripRibHighlightPaint)
        }

        // Deck Locating Guide Pin Holes (Right side chin corners)
        canvas.drawCircle(locatorPinX, locatorPinTopY, locatorPinRadius, locatorPinPaint)
        canvas.drawCircle(locatorPinX, locatorPinBottomY, locatorPinRadius, locatorPinPaint)
        canvas.drawCircle(locatorPinX, locatorPinTopY, locatorPinRadius * 0.55f, innerBevelPaint)
        canvas.drawCircle(locatorPinX, locatorPinBottomY, locatorPinRadius * 0.55f, innerBevelPaint)

        // Embossed Shell Markings ("MADE IN JAPAN" and Side "A")
        canvas.drawText("MADE IN JAPAN", chassisRect.centerX(), chassisRect.bottom - 4f, shellEmbossTextPaint)
        canvas.drawText("A", chassisRect.left + chassisRect.width() * 0.08f, chassisRect.top + chassisRect.height() * 0.06f, shellEmbossTextPaint)

        // 5 Real Assembly Fastener Screws with Slotted Drive Heads
        for (i in 0 until 5) {
            val sx = screwCoords[i * 2]
            val sy = screwCoords[i * 2 + 1]
            canvas.drawCircle(sx, sy, screwRadius, rivetPaint)

            val rot = screwRotations[i] * (PI.toFloat() / 180f)
            val dx = cos(rot) * screwRadius * 0.65f
            val dy = sin(rot) * screwRadius * 0.65f
            canvas.drawLine(sx - dx, sy - dy, sx + dx, sy + dy, rivetSlotPaint)
        }

        canvas.drawPath(shellPath, chassisBorderPaint)

        // Molded Concentric Reel Wells on Cassette Shell Faceplate (Embossed ABS plastic)
        canvas.drawCircle(hubCenterX, topHubY, maxTapeRadius, innerBevelPaint)
        canvas.drawCircle(hubCenterX, topHubY, maxTapeRadius * 0.82f, innerBevelPaint)
        canvas.drawCircle(hubCenterX, bottomHubY, maxTapeRadius, innerBevelPaint)
        canvas.drawCircle(hubCenterX, bottomHubY, maxTapeRadius * 0.82f, innerBevelPaint)

        // =========================================================================
        // 2. STUDIO PAPER LABEL / SPINE (LEFT COLUMN)
        // =========================================================================
        val labelRadius = w * 0.015f
        // Recessed well shadow
        canvas.drawRoundRect(labelWellRect, labelRadius + 1f, labelRadius + 1f, labelWellPaint)
        // White studio paper
        canvas.drawRoundRect(labelRect, labelRadius, labelRadius, labelPaint)

        // Top Formulation Accent Stripe
        canvas.drawRect(labelStripeRect, labelStripePaint)
        val stripeTag = when {
            cassetteLabel.contains("METAL", ignoreCase = true) -> "METAL"
            cassetteLabel.contains("TYPE I", ignoreCase = true) || cassetteLabel.contains("NORMAL", ignoreCase = true) -> "NORMAL"
            else -> "CrO2"
        }
        canvas.drawText(
            stripeTag,
            labelStripeRect.centerX(),
            labelStripeRect.centerY() + (labelStripeTextPaint.textSize * 0.35f),
            labelStripeTextPaint
        )

        // Side "A" Badge Pill
        val badgeRadius = labelSideBadgeRect.height() * 0.25f
        canvas.drawRoundRect(labelSideBadgeRect, badgeRadius, badgeRadius, labelSideBadgePaint)
        canvas.drawText(
            "A",
            labelSideBadgeRect.centerX(),
            labelSideBadgeRect.centerY() + (labelSideTextPaint.textSize * 0.35f),
            labelSideTextPaint
        )

        // Vertical Ruled Paper Lines & Spine Title Typography (-90° rotation)
        canvas.save()
        val spineCenterX = labelRect.centerX()
        val spineCenterY = labelSideBadgeRect.bottom + (labelRect.bottom - labelSideBadgeRect.bottom) * 0.5f
        canvas.translate(spineCenterX, spineCenterY)
        canvas.rotate(-90f)

        val lw = labelRect.width()
        val spineLen = (labelRect.bottom - labelSideBadgeRect.bottom) * 0.86f

        // Two Faint Ruled Paper Lines separating the 3 rows along spine length
        val ruleY1 = -lw * 0.05f
        val ruleY2 = lw * 0.18f
        canvas.drawLine(-spineLen * 0.5f, ruleY1, spineLen * 0.5f, ruleY1, labelRuledLinePaint)
        canvas.drawLine(-spineLen * 0.5f, ruleY2, spineLen * 0.5f, ruleY2, labelRuledLinePaint)

        // Line 1: Top Now Playing Title (with left spacing from cassette label edge)
        val displayTitle = if (nowPlayingTitle.isNotBlank()) nowPlayingTitle else cassetteLabel
        canvas.drawText(
            displayTitle,
            0f,
            -lw * 0.16f,
            labelTitleTextPaint
        )

        // Line 2: Middle Artist & Time Duration
        val displayArtistTime = if (nowPlayingTime.isNotBlank()) {
            if (nowPlayingArtist.isNotBlank()) "$nowPlayingArtist • $nowPlayingTime" else nowPlayingTime
        } else if (nowPlayingArtist.isNotBlank()) {
            nowPlayingArtist
        } else {
            "STUDIO MASTER • STEREO"
        }
        canvas.drawText(
            displayArtistTime,
            0f,
            lw * 0.07f,
            labelSubTextPaint
        )

        // Line 3: Below MP3 bits kHz
        val displaySpecs = if (nowPlayingSpecs.isNotBlank()) {
            nowPlayingSpecs
        } else {
            "MP3 • 320 kbps • 44.1 kHz"
        }
        canvas.drawText(
            displaySpecs,
            0f,
            lw * 0.31f,
            labelSpecsTextPaint
        )
        canvas.restore()

        // =========================================================================
        // 3. CLEAR ACRYLIC WINDOW (VERTICAL CENTER COLUMN)
        // =========================================================================
        val winRadius = windowRect.width() * 0.08f
        // Recessed outer frame well
        canvas.drawRoundRect(windowWellRect, winRadius + 1f, winRadius + 1f, windowWellPaint)
        // Acrylic window glass
        canvas.drawRoundRect(windowRect, winRadius, winRadius, windowPaint)

        // Molded Tape Volume Ruler Graduations (Ticks along window slit edges)
        val tickLen = 4.5f
        for (t in 0 until 5) {
            val ty = rulerTickYs[t]
            if (ty < bridgeRect.top - 4f || ty > bridgeRect.bottom + 4f) {
                canvas.drawLine(windowRect.left + 2f, ty, windowRect.left + 2f + tickLen, ty, rulerTickPaint)
                canvas.drawLine(windowRect.right - 2f - tickLen, ty, windowRect.right - 2f, ty, rulerTickPaint)
            }
        }
        // Center "50" mark between counter and bottom hub
        val midGapY = (bridgeRect.bottom + (bottomHubY - hubRadius)) * 0.5f
        canvas.drawLine(hubCenterX - 14f, midGapY, hubCenterX - 5f, midGapY, rulerTickPaint)
        canvas.drawText("50", hubCenterX, midGapY + (rulerTextPaint.textSize * 0.35f), rulerTextPaint)
        canvas.drawLine(hubCenterX + 5f, midGapY, hubCenterX + 14f, midGapY, rulerTickPaint)

        // =========================================================================
        // 4. KINETIC TAPE PACKS & AUTHENTIC VERTICAL TAPE RIBBON PATH
        // =========================================================================
        val rMinSq = minHubRadius * minHubRadius
        val rMaxSq = maxTapeRadius * maxTapeRadius
        val diffSq = rMaxSq - rMinSq

        val topRadius = sqrt((1.0f - progressFraction) * diffSq + rMinSq)
        val bottomRadius = sqrt(progressFraction * diffSq + rMinSq)

        // Draw Top & Bottom Wound Magnetic Tape Packs (Cleanly enclosed within window frame)
        canvas.save()
        canvas.clipRect(windowRect)
        canvas.drawCircle(hubCenterX, topHubY, topRadius, tapePackPaint)
        canvas.drawCircle(hubCenterX, bottomHubY, bottomRadius, tapePackPaint)

        // Concentric Winding Micro-Grooves on Tape Packs
        if (topRadius > minHubRadius + 4f) {
            val deltaT = topRadius - minHubRadius
            canvas.drawCircle(hubCenterX, topHubY, minHubRadius + deltaT * 0.25f, tapeMicroGroovePaint)
            canvas.drawCircle(hubCenterX, topHubY, minHubRadius + deltaT * 0.50f, tapeMicroGroovePaint)
            canvas.drawCircle(hubCenterX, topHubY, minHubRadius + deltaT * 0.75f, tapeMicroGroovePaint)
        }
        if (bottomRadius > minHubRadius + 4f) {
            val deltaB = bottomRadius - minHubRadius
            canvas.drawCircle(hubCenterX, bottomHubY, minHubRadius + deltaB * 0.25f, tapeMicroGroovePaint)
            canvas.drawCircle(hubCenterX, bottomHubY, minHubRadius + deltaB * 0.50f, tapeMicroGroovePaint)
            canvas.drawCircle(hubCenterX, bottomHubY, minHubRadius + deltaB * 0.75f, tapeMicroGroovePaint)
        }

        // Rotating Specular Sheen Reflection on wound tape
        drawSpoolSheen(canvas, hubCenterX, topHubY, minHubRadius, topRadius, topRotationDegrees)
        drawSpoolSheen(canvas, hubCenterX, bottomHubY, minHubRadius, bottomRadius, bottomRotationDegrees)
        canvas.restore()

        // Authentic Vertical Mechanical Tape Ribbon Path:
        // 1. Unwinds from top spool outer edge
        // 2. Extends toward upper guide roller on right side
        // 3. Runs vertically straight down through erase head, felt pad, and permalloy head
        // 4. Wraps around lower guide roller on right side
        // 5. Winds into bottom spool outer edge
        tapeLoopPath.reset()
        val tapeRollerX = capstanX + rollerRadius * 0.6f
        tapeLoopPath.moveTo(hubCenterX + topRadius * 0.5f, topHubY - topRadius * 0.85f)
        tapeLoopPath.lineTo(tapeRollerX, topCapstanY - rollerRadius * 0.6f)
        tapeLoopPath.lineTo(tapeRollerX, bottomCapstanY + rollerRadius * 0.6f)
        tapeLoopPath.lineTo(hubCenterX + bottomRadius * 0.5f, bottomHubY + bottomRadius * 0.85f)
        canvas.drawPath(tapeLoopPath, tapeRibbonPaint)

        // =========================================================================
        // 5. HEAD CHAMBER MECHANICS & FELT PRESSURE PAD (RIGHT SIDE)
        // =========================================================================
        // Upper & Lower Tape Guide Rollers & Brass Capstans
        canvas.drawCircle(capstanX, topCapstanY, rollerRadius, rollerPaint)
        canvas.drawCircle(capstanX, bottomCapstanY, rollerRadius, rollerPaint)
        canvas.drawCircle(capstanX, topCapstanY, rollerRadius, rollerRimPaint)
        canvas.drawCircle(capstanX, bottomCapstanY, rollerRadius, rollerRimPaint)

        canvas.drawCircle(capstanX, topCapstanY, capstanRadius, capstanPaint)
        canvas.drawCircle(capstanX, bottomCapstanY, capstanRadius, capstanPaint)
        canvas.drawCircle(capstanX, topCapstanY, capstanRadius * 0.4f, rivetSlotPaint)
        canvas.drawCircle(capstanX, bottomCapstanY, capstanRadius * 0.4f, rivetSlotPaint)

        // Bronze Leaf Spring and Felt Pressure Pad directly behind playback head
        canvas.drawRoundRect(springPadRect, 1.5f, 1.5f, springPadPaint)
        canvas.drawRoundRect(feltPadRect, 1f, 1f, feltPadPaint)

        // Permalloy Playback Head Housing
        canvas.drawRoundRect(headRect, 3f, 3f, headPaint)
        canvas.drawRoundRect(headCoreRect, 2f, 2f, headCorePaint)
        // Center magnetic gap line
        canvas.drawLine(headRect.left, headRect.centerY(), headRect.right, headRect.centerY(), headGapPaint)
        canvas.drawRoundRect(headRect, 3f, 3f, windowBorderPaint)

        // Dual Azimuth Adjustment Screws (above and below head)
        canvas.drawCircle(headScrewX, headScrewTopY, headScrewRadius, headScrewPaint)
        canvas.drawCircle(headScrewX, headScrewBottomY, headScrewRadius, headScrewPaint)
        canvas.drawLine(headScrewX, headScrewTopY - headScrewRadius * 0.6f, headScrewX, headScrewTopY + headScrewRadius * 0.6f, rivetSlotPaint)
        canvas.drawLine(headScrewX, headScrewBottomY - headScrewRadius * 0.6f, headScrewX, headScrewBottomY + headScrewRadius * 0.6f, rivetSlotPaint)

        // Center Spindle Bridge (Between top and bottom spools)
        canvas.drawRoundRect(bridgeRect, 4f, 4f, chassisPaint)
        canvas.drawRoundRect(bridgeRect, 4f, 4f, windowBorderPaint)
        canvas.drawLine(bridgeRect.left + 5f, bridgeRect.centerY(), bridgeRect.right - 5f, bridgeRect.centerY(), windowBorderPaint)

        // =========================================================================
        // 6. ROTATING SPINDLE DRIVE HUBS (TOP & BOTTOM)
        // =========================================================================
        drawHub(canvas, hubCenterX, topHubY, topRotationDegrees)
        drawHub(canvas, hubCenterX, bottomHubY, bottomRotationDegrees)

        // =========================================================================
        // 7. ACRYLIC REFLECTIONS & WINDOW BEVEL
        // =========================================================================
        canvas.drawPath(glarePath1, glarePaint)
        canvas.drawPath(glarePath2, glarePaint2)
        canvas.drawRoundRect(windowRect, winRadius, winRadius, windowBorderPaint)

        // =========================================================================
        // 8. 60 FPS ANIMATION LOOP
        // =========================================================================
        if (isPlaying) {
            val now = System.nanoTime()
            if (lastFrameTimeNanos > 0L) {
                val dtSeconds = (now - lastFrameTimeNanos) / 1_000_000_000.0f
                val baseSpeed = 160.0f
                val topSpeed = baseSpeed * (maxTapeRadius / topRadius)
                val bottomSpeed = baseSpeed * (maxTapeRadius / bottomRadius)

                topRotationDegrees = (topRotationDegrees + topSpeed * dtSeconds) % 360f
                bottomRotationDegrees = (bottomRotationDegrees + bottomSpeed * dtSeconds) % 360f
            }
            lastFrameTimeNanos = now
            postInvalidateOnAnimation()
        } else {
            lastFrameTimeNanos = 0L
        }
    }

    private fun drawSpoolSheen(canvas: Canvas, cx: Float, cy: Float, innerR: Float, outerR: Float, rotationDeg: Float) {
        if (outerR <= innerR + 4f) return
        val rad1 = (rotationDeg * (PI / 180f)).toFloat()
        val rad2 = ((rotationDeg + 180f) * (PI / 180f)).toFloat()

        val cos1 = cos(rad1)
        val sin1 = sin(rad1)
        canvas.drawLine(cx + innerR * cos1, cy + innerR * sin1, cx + outerR * cos1, cy + outerR * sin1, tapeSheenPaint)

        val cos2 = cos(rad2)
        val sin2 = sin(rad2)
        canvas.drawLine(cx + innerR * cos2, cy + innerR * sin2, cx + outerR * cos2, cy + outerR * sin2, tapeSheenPaint)
    }

    private fun drawHub(canvas: Canvas, cx: Float, cy: Float, rotationDeg: Float) {
        // Outer White Delrin Plastic Hub Ring
        canvas.drawCircle(cx, cy, hubRadius, hubBodyPaint)

        // Precision Concentric Hub Groove
        canvas.drawCircle(cx, cy, hubRadius * 0.88f, hubGroovePaint)

        // Colored Leader Tape Clamp Wedge (rotating with hub)
        val clampAngle = (rotationDeg + 45f) * (PI.toFloat() / 180f)
        val clampDist = hubRadius * 0.76f
        val clampX = cx + (clampDist * cos(clampAngle))
        val clampY = cy + (clampDist * sin(clampAngle))
        canvas.drawCircle(clampX, clampY, hubRadius * 0.12f, hubClampPaint)

        // 3 Strobe Flange Cutout Windows (at 0°, 120°, 240°)
        val strobeR = hubRadius * 0.65f
        val strobeHoleR = hubRadius * 0.11f
        for (s in 0 until 3) {
            val sAngle = (rotationDeg + s * 120f) * (PI.toFloat() / 180f)
            val sx = cx + (strobeR * cos(sAngle))
            val sy = cy + (strobeR * sin(sAngle))
            canvas.drawCircle(sx, sy, strobeHoleR, hubStrobeHolePaint)
        }

        // 6-Tooth Star Spindle Teeth (Mechanical drive teeth)
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

    fun setNowPlaying(title: String, artist: String, timeReadout: String = "", audioSpecs: String = "") {
        this.nowPlayingTitle = title.trim()
        this.nowPlayingArtist = artist.trim()
        this.nowPlayingTime = timeReadout.trim()
        this.nowPlayingSpecs = audioSpecs.trim()
        fitLabelText()
        invalidate()
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
