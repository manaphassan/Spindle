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
import android.text.TextPaint
import android.text.TextUtils

/** Mutable render state shared by [FaceplateDeckView] and its painters (design units = dp). */
internal class FaceplateDeckState {
    var colorway = FaceplateColorway.SILVER
    var title = ""
    var subtitle = ""
    var positionLabel = "C-90 · 45 MIN PER SIDE · REFERENCE"
    var formatLine = ""
    var rateLine = ""
    var counterDigits = "0000"
    var reverseLabel = "OFF"
    var dirForward = false
    var dirReverse = false
    var running = false
    var tapeIndex = 0
    var dolbyIndex = 0
    var isDirect = true
    var progress = 0f
    var leftAngle = 0f
    var rightAngle = 0f
    var albumArt: Bitmap? = null
    var litL = 0
    var litR = 0
    var holdL = -1
    var holdR = -1
    var clipL = false
    var clipR = false
    var pressedKey = -1
    var playLatched = false
    var resetPressed = false
}

/** Fixed layout of the faceplate on a 360 x 592 dp design canvas. */
internal object FaceplateGeometry {
    const val DESIGN_W = 360f
    const val DESIGN_H = 592f
    const val KEY_COUNT = 5
    const val KEY_W = 62.4f
    const val KEY_GAP = 6f

    val door = RectF(12f, 52f, 348f, 276f)
    val glass = RectF(18f, 58f, 342f, 270f)
    val counter = RectF(12f, 284f, 104f, 336f)
    val reset = RectF(104f, 288f, 134f, 336f)
    val reverse = RectF(136f, 284f, 208f, 336f)
    val lcd = RectF(216f, 290f, 348f, 330f)
    val meter = RectF(12f, 338f, 348f, 390f)
    val tapeSwitch = RectF(16f, 396f, 176f, 448f)
    val dolbySwitch = RectF(196f, 396f, 344f, 448f)

    fun keyLeft(i: Int) = 12f + i * (KEY_W + KEY_GAP)
    fun keyHit(i: Int, out: RectF): RectF = out.apply { set(keyLeft(i), 460f, keyLeft(i) + KEY_W, 576f) }
}

internal class FaceplateCassettePainter {

    private companion object {
        val SHELL = Color.parseColor("#2E3039")
        val SHELL_EDGE = Color.parseColor("#3A3D48")
        val SCREW = Color.parseColor("#5A5E6B")
        val LABEL = Color.parseColor("#FDE68A")
        val TANGERINE = Color.parseColor("#F97316")
        val INDIGO = Color.parseColor("#2A2E45")
        val WINDOW = Color.parseColor("#1B1C22")
        val TAPE = Color.parseColor("#4A3426")
        val TAPE_EDGE = Color.parseColor("#5C4231")
        val HUB = Color.parseColor("#F5F5F4")
        val HUB_CORE = Color.parseColor("#23252F")
        val HOLE = Color.parseColor("#0E0F14")
        val HEAD = Color.parseColor("#262830")
        val PAD = Color.parseColor("#C9B79C")
        val RULER = Color.argb(150, 253, 230, 138)
        val RING = Color.argb(180, 92, 66, 49)
    }

    private val kinematics = SpindleKinematics(hubRadiusRatio = 0.375f, maxTapeRadiusRatio = 1.0f)
    private val spool = SpindleKinematics.SpoolState()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val handTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("casual", Typeface.BOLD)
        textSize = 15f
        color = Color.parseColor("#2A2E45")
    }
    private val handSub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("casual", Typeface.NORMAL)
        textSize = 10.5f
        color = Color.parseColor("#4A4F6A")
    }
    private val sideLetter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textSize = 30f
        color = Color.parseColor("#2A2E45")
    }
    private val labelPrint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 7f
        letterSpacing = 0.17f
        color = Color.parseColor("#2A2E45")
    }

    private val tmpRect = RectF()
    private val windowRect = RectF(100f, 124f, 264f, 172f)
    private val windowPath = Path().apply { addRoundRect(windowRect, 24f, 24f, Path.Direction.CW) }
    private val headPath = Path().apply {
        moveTo(98f, 222f); lineTo(262f, 222f); lineTo(282f, 262f); lineTo(78f, 262f); close()
    }
    private val glare = LinearGradient(
        18f, 58f, 342f, 270f,
        intArrayOf(Color.argb(26, 255, 255, 255), Color.argb(5, 255, 255, 255), Color.argb(0, 255, 255, 255)),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP
    )

    private var cachedTitleSrc = ""
    private var cachedTitle = ""
    private var cachedSubSrc = ""
    private var cachedSub = ""

    fun draw(canvas: Canvas, st: FaceplateDeckState) {
        // Shell
        fill.color = SHELL
        tmpRect.set(26f, 64f, 334f, 262f)
        canvas.drawRoundRect(tmpRect, 8f, 8f, fill)
        stroke.color = SHELL_EDGE; stroke.strokeWidth = 1f
        canvas.drawRoundRect(tmpRect, 8f, 8f, stroke)
        drawScrew(canvas, 34f, 72f); drawScrew(canvas, 326f, 72f)
        drawScrew(canvas, 34f, 254f); drawScrew(canvas, 326f, 254f)

        // Paper label with tangerine stripe
        fill.color = LABEL
        tmpRect.set(42f, 72f, 318f, 208f)
        canvas.drawRoundRect(tmpRect, 4f, 4f, fill)
        fill.color = TANGERINE
        canvas.drawRect(42f, 180f, 318f, 192f, fill)
        fill.color = INDIGO
        canvas.drawRect(42f, 192f, 318f, 195f, fill)

        if (st.title != cachedTitleSrc) {
            cachedTitleSrc = st.title
            cachedTitle = TextUtils.ellipsize(st.title, handTitle, 250f, TextUtils.TruncateAt.END).toString()
        }
        if (st.subtitle != cachedSubSrc) {
            cachedSubSrc = st.subtitle
            cachedSub = TextUtils.ellipsize(st.subtitle, handSub, 250f, TextUtils.TruncateAt.END).toString()
        }
        canvas.drawText(cachedTitle, 54f, 97f, handTitle)
        canvas.drawText(cachedSub, 54f, 114f, handSub)
        canvas.drawText("A", 58f, 166f, sideLetter)
        canvas.drawText(st.positionLabel, 54f, 204f, labelPrint)

        drawArtSticker(canvas, st.albumArt)
        drawWindowAndReels(canvas, st)
        drawHeadArea(canvas)

        fill.shader = glare
        canvas.drawRoundRect(FaceplateGeometry.glass, 7f, 7f, fill)
        fill.shader = null
    }

    private fun drawScrew(canvas: Canvas, x: Float, y: Float) {
        fill.color = SCREW
        canvas.drawCircle(x, y, 3.4f, fill)
        stroke.color = SHELL; stroke.strokeWidth = 0.9f
        canvas.drawLine(x - 2f, y, x + 2f, y, stroke)
        canvas.drawLine(x, y - 2f, x, y + 2f, stroke)
    }

    private fun drawArtSticker(canvas: Canvas, art: Bitmap?) {
        canvas.save()
        canvas.rotate(4f, 296f, 148f)
        fill.color = Color.WHITE
        canvas.drawRect(275f, 127f, 317f, 169f, fill)
        tmpRect.set(278f, 130f, 314f, 166f)
        if (art != null && !art.isRecycled) {
            canvas.drawBitmap(art, null, tmpRect, bitmapPaint)
        } else {
            fill.color = INDIGO
            canvas.drawRect(tmpRect, fill)
            fill.color = TANGERINE
            canvas.drawCircle(296f, 148f, 10f, fill)
            fill.color = INDIGO
            canvas.drawCircle(296f, 148f, 3.5f, fill)
        }
        canvas.restore()
    }

    private fun drawWindowAndReels(canvas: Canvas, st: FaceplateDeckState) {
        fill.color = WINDOW
        canvas.drawRoundRect(windowRect, 24f, 24f, fill)

        canvas.save()
        canvas.clipPath(windowPath)
        kinematics.calculate(st.progress, 40f, spool)
        drawReel(canvas, 132f, 148f, spool.leftRadius, st.leftAngle)
        drawReel(canvas, 232f, 148f, spool.rightRadius, st.rightAngle)
        stroke.color = RULER; stroke.strokeWidth = 0.6f
        for (i in 0..10) {
            val x = 152f + i * 6f
            canvas.drawLine(x, 126f, x, if (i % 5 == 0) 133f else 130f, stroke)
        }
        canvas.restore()

        stroke.color = INDIGO; stroke.strokeWidth = 1.4f
        canvas.drawRoundRect(windowRect, 24f, 24f, stroke)
    }

    private fun drawReel(canvas: Canvas, cx: Float, cy: Float, packR: Float, angle: Float) {
        fill.color = TAPE
        canvas.drawCircle(cx, cy, packR, fill)
        stroke.color = RING; stroke.strokeWidth = 0.6f
        var r = 18f
        while (r < packR) {
            canvas.drawCircle(cx, cy, r, stroke)
            r += 4f
        }
        fill.color = HUB
        canvas.drawCircle(cx, cy, 15f, fill)
        fill.color = HUB_CORE
        canvas.drawCircle(cx, cy, 9f, fill)

        canvas.save()
        canvas.rotate(angle, cx, cy)
        for (i in 0 until 6) {
            canvas.save()
            canvas.rotate(i * 60f, cx, cy)
            fill.color = if (i == 0) TANGERINE else HUB
            tmpRect.set(cx - 1.6f, cy - 9.8f, cx + 1.6f, cy - 4.6f)
            canvas.drawRoundRect(tmpRect, 0.8f, 0.8f, fill)
            canvas.restore()
        }
        canvas.restore()
        fill.color = HOLE
        canvas.drawCircle(cx, cy, 3.2f, fill)
    }

    private fun drawHeadArea(canvas: Canvas) {
        fill.color = HEAD
        canvas.drawPath(headPath, fill)
        stroke.color = SHELL_EDGE; stroke.strokeWidth = 1f
        canvas.drawPath(headPath, stroke)
        fill.color = HOLE
        canvas.drawCircle(124f, 244f, 5f, fill)
        canvas.drawCircle(236f, 244f, 5f, fill)
        canvas.drawRect(102f, 240f, 109f, 248f, fill)
        canvas.drawRect(251f, 240f, 258f, 248f, fill)
        tmpRect.set(170f, 236f, 190f, 252f)
        canvas.drawRoundRect(tmpRect, 2f, 2f, fill)
        fill.color = PAD
        canvas.drawRect(175f, 248f, 185f, 252f, fill)
        stroke.color = TAPE_EDGE; stroke.strokeWidth = 2.2f
        canvas.drawLine(82f, 259.5f, 278f, 259.5f, stroke)
    }
}
