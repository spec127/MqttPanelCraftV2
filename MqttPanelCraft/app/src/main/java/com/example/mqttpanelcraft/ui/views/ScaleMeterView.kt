package com.example.mqttpanelcraft.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ScaleMeterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Style { SOLID, SEGMENTED, THERMOMETER }
    enum class Orientation { VERTICAL, HORIZONTAL }
    enum class ThresholdEffect { GRADIENT, TOP_TIP, VALUE_CHANGE }

    var style: Style = Style.SOLID
        set(value) { field = value; invalidate() }
    
    var meterOrientation: Orientation = Orientation.VERTICAL
        set(value) { field = value; requestLayout(); invalidate() }

    var showValue: Boolean = false
        set(value) { field = value; invalidate() }

    var showTicks: Boolean = false
        set(value) { field = value; invalidate() }

    var showBubble: Boolean = false
        set(value) { field = value; invalidate() }

    var isEditMode: Boolean = false
        set(value) {
            field = value
            if (value) {
                animator?.cancel()
                currentAnimValue = this.value
                invalidate()
            }
        }

    var unit: String = "%"
        set(value) { field = value; invalidate() }

    var minValue: Float = 0f
        set(value) { field = value; invalidate() }
        
    var maxValue: Float = 100f
        set(value) { field = value; invalidate() }
        
    var value: Float = 0f
        set(v) { 
            val target = v.coerceIn(minValue, maxValue)
            if (field != target) {
                animateValue(field, target)
                field = target
            }
        }

    var themeColor: Int = Color.parseColor("#FF9800")
        set(value) { 
            field = value
            trackColor = Color.argb(76, Color.red(value), Color.green(value), Color.blue(value))
            invalidate() 
        }
        
    private var trackColor: Int = Color.argb(76, 255, 152, 0) // 預設配合 #FF9800 的半透明底色

    var thresholdMode: Boolean = false
        set(value) { field = value; invalidate() }

    var thresholdEffect: ThresholdEffect = ThresholdEffect.VALUE_CHANGE
        set(value) { field = value; invalidate() }

    // List of Pair<ThresholdValue, Color>
    var thresholds: List<Pair<Float, Int>> = emptyList()
        set(value) { 
            field = value.sortedBy { it.first }
            invalidate() 
        }

    private var currentAnimValue: Float = 0f
    private var animator: ValueAnimator? = null
    
    private val paintTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintScale = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        currentAnimValue = value
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

    private fun animateValue(from: Float, to: Float) {
        animator?.cancel()
        if (isEditMode || !isAttachedToWindow) {
            currentAnimValue = to
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = 300
            addUpdateListener { 
                currentAnimValue = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    private fun getTargetThresholdColor(): Int {
        if (thresholds.isEmpty()) return themeColor
        for (t in thresholds) {
            if (currentAnimValue <= t.first) {
                return t.second
            }
        }
        return thresholds.last().second
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        
        val shortSide = if (meterOrientation == Orientation.VERTICAL) w else h
        // 參考 Slider，以短邊 60dp 為基準進行縮放，最低限度 0.4 倍避免太小，並微調 0.9 倍率
        var vScale = ((shortSide / (60f * density)).coerceAtLeast(0.4f)) * 0.9f
        
        val gap = 4f * density
        var trackThickness = (20f * density * vScale).coerceAtMost(shortSide * 0.4f)
        // 讓氣泡稍微小一點，更精緻
        var bubbleRadius = (12f * density) * vScale
        var majorTickLen = (8f * density) * vScale
        
        paintText.textSize = 10f * density * vScale
        val maxTextWidth = paintText.measureText("100.0") // 估算最大數字寬度
        
        // 所需的安全邊界：水滴氣泡佔用半徑的 2.5 倍
        var leftNeed = if (meterOrientation == Orientation.VERTICAL && showBubble) bubbleRadius * 2.5f + gap + 4f * density else 8f * density
        var rightNeed = if (meterOrientation == Orientation.VERTICAL && showTicks) majorTickLen + gap + maxTextWidth + 4f * density else 8f * density
        var topNeed = if (meterOrientation == Orientation.HORIZONTAL && showBubble) bubbleRadius * 2.5f + gap + 4f * density else 8f * density
        var bottomNeed = if (meterOrientation == Orientation.HORIZONTAL && showTicks) majorTickLen + gap + paintText.textSize + 14f * density else 8f * density
        
        // 防裁切：如果所需總寬度超過畫布，強制等比壓縮 vScale
        if (meterOrientation == Orientation.VERTICAL) {
            val totalNeedW = leftNeed + trackThickness + rightNeed
            if (totalNeedW > w) {
                vScale *= (w / totalNeedW)
                trackThickness = (20f * density * vScale).coerceAtMost(shortSide * 0.4f)
                bubbleRadius = (12f * density) * vScale
                majorTickLen = (8f * density) * vScale
                paintText.textSize = 10f * density * vScale
                leftNeed = if (showBubble) bubbleRadius * 2.5f + gap + 4f * density else 8f * density
                rightNeed = if (showTicks) majorTickLen + gap + paintText.measureText("100.0") + 4f * density else 8f * density
            }
        } else {
            val totalNeedH = topNeed + trackThickness + bottomNeed
            if (totalNeedH > h) {
                vScale *= (h / totalNeedH)
                trackThickness = (20f * density * vScale).coerceAtMost(shortSide * 0.4f)
                bubbleRadius = (12f * density) * vScale
                majorTickLen = (8f * density) * vScale
                paintText.textSize = 10f * density * vScale
                topNeed = if (showBubble) bubbleRadius * 2.5f + gap + 4f * density else 8f * density
                bottomNeed = if (showTicks) majorTickLen + gap + paintText.textSize + 14f * density else 8f * density
            }
        }

        var digitalScreenSize = 0f
        var panelMarginBottom = 16f * density
        var panelMarginTop = (6f * density) * vScale // 再度縮短面板與主體的距離
        
        var trackLeft: Float; var trackTop: Float; var trackRight: Float; var trackBottom: Float
        
        if (meterOrientation == Orientation.VERTICAL) {
            // 動態置中：把剩餘的寬度平分，而不是絕對的 w/2，徹底根絕左右被裁切的風險
            val cx = leftNeed + (w - leftNeed - rightNeed) / 2f
            trackLeft = cx - trackThickness / 2f
            trackRight = cx + trackThickness / 2f
            trackTop = 24f * density
            trackBottom = h - 24f * density
            
            if (showValue) {
                digitalScreenSize = (36f * density) * vScale // 縮小面板高度，解決臃腫
                val panelTop = h - panelMarginBottom - digitalScreenSize
                val extraBubblePad = if (showBubble) bubbleRadius * 1.5f else 0f
                trackBottom = panelTop - panelMarginTop - extraBubblePad
            }
        } else {
            val cy = topNeed + (h - topNeed - bottomNeed) / 2f
            trackTop = cy - trackThickness / 2f
            trackBottom = cy + trackThickness / 2f
            trackLeft = 24f * density
            trackRight = w - 24f * density
            
            if (showValue) {
                digitalScreenSize = (36f * density) * vScale
                val panelLeft = w - panelMarginBottom - digitalScreenSize
                val extraBubblePad = if (showBubble) bubbleRadius * 1.5f else 0f
                trackRight = panelLeft - panelMarginTop - extraBubblePad
            }
        }

        val range = maxValue - minValue
        val pct = if (range > 0) ((currentAnimValue - minValue) / range).coerceIn(0f, 1f) else 0f
        
        paintTrack.color = trackColor
        
        // Handle Threshold Color
        val targetColor = if (thresholdMode) getTargetThresholdColor() else themeColor
        setupFillPaint(trackLeft, trackTop, trackRight, trackBottom, targetColor, pct)

        // 1. Draw Base & Fill
        when (style) {
            Style.THERMOMETER -> drawThermometer(canvas, trackLeft, trackTop, trackRight, trackBottom, pct)
            Style.SEGMENTED -> drawSegmented(canvas, trackLeft, trackTop, trackRight, trackBottom, pct)
            else -> drawSolid(canvas, trackLeft, trackTop, trackRight, trackBottom, pct)
        }

        // 2. Overlay Feedback (Sharper Ticks & Sidebar Bubble)
        if (showTicks) {
            drawSharperTicks(canvas, trackLeft, trackTop, trackRight, trackBottom, vScale)
        }
        if (showBubble) {
            drawSidebarBubble(canvas, trackLeft, trackTop, trackRight, trackBottom, pct, targetColor, vScale)
        }
        
        // 3. Digital Screen
        if (showValue) {
            drawDigitalScreen(canvas, w, h, digitalScreenSize)
        }
    }

    private fun setupFillPaint(l: Float, t: Float, r: Float, b: Float, targetColor: Int, pct: Float) {
        paintFill.shader = null
        if (!thresholdMode) {
            paintFill.color = themeColor
            return
        }

        when (thresholdEffect) {
            ThresholdEffect.GRADIENT -> {
                val range = maxValue - minValue
                val colors = mutableListOf<Int>()
                val positions = mutableListOf<Float>()
                val tolerance = 0.10f // 10% ratio (總計 20% 漸變區間，符合使用者期望的 40~60)
                
                if (range > 0f && thresholds.isNotEmpty()) {
                    var prevColor = thresholds[0].second
                    colors.add(prevColor)
                    positions.add(0f)
                    
                    for (i in 0 until thresholds.size - 1) {
                        val currentTh = thresholds[i]
                        val nextTh = thresholds[i + 1]
                        
                        val targetColor = nextTh.second
                        val centerRatio = ((currentTh.first - minValue) / range).coerceIn(0f, 1f)
                        
                        // 動態計算漸變起點與終點 (該區段長度的 20%，最小為整體的 2%)
                        val prevThValue = if (i == 0) minValue else thresholds[i - 1].first
                        val leftLen = currentTh.first - prevThValue
                        val rightLen = nextTh.first - currentTh.first
                        
                        val leftTol = Math.max((leftLen / range) * 0.20f, 0.02f)
                        val rightTol = Math.max((rightLen / range) * 0.20f, 0.02f)
                        
                        val startRatio = (centerRatio - leftTol).coerceIn(0f, 1f)
                        val endRatio = (centerRatio + rightTol).coerceIn(0f, 1f)
                        
                        // 維持上一個顏色到 startRatio
                        colors.add(prevColor)
                        positions.add(Math.max(positions.last() + 0.0001f, startRatio))
                        
                        // 在 endRatio 完成到目標顏色的漸變
                        colors.add(targetColor)
                        positions.add(Math.max(positions.last() + 0.0001f, endRatio))
                        
                        prevColor = targetColor
                    }
                    
                    // 最後一段維持到結束
                    colors.add(prevColor)
                    positions.add(Math.max(positions.last() + 0.0001f, 1f))
                } else {
                    colors.add(themeColor)
                    positions.add(0f)
                    colors.add(themeColor)
                    positions.add(1f)
                }

                val shader = if (meterOrientation == Orientation.VERTICAL) {
                    LinearGradient(0f, b, 0f, t, colors.toIntArray(), positions.toFloatArray(), Shader.TileMode.CLAMP)
                } else {
                    LinearGradient(l, 0f, r, 0f, colors.toIntArray(), positions.toFloatArray(), Shader.TileMode.CLAMP)
                }
                paintFill.shader = shader
            }
            ThresholdEffect.VALUE_CHANGE -> {
                paintFill.color = targetColor
            }
            ThresholdEffect.TOP_TIP -> {
                // For TOP_TIP, we might need to draw a separate layer, but for now we'll use a sharp gradient
                val colors = intArrayOf(themeColor, themeColor, targetColor)
                val positions = floatArrayOf(0f, 0.85f, 1f)
                val shader = if (meterOrientation == Orientation.VERTICAL) {
                    LinearGradient(0f, b, 0f, t, colors, positions, Shader.TileMode.CLAMP)
                } else {
                    LinearGradient(l, 0f, r, 0f, colors, positions, Shader.TileMode.CLAMP)
                }
                paintFill.shader = shader
            }
        }
    }

    private fun drawSolid(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, pct: Float) {
        val radius = 12f
        canvas.drawRoundRect(l, t, r, b, radius, radius, paintTrack)
        if (meterOrientation == Orientation.VERTICAL) {
            val fillTop = b - (b - t) * pct
            canvas.drawRoundRect(l, fillTop, r, b, radius, radius, paintFill)
        } else {
            val fillRight = l + (r - l) * pct
            canvas.drawRoundRect(l, t, fillRight, b, radius, radius, paintFill)
        }
    }

    private fun drawSegmented(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, pct: Float) {
        val segments = 12
        val gap = 4f
        val segHeight = (b - t - gap * (segments - 1)) / segments
        val segWidth = (r - l - gap * (segments - 1)) / segments
        // 修復精度誤差：使用 Math.round 確保到達 99.9% 也能完全點亮最上面一格
        val activeCount = Math.round(pct * segments).toInt()

        for (i in 0 until segments) {
            if (meterOrientation == Orientation.VERTICAL) {
                val segTop = b - (i + 1) * segHeight - i * gap
                val segBottom = b - i * segHeight - i * gap
                val isFilled = i < activeCount
                // 捨棄圓角改為平滑方塊，避免視覺錯覺導致感覺沒有頂到邊緣
                canvas.drawRect(l, segTop, r, segBottom, if (isFilled) paintFill else paintTrack)
            } else {
                val segLeft = l + i * segWidth + i * gap
                val segRight = l + (i + 1) * segWidth + i * gap
                val isFilled = i < activeCount
                canvas.drawRect(segLeft, t, segRight, b, if (isFilled) paintFill else paintTrack)
            }
        }
    }

    private fun drawThermometer(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, pct: Float) {
        val bulbRadius = if (meterOrientation == Orientation.VERTICAL) (r - l) * 0.6f else (b - t) * 0.6f
        
        if (meterOrientation == Orientation.VERTICAL) {
            val bulbX = l + (r - l) / 2f
            val bulbY = b - bulbRadius
            val tubeLeft = bulbX - bulbRadius / 2.5f
            val tubeRight = bulbX + bulbRadius / 2.5f
            // 修復柱狀沒有到頂的問題：tubeTop 應該直接是 t，而不是加上球的半徑
            val tubeTop = t
            val tubeBottom = bulbY
            
            canvas.drawCircle(bulbX, bulbY, bulbRadius, paintTrack)
            canvas.drawRoundRect(tubeLeft, tubeTop, tubeRight, tubeBottom, (tubeRight-tubeLeft)/2f, (tubeRight-tubeLeft)/2f, paintTrack)

            
            canvas.drawCircle(bulbX, bulbY, bulbRadius - 4f, paintFill)
            val fillTop = tubeBottom - (tubeBottom - tubeTop) * pct
            canvas.drawRoundRect(tubeLeft + 4f, fillTop, tubeRight - 4f, tubeBottom, (tubeRight-tubeLeft)/2f, (tubeRight-tubeLeft)/2f, paintFill)
        } else {
            val bulbY = t + (b - t) / 2f
            val bulbX = l + bulbRadius
            val tubeTop = bulbY - bulbRadius / 2.5f
            val tubeBottom = bulbY + bulbRadius / 2.5f
            // 修復橫向柱狀沒有到頂的問題：tubeRight 應該直接是 r
            val tubeLeft = bulbX
            val tubeRight = r
            
            canvas.drawCircle(bulbX, bulbY, bulbRadius, paintTrack)
            canvas.drawRoundRect(tubeLeft, tubeTop, tubeRight, tubeBottom, (tubeBottom-tubeTop)/2f, (tubeBottom-tubeTop)/2f, paintTrack)
            
            canvas.drawCircle(bulbX, bulbY, bulbRadius - 4f, paintFill)
            val fillRight = tubeLeft + (tubeRight - tubeLeft) * pct
            canvas.drawRoundRect(tubeLeft, tubeTop + 4f, fillRight, tubeBottom - 4f, (tubeBottom-tubeTop)/2f, (tubeBottom-tubeTop)/2f, paintFill)
        }
    }

    private fun drawSharperTicks(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, vScale: Float) {
        val density = resources.displayMetrics.density
        val count = 11
        val majorTickLen = (8f * density) * vScale
        val minorTickLen = (4f * density) * vScale
        paintScale.color = Color.parseColor("#70000000")
        paintScale.strokeWidth = 1.5f * density
        
        paintText.color = Color.parseColor("#777777")
        paintText.textSize = (10f * density) * vScale
        
        if (meterOrientation == Orientation.VERTICAL) {
            val step = (b - t) / (count - 1)
            for (i in 0 until count) {
                val ty = b - i * step
                val len = if (i % 5 == 0) majorTickLen else minorTickLen
                // 參考 Slider，只在右側繪製刻度，避免與左側氣泡衝突
                val tx = r + 4f * density
                canvas.drawLine(tx, ty, tx + len, ty, paintScale)
                if (i % 5 == 0) {
                    val v = minValue + (maxValue - minValue) * (i.toFloat() / (count - 1))
                    val label = v.toInt().toString()
                    paintText.textAlign = Paint.Align.LEFT
                    canvas.drawText(label, tx + len + 4f * density, ty + (paintText.textSize / 3f), paintText)
                }
            }
        } else {
            val step = (r - l) / (count - 1)
            for (i in 0 until count) {
                val tx = l + i * step
                val len = if (i % 5 == 0) majorTickLen else minorTickLen
                // 參考 Slider，只在下方繪製刻度，避免與上方氣泡衝突
                val ty = b + 4f * density
                canvas.drawLine(tx, ty, tx, ty + len, paintScale)
                if (i % 5 == 0) {
                    val v = minValue + (maxValue - minValue) * (i.toFloat() / (count - 1))
                    val label = v.toInt().toString()
                    paintText.textAlign = Paint.Align.CENTER
                    val labelTx = if (i == 0) tx + (5f * density) else if (i == count - 1) tx - (5f * density) else tx
                    canvas.drawText(label, labelTx, ty + len + 4f * density + paintText.textSize, paintText)
                }
            }
        }
    }

    private fun drawSidebarBubble(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, pct: Float, color: Int, vScale: Float) {
        val density = resources.displayMetrics.density
        // 使用傳入的 bubbleRadius，並確保它是 vScale 過的
        val bubbleRadius = (12f * density) * vScale
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
            setShadowLayer(4f * density, 0f, 2f * density, Color.argb(60, 0, 0, 0))
        }
        
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        val gap = 4f * density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = bubbleRadius * 1.1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        
        val label = String.format("%.0f", minValue + (maxValue - minValue) * pct)
        val path = Path()
        
        if (meterOrientation == Orientation.VERTICAL) {
            val by = b - (b - t) * pct
            val tipX = l - gap
            val tipY = by
            
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
            canvas.drawText(label, 0f, textPaint.textSize / 3f, textPaint)
            canvas.restore()
            
        } else {
            val bx = l + (r - l) * pct
            val tipX = bx
            val tipY = t - gap
            
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
            canvas.drawText(label, 0f, textPaint.textSize / 3f, textPaint)
            canvas.restore()
        }
    }

    private fun drawDigitalScreen(canvas: Canvas, w: Float, h: Float, size: Float) {
        val density = resources.displayMetrics.density
        // 數字面板動態中心不應該強制在 w/2，而是跟隨 trackLeft / trackRight 計算出的 cx/cy
        // 但由於這裡無法直接拿到 cx，我們重新計算。但在上面的邏輯中 cx 被放在區域變數了。
        // 我們用最簡單的置中：w / 2f
        val cx = w / 2f
        val cy = h / 2f
        
        // 面板強制對齊元件中心，寬高根據 size 動態變化
        val rect = if (meterOrientation == Orientation.VERTICAL) {
            val panelW = Math.min(w * 0.8f, 70f * density) // 讓面板稍微細長一點，不臃腫
            val marginBottom = 16f * density
            RectF(cx - panelW / 2f, h - marginBottom - size, cx + panelW / 2f, h - marginBottom)
        } else {
            val panelH = Math.min(h * 0.8f, 70f * density)
            val marginRight = 24f * density // 往左微調
            val offsetY = panelH * 0.2f // 往下微調
            RectF(w - marginRight - size * 1.4f, cy - panelH / 2f + offsetY, w - marginRight, cy + panelH / 2f + offsetY)
        }
        
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
        
        paintText.color = Color.parseColor("#1E293B")
        // 加大數字佔比，把原本 0.4 提升到 0.55，消滅內部贅餘空白
        paintText.textSize = size * 0.55f 
        val valStr = String.format("%.1f", currentAnimValue)
        val text = if (unit.isNotEmpty()) "$valStr $unit" else valStr
        
        // 單位自適應防裁切：如果文字過長，動態縮小字體直到剛好能塞進面板，保留 10% 的安全 Padding
        val availableWidth = rect.width() * 0.9f
        while (paintText.measureText(text) > availableWidth && paintText.textSize > 8f * density) {
            paintText.textSize -= 0.5f * density
        }
        
        paintText.textAlign = Paint.Align.CENTER
        canvas.drawText(text, rect.centerX(), rect.centerY() + (paintText.textSize / 3f), paintText)
    }
}
