package com.example.mqttpanelcraft.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class AlignmentOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.BLUE // Customize color as needed
        style = Paint.Style.STROKE
        strokeWidth = 3f // dp calculation ideally
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    
    // Store lines to draw: list of (x1, y1, x2, y2)
    private val lines = mutableListOf<FloatArray>()

    fun clear() {
        lines.clear()
        invalidate()
    }

    fun addLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        lines.add(floatArrayOf(x1, y1, x2, y2))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (line in lines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], paint)
        }
    }
}
