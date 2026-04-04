package com.example.mqttpanelcraft.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/** A premium LED view with smooth breathing/blinking effects and multiple styles. */
class LedView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {

    enum class Style {
        ORB,
        ICON_GLOW,
        CONCENTRIC,
        RADIUS_XL,
        NEON_TEXT
    }
    enum class Effect {
        NONE,
        BLINK,
        BREATHE
    }

    var style: Style = Style.ORB
        set(value) {
            field = value
            invalidate()
        }

    var effect: Effect = Effect.BREATHE
        set(value) {
            field = value
            updateAnimation()
        }

    var activeColor: Int = Color.GREEN
        set(value) {
            field = value
            invalidate()
        }

    var idleColor: Int = Color.GRAY
        set(value) {
            field = value
            invalidate()
        }

    var isActive: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var effectSpeed: Long = 1000L
        set(value) {
            field = value
            updateAnimation()
        }

    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    /** The icon to display in the center (supported by ICON_GLOW, RADIUS_XL) */
    var iconResId: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private var animValue: Float = 1.0f
    private var animator: ValueAnimator? = null

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }

    private fun updateAnimation() {
        animator?.cancel()
        if (effect == Effect.NONE) {
            animValue = 1.0f
            invalidate()
            return
        }

        animator =
                ValueAnimator.ofFloat(0.1f, 1.0f).apply {
                    duration = effectSpeed
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        animValue = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        
        val cx = w / 2f
        val cy = h / 2f
        val radius = Math.max(0.1f, (Math.min(w, h) / 2f) * 0.8f)

        val baseColor = if (isActive) activeColor else idleColor
        val alpha = if (isActive) (255 * animValue).toInt() else 255
        val colorWithAlpha =
                Color.argb(
                        alpha,
                        Color.red(baseColor),
                        Color.green(baseColor),
                        Color.blue(baseColor)
                )

        when (style) {
            Style.ORB -> drawOrb(canvas, cx, cy, radius, colorWithAlpha)
            Style.ICON_GLOW -> drawIconGlow(canvas, cx, cy, radius * 1.2f, colorWithAlpha)
            Style.CONCENTRIC -> drawConcentric(canvas, cx, cy, radius, colorWithAlpha)
            Style.RADIUS_XL -> drawRadiusXL(canvas, cx, cy, radius, colorWithAlpha)
            Style.NEON_TEXT -> drawNeonText(canvas, cx, cy, radius, colorWithAlpha)
        }

        // Default label drawing (only if label is explicitly needed for simple styles in future)
        // Currently all styles handle their text rendering internally
    }

    private fun drawOrb(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        // High quality atmospheric glow
        glowPaint.shader =
                RadialGradient(
                        cx,
                        cy,
                        radius * 1.8f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(color, 180),
                                ColorUtils.setAlphaComponent(color, 40),
                                Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                )
        canvas.drawCircle(cx, cy, radius * 1.8f, glowPaint)

        // Solid core
        mainPaint.shader = null
        mainPaint.color = color
        canvas.drawCircle(cx, cy, radius * 0.85f, mainPaint)

        // Inner core glow (high brightness)
        mainPaint.color = Color.WHITE
        mainPaint.alpha = 80
        canvas.drawCircle(cx, cy, radius * 0.6f, mainPaint)
    }

    private fun drawLens(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        // Rim
        mainPaint.shader = null
        mainPaint.color = Color.DKGRAY
        canvas.drawCircle(cx, cy, radius * 1.05f, mainPaint)

        // Lens Surface
        val gradient =
                LinearGradient(
                        0f,
                        0f,
                        0f,
                        height.toFloat(),
                        intArrayOf(
                                ColorUtils.lighten(color, 0.3f),
                                color,
                                ColorUtils.darken(color, 0.5f)
                        ),
                        null,
                        Shader.TileMode.CLAMP
                )
        mainPaint.shader = gradient
        canvas.drawCircle(cx, cy, radius, mainPaint)

        // Inner Pattern (Subtle)
        mainPaint.shader = null
        mainPaint.color = Color.argb(30, 255, 255, 255)
        mainPaint.style = Paint.Style.STROKE
        mainPaint.strokeWidth = 2f
        for (i in 1..4) {
            canvas.drawCircle(cx, cy, radius * (i * 0.2f), mainPaint)
        }
        mainPaint.style = Paint.Style.FILL
    }

    private fun drawPixel(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Pixel Glow
        if (isActive) {
            glowPaint.color = ColorUtils.setAlphaComponent(color, 60)
            canvas.drawRoundRect(
                    cx - radius * 1.2f,
                    cy - radius * 1.2f,
                    cx + radius * 1.2f,
                    cy + radius * 1.2f,
                    radius * 0.2f,
                    radius * 0.2f,
                    glowPaint
            )
        }

        mainPaint.shader = null
        mainPaint.color = color
        canvas.drawRoundRect(rect, radius * 0.15f, radius * 0.15f, mainPaint)

        // Grid overlay
        mainPaint.color = Color.argb(40, 0, 0, 0)
        val step = radius / 3f
        for (i in -2..2) {
            canvas.drawLine(cx + i * step, cy - radius, cx + i * step, cy + radius, mainPaint)
            canvas.drawLine(cx - radius, cy + i * step, cx + radius, cy + i * step, mainPaint)
        }
    }

    private fun drawNeon(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        // Neon Glow (Borderless)
        glowPaint.shader =
                RadialGradient(
                        cx,
                        cy,
                        radius * 1.5f,
                        intArrayOf(ColorUtils.setAlphaComponent(color, 200), Color.TRANSPARENT),
                        null,
                        Shader.TileMode.CLAMP
                )
        canvas.drawCircle(cx, cy, radius * 1.5f, glowPaint)

        // Core light
        mainPaint.shader = null
        mainPaint.color = Color.WHITE
        mainPaint.alpha = if (isActive) 255 else 100
        canvas.drawCircle(cx, cy, radius * 0.4f, mainPaint)

        // Colored ring
        mainPaint.color = color
        mainPaint.style = Paint.Style.STROKE
        mainPaint.strokeWidth = 8f
        canvas.drawCircle(cx, cy, radius * 0.8f, mainPaint)
        mainPaint.style = Paint.Style.FILL
    }

    private fun drawIconGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        // Large atmospheric glow
        glowPaint.shader =
                RadialGradient(
                        cx,
                        cy,
                        radius * 1.5f,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(color, 180),
                                ColorUtils.setAlphaComponent(color, 40),
                                Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.4f, 1f),
                        Shader.TileMode.CLAMP
                )
        canvas.drawCircle(cx, cy, radius * 1.5f, glowPaint)

        // Central white icon
        if (iconResId != 0) {
            val iconSize = radius * 0.7f
            drawIcon(canvas, iconResId, cx, cy, iconSize, Color.WHITE)
        }
    }

    private fun drawConcentric(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        // Outer faint ring
        mainPaint.style = Paint.Style.STROKE
        mainPaint.strokeWidth = 2f
        mainPaint.color = Color.argb(40, 200, 200, 200)
        canvas.drawCircle(cx, cy, radius * 1.1f, mainPaint)

        // Inner filled circle (faint background)
        mainPaint.style = Paint.Style.FILL
        mainPaint.color = ColorUtils.setAlphaComponent(color, 20)
        canvas.drawCircle(cx, cy, radius * 0.8f, mainPaint)

        // Central Text
        if (label.isNotEmpty()) {
            textPaint.color = color
            textPaint.textSize = radius * 0.4f
            val fontMetrics = textPaint.fontMetrics
            val textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(label, cx, textY, textPaint)
        }
    }

    private fun drawRadiusXL(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val rectWidth = radius * 1.6f
        val rectHeight = radius * 1.6f
        val rect =
                RectF(
                        cx - rectWidth / 2f,
                        cy - rectHeight / 2f,
                        cx + rectWidth / 2f,
                        cy + rectHeight / 2f
                )
        val corner = radius * 0.4f

        // Border
        mainPaint.style = Paint.Style.STROKE
        mainPaint.strokeWidth = 4f
        mainPaint.color = Color.argb(30, 200, 200, 200)
        canvas.drawRoundRect(rect, corner, corner, mainPaint)

        // Inner Fill
        mainPaint.style = Paint.Style.FILL
        mainPaint.color = ColorUtils.setAlphaComponent(color, 15)
        canvas.drawRoundRect(rect, corner, corner, mainPaint)

        // Icon (Top half)
        if (iconResId != 0) {
            val iconSize = radius * 0.5f
            drawIcon(canvas, iconResId, cx, cy - radius * 0.25f, iconSize, color)
        }

        // Text (Bottom half)
        if (label.isNotEmpty()) {
            textPaint.color = color
            textPaint.textSize = radius * 0.25f
            textPaint.alpha = 255
            canvas.drawText(label, cx, cy + radius * 0.45f, textPaint)
        }
    }

    private fun drawNeonText(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        // Glowing Text
        if (label.isNotEmpty()) {
            textPaint.textSize = radius * 0.6f
            textPaint.color = color
            textPaint.setShadowLayer(10f, 0f, 0f, color)
            val fontMetrics = textPaint.fontMetrics
            val textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(label, cx, textY, textPaint)
            textPaint.clearShadowLayer()
        }
    }

    private fun drawLabel(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        textPaint.color = if (isActive) Color.WHITE else Color.LTGRAY
        textPaint.textSize = radius * 0.5f
        val fontMetrics = textPaint.fontMetrics
        val textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(label, cx, textY, textPaint)
    }

    private fun drawIcon(
            canvas: Canvas,
            resId: Int,
            cx: Float,
            cy: Float,
            size: Float,
            color: Int
    ) {
        try {
            val drawable =
                    androidx.core.content.res.ResourcesCompat.getDrawable(resources, resId, null)
                            ?: return
            androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, color)
            val left = (cx - size / 2f).toInt()
            val top = (cy - size / 2f).toInt()
            val right = (cx + size / 2f).toInt()
            val bottom = (cy + size / 2f).toInt()
            drawable.setBounds(left, top, right, bottom)
            drawable.draw(canvas)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    object ColorUtils {
        fun setAlphaComponent(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or (alpha shl 24)
        }
        fun darken(color: Int, factor: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[2] *= (1f - factor)
            return Color.HSVToColor(hsv)
        }
        fun lighten(color: Int, factor: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[2] += (1f - hsv[2]) * factor
            return Color.HSVToColor(hsv)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
