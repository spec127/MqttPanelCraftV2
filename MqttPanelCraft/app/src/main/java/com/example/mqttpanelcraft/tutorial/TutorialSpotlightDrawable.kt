package com.example.mqttpanelcraft.tutorial

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** A drawing-only overlay: all touches still reach the real controls below it. */
class TutorialSpotlightDrawable(private val density: Float) : Drawable() {
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xA6000000.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(167, 139, 250)
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
    }
    private val dimPath = Path()
    private val hole = Path()
    private val targetBounds = Rect()
    private val cardBounds = Rect()

    fun update(viewport: Rect, target: Rect, card: Rect) {
        if (bounds == viewport && targetBounds == target && cardBounds == card) return
        bounds = viewport
        targetBounds.set(target)
        cardBounds.set(card)
        dimPath.reset()
        dimPath.addRect(RectF(viewport), Path.Direction.CW)
        // Subtract individually so overlapping card/target holes remain transparent.
        for ((rect, radius) in listOf(targetBounds to 12f, cardBounds to 16f)) {
            if (rect.isEmpty) continue
            hole.reset()
            hole.addRoundRect(RectF(rect), radius * density, radius * density, Path.Direction.CW)
            dimPath.op(hole, Path.Op.DIFFERENCE)
        }
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(dimPath, shade)
        if (!targetBounds.isEmpty) {
            canvas.drawRoundRect(RectF(targetBounds), 12 * density, 12 * density, ring)
        }
    }

    override fun setAlpha(alpha: Int) { shade.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { shade.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
