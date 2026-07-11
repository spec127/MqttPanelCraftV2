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

    private var spacing = 0
    private var dotRadiusPx = 0f
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EAEAEA")
        style = Paint.Style.FILL
    }
    private val clipRect = Rect()

    init {
        val density = context.resources.displayMetrics.density
        spacing = (Constants.GRID_UNIT_DP * density).toInt()
        dotRadiusPx = (Constants.GRID_DOT_RADIUS_DP * density) * 0.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (spacing <= 0) return

        canvas.getClipBounds(clipRect)

        val startX = (clipRect.left / spacing) * spacing
        val startY = (clipRect.top / spacing) * spacing
        val endX = clipRect.right + spacing
        val endY = clipRect.bottom + spacing

        // Pre-allocate array for points could be an optimization, but direct drawCircle is also fine
        // since the clip bounds are relatively small (screen size).
        for (x in startX..endX step spacing) {
            for (y in startY..endY step spacing) {
                canvas.drawCircle(x + spacing / 2f, y + spacing / 2f, dotRadiusPx, dotPaint)
            }
        }
    }
}
