package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * 嚴格邊界裁切的 ScrollView (ClippedScrollView)
 * 透過 override draw 與 dispatchDraw 並調用 canvas.clipRect(scrollX, scrollY, scrollX + width, scrollY + height)，
 * 考慮視窗當前的捲動位移 (scrollX, scrollY)，從 2D/GPU 繪製管線底層嚴格禁止任何子視圖（含卡片、動畫、陰影等）渲染超出視窗的可視邊界。
 */
class ClippedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    override fun draw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipRect(scrollX, scrollY, scrollX + width, scrollY + height)
        super.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipRect(scrollX, scrollY, scrollX + width, scrollY + height)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
    }
}
