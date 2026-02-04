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
    override val propertiesLayoutId = R.layout.layout_prop_generic_color
    
    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
        val vPropColorPreview = panelView.findViewById<android.widget.TextView>(R.id.vPropColorPreview)
        
        // Initial State
        val currentColor = data.props["color"] ?: ""
        updateColorView(vPropColorPreview, currentColor)
        
        vPropColorPreview.setOnClickListener {
             com.example.mqttpanelcraft.ui.ColorPickerDialog(
                context = panelView.context,
                initialColor = if (currentColor.isEmpty()) "#FFFFFFFF" else currentColor,
                showAlpha = true,
                onColorSelected = { selectedHex ->
                    onUpdate("color", selectedHex)
                    updateColorView(vPropColorPreview, selectedHex)
                }
            ).show(vPropColorPreview)
        }
    }
    
    private fun updateColorView(view: android.widget.TextView, hex: String) {
        try {
            val context = view.context
            val defaultStr = context.getString(R.string.properties_label_default)
            if (hex.isEmpty() || hex == "Default" || hex == defaultStr) {
                 view.text = defaultStr
                 view.setTextColor(android.graphics.Color.BLACK)
                 view.setShadowLayer(0f, 0f, 0f, 0)
                 
                 val bg = view.background as? android.graphics.drawable.GradientDrawable ?: android.graphics.drawable.GradientDrawable()
                 bg.setColor(android.graphics.Color.WHITE) 
                 val density = context.resources.displayMetrics.density
                 bg.setStroke((1 * density).toInt(), android.graphics.Color.LTGRAY, (5 * density).toFloat(), (3 * density).toFloat()) // Dashed border
                 bg.cornerRadius = (12 * density)
                 view.background = bg
                 return
            }

            val color = android.graphics.Color.parseColor(hex)
            // Update View Background
            val bg = view.background as? android.graphics.drawable.GradientDrawable ?: android.graphics.drawable.GradientDrawable()
            bg.setColor(color)
            val density = context.resources.displayMetrics.density
            bg.setStroke((2 * density).toInt(), android.graphics.Color.parseColor("#808080")) // Solid border
            bg.cornerRadius = (12 * density)
            view.background = bg
            
            // Update Text
            view.text = hex
            
            // Contrast Logic
            val alpha = android.graphics.Color.alpha(color)
            val luminescence = (0.299 * android.graphics.Color.red(color) + 0.587 * android.graphics.Color.green(color) + 0.114 * android.graphics.Color.blue(color))
            
            val contrastColor = if (alpha < 180 || luminescence > 186) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            
            view.setTextColor(contrastColor)
            view.setShadowLayer(0f, 0f, 0f, 0) // Ensure no shadow
            
        } catch(e: Exception) {}
    }

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
