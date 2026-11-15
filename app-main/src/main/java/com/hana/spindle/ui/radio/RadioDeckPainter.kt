package com.hana.spindle.ui.radio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.hana.spindle.playback.AnalogFmEngine
import com.hana.spindle.ui.cassette.FaceplateColorway
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Fixed layout of the radio faceplate on the same 360 x 592 dp canvas as the tape deck. */
internal object RadioDeckGeometry {
    const val DESIGN_W = 360f
    const val DESIGN_H = 592f
    const val KEY_COUNT = 5
    const val KEY_SCAN = 4
    const val KEY_W = 62.4f
    const val KEY_GAP = 6f

    val door = RectF(12f, 52f, 348f, 276f)
    val glass = RectF(18f, 58f, 342f, 270f)
    val dial = RectF(26f, 66f, 334f, 166f)
    val lcd = RectF(26f, 174f, 334f, 262f)
    val seekPrev = RectF(12f, 284f, 58f, 336f)
    val wheel = RectF(58f, 288f, 302f, 336f)
    val seekNext = RectF(302f, 284f, 348f, 336f)
    val meter = RectF(12f, 338f, 348f, 390f)
    val bandSwitch = RectF(16f, 396f, 156f, 448f)
    val modeSwitch = RectF(176f, 396f, 288f, 448f)
    val tapeHint = RectF(280f, 576f, 360f, 592f)

    fun keyLeft(i: Int) = 12f + i * (KEY_W + KEY_GAP)
    fun keyHit(i: Int, out: RectF): RectF = out.apply { set(keyLeft(i), 460f, keyLeft(i) + KEY_W, 576f) }
}

/** Per-frame inputs that are not part of [RadioDeckModel] (touch feedback, drag preview). */
internal class RadioDeckRenderState {
    var colorway = FaceplateColorway.SILVER
    var model = RadioDeckModel()
    var frequency = 88.5f
    var wheelPhase = 0f
    var pressed = -1
}

/**
 * Draws the radio faceplate. The chassis, dial scale and recesses are cached in a bitmap;
 * [drawDynamic] paints needle, LCD, meters, switch knobs, lamps and keys.
 */
internal class RadioDeckPainter {

    companion object {
        val BAND_STOPS = arrayOf("FM", "NET")
        val MODE_STOPS = arrayOf("AUTO", "MONO")

        const val SEG_X0 = 34f
        const val SEG_W = 9f
        const val SEG_STEP = 11.4f

        private const val DIM_ALPHA = 102
        private val EMERALD = Color.parseColor("#00E676")
        private val LAMP_OFF = Color.parseColor("#2C3A33")
        private val TANGERINE = Color.parseColor("#F97316")
        private val DIAL_BG = Color.parseColor("#0A0B10")
        private val WELL = Color.argb(71, 0, 0, 0)
        private val HALO = Color.argb(46, 0, 230, 118)
        private val GLARE = Color.argb(9, 255, 255, 255)
        private val TRAIL_ALPHA = intArrayOf(115, 66, 36, 15)
    }

    private val minFreq = AnalogFmEngine.MIN_FREQ
    private val maxFreq = AnalogFmEngine.MAX_FREQ

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val print = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 8f
        letterSpacing = 0.15f
    }
    private val printActive =
        Paint(print).apply { textSize = 8.5f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    private val printInactive = Paint(print).apply { textSize = 8.5f; textAlign = Paint.Align.CENTER }
    private val wordmark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 17f
        letterSpacing = 0.3f
        isFakeBoldText = true
    }
    private val scaleNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 10f
        textAlign = Paint.Align.CENTER
    }
    private val markLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 7.5f
        letterSpacing = 0.1f
        textAlign = Paint.Align.CENTER
    }
    private val mono = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val bigFreq = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 44f
        textAlign = Paint.Align.RIGHT
    }
    private val numeral = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 22f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val meterText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 7.5f
    }

    private val r = RectF()
    private val glyph = Path()
    private val glare = Path().apply {
        moveTo(18f, 58f); lineTo(138f, 58f); lineTo(68f, 270f); lineTo(18f, 270f); close()
    }

    private var wheelShader: Shader? = null
    private var wheelShaderFor: FaceplateColorway? = null

    private var freqTextFor = Float.NaN
    private var freqText = ""
    private var lineFor: String? = null
    private var lineShown: CharSequence = ""

    fun buildStatic(width: Int, height: Int, s: Float, ox: Float, oy: Float, cw: FaceplateColorway): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), cw.bodyTop, cw.bodyBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null

        // Same deterministic brushed grain as the tape deck, different seed so the two pages differ
        var seed = 11L
        stroke.strokeWidth = 0.6f * s
        var y = 0f
        while (y < height) {
            seed = (seed * 9301 + 49297) % 233280
            val light = seed % 2L == 0L
            seed = (seed * 9301 + 49297) % 233280
            val a = ((if (light) cw.brushLightAlpha else cw.brushDarkAlpha) * (seed / 233280f) * 255).toInt()
            stroke.color = if (light) Color.argb(a, 255, 255, 255) else Color.argb(a, 0, 0, 0)
            c.drawLine(0f, y, width.toFloat(), y, stroke)
            y += 1.5f * s
        }

        fill.color = cw.band
        c.drawRect(0f, 0f, width.toFloat(), oy + 44f * s, fill)

        c.translate(ox, oy)
        c.scale(s, s)

        wordmark.color = cw.wordmark
        c.drawText("SPINDLE", 16f, 29f, wordmark)
        fill.color = TANGERINE
        val dotX = 16f + wordmark.measureText("SPINDLE") + 1f
        c.drawRect(dotX, 24f, dotX + 5f, 29f, fill)
        drawText(c, "FM STEREO TUNER", 344f, 20f, cw.bandSub, Paint.Align.RIGHT)
        drawText(c, "ON AIR", 344f, 34f, cw.bandSub, Paint.Align.RIGHT)

        fill.color = cw.doorFrame
        c.drawRoundRect(RadioDeckGeometry.door, 10f, 10f, fill)
        fill.color = cw.glass
        c.drawRoundRect(RadioDeckGeometry.glass, 7f, 7f, fill)

        drawDialScale(c, cw)

        fill.color = cw.recess
        c.drawRoundRect(RadioDeckGeometry.lcd, 3f, 3f, fill)
        c.drawRoundRect(RadioDeckGeometry.meter, 6f, 6f, fill)

        drawText(c, "BAND", 16f, 406f, cw.print, Paint.Align.LEFT)
        fill.color = cw.recess
        r.set(16f, 424f, 156f, 440f); c.drawRoundRect(r, 8f, 8f, fill)

        for (i in 0 until RadioDeckGeometry.KEY_COUNT) {
            val x = RadioDeckGeometry.keyLeft(i)
            fill.color = WELL
            r.set(x - 2f, 474f, x + RadioDeckGeometry.KEY_W + 2f, 572f)
            c.drawRoundRect(r, 6f, 6f, fill)
        }

        drawText(c, "TAPE ›", 344f, 586f, cw.hint, Paint.Align.RIGHT)
        return bmp
    }

    private fun drawDialScale(c: Canvas, cw: FaceplateColorway) {
        val d = RadioDeckGeometry.dial
        fill.color = DIAL_BG
        c.drawRoundRect(d, 3f, 3f, fill)
        fill.shader = RadialGradient(
            d.centerX(), d.top + d.height() * 0.55f, d.width() * 0.6f,
            withAlpha(cw.lcdText, 26), withAlpha(cw.lcdText, 0), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(d, 3f, 3f, fill)
        fill.shader = null

        drawText(c, "FM", 34f, 82f, cw.lcdText, Paint.Align.LEFT)
        drawText(c, "MHz", 326f, 82f, cw.lcdSub, Paint.Align.RIGHT)

        stroke.color = withAlpha(cw.lcdText, 140)
        stroke.strokeWidth = 1f
        c.drawLine(RadioDeckMath.SCALE_X0, 114f, RadioDeckMath.SCALE_X1, 114f, stroke)
        scaleNum.color = withAlpha(cw.lcdText, 230)
        for (i in 0..40) {
            val f = 88f + i * 0.5f
            val x = RadioDeckMath.freqToX(f, minFreq, maxFreq)
            val major = i % 4 == 0
            stroke.color = withAlpha(cw.lcdText, if (major) 217 else 102)
            stroke.strokeWidth = if (major) 1.2f else 0.8f
            c.drawLine(x, if (major) 102f else 108f, x, 114f, stroke)
            if (major) c.drawText((88 + i / 2).toString(), x, 97f, scaleNum)
        }
        drawText(c, "DRAG DIAL TO TUNE", 34f, 158f, cw.lcdSub, Paint.Align.LEFT, 7f)
    }

    private fun drawText(c: Canvas, t: String, x: Float, y: Float, color: Int, align: Paint.Align, size: Float = 8f) {
        print.color = color
        print.textAlign = align
        print.textSize = size
        print.letterSpacing = 0.15f
        c.drawText(t, x, y, print)
    }

    // ---- Dynamic layer ------------------------------------------------------------------

    fun drawDynamic(c: Canvas, st: RadioDeckRenderState) {
        val cw = st.colorway
        val m = st.model
        drawLamp(c, 296f, 31f, 3f, m.onAir)
        drawWindow(c, cw, m, st.frequency)
        drawTuningRow(c, cw, m, st)
        drawMeter(c, cw, m.meter)
        drawSwitches(c, cw, m)
        drawKeys(c, cw, m, st.pressed)
        drawText(c, m.hint, 180f, 586f, cw.hint, Paint.Align.CENTER)
    }

    private fun drawWindow(c: Canvas, cw: FaceplateColorway, m: RadioDeckModel, freq: Float) {
        c.save()
        c.clipRect(RadioDeckGeometry.dial)

        var lastLabelX = -1000f
        for (mark in m.stations) {
            val x = RadioDeckMath.freqToX(mark.frequency, minFreq, maxFreq)
            val on = !m.fmDead && abs(mark.frequency - freq) < 0.05f
            fill.color = if (on) TANGERINE else withAlpha(cw.lcdText, 153)
            glyph.reset()
            glyph.moveTo(x, 118f); glyph.lineTo(x + 4f, 124f); glyph.lineTo(x - 4f, 124f); glyph.close()
            c.drawPath(glyph, fill)
            if (x - lastLabelX >= 40f) {
                markLabel.color = if (on) cw.lcdText else cw.lcdSub
                c.drawText(mark.callsign, x, 136f, markLabel)
                lastLabelX = x
            }
        }

        mono.textSize = 8f
        mono.color = cw.lcdSub
        mono.textAlign = Paint.Align.RIGHT
        c.drawText(m.region, 326f, 158f, mono)

        val nx = RadioDeckMath.freqToX(freq, minFreq, maxFreq)
        if (m.scanning) {
            for (k in TRAIL_ALPHA.indices) {
                fill.color = withAlpha(TANGERINE, TRAIL_ALPHA[k])
                val tx = nx - 6f * (k + 1)
                c.drawRect(tx - 1f, 70f, tx + 1f, 162f, fill)
            }
        }
        if (m.fmDead) {
            fill.color = withAlpha(cw.lcdSub, 128)
        } else {
            fill.color = withAlpha(TANGERINE, 36)
            c.drawRect(nx - 6f, 70f, nx + 6f, 162f, fill)
            fill.color = TANGERINE
        }
        c.drawRect(nx - 1f, 70f, nx + 1f, 162f, fill)
        r.set(nx - 4f, 70f, nx + 4f, 73f)
        c.drawRoundRect(r, 1f, 1f, fill)
        c.restore()

        drawLcd(c, cw, m, freq)

        fill.color = GLARE
        c.drawPath(glare, fill)
    }

    private fun drawLcd(c: Canvas, cw: FaceplateColorway, m: RadioDeckModel, freq: Float) {
        val lit = cw.lcdText
        val off = cw.ledOff
        c.save()
        c.clipRect(RadioDeckGeometry.lcd)

        stroke.color = lit; stroke.strokeWidth = 1f
        r.set(34f, 184f, 64f, 198f); c.drawRoundRect(r, 2f, 2f, stroke)
        mono.textSize = 9.5f; mono.color = lit; mono.textAlign = Paint.Align.CENTER
        c.drawText(if (m.isNet) "NET" else "FM", 49f, 194.5f, mono)

        m.warning?.let { warn ->
            val w = maxOf(46f, mono.measureText(warn) + 12f)
            r.set(70f, 184f, 70f + w, 198f)
            fill.color = withAlpha(cw.ledHot, 31); c.drawRoundRect(r, 2f, 2f, fill)
            stroke.color = cw.ledHot; c.drawRoundRect(r, 2f, 2f, stroke)
            mono.color = cw.ledHot
            c.drawText(warn, r.centerX(), 194.5f, mono)
        }

        mono.textAlign = Paint.Align.LEFT
        mono.textSize = 10f
        mono.color = if (m.activePreset >= 0) lit else off
        c.drawText(if (m.activePreset >= 0) "P${m.activePreset + 1}" else "P-", 34f, 214f, mono)
        mono.textSize = 8.5f
        mono.color = if (m.lcdPilotLit) lit else off
        c.drawText(m.lcdPilotText, 34f, 230f, mono)

        if (m.genre.isNotEmpty()) {
            print.letterSpacing = 0.13f
            print.textSize = 7.5f
            print.color = cw.lcdSub
            print.textAlign = Paint.Align.RIGHT
            c.drawText(m.genre, 326f, 194f, print)
        }

        if (freq != freqTextFor) {
            freqTextFor = freq
            freqText = String.format(Locale.US, "%.1f", freq)
        }
        // Ghost segments stay faint so the unlit leading "1" never reads as part of the frequency
        bigFreq.color = withAlpha(off, 150)
        c.drawText("188.8", 282f, 232f, bigFreq)
        bigFreq.color = if (m.fmDead) withAlpha(cw.lcdSub, 179) else lit
        c.drawText(freqText, 282f, 232f, bigFreq)
        mono.textSize = 11f; mono.color = cw.lcdSub
        c.drawText("MHz", 288f, 232f, mono)

        stroke.color = withAlpha(cw.lcdSub, 46)
        c.drawLine(34f, 241f, 326f, 241f, stroke)
        mono.textSize = 8f
        c.drawText(m.lineTag, 34f, 255f, mono)
        mono.textSize = 9.5f
        if (m.line != lineFor) {
            lineFor = m.line
            lineShown = TextUtils.ellipsize(m.line, mono, 264f, TextUtils.TruncateAt.END)
        }
        mono.color = if (m.lineWarn) cw.ledHot else lit
        c.drawText(lineShown, 0, lineShown.length, 62f, 255f, mono)
        c.restore()
    }

    private fun drawTuningRow(c: Canvas, cw: FaceplateColorway, m: RadioDeckModel, st: RadioDeckRenderState) {
        if (m.fmDead) c.saveLayerAlpha(0f, 282f, 360f, 337f, DIM_ALPHA)
        drawText(c, "SEEK", 36f, 293f, cw.print, Paint.Align.CENTER)
        drawText(c, "TUNING", 180f, 293f, cw.print, Paint.Align.CENTER)
        drawText(c, "SEEK", 324f, 293f, cw.print, Paint.Align.CENTER)

        drawSeekButton(c, cw, 36f, -1f, st.pressed == RadioDeckView.ID_SEEK_PREV)
        drawSeekButton(c, cw, 324f, 1f, st.pressed == RadioDeckView.ID_SEEK_NEXT)

        fill.color = cw.recess
        r.set(58f, 300f, 302f, 328f); c.drawRoundRect(r, 5f, 5f, fill)
        if (wheelShaderFor != cw) {
            wheelShader = LinearGradient(
                62f, 0f, 298f, 0f, intArrayOf(cw.knobGroove, cw.knob, cw.knobGroove), null, Shader.TileMode.CLAMP
            )
            wheelShaderFor = cw
        }
        fill.shader = wheelShader
        r.set(62f, 303f, 298f, 325f); c.drawRoundRect(r, 3f, 3f, fill)
        fill.shader = null

        // Knurls rotate with the drag distance (cylinder radius 116 dp)
        val phase = Math.toDegrees((st.wheelPhase / 116f).toDouble()).toFloat().mod(6f)
        for (k in -15..15) {
            val deg = k * 6f + phase
            if (abs(deg) > 86f) continue
            val rad = Math.toRadians(deg.toDouble())
            val cosA = cos(rad).toFloat()
            val x = 180f + 116f * sin(rad).toFloat()
            stroke.color = withAlpha(cw.recess, ((0.15f + 0.5f * cosA) * 255).toInt())
            stroke.strokeWidth = 0.5f + 1.1f * cosA
            c.drawLine(x, 305f, x, 323f, stroke)
        }
        fill.color = Color.argb(31, 255, 255, 255)
        r.set(62f, 303f, 298f, 307f); c.drawRoundRect(r, 2f, 2f, fill)
        fill.color = TANGERINE
        glyph.reset()
        glyph.moveTo(176f, 296f); glyph.lineTo(184f, 296f); glyph.lineTo(180f, 301f); glyph.close()
        c.drawPath(glyph, fill)
        if (m.fmDead) c.restore()
    }

    private fun drawSeekButton(c: Canvas, cw: FaceplateColorway, cx: Float, dir: Float, pressed: Boolean) {
        fill.color = if (pressed) cw.keyFront else cw.keyTop
        c.drawCircle(cx, 314f, 13f, fill)
        stroke.color = cw.keyFront; stroke.strokeWidth = 1.5f
        c.drawCircle(cx, 314f, 13f, stroke)
        fill.color = cw.keyGlyph
        glyph.reset()
        for (tip in floatArrayOf(cx, cx + 7f * dir)) {
            glyph.moveTo(tip + 0f, 314f)
            glyph.lineTo(tip - 7f * dir, 309f)
            glyph.lineTo(tip - 7f * dir, 319f)
            glyph.close()
        }
        c.drawPath(glyph, fill)
    }

    private fun drawMeter(c: Canvas, cw: FaceplateColorway, mt: RadioDeckModel.Meter) {
        meterText.textAlign = Paint.Align.CENTER
        meterText.textSize = 7f
        meterText.color = cw.lcdSub
        c.drawText(mt.row1Name, 22f, 353f, meterText)
        c.drawText(mt.row2Name, 22f, 367f, meterText)

        val lit1 = RadioDeckMath.levelSegments(mt.row1Level)
        for (i in 0 until RadioDeckMath.SEGMENTS) segment(c, i, 346f, if (i < lit1) cw.ledOn else cw.ledOff)
        if (mt.threshold >= 0f) {
            val mx = SEG_X0 + RadioDeckMath.levelSegments(mt.threshold) * SEG_STEP - 1.2f
            fill.color = TANGERINE
            glyph.reset()
            glyph.moveTo(mx - 3f, 340f); glyph.lineTo(mx + 3f, 340f); glyph.lineTo(mx, 344f); glyph.close()
            c.drawPath(glyph, fill)
        }

        if (mt.row2CenterZero) {
            val centered = mt.tuneActive && RadioDeckMath.isCentered(mt.tuneDeviation)
            val single = if (mt.tuneActive && !centered) RadioDeckMath.tuneSegment(mt.tuneDeviation) else -1
            for (i in 0 until RadioDeckMath.SEGMENTS) {
                val on = (centered && (i == 12 || i == 13)) || i == single
                segment(c, i, 360f, if (on) cw.ledOn else cw.ledOff)
            }
        } else {
            val lit2 = RadioDeckMath.levelSegments(mt.row2Level)
            for (i in 0 until RadioDeckMath.SEGMENTS) segment(c, i, 360f, if (i < lit2) cw.ledOn else cw.ledOff)
        }
        if (mt.showLockLamp) {
            fill.color = if (mt.locked) cw.ledOn else cw.ledOff
            c.drawCircle(338f, 364f, 3f, fill)
        }

        meterText.textSize = 7.5f
        meterText.textAlign = Paint.Align.LEFT
        c.drawText(mt.scaleLeft, SEG_X0, 384f, meterText)
        meterText.textAlign = Paint.Align.CENTER
        c.drawText(mt.scaleCenter, SEG_X0 + 13 * SEG_STEP - 1.2f, 384f, meterText)
        meterText.textAlign = Paint.Align.RIGHT
        c.drawText(mt.scaleRight, SEG_X0 + 25 * SEG_STEP + SEG_W, 384f, meterText)
    }

    private fun segment(c: Canvas, i: Int, y: Float, color: Int) {
        fill.color = color
        val x = SEG_X0 + i * SEG_STEP
        r.set(x, y, x + SEG_W, y + 8f)
        c.drawRoundRect(r, 1f, 1f, fill)
    }

    private fun drawSwitches(c: Canvas, cw: FaceplateColorway, m: RadioDeckModel) {
        drawKnob(c, cw, 16f, 140f, BAND_STOPS, if (m.isNet) 1 else 0)

        val modeDim = m.isNet || m.fmDead
        if (modeDim) c.saveLayerAlpha(170f, 396f, 292f, 448f, DIM_ALPHA)
        drawText(c, "MODE", 176f, 406f, cw.print, Paint.Align.LEFT)
        fill.color = cw.recess
        r.set(176f, 424f, 288f, 440f); c.drawRoundRect(r, 8f, 8f, fill)
        drawKnob(c, cw, 176f, 112f, MODE_STOPS, if (m.forceMono) 1 else 0)
        if (modeDim) c.restore()

        print.isFakeBoldText = true
        drawText(c, m.lampLabel, 344f, 406f, cw.print, Paint.Align.RIGHT)
        print.isFakeBoldText = false
        drawLamp(c, 326f, 432f, 3.5f, m.lampOn)
    }

    private fun drawKnob(c: Canvas, cw: FaceplateColorway, x: Float, w: Float, stops: Array<String>, active: Int) {
        val step = w / stops.size
        for (i in stops.indices) {
            val p = if (i == active) printActive.apply { color = cw.print } else printInactive.apply {
                color = cw.printMuted
            }
            c.drawText(stops[i], x + step * i + step / 2f, 419f, p)
        }
        val kx = x + step * active + 3f
        val kw = step - 6f
        fill.color = cw.knob
        r.set(kx, 422f, kx + kw, 442f); c.drawRoundRect(r, 4f, 4f, fill)
        stroke.color = cw.knobGroove; stroke.strokeWidth = 0.8f
        c.drawRoundRect(r, 4f, 4f, stroke)
        stroke.strokeWidth = 1f
        for (g in -2..2) {
            val gx = kx + kw / 2f + g * 4f
            c.drawLine(gx, 426f, gx, 438f, stroke)
        }
    }

    private fun drawLamp(c: Canvas, x: Float, y: Float, radius: Float, on: Boolean) {
        if (on) {
            fill.color = HALO
            c.drawCircle(x, y, radius * 2f, fill)
        }
        fill.color = if (on) EMERALD else LAMP_OFF
        c.drawCircle(x, y, radius, fill)
    }

    private fun drawKeys(c: Canvas, cw: FaceplateColorway, m: RadioDeckModel, pressed: Int) {
        val w = RadioDeckGeometry.KEY_W
        for (i in 0 until RadioDeckGeometry.KEY_COUNT) {
            val x = RadioDeckGeometry.keyLeft(i)
            val cx = x + w / 2f
            val isScan = i == RadioDeckGeometry.KEY_SCAN
            val preset = m.presets.getOrNull(i)
            val enabled = isKeyEnabled(m, i)
            val latched = if (isScan) m.scanning else i == m.activePreset
            val down = latched || pressed == i

            if (!enabled) c.saveLayerAlpha(x - 3f, 456f, x + w + 3f, 576f, DIM_ALPHA)

            val label = if (isScan) "SCAN" else preset?.label.orEmpty()
            printActive.color = cw.print
            printActive.letterSpacing = if (label.length > 7) 0.05f else 0.15f
            printActive.textSize = 8.5f
            val lw = printActive.measureText(label)
            if (lw > w + 4f) printActive.textSize = 8.5f * (w + 4f) / lw
            c.drawText(label, cx, 470f, printActive)
            printActive.letterSpacing = 0.15f
            printActive.textSize = 8.5f

            val top = if (latched) cw.playTop else cw.keyTop
            val front = if (latched) cw.playFront else cw.keyFront
            val edge = if (latched) cw.playTopEdge else cw.keyTopEdge
            val glyphColor = if (latched) cw.playGlyph else cw.keyGlyph
            val ty = if (down) 488f else 478f
            val th = if (down) 68f else 70f
            val fh = if (down) 8f else 18f

            fill.color = front
            r.set(x, ty + th - 6f, x + w, ty + th + fh); c.drawRoundRect(r, 5f, 5f, fill)
            fill.color = top
            r.set(x, ty, x + w, ty + th); c.drawRoundRect(r, 5f, 5f, fill)
            fill.color = withAlpha(edge, 180)
            r.set(x + 1f, ty + 1f, x + w - 1f, ty + 3f); c.drawRoundRect(r, 1f, 1f, fill)
            stroke.color = withAlpha(front, 140); stroke.strokeWidth = 1f
            for (k in 0 until 3) {
                val ly = ty + 9f + k * 4f
                c.drawLine(x + 14f, ly, x + w - 14f, ly, stroke)
            }

            val cy = ty + th / 2f + 6f
            if (isScan) {
                fill.color = glyphColor
                glyph.reset()
                glyph.moveTo(cx - 12f, cy - 2f); glyph.lineTo(cx - 6f, cy - 7f); glyph.lineTo(
                    cx - 6f,
                    cy + 3f
                ); glyph.close()
                glyph.moveTo(cx + 12f, cy - 2f); glyph.lineTo(cx + 6f, cy - 7f); glyph.lineTo(
                    cx + 6f,
                    cy + 3f
                ); glyph.close()
                c.drawPath(glyph, fill)
                c.drawRect(cx - 7f, cy - 3f, cx + 7f, cy - 1f, fill)
                fill.color = if (latched) glyphColor else TANGERINE
                r.set(cx - 1f, cy - 9f, cx + 1f, cy + 5f); c.drawRoundRect(r, 1f, 1f, fill)
            } else if (preset != null) {
                numeral.color = glyphColor
                c.drawText(preset.number, cx, cy + 2f, numeral)
                mono.textSize = 8.5f
                mono.textAlign = Paint.Align.CENTER
                mono.color = withAlpha(glyphColor, 191)
                c.drawText(preset.freqText, cx, cy + 16f, mono)
            }
            if (!enabled) c.restore()
        }
    }

    fun isKeyEnabled(m: RadioDeckModel, i: Int): Boolean = when {
        m.fmDead -> false
        i == RadioDeckGeometry.KEY_SCAN -> !m.isNet
        else -> m.presets.getOrNull(i)?.enabled == true
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
