package com.hana.spindle.ui.cassette

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Draws the Walkman chassis. Everything that never moves (brushed body, printed
 * legends, recesses, key wells) is rendered once into a cached bitmap; [drawDynamic]
 * only paints lamps, counter, meter, switch knobs and keys each frame.
 */
internal class WalkmanFaceplatePainter {

    companion object {
        val KEY_LABELS = arrayOf("REW", "PLAY", "FF", "STOP", "EJECT")
        val TAPE_STOPS = arrayOf("FLAT", "I", "II", "III", "IV")
        val DOLBY_STOPS = arrayOf("OFF", "B", "C")

        /** Lower edge of each meter segment in dBFS (26 segments, true digital peak scale). */
        val METER_DB = floatArrayOf(
            -48f, -42f, -36f, -33f, -30f, -27f, -24f, -21f, -18f, -16f, -14f, -12f, -10f,
            -9f, -8f, -7f, -6f, -5f, -4f, -3f, -2.5f, -2f, -1.5f, -1f, -0.5f, -0.1f
        )
        const val METER_HOT_FROM = 23 // >= -1 dBFS
        private val METER_SCALE =
            arrayOf("-48" to 0, "-36" to 2, "-24" to 6, "-18" to 8, "-12" to 11, "-6" to 16, "-3" to 19, "0" to 25)

        const val SEG_X0 = 34f
        const val SEG_W = 9f
        const val SEG_STEP = 11.4f

        private val EMERALD = Color.parseColor("#00E676")
        private val LAMP_OFF = Color.parseColor("#2C3A33")
        private val TANGERINE = Color.parseColor("#F97316")
        private val DRUM_TEXT = Color.parseColor("#FAFAF9")
        private val DRUM_HI = Color.argb(16, 255, 255, 255)
        private val DRUM_LO = Color.argb(90, 0, 0, 0)
        private val WELL = Color.argb(71, 0, 0, 0)
        private val HALO = Color.argb(46, 0, 230, 118)
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val print = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 8f
        letterSpacing = 0.15f
    }
    private val printActive = Paint(print).apply { textSize = 8.5f; isFakeBoldText = true }
    private val printInactive = Paint(print).apply { textSize = 8.5f }
    private val wordmark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 17f
        letterSpacing = 0.3f
        isFakeBoldText = true
    }
    private val lcdMain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 11f
    }
    private val lcdSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 9.5f
    }
    private val drum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 17f
        textAlign = Paint.Align.CENTER
        color = DRUM_TEXT
    }
    private val scale = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 7.5f
        textAlign = Paint.Align.CENTER
    }

    private val r = RectF()
    private val glyph = Path()

    fun buildStatic(width: Int, height: Int, s: Float, ox: Float, oy: Float, cw: WalkmanColorway): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), cw.bodyTop, cw.bodyBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null

        // Brushed metal grain (deterministic so it never shimmers between rebuilds)
        var seed = 7L
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
        drawText(c, "STEREO CASSETTE PLAYER", 344f, 20f, cw.bandSub, Paint.Align.RIGHT)
        drawText(c, "RUN", 344f, 34f, cw.bandSub, Paint.Align.RIGHT)

        fill.color = cw.doorFrame
        c.drawRoundRect(WalkmanGeometry.door, 10f, 10f, fill)
        fill.color = cw.glass
        c.drawRoundRect(WalkmanGeometry.glass, 7f, 7f, fill)

        drawText(c, "COUNTER", 16f, 293f, cw.print, Paint.Align.LEFT)
        drawText(c, "RESET", 119f, 331f, cw.print, Paint.Align.CENTER)
        drawText(c, "AUTO REVERSE", 172f, 293f, cw.print, Paint.Align.CENTER)

        fill.color = cw.recess
        c.drawRoundRect(WalkmanGeometry.lcd, 4f, 4f, fill)
        c.drawRoundRect(WalkmanGeometry.meter, 6f, 6f, fill)
        drawText(c, "L", 22f, 353f, cw.lcdSub, Paint.Align.CENTER)
        drawText(c, "R", 22f, 367f, cw.lcdSub, Paint.Align.CENTER)
        for ((label, i) in METER_SCALE) {
            scale.color = if (i >= METER_HOT_FROM) cw.ledHot else cw.lcdSub
            c.drawText(label, SEG_X0 + i * SEG_STEP + SEG_W / 2f, 384f, scale)
        }
        scale.color = cw.lcdSub
        c.drawText("dBFS", 338f, 384f, scale)

        drawText(c, "TAPE EQ", 16f, 406f, cw.print, Paint.Align.LEFT)
        drawText(c, "DOLBY NR", 196f, 406f, cw.print, Paint.Align.LEFT)
        drawText(c, "DIRECT", 344f, 406f, cw.print, Paint.Align.RIGHT)
        fill.color = cw.recess
        r.set(16f, 424f, 176f, 440f); c.drawRoundRect(r, 8f, 8f, fill)
        r.set(196f, 424f, 344f, 440f); c.drawRoundRect(r, 8f, 8f, fill)

        for (i in 0 until WalkmanGeometry.KEY_COUNT) {
            val x = WalkmanGeometry.keyLeft(i)
            drawText(c, KEY_LABELS[i], x + WalkmanGeometry.KEY_W / 2f, 470f, cw.print, Paint.Align.CENTER, 8.5f)
            fill.color = WELL
            r.set(x - 2f, 474f, x + WalkmanGeometry.KEY_W + 2f, 572f)
            c.drawRoundRect(r, 6f, 6f, fill)
        }

        drawText(c, "‹ RADIO", 16f, 586f, cw.hint, Paint.Align.LEFT)
        drawText(c, "TAP CASSETTE FOR J-CARD", 180f, 586f, cw.hint, Paint.Align.CENTER)
        drawText(c, "SYSTEM ›", 344f, 586f, cw.hint, Paint.Align.RIGHT)
        return bmp
    }

    private fun drawText(c: Canvas, t: String, x: Float, y: Float, color: Int, align: Paint.Align, size: Float = 8f) {
        print.color = color
        print.textAlign = align
        print.textSize = size
        c.drawText(t, x, y, print)
    }

    fun drawDynamic(c: Canvas, st: WalkmanDeckState) {
        val cw = st.colorway
        drawLamp(c, 308f, 31f, st.running)
        drawLamp(c, 286f, 403f, st.isDirect)

        // Counter drums MM:SS
        val xs = floatArrayOfDrums
        for (i in 0 until 4) {
            val x = xs[i]
            fill.color = cw.recess
            r.set(x, 298f, x + 18f, 324f); c.drawRoundRect(r, 2f, 2f, fill)
            fill.color = DRUM_HI; c.drawRect(x, 298f, x + 18f, 304f, fill)
            fill.color = DRUM_LO; c.drawRect(x, 318f, x + 18f, 324f, fill)
            val ch = if (i < st.counterDigits.length) st.counterDigits[i] else '0'
            drumChar[0] = ch
            c.drawText(drumChar, 0, 1, x + 9f, 317f, drum)
        }
        fill.color = cw.print
        c.drawCircle(59f, 306f, 1.6f, fill)
        c.drawCircle(59f, 316f, 1.6f, fill)

        fill.color = if (st.resetPressed) cw.keyFront else cw.keyTop
        c.drawCircle(119f, 311f, 9f, fill)
        stroke.color = cw.keyFront; stroke.strokeWidth = 1.5f
        c.drawCircle(119f, 311f, 9f, stroke)

        // Transport direction + auto-reverse mode
        glyph.reset()
        glyph.moveTo(148f, 311f); glyph.lineTo(158f, 304f); glyph.lineTo(158f, 318f); glyph.close()
        fill.color = if (st.dirReverse) TANGERINE else withAlpha(cw.printMuted, 110)
        c.drawPath(glyph, fill)
        glyph.reset()
        glyph.moveTo(196f, 311f); glyph.lineTo(186f, 304f); glyph.lineTo(186f, 318f); glyph.close()
        fill.color = if (st.dirForward) TANGERINE else withAlpha(cw.printMuted, 110)
        c.drawPath(glyph, fill)
        printActive.color = cw.print; printActive.textAlign = Paint.Align.CENTER
        c.drawText(st.reverseLabel, 172f, 331f, printActive)

        // Format readout
        c.save()
        c.clipRect(WalkmanGeometry.lcd)
        lcdMain.color = cw.lcdText
        c.drawText(st.formatLine, 226f, 306f, lcdMain)
        lcdSub.color = cw.lcdSub
        c.drawText(st.rateLine, 226f, 321f, lcdSub)
        c.restore()

        drawMeterRow(c, cw, 346f, st.litL, st.holdL, st.clipL)
        drawMeterRow(c, cw, 360f, st.litR, st.holdR, st.clipR)

        drawSwitch(c, cw, 16f, 160f, TAPE_STOPS, st.tapeIndex)
        drawSwitch(c, cw, 196f, 148f, DOLBY_STOPS, st.dolbyIndex)

        for (i in 0 until WalkmanGeometry.KEY_COUNT) {
            drawKey(c, cw, i, st.pressedKey == i || (i == 1 && st.playLatched))
        }
    }

    private val floatArrayOfDrums = floatArrayOf(16f, 36f, 64f, 84f)
    private val drumChar = CharArray(1)

    private fun drawLamp(c: Canvas, x: Float, y: Float, on: Boolean) {
        if (on) {
            fill.color = HALO
            c.drawCircle(x, y, 6f, fill)
        }
        fill.color = if (on) EMERALD else LAMP_OFF
        c.drawCircle(x, y, 3f, fill)
    }

    private fun drawMeterRow(c: Canvas, cw: WalkmanColorway, y: Float, lit: Int, hold: Int, clip: Boolean) {
        for (i in METER_DB.indices) {
            val on = i < lit || i == hold
            fill.color = when {
                !on -> cw.ledOff
                i >= METER_HOT_FROM -> cw.ledHot
                else -> cw.ledOn
            }
            val x = SEG_X0 + i * SEG_STEP
            r.set(x, y, x + SEG_W, y + 8f)
            c.drawRoundRect(r, 1f, 1f, fill)
        }
        fill.color = if (clip) cw.ledHot else cw.ledOff
        c.drawCircle(338f, y + 4f, 3f, fill)
    }

    private fun drawSwitch(c: Canvas, cw: WalkmanColorway, x: Float, w: Float, stops: Array<String>, active: Int) {
        val step = w / stops.size
        for (i in stops.indices) {
            val p = if (i == active) printActive.apply { color = cw.print } else printInactive.apply {
                color = cw.printMuted
            }
            p.textAlign = Paint.Align.CENTER
            c.drawText(stops[i], x + step * i + step / 2f, 419f, p)
        }
        val kx = x + step * active.coerceIn(0, stops.size - 1) + 3f
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

    private fun drawKey(c: Canvas, cw: WalkmanColorway, i: Int, down: Boolean) {
        val x = WalkmanGeometry.keyLeft(i)
        val w = WalkmanGeometry.KEY_W
        val isPlay = i == 1
        val top = if (isPlay) cw.playTop else cw.keyTop
        val front = if (isPlay) cw.playFront else cw.keyFront
        val edge = if (isPlay) cw.playTopEdge else cw.keyTopEdge
        val glyphColor = if (isPlay) cw.playGlyph else cw.keyGlyph
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

        val cx = x + w / 2f
        val cy = ty + th / 2f + 6f
        fill.color = glyphColor
        glyph.reset()
        when (i) {
            0 -> {
                tri(cx - 1f, cy, -1f); tri(cx - 11f, cy, -1f)
            }

            1 -> {
                glyph.moveTo(cx - 6f, cy - 9f); glyph.lineTo(cx + 9f, cy); glyph.lineTo(cx - 6f, cy + 9f); glyph.close()
            }

            2 -> {
                tri(cx + 1f, cy, 1f); tri(cx + 11f, cy, 1f)
            }

            3 -> {
                r.set(cx - 7f, cy - 7f, cx + 7f, cy + 7f); glyph.addRoundRect(r, 1.5f, 1.5f, Path.Direction.CW)
            }

            else -> {
                glyph.moveTo(cx, cy - 9f); glyph.lineTo(cx + 9f, cy); glyph.lineTo(cx - 9f, cy); glyph.close()
                r.set(cx - 9f, cy + 3f, cx + 9f, cy + 6.5f); glyph.addRoundRect(r, 1f, 1f, Path.Direction.CW)
            }
        }
        c.drawPath(glyph, fill)
    }

    /** Adds a 10x14 triangle with its tip at (tipX, cy) pointing left (dir -1) or right (dir 1). */
    private fun tri(tipX: Float, cy: Float, dir: Float) {
        glyph.moveTo(tipX, cy)
        glyph.lineTo(tipX - 10f * dir, cy - 7f)
        glyph.lineTo(tipX - 10f * dir, cy + 7f)
        glyph.close()
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}
