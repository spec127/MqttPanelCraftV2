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

    var color: Int = Color.parseColor("#2196F3")
        set(v) {
            field = v
            invalidate()
        }

    var feedback: String = "None" // None, Ticks, Bubble, Both
        set(v) {
            field = v
            invalidate()
        }

    private var isDragging = false

    // V17.3: Helper for continuous sending
    private var repeatingRunnable: Runnable? = null

    fun startRepeatingTask(interval: Long, action: () -> Unit) {
        stopRepeatingTask()
        repeatingRunnable =
                object : Runnable {
                    override fun run() {
                        action()
                        postDelayed(this, interval)
                    }
                }
        post(repeatingRunnable)
    }

    fun stopRepeatingTask() {
        repeatingRunnable?.let { removeCallbacks(it) }
        repeatingRunnable = null
    }

    // V17.2: Throttling state must be stored in View to survive attachBehavior re-calls
    var lastSendTime: Long = 0L
    var isFirstMoveSinceActionUp: Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                onActionDown?.invoke() // V17.3
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
    var onActionDown: (() -> Unit)? = null // V17.3

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

    // V18.5: Added specific paint for track border
    private val trackBorderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                color = Color.parseColor("#333333") // Dark gray border by default
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
        val isClassic = sliderStyle == "Classic"
        val hasBubble = (feedback == "Bubble" || feedback == "Both")

        // Debug Log for Bubble
        if (isDragging) {
            // android.util.Log.d("SliderDraw", "Draw: Bubble=$hasBubble (feed=$feedback), w=$w,
            // h=$h")
        }

        // V12: Dynamic scaling factor based on shortest dimension
        // We use 60dp as the baseline fader size. If w (horizontal) or h (vertical) is smaller, we
        // scale down.
        val baseFaderSize = 60f * density
        // V18.2: Removed cap on shortSide to allow full proportional scaling as requested
        val shortSide = if (isVertical) w else h

        // Scale factor: if shortSide != 60dp, all elements scale proportionally
        // V15.3: Removed 1.0f cap to allow growth; kept 0.15f floor for extreme thinness
        // V18.5: Reduced scale by 15% as requested (0.85f factor)
        val vScale = ((shortSide / baseFaderSize).coerceAtLeast(0.15f)) * 0.85f

        val trackThickness =
                if (isCapsule) (if (isVertical) w * 0.125f else h * 0.125f)
                else 4f * density * vScale

        val thumbScale = if (isSquare) 1.0f else 1.2f
        val baseThumbSize =
                if (isSquare) {
                    if (isClassic) 24f * density * vScale else 40f * density * vScale
                } else {
                    22f * density * vScale
                }

        val thumbSize = if (isCapsule) trackThickness * thumbScale else baseThumbSize

        // V18.3: Detect thumbnail/small mode
        val isSmall = Math.min(w, h) < 50f * density

        // V18.5: Calculate Bubble Space Requirements to prevent clipping
        // Bubble Geometry:
        // offsetFromThumb = thumbSize / 2 + 8f * density * vScale
        // tipDistance = 20f * density * vScale
        // radius = 10f * density * vScale
        // totalExtension = offset + tipDistance + radius + padding
        val bubbleRadius = 10f * density * vScale
        val bubbleExtension =
                (thumbSize / 2f) +
                        (8f * density * vScale) +
                        (2f * bubbleRadius) +
                        (bubbleRadius * 1.3f) // 1.3 is hRadius factor

        // V17.0: Remove bubbleSpace - use overflow strategy instead
        // V18.3: Reduce padding for thumbnails to maximize visible track
        // V18.5: If Bubble is enabled, ensure pad covers the bubble's width (horizontal) or height
        // (vertical) at the ends
        // BUT bubble moves with thumb. The critical clipping happens perpendicular to track (shift)
        // AND parallel to track (at 0% and 100%).
        // Parallel clipping: Bubble width is ~ 2 * 1.3 * 10 * vScale = 26 * vScale.
        // Thumb width is ~ 22 * vScale.
        // So Bubble is slightly wider. We need slightly more pad if bubble is on.
        var pad = if (isSmall) 2f * density else (thumbSize / 2f + 4f * density * vScale)
        if (hasBubble && !isSmall) {
            val bubbleHalfWidth = bubbleRadius * 1.3f
            pad = Math.max(pad, bubbleHalfWidth + 2f * density)
        }

        // V18.1: Alignment Shift
        // User wants:
        // - Bubble side (Left/Top): 200% space
        // - Opposite side (Right/Bottom): 80% space
        // - Capsule opposite side: Minimal space
        // This means shifting the track AWAY from the bubble side.

        // V18.5: Dynamic Shift to contain bubble
        // We need 'bubbleExtension' space on the Top (Horizontal) or Left (Vertical).
        // Center is at w/2 (Vert) or h/2 (Horz).
        // Space available at center = w/2. We need bubbleExtension.
        // If we shift by S, space becomes availableHalf + S (if shifting away from bubble).
        // So: availableHalf + S >= bubbleExtension
        // S >= bubbleExtension - availableHalf

        val defaultShift =
                if (isSmall) {
                    0f
                } else if (isCapsule) {
                    thumbSize * 0.4f
                } else {
                    thumbSize * 0.25f
                }

        var shiftAmount = defaultShift

        if (hasBubble && !isSmall) {
            val availableHalf = if (isVertical) w / 2f else h / 2f
            val requiredShift = bubbleExtension - availableHalf + (4f * density) // +4dp margin
            if (requiredShift > shiftAmount) {
                shiftAmount = requiredShift
            }
        }

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        if (isVertical) {
            // Centered vertically -> Shift X to Right (Bubble is on Left)
            startX = (w / 2f) + shiftAmount
            startY = h - pad
            endX = startX
            endY = pad
        } else {
            // Centered horizontally -> Shift Y to Bottom (Bubble is on Top)
            startX = pad
            startY = (h / 2f) + shiftAmount
            endX = w - pad
            endY = startY
        }

        // V18.5: Draw Track Border
        if (!isSmall) {
            val borderW = (1.5f * density).toInt()
            trackBorderPaint.strokeWidth = trackThickness + (2 * borderW)
            val currentNightMode =
                    resources.configuration.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDark = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            trackBorderPaint.color = if (isDark) Color.WHITE else Color.parseColor("#333333")
            trackBorderPaint.alpha = 40 // Low opacity border

            canvas.drawLine(startX, startY, endX, endY, trackBorderPaint)

            // For Capsule, draw filled circle caps for border too
            if (isCapsule) {
                trackBorderPaint.style = Paint.Style.FILL
                canvas.drawCircle(startX, startY, (trackThickness / 2f) + borderW, trackBorderPaint)
                canvas.drawCircle(endX, endY, (trackThickness / 2f) + borderW, trackBorderPaint)
                trackBorderPaint.style = Paint.Style.STROKE
            }
        }

        // 1. Track
        trackPaint.strokeWidth = trackThickness
        trackPaint.color = ColorUtils.setAlphaComponent(Color.LTGRAY, 80)
        canvas.drawLine(startX, startY, endX, endY, trackPaint)

        // 2. Ticks & Labels
        // V18.3: Hide ticks on thumbnails for clarity
        if (!isSmall && (feedback == "Ticks" || feedback == "Both")) {
            drawTicks(canvas, startX, startY, endX, endY, trackThickness, density, vScale)
        }

        // 3. Progress
        val progress = if (maxValue > minValue) (value - minValue) / (maxValue - minValue) else 0f
        val currX = startX + (endX - startX) * progress
        val currY = startY + (endY - startY) * progress

        progressPaint.strokeWidth = trackThickness
        progressPaint.color = color
        // V9.2: Use BUTT cap for progress so it ends exactly at thumb center (strip line)
        progressPaint.strokeCap = Paint.Cap.BUTT

        // V9.4: Fill the starting cap for Capsule
        if (isCapsule) {
            progressPaint.style = Paint.Style.FILL
            canvas.drawCircle(startX, startY, trackThickness / 2f, progressPaint)
            progressPaint.style = Paint.Style.STROKE
        }

        canvas.drawLine(startX, startY, currX, currY, progressPaint)

        // 4. Thumb
        if (isSquare) {
            // V9.1: Separate dimensions for Classic and Capsule
            val alongTrack: Float
            val perpendicular: Float

            if (isClassic) {
                // Classic: Narrower and Thicker - V15: 18dp wide, 24dp thick
                alongTrack = 24f * density * vScale
                perpendicular = 18f * density * vScale
            } else {
                // Capsule: Flatter - V15: 16dp along track
                alongTrack = 16f * density * vScale
                perpendicular = trackThickness * 3.0f
            }

            val tW = if (isVertical) perpendicular else alongTrack
            val tH = if (isVertical) alongTrack else perpendicular
            drawSquareThumb(canvas, currX, currY, tW, tH, isVertical, density, vScale)
        } else {
            drawCircleThumb(canvas, currX, currY, thumbSize, density, vScale)
        }

        // 5. Feedback: Bubble
        if (isDragging && hasBubble) {
            val formatted =
                    when {
                        value < 10f -> String.format("%.2f", value)
                        value < 100f -> String.format("%.1f", value)
                        else -> String.format("%.0f", value)
                    }
            // V18.2: Allow bubble to scale proportionally with slider size (no cap)
            val bubbleScale = vScale

            // V18.1: Reduced offset (16dp -> 8dp) for tighter gap
            // val offsetFromThumb = thumbSize / 2 + 8f * density

            drawTeardropBubble(canvas, currX, currY, thumbSize, formatted, density, bubbleScale)
        }
    }

    private fun drawTeardropBubble(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            thumbSize: Float,
            text: String,
            density: Float,
            bubbleScale: Float
    ) {
        val isVertical = orientation == "Vertical"

        // V15.4: Bubble size capped at 1.0x. -> REMOVED cap in V18.2
        val bubbleRadius = 10f * density * bubbleScale

        // V18.2: Offset scales with bubble size (vScale)
        // thumbSize is already scaled
        val offsetFromThumb = thumbSize / 2 + 8f * density * bubbleScale

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
                    textSize = 12f * density * bubbleScale
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }

        // V15.5: Reduced size
        val glowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = this@PanelSliderView.color
                    style = Paint.Style.STROKE
                    strokeWidth = 1.4f * density * bubbleScale
                    maskFilter =
                            BlurMaskFilter(2.8f * density * bubbleScale, BlurMaskFilter.Blur.NORMAL)
                }

        val hRadius = bubbleRadius * 1.3f
        val vRadius = bubbleRadius * 1.0f
        val tipDistance = bubbleRadius * 2.0f

        val path = Path()
        val bx: Float
        val by: Float
        val rect: RectF

        if (isVertical) {
            bx = cx - offsetFromThumb - tipDistance
            by = cy
            rect = RectF(bx - hRadius, by - vRadius, bx + hRadius, by + vRadius)
            path.moveTo(cx - offsetFromThumb, cy)
            path.quadTo(cx - offsetFromThumb - hRadius * 0.5f, by - vRadius, bx, by - vRadius)
            path.arcTo(rect, 270f, -180f, false)
            path.quadTo(
                    cx - offsetFromThumb - hRadius * 0.5f,
                    by + vRadius,
                    cx - offsetFromThumb,
                    cy
            )
            path.close()
        } else {
            bx = cx
            by = cy - offsetFromThumb - tipDistance
            rect = RectF(bx - hRadius, by - vRadius, bx + hRadius, by + vRadius)
            path.moveTo(cx, cy - offsetFromThumb)
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

        canvas.drawPath(path, bubblePaint)
        canvas.drawPath(path, glowPaint)

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
            density: Float,
            scale: Float
    ) {
        val totalLength =
                if (orientation == "Vertical") Math.abs(startY - endY) else Math.abs(endX - startX)

        val rawN = (totalLength / (20f * density)).toInt()
        val tickCount = (rawN / 5 * 5).coerceIn(5, 30)

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

        // V18.1: Reduced gap between track and ticks (4f -> 2f)
        val offset = if (isCapsule) 0f else (trackThickness / 2 + 2f * density * scale)

        for (i in 0..tickCount) {
            val p = i.toFloat() / tickCount
            val tx = startX + (endX - startX) * p
            val ty = startY + (endY - startY) * p

            val isBig = (i % 10 == 0)
            val isSmall = (i % 5 == 0 && !isBig)

            // V18.1: Thinner and shorter ticks
            // Length: Big 15->10, Small 9->6, Tiny 4->3
            var tickLen = (if (isBig) 10f else if (isSmall) 6f else 3f) * density * scale
            if (isCapsule) {
                tickLen = Math.min(tickLen, trackThickness * 0.8f)
            }

            // Width: Big 2.5->1.5, Small 1.5->1.0, Tiny 0.8->0.6
            tickPaint.strokeWidth =
                    (if (isBig) 1.5f else if (isSmall) 1.0f else 0.6f) * density * scale
            tickPaint.alpha = if (isBig) 255 else if (isSmall) 180 else 100

            if (isVertical) {
                if (isCapsule) {
                    canvas.drawLine(tx - tickLen / 2, ty, tx + tickLen / 2, ty, tickPaint)
                } else {
                    canvas.drawLine(tx + offset, ty, tx + offset + tickLen, ty, tickPaint)
                }
            } else {
                if (isCapsule) {
                    canvas.drawLine(tx, ty - tickLen / 2, tx, ty + tickLen / 2, tickPaint)
                } else {
                    canvas.drawLine(tx, ty + offset, tx, ty + offset + tickLen, tickPaint)
                }
            }
        }
    }

    private fun drawCircleThumb(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            size: Float,
            density: Float,
            scale: Float
    ) {
        val radius = size / 2f
        // V15.6: Reduced glow (6f -> 4.2f)
        thumbGlowPaint.maskFilter =
                BlurMaskFilter(4.2f * density * scale, BlurMaskFilter.Blur.NORMAL)
        thumbGlowPaint.color = ColorUtils.setAlphaComponent(color, 120)
        canvas.drawCircle(
                cx,
                cy,
                radius + 1.4f * density * scale,
                thumbGlowPaint
        ) // V15.6: 2f -> 1.4f
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
            density: Float,
            scale: Float
    ) {
        val halfW = thumbW / 2f
        val halfH = thumbH / 2f
        val rect = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        // V15.6: Reduced glow (4f -> 2.8f)
        thumbGlowPaint.maskFilter =
                BlurMaskFilter(2.8f * density * scale, BlurMaskFilter.Blur.NORMAL)
        thumbGlowPaint.color = ColorUtils.setAlphaComponent(color, 180)
        canvas.drawRoundRect(
                rect,
                2.8f * density * scale,
                2.8f * density * scale,
                thumbGlowPaint
        ) // V15.6: 4f -> 2.8f
        thumbPaint.color = Color.WHITE
        canvas.drawRoundRect(rect, 4f * density * scale, 4f * density * scale, thumbPaint)
        val stripW = if (isVertical) rect.width() * 0.8f else 2f * density * scale
        val stripH = if (isVertical) 2f * density * scale else rect.height() * 0.8f
        val stripRect =
                RectF(cx - stripW / 2f, cy - stripH / 2f, cx + stripW / 2f, cy + stripH / 2f)
        thumbPaint.color = color
        canvas.drawRoundRect(stripRect, 100f, 100f, thumbPaint)
    }

    private fun updateValueFromTouch(tx: Float, ty: Float) {
        val density = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val isVertical = orientation == "Vertical"
        val isCapsule = sliderStyle == "Capsule"
        val isSquare = shape == "Square"
        val isClassic = sliderStyle == "Classic"

        // V16.0: Scaling must EXACTLY match drawLinear
        val baseFaderSize = 60f * density
        val shortSide = if (isVertical) w else h
        // V18.6: FIX: Apply the same 0.85f scaling factor as in onDraw
        val vScale = ((shortSide / baseFaderSize).coerceAtLeast(0.15f)) * 0.85f

        val trackThickness =
                if (isCapsule) (if (isVertical) w * 0.125f else h * 0.125f)
                else 4f * density * vScale

        val thumbScale = if (isSquare) 1.0f else 1.2f
        val baseThumbSize =
                if (isSquare) {
                    if (isClassic) 24f * density * vScale else 40f * density * vScale
                } else {
                    22f * density * vScale
                }

        val thumbSize = if (isCapsule) trackThickness * thumbScale else baseThumbSize
        val pad = thumbSize / 2f + 4f * density * vScale

        // V16.0: Touch coordinates must match the offset track exactly
        val progress =
                if (isVertical) {
                    ((h - pad - ty) / (h - 2 * pad)).coerceIn(0f, 1f)
                } else {
                    ((tx - pad) / (w - 2 * pad)).coerceIn(0f, 1f)
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
