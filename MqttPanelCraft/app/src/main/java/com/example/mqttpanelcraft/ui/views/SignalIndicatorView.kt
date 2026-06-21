package com.example.mqttpanelcraft.ui.views

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class SignalIndicatorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class IconStyle(val displayName: String) {
        CELLULAR("行動網路"),
        WIFI("WiFi"),
        BATTERY("電池"),
        SPEAKER("喇叭"),
        FAN("風扇"),
        ARROWS_LEFT("向左箭頭"),
        ARROWS_RIGHT("向右箭頭"),
        STARS("星星"),
        HEARTS("愛心"),
        DROPS("水滴")
    }

    enum class ValueMapping { ABSOLUTE, RATIO }
    enum class ColorMode { SOLID, STEP, GRADIENT }
    enum class AlarmType { LOW, HIGH }

    var minValue: Float = 0f
        set(value) { field = value; invalidate() }
    var maxValue: Float = 100f
        set(value) { field = value; invalidate() }
    var maxLevels: Int = 4
        set(value) { field = value.coerceAtLeast(1); invalidate() }
    
    var value: Float = 0f
        set(v) { 
            if (field != v) {
                field = v
                checkAlarms()
                invalidate() 
            }
        }
        
    var showValue: Boolean = false
        set(value) { field = value; invalidate() }

    var iconStyle: IconStyle = IconStyle.BATTERY
        set(value) { field = value; invalidate() }
    var valueMapping: ValueMapping = ValueMapping.RATIO
        set(value) { field = value; invalidate() }

    var colorMode: ColorMode = ColorMode.SOLID
        set(value) { field = value; invalidate() }
    var themeColor: Int = Color.parseColor("#FF9800")
        set(value) { field = value; invalidate() }
    var colorStart: Int = Color.parseColor("#FF9800")
        set(value) { field = value; invalidate() }
    var colorEnd: Int = Color.parseColor("#F44336")
        set(value) { field = value; invalidate() }

    var alarmEnabled: Boolean = false
        set(value) { field = value; checkAlarms() }
    var alarmType: AlarmType = AlarmType.LOW
        set(value) { field = value; checkAlarms() }
    var alarmThreshold: Float = 0f
        set(value) { field = value; checkAlarms() }
    var alarmDuration: Float = 0f
        set(value) { field = value; checkAlarms() }

    // Alarm animation
    private var isAlarmActive = false
    private var alarmAlpha = 255
    private var alarmAnimator: ValueAnimator? = null
    private var alarmStartTime: Long = 0

    // Fan animation
    private var fanRotation = 0f
    private var fanAnimator: ValueAnimator? = null

    private val paintActive = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintInactive = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#444444") }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // startFanAnimation() removed per user request
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        alarmAnimator?.cancel()
    }

    // fanAnimation logic removed

    private fun checkAlarms() {
        if (!alarmEnabled) {
            stopAlarm()
            return
        }
        
        val trigger = if (alarmType == AlarmType.LOW) {
            value <= alarmThreshold
        } else {
            value >= alarmThreshold
        }
        
        if (trigger && !isAlarmActive) {
            isAlarmActive = true
            startAlarmAnimation()
        } else if (!trigger && isAlarmActive) {
            stopAlarm()
        }
    }

    private fun stopAlarm() {
        isAlarmActive = false
        alarmAnimator?.cancel()
        alarmAlpha = 255
        invalidate()
    }

    private fun startAlarmAnimation() {
        alarmAnimator?.cancel()
        alarmStartTime = System.currentTimeMillis()
        alarmAnimator = ValueAnimator.ofInt(255, 60, 255).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (alarmDuration > 0f) {
                    val elapsedSec = (System.currentTimeMillis() - alarmStartTime) / 1000f
                    if (elapsedSec >= alarmDuration) {
                        stopAlarm()
                        isAlarmActive = true // mark active so it doesn't trigger again while still in threshold
                        return@addUpdateListener
                    }
                }
                alarmAlpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    private fun getActiveColor(): Int {
        if (colorMode == ColorMode.SOLID) return themeColor
        
        // Ratio interpolation
        val fraction = if (valueMapping == ValueMapping.ABSOLUTE) {
            (value / maxLevels).coerceIn(0f, 1f)
        } else {
            val range = maxValue - minValue
            if (range <= 0) 0f else ((value - minValue) / range).coerceIn(0f, 1f)
        }

        if (colorMode == ColorMode.GRADIENT) {
            return ArgbEvaluator().evaluate(fraction, colorStart, colorEnd) as Int
        }
        
        // STEP mode: switch color midway? User didn't specify exactly. "階段" usually means discrete stops.
        // I will do simple 50% cutoff for Step.
        return if (fraction < 0.5f) colorStart else colorEnd
    }

    private fun getActiveLevels(): Int {
        val isBackFive = (maxLevels == 5)
        return if (valueMapping == ValueMapping.ABSOLUTE) {
            val v = value.toInt()
            if (isBackFive) {
                if (v <= 0) 0 else v.coerceIn(1, 5)
            } else {
                v.coerceIn(0, 4)
            }
        } else {
            val range = maxValue - minValue
            if (range <= 0f) {
                if (isBackFive) 1 else 0
            } else {
                val pct = ((value - minValue) / range).coerceIn(0f, 1f)
                if (isBackFive) {
                    Math.round(pct * 4) + 1
                } else {
                    Math.round(pct * 4)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val activeLevels = getActiveLevels()
        val c = getActiveColor()
        paintActive.color = Color.argb(alarmAlpha, Color.red(c), Color.green(c), Color.blue(c))
        paintInactive.color = Color.argb((alarmAlpha * 0.3f).toInt(), Color.red(c), Color.green(c), Color.blue(c))

        val iconRect = RectF()
        if (showValue) {
            paintText.textSize = h * 0.35f
            paintText.color = c // text does not blink, uses active color
            val textStr = if (value % 1f == 0f) value.toInt().toString() else String.format("%.1f", value)
            val textW = paintText.measureText(textStr)
            
            val iconSize = Math.min(w * 0.6f, h * 0.9f)
            val gap = 12f * resources.displayMetrics.density
            val totalW = iconSize + gap + textW
            val startX = (w - totalW) / 2f
            iconRect.set(startX, (h - iconSize)/2f, startX + iconSize, (h + iconSize)/2f)
            
            canvas.drawText(textStr, startX + iconSize + gap + textW/2f, h/2f - (paintText.descent() + paintText.ascent())/2f, paintText)
        } else {
            val isBackFive = iconStyle in listOf(IconStyle.ARROWS_LEFT, IconStyle.ARROWS_RIGHT, IconStyle.STARS, IconStyle.HEARTS, IconStyle.DROPS)
            if (isBackFive) {
                val paddingX = w * 0.05f
                val paddingY = h * 0.05f
                iconRect.set(paddingX, paddingY, w - paddingX, h - paddingY)
            } else {
                val iconSize = Math.min(w, h) * 0.9f
                iconRect.set((w - iconSize)/2f, (h - iconSize)/2f, (w + iconSize)/2f, (h + iconSize)/2f)
            }
        }

        when (iconStyle) {
            IconStyle.CELLULAR -> drawCellular(canvas, iconRect, activeLevels, maxLevels)
            IconStyle.WIFI -> drawWifi(canvas, iconRect.centerX(), iconRect.centerY(), iconRect.width()/2f, activeLevels, maxLevels)
            IconStyle.BATTERY -> drawBattery(canvas, iconRect, activeLevels, maxLevels)
            IconStyle.SPEAKER -> drawSpeaker(canvas, iconRect, activeLevels, maxLevels)
            IconStyle.FAN -> drawFan(canvas, iconRect.centerX(), iconRect.centerY(), iconRect.width()/2f, activeLevels, maxLevels)
            IconStyle.ARROWS_LEFT -> drawArrows(canvas, iconRect, activeLevels, maxLevels, true)
            IconStyle.ARROWS_RIGHT -> drawArrows(canvas, iconRect, activeLevels, maxLevels, false)
            IconStyle.STARS -> drawShapes(canvas, iconRect, activeLevels, maxLevels, "star")
            IconStyle.HEARTS -> drawShapes(canvas, iconRect, activeLevels, maxLevels, "heart")
            IconStyle.DROPS -> drawShapes(canvas, iconRect, activeLevels, maxLevels, "drop")
        }
    }

    private fun getPaintForLevel(levelIndex: Int, total: Int, isActive: Boolean): Paint {
        val basePaint = if (isActive) paintActive else paintInactive
        if (!isActive) return basePaint

        // Base active paint config
        val c = getActiveColor()
        basePaint.color = Color.argb(alarmAlpha, Color.red(c), Color.green(c), Color.blue(c))

        if (colorMode == ColorMode.GRADIENT && total > 1) {
            val fraction = levelIndex.toFloat() / (total - 1)
            val stepColor = ArgbEvaluator().evaluate(fraction, colorStart, colorEnd) as Int
            basePaint.color = Color.argb(alarmAlpha, Color.red(stepColor), Color.green(stepColor), Color.blue(stepColor))
        } else if (colorMode == ColorMode.STEP) {
            val fraction = levelIndex.toFloat() / (total - 1)
            val stepColor = if (fraction < 0.5f) colorStart else colorEnd
            basePaint.color = Color.argb(alarmAlpha, Color.red(stepColor), Color.green(stepColor), Color.blue(stepColor))
        }

        return basePaint
    }

    private fun drawCellular(canvas: Canvas, r: RectF, active: Int, total: Int) {
        val gap = r.width() * 0.1f
        val barW = (r.width() - gap * (total - 1)) / total
        for (i in 0 until total) {
            val h = r.height() * ((i + 1).toFloat() / total)
            val left = r.left + i * (barW + gap)
            val rect = RectF(left, r.bottom - h, left + barW, r.bottom)
            val paint = getPaintForLevel(i, total, i < active)
            canvas.drawRoundRect(rect, barW*0.3f, barW*0.3f, paint)
        }
    }

    private fun drawWifi(canvas: Canvas, r: RectF, active: Int, total: Int) {
        val cx = r.centerX()
        val cy = r.bottom
        val maxRad = r.width() / 2f
        
        paintActive.style = Paint.Style.STROKE
        paintActive.strokeWidth = r.width() * 0.1f
        paintActive.strokeCap = Paint.Cap.ROUND
        paintInactive.style = Paint.Style.STROKE
        paintInactive.strokeWidth = r.width() * 0.1f
        paintInactive.strokeCap = Paint.Cap.ROUND

        for (i in 0 until total) {
            val rad = maxRad * ((i + 1).toFloat() / total)
            val paint = getPaintForLevel(i, total, i < active)
            
            if (i == 0) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy - r.width()*0.05f, rad, paint)
                paint.style = Paint.Style.STROKE
            } else {
                val rect = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
                canvas.drawArc(rect, -135f, 90f, false, paint)
            }
        }
        paintActive.style = Paint.Style.FILL
        paintInactive.style = Paint.Style.FILL
    }

    private fun drawBattery(canvas: Canvas, r: RectF, active: Int, total: Int) {
        val tipW = r.width() * 0.1f
        val tipH = r.height() * 0.3f
        val bodyR = RectF(r.left, r.top, r.right - tipW, r.bottom)
        val tipR = RectF(bodyR.right, r.centerY() - tipH/2, r.right, r.centerY() + tipH/2)
        
        paintInactive.style = Paint.Style.STROKE
        paintInactive.strokeWidth = r.height() * 0.08f
        canvas.drawRoundRect(bodyR, r.height()*0.1f, r.height()*0.1f, paintInactive)
        paintInactive.style = Paint.Style.FILL
        canvas.drawRoundRect(tipR, tipW*0.2f, tipW*0.2f, paintInactive)

        val pad = r.height() * 0.15f
        val gap = r.width() * 0.04f
        val innerW = bodyR.width() - pad*2
        val innerH = bodyR.height() - pad*2
        val cellW = (innerW - gap*(total-1)) / total
        
        for (i in 0 until total) {
            val left = bodyR.left + pad + i*(cellW+gap)
            val rect = RectF(left, bodyR.top + pad, left + cellW, bodyR.top + pad + innerH)
            val paint = getPaintForLevel(i, total, i < active)
            canvas.drawRoundRect(rect, cellW*0.2f, cellW*0.2f, paint)
        }
    }

    private fun drawSpeaker(canvas: Canvas, r: RectF, active: Int, total: Int) {
        val spkW = r.width() * 0.35f
        val spkR = RectF(r.left, r.centerY() - spkW/2, r.left + spkW*0.6f, r.centerY() + spkW/2)
        
        val spkPath = Path().apply {
            moveTo(spkR.left, spkR.top)
            lineTo(spkR.right, spkR.top)
            lineTo(r.left + spkW, r.top + r.height()*0.2f)
            lineTo(r.left + spkW, r.bottom - r.height()*0.2f)
            lineTo(spkR.right, spkR.bottom)
            lineTo(spkR.left, spkR.bottom)
            close()
        }
        val pBase = if (active > 0) paintActive else paintInactive
        canvas.drawPath(spkPath, pBase)

        val waveLines = total - 1
        if (waveLines <= 0) return

        val cx = r.left + spkW
        val cy = r.centerY()
        val maxRad = r.width() - spkW

        paintActive.style = Paint.Style.STROKE
        paintActive.strokeWidth = r.width() * 0.08f
        paintActive.strokeCap = Paint.Cap.ROUND
        paintInactive.style = Paint.Style.STROKE
        paintInactive.strokeWidth = r.width() * 0.08f
        paintInactive.strokeCap = Paint.Cap.ROUND

        for (i in 1 .. waveLines) {
            val rad = maxRad * (i.toFloat() / waveLines)
            val rect = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
            val paint = if (i < active) paintActive else paintInactive
            canvas.drawArc(rect, -45f, 90f, false, paint)
        }
        paintActive.style = Paint.Style.FILL
        paintInactive.style = Paint.Style.FILL
    }

    private fun drawWifi(canvas: Canvas, cx: Float, cy: Float, rad: Float, active: Int, total: Int) {
        val dotRadius = rad * 0.15f
        canvas.drawCircle(cx, cy + rad * 0.6f, dotRadius, if (active > 0) paintActive else paintInactive)
        
        val arcSpacing = rad * 0.3f
        paintActive.style = Paint.Style.STROKE
        paintActive.strokeWidth = rad * 0.15f
        paintInactive.style = Paint.Style.STROKE
        paintInactive.strokeWidth = rad * 0.15f
        
        for (i in 1 until total) {
            val arcRad = dotRadius + i * arcSpacing
            val oval = RectF(cx - arcRad, cy + rad * 0.6f - arcRad, cx + arcRad, cy + rad * 0.6f + arcRad)
            val paint = getPaintForLevel(i, total, i < active)
            // Draw arc for wifi, sweep angle 90 degrees, centered at top (-135 to -45)
            canvas.drawArc(oval, -135f, 90f, false, paint)
        }
        paintActive.style = Paint.Style.FILL
        paintInactive.style = Paint.Style.FILL
    }

    private fun drawFan(canvas: Canvas, cx: Float, cy: Float, rad: Float, active: Int, total: Int) {
        val pBase = if (active > 0) paintActive else paintInactive
        val centerRad = rad * 0.15f
        
        // Save current style
        val originalStyleActive = paintActive.style
        val originalStyleInactive = paintInactive.style
        
        paintActive.style = Paint.Style.STROKE
        paintActive.strokeWidth = rad * 0.1f
        paintInactive.style = Paint.Style.STROKE
        paintInactive.strokeWidth = rad * 0.1f

        canvas.drawCircle(cx, cy, centerRad, pBase)

        if (active == 0) {
            paintActive.style = originalStyleActive
            paintInactive.style = originalStyleInactive
            return
        }

        val blades = 4 // Image 2 is a 4-blade fan
        val angleStep = 360f / blades
        val bladeRect = RectF(cx - rad*0.25f, cy - rad*0.9f, cx + rad*0.25f, cy - rad*0.15f)
        
        for (i in 0 until blades) {
            val paint = getPaintForLevel(i, blades, i < active)
            
            canvas.save()
            canvas.rotate(i * angleStep, cx, cy)
            canvas.drawOval(bladeRect, paint)
            canvas.restore()
        }
        
        paintActive.style = originalStyleActive
        paintInactive.style = originalStyleInactive
    }

    private fun drawArrows(canvas: Canvas, r: RectF, active: Int, total: Int, isLeft: Boolean) {
        val totalUnits = total + (total - 1) * 0.25f
        val maxItemW = r.width() / totalUnits
        val itemSize = Math.min(maxItemW, r.height())
        val gap = itemSize * 0.25f
        val groupW = itemSize * total + gap * (total - 1)
        val startX = r.left + (r.width() - groupW) / 2f
        val startY = r.centerY()
        
        paintActive.style = Paint.Style.STROKE
        paintActive.strokeWidth = itemSize * 0.15f
        paintActive.strokeJoin = Paint.Join.ROUND
        paintActive.strokeCap = Paint.Cap.ROUND
        paintInactive.style = Paint.Style.STROKE
        paintInactive.strokeWidth = itemSize * 0.15f
        paintInactive.strokeJoin = Paint.Join.ROUND
        paintInactive.strokeCap = Paint.Cap.ROUND

        for (i in 0 until total) {
            val left = startX + i * (itemSize + gap)
            val rect = RectF(left, startY - itemSize/2, left + itemSize, startY + itemSize/2)
            val paint = getPaintForLevel(i, total, i < active)
            
            val path = android.graphics.Path()
            val insetX = itemSize * 0.25f
            val insetY = rect.height() * 0.25f
            if (isLeft) {
                path.moveTo(rect.right - insetX, rect.top + insetY)
                path.lineTo(rect.left + insetX, rect.centerY())
                path.lineTo(rect.right - insetX, rect.bottom - insetY)
            } else {
                path.moveTo(rect.left + insetX, rect.top + insetY)
                path.lineTo(rect.right - insetX, rect.centerY())
                path.lineTo(rect.left + insetX, rect.bottom - insetY)
            }
            
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = rect.height() * 0.15f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
        }
        paintActive.style = Paint.Style.FILL
        paintInactive.style = Paint.Style.FILL
    }

    private fun drawShapes(canvas: Canvas, r: RectF, active: Int, total: Int, type: String) {
        val totalUnits = total + (total - 1) * 0.25f
        val maxItemW = r.width() / totalUnits
        val itemSize = Math.min(maxItemW, r.height())
        val gap = itemSize * 0.25f
        val groupW = itemSize * total + gap * (total - 1)
        val startX = r.left + (r.width() - groupW) / 2f
        val startY = r.centerY()
        
        for (i in 0 until total) {
            val left = startX + i * (itemSize + gap)
            val rect = RectF(left, startY - itemSize/2, left + itemSize, startY + itemSize/2)
            val paint = getPaintForLevel(i, total, i < active)
            
            val path = android.graphics.Path()
            val cx = rect.centerX()
            val cy = rect.centerY()
            val w = rect.width()
            val h = rect.height()
            
            when (type) {
                "star" -> {
                    val outerRad = Math.min(w, h) * 0.45f
                    val innerRad = outerRad * 0.382f
                    for (j in 0 until 10) {
                        val angle = Math.toRadians((j * 36 - 90).toDouble())
                        val rad = if (j % 2 == 0) outerRad else innerRad
                        val x = (cx + rad * Math.cos(angle)).toFloat()
                        val y = (cy + rad * Math.sin(angle)).toFloat()
                        if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                }
                "heart" -> {
                    path.moveTo(cx, cy - h * 0.15f)
                    path.cubicTo(
                        cx + w * 0.55f, cy - h * 0.6f,
                        cx + w * 0.65f, cy + h * 0.15f,
                        cx, cy + h * 0.45f
                    )
                    path.cubicTo(
                        cx - w * 0.65f, cy + h * 0.15f,
                        cx - w * 0.55f, cy - h * 0.6f,
                        cx, cy - h * 0.15f
                    )
                    path.close()
                }
                "drop" -> {
                    path.moveTo(cx, rect.top + h * 0.05f)
                    path.cubicTo(
                        cx + w * 0.5f, rect.top + h * 0.45f,
                        cx + w * 0.5f, rect.bottom - h * 0.05f,
                        cx, rect.bottom - h * 0.05f
                    )
                    path.cubicTo(
                        cx - w * 0.5f, rect.bottom - h * 0.05f,
                        cx - w * 0.5f, rect.top + h * 0.45f,
                        cx, rect.top + h * 0.05f
                    )
                    path.close()
                }
            }
            canvas.drawPath(path, paint)
        }
    }
}
