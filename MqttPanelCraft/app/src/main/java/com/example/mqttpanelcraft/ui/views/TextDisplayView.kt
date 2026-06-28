package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import kotlin.random.Random

class TextDisplayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Style { GLASS, NOTE }
    
    var currentStyle: Style = Style.GLASS
        set(value) {
            field = value
            updateAppearance()
            invalidate()
        }

    var themeColor: Int = Color.parseColor("#4ADE80") // Text color
        set(value) {
            field = value
            updateAppearance()
            invalidate()
        }
        
    var bgColor: Int = Color.parseColor("#CC1E1E1E") // Overall background color
        set(value) {
            field = value
            updateAppearance()
            invalidate()
        }

    var isLogMode: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var displayLines: Int = 5
        set(value) {
            field = if (value > 0) value else 1
            requestLayout()
            invalidate()
        }
        
    var prefixLength: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var fontStyle: String = "NORMAL"
        set(value) {
            field = value
            updateAppearance()
            invalidate()
        }
    var isScrollable: Boolean = true
        set(value) {
            field = value
            (scrollView as? CustomScrollView)?.isScrollable = value
        }

    val scrollView: ScrollView
    val textView: TextView
    
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintRedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#E06C75") // Soft red
    }
    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(80, 255, 255, 255)
    }

    private var calculatedTextSize: Float = 14f
    private var calculatedLineHeight: Float = 0f
    private var isBgLight: Boolean = false

    inner class CustomScrollView(context: Context) : ScrollView(context) {
        var isScrollable = true
        override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
            return if (isScrollable) super.onTouchEvent(ev) else false
        }
        override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
            return if (isScrollable) super.onInterceptTouchEvent(ev) else false
        }
    }

    init {
        setWillNotDraw(false)
        
        scrollView = CustomScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isFillViewport = true
            clipToPadding = false
        }
        
        textView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            includeFontPadding = false
        }
        
        scrollView.addView(textView)
        addView(scrollView)
        
        updateAppearance()
    }

    private fun updateAppearance() {
        if (fontStyle == "HANDWRITING") {
            textView.typeface = android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL)
        } else {
            textView.typeface = android.graphics.Typeface.DEFAULT
        }
        textView.setTextColor(themeColor)
        
        paintBg.color = bgColor
        if (currentStyle == Style.GLASS) {
            paintBg.alpha = (Color.alpha(bgColor) * 0.7f).toInt()
        }
        
        isBgLight = ColorUtils.calculateLuminance(bgColor) > 0.5
        
        when (currentStyle) {
            Style.NOTE -> {
                paintLine.color = if (isBgLight) Color.argb(100, 0, 0, 0) else Color.argb(100, 255, 255, 255)
                paintRedLine.color = if (isBgLight) Color.parseColor("#D32F2F") else Color.parseColor("#E57373")
            }
            Style.GLASS -> {
                paintBorder.color = Color.argb(60, 255, 255, 255)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            // Auto calculate text size and layout
            val linesToFit = if (isLogMode) displayLines else 1
            
            // Calculate padding
            val topPad = 16
            val bottomPad = 16
            
            val availableHeight = h - topPad - bottomPad
            calculatedLineHeight = (availableHeight / linesToFit).toFloat()
            
            // Estimate text size
            if (!isLogMode) {
                // Limit text size based on width to prevent flattening out
                val estimatedCharsPerRow = 15f
                val maxTextSizeByWidth = w / (estimatedCharsPerRow * 0.6f)
                val maxTextSizeByHeight = availableHeight * 0.5f
                calculatedTextSize = minOf(maxTextSizeByWidth, maxTextSizeByHeight).coerceIn(14f, 64f)
            } else {
                calculatedTextSize = calculatedLineHeight * 0.75f
            }
            
            // Set padding to align text perfectly with drawn lines
            scrollView.setPadding(32, topPad, 16, bottomPad)
            
            // Apply text size
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, calculatedTextSize)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = 24f
        
        // Draw background
        canvas.drawRoundRect(rect, radius, radius, paintBg)
        
        if (currentStyle == Style.NOTE) {
            // Paper noise texture (subtle horizontal lines)
            paintDot.color = if (isBgLight) Color.argb(10, 0, 0, 0) else Color.argb(10, 255, 255, 255)
            for (i in 0..height step 8) {
                if (Random.nextBoolean()) {
                    canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), paintDot)
                }
            }

            val topPad = scrollView.paddingTop.toFloat()
            
            val lineSpacing = if (isLogMode) calculatedLineHeight else (calculatedTextSize * 1.5f).coerceAtLeast(30f)
            var lineY = topPad + lineSpacing
            while (lineY < height - 8f) {
                canvas.drawLine(0f, lineY, width.toFloat(), lineY, paintLine)
                lineY += lineSpacing
            }
            
            // Default note margin
            canvas.drawLine(24f, 0f, 24f, height.toFloat(), paintRedLine)
            
        } else if (currentStyle == Style.GLASS) {
            // Draw glass glare
            canvas.save()
            val clipPath = android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) }
            canvas.clipPath(clipPath)
            
            val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = android.graphics.LinearGradient(
                    0f, 0f, width * 0.5f, height * 0.8f,
                    Color.argb(130, 255, 255, 255), Color.argb(0, 255, 255, 255), android.graphics.Shader.TileMode.CLAMP
                )
            }
            val glarePoly = android.graphics.Path()
            glarePoly.moveTo(0f, 0f)
            glarePoly.lineTo(width.toFloat() * 0.8f, 0f)
            glarePoly.lineTo(0f, height.toFloat() * 0.8f)
            glarePoly.close()
            canvas.drawPath(glarePoly, glarePaint)
            canvas.restore()

            // Draw thick 3D rim border (light top-left, dark bottom-right)
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                shader = android.graphics.LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.argb(200, 255, 255, 255), Color.argb(20, 255, 255, 255), Color.argb(120, 0, 0, 0)),
                    floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP
                )
            }
            val borderRect = RectF(3f, 3f, width.toFloat() - 3f, height.toFloat() - 3f)
            canvas.drawRoundRect(borderRect, radius, radius, rimPaint)
            
            // Draw inner rim for crystal depth
            val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                shader = android.graphics.LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(120, 255, 255, 255), Color.argb(0, 0, 0, 0)),
                    floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP
                )
            }
            val innerRect = RectF(7f, 7f, width.toFloat() - 7f, height.toFloat() - 7f)
            canvas.drawRoundRect(innerRect, radius - 4f, radius - 4f, innerRimPaint)
        }
        
        super.onDraw(canvas)
    }

    fun appendText(text: String, maxLines: Int) {
        val currentText = textView.text.toString()
        val newText = if (currentText.isEmpty()) text else "$currentText\n$text"
        
        // Truncate if exceeds maxLines
        val lines = newText.split("\n")
        if (lines.size > maxLines) {
            textView.text = lines.takeLast(maxLines).joinToString("\n")
        } else {
            textView.text = newText
        }
        
        // Auto scroll to bottom
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
