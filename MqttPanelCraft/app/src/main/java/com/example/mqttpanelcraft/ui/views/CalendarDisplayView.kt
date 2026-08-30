package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarDisplayView(context: Context) : FrameLayout(context) {
    var isEditMode: Boolean = false

    private var visualStyle = "MONTH"
    private var dateFormat = "yyyy-MM-dd"
    private var timeFormat = "HH:mm"
    private var primaryColor = Color.parseColor("#7B1FA2")
    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L)
        }
    }

    fun setConfig(style: String, datePattern: String, timePattern: String, colorHex: String) {
        val normalizedStyle = when (style) {
            "BIG_DATE" -> "BIG_DATE"
            "DATE_TIME", "DIGITAL", "ANALOG" -> "DATE_TIME"
            else -> "MONTH"
        }
        val normalizedDate = normalizeDatePattern(datePattern)
        val normalizedTime = timePattern.ifBlank { "HH:mm" }
        val parsedColor = try { Color.parseColor(colorHex) } catch (_: Exception) { Color.parseColor("#7B1FA2") }
        if (visualStyle == normalizedStyle && dateFormat == normalizedDate && timeFormat == normalizedTime && primaryColor == parsedColor) {
            return
        }
        visualStyle = normalizedStyle
        dateFormat = normalizedDate
        timeFormat = normalizedTime
        primaryColor = parsedColor
        render()
    }

    private fun currentScale(): Float {
        val h = height
        return if (h > 0) (h / (180f * resources.displayMetrics.density)).coerceIn(0.2f, 3.0f) else 1f
    }

    private fun render() {
        removeAllViews()
        val scale = currentScale()
        background = GradientDrawable().apply {
            setColor(if (ColorUtils.calculateLuminance(primaryColor) < 0.45) Color.WHITE else Color.rgb(28, 24, 30))
            setStroke(dp(1), ColorUtils.setAlphaComponent(primaryColor, 135))
            cornerRadius = (dp(10) * scale).coerceAtLeast(dp(3).toFloat())
        }

        when (visualStyle) {
            "BIG_DATE" -> renderBigDate(scale)
            "DATE_TIME" -> renderDateTime(scale)
            else -> renderMonth(scale)
        }
    }

    private fun renderBigDate(scale: Float) {
        val now = Calendar.getInstance()
        val day = SimpleDateFormat("dd", Locale.getDefault()).format(now.time)
        val monthAndWeekday = SimpleDateFormat("M 月 · EEEE", Locale.getDefault()).format(now.time)
        addView(verticalContainer(scale).apply {
            addView(text(day, (54f * scale).coerceAtLeast(13f), primaryColor, Typeface.NORMAL))
            addView(text(monthAndWeekday, (13f * scale).coerceAtLeast(7f), secondaryColor(), Typeface.NORMAL))
        })
    }

    private fun renderDateTime(scale: Float) {
        val now = Calendar.getInstance().time
        val date = safeFormat(dateFormat, now)
        val time = safeFormat(timeFormat, now)
        addView(verticalContainer(scale).apply {
            addView(text(time, (36f * scale).coerceAtLeast(12f), primaryColor, Typeface.NORMAL))
            addView(text(date, (13f * scale).coerceAtLeast(7f), secondaryColor(), Typeface.NORMAL))
        })
    }

    private fun renderMonth(scale: Float) {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val monthTitle = SimpleDateFormat("yyyy 年 M 月", Locale.getDefault()).format(calendar.time)
        val padH = (dp(10) * scale).toInt().coerceAtLeast(dp(2))
        val padV = (dp(8) * scale).toInt().coerceAtLeast(dp(2))
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padH, padV, padH, padV)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        content.addView(text(monthTitle, (14f * scale).coerceAtLeast(8f), primaryColor, Typeface.BOLD).apply {
            setPadding(0, 0, 0, (dp(4) * scale).toInt().coerceAtLeast(dp(1)))
        })

        val grid = GridLayout(context).apply {
            columnCount = 7
            rowCount = 7
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
            grid.addView(dayCell(label, (10f * scale).coerceAtLeast(5.5f), secondaryColor(), false, scale))
        }

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstColumn = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        repeat(firstColumn) { grid.addView(dayCell("", (10f * scale).coerceAtLeast(5.5f), secondaryColor(), false, scale)) }
        for (day in 1..daysInMonth) {
            grid.addView(dayCell(day.toString(), (10f * scale).coerceAtLeast(5.5f), if (day == today) Color.WHITE else primaryColor, day == today, scale))
        }
        val used = 7 + firstColumn + daysInMonth
        repeat((49 - used).coerceAtLeast(0)) { grid.addView(dayCell("", (10f * scale).coerceAtLeast(5.5f), secondaryColor(), false, scale)) }
        content.addView(grid)
        addView(content)
    }

    private fun dayCell(value: String, sizeSp: Float, color: Int, selected: Boolean, scale: Float): TextView =
        text(value, sizeSp, color, Typeface.NORMAL).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                val m = (dp(1) * scale).toInt()
                setMargins(m, m, m, m)
            }
            if (selected) {
                background = GradientDrawable().apply {
                    setColor(primaryColor)
                    cornerRadius = (dp(5) * scale).coerceAtLeast(dp(2).toFloat())
                }
            }
        }

    private fun verticalContainer(scale: Float) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        val pad = (dp(10) * scale).toInt().coerceAtLeast(dp(2))
        setPadding(pad, pad, pad, pad)
    }

    private fun text(value: String, sizeSp: Float, color: Int, style: Int) = TextView(context).apply {
        text = value
        gravity = Gravity.CENTER
        includeFontPadding = false
        isSingleLine = true
        maxLines = 1
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTypeface(null, style)
    }

    private fun safeFormat(pattern: String, date: java.util.Date): String =
        try {
            SimpleDateFormat(pattern, Locale.getDefault()).format(date).replace(" ", "\u00A0")
        } catch (_: Exception) { "" }

    private fun normalizeDatePattern(pattern: String): String =
        pattern.replace("YYYY", "yyyy").replace("DD", "dd")

    private fun secondaryColor(): Int =
        if (ColorUtils.calculateLuminance(primaryColor) < 0.45) Color.DKGRAY else Color.LTGRAY

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(updateRunnable)
        updateRunnable.run()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(updateRunnable)
        super.onDetachedFromWindow()
    }
}
