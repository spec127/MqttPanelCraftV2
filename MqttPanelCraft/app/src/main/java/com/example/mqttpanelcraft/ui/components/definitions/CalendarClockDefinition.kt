package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.CalendarClockView

object CalendarClockDefinition : IComponentDefinition {
    override val type: String = "CALENDAR"
    override val defaultSize: Size = Size(180, 80)
    override val labelPrefix: String = "calendar"
    override val iconResId: Int = android.R.drawable.ic_menu_today
    override val group: String = "DISPLAY"
    override val propertiesLayoutId: Int = R.layout.layout_prop_calendar

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "mode" to "COMBO",
        "date_format" to "YYYY-MM-DD",
        "time_format" to "HH:mm",
        "text_color" to "#FFFFFF",
        "bg_color" to "#33000000",
        "text_size" to "16"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val view = CalendarClockView(context).apply {
            tag = "target_calendar"
            this.isEditMode = isEditMode
        }
        container.addView(view, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val calView = container.findViewWithTag<CalendarClockView>("target_calendar") ?: return

        calView.mode = data.props["mode"] ?: "COMBO"
        calView.dateFormatStr = data.props["date_format"] ?: "YYYY-MM-DD"
        calView.timeFormatStr = data.props["time_format"] ?: "HH:mm"
        calView.textColorHex = data.props["text_color"] ?: "#FFFFFF"
        calView.bgColorHex = data.props["bg_color"] ?: "#33000000"
        
        val sizeStr = data.props["text_size"] ?: "16"
        calView.baseTextSize = sizeStr.toFloatOrNull() ?: 16f
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val binder = CommonPropBinder
        
        binder.bindDropdown(
            panelView, R.id.spCalendarMode, "mode", data, onUpdate,
            listOf("純時鐘", "純日曆", "日曆與時鐘"),
            mapOf("純時鐘" to "CLOCK", "純日曆" to "CALENDAR", "日曆與時鐘" to "COMBO"),
            defaultValue = "COMBO"
        )
        
        binder.bindDropdown(
            panelView, R.id.spDateFormat, "date_format", data, onUpdate,
            listOf("YYYY-MM-DD", "MM/DD/YYYY", "DD/MM/YYYY"),
            defaultValue = "YYYY-MM-DD"
        )
        
        binder.bindDropdown(
            panelView, R.id.spTimeFormat, "time_format", data, onUpdate,
            listOf("HH:mm:ss", "HH:mm", "hh:mm a"),
            defaultValue = "HH:mm"
        )
        
        binder.bindDropdown(
            panelView, R.id.spTextSize, "text_size", data, onUpdate,
            listOf("12", "14", "16", "18", "24", "32"),
            defaultValue = "16"
        )
        
        binder.bindColorPalette(
            panelView, R.id.containerTextColorPalette, "text_color", data, onUpdate,
            label = "文字顏色", defaultColor = "#FFFFFF"
        )
        
        binder.bindColorPalette(
            panelView, R.id.containerBgColorPalette, "bg_color", data, onUpdate,
            label = "背景顏色", defaultColor = "#33000000"
        )
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
    ) {}
}
