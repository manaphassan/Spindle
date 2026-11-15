package com.hana.spindle.ui.cassette

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import com.hana.spindle.playback.AudioFxController.DolbyMode
import com.hana.spindle.playback.AudioFxController.TapeFormulation
import com.hana.spindle.playback.RepeatMode
import java.util.Locale
import kotlin.math.log10
import kotlin.math.max

/**
 * Faceplate home deck: horizontal cassette behind a smoked door, mechanical
 * counter, real L/R peak meter, honest TAPE EQ / DOLBY NR slide switches with a
 * DIRECT lamp, and five piano keys. The previous [VerticalDeckView] is kept intact
 * and selectable as the "Classic" deck style.
 */
class FaceplateDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    internal companion object {
        const val ID_KEY_FIRST = 0 // 0..4: REW, PLAY, FF, STOP, EJECT
        const val ID_CASSETTE = 5
        const val ID_RESET = 6
        const val ID_REVERSE = 7
        const val ID_TAPE = 8
        const val ID_DOLBY = 9
        const val ID_METER = 10
        const val ID_COUNT = 11

        const val KEY_REW = 0
        const val KEY_PLAY = 1
        const val KEY_FF = 2
        const val KEY_STOP = 3
        const val KEY_EJECT = 4

        private const val METER_RELEASE_DB_PER_SEC = 12f
        private const val METER_HOLD_MS = 1200L
        private const val CLIP_HOLD_MS = 1000L
        private const val METER_FLOOR_DB = -60f

        private val TAPE_ORDER = arrayOf(
            TapeFormulation.FLAT, TapeFormulation.TYPE_I_NORMAL, TapeFormulation.TYPE_II_CHROME,
            TapeFormulation.TYPE_III_FERRICHROME, TapeFormulation.TYPE_IV_METAL
        )
        private val DOLBY_ORDER = arrayOf(DolbyMode.OFF, DolbyMode.DOLBY_B, DolbyMode.DOLBY_C)
        private val POSITION_LABELS = arrayOf(
            "REFERENCE", "NORMAL POSITION", "HIGH POSITION", "FeCr POSITION", "METAL POSITION"
        )
    }

    internal val state = FaceplateDeckState()
    private val cassettePainter = FaceplateCassettePainter()
    private val faceplatePainter = FaceplateBodyPainter()
    private var staticLayer: Bitmap? = null
    internal var fitScale = 1f
        private set
    internal var offsetX = 0f
        private set
    internal var offsetY = 0f
        private set

    private val kinematics = SpindleKinematics(hubRadiusRatio = 0.375f, maxTapeRadiusRatio = 1.0f)
    private val spool = SpindleKinematics.SpoolState()
    private val inertia = SpindleKinematics.ReelInertia()
    private val flywheel = SpindleKinematics.FlywheelInertiaTracker()
    private var lastFrameMs = 0L
    private var batterySaver = false

    private val levels = FloatArray(4)
    private var dispDbL = METER_FLOOR_DB
    private var dispDbR = METER_FLOOR_DB
    private var holdDbL = METER_FLOOR_DB
    private var holdDbR = METER_FLOOR_DB
    private var holdUntilL = 0L
    private var holdUntilR = 0L
    private var clipUntilL = 0L
    private var clipUntilR = 0L

    private val accessibility = FaceplateDeckAccessibility(this)

    // ---- Public state -------------------------------------------------------------

    var colorway: FaceplateColorway
        get() = state.colorway
        set(value) {
            if (state.colorway == value) return
            state.colorway = value
            rebuildStaticLayer()
            invalidate()
        }

    var isPlaying: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            state.running = value
            state.playLatched = value
            updateDirection()
            kickAnimation()
            accessibility.invalidateRoot()
        }

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            state.progress = field
            if (!isAnimating()) invalidate()
        }

    /** Stable identity of the loaded track; the counter memory resets when it changes. */
    var trackKey: String? = null
        set(value) {
            if (field != value) counterOffsetMs = 0L
            field = value
        }

    var trackTitle: String
        get() = state.title
        set(value) {
            state.title = value; invalidate()
        }

    var trackSubtitle: String
        get() = state.subtitle
        set(value) {
            state.subtitle = value; invalidate()
        }

    var formatLine: String
        get() = state.formatLine
        set(value) {
            state.formatLine = value; invalidate()
        }

    var rateLine: String
        get() = state.rateLine
        set(value) {
            state.rateLine = value; invalidate()
        }

    var albumArtBitmap: Bitmap?
        get() = state.albumArt
        set(value) {
            state.albumArt = value; invalidate()
        }

    var currentTimeMs: Long = 0L
        set(value) {
            field = value
            updateCounter()
        }

    var tapeFormulation: TapeFormulation = TapeFormulation.FLAT
        set(value) {
            field = value
            if (draggingSwitch != ID_TAPE) applyTapeIndex(TAPE_ORDER.indexOf(value).coerceAtLeast(0))
        }

    var dolbyMode: DolbyMode = DolbyMode.OFF
        set(value) {
            field = value
            if (draggingSwitch != ID_DOLBY) {
                state.dolbyIndex = DOLBY_ORDER.indexOf(value).coerceAtLeast(0)
                invalidate()
            }
        }

    var isDirect: Boolean
        get() = state.isDirect
        set(value) {
            if (state.isDirect != value) {
                state.isDirect = value; invalidate()
            }
        }

    var repeatMode: RepeatMode = RepeatMode.OFF
        set(value) {
            field = value
            state.reverseLabel = when (value) {
                RepeatMode.OFF -> "OFF"
                RepeatMode.ALL -> "ALL"
                RepeatMode.ONE -> "ONE"
            }
            invalidate()
        }

    /** Supplies [peakL, peakR, rmsL, rmsR] heard since the last frame; false when idle. */
    var levelProvider: ((FloatArray) -> Boolean)? = null

    // ---- Callbacks ------------------------------------------------------------------

    var onPlayClicked: (() -> Unit)? = null
    var onStopClicked: (() -> Unit)? = null
    var onPrevClicked: (() -> Unit)? = null
    var onNextClicked: (() -> Unit)? = null
    var onPrevAlbumClicked: (() -> Unit)? = null
    var onNextAlbumClicked: (() -> Unit)? = null
    var onHoldSeekStart: ((isForward: Boolean) -> Unit)? = null
    var onHoldSeekProgress: ((pitch: Float) -> Unit)? = null
    var onHoldSeekForward: (() -> Unit)? = null
    var onHoldSeekRewind: (() -> Unit)? = null
    var onHoldSeekStop: (() -> Unit)? = null
    var onLeaderAutoStop: (() -> Unit)? = null
    var onEjectClicked: (() -> Unit)? = null
    var onEjectLongClicked: (() -> Unit)? = null
    var onJCardClicked: (() -> Unit)? = null
    var onMeterClicked: (() -> Unit)? = null
    var onReverseClicked: (() -> Unit)? = null
    var onTapeTypeSelected: ((TapeFormulation) -> Unit)? = null
    var onTapeSwitchLongPressed: (() -> Unit)? = null
    var onDolbySelected: ((DolbyMode) -> Unit)? = null
    var onDoubleTapChassis: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Cassette deck"
        ViewCompat.setAccessibilityDelegate(this, accessibility)
    }

    // ---- Layout & drawing ----------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitScale = minOf(w / FaceplateGeometry.DESIGN_W, h / FaceplateGeometry.DESIGN_H)
        offsetX = (w - FaceplateGeometry.DESIGN_W * fitScale) / 2f
        offsetY = (h - FaceplateGeometry.DESIGN_H * fitScale) / 2f
        rebuildStaticLayer()
    }

    private fun rebuildStaticLayer() {
        if (width <= 0 || height <= 0) return
        staticLayer?.recycle()
        staticLayer = faceplatePainter.buildStatic(width, height, fitScale, offsetX, offsetY, state.colorway)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val animating = stepPhysics(now)

        staticLayer?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(fitScale, fitScale)
        cassettePainter.draw(canvas, state)
        faceplatePainter.drawDynamic(canvas, state)
        canvas.restore()

        if (animating) {
            if (batterySaver) postInvalidateDelayed(33L) else postInvalidateOnAnimation()
        } else {
            lastFrameMs = 0L
        }
    }

    private fun isAnimating() = lastFrameMs != 0L

    private fun kickAnimation() {
        if (lastFrameMs == 0L) lastFrameMs = SystemClock.uptimeMillis()
        invalidate()
    }

    /** Advances reels, flywheel and meter ballistics. Returns true while anything still moves. */
    private fun stepPhysics(now: Long): Boolean {
        val dt = if (lastFrameMs > 0L) ((now - lastFrameMs) / 1000f).coerceIn(0.001f, 0.05f) else 0.016f
        lastFrameMs = now

        kinematics.calculateInertia(progress, inertia)
        val target = when {
            isHoldSeeking -> if (isHoldSeekingFwd) holdSeekSpeed else -holdSeekSpeed
            isPlaying -> 1f
            else -> 0f
        }
        val speed = flywheel.step(target, dt, (inertia.leftInertia + inertia.rightInertia) * 0.5f)
        if (speed != 0f) {
            kinematics.calculate(progress, 40f, spool)
            val wow = kinematics.getWowFlutterMultiplier((now % 60000L) / 1000f)
            // Forward play winds both hubs counter-clockwise (tape runs left to right along the head).
            state.leftAngle = (state.leftAngle - 270f * spool.leftAngularSpeed * wow * speed * dt + 360f) % 360f
            state.rightAngle = (state.rightAngle - 270f * spool.rightAngularSpeed * wow * speed * dt + 360f) % 360f
        }

        val meterBusy = stepMeter(now, dt)
        return speed != 0f || isPlaying || isHoldSeeking || meterBusy
    }

    private fun stepMeter(now: Long, dt: Float): Boolean {
        val provider = levelProvider
        val active = provider != null && isPlaying && provider(levels)
        val peakL = if (active) levels[0] else 0f
        val peakR = if (active) levels[1] else 0f

        dispDbL = max(toDb(peakL), dispDbL - METER_RELEASE_DB_PER_SEC * dt)
        dispDbR = max(toDb(peakR), dispDbR - METER_RELEASE_DB_PER_SEC * dt)
        if (dispDbL >= holdDbL || now > holdUntilL) {
            holdDbL = dispDbL; holdUntilL = now + METER_HOLD_MS
        }
        if (dispDbR >= holdDbR || now > holdUntilR) {
            holdDbR = dispDbR; holdUntilR = now + METER_HOLD_MS
        }
        if (peakL >= 0.999f) clipUntilL = now + CLIP_HOLD_MS
        if (peakR >= 0.999f) clipUntilR = now + CLIP_HOLD_MS

        state.litL = litSegments(dispDbL)
        state.litR = litSegments(dispDbR)
        state.holdL = litSegments(holdDbL) - 1
        state.holdR = litSegments(holdDbR) - 1
        state.clipL = now < clipUntilL
        state.clipR = now < clipUntilR
        return dispDbL > METER_FLOOR_DB || dispDbR > METER_FLOOR_DB || state.clipL || state.clipR ||
                state.holdL >= 0 || state.holdR >= 0
    }

    private fun toDb(linear: Float): Float =
        if (linear <= 0.000001f) METER_FLOOR_DB else (20f * log10(linear)).coerceIn(METER_FLOOR_DB, 0f)

    private fun litSegments(db: Float): Int {
        var n = 0
        for (threshold in FaceplateBodyPainter.METER_DB) if (db >= threshold) n++ else break
        return n
    }

    private var counterOffsetMs = 0L

    private fun updateCounter() {
        val shown = (currentTimeMs - counterOffsetMs).coerceAtLeast(0L) / 1000L
        val digits = String.format(Locale.US, "%02d%02d", (shown / 60L) % 100L, shown % 60L)
        if (digits != state.counterDigits) {
            state.counterDigits = digits
            if (!isAnimating()) invalidate()
        }
    }

    private fun updateDirection() {
        state.dirForward = (isPlaying && !isHoldSeeking) || (isHoldSeeking && isHoldSeekingFwd)
        state.dirReverse = isHoldSeeking && !isHoldSeekingFwd
    }

    private fun applyTapeIndex(index: Int) {
        state.tapeIndex = index.coerceIn(0, TAPE_ORDER.size - 1)
        state.positionLabel = "C-90 · 45 MIN PER SIDE · " + POSITION_LABELS[state.tapeIndex]
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        batterySaver = context.getSharedPreferences("spindle_prefs", Context.MODE_PRIVATE)
            .getBoolean("pref_battery_saver_kinematics", false)
        kickAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) kickAnimation() else lastFrameMs = 0L
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        uiHandler.removeCallbacksAndMessages(null)
        if (isHoldSeeking) stopHoldSeek()
        staticLayer?.recycle()
        staticLayer = null
    }

    // ---- Hit testing (design units) ------------------------------------------------

    private val hitRect = RectF()

    internal fun hitTest(xPx: Float, yPx: Float): Int {
        val x = (xPx - offsetX) / fitScale
        val y = (yPx - offsetY) / fitScale
        for (i in 0 until FaceplateGeometry.KEY_COUNT) {
            if (FaceplateGeometry.keyHit(i, hitRect).contains(x, y)) return ID_KEY_FIRST + i
        }
        return when {
            FaceplateGeometry.glass.contains(x, y) -> ID_CASSETTE
            FaceplateGeometry.reset.contains(x, y) -> ID_RESET
            FaceplateGeometry.reverse.contains(x, y) -> ID_REVERSE
            FaceplateGeometry.tapeSwitch.contains(x, y) -> ID_TAPE
            FaceplateGeometry.dolbySwitch.contains(x, y) -> ID_DOLBY
            FaceplateGeometry.meter.contains(x, y) -> ID_METER
            else -> -1
        }
    }

    internal fun designBounds(id: Int, out: RectF): RectF {
        when (id) {
            in ID_KEY_FIRST until ID_KEY_FIRST + FaceplateGeometry.KEY_COUNT -> FaceplateGeometry.keyHit(id, out)
            ID_CASSETTE -> out.set(FaceplateGeometry.glass)
            ID_RESET -> out.set(FaceplateGeometry.reset)
            ID_REVERSE -> out.set(FaceplateGeometry.reverse)
            ID_TAPE -> out.set(FaceplateGeometry.tapeSwitch)
            ID_DOLBY -> out.set(FaceplateGeometry.dolbySwitch)
            else -> out.set(FaceplateGeometry.meter)
        }
        out.set(
            out.left * fitScale + offsetX, out.top * fitScale + offsetY,
            out.right * fitScale + offsetX, out.bottom * fitScale + offsetY
        )
        return out
    }

    internal fun describe(id: Int): String = when (id) {
        KEY_REW -> "Rewind. Tap for previous track, double tap for previous album, hold to rewind"
        KEY_PLAY -> if (isPlaying) "Pause" else "Play"
        KEY_FF -> "Fast forward. Tap for next track, double tap for next album, hold to wind forward"
        KEY_STOP -> "Stop"
        KEY_EJECT -> "Eject. Opens the library. Hold to eject the tape"
        ID_CASSETTE -> "Cassette: ${state.title}. Opens the J-card"
        ID_RESET -> "Counter ${
            state.counterDigits.substring(
                0,
                2
            )
        } minutes ${state.counterDigits.substring(2)} seconds. Reset counter"

        ID_REVERSE -> "Auto reverse ${state.reverseLabel.lowercase(Locale.US)}"
        ID_TAPE -> "Tape EQ ${FaceplateBodyPainter.TAPE_STOPS[state.tapeIndex]}"
        ID_DOLBY -> "Dolby noise reduction ${FaceplateBodyPainter.DOLBY_STOPS[state.dolbyIndex]}"
        else -> "Level meter. Opens the VU meters"
    }

    /** Click from touch or accessibility. */
    internal fun performVirtualClick(id: Int) {
        when (id) {
            KEY_REW -> onPrevClicked?.invoke()
            KEY_PLAY -> onPlayClicked?.invoke()
            KEY_FF -> onNextClicked?.invoke()
            KEY_STOP -> onStopClicked?.invoke()
            KEY_EJECT -> onEjectClicked?.invoke()
            ID_CASSETTE -> onJCardClicked?.invoke()
            ID_RESET -> {
                counterOffsetMs = currentTimeMs; updateCounter()
            }

            ID_REVERSE -> onReverseClicked?.invoke()
            ID_TAPE -> selectTape((state.tapeIndex + 1) % TAPE_ORDER.size)
            ID_DOLBY -> selectDolby((state.dolbyIndex + 1) % DOLBY_ORDER.size)
            ID_METER -> onMeterClicked?.invoke()
        }
        accessibility.invalidateVirtualView(id)
    }

    internal fun performVirtualLongClick(id: Int): Boolean = when (id) {
        KEY_EJECT -> {
            onEjectLongClicked?.invoke(); true
        }

        ID_TAPE -> {
            onTapeSwitchLongPressed?.invoke(); true
        }

        else -> false
    }

    private fun selectTape(index: Int) {
        val type = TAPE_ORDER[index.coerceIn(0, TAPE_ORDER.size - 1)]
        applyTapeIndex(index)
        if (type != tapeFormulation) onTapeTypeSelected?.invoke(type)
    }

    private fun selectDolby(index: Int) {
        val mode = DOLBY_ORDER[index.coerceIn(0, DOLBY_ORDER.size - 1)]
        state.dolbyIndex = index
        invalidate()
        if (mode != dolbyMode) onDolbySelected?.invoke(mode)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    // ---- Touch ------------------------------------------------------------------------

    private val uiHandler = Handler(Looper.getMainLooper())
    private var downTarget = -1
    private var draggingSwitch = -1
    private var longPressFired = false
    private var isHoldSeeking = false
    private var isHoldSeekingFwd = false
    private var holdSeekSpeed = 1f
    private var lastTapKey = -1
    private var lastTapTime = 0L
    private var lastChassisTapTime = 0L
    private var pendingSingleTap: Runnable? = null

    private val longPressRunnable = Runnable {
        longPressFired = performVirtualLongClick(downTarget)
        if (longPressFired) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (downTarget == ID_TAPE) {
                draggingSwitch = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                applyTapeIndex(TAPE_ORDER.indexOf(tapeFormulation).coerceAtLeast(0))
            }
            if (downTarget == KEY_EJECT) {
                state.pressedKey = -1; invalidate()
            }
        }
    }

    private val holdSeekRunnable = object : Runnable {
        private var ticks = 0
        override fun run() {
            if (!isHoldSeeking) {
                ticks = 0
                isHoldSeeking = true
                isHoldSeekingFwd = downTarget == KEY_FF
                updateDirection()
                onHoldSeekStart?.invoke(isHoldSeekingFwd)
                kickAnimation()
            }
            ticks++
            holdSeekSpeed = (1f + ticks * 0.22f).coerceAtMost(6.5f)
            onHoldSeekProgress?.invoke((0.92f + (holdSeekSpeed - 1f) * 0.12f).coerceIn(0.92f, 1.65f))
            if (isHoldSeekingFwd) onHoldSeekForward?.invoke() else onHoldSeekRewind?.invoke()
            if ((isHoldSeekingFwd && progress >= 0.998f) || (!isHoldSeekingFwd && progress <= 0.002f)) {
                onLeaderAutoStop?.invoke()
                endHoldSeek()
                state.pressedKey = -1
                downTarget = -1
                invalidate()
                return
            }
            if (ticks % 4 == 0) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            uiHandler.postDelayed(this, 50L)
        }
    }

    private fun endHoldSeek() {
        uiHandler.removeCallbacks(holdSeekRunnable)
        isHoldSeeking = false
        holdSeekSpeed = 1f
        updateDirection()
    }

    private fun stopHoldSeek() {
        endHoldSeek()
        onHoldSeekStop?.invoke()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTarget = hitTest(event.x, event.y)
                longPressFired = false
                when (downTarget) {
                    -1 -> return handleChassisTap()
                    KEY_REW, KEY_FF -> {
                        pressKey(downTarget)
                        uiHandler.postDelayed(holdSeekRunnable, 320L)
                    }

                    KEY_PLAY, KEY_STOP -> pressKey(downTarget)
                    KEY_EJECT -> {
                        pressKey(downTarget)
                        uiHandler.postDelayed(
                            longPressRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong().coerceAtLeast(600L)
                        )
                    }

                    ID_TAPE, ID_DOLBY -> {
                        draggingSwitch = downTarget
                        parent?.requestDisallowInterceptTouchEvent(true)
                        previewSwitch(event.x)
                        if (downTarget == ID_TAPE) {
                            uiHandler.postDelayed(
                                longPressRunnable,
                                ViewConfiguration.getLongPressTimeout().toLong().coerceAtLeast(600L)
                            )
                        }
                    }

                    ID_RESET -> {
                        state.resetPressed = true; invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (draggingSwitch != -1) {
                    val before = if (draggingSwitch == ID_TAPE) state.tapeIndex else state.dolbyIndex
                    previewSwitch(event.x)
                    val after = if (draggingSwitch == ID_TAPE) state.tapeIndex else state.dolbyIndex
                    if (after != before) {
                        uiHandler.removeCallbacks(longPressRunnable)
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                } else if (downTarget in KEY_REW..KEY_EJECT && !isHoldSeeking && hitTest(
                        event.x,
                        event.y
                    ) != downTarget
                ) {
                    cancelPress()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(longPressRunnable)
                val target = downTarget
                downTarget = -1
                state.pressedKey = -1
                state.resetPressed = false
                if (draggingSwitch != -1) {
                    val sw = draggingSwitch
                    draggingSwitch = -1
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (!longPressFired) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (sw == ID_TAPE) selectTape(state.tapeIndex) else selectDolby(state.dolbyIndex)
                    } else {
                        applyTapeIndex(TAPE_ORDER.indexOf(tapeFormulation).coerceAtLeast(0))
                    }
                    return true
                }
                invalidate()
                if (target == -1 || longPressFired) return true
                if (target == KEY_REW || target == KEY_FF) {
                    uiHandler.removeCallbacks(holdSeekRunnable)
                    if (isHoldSeeking) {
                        stopHoldSeek(); return true
                    }
                    handleTransportTap(target)
                    return true
                }
                if (hitTest(event.x, event.y) == target) {
                    if (target in KEY_REW..KEY_EJECT) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    performVirtualClick(target)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPress()
                if (draggingSwitch != -1) {
                    draggingSwitch = -1
                    applyTapeIndex(TAPE_ORDER.indexOf(tapeFormulation).coerceAtLeast(0))
                    state.dolbyIndex = DOLBY_ORDER.indexOf(dolbyMode).coerceAtLeast(0)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pressKey(i: Int) {
        state.pressedKey = i
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        invalidate()
    }

    private fun cancelPress() {
        uiHandler.removeCallbacks(longPressRunnable)
        uiHandler.removeCallbacks(holdSeekRunnable)
        if (isHoldSeeking) stopHoldSeek()
        downTarget = -1
        state.pressedKey = -1
        state.resetPressed = false
        invalidate()
    }

    private fun previewSwitch(xPx: Float) {
        val x = (xPx - offsetX) / fitScale
        if (draggingSwitch == ID_TAPE) {
            val r = FaceplateGeometry.tapeSwitch
            val idx = ((x - r.left) / (r.width() / TAPE_ORDER.size)).toInt().coerceIn(0, TAPE_ORDER.size - 1)
            if (idx != state.tapeIndex) applyTapeIndex(idx)
        } else {
            val r = FaceplateGeometry.dolbySwitch
            val idx = ((x - r.left) / (r.width() / DOLBY_ORDER.size)).toInt().coerceIn(0, DOLBY_ORDER.size - 1)
            if (idx != state.dolbyIndex) {
                state.dolbyIndex = idx; invalidate()
            }
        }
    }

    /** Single tap skips a track; a second tap within 280 ms skips the album instead. */
    private fun handleTransportTap(key: Int) {
        val now = SystemClock.uptimeMillis()
        if (lastTapKey == key && now - lastTapTime < 280L) {
            pendingSingleTap?.let { uiHandler.removeCallbacks(it) }
            pendingSingleTap = null
            lastTapKey = -1
            if (key == KEY_FF) onNextAlbumClicked?.invoke() else onPrevAlbumClicked?.invoke()
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }
        lastTapKey = key
        lastTapTime = now
        pendingSingleTap?.let { uiHandler.removeCallbacks(it) }
        val r = Runnable {
            lastTapKey = -1
            pendingSingleTap = null
            if (key == KEY_FF) onNextClicked?.invoke() else onPrevClicked?.invoke()
        }
        pendingSingleTap = r
        uiHandler.postDelayed(r, 260L)
    }

    private fun handleChassisTap(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastChassisTapTime < ViewConfiguration.getDoubleTapTimeout()) {
            lastChassisTapTime = 0L
            onDoubleTapChassis?.invoke()
        } else {
            lastChassisTapTime = now
        }
        return false
    }
}
