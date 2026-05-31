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
    
    // 與 ScaleMeter 共用的變色邏輯列舉
    enum class ThresholdEffect { GRADIENT, TOP_TIP, VALUE_CHANGE }

    // --- 外觀參數 ---
    var meterStyle: Style = Style.NEEDLE
        set(value) { field = value; invalidate() }

    var trackAngle: TrackAngle = TrackAngle.ARC_270
        set(value) { field = value; requestLayout(); invalidate() }

    var showTicks: Boolean = false
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
    var themeColor: Int = Color.parseColor("#4CAF50")
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
        val trackThickness = 12f * density * vScale
        trackPaint.strokeWidth = trackThickness
        progressPaint.strokeWidth = trackThickness
        
        val maxTextSize = 12f * density * vScale // 再次縮小中央數值
        val padding = trackThickness / 2f + 20f * density * vScale // 為 Min/Max 標籤留出空間
        
        var cx = w / 2f
        var cy = h / 2f
        var radius = minOf(w / 2f, h / 2f) - padding
        
        // 依照 Angle 調整圓心，極大化空間利用
        when (trackAngle) {
            TrackAngle.ARC_180 -> {
                // 180度半圓：圓心盡量靠下，但要留一點空間給下方文字
                radius = minOf(w / 2f, h) - padding - maxTextSize
                cy = h - padding - maxTextSize
            }
            TrackAngle.ARC_120 -> {
                // 120度圓弧：圓心靠下，半徑可以更大
                radius = minOf(w / 2f, h * 1.5f) - padding
                cy = h - padding
            }
            TrackAngle.ARC_270 -> {
                // 270度：完全置中
            }
        }
        
        val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        
        // --- 2. 設定顏色與 SweepGradient ---
        val currentColor = getTargetThresholdColor()
        
        if (thresholdMode && thresholdEffect == ThresholdEffect.GRADIENT && thresholds.isNotEmpty()) {
            val colors = mutableListOf<Int>()
            val positions = mutableListOf<Float>()
            val range = maxValue - minValue
            
            // SweepGradient 的 0度在三點鐘方向。
            // 我們需要把 threshold 從 minValue..maxValue 映射到 startAngle..startAngle+sweepAngle (轉換到 0..360 內)
            // 為了簡化，如果在 Sweep 模式下，直接給定顏色陣列會更穩。
            // 但因為要支援多段顏色，我們採用近似的漸變：
            val gradientSweep = SweepGradient(cx, cy, 
                intArrayOf(themeColor, themeColor), // Placeholder
                null
            )
            // TODO: 完整的環形漸層矩陣映射，暫時用單色取代，後續可優化
            progressPaint.shader = null
            progressPaint.color = currentColor
            needlePaint.color = currentColor
            
            // 為光圈增加微發光感
            progressPaint.setShadowLayer(8f * density * vScale, 0f, 0f, currentColor)
        } else {
            progressPaint.shader = null
            progressPaint.color = currentColor
            needlePaint.color = currentColor
            progressPaint.setShadowLayer(8f * density * vScale, 0f, 0f, currentColor)
        }

        // --- 3. 繪製軌道 (Track & Progress) ---
        val currentSweep = sweepAngle * getProgressRatio()
        
        if (meterStyle == Style.SEGMENTED) {
            // 陣列風格 (Segmented)：無指針，打斷的軌道
            val segmentCount = 40
            val gapAngle = 2f // 每段之間的間隙角度
            val segmentSweep = (sweepAngle - (segmentCount - 1) * gapAngle) / segmentCount
            
            for (i in 0 until segmentCount) {
                val segStart = startAngle + i * (segmentSweep + gapAngle)
                // 判斷這一段是否被點亮
                val isLit = (i.toFloat() / segmentCount) <= getProgressRatio()
                
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
            // 畫進度
            canvas.drawArc(arcRect, startAngle, currentSweep, false, progressPaint)
            
            // 畫指針
            val pointerAngleRad = Math.toRadians((startAngle + currentSweep).toDouble())
            val needleLen = radius - trackThickness * 0.5f // 加長指針，讓它看起來不那麼短
            val nx = cx + cos(pointerAngleRad).toFloat() * needleLen
            val ny = cy + sin(pointerAngleRad).toFloat() * needleLen
            
            // 繪製細長指針 (從圓心到邊緣)
            needlePaint.strokeWidth = 3f * density * vScale
            needlePaint.style = Paint.Style.STROKE
            needlePaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx, cy, nx, ny, needlePaint)
            
            // 繪製中心圓盤 (Pivot)
            needlePaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, 4f * density * vScale, needlePaint) // 縮小外圈軸心
            // 畫一個小黑點在中間增加立體感
            needlePaint.color = Color.BLACK
            canvas.drawCircle(cx, cy, 1.5f * density * vScale, needlePaint) // 縮小內圈黑點
        }

        // --- 3.5 繪製刻度 ---
        if (showTicks) {
            val count = 11
            val majorTickLen = 8f * density * vScale
            val minorTickLen = 4f * density * vScale
            val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                color = Color.parseColor("#40FFFFFF")
            }
            val angleStep = sweepAngle / (count - 1)
            for (i in 0 until count) {
                val currentAngle = startAngle + i * angleStep
                val angleRad = Math.toRadians(currentAngle.toDouble())
                val len = if (i % 5 == 0) majorTickLen else minorTickLen
                // 從軌道內側邊緣往圓心畫
                val tx1 = cx + cos(angleRad).toFloat() * (radius - trackThickness / 2f)
                val ty1 = cy + sin(angleRad).toFloat() * (radius - trackThickness / 2f)
                val tx2 = cx + cos(angleRad).toFloat() * (radius - trackThickness / 2f - len)
                val ty2 = cy + sin(angleRad).toFloat() * (radius - trackThickness / 2f - len)
                canvas.drawLine(tx1, ty1, tx2, ty2, tickPaint)
            }
        }

        // --- 4. 繪製 Min / Max 標籤 ---
        minMaxTextPaint.textSize = 9f * density * vScale // 頭尾數值調小
        // 算出起點與終點的座標
        val startRad = Math.toRadians(startAngle.toDouble())
        val endRad = Math.toRadians((startAngle + sweepAngle).toDouble())
        // 讓文字稍微外推一點
        val labelRadius = radius + trackThickness
        
        val minX = cx + cos(startRad).toFloat() * labelRadius
        val minY = cy + sin(startRad).toFloat() * labelRadius
        val maxX = cx + cos(endRad).toFloat() * labelRadius
        val maxY = cy + sin(endRad).toFloat() * labelRadius
        
        canvas.drawText(String.format("%.0f", minValue), minX, minY + minMaxTextPaint.textSize / 3, minMaxTextPaint)
        canvas.drawText(String.format("%.0f", maxValue), maxX, maxY + minMaxTextPaint.textSize / 3, minMaxTextPaint)

        // --- 5. 繪製中央資訊面板 (Value & Unit) ---
        textPaint.textSize = maxTextSize
        val valueStr = String.format("%.1f", currentAnimValue)
        val textToDraw = if (unit.isNotEmpty()) "$valueStr $unit" else valueStr
        
        // 處理排版位置
        val textY = when (trackAngle) {
            TrackAngle.ARC_270 -> cy + radius * 0.4f // 置於圓心下方
            TrackAngle.ARC_180 -> cy + maxTextSize // 置於圓心正下方
            TrackAngle.ARC_120 -> cy - radius / 2 // 置於圓心偏上方 (被指針蓋住的位置，營造層次感)
        }
        
        textPaint.color = currentColor
        canvas.drawText(textToDraw, cx, textY, textPaint)
    }
}
