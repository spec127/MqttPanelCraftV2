package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

/**
 * 圖表元件 (LineChartDefinition - 折線圖等)
 *
 * Design Intent:
 * 全新設計的多媒體圖表元件，支援即時接收 MQTT 數值並以動態折線與網格背景進行呈現。
 */
object LineChartDefinition : IComponentDefinition {

    override val type: String = "CHART"
    override val defaultSize: Size = Size(280, 180)
    override val labelPrefix: String = "chart"
    override val iconResId: Int = android.R.drawable.ic_menu_sort_by_size
    override val group: String = "DISPLAY"

    override val propertiesLayoutId: Int = R.layout.layout_prop_generic_color

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "color" to "#00BCD4",
        "theme_color" to "#00BCD4",
        "max_points" to "30"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val chartView = SimpleLineChartView(context).apply {
            tag = "target_chart"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(chartView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val chartView = (view as? FrameLayout)?.findViewWithTag<SimpleLineChartView>("target_chart") ?: return
        data.props["color"]?.let { colorHex ->
            try {
                chartView.lineColor = Color.parseColor(colorHex)
                chartView.invalidate()
            } catch (_: Exception) {}
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        // 綁定顏色調整面板
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // 圖表被動展示
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val chartView = (view as? FrameLayout)?.findViewWithTag<SimpleLineChartView>("target_chart") ?: return
        val value = payload.trim().toFloatOrNull()
        if (value != null) {
            chartView.addPoint(value)
        }
    }

    /**
     * 輕量級高品質折線圖渲染 View
     */
    private class SimpleLineChartView(context: Context) : View(context) {
        private val points = mutableListOf<Float>()
        private val maxPoints = 30
        var lineColor: Int = Color.parseColor("#00BCD4")

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val gridPaint = Paint().apply {
            color = Color.parseColor("#333333")
            strokeWidth = 2f
        }
        private val path = Path()

        init {
            // 初始化示範數據
            points.addAll(listOf(20f, 45f, 30f, 65f, 50f, 80f, 60f))
        }

        fun addPoint(value: Float) {
            points.add(value)
            while (points.size > maxPoints) {
                points.removeAt(0)
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.parseColor("#181818"))

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            // 繪製背景格線
            for (i in 1..4) {
                val y = h * i / 5f
                canvas.drawLine(0f, y, w, y, gridPaint)
            }

            if (points.isEmpty()) return

            val minVal = (points.minOrNull() ?: 0f) - 10f
            val maxVal = (points.maxOrNull() ?: 100f) + 10f
            val range = if (maxVal == minVal) 100f else maxVal - minVal

            linePaint.color = lineColor
            path.reset()

            points.forEachIndexed { i, valPoint ->
                val x = if (points.size == 1) w / 2f else i * w / (points.size - 1)
                val y = h - ((valPoint - minVal) / range * (h * 0.8f) + h * 0.1f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePaint)
        }
    }
}
