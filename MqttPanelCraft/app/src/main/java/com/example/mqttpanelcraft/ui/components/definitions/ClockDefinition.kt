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
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.LocalComponentTriggerSource
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.ClockTriggerView
import com.google.android.material.button.MaterialButtonToggleGroup

object ClockDefinition : IComponentDefinition, LocalComponentTriggerSource {
    override val type: String = "CLOCK"
    override val defaultSize: Size = Size(160, 100)
    override val labelPrefix: String = "clock"
    override val iconResId: Int = android.R.drawable.ic_lock_idle_alarm
    override val group: String = "DISPLAY"
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
        val modeToggle = panelView.findViewById<MaterialButtonToggleGroup>(R.id.tgClockMode)
        val initialMode = data.props["clock_mode"] ?: "TIME"
        modeToggle?.check(
            when (initialMode) {
                "COUNTDOWN" -> R.id.btnClockModeCountdown
                "SCHEDULE" -> R.id.btnClockModeSchedule
                else -> R.id.btnClockModeTime
            }
        )
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
            label = "主題顏色", defaultColor = "#7B1FA2"
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
        modeToggle?.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                val mode = when (checkedId) {
                    R.id.btnClockModeCountdown -> "COUNTDOWN"
                    R.id.btnClockModeSchedule -> "SCHEDULE"
                    else -> "TIME"
                }
                updateVisibility(mode)
                onUpdate("clock_mode", mode)
            }
        }

        CommonPropBinder.bindDropdown(
            panelView, R.id.spClockVisualStyle, "visual_style", data, onUpdate,
            listOf("數字鐘", "類比時鐘"),
            mapOf("數字鐘" to "DIGITAL", "類比時鐘" to "ANALOG"),
            defaultValue = "DIGITAL"
        )

        bindLinkedComponents(panelView, data, onUpdate)
    }

    private fun bindLinkedComponents(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
        val container = panelView.findViewById<LinearLayout>(R.id.containerClockLinkedComponents) ?: return
        container.removeAllViews()
        val linked = data.props["linked_components"].orEmpty().split(",").filter { it.isNotBlank() }.toMutableSet()
        val owner = findActivity(panelView.context) as? ViewModelStoreOwner
        val components = owner?.let { ViewModelProvider(it)[ProjectViewModel::class.java].components.value }.orEmpty()
        val targets = components.filter { component ->
            component.id != data.id && ComponentDefinitionRegistry.get(component.type)?.group == "CONTROL"
        }
        container.addView(CheckBox(panelView.context).apply {
            text = "${data.label}（時鐘本身不發送）"
            isChecked = true
            isEnabled = false
            alpha = 0.5f
        })
        targets.forEach { component ->
            container.addView(CheckBox(panelView.context).apply {
                text = component.label
                isChecked = component.id.toString() in linked
                setOnCheckedChangeListener { _, checked ->
                    if (checked) linked.add(component.id.toString()) else linked.remove(component.id.toString())
                    onUpdate("linked_components", linked.joinToString(","))
                }
            })
        }
        if (targets.isEmpty()) {
            container.addView(TextView(panelView.context).apply {
                text = "目前沒有可連動的控制元件"
                textSize = 12f
                setTextColor(Color.parseColor("#7A7080"))
            })
        }
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

    override fun attachLocalTrigger(
        view: View,
        data: ComponentData,
        onTriggerLinked: (source: ComponentData, value: String) -> Unit
    ) {
        view.findViewWithTag<ClockTriggerView>("target_clock")?.onLocalTrigger = { value ->
            onTriggerLinked(data, value)
        }
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) = Unit
}
