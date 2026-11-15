package com.hana.spindle.ui.cassette

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Brushed-metal faceplate body as a plain background, for screens built from regular views
 * (the tape and radio decks paint their own). Rendered once per size into a bitmap.
 */
class FaceplateChassisDrawable(
    val colorway: FaceplateColorway,
    private val density: Float,
    private val grainSeed: Long = 23L
) : Drawable() {

    private val fill = Paint()
    private val line = Paint()
    private var cache: Bitmap? = null

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        cache?.recycle()
        cache = null
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val bmp = cache ?: render(b.width(), b.height()).also { cache = it }
        canvas.drawBitmap(bmp, b.left.toFloat(), b.top.toFloat(), null)
    }

    private fun render(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fill.shader =
            LinearGradient(0f, 0f, 0f, h.toFloat(), colorway.bodyTop, colorway.bodyBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        fill.shader = null

        // Same deterministic grain as the deck painters so the pages never shimmer
        var seed = grainSeed
        line.strokeWidth = 0.6f * density
        var y = 0f
        while (y < h) {
            seed = (seed * 9301 + 49297) % 233280
            val light = seed % 2L == 0L
            seed = (seed * 9301 + 49297) % 233280
            val a =
                ((if (light) colorway.brushLightAlpha else colorway.brushDarkAlpha) * (seed / 233280f) * 255).toInt()
            line.color = if (light) Color.argb(a, 255, 255, 255) else Color.argb(a, 0, 0, 0)
            c.drawLine(0f, y, w.toFloat(), y, line)
            y += 1.5f * density
        }
        return bmp
    }

    /** Frees the cached bitmap; it is rebuilt on the next draw. */
    fun release() {
        cache?.recycle()
        cache = null
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
