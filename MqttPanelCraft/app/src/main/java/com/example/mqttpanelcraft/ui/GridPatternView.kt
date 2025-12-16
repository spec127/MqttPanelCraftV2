package com.example.mqttpanelcraft.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.mqttpanelcraft.R

class GridPatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val gridSize = 40f // dp (space between dots)
    private val dotRadius = 6f // dp (radius of dots)
    
    private var density = 1f

    init {
        density = context.resources.displayMetrics.density
        updateColor()
    }

    private fun updateColor() {
        paint.color = ContextCompat.getColor(context, R.color.grid_dot_color)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val spacing = gridSize * density
        val radius = dotRadius * density / 2 // visual adjustment

        val width = width.toFloat()
        val height = height.toFloat()

        val startX = (width % spacing) / 2
        val startY = (height % spacing) / 2

        var x = startX
        while (x < width) {
            var y = startY
            while (y < height) {
                canvas.drawCircle(x, y, radius, paint)
                y += spacing
            }
            x += spacing
        }
    }
}
