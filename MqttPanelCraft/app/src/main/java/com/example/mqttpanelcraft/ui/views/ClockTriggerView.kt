package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.Color
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
    var isEditMode: Boolean = false
    var onLocalTrigger: ((String) -> Unit)? = null

    private val mainText = TextView(context)
    private val subText = TextView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var mode = "TIME"
    private var timeFormat = "HH:mm"
    private var countdownSeconds = 60L
    private var scheduleTime = "07:30"
    private var triggerValue = "TRIGGER"
    private var primaryColor = Color.parseColor("#7B1FA2")
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
        val content = LinearLayout(context).apply {
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
        }
        subText.apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(6), 0, 0)
        }
        content.addView(mainText)
        content.addView(subText)
        addView(content)
    }

    fun setConfig(
        newMode: String,
        newTimeFormat: String,
        newCountdownSeconds: Long,
        newScheduleTime: String,
        newTriggerValue: String,
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
        primaryColor = try { Color.parseColor(colorHex) } catch (_: Exception) { Color.parseColor("#7B1FA2") }
        if (shouldResetCountdown && mode == "COUNTDOWN") resetCountdown()
        updateColors()
        updateNow()
    }

    private fun resetCountdown() {
        countdownEndAt = System.currentTimeMillis() + countdownSeconds * 1000L
        countdownTriggered = false
    }

    private fun updateNow() {
        val runtime = !((parent as? InterceptableFrameLayout)?.isEditMode ?: false)
        if (runtime && !wasRuntime && mode == "COUNTDOWN") resetCountdown()
        wasRuntime = runtime
        when (mode) {
            "COUNTDOWN" -> updateCountdown(runtime)
            "SCHEDULE" -> updateSchedule(runtime)
            else -> {
                mainText.text = safeTimeFormat(timeFormat, Date())
                subText.text = "目前時間"
            }
        }
    }

    private fun updateCountdown(runtime: Boolean) {
        if (countdownEndAt == 0L) resetCountdown()
        val remaining = ((countdownEndAt - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L)
        val hours = remaining / 3600L
        val minutes = (remaining % 3600L) / 60L
        val seconds = remaining % 60L
        mainText.text = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        subText.text = if (remaining == 0L) "已觸發連結元件" else "倒數計時"
        if (runtime && remaining == 0L && !countdownTriggered) {
            countdownTriggered = true
            onLocalTrigger?.invoke(triggerValue)
        }
    }

    private fun updateSchedule(runtime: Boolean) {
        val now = Date()
        mainText.text = safeTimeFormat(timeFormat, now)
        subText.text = "定時 $scheduleTime"
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
        val darkAccent = ColorUtils.calculateLuminance(primaryColor) < 0.45
        background = GradientDrawable().apply {
            setColor(if (darkAccent) Color.WHITE else Color.rgb(28, 24, 30))
            setStroke(dp(1), ColorUtils.setAlphaComponent(primaryColor, 135))
            cornerRadius = dp(10).toFloat()
        }
        mainText.setTextColor(primaryColor)
        subText.setTextColor(if (darkAccent) Color.DKGRAY else Color.LTGRAY)
    }

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
