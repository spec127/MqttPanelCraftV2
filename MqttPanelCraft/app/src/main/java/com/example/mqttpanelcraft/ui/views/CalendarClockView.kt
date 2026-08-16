package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarClockView(context: Context) : FrameLayout(context) {

    private val container: LinearLayout = LinearLayout(context)
    private val dateText: TextView = TextView(context)
    private val timeText: TextView = TextView(context)

    private val handler = Handler(Looper.getMainLooper())
    private var timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    var timeFormatStr: String = "HH:mm"
        set(value) {
            field = value
            try {
                timeFormatter = SimpleDateFormat(value, Locale.getDefault())
            } catch (e: Exception) {}
            timeText.text = timeFormatter.format(Date())
        }
        
    var dateFormatStr: String = "yyyy-MM-dd"
        set(value) {
            field = value
            try {
                dateFormatter = SimpleDateFormat(value, Locale.getDefault())
            } catch (e: Exception) {}
            dateText.text = dateFormatter.format(Date())
        }

    var isEditMode = false

    var mode: String = "COMBO"
        set(value) {
            field = value
            updateVisibility()
        }

    var textColorHex: String = "#FFFFFF"
        set(value) {
            field = value
            try {
                val color = Color.parseColor(value)
                dateText.setTextColor(color)
                timeText.setTextColor(color)
            } catch (e: Exception) {
            }
        }

    var bgColorHex: String = "#33000000"
        set(value) {
            field = value
            try {
                val color = Color.parseColor(value)
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 8f * resources.displayMetrics.density
                }
                background = drawable
            } catch (e: Exception) {
            }
        }

    var baseTextSize: Float = 16f
        set(value) {
            field = value
            updateTextSize(1f) // Will be scaled in onSizeChanged
        }

    private var currentScale: Float = 1f

    private val updateRunnable = object : Runnable {
        override fun run() {
            val now = Date()
            timeText.text = timeFormatter.format(now)
            dateText.text = dateFormatter.format(now)
            if (!isEditMode) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    init {
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER
        container.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        dateText.gravity = Gravity.CENTER
        dateText.includeFontPadding = false

        timeText.gravity = Gravity.CENTER
        timeText.includeFontPadding = false
        timeText.setTypeface(null, android.graphics.Typeface.BOLD)

        container.addView(dateText, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        container.addView(timeText, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(container)
    }

    private fun updateVisibility() {
        when (mode) {
            "CLOCK" -> {
                dateText.visibility = GONE
                timeText.visibility = VISIBLE
            }
            "CALENDAR" -> {
                dateText.visibility = VISIBLE
                timeText.visibility = GONE
            }
            else -> { // COMBO
                dateText.visibility = VISIBLE
                timeText.visibility = VISIBLE
            }
        }
    }

    private fun updateTextSize(scale: Float) {
        currentScale = scale
        val scaledSize = baseTextSize * scale
        dateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize * 0.7f)
        timeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize * 1.2f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        val baseWidth = 180f * density
        val scale = (w / baseWidth).coerceIn(0.5f, 3.0f)
        updateTextSize(scale)
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
