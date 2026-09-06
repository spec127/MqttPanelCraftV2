package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.example.mqttpanelcraft.ui.components.InterceptableFrameLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockTriggerView(context: Context) : FrameLayout(context) {
    var componentId: Int = android.view.View.NO_ID
    var isEditMode: Boolean = false
    var onLocalTrigger: ((String) -> Unit)? = null

    private val content = LinearLayout(context)
    private val mainText = TextView(context)
    private val subText = TextView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var mode = "TIME"
    private var timeFormat = "HH:mm"
    private var countdownSeconds = 60L
    private var scheduleTime = "07:30"
    private var triggerValue = "TRIGGER"
    private var visualStyle = "DIGITAL"
    private var primaryColor = Color.parseColor("#7B1FA2")
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var countdownEndAt = 0L
    private var countdownTriggered = false
    private var lastScheduleDate = ""
    private var wasRuntime = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNow()
            handler.postDelayed(this, 1000L)
        }
    }

    init {
        setWillNotDraw(false)
        content.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        mainText.apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 38f)
            setTypeface(null, Typeface.NORMAL)
            isSingleLine = true
        }
        content.addView(mainText)
        addView(content)
    }

    fun setConfig(
        newMode: String,
        newTimeFormat: String,
        newCountdownSeconds: Long,
        newScheduleTime: String,
        newTriggerValue: String,
        newVisualStyle: String,
        colorHex: String
    ) {
        val safeMode = if (newMode in setOf("TIME", "COUNTDOWN", "SCHEDULE")) newMode else "TIME"
        val safeSeconds = newCountdownSeconds.coerceAtLeast(1L)
        val shouldResetCountdown = mode != safeMode || countdownSeconds != safeSeconds
        mode = safeMode
        timeFormat = newTimeFormat.ifBlank { "HH:mm" }
        countdownSeconds = safeSeconds
        scheduleTime = newScheduleTime.ifBlank { "07:30" }
        triggerValue = newTriggerValue.ifBlank { "TRIGGER" }
        visualStyle = when (newVisualStyle) {
            "ANALOG" -> "ANALOG"
            "COMBO" -> "COMBO"
            else -> "DIGITAL"
        }
        primaryColor = try { Color.parseColor(colorHex) } catch (_: Exception) { Color.parseColor("#7B1FA2") }
        if (shouldResetCountdown && mode == "COUNTDOWN") resetCountdown()
        updateColors()
        updateNow()
    }

    private fun resetCountdown() {
        countdownEndAt = System.currentTimeMillis() + countdownSeconds * 1000L
        countdownTriggered = false
    }

    private fun currentScale(): Float {
        val h = height
        return if (h > 0) (h / (100f * resources.displayMetrics.density)).coerceIn(0.2f, 3.0f) else 1f
    }

    private fun updateNow() {
        val scale = currentScale()
        val runtime = !((parent as? InterceptableFrameLayout)?.isEditMode ?: false)
        if (runtime && !wasRuntime && mode == "COUNTDOWN") resetCountdown()
        wasRuntime = runtime

        val isCombo = visualStyle == "COMBO"
        val isAnalog = visualStyle == "ANALOG"

        if (isAnalog) {
            content.visibility = GONE
        } else {
            content.visibility = VISIBLE
            if (isCombo) {
                content.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                val padH = (dp(8) * scale).toInt().coerceAtLeast(dp(2))
                val padB = (dp(6) * scale).toInt().coerceAtLeast(dp(2))
                content.setPadding(padH, 0, padH, padB)
                mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, (18f * scale).coerceAtLeast(8.5f))
            } else {
                content.gravity = Gravity.CENTER
                val padH = (dp(10) * scale).toInt().coerceAtLeast(dp(2))
                val padV = (dp(6) * scale).toInt().coerceAtLeast(dp(2))
                content.setPadding(padH, padV, padH, padV)
                mainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, (36f * scale).coerceAtLeast(11f))
            }
        }

        when (mode) {
            "COUNTDOWN" -> updateCountdown(runtime)
            "SCHEDULE" -> updateSchedule(runtime)
            else -> {
                mainText.text = safeTimeFormat(timeFormat, Date())
            }
        }
        invalidate()
    }

    private fun updateCountdown(runtime: Boolean) {
        com.example.mqttpanelcraft.MqttRepository.getClockDeadline(componentId)?.let { countdownEndAt = it }
        if (countdownEndAt == 0L) resetCountdown()
        val remaining = ((countdownEndAt - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L)
        val hours = remaining / 3600L
        val minutes = (remaining % 3600L) / 60L
        val seconds = remaining % 60L
        mainText.text = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        if (runtime && remaining == 0L && !countdownTriggered) {
            countdownTriggered = true
            onLocalTrigger?.invoke(triggerValue)
        }
    }

    private fun updateSchedule(runtime: Boolean) {
        val now = Date()
        mainText.text = safeTimeFormat(timeFormat, now)
        val currentTime = safeTimeFormat("HH:mm", now)
        val currentDate = safeTimeFormat("yyyy-MM-dd", now)
        if (runtime && currentTime == scheduleTime && currentDate != lastScheduleDate) {
            lastScheduleDate = currentDate
            onLocalTrigger?.invoke(triggerValue)
        }
    }

    private fun safeTimeFormat(pattern: String, date: Date): String =
        try { SimpleDateFormat(pattern, Locale.getDefault()).format(date) } catch (_: Exception) { "--:--" }

    private fun updateColors() {
        val scale = currentScale()
        val darkAccent = ColorUtils.calculateLuminance(primaryColor) < 0.45
        background = GradientDrawable().apply {
            setColor(if (darkAccent) Color.WHITE else Color.rgb(28, 24, 30))
            setStroke(dp(1), ColorUtils.setAlphaComponent(primaryColor, 135))
            cornerRadius = (dp(10) * scale).coerceAtLeast(dp(3).toFloat())
        }
        mainText.setTextColor(primaryColor)
        clockPaint.color = primaryColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visualStyle != "ANALOG" && visualStyle != "COMBO") return
        if (mode != "TIME") return

        val isCombo = visualStyle == "COMBO"
        val centerX = width / 2f
        val centerY = if (isCombo) height * 0.38f else height / 2f
        val radius = if (isCombo) minOf(width * 0.38f, height * 0.28f) else minOf(width, height) * 0.36f

        clockPaint.style = Paint.Style.STROKE
        clockPaint.strokeWidth = dp(2).toFloat()
        canvas.drawCircle(centerX, centerY, radius, clockPaint)

        repeat(12) { index ->
            val angle = Math.PI * index / 6.0
            val inner = if (index % 3 == 0) radius * 0.78f else radius * 0.86f
            canvas.drawLine(
                centerX + Math.sin(angle).toFloat() * inner,
                centerY - Math.cos(angle).toFloat() * inner,
                centerX + Math.sin(angle).toFloat() * radius,
                centerY - Math.cos(angle).toFloat() * radius,
                clockPaint
            )
        }

        val now = java.util.Calendar.getInstance()
        val hourAngle = Math.PI * (now.get(java.util.Calendar.HOUR) + now.get(java.util.Calendar.MINUTE) / 60.0) / 6.0
        val minuteAngle = Math.PI * now.get(java.util.Calendar.MINUTE) / 30.0
        val strokeScale = if (isCombo) 0.75f else 1f
        clockPaint.strokeCap = Paint.Cap.ROUND
        clockPaint.strokeWidth = (dp(4) * strokeScale).toFloat().coerceAtLeast(dp(1.8f).toFloat())
        canvas.drawLine(centerX, centerY, centerX + Math.sin(hourAngle).toFloat() * radius * 0.5f, centerY - Math.cos(hourAngle).toFloat() * radius * 0.5f, clockPaint)
        clockPaint.strokeWidth = (dp(3) * strokeScale).toFloat().coerceAtLeast(dp(1.4f).toFloat())
        canvas.drawLine(centerX, centerY, centerX + Math.sin(minuteAngle).toFloat() * radius * 0.72f, centerY - Math.cos(minuteAngle).toFloat() * radius * 0.72f, clockPaint)
        clockPaint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, (dp(3) * strokeScale).toFloat().coerceAtLeast(dp(1.5f).toFloat()), clockPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateColors()
        updateNow()
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
