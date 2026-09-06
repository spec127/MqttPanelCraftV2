package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
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
        val allStyles = listOf("完整月曆", "今日大字", "日期+時間")
        val styleMap = mapOf(
            "完整月曆" to "MONTH",
            "今日大字" to "BIG_DATE",
            "日期+時間" to "DATE_TIME"
        )
        CommonPropBinder.bindDropdown(
            panelView, R.id.spVisualStyle, "visual_style", styleData, onUpdate,
            allStyles,
            styleMap,
            defaultValue = "MONTH"
        )
        val timeFormatContainer = panelView.findViewById<View>(R.id.containerTimeFormat)
        fun updateTimeFormatVisibility(style: String) {
            timeFormatContainer?.visibility = if (style == "DATE_TIME") View.VISIBLE else View.GONE
        }
        updateTimeFormatVisibility(normalizedStyle)
        panelView.findViewById<TextView>(R.id.spVisualStyle)?.addTextChangedListener(
            object : TextWatcher {
                override fun afterTextChanged(value: Editable?) {
                    updateTimeFormatVisibility(if (value?.toString() == "日期+時間") "DATE_TIME" else "")
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            }
        )
        CommonPropBinder.bindColorPalette(
            panelView, R.id.propColor, "color", data, onUpdate,
            label = "主題顏色", defaultColor = "#7B1FA2"
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
