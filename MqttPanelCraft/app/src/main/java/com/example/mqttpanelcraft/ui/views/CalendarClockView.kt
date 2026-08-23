package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarClockView(context: Context) : FrameLayout(context) {

    var visualStyle: String = "DIGITAL"
        set(value) {
            field = value
            setWillNotDraw(false)
            updateViewLayout()
            invalidate()
        }

    private val container = LinearLayout(context)
    private val mainText = TextView(context)
    private val subTextTop = TextView(context)
    private val subTextBottom = TextView(context)

    private val handler = Handler(Looper.getMainLooper())
    private var timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    var timeFormatStr: String = "HH:mm"
        set(value) {
            field = value
            try { timeFormatter = SimpleDateFormat(value, Locale.getDefault()) } catch (e: Exception) {}
            updateText()
        }
        
    var dateFormatStr: String = "yyyy-MM-dd"
        set(value) {
            field = value
            try { dateFormatter = SimpleDateFormat(value, Locale.getDefault()) } catch (e: Exception) {}
            updateText()
        }

    var isEditMode = false

    var mode: String = "COMBO"
        set(value) {
            field = value
            updateViewLayout()
        }

    var primaryColorHex: String = "#7B1FA2"
        set(value) {
            field = value
            updateColors()
        }

    private var currentScale: Float = 1f
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateText()
            invalidate() // for analog clock
            if (!isEditMode) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    init {
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER
        container.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        mainText.gravity = Gravity.CENTER
        mainText.includeFontPadding = false
        mainText.setTypeface(null, android.graphics.Typeface.BOLD)

        subTextTop.gravity = Gravity.CENTER
        subTextTop.includeFontPadding = false
        
        subTextBottom.gravity = Gravity.CENTER
        subTextBottom.includeFontPadding = false

        container.addView(subTextTop, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        container.addView(mainText, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        container.addView(subTextBottom, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(container)
    }

    private fun updateText() {
        val now = Date()
        val dateStr = dateFormatter.format(now)
        val timeStr = timeFormatter.format(now)

        when (visualStyle) {
            "BIG_DATE" -> {
                if (mode == "COMBO") {
                    val yearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(now)
                    val day = SimpleDateFormat("dd", Locale.getDefault()).format(now)
                    subTextTop.text = yearMonth
                    mainText.text = day
                    subTextBottom.text = timeStr
                } else if (mode == "CALENDAR") {
                    subTextTop.text = ""
                    mainText.text = dateStr
                    subTextBottom.text = ""
                }
            }
            "DIGITAL" -> {
                if (mode == "COMBO") {
                    subTextTop.text = dateStr
                    mainText.text = timeStr
                    subTextBottom.text = ""
                } else if (mode == "CLOCK") {
                    subTextTop.text = ""
                    mainText.text = timeStr
                    subTextBottom.text = ""
                } else if (mode == "CALENDAR") {
                    subTextTop.text = ""
                    mainText.text = dateStr
                    subTextBottom.text = ""
                }
            }
            "ANALOG" -> {
                // Analog draws its own clock
                subTextTop.text = ""
                mainText.text = ""
                if (mode == "COMBO") {
                    subTextBottom.text = dateStr
                } else {
                    subTextBottom.text = ""
                }
            }
        }
    }

    private fun updateColors() {
        val pColor = try { Color.parseColor(primaryColorHex) } catch(e:Exception) { Color.BLACK }
        val luminance = ColorUtils.calculateLuminance(pColor)
        val isDark = luminance < 0.5
        val bgColor = if (isDark) Color.WHITE else Color.BLACK
        val secColor = if (isDark) Color.DKGRAY else Color.LTGRAY

        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = 16f * resources.displayMetrics.density
        }

        mainText.setTextColor(pColor)
        subTextTop.setTextColor(secColor)
        subTextBottom.setTextColor(secColor)
        
        clockPaint.color = pColor
        invalidate()
    }

    private fun updateViewLayout() {
        updateText()
        updateColors()
        
        val scale = currentScale
        when (visualStyle) {
            "BIG_DATE" -> {
                mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 60f * scale)
                subTextTop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * scale)
                subTextBottom.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * scale)
                subTextTop.visibility = VISIBLE
                subTextBottom.visibility = VISIBLE
                mainText.visibility = VISIBLE
            }
            "DIGITAL" -> {
                mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f * scale)
                subTextTop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale)
                subTextBottom.visibility = GONE
                subTextTop.visibility = if (mode == "COMBO") VISIBLE else GONE
                mainText.visibility = VISIBLE
            }
            "ANALOG" -> {
                mainText.visibility = GONE
                subTextTop.visibility = GONE
                subTextBottom.visibility = if (mode == "COMBO") VISIBLE else GONE
                subTextBottom.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale)
                // Center text at bottom
                subTextBottom.setPadding(0, (width * 0.8f).toInt(), 0, 0)
            }
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (visualStyle == "ANALOG") {
            val cx = width / 2f
            val cy = height / 2f - (if (mode == "COMBO") height * 0.15f else 0f)
            val radius = Math.min(width, height) * 0.35f
            
            // Draw clock face
            clockPaint.style = Paint.Style.STROKE
            clockPaint.strokeWidth = 4f * resources.displayMetrics.density
            canvas.drawCircle(cx, cy, radius, clockPaint)
            
            // Draw ticks
            for (i in 0..11) {
                val angle = Math.PI * i / 6.0
                val startRadius = radius * 0.85f
                val startX = cx + Math.sin(angle).toFloat() * startRadius
                val startY = cy - Math.cos(angle).toFloat() * startRadius
                val stopX = cx + Math.sin(angle).toFloat() * radius
                val stopY = cy - Math.cos(angle).toFloat() * radius
                canvas.drawLine(startX, startY, stopX, stopY, clockPaint)
            }
            
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR)
            val min = cal.get(java.util.Calendar.MINUTE)
            val sec = cal.get(java.util.Calendar.SECOND)
            
            // Hour hand
            val hAngle = Math.PI * (hour + min / 60.0) / 6.0
            clockPaint.strokeWidth = 6f * resources.displayMetrics.density
            canvas.drawLine(cx, cy, cx + Math.sin(hAngle).toFloat() * radius * 0.5f, cy - Math.cos(hAngle).toFloat() * radius * 0.5f, clockPaint)
            
            // Minute hand
            val mAngle = Math.PI * (min + sec / 60.0) / 30.0
            clockPaint.strokeWidth = 4f * resources.displayMetrics.density
            canvas.drawLine(cx, cy, cx + Math.sin(mAngle).toFloat() * radius * 0.7f, cy - Math.cos(mAngle).toFloat() * radius * 0.7f, clockPaint)
            
            // Second hand
            val sAngle = Math.PI * sec / 30.0
            val oldColor = clockPaint.color
            clockPaint.color = Color.RED
            clockPaint.strokeWidth = 2f * resources.displayMetrics.density
            canvas.drawLine(cx, cy, cx + Math.sin(sAngle).toFloat() * radius * 0.8f, cy - Math.cos(sAngle).toFloat() * radius * 0.8f, clockPaint)
            clockPaint.color = oldColor
            
            // Center dot
            clockPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, 4f * resources.displayMetrics.density, clockPaint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val baseWidth = 150f * resources.displayMetrics.density
        currentScale = (w / baseWidth).coerceIn(0.5f, 3.0f)
        updateViewLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateRunnable.run()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(updateRunnable)
    }
}