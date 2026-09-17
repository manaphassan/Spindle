package com.hana.spindle.ui.cassette

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
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

    private val kinematics = SpindleKinematics(hubRadiusRatio = 0.27f, maxTapeRadiusRatio = 0.56f)

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
                animateHeadEngage(value)
                if (value) startRotation() else stopRotation()
            }
        }

    var progress: Float = 0.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var trackTitle: String = ""
        set(value) {
            field = value
            marqueeOffset = 0f
            invalidate()
        }

    var artistName: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var audioFormat: String = ""
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

    var isLowBattery: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    // Dynamic Album Cover Accent Color with smooth transition animation
    var coverAccentColor: Int = Color.parseColor("#F97316")
        private set

    private var currentAccentColor: Int = Color.parseColor("#F97316")
    private var colorAnimator: ValueAnimator? = null

    fun setCoverAccentColor(newColor: Int, animate: Boolean = true) {
        if (coverAccentColor == newColor) return
        coverAccentColor = newColor
        colorAnimator?.cancel()
        if (animate) {
            val startColor = currentAccentColor
            colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, newColor).apply {
                duration = 600L
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    currentAccentColor = anim.animatedValue as Int
                    updateAccentPaints()
                    invalidate()
                }
                start()
            }
        } else {
            currentAccentColor = newColor
            updateAccentPaints()
            invalidate()
        }
    }

    // Callbacks
    var onPlayClicked: (() -> Unit)? = null
    var onPrevClicked: (() -> Unit)? = null
    var onNextClicked: (() -> Unit)? = null
    var onNextAlbumClicked: (() -> Unit)? = null
    var onPrevAlbumClicked: (() -> Unit)? = null
    var onHoldSeekForward: (() -> Unit)? = null
    var onHoldSeekRewind: (() -> Unit)? = null
    var onHoldSeekEnd: (() -> Unit)? = null
    var onRewindClicked: (() -> Unit)? = null
    var onFastForwardClicked: (() -> Unit)? = null
    var onEjectClicked: (() -> Unit)? = null
    var onSeek: ((Float) -> Unit)? = null

    // Touch & interaction tracking
    private var isDraggingProgress = false
    private var pressedButtonIndex = -1 // 0: REW, 1: FWD, 2: PLAY, 3: EJECT
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var holdSeekRunnable: Runnable? = null
    private var pendingSingleTapRunnable: Runnable? = null
    private var isHoldSeeking = false
    private var lastTapButtonIndex = -1
    private var lastTapTime = 0L

    // Kinetic Animation State
    private var rotationAnimator: ValueAnimator? = null
    private var headAnimator: ValueAnimator? = null
    private var headEngageProgress = 0f
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
    private val tapeShellSpoolPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeTexturePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeBridgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapePathPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapePathHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val pressurePadSpringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressurePadFeltPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeHeadChassisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeHeadBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeHeadCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val hubRimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubTeethPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubInnerCapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubCenterPipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubClutchDimplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orangeNotchPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val clockGhostPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clockLitPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artistTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spindleLogoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spindleSubtextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val acrylicSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val acrylicLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ledInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledActiveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ledRunActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledRunInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledPeakActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledPeakInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val buttonBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonMutedIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonMutedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Cassette Wireframe & Structural Outline Paints
    private val cassetteInnerLipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteSubtleOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteScrewBossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteHighlightLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Dynamic Cassette Label, Slip-Sheet & Audiophile Accents
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelAccentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelAccentSubtlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelRuledLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeWindowGaugePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeWindowGaugeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val formatBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val formatBadgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val formatBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cassetteSlipSheetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val deckGuidePinPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pre-allocated Geometries
    private val chassisRect = RectF()
    private val cassetteSlotRect = RectF()
    private val cassetteRect = RectF()
    private val innerShellRect = RectF()
    private val labelRecessRect = RectF()
    private val labelAccentBarRect = RectF()
    private val formatBadgeRect = RectF()
    private val sideBadgeRect = RectF()
    private val centerWindowRect = RectF()
    private val headCavityRect = RectF()
    private val ledBarRect = RectF()
    private val ledRunRect = RectF()
    private val ledPeakRect = RectF()
    private val tempRectF = RectF()
    private val tempSegmentRect = RectF()
    private val trapPath = Path()
    private val innerTrapPath = Path()
    private val threadedTapePath = Path()
    private val acrylicSheenPath = Path()
    private val pressurePadSpringPath = Path()
    private val pressurePadRect = RectF()
    private val tapeHeadRect = RectF()
    private var textRightMarginX = 0f
    private var columnCenterX = 0f
    private var rollerX = 0f
    private var rollerYTop = 0f
    private var rollerYBottom = 0f

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
        val isLight = theme.id == CassetteTheme.LIGHT.id
        val isEink = theme.id == CassetteTheme.MONOCHROME_EINK.id

        if (isEink) {
            // Pure 1-bit high-contrast Monochrome E-Ink on Pure White Canvas
            chassisPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            chassisBevelPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
            slotBevelPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
            slotShadowPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }

            screwWellPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            screwHeadPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            screwHighlightPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
            screwGroovePaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }

            cassetteShellPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            cassetteBorderPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2.0f }
            cassetteHighlightLinePaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.0f }
            cassetteInnerLipPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
            cassetteOutlinePaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.6f }
            cassetteSubtleOutlinePaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.2f }
            cassetteScrewBossPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
            cassetteCutoutPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            cassetteGuidePaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }

            windowPanelPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            windowPanelBorderPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
            windowGlassHighlightPaint.apply { color = Color.TRANSPARENT; style = Paint.Style.FILL }

            centerWindowPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            centerWindowBorderPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.6f }

            tapeSpoolPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            tapeShellSpoolPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            tapeTexturePaint.apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.2f }
            tapeBridgePaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            tapePathPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f }
            tapePathHighlightPaint.apply { color = Color.TRANSPARENT; style = Paint.Style.STROKE; strokeWidth = 1f }

            pressurePadSpringPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
            pressurePadFeltPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            tapeHeadChassisPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            tapeHeadBevelPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
            tapeHeadCorePaint.apply { color = Color.BLACK; style = Paint.Style.FILL }

            hubRimPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            hubTeethPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            hubInnerCapPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            hubCenterPipPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            hubClutchDimplePaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            orangeNotchPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }

            clockGhostPaint.apply { color = Color.TRANSPARENT; style = Paint.Style.FILL }
            clockLitPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }

            titleTextPaint.apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            artistTextPaint.apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
            spindleLogoPaint.apply { color = Color.BLACK; textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.12f }
            spindleSubtextPaint.apply { color = Color.BLACK; textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); letterSpacing = 0.16f }
            cassetteBadgePaint.apply { color = Color.BLACK; textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); letterSpacing = 0.08f }

            acrylicSheenPaint.apply { color = Color.TRANSPARENT; style = Paint.Style.FILL }
            acrylicLinePaint.apply { color = Color.TRANSPARENT; style = Paint.Style.STROKE; strokeWidth = 1f }

            ledInactivePaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            ledActivePaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            ledActiveGlowPaint.apply { color = Color.TRANSPARENT; style = Paint.Style.STROKE; strokeWidth = 1f }
            ledBorderPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }

            ledRunActivePaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            ledRunInactivePaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            ledPeakActivePaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            ledPeakInactivePaint.apply { color = Color.WHITE; style = Paint.Style.FILL }

            buttonBasePaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            buttonPressedPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            buttonHighlightPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
            buttonBorderPaint.apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
            buttonIconPaint.apply { color = Color.BLACK; style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonLabelPaint.apply { color = Color.BLACK; style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonMutedIconPaint.apply { color = Color.WHITE; style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonMutedLabelPaint.apply { color = Color.WHITE; style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        } else if (isLight) {
            // Sunny Mixtape / Dark Indigo Framing & Pale Warm Stone (HEX: #2A2E45, #F97316, #FB7185, #FDE68A, #FAFAF9)
            chassisPaint.apply { color = Color.parseColor("#FAFAF9"); style = Paint.Style.FILL }
            chassisBevelPaint.apply { color = Color.parseColor("#E5E5E2"); style = Paint.Style.STROKE; strokeWidth = 3f }
            slotBevelPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            slotShadowPaint.apply { color = Color.parseColor("#EDEDE8"); style = Paint.Style.FILL }

            screwWellPaint.apply { color = Color.parseColor("#E5E5E2"); style = Paint.Style.FILL }
            screwHeadPaint.apply { color = Color.parseColor("#F0F0ED"); style = Paint.Style.FILL }
            screwHighlightPaint.apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
            screwGroovePaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.STROKE; strokeWidth = 2.5f }

            cassetteShellPaint.apply { color = Color.parseColor("#FAFAF9"); style = Paint.Style.FILL }
            cassetteBorderPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.STROKE; strokeWidth = 2.0f }
            cassetteHighlightLinePaint.apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.0f }
            cassetteInnerLipPaint.apply { color = Color.parseColor("#3D4260"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
            cassetteOutlinePaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.STROKE; strokeWidth = 1.6f }
            cassetteSubtleOutlinePaint.apply { color = Color.parseColor("#4A4F6B"); style = Paint.Style.STROKE; strokeWidth = 1.3f }
            cassetteScrewBossPaint.apply { color = Color.parseColor("#3D4260"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
            cassetteCutoutPaint.apply { color = Color.parseColor("#E8E8E4"); style = Paint.Style.FILL }
            cassetteGuidePaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.STROKE; strokeWidth = 1.5f }

            windowPanelPaint.apply { color = Color.parseColor("#F5F5F3"); style = Paint.Style.FILL }
            windowPanelBorderPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.STROKE; strokeWidth = 2f }
            windowGlassHighlightPaint.apply { color = Color.argb(40, 255, 255, 255); style = Paint.Style.FILL }

            centerWindowPaint.apply { color = Color.parseColor("#FFFFFF"); style = Paint.Style.FILL }
            centerWindowBorderPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.STROKE; strokeWidth = 1.6f }

            tapeSpoolPaint.apply { color = Color.parseColor("#5A3832"); style = Paint.Style.FILL }
            tapeShellSpoolPaint.apply { color = Color.argb(70, 90, 56, 50); style = Paint.Style.FILL }
            tapeTexturePaint.apply { color = Color.parseColor("#38231E"); style = Paint.Style.STROKE; strokeWidth = 1.2f }
            tapeBridgePaint.apply { color = Color.parseColor("#2A211D"); style = Paint.Style.FILL }
            tapePathPaint.apply { color = Color.argb(120, 75, 45, 40); style = Paint.Style.STROKE; strokeWidth = 5f }
            tapePathHighlightPaint.apply { color = Color.argb(40, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.2f }

            pressurePadSpringPaint.apply { color = Color.parseColor("#B45309"); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            pressurePadFeltPaint.apply { color = Color.parseColor("#FB7185"); style = Paint.Style.FILL }
            tapeHeadChassisPaint.apply { color = Color.parseColor("#5A5E78"); style = Paint.Style.FILL }
            tapeHeadBevelPaint.apply { color = Color.parseColor("#E5E5E2"); style = Paint.Style.STROKE; strokeWidth = 2f }
            tapeHeadCorePaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.FILL }

            hubRimPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.FILL }
            hubTeethPaint.apply { color = Color.parseColor("#FAFAF9"); style = Paint.Style.FILL }
            hubInnerCapPaint.apply { color = Color.parseColor("#3D4260"); style = Paint.Style.FILL }
            hubCenterPipPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.FILL }
            hubClutchDimplePaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL }
            orangeNotchPaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL }

            clockGhostPaint.apply { color = Color.parseColor("#E5E5E2"); style = Paint.Style.FILL }
            clockLitPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.FILL }

            titleTextPaint.apply { color = Color.parseColor("#2A2E45"); textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            artistTextPaint.apply { color = Color.parseColor("#5A5E78"); textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
            spindleLogoPaint.apply { color = Color.parseColor("#2A2E45"); textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.12f }
            spindleSubtextPaint.apply { color = Color.parseColor("#5A5E78"); textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); letterSpacing = 0.16f }
            cassetteBadgePaint.apply { color = Color.parseColor("#FB7185"); textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); letterSpacing = 0.08f }

            acrylicSheenPaint.apply { color = Color.argb(10, 42, 46, 69); style = Paint.Style.FILL }
            acrylicLinePaint.apply { color = Color.argb(30, 42, 46, 69); style = Paint.Style.STROKE; strokeWidth = 1.5f }

            ledInactivePaint.apply { color = Color.parseColor("#E5E5E2"); style = Paint.Style.FILL }
            ledActivePaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL }
            ledActiveGlowPaint.apply { color = Color.argb(90, 249, 115, 22); style = Paint.Style.STROKE; strokeWidth = 3f }
            ledBorderPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.STROKE; strokeWidth = 1.5f }

            ledRunActivePaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL }
            ledRunInactivePaint.apply { color = Color.parseColor("#E5E5E2"); style = Paint.Style.FILL }
            ledPeakActivePaint.apply { color = Color.parseColor("#FB7185"); style = Paint.Style.FILL }
            ledPeakInactivePaint.apply { color = Color.parseColor("#E5E5E2"); style = Paint.Style.FILL }

            buttonBasePaint.apply { color = Color.parseColor("#FAFAF9"); style = Paint.Style.FILL }
            buttonPressedPaint.apply { color = Color.parseColor("#E5E5E2"); style = Paint.Style.FILL }
            buttonHighlightPaint.apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
            buttonBorderPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.STROKE; strokeWidth = 2f }
            buttonIconPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonLabelPaint.apply { color = Color.parseColor("#5A5E78"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonMutedIconPaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonMutedLabelPaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        } else {
            // Dark Indigo Mixtape (HEX: #2A2E45, #F97316, #FB7185, #FDE68A, #FAFAF9)
            chassisPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.FILL }
            chassisBevelPaint.apply { color = Color.parseColor("#1F2233"); style = Paint.Style.STROKE; strokeWidth = 3f }
            slotBevelPaint.apply { color = Color.parseColor("#1F2233"); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            slotShadowPaint.apply { color = Color.parseColor("#151724"); style = Paint.Style.FILL }

            screwWellPaint.apply { color = Color.parseColor("#151724"); style = Paint.Style.FILL }
            screwHeadPaint.apply { color = Color.parseColor("#353A54"); style = Paint.Style.FILL }
            screwHighlightPaint.apply { color = Color.parseColor("#4F567A"); style = Paint.Style.STROKE; strokeWidth = 2f }
            screwGroovePaint.apply { color = Color.parseColor("#151724"); style = Paint.Style.STROKE; strokeWidth = 2.5f }

            cassetteShellPaint.apply { color = Color.parseColor("#1E2132"); style = Paint.Style.FILL }
            cassetteBorderPaint.apply { color = Color.parseColor("#484E70"); style = Paint.Style.STROKE; strokeWidth = 2.0f }
            cassetteHighlightLinePaint.apply { color = Color.argb(120, 250, 250, 249); style = Paint.Style.STROKE; strokeWidth = 1.0f }
            cassetteInnerLipPaint.apply { color = Color.parseColor("#3B405D"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
            cassetteOutlinePaint.apply { color = Color.parseColor("#5C648E"); style = Paint.Style.STROKE; strokeWidth = 1.6f }
            cassetteSubtleOutlinePaint.apply { color = Color.parseColor("#484E70"); style = Paint.Style.STROKE; strokeWidth = 1.3f }
            cassetteScrewBossPaint.apply { color = Color.parseColor("#5C648E"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
            cassetteCutoutPaint.apply { color = Color.parseColor("#12141F"); style = Paint.Style.FILL }
            cassetteGuidePaint.apply { color = Color.parseColor("#484E70"); style = Paint.Style.STROKE; strokeWidth = 1.5f }

            windowPanelPaint.apply { color = Color.parseColor("#171926"); style = Paint.Style.FILL }
            windowPanelBorderPaint.apply { color = Color.parseColor("#353A54"); style = Paint.Style.STROKE; strokeWidth = 2f }
            windowGlassHighlightPaint.apply { color = Color.argb(20, 250, 250, 249); style = Paint.Style.FILL }

            centerWindowPaint.apply { color = Color.parseColor("#12141F"); style = Paint.Style.FILL }
            centerWindowBorderPaint.apply { color = Color.parseColor("#484E70"); style = Paint.Style.STROKE; strokeWidth = 1.6f }

            tapeSpoolPaint.apply { color = Color.parseColor("#5A3832"); style = Paint.Style.FILL }
            tapeShellSpoolPaint.apply { color = Color.argb(85, 90, 56, 50); style = Paint.Style.FILL }
            tapeTexturePaint.apply { color = Color.parseColor("#38231E"); style = Paint.Style.STROKE; strokeWidth = 1.2f }
            tapeBridgePaint.apply { color = Color.parseColor("#181412"); style = Paint.Style.FILL }
            tapePathPaint.apply { color = Color.argb(125, 75, 45, 40); style = Paint.Style.STROKE; strokeWidth = 5f }
            tapePathHighlightPaint.apply { color = Color.argb(30, 250, 250, 249); style = Paint.Style.STROKE; strokeWidth = 1.2f }

            pressurePadSpringPaint.apply { color = Color.parseColor("#B45309"); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            pressurePadFeltPaint.apply { color = Color.parseColor("#FB7185"); style = Paint.Style.FILL }
            tapeHeadChassisPaint.apply { color = Color.parseColor("#788294"); style = Paint.Style.FILL }
            tapeHeadBevelPaint.apply { color = Color.parseColor("#B0B4CE"); style = Paint.Style.STROKE; strokeWidth = 2f }
            tapeHeadCorePaint.apply { color = Color.parseColor("#1E2132"); style = Paint.Style.FILL }

            hubRimPaint.apply { color = Color.parseColor("#FAFAF9"); style = Paint.Style.FILL }
            hubTeethPaint.apply { color = Color.parseColor("#B0B4CE"); style = Paint.Style.FILL }
            hubInnerCapPaint.apply { color = Color.parseColor("#202334"); style = Paint.Style.FILL }
            hubCenterPipPaint.apply { color = Color.parseColor("#12141F"); style = Paint.Style.FILL }
            hubClutchDimplePaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL }
            orangeNotchPaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL }

            clockGhostPaint.apply { color = Color.parseColor("#202334"); style = Paint.Style.FILL }
            clockLitPaint.apply { color = Color.parseColor("#FDE68A"); style = Paint.Style.FILL }

            titleTextPaint.apply { color = Color.parseColor("#FAFAF9"); textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            artistTextPaint.apply { color = Color.parseColor("#B0B4CE"); textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
            spindleLogoPaint.apply { color = Color.parseColor("#FAFAF9"); textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.12f }
            spindleSubtextPaint.apply { color = Color.parseColor("#B0B4CE"); textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); letterSpacing = 0.16f }
            cassetteBadgePaint.apply { color = Color.parseColor("#FB7185"); textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); letterSpacing = 0.08f }

            acrylicSheenPaint.apply { color = Color.argb(14, 250, 250, 249); style = Paint.Style.FILL }
            acrylicLinePaint.apply { color = Color.argb(45, 250, 250, 249); style = Paint.Style.STROKE; strokeWidth = 1.5f }

            ledInactivePaint.apply { color = Color.parseColor("#1A1C2A"); style = Paint.Style.FILL }
            ledActivePaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL }
            ledActiveGlowPaint.apply { color = Color.argb(90, 249, 115, 22); style = Paint.Style.STROKE; strokeWidth = 3f }
            ledBorderPaint.apply { color = Color.parseColor("#353A54"); style = Paint.Style.STROKE; strokeWidth = 1.5f }

            ledRunActivePaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL }
            ledRunInactivePaint.apply { color = Color.parseColor("#38231B"); style = Paint.Style.FILL }
            ledPeakActivePaint.apply { color = Color.parseColor("#FB7185"); style = Paint.Style.FILL }
            ledPeakInactivePaint.apply { color = Color.parseColor("#351C22"); style = Paint.Style.FILL }

            buttonBasePaint.apply { color = Color.parseColor("#202334"); style = Paint.Style.FILL }
            buttonPressedPaint.apply { color = Color.parseColor("#151724"); style = Paint.Style.FILL }
            buttonHighlightPaint.apply { color = Color.parseColor("#353A54"); style = Paint.Style.STROKE; strokeWidth = 2f }
            buttonBorderPaint.apply { color = Color.parseColor("#151724"); style = Paint.Style.STROKE; strokeWidth = 2f }
            buttonIconPaint.apply { color = Color.parseColor("#FAFAF9"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonLabelPaint.apply { color = Color.parseColor("#B0B4CE"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonMutedIconPaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonMutedLabelPaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        }

        labelBgPaint.apply {
            style = Paint.Style.FILL
        }
        labelAccentBarPaint.apply {
            style = Paint.Style.FILL
        }
        labelAccentSubtlePaint.apply {
            style = Paint.Style.FILL
        }
        labelBorderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        labelRuledLinePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        labelBadgeBgPaint.apply {
            style = Paint.Style.FILL
        }
        labelBadgeTextPaint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        tapeWindowGaugePaint.apply {
            color = Color.argb(190, 250, 250, 249)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        tapeWindowGaugeTextPaint.apply {
            color = Color.argb(210, 250, 250, 249)
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        formatBadgeBgPaint.apply {
            style = Paint.Style.FILL
        }
        formatBadgeBorderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        formatBadgeTextPaint.apply {
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        cassetteSlipSheetPaint.apply {
            style = Paint.Style.FILL
        }
        deckGuidePinPaint.apply {
            color = Color.parseColor("#4B5263")
            style = Paint.Style.FILL
        }

        updateAccentPaints()
    }

    private fun updateAccentPaints() {
        val isEink = theme.id == CassetteTheme.MONOCHROME_EINK.id
        val isLight = theme.id == CassetteTheme.LIGHT.id

        if (isEink) {
            labelAccentBarPaint.color = Color.BLACK
            labelAccentSubtlePaint.color = Color.BLACK
            labelBgPaint.color = Color.WHITE
            labelBorderPaint.color = Color.BLACK
            labelRuledLinePaint.color = Color.BLACK
            cassetteSlipSheetPaint.color = Color.TRANSPARENT
            orangeNotchPaint.color = Color.BLACK
            labelBadgeBgPaint.color = Color.BLACK
            labelBadgeTextPaint.color = Color.WHITE
            formatBadgeBgPaint.color = Color.WHITE
            formatBadgeBorderPaint.color = Color.BLACK
            formatBadgeTextPaint.color = Color.BLACK
            ledPeakActivePaint.color = Color.BLACK
            ledPeakActivePaint.clearShadowLayer()
            return
        }

        val accent = currentAccentColor
        val r = Color.red(accent)
        val g = Color.green(accent)
        val b = Color.blue(accent)

        labelAccentBarPaint.color = accent
        labelAccentSubtlePaint.color = Color.argb(50, r, g, b)

        // Tinted nostalgic Butter Yellow cassette label body
        labelBgPaint.color = if (isLight) Color.parseColor("#FDE68A") else Color.parseColor("#23273A")
        labelBorderPaint.color = if (isLight) Color.parseColor("#2A2E45") else Color.parseColor("#3B405D")
        labelRuledLinePaint.color = if (isLight) Color.argb(45, 42, 46, 69) else Color.argb(30, 253, 230, 138)

        // Internal tinted Teflon/polyester slip-sheet visible through clear shell
        cassetteSlipSheetPaint.color = Color.argb(22, r, g, b)

        // Spool marker notch & badges
        orangeNotchPaint.color = accent
        labelBadgeBgPaint.color = Color.parseColor("#FB7185")
        formatBadgeBgPaint.color = Color.argb(40, r, g, b)
        formatBadgeBorderPaint.color = accent
        formatBadgeTextPaint.color = accent

        // Dynamic LED meter peak glow
        ledPeakActivePaint.color = Color.parseColor("#FB7185")
        ledPeakActivePaint.setShadowLayer(6f, 0f, 0f, Color.argb(140, 251, 113, 133))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        chassisRect.set(0f, 0f, w.toFloat(), h.toFloat())

        // 1. Bottom 4 Mechanical Keys anchored at the bottom of the screen
        val btnMarginH = w * 0.035f
        val btnBottom = h * 0.985f
        val btnH = h * 0.115f
        val btnTop = btnBottom - btnH
        val btnGap = w * 0.014f
        val totalBtnW = (w - (btnMarginH * 2f)) - (btnGap * 3f)
        val btnW = totalBtnW / 4f

        btnRewRect.set(btnMarginH, btnTop, btnMarginH + btnW, btnBottom)
        btnFwdRect.set(btnRewRect.right + btnGap, btnTop, btnRewRect.right + btnGap + btnW, btnBottom)
        btnPlayRect.set(btnFwdRect.right + btnGap, btnTop, btnFwdRect.right + btnGap + btnW, btnBottom)
        btnEjectRect.set(btnPlayRect.right + btnGap, btnTop, btnPlayRect.right + btnGap + btnW, btnBottom)

        buttonIconPaint.textSize = btnW * 0.32f
        buttonLabelPaint.textSize = btnW * 0.19f
        buttonMutedIconPaint.textSize = btnW * 0.32f
        buttonMutedLabelPaint.textSize = btnW * 0.19f

        // 2. 12-LED Progress Bar directly above the bottom buttons
        val barH = h * 0.022f
        val barGap = h * 0.012f
        val barBottom = btnTop - barGap
        val barTop = barBottom - barH
        val ledMarginH = w * 0.075f
        ledBarRect.set(
            ledMarginH,
            barTop,
            w - ledMarginH,
            barBottom
        )

        // 3. Dual Status LEDs directly above the progress bar on the right (LOW BATT Red, RUN Green)
        val ledH = h * 0.012f
        val ledBottom = barTop - h * 0.008f
        val ledTop = ledBottom - ledH
        val ledW = w * 0.080f
        val ledGapL = w * 0.020f
        ledRunRect.set(ledBarRect.right - ledW, ledTop, ledBarRect.right, ledBottom)
        ledPeakRect.set(ledRunRect.left - ledGapL - ledW, ledTop, ledRunRect.left - ledGapL, ledBottom)

        // 4. Cassette FULL HEIGHT: fills entire space above the LEDs/meter ("cassette full height")
        val slotInset = w * 0.008f
        val cTop = h * 0.020f
        val cBottom = ledTop - h * 0.012f - slotInset
        val ch = cBottom - cTop
        val cw = w * 0.930f // Wide sleek cassette door filling width
        val cLeft = (w - cw) * 0.5f

        cassetteRect.set(cLeft, cTop, cLeft + cw, cBottom)
        cassetteSlotRect.set(
            cLeft - slotInset,
            cTop - slotInset,
            cLeft + cw + slotInset,
            cBottom + slotInset
        )

        // Inner perimeter chamfer seam / double outline
        innerShellRect.set(
            cassetteRect.left + cw * 0.022f,
            cassetteRect.top + ch * 0.015f,
            cassetteRect.right - cw * 0.022f,
            cassetteRect.bottom - ch * 0.015f
        )

        // 5. Spindle Center Window & Mechanics: Centered on screen
        val reelCenterX = cassetteRect.centerX()
        val winW = cw * 0.350f
        val winLeft = reelCenterX - winW * 0.5f
        val winRight = reelCenterX + winW * 0.5f

        // Center align Walkman & Time in the column between left frame (innerShellRect.left) and clear spindle frame (winLeft)
        val colLeft = innerShellRect.left
        val colRight = winLeft
        columnCenterX = (colLeft + colRight) * 0.5f
        textRightMarginX = columnCenterX

        baseHubDimension = winW
        hubOuterRadius = winW * 0.270f
        val maxTapeRadius = winW * 0.560f

        // Position the two spindles vertically across the full height of the cassette
        val topSpindleY = cassetteRect.top + ch * 0.312f
        val bottomSpindleY = cassetteRect.top + ch * 0.720f

        topHubCenter.set(reelCenterX, topSpindleY)
        bottomHubCenter.set(reelCenterX, bottomSpindleY)

        // Window tightly frames the two spindles vertically in the center
        val winTop = cassetteRect.top + ch * 0.068f
        val winBottom = cassetteRect.bottom - ch * 0.068f
        centerWindowRect.set(winLeft, winTop, winRight, winBottom)

        // Right side: Bottom tape (Trapezoid & head cavity)
        val trapInnerX = cassetteRect.left + cw * 0.740f

        // Head opening on the right side of cassette (centered between spindles)
        val headLeft = cassetteRect.right - cw * 0.125f
        val headRight = cassetteRect.right - cw * 0.018f
        val headMidY = (topHubCenter.y + bottomHubCenter.y) * 0.5f
        val headH = ch * 0.125f
        headCavityRect.set(
            headLeft,
            headMidY - headH * 0.5f,
            headRight,
            headMidY + headH * 0.5f
        )

        // Pre-compute bronze beryllium leaf spring plate
        val padMidY = headCavityRect.centerY()
        pressurePadSpringPath.reset()
        pressurePadSpringPath.moveTo(headCavityRect.left + 3f, padMidY - headH * 0.28f)
        pressurePadSpringPath.quadTo(headCavityRect.left + 12f, padMidY, headCavityRect.left + 3f, padMidY + headH * 0.28f)

        // Pre-compute ruby red felt pressure pad
        val padW = cw * 0.020f
        val padH = headH * 0.26f
        val padX = headCavityRect.left + 10f
        pressurePadRect.set(padX - padW * 0.5f, padMidY - padH * 0.5f, padX + padW * 0.5f, padMidY + padH * 0.5f)

        // Pre-compute right trapezoid contour (outer and inner stepped)
        // Bottom frame height length 80% so top and bottom has 20% space (10% top, 10% bottom)
        val trapOuterX = cassetteRect.right - cw * 0.025f
        val trapTopOuterY = cassetteRect.top + ch * 0.100f
        val trapBottomOuterY = cassetteRect.bottom - ch * 0.100f
        val trapSlope = ch * 0.045f
        val trapTopInnerY = trapTopOuterY + trapSlope
        val trapBottomInnerY = trapBottomOuterY - trapSlope

        trapPath.reset()
        trapPath.moveTo(trapInnerX, trapTopInnerY)
        trapPath.lineTo(trapOuterX, trapTopOuterY)
        trapPath.lineTo(trapOuterX, trapBottomOuterY)
        trapPath.lineTo(trapInnerX, trapBottomInnerY)
        trapPath.close()

        val innerTrapInnerX = trapInnerX + cw * 0.025f
        val innerTrapOuterX = trapOuterX - cw * 0.018f
        val innerTrapTopOuterY = trapTopOuterY + ch * 0.018f
        val innerTrapBottomOuterY = trapBottomOuterY - ch * 0.018f
        val innerTrapTopInnerY = trapTopInnerY + ch * 0.018f
        val innerTrapBottomInnerY = trapBottomInnerY - ch * 0.018f

        innerTrapPath.reset()
        innerTrapPath.moveTo(innerTrapInnerX, innerTrapTopInnerY)
        innerTrapPath.lineTo(innerTrapOuterX, innerTrapTopOuterY)
        innerTrapPath.lineTo(innerTrapOuterX, innerTrapBottomOuterY)
        innerTrapPath.lineTo(innerTrapInnerX, innerTrapBottomInnerY)
        innerTrapPath.close()

        // Pre-compute threaded analog magnetic tape path & guide rollers
        rollerX = cassetteRect.right - cw * 0.075f
        rollerYTop = trapTopOuterY + ch * 0.065f
        rollerYBottom = trapBottomOuterY - ch * 0.065f
        threadedTapePath.reset()
        threadedTapePath.moveTo(topHubCenter.x, topHubCenter.y)
        threadedTapePath.lineTo(rollerX, rollerYTop)
        threadedTapePath.lineTo(rollerX, rollerYBottom)
        threadedTapePath.lineTo(bottomHubCenter.x, bottomHubCenter.y)

        // Pre-compute diagonal acrylic specular sheen (starts below digital clock)
        acrylicSheenPath.reset()
        acrylicSheenPath.moveTo(cassetteRect.left, cassetteRect.top + ch * 0.20f)
        acrylicSheenPath.lineTo(cassetteRect.right, cassetteRect.top + ch * 0.44f)
        acrylicSheenPath.lineTo(cassetteRect.right, cassetteRect.top + ch * 0.56f)
        acrylicSheenPath.lineTo(cassetteRect.left, cassetteRect.top + ch * 0.32f)
        acrylicSheenPath.close()

        // Text Sizing - High legibility bold typography for audiophile DAP
        titleTextPaint.textSize = cw * 0.062f
        titleTextPaint.color = Color.WHITE
        titleTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        artistTextPaint.textSize = cw * 0.036f
        artistTextPaint.color = Color.parseColor("#CBD5E1")
        artistTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        formatBadgeTextPaint.textSize = cw * 0.024f
        tapeWindowGaugeTextPaint.textSize = cw * 0.020f
        labelBadgeTextPaint.textSize = cw * 0.020f

        spindleLogoPaint.textSize = cw * 0.042f
        spindleLogoPaint.color = Color.WHITE
        spindleLogoPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        spindleSubtextPaint.textSize = cw * 0.026f
        spindleSubtextPaint.color = Color.parseColor("#94A3B8")
        spindleSubtextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        cassetteBadgePaint.textSize = cw * 0.018f
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

        // 2b. Draw Deck Status LEDs (RUN & PEAK)
        drawStatusLeds(canvas)

        // 3. Draw Transparent Acrylic Window Panel & Kinetic Rotating Reels
        drawWindowPanelAndKineticReels(canvas)

        // 3b. Draw Cassette Structural Blueprint Wireframe Overlay (on top of reels & window)
        drawCassetteWireframeOverlay(canvas)

        // 4. Draw Top-Left 7-Segment Digital Clock (Rotated -90° Vertical)
        drawVerticalDigitalClock(canvas)

        // 4b. Draw Hi-Res Audio Format Capsule Badge directly below Time (when song played)
        drawAudioFormatBadgeBelowTime(canvas)

        // 5. Draw Bottom-Left Walkman Branding or Now Playing Song Info (Rotated -90° Vertical)
        drawVerticalSpindleBranding(canvas)

        // 6. Draw 12-LED Song Playback Progress Bar
        drawLedProgressBar(canvas)

        // 8. Draw 4 Mechanical Tactile Buttons (REW, FWD, PLAY [Muted when playing], EJECT)
        drawBottomButtons(canvas)
    }

    /**
     * Draws the dark matte industrial chassis faceplate.
     */
    private fun drawChassis(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(chassisRect, chassisPaint)
        canvas.drawRect(chassisRect, chassisBevelPaint)
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
     * Draws a corner screw surrounded by molded circular boss wells and a diagonal reinforcement strut
     * matching the authentic technical cassette blueprint.
     */
    private fun drawCornerScrewWithBoss(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        cornerX: Float,
        cornerY: Float,
        angleDeg: Float
    ) {
        // Outer molded circular boss rings
        canvas.drawCircle(cx, cy, r * 1.65f, cassetteScrewBossPaint)
        canvas.drawCircle(cx, cy, r * 1.28f, cassetteSubtleOutlinePaint)

        // Diagonal corner strut connecting shell corner to the screw boss well
        val dirX = cx - cornerX
        val dirY = cy - cornerY
        val len = Math.hypot(dirX.toDouble(), dirY.toDouble()).toFloat()
        if (len > 0f) {
            val stopX = cx - (dirX / len) * (r * 1.65f)
            val stopY = cy - (dirY / len) * (r * 1.65f)
            canvas.drawLine(cornerX, cornerY, stopX, stopY, cassetteScrewBossPaint)
        }

        // Torx screw inside the boss
        drawTorxScrew(canvas, cx, cy, r, angleDeg)
    }

    /**
     * Draws the recessed cassette compartment slot, dark smoky cassette shell body,
     * tape head opening, and tape path mechanism.
     */
    private fun drawCassetteSlotAndShell(canvas: Canvas) {
        val cr = 18f
        // 1. Recessed cassette slot bay cavity
        canvas.drawRoundRect(cassetteSlotRect, cr, cr, slotShadowPaint)
        canvas.drawRoundRect(cassetteSlotRect, cr, cr, slotBevelPaint)

        val cw = cassetteRect.width()
        val ch = cassetteRect.height()

        // 1b. Mechanical Deck Locating Guide Pins (cylindrical alignment studs in cassette bay)
        val pinR = cw * 0.010f
        val pinX = cassetteSlotRect.left + cw * 0.015f
        canvas.drawCircle(pinX, cassetteRect.top + ch * 0.16f, pinR, deckGuidePinPaint)
        canvas.drawCircle(pinX, cassetteRect.top + ch * 0.16f, pinR * 0.5f, screwHighlightPaint)
        canvas.drawCircle(pinX, cassetteRect.bottom - ch * 0.16f, pinR, deckGuidePinPaint)
        canvas.drawCircle(pinX, cassetteRect.bottom - ch * 0.16f, pinR * 0.5f, screwHighlightPaint)

        // 2. Cassette Shell Body Base
        canvas.drawRoundRect(cassetteRect, cr, cr, cassetteShellPaint)

        // 2b. Internal Slip-Sheet Tint (subtle dynamic tint visible through smoky acrylic)
        canvas.drawRoundRect(innerShellRect, 12f, 12f, cassetteSlipSheetPaint)

        // Guide Roller Circles (Top right and bottom right of 80% bottom frame)
        val rollerR = cw * 0.024f
        canvas.drawCircle(rollerX, rollerYTop, rollerR, screwHeadPaint)
        canvas.drawCircle(rollerX, rollerYTop, rollerR * 0.5f, screwWellPaint)
        canvas.drawCircle(rollerX, rollerYBottom, rollerR, screwHeadPaint)
        canvas.drawCircle(rollerX, rollerYBottom, rollerR * 0.5f, screwWellPaint)

        // 1. Tape head opening & pressure pad mechanism
        canvas.drawRoundRect(headCavityRect, 6f, 6f, centerWindowPaint)
        canvas.drawRoundRect(headCavityRect, 6f, 6f, centerWindowBorderPaint)

        // 2. Bronze beryllium leaf spring plate mounted behind tape
        canvas.drawPath(pressurePadSpringPath, pressurePadSpringPaint)

        // 3. Ruby red felt pressure pad at contact point under the tape
        canvas.drawRoundRect(pressurePadRect, 2f, 2f, pressurePadFeltPaint)

        // 4. Playback Tape Head (advances into cavity when isPlaying)
        val retractedHeadX = headCavityRect.right + 12f
        val engagedHeadX = pressurePadRect.right + 2f
        val currentHeadX = retractedHeadX + (engagedHeadX - retractedHeadX) * headEngageProgress
        val headW = cw * 0.040f
        val headH = headCavityRect.height() * 0.46f
        tapeHeadRect.set(
            currentHeadX,
            headCavityRect.centerY() - headH * 0.5f,
            currentHeadX + headW,
            headCavityRect.centerY() + headH * 0.5f
        )

        if (currentHeadX < headCavityRect.right) {
            canvas.save()
            canvas.clipRect(headCavityRect)
            canvas.drawRoundRect(tapeHeadRect, 4f, 4f, tapeHeadChassisPaint)
            canvas.drawRoundRect(tapeHeadRect, 4f, 4f, tapeHeadBevelPaint)
            val coreW = 3.5f
            tempRectF.set(
                tapeHeadRect.left + 2f,
                headCavityRect.centerY() - headH * 0.22f,
                tapeHeadRect.left + 2f + coreW,
                headCavityRect.centerY() + headH * 0.22f
            )
            canvas.drawRoundRect(tempRectF, 1f, 1f, tapeHeadCorePaint)
            canvas.restore()
        }

        // 5. Tape path threading through the cassette cavity (flexes over engaged head)
        threadedTapePath.reset()
        threadedTapePath.moveTo(topHubCenter.x, topHubCenter.y)
        threadedTapePath.lineTo(rollerX, rollerYTop)
        if (headEngageProgress > 0.05f) {
            val tapeTouchX = currentHeadX.coerceAtMost(rollerX)
            threadedTapePath.lineTo(tapeTouchX, headCavityRect.centerY() - headH * 0.38f)
            threadedTapePath.lineTo(tapeTouchX, headCavityRect.centerY() + headH * 0.38f)
        }
        threadedTapePath.lineTo(rollerX, rollerYBottom)
        threadedTapePath.lineTo(bottomHubCenter.x, bottomHubCenter.y)

        // Draw translucent tape ribbon outside clear window seen through protective plastic shell
        canvas.drawPath(threadedTapePath, tapePathPaint)
        canvas.drawPath(threadedTapePath, tapePathHighlightPaint)
    }

    /**
     * Draws the complete technical wireframe blueprint overlay of the cassette tape on top of the
     * cassette body, reels, and window.
     */
    private fun drawCassetteWireframeOverlay(canvas: Canvas) {
        val cr = 18f
        val cw = cassetteRect.width()
        val ch = cassetteRect.height()

        // 1. Primary Outer Shell Wireframe Boundary
        canvas.drawRoundRect(cassetteRect, cr, cr, cassetteBorderPaint)

        // 2. Inner Perimeter Chamfer Seam / Double Outline
        canvas.drawRoundRect(innerShellRect, 12f, 12f, cassetteInnerLipPaint)
        canvas.drawRoundRect(innerShellRect, 12f, 12f, cassetteHighlightLinePaint)

        // 3. Molded Side Grip Notches on top and bottom edges
        val notchLeft = cassetteRect.left + cw * 0.72f
        val notchRight = cassetteRect.left + cw * 0.90f
        val notchDepth = ch * 0.012f
        tempRectF.set(notchLeft, cassetteRect.top, notchRight, cassetteRect.top + notchDepth)
        canvas.drawRoundRect(tempRectF, 2f, 2f, cassetteCutoutPaint)
        canvas.drawRoundRect(tempRectF, 2f, 2f, cassetteOutlinePaint)

        tempRectF.set(notchLeft, cassetteRect.bottom - notchDepth, notchRight, cassetteRect.bottom)
        canvas.drawRoundRect(tempRectF, 2f, 2f, cassetteCutoutPaint)
        canvas.drawRoundRect(tempRectF, 2f, 2f, cassetteOutlinePaint)

        // 4. Large Label Recess Wireframe: frame outline removed per user instruction
        // (Clean borderless song title frame wrapping text)

        // 5. Circular Molded Reel Wells (concentric circles around top and bottom spools)
        canvas.drawCircle(topHubCenter.x, topHubCenter.y, hubOuterRadius * 1.58f, cassetteSubtleOutlinePaint)
        canvas.drawCircle(topHubCenter.x, topHubCenter.y, hubOuterRadius * 1.30f, cassetteSubtleOutlinePaint)
        canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, hubOuterRadius * 1.58f, cassetteSubtleOutlinePaint)
        canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, hubOuterRadius * 1.30f, cassetteSubtleOutlinePaint)

        // 6. Center Molded Rectangular Ridge between the reels
        val midY = (topHubCenter.y + bottomHubCenter.y) * 0.5f
        val ridgeW = baseHubDimension * 0.68f
        val ridgeH = ch * 0.052f
        tempRectF.set(topHubCenter.x - ridgeW * 0.5f, midY - ridgeH * 0.5f, topHubCenter.x + ridgeW * 0.5f, midY + ridgeH * 0.5f)
        canvas.drawRoundRect(tempRectF, 3f, 3f, cassetteSubtleOutlinePaint)

        // 7. 4 Corner Screws with Molded Circular Bosses and Diagonal Corner Struts
        val cScrewR = cw * 0.022f
        val csOffsetX = cw * 0.048f
        val csOffsetY = ch * 0.028f

        drawCornerScrewWithBoss(canvas, cassetteRect.left + csOffsetX, cassetteRect.top + csOffsetY, cScrewR, innerShellRect.left, innerShellRect.top, 40f)
        drawCornerScrewWithBoss(canvas, cassetteRect.right - csOffsetX, cassetteRect.top + csOffsetY, cScrewR, innerShellRect.right, innerShellRect.top, 80f)
        drawCornerScrewWithBoss(canvas, cassetteRect.left + csOffsetX, cassetteRect.bottom - csOffsetY, cScrewR, innerShellRect.left, innerShellRect.bottom, 20f)
        drawCornerScrewWithBoss(canvas, cassetteRect.right - csOffsetX, cassetteRect.bottom - csOffsetY, cScrewR, innerShellRect.right, innerShellRect.bottom, 60f)

        // 8. Dual-Stepped Trapezoid Faceplate Contours (Outer & Inner - 80% height length)
        canvas.drawPath(trapPath, cassetteOutlinePaint)
        canvas.drawPath(innerTrapPath, cassetteSubtleOutlinePaint)

        // 8a. Center Faceplate Screw with Molded Circular Boss (between capstan drive cutouts)
        val centerScrewX = cassetteRect.left + cw * 0.805f
        val centerScrewY = midY
        canvas.drawCircle(centerScrewX, centerScrewY, cScrewR * 1.55f, cassetteScrewBossPaint)
        drawTorxScrew(canvas, centerScrewX, centerScrewY, cScrewR * 0.88f, 30f)

        // 9. Capstan Drive Holes (circular molded openings for deck drive pins)
        val capstanR = cw * 0.024f
        val capstanX = cassetteRect.left + cw * 0.910f
        val capstanYTop = midY - ch * 0.24f
        val capstanYBottom = midY + ch * 0.24f

        canvas.drawCircle(capstanX, capstanYTop, capstanR, cassetteCutoutPaint)
        canvas.drawCircle(capstanX, capstanYTop, capstanR, cassetteOutlinePaint)
        canvas.drawCircle(capstanX, capstanYTop, capstanR * 0.55f, cassetteSubtleOutlinePaint)

        canvas.drawCircle(capstanX, capstanYBottom, capstanR, cassetteCutoutPaint)
        canvas.drawCircle(capstanX, capstanYBottom, capstanR, cassetteOutlinePaint)
        canvas.drawCircle(capstanX, capstanYBottom, capstanR * 0.55f, cassetteSubtleOutlinePaint)

        // 9a. Pinch Roller Rectangular Window Cutouts
        val prW = cw * 0.048f
        val prH = ch * 0.038f
        val prX = cassetteRect.left + cw * 0.820f
        val prYTop = midY - ch * 0.12f
        val prYBottom = midY + ch * 0.12f

        tempRectF.set(prX - prW * 0.5f, prYTop - prH * 0.5f, prX + prW * 0.5f, prYTop + prH * 0.5f)
        canvas.drawRoundRect(tempRectF, 3f, 3f, cassetteCutoutPaint)
        canvas.drawRoundRect(tempRectF, 3f, 3f, cassetteOutlinePaint)

        tempRectF.set(prX - prW * 0.5f, prYBottom - prH * 0.5f, prX + prW * 0.5f, prYBottom + prH * 0.5f)
        canvas.drawRoundRect(tempRectF, 3f, 3f, cassetteCutoutPaint)
        canvas.drawRoundRect(tempRectF, 3f, 3f, cassetteOutlinePaint)

        // 10. Diagonal specular acrylic reflection sheen & sharp line across upper shell
        canvas.drawPath(acrylicSheenPath, acrylicSheenPaint)
        canvas.drawLine(
            cassetteRect.left, cassetteRect.top + ch * 0.20f,
            cassetteRect.right, cassetteRect.top + ch * 0.44f,
            acrylicLinePaint
        )
    }

    /**
     * Draws the dual pill-shaped deck status LEDs (LOW BATT Red, RUN Green).
     */
    private fun drawStatusLeds(canvas: Canvas) {
        // LOW BATT Red LED (illuminated with ruby glow when isLowBattery is true)
        canvas.drawRoundRect(ledPeakRect, 4f, 4f, if (isLowBattery) ledPeakActivePaint else ledPeakInactivePaint)
        // RUN Green LED (illuminated with neon glow when isPlaying)
        canvas.drawRoundRect(ledRunRect, 4f, 4f, if (isPlaying) ledRunActivePaint else ledRunInactivePaint)
    }

    /**
     * Draws the central transparent acrylic window panel and the kinetic rotating reels visible within it.
     */
    private fun drawWindowPanelAndKineticReels(canvas: Canvas) {
        val spoolState = kinematics.calculate(progress, baseHubDimension)
        val rTopTape = spoolState.leftRadius
        val rBottomTape = spoolState.rightRadius

        // 1. Translucent tape spools visible through protective plastic shell OUTSIDE the clear window
        canvas.drawCircle(topHubCenter.x, topHubCenter.y, rTopTape, tapeShellSpoolPaint)
        canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, rBottomTape, tapeShellSpoolPaint)

        // 2. Central transparent window panel cavity
        canvas.drawRoundRect(centerWindowRect, 14f, 14f, centerWindowPaint)
        canvas.drawRoundRect(centerWindowRect, 14f, 14f, centerWindowBorderPaint)

        // 3. Crisp, fully opaque magnetic tape packs & hubs INSIDE the clear window
        canvas.save()
        canvas.clipRect(centerWindowRect)

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

        // 4. Printed tape counter gauge scale (100 ... 50 ... 0)
        val cw = cassetteRect.width()
        val gaugeX = centerWindowRect.left + cw * 0.038f
        val gaugeTopY = topHubCenter.y + hubOuterRadius * 0.95f
        val gaugeBottomY = bottomHubCenter.y - hubOuterRadius * 0.95f
        val tickCount = 9
        val tickStep = (gaugeBottomY - gaugeTopY) / (tickCount - 1)
        for (i in 0 until tickCount) {
            val ty = gaugeTopY + i * tickStep
            val isMajor = (i == 0 || i == 4 || i == 8)
            val tickLen = if (isMajor) cw * 0.022f else cw * 0.012f
            canvas.drawLine(gaugeX, ty, gaugeX + tickLen, ty, tapeWindowGaugePaint)
            if (isMajor) {
                val label = when (i) {
                    0 -> "100"
                    4 -> "50"
                    else -> "0"
                }
                canvas.save()
                canvas.translate(gaugeX + tickLen + cw * 0.016f, ty)
                canvas.rotate(-90f)
                canvas.drawText(label, 0f, tapeWindowGaugeTextPaint.textSize * 0.35f, tapeWindowGaugeTextPaint)
                canvas.restore()
            }
        }

        canvas.restore()

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

        // 3-point drive clutch dimples stamped around the center chrome spindle pin
        val dimpleDist = innerRadius * 0.32f
        val dimpleR = innerRadius * 0.055f
        for (j in 0 until 3) {
            val dAngle = Math.toRadians(j * 120.0)
            val dx = cx + (dimpleDist * Math.cos(dAngle)).toFloat()
            val dy = cy + (dimpleDist * Math.sin(dAngle)).toFloat()
            canvas.drawCircle(dx, dy, dimpleR, hubClutchDimplePaint)
        }

        canvas.restore()
    }

    /**
     * Draws the real-time 7-segment digital device clock (Rotated -90° Vertical).
     * Replicates the exact vertical orientation in the reference image (digits reading from bottom to top),
     * right-aligned with the top border of the central clear window frame.
     */
    private fun drawVerticalDigitalClock(canvas: Canvas) {
        calendar.timeInMillis = System.currentTimeMillis()
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        val hour = if (is24) calendar.get(Calendar.HOUR_OF_DAY) else {
            val h12 = calendar.get(Calendar.HOUR)
            if (h12 == 0) 12 else h12
        }
        val minute = calendar.get(Calendar.MINUTE)

        val h1 = hour / 10
        val h2 = hour % 10
        val m1 = minute / 10
        val m2 = minute % 10

        val cw = cassetteRect.width()
        val ch = cassetteRect.height()

        val digitH = cw * 0.052f  // Height across column (facing left)
        val digitW = digitH * 0.52f // Width along column
        val digitGap = digitW * 0.22f
        val colonW = digitW * 0.32f
        val totalClockLength = digitW * 4f + digitGap * 2.4f + colonW

        // Center-align clock horizontally in the column between left frame and clear spindle frame
        val originX = columnCenterX + digitH * 0.5f
        val originY = centerWindowRect.top + totalClockLength + ch * 0.008f

        canvas.save()
        canvas.translate(originX, originY)
        canvas.rotate(-90f)

        var currentX = 0f

        // Digit 1 (Hour tens) - Draw ghost 8 if 0 (matches crop_clock.png)
        draw7SegmentDigitVertical(canvas, currentX, digitW, digitH, if (h1 > 0) h1 else -1)
        currentX += digitW + digitGap

        // Digit 2 (Hour units)
        draw7SegmentDigitVertical(canvas, currentX, digitW, digitH, h2)
        currentX += digitW + digitGap * 0.7f

        // Colon ':'
        drawColonVertical(canvas, currentX, colonW, digitH)
        currentX += colonW + digitGap * 0.7f

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
     * Draws the SONG TITLE, Artist Name • Song Duration, and Hi-Res Audio Format capsule badge
     * in the expansive center-left column on the cassette label face (Rotated -90° Vertical).
     * Sized with bold, prominent typography (cw * 0.062f) for effortless legibility.
     */
    /**
     * Draws the Hi-Res Audio Format capsule badge positioned directly below the 7-segment digital clock,
     * center-aligned in the column between the left frame and the clear spindle frame.
     */
    private fun drawAudioFormatBadgeBelowTime(canvas: Canvas) {
        val hasSong = trackTitle.isNotEmpty() && trackTitle != "No Track Loaded"
        if (!hasSong) return

        val cw = cassetteRect.width()
        val ch = cassetteRect.height()

        val digitH = cw * 0.052f
        val digitW = digitH * 0.52f
        val digitGap = digitW * 0.22f
        val colonW = digitW * 0.32f
        val clockLength = digitW * 4f + digitGap * 2.4f + colonW
        val clockBottomY = centerWindowRect.top + clockLength + ch * 0.008f

        val badgeText = if (audioFormat.isNotEmpty()) audioFormat else "HI-RES AUDIO"
        val badgeW = formatBadgeTextPaint.measureText(badgeText) + cw * 0.028f
        val badgeH = cw * 0.028f
        val badgeGap = ch * 0.022f
        val badgeCenterY = clockBottomY + badgeGap + badgeW * 0.5f

        canvas.save()
        canvas.translate(columnCenterX, badgeCenterY)
        canvas.rotate(-90f)

        formatBadgeRect.set(-badgeW * 0.5f, -badgeH * 0.5f, badgeW * 0.5f, badgeH * 0.5f)
        canvas.drawRoundRect(formatBadgeRect, badgeH * 0.5f, badgeH * 0.5f, formatBadgeBgPaint)
        canvas.drawRoundRect(formatBadgeRect, badgeH * 0.5f, badgeH * 0.5f, formatBadgeBorderPaint)
        canvas.drawText(
            badgeText,
            0f,
            formatBadgeTextPaint.textSize * 0.35f,
            formatBadgeTextPaint
        )

        canvas.restore()
    }

    /**
     * Draws the WALKMAN Player branding (default idle) or Now Playing Track Info (when a song is played)
     * at the bottom-left of the cassette shell, center-aligned in the column between the left frame
     * and the clear spindle frame (Rotated -90° Vertical).
     *
     * In default idle state (no track loaded):
     * - Line 0: WALKMAN (crisp bold white typography)
     * - Line 1: Player (silver/slate subtext)
     *
     * When a song is played / loaded:
     * - Line 0: Song Title (prominent bold white with smooth marquee scrolling when playing)
     * - Line 1: Artist Name • song time / total duration (e.g. Linkin Park • 01:24 / 03:24)
     */
    private fun drawVerticalSpindleBranding(canvas: Canvas) {
        val hasSong = trackTitle.isNotEmpty() && trackTitle != "No Track Loaded"

        val cw = cassetteRect.width()
        val ch = cassetteRect.height()

        val originX = columnCenterX
        val originY = centerWindowRect.bottom - ch * 0.008f

        canvas.save()
        canvas.translate(originX, originY)
        canvas.rotate(-90f)

        // Local coordinates:
        // +X points UP towards the top-left digital clock and badge.
        // -Y points LEFT towards the outer cassette chassis edge.
        // +Y points RIGHT towards the center spindle window.
        // y = 0f is exactly at columnCenterX!

        val titleSize = spindleLogoPaint.textSize
        val artistSize = spindleSubtextPaint.textSize
        val lineGap = cw * 0.012f
        val totalBlockH = titleSize + lineGap + artistSize

        // Center Line 0 and Line 1 together symmetrically around y = 0 (columnCenterX)
        val line0Y = -totalBlockH * 0.5f + 0.8f * titleSize
        val line1Y = +totalBlockH * 0.5f - 0.2f * artistSize

        if (!hasSong) {
            // Default Walkman Player typography
            spindleLogoPaint.letterSpacing = 0.12f
            spindleSubtextPaint.letterSpacing = 0.16f

            // Line 0: WALKMAN (crisp bold white typography)
            canvas.drawText("WALKMAN", 0f, line0Y, spindleLogoPaint)

            // Line 1: Player (silver/slate subtext)
            canvas.drawText("Player", 0f, line1Y, spindleSubtextPaint)
        } else {
            spindleLogoPaint.letterSpacing = 0.02f
            spindleSubtextPaint.letterSpacing = 0.02f

            // Compute available vertical length along +X before reaching the badge below time
            val digitH = cw * 0.052f
            val digitW = digitH * 0.52f
            val digitGap = digitW * 0.22f
            val colonW = digitW * 0.32f
            val clockLength = digitW * 4f + digitGap * 2.4f + colonW
            val clockBottomY = centerWindowRect.top + clockLength + ch * 0.008f

            val badgeText = if (audioFormat.isNotEmpty()) audioFormat else "HI-RES AUDIO"
            val badgeW = formatBadgeTextPaint.measureText(badgeText) + cw * 0.028f
            val badgeGap = ch * 0.022f
            val badgeBottomY = clockBottomY + badgeGap + badgeW

            val maxAvailableLength = ((originY - badgeBottomY) - ch * 0.035f).coerceAtLeast(100f)

            // Line 0: Song Title (prominent bold white with smooth marquee scrolling when playing)
            val measuredTitleW = spindleLogoPaint.measureText(trackTitle)
            if (measuredTitleW > maxAvailableLength && isPlaying) {
                val now = SystemClock.uptimeMillis()
                if (lastMarqueeTime != 0L) {
                    val dt = (now - lastMarqueeTime) / 1000f
                    marqueeOffset += dt * 36f
                    val totalScrollW = measuredTitleW + 48f
                    if (marqueeOffset > totalScrollW) {
                        marqueeOffset = 0f
                    }
                }
                lastMarqueeTime = now

                canvas.save()
                tempRectF.set(
                    0f,
                    line0Y - titleSize * 1.25f,
                    maxAvailableLength,
                    line0Y + titleSize * 0.40f
                )
                canvas.clipRect(tempRectF)
                val startX = -marqueeOffset
                canvas.drawText(trackTitle, startX, line0Y, spindleLogoPaint)
                canvas.drawText(trackTitle, startX + measuredTitleW + 48f, line0Y, spindleLogoPaint)
                canvas.restore()
            } else if (measuredTitleW > maxAvailableLength) {
                val budget = maxAvailableLength - spindleLogoPaint.measureText("…")
                var trimmed = trackTitle
                while (trimmed.isNotEmpty() && spindleLogoPaint.measureText(trimmed) > budget) {
                    trimmed = trimmed.dropLast(1)
                }
                canvas.drawText("${trimmed.trimEnd()}…", 0f, line0Y, spindleLogoPaint)
            } else {
                canvas.drawText(trackTitle, 0f, line0Y, spindleLogoPaint)
            }

            // Line 1: Artist Name • song time / total duration (e.g. Linkin Park • 01:24 / 03:24)
            val curTimeStr = formatTime(currentTimeMs)
            val totTimeStr = formatTime(durationMs)
            val artistPart = if (artistName.isNotEmpty() && artistName != "Unknown Artist") artistName else "Unknown Artist"
            val subtitle = "$artistPart • $curTimeStr / $totTimeStr"

            val measuredSubW = spindleSubtextPaint.measureText(subtitle)
            if (measuredSubW > maxAvailableLength) {
                val durSuffix = " • $curTimeStr / $totTimeStr"
                val budgetForArtist = maxAvailableLength - spindleSubtextPaint.measureText("…$durSuffix")
                var trimmedArtist = artistPart
                while (trimmedArtist.isNotEmpty() && spindleSubtextPaint.measureText("$trimmedArtist…$durSuffix") > maxAvailableLength) {
                    trimmedArtist = trimmedArtist.dropLast(1)
                }
                canvas.drawText("${trimmedArtist.trimEnd()}…$durSuffix", 0f, line1Y, spindleSubtextPaint)
            } else {
                canvas.drawText(subtitle, 0f, line1Y, spindleSubtextPaint)
            }
        }

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
        drawKeyButton(canvas, btnRewRect, 0, "◀◀", "REW")
        drawKeyButton(canvas, btnFwdRect, 1, "▶▶", "FWD")
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

                    // If REW (0) or FWD (1), schedule hold-seeking runnable after 350ms
                    if (pressedButtonIndex == 0 || pressedButtonIndex == 1) {
                        val btnIndex = pressedButtonIndex
                        holdSeekRunnable?.let { gestureHandler.removeCallbacks(it) }
                        isHoldSeeking = false
                        holdSeekRunnable = object : Runnable {
                            override fun run() {
                                isHoldSeeking = true
                                if (btnIndex == 1) {
                                    onHoldSeekForward?.invoke()
                                    topReelAngle = (topReelAngle + 22f) % 360f
                                    bottomReelAngle = (bottomReelAngle + 22f) % 360f
                                } else {
                                    onHoldSeekRewind?.invoke()
                                    topReelAngle = (topReelAngle - 22f + 360f) % 360f
                                    bottomReelAngle = (bottomReelAngle - 22f + 360f) % 360f
                                }
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                invalidate()
                                gestureHandler.postDelayed(this, 120L)
                            }
                        }
                        gestureHandler.postDelayed(holdSeekRunnable!!, 350L)
                    }
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

                    if (clickedIndex == 0 || clickedIndex == 1) {
                        holdSeekRunnable?.let { gestureHandler.removeCallbacks(it) }
                        holdSeekRunnable = null

                        if (isHoldSeeking) {
                            isHoldSeeking = false
                            onHoldSeekEnd?.invoke()
                            return true
                        }

                        val now = SystemClock.uptimeMillis()
                        if (lastTapButtonIndex == clickedIndex && (now - lastTapTime) < 280L) {
                            // Double tap: Skip Album!
                            pendingSingleTapRunnable?.let { gestureHandler.removeCallbacks(it) }
                            pendingSingleTapRunnable = null
                            lastTapButtonIndex = -1
                            lastTapTime = 0L

                            if (clickedIndex == 1) {
                                onNextAlbumClicked?.invoke()
                            } else {
                                onPrevAlbumClicked?.invoke()
                            }
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else {
                            // First tap: Wait for possible double tap, otherwise trigger next/previous song!
                            lastTapButtonIndex = clickedIndex
                            lastTapTime = now

                            pendingSingleTapRunnable?.let { gestureHandler.removeCallbacks(it) }
                            pendingSingleTapRunnable = Runnable {
                                if (clickedIndex == 1) {
                                    onNextClicked?.invoke()
                                } else {
                                    onPrevClicked?.invoke()
                                }
                                lastTapButtonIndex = -1
                                pendingSingleTapRunnable = null
                            }
                            gestureHandler.postDelayed(pendingSingleTapRunnable!!, 260L)
                        }
                        return true
                    }

                    when (clickedIndex) {
                        2 -> if (btnPlayRect.contains(x, y)) onPlayClicked?.invoke()
                        3 -> if (btnEjectRect.contains(x, y)) onEjectClicked?.invoke()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isDraggingProgress = false
                parent?.requestDisallowInterceptTouchEvent(false)
                holdSeekRunnable?.let { gestureHandler.removeCallbacks(it) }
                holdSeekRunnable = null
                if (isHoldSeeking) {
                    isHoldSeeking = false
                    onHoldSeekEnd?.invoke()
                }
                pendingSingleTapRunnable?.let { gestureHandler.removeCallbacks(it) }
                pendingSingleTapRunnable = null
                lastTapButtonIndex = -1
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

    private fun animateHeadEngage(engage: Boolean) {
        headAnimator?.cancel()
        val target = if (engage) 1f else 0f
        headAnimator = ValueAnimator.ofFloat(headEngageProgress, target).apply {
            duration = 180L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                headEngageProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying) {
            headEngageProgress = 1f
            startRotation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        holdSeekRunnable?.let { gestureHandler.removeCallbacks(it) }
        pendingSingleTapRunnable?.let { gestureHandler.removeCallbacks(it) }
        headAnimator?.cancel()
        headAnimator = null
        stopRotation()
    }
}
