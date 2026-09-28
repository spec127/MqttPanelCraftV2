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
import kotlin.math.abs

object CalendarClockDefinition : IComponentDefinition {
    const val FAMILY_CALENDAR = "CALENDAR"
    const val FAMILY_CLOCK = "CLOCK"
    const val PROP_CALENDAR_STYLE = "calendar_style"
    const val PROP_CLOCK_STYLE = "clock_style"
    const val PROP_VISUAL_STYLE = "visual_style"
    private const val SIZE_SLOP_DP = 8

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
        PROP_CALENDAR_STYLE to "MONTH",
        PROP_CLOCK_STYLE to "DIGITAL",
        PROP_VISUAL_STYLE to "MONTH",
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
                clockStyleOf(data),
                data.props["color"] ?: "#7B1FA2"
            )
        } else {
            calView?.setConfig(
                calendarStyleOf(data),
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
            val fromKind = familyKind(data)
            val fromStyle = styleOf(fromKind, data)
            val toStyle = styleOf(kind, data)
            maybeResizeForStyle(
                data,
                panelView.resources.displayMetrics.density,
                fromKind,
                fromStyle,
                kind,
                toStyle,
                onUpdate
            )
            onUpdate("family_kind", kind)
            onUpdate(PROP_VISUAL_STYLE, toStyle)
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

        val calendarStyle = calendarStyleOf(data)
        val styleData = data.copy(props = data.props.toMutableMap().apply {
            put(PROP_CALENDAR_STYLE, calendarStyle)
        })
        val timeFormatContainer = panelView.findViewById<View>(R.id.containerTimeFormat)
        fun updateTimeFormatVisibility(style: String) {
            timeFormatContainer?.visibility = if (style == "DATE_TIME") View.VISIBLE else View.GONE
        }
        updateTimeFormatVisibility(calendarStyle)
        CommonPropBinder.bindLocalizedDropdown(
            panelView,
            R.id.spVisualStyle,
            PROP_CALENDAR_STYLE,
            styleData,
            { _, value ->
                if (!isClockFamily(data)) {
                    maybeResizeForStyle(
                        data,
                        panelView.resources.displayMetrics.density,
                        FAMILY_CALENDAR,
                        calendarStyleOf(data),
                        FAMILY_CALENDAR,
                        value,
                        onUpdate
                    )
                    onUpdate(PROP_VISUAL_STYLE, value)
                }
                onUpdate(PROP_CALENDAR_STYLE, value)
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
        if (data.type == ClockDefinition.type || data.props["family_kind"] == FAMILY_CLOCK) {
            FAMILY_CLOCK
        } else {
            FAMILY_CALENDAR
        }

    fun isClockFamily(data: ComponentData): Boolean = familyKind(data) == FAMILY_CLOCK

    fun mergeComponent(data: ComponentData): ComponentData {
        if (data.type != type && data.type != ClockDefinition.type) return data
        val props = data.props.toMutableMap()
        val skip = setOf("family_kind", PROP_VISUAL_STYLE, PROP_CALENDAR_STYLE, PROP_CLOCK_STYLE)
        if (data.type == ClockDefinition.type) {
            ClockDefinition.getDefaultProps().forEach { (key, value) ->
                if (key !in skip) props.putIfAbsent(key, value)
            }
            getDefaultProps().forEach { (key, value) ->
                if (key !in skip) props.putIfAbsent(key, value)
            }
            props["family_kind"] = FAMILY_CLOCK
            splitStyles(props, FAMILY_CLOCK)
            return data.copy(type = type, props = props)
        }
        getDefaultProps().forEach { (key, value) ->
            if (key !in skip) props.putIfAbsent(key, value)
        }
        val kind = familyKind(data.copy(props = props))
        props["family_kind"] = kind
        splitStyles(props, kind)
        return if (props == data.props) data else data.copy(props = props)
    }

    fun calendarStyleOf(data: ComponentData): String {
        val stored = data.props[PROP_CALENDAR_STYLE]
        if (stored == "BIG_DATE" || stored == "DATE_TIME" || stored == "MONTH") return stored
        return normalizeCalendarStyle(data.props[PROP_VISUAL_STYLE])
    }

    fun clockStyleOf(data: ComponentData): String {
        val stored = data.props[PROP_CLOCK_STYLE]
        if (stored == "ANALOG" || stored == "COMBO" || stored == "DIGITAL") return stored
        val legacy = data.props[PROP_VISUAL_STYLE]
        return if (legacy == "ANALOG" || legacy == "COMBO" || legacy == "DIGITAL") legacy else "DIGITAL"
    }

    fun styleOf(kind: String, data: ComponentData): String =
        if (kind == FAMILY_CLOCK) clockStyleOf(data) else calendarStyleOf(data)

    fun defaultSizeDp(kind: String, style: String?): Pair<Int, Int> {
        if (kind == FAMILY_CLOCK) {
            return when (clockStyle(style)) {
                "ANALOG", "COMBO" -> 160 to 160
                else -> 160 to 100
            }
        }
        return when (normalizeCalendarStyle(style)) {
            "DATE_TIME" -> 180 to 88
            "BIG_DATE" -> 140 to 168
            else -> 180 to 180
        }
    }

    fun maybeResizeForStyle(
        data: ComponentData,
        density: Float,
        fromKind: String,
        fromStyle: String?,
        toKind: String,
        toStyle: String?,
        onUpdate: (String, String) -> Unit
    ) {
        val from = defaultSizeDp(fromKind, fromStyle)
        val to = defaultSizeDp(toKind, toStyle)
        if (from == to) return
        val slop = (SIZE_SLOP_DP * density).toInt().coerceAtLeast(2)
        val fromW = (from.first * density).toInt()
        val fromH = (from.second * density).toInt()
        if (abs(data.width - fromW) > slop || abs(data.height - fromH) > slop) return
        onUpdate("w", (to.first * density).toInt().toString())
        onUpdate("h", (to.second * density).toInt().toString())
    }

    fun normalizeCalendarStyle(style: String?): String = when (style) {
        "BIG_DATE" -> "BIG_DATE"
        "DATE_TIME" -> "DATE_TIME"
        else -> "MONTH"
    }

    fun clockStyle(style: String?): String = when (style) {
        "ANALOG" -> "ANALOG"
        "COMBO" -> "COMBO"
        else -> "DIGITAL"
    }

    private fun splitStyles(props: MutableMap<String, String>, kind: String) {
        val legacy = props[PROP_VISUAL_STYLE]
        val calendarStored = props[PROP_CALENDAR_STYLE]
        props[PROP_CALENDAR_STYLE] = when {
            calendarStored == "BIG_DATE" || calendarStored == "DATE_TIME" || calendarStored == "MONTH" -> calendarStored
            legacy == "BIG_DATE" || legacy == "DATE_TIME" || legacy == "MONTH" -> legacy!!
            else -> "MONTH"
        }
        val clockStored = props[PROP_CLOCK_STYLE]
        props[PROP_CLOCK_STYLE] = when {
            clockStored == "ANALOG" || clockStored == "COMBO" || clockStored == "DIGITAL" -> clockStored
            legacy == "ANALOG" || legacy == "COMBO" || legacy == "DIGITAL" -> legacy!!
            else -> "DIGITAL"
        }
        props[PROP_VISUAL_STYLE] =
            if (kind == FAMILY_CLOCK) props.getValue(PROP_CLOCK_STYLE) else props.getValue(PROP_CALENDAR_STYLE)
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
