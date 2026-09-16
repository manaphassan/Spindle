package com.hana.spindle.ui.cassette

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.hana.spindle.theme.CassetteTheme
import com.hana.spindle.theme.ChassisStyle

/**
 * Hardware-accelerated Custom View rendering the flagship Vertical Walkman Deck.
 * Inspired by retro Sony Walkman vertical players and 80s vaporwave tape decks.
 *
 * Implements:
 * 1. Dark brushed aluminum faceplate with vertical grain / Studio White with diagonal neon racing stripes.
 * 2. Retro geometric WALKMAN / SPINDLE typography.
 * 3. Deep recessed cassette bay with metallic chrome bevel and drop shadow.
 * 4. Analog linear tape counter ruler (||| 0 ||| 1 ||| ... ||| 9 |||) with interactive red mechanical slider.
 * 5. 4 tactile recessed mechanical deck buttons: REW, FWD, PLAY (with backlight glow), REC (red jewel LED).
 *
 * Designed with zero allocations in onDraw() for maximum 60fps smoothness on low-RAM DAPs.
 */
class VerticalDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Theme & State
    var theme: CassetteTheme = CassetteTheme.VERTICAL_STUDIO_DECK
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var isPlaying: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var progress: Float = 0.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    // Callbacks
    var onPlayClicked: (() -> Unit)? = null
    var onPrevClicked: (() -> Unit)? = null
    var onNextClicked: (() -> Unit)? = null
    var onRecClicked: (() -> Unit)? = null // Side A/B flip or Catalog eject
    var onSeek: ((Float) -> Unit)? = null

    // Touch interaction tracking
    private var isDraggingSlider = false
    private var pressedButtonIndex = -1 // 0: REW, 1: FWD, 2: PLAY, 3: REC

    // Pre-allocated Paints
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brushedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bayBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bayInnerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logoShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulerTickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulerThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPlayGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val recLedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripePaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripePaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripePaint3 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val diagonalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pre-allocated Geometries
    private val chassisRect = RectF()
    val cassetteBayRect = RectF()
    private val rulerTrackRect = RectF()
    private val rulerThumbRect = RectF()
    private val btnRewRect = RectF()
    private val btnFwdRect = RectF()
    private val btnPlayRect = RectF()
    private val btnRecRect = RectF()
    private val buttonHousingRect = RectF()
    private val vaporwavePath = Path()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updatePaints()
    }

    private fun updatePaints() {
        val isVaporwave = theme.chassisStyle == ChassisStyle.VAPORWAVE_80S

        chassisPaint.apply {
            color = theme.chassisColor
            style = Paint.Style.FILL
        }
        brushedLinePaint.apply {
            color = Color.argb(12, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        bayBevelPaint.apply {
            color = if (isVaporwave) Color.parseColor("#4FB0B8") else Color.parseColor("#E0E0E0") // Neon teal bevel or chrome bevel
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        bayInnerShadowPaint.apply {
            color = Color.parseColor("#0A0A0C")
            style = Paint.Style.FILL
        }
        logoPaint.apply {
            color = if (isVaporwave) Color.parseColor("#1E293B") else Color.parseColor("#F5F5F5")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = if (isVaporwave) 0.08f else 0.15f
            textAlign = Paint.Align.CENTER
        }
        logoShadowPaint.apply {
            color = if (isVaporwave) Color.argb(25, 0, 0, 0) else Color.argb(100, 0, 0, 0)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = if (isVaporwave) 0.08f else 0.15f
            textAlign = Paint.Align.CENTER
        }
        arrowPaint.apply {
            color = if (isVaporwave) Color.parseColor("#1E293B") else Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeJoin = Paint.Join.ROUND
        }
        rulerTextPaint.apply {
            color = if (isVaporwave) Color.parseColor("#475569") else Color.parseColor("#9E9E9E")
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        rulerTickPaint.apply {
            color = if (isVaporwave) Color.parseColor("#64748B") else Color.parseColor("#757575")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        rulerTrackPaint.apply {
            color = if (isVaporwave) Color.parseColor("#CBD5E1") else Color.parseColor("#121214")
            style = Paint.Style.FILL
        }
        rulerThumbPaint.apply {
            color = if (isVaporwave) Color.parseColor("#EC4899") else Color.parseColor("#D50000") // Hot pink cursor for vaporwave
            style = Paint.Style.FILL
        }
        buttonBasePaint.apply {
            color = if (isVaporwave) Color.parseColor("#0F172A") else Color.parseColor("#212124")
            style = Paint.Style.FILL
        }
        buttonHighlightPaint.apply {
            color = if (isVaporwave) Color.argb(60, 56, 189, 248) else Color.argb(40, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        buttonShadowPaint.apply {
            color = Color.argb(120, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        buttonTextPaint.apply {
            color = if (isVaporwave) Color.parseColor("#38BDF8") else Color.parseColor("#E53935") // Sky blue arrows in vaporwave
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        buttonSubTextPaint.apply {
            color = if (isVaporwave) Color.parseColor("#94A3B8") else Color.parseColor("#9E9E9E")
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        buttonPlayGlowPaint.apply {
            color = if (isVaporwave) Color.parseColor("#F472B6") else Color.parseColor("#00E676") // Neon pink play glow in vaporwave
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        recLedPaint.apply {
            color = if (isVaporwave) Color.parseColor("#EC4899") else Color.parseColor("#D50000")
            style = Paint.Style.FILL
        }
        // Vaporwave neon racing stripes
        stripePaint1.apply {
            color = Color.parseColor("#70C1E8") // Sky Blue
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        stripePaint2.apply {
            color = Color.parseColor("#E084B4") // Pastel Pink
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        stripePaint3.apply {
            color = Color.parseColor("#4FB0B8") // Teal
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        diagonalTextPaint.apply {
            color = Color.parseColor("#70C1E8")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.12f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        chassisRect.set(0f, 0f, w.toFloat(), h.toFloat())

        // Top Header: 0 to 12% height
        logoPaint.textSize = w * 0.085f
        logoShadowPaint.textSize = w * 0.085f

        // Cassette Bay Window: 10.5% to 68.5% height
        val bayMarginH = w * 0.13f
        cassetteBayRect.set(
            bayMarginH,
            h * 0.105f,
            w - bayMarginH,
            h * 0.685f
        )

        // Linear Ruler Track: 73% to 76% height
        val rulerMarginH = w * 0.10f
        rulerTrackRect.set(
            rulerMarginH,
            h * 0.744f,
            w - rulerMarginH,
            h * 0.756f
        )
        rulerTextPaint.textSize = w * 0.034f

        // Bottom Button Deck: 80% to 92% height
        val deckTop = h * 0.80f
        val deckBottom = h * 0.92f
        val deckMarginH = w * 0.08f
        buttonHousingRect.set(deckMarginH, deckTop - 4f, w - deckMarginH, deckBottom + 4f)

        val totalBtnWidth = w - (deckMarginH * 2f)
        val btnGap = 8f
        val btnWidth = (totalBtnWidth - (btnGap * 3f)) / 4f

        btnRewRect.set(deckMarginH, deckTop, deckMarginH + btnWidth, deckBottom)
        btnFwdRect.set(btnRewRect.right + btnGap, deckTop, btnRewRect.right + btnGap + btnWidth, deckBottom)
        btnPlayRect.set(btnFwdRect.right + btnGap, deckTop, btnFwdRect.right + btnGap + btnWidth, deckBottom)
        btnRecRect.set(btnPlayRect.right + btnGap, deckTop, btnPlayRect.right + btnGap + btnWidth, deckBottom)

        buttonTextPaint.textSize = btnWidth * 0.28f
        buttonPlayGlowPaint.textSize = btnWidth * 0.28f
        buttonSubTextPaint.textSize = btnWidth * 0.18f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // 1. Draw Main Chassis Faceplate
        canvas.drawRect(chassisRect, chassisPaint)

        if (theme.chassisStyle == ChassisStyle.VAPORWAVE_80S) {
            drawVaporwaveAccents(canvas, w, h)
        } else {
            drawBrushedMetalGrain(canvas, w, h)
        }

        // 2. Top Header Typography (WALKMAN)
        val logoText = if (theme.chassisStyle == ChassisStyle.VAPORWAVE_80S) "WALK•MAN" else "WALKMAN"
        val logoY = if (theme.chassisStyle == ChassisStyle.VAPORWAVE_80S) h * 0.058f else h * 0.070f
        canvas.drawText(logoText, w * 0.5f, logoY + 2f, logoShadowPaint)
        canvas.drawText(logoText, w * 0.5f, logoY, logoPaint)

        // 3. Recessed Cassette Bay Border & Chrome Highlight Bevel
        canvas.drawRoundRect(cassetteBayRect, 18f, 18f, bayInnerShadowPaint)
        canvas.drawRoundRect(cassetteBayRect, 18f, 18f, bayBevelPaint)

        // 4. Analog Linear Tape Progress Ruler
        drawLinearTapeRuler(canvas, w, h)

        // 5. 4 Mechanical Tactile Deck Buttons
        drawDeckButtons(canvas)
    }

    /**
     * Draws subtle vertical brushed aluminum streaks for realistic metallic depth.
     */
    private fun drawBrushedMetalGrain(canvas: Canvas, w: Float, h: Float) {
        var x = 8f
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, brushedLinePaint)
            x += 12f
        }
    }

    /**
     * Draws 80s Vaporwave / Cyberpunk triple neon racing stripes framing the cassette bay.
     */
    private fun drawVaporwaveAccents(canvas: Canvas, w: Float, h: Float) {
        val bayLeft = cassetteBayRect.left
        val bayTop = cassetteBayRect.top
        val bayBottom = cassetteBayRect.bottom
        val bayRight = cassetteBayRect.right

        // 3 continuous neon stripes running down the left flank and across the top
        // Stripe 1 (Outer - Sky Blue)
        vaporwavePath.reset()
        vaporwavePath.moveTo(bayRight, bayTop - 26f)
        vaporwavePath.lineTo(bayLeft - 30f, bayTop - 26f)
        vaporwavePath.lineTo(bayLeft - 30f, bayBottom)
        canvas.drawPath(vaporwavePath, stripePaint1)

        // Stripe 2 (Middle - Pastel Pink)
        vaporwavePath.reset()
        vaporwavePath.moveTo(bayRight, bayTop - 16f)
        vaporwavePath.lineTo(bayLeft - 20f, bayTop - 16f)
        vaporwavePath.lineTo(bayLeft - 20f, bayBottom)
        canvas.drawPath(vaporwavePath, stripePaint2)

        // Stripe 3 (Inner - Teal)
        vaporwavePath.reset()
        vaporwavePath.moveTo(bayRight, bayTop - 6f)
        vaporwavePath.lineTo(bayLeft - 10f, bayTop - 6f)
        vaporwavePath.lineTo(bayLeft - 10f, bayBottom)
        canvas.drawPath(vaporwavePath, stripePaint3)

        // Parallel angled/vertical text alongside stripes on the white chassis
        // Placed along a single vertical line at bayLeft - 44f
        val textX = bayLeft - 44f

        // 1. Upper portion: STEREO CASSETTE PLAYER (Sky Blue)
        canvas.save()
        val text1Y = bayTop + (bayBottom - bayTop) * 0.35f
        canvas.rotate(-90f, textX, text1Y)
        diagonalTextPaint.textSize = w * 0.024f
        diagonalTextPaint.color = Color.parseColor("#70C1E8")
        canvas.drawText("STEREO CASSETTE PLAYER", textX, text1Y, diagonalTextPaint)
        canvas.restore()

        // 2. Lower portion: STEREO (Bold Teal)
        canvas.save()
        val text2Y = bayTop + (bayBottom - bayTop) * 0.72f
        canvas.rotate(-90f, textX, text2Y)
        diagonalTextPaint.textSize = w * 0.034f
        diagonalTextPaint.color = Color.parseColor("#4FB0B8")
        canvas.drawText("STEREO", textX, text2Y, diagonalTextPaint)
        canvas.restore()

        // Hollow retro arrow pointing left under WALK•MAN
        val arrowY = h * 0.076f
        val arrowCenterX = w * 0.5f
        val arrowW = 46f
        val arrowH = 12f
        vaporwavePath.reset()
        vaporwavePath.moveTo(arrowCenterX - arrowW * 0.5f, arrowY)
        vaporwavePath.lineTo(arrowCenterX - arrowW * 0.15f, arrowY - arrowH * 0.5f)
        vaporwavePath.lineTo(arrowCenterX - arrowW * 0.15f, arrowY - arrowH * 0.2f)
        vaporwavePath.lineTo(arrowCenterX + arrowW * 0.5f, arrowY - arrowH * 0.2f)
        vaporwavePath.lineTo(arrowCenterX + arrowW * 0.5f, arrowY + arrowH * 0.2f)
        vaporwavePath.lineTo(arrowCenterX - arrowW * 0.15f, arrowY + arrowH * 0.2f)
        vaporwavePath.lineTo(arrowCenterX - arrowW * 0.15f, arrowY + arrowH * 0.5f)
        vaporwavePath.close()
        canvas.drawPath(vaporwavePath, arrowPaint)
    }

    /**
     * Draws the calibrated linear tape progress ruler:
     * ||| 0 ||| 1 ||| 2 ||| 3 ||| 4 ||| 5 ||| 6 ||| 7 ||| 8 ||| 9 |||
     * with the sliding red mechanical indicator.
     */
    private fun drawLinearTapeRuler(canvas: Canvas, w: Float, h: Float) {
        val rulerY = h * 0.728f
        val startX = rulerTrackRect.left + 12f
        val endX = rulerTrackRect.right - 12f
        val totalTrackLen = endX - startX

        // 1. Draw Recessed Groove
        canvas.drawRoundRect(rulerTrackRect, 6f, 6f, rulerTrackPaint)

        // 2. Draw 10 Major Numbered Divisions (0 to 9) and intermediate ticks
        for (i in 0..9) {
            val posX = startX + (totalTrackLen * (i / 9f))
            canvas.drawText("$i", posX, rulerY, rulerTextPaint)

            // Tick above groove
            canvas.drawLine(posX, rulerY + 4f, posX, rulerTrackRect.top - 2f, rulerTickPaint)

            // Intermediate minor ticks
            if (i < 9) {
                val nextX = startX + (totalTrackLen * ((i + 1) / 9f))
                val midX = (posX + nextX) * 0.5f
                canvas.drawLine(midX, rulerY + 6f, midX, rulerTrackRect.top - 2f, rulerTickPaint)
            }
        }

        // 3. Draw Sliding Red Mechanical Pointer Block
        val thumbX = startX + (totalTrackLen * progress)
        val thumbWidth = 16f
        val thumbHeight = rulerTrackRect.height() + 14f
        rulerThumbRect.set(
            thumbX - (thumbWidth * 0.5f),
            rulerTrackRect.centerY() - (thumbHeight * 0.5f),
            thumbX + (thumbWidth * 0.5f),
            rulerTrackRect.centerY() + (thumbHeight * 0.5f)
        )
        canvas.drawRoundRect(rulerThumbRect, 4f, 4f, rulerThumbPaint)
    }

    /**
     * Draws the 4 tactile mechanical deck buttons: REW, FWD, PLAY, REC.
     */
    private fun drawDeckButtons(canvas: Canvas) {
        // Button 1: REW
        drawSingleButton(canvas, btnRewRect, "◄◄", "REW", pressedButtonIndex == 0, isGlow = false)

        // Button 2: FWD
        drawSingleButton(canvas, btnFwdRect, "►►", "FWD", pressedButtonIndex == 1, isGlow = false)

        // Button 3: PLAY / PAUSE (glowing when playing)
        val playSymbol = if (isPlaying) "❚❚" else "►"
        val playSub = if (isPlaying) "PAUSE" else "PLAY"
        drawSingleButton(canvas, btnPlayRect, playSymbol, playSub, pressedButtonIndex == 2, isGlow = isPlaying)

        // Button 4: REC (Luminous Red Jewel LED)
        drawRecButton(canvas, btnRecRect, pressedButtonIndex == 3)
    }

    private fun drawSingleButton(
        canvas: Canvas,
        rect: RectF,
        symbol: String,
        subtext: String,
        isPressed: Boolean,
        isGlow: Boolean
    ) {
        val radius = 10f
        canvas.drawRoundRect(rect, radius, radius, buttonBasePaint)

        if (!isPressed) {
            canvas.drawRoundRect(rect, radius, radius, buttonHighlightPaint)
            canvas.drawRoundRect(rect, radius, radius, buttonShadowPaint)
        }

        val textY = rect.centerY() - 2f
        val paint = if (isGlow) buttonPlayGlowPaint else buttonTextPaint
        canvas.drawText(symbol, rect.centerX(), textY, paint)

        val subY = rect.bottom - (rect.height() * 0.16f)
        canvas.drawText(subtext, rect.centerX(), subY, buttonSubTextPaint)
    }

    private fun drawRecButton(canvas: Canvas, rect: RectF, isPressed: Boolean) {
        val radius = 10f
        canvas.drawRoundRect(rect, radius, radius, buttonBasePaint)

        if (!isPressed) {
            canvas.drawRoundRect(rect, radius, radius, buttonHighlightPaint)
            canvas.drawRoundRect(rect, radius, radius, buttonShadowPaint)
        }

        val ledRadius = rect.width() * 0.20f
        val ledCenterY = rect.centerY() - 4f
        canvas.drawCircle(rect.centerX(), ledCenterY, ledRadius, recLedPaint)

        val subY = rect.bottom - (rect.height() * 0.16f)
        canvas.drawText("REC", rect.centerX(), subY, buttonSubTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (y >= rulerTrackRect.top - 30f && y <= rulerTrackRect.bottom + 30f &&
                    x >= rulerTrackRect.left - 10f && x <= rulerTrackRect.right + 10f) {
                    isDraggingSlider = true
                    updateSeekProgress(x)
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                }

                when {
                    btnRewRect.contains(x, y) -> {
                        pressedButtonIndex = 0
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        invalidate()
                        return true
                    }
                    btnFwdRect.contains(x, y) -> {
                        pressedButtonIndex = 1
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        invalidate()
                        return true
                    }
                    btnPlayRect.contains(x, y) -> {
                        pressedButtonIndex = 2
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        invalidate()
                        return true
                    }
                    btnRecRect.contains(x, y) -> {
                        pressedButtonIndex = 3
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        invalidate()
                        return true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDraggingSlider) {
                    updateSeekProgress(x)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDraggingSlider) {
                    isDraggingSlider = false
                    onSeek?.invoke(progress)
                    return true
                }

                when (pressedButtonIndex) {
                    0 -> if (btnRewRect.contains(x, y)) onPrevClicked?.invoke()
                    1 -> if (btnFwdRect.contains(x, y)) onNextClicked?.invoke()
                    2 -> if (btnPlayRect.contains(x, y)) onPlayClicked?.invoke()
                    3 -> if (btnRecRect.contains(x, y)) onRecClicked?.invoke()
                }
                pressedButtonIndex = -1
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDraggingSlider = false
                pressedButtonIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSeekProgress(touchX: Float) {
        val startX = rulerTrackRect.left + 12f
        val endX = rulerTrackRect.right - 12f
        val totalTrackLen = endX - startX
        if (totalTrackLen > 0f) {
            val normalized = ((touchX - startX) / totalTrackLen).coerceIn(0f, 1f)
            progress = normalized
            invalidate()
        }
    }
}
