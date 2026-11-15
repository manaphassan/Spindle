package com.hana.spindle.ui.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import com.hana.spindle.playback.AnalogFmEngine
import com.hana.spindle.ui.cassette.WalkmanColorway
import java.util.Locale
import kotlin.math.abs

/**
 * Walkman-style radio page: slide-rule dial and LCD behind the smoked door, SEEK buttons
 * around a knurled tuning wheel, SIG / centre-zero TUNE meter, BAND and MODE slide switches
 * with a STEREO lamp, and four latching preset keys plus SCAN. Pairs with the tape deck's
 * [com.hana.spindle.ui.cassette.WalkmanDeckView] and shares its colorways.
 *
 * The view holds no radio logic: [RadioFragment] pushes a [RadioDeckModel] and reacts to callbacks.
 */
class RadioDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    internal companion object {
        const val ID_KEY_FIRST = 0 // 0..3 presets, 4 SCAN
        const val ID_DIAL = 5
        const val ID_LCD = 6
        const val ID_SEEK_PREV = 7
        const val ID_WHEEL = 8
        const val ID_SEEK_NEXT = 9
        const val ID_BAND = 10
        const val ID_MODE = 11
        const val ID_TAPE = 12
        const val ID_COUNT = 13
    }

    private val render = RadioDeckRenderState()
    private val painter = RadioDeckPainter()
    private var staticLayer: Bitmap? = null
    private var fitScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private val accessibility = RadioDeckAccessibility(this)

    private val minFreq = AnalogFmEngine.MIN_FREQ
    private val maxFreq = AnalogFmEngine.MAX_FREQ
    private val step = AnalogFmEngine.STEP_MHZ

    var colorway: WalkmanColorway
        get() = render.colorway
        set(value) {
            if (render.colorway == value) return
            render.colorway = value
            rebuildStaticLayer()
            invalidate()
        }

    var model: RadioDeckModel
        get() = render.model
        set(value) {
            if (render.model == value) return
            val old = render.model
            render.model = value
            if (dragFreq == null) render.frequency = value.frequency
            if (value.fmDead && !old.fmDead && (downTarget == ID_DIAL || downTarget == ID_WHEEL)) cancelGesture()
            invalidate()
            accessibility.invalidateRoot()
        }

    // ---- Callbacks --------------------------------------------------------------------

    /** Dial or wheel moved to [freq]. [isFinal] is true on release (and for every wheel detent in FM). */
    var onTune: ((freq: Float, isFinal: Boolean) -> Unit)? = null
    var onSeek: ((forward: Boolean) -> Unit)? = null
    var onPowerToggle: (() -> Unit)? = null
    var onBandSelected: ((isNet: Boolean) -> Unit)? = null
    var onMonoSelected: ((forceMono: Boolean) -> Unit)? = null
    var onPresetClicked: ((index: Int) -> Unit)? = null
    var onPresetLongClicked: ((index: Int) -> Boolean)? = null
    var onScanClicked: (() -> Unit)? = null
    var onTapeClicked: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Walkman radio tuner"
        ViewCompat.setAccessibilityDelegate(this, accessibility)
    }

    // ---- Layout & drawing -----------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitScale = minOf(w / RadioDeckGeometry.DESIGN_W, h / RadioDeckGeometry.DESIGN_H)
        offsetX = (w - RadioDeckGeometry.DESIGN_W * fitScale) / 2f
        offsetY = (h - RadioDeckGeometry.DESIGN_H * fitScale) / 2f
        rebuildStaticLayer()
    }

    private fun rebuildStaticLayer() {
        if (width <= 0 || height <= 0) return
        staticLayer?.recycle()
        staticLayer = painter.buildStatic(width, height, fitScale, offsetX, offsetY, render.colorway)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        staticLayer?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(fitScale, fitScale)
        painter.drawDynamic(canvas, render)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        uiHandler.removeCallbacksAndMessages(null)
        staticLayer?.recycle()
        staticLayer = null
    }

    // ---- Hit testing (design units) -------------------------------------------------

    private val hitRect = RectF()

    internal fun hitTest(xPx: Float, yPx: Float): Int {
        val x = toDesignX(xPx)
        val y = (yPx - offsetY) / fitScale
        for (i in 0 until RadioDeckGeometry.KEY_COUNT) {
            if (RadioDeckGeometry.keyHit(i, hitRect).contains(x, y)) return ID_KEY_FIRST + i
        }
        return when {
            RadioDeckGeometry.dial.contains(x, y) -> ID_DIAL
            RadioDeckGeometry.lcd.contains(x, y) -> ID_LCD
            RadioDeckGeometry.seekPrev.contains(x, y) -> ID_SEEK_PREV
            RadioDeckGeometry.wheel.contains(x, y) -> ID_WHEEL
            RadioDeckGeometry.seekNext.contains(x, y) -> ID_SEEK_NEXT
            RadioDeckGeometry.bandSwitch.contains(x, y) -> ID_BAND
            RadioDeckGeometry.modeSwitch.contains(x, y) -> ID_MODE
            RadioDeckGeometry.tapeHint.contains(x, y) -> ID_TAPE
            else -> -1
        }
    }

    private fun toDesignX(xPx: Float) = (xPx - offsetX) / fitScale

    internal fun designBounds(id: Int, out: RectF): RectF {
        when (id) {
            in ID_KEY_FIRST until ID_KEY_FIRST + RadioDeckGeometry.KEY_COUNT -> RadioDeckGeometry.keyHit(id, out)
            ID_DIAL -> out.set(RadioDeckGeometry.dial)
            ID_LCD -> out.set(RadioDeckGeometry.lcd)
            ID_SEEK_PREV -> out.set(RadioDeckGeometry.seekPrev)
            ID_WHEEL -> out.set(RadioDeckGeometry.wheel)
            ID_SEEK_NEXT -> out.set(RadioDeckGeometry.seekNext)
            ID_BAND -> out.set(RadioDeckGeometry.bandSwitch)
            ID_MODE -> out.set(RadioDeckGeometry.modeSwitch)
            else -> out.set(RadioDeckGeometry.tapeHint)
        }
        out.set(
            out.left * fitScale + offsetX, out.top * fitScale + offsetY,
            out.right * fitScale + offsetX, out.bottom * fitScale + offsetY
        )
        return out
    }

    internal fun isEnabledTarget(id: Int): Boolean {
        val m = render.model
        return when (id) {
            in ID_KEY_FIRST until ID_KEY_FIRST + RadioDeckGeometry.KEY_COUNT -> painter.isKeyEnabled(m, id)
            ID_DIAL, ID_WHEEL, ID_SEEK_PREV, ID_SEEK_NEXT, ID_LCD -> !m.fmDead
            ID_MODE -> !m.isNet && !m.fmDead
            else -> true
        }
    }

    internal fun isTunable(id: Int) = id == ID_DIAL || id == ID_WHEEL

    internal fun describe(id: Int): String {
        val m = render.model
        val freq = String.format(Locale.US, "%.1f megahertz", render.frequency)
        return when (id) {
            in ID_KEY_FIRST until RadioDeckGeometry.KEY_SCAN -> {
                val p = m.presets.getOrNull(id)
                if (p == null || !p.enabled) "Preset ${id + 1}, empty"
                else {
                    val state = if (id == m.activePreset) ", selected" else ""
                    val hold = if (!m.isNet) ". Hold to store the current frequency" else ""
                    "Preset ${p.number}, ${p.label}, ${p.freqText}$state$hold"
                }
            }

            RadioDeckGeometry.KEY_SCAN -> if (m.scanning) "Scan, running. Tap to stop" else "Scan for the next station"
            ID_DIAL -> "Tuning dial, $freq"
            ID_LCD -> "Display, ${if (m.isNet) "net" else "FM"} $freq. ${m.line}. ${if (m.onAir) "Tap to stop" else "Tap to listen"}"
            ID_SEEK_PREV -> "Seek previous station"
            ID_WHEEL -> "Tuning wheel, $freq"
            ID_SEEK_NEXT -> "Seek next station"
            ID_BAND -> "Band ${if (m.isNet) "net" else "FM"}. Tap to switch to ${if (m.isNet) "FM" else "net"}"
            ID_MODE -> "Mode ${if (m.forceMono) "mono" else "auto stereo"}"
            else -> "Back to tape"
        }
    }

    internal fun performVirtualClick(id: Int) {
        if (!isEnabledTarget(id)) return
        val m = render.model
        when (id) {
            in ID_KEY_FIRST until RadioDeckGeometry.KEY_SCAN -> onPresetClicked?.invoke(id)
            RadioDeckGeometry.KEY_SCAN -> onScanClicked?.invoke()
            ID_LCD -> onPowerToggle?.invoke()
            ID_SEEK_PREV -> onSeek?.invoke(false)
            ID_SEEK_NEXT -> onSeek?.invoke(true)
            ID_BAND -> onBandSelected?.invoke(!m.isNet)
            ID_MODE -> onMonoSelected?.invoke(!m.forceMono)
            ID_TAPE -> onTapeClicked?.invoke()
        }
        accessibility.invalidateVirtualView(id)
    }

    internal fun performVirtualLongClick(id: Int): Boolean {
        if (id !in ID_KEY_FIRST until RadioDeckGeometry.KEY_SCAN || !isEnabledTarget(id)) return false
        return onPresetLongClicked?.invoke(id) == true
    }

    /** Accessibility scroll on the dial or wheel: one 0.1 MHz step. */
    internal fun stepTuning(forward: Boolean) {
        if (render.model.fmDead) return
        val f = RadioDeckMath.snap(render.frequency + if (forward) step else -step, minFreq, maxFreq, step)
        if (f != render.frequency) onTune?.invoke(f, true)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    // ---- Touch --------------------------------------------------------------------------

    private val uiHandler = Handler(Looper.getMainLooper())
    private var downTarget = -1
    private var longPressFired = false
    private var dragFreq: Float? = null
    private var lastWheelX = 0f
    private var wheelAccum = 0f

    private val longPressRunnable = Runnable {
        longPressFired = performVirtualLongClick(downTarget)
        if (longPressFired) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            render.pressed = -1
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val target = hitTest(event.x, event.y)
                if (target == -1 || !isEnabledTarget(target)) return false
                downTarget = target
                longPressFired = false
                when (target) {
                    ID_DIAL -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        dragFreq = render.frequency
                        dragDialTo(event.x)
                    }

                    ID_WHEEL -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        dragFreq = render.frequency
                        lastWheelX = event.x
                        wheelAccum = 0f
                    }

                    in ID_KEY_FIRST until RadioDeckGeometry.KEY_SCAN -> {
                        press(target)
                        if (!render.model.isNet) {
                            uiHandler.postDelayed(
                                longPressRunnable,
                                ViewConfiguration.getLongPressTimeout().toLong().coerceAtLeast(600L)
                            )
                        }
                    }

                    RadioDeckGeometry.KEY_SCAN, ID_SEEK_PREV, ID_SEEK_NEXT -> press(target)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                when (downTarget) {
                    ID_DIAL -> dragDialTo(event.x)
                    ID_WHEEL -> dragWheel(event.x)
                    -1 -> Unit
                    else -> if (hitTest(event.x, event.y) != downTarget) cancelGesture()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(longPressRunnable)
                val target = downTarget
                downTarget = -1
                render.pressed = -1
                if (target == ID_DIAL || target == ID_WHEEL) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val f = dragFreq
                    dragFreq = null
                    if (f != null) onTune?.invoke(f, true)
                    invalidate()
                    return true
                }
                invalidate()
                if (target == -1 || longPressFired) return true
                if (hitTest(event.x, event.y) == target) {
                    performHapticFeedback(
                        if (target == ID_BAND || target == ID_MODE) HapticFeedbackConstants.KEYBOARD_TAP
                        else HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    performVirtualClick(target)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelGesture()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun press(id: Int) {
        render.pressed = id
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        invalidate()
    }

    private fun cancelGesture() {
        uiHandler.removeCallbacks(longPressRunnable)
        if (downTarget == ID_DIAL || downTarget == ID_WHEEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
            dragFreq?.let { onTune?.invoke(it, true) }
        }
        dragFreq = null
        downTarget = -1
        render.pressed = -1
        invalidate()
    }

    private fun dragDialTo(xPx: Float) {
        val f = RadioDeckMath.xToFreq(toDesignX(xPx), minFreq, maxFreq, step)
        previewFrequency(f)
    }

    /** Each [RadioDeckMath.WHEEL_DETENT_DP] of travel is one 0.1 MHz detent; right tunes up. */
    private fun dragWheel(xPx: Float) {
        val dx = (xPx - lastWheelX) / fitScale
        lastWheelX = xPx
        render.wheelPhase += dx
        wheelAccum += dx
        var f = dragFreq ?: render.frequency
        while (abs(wheelAccum) >= RadioDeckMath.WHEEL_DETENT_DP) {
            val dir = if (wheelAccum > 0f) 1 else -1
            wheelAccum -= dir * RadioDeckMath.WHEEL_DETENT_DP
            f = RadioDeckMath.snap(f + dir * step, minFreq, maxFreq, step)
        }
        if (!previewFrequency(f)) invalidate()
    }

    private fun previewFrequency(f: Float): Boolean {
        if (f == dragFreq && f == render.frequency) return false
        val changed = f != render.frequency
        dragFreq = f
        render.frequency = f
        if (changed) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onTune?.invoke(f, false)
        }
        invalidate()
        return true
    }
}
