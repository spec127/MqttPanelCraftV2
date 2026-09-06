package com.example.mqttpanelcraft.ui.components.definitions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.util.Size
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.example.mqttpanelcraft.ProjectViewModel
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.components.prop.PropertyOption
import com.example.mqttpanelcraft.ui.views.ClockTriggerView
import com.google.android.material.button.MaterialButtonToggleGroup

object ClockDefinition : IComponentDefinition {
    override val type: String = "CLOCK"
    override val defaultSize: Size = Size(160, 100)
    override val labelPrefix: String = "clock"
    override val displayNameResId: Int = R.string.component_label_clock
    override val iconResId: Int = android.R.drawable.ic_lock_idle_alarm
    override val group = ComponentGroup.DISPLAY
    override val propertiesLayoutId: Int = R.layout.layout_prop_clock

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "clock_mode" to "TIME",
        "time_format" to "HH:mm",
        "countdown_seconds" to "60",
        "schedule_time" to "07:30",
        "trigger_value" to "TRIGGER",
        "linked_components" to "",
        "visual_style" to "DIGITAL",
        "color" to "#7B1FA2"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        container.addView(ClockTriggerView(context).apply {
            tag = "target_clock"
            this.isEditMode = isEditMode
        }, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val clock = view.findViewWithTag<ClockTriggerView>("target_clock") ?: return
        clock.componentId = data.id
        clock.setConfig(
            data.props["clock_mode"] ?: "TIME",
            data.props["time_format"] ?: "HH:mm",
            data.props["countdown_seconds"]?.toLongOrNull() ?: 60L,
            data.props["schedule_time"] ?: "07:30",
            data.props["trigger_value"] ?: "TRIGGER",
            data.props["visual_style"] ?: "DIGITAL",
            data.props["color"] ?: "#7B1FA2"
        )
    }

    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
        val initialMode = data.props["clock_mode"] ?: "TIME"
        CommonPropBinder.bindDropdown(
            panelView, R.id.spClockTimeFormat, "time_format", data, onUpdate,
            listOf("HH:mm:ss", "HH:mm", "hh:mm a"),
            defaultValue = "HH:mm"
        )
        CommonPropBinder.bindEditText(panelView, R.id.etCountdownSeconds, "countdown_seconds", data, onUpdate, "60")
        CommonPropBinder.bindEditText(panelView, R.id.etScheduleTime, "schedule_time", data, onUpdate, "07:30")
        CommonPropBinder.bindEditText(panelView, R.id.etTriggerValue, "trigger_value", data, onUpdate, "TRIGGER")
        CommonPropBinder.bindColorPalette(
            panelView, R.id.propClockColor, "color", data, onUpdate,
            label = panelView.context.getString(R.string.properties_label_theme_color),
            defaultColor = "#7B1FA2"
        )

        val countdownContainer = panelView.findViewById<View>(R.id.containerCountdownSeconds)
        val scheduleContainer = panelView.findViewById<View>(R.id.containerScheduleTime)
        val triggerContainer = panelView.findViewById<View>(R.id.containerClockTriggerSettings)
        fun updateVisibility(mode: String) {
            countdownContainer?.visibility = if (mode == "COUNTDOWN") View.VISIBLE else View.GONE
            scheduleContainer?.visibility = if (mode == "SCHEDULE") View.VISIBLE else View.GONE
            triggerContainer?.visibility = if (mode == "TIME") View.GONE else View.VISIBLE
        }
        updateVisibility(initialMode)
        CommonPropBinder.bindToggleGroup(
                panelView,
                R.id.tgClockMode,
                "clock_mode",
                data,
                { _, mode ->
                updateVisibility(mode)
                onUpdate("clock_mode", mode)
                },
                mapOf(
                        R.id.btnClockModeTime to "TIME",
                        R.id.btnClockModeCountdown to "COUNTDOWN",
                        R.id.btnClockModeSchedule to "SCHEDULE"
                )
        )

        CommonPropBinder.bindLocalizedDropdown(
            panelView,
            R.id.spClockVisualStyle,
            "visual_style",
            data,
            onUpdate,
            listOf(
                PropertyOption("DIGITAL", R.string.clock_style_digital),
                PropertyOption("ANALOG", R.string.clock_style_analog),
                PropertyOption("COMBO", R.string.clock_style_combo)
            ),
            "DIGITAL"
        )

        bindLinkedComponents(panelView, data, onUpdate)
    }

    private fun bindLinkedComponents(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
        val owner = findActivity(panelView.context) as? ViewModelStoreOwner
        val components = owner?.let { ViewModelProvider(it)[ProjectViewModel::class.java].components.value }.orEmpty()
        val targets = components.filter { component ->
            component.id != data.id &&
                    ComponentDefinitionRegistry.get(component.type)?.group == ComponentGroup.CONTROL
        }
        CommonPropBinder.bindLinkedComponents(
            panelView,
            R.id.containerClockLinkedComponents,
            data,
            targets,
            onUpdate,
            emptyTextResId = R.string.clock_no_linked,
            itemLabel = { it.label },
            ownerLabel = { panelView.context.getString(R.string.clock_self_linked, it.label) }
        )
    }

    private fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) = Unit

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) = Unit
}
