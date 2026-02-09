package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils

// V5.1 - Explicit ViewGroup fixed
class PanelSliderView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {

    // V6.1 - Improved internal layout & compilation fixes
    var minValue: Float = 0f
    var maxValue: Float = 100f
    var value: Float = 0f
        set(v) {
            field = v.coerceIn(minValue, maxValue)
            invalidate()
        }
    var stepSize: Float = 1.0f

    var sliderStyle: String = "Classic" // Classic, Capsule, Arc, Ring
        set(v) {
            field = v
            invalidate()
        }
    var shape: String = "Circle" // Square, Circle
        set(v) {
            field = v
            invalidate()
        }
    var displayMode: String = "text" // text, icon, text_icon
        set(v) {
            field = v
            invalidate()
        }
    var label: String = ""
        set(v) {
            field = v
            invalidate()
        }
    var iconKey: String = "tune"
        set(v) {
            field = v
            invalidate()
        }
    var color: Int = Color.parseColor("#2196F3")
        set(v) {
            field = v
            invalidate()
        }

    // var unit: String = "" // REMOVED for V6

    var feedback: String = "None" // None, Ticks, Bubble, Both
        set(v) {
            field = v
            invalidate()
        }

    private var isDragging = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateValueFromTouch(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                isDragging = true
                updateValueFromTouch(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                onActionUp?.invoke()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private val trackPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
    private val progressPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                color = Color.WHITE
            }

    // Keep orientation as separate property for linear styles
    var orientation: String = "Horizontal"
        set(v) {
            field = v
            invalidate()
        }

    var onValueChange: ((Float) -> Unit)? = null
    var onActionUp: (() -> Unit)? = null

    init {
        // Enable software rendering for BlurMaskFilter if needed
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        forceUnclipParents()
    }

    private fun forceUnclipParents() {
        var p = parent
        while (p != null && p is android.view.ViewGroup) {
            val vg = p as android.view.ViewGroup
            vg.setClipChildren(false)
            vg.setClipToPadding(false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                vg.clipToOutline = false
            }
            p = p.parent
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Still unclip to be safe, but V6 aims for internal drawing
        forceUnclipParents()
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density

        drawLinear(canvas, w, h, density)
    }

    private fun drawLinear(canvas: Canvas, w: Float, h: Float, density: Float) {
        val isVertical = orientation == "Vertical"
        val isCapsule = sliderStyle == "Capsule"
        val isSquare = shape == "Square"
        val hasBubble = (feedback == "Bubble" || feedback == "Both")

        val trackThickness =
                if (isCapsule) (if (isVertical) w * 0.4f else h * 0.4f) else 4f * density

        val isClassic = sliderStyle == "Classic"
        val thumbScale = if (isSquare) 1.0f else 1.2f
        val baseThumbSize =
                if (isSquare) {
                    if (isClassic) 24f * density else 40f * density
                } else {
                    22f * density
                }

        val thumbSize = if (isCapsule) trackThickness * thumbScale else baseThumbSize

        // V7: Reserve space for bubble inside the view
        // Total bubble width estimate: offsetFromThumb + tipDistance + hRadius
        // thumbSize/2 + 2 + 1.5*R + 1.2*R ~= thumbSize/2 + 2 + 2.7*11dp
        val bubbleSpace = if (hasBubble) 45f * density else 0f

        val pad = thumbSize / 2f + 4f * density

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        if (isVertical) {
            // Re-center track to the RIGHT if bubble is on the LEFT
            // startX = (w + bubbleSpace) / 2f // Old way
            startX = (w + bubbleSpace) / 2f // Shift right to make room for left bubble
            startY = h - pad
            endX = startX
            endY = pad
        } else {
            // Re-center track to the BOTTOM if bubble is on TOP
            startX = pad
            startY = (h + bubbleSpace) / 2f // Shift down to make room for top bubble
            endX = w - pad
            endY = startY
        }

        // 1. Track
        trackPaint.strokeWidth = trackThickness
        trackPaint.color = ColorUtils.setAlphaComponent(Color.LTGRAY, 80)
        canvas.drawLine(startX, startY, endX, endY, trackPaint)

        // 2. Ticks
        if (feedback == "Ticks" || feedback == "Both") {
            drawTicks(canvas, startX, startY, endX, endY, trackThickness, density)
        }

        // 3. Progress
        val progress = if (maxValue > minValue) (value - minValue) / (maxValue - minValue) else 0f
        val currX = startX + (endX - startX) * progress
        val currY = startY + (endY - startY) * progress

        progressPaint.strokeWidth = trackThickness
        progressPaint.color = color
        canvas.drawLine(startX, startY, currX, currY, progressPaint)

        // 4. Thumb
        if (isSquare) {
            // V6: Square thumb wider (doubled along track)
            val alongTrack = if (isClassic) 64f * density else 32f * density
            val perpendicular = if (isClassic) 24f * density else trackThickness * 2.0f

            val tW = if (isVertical) perpendicular else alongTrack
            val tH = if (isVertical) alongTrack else perpendicular
            drawSquareThumb(canvas, currX, currY, tW, tH, isVertical, density)
        } else {
            drawCircleThumb(canvas, currX, currY, thumbSize, density)
        }

        // 5. Feedback: Bubble
        if (isDragging && hasBubble) {
            val formatted =
                    when {
                        value < 10f -> String.format("%.2f", value)
                        value < 100f -> String.format("%.1f", value)
                        else -> String.format("%.0f", value)
                    }
            // val fullText = if (unit.isNotEmpty()) "$formatted $unit" else formatted
            val fullText = formatted

            drawTeardropBubble(canvas, currX, currY, thumbSize, fullText, density)
        }
    }

    private fun drawTeardropBubble(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            thumbSize: Float,
            text: String,
            density: Float
    ) {
        val isVertical = orientation == "Vertical"
        val bubbleRadius = 11f * density
        val offsetFromThumb = thumbSize / 2 + 2f * density

        val isLight = ColorUtils.calculateLuminance(this@PanelSliderView.color) > 0.52
        val contentColor = if (isLight) Color.BLACK else Color.WHITE

        val bubblePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = this@PanelSliderView.color
                    style = Paint.Style.FILL
                }

        val textPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = contentColor
                    textSize = 11f * density
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }

        val path = Path()
        val bx: Float
        val by: Float
        val rect: RectF

        val hRadius = bubbleRadius * 1.25f
        val vRadius = bubbleRadius * 0.95f
        val tipDistance = bubbleRadius * 1.5f

        if (isVertical) {
            // V6: Pointing RIGHT towards the vertical slider (Bubble on LEFT)
            bx = cx - offsetFromThumb - tipDistance
            by = cy
            rect = RectF(bx - hRadius, by - vRadius, bx + hRadius, by + vRadius)

            path.moveTo(cx - offsetFromThumb, cy) // The tip (on the right of the bubble)
            path.quadTo(cx - offsetFromThumb - hRadius * 0.5f, by - vRadius, bx, by - vRadius)
            path.arcTo(rect, 270f, -180f, false) // Reverse sweep
            path.quadTo(
                    cx - offsetFromThumb - hRadius * 0.5f,
                    by + vRadius,
                    cx - offsetFromThumb,
                    cy
            )
            path.close()
        } else {
            // Pointing DOWN towards the horizontal slider (Bubble on TOP)
            bx = cx
            by = cy - offsetFromThumb - tipDistance
            rect = RectF(bx - hRadius, by - vRadius, bx + hRadius, by + vRadius)

            path.moveTo(cx, cy - offsetFromThumb) // The tip
            path.quadTo(bx - hRadius, cy - offsetFromThumb - vRadius * 0.5f, bx - hRadius, by)
            path.arcTo(rect, 180f, 180f, false)
            path.quadTo(
                    bx + hRadius,
                    cy - offsetFromThumb - vRadius * 0.5f,
                    cx,
                    cy - offsetFromThumb
            )
            path.close()
        }

        bubblePaint.setShadowLayer(
                4f * density,
                0f,
                2f * density,
                ColorUtils.setAlphaComponent(Color.BLACK, 60)
        )
        canvas.drawPath(path, bubblePaint)
        bubblePaint.clearShadowLayer()

        val fontMetrics = textPaint.fontMetrics
        val textOffset = (fontMetrics.ascent + fontMetrics.descent) / 2
        canvas.drawText(text, bx, by - textOffset, textPaint)
    }

    private fun drawTicks(
            canvas: Canvas,
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            trackThickness: Float,
            density: Float
    ) {
        val range = maxValue - minValue
        if (range <= 0 || stepSize <= 0) return
        val rawSteps = (range / stepSize).toInt()
        if (rawSteps <= 0) return

        val currentNightMode =
                resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val tickColor = if (isDarkMode) Color.LTGRAY else Color.DKGRAY

        val tickPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = tickColor
                    alpha = 180
                    strokeCap = Paint.Cap.ROUND
                }

        val isVertical = orientation == "Vertical"
        val isCapsule = sliderStyle == "Capsule"

        if (isCapsule) {
            var drawStep = 1
            var count = rawSteps
            while (count > 10) {
                drawStep *= 2
                count = rawSteps / drawStep
            }

            tickPaint.strokeWidth = 1.5f * density
            val tickLen = trackThickness * 0.5f

            for (i in 0..rawSteps step drawStep) {
                val p = i.toFloat() / rawSteps
                val tx = startX + (endX - startX) * p
                val ty = startY + (endY - startY) * p
                if (isVertical) {
                    canvas.drawLine(tx - tickLen / 2, ty, tx + tickLen / 2, ty, tickPaint)
                } else {
                    canvas.drawLine(tx, ty - tickLen / 2, tx, ty + tickLen / 2, tickPaint)
                }
            }
        } else {
            val drawStep = if (rawSteps > 100) rawSteps / 50 else 1
            val offset = trackThickness / 2 + 4f * density

            for (i in 0..rawSteps step drawStep) {
                val p = i.toFloat() / rawSteps
                val tx = startX + (endX - startX) * p
                val ty = startY + (endY - startY) * p

                val is10 = (i % 10 == 0)
                val is5 = (i % 5 == 0)
                val lenDp = if (is10) 8f else if (is5) 6f else 4f
                val tickLen = lenDp * density
                tickPaint.strokeWidth = if (is10) 2f * density else 1f * density

                if (isVertical) {
                    // V6: Move ruler ticks to the RIGHT side if bubble is on the left
                    val x1 = tx + offset
                    val x2 = tx + offset + tickLen
                    canvas.drawLine(x1, ty, x2, ty, tickPaint)
                } else {
                    // bottom side
                    val y1 = ty + offset
                    val y2 = ty + offset + tickLen
                    canvas.drawLine(tx, y1, tx, y2, tickPaint)
                }
            }
        }
    }

    private fun drawCircleThumb(canvas: Canvas, cx: Float, cy: Float, size: Float, density: Float) {
        val radius = size / 2f
        thumbGlowPaint.maskFilter = BlurMaskFilter(6f * density, BlurMaskFilter.Blur.NORMAL)
        thumbGlowPaint.color = ColorUtils.setAlphaComponent(color, 120)
        canvas.drawCircle(cx, cy, radius + 2f * density, thumbGlowPaint)
        thumbPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, thumbPaint)
        thumbPaint.color = color
        canvas.drawCircle(cx, cy, radius * 0.7f, thumbPaint)
    }

    private fun drawSquareThumb(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            thumbW: Float,
            thumbH: Float,
            isVertical: Boolean,
            density: Float
    ) {
        val halfW = thumbW / 2f
        val halfH = thumbH / 2f
        val rect = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        thumbGlowPaint.maskFilter = BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL)
        thumbGlowPaint.color = ColorUtils.setAlphaComponent(color, 180)
        canvas.drawRoundRect(rect, 4f * density, 4f * density, thumbGlowPaint)
        thumbPaint.color = Color.WHITE
        canvas.drawRoundRect(rect, 4f * density, 4f * density, thumbPaint)
        val stripW = if (isVertical) rect.width() * 0.8f else 2f * density
        val stripH = if (isVertical) 2f * density else rect.height() * 0.8f
        val stripRect =
                RectF(cx - stripW / 2f, cy - stripH / 2f, cx + stripW / 2f, cy + stripH / 2f)
        thumbPaint.color = color
        canvas.drawRoundRect(stripRect, 100f, 100f, thumbPaint)
    }

    private fun updateValueFromTouch(tx: Float, ty: Float) {
        // Reuse logic from drawLinear for padding
        val density = resources.displayMetrics.density
        val isVertical = orientation == "Vertical"
        val isCapsule = sliderStyle == "Capsule"
        val isSquare = shape == "Square"
        val hasBubble = (feedback == "Bubble" || feedback == "Both")

        val trackThickness =
                if (isCapsule) (if (isVertical) width * 0.4f else height * 0.4f) else 4f * density
        val thumbScale = if (isSquare) 2.0f else 1.2f
        val isClassic = sliderStyle == "Classic"
        val baseThumbSize =
                if (isSquare) {
                    if (isClassic) 32f * density else 48f * density
                } else {
                    22f * density
                }
        val thumbSize = if (isCapsule) trackThickness * thumbScale else baseThumbSize
        val pad = thumbSize / 2f + 4f * density

        val progress =
                if (isVertical) {
                    ((height - pad - ty) / (height - 2 * pad)).coerceIn(0f, 1f)
                } else {
                    ((tx - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
                }
        applyValue(progress)
    }

    private fun applyValue(progress: Float) {
        val rawValue = minValue + progress * (maxValue - minValue)
        val steppedValue = Math.round(rawValue / stepSize) * stepSize

        if (steppedValue != value) {
            value = steppedValue
            onValueChange?.invoke(value)
        }
    }
}
