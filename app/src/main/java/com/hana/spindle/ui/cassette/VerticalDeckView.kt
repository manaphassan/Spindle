package com.hana.spindle.ui.cassette

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.hana.spindle.theme.CassetteTheme
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Hardware-accelerated Custom View rendering the flagship Retro Vertical Tape Recorder Deck.
 * Faithfully replicates the classic industrial cassette recorder interface:
 *
 * 1. Dark matte industrial chassis with 4 corner Torx/hex screw wells.
 * 2. Vertical smoky acrylic cassette tape with kinetic differential dual reels (SpindleKinematics),
 *    silver multi-tooth gear hubs, center black axle caps, and signature curved orange calibration notches.
 * 3. Top-left 7-segment digital LED clock displaying the real-time device system time.
 * 4. Left column track details (Song Title with marquee scrolling, and Artist • Duration) positioned
 *    between the clock and the bottom Spindle branding.
 * 5. Bottom-left retro SPINDLE typography (replacing MIUI RECORDER).
 * 6. 12-LED horizontal song progress bar directly above the bottom keys with interactive touch-seeking.
 * 7. 4 tactile beveled mechanical buttons: REW, FWD, PLAY (with illuminated jewel indicator), and EJECT.
 *
 * Designed with zero object allocations in onDraw() for continuous 60fps rendering on low-RAM DAPs.
 */
class VerticalDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val kinematics = SpindleKinematics()

    // State
    var theme: CassetteTheme = CassetteTheme.VERTICAL_STUDIO_DECK
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) startRotation() else stopRotation()
            }
        }

    var progress: Float = 0.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var trackTitle: String = "No Track Loaded"
        set(value) {
            field = value
            marqueeOffset = 0f
            invalidate()
        }

    var artistName: String = "Spindle Audio Player"
        set(value) {
            field = value
            invalidate()
        }

    var durationMs: Long = 0L
        set(value) {
            field = value
            invalidate()
        }

    var currentTimeMs: Long = 0L
        set(value) {
            field = value
            invalidate()
        }

    // Callbacks
    var onPlayClicked: (() -> Unit)? = null
    var onPrevClicked: (() -> Unit)? = null
    var onNextClicked: (() -> Unit)? = null
    var onEjectClicked: (() -> Unit)? = null
    var onSeek: ((Float) -> Unit)? = null

    // Touch & interaction tracking
    private var isDraggingProgress = false
    private var pressedButtonIndex = -1 // 0: REW, 1: FWD, 2: PLAY, 3: EJECT

    // Kinetic Animation State
    private var rotationAnimator: ValueAnimator? = null
    private var topReelAngle = 0f
    private var bottomReelAngle = 0f
    private var marqueeOffset = 0f
    private var lastMarqueeTime = 0L

    // Pre-allocated Paints (Zero allocation in onDraw)
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chassisBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwWellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwGroovePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cassetteShellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteInnerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerWindowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerWindowBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val tapeSpoolPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeTexturePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeBridgePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val hubRimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubTeethPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubInnerCapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubCenterPipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orangeNotchPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val clockGhostPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clockLitPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artistTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spindleLogoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spindleSubtextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ledInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledActiveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val buttonBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val playJewelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val playJewelGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pre-allocated Geometries
    private val chassisRect = RectF()
    private val cassetteRect = RectF()
    private val centerWindowRect = RectF()
    private val headCavityRect = RectF()
    private val ledBarRect = RectF()
    private val tempRectF = RectF()
    private val tempSegmentRect = RectF()

    private val btnRewRect = RectF()
    private val btnFwdRect = RectF()
    private val btnPlayRect = RectF()
    private val btnEjectRect = RectF()

    private val topHubCenter = PointF()
    private val bottomHubCenter = PointF()
    private var baseHubDimension = 0f
    private var hubOuterRadius = 0f

    private val calendar = Calendar.getInstance()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updatePaints()
    }

    private fun updatePaints() {
        chassisPaint.apply {
            color = Color.parseColor("#16181B") // Dark matte industrial casing
            style = Paint.Style.FILL
        }
        chassisBevelPaint.apply {
            color = Color.parseColor("#262930")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        screwWellPaint.apply {
            color = Color.parseColor("#0C0D0F")
            style = Paint.Style.FILL
        }
        screwHeadPaint.apply {
            color = Color.parseColor("#2E313A")
            style = Paint.Style.FILL
        }
        screwHighlightPaint.apply {
            color = Color.parseColor("#4B505E")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        screwGroovePaint.apply {
            color = Color.parseColor("#121317")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }

        cassetteShellPaint.apply {
            color = Color.parseColor("#131418") // Smoky acrylic dark cassette body
            style = Paint.Style.FILL
        }
        cassetteBorderPaint.apply {
            color = Color.parseColor("#282B33")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        cassetteInnerShadowPaint.apply {
            color = Color.parseColor("#08090B")
            style = Paint.Style.FILL
        }
        cassetteGuidePaint.apply {
            color = Color.parseColor("#22242B")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        centerWindowPaint.apply {
            color = Color.parseColor("#0C0D0F")
            style = Paint.Style.FILL
        }
        centerWindowBorderPaint.apply {
            color = Color.parseColor("#1F2128")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        tapeSpoolPaint.apply {
            color = Color.parseColor("#38231B") // Magnetic brown oxide tape pack
            style = Paint.Style.FILL
        }
        tapeTexturePaint.apply {
            color = Color.parseColor("#261712")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        tapeBridgePaint.apply {
            color = Color.parseColor("#321F18")
            style = Paint.Style.FILL
        }

        hubRimPaint.apply {
            color = Color.parseColor("#CBD5E1") // Silver/chrome metallic hub teeth
            style = Paint.Style.FILL
        }
        hubTeethPaint.apply {
            color = Color.parseColor("#94A3B8")
            style = Paint.Style.FILL
        }
        hubInnerCapPaint.apply {
            color = Color.parseColor("#1E2127")
            style = Paint.Style.FILL
        }
        hubCenterPipPaint.apply {
            color = Color.parseColor("#0C0D0F")
            style = Paint.Style.FILL
        }
        orangeNotchPaint.apply {
            color = Color.parseColor("#FF5722") // Signature curved orange calibration notch
            style = Paint.Style.FILL
        }

        clockGhostPaint.apply {
            color = Color.parseColor("#1C1E24") // Faint unlit 88:88 background segments
            style = Paint.Style.FILL
        }
        clockLitPaint.apply {
            color = Color.parseColor("#F8FAFC") // Bright ice-white illuminated segments
            style = Paint.Style.FILL
        }

        titleTextPaint.apply {
            color = Color.parseColor("#F1F5F9")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        artistTextPaint.apply {
            color = Color.parseColor("#94A3B8")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        spindleLogoPaint.apply {
            color = Color.parseColor("#8E929E")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.18f
        }
        spindleSubtextPaint.apply {
            color = Color.parseColor("#5A5E6B")
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            letterSpacing = 0.08f
        }

        ledInactivePaint.apply {
            color = Color.parseColor("#0F1013")
            style = Paint.Style.FILL
        }
        ledActivePaint.apply {
            color = Color.parseColor("#FF5722") // Radiant warm orange/amber progress LED
            style = Paint.Style.FILL
        }
        ledActiveGlowPaint.apply {
            color = Color.argb(80, 255, 87, 34)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        ledBorderPaint.apply {
            color = Color.parseColor("#22252D")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        buttonBasePaint.apply {
            color = Color.parseColor("#21232A")
            style = Paint.Style.FILL
        }
        buttonPressedPaint.apply {
            color = Color.parseColor("#141519")
            style = Paint.Style.FILL
        }
        buttonHighlightPaint.apply {
            color = Color.parseColor("#343844")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        buttonBorderPaint.apply {
            color = Color.parseColor("#15171C")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        buttonIconPaint.apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        buttonLabelPaint.apply {
            color = Color.parseColor("#8E929E")
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        playJewelPaint.apply {
            color = Color.parseColor("#EF4444") // Red illuminated recording/play diode
            style = Paint.Style.FILL
        }
        playJewelGlowPaint.apply {
            color = Color.argb(120, 239, 68, 68)
            style = Paint.Style.FILL
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        chassisRect.set(0f, 0f, w.toFloat(), h.toFloat())

        // 1. Cassette Shell Body: Fills from 2% to 77.5% height
        val cassetteMarginH = w * 0.035f
        cassetteRect.set(
            cassetteMarginH,
            h * 0.020f,
            w - cassetteMarginH,
            h * 0.775f
        )

        // Center window connecting top and bottom reels (shifted right to leave ample room for left column text)
        val reelCenterX = cassetteRect.left + cassetteRect.width() * 0.55f
        val winW = cassetteRect.width() * 0.39f
        val winH = cassetteRect.height() * 0.74f
        centerWindowRect.set(
            reelCenterX - winW * 0.5f,
            cassetteRect.centerY() - winH * 0.5f,
            reelCenterX + winW * 0.5f,
            cassetteRect.centerY() + winH * 0.5f
        )

        // Head opening on the right side of cassette
        val headW = w * 0.06f
        val headH = h * 0.18f
        headCavityRect.set(
            cassetteRect.right - headW,
            cassetteRect.centerY() - headH * 0.5f,
            cassetteRect.right,
            cassetteRect.centerY() + headH * 0.5f
        )

        // Reels Center Coordinates (Vertical Cassette)
        topHubCenter.set(reelCenterX, cassetteRect.top + cassetteRect.height() * 0.27f)
        bottomHubCenter.set(reelCenterX, cassetteRect.top + cassetteRect.height() * 0.63f)

        baseHubDimension = cassetteRect.width() * 0.30f
        hubOuterRadius = baseHubDimension * 0.40f

        // Text Sizing
        titleTextPaint.textSize = w * 0.040f
        artistTextPaint.textSize = w * 0.029f
        spindleLogoPaint.textSize = w * 0.044f
        spindleSubtextPaint.textSize = w * 0.022f

        // 2. 12-LED Progress Bar: 79.5% to 82.2% height
        val ledMarginH = w * 0.10f
        ledBarRect.set(
            ledMarginH,
            h * 0.795f,
            w - ledMarginH,
            h * 0.822f
        )

        // 3. Bottom 4 Mechanical Keys: 84.5% to 96.5% height
        val btnMarginH = w * 0.045f
        val btnTop = h * 0.845f
        val btnBottom = h * 0.965f
        val btnGap = w * 0.016f
        val totalBtnW = (w - (btnMarginH * 2f)) - (btnGap * 3f)
        val btnW = totalBtnW / 4f

        btnRewRect.set(btnMarginH, btnTop, btnMarginH + btnW, btnBottom)
        btnFwdRect.set(btnRewRect.right + btnGap, btnTop, btnRewRect.right + btnGap + btnW, btnBottom)
        btnPlayRect.set(btnFwdRect.right + btnGap, btnTop, btnFwdRect.right + btnGap + btnW, btnBottom)
        btnEjectRect.set(btnPlayRect.right + btnGap, btnTop, btnPlayRect.right + btnGap + btnW, btnBottom)

        buttonIconPaint.textSize = btnW * 0.32f
        buttonLabelPaint.textSize = btnW * 0.20f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // 1. Draw Outer Chassis & 4 Corner Torx Screws
        drawChassis(canvas, w, h)

        // 2. Draw Vertical Smoky Cassette Tape & Internal Guides
        drawCassetteShell(canvas)

        // 3. Draw Kinetic Rotating Reels with Orange Calibration Notches
        drawKineticReels(canvas)

        // 4. Draw Real-Time 7-Segment Digital Device Clock (Top-Left)
        drawDigitalClock(canvas)

        // 5. Draw Left Column Track Info (Title & Artist • Duration)
        drawTrackInfo(canvas)

        // 6. Draw Bottom-Left SPINDLE Typography (Replaces MIUI RECORDER)
        drawSpindleBranding(canvas)

        // 7. Draw 12-LED Song Playback Progress Bar
        drawLedProgressBar(canvas)

        // 8. Draw 4 Mechanical Tactile Buttons (REW, FWD, PLAY, EJECT)
        drawBottomButtons(canvas)
    }

    /**
     * Draws the dark matte industrial chassis faceplate and 4 corner Torx screws.
     */
    private fun drawChassis(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(chassisRect, chassisPaint)
        canvas.drawRect(chassisRect, chassisBevelPaint)

        // 4 Large Chassis Corner Screws
        val screwR = w * 0.024f
        val marginX = w * 0.075f
        val marginYTop = h * 0.040f
        val marginYBottom = h * 0.760f

        drawTorxScrew(canvas, marginX, marginYTop, screwR, 25f)
        drawTorxScrew(canvas, w - marginX, marginYTop, screwR, 70f)
        drawTorxScrew(canvas, marginX, marginYBottom, screwR, 15f)
        drawTorxScrew(canvas, w - marginX, marginYBottom, screwR, 45f)
    }

    /**
     * Draws an authentic mechanical Torx screw with metallic bevel and groove.
     */
    private fun drawTorxScrew(canvas: Canvas, cx: Float, cy: Float, r: Float, angleDeg: Float) {
        // Recessed well
        canvas.drawCircle(cx, cy, r * 1.25f, screwWellPaint)
        // Screw head
        canvas.drawCircle(cx, cy, r, screwHeadPaint)
        // Top highlight
        tempRectF.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(tempRectF, 200f, 120f, false, screwHighlightPaint)

        // Cross / Torx groove
        canvas.save()
        canvas.rotate(angleDeg, cx, cy)
        canvas.drawLine(cx - r * 0.55f, cy, cx + r * 0.55f, cy, screwGroovePaint)
        canvas.drawLine(cx, cy - r * 0.55f, cx, cy + r * 0.55f, screwGroovePaint)
        canvas.restore()
    }

    /**
     * Draws the vertical smoky acrylic cassette shell, bevels, internal guides, and corner screws.
     */
    private fun drawCassetteShell(canvas: Canvas) {
        val cr = 18f
        // Recessed shadow behind cassette
        canvas.drawRoundRect(cassetteRect, cr, cr, cassetteInnerShadowPaint)
        // Cassette smoky acrylic body
        canvas.drawRoundRect(cassetteRect, cr, cr, cassetteShellPaint)
        // Beveled metallic border
        canvas.drawRoundRect(cassetteRect, cr, cr, cassetteBorderPaint)

        // 4 Cassette Corner Screws
        val cScrewR = cassetteRect.width() * 0.016f
        val csOffsetX = cassetteRect.width() * 0.055f
        val csOffsetY = cassetteRect.height() * 0.035f

        drawTorxScrew(canvas, cassetteRect.left + csOffsetX, cassetteRect.top + csOffsetY, cScrewR, 40f)
        drawTorxScrew(canvas, cassetteRect.right - csOffsetX, cassetteRect.top + csOffsetY, cScrewR, 80f)
        drawTorxScrew(canvas, cassetteRect.left + csOffsetX, cassetteRect.bottom - csOffsetY, cScrewR, 20f)
        drawTorxScrew(canvas, cassetteRect.right - csOffsetX, cassetteRect.bottom - csOffsetY, cScrewR, 60f)

        // Center right screw next to tape head opening
        drawTorxScrew(canvas, cassetteRect.right - csOffsetX * 1.1f, cassetteRect.centerY(), cScrewR * 0.9f, 30f)

        // Center Tape Window (Connecting top & bottom spools)
        canvas.drawRoundRect(centerWindowRect, 14f, 14f, centerWindowPaint)
        canvas.drawRoundRect(centerWindowRect, 14f, 14f, centerWindowBorderPaint)

        // Right side tape head opening & orange felt pressure pad
        canvas.drawRoundRect(headCavityRect, 6f, 6f, centerWindowPaint)
        tempRectF.set(
            headCavityRect.left + 4f,
            headCavityRect.centerY() - 12f,
            headCavityRect.left + 12f,
            headCavityRect.centerY() + 12f
        )
        canvas.drawRoundRect(tempRectF, 2f, 2f, orangeNotchPaint)

        // Internal Guide Roller Circles (Top right and bottom right)
        canvas.drawCircle(cassetteRect.right - csOffsetX * 1.5f, cassetteRect.top + csOffsetY * 2.2f, cScrewR * 1.8f, cassetteGuidePaint)
        canvas.drawCircle(cassetteRect.right - csOffsetX * 1.5f, cassetteRect.bottom - csOffsetY * 2.2f, cScrewR * 1.8f, cassetteGuidePaint)
    }

    /**
     * Draws the differential dual reels with magnetic tape packs and spinning orange calibration notches.
     */
    private fun drawKineticReels(canvas: Canvas) {
        val spoolState = kinematics.calculate(progress, baseHubDimension)
        val rTopTape = spoolState.leftRadius
        val rBottomTape = spoolState.rightRadius

        // Vertical tape bridge between spools inside the center window
        val bridgeWidth = hubOuterRadius * 0.4f
        tempRectF.set(
            centerWindowRect.centerX() - bridgeWidth * 0.5f,
            topHubCenter.y,
            centerWindowRect.centerX() + bridgeWidth * 0.5f,
            bottomHubCenter.y
        )
        canvas.drawRect(tempRectF, tapeBridgePaint)

        // Top Spool Tape Pack (Supply)
        canvas.drawCircle(topHubCenter.x, topHubCenter.y, rTopTape, tapeSpoolPaint)
        canvas.drawCircle(topHubCenter.x, topHubCenter.y, rTopTape * 0.92f, tapeTexturePaint)
        canvas.drawCircle(topHubCenter.x, topHubCenter.y, rTopTape * 0.84f, tapeTexturePaint)

        // Bottom Spool Tape Pack (Take-up)
        canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, rBottomTape, tapeSpoolPaint)
        canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, rBottomTape * 0.92f, tapeTexturePaint)
        canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, rBottomTape * 0.84f, tapeTexturePaint)

        // Top Reel Mechanism & Signature Orange Calibration Notch
        drawHubMechanism(canvas, topHubCenter.x, topHubCenter.y, hubOuterRadius, topReelAngle)

        // Bottom Reel Mechanism & Signature Orange Calibration Notch
        drawHubMechanism(canvas, bottomHubCenter.x, bottomHubCenter.y, hubOuterRadius, bottomReelAngle)
    }

    /**
     * Draws a cassette spindle hub with metallic teeth, inner axle cap, and spinning orange marker notch.
     */
    private fun drawHubMechanism(canvas: Canvas, cx: Float, cy: Float, radius: Float, rotationAngle: Float) {
        // Outer silver gear ring
        canvas.drawCircle(cx, cy, radius, hubRimPaint)

        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)

        // 6 Gear Teeth splines
        val toothW = radius * 0.18f
        val toothH = radius * 0.26f
        for (i in 0 until 6) {
            tempRectF.set(cx - toothW * 0.5f, cy - radius, cx + toothW * 0.5f, cy - radius + toothH)
            canvas.drawRoundRect(tempRectF, 2f, 2f, hubTeethPaint)
            canvas.rotate(60f, cx, cy)
        }

        // Inner black hub core
        val innerRadius = radius * 0.70f
        canvas.drawCircle(cx, cy, innerRadius, hubInnerCapPaint)

        // Signature Curved Orange Calibration Notch (Covers 55 degrees on outer rim of hub core)
        tempRectF.set(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius)
        canvas.drawArc(tempRectF, 0f, 55f, true, orangeNotchPaint)

        // Center axle well and chrome spindle pip
        canvas.drawCircle(cx, cy, innerRadius * 0.44f, hubCenterPipPaint)
        canvas.drawCircle(cx, cy, innerRadius * 0.20f, hubRimPaint)

        canvas.restore()
    }

    /**
     * Draws the real-time 7-segment digital device clock (top-left).
     */
    private fun drawDigitalClock(canvas: Canvas) {
        calendar.timeInMillis = System.currentTimeMillis()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val clockLeft = cassetteRect.left + cassetteRect.width() * 0.08f
        val clockTop = cassetteRect.top + cassetteRect.height() * 0.052f
        val digitW = cassetteRect.width() * 0.058f
        val digitH = cassetteRect.height() * 0.048f
        val digitGap = digitW * 0.20f

        val h1 = hour / 10
        val h2 = hour % 10
        val m1 = minute / 10
        val m2 = minute % 10

        var currentX = clockLeft

        // Digit 1 (Hour tens)
        draw7SegmentDigit(canvas, currentX, clockTop, digitW, digitH, h1)
        currentX += digitW + digitGap

        // Digit 2 (Hour units)
        draw7SegmentDigit(canvas, currentX, clockTop, digitW, digitH, h2)
        currentX += digitW + digitGap * 0.8f

        // Colon ':'
        drawColon(canvas, currentX, clockTop, digitW * 0.35f, digitH)
        currentX += digitW * 0.35f + digitGap * 0.8f

        // Digit 3 (Minute tens)
        draw7SegmentDigit(canvas, currentX, clockTop, digitW, digitH, m1)
        currentX += digitW + digitGap

        // Digit 4 (Minute units)
        draw7SegmentDigit(canvas, currentX, clockTop, digitW, digitH, m2)
    }

    private fun draw7SegmentDigit(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, digit: Int) {
        val t = w * 0.22f // Segment thickness
        val midY = y + h * 0.5f

        // Segments: 0:a(top), 1:b(TR), 2:c(BR), 3:d(bottom), 4:e(BL), 5:f(TL), 6:g(mid)
        val mask = when (digit) {
            0 -> 0b0111111
            1 -> 0b0000110
            2 -> 0b1011011
            3 -> 0b1001111
            4 -> 0b1100110
            5 -> 0b1101101
            6 -> 0b1111101
            7 -> 0b0000111
            8 -> 0b1111111
            9 -> 0b1101111
            else -> 0b0000000
        }

        // Draw all 7 ghost segments first
        drawSegment(canvas, x + t * 0.7f, y, x + w - t * 0.7f, y + t, clockGhostPaint) // a
        drawSegment(canvas, x + w - t, y + t * 0.7f, x + w, midY - t * 0.3f, clockGhostPaint) // b
        drawSegment(canvas, x + w - t, midY + t * 0.3f, x + w, y + h - t * 0.7f, clockGhostPaint) // c
        drawSegment(canvas, x + t * 0.7f, y + h - t, x + w - t * 0.7f, y + h, clockGhostPaint) // d
        drawSegment(canvas, x, midY + t * 0.3f, x + t, y + h - t * 0.7f, clockGhostPaint) // e
        drawSegment(canvas, x, y + t * 0.7f, x + t, midY - t * 0.3f, clockGhostPaint) // f
        drawSegment(canvas, x + t * 0.7f, midY - t * 0.5f, x + w - t * 0.7f, midY + t * 0.5f, clockGhostPaint) // g

        // Draw active lit segments
        if ((mask and (1 shl 0)) != 0) drawSegment(canvas, x + t * 0.7f, y, x + w - t * 0.7f, y + t, clockLitPaint)
        if ((mask and (1 shl 1)) != 0) drawSegment(canvas, x + w - t, y + t * 0.7f, x + w, midY - t * 0.3f, clockLitPaint)
        if ((mask and (1 shl 2)) != 0) drawSegment(canvas, x + w - t, midY + t * 0.3f, x + w, y + h - t * 0.7f, clockLitPaint)
        if ((mask and (1 shl 3)) != 0) drawSegment(canvas, x + t * 0.7f, y + h - t, x + w - t * 0.7f, y + h, clockLitPaint)
        if ((mask and (1 shl 4)) != 0) drawSegment(canvas, x, midY + t * 0.3f, x + t, y + h - t * 0.7f, clockLitPaint)
        if ((mask and (1 shl 5)) != 0) drawSegment(canvas, x, y + t * 0.7f, x + t, midY - t * 0.3f, clockLitPaint)
        if ((mask and (1 shl 6)) != 0) drawSegment(canvas, x + t * 0.7f, midY - t * 0.5f, x + w - t * 0.7f, midY + t * 0.5f, clockLitPaint)
    }

    private fun drawSegment(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, paint: Paint) {
        tempSegmentRect.set(l, t, r, b)
        canvas.drawRoundRect(tempSegmentRect, 2f, 2f, paint)
    }

    private fun drawColon(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val dotR = w * 0.40f
        val cx = x + w * 0.5f
        val dot1Y = y + h * 0.33f
        val dot2Y = y + h * 0.67f

        // Lit colon dots
        canvas.drawCircle(cx, dot1Y, dotR, clockLitPaint)
        canvas.drawCircle(cx, dot2Y, dotR, clockLitPaint)
    }

    /**
     * Draws the track title and artist • duration in the left column space.
     */
    private fun drawTrackInfo(canvas: Canvas) {
        val leftX = cassetteRect.left + cassetteRect.width() * 0.08f
        val maxAvailableW = (centerWindowRect.left - leftX) - 12f
        val centerY = cassetteRect.top + cassetteRect.height() * 0.44f

        val titleY = centerY - 6f
        val artistY = centerY + (titleTextPaint.textSize * 0.95f)

        // Clip to column width so text never overlaps reels
        canvas.save()
        tempRectF.set(leftX, titleY - titleTextPaint.textSize * 1.2f, leftX + maxAvailableW, artistY + artistTextPaint.textSize * 1.2f)
        canvas.clipRect(tempRectF)

        val measuredTitleW = titleTextPaint.measureText(trackTitle)
        if (measuredTitleW > maxAvailableW && isPlaying) {
            // Smooth marquee scroll
            val now = SystemClock.uptimeMillis()
            if (lastMarqueeTime != 0L) {
                val dt = (now - lastMarqueeTime) / 1000f
                marqueeOffset += dt * 35f
                val totalScrollW = measuredTitleW + 40f
                if (marqueeOffset > totalScrollW) {
                    marqueeOffset = 0f
                }
            }
            lastMarqueeTime = now
            canvas.drawText(trackTitle, leftX - marqueeOffset, titleY, titleTextPaint)
            canvas.drawText(trackTitle, leftX - marqueeOffset + measuredTitleW + 40f, titleY, titleTextPaint)
        } else {
            canvas.drawText(trackTitle, leftX, titleY, titleTextPaint)
        }

        // Subtitle: Artist • Duration
        val durStr = formatTime(durationMs)
        val subtitle = "$artistName • $durStr"
        val measuredSubW = artistTextPaint.measureText(subtitle)
        if (measuredSubW > maxAvailableW) {
            val budgetForArtist = maxAvailableW - artistTextPaint.measureText("… • $durStr")
            var trimmedArtist = artistName
            while (trimmedArtist.isNotEmpty() && artistTextPaint.measureText(trimmedArtist) > budgetForArtist) {
                trimmedArtist = trimmedArtist.dropLast(1)
            }
            canvas.drawText("${trimmedArtist.trimEnd()}… • $durStr", leftX, artistY, artistTextPaint)
        } else {
            canvas.drawText(subtitle, leftX, artistY, artistTextPaint)
        }

        canvas.restore()
    }

    /**
     * Draws the SPINDLE logo at the bottom left (replaces MIUI RECORDER).
     */
    private fun drawSpindleBranding(canvas: Canvas) {
        val leftX = cassetteRect.left + cassetteRect.width() * 0.08f
        val brandY = cassetteRect.bottom - cassetteRect.height() * 0.090f

        canvas.drawText("SPINDLE", leftX, brandY, spindleLogoPaint)
        canvas.drawText("AUDIO RECORDER", leftX, brandY + (spindleLogoPaint.textSize * 0.85f), spindleSubtextPaint)
    }

    /**
     * Draws the 12-segment horizontal LED song progress bar directly above the 4 keys.
     */
    private fun drawLedProgressBar(canvas: Canvas) {
        val count = 12
        val totalW = ledBarRect.width()
        val gap = 6f
        val segW = (totalW - (gap * (count - 1))) / count
        val litThreshold = (progress * count).toInt().coerceIn(0, count)

        for (i in 0 until count) {
            val segLeft = ledBarRect.left + (i * (segW + gap))
            val segRight = segLeft + segW
            tempRectF.set(segLeft, ledBarRect.top, segRight, ledBarRect.bottom)

            // Draw recessed slot background
            canvas.drawRoundRect(tempRectF, 3f, 3f, ledInactivePaint)
            canvas.drawRoundRect(tempRectF, 3f, 3f, ledBorderPaint)

            // Draw glowing lit segment
            if (i < litThreshold || (i == 0 && progress > 0f && isPlaying)) {
                canvas.drawRoundRect(tempRectF, 3f, 3f, ledActivePaint)
                canvas.drawRoundRect(tempRectF, 3f, 3f, ledActiveGlowPaint)
            }
        }
    }

    /**
     * Draws the 4 tactile bottom buttons: REW, FWD, PLAY, and EJECT.
     */
    private fun drawBottomButtons(canvas: Canvas) {
        drawKeyButton(canvas, btnRewRect, 0, "|◀◀", "REW")
        drawKeyButton(canvas, btnFwdRect, 1, "▶▶|", "FWD")
        drawPlayKeyButton(canvas, btnPlayRect, 2)
        drawKeyButton(canvas, btnEjectRect, 3, "⏏", "EJECT")
    }

    private fun drawKeyButton(canvas: Canvas, rect: RectF, index: Int, symbol: String, label: String) {
        val isPressed = pressedButtonIndex == index
        val r = 10f

        canvas.drawRoundRect(rect, r, r, if (isPressed) buttonPressedPaint else buttonBasePaint)
        canvas.drawRoundRect(rect, r, r, buttonBorderPaint)
        if (!isPressed) {
            canvas.drawRoundRect(rect, r, r, buttonHighlightPaint)
        }

        val offsetY = if (isPressed) 3f else 0f
        val symbolY = rect.centerY() - 2f + offsetY
        val labelY = rect.bottom - (rect.height() * 0.18f) + offsetY

        canvas.drawText(symbol, rect.centerX(), symbolY, buttonIconPaint)
        canvas.drawText(label, rect.centerX(), labelY, buttonLabelPaint)
    }

    private fun drawPlayKeyButton(canvas: Canvas, rect: RectF, index: Int) {
        val isPressed = pressedButtonIndex == index
        val r = 10f

        canvas.drawRoundRect(rect, r, r, if (isPressed) buttonPressedPaint else buttonBasePaint)
        canvas.drawRoundRect(rect, r, r, buttonBorderPaint)
        if (!isPressed) {
            canvas.drawRoundRect(rect, r, r, buttonHighlightPaint)
        }

        val offsetY = if (isPressed) 3f else 0f
        val jewelY = rect.centerY() - 4f + offsetY
        val labelY = rect.bottom - (rect.height() * 0.18f) + offsetY

        if (isPlaying) {
            // Active Red/Orange Illuminated Recording Jewel Dot
            val jewelR = rect.width() * 0.14f
            canvas.drawCircle(rect.centerX(), jewelY, jewelR * 1.5f, playJewelGlowPaint)
            canvas.drawCircle(rect.centerX(), jewelY, jewelR, playJewelPaint)
            canvas.drawText("❚❚ PAUSE", rect.centerX(), labelY, buttonLabelPaint)
        } else {
            // Inactive Play Triangle
            canvas.drawText("▶", rect.centerX(), jewelY + 6f, buttonIconPaint)
            canvas.drawText("PLAY", rect.centerX(), labelY, buttonLabelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Check 12-LED progress bar seek
                if (y >= ledBarRect.top - 25f && y <= ledBarRect.bottom + 25f &&
                    x >= ledBarRect.left - 10f && x <= ledBarRect.right + 10f) {
                    isDraggingProgress = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateProgressFromTouch(x)
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                }

                // Check 4 bottom buttons
                when {
                    btnRewRect.contains(x, y) -> pressedButtonIndex = 0
                    btnFwdRect.contains(x, y) -> pressedButtonIndex = 1
                    btnPlayRect.contains(x, y) -> pressedButtonIndex = 2
                    btnEjectRect.contains(x, y) -> pressedButtonIndex = 3
                }

                if (pressedButtonIndex != -1) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDraggingProgress) {
                    updateProgressFromTouch(x)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDraggingProgress) {
                    isDraggingProgress = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    updateProgressFromTouch(x)
                    return true
                }

                val clickedIndex = pressedButtonIndex
                pressedButtonIndex = -1
                invalidate()

                if (clickedIndex != -1) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    when (clickedIndex) {
                        0 -> if (btnRewRect.contains(x, y)) onPrevClicked?.invoke()
                        1 -> if (btnFwdRect.contains(x, y)) onNextClicked?.invoke()
                        2 -> if (btnPlayRect.contains(x, y)) onPlayClicked?.invoke()
                        3 -> if (btnEjectRect.contains(x, y)) onEjectClicked?.invoke()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isDraggingProgress = false
                parent?.requestDisallowInterceptTouchEvent(false)
                pressedButtonIndex = -1
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgressFromTouch(x: Float) {
        val newProgress = ((x - ledBarRect.left) / ledBarRect.width()).coerceIn(0f, 1f)
        progress = newProgress
        onSeek?.invoke(newProgress)
    }

    private fun startRotation() {
        if (rotationAnimator?.isRunning == true) return
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val spoolState = kinematics.calculate(progress, baseHubDimension)
                topReelAngle = (topReelAngle + 4.5f * spoolState.leftAngularSpeed) % 360f
                bottomReelAngle = (bottomReelAngle + 4.5f * spoolState.rightAngularSpeed) % 360f
                invalidate()
            }
            start()
        }
    }

    private fun stopRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        lastMarqueeTime = 0L
        invalidate()
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying) startRotation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRotation()
    }
}
