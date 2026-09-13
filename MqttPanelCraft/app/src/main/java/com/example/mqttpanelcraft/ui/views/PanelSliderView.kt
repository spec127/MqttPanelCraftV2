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

    private data class LinearGeom(
            val startX: Float,
            val startY: Float,
            val endX: Float,
            val endY: Float,
            val trackThickness: Float,
            val thumbSize: Float,
            val vScale: Float,
            val isSmall: Boolean
    )

    private fun computeLinearGeom(w: Float, h: Float, density: Float): LinearGeom {
        val isVertical = orientation == "Vertical"
        val isCapsule = sliderStyle == "Capsule"
        val isSquare = shape == "Square"
        val isClassic = sliderStyle == "Classic"
        val hasBubble = feedback == "Bubble" || feedback == "Both"
        val hasTicks = feedback == "Ticks" || feedback == "Both"
        val isSmall = Math.min(w, h) < 50f * density
        val baseFaderSize = 60f * density
        val shortSide = if (isVertical) w else h
        var vScale = ((shortSide / baseFaderSize).coerceAtLeast(0.15f)) * 0.85f
        val gap = 4f * density

        fun thickness(scale: Float) =
                if (isCapsule) (if (isVertical) w * 0.125f else h * 0.125f)
                else 4f * density * scale

        fun thumb(scale: Float, thick: Float): Float {
            val thumbScale = if (isSquare) 1.0f else 1.2f
            val baseThumb =
                    if (isSquare) {
                        if (isClassic) 24f * density * scale else 40f * density * scale
                    } else {
                        22f * density * scale
                    }
            return if (isCapsule) thick * thumbScale else baseThumb
        }

        var trackThickness = thickness(vScale)
        var thumbSize = thumb(vScale, trackThickness)
        var bubbleRadius = 12f * density * vScale
        var majorTickLen = 10f * density * vScale
        var labelSize = 10f * density * vScale
        fun textWidth() =
                Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = labelSize }.measureText("100.0")

        var leftNeed = 8f * density
        var rightNeed = 8f * density
        var topNeed = 8f * density
        var bottomNeed = 8f * density
        if (!isSmall) {
            if (isVertical) {
                leftNeed =
                        if (hasBubble) bubbleRadius * 2.5f + gap + 4f * density
                        else thumbSize / 2f + 4f * density
                rightNeed =
                        if (hasTicks) majorTickLen + gap + textWidth() + 4f * density
                        else thumbSize / 2f + 4f * density
            } else {
                topNeed =
                        if (hasBubble) bubbleRadius * 2.5f + gap + 4f * density
                        else thumbSize / 2f + 4f * density
                bottomNeed =
                        if (hasTicks) majorTickLen + gap + labelSize + 14f * density
                        else thumbSize / 2f + 4f * density
            }
        }

        if (isVertical) {
            val totalNeedW = leftNeed + trackThickness + rightNeed
            if (totalNeedW > w && totalNeedW > 0f) {
                vScale *= (w / totalNeedW)
                trackThickness = thickness(vScale)
                thumbSize = thumb(vScale, trackThickness)
                bubbleRadius = 12f * density * vScale
                majorTickLen = 10f * density * vScale
                labelSize = 10f * density * vScale
                leftNeed = if (hasBubble && !isSmall) bubbleRadius * 2.5f + gap + 4f * density else 8f * density
                rightNeed =
                        if (hasTicks && !isSmall) majorTickLen + gap + textWidth() + 4f * density
                        else 8f * density
            }
        } else {
            val totalNeedH = topNeed + trackThickness + bottomNeed
            if (totalNeedH > h && totalNeedH > 0f) {
                vScale *= (h / totalNeedH)
                trackThickness = thickness(vScale)
                thumbSize = thumb(vScale, trackThickness)
                bubbleRadius = 12f * density * vScale
                majorTickLen = 10f * density * vScale
                labelSize = 10f * density * vScale
                topNeed = if (hasBubble && !isSmall) bubbleRadius * 2.5f + gap + 4f * density else 8f * density
                bottomNeed =
                        if (hasTicks && !isSmall) majorTickLen + gap + labelSize + 14f * density
                        else 8f * density
            }
        }

        val bubbleHalf = if (hasBubble && !isSmall) bubbleRadius * 1.3f + 2f * density else 0f
        val endPad =
                Math.max(thumbSize / 2f + 4f * density * vScale, bubbleHalf).coerceAtLeast(2f * density)

        return if (isVertical) {
            val extra = ((w - (leftNeed + trackThickness + rightNeed)) / 2f).coerceAtLeast(0f)
            val x = extra + leftNeed + trackThickness / 2f
            LinearGeom(x, h - endPad, x, endPad, trackThickness, thumbSize, vScale, isSmall)
        } else {
            val extra = ((h - (topNeed + trackThickness + bottomNeed)) / 2f).coerceAtLeast(0f)
            val y = extra + topNeed + trackThickness / 2f
            LinearGeom(endPad, y, w - endPad, y, trackThickness, thumbSize, vScale, isSmall)
        }
    }

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
        val geom = computeLinearGeom(w, h, density)
        val vScale = geom.vScale
        val trackThickness = geom.trackThickness
        val thumbSize = geom.thumbSize
        val isSmall = geom.isSmall
        val startX = geom.startX
        val startY = geom.startY
        val endX = geom.endX
        val endY = geom.endY

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
        trackPaint.color =
                ColorUtils.setAlphaComponent(
                        Color.LTGRAY,
                        160
                ) // V21.6: Increased from 80 to 160 for better definition
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

        // 5. Feedback: Bubble (常駐顯示)
        if (hasBubble) {
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
        val bubbleRadius = (12f * density) * bubbleScale
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@PanelSliderView.color
            style = Paint.Style.FILL
            setShadowLayer(4f * density, 0f, 2f * density, Color.argb(60, 0, 0, 0))
        }

        setLayerType(LAYER_TYPE_SOFTWARE, null)
        val gap = 4f * density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = bubbleRadius * 1.1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val path = Path()
        if (isVertical) {
            val tipX = cx - thumbSize / 2 - gap
            val tipY = cy

            canvas.save()
            canvas.translate(tipX, tipY)

            val offset = bubbleRadius * 1.414f
            canvas.translate(-offset, 0f)
            canvas.rotate(-45f)

            val rect = RectF(-bubbleRadius, -bubbleRadius, bubbleRadius, bubbleRadius)
            val radii = floatArrayOf(bubbleRadius, bubbleRadius, bubbleRadius, bubbleRadius, 0f, 0f, bubbleRadius, bubbleRadius)
            path.addRoundRect(rect, radii, Path.Direction.CW)
            canvas.drawPath(path, bubblePaint)

            canvas.rotate(45f)
            val fontMetrics = textPaint.fontMetrics
            val textOffset = (fontMetrics.ascent + fontMetrics.descent) / 2
            canvas.drawText(text, 0f, -textOffset, textPaint)
            canvas.restore()
        } else {
            val tipX = cx
            val tipY = cy - thumbSize / 2 - gap

            canvas.save()
            canvas.translate(tipX, tipY)

            val offset = bubbleRadius * 1.414f
            canvas.translate(0f, -offset)
            canvas.rotate(45f)

            val rect = RectF(-bubbleRadius, -bubbleRadius, bubbleRadius, bubbleRadius)
            val radii = floatArrayOf(bubbleRadius, bubbleRadius, bubbleRadius, bubbleRadius, 0f, 0f, bubbleRadius, bubbleRadius)
            path.addRoundRect(rect, radii, Path.Direction.CW)
            canvas.drawPath(path, bubblePaint)

            canvas.rotate(-45f)
            val fontMetrics = textPaint.fontMetrics
            val textOffset = (fontMetrics.ascent + fontMetrics.descent) / 2
            canvas.drawText(text, 0f, -textOffset, textPaint)
            canvas.restore()
        }
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
        val isVertical = orientation == "Vertical"
        val count = 11 // 10 個主要刻度區間，跟 ScaleMeter 一樣
        val majorTickLen = (10f * density) * scale
        val minorTickLen = (5f * density) * scale
        val tickColor = Color.parseColor("#888888")

        val paintScale = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tickColor
            strokeWidth = 1.5f * density * scale
            strokeCap = Paint.Cap.ROUND
        }
        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#888888")
            textSize = (10f * density) * scale
        }

        if (isVertical) {
            val step = (endY - startY) / (count - 1)
            for (i in 0 until count) {
                val ty = startY + i * step
                val len = if (i % 2 == 0) majorTickLen else minorTickLen
                val tx = startX + trackThickness / 2 + 4f * density * scale
                canvas.drawLine(tx, ty, tx + len, ty, paintScale)
                if (i % 2 == 0) {
                    val p = i.toFloat() / (count - 1)
                    val valAtTick = minValue + p * (maxValue - minValue)
                    val label = if (maxValue - minValue >= 10f) String.format("%.0f", valAtTick) else String.format("%.1f", valAtTick)
                    paintText.textAlign = Paint.Align.LEFT
                    canvas.drawText(label, tx + len + 4f * density * scale, ty + (paintText.textSize / 3f), paintText)
                }
            }
        } else {
            val step = (endX - startX) / (count - 1)
            for (i in 0 until count) {
                val tx = startX + i * step
                val len = if (i % 2 == 0) majorTickLen else minorTickLen
                val ty = startY + trackThickness / 2 + 4f * density * scale
                canvas.drawLine(tx, ty, tx, ty + len, paintScale)
                if (i % 2 == 0) {
                    val p = i.toFloat() / (count - 1)
                    val valAtTick = minValue + p * (maxValue - minValue)
                    val label = if (maxValue - minValue >= 10f) String.format("%.0f", valAtTick) else String.format("%.1f", valAtTick)
                    paintText.textAlign = Paint.Align.CENTER
                    canvas.drawText(label, tx, ty + len + 4f * density * scale + paintText.textSize, paintText)
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
        val geom = computeLinearGeom(w, h, density)
        val progress =
                if (isVertical) {
                    val span = geom.startY - geom.endY
                    if (span == 0f) 0f else ((geom.startY - ty) / span).coerceIn(0f, 1f)
                } else {
                    val span = geom.endX - geom.startX
                    if (span == 0f) 0f else ((tx - geom.startX) / span).coerceIn(0f, 1f)
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
