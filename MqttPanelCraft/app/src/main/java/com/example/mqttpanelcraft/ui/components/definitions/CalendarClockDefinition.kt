package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.components.prop.PropertyOption
import com.example.mqttpanelcraft.ui.views.CalendarDisplayView

object CalendarClockDefinition : IComponentDefinition {
    override val type: String = "CALENDAR"
    override val defaultSize: Size = Size(180, 180)
    override val labelPrefix: String = "calendar"
    override val displayNameResId: Int = R.string.component_label_calendar
    override val iconResId: Int = android.R.drawable.ic_menu_today
    override val group = ComponentGroup.DISPLAY
    override val propertiesLayoutId: Int = R.layout.layout_prop_calendar

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "date_format" to "YYYY-MM-DD",
        "time_format" to "HH:mm",
        "color" to "#7B1FA2",
        "visual_style" to "MONTH"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val view = CalendarDisplayView(context).apply {
            tag = "target_calendar"
            this.isEditMode = isEditMode
        }
        container.addView(view, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val calView = container.findViewWithTag<CalendarDisplayView>("target_calendar") ?: return
        calView.setConfig(
            normalizeStyle(data.props["visual_style"]),
            data.props["date_format"] ?: "YYYY-MM-DD",
            data.props["time_format"] ?: "HH:mm",
            data.props["color"] ?: "#7B1FA2"
        )
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        CommonPropBinder.bindDropdown(
            panelView, R.id.spDateFormat, "date_format", data, onUpdate,
            listOf("YYYY-MM-DD", "MM/DD/YYYY", "DD/MM/YYYY", "YYYY年MM月DD日"),
            defaultValue = "YYYY-MM-DD"
        )
        
        CommonPropBinder.bindDropdown(
            panelView, R.id.spTimeFormat, "time_format", data, onUpdate,
            listOf("HH:mm:ss", "HH:mm", "hh:mm a"),
            defaultValue = "HH:mm"
        )
        
        val normalizedStyle = normalizeStyle(data.props["visual_style"])
        if (data.props["visual_style"] != normalizedStyle) onUpdate("visual_style", normalizedStyle)
        val styleData = data.copy(props = data.props.toMutableMap().apply { put("visual_style", normalizedStyle) })
        val timeFormatContainer = panelView.findViewById<View>(R.id.containerTimeFormat)
        fun updateTimeFormatVisibility(style: String) {
            timeFormatContainer?.visibility = if (style == "DATE_TIME") View.VISIBLE else View.GONE
        }
        updateTimeFormatVisibility(normalizedStyle)
        CommonPropBinder.bindLocalizedDropdown(
            panelView,
            R.id.spVisualStyle,
            "visual_style",
            styleData,
            { key, value ->
                onUpdate(key, value)
                updateTimeFormatVisibility(value)
            },
            listOf(
                PropertyOption("MONTH", R.string.calendar_style_month),
                PropertyOption("BIG_DATE", R.string.calendar_style_big_date),
                PropertyOption("DATE_TIME", R.string.calendar_style_date_time)
            ),
            "MONTH"
        )
        CommonPropBinder.bindColorPalette(
            panelView, R.id.propColor, "color", data, onUpdate,
            label = panelView.context.getString(R.string.properties_label_theme_color),
            defaultColor = "#7B1FA2"
        )
    }

    private fun normalizeStyle(style: String?): String = when (style) {
        "BIG_DATE" -> "BIG_DATE"
        "DATE_TIME", "DIGITAL", "ANALOG" -> "DATE_TIME"
        else -> "MONTH"
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
