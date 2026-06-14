package com.example.mqttpanelcraft.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.Constants

class GridPatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val patternPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = false
    }

    private val gridSize = Constants.GRID_UNIT_DP // dp (space between dots)
    private val dotRadius = Constants.GRID_DOT_RADIUS_DP // dp (radius of dots)
    
    private var density = 1f
    private var patternBitmap: Bitmap? = null

    init {
        density = context.resources.displayMetrics.density
        updatePattern()
    }

    private fun updatePattern() {
        val spacing = (gridSize * density).toInt()
        val radius = dotRadius * density / 2 // visual adjustment

        if (spacing <= 0) return

        val color = ContextCompat.getColor(context, R.color.grid_dot_color)
        
        val bitmap = Bitmap.createBitmap(spacing, spacing, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        
        // Draw dot at top-left so it tiles correctly
        canvas.drawCircle(0f, 0f, radius, dotPaint)
        
        patternBitmap = bitmap
        patternPaint.shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (patternBitmap != null) {
            val bounds = canvas.clipBounds
            if (!bounds.isEmpty) {
                canvas.drawRect(bounds, patternPaint)
            } else {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), patternPaint)
            }
        }
    }
}
