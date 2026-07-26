package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

/**
 * 圖形元件 (ShapeDefinition)
 *
 * Design Intent:
 * 作為多媒體與面板美化的幾何圖形裝飾元件，可顯示圓角矩形、圓形、方塊或線條等圖形，用於劃分區塊或裝飾面板。
 */
object ShapeDefinition : IComponentDefinition {

    override val type: String = "SHAPE"
    override val defaultSize: Size = Size(160, 100)
    override val labelPrefix: String = "shape"
    override val iconResId: Int = android.R.drawable.ic_menu_crop
    override val group: String = "DISPLAY"

    override val propertiesLayoutId: Int = R.layout.layout_prop_generic_color

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "color" to "#33FFFFFF",
        "theme_color" to "#33FFFFFF",
        "shape_type" to "ROUNDED_RECT"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val shapeView = ShapeView(context).apply {
            tag = "target_shape"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(shapeView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val shapeView = (view as? FrameLayout)?.findViewWithTag<ShapeView>("target_shape") ?: return

        data.props["shape_type"]?.let {
            shapeView.shapeType = it
        }

        data.props["color"]?.let { colorHex ->
            try {
                shapeView.shapeColor = Color.parseColor(colorHex)
                shapeView.invalidate()
            } catch (_: Exception) {}
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        // 綁定顏色調整
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {}

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // Graphic components no longer have MQ functionality
    }

    private class ShapeView(context: Context) : View(context) {
        var shapeType: String = "ROUNDED_RECT"
        var shapeColor: Int = Color.parseColor("#33FFFFFF")
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = shapeColor
            paint.style = Paint.Style.FILL

            rect.set(8f, 8f, width - 8f, height - 8f)
            when (shapeType.uppercase()) {
                "CIRCLE", "OVAL" -> canvas.drawOval(rect, paint)
                "RECT", "RECTANGLE" -> canvas.drawRect(rect, paint)
                else -> canvas.drawRoundRect(rect, 24f, 24f, paint)
            }
        }
    }
}
