package com.example.mqttpanelcraft.tutorial

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.max

/** A circle around the current control. The rest of the screen stays clear and tappable. */
class TutorialSpotlightDrawable(private val density: Float) : Drawable() {
    private val under = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f * density
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 87, 34)
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
    }
    private val targetBounds = Rect()

    fun update(viewport: Rect, target: Rect, ignoredCard: Rect) {
        if (bounds == viewport && targetBounds == target) return
        bounds = viewport
        targetBounds.set(target)
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (targetBounds.isEmpty) return
        val radius = max(targetBounds.width(), targetBounds.height()) / 2f + 8f * density
        val cx = targetBounds.exactCenterX()
        val cy = targetBounds.exactCenterY()
        canvas.drawCircle(cx, cy, radius, under)
        canvas.drawCircle(cx, cy, radius, ring)
    }

    override fun setAlpha(alpha: Int) {
        ring.alpha = alpha
        under.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        ring.colorFilter = colorFilter
        under.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
