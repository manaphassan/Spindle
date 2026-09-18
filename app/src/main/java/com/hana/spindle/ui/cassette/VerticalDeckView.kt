package com.hana.spindle.ui.cassette

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
 * 6. Bottom-left retro SPINDLE branding, oriented vertically (-90° rotation).
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
            val changed = (field != value)
            field = value
            if (changed) {
                animateHeadEngage(value)
            }
            if (value) {
                if (rotationAnimator?.isRunning != true) {
                    startRotation()
                }
            } else {
                stopRotation()
            }
            invalidate()
        }

    var progress: Float = 0.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (isEinkMode) {
                val spoolState = kinematics.calculate(field, baseHubDimension)
                topReelAngle = (field * 360f * 3f * spoolState.leftAngularSpeed) % 360f
                bottomReelAngle = (field * 360f * 3f * spoolState.rightAngularSpeed) % 360f
                tapeTravelOffset = (field * 400f) % 40f
            }
            invalidate()
        }

    var currentLyricText: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isLyricsModeEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var isBitPerfect: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isEinkMode: Boolean = false
        set(value) {
            field = value
            if (value) stopRotation() else if (isPlaying) startRotation()
            invalidate()
        }

    var trackTitle: String = ""
        set(value) {
            field = value
            marqueeOffset = 0f
            invalidate()
        }

    var isSongLoaded: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var deviceName: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var androidVersionText: String = ""
        set(value) {
            field = value
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

    var outputRoute: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var bluetoothDeviceName: String? = null
        set(value) {
            field = value
            invalidate()
        }

    var bluetoothBatteryPct: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var isBluetoothConnected: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var bluetoothConnectionStatus: String = "DISCONNECTED"
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

    var batteryLevel: Int = 100
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    // Dynamic Album Cover Accent Color with smooth transition animation
    var coverAccentColor: Int = Color.parseColor("#F97316")
        private set

    // Dynamic Album Artwork Sticker on Cassette Shell
    var albumArtBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

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
    var onEjectLongClicked: (() -> Unit)? = null
    var onSeek: ((Float) -> Unit)? = null
    var onDoubleTapChassis: (() -> Unit)? = null
    var onTitleClicked: (() -> Unit)? = null

    // Touch & interaction tracking
    private var isDraggingProgress = false
    private var pressedButtonIndex = -1 // 0: REW, 1: FWD, 2: PLAY, 3: EJECT
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var holdSeekRunnable: Runnable? = null
    private var holdEjectRunnable: Runnable? = null
    private var pendingSingleTapRunnable: Runnable? = null
    private var isHoldSeeking = false
    private var isHoldEjecting = false
    private var lastTapButtonIndex = -1
    private var lastTapTime = 0L

    private val chassisGestureDetector by lazy {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (pressedButtonIndex == -1 && !isDraggingProgress && !isTouchInsideButtonsOrProgress(e.x, e.y)) {
                    onDoubleTapChassis?.invoke()
                    return true
                }
                return false
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isSongPresent() || isSongLoaded) {
                    val isSpineArea = (e.x <= centerWindowRect.left && e.y >= centerWindowRect.top && e.y <= centerWindowRect.bottom)
                    val isCassetteArea = centerWindowRect.contains(e.x, e.y) || cassetteRect.contains(e.x, e.y)
                    if (isSpineArea || isCassetteArea) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTitleClicked?.invoke()
                        return true
                    }
                }
                if (e.x <= centerWindowRect.left && e.y >= centerWindowRect.top && e.y <= centerWindowRect.bottom) {
                    isLyricsModeEnabled = !isLyricsModeEnabled
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    invalidate()
                    return true
                }
                return false
            }
            override fun onDown(e: MotionEvent): Boolean = true
        })
    }

    private fun isTouchInsideButtonsOrProgress(x: Float, y: Float): Boolean {
        return btnRewRect.contains(x, y) ||
               btnFwdRect.contains(x, y) ||
               btnPlayRect.contains(x, y) ||
               btnEjectRect.contains(x, y) ||
               (y >= ledBarRect.top - 25f && y <= ledBarRect.bottom + 25f)
    }

    // Kinetic Animation State
    private var rotationAnimator: ValueAnimator? = null
    private var headAnimator: ValueAnimator? = null
    private var headEngageProgress = 0f
    private var topReelAngle = 0f
    private var bottomReelAngle = 0f
    private var tapeTravelOffset = 0f
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
    private val ledRunActiveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledRunInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledPeakActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledPeakActiveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledPeakInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledBatteryAmberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledBatteryAmberGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val statusLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var batteryLabelX = 0f
    private var batteryLabelY = 0f
    private var activeLabelX = 0f
    private var activeLabelY = 0f

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
    private val routeBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var formatBadgeBaselineOffset = 0f
    private var routeBadgeBaselineOffset = 0f
    private val foilBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.12f
    }
    private val tapeMicroGroovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
        color = Color.argb(45, 255, 255, 255)
    }
    private val tapeMotionSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
    }
    private val tapeSpoolSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val spoolFlangeSpokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

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
    private val btnIconPath = Path()
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
        isClickable = true
        isFocusable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
        deviceName = com.hana.spindle.util.DeviceUtils.getDeviceName(context)
        androidVersionText = com.hana.spindle.util.DeviceUtils.getAndroidVersionString()
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
            orangeNotchPaint.apply { color = Color.WHITE; style = Paint.Style.FILL }

            tapeMotionSheenPaint.apply { color = Color.argb(120, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 1.4f }
            tapeSpoolSheenPaint.apply { color = Color.argb(90, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 1.2f }
            spoolFlangeSpokePaint.apply { color = Color.argb(120, 0, 0, 0); style = Paint.Style.FILL }

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
            ledRunActiveGlowPaint.apply { color = Color.TRANSPARENT; style = Paint.Style.STROKE; strokeWidth = 1f }
            ledRunInactivePaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            ledPeakActivePaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            ledPeakActiveGlowPaint.apply { color = Color.TRANSPARENT; style = Paint.Style.STROKE; strokeWidth = 1f }
            ledPeakInactivePaint.apply { color = Color.WHITE; style = Paint.Style.FILL }
            ledBatteryAmberPaint.apply { color = Color.BLACK; style = Paint.Style.FILL }
            ledBatteryAmberGlowPaint.apply { color = Color.TRANSPARENT; style = Paint.Style.STROKE; strokeWidth = 1f }
            statusLabelPaint.apply { color = Color.BLACK; style = Paint.Style.FILL; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.05f }

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

            ledRunActivePaint.apply { color = Color.parseColor("#00E676"); style = Paint.Style.FILL }
            ledRunActiveGlowPaint.apply { color = Color.argb(90, 0, 230, 118); style = Paint.Style.STROKE; strokeWidth = 2f }
            ledRunInactivePaint.apply { color = Color.parseColor("#A8CDB2"); style = Paint.Style.FILL }
            ledPeakActivePaint.apply { color = Color.parseColor("#EF4444"); style = Paint.Style.FILL }
            ledPeakActiveGlowPaint.apply { color = Color.argb(90, 239, 68, 68); style = Paint.Style.STROKE; strokeWidth = 2f }
            ledPeakInactivePaint.apply { color = Color.parseColor("#D9A4A8"); style = Paint.Style.FILL }
            ledBatteryAmberPaint.apply { color = Color.parseColor("#F59E0B"); style = Paint.Style.FILL }
            ledBatteryAmberGlowPaint.apply { color = Color.argb(90, 245, 158, 11); style = Paint.Style.STROKE; strokeWidth = 2f }
            statusLabelPaint.apply { color = Color.parseColor("#5A5E78"); style = Paint.Style.FILL; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.05f }

            buttonBasePaint.apply { color = Color.parseColor("#FAFAF9"); style = Paint.Style.FILL }
            buttonPressedPaint.apply { color = Color.parseColor("#E5E5E2"); style = Paint.Style.FILL }
            buttonHighlightPaint.apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
            buttonBorderPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.STROKE; strokeWidth = 2f }
            buttonIconPaint.apply { color = Color.parseColor("#2A2E45"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonLabelPaint.apply { color = Color.parseColor("#5A5E78"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonMutedIconPaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            buttonMutedLabelPaint.apply { color = Color.parseColor("#F97316"); style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        } else {
            // Dynamic Cassette Model (Dark, TDK, Maxell, BASF, Skeleton Reel)
            chassisPaint.apply { color = theme.chassisColor; style = Paint.Style.FILL }
            chassisBevelPaint.apply { color = theme.diagonalBezelColor; style = Paint.Style.STROKE; strokeWidth = 3f }
            slotBevelPaint.apply { color = theme.diagonalBezelColor; style = Paint.Style.STROKE; strokeWidth = 2.5f }
            slotShadowPaint.apply { color = theme.cardBorderColor; style = Paint.Style.FILL }

            screwWellPaint.apply { color = theme.diagonalBezelColor; style = Paint.Style.FILL }
            screwHeadPaint.apply { color = theme.surfaceColor; style = Paint.Style.FILL }
            screwHighlightPaint.apply { color = Color.argb(60, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2f }
            screwGroovePaint.apply { color = theme.diagonalBezelColor; style = Paint.Style.STROKE; strokeWidth = 2.5f }

            cassetteShellPaint.apply { color = theme.shellColor; style = Paint.Style.FILL }
            cassetteBorderPaint.apply { color = theme.cardBorderColor; style = Paint.Style.STROKE; strokeWidth = 2.0f }
            cassetteHighlightLinePaint.apply { color = Color.argb(120, 250, 250, 249); style = Paint.Style.STROKE; strokeWidth = 1.0f }
            cassetteInnerLipPaint.apply { color = theme.cardBorderColor; style = Paint.Style.STROKE; strokeWidth = 1.5f }
            cassetteOutlinePaint.apply { color = theme.cardBorderColor; style = Paint.Style.STROKE; strokeWidth = 1.6f }
            cassetteSubtleOutlinePaint.apply { color = Color.argb(80, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.3f }
            cassetteScrewBossPaint.apply { color = theme.cardBorderColor; style = Paint.Style.STROKE; strokeWidth = 1.5f }
            cassetteCutoutPaint.apply { color = theme.surfaceColor; style = Paint.Style.FILL }
            cassetteGuidePaint.apply { color = theme.cardBorderColor; style = Paint.Style.STROKE; strokeWidth = 1.5f }

            windowPanelPaint.apply { color = theme.surfaceColor; style = Paint.Style.FILL }
            windowPanelBorderPaint.apply { color = theme.cardBorderColor; style = Paint.Style.STROKE; strokeWidth = 2f }
            windowGlassHighlightPaint.apply { color = theme.windowTint; style = Paint.Style.FILL }

            centerWindowPaint.apply { color = theme.surfaceColor; style = Paint.Style.FILL }
            centerWindowBorderPaint.apply { color = theme.cardBorderColor; style = Paint.Style.STROKE; strokeWidth = 1.6f }

            tapeSpoolPaint.apply { color = theme.tapeRibbonColor; style = Paint.Style.FILL }
            tapeShellSpoolPaint.apply { color = Color.argb(85, Color.red(theme.tapeRibbonColor), Color.green(theme.tapeRibbonColor), Color.blue(theme.tapeRibbonColor)); style = Paint.Style.FILL }
            tapeTexturePaint.apply { color = Color.argb(60, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.2f }
            tapeBridgePaint.apply { color = Color.argb(180, Color.red(theme.tapeRibbonColor), Color.green(theme.tapeRibbonColor), Color.blue(theme.tapeRibbonColor)); style = Paint.Style.FILL }
            tapePathPaint.apply { color = Color.argb(125, Color.red(theme.tapeRibbonColor), Color.green(theme.tapeRibbonColor), Color.blue(theme.tapeRibbonColor)); style = Paint.Style.STROKE; strokeWidth = 5f }
            tapePathHighlightPaint.apply { color = Color.argb(30, 250, 250, 249); style = Paint.Style.STROKE; strokeWidth = 1.2f }

            pressurePadSpringPaint.apply { color = Color.parseColor("#B45309"); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            pressurePadFeltPaint.apply { color = Color.parseColor("#FB7185"); style = Paint.Style.FILL }
            tapeHeadChassisPaint.apply { color = Color.parseColor("#788294"); style = Paint.Style.FILL }
            tapeHeadBevelPaint.apply { color = Color.parseColor("#B0B4CE"); style = Paint.Style.STROKE; strokeWidth = 2f }
            tapeHeadCorePaint.apply { color = Color.parseColor("#1E2132"); style = Paint.Style.FILL }

            hubRimPaint.apply { color = theme.reelHubColor; style = Paint.Style.FILL }
            hubTeethPaint.apply { color = theme.surfaceColor; style = Paint.Style.FILL }
            hubInnerCapPaint.apply { color = theme.surfaceColor; style = Paint.Style.FILL }
            hubCenterPipPaint.apply { color = theme.diagonalBezelColor; style = Paint.Style.FILL }
            hubClutchDimplePaint.apply { color = theme.accentColor; style = Paint.Style.FILL }
            orangeNotchPaint.apply { color = theme.accentColor; style = Paint.Style.FILL }

            clockGhostPaint.apply { color = theme.surfaceColor; style = Paint.Style.FILL }
            clockLitPaint.apply { color = theme.vfdGlowColor; style = Paint.Style.FILL }

            titleTextPaint.apply { color = theme.textPrimaryColor; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            artistTextPaint.apply { color = theme.textSecondaryColor; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
            spindleLogoPaint.apply { color = theme.textPrimaryColor; textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.12f }
            spindleSubtextPaint.apply { color = theme.textSecondaryColor; textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); letterSpacing = 0.16f }
            cassetteBadgePaint.apply { color = theme.labelTextColor; textAlign = Paint.Align.LEFT; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); letterSpacing = 0.08f }

            acrylicSheenPaint.apply { color = Color.argb(14, 250, 250, 249); style = Paint.Style.FILL }
            acrylicLinePaint.apply { color = Color.argb(45, 250, 250, 249); style = Paint.Style.STROKE; strokeWidth = 1.5f }

            ledInactivePaint.apply { color = theme.surfaceColor; style = Paint.Style.FILL }
            ledActivePaint.apply { color = theme.accentColor; style = Paint.Style.FILL }
            ledActiveGlowPaint.apply { color = Color.argb(90, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor)); style = Paint.Style.STROKE; strokeWidth = 3f }
            ledBorderPaint.apply { color = theme.cardBorderColor; style = Paint.Style.STROKE; strokeWidth = 1.5f }

            ledRunActivePaint.apply { color = Color.parseColor("#00E676"); style = Paint.Style.FILL }
            ledRunActiveGlowPaint.apply { color = Color.argb(100, 0, 230, 118); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            ledRunInactivePaint.apply { color = Color.parseColor("#143521"); style = Paint.Style.FILL }
            ledPeakActivePaint.apply { color = Color.parseColor("#EF4444"); style = Paint.Style.FILL }
            ledPeakActiveGlowPaint.apply { color = Color.argb(100, 239, 68, 68); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            ledPeakInactivePaint.apply { color = Color.parseColor("#45181C"); style = Paint.Style.FILL }
            ledBatteryAmberPaint.apply { color = Color.parseColor("#F59E0B"); style = Paint.Style.FILL }
            ledBatteryAmberGlowPaint.apply { color = Color.argb(100, 245, 158, 11); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            statusLabelPaint.apply { color = Color.parseColor("#94A3B8"); style = Paint.Style.FILL; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.05f }

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
            letterSpacing = 0.04f
        }
        routeBadgeTextPaint.apply {
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.06f
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
            orangeNotchPaint.color = Color.WHITE
            labelBadgeBgPaint.color = Color.BLACK
            labelBadgeTextPaint.color = Color.WHITE
            formatBadgeBgPaint.color = Color.WHITE
            formatBadgeBorderPaint.color = Color.BLACK
            formatBadgeTextPaint.color = Color.BLACK
            routeBadgeTextPaint.color = Color.BLACK
            deckGuidePinPaint.color = Color.BLACK
            tapeWindowGaugePaint.color = Color.BLACK
            tapeWindowGaugeTextPaint.color = Color.BLACK
            tapeMotionSheenPaint.color = Color.argb(120, 0, 0, 0)
            tapeSpoolSheenPaint.color = Color.argb(90, 0, 0, 0)
            spoolFlangeSpokePaint.color = Color.argb(120, 0, 0, 0)
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
        routeBadgeTextPaint.color = if (isBitPerfect) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")

        tapeMotionSheenPaint.color = if (isLight) Color.argb(60, 42, 46, 69) else Color.argb(95, 255, 255, 255)
        tapeSpoolSheenPaint.color = if (isLight) Color.argb(55, 42, 46, 69) else Color.argb(70, 255, 255, 255)
        spoolFlangeSpokePaint.color = if (isLight) Color.argb(85, 42, 46, 69) else Color.argb(95, 255, 255, 255)

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
        val btnBottom = h * 0.982f
        val btnH = h * 0.115f
        val btnTop = btnBottom - btnH
        val btnGap = w * 0.015f
        val availableW = w - (btnMarginH * 2f)
        val btnW = (availableW - (btnGap * 3f)) / 4f
        val totalBtnClusterW = 4f * btnW + 3f * btnGap
        val startX = (w - totalBtnClusterW) * 0.5f

        btnRewRect.set(startX, btnTop, startX + btnW, btnBottom)
        btnFwdRect.set(btnRewRect.right + btnGap, btnTop, btnRewRect.right + btnGap + btnW, btnBottom)
        btnPlayRect.set(btnFwdRect.right + btnGap, btnTop, btnFwdRect.right + btnGap + btnW, btnBottom)
        btnEjectRect.set(btnPlayRect.right + btnGap, btnTop, btnPlayRect.right + btnGap + btnW, btnBottom)

        buttonIconPaint.textSize = btnH * 0.22f
        buttonLabelPaint.textSize = btnH * 0.155f
        buttonMutedIconPaint.textSize = btnH * 0.22f
        buttonMutedLabelPaint.textSize = btnH * 0.155f

        // 2. 12-LED Progress Bar directly above the bottom buttons (height reduced by half)
        val barH = h * 0.011f
        val barGap = h * 0.012f
        val barBottom = btnTop - barGap
        val barTop = barBottom - barH
        val ledMarginH = startX
        ledBarRect.set(
            ledMarginH,
            barTop,
            w - ledMarginH,
            barBottom
        )

        // 3. Center Battery & Active Status LEDs with "Battery" on left and "Active" on right
        val statusLedH = barH
        val statusLedW = w * 0.080f
        statusLabelPaint.textSize = w * 0.024f

        val batteryText = "Battery"
        val activeText = "Active"
        val batteryTextW = statusLabelPaint.measureText(batteryText)
        val activeTextW = statusLabelPaint.measureText(activeText)
        val labelPillGap = w * 0.014f
        val centerClusterGap = w * 0.040f

        val totalClusterW = batteryTextW + labelPillGap + statusLedW + centerClusterGap + statusLedW + labelPillGap + activeTextW
        val clusterLeft = (w - totalClusterW) * 0.5f

        val statusLedBottom = barTop - h * 0.018f
        val statusLedTop = statusLedBottom - statusLedH

        batteryLabelX = clusterLeft
        batteryLabelY = statusLedTop + (statusLedH * 0.5f) - ((statusLabelPaint.descent() + statusLabelPaint.ascent()) * 0.5f)

        val batteryLedLeft = batteryLabelX + batteryTextW + labelPillGap
        val batteryLedRight = batteryLedLeft + statusLedW
        ledPeakRect.set(batteryLedLeft, statusLedTop, batteryLedRight, statusLedBottom)

        val activeLedLeft = batteryLedRight + centerClusterGap
        val activeLedRight = activeLedLeft + statusLedW
        ledRunRect.set(activeLedLeft, statusLedTop, activeLedRight, statusLedBottom)

        activeLabelX = activeLedRight + labelPillGap
        activeLabelY = batteryLabelY

        // 4. Cassette FULL HEIGHT: fills entire space above the LEDs/meter ("cassette full height")
        val slotInset = w * 0.008f
        val cTop = h * 0.020f
        val cBottom = statusLedTop - h * 0.014f - slotInset
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

        // Center align Nameplate & Time in the column between left frame (innerShellRect.left) and clear spindle frame (winLeft)
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

        formatBadgeTextPaint.textSize = cw * 0.021f
        routeBadgeTextPaint.textSize = cw * 0.018f

        val fmFormat = formatBadgeTextPaint.fontMetrics
        formatBadgeBaselineOffset = -(fmFormat.ascent + fmFormat.descent) * 0.5f

        val fmRoute = routeBadgeTextPaint.fontMetrics
        routeBadgeBaselineOffset = -(fmRoute.ascent + fmRoute.descent) * 0.5f
        tapeWindowGaugeTextPaint.textSize = cw * 0.020f
        labelBadgeTextPaint.textSize = cw * 0.020f
        foilBadgePaint.textSize = cw * 0.020f

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

        // 5. Draw Bottom-Left Spindle Branding or Now Playing Song Info (Rotated -90° Vertical)
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

        // 11. Specular Hot-Stamped Metallic Foil Stamping
        val isTdk = (theme.id == com.hana.spindle.theme.CassetteTheme.TDK_SA_90.id)
        val isMaxell = (theme.id == com.hana.spindle.theme.CassetteTheme.MAXELL_XLII.id)
        val isBasf = (theme.id == com.hana.spindle.theme.CassetteTheme.BASF_CHROME.id)

        if (isTdk || isMaxell || isBasf) {
            val foilText = when {
                isTdk -> "TDK SA-90 • HIGH BIAS 70µs EQ"
                isMaxell -> "MAXELL XLII-S • POSITION HIGH"
                else -> "BASF CHROME EXTRA • IEC II"
            }
            val foilX = cassetteRect.left + cw * 0.40f
            val foilY = cassetteRect.top + ch * 0.052f

            val foilColor1 = if (isTdk) Color.parseColor("#FEF08A") else if (isMaxell) Color.parseColor("#FFFFFF") else Color.parseColor("#FEF08A")
            val foilColor2 = if (isTdk) Color.parseColor("#D4AF37") else if (isMaxell) Color.parseColor("#94A3B8") else Color.parseColor("#EAB308")

            foilBadgePaint.shader = LinearGradient(
                foilX - 80f, foilY, foilX + 80f, foilY,
                intArrayOf(foilColor1, foilColor2, foilColor1, foilColor2),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawText(foilText, foilX, foilY, foilBadgePaint)
        }
    }

    /**
     * Draws the centered dual deck status LEDs ("Battery" on left, "Active" on right).
     */
    private fun drawStatusLeds(canvas: Canvas) {
        val isEink = (theme.id == CassetteTheme.MONOCHROME_EINK.id)

        // 1. "Battery" text on left of battery led bar
        canvas.drawText("Battery", batteryLabelX, batteryLabelY, statusLabelPaint)

        // 2. Battery LED:
        // Always OFF normally; amber if low (<= 20%); red if critical low (<= 10%); blinking red if almost depleted (<= 5%).
        val cornerR = ledPeakRect.height() * 0.35f
        when {
            batteryLevel > 20 -> {
                // Normal: default OFF state -> muted red
                if (isEink) {
                    canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledInactivePaint)
                } else {
                    canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledPeakInactivePaint)
                }
                canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledBorderPaint)
            }
            batteryLevel in 11..20 -> {
                // Low: amber
                if (isEink) {
                    canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledActivePaint)
                } else {
                    canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledBatteryAmberPaint)
                    canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledBatteryAmberGlowPaint)
                    canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledBorderPaint)
                }
            }
            batteryLevel in 6..10 -> {
                // Critical low: red
                if (isEink) {
                    canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledActivePaint)
                } else {
                    canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledPeakActivePaint)
                    canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledPeakActiveGlowPaint)
                    canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledBorderPaint)
                }
            }
            else -> {
                // Almost depleted (<= 5%): blinking red
                val isBlinkOn = (System.currentTimeMillis() / 450L) % 2L == 0L
                postInvalidateDelayed(225L)
                if (isBlinkOn) {
                    if (isEink) {
                        canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledActivePaint)
                    } else {
                        canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledPeakActivePaint)
                        canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledPeakActiveGlowPaint)
                    }
                } else {
                    if (isEink) {
                        canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledInactivePaint)
                    } else {
                        canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledPeakInactivePaint)
                    }
                }
                canvas.drawRoundRect(ledPeakRect, cornerR, cornerR, ledBorderPaint)
            }
        }

        // 3. Play status LED:
        // Always green when song is played; off if no sound; blinking green when near end of song.
        val runCornerR = ledRunRect.height() * 0.35f
        if (!isPlaying) {
            // Off if no sound -> default muted green
            if (isEink) {
                canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledInactivePaint)
            } else {
                canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledRunInactivePaint)
            }
            canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledBorderPaint)
        } else {
            // Song is played: check if near end of song (progress >= 0.94f)
            val isNearEnd = progress >= 0.94f
            if (isNearEnd) {
                // Blinking when near end of song
                val isBlinkOn = (System.currentTimeMillis() / 380L) % 2L == 0L
                postInvalidateDelayed(190L)
                if (isBlinkOn) {
                    if (isEink) {
                        canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledActivePaint)
                    } else {
                        canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledRunActivePaint)
                        canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledRunActiveGlowPaint)
                    }
                } else {
                    if (isEink) {
                        canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledInactivePaint)
                    } else {
                        canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledRunInactivePaint)
                    }
                }
            } else {
                // Always green when song is played
                if (isEink) {
                    canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledActivePaint)
                } else {
                    canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledRunActivePaint)
                    canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledRunActiveGlowPaint)
                }
            }
            canvas.drawRoundRect(ledRunRect, runCornerR, runCornerR, ledBorderPaint)
        }

        // 4. "Active" text on right of status led
        canvas.drawText("Active", activeLabelX, activeLabelY, statusLabelPaint)
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

        // Animated traveling tape ribbon texture gliding from supply to take-up spool
        val bridgeH = bottomHubCenter.y - topHubCenter.y
        if (bridgeH > 0f) {
            val step = bridgeWidth * 0.75f
            val count = (bridgeH / step).toInt() + 1
            for (i in 0 until count) {
                val lineY = topHubCenter.y + ((i * step + tapeTravelOffset) % bridgeH)
                canvas.drawLine(tempRectF.left + 2f, lineY, tempRectF.right - 2f, lineY, tapeMotionSheenPaint)
            }
        }

        // Top Spool Tape Pack (Supply)
        canvas.drawCircle(topHubCenter.x, topHubCenter.y, rTopTape, tapeSpoolPaint)
        canvas.drawCircle(topHubCenter.x, topHubCenter.y, rTopTape * 0.94f, tapeTexturePaint)
        canvas.drawCircle(topHubCenter.x, topHubCenter.y, rTopTape * 0.88f, tapeTexturePaint)
        canvas.drawCircle(topHubCenter.x, topHubCenter.y, rTopTape * 0.82f, tapeTexturePaint)
        if (rTopTape > hubOuterRadius + 8f) {
            val deltaR = rTopTape - hubOuterRadius
            for (g in 1..3) {
                canvas.drawCircle(topHubCenter.x, topHubCenter.y, hubOuterRadius + deltaR * (g / 4f), tapeMicroGroovePaint)
            }
        }
        drawSpoolRotatingSheenAndSpokes(canvas, topHubCenter.x, topHubCenter.y, hubOuterRadius, rTopTape, topReelAngle)

        // Bottom Spool Tape Pack (Take-up)
        canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, rBottomTape, tapeSpoolPaint)
        canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, rBottomTape * 0.94f, tapeTexturePaint)
        canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, rBottomTape * 0.88f, tapeTexturePaint)
        canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, rBottomTape * 0.82f, tapeTexturePaint)
        if (rBottomTape > hubOuterRadius + 8f) {
            val deltaR = rBottomTape - hubOuterRadius
            for (g in 1..3) {
                canvas.drawCircle(bottomHubCenter.x, bottomHubCenter.y, hubOuterRadius + deltaR * (g / 4f), tapeMicroGroovePaint)
            }
        }
        drawSpoolRotatingSheenAndSpokes(canvas, bottomHubCenter.x, bottomHubCenter.y, hubOuterRadius, rBottomTape, bottomReelAngle)

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
    private fun getFormatRowText(): String {
        return if (audioFormat.isNotEmpty()) audioFormat else "HI-RES AUDIO"
    }

    private fun getRouteRowText(): String {
        return when {
            outputRoute.contains("USB", ignoreCase = true) -> if (isBitPerfect) "USB DIRECT" else "USB DAC"
            outputRoute.contains("3.5mm", ignoreCase = true) -> if (isBitPerfect) "3.5mm DIRECT" else "3.5mm OUTPUT"
            outputRoute.contains("Bluetooth", ignoreCase = true) || isBluetoothConnected -> {
                val btNameShort = bluetoothDeviceName?.takeIf { it.isNotBlank() } ?: "BLUETOOTH"
                val batteryPart = if (bluetoothBatteryPct != null && bluetoothBatteryPct!! >= 0) " (${bluetoothBatteryPct}%)" else ""
                "$btNameShort$batteryPart"
            }
            outputRoute.contains("Speaker", ignoreCase = true) -> if (isBitPerfect) "SPEAKER DIRECT" else "SPEAKER"
            isBitPerfect -> "DIRECT ALSA"
            outputRoute.isNotBlank() -> outputRoute.uppercase(Locale.ROOT)
            else -> "3.5mm DIRECT"
        }
    }

    private fun isSongPresent(): Boolean {
        val hardwareName = com.hana.spindle.util.DeviceUtils.getHardwareDeviceName()
        return isSongLoaded && trackTitle.isNotBlank() &&
                trackTitle != "No Track Loaded" &&
                trackTitle != deviceName &&
                trackTitle != hardwareName &&
                trackTitle != com.hana.spindle.util.DeviceUtils.getDeviceName(context)
    }

    private fun getAudioBadgeWidth(cw: Float): Float {
        val row1Text = getFormatRowText()
        val row2Text = getRouteRowText()
        val formatW = formatBadgeTextPaint.measureText(row1Text)
        val routeW = routeBadgeTextPaint.measureText(row2Text)
        return maxOf(formatW, routeW) + cw * 0.048f
    }

    private fun drawAudioFormatBadgeBelowTime(canvas: Canvas) {
        val hasSong = isSongPresent()
        if (!hasSong) return

        val cw = cassetteRect.width()
        val ch = cassetteRect.height()

        val digitH = cw * 0.052f
        val digitW = digitH * 0.52f
        val digitGap = digitW * 0.22f
        val colonW = digitW * 0.32f
        val clockLength = digitW * 4f + digitGap * 2.4f + colonW
        val clockBottomY = centerWindowRect.top + clockLength + ch * 0.008f

        val row1Text = getFormatRowText()
        val row2Text = getRouteRowText()

        val badgeW = getAudioBadgeWidth(cw)
        val badgeH = cw * 0.072f
        val badgeGap = ch * 0.016f
        val badgeCenterY = clockBottomY + badgeGap + badgeW * 0.5f

        canvas.save()
        canvas.translate(columnCenterX, badgeCenterY)
        canvas.rotate(-90f)

        formatBadgeRect.set(-badgeW * 0.5f, -badgeH * 0.5f, badgeW * 0.5f, badgeH * 0.5f)
        val cornerRadius = badgeH * 0.28f
        canvas.drawRoundRect(formatBadgeRect, cornerRadius, cornerRadius, formatBadgeBgPaint)
        canvas.drawRoundRect(formatBadgeRect, cornerRadius, cornerRadius, formatBadgeBorderPaint)

        // Subtle divider hairline between Row 1 (format) and Row 2 (route destination)
        val dividerMargin = cw * 0.024f
        canvas.drawLine(-badgeW * 0.5f + dividerMargin, 0f, badgeW * 0.5f - dividerMargin, 0f, formatBadgeBorderPaint)

        // Row 1: Audio format specification (centered with ample line height in top half [-badgeH * 0.5f, 0f])
        val row1Y = -badgeH * 0.25f + formatBadgeBaselineOffset
        canvas.drawText(
            row1Text,
            0f,
            row1Y,
            formatBadgeTextPaint
        )

        // Row 2: Real-time Output Routing Destination (centered with ample line height in bottom half [0f, badgeH * 0.5f])
        val row2Y = badgeH * 0.25f + routeBadgeBaselineOffset
        canvas.drawText(
            row2Text,
            0f,
            row2Y,
            routeBadgeTextPaint
        )

        canvas.restore()
    }

    /**
     * Draws the SPINDLE Player branding (default idle) or Now Playing Track Info (when a song is played)
     * at the bottom-left of the cassette shell, center-aligned in the column between the left frame
     * and the clear spindle frame (Rotated -90° Vertical).
     *
     * In default idle state (no track loaded):
     * - Line 0: SPINDLE (crisp bold white typography)
     * - Line 1: Deck (silver/slate subtext)
     *
     * When a song is played / loaded:
     * - Line 0: Song Title (prominent bold white with smooth marquee scrolling when playing)
     * - Line 1: Artist Name • song time / total duration (e.g. Linkin Park • 01:24 / 03:24)
     */
    private fun drawVerticalSpindleBranding(canvas: Canvas) {
        val hasSong = isSongPresent()

        val cw = cassetteRect.width()
        val ch = cassetteRect.height()

        val originX = columnCenterX
        val originY = centerWindowRect.bottom - ch * 0.008f

        // Compute available vertical length along +X before reaching clock or format badge
        val digitH = cw * 0.052f
        val digitW = digitH * 0.52f
        val digitGap = digitW * 0.22f
        val colonW = digitW * 0.32f
        val clockLength = digitW * 4f + digitGap * 2.4f + colonW
        val clockBottomY = centerWindowRect.top + clockLength + ch * 0.008f

        val badgeW = getAudioBadgeWidth(cw)
        val badgeGap = ch * 0.016f
        val badgeBottomY = clockBottomY + badgeGap + badgeW

        val maxAvailableLength = if (hasSong) {
            ((originY - badgeBottomY) - ch * 0.028f).coerceAtLeast(80f)
        } else {
            ((originY - clockBottomY) - ch * 0.035f).coerceAtLeast(100f)
        }

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
            val defaultHw = com.hana.spindle.util.DeviceUtils.getHardwareDeviceName()
            val displayName = if (trackTitle.isNotBlank() && trackTitle != "No Track Loaded") {
                trackTitle
            } else if (deviceName.isNotBlank()) {
                deviceName
            } else {
                defaultHw
            }
            val displayVersion = if (androidVersionText.isNotBlank()) androidVersionText else com.hana.spindle.util.DeviceUtils.getAndroidVersionString()

            spindleLogoPaint.letterSpacing = 0.08f
            spindleSubtextPaint.letterSpacing = 0.10f

            // Line 0: Hardware / Device Name (e.g. SONY SO-02J)
            val measuredNameW = spindleLogoPaint.measureText(displayName)
            if (measuredNameW > maxAvailableLength) {
                spindleLogoPaint.letterSpacing = 0.02f
                val recheckW = spindleLogoPaint.measureText(displayName)
                if (recheckW > maxAvailableLength) {
                    val budget = maxAvailableLength - spindleLogoPaint.measureText("…")
                    var trimmed = displayName
                    while (trimmed.isNotEmpty() && spindleLogoPaint.measureText(trimmed) > budget) {
                        trimmed = trimmed.dropLast(1)
                    }
                    canvas.drawText("${trimmed.trimEnd()}…", 0f, line0Y, spindleLogoPaint)
                } else {
                    canvas.drawText(displayName, 0f, line0Y, spindleLogoPaint)
                }
            } else {
                canvas.drawText(displayName, 0f, line0Y, spindleLogoPaint)
            }

            // Line 1: Android Version & Dessert Codename (e.g. ANDROID 8.0 • OREO)
            val measuredVerW = spindleSubtextPaint.measureText(displayVersion)
            if (measuredVerW > maxAvailableLength) {
                spindleSubtextPaint.letterSpacing = 0.02f
                val recheckVerW = spindleSubtextPaint.measureText(displayVersion)
                if (recheckVerW > maxAvailableLength) {
                    val budget = maxAvailableLength - spindleSubtextPaint.measureText("…")
                    var trimmed = displayVersion
                    while (trimmed.isNotEmpty() && spindleSubtextPaint.measureText(trimmed) > budget) {
                        trimmed = trimmed.dropLast(1)
                    }
                    canvas.drawText("${trimmed.trimEnd()}…", 0f, line1Y, spindleSubtextPaint)
                } else {
                    canvas.drawText(displayVersion, 0f, line1Y, spindleSubtextPaint)
                }
            } else {
                canvas.drawText(displayVersion, 0f, line1Y, spindleSubtextPaint)
            }
        } else {
            spindleLogoPaint.letterSpacing = 0.02f
            spindleSubtextPaint.letterSpacing = 0.02f

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

            // Line 1: Live synchronized lyrics ticker or Artist • Time
            val isShowingLyrics = isLyricsModeEnabled && currentLyricText.isNotBlank()
            val curTimeStr = formatTime(currentTimeMs)
            val totTimeStr = formatTime(durationMs)
            val artistPart = if (artistName.isNotEmpty() && artistName != "Unknown Artist") artistName else "Unknown Artist"
            val subtitle = if (isShowingLyrics) {
                currentLyricText
            } else {
                "$artistPart • $curTimeStr / $totTimeStr"
            }

            val origSubColor = spindleSubtextPaint.color
            if (isShowingLyrics) {
                spindleSubtextPaint.color = theme.vfdGlowColor
            }

            val measuredSubW = spindleSubtextPaint.measureText(subtitle)
            if (measuredSubW > maxAvailableLength) {
                if (isShowingLyrics) {
                    val budget = maxAvailableLength - spindleSubtextPaint.measureText("…")
                    var trimmed = subtitle
                    while (trimmed.isNotEmpty() && spindleSubtextPaint.measureText(trimmed) > budget) {
                        trimmed = trimmed.dropLast(1)
                    }
                    canvas.drawText("${trimmed.trimEnd()}…", 0f, line1Y, spindleSubtextPaint)
                } else {
                    val durSuffix = " • $curTimeStr / $totTimeStr"
                    val budgetForArtist = maxAvailableLength - spindleSubtextPaint.measureText("…$durSuffix")
                    var trimmedArtist = artistPart
                    while (trimmedArtist.isNotEmpty() && spindleSubtextPaint.measureText("$trimmedArtist…$durSuffix") > maxAvailableLength) {
                        trimmedArtist = trimmedArtist.dropLast(1)
                    }
                    canvas.drawText("${trimmedArtist.trimEnd()}…$durSuffix", 0f, line1Y, spindleSubtextPaint)
                }
            } else {
                canvas.drawText(subtitle, 0f, line1Y, spindleSubtextPaint)
            }
            spindleSubtextPaint.color = origSubColor
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

        val cornerR = 2f
        for (i in 0 until count) {
            val segLeft = ledBarRect.left + (i * (segW + gap))
            val segRight = segLeft + segW
            tempRectF.set(segLeft, ledBarRect.top, segRight, ledBarRect.bottom)

            // Draw recessed slot background
            canvas.drawRoundRect(tempRectF, cornerR, cornerR, ledInactivePaint)
            canvas.drawRoundRect(tempRectF, cornerR, cornerR, ledBorderPaint)

            // Draw glowing lit segment
            if (i < litThreshold || (i == 0 && progress > 0f && isPlaying)) {
                canvas.drawRoundRect(tempRectF, cornerR, cornerR, ledActivePaint)
                canvas.drawRoundRect(tempRectF, cornerR, cornerR, ledActiveGlowPaint)
            }
        }
    }

    /**
     * Draws the 4 tactile bottom buttons: REW, FWD, PLAY, and EJECT.
     */
    private fun drawBottomButtons(canvas: Canvas) {
        drawKeyButton(canvas, btnRewRect, 0, "REW")
        drawKeyButton(canvas, btnFwdRect, 1, "FWD")
        drawPlayKeyButton(canvas, btnPlayRect, 2)
        drawKeyButton(canvas, btnEjectRect, 3, "EJECT")
    }

    private fun drawKeyButton(canvas: Canvas, rect: RectF, index: Int, label: String) {
        val isPressed = pressedButtonIndex == index
        val r = 10f

        canvas.drawRoundRect(rect, r, r, if (isPressed) buttonPressedPaint else buttonBasePaint)
        canvas.drawRoundRect(rect, r, r, buttonBorderPaint)
        if (!isPressed) {
            canvas.drawRoundRect(rect, r, r, buttonHighlightPaint)
        }

        val offsetY = if (isPressed) 3f else 0f
        val btnH = rect.height()
        val s = btnH * 0.125f // Icon half-extent
        val iconH = s * 2f
        val iconLabelGap = btnH * 0.085f

        // Calculate exact font cap-height so icon + label block is perfectly centered vertically
        val fontMetrics = buttonLabelPaint.fontMetrics
        val capHeight = -fontMetrics.ascent
        val totalBlockH = iconH + iconLabelGap + capHeight

        // Perfectly centered vertical anchor
        val blockTop = rect.centerY() - (totalBlockH * 0.5f) + offsetY
        val iconCenterX = rect.centerX()
        val iconCenterY = blockTop + s
        val labelY = blockTop + iconH + iconLabelGap + capHeight - (fontMetrics.descent * 0.4f)

        when (index) {
            0 -> {
                // REW: two identical left-facing triangles, cleanly spaced and centered
                val triW = s * 0.82f
                val triH = s * 0.82f
                val triGap = s * 0.20f
                val totalIconW = triW * 2f + triGap
                val leftEdge = iconCenterX - totalIconW * 0.5f

                btnIconPath.reset()
                // First triangle (left)
                btnIconPath.moveTo(leftEdge, iconCenterY)
                btnIconPath.lineTo(leftEdge + triW, iconCenterY - triH)
                btnIconPath.lineTo(leftEdge + triW, iconCenterY + triH)
                btnIconPath.close()

                // Second triangle (right)
                val rightTriLeft = leftEdge + triW + triGap
                btnIconPath.moveTo(rightTriLeft, iconCenterY)
                btnIconPath.lineTo(rightTriLeft + triW, iconCenterY - triH)
                btnIconPath.lineTo(rightTriLeft + triW, iconCenterY + triH)
                btnIconPath.close()

                canvas.drawPath(btnIconPath, buttonIconPaint)
            }
            1 -> {
                // FWD: two identical right-facing triangles, cleanly spaced and centered
                val triW = s * 0.82f
                val triH = s * 0.82f
                val triGap = s * 0.20f
                val totalIconW = triW * 2f + triGap
                val leftEdge = iconCenterX - totalIconW * 0.5f

                btnIconPath.reset()
                // First triangle (left)
                btnIconPath.moveTo(leftEdge + triW, iconCenterY)
                btnIconPath.lineTo(leftEdge, iconCenterY - triH)
                btnIconPath.lineTo(leftEdge, iconCenterY + triH)
                btnIconPath.close()

                // Second triangle (right)
                val rightTriLeft = leftEdge + triW + triGap
                btnIconPath.moveTo(rightTriLeft + triW, iconCenterY)
                btnIconPath.lineTo(rightTriLeft, iconCenterY - triH)
                btnIconPath.lineTo(rightTriLeft, iconCenterY + triH)
                btnIconPath.close()

                canvas.drawPath(btnIconPath, buttonIconPaint)
            }
            3 -> {
                // EJECT: upward-pointing triangle above a horizontal bar, perfectly centered
                val triW = s * 1.40f
                val halfW = triW * 0.5f
                val triH = s * 0.78f
                val ejectGap = s * 0.20f
                val barH = s * 0.26f
                val totalEjectH = triH + ejectGap + barH

                val ejectTop = iconCenterY - totalEjectH * 0.5f
                val triBottom = ejectTop + triH
                val barTop = triBottom + ejectGap
                val barBottom = barTop + barH

                btnIconPath.reset()
                btnIconPath.moveTo(iconCenterX, ejectTop)
                btnIconPath.lineTo(iconCenterX - halfW, triBottom)
                btnIconPath.lineTo(iconCenterX + halfW, triBottom)
                btnIconPath.close()
                canvas.drawPath(btnIconPath, buttonIconPaint)

                tempRectF.set(
                    iconCenterX - halfW,
                    barTop,
                    iconCenterX + halfW,
                    barBottom
                )
                canvas.drawRoundRect(tempRectF, 2f, 2f, buttonIconPaint)
            }
        }

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
        val btnH = rect.height()
        val s = btnH * 0.125f // Icon half-extent
        val iconH = s * 2f
        val iconLabelGap = btnH * 0.085f

        val activeLabelPaint = if (isPlaying) buttonMutedLabelPaint else buttonLabelPaint
        val activeIconPaint = if (isPlaying) buttonMutedIconPaint else buttonIconPaint

        val fontMetrics = activeLabelPaint.fontMetrics
        val capHeight = -fontMetrics.ascent
        val totalBlockH = iconH + iconLabelGap + capHeight

        val blockTop = rect.centerY() - (totalBlockH * 0.5f) + offsetY
        val iconCenterX = rect.centerX()
        val iconCenterY = blockTop + s
        val labelY = blockTop + iconH + iconLabelGap + capHeight - (fontMetrics.descent * 0.4f)

        if (isPlaying) {
            // Muted pressed/latched state - PAUSE (two vertical pill bars, perfectly centered)
            val barW = s * 0.38f
            val barH = s * 1.64f
            val barGap = s * 0.38f
            val totalPauseW = barW * 2f + barGap
            val leftEdge = iconCenterX - totalPauseW * 0.5f
            val barTop = iconCenterY - barH * 0.5f
            val barBottom = iconCenterY + barH * 0.5f

            tempRectF.set(leftEdge, barTop, leftEdge + barW, barBottom)
            canvas.drawRoundRect(tempRectF, 2.5f, 2.5f, activeIconPaint)

            val rightBarLeft = leftEdge + barW + barGap
            tempRectF.set(rightBarLeft, barTop, rightBarLeft + barW, barBottom)
            canvas.drawRoundRect(tempRectF, 2.5f, 2.5f, activeIconPaint)

            canvas.drawText("PAUSE", rect.centerX(), labelY, activeLabelPaint)
        } else {
            // Raised Play key - PLAY (one right-facing solid triangle, optically and geometrically centered)
            val triW = s * 1.30f
            val triH = s * 0.85f
            val baseLeft = iconCenterX - triW * 0.46f
            val tipRight = iconCenterX + triW * 0.54f

            btnIconPath.reset()
            btnIconPath.moveTo(tipRight, iconCenterY)
            btnIconPath.lineTo(baseLeft, iconCenterY - triH)
            btnIconPath.lineTo(baseLeft, iconCenterY + triH)
            btnIconPath.close()
            canvas.drawPath(btnIconPath, activeIconPaint)

            canvas.drawText("PLAY", rect.centerX(), labelY, activeLabelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        chassisGestureDetector.onTouchEvent(event)
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

                    // If EJECT (3), schedule hold-eject runnable after 600ms
                    if (pressedButtonIndex == 3) {
                        holdEjectRunnable?.let { gestureHandler.removeCallbacks(it) }
                        isHoldEjecting = false
                        val ejectHoldTimeout = ViewConfiguration.getLongPressTimeout().toLong().coerceAtLeast(600L)
                        holdEjectRunnable = Runnable {
                            isHoldEjecting = true
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onEjectLongClicked?.invoke()
                        }
                        gestureHandler.postDelayed(holdEjectRunnable!!, ejectHoldTimeout)
                    }
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDraggingProgress) {
                    updateProgressFromTouch(x)
                    return true
                }
                if (pressedButtonIndex == 3 && !btnEjectRect.contains(x, y)) {
                    holdEjectRunnable?.let { gestureHandler.removeCallbacks(it) }
                    holdEjectRunnable = null
                    isHoldEjecting = false
                    pressedButtonIndex = -1
                    invalidate()
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
                        3 -> {
                            holdEjectRunnable?.let { gestureHandler.removeCallbacks(it) }
                            holdEjectRunnable = null
                            if (isHoldEjecting) {
                                isHoldEjecting = false
                                return true
                            }
                            if (btnEjectRect.contains(x, y)) onEjectClicked?.invoke()
                        }
                    }
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isDraggingProgress = false
                parent?.requestDisallowInterceptTouchEvent(false)
                holdSeekRunnable?.let { gestureHandler.removeCallbacks(it) }
                holdSeekRunnable = null
                holdEjectRunnable?.let { gestureHandler.removeCallbacks(it) }
                holdEjectRunnable = null
                isHoldEjecting = false
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
        if (isEinkMode) {
            stopRotation()
            return
        }
        if (rotationAnimator?.isRunning == true) return
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val spoolState = kinematics.calculate(progress, baseHubDimension)
                topReelAngle = (topReelAngle + 4.5f * spoolState.leftAngularSpeed) % 360f
                bottomReelAngle = (bottomReelAngle + 4.5f * spoolState.rightAngularSpeed) % 360f
                tapeTravelOffset = (tapeTravelOffset + 2.8f * spoolState.leftAngularSpeed) % 40f
                invalidate()
            }
            start()
        }
    }

    /**
     * Draws kinetic rotating elements on the tape pack: anisotropic specular sheen wedges,
     * classic 3-spoke flange strobe markers, and the physical tape anchor notch.
     */
    private fun drawSpoolRotatingSheenAndSpokes(canvas: Canvas, cx: Float, cy: Float, innerR: Float, outerR: Float, rotationAngle: Float) {
        if (outerR <= innerR + 4f) return

        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)

        // 1. Anisotropic Specular Sheen (2 opposing radial glare cones at 0° and 180°)
        for (cone in 0..1) {
            val baseAngle = cone * 180.0
            for (lineOffset in -1..1) {
                val rad = Math.toRadians(baseAngle + lineOffset * 9.0)
                val cos = Math.cos(rad).toFloat()
                val sin = Math.sin(rad).toFloat()
                canvas.drawLine(
                    cx + innerR * cos,
                    cy + innerR * sin,
                    cx + outerR * cos,
                    cy + outerR * sin,
                    tapeSpoolSheenPaint
                )
            }
        }

        // 2. Three Classic Reel Flange Spoke Windows / Strobe Cutouts (at 0°, 120°, 240°)
        val spokeR = innerR + (outerR - innerR) * 0.38f
        val markerRadius = ((outerR - innerR) * 0.12f).coerceIn(2.5f, 6.0f)
        for (s in 0 until 3) {
            val sAngle = Math.toRadians(s * 120.0)
            val mx = cx + (spokeR * Math.cos(sAngle)).toFloat()
            val my = cy + (spokeR * Math.sin(sAngle)).toFloat()
            canvas.drawCircle(mx, my, markerRadius, spoolFlangeSpokePaint)
        }

        // 3. Spool Tape Anchor Clamp Notch (holds tape to hub core)
        val clampAngle = Math.toRadians(45.0)
        val clampX = cx + (innerR * Math.cos(clampAngle)).toFloat()
        val clampY = cy + (innerR * Math.sin(clampAngle)).toFloat()
        val clampOuterX = cx + ((innerR + (outerR - innerR) * 0.28f) * Math.cos(clampAngle)).toFloat()
        val clampOuterY = cy + ((innerR + (outerR - innerR) * 0.28f) * Math.sin(clampAngle)).toFloat()
        canvas.drawLine(clampX, clampY, clampOuterX, clampOuterY, spoolFlangeSpokePaint)

        canvas.restore()
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

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            if (isPlaying && rotationAnimator?.isRunning != true) {
                startRotation()
            }
        } else {
            stopRotation()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            if (isPlaying && rotationAnimator?.isRunning != true) {
                startRotation()
            }
        } else {
            stopRotation()
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
