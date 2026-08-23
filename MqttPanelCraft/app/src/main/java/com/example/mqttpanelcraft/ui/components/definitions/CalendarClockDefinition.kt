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
    override val defaultSize: Size = Size(150, 180)
    override val labelPrefix: String = "calendar"
    override val iconResId: Int = android.R.drawable.ic_menu_today
    override val group: String = "DISPLAY"
    override val propertiesLayoutId: Int = R.layout.layout_prop_calendar

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "mode" to "COMBO",
        "date_format" to "YYYY-MM-DD",
        "time_format" to "HH:mm",
        "color" to "#7B1FA2",
        "visual_style" to "DIGITAL"
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
        calView.primaryColorHex = data.props["color"] ?: "#7B1FA2"
        calView.visualStyle = data.props["visual_style"] ?: "DIGITAL"
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val binder = CommonPropBinder
        
        binder.bindDropdown(
            panelView, R.id.spCalendarMode, "mode", data, onUpdate,
            listOf("純時間", "純日期", "時間+日期"),
            mapOf("純時間" to "CLOCK", "純日期" to "CALENDAR", "時間+日期" to "COMBO"),
            defaultValue = "COMBO"
        )
        
        binder.bindDropdown(
            panelView, R.id.spDateFormat, "date_format", data, onUpdate,
            listOf("YYYY-MM-DD", "MM/DD/YYYY", "DD/MM/YYYY", "YYYY年MM月DD日"),
            defaultValue = "YYYY-MM-DD"
        )
        
        binder.bindDropdown(
            panelView, R.id.spTimeFormat, "time_format", data, onUpdate,
            listOf("HH:mm:ss", "HH:mm", "hh:mm a"),
            defaultValue = "HH:mm"
        )
        
        // Hide format input if not needed
        val containerDate = panelView.findViewById<View>(R.id.containerDateFormat)
        val containerTime = panelView.findViewById<View>(R.id.containerTimeFormat)
        
        fun updateFormatVisibility(currentMode: String) {
            containerDate?.visibility = if (currentMode == "CLOCK") View.GONE else View.VISIBLE
            containerTime?.visibility = if (currentMode == "CALENDAR") View.GONE else View.VISIBLE
        }
        
        updateFormatVisibility(data.props["mode"] ?: "COMBO")
        
        val spCalendarMode = panelView.findViewById<android.widget.TextView>(R.id.spCalendarMode)
        spCalendarMode?.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val valStr = s?.toString() ?: ""
                val currentMode = when {
                    valStr.contains("時間") && !valStr.contains("日期") -> "CLOCK"
                    valStr.contains("日期") && !valStr.contains("時間") -> "CALENDAR"
                    else -> "COMBO"
                }
                updateFormatVisibility(currentMode)
                
                val currentStyle = data.props["visual_style"] ?: "DIGITAL"
                if (currentMode == "CLOCK" && currentStyle == "BIG_DATE") {
                    data.props["visual_style"] = "DIGITAL"
                    onUpdate("visual_style", "DIGITAL")
                    panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.spVisualStyle)?.setText("電子看板", false)
                }
                if (currentMode == "CALENDAR" && currentStyle == "ANALOG") {
                    data.props["visual_style"] = "DIGITAL"
                    onUpdate("visual_style", "DIGITAL")
                    panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.spVisualStyle)?.setText("電子看板", false)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Style mapping & restrictions
        val allStyles = listOf("電子看板", "大字日期", "類比時鐘")
        val styleMap = mapOf(
            "大字日期" to "BIG_DATE",
            "電子看板" to "DIGITAL",
            "類比時鐘" to "ANALOG"
        )
        
        val mode = data.props["mode"] ?: "COMBO"
        val availableStyles = allStyles.filter { style ->
            if (mode == "CLOCK" && style == "大字日期") return@filter false
            if (mode == "CALENDAR" && style == "類比時鐘") return@filter false
            true
        }
        
        binder.bindDropdown(
            panelView, R.id.spVisualStyle, "visual_style", data, onUpdate,
            availableStyles,
            styleMap,
            defaultValue = "DIGITAL"
        )
        
        binder.bindColorPalette(
            panelView, R.id.propColor, "color", data, onUpdate,
            label = "主題顏色", defaultColor = "#7B1FA2"
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