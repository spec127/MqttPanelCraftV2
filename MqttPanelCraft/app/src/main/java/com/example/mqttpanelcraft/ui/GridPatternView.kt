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

        // Fix: Draw dot correctly centered in the tile so it tiles properly
        // without edge clipping, and make the dot size larger to be visible.
        val cx = spacing / 2f
        val cy = spacing / 2f
        val radius = (dotRadius * density) * 0.5f // 1dp diameter, smaller dots

        if (spacing <= 0) return

        val color = Color.parseColor("#EAEAEA") // Lighter color
        
        val bitmap = Bitmap.createBitmap(spacing, spacing, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        
        canvas.drawCircle(cx, cy, radius, dotPaint)
        
        patternBitmap = bitmap
        patternPaint.shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (patternBitmap != null) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), patternPaint)
        }
    }
}
