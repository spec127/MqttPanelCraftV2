package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.*
import org.json.JSONObject

class JoystickView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {

    // Properties
    var joystickMode: String = "Joystick" // Joystick, Buttons
        set(value) {
            field = value
            invalidate()
        }
    var axes: String = "4-Way" // 2-Way, 4-Way
        set(value) {
            field = value
            invalidate()
        }
    var direction2Way: String = "Horizontal" // Horizontal, Vertical
        set(value) {
            field = value
            invalidate()
        }
    var interval: Long = 100L
        set(value) {
            field = value.coerceAtLeast(50L)
        }
    var color: Int = Color.parseColor("#6366F1")
        set(value) {
            field = value
            invalidate()
        }
    var visualStyle: String = "Neon" // Neon, Arrow, Industrial, Beveled
        set(value) {
            field = value
            invalidate()
        }

    // Callbacks
    var onJoystickChange: ((String) -> Unit)? = null // For Joystick JSON or Button label

    // State
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var stickRadius = 0f

    private var currentX = 0f
    private var currentY = 0f

    private var isTouched = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastEmittedPayload: String? = null

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val highlightPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
                alpha = 40
            }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val stickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val guidePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                strokeCap = Paint.Cap.ROUND
            }

    private val borderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.WHITE
                alpha = 100
            }

    private val repeatingRunnable =
            object : Runnable {
                override fun run() {
                    if (isTouched) {
                        emitCurrentState()
                        handler.postDelayed(this, interval)
                    }
                }
            }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val density = resources.displayMetrics.density
        val isSmall = h < 100 * density

        centerX = w / 2f
        // Phase 40: Revert to Center (0.5f) as per user request
        centerY = if (isSmall) h * 0.5f else h / 2f

        // Base radius for 4-way mode (proportional)
        // Phase 40: Set to 0.5f (Full Height)
        val baseRadiusProportional = if (isSmall) h * 0.5f else (min(w, h) / 2f) * 0.92f

        if (axes == "4-Way") {
            baseRadius = baseRadiusProportional
        } else {
            // For 2-Way, baseRadius represents the relevant axis extent
            // Phase 40: Set to 0.5f (Full Height)
            baseRadius =
                    if (direction2Way == "Horizontal") (w / 2f) * 0.92f
                    else (if (isSmall) h * 0.5f else (h / 2f) * 0.92f)
        }
        stickRadius = baseRadiusProportional * 0.45f

        currentX = centerX
        currentY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val isDarkMode =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        // Draw Base
        if (joystickMode == "Joystick") {
            if (visualStyle == "Arrow") {
                drawArrowBase(canvas, density, isDarkMode)
            } else {
                drawNeonBase(canvas, density, isDarkMode)
            }
        } else {
            if (visualStyle == "Beveled") {
                drawBeveledButtonBoard(canvas, density, isDarkMode)
            } else if (axes == "2-Way") {
                drawButtonBoard2Way(canvas, density)
            }
        }

        // Draw Guides or D-pad
        if (joystickMode == "Buttons" && visualStyle == "Beveled") {
            // Beveled buttons are drawn inside drawBeveledButtonBoard for integration
        } else {
            drawVisualCues(canvas, density)
        }

        // Draw Stick (only in Joystick mode)
        if (joystickMode == "Joystick") {
            if (visualStyle == "Arrow") {
                drawArrowStick(canvas, density, isDarkMode)
            } else {
                drawNeonStick(canvas, density)
            }
        }
    }

    private fun drawArrowBase(canvas: Canvas, density: Float, isDarkMode: Boolean) {
        val is2Way = axes == "2-Way"

        if (!is2Way) {
            // 1. Tinted thin shadow - Restored to 0.8f * density
            // 1. Shadow - restored to Batch 6 width (0.8dp)
            basePaint.setStyle(Paint.Style.FILL)
            val shadowColor = if (isDarkMode) Color.BLACK else Color.BLACK
            basePaint.setColor(shadowColor)
            basePaint.setAlpha(if (isDarkMode) 20 else 4) // Very subtle in light mode
            canvas.drawCircle(centerX, centerY, baseRadius + 0.8f * density, basePaint)

            // 2. Body: Theme Colored Base (Opaque in Dark, 30% in Light)
            basePaint.setShader(null)
            basePaint.setColor(color)
            basePaint.setAlpha(
                    if (visualStyle == "Arrow") 77 else if (isDarkMode) 255 else 77
            ) // Conditional opacity
            canvas.drawCircle(centerX, centerY, baseRadius, basePaint)

            // 3. Rim - Opaque bright border
            rimPaint.setStrokeWidth(1.0f * density)
            rimPaint.setColor(if (isDarkMode) Color.WHITE else Color.GRAY)
            rimPaint.setAlpha(255) // Opaque
            canvas.drawCircle(centerX, centerY, baseRadius - 0.5f * density, rimPaint)
        } else {
            // Slot shape (Rounded Rectangle)
            val halfW = if (direction2Way == "Horizontal") baseRadius else stickRadius * 1.5f
            val halfH = if (direction2Way == "Vertical") baseRadius else stickRadius * 1.5f
            val rect = RectF(centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH)
            val corner = kotlin.math.min(halfW, halfH)

            // Shadow - Restored to 0.8f * density
            // Shadow - Restored to 0.8f * density
            basePaint.setStyle(Paint.Style.FILL)
            basePaint.setColor(if (isDarkMode) Color.BLACK else Color.BLACK)
            basePaint.setAlpha(if (isDarkMode) 20 else 4)
            canvas.drawRoundRect(
                    RectF(
                            rect.left - 0.8f * density,
                            rect.top - 0.8f * density,
                            rect.right + 0.8f * density,
                            rect.bottom + 0.8f * density
                    ),
                    corner,
                    corner,
                    basePaint
            )

            // Body: Theme Colored Base (30% in Arrow Mode)
            basePaint.setShader(null)
            basePaint.setColor(color)
            basePaint.setAlpha(77) // Arrow Mode is always 30%
            canvas.drawRoundRect(rect, corner, corner, basePaint)

            // Opaque Rim for 2-Way Slot
            // Opaque Rim for 2-Way Slot
            rimPaint.setStrokeWidth(1.0f * density)
            rimPaint.setColor(Color.WHITE)
            rimPaint.setAlpha(255)
            val rimRect =
                    RectF(
                            rect.left + density,
                            rect.top + density,
                            rect.right - density,
                            rect.bottom - density
                    )
            canvas.drawRoundRect(rimRect, corner, corner, rimPaint)
        }

        // Draw Follower Indicator (Image 2 Style)
        if (isTouched) {
            val fdx = currentX - centerX
            val fdy = currentY - centerY
            val dist = kotlin.math.sqrt(fdx * fdx + fdy * fdy)
            if (dist > 5f * density) {
                var angle = Math.toDegrees(atan2(fdy.toDouble(), fdx.toDouble())).toFloat()

                // Snap to axes for 2-Way
                if (axes == "2-Way") {
                    angle =
                            if (direction2Way == "Horizontal") {
                                if (fdx > 0) 0f else 180f
                            } else {
                                if (fdy > 0) 90f else 270f
                            }
                }

                // Follower position: move to OUTSIDE (baseRadius + offset)
                val arrowTipPos = baseRadius + 22f * density

                canvas.save()
                canvas.translate(centerX, centerY) // Pivot is center
                canvas.rotate(angle)
                canvas.translate(arrowTipPos, 0f) // Move OUTSIDE

                val followerPath =
                        Path().apply {
                            val w = 96f * density // 4x Wider (was 24f)
                            val h = 12f * density // Flatter
                            val hOffset = 4f * density // Notch depth

                            moveTo(h, 0f) // Tip pointing right
                            lineTo(-h, -w / 2f) // Top wing
                            lineTo(-(h - hOffset), 0f) // Notch
                            lineTo(-h, w / 2f) // Bottom wing
                            close()
                        }
                val followerPaint =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = this@JoystickView.color
                            alpha = 255
                            style = Paint.Style.FILL
                        }
                canvas.drawPath(followerPath, followerPaint)
                canvas.restore()
            }
        }

        // Draw Triangle Arrows (Small Background Indicators)
        val arrowSize = 6f * density
        val arrowDist = baseRadius * 0.88f

        val fillCol = if (isDarkMode) Color.BLACK else Color.WHITE
        val strokeCol = if (isDarkMode) Color.WHITE else Color.GRAY

        val arrowPaintFill =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fillCol
                    style = Paint.Style.FILL
                }
        val arrowPaintStroke =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = strokeCol
                    style = Paint.Style.STROKE
                    strokeWidth = 1.0f * density
                }

        fun drawTriangle(cx: Float, cy: Float, rotation: Float) {
            canvas.save()
            canvas.rotate(rotation, cx, cy)
            val path =
                    Path().apply {
                        moveTo(0f, -arrowSize)
                        lineTo(arrowSize * 0.8f, arrowSize * 0.6f)
                        lineTo(-arrowSize * 0.8f, arrowSize * 0.6f)
                        close()
                    }
            canvas.translate(cx, cy)
            canvas.drawPath(path, arrowPaintFill)
            canvas.drawPath(path, arrowPaintStroke)
            canvas.restore()
        }

        val arrowInset = 6f * density
        if (!is2Way) {
            drawTriangle(centerX, centerY - arrowDist + arrowInset, 0f) // Up
            drawTriangle(centerX, centerY + arrowDist - arrowInset, 180f) // Down
            drawTriangle(centerX - arrowDist + arrowInset, centerY, 270f) // Left
            drawTriangle(centerX + arrowDist - arrowInset, centerY, 90f) // Right
        } else {
            if (direction2Way == "Vertical") {
                drawTriangle(centerX, centerY - arrowDist + arrowInset, 0f)
                drawTriangle(centerX, centerY + arrowDist - arrowInset, 180f)
            } else {
                drawTriangle(centerX - arrowDist + arrowInset, centerY, 270f)
                drawTriangle(centerX + arrowDist - arrowInset, centerY, 90f)
            }
        }
    }

    private fun drawArrowStick(canvas: Canvas, density: Float, isDarkMode: Boolean) {
        // Reverted to circle for stick head per Batch 9 feedback
        basePaint.style = Paint.Style.FILL
        basePaint.color = Color.BLACK
        basePaint.alpha = if (isDarkMode) 25 else 8
        canvas.drawCircle(currentX, currentY, stickRadius + 1f * density, basePaint)

        val whiteWeight = 0.5f
        val lightenedColor = ColorUtils.blendARGB(color, Color.WHITE, whiteWeight)
        basePaint.color = lightenedColor
        basePaint.alpha = 255
        canvas.drawCircle(currentX, currentY, stickRadius, basePaint)

        rimPaint.strokeWidth = 1.5f * density
        rimPaint.color = if (isDarkMode) Color.WHITE else Color.GRAY
        rimPaint.alpha = 255
        canvas.drawCircle(currentX, currentY, stickRadius, rimPaint)
    }

    private fun drawNeonBase(canvas: Canvas, density: Float, isDarkMode: Boolean) {
        val is2Way = axes == "2-Way"

        // Body Colors (Adaptive)
        val color1 = if (isDarkMode) "#333333" else "#CCCCCC" // Target: Out深 (Original color3)
        val color2 = if (isDarkMode) "#1A1A1A" else "#E0E0E0"
        val color3 = if (isDarkMode) "#000000" else "#F0F0F0" // Target: In淺 (Original color1)

        if (!is2Way) {
            // 4-Way: Tinted thin shadow - restored
            basePaint.style = Paint.Style.FILL
            val shadowColor = if (isDarkMode) Color.BLACK else Color.BLACK
            basePaint.color = shadowColor
            basePaint.alpha = if (isDarkMode) 20 else 4
            canvas.drawCircle(centerX, centerY, baseRadius + 0.8f * density, basePaint)

            // 1. Static Center Shadow (Neon Mode Requirement)
            basePaint.style = Paint.Style.FILL
            basePaint.color = Color.BLACK
            basePaint.alpha = if (isDarkMode) 40 else 15
            canvas.drawCircle(centerX, centerY, stickRadius, basePaint)

            // 2. Body Gradient - (Opaque in Dark, 30% in Light)
            val ringGradient =
                    RadialGradient(
                            centerX,
                            centerY,
                            baseRadius,
                            intArrayOf(
                                    Color.parseColor(color1),
                                    Color.parseColor(color2),
                                    Color.parseColor(color3)
                            ),
                            floatArrayOf(0.7f, 0.9f, 1.0f),
                            Shader.TileMode.CLAMP
                    )
            basePaint.shader = ringGradient
            // Classic Mode: 50% in Light, 100% in Dark
            basePaint.alpha = if (isDarkMode) 255 else 127
            canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
            basePaint.shader = null

            // 3. Rim - Removed for Classic Style as requested
            // rimPaint.alpha = 0

            // 4. Well
            basePaint.color =
                    if (isDarkMode) Color.parseColor("#0D0D0D") else Color.argb(128, 255, 255, 255)
            canvas.drawCircle(centerX, centerY, baseRadius * 0.75f, basePaint)
        } else {
            // 2-Way: Slot shape (Rounded Rectangle) - Force semi-circular ends
            val halfW = if (direction2Way == "Horizontal") baseRadius else stickRadius * 1.5f
            val halfH = if (direction2Way == "Vertical") baseRadius else stickRadius * 1.5f
            val rect = RectF(centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH)
            val corner = min(halfW, halfH) // Perfectly semi-circular ends

            // 1. Shadow - restored
            basePaint.style = Paint.Style.FILL
            basePaint.color = if (isDarkMode) Color.BLACK else Color.BLACK
            basePaint.alpha = if (isDarkMode) 20 else 4
            canvas.drawRoundRect(
                    RectF(
                            rect.left - 0.8f * density,
                            rect.top - 0.8f * density,
                            rect.right + 0.8f * density,
                            rect.bottom + 0.8f * density
                    ),
                    corner,
                    corner,
                    basePaint
            )

            // 2. Body Gradient - (Opaque in Dark, 30% in Light)
            val ringGradient =
                    RadialGradient(
                            centerX,
                            centerY,
                            baseRadius,
                            intArrayOf(
                                    Color.parseColor(color1),
                                    Color.parseColor(color2),
                                    Color.parseColor(color3)
                            ),
                            floatArrayOf(0.7f, 0.9f, 1.0f),
                            Shader.TileMode.CLAMP
                    )
            basePaint.shader = ringGradient
            // Classic Mode: 50% in Light, 100% in Dark
            basePaint.alpha = if (isDarkMode) 255 else 127
            canvas.drawRoundRect(rect, corner, corner, basePaint)
            basePaint.shader = null

            // 3. Static Center Shadow (Neon Mode Requirement)
            basePaint.style = Paint.Style.FILL
            basePaint.color = Color.BLACK
            basePaint.alpha = if (isDarkMode) 40 else 15
            canvas.drawCircle(centerX, centerY, stickRadius, basePaint)

            // 4. Rim - Removed for 2-Way Neon too as per request
            // rimPaint.alpha = 0

            // 4. Well
            basePaint.color =
                    if (isDarkMode) Color.parseColor("#0D0D0D") else Color.argb(128, 255, 255, 255)
            val wellW = if (direction2Way == "Vertical") stickRadius * 1.2f else baseRadius * 0.75f
            val wellH =
                    if (direction2Way == "Horizontal") stickRadius * 1.2f else baseRadius * 0.75f
            val wellRect = RectF(centerX - wellW, centerY - wellH, centerX + wellW, centerY + wellH)
            val wellCorner = kotlin.math.min(wellW, wellH)
            canvas.drawRoundRect(wellRect, wellCorner, wellCorner, basePaint)
        }
    }

    private fun drawBeveledButtonBoard(canvas: Canvas, density: Float, isDarkMode: Boolean) {
        val is4Way = axes == "4-Way"
        val pad = 4f * density

        // 1. Base Glass background (Sub-functions now handle the glass opacity)
        // No longer drawing base here to allow sub-functions to control local 60/80 alpha

        // 2. Beveled Quadrants / Diamonds
        val dx = (currentX - centerX) / baseRadius
        val dy = -(currentY - centerY) / baseRadius
        val activeDir = if (isTouched) calculateButtonLabel(dx, dy) else "none"

        if (is4Way) {
            draw4WayBeveled(canvas, activeDir, density)
        } else {
            draw2WayBeveledIntegrated(canvas, activeDir, density)
        }
    }

    private fun draw4WayBeveled(canvas: Canvas, active: String, density: Float) {
        val cx = centerX
        val cy = centerY
        val radius = kotlin.math.min(width.toFloat(), height.toFloat()) / 2f - 4f * density
        val gap = 1.0f // Tighter gap
        val isDarkMode =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        // Drawing sector backgrounds (Opaque in Dark, 30% in Light)
        fun drawSectorBackground(startAngle: Float, dir: String) {
            val isActive = active == dir
            val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            basePaint.color = color // Theme color
            // Beveled is Buttons variant of Arrow Mode -> 30%
            basePaint.alpha = if (isActive) 120 else 77
            canvas.drawArc(oval, startAngle + gap, 90f - 2 * gap, true, basePaint)
        }

        drawSectorBackground(-135f, "up")
        drawSectorBackground(45f, "down")
        drawSectorBackground(135f, "left")
        drawSectorBackground(-45f, "right")

        // Draw Crossing Lines (White in Light, Grey in Dark) instead of arc rims
        rimPaint.color = if (isDarkMode) Color.TRANSPARENT else Color.WHITE
        if (isDarkMode) {
            rimPaint.color = Color.parseColor("#4D4D4D") // Grey for dark mode
        } else {
            rimPaint.color = Color.WHITE
        }
        rimPaint.alpha = 255
        rimPaint.strokeWidth = 1.2f * density
        rimPaint.style = Paint.Style.STROKE
        val lineLen = radius // from center to edge
        val cos45 = 0.707f
        val offset = lineLen * cos45

        // Diagonal 1: \
        canvas.drawLine(cx - offset, cy - offset, cx + offset, cy + offset, rimPaint)
        // Diagonal 2: /
        canvas.drawLine(cx - offset, cy + offset, cx + offset, cy - offset, rimPaint)

        // Outer Rim (White Circle)
        canvas.drawCircle(cx, cy, radius, rimPaint)
        rimPaint.style = Paint.Style.FILL

        // Icons
        val iconDist = radius * 0.65f
        drawBevelIcon(canvas, cx, cy - iconDist, "up", active == "up", density)
        drawBevelIcon(canvas, cx, cy + iconDist, "down", active == "down", density)
        drawBevelIcon(canvas, cx - iconDist, cy, "left", active == "left", density)
        drawBevelIcon(canvas, cx + iconDist, cy, "right", active == "right", density)
    }

    private fun draw2WayBeveledIntegrated(canvas: Canvas, active: String, density: Float) {
        val isVert = direction2Way == "Vertical"
        val isDarkMode =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        val halfW = if (isVert) stickRadius * 1.5f else baseRadius
        val halfH = if (isVert) baseRadius else stickRadius * 1.5f
        val rect = RectF(centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH)
        val corner = kotlin.math.min(halfW, halfH)

        // Base Glass (Opaque in Dark, 30% in Light)
        basePaint.color = color
        basePaint.alpha = if (isDarkMode) 255 else 77
        canvas.drawRoundRect(rect, corner, corner, basePaint)

        // Integrated interaction zones (80 Alpha Layer)
        if (active != "none") {
            basePaint.color = color
            basePaint.alpha = 20 // 60 + 20 = 80 total visual opacity
            val hRect =
                    if (isVert) {
                        if (active == "up") RectF(rect.left, rect.top, rect.right, centerY)
                        else RectF(rect.left, centerY, rect.right, rect.bottom)
                    } else {
                        if (active == "left") RectF(rect.left, rect.top, centerX, rect.bottom)
                        else RectF(centerX, rect.top, rect.right, rect.bottom)
                    }
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) })
            canvas.drawRect(hRect, basePaint)
            canvas.restore()
        }

        // Divider & Bevel Edge (White in Light, Grey in Dark)
        rimPaint.color = if (isDarkMode) Color.parseColor("#4D4D4D") else Color.WHITE
        rimPaint.alpha = 255
        rimPaint.strokeWidth = 1.5f * density
        if (isVert) {
            canvas.drawLine(
                    rect.left + corner * 0.5f,
                    centerY,
                    rect.right - corner * 0.5f,
                    centerY,
                    rimPaint
            )
        } else {
            canvas.drawLine(
                    centerX,
                    rect.top + corner * 0.5f,
                    centerX,
                    rect.bottom - corner * 0.5f,
                    rimPaint
            )
        }

        // Final Rim Border (Match the style)
        rimPaint.style = Paint.Style.STROKE
        canvas.drawRoundRect(rect, corner, corner, rimPaint)
        rimPaint.style = Paint.Style.FILL

        // Icons
        val iconDist = (if (isVert) halfH else halfW) * 0.6f
        if (isVert) {
            drawBevelIcon(canvas, centerX, centerY - iconDist, "up", active == "up", density)
            drawBevelIcon(canvas, centerX, centerY + iconDist, "down", active == "down", density)
        } else {
            drawBevelIcon(canvas, centerX - iconDist, centerY, "left", active == "left", density)
            drawBevelIcon(canvas, centerX + iconDist, centerY, "right", active == "right", density)
        }
    }

    private fun drawBevelIcon(
            canvas: Canvas,
            x: Float,
            y: Float,
            dir: String,
            isActive: Boolean,
            density: Float
    ) {
        val isDarkMode =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
        val size = 6f * density
        val bevelGrey = Color.parseColor("#4D4D4D")
        val strokeCol =
                if (isActive) {
                    Color.WHITE
                } else {
                    if (isDarkMode) bevelGrey else Color.WHITE
                }
        val iconPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = strokeCol
                    alpha = 255 // 100% Opaque
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f * density // Thicker icons
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
        val path = Path()
        when (dir) {
            "up" -> {
                path.moveTo(x - size, y + size / 2)
                path.lineTo(x, y - size / 2)
                path.lineTo(x + size, y + size / 2)
            }
            "down" -> {
                path.moveTo(x - size, y - size / 2)
                path.lineTo(x, y + size / 2)
                path.lineTo(x + size, y - size / 2)
            }
            "left" -> {
                path.moveTo(x + size / 2, y - size)
                path.lineTo(x - size / 2, y)
                path.lineTo(x + size / 2, y + size)
            }
            "right" -> {
                path.moveTo(x - size / 2, y - size)
                path.lineTo(x + size / 2, y)
                path.lineTo(x - size / 2, y + size)
            }
        }
        canvas.drawPath(path, iconPaint)
    }

    private fun drawNeonStick(canvas: Canvas, density: Float) {
        val glowRadius = if (isTouched) 15f * density else 10f * density
        val glowColor = ColorUtils.setAlphaComponent(color, if (isTouched) 200 else 150)

        stickPaint.style = Paint.Style.FILL
        stickPaint.setShadowLayer(glowRadius, 0f, 0f, glowColor)

        // Batch 7: Lightened Theme Color
        val litColor = ColorUtils.blendARGB(color, Color.WHITE, 0.4f)

        // Sphere Gradient
        val puckGradient =
                RadialGradient(
                        currentX,
                        currentY,
                        stickRadius,
                        intArrayOf(
                                ColorUtils.setAlphaComponent(litColor, 200),
                                litColor,
                                ColorUtils.setAlphaComponent(litColor, 255)
                        ),
                        floatArrayOf(0f, 0.6f, 1.0f),
                        Shader.TileMode.CLAMP
                )
        stickPaint.shader = puckGradient
        canvas.drawCircle(currentX, currentY, stickRadius, stickPaint)

        // Batch 7: Opaque bright border for stick
        stickPaint.shader = null
        stickPaint.style = Paint.Style.STROKE
        stickPaint.strokeWidth = 1.8f * density
        stickPaint.color = Color.WHITE
        stickPaint.alpha = 255 // Opaque
        canvas.drawCircle(currentX, currentY, stickRadius, stickPaint)

        stickPaint.shader = null
        stickPaint.clearShadowLayer()

        // Specular Highlight (Dynamic relative motion)
        val maxDist = baseRadius - stickRadius
        val dx = currentX - centerX
        val dy = currentY - centerY
        val hOffX = if (maxDist > 0) -(dx / maxDist) * (stickRadius * 0.25f) else 0f
        val hOffY = if (maxDist > 0) -(dy / maxDist) * (stickRadius * 0.25f) else 0f
        canvas.drawCircle(currentX + hOffX, currentY + hOffY, stickRadius * 0.6f, highlightPaint)
    }

    private fun drawIndustrialBase(canvas: Canvas, density: Float) {}
    private fun drawIndustrialStick(canvas: Canvas, density: Float) {}

    private fun drawButtonBoard2Way(canvas: Canvas, density: Float) {
        val isVert = direction2Way == "Vertical"
        val pad = 4f * density

        // 1. Board Dimensions (Pill Shape)
        val boardW = if (isVert) stickRadius * 2.8f else (width / 2f) * 0.9f
        val boardH = if (isVert) (height / 2f) * 0.9f else stickRadius * 2.8f
        val rect = RectF(centerX - boardW, centerY - boardH, centerX + boardW, centerY + boardH)
        val corner = 6f * density // Small corner radius

        // 1. Shadow (Restored)
        basePaint.style = Paint.Style.FILL
        basePaint.color = Color.BLACK
        basePaint.alpha = 20 // Assuming dark mode for shadow, or a default subtle value
        canvas.drawRoundRect(
                RectF(
                        rect.left - 0.8f * density,
                        rect.top - 0.8f * density,
                        rect.right + 0.8f * density,
                        rect.bottom + 0.8f * density
                ),
                corner,
                corner,
                basePaint
        )

        // 2. Base Background (Match 4-way button color)
        basePaint.color = color
        basePaint.alpha = 160
        canvas.drawRoundRect(rect, corner, corner, basePaint)

        // 3. Determine Active Half
        val dx = (currentX - centerX) / baseRadius
        val dy = -(currentY - centerY) / baseRadius
        val active = if (isTouched) calculateButtonLabel(dx, dy) else "none"

        // 4. Draw Active Highlight
        if (active != "none") {
            basePaint.color = color
            basePaint.alpha = 255 // Solid highlight
            val highlightRect =
                    if (isVert) {
                        if (active == "up") RectF(rect.left, rect.top, rect.right, centerY)
                        else RectF(rect.left, centerY, rect.right, rect.bottom)
                    } else {
                        if (active == "left") RectF(rect.left, rect.top, centerX, rect.bottom)
                        else RectF(centerX, rect.top, rect.right, rect.bottom)
                    }
            // Clip to board shape for highlight
            canvas.save()
            val clipPath = Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) }
            canvas.clipPath(clipPath)
            canvas.drawRect(highlightRect, basePaint)
            canvas.restore()
        }

        // 5. Divider
        guidePaint.color = Color.WHITE
        guidePaint.alpha = 60
        guidePaint.strokeWidth = 2.5f * density // Thicker divider
        if (isVert) {
            canvas.drawLine(
                    centerX - boardW * 0.8f,
                    centerY,
                    centerX + boardW * 0.8f,
                    centerY,
                    guidePaint
            )
        } else {
            canvas.drawLine(
                    centerX,
                    centerY - boardH * 0.8f,
                    centerX,
                    centerY + boardH * 0.8f,
                    guidePaint
            )
        }

        // 6. Icons (Triangles)
        basePaint.color = Color.WHITE
        val arrowSize = min(boardW, boardH) * 0.35f
        fun drawArrow(tx: Float, ty: Float, rot: Float, isActive: Boolean) {
            basePaint.alpha = if (isActive) 255 else 160
            val path = Path()
            path.moveTo(0f, -arrowSize)
            path.lineTo(arrowSize * 0.8f, arrowSize * 0.5f)
            path.lineTo(-arrowSize * 0.8f, arrowSize * 0.5f)
            path.close()
            val m = Matrix()
            m.postRotate(rot)
            m.postTranslate(tx, ty)
            path.transform(m)
            canvas.drawPath(path, basePaint)
        }

        if (isVert) {
            drawArrow(centerX, centerY - boardH * 0.5f, 0f, active == "up")
            drawArrow(centerX, centerY + boardH * 0.5f, 180f, active == "down")
        } else {
            drawArrow(centerX - boardW * 0.5f, centerY, 270f, active == "left")
            drawArrow(centerX + boardW * 0.5f, centerY, 90f, active == "right")
        }

        // 7. Border
        borderPaint.color = color
        borderPaint.alpha = 140
        canvas.drawRoundRect(rect, corner, corner, borderPaint)
    }

    private fun drawVisualCues(canvas: Canvas, density: Float) {
        guidePaint.color = color

        // Removed guide lines (tracks) for 2-Way as requested

        // Removed crosshairs for 4-way as requested

        if (joystickMode == "Buttons") {
            // Draw D-pad segments
            basePaint.color = color
            val offset = baseRadius * 0.55f
            val buttonSize = baseRadius * 0.4f

            // Determine active button
            val dx = (currentX - centerX) / baseRadius
            val dy = -(currentY - centerY) / baseRadius
            val activeDir = if (isTouched) calculateButtonLabel(dx, dy) else "none"

            if (axes == "4-Way") {
                drawDpadButton(canvas, centerX, centerY - offset, buttonSize, 0f, activeDir == "up")
                drawDpadButton(
                        canvas,
                        centerX,
                        centerY + offset,
                        buttonSize,
                        180f,
                        activeDir == "down"
                )
                drawDpadButton(
                        canvas,
                        centerX - offset,
                        centerY,
                        buttonSize,
                        270f,
                        activeDir == "left"
                )
                drawDpadButton(
                        canvas,
                        centerX + offset,
                        centerY,
                        buttonSize,
                        90f,
                        activeDir == "right"
                )
            } else if (visualStyle != "Beveled") {
                // For non-beveled 2-Way, the board reflects the buttons, so we skip drawing
                // separated ones
                // No action needed here as we want to skip drawDpadButton for integrated 2-way
            }
        }
    }

    private fun drawDpadButton(
            canvas: Canvas,
            x: Float,
            y: Float,
            size: Float,
            rotation: Float,
            isActive: Boolean
    ) {
        basePaint.alpha = if (isActive) 255 else 160
        basePaint.style = Paint.Style.FILL

        val path = Path()
        val corner = size * 0.2f

        if (axes == "2-Way") {
            // Square Rounded Shape for 2-Way Buttons
            path.addRoundRect(
                    -size * 0.75f,
                    -size * 0.75f,
                    size * 0.75f,
                    size * 0.75f,
                    corner,
                    corner,
                    Path.Direction.CW
            )
        } else {
            // Shape: Inverted Rounded Pentagon (Point facing center) for 4-Way
            // At 0f rotation (Up button), it points INWARDS (towards center).
            path.moveTo(-size * 0.75f, -size) // Top Left
            path.lineTo(size * 0.75f, -size) // Top Right
            path.lineTo(size * 0.75f, size * 0.2f) // Bottom Right shoulder
            path.lineTo(0f, size) // Bottom Point (facing center)
            path.lineTo(-size * 0.75f, size * 0.2f) // Bottom Left shoulder
            path.close()
        }

        val matrix = Matrix()
        matrix.postRotate(rotation)
        matrix.postTranslate(x, y)
        path.transform(matrix)

        val oldEffect = basePaint.pathEffect
        basePaint.pathEffect = CornerPathEffect(corner)
        canvas.drawPath(path, basePaint)

        // Directional Triangle inside
        basePaint.color = Color.WHITE
        basePaint.alpha = if (isActive) 255 else 180
        basePaint.pathEffect = null

        val arrow = Path()
        val arrowSize = size * 0.3f
        arrow.moveTo(0f, -size * 0.7f) // Tip pointing OUT
        arrow.lineTo(arrowSize, -size * 0.3f)
        arrow.lineTo(-arrowSize, -size * 0.3f)
        arrow.close()
        arrow.transform(matrix)
        canvas.drawPath(arrow, basePaint)

        // Restore color for next buttons
        basePaint.color = color
        basePaint.pathEffect = oldEffect
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTouched = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateStickPosition(event.x, event.y)
                handler.post(repeatingRunnable)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateStickPosition(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouched = false
                parent?.requestDisallowInterceptTouchEvent(false)
                handler.removeCallbacks(repeatingRunnable)
                resetStick()
                emitCurrentState() // Final 0,0 or Release
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateStickPosition(touchX: Float, touchY: Float) {
        var dx = touchX - centerX
        var dy = touchY - centerY

        // Apply Constraints for 2-Way
        if (axes == "2-Way") {
            if (direction2Way == "Horizontal") dy = 0f else dx = 0f
        }

        currentX = centerX + dx
        currentY = centerY + dy

        // 2. Limit movement radius
        val limit = baseRadius * 0.75f
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > limit) {
            currentX = centerX + (dx / dist) * limit
            currentY = centerY + (dy / dist) * limit
        }
    }

    private fun resetStick() {
        currentX = centerX
        currentY = centerY
    }

    private fun emitCurrentState() {
        val dx = (currentX - centerX) / baseRadius
        val dy = -(currentY - centerY) / baseRadius // Invert Y for Cartesian

        val payload =
                if (joystickMode == "Joystick") {
                    val json = JSONObject()
                    json.put("x", (dx * 100).toInt())
                    json.put("y", (dy * 100).toInt())
                    json.toString()
                } else {
                    // Button Mode Logic
                    calculateButtonLabel(dx, dy)
                }

        // Avoid duplicate emits if nothing changed (especially on UP)
        if (payload != lastEmittedPayload) {
            onJoystickChange?.invoke(payload)
            lastEmittedPayload = payload
        }
    }

    private fun calculateButtonLabel(dx: Float, dy: Float): String {
        val threshold = 0.3f
        if (abs(dx) < threshold && abs(dy) < threshold) return "none"

        return if (abs(dx) > abs(dy)) {
            if (dx > 0) "right" else "left"
        } else {
            if (dy > 0) "up" else "down"
        }
    }
}
