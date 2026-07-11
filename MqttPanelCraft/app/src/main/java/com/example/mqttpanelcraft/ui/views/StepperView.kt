package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 數值步進畫布專屬視覺元件 (StepperView) - v0.11.5 旗艦重構版
 *
 * Design Intent:
 * 根據用戶回饋徹底重新設計三種旗艦級 UI 風格：
 * 1. "Standard" (標準): 現代一體化圓角卡片，左右配備精緻尖括號 < 與 > 控制鈕。
 * 2. "Block" (分離方塊): 三個獨立高質感的立體懸浮方塊，消除原本廉價灰底，中央顯示器具備高對比卡片框線。
 * 3. "Smooth" (圓滑): 極致流暢的全膠囊圓潤造型，端點帶有細緻高光與漸層觸感回饋。
 * 並且保留「純鬆手發送 (Release-Only)」與長按連續加速機制。
 */
class StepperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        isClickable = true
    }

    var minValue: Float = 0f
    var maxValue: Float = 100f
    var stepValue: Float = 1f
    var currentValue: Float = 50f
        set(value) {
            val clamped = min(max(value, minValue), maxValue)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    // 風格設定: "Standard", "Block", "Smooth"
    var visualStyle: String = "Standard"
        set(value) {
            field = value
            invalidate()
        }

    var themeColor: Int = Color.parseColor("#6366F1")
        set(value) {
            field = value
            invalidate()
        }

    var longPressEnabled: Boolean = true

    // 觸控按壓狀態 (0=無, -1=減/左, +1=加/右)
    private var pressedButton: Int = 0
    private val handlerRepeat = Handler(Looper.getMainLooper())
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (pressedButton != 0 && longPressEnabled) {
                step(pressedButton)
                handlerRepeat.postDelayed(this, 110L)
            }
        }
    }

    var onValueCommit: ((String) -> Unit)? = null

    // 繪圖物件緩存
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rectMinus = RectF()
    private val rectValue = RectF()
    private val rectPlus = RectF()

    fun formatValue(valFloat: Float): String {
        return if (valFloat % 1f == 0f) {
            valFloat.toInt().toString()
        } else {
            String.format("%.1f", valFloat)
        }
    }

    private fun step(deltaSign: Int) {
        val nextVal = currentValue + deltaSign * stepValue
        currentValue = min(max(nextVal, minValue), maxValue)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val btnW = w * 0.28f

        when (visualStyle) {
            "Block" -> drawBlockStyle(canvas, w, h, btnW, density, isDark)
            "Smooth" -> drawSmoothStyle(canvas, w, h, btnW, density, isDark)
            else -> drawStandardStyle(canvas, w, h, btnW, density, isDark)
        }
    }

    /**
     * 1. 標準風格 (Standard) - 左右為 < 與 >
     */
    private fun drawStandardStyle(
        canvas: Canvas,
        w: Float,
        h: Float,
        btnW: Float,
        density: Float,
        isDark: Boolean
    ) {
        val corner = 12f * density

        // 卡片底色
        bgPaint.style = Paint.Style.FILL
        bgPaint.color = if (isDark) Color.parseColor("#1F2128") else Color.parseColor("#FFFFFF")
        canvas.drawRoundRect(1f * density, 1f * density, w - 1f * density, h - 1f * density, corner, corner, bgPaint)

        // 觸壓高亮
        rectMinus.set(0f, 0f, btnW, h)
        rectPlus.set(w - btnW, 0f, w, h)
        if (pressedButton == -1) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = adjustAlpha(themeColor, 0.2f)
            canvas.drawRoundRect(0f, 0f, btnW * 1.3f, h, corner, corner, btnPaint)
        } else if (pressedButton == 1) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = adjustAlpha(themeColor, 0.2f)
            canvas.drawRoundRect(w - btnW * 1.3f, 0f, w, h, corner, corner, btnPaint)
        }

        // 精緻邊框
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 1.6f * density
        borderPaint.color = adjustAlpha(themeColor, 0.8f)
        canvas.drawRoundRect(1f * density, 1f * density, w - 1f * density, h - 1f * density, corner, corner, borderPaint)

        // 中間數值
        rectValue.set(btnW, 0f, w - btnW, h)
        drawCenteredText(canvas, formatValue(currentValue), rectValue, isDark, density)

        // 繪製左右尖括號 < 與 >
        drawChevronLeft(canvas, rectMinus.centerX(), rectMinus.centerY(), h * 0.22f, density, themeColor)
        drawChevronRight(canvas, rectPlus.centerX(), rectPlus.centerY(), h * 0.22f, density, themeColor)
    }

    /**
     * 2. 分離方塊風格 (Block) - 高對比三卡片懸浮
     */
    private fun drawBlockStyle(
        canvas: Canvas,
        w: Float,
        h: Float,
        btnW: Float,
        density: Float,
        isDark: Boolean
    ) {
        val gap = 6f * density
        val corner = 10f * density

        rectMinus.set(0f, 0f, btnW - gap / 2, h)
        rectValue.set(btnW + gap / 2, 0f, w - btnW - gap / 2, h)
        rectPlus.set(w - btnW + gap / 2, 0f, w, h)

        // 左方塊 (-)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = if (pressedButton == -1) lightenColor(themeColor, 0.2f) else themeColor
        canvas.drawRoundRect(rectMinus, corner, corner, btnPaint)

        // 中間方塊 (高對比潔淨質感底色與細線框，不再是灰濛醜底)
        bgPaint.style = Paint.Style.FILL
        bgPaint.color = if (isDark) Color.parseColor("#15171E") else Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(rectValue, corner, corner, bgPaint)

        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 1.3f * density
        borderPaint.color = adjustAlpha(themeColor, 0.45f)
        canvas.drawRoundRect(rectValue, corner, corner, borderPaint)

        // 右方塊 (+)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = if (pressedButton == 1) lightenColor(themeColor, 0.2f) else themeColor
        canvas.drawRoundRect(rectPlus, corner, corner, btnPaint)

        drawCenteredText(canvas, formatValue(currentValue), rectValue, isDark, density)
        drawMinusIcon(canvas, rectMinus.centerX(), rectMinus.centerY(), h * 0.2f, density, Color.WHITE)
        drawPlusIcon(canvas, rectPlus.centerX(), rectPlus.centerY(), h * 0.2f, density, Color.WHITE)
    }

    /**
     * 3. 圓滑風格 (Smooth) - 全膠囊流線外觀
     */
    private fun drawSmoothStyle(
        canvas: Canvas,
        w: Float,
        h: Float,
        btnW: Float,
        density: Float,
        isDark: Boolean
    ) {
        val corner = h * 0.5f

        // 整體膠囊背景
        bgPaint.style = Paint.Style.FILL
        bgPaint.color = if (isDark) Color.parseColor("#1D1E26") else Color.parseColor("#F1F5F9")
        canvas.drawRoundRect(0f, 0f, w, h, corner, corner, bgPaint)

        rectMinus.set(0f, 0f, btnW, h)
        rectPlus.set(w - btnW, 0f, w, h)

        // 左側圓滑觸擊感
        if (pressedButton == -1) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = adjustAlpha(themeColor, 0.28f)
            canvas.drawRoundRect(0f, 0f, btnW * 1.5f, h, corner, corner, btnPaint)
        }
        // 右側圓滑觸擊感
        if (pressedButton == 1) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = adjustAlpha(themeColor, 0.28f)
            canvas.drawRoundRect(w - btnW * 1.5f, 0f, w, h, corner, corner, btnPaint)
        }

        // 外圈流線邊框
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 2f * density
        borderPaint.color = themeColor
        canvas.drawRoundRect(1f * density, 1f * density, w - 1f * density, h - 1f * density, corner, corner, borderPaint)

        rectValue.set(btnW, 0f, w - btnW, h)
        drawCenteredText(canvas, formatValue(currentValue), rectValue, isDark, density)

        drawMinusIcon(canvas, rectMinus.centerX(), rectMinus.centerY(), h * 0.2f, density, themeColor)
        drawPlusIcon(canvas, rectPlus.centerX(), rectPlus.centerY(), h * 0.2f, density, themeColor)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, rect: RectF, isDark: Boolean, density: Float) {
        textPaint.color = if (isDark) Color.WHITE else Color.parseColor("#0F172A")
        textPaint.textSize = min(rect.height() * 0.44f, 22f * density)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.CENTER

        val fontMetrics = textPaint.fontMetrics
        val textY = rect.centerY() - (fontMetrics.descent + fontMetrics.ascent) / 2f
        canvas.drawText(text, rect.centerX(), textY, textPaint)
    }

    private fun drawChevronLeft(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        density: Float,
        color: Int
    ) {
        iconPaint.color = color
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = 2.6f * density
        iconPaint.strokeCap = Paint.Cap.ROUND
        iconPaint.strokeJoin = Paint.Join.ROUND

        val path = Path()
        path.moveTo(cx + radius * 0.45f, cy - radius * 0.85f)
        path.lineTo(cx - radius * 0.45f, cy)
        path.lineTo(cx + radius * 0.45f, cy + radius * 0.85f)
        canvas.drawPath(path, iconPaint)
    }

    private fun drawChevronRight(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        density: Float,
        color: Int
    ) {
        iconPaint.color = color
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = 2.6f * density
        iconPaint.strokeCap = Paint.Cap.ROUND
        iconPaint.strokeJoin = Paint.Join.ROUND

        val path = Path()
        path.moveTo(cx - radius * 0.45f, cy - radius * 0.85f)
        path.lineTo(cx + radius * 0.45f, cy)
        path.lineTo(cx - radius * 0.45f, cy + radius * 0.85f)
        canvas.drawPath(path, iconPaint)
    }

    private fun drawMinusIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        density: Float,
        color: Int
    ) {
        iconPaint.color = color
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = 2.6f * density
        iconPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx - radius, cy, cx + radius, cy, iconPaint)
    }

    private fun drawPlusIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        density: Float,
        color: Int
    ) {
        iconPaint.color = color
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = 2.6f * density
        iconPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx - radius, cy, cx + radius, cy, iconPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, iconPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val x = event.x
                val btnW = width * 0.28f
                pressedButton = when {
                    x <= btnW -> -1
                    x >= width - btnW -> 1
                    else -> 0
                }
                if (pressedButton != 0) {
                    step(pressedButton)
                    if (longPressEnabled) {
                        handlerRepeat.postDelayed(repeatRunnable, 320L)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handlerRepeat.removeCallbacks(repeatRunnable)
                if (pressedButton != 0 && event.actionMasked == MotionEvent.ACTION_UP) {
                    onValueCommit?.invoke(formatValue(currentValue))
                }
                pressedButton = 0
                invalidate()
            }
        }
        return true
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val newR = min(255, (r + (255 - r) * factor).toInt())
        val newG = min(255, (g + (255 - g) * factor).toInt())
        val newB = min(255, (b + (255 - b) * factor).toInt())
        return Color.rgb(newR, newG, newB)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
