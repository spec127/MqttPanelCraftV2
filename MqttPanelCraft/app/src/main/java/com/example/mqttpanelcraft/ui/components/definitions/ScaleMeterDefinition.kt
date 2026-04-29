package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.View
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object ScaleMeterDefinition : IComponentDefinition {
    override val type: String = "SCALE_METER"
    override val defaultSize: Size = Size(100, 200)
    override val labelPrefix: String = "scale"
    override val iconResId: Int = android.R.drawable.ic_menu_sort_by_size
    override val group: String = "SENSOR"
    override val propertiesLayoutId: Int = 0

    override fun createView(context: Context, isEditMode: Boolean): View {
        return View(context).apply { setBackgroundColor(0x88888888.toInt()) }
    }

    override fun onUpdateView(view: View, data: ComponentData) {}
    
    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {}

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
        updateProp: (key: String, value: String) -> Unit
    ) {}
}
