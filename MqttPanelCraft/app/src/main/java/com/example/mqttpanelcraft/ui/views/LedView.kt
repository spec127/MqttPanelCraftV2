package com.example.mqttpanelcraft.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import kotlin.math.*
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

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // Enhanced color logic
        var baseColor = if (isActive) activeColor else idleColor
        
        // Boost saturation when active as requested
        if (isActive) {
            val hsv = FloatArray(3)
            Color.colorToHSV(baseColor, hsv)
            hsv[1] = (hsv[1] * 1.2f).coerceAtMost(1.0f) // Boost saturation by 20%
            hsv[2] = (hsv[2] * 1.1f).coerceAtMost(1.0f) // Slightly boost brightness too
            baseColor = Color.HSVToColor(hsv)
        }

        // Reduced opacity when inactive (off) for transparency effect
        val alpha = if (isActive) (255 * animValue).toInt() else 100 
        val colorWithAlpha =
                Color.argb(
                        alpha,
                        Color.red(baseColor),
                        Color.green(baseColor),
                        Color.blue(baseColor)
                )

        if (isActive && style != Style.NEON_TEXT) {
            // Skip center glow for Neon mode to avoid "blob" effect
            drawAtmosphericGlow(canvas, cx, cy, radius, baseColor)
        }

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

    private fun drawAtmosphericGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val glowRadius = radius * 2.5f // V21.12: Increased from 1.75f for "WOW" effect
        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(
                ColorUtils.setAlphaComponent(color, 150), // Slightly softer
                ColorUtils.setAlphaComponent(color, 35),  
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f), 
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)
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
            val iconSize = radius * 0.85f // Slightly larger icon
            // V21.12: Always use White for glowing icons to emphasize light emission
            drawIcon(canvas, iconResId, cx, cy, iconSize, Color.WHITE)
        }
    }

    private fun drawConcentric(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        // Enhanced Glow Fill for realistic "Light On" feeling
        mainPaint.style = Paint.Style.STROKE
        mainPaint.strokeWidth = 6f
        mainPaint.color = if (isActive) ColorUtils.setAlphaComponent(color, 200) else Color.argb(60, 180, 180, 180)
        canvas.drawCircle(cx, cy, radius * 1.3f, mainPaint)

        mainPaint.style = Paint.Style.FILL
        if (isActive) {
            // Stronger gradient to simulate light emission
            val glowFill = RadialGradient(
                cx, cy, radius * 1.3f,
                intArrayOf(LedView.ColorUtils.lighten(color, 0.6f), color, LedView.ColorUtils.darken(color, 0.3f)),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            mainPaint.shader = glowFill
        } else {
            mainPaint.color = LedView.ColorUtils.setAlphaComponent(color, 80)
            mainPaint.shader = null
        }
        canvas.drawCircle(cx, cy, radius * 1.1f, mainPaint)
        mainPaint.shader = null

        // Content drawing logic (icons/text)
        val hasIcon = iconResId != 0
        val hasLabel = label.isNotEmpty()
        val contentColor = if (isDarkColor(color)) Color.WHITE else Color.BLACK
        
        if (hasIcon && hasLabel) {
            val iconSize = radius * 0.9f
            drawIcon(canvas, iconResId, cx, cy - radius * 0.35f, iconSize, contentColor)
            drawMultilineText(canvas, label, cx, cy + radius * 0.65f, radius * 0.45f, contentColor)
        } else if (hasIcon) {
            val iconSize = radius * 1.3f
            drawIcon(canvas, iconResId, cx, cy, iconSize, contentColor)
        } else if (hasLabel) {
            drawMultilineText(canvas, label, cx, cy, radius * 0.8f, contentColor, true)
        }
    }

    private fun drawRadiusXL(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        // Enhanced Glow Fill for Rectangle + Outer Ring (Consistent with Concentric)
        val rectWidth = radius * 2.5f
        val rectHeight = radius * 2.5f
        val rect = RectF(cx - rectWidth / 2f, cy - rectHeight / 2f,
                         cx + rectWidth / 2f, cy + rectHeight / 2f)
        val corner = radius * 0.3f

        // New Outer Ring for RadiusXL
        val outerRect = RectF(cx - (rectWidth * 1.15f) / 2f, cy - (rectHeight * 1.15f) / 2f,
                              cx + (rectWidth * 1.15f) / 2f, cy + (rectHeight * 1.15f) / 2f)
        mainPaint.style = Paint.Style.STROKE
        mainPaint.strokeWidth = 4f
        mainPaint.color = if (isActive) ColorUtils.setAlphaComponent(color, 150) else Color.argb(60, 180, 180, 180)
        canvas.drawRoundRect(outerRect, corner * 1.3f, corner * 1.3f, mainPaint)

        mainPaint.style = Paint.Style.STROKE
        mainPaint.strokeWidth = 6f
        mainPaint.color = if (isActive) ColorUtils.setAlphaComponent(color, 200) else Color.argb(60, 180, 180, 180)
        canvas.drawRoundRect(rect, corner, corner, mainPaint)

        mainPaint.style = Paint.Style.FILL
        if (isActive) {
            val glowFill = RadialGradient(
                cx, cy, radius * 1.5f,
                intArrayOf(LedView.ColorUtils.lighten(color, 0.4f), color, LedView.ColorUtils.darken(color, 0.2f)),
                floatArrayOf(0f, 0.8f, 1f),
                Shader.TileMode.CLAMP
            )
            mainPaint.shader = glowFill
        } else {
            mainPaint.color = LedView.ColorUtils.setAlphaComponent(color, 70)
            mainPaint.shader = null
        }
        canvas.drawRoundRect(rect, corner, corner, mainPaint)
        mainPaint.shader = null

        // Content drawing logic (icons/text)
        val hasIcon = iconResId != 0
        val hasLabel = label.isNotEmpty()
        val contentColor = if (isDarkColor(color)) Color.WHITE else Color.BLACK

        if (hasIcon && hasLabel) {
            val iconSize = radius * 0.9f
            drawIcon(canvas, iconResId, cx, cy - radius * 0.4f, iconSize, contentColor)
            drawMultilineText(canvas, label, cx, cy + radius * 0.7f, radius * 0.45f, contentColor)
        } else if (hasIcon) {
            val iconSize = radius * 1.3f
            drawIcon(canvas, iconResId, cx, cy, iconSize, contentColor)
        } else if (hasLabel) {
            drawMultilineText(canvas, label, cx, cy, radius * 0.8f, contentColor, true)
        }
    }

    private fun drawNeonText(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        // Double-layer Neon Glow for better "Tube" effect
        val contentColor = if (isActive) color else Color.DKGRAY
        
        if (iconResId != 0) {
            if (isActive) {
                // Outer faint glow
                textPaint.setShadowLayer(25f, 0f, 0f, ColorUtils.setAlphaComponent(color, 120))
                drawIcon(canvas, iconResId, cx, cy, radius * 1.2f, contentColor)
                // Inner tight glow
                textPaint.setShadowLayer(10f, 0f, 0f, color)
                drawIcon(canvas, iconResId, cx, cy, radius * 1.2f, contentColor)
            } else {
                drawIcon(canvas, iconResId, cx, cy, radius * 1.2f, contentColor)
            }
            textPaint.clearShadowLayer()
        } else if (label.isNotEmpty()) {
            val maxLines = label.split("\n").size
            val avgChars = label.length / maxLines
            val baseSize = (radius * 2.5f) / max(1, avgChars / 2)
            val finalSize = min(baseSize, (radius * 1.8f) / maxLines)
            
            // Draw with enhanced shadow layer
            if (isActive) {
                textPaint.setShadowLayer(20f, 0f, 0f, color)
            }
            drawMultilineText(canvas, label, cx, cy, finalSize, contentColor, true, isActive)
            textPaint.clearShadowLayer()
        }
    }

    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        cx: Float,
        cy: Float,
        size: Float,
        color: Int,
        centerVertical: Boolean = false,
        useShadow: Boolean = false
    ) {
        textPaint.color = color
        textPaint.textSize = size
        textPaint.alpha = Color.alpha(color)
        if (useShadow) textPaint.setShadowLayer(10f, 0f, 0f, color) else textPaint.clearShadowLayer()
        
        val lines = text.split("\n")
        val fm = textPaint.fontMetrics
        val lineHeight = fm.descent - fm.ascent
        val totalHeight = lineHeight * lines.size
        
        var startY = if (centerVertical) cy - (totalHeight / 2f) - fm.ascent else cy
        
        for (line in lines) {
            canvas.drawText(line, cx, startY, textPaint)
            startY += lineHeight
        }
        textPaint.clearShadowLayer()
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

    private fun isDarkColor(color: Int): Boolean {
        // Calculate relative luminance
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.4 // Threshold for white text
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
