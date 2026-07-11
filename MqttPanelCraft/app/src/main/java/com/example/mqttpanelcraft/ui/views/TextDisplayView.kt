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
import kotlin.math.roundToInt
import kotlin.random.Random

class TextDisplayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Style { CAPSULE, INFINITY, GLASS, NOTE }
    
    var currentStyle: Style = Style.CAPSULE
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
            recalculateSizes()
            requestLayout()
            invalidate()
        }

    var displayLines: Int = 5
        set(value) {
            field = if (value > 0) value else 1
            recalculateSizes()
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
    var isScrollable: Boolean = false
        set(value) {
            field = value
            (scrollView as? CustomScrollView)?.let {
                it.isScrollable = value
                it.isVerticalScrollBarEnabled = value
                it.overScrollMode = if (value) OVER_SCROLL_IF_CONTENT_SCROLLS else OVER_SCROLL_NEVER
            }
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
        var isScrollable = false
        override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
            if (isScrollable && ev.action == android.view.MotionEvent.ACTION_DOWN) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            return if (isScrollable) super.onTouchEvent(ev) else false
        }
        override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
            if (isScrollable && ev.action == android.view.MotionEvent.ACTION_DOWN) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            return if (isScrollable) super.onInterceptTouchEvent(ev) else false
        }
    }

    inner class LinedTextView(context: Context) : androidx.appcompat.widget.AppCompatTextView(context) {
        override fun onDraw(canvas: Canvas) {
            if (currentStyle == Style.NOTE && calculatedLineHeight > 0f) {
                val linePaint = paintLine
                val count = lineCount
                val r = android.graphics.Rect()
                var lastBottom = 0f
                for (i in 0 until count) {
                    getLineBounds(i, r)
                    val lineY = r.bottom.toFloat()
                    canvas.drawLine(0f, lineY, width.toFloat(), lineY, linePaint)
                    lastBottom = lineY
                }
                val spacing = calculatedLineHeight
                if (spacing > 0 && lastBottom < height) {
                    var emptyY = if (lastBottom > 0f) lastBottom + spacing else spacing
                    while (emptyY <= height) {
                        canvas.drawLine(0f, emptyY, width.toFloat(), emptyY, linePaint)
                        emptyY += spacing
                    }
                }
            }
            super.onDraw(canvas)
        }
    }

    init {
        setWillNotDraw(false)
        
        scrollView = CustomScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isFillViewport = true
            clipToPadding = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
        }
        
        textView = LinedTextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            includeFontPadding = false
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
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
        if (currentStyle == Style.GLASS) {
            textView.setTextColor(Color.WHITE)
        } else {
            textView.setTextColor(themeColor)
        }

        paintBg.color = bgColor
        if (currentStyle == Style.GLASS) {
            paintBg.alpha = (Color.alpha(bgColor) * 0.7f).toInt()
        }
        
        isBgLight = ColorUtils.calculateLuminance(bgColor) > 0.52
        
        when (currentStyle) {
            Style.NOTE -> {
                val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val isPaperLight = !isDark
                val density = resources.displayMetrics.density
                paintLine.strokeWidth = 1f * density
                paintLine.color = if (isPaperLight) Color.argb(110, 130, 145, 160) else Color.argb(110, 160, 170, 180)
                val compColor = themeColor
                paintRedLine.color = compColor
            }
            Style.GLASS -> {
                paintBorder.color = Color.argb(130, 255, 255, 255)
            }
            else -> {}
        }
        recalculateSizes()
    }

    private fun recalculateSizes(w: Int = width, h: Int = height) {
        if (h > 0 && w > 0) {
            val density = resources.displayMetrics.density
            val linesToFit = if (isLogMode) displayLines else 1
            
            // Scale padding appropriately with height so small previews/views are not clipped
            val minPadPx = (2 * density).toInt()
            val maxPadPx = (20 * density).toInt()
            val pad = (h * 0.12f).toInt().coerceIn(minPadPx, maxPadPx)
            val topPad = pad
            val bottomPad = pad
            val leftPad = when (currentStyle) {
                Style.NOTE -> (42 * density).toInt()
                Style.CAPSULE -> (pad * 1.5f).toInt()
                else -> pad
            }
            val rightPad = if (currentStyle == Style.CAPSULE) (pad * 1.5f).toInt() else pad
            
            val availableHeight = h - topPad - bottomPad
            calculatedLineHeight = (availableHeight / linesToFit).toFloat().coerceAtLeast(10f)
            
            if (!isLogMode) {
                (scrollView as? CustomScrollView)?.isScrollable = false
                scrollView.scrollTo(0, 0)
                val estimatedCharsPerRow = 15f
                val maxTextSizeByWidth = w / (estimatedCharsPerRow * 0.55f)
                val maxTextSizeByHeight = availableHeight * 0.75f
                calculatedTextSize = (h * 0.38f).coerceIn(12f * density, 80f * density)
                
                textView.maxLines = 1
                textView.isSingleLine = true
                textView.ellipsize = android.text.TextUtils.TruncateAt.END
                androidx.core.widget.TextViewCompat.setLineHeight(textView, 0)
            } else {
                calculatedTextSize = (calculatedLineHeight * 0.75f).coerceIn(10f * density, 60f * density)
                
                textView.maxLines = displayLines
                textView.isSingleLine = false
                textView.ellipsize = null
                androidx.core.widget.TextViewCompat.setLineHeight(textView, calculatedLineHeight.roundToInt())
            }
            
            scrollView.setPadding(leftPad, topPad, rightPad, bottomPad)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, calculatedTextSize)
            
            val hParam = if (isLogMode) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT
            if (textView.layoutParams.height != hParam) {
                textView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, hParam)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateSizes(w, h)
        if (!isLogMode) {
            scrollView.post { scrollView.scrollTo(0, 0) }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        
        when (currentStyle) {
            Style.CAPSULE -> {
                val radius = minOf(width, height) / 2f
                paintBg.color = Color.WHITE
                canvas.drawRoundRect(rect, radius, radius, paintBg)
                
                paintBorder.style = Paint.Style.STROKE
                paintBorder.strokeWidth = 2f * density
                paintBorder.color = bgColor
                canvas.drawRoundRect(rect, radius, radius, paintBorder)
            }
            Style.INFINITY -> {
                paintBorder.style = Paint.Style.STROKE
                paintBorder.strokeWidth = 2f * density
                paintBorder.color = themeColor
                val y = height - (1f * density)
                canvas.drawLine(0f, y, width.toFloat(), y, paintBorder)
            }
            Style.GLASS -> {
                val radius = 24f * density
                
                // Subtle floating drop-shadow
                rect.offset(0f, 6f * density)
                paintBg.color = Color.argb(25, 0, 0, 0)
                canvas.drawRoundRect(rect, radius, radius, paintBg)
                rect.offset(0f, -6f * density)

                // Translucent acrylic background
                paintBg.color = bgColor
                paintBg.alpha = (Color.alpha(bgColor) * 0.25f).toInt().coerceIn(30, 80)
                canvas.drawRoundRect(rect, radius, radius, paintBg)

                // Diagonal crystal reflection sheen
                canvas.save()
                val clipPath = android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) }
                canvas.clipPath(clipPath)
                val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    shader = android.graphics.LinearGradient(
                        0f, 0f, width.toFloat() * 0.7f, height.toFloat() * 0.7f,
                        intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(15, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                        floatArrayOf(0f, 0.4f, 1f), android.graphics.Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(rect, sheenPaint)
                canvas.restore()

                // Crisp thin light border
                paintBorder.style = Paint.Style.STROKE
                paintBorder.strokeWidth = 1.2f * density
                paintBorder.color = Color.argb(130, 255, 255, 255)
                canvas.drawRoundRect(rect, radius, radius, paintBorder)
            }
            Style.NOTE -> {
                val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val isPaperLight = !isDark
                val radius = 8f * density
                val paperBg = if (isPaperLight) Color.parseColor("#F5F3ED") else Color.parseColor("#282826")
                paintBg.color = paperBg
                canvas.drawRoundRect(rect, radius, radius, paintBg)
                
                // Paper specks
                paintDot.color = if (isPaperLight) Color.argb(22, 0, 0, 0) else Color.argb(22, 255, 255, 255)
                val seed = (width * 31 + height).toLong()
                val rnd = java.util.Random(seed)
                val numDots = (width * height) / 250
                for (i in 0 until numDots) {
                    val dx = rnd.nextFloat() * width
                    val dy = rnd.nextFloat() * height
                    canvas.drawPoint(dx, dy, paintDot)
                }

                // Left edge page binding gutter shadow
                val gutterWidth = 36f * density
                val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    shader = android.graphics.LinearGradient(
                        0f, 0f, gutterWidth, 0f,
                        if (isPaperLight) Color.argb(25, 0, 0, 0) else Color.argb(35, 0, 0, 0),
                        Color.TRANSPARENT,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, gutterWidth, height.toFloat(), gutterPaint)

                // Double margin lines (complementary colors)
                val marginX1 = 26f * density
                val marginX2 = 30f * density
                canvas.drawLine(marginX1, 0f, marginX1, height.toFloat(), paintRedLine)
                canvas.drawLine(marginX2, 0f, marginX2, height.toFloat(), paintRedLine)

                // Outer border
                paintBorder.style = Paint.Style.STROKE
                paintBorder.strokeWidth = 1f * density
                paintBorder.color = if (isPaperLight) Color.argb(50, 0, 0, 0) else Color.argb(50, 255, 255, 255)
                canvas.drawRoundRect(rect, radius, radius, paintBorder)
            }
        }
        
        super.onDraw(canvas)
    }

    fun appendText(text: String, maxLines: Int) {
        val currentText = textView.text.toString()
        val newText = if (currentText.isEmpty() || currentText == "Waiting for data..." || currentText == "loading..." || currentText == "loading ...") text else "$currentText\n$text"
        
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
