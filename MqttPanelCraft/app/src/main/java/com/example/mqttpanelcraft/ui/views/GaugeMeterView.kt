package com.example.mqttpanelcraft.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class GaugeMeterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Style { NEEDLE, SEGMENTED }
    enum class TrackAngle { ARC_120, ARC_180, ARC_270 }
    enum class TickMode { NONE, TICKS, LIMITS, ALL }
    
    // 與 ScaleMeter 共用的變色邏輯列舉
    enum class ThresholdEffect { GRADIENT, TOP_TIP, VALUE_CHANGE }

    // --- 外觀參數 ---
    var meterStyle: Style = Style.NEEDLE
        set(value) { field = value; invalidate() }

    var trackAngle: TrackAngle = TrackAngle.ARC_270
        set(value) { field = value; requestLayout(); invalidate() }

    var tickMode: TickMode = TickMode.LIMITS
        set(value) { field = value; invalidate() }

    // --- 資料與數值 ---
    var unit: String = ""
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

    // --- 變色邏輯 (無縫繼承 ScaleMeter) ---
    var themeColor: Int = Color.parseColor("#FF9800")
        set(value) { field = value; invalidate() }

    var thresholdMode: Boolean = false
        set(value) { field = value; invalidate() }

    var thresholdEffect: ThresholdEffect = ThresholdEffect.VALUE_CHANGE
        set(value) { field = value; invalidate() }

    var thresholds: List<Pair<Float, Int>> = emptyList()
        set(value) {
            field = value.sortedBy { it.first }
            invalidate()
        }

    // --- 內部狀態與繪製工具 ---
    private var currentAnimValue: Float = 0f
    private var animator: ValueAnimator? = null

    // 畫筆 (Paints)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#1AFFFFFF") // 暗灰色，帶點半透明
    }
    
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
    }
    
    private val minMaxTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#94A3B8") // 加深為更清晰的灰色
    }

    init {
        currentAnimValue = value
        // 開啟硬體加速或 LayerType 來支援 ShadowLayer
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun animateValue(from: Float, to: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = 300
            addUpdateListener {
                currentAnimValue = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // 計算閾值顏色 (Solid)
    private fun getTargetThresholdColor(): Int {
        if (thresholds.isEmpty() || !thresholdMode) return themeColor
        for (t in thresholds) {
            if (currentAnimValue <= t.first) {
                return t.second
            }
        }
        return thresholds.last().second
    }

    // 計算當前值在 0f ~ 1f 之間的進度
    private fun getProgressRatio(): Float {
        val range = maxValue - minValue
        if (range <= 0) return 0f
        return ((currentAnimValue - minValue) / range).coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        // 以 100dp 作為基準計算 vScale
        val vScale = (minOf(w, h) / (100f * density)).coerceAtLeast(0.4f)
        
        // 解析角度設定
        val startAngle: Float
        val sweepAngle: Float
        when (trackAngle) {
            TrackAngle.ARC_270 -> {
                startAngle = 135f
                sweepAngle = 270f
            }
            TrackAngle.ARC_180 -> {
                startAngle = 180f
                sweepAngle = 180f
            }
            TrackAngle.ARC_120 -> {
                startAngle = 210f
                sweepAngle = 120f
            }
        }

        // --- 1. 計算幾何圓心與半徑 ---
        val trackThickness = if (meterStyle == Style.SEGMENTED) 12f * density * vScale else 6f * density * vScale // 陣列模式軌道加倍
        trackPaint.strokeWidth = trackThickness
        progressPaint.strokeWidth = trackThickness
        
        val maxTextSize = 14f * density * vScale // 中央數值大小適中
        val padding = trackThickness / 2f + 12f * density * vScale // 恢復正常的安全邊距，避免把半徑擠成負數
        
        val cx = w / 2f
        var radius = minOf(w / 2f, h / 2f) - padding
        if (radius < 10f * density * vScale) radius = 10f * density * vScale // 防止因畫布過小導致半徑崩潰
        
        var cy = h / 2f
        when (trackAngle) {
            TrackAngle.ARC_180 -> {
                val totalH = radius + maxTextSize * 1.5f
                val topY = (h - totalH) / 2f
                cy = topY + radius
            }
            TrackAngle.ARC_120 -> {
                val totalH = radius * 0.5f + maxTextSize * 1.5f
                val topY = (h - totalH) / 2f
                cy = topY + radius
            }
            TrackAngle.ARC_270 -> cy = h / 2f
        }
        
        val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        
        // --- 2. 設定顏色與 SweepGradient ---
        val currentColor = getTargetThresholdColor()
        
        if (thresholdMode && thresholdEffect == ThresholdEffect.GRADIENT && thresholds.isNotEmpty()) {
            val colors = mutableListOf<Int>()
            val positions = mutableListOf<Float>()
            val range = maxValue - minValue
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
                    
                    val startPos = startRatio * (sweepAngle / 360f)
                    val endPos = endRatio * (sweepAngle / 360f)
                    
                    // 維持上一個顏色到 startPos
                    colors.add(prevColor)
                    positions.add(Math.max(positions.last() + 0.0001f, startPos))
                    
                    // 在 endPos 完成到目標顏色的漸變
                    colors.add(targetColor)
                    positions.add(Math.max(positions.last() + 0.0001f, endPos))
                    
                    prevColor = targetColor
                }
                
                // 最後一段維持到結束
                colors.add(prevColor)
                positions.add(Math.max(positions.last() + 0.0001f, sweepAngle / 360f))
            } else {
                colors.add(themeColor)
                positions.add(0f)
                colors.add(themeColor)
                positions.add(sweepAngle / 360f)
            }
            
            // 為了防範起點圓角往回長（例如 358度）吃到錯誤的漸變，必須在未繪製的空白區域中點切回第一色
            val emptyMid = (sweepAngle / 360f + 1.0f) / 2f
            colors.add(colors.last())
            positions.add(emptyMid)
            
            colors.add(colors.first())
            positions.add(emptyMid + 0.001f) // 硬切換
            
            colors.add(colors.first())
            positions.add(1.0f)
            
            val gradientSweep = SweepGradient(cx, cy, colors.toIntArray(), positions.toFloatArray())
            val matrix = Matrix()
            matrix.setRotate(startAngle, cx, cy)
            gradientSweep.setLocalMatrix(matrix)
            
            progressPaint.shader = gradientSweep
            needlePaint.shader = gradientSweep // 讓指針也套用漸變，會與指向的角度完美吻合
            progressPaint.color = Color.WHITE
            
            // 為光圈增加微發光感 (調細光暈)
            progressPaint.setShadowLayer(4f * density * vScale, 0f, 0f, currentColor)
        } else {
            progressPaint.shader = null
            needlePaint.shader = null
            progressPaint.color = currentColor
            needlePaint.color = currentColor
            progressPaint.setShadowLayer(4f * density * vScale, 0f, 0f, currentColor)
        }

        // --- 3. 繪製軌道 (Track & Progress) ---
        val currentSweep = sweepAngle * getProgressRatio()
        
        if (meterStyle == Style.SEGMENTED) {
            // 陣列風格 (Segmented)：無指針，打斷的軌道
            // 移除 ROUND 圓角，讓格子間的切割線明顯
            progressPaint.strokeCap = Paint.Cap.BUTT
            trackPaint.strokeCap = Paint.Cap.BUTT
            trackPaint.color = Color.parseColor("#E2E8F0") // 未填塞部分改為淺灰色，確保在白底清晰可見
            
            val segmentCount = 15 // 陣列格寬度加倍，數量減半
            val gapAngle = 4f // 增加間隙角度
            val segmentSweep = (sweepAngle - (segmentCount - 1) * gapAngle) / segmentCount
            
            for (i in 0 until segmentCount) {
                val segStart = startAngle + i * (segmentSweep + gapAngle)
                // 判斷這一段是否被點亮
                val ratio = getProgressRatio()
                val isLit = if (ratio <= 0f) false else (i.toFloat() / segmentCount) < ratio
                
                if (isLit) {
                    canvas.drawArc(arcRect, segStart, segmentSweep, false, progressPaint)
                } else {
                    canvas.drawArc(arcRect, segStart, segmentSweep, false, trackPaint)
                }
            }
        } else {
            // 指針風格 (Needle)：連貫軌道 + 實體指針
            // 畫底軌道
            canvas.drawArc(arcRect, startAngle, sweepAngle, false, trackPaint)
            // 畫進度 (當 currentSweep > 0 時才畫，避免圓形端點在 0 時露出)
            if (currentSweep > 0f) {
                canvas.drawArc(arcRect, startAngle, currentSweep, false, progressPaint)
            }
            
            // 畫指針
            val pointerAngleRad = Math.toRadians((startAngle + currentSweep).toDouble())
            val needleLen = radius - trackThickness - 6f * density * vScale // 讓指針與光軌保持間隔，不碰觸
            val nx = cx + cos(pointerAngleRad).toFloat() * needleLen
            val ny = cy + sin(pointerAngleRad).toFloat() * needleLen
            
            // 繪製細長指針 (從圓心到邊緣)
            needlePaint.strokeWidth = 2f * density * vScale // 指針變細
            needlePaint.style = Paint.Style.STROKE
            needlePaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx, cy, nx, ny, needlePaint)
            
            // 繪製中心圓盤 (Pivot)
            needlePaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, 3f * density * vScale, needlePaint) // 縮小外圈軸心，降低臃腫感
            // 畫一個小黑點在中間增加立體感
            needlePaint.color = Color.BLACK
            canvas.drawCircle(cx, cy, 1f * density * vScale, needlePaint) // 縮小內圈黑點
        }

        val showTicksView = tickMode == TickMode.TICKS || tickMode == TickMode.ALL
        val showLimitsView = tickMode == TickMode.LIMITS || tickMode == TickMode.ALL

        // --- 3.5 繪製刻度 ---
        if (showTicksView) {
            val count = 11
            val majorTickLen = 8f * density * vScale
            val minorTickLen = 4f * density * vScale
            val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * density // 刻度稍微加粗，確保可見
                color = Color.parseColor("#94A3B8") // 統一採用 Slate 400 淺藍灰色，淺色畫布中清晰可見
                strokeCap = Paint.Cap.ROUND
            }
            val angleStep = sweepAngle / (count - 1)
            for (i in 0 until count) {
                val currentAngle = startAngle + i * angleStep
                val angleRad = Math.toRadians(currentAngle.toDouble())
                val len = if (i % 5 == 0) majorTickLen else minorTickLen
                
                // 計算內圈邊緣，並加入保護機制確保不會穿透到另一邊
                val innerEdge = (radius - trackThickness / 2f - 6f * density * vScale).coerceAtLeast(0f)
                val innerEdgeEnd = (innerEdge - len).coerceAtLeast(0f)
                
                // 從軌道內側邊緣往圓心畫，並推離軌道邊緣以免被光暈吃掉
                val tx1 = cx + cos(angleRad).toFloat() * innerEdge
                val ty1 = cy + sin(angleRad).toFloat() * innerEdge
                val tx2 = cx + cos(angleRad).toFloat() * innerEdgeEnd
                val ty2 = cy + sin(angleRad).toFloat() * innerEdgeEnd
                canvas.drawLine(tx1, ty1, tx2, ty2, tickPaint)
            }
        }

        // --- 4. 繪製 Min / Max 標籤 ---
        if (showLimitsView) {
            minMaxTextPaint.textSize = 9f * density * vScale // 頭尾數值調小
            // 算出起點與終點的座標
            val startRad = Math.toRadians(startAngle.toDouble())
            val endRad = Math.toRadians((startAngle + sweepAngle).toDouble())
            // 讓文字稍微外推，但因為整體 padding 縮小，距離不可設太大以免出界
            val labelRadius = radius + trackThickness / 2f + 6f * density * vScale
            
            val minX = cx + cos(startRad).toFloat() * labelRadius
            val minY = cy + cy * 0f + sin(startRad).toFloat() * labelRadius
            val maxX = cx + cos(endRad).toFloat() * labelRadius
            val maxY = cy + cy * 0f + sin(endRad).toFloat() * labelRadius
            
            canvas.drawText(String.format("%.0f", minValue), minX, minY + minMaxTextPaint.textSize / 3, minMaxTextPaint)
            canvas.drawText(String.format("%.0f", maxValue), maxX, maxY + minMaxTextPaint.textSize / 3, minMaxTextPaint)
        }

        // --- 5. 繪製中央資訊面板
        // --- 5. 繪製中央資訊面板
        val centerTextSize = radius * 0.4f
        var textSize = centerTextSize
        textPaint.textSize = textSize
        
        val valueStr = String.format("%.1f", currentAnimValue)
        val textToDraw = if (unit.isNotEmpty()) "$valueStr $unit" else valueStr
        
        // 文字太長時自動縮小字體 (防止單位過長爆出邊界)
        var textWidth = textPaint.measureText(textToDraw)
        val maxWidth = radius * 1.6f
        while (textWidth > maxWidth && textSize > centerTextSize * 0.3f) {
            textSize -= 2f
            textPaint.textSize = textSize
            textWidth = textPaint.measureText(textToDraw)
        }
        
        // 不管哪個角度，數字跟指針的圓心 (cy) 都保持一樣的絕對間隔距離
        // 特別處理 270 度：大幅往下放
        val textY = if (trackAngle == TrackAngle.ARC_270) cy + radius * 0.9f else cy + radius * 0.45f
        
        textPaint.color = currentColor
        canvas.drawText(textToDraw, cx, textY, textPaint)
    }
}
