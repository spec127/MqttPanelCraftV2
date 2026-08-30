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

    private fun render() {
        removeAllViews()
        background = GradientDrawable().apply {
            setColor(if (ColorUtils.calculateLuminance(primaryColor) < 0.45) Color.WHITE else Color.rgb(28, 24, 30))
            setStroke(dp(1), ColorUtils.setAlphaComponent(primaryColor, 135))
            cornerRadius = dp(10).toFloat()
        }

        when (visualStyle) {
            "BIG_DATE" -> renderBigDate()
            "DATE_TIME" -> renderDateTime()
            else -> renderMonth()
        }
    }

    private fun renderBigDate() {
        val now = Calendar.getInstance()
        val day = SimpleDateFormat("dd", Locale.getDefault()).format(now.time)
        val monthAndWeekday = SimpleDateFormat("M 月 · EEEE", Locale.getDefault()).format(now.time)
        addView(verticalContainer().apply {
            addView(text(day, 58f, primaryColor, Typeface.NORMAL))
            addView(text(monthAndWeekday, 14f, secondaryColor(), Typeface.NORMAL))
        })
    }

    private fun renderDateTime() {
        val now = Calendar.getInstance().time
        val date = safeFormat(dateFormat, now)
        val time = safeFormat(timeFormat, now)
        addView(verticalContainer().apply {
            addView(text(time, 38f, primaryColor, Typeface.NORMAL))
            addView(text(date, 14f, secondaryColor(), Typeface.NORMAL))
        })
    }

    private fun renderMonth() {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val monthTitle = SimpleDateFormat("yyyy 年 M 月", Locale.getDefault()).format(calendar.time)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        content.addView(text(monthTitle, 15f, primaryColor, Typeface.BOLD).apply {
            setPadding(0, 0, 0, dp(5))
        })

        val grid = GridLayout(context).apply {
            columnCount = 7
            rowCount = 7
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
            grid.addView(dayCell(label, secondaryColor(), false))
        }

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstColumn = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        repeat(firstColumn) { grid.addView(dayCell("", secondaryColor(), false)) }
        for (day in 1..daysInMonth) {
            grid.addView(dayCell(day.toString(), if (day == today) Color.WHITE else primaryColor, day == today))
        }
        val used = 7 + firstColumn + daysInMonth
        repeat((49 - used).coerceAtLeast(0)) { grid.addView(dayCell("", secondaryColor(), false)) }
        content.addView(grid)
        addView(content)
    }

    private fun dayCell(value: String, color: Int, selected: Boolean): TextView =
        text(value, 11f, color, Typeface.NORMAL).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
            if (selected) {
                background = GradientDrawable().apply {
                    setColor(primaryColor)
                    cornerRadius = dp(5).toFloat()
                }
            }
        }

    private fun verticalContainer() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setPadding(dp(10), dp(10), dp(10), dp(10))
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
