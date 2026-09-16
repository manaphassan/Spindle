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
 * Faithfully replicates the classic industrial cassette recorder interface from the reference design:
 *
 * 1. Dark matte industrial chassis with 4 corner Torx/hex screw wells.
 * 2. Deep recessed cassette slot holding the cassette shell.
 * 3. Clear transparent acrylic window panel exposing the spooled brown magnetic tape pack,
 *    silver multi-tooth gear hubs, center black axle caps, and spinning curved orange calibration notches.
 * 4. Top-left 7-segment digital LED clock displaying actual real-time device system time,
 *    oriented vertically (-90° rotation) reading from bottom to top.
 * 5. Left column track details (Song Title with marquee scrolling, and Artist • Duration)
 *    oriented vertically (-90° rotation) running along the cassette spine.
 * 6. Bottom-left retro SPINDLE branding (replacing MIUI RECORDER), oriented vertically (-90° rotation).
 * 7. 12-LED horizontal song progress bar directly above the bottom keys with interactive touch-seeking.
 * 8. 4 tactile beveled mechanical buttons: REW, FWD, PLAY (mutted/depressed when playing, no red circle), and EJECT.
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
    private val slotBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val screwWellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwGroovePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cassetteShellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteCutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val windowPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowPanelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowGlassHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    private val buttonMutedIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonMutedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pre-allocated Geometries
    private val chassisRect = RectF()
    private val cassetteSlotRect = RectF()
    private val cassetteRect = RectF()
    private val transparentPanelRect = RectF()
    private val centerWindowRect = RectF()
    private val headCavityRect = RectF()
    private val ledBarRect = RectF()
    private val tempRectF = RectF()
    private val tempSegmentRect = RectF()
    private val trapPath = Path()

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
            color = Color.parseColor("#17181C") // Industrial dark matte faceplate
            style = Paint.Style.FILL
        }
        chassisBevelPaint.apply {
            color = Color.parseColor("#282B33")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        slotBevelPaint.apply {
            color = Color.parseColor("#22252C")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        slotShadowPaint.apply {
            color = Color.parseColor("#090A0D") // Deep recessed cassette slot cavity
            style = Paint.Style.FILL
        }

        screwWellPaint.apply {
            color = Color.parseColor("#0B0C0F")
            style = Paint.Style.FILL
        }
        screwHeadPaint.apply {
            color = Color.parseColor("#2B2E37")
            style = Paint.Style.FILL
        }
        screwHighlightPaint.apply {
            color = Color.parseColor("#4A4F5D")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        screwGroovePaint.apply {
            color = Color.parseColor("#101115")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }

        cassetteShellPaint.apply {
            color = Color.parseColor("#15171C") // Dark smoky acrylic cassette shell body
            style = Paint.Style.FILL
        }
        cassetteBorderPaint.apply {
            color = Color.parseColor("#2A2D36")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        cassetteCutoutPaint.apply {
            color = Color.parseColor("#0B0C0F")
            style = Paint.Style.FILL
        }
        cassetteGuidePaint.apply {
            color = Color.parseColor("#20232B")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        // Large transparent acrylic window panel
        windowPanelPaint.apply {
            color = Color.parseColor("#101115") // Transparent panel cavity showing hubs & reels
            style = Paint.Style.FILL
        }
        windowPanelBorderPaint.apply {
            color = Color.parseColor("#252831")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        windowGlassHighlightPaint.apply {
            color = Color.argb(20, 255, 255, 255)
            style = Paint.Style.FILL
        }

        centerWindowPaint.apply {
            color = Color.parseColor("#08090C")
            style = Paint.Style.FILL
        }
        centerWindowBorderPaint.apply {
            color = Color.parseColor("#1A1C22")
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
            color = Color.parseColor("#CCD4DF") // Silver/chrome metallic hub teeth
            style = Paint.Style.FILL
        }
        hubTeethPaint.apply {
            color = Color.parseColor("#8E97A6")
            style = Paint.Style.FILL
        }
        hubInnerCapPaint.apply {
            color = Color.parseColor("#1A1C22")
            style = Paint.Style.FILL
        }
        hubCenterPipPaint.apply {
            color = Color.parseColor("#0A0B0E")
            style = Paint.Style.FILL
        }
        orangeNotchPaint.apply {
            color = Color.parseColor("#FF5722") // Signature curved orange calibration notch
            style = Paint.Style.FILL
        }

        clockGhostPaint.apply {
            color = Color.parseColor("#1C1E24") // Faint unlit ghost 88:88 segments
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
            color = Color.parseColor("#8E929E")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        spindleLogoPaint.apply {
            color = Color.parseColor("#7E828E")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.20f
        }
        spindleSubtextPaint.apply {
            color = Color.parseColor("#505460")
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.10f
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
            color = Color.parseColor("#121316") // Muted sunken depressed button
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
        buttonMutedIconPaint.apply {
            color = Color.parseColor("#717482") // Muted icon when pressed/latched
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        buttonMutedLabelPaint.apply {
            color = Color.parseColor("#5A5D6B") // Muted label when pressed/latched
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        chassisRect.set(0f, 0f, w.toFloat(), h.toFloat())

        // Cassette Compartment Slot: Fills from 2.0% to 77.5% height
        val slotMarginH = w * 0.030f
        cassetteSlotRect.set(
            slotMarginH,
            h * 0.020f,
            w - slotMarginH,
            h * 0.775f
        )

        // Cassette Shell inside the slot
        val shellInset = w * 0.008f
        cassetteRect.set(
            cassetteSlotRect.left + shellInset,
            cassetteSlotRect.top + shellInset,
            cassetteSlotRect.right - shellInset,
            cassetteSlotRect.bottom - shellInset
        )

        val cw = cassetteRect.width()
        val ch = cassetteRect.height()

        // Center Transparent Acrylic Window Panel (Framing both spools)
        val winLeft = cassetteRect.left + cw * 0.26f
        val winRight = cassetteRect.left + cw * 0.69f
        val winTop = cassetteRect.top + ch * 0.055f
        val winBottom = cassetteRect.bottom - ch * 0.055f
        centerWindowRect.set(winLeft, winTop, winRight, winBottom)

        // Reels Center Coordinates (Centered horizontally inside the central window panel)
        val reelCenterX = centerWindowRect.centerX()
        topHubCenter.set(reelCenterX, centerWindowRect.top + centerWindowRect.height() * 0.26f)
        bottomHubCenter.set(reelCenterX, centerWindowRect.top + centerWindowRect.height() * 0.65f)

        baseHubDimension = centerWindowRect.width() * 0.88f
        hubOuterRadius = baseHubDimension * 0.33f

        // Head opening on the right side of cassette
        val headLeft = cassetteRect.right - cw * 0.09f
        val headRight = cassetteRect.right - cw * 0.02f
        val headH = ch * 0.16f
        headCavityRect.set(
            headLeft,
            cassetteRect.centerY() - headH * 0.5f,
            headRight,
            cassetteRect.centerY() + headH * 0.5f
        )

        // Pre-compute right trapezoid contour
        trapPath.reset()
        trapPath.moveTo(cassetteRect.left + cw * 0.72f, cassetteRect.top + ch * 0.030f)
        trapPath.lineTo(cassetteRect.right - cw * 0.035f, cassetteRect.top + ch * 0.14f)
        trapPath.lineTo(cassetteRect.right - cw * 0.035f, cassetteRect.bottom - ch * 0.14f)
        trapPath.lineTo(cassetteRect.left + cw * 0.72f, cassetteRect.bottom - ch * 0.030f)

        // Text Sizing for vertical left column
        titleTextPaint.textSize = w * 0.035f
        artistTextPaint.textSize = w * 0.024f
        spindleLogoPaint.textSize = w * 0.044f
        spindleSubtextPaint.textSize = w * 0.022f

        // 12-LED Progress Bar: 79.5% to 82.2% height
        val ledMarginH = w * 0.10f
        ledBarRect.set(
            ledMarginH,
            h * 0.795f,
            w - ledMarginH,
            h * 0.822f
        )

        // Bottom 4 Mechanical Keys: 84.5% to 96.5% height
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
        buttonMutedIconPaint.textSize = btnW * 0.32f
        buttonMutedLabelPaint.textSize = btnW * 0.20f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // 1. Draw Outer Chassis & 4 Corner Torx Screws
        drawChassis(canvas, w, h)

        // 2. Draw Recessed Cassette Slot & Smoky Acrylic Cassette Body
        drawCassetteSlotAndShell(canvas)

        // 3. Draw Transparent Acrylic Window Panel & Kinetic Rotating Reels
        drawWindowPanelAndKineticReels(canvas)

        // 4. Draw Top-Left 7-Segment Digital Clock (Rotated -90° Vertical)
        drawVerticalDigitalClock(canvas)

        // 5. Draw Middle Song Title and Artist • Duration (Rotated -90° Vertical)
        drawVerticalTrackInfo(canvas)

        // 6. Draw Bottom-Left SPINDLE Branding (Rotated -90° Vertical)
        drawVerticalSpindleBranding(canvas)

        // 7. Draw 12-LED Song Playback Progress Bar
        drawLedProgressBar(canvas)

        // 8. Draw 4 Mechanical Tactile Buttons (REW, FWD, PLAY [Muted when playing], EJECT)
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
     * Draws the recessed cassette compartment slot, dark smoky cassette shell with corner screws,
     * authentic right-side trapezoid faceplate contour, capstans, guide rollers, and tape head opening.
     */
    private fun drawCassetteSlotAndShell(canvas: Canvas) {
        val cr = 18f
        // 1. Recessed cassette slot bay cavity
        canvas.drawRoundRect(cassetteSlotRect, cr, cr, slotShadowPaint)
        canvas.drawRoundRect(cassetteSlotRect, cr, cr, slotBevelPaint)

        // 2. Cassette Shell Body
        canvas.drawRoundRect(cassetteRect, cr, cr, cassetteShellPaint)
        canvas.drawRoundRect(cassetteRect, cr, cr, cassetteBorderPaint)

        val cw = cassetteRect.width()
        val ch = cassetteRect.height()

        // 4 Corner screws on the cassette shell
        val cScrewR = cw * 0.015f
        val csOffsetX = cw * 0.045f
        val csOffsetY = ch * 0.030f

        drawTorxScrew(canvas, cassetteRect.left + csOffsetX, cassetteRect.top + csOffsetY, cScrewR, 40f)
        drawTorxScrew(canvas, cassetteRect.right - csOffsetX, cassetteRect.top + csOffsetY, cScrewR, 80f)
        drawTorxScrew(canvas, cassetteRect.left + csOffsetX, cassetteRect.bottom - csOffsetY, cScrewR, 20f)
        drawTorxScrew(canvas, cassetteRect.right - csOffsetX, cassetteRect.bottom - csOffsetY, cScrewR, 60f)

        // Center right screw next to tape head opening
        drawTorxScrew(canvas, cassetteRect.right - cw * 0.11f, cassetteRect.centerY(), cScrewR * 0.9f, 30f)

        // Trapezoid faceplate contour on the right side
        canvas.drawPath(trapPath, cassetteGuidePaint)

        // Internal Guide Roller Circles (Top right and bottom right)
        val rollerR = cw * 0.024f
        val rollerX = cassetteRect.right - cw * 0.075f
        val rollerYTop = cassetteRect.top + ch * 0.09f
        val rollerYBottom = cassetteRect.bottom - ch * 0.09f

        canvas.drawCircle(rollerX, rollerYTop, rollerR, screwHeadPaint)
        canvas.drawCircle(rollerX, rollerYTop, rollerR * 0.5f, screwWellPaint)
        canvas.drawCircle(rollerX, rollerYBottom, rollerR, screwHeadPaint)
        canvas.drawCircle(rollerX, rollerYBottom, rollerR * 0.5f, screwWellPaint)

        // Capstan drive holes (cutouts for the deck drive pins)
        val capstanW = cw * 0.035f
        val capstanH = ch * 0.045f
        val capstanX = cassetteRect.right - cw * 0.065f
        tempRectF.set(capstanX - capstanW * 0.5f, topHubCenter.y - capstanH * 0.5f, capstanX + capstanW * 0.5f, topHubCenter.y + capstanH * 0.5f)
        canvas.drawRoundRect(tempRectF, 3f, 3f, cassetteCutoutPaint)
        canvas.drawRoundRect(tempRectF, 3f, 3f, cassetteGuidePaint)

        tempRectF.set(capstanX - capstanW * 0.5f, bottomHubCenter.y - capstanH * 0.5f, capstanX + capstanW * 0.5f, bottomHubCenter.y + capstanH * 0.5f)
        canvas.drawRoundRect(tempRectF, 3f, 3f, cassetteCutoutPaint)
        canvas.drawRoundRect(tempRectF, 3f, 3f, cassetteGuidePaint)

        // Tape head opening & red magnetic pressure pad
        canvas.drawRoundRect(headCavityRect, 6f, 6f, centerWindowPaint)
        canvas.drawRoundRect(headCavityRect, 6f, 6f, centerWindowBorderPaint)
        tempRectF.set(
            headCavityRect.left + 4f,
            headCavityRect.centerY() - 14f,
            headCavityRect.left + 12f,
            headCavityRect.centerY() + 14f
        )
        canvas.drawRoundRect(tempRectF, 2f, 2f, orangeNotchPaint)
    }

    /**
     * Draws the central transparent acrylic window panel and the kinetic rotating reels visible within it.
     */
    private fun drawWindowPanelAndKineticReels(canvas: Canvas) {
        // 1. Central transparent window panel cavity
        canvas.drawRoundRect(centerWindowRect, 14f, 14f, centerWindowPaint)
        canvas.drawRoundRect(centerWindowRect, 14f, 14f, centerWindowBorderPaint)

        // 2. Kinetic Spools
        val spoolState = kinematics.calculate(progress, baseHubDimension)
        val rTopTape = spoolState.leftRadius
        val rBottomTape = spoolState.rightRadius

        // Vertical tape bridge between spools inside the center window
        val bridgeWidth = hubOuterRadius * 0.44f
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

        // Top Reel Hub Mechanism & Signature Orange Calibration Notch
        drawHubMechanism(canvas, topHubCenter.x, topHubCenter.y, hubOuterRadius, topReelAngle)

        // Bottom Reel Hub Mechanism & Signature Orange Calibration Notch
        drawHubMechanism(canvas, bottomHubCenter.x, bottomHubCenter.y, hubOuterRadius, bottomReelAngle)

        // Subtle specular highlight on acrylic window
        tempRectF.set(
            centerWindowRect.left + 4f,
            centerWindowRect.top + 4f,
            centerWindowRect.right - 4f,
            centerWindowRect.top + 28f
        )
        canvas.drawRoundRect(tempRectF, 10f, 10f, windowGlassHighlightPaint)
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
     * Draws the real-time 7-segment digital device clock (Rotated -90° Vertical).
     * Replicates the exact vertical orientation in the reference image (digits reading from bottom to top).
     */
    private fun drawVerticalDigitalClock(canvas: Canvas) {
        calendar.timeInMillis = System.currentTimeMillis()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val h1 = hour / 10
        val h2 = hour % 10
        val m1 = minute / 10
        val m2 = minute % 10

        val cw = cassetteRect.width()
        val ch = cassetteRect.height()
        val originX = cassetteRect.left + cw * 0.145f
        val originY = cassetteRect.top + ch * 0.205f

        canvas.save()
        canvas.translate(originX, originY)
        canvas.rotate(-90f)

        // In this local frame:
        // +X points UP along the screen
        // +Y points RIGHT across the screen (towards the window)
        // -Y points LEFT towards the phone edge
        val digitH = cw * 0.055f  // Height across column (facing left)
        val digitW = digitH * 0.52f // Width along column
        val digitGap = digitW * 0.24f

        var currentX = 0f

        // Digit 1 (Hour tens) - Draw ghost 8 if 0 (matches crop_clock.png)
        draw7SegmentDigitVertical(canvas, currentX, digitW, digitH, if (h1 > 0) h1 else -1)
        currentX += digitW + digitGap

        // Digit 2 (Hour units)
        draw7SegmentDigitVertical(canvas, currentX, digitW, digitH, h2)
        currentX += digitW + digitGap * 0.7f

        // Colon ':'
        drawColonVertical(canvas, currentX, digitW * 0.32f, digitH)
        currentX += digitW * 0.32f + digitGap * 0.7f

        // Digit 3 (Minute tens)
        draw7SegmentDigitVertical(canvas, currentX, digitW, digitH, m1)
        currentX += digitW + digitGap

        // Digit 4 (Minute units)
        draw7SegmentDigitVertical(canvas, currentX, digitW, digitH, m2)

        canvas.restore()
    }

    /**
     * Draws a 7-segment digit in the vertical local frame.
     * Top of digit points towards -Y (left of phone), bottom is at y=0 (towards reels).
     */
    private fun draw7SegmentDigitVertical(canvas: Canvas, x: Float, dw: Float, dh: Float, digit: Int) {
        val t = dh * 0.12f // Slim, crisp segment thickness
        val segGap = t * 0.22f
        val midY = -dh * 0.5f

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
        drawSegment(canvas, x + t * 0.8f, -dh, x + dw - t * 0.8f, -dh + t, clockGhostPaint) // a (top)
        drawSegment(canvas, x + dw - t, -dh + t * 0.8f, x + dw, midY - segGap, clockGhostPaint) // b (TR)
        drawSegment(canvas, x + dw - t, midY + segGap, x + dw, -t * 0.8f, clockGhostPaint) // c (BR)
        drawSegment(canvas, x + t * 0.8f, -t, x + dw - t * 0.8f, 0f, clockGhostPaint) // d (bottom)
        drawSegment(canvas, x, midY + segGap, x + t, -t * 0.8f, clockGhostPaint) // e (BL)
        drawSegment(canvas, x, -dh + t * 0.8f, x + t, midY - segGap, clockGhostPaint) // f (TL)
        drawSegment(canvas, x + t * 0.8f, midY - t * 0.45f, x + dw - t * 0.8f, midY + t * 0.45f, clockGhostPaint) // g (mid)

        // Draw active lit segments
        if ((mask and (1 shl 0)) != 0) drawSegment(canvas, x + t * 0.8f, -dh, x + dw - t * 0.8f, -dh + t, clockLitPaint)
        if ((mask and (1 shl 1)) != 0) drawSegment(canvas, x + dw - t, -dh + t * 0.8f, x + dw, midY - segGap, clockLitPaint)
        if ((mask and (1 shl 2)) != 0) drawSegment(canvas, x + dw - t, midY + segGap, x + dw, -t * 0.8f, clockLitPaint)
        if ((mask and (1 shl 3)) != 0) drawSegment(canvas, x + t * 0.8f, -t, x + dw - t * 0.8f, 0f, clockLitPaint)
        if ((mask and (1 shl 4)) != 0) drawSegment(canvas, x, midY + segGap, x + t, -t * 0.8f, clockLitPaint)
        if ((mask and (1 shl 5)) != 0) drawSegment(canvas, x, -dh + t * 0.8f, x + t, midY - segGap, clockLitPaint)
        if ((mask and (1 shl 6)) != 0) drawSegment(canvas, x + t * 0.8f, midY - t * 0.45f, x + dw - t * 0.8f, midY + t * 0.45f, clockLitPaint)
    }

    private fun drawSegment(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, paint: Paint) {
        tempSegmentRect.set(l, t, r, b)
        canvas.drawRoundRect(tempSegmentRect, 2f, 2f, paint)
    }

    private fun drawColonVertical(canvas: Canvas, x: Float, cw: Float, dh: Float) {
        val dotR = cw * 0.40f
        val cx = x + cw * 0.5f
        val dot1Y = -dh * 0.33f
        val dot2Y = -dh * 0.67f

        // Lit colon dots
        canvas.drawCircle(cx, dot1Y, dotR, clockLitPaint)
        canvas.drawCircle(cx, dot2Y, dotR, clockLitPaint)
    }

    /**
     * Draws the track title and artist • duration in the left column space (Rotated -90° Vertical).
     * Runs vertically along the cassette spine, exactly matching the reference layout.
     */
    private fun drawVerticalTrackInfo(canvas: Canvas) {
        val originX = cassetteRect.left + cassetteRect.width() * 0.14f
        val originY = cassetteRect.top + cassetteRect.height() * 0.60f
        val maxAvailableLength = cassetteRect.height() * 0.34f

        canvas.save()
        canvas.translate(originX, originY)
        canvas.rotate(-90f)

        // Clip to column length so text does not overlap clock or logo
        tempRectF.set(
            0f,
            -titleTextPaint.textSize * 1.5f,
            maxAvailableLength,
            titleTextPaint.textSize * 2.5f
        )
        canvas.clipRect(tempRectF)

        // Line 1: Song Title
        val measuredTitleW = titleTextPaint.measureText(trackTitle)
        if (measuredTitleW > maxAvailableLength && isPlaying) {
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
            canvas.drawText(trackTitle, -marqueeOffset, 0f, titleTextPaint)
            canvas.drawText(trackTitle, -marqueeOffset + measuredTitleW + 40f, 0f, titleTextPaint)
        } else {
            canvas.drawText(trackTitle, 0f, 0f, titleTextPaint)
        }

        // Line 2: Artist • Duration (shifted in +Y direction towards the window)
        val durStr = formatTime(durationMs)
        val subtitle = "$artistName • $durStr"
        val line2Y = titleTextPaint.textSize * 1.18f

        val measuredSubW = artistTextPaint.measureText(subtitle)
        if (measuredSubW > maxAvailableLength) {
            val budgetForArtist = maxAvailableLength - artistTextPaint.measureText("… • $durStr")
            var trimmedArtist = artistName
            while (trimmedArtist.isNotEmpty() && artistTextPaint.measureText(trimmedArtist) > budgetForArtist) {
                trimmedArtist = trimmedArtist.dropLast(1)
            }
            canvas.drawText("${trimmedArtist.trimEnd()}… • $durStr", 0f, line2Y, artistTextPaint)
        } else {
            canvas.drawText(subtitle, 0f, line2Y, artistTextPaint)
        }

        canvas.restore()
    }

    /**
     * Draws the SPINDLE logo at the bottom left (Rotated -90° Vertical, replacing MIUI RECORDER).
     */
    private fun drawVerticalSpindleBranding(canvas: Canvas) {
        val originX = cassetteRect.left + cassetteRect.width() * 0.14f
        val originY = cassetteRect.bottom - cassetteRect.height() * 0.050f

        canvas.save()
        canvas.translate(originX, originY)
        canvas.rotate(-90f)

        // Primary Brand: SPINDLE
        canvas.drawText("SPINDLE", 0f, 0f, spindleLogoPaint)

        // Secondary Tag: RECORDER (alongside SPINDLE towards the window)
        val subtextY = spindleLogoPaint.textSize * 0.90f
        canvas.drawText("RECORDER", 0f, subtextY, spindleSubtextPaint)

        canvas.restore()
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

    /**
     * Draws the PLAY button.
     * When playing: rendered in a muted sunken/depressed state (physically latched down, NO red circle).
     * When stopped/paused: rendered in a raised state with top highlight bevel.
     */
    private fun drawPlayKeyButton(canvas: Canvas, rect: RectF, index: Int) {
        val isTouched = pressedButtonIndex == index
        val isSunken = isPlaying || isTouched
        val r = 10f

        canvas.drawRoundRect(rect, r, r, if (isSunken) buttonPressedPaint else buttonBasePaint)
        canvas.drawRoundRect(rect, r, r, buttonBorderPaint)
        if (!isSunken) {
            canvas.drawRoundRect(rect, r, r, buttonHighlightPaint)
        }

        val offsetY = if (isSunken) 3f else 0f
        val symbolY = rect.centerY() - 2f + offsetY
        val labelY = rect.bottom - (rect.height() * 0.18f) + offsetY

        if (isPlaying) {
            // Muted pressed/latched state - NO red circle button
            canvas.drawText("❚❚", rect.centerX(), symbolY, buttonMutedIconPaint)
            canvas.drawText("PAUSE", rect.centerX(), labelY, buttonMutedLabelPaint)
        } else {
            // Raised Play key
            canvas.drawText("▶", rect.centerX(), symbolY, buttonIconPaint)
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
