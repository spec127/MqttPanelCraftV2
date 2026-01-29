package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object LineChartDefinition : IComponentDefinition {
    
    override val type = "CHART"
    override val defaultSize = Size(300, 200)
    override val labelPrefix = "chart"
    override val iconResId = android.R.drawable.ic_menu_sort_by_size // Placeholder for Chart
    override val group = "DISPLAY"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        // Placeholder View: Just a Box with "Chart" text for now.
        // Implementing a real chart requires canvas drawing or library (MPAndroidChart).
        // Since no library is guaranteed, I'll use a custom View stub.
        val tv = TextView(context).apply {
            text = "Line Chart (Placeholder)"
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.LTGRAY)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        container.addView(tv, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {}

    override val propertiesLayoutId = 0 
    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {}

    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {}

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {
        // Collect data points?
    }
}
