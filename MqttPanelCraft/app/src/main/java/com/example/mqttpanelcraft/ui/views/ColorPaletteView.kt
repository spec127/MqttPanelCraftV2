package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class ColorPaletteView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {

    // Properties
    var controllerStyle: String = "Square Pad" // "Square Pad", "Arc Ring"
        set(value) {
            field = value
            invalidate()
        }
    var colorMode: String = "Full Color" // "Full Color", "Monochrome"
        set(value) {
            field = value
            invalidate()
        }
    var themeColor: Int = Color.parseColor("#3B82F6") // Only for Mono Square
        set(value) {
            field = value
            invalidate()
        }
    var controlTarget: String = "Brightness" // "Brightness", "Saturation" (Mono Arc)
        set(value) {
            field = value
            invalidate()
        }
    var showHexCode: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    // MQTT Emission (hue, sat, val, isFinal)
    var onColorChange: ((Float, Float, Float, Boolean) -> Unit)? = null

    // Internal State
    private var currentH: Float = 0f
    private var currentS: Float = 1f
    private var currentV: Float = 1f

    // For Mono mode, base color components
    private val themeHsv = FloatArray(3)

    // Paints
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * resources.displayMetrics.density
            }
    private val markerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * resources.displayMetrics.density
                color = Color.WHITE
                setShadowLayer(4f, 0f, 0f, Color.BLACK)
            }
    private val glassPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 15
            }

    private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                color = Color.WHITE
                textSize = 12f * resources.displayMetrics.density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

    private val subTextPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                color = Color.argb(100, 255, 255, 255)
                textSize = 9f * resources.displayMetrics.density
                letterSpacing = 0.1f
            }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val isDarkMode =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (controllerStyle == "Arc Ring") {
            drawArcRing(canvas, density, isDarkMode)
        } else {
            drawSquarePad(canvas, density, isDarkMode)
        }
    }

    private fun drawArcRing(canvas: Canvas, density: Float, isDarkMode: Boolean) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 10f * density
        val ringWidth = radius * 0.25f
        val innerRadius = radius - ringWidth

        // 1. Draw Ring
        if (colorMode == "Full Color") {
            val sweep =
                    SweepGradient(
                            cx,
                            cy,
                            intArrayOf(
                                    Color.RED,
                                    Color.YELLOW,
                                    Color.GREEN,
                                    Color.CYAN,
                                    Color.BLUE,
                                    Color.MAGENTA,
                                    Color.RED
                            ),
                            null
                    )
            mainPaint.shader = sweep
        } else {
            // Monochrome Gradient logic
            Color.colorToHSV(themeColor, themeHsv)
            val h = themeHsv[0]
            val s = if (controlTarget == "Saturation") 0f else themeHsv[1]
            val v = if (controlTarget == "Brightness") 0f else themeHsv[2]

            val c1 =
                    Color.HSVToColor(
                            floatArrayOf(
                                    h,
                                    if (controlTarget == "Saturation") 0f else s,
                                    if (controlTarget == "Brightness") 0f else v
                            )
                    )
            val c2 =
                    Color.HSVToColor(
                            floatArrayOf(
                                    h,
                                    if (controlTarget == "Saturation") 1f else s,
                                    if (controlTarget == "Brightness") 1f else v
                            )
                    )

            val sweep = SweepGradient(cx, cy, c1, c2)
            mainPaint.shader = sweep
        }

        mainPaint.style = Paint.Style.STROKE
        mainPaint.strokeWidth = ringWidth
        canvas.drawCircle(cx, cy, radius - ringWidth / 2, mainPaint)
        mainPaint.shader = null

        // 2. Draw Glass Center
        glassPaint.alpha = if (isDarkMode) 20 else 10
        canvas.drawCircle(cx, cy, innerRadius - 2 * density, glassPaint)

        // 3. Central Content
        val iconSize = 24f * density
        val p = Path()
        // Simple Palette Icon path (placeholder for real drawable if needed)
        // For now let's draw a text label or icon center
        val centerVal = getCurrentColor()
        textPaint.color = centerVal
        canvas.drawText(
                if (showHexCode) String.format("#%06X", (0xFFFFFF and centerVal)) else "COLOR",
                cx,
                cy + 5f * density,
                textPaint
        )

        canvas.drawText(
                if (colorMode == "Full Color") "HUE" else controlTarget.uppercase(),
                cx,
                cy - 20f * density,
                subTextPaint
        )

        // 4. Marker/Thumb
        val angle =
                if (colorMode == "Full Color") currentH
                else {
                    if (controlTarget == "Brightness") currentV * 360f else currentS * 360f
                }
        val markerX =
                cx + (radius - ringWidth / 2) * cos(Math.toRadians(angle.toDouble())).toFloat()
        val markerY =
                cy + (radius - ringWidth / 2) * sin(Math.toRadians(angle.toDouble())).toFloat()

        markerPaint.style = Paint.Style.FILL
        markerPaint.color = Color.WHITE
        canvas.drawCircle(markerX, markerY, (ringWidth / 2) * 1.1f, markerPaint)
        markerPaint.style = Paint.Style.STROKE
        markerPaint.color = Color.argb(50, 0, 0, 0)
        canvas.drawCircle(markerX, markerY, (ringWidth / 2) * 1.1f, markerPaint)
    }

    private fun drawSquarePad(canvas: Canvas, density: Float, isDarkMode: Boolean) {
        val padMargin = 10f * density
        val w = width - 2 * padMargin
        val h = height - 2 * padMargin
        val rect = RectF(padMargin, padMargin, width - padMargin, height - padMargin)
        val corner = 12f * density

        // 1. Base Gradient
        val baseColor =
                if (colorMode == "Full Color") Color.HSVToColor(floatArrayOf(currentH, 1f, 1f))
                else themeColor

        // Saturation (Horizontal) - Left: White, Right: BaseColor
        val satShader =
                LinearGradient(
                        rect.left,
                        rect.top,
                        rect.right,
                        rect.top,
                        Color.WHITE,
                        baseColor,
                        Shader.TileMode.CLAMP
                )
        // Value (Vertical) - Top: Transparent, Bottom: Black
        val valShader =
                LinearGradient(
                        rect.left,
                        rect.top,
                        rect.left,
                        rect.bottom,
                        Color.TRANSPARENT,
                        Color.BLACK,
                        Shader.TileMode.CLAMP
                )

        val compose = ComposeShader(satShader, valShader, PorterDuff.Mode.DARKEN)
        mainPaint.shader = compose
        mainPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, corner, corner, mainPaint)
        mainPaint.shader = null

        // 2. Marker
        val markerX = rect.left + currentS * w
        val markerY = rect.top + (1f - currentV) * h

        markerPaint.style = Paint.Style.STROKE
        markerPaint.color = Color.WHITE
        markerPaint.strokeWidth = 3f * density
        canvas.drawCircle(markerX, markerY, 8f * density, markerPaint)

        // 3. Optional Hex Code Overlay at bottom
        if (showHexCode) {
            val hex = String.format("#%06X", (0xFFFFFF and getCurrentColor()))
            textPaint.color = if (currentV < 0.5f) Color.WHITE else Color.BLACK
            textPaint.alpha = 150
            canvas.drawText(hex, width / 2f, rect.bottom - 10f * density, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                handleTouch(event.x, event.y)
                emitColor(false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                handleTouch(event.x, event.y)
                emitColor(true)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(tx: Float, ty: Float) {
        val cx = width / 2f
        val cy = height / 2f

        if (controllerStyle == "Arc Ring") {
            val dx = tx - cx
            val dy = ty - cy
            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (angle < 0) angle += 360f

            if (colorMode == "Full Color") {
                currentH = angle
            } else {
                val ratio = angle / 360f
                if (controlTarget == "Brightness") currentV = ratio else currentS = ratio
            }
        } else {
            val density = resources.displayMetrics.density
            val padMargin = 10f * density
            val x = (tx - padMargin).coerceIn(0f, width - 2 * padMargin)
            val y = (ty - padMargin).coerceIn(0f, height - 2 * padMargin)

            currentS = x / (width - 2 * padMargin)
            currentV = 1f - (y / (height - 2 * padMargin))
        }

        // Continuous emission handled by Definition (via attachBehavior)
    }

    private fun emitColor(isFinal: Boolean) {
        onColorChange?.invoke(currentH, currentS, currentV, isFinal)
    }

    private fun getCurrentColor(): Int {
        if (colorMode == "Monochrome") {
            Color.colorToHSV(themeColor, themeHsv)
            // Use currentS and currentV if in Square, or mixed if in Arc
            if (controllerStyle == "Square Pad") {
                return Color.HSVToColor(floatArrayOf(themeHsv[0], currentS, currentV))
            } else {
                return Color.HSVToColor(
                        floatArrayOf(
                                themeHsv[0],
                                if (controlTarget == "Saturation") currentS else themeHsv[1],
                                if (controlTarget == "Brightness") currentV else themeHsv[2]
                        )
                )
            }
        }
        return Color.HSVToColor(floatArrayOf(currentH, currentS, currentV))
    }
}
