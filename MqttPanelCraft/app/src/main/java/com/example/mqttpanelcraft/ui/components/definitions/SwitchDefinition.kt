package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.SwitchCompat
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object SwitchDefinition : IComponentDefinition {
    
    // 1. Identity
    override val type = "SWITCH"
    override val defaultSize = Size(100, 60)
    override val labelPrefix = "sw"
    override val iconResId = android.R.drawable.btn_radio
    override val group = "CONTROL"

    // 2. Factory (Appearance)
    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        
        val switchView = SwitchCompat(context).apply { 
            text = "" // No text inside switch usually
            setShowText(false)
            
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }
        
        // Add content at index 0
        container.addView(switchView, 0)
        
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val switchView = findSwitchIn(container) ?: return
        
        // 1. Color (Track/Thumb tinting logic could go here)
        // For simplicity, we stick to default or theme colors for now, 
        // or we could use data.props["color"] to tint the thumb.
        val colorHex = data.props["color"]
        if (colorHex != null) {
            try {
                val color = android.graphics.Color.parseColor(colorHex)
                androidx.core.widget.CompoundButtonCompat.setButtonTintList(switchView, android.content.res.ColorStateList.valueOf(color))
                // Also tint thumb/track if possible, but basic button tint might comply
                switchView.thumbTintList = android.content.res.ColorStateList.valueOf(color)
                switchView.trackTintList = android.content.res.ColorStateList.valueOf(color) // simplistic
            } catch (e: Exception) { }
        }

        // 2. Initial State (if available, though typically state comes from MQTT)
        // We usually don't bind "default" state from props unless specific requirement.
    }

    // 3. Properties (Binder)
    override val propertiesLayoutId = 0
    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {}

    // 4. Runtime Behavior
    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        val container = view as? FrameLayout ?: return
        val switchView = findSwitchIn(container) ?: return
        
        switchView.setOnCheckedChangeListener { _, isChecked ->
             // Only send if NOT updating from MQTT (Lock check)
             if (view.tag != "UPDATING_FROM_MQTT") {
                 val topic = data.topicConfig
                 if (topic.isNotEmpty()) {
                     val payload = if (isChecked) "1" else "0"
                     sendMqtt(topic, payload)
                 }
             }
        }
    }

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {
        val container = view as? FrameLayout ?: return
        val switchView = findSwitchIn(container) ?: return
        
        val newState = (payload == "1" || payload.equals("on", true) || payload.equals("true", true))
        
        if (switchView.isChecked != newState) {
            // Set Lock
            view.tag = "UPDATING_FROM_MQTT"
            switchView.isChecked = newState
            view.tag = null // Release Lock
        }
    }

    private fun findSwitchIn(container: FrameLayout): SwitchCompat? {
        for (i in 0 until container.childCount) {
             val child = container.getChildAt(i)
             if (child is SwitchCompat) return child
        }
        return null
    }

}
