package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class ColorPaletteView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {

    // Properties
    private val density = resources.displayMetrics.density
    private var swatchAreaHeightReal = 0f
    private var topSpaceHeightReal = 24.0f * density
    private var activeTouchArea = 0 // 0: None, 1: Palette, 2: Swatches
    private var isManualColorSet = false

    var controllerStyle: String = "Arc Ring" // "Square Pad", "Arc Ring"
        set(value) {
            field = value
            invalidate()
        }
    var colorMode: String = "Full Color" // "Full Color", "Monochrome"
        set(value) {
            if (field != value) {
                field = value
                handleModeSwitch(value)
                invalidate()
            }
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
    var showHue: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    var showSaturation: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    var showBrightness: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    // Property for Base Hue (used in Square Pad Full Color)
    var baseHue: Float = 0f
        set(value) {
            field = value
            currentH = value
            invalidate()
        }

    // MQTT Emission (hue, sat, val, isFinal)
    var onColorChange: ((Float, Float, Float, Boolean) -> Unit)? = null
    var onThemeColorSelect: ((Int) -> Unit)? = null

    // Internal State
    var currentH: Float = 0f
        set(value) {
            field = value % 360f
            isManualColorSet = true
            invalidate()
        }
    var currentS: Float = 1f
        set(value) {
            field = value
            isManualColorSet = true
            invalidate()
        }
    var currentV: Float = 1f
        set(value) {
            field = value
            isManualColorSet = true
            invalidate()
        }

    // For Mono mode, base color components
    private val themeHsv = FloatArray(3)

    // Paints
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * resources.displayMetrics.density
                color = Color.WHITE
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
                color = Color.argb(180, 255, 255, 255)
                textSize = 9f * resources.displayMetrics.density
                letterSpacing = 0.1f
            }

    private fun getSafeLuminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val isDarkMode =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        // Phase 36: Extreme Magnification for small sidebar previews
        val isSmall = this.height < 100 * density
        topSpaceHeightReal = if (isSmall) 0f else 24f * density
        swatchAreaHeightReal =
                if (colorMode == "Full Color" || this.height < 120 * density) 0f else 24f * density

        if (this.width <= 0 || this.height <= 0) return

        val paletteHeight =
                max(30f * density, this.height - swatchAreaHeightReal - topSpaceHeightReal)

        canvas.save()
        canvas.translate(0f, topSpaceHeightReal)

        if (controllerStyle == "Arc Ring") {
            drawArcRing(canvas, density, isDarkMode, paletteHeight)
        } else {
            drawSquarePad(canvas, density, isDarkMode, paletteHeight)
        }
        canvas.restore()

        if (swatchAreaHeightReal > 0) {
            drawSwatches(canvas, density, isDarkMode)
        }

        // Title Overlay for Square Pad
        if (controllerStyle == "Square Pad") {
            val showAny = showHexCode || showHue || showSaturation || showBrightness
            if (showAny) {
                val currentColor = getCurrentColor()
                val hex = String.format("#%06X", (0xFFFFFF and currentColor))
                val parts = mutableListOf<String>()

                if (colorMode == "Full Color") {
                    // Phase 32: For Square + Full Color, only show Hex and Brightness
                    if (showHexCode) parts.add(hex)
                    if (showBrightness) parts.add("B: ${(currentV * 100).toInt()}%")
                } else {
                    if (showHexCode) parts.add(hex)
                    // H removed in mono per user request
                    val sPercent = (currentS * 100).toInt()
                    val bPercent = (currentV * 100).toInt()
                    if (showSaturation) parts.add("S: $sPercent%")
                    if (showBrightness) parts.add("B: $bPercent%")
                }

                val overlay = parts.joinToString(" | ")
                val dynamicTextSize = max(11f * density, paletteHeight * 0.065f)

                // Phase 32: Apply theme-aware shadow ONLY to text
                val shadowColor = if (isDarkMode) Color.WHITE else Color.BLACK
                textPaint.setShadowLayer(3f, 0f, 0f, shadowColor)

                textPaint.textSize = dynamicTextSize
                textPaint.color = currentColor

                val ty = topSpaceHeightReal - 6f * density
                canvas.drawText(overlay, this.width / 2f, ty, textPaint)
                textPaint.clearShadowLayer()
            }
        }
    }

    private fun drawArcRing(
            canvas: Canvas,
            density: Float,
            isDarkMode: Boolean,
            availableHeight: Float
    ) {
        val cx = this.width / 2f
        // Phase 41: Shift Center UP slightly to 0.42f (User request: Right side higher)
        val isSmall = availableHeight < 100f * density
        val cy = if (isSmall) availableHeight * 0.42f else availableHeight / 2f

        // Phase 41: Radius to 0.42f to fit top edge perfectly (0.42 - 0.42 = 0)
        val margin = if (availableHeight > 80f * density) 10f * density else 0f
        val radius = if (isSmall) availableHeight * 0.42f else (min(cx, cy) - margin)

        // Phase 41: Thicker ring for small view
        val ringWidth = if (availableHeight > 80f * density) radius * 0.25f else radius * 0.6f
        val innerRadius = radius - ringWidth

        // 1. Ring Gradient
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
            val matrix = Matrix()
            matrix.setRotate(-90f, cx, cy)
            sweep.setLocalMatrix(matrix)
            mainPaint.shader = sweep
        } else {
            Color.colorToHSV(themeColor, themeHsv)
            val themeC = Color.HSVToColor(floatArrayOf(themeHsv[0], themeHsv[1], 1f))
            val colors: IntArray
            val positions: FloatArray?
            if (controlTarget == "Brightness") {
                // Phase 32: 0% Black (Start) -> 50% Theme -> 100% White (End)
                colors = intArrayOf(Color.BLACK, themeC, Color.WHITE)
                positions = floatArrayOf(0f, 0.5f, 1f)
            } else {
                // Phase 32: 100% Theme (Start) -> 0% White (End) Clockwise per user req
                colors = intArrayOf(themeC, Color.WHITE)
                positions = floatArrayOf(0f, 1f)
            }
            val sweep = SweepGradient(cx, cy, colors, positions)
            val matrix = Matrix()
            matrix.setRotate(-90f, cx, cy)
            sweep.setLocalMatrix(matrix)
            mainPaint.shader = sweep
        }

        mainPaint.style = Paint.Style.STROKE
        mainPaint.strokeWidth = ringWidth
        canvas.drawCircle(cx, cy, radius - ringWidth / 2, mainPaint)
        mainPaint.shader = null

        // 2. Glass Center
        glassPaint.alpha = if (isDarkMode) 20 else 10
        canvas.drawCircle(cx, cy, innerRadius - 2 * density, glassPaint)

        // 3. Labels
        val innerScale = innerRadius / (100f * density)
        val topY = cy - 25f * density * innerScale
        subTextPaint.textSize = 10f * density * innerScale
        textPaint.textSize = 25f * density * innerScale

        // Phase 32: Apply theme-aware shadow ONLY to center status text
        val shadowColorInput = if (isDarkMode) Color.WHITE else Color.BLACK
        textPaint.setShadowLayer(3f, 0f, 0f, shadowColorInput)
        subTextPaint.setShadowLayer(2f, 0f, 0f, shadowColorInput)

        val isFC = colorMode == "Full Color"
        val showMainText =
                if (isFC) showHue
                else (if (controlTarget == "Brightness") showBrightness else showSaturation)

        val currentColor = getCurrentColor()
        val isColorDark = getSafeLuminance(currentColor) < 0.35f

        // Phase 37: Hide all center text for small sidebar previews
        if (!isSmall) {
            if (showMainText) {
                val labelMain = if (isFC) "HUE" else controlTarget.uppercase()
                val valMain =
                        if (isFC) (currentH.toInt() % 360)
                        else
                                ((if (controlTarget == "Brightness") currentV else currentS) * 100)
                                        .toInt()
                val unit = if (isFC) "°" else "%"

                subTextPaint.color =
                        if (isDarkMode) Color.argb(180, 255, 255, 255) else Color.DKGRAY
                canvas.drawText(labelMain, cx, topY - 14f * density * innerScale, subTextPaint)

                textPaint.color = if (isColorDark && isDarkMode) Color.WHITE else currentColor
                canvas.drawText(
                        String.format("%d%s", valMain, unit),
                        cx,
                        topY + 8f * density * innerScale,
                        textPaint
                )
            }

            if (showHexCode) {
                val bottomY = cy + 40f * density * innerScale
                canvas.drawText("COLOR", cx, bottomY - 18f * density * innerScale, subTextPaint)
                val hex = String.format("#%06X", (0xFFFFFF and currentColor))
                textPaint.color = if (isColorDark && isDarkMode) Color.WHITE else currentColor
                canvas.drawText(hex, cx, bottomY + 8f * density * innerScale, textPaint)
            }
        }

        textPaint.clearShadowLayer()
        subTextPaint.clearShadowLayer()

        // 4. Marker
        val angle =
                if (isFC) currentH - 90f
                else {
                    // Saturation 100% -> Marker at Top (0 deg offset)
                    // Saturation 0% -> Marker at End (360 deg offset)
                    val ratio = if (controlTarget == "Brightness") currentV else (1f - currentS)
                    ratio * 359f - 90f // Use 359 to avoid visual overlap at exactly top
                }
        val markerPosRadius = radius - ringWidth / 2
        val mx = cx + markerPosRadius * cos(Math.toRadians(angle.toDouble())).toFloat()
        val my = cy + markerPosRadius * sin(Math.toRadians(angle.toDouble())).toFloat()

        markerPaint.style = Paint.Style.FILL
        markerPaint.color = Color.WHITE
        canvas.drawCircle(mx, my, (ringWidth / 2) * 1.1f, markerPaint)
        markerPaint.style = Paint.Style.STROKE
        markerPaint.color = Color.argb(80, 0, 0, 0)
        markerPaint.strokeWidth = 2f * density
        canvas.drawCircle(mx, my, (ringWidth / 2) * 1.1f, markerPaint)
    }

    private fun drawSquarePad(
            canvas: Canvas,
            density: Float,
            isDarkMode: Boolean,
            availableHeight: Float
    ) {
        mainPaint.style = Paint.Style.FILL
        val padMargin =
                if (colorMode == "Full Color" || availableHeight < 80f * density) 0f
                else 6f * density
        val w = this.width - 2 * padMargin
        val h = max(10f, availableHeight - 2 * padMargin)
        val rect = RectF(padMargin, padMargin, this.width - padMargin, padMargin + h)
        val corner = if (colorMode == "Full Color") 0f else 8f * density

        if (colorMode == "Full Color") {
            val hueColors =
                    intArrayOf(
                            Color.RED,
                            Color.YELLOW,
                            Color.GREEN,
                            Color.CYAN,
                            Color.BLUE,
                            Color.MAGENTA,
                            Color.RED
                    )
            val hueShader =
                    LinearGradient(
                            rect.left,
                            rect.top,
                            rect.right,
                            rect.top,
                            hueColors,
                            null,
                            Shader.TileMode.CLAMP
                    )
            val valColors = intArrayOf(Color.WHITE, Color.TRANSPARENT, Color.BLACK)
            val valShader =
                    LinearGradient(
                            rect.left,
                            rect.top,
                            rect.left,
                            rect.bottom,
                            valColors,
                            floatArrayOf(0f, 0.5f, 1f),
                            Shader.TileMode.CLAMP
                    )
            mainPaint.shader = hueShader
            canvas.drawRoundRect(rect, corner, corner, mainPaint)
            mainPaint.shader = valShader
            canvas.drawRoundRect(rect, corner, corner, mainPaint)
            mainPaint.shader = null

            val mx = rect.left + (currentH / 360f) * w
            val my =
                    if (currentS < 1f || currentV == 1f) rect.top + (currentS * 0.5f) * h
                    else rect.top + (0.5f + (1f - currentV) * 0.5f) * h
            markerPaint.color = if (currentV < 0.5f) Color.WHITE else Color.BLACK
            canvas.drawCircle(mx, my, 8f * density, markerPaint)
        } else {
            Color.colorToHSV(themeColor, themeHsv)
            val satShader =
                    LinearGradient(
                            rect.left,
                            rect.top,
                            rect.right,
                            rect.top,
                            Color.WHITE,
                            Color.HSVToColor(floatArrayOf(themeHsv[0], 1f, 1f)),
                            Shader.TileMode.CLAMP
                    )
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
            mainPaint.shader = satShader
            canvas.drawRoundRect(rect, corner, corner, mainPaint)
            mainPaint.shader = valShader
            canvas.drawRoundRect(rect, corner, corner, mainPaint)
            mainPaint.shader = null

            val mx = rect.left + currentS * w
            val my = rect.top + (1f - currentV) * h
            markerPaint.color = if (currentV < 0.5f) Color.WHITE else Color.BLACK
            canvas.drawCircle(mx, my, 8f * density, markerPaint)
        }
    }

    private fun drawSwatches(canvas: Canvas, density: Float, isDarkMode: Boolean) {
        if (colorMode == "Full Color") return
        val sideMargin = 12f * density
        val barHeight = 11f * density
        val centerY = this.height - swatchAreaHeightReal / 2f
        val barRect =
                RectF(
                        sideMargin,
                        centerY - barHeight / 2f,
                        this.width - sideMargin,
                        centerY + barHeight / 2f
                )

        val colors =
                intArrayOf(
                        Color.RED,
                        Color.YELLOW,
                        Color.GREEN,
                        Color.CYAN,
                        Color.BLUE,
                        Color.MAGENTA,
                        Color.RED
                )
        val hueGradient =
                LinearGradient(
                        barRect.left,
                        barRect.top,
                        barRect.right,
                        barRect.top,
                        colors,
                        null,
                        Shader.TileMode.CLAMP
                )
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = hueGradient
        canvas.drawRoundRect(barRect, barHeight / 2, barHeight / 2, p)

        Color.colorToHSV(themeColor, themeHsv)
        val markerX = barRect.left + (themeHsv[0] / 360f) * barRect.width()
        p.shader = null
        p.color = if (isDarkMode) Color.WHITE else Color.BLACK
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * density
        canvas.drawCircle(markerX, centerY, barHeight / 2 + 2f * density, p)
        p.style = Paint.Style.FILL
        p.color = themeColor
        canvas.drawCircle(markerX, centerY, barHeight / 2 - 1f * density, p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val paletteHeight =
                        max(100f * density, this.height - swatchAreaHeightReal - topSpaceHeightReal)
                val inPal = event.y in topSpaceHeightReal..(topSpaceHeightReal + paletteHeight)
                activeTouchArea =
                        if (inPal) 1 else if (event.y > topSpaceHeightReal + paletteHeight) 2 else 0
                handleTouch(event.x, event.y, false)
                emitColor(false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTouchArea > 0) handleTouch(event.x, event.y, true)
                emitColor(false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                handleTouch(event.x, event.y, false)
                emitColor(true)
                activeTouchArea = 0
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(tx: Float, ty: Float, isDragging: Boolean = false) {
        val paletteHeight =
                max(100f * density, this.height - swatchAreaHeightReal - topSpaceHeightReal)
        if (activeTouchArea == 2) {
            val side = 12f * density
            val rw = this.width - 2 * side
            val hue = ((tx - side).coerceIn(0f, rw) / rw) * 360f
            themeColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
            baseHue = hue
            onThemeColorSelect?.invoke(themeColor)
            return
        }
        if (activeTouchArea != 1) return

        val cx = this.width / 2f
        val cy = paletteHeight / 2f
        val ly = ty - topSpaceHeightReal

        if (controllerStyle == "Arc Ring") {
            var angle =
                    Math.toDegrees(atan2((ly - cy).toDouble(), (tx - cx).toDouble())).toFloat() +
                            90f
            if (angle < 0) angle += 360f
            if (angle >= 360f) angle -= 360f

            if (colorMode == "Full Color") {
                currentH = angle
            } else {
                // Phase 54: Prevent abrupt 0 <-> 360 jump during drag
                // If it's Monochromatic, the ends (0 and 360) have totally different meanings
                val oldAngle =
                        if (controlTarget == "Brightness") currentV * 360f
                        else (1f - currentS) * 360f
                val diff = abs(angle - oldAngle)

                // Phase 55.6: Fix jump logic to snap to boundary (0 or 1)
                // Trigger on ANY large jump (>180 deg), whether dragging or tapping/releasing
                val isGapJump = diff > 180f
                if (isGapJump) {
                    // Snap to the closest boundary
                    // If old angle was > 180 (near 360), snap to 1.0
                    // If old angle was < 180 (near 0), snap to 0.0
                    val targetRatio = if (oldAngle > 180f) 1.0f else 0.0f
                    if (controlTarget == "Brightness") {
                        currentV = targetRatio
                    } else {
                        // For Saturation, S=0 is White (End/360), S=1 is Theme (Start/0)
                        // If snapping to End (1.0 ratio), S should be 0.0
                        // If snapping to Start (0.0 ratio), S should be 1.0
                        currentS = 1f - targetRatio
                    }
                } else {
                    val ratio = angle / 360f
                    if (controlTarget == "Brightness") {
                        currentV = ratio
                    } else {
                        // Phase 36: Saturation Calibration
                        // Removed the > 0.985f check to preventing wrapping back to 1.0 (Color) at
                        // the end (White)
                        currentS = 1f - ratio
                    }
                }
            }
        } else {
            val m = if (colorMode == "Full Color") 0f else 6f * density
            val rw = this.width - 2 * m
            val rh = max(10f, paletteHeight - 2 * m)
            val x = (tx - m).coerceIn(0f, rw)
            val y = (ly - m).coerceIn(0f, rh)
            if (colorMode == "Full Color") {
                currentH = (x / rw) * 360f
                val ry = y / rh
                if (ry < 0.5f) {
                    currentS = ry * 2f
                    currentV = 1f
                } else {
                    currentS = 1f
                    currentV = 1f - (ry - 0.5f) * 2f
                }
            } else {
                currentS = x / rw
                currentV = 1f - (y / rh)
            }
        }
    }

    private fun emitColor(isFinal: Boolean) {
        onColorChange?.invoke(currentH, currentS, currentV, isFinal)
    }

    private fun handleModeSwitch(newMode: String) {
        if (newMode == "Monochrome") {
            Color.colorToHSV(themeColor, themeHsv)
            currentH = themeHsv[0]
            // Phase 35: Default to 100% saturation (top position) when switching to mono
            currentS = 1.0f
            // Phase 55.7: Default brightness to 0% (Off) when switching to Mono, unless manually
            // set
            if (!isManualColorSet) currentV = 0.0f
        } else {
            // Phase 54: Ensure S/V are 1.0 when switching to Full Color if they were low
            // This prevents the "always black" issue in Full Color Arc mode
            currentS = 1.0f
            currentV = 1.0f
        }
        isManualColorSet = false
    }

    private fun getCurrentColor(): Int {
        if (controllerStyle == "Square Pad") {
            val h =
                    if (colorMode == "Full Color") currentH
                    else {
                        Color.colorToHSV(themeColor, themeHsv)
                        themeHsv[0]
                    }
            return Color.HSVToColor(floatArrayOf(h, currentS, currentV))
        }
        if (colorMode == "Full Color") {
            // Phase 54: Ensure Full Color Ring mode also returns correct HSV color
            // Phase 55.6: Force V=1.0 for Arc Ring to prevent "Black" if V somehow got set to 0
            if (controllerStyle == "Arc Ring") {
                return Color.HSVToColor(floatArrayOf(currentH, 1f, 1f))
            }
            return Color.HSVToColor(floatArrayOf(currentH, currentS, currentV))
        }

        // Monochrome fallback
        Color.colorToHSV(themeColor, themeHsv)
        val themeC = Color.HSVToColor(floatArrayOf(themeHsv[0], themeHsv[1], 1f))
        val ratio = if (controlTarget == "Brightness") currentV else currentS
        return if (controlTarget == "Brightness") {
            // Phase 32: 0% Black (Ratio 0) -> 100% White (Ratio 1)
            if (ratio <= 0.5f) blendColors(Color.BLACK, themeC, ratio * 2f)
            else blendColors(themeC, Color.WHITE, (ratio - 0.5f) * 2f)
        } else blendColors(Color.WHITE, themeC, ratio)
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * ratio).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * ratio).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * ratio).toInt()
        return Color.rgb(r, g, b)
    }
}
