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
import com.example.mqttpanelcraft.ui.views.ClockTriggerView
import com.google.android.material.button.MaterialButtonToggleGroup

object CalendarClockDefinition : IComponentDefinition {
    const val FAMILY_CALENDAR = "CALENDAR"
    const val FAMILY_CLOCK = "CLOCK"

    override val type: String = "CALENDAR"
    override val defaultSize: Size = Size(180, 180)
    override val labelPrefix: String = "calendar"
    override val displayNameResId: Int = R.string.component_label_calendar_clock
    override val iconResId: Int = android.R.drawable.ic_menu_today
    override val group = ComponentGroup.DISPLAY
    override val propertiesLayoutId: Int = R.layout.layout_prop_calendar

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "family_kind" to FAMILY_CALENDAR,
        "date_format" to "YYYY-MM-DD",
        "time_format" to "HH:mm",
        "color" to "#7B1FA2",
        "visual_style" to "MONTH",
        "clock_mode" to "TIME",
        "countdown_seconds" to "60",
        "schedule_time" to "07:30",
        "trigger_value" to "TRIGGER",
        "linked_components" to ""
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val match = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(CalendarDisplayView(context).apply {
            tag = "target_calendar"
            this.isEditMode = isEditMode
            layoutParams = match
        }, 0)
        container.addView(ClockTriggerView(context).apply {
            tag = "target_clock"
            this.isEditMode = isEditMode
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val calView = container.findViewWithTag<CalendarDisplayView>("target_calendar")
        val clockView = container.findViewWithTag<ClockTriggerView>("target_clock")
        val isClock = familyKind(data) == FAMILY_CLOCK
        calView?.visibility = if (isClock) View.GONE else View.VISIBLE
        clockView?.visibility = if (isClock) View.VISIBLE else View.GONE
        if (isClock) {
            clockView?.componentId = data.id
            clockView?.isEditMode = calView?.isEditMode == true
            clockView?.setConfig(
                data.props["clock_mode"] ?: "TIME",
                data.props["time_format"] ?: "HH:mm",
                data.props["countdown_seconds"]?.toLongOrNull() ?: 60L,
                data.props["schedule_time"] ?: "07:30",
                clockStyle(data.props["visual_style"]),
                data.props["color"] ?: "#7B1FA2"
            )
        } else {
            calView?.setConfig(
                normalizeCalendarStyle(data.props["visual_style"]),
                data.props["date_format"] ?: "YYYY-MM-DD",
                data.props["time_format"] ?: "HH:mm",
                data.props["color"] ?: "#7B1FA2"
            )
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val calendarContainer = panelView.findViewById<View>(R.id.containerCalendarFamily)
        val clockContainer = panelView.findViewById<View>(R.id.containerClockFamily)
        val familyToggle = panelView.findViewById<MaterialButtonToggleGroup>(R.id.tgCalendarFamily)
        val initialFamily = familyKind(data)

        fun applyFamily(kind: String) {
            val isClock = kind == FAMILY_CLOCK
            calendarContainer?.visibility = if (isClock) View.GONE else View.VISIBLE
            clockContainer?.visibility = if (isClock) View.VISIBLE else View.GONE
        }
        applyFamily(initialFamily)
        familyToggle?.check(if (initialFamily == FAMILY_CLOCK) R.id.btnFamilyClock else R.id.btnFamilyCalendar)
        familyToggle?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val kind = if (checkedId == R.id.btnFamilyClock) FAMILY_CLOCK else FAMILY_CALENDAR
            onUpdate("family_kind", kind)
            if (kind == FAMILY_CLOCK) {
                if (clockStyle(data.props["visual_style"]) != data.props["visual_style"]) {
                    onUpdate("visual_style", "DIGITAL")
                }
            } else if (normalizeCalendarStyle(data.props["visual_style"]) != data.props["visual_style"]) {
                onUpdate("visual_style", "MONTH")
            }
            applyFamily(kind)
        }

        CommonPropBinder.bindDropdown(
            panelView, R.id.spDateFormat, "date_format", data, onUpdate,
            listOf("YYYY-MM-DD", "MM/DD/YYYY", "DD/MM/YYYY", "MMM d, yyyy"),
            defaultValue = "YYYY-MM-DD"
        )
        CommonPropBinder.bindDropdown(
            panelView, R.id.spTimeFormat, "time_format", data, onUpdate,
            listOf("HH:mm:ss", "HH:mm", "hh:mm a"),
            defaultValue = "HH:mm"
        )

        val normalizedStyle = normalizeCalendarStyle(data.props["visual_style"])
        val styleData = data.copy(props = data.props.toMutableMap().apply {
            if (!isClockFamily(data)) put("visual_style", normalizedStyle)
        })
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

        ClockDefinition.bindPropertiesPanel(panelView, data, onUpdate)
    }

    fun familyKind(data: ComponentData): String =
        if (data.props["family_kind"] == FAMILY_CLOCK) FAMILY_CLOCK else FAMILY_CALENDAR

    fun isClockFamily(data: ComponentData): Boolean = familyKind(data) == FAMILY_CLOCK

    private fun normalizeCalendarStyle(style: String?): String = when (style) {
        "BIG_DATE" -> "BIG_DATE"
        "DATE_TIME" -> "DATE_TIME"
        else -> "MONTH"
    }

    private fun clockStyle(style: String?): String = when (style) {
        "ANALOG" -> "ANALOG"
        "COMBO" -> "COMBO"
        else -> "DIGITAL"
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
